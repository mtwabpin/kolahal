package com.mtwabpin.kolahal.productivity

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month

/** Pure parsing of the times and durations that the user types after `remind`, `timer` and `alarm`. */
object TimeSpec {
    private val DURATION = Regex("^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m(?:in)?)?(?:(\\d+)s)?$")
    private val CLOCK = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a|p)?$")

    /**
     * Converts "10m", "2h", "1h30m", "90s" or "1d" to seconds. A bare number counts as minutes only
     * when [bareMinutes] is true (`timer 5`), because `remind 2 tickets` must not mean two minutes.
     */
    fun parseDuration(raw: String, bareMinutes: Boolean = false): Long? {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) return if (bareMinutes) s.toLongOrNull()?.takeIf { it in 1..MAX_DURATION_S / 60 }?.times(60) else null
        val m = DURATION.find(s) ?: return null
        val (d, h, min, sec) = m.destructured
        if (d.isEmpty() && h.isEmpty() && min.isEmpty() && sec.isEmpty()) return null
        // Each part is at most [MAX_DURATION_S]. Bigger values overflowed into a few hours, or later
        // gave a time that a date can't hold.
        fun part(digits: String, unit: Long) = if (digits.isEmpty()) 0L else digits.toLongOrNull()?.takeIf { it <= MAX_DURATION_S / unit }?.times(unit)
        val total = (part(d, 86_400) ?: return null) + (part(h, 3_600) ?: return null) +
            (part(min, 60) ?: return null) + (part(sec, 1) ?: return null)
        return total.takeIf { it in 1..MAX_DURATION_S }
    }

    /** The longest duration accepted: a hundred years. */
    const val MAX_DURATION_S = 100L * 366 * 86_400

    /** Parses "18:30", "9:05", "6pm" or "6:30pm". A bare hour ("9") is accepted only when [allowBareHour] is true. */
    fun parseClock(raw: String, allowBareHour: Boolean = false): LocalTime? {
        val s = raw.trim().lowercase()
        val m = CLOCK.find(s) ?: return null
        val (hRaw, minRaw, ampm) = m.destructured
        if (minRaw.isEmpty() && ampm.isEmpty() && !allowBareHour) return null
        var hour = hRaw.toInt()
        val minute = minRaw.toIntOrNull() ?: 0
        if (minute > 59) return null
        if (ampm.isNotEmpty()) {
            if (hour !in 1..12) return null
            if (ampm.startsWith("p") && hour != 12) hour += 12
            if (ampm.startsWith("a") && hour == 12) hour = 0
        }
        if (hour > 23) return null
        return LocalTime.of(hour, minute)
    }

    /** A point in time, and how many leading tokens described it. The other tokens are the reminder text. */
    data class Parsed(val at: LocalDateTime, val consumed: Int)

    /**
     * Returns the duration in seconds when [tokens] start with one (`2h`, `in 10m`), else null.
     * Callers add it to the real clock, because across a DST change, wall-clock math on [Parsed.at]
     * is one hour off.
     */
    fun relativeSeconds(tokens: List<String>): Long? {
        val first = tokens.getOrNull(0)?.lowercase() ?: return null
        if (first == "at") return null
        return parseDuration((if (first == "in") tokens.getOrNull(1) else first)?.lowercase() ?: return null)
    }

    /** Parses a weekday: "fri", "friday" (at least three letters). */
    private fun weekday(token: String): DayOfWeek? {
        val t = token.lowercase()
        if (t.length < 3) return null
        return DayOfWeek.entries.firstOrNull { it.name.lowercase().startsWith(t) }
    }

    /** Parses a month: "dec", "december" (at least three letters). */
    private fun month(token: String): Month? {
        val t = token.lowercase().trimEnd('.')
        if (t.length < 3) return null
        return Month.entries.firstOrNull { it.name.lowercase().startsWith(t) }
    }

    /** Parses a day of the month: "3", "3rd", "21st". */
    private fun dayOfMonth(token: String): Int? =
        token.lowercase().removeSuffix("st").removeSuffix("nd").removeSuffix("rd").removeSuffix("th")
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it in 1..31 }

    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val YEAR = Regex("^\\d{4}$")

    private val LEAP_YEAR: LocalDate = LocalDate.of(2028, 1, 1)
    private val DAY_NUMBER = Regex("^\\d+(st|nd|rd|th)?$", RegexOption.IGNORE_CASE)

    /**
     * True when the tokens from the third one look like a date, valid or not: a month and a number in
     * either order, or `2027-12-03`.
     */
    private fun looksLikeDate(tokens: List<String>): Boolean {
        val a = tokens.getOrNull(2) ?: return false
        if (ISO_DATE.matches(a)) return true
        val b = tokens.getOrNull(3) ?: return false
        return (month(a) != null && DAY_NUMBER.matches(b)) || (DAY_NUMBER.matches(a) && month(b) != null)
    }

    /** A date read from the tokens, how many tokens it used, and whether it gave a year. */
    private data class Day(val date: LocalDate, val used: Int, val yearGiven: Boolean)

    /** Parses `dec 3`, `3 dec`, `december 3rd`, any of these with a year after it (`dec 3 2027`), or `2027-12-03`. */
    private fun parseDate(tokens: List<String>, i: Int, today: LocalDate): Day? {
        val a = tokens.getOrNull(i) ?: return null
        if (ISO_DATE.matches(a)) return runCatching { Day(LocalDate.parse(a), 1, true) }.getOrNull()
        val b = tokens.getOrNull(i + 1) ?: return null
        val (m, d) = month(a)?.let { m -> dayOfMonth(b)?.let { m to it } }
            ?: dayOfMonth(a)?.let { d -> month(b)?.let { it to d } }
            ?: return null
        val year = tokens.getOrNull(i + 2)?.takeIf { YEAR.matches(it) }?.toInt()
        // Without a year, use this year, or the next year that has the date (29 February).
        val date = if (year != null) runCatching { LocalDate.of(year, m, d) }.getOrNull() else nextWith(today.year, m, d)
        return Day(date ?: return null, if (year != null) 3 else 2, year != null)
    }

    /** Returns [month] [day] in [from], or in the first later year that has that day. Null when no year does. */
    private fun nextWith(from: Int, month: java.time.Month, day: Int): LocalDate? =
        (from..from + 8).firstNotNullOfOrNull { y -> runCatching { LocalDate.of(y, month, day) }.getOrNull() }

    /** Default time for a day given without a time ("tomorrow", "fri"). */
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * Parses the start of [tokens]: `10m`, `in 2h`, `1h30m`, `18:30`, `at 6pm`, `tomorrow 9:00`,
     * `today 17:00`, `fri 8am`, `dec 3`, `on 3 dec 2027 at 10:00`, `2027-12-03`.
     * If the time has already passed:
     * - a clock time means tomorrow
     * - a weekday means next week
     * - a date without a year means next year
     *
     * Returns null when the tokens don't start with a time.
     */
    fun parseWhen(tokens: List<String>, now: LocalDateTime): Parsed? {
        var i = 0
        val first = tokens.getOrNull(0)?.lowercase() ?: return null
        if (first == "in" || first == "at" || first == "on") i = 1
        val head = tokens.getOrNull(i)?.lowercase() ?: return null

        if (first != "at" && first != "on") parseDuration(head)?.let { return Parsed(now.plusSeconds(it).withNano(0), i + 1) }
        if (first == "in") return null

        parseDate(tokens, i, now.toLocalDate())?.let { day ->
            var j = i + day.used
            val at = tokens.getOrNull(j)?.lowercase() == "at"
            if (at) j++
            // Accept a bare hour only after `at`: `dec 3 2 tickets` is about two tickets.
            val time = tokens.getOrNull(j)?.let { parseClock(it, allowBareHour = at) }
            var whenAt = day.date.atTime(time ?: DEFAULT_TIME)
            // If the date has passed this year, use the next year that has the day (29 February
            // returns after four years).
            if (!day.yearGiven && !whenAt.isAfter(now)) {
                whenAt = nextWith(day.date.year + 1, day.date.month, day.date.dayOfMonth)?.atTime(whenAt.toLocalTime()) ?: return null
            }
            if (!whenAt.isAfter(now)) return null
            return Parsed(whenAt, if (time != null) j + 1 else i + day.used)
        }

        val weekday = weekday(head)
        val day: LocalDate? = when (head) {
            "today" -> now.toLocalDate()
            "tomorrow", "tmrw", "tmr" -> now.toLocalDate().plusDays(1)
            // Today's weekday name means today, if its time is still ahead.
            else -> weekday?.let { dow ->
                var d = now.toLocalDate()
                while (d.dayOfWeek != dow) d = d.plusDays(1)
                d
            }
        }
        if (day != null) {
            var j = i + 1
            if (tokens.getOrNull(j)?.lowercase() == "at") j++
            val time = tokens.getOrNull(j)?.let { parseClock(it, allowBareHour = true) }
            var at = day.atTime(time ?: DEFAULT_TIME)
            if (weekday != null && !at.isAfter(now)) at = at.plusWeeks(1)
            // "today 9:00" typed at 10:00 is in the past. Refuse it instead of firing at once.
            if (!at.isAfter(now)) return null
            return Parsed(at, if (time != null) j + 1 else i + 1)
        }

        val time = parseClock(head, allowBareHour = first == "at") ?: return null
        var at = now.toLocalDate().atTime(time)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return Parsed(at, i + 1)
    }

    /** A repeating reminder: its [Repeat] rule, its first time, and how many leading tokens described it. */
    data class Repeating(val rule: String, val at: LocalDateTime, val consumed: Int)

    /**
     * Parses `every day 8:00`, `every weekday`, `every weekend`, `every mon,thu 19:00`,
     * `every month 1st`, `every year mar 14`. Each can have a time, with or without `at`. The default
     * time is 9:00. Returns null when [tokens] don't start with `every` and one of these.
     */
    fun parseRepeat(tokens: List<String>, now: LocalDateTime): Repeating? {
        if (tokens.getOrNull(0)?.lowercase() != "every") return null
        val head = tokens.getOrNull(1)?.lowercase() ?: return null
        val today = now.toLocalDate()
        var j = 2
        val rule = when (head) {
            "day", "days", "daily" -> "day"
            "weekday", "weekdays" -> "weekdays"
            "weekend", "weekends" -> "weekend"
            "month" -> {
                val n = tokens.getOrNull(2)?.let(::dayOfMonth)
                if (n != null) j = 3
                "month:${n ?: today.dayOfMonth}"
            }
            "year" -> {
                // Parse in a leap year, so 29 February is a valid date. Other years use the 28th ([Repeat]).
                val day = parseDate(tokens, 2, LEAP_YEAR)
                // It looks like a date but isn't valid here (`feb 30`, or `mar 14 2027`, because it
                // repeats every year). Treat it as a mistake, not as the reminder's words.
                if (day?.yearGiven == true || (day == null && looksLikeDate(tokens))) return null
                if (day != null) j = 2 + day.used
                val d = day?.date ?: today
                // Use plain digits in any phone language, because the rule is stored and read back.
                String.format(java.util.Locale.ROOT, "year:%02d-%02d", d.monthValue, d.dayOfMonth)
            }
            else -> head.split(',').map { weekday(it) ?: return null }.distinct().sorted()
                .joinToString(",") { it.name.lowercase().take(3) }
        }
        val at = tokens.getOrNull(j)?.lowercase() == "at"
        if (at) j++
        val time = tokens.getOrNull(j)?.let { parseClock(it, allowBareHour = at) }
        if (time != null) j++ else if (at) return null
        return Repeating(rule, Repeat.next(rule, time ?: DEFAULT_TIME, now), j)
    }

    private val SMALL_HOURS_END: LocalTime = LocalTime.of(6, 0)

    /**
     * True for a time before 6:00. If the user typed the time, it is worth asking: `remind tomorrow 2 tickets`
     * more likely means two tickets than 2 AM.
     */
    fun inTheSmallHours(at: LocalDateTime): Boolean = at.toLocalTime().isBefore(SMALL_HOURS_END)

    data class Reading(val at: LocalDateTime, val text: String)

    /**
     * The other way to read [tokens], whose first [consumed] tokens gave the time. A bare hour after a
     * day word becomes the first word of the text, and the time becomes the default time. Returns null
     * for any other kind of time.
     */
    fun otherReading(tokens: List<String>, consumed: Int, now: LocalDateTime): Reading? {
        val hour = tokens.getOrNull(consumed - 1) ?: return null
        if (!hour.all(Char::isDigit) || tokens.getOrNull(consumed - 2)?.lowercase() == "at") return null
        val at = parseWhen(tokens.take(consumed - 1) + DEFAULT_TIME.toString(), now)?.at ?: return null
        return Reading(at, tokens.drop(consumed - 1).joinToString(" "))
    }

    /** Words for [at] and [text] that mean the same whenever they run. A "tomorrow" would change at midnight. */
    fun exactWords(at: LocalDateTime, text: String): String = "${at.toLocalDate()} ${at.toLocalTime()} $text"

    /** Formats seconds: 7800 -> "2h 10m", 45 -> "45s", 90000 -> "1d 1h". */
    fun formatDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val d = s / 86_400
        val h = s % 86_400 / 3_600
        val m = s % 3_600 / 60
        val sec = s % 60
        return when {
            d > 0 -> "${d}d" + if (h > 0) " ${h}h" else ""
            h > 0 -> "${h}h" + if (m > 0) " ${m}m" else ""
            m > 0 -> "${m}m" + if (sec > 0 && m < 10) " ${sec}s" else ""
            else -> "${sec}s"
        }
    }
}
