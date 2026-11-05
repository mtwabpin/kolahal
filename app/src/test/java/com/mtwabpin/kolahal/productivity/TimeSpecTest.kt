package com.mtwabpin.kolahal.productivity

import com.mtwabpin.kolahal.withDefaultLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class TimeSpecTest {
    // Saturday 17 Oct 2026, 14:20
    private val now = LocalDateTime.of(2026, 10, 17, 14, 20, 0)
    private fun parse(s: String) = TimeSpec.parseWhen(s.split(" "), now)

    @Test fun theSmallHoursEndAtSix() {
        assertEquals(true, TimeSpec.inTheSmallHours(LocalDateTime.of(2026, 10, 18, 0, 0)))
        assertEquals(true, TimeSpec.inTheSmallHours(LocalDateTime.of(2026, 10, 18, 5, 59)))
        assertEquals(false, TimeSpec.inTheSmallHours(LocalDateTime.of(2026, 10, 18, 6, 0)))
        assertEquals(false, TimeSpec.inTheSmallHours(LocalDateTime.of(2026, 10, 18, 23, 0)))
    }

    /** A bare hour after a day word could be the text's first word instead, at the default time. */
    @Test fun aBareHourAfterADayWordReadsTwoWays() {
        fun other(s: String): TimeSpec.Reading? {
            val tokens = s.split(" ")
            return TimeSpec.otherReading(tokens, TimeSpec.parseWhen(tokens, now)!!.consumed, now)
        }
        assertEquals(TimeSpec.Reading(LocalDateTime.of(2026, 10, 18, 9, 0), "2 tickets"), other("tomorrow 2 tickets"))
        assertEquals(TimeSpec.Reading(LocalDateTime.of(2026, 10, 23, 9, 0), "3 things to buy"), other("fri 3 things to buy"))
        assertEquals(TimeSpec.Reading(LocalDateTime.of(2026, 10, 18, 9, 0), "2 tickets"), other("on tomorrow 2 tickets"))
        // It says what it means: there's no other way to read these.
        assertNull(other("tomorrow at 2 tickets"))
        assertNull(other("tomorrow 2am tickets"))
        assertNull(other("tomorrow 2:00 tickets"))
        assertNull(other("at 5 tickets"))
        assertNull(other("5:30 tickets"))
    }

    /** What a tap on the question sets is what it said, even after midnight. */
    @Test fun exactWordsReadBackAsTheSameTime() {
        val at = LocalDateTime.of(2026, 10, 18, 2, 0)
        assertEquals("2026-10-18 02:00 2 tickets", TimeSpec.exactWords(at, "2 tickets"))
        val tokens = TimeSpec.exactWords(at, "2 tickets").split(" ")
        val parsed = TimeSpec.parseWhen(tokens, now.withHour(23))!!
        assertEquals(at, parsed.at)
        assertEquals(listOf("2", "tickets"), tokens.drop(parsed.consumed))
    }

    @Test fun minutes() = assertEquals(TimeSpec.Parsed(now.plusMinutes(10), 1), parse("10m tea"))
    @Test fun hours() = assertEquals(now.plusHours(2), parse("2h call")?.at)
    @Test fun combined() = assertEquals(TimeSpec.Parsed(now.plusMinutes(90), 1), parse("1h30m stretch"))
    @Test fun inPrefix() = assertEquals(TimeSpec.Parsed(now.plusMinutes(5), 2), parse("in 5m x"))
    @Test fun clockLaterToday() = assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 10, 17, 18, 30), 1), parse("18:30 gym"))
    @Test fun clockPassedMeansTomorrow() = assertEquals(LocalDateTime.of(2026, 10, 18, 9, 0), parse("9:00 x")?.at)
    @Test fun ampm() = assertEquals(LocalDateTime.of(2026, 10, 17, 18, 0), parse("6pm x")?.at)
    @Test fun atBareHour() = assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 10, 17, 17, 0), 2), parse("at 17 x"))
    @Test fun tomorrowWithTime() = assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 10, 18, 9, 0), 2), parse("tomorrow 9:00 call mom"))
    @Test fun tomorrowAtTime() = assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 10, 18, 7, 15), 3), parse("tomorrow at 7:15 run"))
    @Test fun tomorrowDefaultsToNine() = assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 10, 18, 9, 0), 1), parse("tomorrow call mom"))
    @Test fun weekday() = assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 10, 23, 8, 0), 2), parse("fri 8am standup"))
    @Test fun sameWeekdayIsNextWeek() = assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), parse("saturday x")?.at)
    /** Today's own name, with a time still ahead today, is today. */
    @Test fun sameWeekdayLaterIsToday() = assertEquals(LocalDateTime.of(2026, 10, 17, 18, 0), parse("sat 18:00 x")?.at)

    @Test fun dates() {
        assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 12, 3, 9, 0), 2), parse("dec 3 dentist"))
        assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 12, 3, 10, 30), 3), parse("3 december 10:30 dentist"))
        assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 12, 3, 10, 0), 5), parse("on dec 3rd at 10 dentist"))
        assertEquals(TimeSpec.Parsed(LocalDateTime.of(2027, 12, 3, 9, 0), 3), parse("dec 3 2027 dentist"))
        assertEquals(TimeSpec.Parsed(LocalDateTime.of(2027, 1, 5, 8, 0), 2), parse("2027-01-05 8am x"))
        // Gone this year: next year's.
        assertEquals(LocalDateTime.of(2027, 3, 14, 9, 0), parse("mar 14 x")?.at)
        // A bare number after a date is not an hour.
        assertEquals(TimeSpec.Parsed(LocalDateTime.of(2026, 12, 3, 9, 0), 2), parse("dec 3 2 tickets"))
        assertNull(parse("dec 3 2025 x"))
        assertNull(parse("feb 30 x"))
        assertNull(parse("may I ask"))
    }

    private fun every(s: String) = TimeSpec.parseRepeat(s.split(" "), now)

    @Test fun repeats() {
        assertEquals(TimeSpec.Repeating("day", LocalDateTime.of(2026, 10, 18, 8, 0), 3), every("every day 8:00 pills"))
        assertEquals(TimeSpec.Repeating("weekdays", LocalDateTime.of(2026, 10, 19, 9, 0), 2), every("every weekday standup"))
        assertEquals(TimeSpec.Repeating("mon,thu", LocalDateTime.of(2026, 10, 19, 19, 0), 4), every("every thu,monday at 19:00 bins"))
        assertEquals(TimeSpec.Repeating("weekend", LocalDateTime.of(2026, 10, 17, 18, 0), 3), every("every weekend 6pm run"))
        assertEquals(TimeSpec.Repeating("month:1", LocalDateTime.of(2026, 11, 1, 9, 0), 3), every("every month 1st rent"))
        // No day: today's, from next month once today's 09:00 has gone.
        assertEquals(TimeSpec.Repeating("month:17", LocalDateTime.of(2026, 11, 17, 9, 0), 2), every("every month rent"))
        assertEquals(
            TimeSpec.Repeating("month:17", LocalDateTime.of(2026, 10, 17, 9, 0), 2),
            TimeSpec.parseRepeat("every month rent".split(" "), now.withHour(7)),
        )
        assertEquals(TimeSpec.Repeating("year:03-14", LocalDateTime.of(2027, 3, 14, 9, 0), 4), every("every year mar 14 mum's birthday"))
        assertNull(every("every blue moon"))
        assertNull(every("every day at nonsense"))
        assertNull(every("daily x"))
    }
    /** 29 February is a yearly date, the 28th in other years. */
    @Test fun aYearlyReminderOnTheTwentyNinth() {
        assertEquals(TimeSpec.Repeating("year:02-29", LocalDateTime.of(2027, 2, 28, 9, 0), 4), every("every year feb 29 leap day"))
        assertEquals(TimeSpec.Repeating("year:02-29", LocalDateTime.of(2027, 2, 28, 9, 0), 4), every("every year 29 feb leap day"))
    }

    /** Meant as a date but not one: not today's date, with the date as the reminder's words. */
    @Test fun aYearlyDateThatCantBeIsRefused() {
        for (line in listOf("every year feb 30 x", "every year 31 apr x", "every year mar 40 x", "every year mar 14 2027 x", "every year 2027-03-14 x")) {
            assertNull(line, every(line))
        }
    }

    /** No date at all still means today's, every year. */
    @Test fun aYearlyReminderWithoutADateIsToday() {
        for (line in listOf("every year march madness", "every year may 5k run")) {
            val r = every(line)!!
            assertEquals(line, "year:10-17", r.rule)
            assertEquals(line, 2, r.consumed)
        }
    }

    @Test fun todayPassedIsRejected() = assertNull(parse("today 9:00 x"))
    @Test fun bareNumberIsNotATime() = assertNull(parse("2 tickets"))
    @Test fun wordsAreNotATime() = assertNull(parse("buy milk"))
    @Test fun empty() = assertNull(TimeSpec.parseWhen(emptyList(), now))

    @Test fun durations() {
        assertEquals(600L, TimeSpec.parseDuration("10m"))
        assertEquals(5400L, TimeSpec.parseDuration("1h30m"))
        assertEquals(90L, TimeSpec.parseDuration("90s"))
        assertEquals(86_400L, TimeSpec.parseDuration("1d"))
        assertEquals(1500L, TimeSpec.parseDuration("25", bareMinutes = true))
        assertNull(TimeSpec.parseDuration("25"))
        assertNull(TimeSpec.parseDuration("0m"))
        assertNull(TimeSpec.parseDuration("abc"))
    }

    @Test fun clocks() {
        assertEquals(LocalTime.of(0, 5), TimeSpec.parseClock("12:05am"))
        assertEquals(LocalTime.of(12, 0), TimeSpec.parseClock("12pm"))
        assertEquals(LocalTime.of(0, 0), TimeSpec.parseClock("12:00am"))
        assertEquals("a dot for the colon", LocalTime.of(7, 30), TimeSpec.parseClock("7.30"))
        assertEquals(LocalTime.of(7, 0), TimeSpec.parseClock("7", allowBareHour = true))
        assertNull(TimeSpec.parseClock("7"))
        assertNull(TimeSpec.parseClock("24:00"))
        assertNull(TimeSpec.parseClock("13pm"))
        assertNull(TimeSpec.parseClock("9:60"))
    }

    @Test fun formatting() {
        assertEquals("2h 10m", TimeSpec.formatDuration(7800))
        assertEquals("45s", TimeSpec.formatDuration(45))
        assertEquals("1d 1h", TimeSpec.formatDuration(90_000))
        assertEquals("5m", TimeSpec.formatDuration(300))
        assertEquals("30s", TimeSpec.formatDuration(30))
        assertEquals("2m", TimeSpec.formatDuration(120))
        assertEquals("1m 30s", TimeSpec.formatDuration(90))
        assertEquals("1h 30m", TimeSpec.formatDuration(5_400))
        assertEquals("2h", TimeSpec.formatDuration(7_200))
        // Past ten minutes the seconds don't matter, and past a day the minutes don't.
        assertEquals("12m", TimeSpec.formatDuration(750))
    }

    @Test fun refs() {
        val list = listOf(10L, 20L, 30L)
        assertEquals(20L, pickRef("2", list) { it })
        assertEquals(30L, pickRef("#30", list) { it })
        assertNull(pickRef("#2", list) { it })
        assertNull(pickRef("4", list) { it })
        assertNull(pickRef("x", list) { it })
    }

    @Test fun relativeSecondsOnlyForDurations() {
        assertEquals(7200L, TimeSpec.relativeSeconds(listOf("2h", "call")))
        assertEquals(600L, TimeSpec.relativeSeconds(listOf("in", "10m", "x")))
        assertNull(TimeSpec.relativeSeconds(listOf("18:30", "x")))
        assertNull(TimeSpec.relativeSeconds(listOf("at", "6pm")))
    }

    /** A number too big for a time is refused: not wrapped round to a few hours, not an error from Java. */
    @Test fun aHugeDurationIsRefused() {
        assertEquals(36_500L * 86_400, TimeSpec.parseDuration("36500d"))
        assertNull(TimeSpec.parseDuration("213503982334602d"))
        assertNull(TimeSpec.parseDuration("1000000000000d"))
        assertNull(TimeSpec.parseDuration("99999999999999999999m"))
        assertNull(TimeSpec.parseDuration("1d99999999999999h"))
        assertNull(TimeSpec.parseDuration("999999999999", bareMinutes = true))
        assertNull(parse("1000000000000d x"))
        assertNull(parse("in 213503982334602d x"))
    }

    /** A yearly rule is stored in plain digits, whatever the phone's language: it's read back later. */
    @Test fun aYearlyRuleIsInPlainDigits() {
        for (tag in listOf("fa", "ar", "bn")) {
            withDefaultLocale(Locale.forLanguageTag(tag)) {
                assertEquals(tag, "year:10-17", every("every year march madness")!!.rule)
                assertEquals(tag, "year:03-14", every("every year mar 14 birthday")!!.rule)
            }
        }
    }

    /** 29 February without a year is the next one there is, as any date without a year is the next it comes. */
    @Test fun leapDayIsTheNextOne() {
        assertEquals(LocalDateTime.of(2028, 2, 29, 9, 0), parse("feb 29 x")!!.at)
        assertEquals(LocalDateTime.of(2028, 2, 29, 18, 0), parse("29 feb at 18:00 x")!!.at)
        // In a leap year after it: the one four years on, not 28 February.
        val after = LocalDateTime.of(2028, 3, 1, 9, 0)
        assertEquals(LocalDateTime.of(2032, 2, 29, 9, 0), TimeSpec.parseWhen("feb 29 x".split(" "), after)!!.at)
        // With a year that has none: no date.
        assertNull(parse("feb 29 2027 x"))
        assertNull(parse("feb 30 x"))
    }
}
