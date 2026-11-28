package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a script hands widget.show{…} goes through three steps: WidgetParser checks it (in :script),
 * WidgetJson stores it, and WidgetJson reads it back for the card (in the main process). For every
 * kind, a full description comes back as the parser made it: a card never shows "widget
 * unavailable" for a description the parser accepted.
 */
class WidgetRoundTripTest {
    private fun parse(kind: WidgetKind, raw: Map<String, Any?>) =
        WidgetParser.parse(raw, kind, { it }, { it })

    private fun roundTrip(kind: WidgetKind, raw: Map<String, Any?>) {
        val spec = parse(kind, raw)
        val text = WidgetJson.encode(spec)
        assertNotNull("$kind: stored as text", text.takeIf { it.isNotEmpty() })
        assertEquals("$kind: read back as it was", spec, WidgetJson.decode(text))
    }

    private val base = mapOf(
        "title" to "Tokyo",
        "icon" to "☀",
        "color" to "warn",
        "tap" to mapOf("args" to "now", "label" to "Refresh"),
        "staleAfter" to 7200,
        "refresh" to 600,
        "note" to "Done today",
    )

    @Test
    fun stat() = roundTrip(
        WidgetKind.STAT,
        base + mapOf(
            "value" to "27°", "label" to "Sunny", "unit" to "C", "delta" to "+2", "trend" to "up",
            "more" to listOf(
                mapOf("value" to "62%", "label" to "humidity"),
                mapOf("value" to "9 km/h")
            ),
            "progress" to 0.4, "moving" to true,
        ),
    )

    @Test
    fun text() = roundTrip(
        WidgetKind.TEXT,
        base + mapOf("text" to "Carry an umbrella after 4 pm.", "caption" to "Today")
    )

    @Test
    fun list() = roundTrip(
        WidgetKind.LIST,
        base + mapOf(
            "items" to listOf(
                mapOf(
                    "title" to "Story",
                    "detail" to "by pg",
                    "trailing" to "412",
                    "icon" to "Y",
                    "color" to "good",
                    "tap" to mapOf("url" to "https://example.com/1")
                ),
                mapOf("title" to "Another", "tap" to mapOf("args" to "open 2", "label" to "Open")),
                mapOf("title" to "Plain"),
                // A row with a checkbox, ticked and not. "Not ticked" must not come back as "no checkbox".
                mapOf("title" to "Buy milk", "done" to false, "tap" to mapOf("args" to "done 1")),
                mapOf("title" to "Call home", "done" to true),
                // A row with a range, and one with a value on it.
                mapOf(
                    "title" to "Sat",
                    "trailing" to "22–31°",
                    "range" to mapOf("low" to 22, "high" to 31)
                ),
                mapOf(
                    "title" to "BTC",
                    "range" to mapOf("low" to 94_100.5, "high" to 97_900, "value" to 97_420)
                ),
            ),
            "empty" to "Nothing new",
        ),
    )

    @Test
    fun buttons() = roundTrip(
        WidgetKind.BUTTONS,
        base + mapOf(
            "items" to listOf(
                mapOf(
                    "label" to "On",
                    "icon" to "☀",
                    "color" to "good",
                    "tap" to mapOf("args" to "on", "label" to "Switch on")
                ),
                mapOf("label" to "Report", "tap" to mapOf("args" to "report", "page" to "log")),
                mapOf("label" to "Docs", "tap" to mapOf("url" to "https://example.com/docs")),
            ),
        ),
    )

    @Test
    fun toggles() = roundTrip(
        WidgetKind.TOGGLES,
        base + mapOf(
            "items" to listOf(
                mapOf(
                    "label" to "Do Not Disturb",
                    "icon" to "🌙",
                    "on" to true,
                    "tap" to mapOf("args" to "dnd off", "label" to "Turn off")
                ),
                // A switch that is off comes back off, not without a state.
                mapOf(
                    "label" to "Work profile",
                    "on" to false,
                    "tap" to mapOf("args" to "work on")
                ),
            ),
        ),
    )

    @Test
    fun clock() = roundTrip(
        WidgetKind.CLOCK,
        base + mapOf(
            "zones" to listOf(
                mapOf("zone" to "Asia/Tokyo", "label" to "Tokyo"),
                mapOf("zone" to "America/New_York"),
                mapOf("zone" to "UTC"),
                mapOf("zone" to "+05:45")
            ),
            "seconds" to true,
        ),
    )

    @Test
    fun agenda() = roundTrip(
        WidgetKind.AGENDA,
        base + mapOf(
            "events" to listOf(
                mapOf(
                    "title" to "Final",
                    "starts" to 1_800_000_000L,
                    "ends" to 1_800_007_200L,
                    "where" to "Tokyo",
                    "color" to "good",
                    "tap" to mapOf("url" to "https://example.com/final")
                ),
                mapOf(
                    "title" to "Holiday",
                    "starts" to 1_800_050_000L,
                    "allDay" to true,
                    "tap" to mapOf("args" to "open 2")
                ),
                mapOf("title" to "Launch", "starts" to 1_800_100_000L),
            ),
            "days" to 7,
            "empty" to "Nothing planned",
        ),
    )

    @Test
    fun image() = roundTrip(
        WidgetKind.IMAGE,
        base + mapOf(
            "image" to "https://example.com/comics/today.png?size=2x&v=3",
            "alt" to "A graph of \"quoted\" things — 日本語",
            "caption" to "Monday",
            "fit" to "fit"
        ),
    )

    @Test
    fun versus() = roundTrip(
        WidgetKind.VERSUS,
        base + mapOf(
            "sides" to listOf(
                mapOf("name" to "Nilsen", "mark" to "N", "color" to "accent", "value" to 72, "text" to "1:12"),
                mapOf("name" to "Carter", "mark" to "U", "color" to "link", "value" to 3490, "text" to "58:10"),
            ),
            "score" to "1½–½",
            "turn" to 1,
            "moving" to true,
            "caption" to "17. b3 · Berlin",
            "badge" to "LIVE",
        ),
    )

    /** Kolahal 0.5.23's side fields: each side's own score, its clock and its lines; no card score then. */
    @Test
    fun versusWithEachSidesScoreClockAndLines() {
        roundTrip(
            WidgetKind.VERSUS,
            base + mapOf(
                "sides" to listOf(
                    mapOf("name" to "Harbour", "score" to "187/4", "text" to "18.2 ov", "lines" to listOf("Ames 64* (38)", "Kahn 22* (11)")),
                    mapOf("name" to "Valley", "score" to "201/6", "text" to "20 ov", "lines" to listOf("Ruiz 3/28 (4)")),
                ),
                "turn" to 1,
                "moving" to true,
            ),
        )
        roundTrip(
            WidgetKind.VERSUS,
            mapOf(
                "sides" to listOf(
                    mapOf("name" to "Iris Novak", "mark" to "IN", "clock" to 252, "detail" to "2214 · White"),
                    mapOf("name" to "Tomas Brandt", "mark" to "TB", "clock" to 3864),
                ),
                "turn" to 2,
                "moving" to true,
            ),
        )
    }

    /** A stored card without a score whose sides don't all have their own is nothing: `widget.show` refuses it. */
    @Test
    fun aStoredVersusWithoutAScoreNeedsEachSidesOwn() {
        val spec = parse(
            WidgetKind.VERSUS,
            mapOf("sides" to listOf(mapOf("name" to "A", "score" to "3"), mapOf("name" to "B", "clock" to 60))),
        )
        val text = WidgetJson.encode(spec)
        assertEquals(spec, WidgetJson.decode(text))
        assertNull(WidgetJson.decode(text.replace(Regex("\"clock\":\\s*60"), "\"text\":\"x\"")))
    }

    @Test
    fun sky() = roundTrip(
        WidgetKind.SKY,
        base + mapOf(
            "rises" to 1_800_000_000L,
            "sets" to 1_800_040_000L,
            "phase" to 0.78,
            "waxing" to true,
            "phases" to listOf(
                mapOf("phase" to 1, "waxing" to true, "at" to 1_800_300_000L),
                mapOf("phase" to 0.5, "at" to 1_800_900_000L),
            ),
        ),
    )

    @Test
    fun progress() = roundTrip(
        WidgetKind.PROGRESS,
        base + mapOf(
            "value" to 72,
            "max" to 100,
            "text" to "72%",
            "label" to "Goal",
            "style" to "bar",
            "moving" to false,
            "items" to listOf(
                mapOf(
                    "label" to "Storage",
                    "value" to 41,
                    "max" to 64,
                    "text" to "41 / 64 GB",
                    "color" to "info"
                ),
                mapOf(
                    "label" to "Data",
                    "value" to 4.1,
                    "max" to 5,
                    "text" to "4.1 / 5 GB",
                    "color" to "bad"
                ),
            ),
        ),
    )

    @Test
    fun chart() = roundTrip(
        WidgetKind.CHART,
        base + mapOf(
            "points" to listOf(41, 58, 37, 64, 52, 71, 46),
            "style" to "bars",
            "min" to 0,
            "max" to 80,
            "value" to "46",
            "label" to "unlocks today",
            "labels" to listOf("6 days ago", "today"),
            "summary" to "Unlocks over 7 days",
        ),
    )

    /** A chart of dots: a habit's five weeks, 1 for a day done and 0 for a day missed. */
    @Test
    fun chartOfDots() = roundTrip(
        WidgetKind.CHART,
        base + mapOf(
            "points" to List(35) { if (it % 6 == 2) 0 else 1 }, "style" to "dots",
            "value" to 5, "label" to "days in a row", "summary" to "Done on 29 of the last 35 days",
        ),
    )

    @Test
    fun countdown() = roundTrip(
        WidgetKind.COUNTDOWN,
        base + mapOf(
            "to" to 1_797_000_000L,
            "from" to 1_790_000_000L,
            "label" to "Goa trip",
            "style" to "days",
            "reached" to "Today!"
        ),
    )

    /** A dot a day and a calendar page come back as they went. */
    @Test
    fun countdownDotsAndPage() {
        roundTrip(WidgetKind.COUNTDOWN, mapOf("to" to 1_797_000_000L, "from" to 1_790_000_000L, "style" to "dots"))
        roundTrip(WidgetKind.COUNTDOWN, mapOf("to" to 1_797_000_000L, "style" to "page"))
    }

    /** The smallest description of each kind comes back too. */
    @Test
    fun theLeastOfEachKind() {
        roundTrip(WidgetKind.STAT, mapOf("value" to "7"))
        roundTrip(WidgetKind.TEXT, mapOf("text" to "hello"))
        roundTrip(WidgetKind.LIST, mapOf("items" to emptyList<Any>()))
        roundTrip(WidgetKind.PROGRESS, mapOf("value" to 0.5))
        roundTrip(WidgetKind.CHART, mapOf("points" to listOf(1, 2)))
        roundTrip(WidgetKind.COUNTDOWN, mapOf("to" to 1_797_000_000L))
        roundTrip(
            WidgetKind.BUTTONS,
            mapOf("items" to listOf(mapOf("label" to "Go", "tap" to mapOf("args" to ""))))
        )
        roundTrip(
            WidgetKind.TOGGLES,
            mapOf(
                "items" to listOf(
                    mapOf(
                        "label" to "Mute",
                        "on" to false,
                        "tap" to mapOf("args" to "mute")
                    )
                )
            )
        )
        roundTrip(WidgetKind.CLOCK, mapOf("zones" to listOf(mapOf("zone" to "UTC"))))
        roundTrip(WidgetKind.AGENDA, mapOf("events" to emptyList<Any>()))
        roundTrip(
            WidgetKind.IMAGE,
            mapOf("image" to "https://example.com/a.png", "alt" to "A picture")
        )
        roundTrip(
            WidgetKind.VERSUS,
            mapOf("sides" to listOf(mapOf("name" to "Lisbon"), mapOf("name" to "Paris")), "score" to "0–0")
        )
        roundTrip(WidgetKind.SKY, mapOf("rises" to 1_800_000_000L, "sets" to 1_800_036_000L))
    }

    /** Every kind a script can use is in the tests above: a new kind needs its round trip. */
    @Test
    fun everyKindForScriptsIsHere() {
        val tested = setOf(
            "stat",
            "text",
            "list",
            "progress",
            "chart",
            "countdown",
            "buttons",
            "toggles",
            "clock",
            "agenda",
            "image",
            "versus",
            "sky"
        )
        assertEquals(tested, WidgetKind.forScripts.map { it.id }.toSet())
    }
}
