package com.mtwabpin.kolahal.productivity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.notify.Notifier
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Schedules `remind` alarms with AlarmManager. They are exact when allowed, otherwise inexact (allowed while idle). */
object ReminderScheduler {
    const val EXTRA_ID = "reminder_id"

    /** On Android 12+, exact alarms need SCHEDULE_EXACT_ALARM, and the user may have turned it off. */
    fun canBeExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pending(context: Context, id: Long, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or flags,
        )

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, r.id) ?: return
        // If the time passed while the phone was off, the alarm fires right away.
        val at = r.at.coerceAtLeast(System.currentTimeMillis() + 1_000)
        // Android refuses an app's 500th alarm. A failure here must not stop the other alarms.
        runCatching {
            if (canBeExact(context)) {
                runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
                    .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        val pi = pending(context, id, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    /**
     * Sets the alarms again on app start, boot and restore, because alarms don't survive a reboot or a
     * force stop.
     * - Only the earliest [MAX_ALARMS] reminders get an alarm. Each fired alarm makes room for the next.
     * - If the time zone changed while Kolahal wasn't running, nothing saw the change. So this first
     *   moves the repeating reminders to the new zone.
     */
    fun rescheduleAll(context: Context) {
        if (zoneChanged(context)) moveToZone(context)
        ReminderStore.get(context).all().sortedBy { it.at }.take(MAX_ALARMS).forEach { schedule(context, it) }
    }

    /** Well below Android's limit of 500 alarms per app. */
    const val MAX_ALARMS = 400

    /**
     * Returns the time of day a repeating reminder fires at: the time the user asked for. If there is
     * none (set before 0.4.12, or unreadable), returns the time it fired at, [fired].
     */
    fun timeOf(r: Reminder, fired: LocalDateTime): LocalTime =
        r.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fired.toLocalTime()

    /**
     * Call on a new time zone. Each future repeating reminder moves to its requested time in the new
     * zone (a 9:00 reminder set in Delhi fires at 9:00 in London). A reminder that is already due, or
     * has no saved time, stays as it is.
     */
    fun followZone(context: Context) {
        zoneChanged(context)
        if (moveToZone(context)) rescheduleAll(context)
    }

    /** The zone the reminders were last moved to. It is the phone's zone, unless that changed while Kolahal wasn't running. */
    internal const val KEY_ZONE = "zone"
    internal const val PREFS = "reminders"

    /** True when the phone's zone differs from the reminders' zone. In both cases, it saves the phone's zone as theirs. */
    private fun zoneChanged(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = ZoneId.systemDefault().id
        val was = prefs.getString(KEY_ZONE, null)
        if (was == now) return false
        prefs.edit { putString(KEY_ZONE, now) }
        // The first time, there is nothing to compare with.
        return was != null
    }

    /** Moves reminders like [followZone], but doesn't set the alarms. Returns true when one moved. */
    private fun moveToZone(context: Context): Boolean {
        val store = ReminderStore.get(context)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var moved = false
        for (r in store.all()) {
            val rule = r.repeat ?: continue
            val time = r.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: continue
            if (r.at <= now) continue
            val next = runCatching {
                Repeat.next(rule, time, LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull() ?: continue
            if (next == r.at) continue
            store.setAt(r.id, next)
            moved = true
        }
        return moved
    }
}

/** Fires one reminder: a notification and a log line. Then it deletes the reminder, or sets it for its next time. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1)
        if (id < 0) return
        val result = goAsync()
        Thread {
            try {
                val store = ReminderStore.get(context)
                val r = store.byId(id) ?: return@Thread // cancelled meanwhile
                val rule = r.repeat
                val zone = ZoneId.systemDefault()
                val fired = Instant.ofEpochMilli(r.at).atZone(zone).toLocalDateTime()
                val time = ReminderScheduler.timeOf(r, fired)
                // Next time: the requested time on the next matching day. If the phone was off for days,
                // it skips to the next day in the future.
                val next = rule?.let {
                    // An unreadable rule fires only this once. An exception here would crash the app at every start.
                    runCatching { Repeat.next(it, time, maxOf(fired, LocalDateTime.now(zone))).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                }
                if (next == null) {
                    store.remove(id)
                    if (rule != null) LogRepository.get(context).add(LogKind.ERROR, "Reminder '${r.text}' had an invalid repeat rule, so it won't repeat")
                } else {
                    // A reminder set before 0.4.12 keeps the time it fired at from now on, as if the user asked for it.
                    store.setAt(id, next, if (r.time == null) time.toString() else null)
                    ReminderScheduler.schedule(context, r.copy(at = next))
                }
                // If there are more reminders than alarms, the next waiting reminder gets its alarm now.
                if (store.all().size >= ReminderScheduler.MAX_ALARMS) ReminderScheduler.rescheduleAll(context)
                Notifier.postReminder(context, id, r.text)
                LogRepository.get(context).add(
                    LogKind.SCRIPT, "Reminder: ${r.text}", "remind",
                    // A tap puts a 10-minute snooze command in the prompt.
                    LogContext.of(LogContext.COMMAND to "remind 10m ${r.text}", LogContext.COPY to r.text),
                )
            } finally {
                result.finish()
            }
        }.start()
    }
}

/**
 * Handles the reminder notification's buttons:
 * - Snooze sets a one-time reminder [EXTRA_MINUTES] from now. A repeating reminder also keeps its own times.
 * - Done only removes the notification.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1)
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        Notifier.cancelReminder(context, id)
        if (intent.action != ACTION_SNOOZE) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
        val result = goAsync()
        Thread {
            try {
                val r = ReminderStore.get(context).add(System.currentTimeMillis() + minutes * 60_000L, text)
                ReminderScheduler.schedule(context, r)
                LogRepository.get(context).add(
                    LogKind.SCRIPT, "Snoozed for ${TimeSpec.formatDuration(minutes * 60L)}: $text", "remind",
                    LogContext.of(LogContext.REMINDER to r.id.toString(), LogContext.COPY to text),
                )
            } finally {
                result.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_SNOOZE = "com.mtwabpin.kolahal.REMINDER_SNOOZE"
        const val ACTION_DONE = "com.mtwabpin.kolahal.REMINDER_DONE"
        const val EXTRA_TEXT = "text"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** Sets reminder alarms again after a reboot, an app update, or a change of the exact-alarm permission. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                val result = goAsync()
                Thread {
                    try {
                        com.mtwabpin.kolahal.data.Quietly.run(context, "Setting reminders again") { ReminderScheduler.rescheduleAll(context) }
                        // Scripts' daily times are alarms too. They are exact once that is allowed.
                        com.mtwabpin.kolahal.data.Quietly.run(context, "Scheduling scripts") { com.mtwabpin.kolahal.script.ScriptScheduler.syncAll(context) }
                    } finally {
                        result.finish()
                    }
                }.start()
            }
        }
    }
}
