package com.mtwabpin.kolahal.productivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class RepeatTest {
    private val nine = LocalTime.of(9, 0)

    @Test fun aRuleItCantReadIsntValid() {
        for (rule in listOf("day", "weekdays", "weekend", "mon", "mon,thu", "month:1", "month:31", "year:03-14", "year:02-29")) {
            assertTrue(rule, Repeat.isValid(rule))
        }
        // Days are named with three letters at least: "t" could be Tuesday or Thursday.
        for (rule in listOf("", "biweekly", "month:x", "month:0", "year:02", "year:x-y", "year:13-40", "year:00-10", "t", "s", "mo", "mon,t")) {
            assertFalse(rule, Repeat.isValid(rule))
        }
    }

    @Test fun nextTimes() {
        // Friday 23 Oct 2026, after its 9:00.
        val fri = LocalDateTime.of(2026, 10, 23, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), Repeat.next("day", nine, fri))
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), Repeat.next("weekdays", nine, fri))
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), Repeat.next("weekend", nine, fri))
        assertEquals(LocalDateTime.of(2026, 10, 29, 9, 0), Repeat.next("mon,thu", nine, LocalDateTime.of(2026, 10, 26, 9, 0)))
    }

    /** The 31st in a shorter month is its last day; 29 February is the 28th in other years. */
    @Test fun shortMonthsAndLeapDays() {
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), Repeat.next("month:31", nine, LocalDateTime.of(2026, 10, 31, 9, 0)))
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0), Repeat.next("year:02-29", nine, LocalDateTime.of(2026, 3, 1, 9, 0)))
        assertEquals(LocalDateTime.of(2028, 2, 29, 9, 0), Repeat.next("year:02-29", nine, LocalDateTime.of(2027, 3, 1, 9, 0)))
    }

    @Test fun labels() {
        assertEquals("every day", Repeat.label("day"))
        assertEquals("every weekday", Repeat.label("weekdays"))
        assertEquals("every Mon and Thu", Repeat.label("mon,thu"))
        assertEquals("every Mon, Wed and Fri", Repeat.label("mon,wed,fri"))
        assertEquals("on the 1st of every month", Repeat.label("month:1"))
        assertEquals("on the 22nd of every month", Repeat.label("month:22"))
        assertEquals("every year on 14 Mar", Repeat.label("year:03-14"))
    }

    /** Rules nothing writes, which would come only from a hand-edited backup, are refused. */
    @Test fun rulesNothingWritesArentValid() {
        for (rule in listOf("year:02-30", "year:04-31", "year:02-30-5", "month:32", "month:99", "mon,funday", "mon,", ",mon", "year:-03-14")) {
            assertFalse(rule, Repeat.isValid(rule))
        }
        // As stored by a phone in Persian before the rule was kept in plain digits.
        assertTrue(Repeat.isValid("year:۰۳-۱۴"))
    }
}
