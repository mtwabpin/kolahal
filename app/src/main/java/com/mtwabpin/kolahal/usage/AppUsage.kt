package com.mtwabpin.kolahal.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/**
 * One app's use, from Android's usage data (Usage Access): `usage <app>`.
 * - Its time on a day is Android's own total, as `usage` shows it.
 * - Its opens, sessions and hours today come from Android's events ([AppSessions]).
 */
class AppUsage(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val zone = ZoneId.systemDefault()

    private fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Android's total time in front for [pkg] on [day] (up to now for today). */
    fun timeOn(pkg: String, day: LocalDate, now: Long = System.currentTimeMillis()): Long {
        val until = minOf(startOf(day.plusDays(1)), now)
        return runCatching { usage.queryAndAggregateUsageStats(startOf(day), until)[pkg]?.totalTimeInForeground ?: 0L }.getOrDefault(0L)
    }

    /**
     * [pkg]'s time on each of the last [days] days, the oldest first, for the days with data
     * ([UsageHistory]). Today is the last.
     */
    fun days(pkg: String, days: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Long>> =
        UsageHistory.days(context, days, today).map { it to timeOn(pkg, it) }

    /** [pkg]'s sessions today, from midnight to now ([AppSessions.of]), an hour slot for each hour so far. */
    fun today(pkg: String, now: Long = System.currentTimeMillis()): AppSessions {
        val from = startOf(LocalDate.now(zone))
        val hours = ((now - from) / 3_600_000L).toInt() + 1
        return AppSessions.of(events(from, now), pkg, from, now, hours.coerceIn(1, 25))
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
