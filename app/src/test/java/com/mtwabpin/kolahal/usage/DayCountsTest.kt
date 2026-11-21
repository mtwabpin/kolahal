package com.mtwabpin.kolahal.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Days' counts and a data plan's month: finished days only, and the pace by the time gone. */
class DayCountsTest {
    // A Thursday.
    private val today = LocalDate.of(2026, 11, 19)
    private fun days(vararg counts: Int) =
        counts.withIndex().map { (i, n) -> today.minusDays((counts.size - 1 - i).toLong()) to n }

    /** Today, with only its hours so far, is no part of the average. */
    @Test
    fun theAverageIsOfFinishedDays() {
        assertEquals(50, DayCounts.finishedAverage(days(40, 60, 3), today))
        assertNull(DayCounts.finishedAverage(days(3), today))
    }

    /** The last 7 finished days against the 7 before; not today, and not with fewer than 3 days each. */
    @Test
    fun theTrendIsOfFinishedWeeks() {
        // 7 days of 100, then 7 of 90, then today's 5: 10% fewer, whatever today says.
        val history = days(*IntArray(7) { 100 }, *IntArray(7) { 90 }, 5)
        assertEquals(-10L, DayCounts.trend(history, today))
        assertNull(DayCounts.trend(days(90, 90, 90, 5), today))
    }

    /** Each week, Monday to Sunday, newest first, over its finished days; this week on a Monday has none, and is left out. */
    @Test
    fun weeksAreMondayToSunday() {
        // Mon 9 Nov to Thu 19 Nov.
        val history = days(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 7)
        val weeks = DayCounts.weeks(history, today)
        assertEquals(
            listOf(
                DayCounts.Week(LocalDate.of(2026, 11, 16), 90, 3),
                DayCounts.Week(LocalDate.of(2026, 11, 9), 40, 7)
            ), weeks
        )
        val monday = LocalDate.of(2026, 11, 16)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 9)),
            DayCounts.weeks(listOf(LocalDate.of(2026, 11, 15) to 10, monday to 3), monday)
                .map { it.monday })
    }

    /** The plan's month from its start day, across the turn of the year; the pace by the time gone. */
    @Test
    fun aPlansMonth() {
        val utc = ZoneOffset.UTC
        assertEquals(
            DataCycle(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5)),
            DataCycle.of(today, 5)
        )
        assertEquals(
            DataCycle(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 20)),
            DataCycle.of(LocalDate.of(2026, 1, 3), 20)
        )
        // A start day past 28 is 28, so every month has it.
        assertEquals(28, DataCycle.of(today, 31).start.dayOfMonth)
        val nov = DataCycle.of(today, 1)
        assertEquals(12L, nov.daysLeft(today))
        // Halfway through the month (15 of 30 days), 30 GB: 60 GB at that pace.
        val half = nov.startMs(utc) + 15 * 86_400_000L
        assertEquals(60_000_000_000L, nov.pace(30_000_000_000L, half, utc))
        // Ten minutes in, the pace is what is used so far: it would say little.
        assertEquals(1_000L, nov.pace(1_000L, nov.startMs(utc) + 600_000L, utc))
    }
}
