package com.mtwabpin.kolahal.productivity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.notify.Notifier
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Schedules `remind` alarms with AlarmManager (exact when allowed, otherwise inexact while idle). */
object ReminderScheduler {
    const val EXTRA_ID = "reminder_id"

    /** Exact alarms need SCHEDULE_EXACT_ALARM on Android 12+, which the user may have turned off. */
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
        // A time that passed while the phone was off fires right away.
        val at = r.at.coerceAtLeast(System.currentTimeMillis() + 1_000)
        // Android refuses an app's 500th alarm: a failure here must not stop the others.
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

    /** App start and boot: alarms don't survive a reboot (or a force stop). */
    /** Alarms for the earliest [MAX_ALARMS] reminders; each one fired makes room for the next. */
    fun rescheduleAll(context: Context) {
        ReminderStore.get(context).all().sortedBy { it.at }.take(MAX_ALARMS).forEach { schedule(context, it) }
    }

    /** Well under Android's limit of 500 alarms per app. */
    const val MAX_ALARMS = 400

    /**
     * The time a repeating reminder fires at: the one asked for. Without one (set before 0.4.12, or
     * unreadable), the time it fired at, [fired].
     */
    fun timeOf(r: Reminder, fired: LocalDateTime): LocalTime =
        r.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fired.toLocalTime()

    /**
     * A new time zone: each repeating reminder still ahead moves to the time it was asked for, in
     * this zone (a 9:00 reminder set in Delhi fires at 9:00 in London). One that's due already, or
     * has no time of its own, stays.
     */
    fun followZone(context: Context) {
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
        if (moved) rescheduleAll(context)
    }
}

/** Fires one reminder: notification plus a log line, then the reminder is gone, or set for its next time. */
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
                // The time it was asked for, on its next day; a phone off for days skips to the next one ahead.
                val next = rule?.let {
                    // A rule it can't read fires this once: thrown from here, it would stop the app at every start.
                    runCatching { Repeat.next(it, time, maxOf(fired, LocalDateTime.now(zone))).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                }
                if (next == null) {
                    store.remove(id)
                    if (rule != null) LogRepository.get(context).add(LogKind.ERROR, "Reminder '${r.text}' had an invalid repeat rule, so it won't repeat")
                } else {
                    // One set before 0.4.12 keeps the time it fired at from now on, as if asked for.
                    store.setAt(id, next, if (r.time == null) time.toString() else null)
                    ReminderScheduler.schedule(context, r.copy(at = next))
                }
                // More reminders than alarms: the next one waiting gets its alarm now.
                if (store.all().size >= ReminderScheduler.MAX_ALARMS) ReminderScheduler.rescheduleAll(context)
                Notifier.postReminder(context, id, r.text)
                LogRepository.get(context).add(
                    LogKind.SCRIPT, "Reminder: ${r.text}", "remind",
                    // Tap puts a 10-minute snooze in the prompt.
                    LogContext.of(LogContext.COMMAND to "remind 10m ${r.text}", LogContext.COPY to r.text),
                )
            } finally {
                result.finish()
            }
        }.start()
    }
}

/**
 * The reminder notification's buttons: Snooze sets it again, once, [EXTRA_MINUTES] from now (a
 * repeating one keeps its own times too); Done only takes the notification away.
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

/** Puts reminders back after a reboot or an app update. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                val result = goAsync()
                Thread {
                    try {
                        runCatching { ReminderScheduler.rescheduleAll(context) }
                        // Scripts' daily times are alarms too (exact once that is allowed).
                        runCatching { com.mtwabpin.kolahal.script.ScriptScheduler.syncAll(context) }
                    } finally {
                        result.finish()
                    }
                }.start()
            }
        }
    }
}
