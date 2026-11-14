package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * What a script hands widget.show{…} goes through three steps: WidgetParser checks it (in :script),
 * WidgetJson stores it, and WidgetJson reads it back for the card (in the main process). For every
 * kind, a full description comes back as the parser made it: a card never shows "widget
 * unavailable" for a description the parser accepted.
 */
class WidgetRoundTripTest {
    private fun parse(kind: WidgetKind, raw: Map<String, Any?>) = WidgetParser.parse(raw, kind, { it }, { it })

    private fun roundTrip(kind: WidgetKind, raw: Map<String, Any?>) {
        val spec = parse(kind, raw)
        val text = WidgetJson.encode(spec)
        assertNotNull("$kind: stored as text", text.takeIf { it.isNotEmpty() })
        assertEquals("$kind: read back as it was", spec, WidgetJson.decode(text))
    }

    private val base = mapOf(
        "title" to "Pune",
        "icon" to "☀",
        "color" to "warn",
        "tap" to mapOf("args" to "now", "label" to "Refresh"),
        "staleAfter" to 7200,
        "refresh" to 600,
    )

    @Test fun stat() = roundTrip(
        WidgetKind.STAT,
        base + mapOf(
            "value" to "27°", "label" to "Sunny", "unit" to "C", "delta" to "+2", "trend" to "up",
            "more" to listOf(mapOf("value" to "62%", "label" to "humidity"), mapOf("value" to "9 km/h")),
            "progress" to 0.4, "moving" to true,
        ),
    )

    @Test fun text() = roundTrip(WidgetKind.TEXT, base + mapOf("text" to "Carry an umbrella after 4 pm.", "caption" to "Today"))

    @Test fun list() = roundTrip(
        WidgetKind.LIST,
        base + mapOf(
            "items" to listOf(
                mapOf("title" to "Story", "detail" to "by pg", "trailing" to "412", "icon" to "Y", "color" to "good", "tap" to mapOf("url" to "https://example.com/1")),
                mapOf("title" to "Another", "tap" to mapOf("args" to "open 2", "label" to "Open")),
                mapOf("title" to "Plain"),
                // A row with a checkbox, ticked and not. "Not ticked" must not come back as "no checkbox".
                mapOf("title" to "Buy milk", "done" to false, "tap" to mapOf("args" to "done 1")),
                mapOf("title" to "Call home", "done" to true),
            ),
            "empty" to "Nothing new",
        ),
    )

    @Test fun buttons() = roundTrip(
        WidgetKind.BUTTONS,
        base + mapOf(
            "items" to listOf(
                mapOf("label" to "On", "icon" to "☀", "color" to "good", "tap" to mapOf("args" to "on", "label" to "Switch on")),
                mapOf("label" to "Report", "tap" to mapOf("args" to "report", "page" to "log")),
                mapOf("label" to "Docs", "tap" to mapOf("url" to "https://example.com/docs")),
            ),
        ),
    )

    @Test fun toggles() = roundTrip(
        WidgetKind.TOGGLES,
        base + mapOf(
            "items" to listOf(
                mapOf("label" to "Do Not Disturb", "icon" to "🌙", "on" to true, "tap" to mapOf("args" to "dnd off", "label" to "Turn off")),
                // A switch that is off comes back off, not without a state.
                mapOf("label" to "Work profile", "on" to false, "tap" to mapOf("args" to "work on")),
            ),
        ),
    )

    @Test fun clock() = roundTrip(
        WidgetKind.CLOCK,
        base + mapOf(
            "zones" to listOf(mapOf("zone" to "Asia/Kolkata", "label" to "Pune"), mapOf("zone" to "America/New_York"), mapOf("zone" to "UTC"), mapOf("zone" to "+05:45")),
            "seconds" to true,
        ),
    )

    @Test fun agenda() = roundTrip(
        WidgetKind.AGENDA,
        base + mapOf(
            "events" to listOf(
                mapOf("title" to "Final", "starts" to 1_800_000_000L, "ends" to 1_800_007_200L, "where" to "Pune", "color" to "good", "tap" to mapOf("url" to "https://example.com/final")),
                mapOf("title" to "Holiday", "starts" to 1_800_050_000L, "allDay" to true, "tap" to mapOf("args" to "open 2")),
                mapOf("title" to "Launch", "starts" to 1_800_100_000L),
            ),
            "days" to 7,
            "empty" to "Nothing planned",
        ),
    )

    @Test fun image() = roundTrip(
        WidgetKind.IMAGE,
        base + mapOf("image" to "https://example.com/comics/today.png?size=2x&v=3", "alt" to "A graph of \"quoted\" things — 日本語", "caption" to "Monday", "fit" to "fit"),
    )

    @Test fun progress() = roundTrip(
        WidgetKind.PROGRESS,
        base + mapOf(
            "value" to 72, "max" to 100, "text" to "72%", "label" to "Goal", "style" to "bar", "moving" to false,
            "items" to listOf(
                mapOf("label" to "Storage", "value" to 41, "max" to 64, "text" to "41 / 64 GB", "color" to "info"),
                mapOf("label" to "Data", "value" to 4.1, "max" to 5, "text" to "4.1 / 5 GB", "color" to "bad"),
            ),
        ),
    )

    @Test fun chart() = roundTrip(
        WidgetKind.CHART,
        base + mapOf(
            "points" to listOf(41, 58, 37, 64, 52, 71, 46), "style" to "bars", "min" to 0, "max" to 80,
            "value" to "46", "label" to "unlocks today", "labels" to listOf("6 days ago", "today"), "summary" to "Unlocks over 7 days",
        ),
    )

    @Test fun countdown() = roundTrip(
        WidgetKind.COUNTDOWN,
        base + mapOf("to" to 1_797_000_000L, "from" to 1_790_000_000L, "label" to "Goa trip", "style" to "days", "reached" to "Today!"),
    )

    /** The smallest description of each kind comes back too. */
    @Test fun theLeastOfEachKind() {
        roundTrip(WidgetKind.STAT, mapOf("value" to "7"))
        roundTrip(WidgetKind.TEXT, mapOf("text" to "hello"))
        roundTrip(WidgetKind.LIST, mapOf("items" to emptyList<Any>()))
        roundTrip(WidgetKind.PROGRESS, mapOf("value" to 0.5))
        roundTrip(WidgetKind.CHART, mapOf("points" to listOf(1, 2)))
        roundTrip(WidgetKind.COUNTDOWN, mapOf("to" to 1_797_000_000L))
        roundTrip(WidgetKind.BUTTONS, mapOf("items" to listOf(mapOf("label" to "Go", "tap" to mapOf("args" to "")))))
        roundTrip(WidgetKind.TOGGLES, mapOf("items" to listOf(mapOf("label" to "Mute", "on" to false, "tap" to mapOf("args" to "mute")))))
        roundTrip(WidgetKind.CLOCK, mapOf("zones" to listOf(mapOf("zone" to "UTC"))))
        roundTrip(WidgetKind.AGENDA, mapOf("events" to emptyList<Any>()))
        roundTrip(WidgetKind.IMAGE, mapOf("image" to "https://example.com/a.png", "alt" to "A picture"))
    }

    /** Every kind a script can use is in the tests above: a new kind needs its round trip. */
    @Test fun everyKindForScriptsIsHere() {
        val tested = setOf("stat", "text", "list", "progress", "chart", "countdown", "buttons", "toggles", "clock", "agenda", "image")
        assertEquals(tested, WidgetKind.forScripts.map { it.id }.toSet())
    }
}
