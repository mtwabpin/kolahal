package com.mtwabpin.kolahal.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which days Android still has usage data for (Usage Access).
 * - Android keeps the events of the last days only: about 10 on many phones. A day before that has
 *   no data, and is not a day with 0 unlocks or no screen time.
 * - The oldest day with events is cut: its data starts in the middle of the day. So the first day
 *   that counts is the day after it ([firstDay]).
 * - The `unlocks` and `usage` commands, and `device.unlocks(days)`, leave out days with no data, and
 *   average over the days that have it.
 */
object UsageHistory {
    /** How far back to look for the oldest events. Android keeps far fewer days than this. */
    private const val LOOK_BACK_DAYS = 45L

    /** [firstDay] as found today: found once a day, as it moves only when a day ends. */
    @Volatile private var found: Pair<LocalDate, LocalDate?>? = null

    /** Tests set this: the first day with data, without asking Android. */
    @VisibleForTesting
    @Volatile var firstDayForTests: (() -> LocalDate?)? = null

    /**
     * The first whole day Android has usage events for, or null when it has none (no usage access,
     * or nothing recorded yet). Today has data from then on.
     */
    fun firstDay(context: Context, today: LocalDate = LocalDate.now()): LocalDate? {
        firstDayForTests?.let { return it() }
        found?.takeIf { it.first == today }?.let { return it.second }
        val first = oldestDayWithEvents(context, today)?.plusDays(1)?.let { minOf(it, today) }
        // None found is not kept: usage access may be allowed in a moment.
        if (first != null) found = today to first
        return first
    }

    /** Whether [day] has usage data: a day from [firstDay] to today. */
    fun hasData(context: Context, day: LocalDate, today: LocalDate = LocalDate.now()): Boolean {
        val first = firstDay(context, today) ?: return day == today
        return day in first..today
    }

    /** The days of the last [days] (up to today) that have data, the oldest first. Today is always there. */
    fun days(context: Context, days: Int, today: LocalDate = LocalDate.now()): List<LocalDate> =
        (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.filter { it == today || hasData(context, it, today) }

    /** The oldest day, back to [LOOK_BACK_DAYS], that has any usage event. */
    private fun oldestDayWithEvents(context: Context, today: LocalDate): LocalDate? {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val zone = ZoneId.systemDefault()
        for (back in LOOK_BACK_DAYS downTo 0) {
            val day = today.minusDays(back)
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (hasEvents(usage, from, until)) return day
        }
        return null
    }

    /** Whether Android has any event in [from, until). From Android 15 it reads only the screen and lock events. */
    private fun hasEvents(usage: UsageStatsManager, from: Long, until: Long): Boolean = runCatching {
        val events = if (Build.VERSION.SDK_INT >= 35) {
            usage.queryEvents(
                UsageEventsQuery.Builder(from, until).setEventTypes(
                    UsageEvents.Event.SCREEN_INTERACTIVE, UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_SHOWN, UsageEvents.Event.KEYGUARD_HIDDEN,
                    UsageEvents.Event.ACTIVITY_RESUMED,
                ).build(),
            )
        } else {
            usage.queryEvents(from, until)
        }
        events?.hasNextEvent() == true
    }.getOrDefault(false)
}
