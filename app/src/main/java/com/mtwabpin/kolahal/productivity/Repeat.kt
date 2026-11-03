package com.mtwabpin.kolahal.productivity

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * How a reminder repeats, as stored: `day`, `weekdays`, `weekend`, a list of days (`mon,thu`),
 * `month:15` (that day of every month; the last day in shorter months) or `year:12-03` (every
 * 3 December; 29 February is the 28th in other years). It fires at the same wall-clock time.
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

    /** The first time after [after] that [rule] falls on, at [time]. */
    fun next(rule: String, time: LocalTime, after: LocalDateTime): LocalDateTime {
        var d = after.toLocalDate()
        // A year and a day covers every rule, 29 February included.
        repeat(400) {
            val at = d.atTime(time)
            if (at.isAfter(after) && matches(rule, d)) return at
            d = d.plusDays(1)
        }
        throw IllegalArgumentException("no date for $rule")
    }

    /**
     * Whether [rule] is one Kolahal writes, so [next] and [label] work with it: a backup can hold
     * anything, a newer version's rules too. A day of the month 1 to 31, a date that exists in some
     * year (29 February too), and days of the week each named.
     */
    fun isValid(rule: String): Boolean {
        val shape = when {
            rule == "day" || rule == "weekdays" || rule == "weekend" -> true
            rule.startsWith("month:") -> rule.removePrefix("month:").toIntOrNull() in 1..31
            rule.startsWith("year:") -> rule.removePrefix("year:").split('-').let { p ->
                val month = p.first().toIntOrNull()
                p.size == 2 && month in 1..12 && p[1].toIntOrNull() in 1..Month.of(month!!).maxLength()
            }
            else -> rule.split(',').all { name -> name.isNotBlank() && DayOfWeek.entries.any { it.name.lowercase().startsWith(name) } }
        }
        return shape && runCatching {
            label(rule)
            next(rule, LocalTime.NOON, LocalDateTime.of(2028, 1, 1, 0, 0))
        }.isSuccess
    }

    /** `every day`, `every Mon and Thu`, `on the 15th of every month`, `every year on 3 Dec`. */
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

    private fun days(rule: String): List<DayOfWeek> =
        rule.split(',').filter { it.isNotBlank() }.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name.lowercase().startsWith(name) } }

    private fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}
