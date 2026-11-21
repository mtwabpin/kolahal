package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MonthCalendarTest {
    private val today = LocalDate.of(2026, 10, 24)

    @Test
    fun thisMonthTheComingMonthOrAGivenOne() {
        assertEquals(YearMonth.of(2026, 10), MonthCalendar.parse("", today, Locale.UK))
        assertEquals(YearMonth.of(2026, 12), MonthCalendar.parse("dec", today, Locale.UK))
        assertEquals(YearMonth.of(2027, 1), MonthCalendar.parse("January", today, Locale.UK))
        assertEquals(YearMonth.of(2026, 10), MonthCalendar.parse("10", today, Locale.UK))
        assertEquals(YearMonth.of(2028, 3), MonthCalendar.parse("3 2028", today, Locale.UK))
        assertEquals(YearMonth.of(2027, 12), MonthCalendar.parse("2027-12", today, Locale.UK))
        assertNull(MonthCalendar.parse("nope", today, Locale.UK))
        assertNull(MonthCalendar.parse("dec 2027 extra", today, Locale.UK))
        assertNull(MonthCalendar.parse("2027-13", today, Locale.UK))
    }

    @Test
    fun monthNamesComplete() {
        assertEquals(listOf("january", "june", "july"), MonthCalendar.suggest("J", Locale.UK))
        assertTrue(MonthCalendar.suggest("october", Locale.UK).isEmpty())
    }

    @Test
    fun theMonthAsLinesWithTodayMarked() {
        val lines = MonthCalendar.lines(YearMonth.of(2026, 10), today, Locale.UK) { "[$it]" }
        assertEquals("    October 2026", lines[0])
        assertEquals("Mo Tu We Th Fr Sa Su", lines[1])
        // October 2026 starts on a Thursday.
        assertEquals("          1  2  3  4", lines[2])
        assertEquals(" 5  6  7  8  9 10 11", lines[3])
        assertEquals("19 20 21 22 23 [24] 25", lines[5])
        assertEquals("26 27 28 29 30 31", lines[6])
        assertEquals(7, lines.size)
        // Weeks start on Sunday in the US.
        assertEquals(
            "Su Mo Tu We Th Fr Sa",
            MonthCalendar.lines(YearMonth.of(2026, 10), today, Locale.US)[1]
        )
    }
}
