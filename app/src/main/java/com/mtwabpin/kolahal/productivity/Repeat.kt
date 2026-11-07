package com.mtwabpin.kolahal.productivity

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * How a reminder repeats, as stored. It fires at the same wall-clock time each time. Rules:
 * - `day`, `weekdays`, `weekend`
 * - a list of days (`mon,thu`)
 * - `month:15`: that day of every month. Shorter months use their last day.
 * - `year:12-03`: every 3 December. 29 February becomes the 28th in non-leap years.
 */
object Repeat {
    fun matches(rule: String, d: LocalDate): Boolean = when {
        rule == "day" -> true
        rule == "weekdays" -> d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY
        rule == "weekend" -> d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        rule.startsWith("month:") -> d.dayOfMonth == minOf(rule.removePrefix("month:").toInt(), d.lengthOfMonth())
        rule.startsWith("year:") -> rule.removePrefix("year:").split('-').map { it.toInt() }.let { (m, day) ->
            d.monthValue == m && d.dayOfMonth == minOf(day, d.lengthOfMonth())
        }
        else -> d.dayOfWeek in days(rule)
    }

    /** Returns the first date and [time] after [after] that matches [rule]. */
    fun next(rule: String, time: LocalTime, after: LocalDateTime): LocalDateTime {
        var d = after.toLocalDate()
        // 400 days is more than a year and a day, which covers every rule, 29 February too.
        repeat(400) {
            val at = d.atTime(time)
            if (at.isAfter(after) && matches(rule, d)) return at
            d = d.plusDays(1)
        }
        throw IllegalArgumentException("no date for $rule")
    }

    /**
     * True when [rule] has a form Kolahal writes, so [next] and [label] work with it. This matters
     * because a backup can hold anything, including rules from a newer version. Valid parts:
     * - a day of the month from 1 to 31
     * - a date that exists in some year (29 February too)
     * - days of the week, each one a known name
     */
    fun isValid(rule: String): Boolean {
        val shape = when {
            rule == "day" || rule == "weekdays" || rule == "weekend" -> true
            rule.startsWith("month:") -> rule.removePrefix("month:").toIntOrNull() in 1..31
            rule.startsWith("year:") -> rule.removePrefix("year:").split('-').let { p ->
                val month = p.first().toIntOrNull()
                p.size == 2 && month in 1..12 && p[1].toIntOrNull() in 1..Month.of(month!!).maxLength()
            }
            else -> rule.split(',').all { dayNamed(it) != null }
        }
        return shape && runCatching {
            label(rule)
            next(rule, LocalTime.NOON, LocalDateTime.of(2028, 1, 1, 0, 0))
        }.isSuccess
    }

    /** Text for a rule, for example `every day`, `every Mon and Thu`, `on the 15th of every month`, `every year on 3 Dec`. */
    fun label(rule: String): String = when {
        rule == "day" -> "every day"
        rule == "weekdays" -> "every weekday"
        rule == "weekend" -> "every Saturday and Sunday"
        rule.startsWith("month:") -> "on the ${ordinal(rule.removePrefix("month:").toInt())} of every month"
        rule.startsWith("year:") -> rule.removePrefix("year:").split('-').map { it.toInt() }.let { (m, day) ->
            "every year on $day ${Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        }
        else -> "every " + days(rule).map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }.let { names ->
            if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }

    private fun days(rule: String): List<DayOfWeek> = rule.split(',').mapNotNull(::dayNamed)

    /** Reads a day name from a rule, such as `mon` or `monday`. It needs at least three letters, as `every` writes them. */
    private fun dayNamed(name: String): DayOfWeek? =
        name.takeIf { it.length >= 3 }?.let { n -> DayOfWeek.entries.firstOrNull { it.name.lowercase().startsWith(n) } }

    private fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}
