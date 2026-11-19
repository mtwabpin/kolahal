package com.mtwabpin.kolahal.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How Kolahal writes a time of day and a day, in one place:
 * - the clock: `22:40`, or `10:40 PM` without the 24-hour setting ([Keys.CLOCK_24H]);
 * - the day: `Tue 20 Oct`;
 * - a day by its name when it is near: `today`, `yesterday`.
 *
 * [clock] and [DAY] are patterns. [DateTimeFormatter] and [java.text.SimpleDateFormat] read them
 * the same way, so code that needs a longer pattern (`Tue 20 Oct, `) builds on them.
 */
object TimeFormats {
    /** The pattern for a day: `Tue 20 Oct`. */
    const val DAY = "EEE d MMM"

    /** The pattern for a time of day: `22:40` with [clock24], `10:40 PM` without. */
    fun clock(clock24: Boolean): String = if (clock24) "HH:mm" else "h:mm a"

    /** A formatter for [clock] in [locale]. */
    fun clockFormat(clock24: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter = DateTimeFormatter.ofPattern(clock(clock24), locale)

    /**
     * The pattern for the time beside a log line: `22:40`, or `10:40` without the 24-hour setting.
     * No AM or PM, as a line is short and the day is above it. The next alarm uses it too.
     */
    fun lineClock(clock24: Boolean): String = if (clock24) "HH:mm" else "h:mm"

    /** A formatter for [DAY] in [locale]. */
    fun dayFormat(locale: Locale = Locale.getDefault()): DateTimeFormatter = DateTimeFormatter.ofPattern(DAY, locale)

    /** A formatter for a day of [year] seen in [thisYear]: [DAY], with the year when they differ (`Thu 2 Dec 2027`). */
    fun dayFormat(year: Int, thisYear: Int, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (year == thisYear) DAY else "$DAY yyyy", locale)

    /**
     * When something is due, seen from [now]: `18:30` today, `tomorrow 09:00`, `Fri 23 Oct 08:00`,
     * or `Thu 2 Dec 2027 08:00` in another year. With [capital], `Tomorrow`, at the start of a line.
     * [at] and [now] are in milliseconds. `reminders` and the reminders card both say it this way.
     */
    fun whenLabel(at: Long, now: Long, zone: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault(), capital: Boolean = false): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = clockFormat(clock24, locale).format(t)
        return when (t.toLocalDate()) {
            today -> time
            today.plusDays(1) -> (if (capital) "Tomorrow " else "tomorrow ") + time
            else -> t.format(dayFormat(t.year, today.year, locale)) + " " + time
        }
    }

    /**
     * [day] as seen from [today]: `today`, `yesterday`, or the day itself (`Tue 20 Oct`).
     * With [capital], the two words start with a capital letter: `Today`, `Yesterday`. The day
     * itself stays as [locale] writes it.
     */
    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault(), capital: Boolean = false): String = when (day) {
        today -> if (capital) "Today" else "today"
        today.minusDays(1) -> if (capital) "Yesterday" else "yesterday"
        else -> day.format(dayFormat(locale))
    }

    /** A time span as people say it: `4h 12m`, or `38m`. Under a minute is `0m`. */
    fun duration(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
}
