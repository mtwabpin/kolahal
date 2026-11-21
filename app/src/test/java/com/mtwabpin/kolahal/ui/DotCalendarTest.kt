package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.ui.DotCalendar.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** Where a chart's dots go (`style = "dots"`): a calendar of days, the last point today. */
class DotCalendarTest {
    /** Friday 20 November 2026. */
    private val friday = LocalDate.of(2026, 11, 20).dayOfWeek

    /** Today's column on [day] where [locale]'s weeks start. */
    private fun today(day: DayOfWeek, locale: Locale) =
        DotCalendar.column(day, DotCalendar.firstDay(locale))

    @Test
    fun aWeekStartsOnTheLocalesFirstDay() {
        assertEquals(DayOfWeek.MONDAY, DotCalendar.firstDay(Locale.UK))
        assertEquals(DayOfWeek.SUNDAY, DotCalendar.firstDay(Locale.US))
        assertEquals(
            listOf("M", "T", "W", "T", "F", "S", "S"),
            DotCalendar.initials(DayOfWeek.MONDAY, Locale.UK)
        )
        assertEquals(
            listOf("S", "M", "T", "W", "T", "F", "S"),
            DotCalendar.initials(DayOfWeek.SUNDAY, Locale.US)
        )
    }

    @Test
    fun todayIsInItsWeekdaysColumn() {
        assertEquals(4, today(friday, Locale.UK))
        assertEquals(5, today(friday, Locale.US))
        assertEquals(0, today(DayOfWeek.MONDAY, Locale.UK))
        assertEquals(6, today(DayOfWeek.SUNDAY, Locale.UK))
        assertEquals(0, today(DayOfWeek.SUNDAY, Locale.US))
        assertEquals(6, today(DayOfWeek.SATURDAY, Locale.US))
    }

    /**
     * Ten days up to Friday, in weeks from Monday: the first is Wednesday of the first week, and
     * today is Friday of the second. Saturday and Sunday are still to come.
     */
    @Test
    fun daysInWeeksFromMonday() {
        val column = today(friday, Locale.UK)
        assertEquals(2, DotCalendar.weeks(10, column))
        assertEquals(
            listOf(
                Cell(0, 0, 2),
                Cell(1, 0, 3),
                Cell(2, 0, 4),
                Cell(3, 0, 5),
                Cell(4, 0, 6),
                Cell(5, 1, 0),
                Cell(6, 1, 1),
                Cell(7, 1, 2),
                Cell(8, 1, 3),
                Cell(9, 1, 4)
            ),
            DotCalendar.cells(10, column, rows = 2),
        )
        assertEquals(listOf(5, 6), DotCalendar.later(column).toList())
    }

    /** The same ten days in weeks from Sunday: each day is one column further on, and only Saturday is to come. */
    @Test
    fun daysInWeeksFromSunday() {
        val column = today(friday, Locale.US)
        assertEquals(2, DotCalendar.weeks(10, column))
        assertEquals(
            listOf(
                Cell(0, 0, 3),
                Cell(1, 0, 4),
                Cell(2, 0, 5),
                Cell(3, 0, 6),
                Cell(4, 1, 0),
                Cell(5, 1, 1),
                Cell(6, 1, 2),
                Cell(7, 1, 3),
                Cell(8, 1, 4),
                Cell(9, 1, 5)
            ),
            DotCalendar.cells(10, column, rows = 2),
        )
        assertEquals(listOf(6), DotCalendar.later(column).toList())
    }

    /**
     * 35 days need six weeks on a Friday, and five on the last day of the week. In five weeks the
     * oldest days that have no room are left out: the first row starts on its first day.
     */
    @Test
    fun fiveWeeksShowTheNewestDays() {
        val monday = today(friday, Locale.UK)
        assertEquals(6, DotCalendar.weeks(35, monday))
        val five = DotCalendar.cells(35, monday, rows = 5)
        assertEquals(33, five.size)
        assertEquals(Cell(2, 0, 0), five.first())
        assertEquals(Cell(34, 4, 4), five.last())
        assertEquals(Cell(33, 4, 3), five[five.lastIndex - 1])

        val sunday = today(friday, Locale.US)
        val fromSunday = DotCalendar.cells(35, sunday, rows = 5)
        assertEquals(34, fromSunday.size)
        assertEquals(Cell(1, 0, 0), fromSunday.first())
        assertEquals(Cell(34, 4, 5), fromSunday.last())

        // A Sunday ends the week from Monday: 35 days are exactly five weeks, with nothing to come.
        val end = today(DayOfWeek.SUNDAY, Locale.UK)
        assertEquals(5, DotCalendar.weeks(35, end))
        assertEquals(Cell(0, 0, 0), DotCalendar.cells(35, end, rows = 5).first())
        assertTrue(DotCalendar.later(end).isEmpty())
        // On a Sunday a week from Sunday has only today in its last row.
        assertEquals(6, DotCalendar.weeks(35, today(DayOfWeek.SUNDAY, Locale.US)))
        assertEquals(
            listOf(Cell(34, 0, 0)),
            DotCalendar.cells(35, today(DayOfWeek.SUNDAY, Locale.US), rows = 1)
        )
    }

    @Test
    fun aFewPointsNeedAWeekOrTwo() {
        assertEquals(1, DotCalendar.weeks(0, 3))
        assertEquals(1, DotCalendar.weeks(1, 0))
        assertEquals(1, DotCalendar.weeks(5, 4))
        assertEquals(2, DotCalendar.weeks(6, 4))
        assertEquals(listOf(Cell(0, 0, 3), Cell(1, 0, 4)), DotCalendar.cells(2, 4, rows = 1))
        assertEquals(emptyList<Cell>(), DotCalendar.cells(0, 4, rows = 1))
    }

    /** A strip is the last seven days, today at the end. */
    @Test
    fun aStripHasTheLastSevenDays() {
        assertEquals((28..34).mapIndexed { column, i -> Cell(i, 0, column) }, DotCalendar.strip(35))
        assertEquals(listOf(Cell(0, 0, 4), Cell(1, 0, 5), Cell(2, 0, 6)), DotCalendar.strip(3))
        assertEquals(emptyList<Cell>(), DotCalendar.strip(0))
    }

    /** Points that are all 0 or 1 are days done and missed. Any other number makes them values. */
    @Test
    fun yesOrNoPointsAreToldApart() {
        assertTrue(DotCalendar.yesNo(listOf(1f, 0f, 1f, 1f)))
        assertTrue("no day done yet", DotCalendar.yesNo(List(35) { 0f }))
        assertTrue("every day done", DotCalendar.yesNo(List(35) { 1f }))
        assertTrue(DotCalendar.yesNo(listOf(1f, -0f)))
        assertFalse(DotCalendar.yesNo(listOf(1f, 2f, 0f)))
        assertFalse(DotCalendar.yesNo(listOf(0.5f, 1f)))
        assertFalse(DotCalendar.yesNo(listOf(-1f, 0f)))
        assertFalse(DotCalendar.yesNo(listOf(42f)))
        assertFalse("no points", DotCalendar.yesNo(emptyList()))
    }
}
