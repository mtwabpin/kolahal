package com.mtwabpin.kolahal.notifications

import android.app.Notification
import android.app.NotificationManager
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.control.MediaRepository
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Feeds posted notifications into [NotificationStore] and, unless muted, the terminal log
 * (as [LogKind.SCRIPT] lines whose context carries [CTX_KEY] so long-press can open/dismiss).
 */
class KolahalNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userManager by lazy { getSystemService(UserManager::class.java) }
    private val ranking = Ranking()

    /** Last terminal write per key: (time, line). Only touched on the main thread. */
    private val lastLogged = HashMap<String, Pair<Long, String>>()

    override fun onListenerConnected() {
        instance = WeakReference(this)
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        val accepted = active.filter(::accept).map { it to toNotif(it) }
        NotificationStore.replaceAll(accepted.map { (sbn, n) -> n to sbn.notification.contentIntent })
        scope.launch { accepted.forEach { (sbn, n) -> saveIcon(n, sbn) } }
        mirror()
        // Access turned off and on again: now playing listens to media sessions again.
        MediaRepository.get(this).reconnect()
    }

    private var mirrorJob: kotlinx.coroutines.Job? = null

    /** No copy is on disk since this listener cleared it: nothing to delete again at the next notification. */
    @Volatile private var mirrorCleared = false

    /**
     * The copy's writes and clears, one at a time: a write that's already writing can't be cancelled,
     * and a clear started meanwhile would end before it, leaving a copy marked cleared.
     */
    private val mirrorWork = Dispatchers.IO.limitedParallelism(1)

    /** Scripts run in the :script process, so they read a snapshot of the feed from the shared database. */
    private fun mirror() {
        mirrorJob?.cancel()
        mirrorJob = scope.launch(mirrorWork) {
            kotlinx.coroutines.delay(400.milliseconds)
            // Only kept while a script may read it (`@uses notifications`); otherwise no copy at all.
            if (!com.mtwabpin.kolahal.script.TriggerDispatcher.get(this@KolahalNotificationListener).wantsNotifications()) {
                // Only this listener writes the copy, so once cleared it stays cleared.
                if (!mirrorCleared) mirrorCleared = clearMirror(this@KolahalNotificationListener)
                return@launch
            }
            mirrorCleared = false
            runCatching {
                com.mtwabpin.kolahal.data.KvStore(this@KolahalNotificationListener, MIRROR_SCOPE)
                    .set(MIRROR_KEY, com.mtwabpin.kolahal.script.Json.stringify(NotificationStore.recent(50, skipProfile = ::isPrivateSerial)))
            }
        }
    }

    override fun onListenerDisconnected() {
        if (instance?.get() === this) instance = null
        // Access revoked or unbound: don't leave a copy of other apps' notifications on disk.
        mirrorJob?.cancel()
        // Synchronous (a single small delete) so onDestroy cancelling the scope can't skip it.
        clearMirror(this)
        // A write already writing can't be cancelled and may land after that: cleared again once it
        // has, on the same queue, outside the scope onDestroy cancels.
        val app = applicationContext
        CoroutineScope(mirrorWork).launch { clearMirror(app) }
    }

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (!accept(sbn, rankingMap)) return
        val n = toNotif(sbn)
        val shown = NotificationStore.find(n.key)?.takeIf { !it.removed }
        NotificationStore.put(n, sbn.notification.contentIntent)
        val extras = sbn.notification.extras
        val message = isMessage(extras.getString(Notification.EXTRA_TEMPLATE), sbn.notification.category)
        // Download/upload progress and "alert once" updates re-post the same key many times a
        // second; keep the store current but don't spam the log or fire scripts for each tick.
        // A chat's conversation is the exception: its silent update carries the next message.
        if (isQuietRepost(
                known = shown != null,
                flags = sbn.notification.flags,
                progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
                indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
                message = message,
                changed = shown != null && shown.line != n.line,
            )
        ) return
        mirror()
        // The line is written once its icon is saved, so it never shows the app name first.
        maybeLog(n, message, scope.launch { saveIcon(n, sbn) })
        // The private space's notifications stay out of scripts' reach, as its apps do (apps.list).
        if (isPrivate(sbn.user)) return
        com.mtwabpin.kolahal.script.TriggerDispatcher.get(this).fire(
            "notification",
            mapOf("app" to n.app, "pkg" to n.pkg, "title" to n.title, "text" to n.text, "time" to n.postTime),
        )
    }

    private fun isPrivate(user: android.os.UserHandle): Boolean {
        // Your own profile never is: no need to ask the system on every notification from it.
        if (android.os.Build.VERSION.SDK_INT < 35 || user == android.os.Process.myUserHandle()) return false
        val la = getSystemService(android.content.pm.LauncherApps::class.java)
        return runCatching { la.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE }.getOrDefault(false)
    }

    private fun isPrivateSerial(serial: Long): Boolean =
        runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()?.let(::isPrivate) ?: false

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        NotificationStore.markRemoved(sbn.key)
        // Posted again later with the same text (a daily reminder), it's a new line; only updates
        // of a notification still showing are kept to one.
        lastLogged.remove(sbn.key)
        mirror()
    }

    private fun accept(sbn: StatusBarNotification): Boolean = accept(sbn, runCatching { currentRanking }.getOrNull())

    private fun accept(sbn: StatusBarNotification, map: RankingMap?): Boolean {
        if (sbn.packageName == packageName || sbn.isOngoing) return false
        // What the app marks secret stays out of the log, the store and scripts.
        if (sbn.notification.visibility == Notification.VISIBILITY_SECRET) return false
        val flags = sbn.notification.flags
        if (flags and (Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return false
        // Silent/minimized channels (weather, "syncing", promos) would only add noise.
        if (map != null && map.getRanking(sbn.key, ranking) &&
            ranking.importance <= NotificationManager.IMPORTANCE_LOW
        ) return false
        val extras = sbn.notification.extras
        return !extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() ||
            !extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank()
    }

    private fun toNotif(sbn: StatusBarNotification): Notif {
        val extras = sbn.notification.extras
        val serial = runCatching { userManager.getSerialNumberForUser(sbn.user) }.getOrDefault(-1L)
        return Notif(
            key = sbn.key,
            pkg = sbn.packageName,
            userSerial = serial,
            app = labelOf(sbn.packageName, serial),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            postTime = sbn.postTime,
            icon = NotifIcons.key(this, sbn.packageName, sbn.notification.smallIcon),
        )
    }

    private fun saveIcon(n: Notif, sbn: StatusBarNotification) {
        val key = n.icon ?: return
        runCatching { NotifIcons.save(this, key, sbn.notification.smallIcon) }
    }

    private fun labelOf(pkg: String, serial: Long): String =
        AppRepository.get(this).find(pkg, null, serial)?.label
            ?: AppRepository.get(this).find(pkg, null, null)?.label
            ?: runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)

    private fun maybeLog(n: Notif, message: Boolean, iconSaved: kotlinx.coroutines.Job) {
        if (!NotifPrefs.get(this).shouldLog(n.pkg)) return
        val line = n.line
        if (line.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!isNewLine(lastLogged[n.key], line, now, message)) return
        lastLogged[n.key] = now to line
        if (lastLogged.size > NotificationStore.CAP * 2) lastLogged.entries.removeAll { now - it.value.first > RATE_LIMIT_MS }

        val ctx = LogContext.of(
            LogContext.PKG to n.pkg,
            LogContext.USER to n.userSerial.takeIf { it >= 0 }?.toString(),
            LogContext.COPY to line,
            CTX_KEY to n.key,
            LogContext.ICON to n.icon,
        )
        val repo = LogRepository.get(this)
        scope.launch {
            iconSaved.join()
            runCatching { repo.add(LogKind.SCRIPT, line, n.app, ctx) }
        }
    }

    companion object {
        /** LogContext key holding the notification key of a feed line. */
        const val CTX_KEY = "notif"
        const val MIRROR_SCOPE = "system"
        const val MIRROR_KEY = "notifications"
        private const val RATE_LIMIT_MS = 30_000L

        /** Removes the notification snapshot mirrored for scripts; false if that failed. Never throws. */
        fun clearMirror(context: android.content.Context): Boolean = runCatching {
            com.mtwabpin.kolahal.data.KvStore(context.applicationContext, MIRROR_SCOPE).set(MIRROR_KEY, null)
        }.isSuccess

        /**
         * Whether a post is an update of an already-shown notification that shouldn't be logged or
         * trigger scripts: progress notifications, and alert-once updates unless a [message]'s
         * text [changed] (a conversation's next message arrives as a silent update of the same
         * notification; a timer or a player updating its text does too, but isn't a message).
         * New keys always count.
         */
        internal fun isQuietRepost(
            known: Boolean,
            flags: Int,
            progressMax: Int,
            indeterminate: Boolean,
            message: Boolean = false,
            changed: Boolean = false,
        ): Boolean =
            known && (progressMax > 0 || indeterminate || (flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && !(message && changed)))

        /**
         * Whether a post of [line] gets a log line, after [last] (when and what this notification
         * last logged): a chat re-posts its notification for every message, and each new message is
         * a line; anything else updating within 30 seconds (a count going up) keeps its first one.
         */
        internal fun isNewLine(last: Pair<Long, String>?, line: String, now: Long, message: Boolean): Boolean =
            last == null || (last.second != line && (message || now - last.first >= RATE_LIMIT_MS))

        /** A messaging-style notification ([Notification.EXTRA_TEMPLATE]) or one an app files as a message. */
        internal fun isMessage(template: String?, category: String?): Boolean =
            template == MESSAGING_TEMPLATE || category == Notification.CATEGORY_MESSAGE

        private const val MESSAGING_TEMPLATE = $$"android.app.Notification$MessagingStyle"

        @Volatile
        private var instance: WeakReference<KolahalNotificationListener>? = null

        /** The connected listener, if the user granted access and the system bound us. */
        internal val connected: KolahalNotificationListener? get() = instance?.get()
    }
}
