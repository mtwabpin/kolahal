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
import com.mtwabpin.kolahal.data.StyledText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Puts posted notifications into [NotificationStore]. Unless the app is muted, it also writes them to
 * the terminal log as [LogKind.SCRIPT] lines. Their context holds [CTX_KEY], so a long-press can open
 * or dismiss the notification.
 */
class KolahalNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Scope for the clear that runs after a write that was still running when access was removed.
     * onDestroy doesn't cancel this scope.
     */
    private val afterDestroy = CoroutineScope(SupervisorJob())
    private val userManager by lazy { getSystemService(UserManager::class.java) }
    private val ranking = Ranking()

    /** Last terminal write per key, as (time, line). Used only on the main thread. */
    private val lastLogged = HashMap<String, Pair<Long, String>>()

    override fun onListenerConnected() {
        instance = WeakReference(this)
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        val accepted = active.filter(::accept).map { it to toNotif(it) }
        NotificationStore.replaceAll(accepted.map { (sbn, n) -> n to sbn.notification.contentIntent })
        // The notifications still showing were logged when they arrived, before access was removed or
        // the process ended. So an unchanged re-post of one of them isn't a new line.
        accepted.forEach { (sbn, n) -> if (n.line.isNotEmpty()) lastLogged.putIfAbsent(n.key, sbn.postTime to n.line) }
        scope.launch { accepted.forEach { (sbn, n) -> saveIcon(n, sbn) } }
        mirror()
        // If access was turned off and on again, now playing listens to media sessions again.
        MediaRepository.get(this).reconnect()
    }

    private var mirrorJob: kotlinx.coroutines.Job? = null

    /**
     * True when this listener cleared the copy and no copy is on disk since. Then the next
     * notification has nothing to delete.
     */
    @Volatile private var mirrorCleared = false

    /**
     * Runs the copy's writes and clears one at a time. A write that has started can't be cancelled.
     * A clear started during it would end first, and leave a copy on disk that is marked cleared.
     */
    private val mirrorWork = Dispatchers.IO.limitedParallelism(1)

    /** Scripts run in the :script process, so they read a copy of the feed from the shared database. */
    private fun mirror() {
        mirrorJob?.cancel()
        mirrorJob = scope.launch(mirrorWork) {
            kotlinx.coroutines.delay(400.milliseconds)
            // Keep a copy only while a script may read it (`@uses notifications`). Otherwise keep no copy.
            if (!com.mtwabpin.kolahal.script.TriggerDispatcher.get(this@KolahalNotificationListener).wantsNotifications()) {
                // Only this listener writes the copy, so after a clear it stays cleared.
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
        // Access was removed or the listener was unbound. Don't leave a copy of other apps'
        // notifications on disk.
        mirrorJob?.cancel()
        // Run it synchronously (it is one small delete), so onDestroy can't skip it by cancelling the scope.
        clearMirror(this)
        // A write that has started can't be cancelled, and it may finish after that clear. So clear
        // again after it, on the same queue, outside the scope that onDestroy cancels.
        val app = applicationContext
        afterDestroy.launch(mirrorWork) { clearMirror(app) }
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
        // second. Keep the store up to date, but don't flood the log or fire scripts for each update.
        // A chat conversation is the exception: its silent update carries the next message.
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
        // Write the line after its icon is saved, so it never shows the app name first.
        maybeLog(n, message, scope.launch { saveIcon(n, sbn) })
        // Scripts can't see notifications from the private space, just as they can't see its apps (apps.list).
        if (isPrivate(sbn.user)) return
        com.mtwabpin.kolahal.script.TriggerDispatcher.get(this).fire(
            "notification",
            mapOf("app" to n.app, "pkg" to n.pkg, "title" to n.title, "text" to n.text, "time" to n.postTime),
        )
    }

    private fun isPrivate(user: android.os.UserHandle): Boolean {
        // Your own profile is never private, so don't ask the system for each of its notifications.
        if (android.os.Build.VERSION.SDK_INT < 35 || user == android.os.Process.myUserHandle()) return false
        val la = getSystemService(android.content.pm.LauncherApps::class.java)
        return runCatching { la.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE }.getOrDefault(false)
    }

    /** Serial of your own profile. It is never the private space, and most notifications come from it. */
    private val ownSerial by lazy { runCatching { userManager.getSerialNumberForUser(android.os.Process.myUserHandle()) }.getOrDefault(-1L) }

    private fun isPrivateSerial(serial: Long): Boolean = serial != ownSerial &&
        (runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()?.let(::isPrivate) ?: false)

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        NotificationStore.markRemoved(sbn.key)
        // If it is posted again later with the same text (a daily reminder), it gets a new line.
        // Only updates of a notification that is still showing are limited to one line.
        lastLogged.remove(sbn.key)
        mirror()
    }

    private fun accept(sbn: StatusBarNotification): Boolean = accept(sbn, runCatching { currentRanking }.getOrNull())

    private fun accept(sbn: StatusBarNotification, map: RankingMap?): Boolean {
        if (sbn.packageName == packageName || sbn.isOngoing) return false
        // Notifications the app marks secret stay out of the log, the store and scripts.
        if (sbn.notification.visibility == Notification.VISIBILITY_SECRET) return false
        val flags = sbn.notification.flags
        if (flags and (Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return false
        // Silent or minimized channels (weather, "syncing", promos) would only add noise.
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
            // Remove styling: another app's text must not style a log line.
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let(StyledText::strip),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.let(StyledText::strip),
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
                StyledText.strip(packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString())
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
        /** LogContext key that holds the notification key of a feed line. */
        const val CTX_KEY = "notif"
        const val MIRROR_SCOPE = com.mtwabpin.kolahal.data.Stores.NOTIFICATIONS_MIRROR
        const val MIRROR_KEY = "notifications"
        private const val RATE_LIMIT_MS = 30_000L

        /** Removes the copy of notifications kept for scripts. Returns false if that failed. Never throws. */
        fun clearMirror(context: android.content.Context): Boolean = runCatching {
            com.mtwabpin.kolahal.data.KvStore(context.applicationContext, MIRROR_SCOPE).set(MIRROR_KEY, null)
        }.isSuccess

        /**
         * True when a post is an update of a notification already shown, and it shouldn't be logged or
         * trigger scripts. This covers:
         * - progress notifications
         * - alert-once updates, unless it is a [message] whose text [changed]
         *
         * A conversation's next message arrives as a silent update of the same notification. A timer
         * or a player that updates its text does this too, but it isn't a message.
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
         * True when a post of [line] gets a log line. [last] is when and what this notification last
         * logged.
         * - A chat re-posts its notification for every message, and each new message gets a line.
         * - Any other update within 30 seconds (for example, a count going up) keeps only its first line.
         */
        internal fun isNewLine(last: Pair<Long, String>?, line: String, now: Long, message: Boolean): Boolean =
            last == null || (last.second != line && (message || now - last.first >= RATE_LIMIT_MS))

        /** True for a messaging-style notification ([Notification.EXTRA_TEMPLATE]), or one the app marks as a message. */
        internal fun isMessage(template: String?, category: String?): Boolean =
            template == MESSAGING_TEMPLATE || category == Notification.CATEGORY_MESSAGE

        private const val MESSAGING_TEMPLATE = $$"android.app.Notification$MessagingStyle"

        @Volatile
        private var instance: WeakReference<KolahalNotificationListener>? = null

        /** The connected listener, if the user granted access and the system bound it. */
        internal val connected: KolahalNotificationListener? get() = instance?.get()
    }
}
