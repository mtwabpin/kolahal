package com.mtwabpin.kolahal.productivity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class AgendaTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 17)
    private fun local(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun utcDay(d: Int) = LocalDate.of(2026, 10, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val now = local(17, 12)

    @Test fun groupsByDayWithAllDayFirst() {
        val events = listOf(
            AgendaEvent(1, "Standup", local(17, 13), local(17, 13, 30), false),
            AgendaEvent(2, "Holiday", utcDay(17), utcDay(18), true),
            AgendaEvent(3, "Dinner", local(18, 20), local(18, 21), false),
            AgendaEvent(4, "Breakfast (over)", local(17, 8), local(17, 9), false),
            AgendaEvent(5, "Far away", local(25, 9), local(25, 10), false),
        )
        val g = Agenda.group(events, zone, today, 3, now)
        assertEquals(listOf(today, today.plusDays(1)), g.map { it.first })
        assertEquals(listOf("Holiday", "Standup"), g[0].second.map { it.title })
        assertEquals(listOf("Dinner"), g[1].second.map { it.title })
    }

    @Test fun multiDayAllDayRepeats() {
        val trip = AgendaEvent(1, "Trip", utcDay(16), utcDay(19), true)
        val g = Agenda.group(listOf(trip), zone, today, 5, now)
        assertEquals(listOf(today, today.plusDays(1)), g.map { it.first })
    }

    @Test fun ongoingEventShowsToday() {
        val e = AgendaEvent(1, "Night shift", local(16, 22), local(17, 14), false)
        assertEquals(listOf(today), Agenda.group(listOf(e), zone, today, 1, now).map { it.first })
    }

    // ---- shown: what the Agenda card and a script's calendar.events both show ----

    private fun midnight(d: Int, z: ZoneId = zone) = LocalDate.of(2026, 10, d).atStartOfDay(z).toEpochSecond()

    /** What has ended and what is past the days is left out. The rest is by day, all-day events first. */
    @Test fun shownIsTheNextDaysInOrder() {
        val events = listOf(
            AgendaEvent(1, "Standup", local(17, 13), local(17, 13, 30), false),
            AgendaEvent(2, "Holiday", utcDay(17), utcDay(18), true),
            AgendaEvent(3, "Dinner", local(18, 20), local(18, 21), false),
            AgendaEvent(4, "Breakfast (over)", local(17, 8), local(17, 9), false),
            AgendaEvent(5, "Far away", local(25, 9), local(25, 10), false),
            AgendaEvent(6, "Yesterday", utcDay(16), utcDay(17), true),
        )
        val shown = Agenda.shown(events, zone, now, 3)
        assertEquals(listOf("Holiday", "Standup", "Dinner"), shown.map { it.title })
        assertEquals(listOf(2L, 1L, 3L), shown.map { it.id })
        assertEquals(listOf(true, false, false), shown.map { it.allDay })
        // One day shows today only. Thirty days reach the far one.
        assertEquals(listOf("Holiday", "Standup"), Agenda.shown(events, zone, now, 1).map { it.title })
        assertEquals(listOf("Holiday", "Standup", "Dinner", "Far away"), Agenda.shown(events, zone, now, 30).map { it.title })
        assertEquals(emptyList<ShownEvent>(), Agenda.shown(emptyList(), zone, now, 3))
    }

    /** A timed event's times are the calendar's own, in seconds. [ShownEvent.begin] keeps the milliseconds, for a tap. */
    @Test fun aTimedEventKeepsItsTimesInSeconds() {
        val standup = Agenda.shown(listOf(AgendaEvent(1, "Standup", local(17, 13), local(17, 13, 30), false)), zone, now, 3).single()
        assertEquals(ShownEvent(1, "Standup", local(17, 13) / 1000, local(17, 13, 30) / 1000, false, local(17, 13)), standup)
        // An event with no length ends when it starts. So does one whose end is before its start.
        val ping = Agenda.shown(listOf(AgendaEvent(7, "Ping", local(18, 9), local(18, 9), false)), zone, now, 3).single()
        assertEquals(ping.starts, ping.ends)
        val odd = Agenda.shown(listOf(AgendaEvent(8, "Odd", local(18, 9), local(18, 8), false)), zone, now, 3).single()
        assertEquals(local(18, 9) / 1000, odd.starts)
        assertEquals(odd.starts, odd.ends)
        // One that started yesterday and still runs keeps its own start.
        val shift = Agenda.shown(listOf(AgendaEvent(9, "Night shift", local(16, 22), local(17, 14), false)), zone, now, 1).single()
        assertEquals(local(16, 22) / 1000, shift.starts)
        assertEquals(local(17, 14) / 1000, shift.ends)
    }

    /** The calendar keeps an all-day event at midnight UTC. It is shown from midnight to midnight where the phone is. */
    @Test fun anAllDayEventIsAtMidnightsOnThePhone() {
        val holiday = Agenda.shown(listOf(AgendaEvent(2, "Holiday", utcDay(17), utcDay(18), true)), zone, now, 3).single()
        assertEquals(midnight(17), holiday.starts)
        assertEquals(midnight(18), holiday.ends)
        assertEquals(utcDay(17), holiday.begin)
        // West of UTC, midnight UTC on the 17th is still the 16th. The event is on the 17th all the same.
        val west = ZoneId.of("America/Los_Angeles")
        val there = LocalDateTime.of(2026, 10, 17, 10, 0).atZone(west).toInstant().toEpochMilli()
        val same = Agenda.shown(listOf(AgendaEvent(2, "Holiday", utcDay(17), utcDay(18), true)), west, there, 3).single()
        assertEquals(midnight(17, west), same.starts)
        assertEquals(midnight(18, west), same.ends)
        // An event with no day at all (its end is its start) still has one day.
        val short = Agenda.shown(listOf(AgendaEvent(3, "Short", utcDay(17), utcDay(17), true)), zone, now, 3).single()
        assertEquals(midnight(18), short.ends)
    }

    /** An all-day event of several days is one event. It starts today when it started before, and ends after its last day. */
    @Test fun aTripIsOneEventThatStartsToday() {
        val trip = AgendaEvent(1, "Trip", utcDay(15), utcDay(20), true)
        val shown = Agenda.shown(listOf(trip), zone, now, 5)
        assertEquals(1, shown.size)
        assertEquals(midnight(17), shown[0].starts)
        assertEquals(midnight(20), shown[0].ends)
        assertEquals(utcDay(15), shown[0].begin)
        // Two days of a repeating event have one id and two starts: both are shown.
        val daily = listOf(AgendaEvent(4, "Run", local(17, 18), local(17, 19), false), AgendaEvent(4, "Run", local(18, 18), local(18, 19), false))
        assertEquals(listOf(local(17, 18), local(18, 18)), Agenda.shown(daily, zone, now, 3).map { it.begin })
    }
}
