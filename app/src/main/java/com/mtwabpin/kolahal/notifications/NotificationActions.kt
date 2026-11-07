package com.mtwabpin.kolahal.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Actions the terminal can take on a feed entry, and helpers for listener access. */
object NotificationActions {

    /**
     * True when the user granted Kolahal notification access. We ask Android directly, because the
     * setting that also stores it can be late to update.
     */
    fun isEnabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, KolahalNotificationListener::class.java))

    /** Intent for the access screen. On API 30+ it is the page for this app; otherwise, or if that fails, the list. */
    fun settingsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 30) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, KolahalNotificationListener::class.java).flattenToString(),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (detail.resolveActivity(context.packageManager) != null) return detail
        }
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Runs the notification's tap action if the notification is still showing. Like the shade, it
     * dismisses auto-cancel notifications. Returns false when the notification is gone or has no intent.
     */
    fun open(context: Context, key: String): Boolean {
        // Null when we couldn't ask, for example when access was just removed and the listener is
        // not unbound yet. Then the tap intent saved in the store still works. Only a real answer
        // without the notification means it is gone.
        val asked = KolahalNotificationListener.connected?.let { l -> runCatching { l.getActiveNotifications(arrayOf(key)) }.getOrNull() }
        val sbn = asked?.firstOrNull()
        if (asked != null && sbn == null) {
            NotificationStore.markRemoved(key)
            return false
        }
        val pi = sbn?.notification?.contentIntent ?: NotificationStore.intent(key) ?: return false
        val sent = send(context, pi)
        if (sent && sbn != null && sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) dismiss(key)
        return sent
    }

    /** Removes the notification from the shade. False when the listener isn't connected. */
    fun dismiss(key: String): Boolean {
        val listener = KolahalNotificationListener.connected ?: return false
        return runCatching { listener.cancelNotification(key) }.isSuccess.also {
            if (it) NotificationStore.markRemoved(key)
        }
    }

    /** Dismisses every clearable notification. */
    fun dismissAll(): Boolean {
        val listener = KolahalNotificationListener.connected ?: return false
        return runCatching { listener.cancelAllNotifications() }.isSuccess
    }

    private fun send(context: Context, pi: PendingIntent): Boolean = runCatching {
        // On Android 14+, the target can start an activity only if we lend it our foreground privilege.
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            ).toBundle()
        } else null
        pi.send(context, 0, null, null, null, null, options)
    }.isSuccess
}
