package com.mtwabpin.kolahal.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/**
 * Apps' use, from Android's usage events (Usage Access): `usage` and `usage <app>`.
 * - Every time is worked out from the events, counted from local midnight ([AppSessions]): the
 *   totals, the days, the opens, the sessions and the hours. Android's own daily totals start at
 *   another hour on many phones (midnight UTC), so a day of theirs mixes two of yours.
 */
class AppUsage(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val zone = ZoneId.systemDefault()

    private fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** [pkg]'s time in front on [day] (up to now for today). */
    fun timeOn(pkg: String, day: LocalDate, now: Long = System.currentTimeMillis()): Long {
        val from = startOf(day)
        val until = minOf(startOf(day.plusDays(1)), now)
        return AppSessions.totals(events(from, until), from, until)[pkg] ?: 0L
    }

    /** Each app's time in front in [from, until), by package. */
    fun totals(from: Long, until: Long): Map<String, Long> = AppSessions.totals(events(from, until), from, until)

    /**
     * [pkg]'s time on each of the last [days] days, the oldest first, for the days with data
     * ([UsageHistory]). Today is the last.
     */
    fun days(pkg: String, days: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Long>> =
        UsageHistory.days(context, days, today).map { it to timeOn(pkg, it) }

    /** [pkg]'s sessions today, from midnight to now ([AppSessions.of]), a slot for each hour of the day. */
    fun today(pkg: String, now: Long = System.currentTimeMillis()): AppSessions {
        val from = startOf(LocalDate.now(zone))
        return AppSessions.of(events(from, now), pkg, from, now, 24)
    }

    /** Android's usage events in [from, until), only of the kinds [AppSessions] reads. */
    private fun events(from: Long, until: Long): List<AppSessions.Companion.Event> {
        val kinds = intArrayOf(
            AppSessions.RESUMED, AppSessions.PAUSED, AppSessions.STOPPED,
            AppSessions.SCREEN_OFF, AppSessions.LOCKED, AppSessions.SHUTDOWN,
        )
        val read = runCatching {
            if (Build.VERSION.SDK_INT >= 35) usage.queryEvents(UsageEventsQuery.Builder(from, until).setEventTypes(*kinds).build())
            else usage.queryEvents(from, until)
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<AppSessions.Companion.Event>()
        val e = UsageEvents.Event()
        while (read.hasNextEvent()) {
            read.getNextEvent(e)
            if (e.eventType in kinds) out += AppSessions.Companion.Event(e.eventType, e.packageName ?: "", e.timeStamp)
        }
        return out
    }
}
