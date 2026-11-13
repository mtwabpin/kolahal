package com.mtwabpin.kolahal.data

import java.time.LocalDate
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

    /** A formatter for [DAY] in [locale]. */
    fun dayFormat(locale: Locale = Locale.getDefault()): DateTimeFormatter = DateTimeFormatter.ofPattern(DAY, locale)

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
}
