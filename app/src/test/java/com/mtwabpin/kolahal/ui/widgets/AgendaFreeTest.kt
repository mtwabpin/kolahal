package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.ui.widgets.AgendaFree.Line
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** An agenda's Now look: what is on now and how far along, the free time, what is next, and the day with its gaps. */
class AgendaFreeTest {
    private val utc = ZoneId.of("UTC")

    /** Tuesday 24 November 2026 at [hour]:[minute], or another day of the month. */
    private fun at(hour: Int, minute: Int = 0, day: Int = 24) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, utc).toEpochSecond()

    private fun event(title: String, starts: Long, ends: Long? = null, allDay: Boolean = false, where: String? = null) =
        WidgetSpec.Event(title, starts, ends, allDay, where)

    private val call = event("Team call", at(10), at(11, 30))
    private val lunch = event("Lunch", at(13), at(14), where = "Lisbon office")
    private val dentist = event("Dentist", at(15, 30), at(16, 15))
    private val birthday = event("Birthday", at(0, day = 25), at(0, day = 26), allDay = true)
    private val gym = event("Gym", at(9, day = 25), at(10, day = 25))
    private val agenda = WidgetSpec.Agenda(WidgetBase(), listOf(call, lunch, dentist, birthday, gym), days = 3)

    private fun moment(now: Long) = AgendaFree.moment(agenda, now, utc, clock24 = true, locale = Locale.UK)

    @Test
    fun aLengthIsWrittenInHoursAndMinutes() {
        assertEquals("45 min", AgendaFree.span(45 * 60))
        assertEquals("1h", AgendaFree.span(3600))
        assertEquals("1h 30", AgendaFree.span(5400))
        assertEquals("12h", AgendaFree.span(12 * 3600))
        assertEquals("1 min", AgendaFree.span(20))
        assertEquals("1 hour 30 minutes", AgendaFree.spanSaid(5400))
        assertEquals("2 hours", AgendaFree.spanSaid(7200))
        assertEquals("1 minute", AgendaFree.spanSaid(60))
    }

    /** The mockup: the call is 60% through and ends at 11:30; lunch is at 13:00, an hour and a half later. */
    @Test
    fun whatIsOnNowAndTheFreeTimeUntilTheNext() {
        val m = moment(at(10, 54))
        assertEquals(call, m.now?.event)
        assertEquals(0.6f, m.now!!.fraction, 0.001f)
        assertEquals("11:30", m.now!!.until)
        assertEquals(AgendaFree.Next(lunch, "13:00", null), m.next)
        assertEquals(5400L, m.free)
        assertEquals(
            "Now Team call, until 11:30, 60% done, free 1 hour 30 minutes until Lunch",
            AgendaFree.said(m)
        )
    }

    /** With nothing on now, the free time counts from now. */
    @Test
    fun betweenTwoEventsTheFreeTimeCountsFromNow() {
        val m = moment(at(12))
        assertNull(m.now)
        assertEquals(lunch, m.next?.event)
        assertEquals(3600L, m.free)
        assertNull("the next event is not said twice", m.first)
        assertEquals("Free 1 hour until Lunch", AgendaFree.said(m))
    }

    /** When the day is over, the next event is on a later day, with no free time counted to it. */
    @Test
    fun whenTheDayIsOverTheNextIsTomorrow() {
        val m = moment(at(17))
        assertNull(m.now)
        assertEquals(AgendaFree.Next(gym, "09:00", "Tomorrow"), m.next)
        assertNull(m.free)
        assertNull(m.first)
        assertNull(AgendaFree.said(m))
    }

    /** An all-day event is never on now. With nothing else, it is what today has. */
    @Test
    fun anAllDayEventIsNotNow() {
        val holiday = event("Holiday", at(0), at(0, day = 25), allDay = true)
        val m = AgendaFree.moment(
            WidgetSpec.Agenda(WidgetBase(), listOf(holiday), days = 1),
            at(11),
            utc,
            clock24 = true,
            locale = Locale.UK
        )
        assertNull(m.now)
        assertNull(m.next)
        assertNull(m.free)
        assertEquals("all day", m.first?.time)
        assertEquals(holiday, m.first?.event)
        // With only an all-day event tomorrow, that is what comes next.
        val later = AgendaFree.moment(
            WidgetSpec.Agenda(WidgetBase(), listOf(birthday), days = 3),
            at(11),
            utc,
            clock24 = true,
            locale = Locale.UK
        )
        assertEquals(AgendaFree.Next(birthday, "all day", "Tomorrow"), later.next)
    }

    /** The day: each event with its length, the free time between two, then tomorrow under its heading. */
    @Test
    fun theDayHasItsEventsAndTheFreeTimeBetweenThem() {
        val lines = AgendaFree.day(agenda, at(10, 54), utc, clock24 = true, locale = Locale.UK)
        fun said(line: Line) = when (line) {
            is Line.Head -> "${line.label} · ${line.date}"
            is Line.Event -> listOfNotNull(line.line.time, line.line.event.title, line.length).joinToString(" ")
            is Line.Free -> "free ${AgendaFree.span(line.seconds)}"
        }
        assertEquals(
            listOf(
                "Today · Tue 24 Nov",
                "now Team call 1h 30",
                "free 1h 30",
                "13:00 Lunch 1h",
                "free 1h 30",
                "15:30 Dentist 45 min",
                "Tomorrow · Wed 25 Nov",
                "all day Birthday",
                "09:00 Gym 1h",
            ),
            lines.map(::said)
        )
        assertEquals(3 * 3600L, AgendaFree.freeToday(lines))
        // Early in the day, the free time until the first event counts from now.
        val early = AgendaFree.day(agenda, at(8), utc, clock24 = true, locale = Locale.UK)
        assertEquals("free 2h", said(early[1]))
        // Less than 15 minutes between two events is not free time.
        val close = WidgetSpec.Agenda(
            WidgetBase(),
            listOf(event("A", at(9), at(10)), event("B", at(10, 10), at(11))),
            days = 1
        )
        assertEquals(
            listOf("Today · Tue 24 Nov", "09:00 A 1h", "10:10 B 50 min"),
            AgendaFree.day(close, at(8, 55), utc, clock24 = true, locale = Locale.UK).map(::said)
        )
    }

    /** An event with no end has no length: the free time after it counts from its start. */
    @Test
    fun anEventWithNoEndHasNoLength() {
        val spec = WidgetSpec.Agenda(
            WidgetBase(),
            listOf(event("Call home", at(18)), event("Dinner", at(19), at(21))),
            days = 1
        )
        val lines = AgendaFree.day(spec, at(17, 30), utc, clock24 = true, locale = Locale.UK)
        assertEquals(
            listOf<Any?>(null, 1800L, null, 3600L, "2h"),
            lines.map {
                when (it) {
                    is Line.Head -> null
                    is Line.Free -> it.seconds
                    is Line.Event -> it.length
                }
            }
        )
    }

    /** As many lines as fit, never ending on free time or a heading; the first event always. */
    @Test
    fun theDayShowsTheLinesThatFit() {
        val lines = AgendaFree.day(agenda, at(10, 54), utc, clock24 = true, locale = Locale.UK)
        // A 4×2 card: the heading, the call, its free time and lunch, then "+3 more".
        assertEquals(4, AgendaFree.shown(lines, 152, 24, 42, 20, 20))
        assertEquals(lines.size, AgendaFree.shown(lines, 400, 24, 42, 20, 20))
        assertEquals("the first event however low", 2, AgendaFree.shown(lines, 30, 24, 42, 20, 20))
        // Room for lunch's free time but not for the dentist: the lines end at lunch.
        assertEquals(4, AgendaFree.shown(lines, 175, 24, 42, 20, 20))
        // The drawn 4×2 card (25 Nov): 163 dp inside, events with no tap (32 dp). Today's three
        // events and the free time between them take 160 dp: all of them, no "+1 more".
        val today = AgendaFree.day(agenda.copy(days = 1), at(10, 54), utc, clock24 = true, locale = Locale.UK)
        assertEquals(6, today.size)
        assertEquals(6, AgendaFree.shown(today, 163, 24, 32, 20, 20))
        // A heading of 28 dp, as the card had it, left 159 dp for the rest: the dentist was left out.
        assertEquals(4, AgendaFree.shown(today, 163 - 4, 24, 32, 20, 20))
    }
}
