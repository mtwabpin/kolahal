package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The list of `calendar.events` goes into an `agenda` widget as it is:
 * `widget.show{ events = calendar.events() }`. A row has an `id`, which the widget reads and drops.
 */
class CalendarRowsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun local(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun utcDay(d: Int) = LocalDate.of(2026, 10, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun theCalendarsRowsAreAnAgendasEvents() {
        val now = local(17, 12)
        val events = listOf(
            AgendaEvent(1, "Standup", local(17, 13), local(17, 13, 30), false),
            AgendaEvent(2, "Holiday", utcDay(17), utcDay(18), true),
            AgendaEvent(3, "Ping", local(18, 9), local(18, 9), false),
        )
        val shown = Agenda.shown(events, zone, now, 3)
        // The rows as a script gets them, with nothing taken out.
        val rows = shown.map(::eventRow)
        val spec = WidgetParser.parse(mapOf("events" to rows, "days" to 3), WidgetKind.AGENDA, text = { it }, link = { it }) as WidgetSpec.Agenda
        assertEquals(listOf("Holiday", "Standup", "Ping"), spec.events.map { it.title })
        assertEquals(shown.map { it.starts }, spec.events.map { it.starts })
        assertEquals(shown.map { it.ends }, spec.events.map { it.ends })
        assertEquals(shown.map { it.allDay }, spec.events.map { it.allDay })
    }
}
