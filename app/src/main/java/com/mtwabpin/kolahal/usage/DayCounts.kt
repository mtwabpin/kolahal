/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.usage

import java.time.LocalDate

/**
 * What a run of days' counts say (the unlocks of each day, oldest first): their average, how the
 * last week compares with the one before, and each week's average. `unlocks` shows them.
 * - Only finished days count: today has only its hours so far, so at 8:00 it would pull every
 *   average down, and every morning the week would seem to drop.
 */
object DayCounts {
    /** The average over the days before [today], or null with none. */
    fun finishedAverage(days: List<Pair<LocalDate, Int>>, today: LocalDate): Int? {
        val finished = days.filter { it.first.isBefore(today) }
        return if (finished.isEmpty()) null else finished.sumOf { it.second } / finished.size
    }

    /** How many days [from] .. [to] days before [today] are in [days], and their average; null under [MIN_DAYS] days. */
    private fun average(days: List<Pair<LocalDate, Int>>, today: LocalDate, from: Long, to: Long): Double? {
        val inRange = days.filter { (d, _) -> d in today.minusDays(to)..today.minusDays(from) }
        return if (inRange.size < MIN_DAYS) null else inRange.sumOf { it.second }.toDouble() / inRange.size
    }

    /** At least this many days in each week for [trend] to say anything. */
    const val MIN_DAYS = 3

    /**
     * How the 7 finished days before [today] compare with the 7 before them, in percent of a day's
     * average: -6 for 6% fewer. Null when either has fewer than [MIN_DAYS] days, or the earlier had none.
     */
    fun trend(days: List<Pair<LocalDate, Int>>, today: LocalDate): Long? {
        val last = average(days, today, 1, 7) ?: return null
        val before = average(days, today, 8, 14)?.takeIf { it > 0 } ?: return null
        return Math.round((last - before) / before * 100)
    }

    /** A week, Monday to Sunday: its Monday, its average over its finished days, and how many there are. */
    data class Week(val monday: LocalDate, val average: Int, val days: Int)

    /** The Monday of [day]'s week. */
    fun monday(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

    /** Each week of [days], the newest first, with its average over its finished days. A week with none (this week, on a Monday) is left out. */
    fun weeks(days: List<Pair<LocalDate, Int>>, today: LocalDate): List<Week> =
        days.filter { it.first.isBefore(today) }.groupBy { monday(it.first) }.toSortedMap(compareByDescending { it })
            .map { (start, list) -> Week(start, list.sumOf { it.second } / list.size, list.size) }
}
