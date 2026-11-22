/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Where the dots of a chart with `style = "dots"` go: one dot for each day. Pure, so it is unit
 * tested.
 * - The last point is today, and the point before it is the day before.
 * - As a calendar ([cells]): a row for each week, in 7 columns, with the locale's first day of the
 *   week first. Today is in its own column of the last row, and the oldest point is furthest up.
 * - As a strip ([strip]): the last seven points in one row, today at the end.
 * A column counts from the start edge: a card that reads right to left draws it from the right.
 */
internal object DotCalendar {
    /** A row of the calendar has a column for each day of the week. */
    const val DAYS = 7

    /** A point's place: its [index] in the points, its [row] from the top and its [column] from the start. */
    data class Cell(val index: Int, val row: Int, val column: Int)

    /**
     * True when every point is 0 or 1, as a habit's days are: a 1 is a day done and a 0 a day
     * missed. No points is no such list.
     */
    fun yesNo(points: List<Float>): Boolean =
        points.isNotEmpty() && points.all { it == 0f || it == 1f }

    /** The first day of the week where [locale] is: Monday in most places, Sunday in the US. */
    fun firstDay(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** The column of [day] in a week that starts on [first]: 0 to 6. */
    fun column(day: DayOfWeek, first: DayOfWeek): Int = (day.value - first.value + DAYS) % DAYS

    /** The first letter of each day of the week in [locale], from [first] on: "M", "T", "W", … */
    fun initials(first: DayOfWeek, locale: Locale): List<String> =
        (0 until DAYS).map { first.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }

    /** The rows that [count] points need to all show when today is in column [today]: at least 1. */
    fun weeks(count: Int, today: Int): Int = maxOf(1, (count + DAYS - 1 - today + DAYS - 1) / DAYS)

    /**
     * The places of the points of [count] that fit in [rows] weeks, when today is in column
     * [today]: the newest ones, the oldest first. An older point has no place, so it isn't drawn.
     */
    fun cells(count: Int, today: Int, rows: Int): List<Cell> {
        // Today's place, counted along the rows from the top start.
        val end = (rows - 1) * DAYS + today
        val shown = minOf(count, end + 1).coerceAtLeast(0)
        return (count - shown until count).map { i ->
            val place = end - (count - 1 - i)
            Cell(i, place / DAYS, place % DAYS)
        }
    }

    /** The days after today in this week: the columns of the last row that have no point yet. */
    fun later(today: Int): IntRange = today + 1 until DAYS

    /** The places of the last seven points of [count] in one row, today in the last column. */
    fun strip(count: Int): List<Cell> {
        val shown = minOf(count, DAYS).coerceAtLeast(0)
        return (count - shown until count).map { i -> Cell(i, 0, DAYS - (count - i)) }
    }
}
