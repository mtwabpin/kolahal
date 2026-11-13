package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored form of a widget's description ([WidgetJson]). What `widget.show` checked comes back
 * the same, and anything else comes back as nothing: the main process never trusts stored JSON.
 */
class WidgetSpecJsonTest {
    private val fullBase = WidgetBase(
        title = "Weather",
        icon = "☀",
        color = StyledText.Colour.WARN,
        tap = WidgetTap(args = "full report", label = "Open the report", toLog = true),
        staleAfter = 7200,
        refresh = 60,
    )
    private val bare = WidgetBase()

    private fun roundTrip(spec: WidgetSpec) {
        val json = WidgetJson.encode(spec)
        assertTrue("encoded: $json", json.startsWith("{"))
        assertEquals(spec, WidgetJson.decode(json))
        // The same description gives the same text, so an equal write can be skipped.
        assertEquals(json, WidgetJson.encode(spec))
    }

    /** The stored JSON of [spec], with [change] applied to its fields. */
    private fun changed(spec: WidgetSpec, change: MutableMap<String, Any?>.() -> Unit): String {
        @Suppress("UNCHECKED_CAST")
        val map = (Json.parse(WidgetJson.encode(spec)) as Map<String, Any?>).toMutableMap()
        map.change()
        return Json.stringify(map)
    }

    private val stat = WidgetSpec.Stat(bare, value = "23°")
    private val text = WidgetSpec.Text(bare, text = "Hello")
    private val rows = WidgetSpec.Rows(bare, items = listOf(WidgetSpec.Row("One")))
    private val progress = WidgetSpec.Progress(bare, value = 0.5f)
    private val chart = WidgetSpec.Chart(bare, points = listOf(1f, 2f, 3f))
    private val countdown = WidgetSpec.Countdown(bare, to = 1_800_000_000L)

    @Test fun statRoundTrips() {
        roundTrip(stat)
        roundTrip(
            WidgetSpec.Stat(
                fullBase, value = "23°", label = "Paris", unit = "C", delta = "+2.4%", trend = "up",
                more = listOf(WidgetSpec.Small("12°", "low"), WidgetSpec.Small("68%", null), WidgetSpec.Small("", "")),
                progress = 0.68f, moving = true,
            )
        )
        roundTrip(stat.copy(progress = 0f, trend = "flat"))
        roundTrip(stat.copy(progress = 1f, trend = "down"))
    }

    @Test fun textRoundTrips() {
        roundTrip(text)
        roundTrip(WidgetSpec.Text(fullBase, text = "First line\nSecond \"quoted\" line \\ with a slash\n\tand a tab — 日本語 👨‍👩‍👧", caption = "An author"))
        roundTrip(WidgetSpec.Text(bare, text = "x".repeat(500), caption = "c".repeat(60)))
    }

    @Test fun listRoundTrips() {
        roundTrip(rows)
        roundTrip(WidgetSpec.Rows(bare, items = emptyList(), empty = "Nothing new"))
        roundTrip(
            WidgetSpec.Rows(
                fullBase,
                items = listOf(
                    WidgetSpec.Row("A story", detail = "120 points", trailing = "2h", icon = "1", color = StyledText.Colour.GOOD, tap = WidgetTap(url = "https://example.com/a?b=1&c=2")),
                    WidgetSpec.Row("Another", tap = WidgetTap(args = "open 2", label = "Open")),
                    WidgetSpec.Row("Plain"),
                ),
                empty = "Nothing new",
            )
        )
        roundTrip(WidgetSpec.Rows(bare, items = (1..12).map { WidgetSpec.Row("Row $it") }))
    }

    @Test fun progressRoundTrips() {
        roundTrip(progress)
        roundTrip(WidgetSpec.Progress(fullBase, value = 68f, max = 100f, text = "68%", label = "Battery", style = "ring", moving = true))
        roundTrip(
            WidgetSpec.Progress(
                bare, value = 0f, style = "bar",
                items = listOf(
                    WidgetSpec.Bar("Mon", 3.5f, 8f, "3.5 h", StyledText.Colour.ACCENT),
                    WidgetSpec.Bar(null, 0f, 1f, null, null),
                    WidgetSpec.Bar("Wed", -2f, 0.1f, "", StyledText.Colour.BAD),
                    WidgetSpec.Bar("Thu", 1e10f, 3.4e38f, null, StyledText.Colour.MUTED),
                ),
            )
        )
    }

    @Test fun chartRoundTrips() {
        roundTrip(chart)
        roundTrip(WidgetSpec.Chart(bare, points = emptyList()))
        roundTrip(
            WidgetSpec.Chart(
                fullBase, points = listOf(0f, -1.5f, 0.1f, 1234.5677f, 1e-7f, 3f), style = "bars", min = -2f, max = 2000f,
                value = "1,234", label = "Steps", labels = listOf("Mon", "Sun"), summary = "Rising all week",
            )
        )
        roundTrip(WidgetSpec.Chart(bare, points = (1..60).map { it / 7f }))
    }

    @Test fun countdownRoundTrips() {
        roundTrip(countdown)
        roundTrip(WidgetSpec.Countdown(fullBase, to = 1_800_000_000L, from = 1_700_000_000L, label = "Trip", style = "days", reached = "Today!"))
        // A moment before 1970, and "clock".
        roundTrip(WidgetSpec.Countdown(bare, to = -300_000_000L, from = -400_000_000L, style = "clock"))
    }

    @Test fun everyColourAndTapFormRoundTrips() {
        StyledText.Colour.entries.forEach { roundTrip(text.copy(base = WidgetBase(color = it))) }
        roundTrip(text.copy(base = WidgetBase(tap = WidgetTap(args = "add 1"))))
        roundTrip(text.copy(base = WidgetBase(tap = WidgetTap(args = ""))))
        roundTrip(text.copy(base = WidgetBase(tap = WidgetTap(url = "http://example.com", label = "Open the site"))))
        roundTrip(text.copy(base = WidgetBase(staleAfter = 60, refresh = 10)))
        roundTrip(text.copy(base = WidgetBase(staleAfter = 604_800, refresh = 3_600)))
    }

    /** The keys are the script's field names, plus the format number and the kind. Empty fields are left out. */
    @Test fun theStoredFormUsesTheScriptsFieldNames() {
        val small = Json.parse(WidgetJson.encode(stat)) as Map<*, *>
        assertEquals(setOf("v", "kind", "value"), small.keys)
        assertEquals(WidgetJson.FORMAT, (small["v"] as Number).toInt())
        assertEquals("stat", small["kind"])

        val full = Json.parse(WidgetJson.encode(WidgetSpec.Rows(fullBase, items = listOf(WidgetSpec.Row("A", tap = WidgetTap(url = "https://a.example"))), empty = "None"))) as Map<*, *>
        assertEquals(setOf("v", "kind", "title", "icon", "color", "tap", "staleAfter", "refresh", "items", "empty"), full.keys)
        assertEquals("list", full["kind"])
        assertEquals("warn", full["color"])
        assertEquals(mapOf("args" to "full report", "label" to "Open the report", "page" to "log"), full["tap"])
        assertEquals(listOf(mapOf("title" to "A", "tap" to mapOf("url" to "https://a.example"))), full["items"])

        // A float is stored as it is written, not in its long double form.
        assertTrue(WidgetJson.encode(progress.copy(value = 0.68f)).contains("0.68"))
        assertTrue(!WidgetJson.encode(progress.copy(value = 0.68f)).contains("0.680000"))
        assertEquals(setOf("v", "kind", "value", "max"), (Json.parse(WidgetJson.encode(progress)) as Map<*, *>).keys)
        assertEquals(setOf("v", "kind", "points", "style"), (Json.parse(WidgetJson.encode(chart)) as Map<*, *>).keys)
        assertEquals(setOf("v", "kind", "to"), (Json.parse(WidgetJson.encode(countdown)) as Map<*, *>).keys)
        assertEquals(setOf("v", "kind", "text"), (Json.parse(WidgetJson.encode(text)) as Map<*, *>).keys)
    }

    @Test fun rubbishGivesNull() {
        val rubbish = listOf(
            null, "", " ", "null", "true", "12", "\"text\"", "[]", "[1,2]", "{}", "{", "}", "{\"v\":1", "not json", "<html></html>",
            "{\"v\":1}", "{\"kind\":\"stat\",\"value\":\"1\"}",
            "{\"v\":1,\"kind\":\"stat\"}",
            "{\"v\":1,\"kind\":\"hologram\",\"value\":\"1\"}",
            "{\"v\":1,\"kind\":\"STAT\",\"value\":\"1\"}",
            "{\"v\":1,\"kind\":7,\"value\":\"1\"}",
            "{\"v\":\"1\",\"kind\":\"stat\",\"value\":\"1\"}",
            "{\"v\":2,\"kind\":\"stat\",\"value\":\"1\"}",
            "{\"v\":0,\"kind\":\"stat\",\"value\":\"1\"}",
            "{\"v\":1,\"kind\":\"stat\",\"value\":\"1\"} trailing {",
            "\u0000\u0001\u0002",
        )
        rubbish.forEach { assertNull("decoded: $it", WidgetJson.decode(it)) }
        // Deep nesting ends the reader, not the app.
        assertNull(WidgetJson.decode("[".repeat(50_000)))
        assertNull(WidgetJson.decode("{\"a\":".repeat(20_000)))
        // Far longer than `widget.show` allows.
        assertNull(WidgetJson.decode(changed(text) { put("text", "x".repeat(WidgetJson.MAX_TEXT + 1)) }))
        assertNotNull(WidgetJson.decode(changed(text) { put("text", "x".repeat(WidgetJson.MAX_TEXT)) }))
        assertNull(WidgetJson.decode(changed(rows) { put("pad", "x".repeat(WidgetJson.MAX_JSON)) }))
    }

    /** One check for each field: the wrong type is refused, not converted. */
    @Test fun aFieldOfTheWrongTypeGivesNull() {
        fun refused(spec: WidgetSpec, key: String, vararg values: Any?) = values.forEach { v ->
            assertNull("$key = $v", WidgetJson.decode(changed(spec) { put(key, v) }))
        }
        val map = mapOf("a" to 1)
        val list = listOf(1)
        // Every kind's own fields.
        refused(stat, "value", 23, true, map, list)
        refused(stat, "label", 1, false, map)
        refused(stat, "unit", 1, list)
        refused(stat, "delta", 2.5, map)
        refused(stat, "trend", "sideways", "UP", 1, true)
        refused(stat, "more", "x", 1, map, listOf("x"), listOf(mapOf("label" to "no value")), listOf(mapOf("value" to 1)), listOf(mapOf("value" to "1", "label" to 2)))
        refused(stat, "progress", "0.5", true, map)
        refused(stat, "moving", "true", 1, map)
        refused(text, "text", 1, true, list)
        refused(text, "caption", 1, map)
        refused(rows, "items", "x", 1, map, listOf("x"), listOf(mapOf("detail" to "no title")), listOf(mapOf("title" to 1)), listOf(mapOf("title" to "a", "color" to "pink")))
        refused(rows, "items", listOf(mapOf("title" to "a", "tap" to "x")), listOf(mapOf("title" to "a", "tap" to mapOf("label" to "nothing to do"))))
        refused(rows, "empty", 1, list)
        refused(progress, "value", "0.5", true, map)
        refused(progress, "max", "1", 0, -1, list)
        refused(progress, "text", 1, map)
        refused(progress, "style", "circle", "Ring", 1)
        refused(progress, "items", "x", map, listOf(1), listOf(mapOf("label" to "no value")), listOf(mapOf("value" to "1")), listOf(mapOf("value" to 1, "max" to 0)), listOf(mapOf("value" to 1, "color" to "pink")))
        refused(progress, "moving", "yes", 0)
        refused(chart, "points", "1,2", 1, map, listOf("1"), listOf(1, null), listOf(1, true), listOf(listOf(1)))
        refused(chart, "style", "pie", "Line", 1)
        refused(chart, "min", "0", true)
        refused(chart, "max", "9", list)
        refused(chart, "value", 12, map)
        refused(chart, "labels", "Mon", 1, map, listOf(1, 2), listOf("Mon", 2))
        refused(chart, "summary", 1, list)
        refused(countdown, "to", "tomorrow", true, map, 1e30, -1e30)
        refused(countdown, "from", "then", list, 1e30)
        refused(countdown, "style", "weeks", "Days", 1)
        refused(countdown, "reached", 1, map)
        // The fields every kind has.
        refused(text, "title", 1, true, map)
        refused(text, "icon", 1, list)
        refused(text, "color", "pink", "#ff0000", "ACCENT", " accent", 1, map)
        refused(text, "staleAfter", "3600", true, map)
        refused(text, "refresh", "60", false, list)
        // A needed field that is missing.
        listOf(stat to "value", text to "text", rows to "items", progress to "value", chart to "points", countdown to "to").forEach { (spec, key) ->
            assertNull("no $key", WidgetJson.decode(changed(spec) { remove(key) }))
        }
    }

    /** A tap has exactly one of `args` and `url`, and a link is a web link. Nothing else is ever opened from a stored description. */
    @Test fun aTapIsCheckedAgain() {
        fun tap(vararg fields: Pair<String, Any?>) = WidgetJson.decode(changed(text) { put("tap", mapOf(*fields)) })
        assertEquals(WidgetTap(args = "add 1"), tap("args" to "add 1")!!.base.tap)
        assertEquals(WidgetTap(url = "HTTPS://Example.com"), tap("url" to "HTTPS://Example.com")!!.base.tap)
        assertEquals(WidgetTap(args = "report", toLog = true), tap("args" to "report", "page" to "log")!!.base.tap)
        // An unknown key is skipped, as a later version may add one.
        assertEquals(WidgetTap(args = "x"), tap("args" to "x", "later" to 1)!!.base.tap)
        assertNull(tap())
        assertNull(tap("label" to "nothing to do"))
        assertNull(tap("args" to "add 1", "url" to "https://example.com"))
        assertNull(tap("args" to 1))
        assertNull(tap("url" to 1))
        assertNull(tap("args" to "x", "label" to 1))
        assertNull(tap("args" to "x", "page" to "home"))
        assertNull(tap("args" to "x", "page" to true))
        listOf(
            "intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/a.txt", "content://contacts/1", "javascript:alert(1)",
            "tel:123", "market://details?id=a", "example.com", "//example.com", " https://example.com", "ftp://example.com", "",
        ).forEach { assertNull("url: $it", tap("url" to it)) }
        assertNull(WidgetJson.decode(changed(text) { put("tap", "add 1") }))
        assertNull(WidgetJson.decode(changed(text) { put("tap", listOf("add 1")) }))
        // A command is not an action: a description can't plant one.
        assertNull(tap("command" to "torch on"))
    }

    /** A number outside its range is brought into it, and a list over its limit is cut as `widget.show` cuts it. */
    @Test fun rangesAndLimitsHoldAgain() {
        fun base(key: String, value: Any?) = WidgetJson.decode(changed(text) { put(key, value) })!!.base
        assertEquals(WidgetJson.MIN_STALE, base("staleAfter", 1).staleAfter)
        assertEquals(WidgetJson.MAX_STALE, base("staleAfter", 99_999_999).staleAfter)
        assertEquals(WidgetJson.MIN_REFRESH, base("refresh", 0).refresh)
        assertEquals(WidgetJson.MIN_REFRESH, base("refresh", -5).refresh)
        assertEquals(WidgetJson.MAX_REFRESH, base("refresh", 86_400).refresh)
        assertEquals(90, base("refresh", 90.0).refresh)
        assertEquals(1f, (WidgetJson.decode(changed(stat) { put("progress", 7) }) as WidgetSpec.Stat).progress)
        assertEquals(0f, (WidgetJson.decode(changed(stat) { put("progress", -0.5) }) as WidgetSpec.Stat).progress)

        val manyRows = WidgetJson.decode(changed(rows) { put("items", (1..40).map { mapOf("title" to "Row $it") }) }) as WidgetSpec.Rows
        assertEquals((1..WidgetJson.MAX_ROWS).map { "Row $it" }, manyRows.items.map { it.title })
        val more = WidgetJson.decode(changed(stat) { put("more", (1..9).map { mapOf("value" to "$it") }) }) as WidgetSpec.Stat
        assertEquals(listOf("1", "2", "3"), more.more.map { it.value })
        val bars = WidgetJson.decode(changed(progress) { put("items", (1..9).map { mapOf("value" to it) }) }) as WidgetSpec.Progress
        assertEquals(listOf(1f, 2f, 3f, 4f), bars.items.map { it.value })
        assertEquals(listOf(1f, 1f, 1f, 1f), bars.items.map { it.max })
        // A chart keeps its last points: the newest.
        val points = WidgetJson.decode(changed(chart) { put("points", (1..100).toList()) }) as WidgetSpec.Chart
        assertEquals((41..100).map { it.toFloat() }, points.points)
        val labels = WidgetJson.decode(changed(chart) { put("labels", listOf("a", "b", "c")) }) as WidgetSpec.Chart
        assertEquals(listOf("a", "b"), labels.labels)
    }

    /** What a field has when the stored form leaves it out. */
    @Test fun missingFieldsGetTheirDefaults() {
        assertEquals(WidgetSpec.Progress(bare, value = 3f, max = 1f), WidgetJson.decode("{\"v\":1,\"kind\":\"progress\",\"value\":3}"))
        assertEquals(WidgetSpec.Chart(bare, points = listOf(1f, 2.5f), style = "line"), WidgetJson.decode("{\"v\":1,\"kind\":\"chart\",\"points\":[1,2.5]}"))
        assertEquals(WidgetSpec.Stat(bare, value = "7"), WidgetJson.decode("{\"v\":1,\"kind\":\"stat\",\"value\":\"7\",\"label\":null,\"more\":null,\"moving\":null,\"tap\":null}"))
        assertEquals(WidgetSpec.Rows(bare, items = emptyList()), WidgetJson.decode("{\"v\":1,\"kind\":\"list\",\"items\":[]}"))
        // A key from a later version is skipped.
        assertEquals(WidgetSpec.Text(bare, text = "Hi"), WidgetJson.decode("{\"v\":1,\"kind\":\"text\",\"text\":\"Hi\",\"later\":{\"a\":[1,2]}}"))
    }

    /** A number that JSON can't hold never reaches the store as a number: the description reads back as nothing. */
    @Test fun numbersThatAreNotFiniteAreNotStored() {
        assertNull(WidgetJson.decode(WidgetJson.encode(progress.copy(value = Float.NaN))))
        assertEquals(progress, WidgetJson.decode(WidgetJson.encode(progress.copy(max = Float.POSITIVE_INFINITY))))
        assertNull(WidgetJson.decode(WidgetJson.encode(chart.copy(points = listOf(1f, Float.NaN, 3f)))))
        // An optional one is left out.
        assertEquals(stat, WidgetJson.decode(WidgetJson.encode(stat.copy(progress = Float.NaN))))
        assertEquals(chart, WidgetJson.decode(WidgetJson.encode(chart.copy(min = Float.NEGATIVE_INFINITY))))
    }
}
