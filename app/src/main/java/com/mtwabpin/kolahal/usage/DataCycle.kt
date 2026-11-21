/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.usage

import java.time.LocalDate
import java.time.ZoneId

/**
 * The month of a mobile data plan (`data.plan`, `data.start-day`): from the day it started again
 * ([start]) to the day it starts again ([next]). `data month`, `usage.data("month")` and the
 * data-month widget all count it here.
 * - The pace is the data used so far, at the same rate for the whole month: by the time gone, not
 *   by whole days, so just after midnight a new day doesn't count as a whole one.
 */
data class DataCycle(val start: LocalDate, val next: LocalDate) {
    fun startMs(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun nextMs(zone: ZoneId): Long = next.atStartOfDay(zone).toInstant().toEpochMilli()

    /** The share of the month gone at [now], from 0 to 1. */
    fun gone(now: Long, zone: ZoneId): Double {
        val from = startMs(zone)
        val whole = (nextMs(zone) - from).toDouble()
        return ((now - from) / whole).coerceIn(0.0, 1.0)
    }

    /** The bytes the whole month comes to at the rate of [used] so far. At the month's very start, [used] itself. */
    fun pace(used: Long, now: Long, zone: ZoneId): Long {
        val g = gone(now, zone)
        // The first minutes say little: a pace from them would be huge.
        return if (g < MIN_GONE) used else (used / g).toLong()
    }

    /** The days until the plan starts again, today counted: 1 on its last day. */
    fun daysLeft(today: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(today, next)

    companion object {
        /** A data plan of [gb] GB (`data.plan`), in bytes as Android counts them (1 GB is 10^9 bytes); null with no plan. */
        fun planBytes(gb: Int): Long? = (gb.toLong() * 1_000_000_000L).takeIf { it > 0 }

        /** Below this share of the month gone, the pace is the data used so far. */
        private const val MIN_GONE = 0.01

        /** The plan's month around [today], for a plan that starts again on [startDay] (1 to 28). */
        fun of(today: LocalDate, startDay: Int): DataCycle {
            val day = startDay.coerceIn(1, 28)
            val thisMonth = today.withDayOfMonth(day)
            val start = if (today.dayOfMonth >= day) thisMonth else thisMonth.minusMonths(1)
            return DataCycle(start, start.plusMonths(1))
        }
    }
}
