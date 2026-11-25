/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The words and the dots of a countdown that counts days on the calendar: `style = "dots"` (a dot
 * a day) and `style = "page"` (the date as a calendar page). Pure, so it is unit tested. Times are
 * in seconds, as a script gives them.
 * - A day is the phone's day: from midnight to midnight where the phone is. On 24 November, a
 *   moment on 20 December is 26 days away, at any hour of either day.
 * - The words change at midnight, and on the moment's day once the moment has passed ([reached]).
 */
internal object CountdownDays {
    const val DOTS = "dots"
    const val PAGE = "page"

    /** Whether a countdown of [style] counts days on the calendar and draws them ([DOTS], [PAGE]). */
    fun counts(style: String?): Boolean = style == DOTS || style == PAGE

    /** At most this many dots. A longer wait has a dot a week; one of more weeks than this has no dots. */
    const val MAX_DOTS = 60

    private fun day(seconds: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()

    /** The days from [now]'s day to the day of [to]: 26 from 24 November to 20 December, 0 on the day, -3 three days after. */
    fun daysLeft(to: Long, now: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(day(now, zone), day(to, zone))

    /**
     * The dots of a wait: [count] dots, a day each, or a week each when [week]. The first [gone]
     * are over. [today] is the dot of today, or null when today is not one of them: the wait is
     * over, or it starts later.
     */
    data class Dots(val count: Int, val gone: Int, val today: Int?, val week: Boolean)

    /**
     * The dots of a countdown to [to] at [now], in [zone].
     * - A dot is a day before the moment's day. With a start ([from]) the dots run from its day,
     *   and the days over stay as dots that went out; without one they are the days left, today's
     *   first, and one fewer each day.
     * - Over [MAX_DOTS] days, a dot is a week, from the first day on; over [MAX_DOTS] weeks, null:
     *   the card has the number alone. Null too with no day to draw.
     */
    fun dots(to: Long, from: Long?, now: Long, zone: ZoneId): Dots? {
        val today = day(now, zone)
        val start = from?.let { day(it, zone) } ?: today
        val total = ChronoUnit.DAYS.between(start, day(to, zone))
        if (total <= 0) return null
        // Today's place among the days, from 0: below 0 before the start, at the total or over once the wait is done.
        val at = ChronoUnit.DAYS.between(start, today)
        val week = total > MAX_DOTS
        val per = if (week) 7L else 1L
        val count = (total + per - 1) / per
        if (count > MAX_DOTS) return null
        val gone = (at.coerceIn(0, total) / per).toInt()
        val todayDot = if (at in 0 until total) (at / per).toInt() else null
        return Dots(count.toInt(), if (todayDot == null && at >= total) count.toInt() else gone, todayDot, week)
    }

    /**
     * The number and its unit, as a dots card writes them: "26" "days left", "1" "day left", on
     * the day [reached] or "Today" with no unit, then "3" "days ago". After the moment on its day it
     * is [reached] once the moment has passed; before it, "Today". Without [left] the unit is "days"
     * and "day", as Poster's note has it.
     */
    fun count(to: Long, now: Long, zone: ZoneId, reached: String?, left: Boolean = true): WidgetWords.Count {
        val days = daysLeft(to, now, zone)
        val unit = if (days == 1L) "day" else "days"
        return when {
            days > 0 -> WidgetWords.Count("$days", if (left) "$unit left" else unit, ticks = false)
            days == 0L -> WidgetWords.Count(onTheDay(to, now, reached), null, ticks = false)
            else -> WidgetWords.Count("${-days}", if (days == -1L) "day ago" else "days ago", ticks = false)
        }
    }

    /** What a card says on the moment's day: [reached] once the moment has passed, else "Today". */
    private fun onTheDay(to: Long, now: Long, reached: String?): String =
        if (now >= to && reached != null) reached else "Today"

    /**
     * When the moment is, from today, as a page says it under the date: "In 26 days", "Tomorrow",
     * on the day "Today" (or [reached] once it has passed), then "Yesterday" and "3 days ago".
     */
    fun inWords(to: Long, now: Long, zone: ZoneId, reached: String?): String {
        val left = daysLeft(to, now, zone)
        return when {
            left > 1 -> "In $left days"
            left == 1L -> "Tomorrow"
            left == 0L -> onTheDay(to, now, reached)
            left == -1L -> "Yesterday"
            else -> "${-left} days ago"
        }
    }

    /**
     * A calendar page of the moment: the [month] on its band ("DEC"), with the [year] when it is not
     * this one ("DEC 2027"), the [day] large ("20"), and the [weekday] under it ("Sunday"). On the
     * moment's day the weekday's place has the time of the moment ("17:25"), unless it is midnight.
     * [left] says when it is ([inWords]).
     */
    data class Page(val month: String, val day: String, val weekday: String, val left: String)

    /** The page of a countdown to [to], seen at [now] in [zone]. Times are written as [clock24] says. */
    fun page(
        to: Long,
        now: Long,
        zone: ZoneId,
        reached: String?,
        clock24: Boolean,
        locale: Locale = Locale.getDefault()
    ): Page {
        val at = Instant.ofEpochSecond(to).atZone(zone)
        val thisYear = day(now, zone).year
        val month = at.format(DateTimeFormatter.ofPattern("MMM", locale)).uppercase(locale).trimEnd('.')
        val band = if (at.year == thisYear) month else "$month ${at.year}"
        val onTheDay = daysLeft(to, now, zone) == 0L
        val midnight = at.toLocalTime() == java.time.LocalTime.MIDNIGHT
        val under = if (onTheDay && !midnight) at.format(TimeFormats.clockFormat(clock24, locale))
        else at.format(DateTimeFormatter.ofPattern("EEEE", locale))
        return Page(band, at.dayOfMonth.toString(), under, inWords(to, now, zone, reached))
    }

    /**
     * What a screen reader says for a countdown of [style] [DOTS] or [PAGE]: when it is and the
     * day, "26 days left, Sunday 20 December" or "In 26 days, Sunday 20 December", with the year
     * when it is not this one. Null for the other styles, which say it their way.
     */
    fun said(spec: WidgetSpec.Countdown, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String? {
        if (!counts(spec.style)) return null
        val at = Instant.ofEpochSecond(spec.to).atZone(zone)
        val pattern = if (at.year == day(now, zone).year) "EEEE d MMMM" else "EEEE d MMMM yyyy"
        val date = at.format(DateTimeFormatter.ofPattern(pattern, locale))
        val left = if (spec.style == DOTS) count(spec.to, now, zone, spec.reached).text
        else inWords(spec.to, now, zone, spec.reached)
        return "$left, $date"
    }

    /** The words under a dots card's number when a dot is a week, after the label if there is one. */
    const val A_DOT_A_WEEK = "A dot a week"

    /**
     * Where [count] dots go in a box [width] by [height] (in any unit): [columns] by [rows], [pitch]
     * apart, the grid as large as it fits and a pitch of at most [most]. Of two grids with the same
     * pitch, the one with fewer rows.
     */
    data class Grid(val columns: Int, val rows: Int, val pitch: Float)

    fun grid(count: Int, width: Float, height: Float, most: Float): Grid {
        if (count <= 0 || width <= 0f || height <= 0f) return Grid(max(count, 0), 0, 0f)
        var best = Grid(count, 1, 0f)
        for (columns in 1..count) {
            val rows = (count + columns - 1) / columns
            val pitch = min(min(width / columns, height / rows), most)
            // A pitch as large, with fewer rows: the grid stays wide, as the mockup draws it.
            if (pitch > best.pitch || (pitch == best.pitch && rows < best.rows)) best = Grid(columns, rows, pitch)
        }
        return best
    }

    /**
     * How far across a box [width] wide dot [i] of [grid] sits, at its middle: the grid centered,
     * the days in reading order. Right to left ([rtl]) the first day is at the right, as the habit's
     * dots, the sky and the timeline go.
     */
    fun dotX(i: Int, grid: Grid, width: Float, rtl: Boolean): Float {
        val x = (width - grid.columns * grid.pitch) / 2 + (i % grid.columns + 0.5f) * grid.pitch
        return if (rtl) width - x else x
    }
}
