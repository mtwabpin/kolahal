package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ApiDocs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What `widget.show` accepts and refuses: the description a script hands over, read by [WidgetParser]. */
class WidgetParserTest {
    private fun parse(raw: Map<String, Any?>, kind: WidgetKind = WidgetKind.STAT) = WidgetParser.parse(raw, kind, { it }, { it })

    private fun refused(raw: Map<String, Any?>, kind: WidgetKind = WidgetKind.STAT): String? =
        assertThrows(IllegalArgumentException::class.java) { parse(raw, kind) }.message

    private fun stat(vararg fields: Pair<String, Any?>) = parse(mapOf("value" to "1") + fields) as WidgetSpec.Stat

    private fun list(vararg items: Any?) = parse(mapOf("items" to items.toList()), WidgetKind.LIST) as WidgetSpec.Rows

    // ---- the kinds ------------------------------------------------------------------------

    @Test fun aStatWithAllItsFields() {
        val spec = parse(
            mapOf(
                "title" to "Pune", "icon" to "☀️", "color" to "good", "staleAfter" to 7200, "refresh" to 30,
                "value" to "24°C", "label" to "Sunny", "unit" to "°C", "delta" to "+2.4%", "trend" to "up",
                "more" to listOf(mapOf("value" to "36%", "label" to "humidity"), mapOf("value" to "9 km/h")),
                "progress" to 0.5, "moving" to true,
            ),
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(title = "Pune", icon = "☀️", color = StyledText.Colour.GOOD, staleAfter = 7200, refresh = 30),
                value = "24°C", label = "Sunny", unit = "°C", delta = "+2.4%", trend = "up",
                more = listOf(WidgetSpec.Small("36%", "humidity"), WidgetSpec.Small("9 km/h", null)),
                progress = 0.5f, moving = true,
            ),
            spec,
        )
    }

    @Test fun aStatNeedsOnlyItsValue() {
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "7"), parse(mapOf("value" to "7")))
        assertEquals("widget: value is needed: the big value, as text or a number", refused(mapOf("label" to "unlocks")))
    }

    @Test fun aTextKeepsItsLineBreaks() {
        val spec = parse(mapOf("text" to "  one\r\ntwo  ", "caption" to "a\nb"), WidgetKind.TEXT)
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "one\ntwo", caption = "a b"), spec)
        assertEquals("widget: text is needed: the text to show", refused(mapOf("caption" to "x"), WidgetKind.TEXT))
    }

    @Test fun aListOfRows() {
        val spec = parse(
            mapOf(
                "items" to listOf(
                    mapOf("title" to "Story", "detail" to "by pg", "trailing" to 120, "icon" to "Y", "color" to "warn", "tap" to mapOf("url" to "https://example.com/1")),
                    mapOf("title" to "Another"),
                ),
                "empty" to "Nothing new",
            ),
            WidgetKind.LIST,
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(),
                items = listOf(
                    WidgetSpec.Row("Story", detail = "by pg", trailing = "120", icon = "Y", color = StyledText.Colour.WARN, tap = WidgetTap(url = "https://example.com/1")),
                    WidgetSpec.Row("Another"),
                ),
                empty = "Nothing new",
            ),
            spec,
        )
    }

    @Test fun aListNeedsItsItemsAndEachItemItsTitle() {
        assertTrue(refused(emptyMap(), WidgetKind.LIST)!!.startsWith("widget: items is needed: a list of rows"))
        assertEquals("widget: item 2 of items: title is needed: the row's text", refused(mapOf("items" to listOf(mapOf("title" to "a"), mapOf("detail" to "b"))), WidgetKind.LIST))
        assertEquals("widget: items must be a list of { title, detail, trailing, icon, color, tap }, not 'rows'", refused(mapOf("items" to "rows"), WidgetKind.LIST))
        assertEquals("widget: item 1 of items: must be { title, detail, trailing, icon, color, tap }, not 'a row'", refused(mapOf("items" to listOf("a row")), WidgetKind.LIST))
    }

    /** An empty Lua table arrives as an empty map: it has no keys to tell a list from a table. */
    @Test fun anEmptyLuaTableIsAnEmptyList() {
        assertEquals(WidgetSpec.Rows(WidgetBase(), items = emptyList()), parse(mapOf("items" to emptyMap<String, Any?>()), WidgetKind.LIST))
        assertEquals(emptyList<WidgetSpec.Small>(), stat("more" to emptyMap<String, Any?>()).more)
        // A table with keys is not a list.
        assertEquals("widget: more must be a list of { value, label }, not a table", refused(mapOf("value" to 1, "more" to mapOf("value" to 2))))
    }

    @Test fun theKindFieldWinsOverTheWidgetLine() {
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "hello"), parse(mapOf("kind" to "text", "text" to "hello"), WidgetKind.STAT))
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "1"), parse(mapOf("kind" to null, "value" to 1), WidgetKind.STAT))
        assertEquals("widget: no kind 'graph'; use stat, text, list, progress, chart, countdown", refused(mapOf("kind" to "graph")))
        assertEquals("widget: no kind 3; use stat, text, list, progress, chart, countdown", refused(mapOf("kind" to 3)))
    }

    /** Until they are built, the other kinds are an error that says so. */
    @Test fun aKindThatIsNotBuiltYet() {
        for (kind in listOf(WidgetKind.PROGRESS, WidgetKind.CHART, WidgetKind.COUNTDOWN)) {
            assertEquals("widget: the ${kind.id} kind can't be shown yet", refused(emptyMap(), kind))
        }
    }

    // ---- typed ----------------------------------------------------------------------------

    /** Lua's numbers arrive as whole numbers or doubles; a whole double shows without its ".0". */
    @Test fun aNumberWhereTextIsExpectedBecomesText() {
        assertEquals("24", stat("value" to 24).value)
        assertEquals("24", stat("value" to 24L).value)
        assertEquals("24", stat("value" to 24.0).value)
        assertEquals("24.5", stat("value" to 24.5).value)
        assertEquals("7", stat("title" to 7).base.title)
        assertEquals("3", stat("more" to listOf(mapOf("value" to 3.0, "label" to 2))).more.single().value)
    }

    @Test fun anythingElseIsAnErrorThatNamesTheField() {
        assertEquals("widget: value must be text, not true", refused(mapOf("value" to true)))
        assertEquals("widget: title must be text, not a table", refused(mapOf("value" to 1, "title" to mapOf("a" to 1))))
        assertEquals("widget: label must be text, not a list", refused(mapOf("value" to 1, "label" to listOf("a"))))
        assertEquals("widget: progress must be a number from 0 to 1, not 'half'", refused(mapOf("value" to 1, "progress" to "half")))
        assertEquals("widget: progress must be a number from 0 to 1, not NaN", refused(mapOf("value" to 1, "progress" to Double.NaN)))
        assertEquals("widget: moving must be true or false, not 1", refused(mapOf("value" to 1, "moving" to 1)))
        assertEquals("widget: trend must be up, down or flat, not 'rising'", refused(mapOf("value" to 1, "trend" to "rising")))
        assertEquals("widget: staleAfter must be a number of seconds, not '1h'", refused(mapOf("value" to 1, "staleAfter" to "1h")))
        assertEquals("widget: item 1 of more: value must be text, not false", refused(mapOf("value" to 1, "more" to listOf(mapOf("value" to false)))))
    }

    /** A field with no value is a field left out: JavaScript's `label: undefined` arrives as null. */
    @Test fun aFieldWithNoValueIsLeftOut() {
        val spec = stat("label" to null, "color" to null, "tap" to null, "more" to null, "progress" to null, "moving" to null, "refresh" to null)
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "1"), spec)
        assertEquals("widget: value is needed: the big value, as text or a number", refused(mapOf("value" to null)))
    }

    /** Text that is left out and text that is empty read the same, except where the text is needed. */
    @Test fun anEmptyTextIsNoText() {
        val spec = stat("title" to "  ", "label" to "", "icon" to "")
        assertNull(spec.base.title)
        assertNull(spec.label)
        assertNull(spec.base.icon)
        assertEquals("", parse(mapOf("value" to "")).let { it as WidgetSpec.Stat }.value)
    }

    @Test fun aColourIsARole() {
        assertEquals(StyledText.Colour.BAD, stat("color" to "bad").base.color)
        assertEquals(StyledText.Colour.GOOD, list(mapOf("title" to "a", "color" to "good")).items.single().color)
        val roles = "accent, link, info, muted, text, good, warn, bad"
        assertEquals("widget: no colour '#ff0000'; use $roles", refused(mapOf("value" to 1, "color" to "#ff0000")))
        assertEquals("widget: no colour 7; use $roles", refused(mapOf("value" to 1, "color" to 7)))
        assertEquals("widget: item 1 of items: no colour 'pink'; use $roles", refused(mapOf("items" to listOf(mapOf("title" to "a", "color" to "pink"))), WidgetKind.LIST))
    }

    // ---- closed ---------------------------------------------------------------------------

    @Test fun anUnknownFieldListsTheKnownOnes() {
        assertEquals(
            "widget: a stat widget has no field 'colour'; use kind, title, icon, color, tap, staleAfter, refresh, value, label, unit, delta, trend, more, progress, moving",
            refused(mapOf("value" to 1, "colour" to "good")),
        )
        // Another kind's field is unknown too.
        assertEquals(
            "widget: a text widget has no field 'value'; use kind, title, icon, color, tap, staleAfter, refresh, text, caption",
            refused(mapOf("text" to "a", "value" to 1), WidgetKind.TEXT),
        )
        assertEquals(
            "widget: item 2 of items: no field 'subtitle'; use title, detail, trailing, icon, color, tap",
            refused(mapOf("items" to listOf(mapOf("title" to "a"), mapOf("title" to "b", "subtitle" to "c"))), WidgetKind.LIST),
        )
        assertEquals("widget: item 1 of more: no field 'unit'; use value, label", refused(mapOf("value" to 1, "more" to listOf(mapOf("value" to 2, "unit" to "km")))))
        // A name with no value is still a name nobody knows.
        assertTrue(refused(mapOf("value" to 1, "colour" to null))!!.startsWith("widget: a stat widget has no field 'colour'"))
        // A list where a table is expected: its places are no fields.
        assertTrue(refused(mapOf("1" to "a", "2" to "b"))!!.startsWith("widget: a stat widget has no field '1', '2'"))
    }

    /** A script can't put a command behind a tap: there is no such field. */
    @Test fun aTapTakesNoCommand() {
        assertEquals("widget: tap: no field 'command'; use args, url, label, page", refused(mapOf("value" to 1, "tap" to mapOf("command" to "torch"))))
        assertEquals("widget: tap: no field 'run'; use args, url, label, page", refused(mapOf("value" to 1, "tap" to mapOf("args" to "", "run" to "clear all"))))
    }

    // ---- bounded --------------------------------------------------------------------------

    @Test fun textIsCutAtItsLimit() {
        val long = "x".repeat(600)
        val spec = stat("title" to long, "icon" to "abcdef", "tap" to mapOf("args" to long, "label" to long))
        assertEquals("x".repeat(39) + "…", spec.base.title)
        assertEquals("abc…", spec.base.icon)
        assertEquals("x".repeat(23) + "…", spec.base.tap!!.label)
        assertEquals("x".repeat(199) + "…", spec.base.tap!!.args)
        val text = parse(mapOf("text" to long, "caption" to long), WidgetKind.TEXT) as WidgetSpec.Text
        assertEquals("x".repeat(499) + "…", text.text)
        assertEquals("x".repeat(59) + "…", text.caption)
        // At the limit, nothing is cut.
        assertEquals("y".repeat(40), stat("title" to "y".repeat(40)).base.title)
        assertEquals("☀️🌧", stat("icon" to "☀️🌧").base.icon)
    }

    /** A cut never ends in half an emoji. */
    @Test fun aCutKeepsAnEmojiWhole() {
        assertEquals("ab…", stat("icon" to "ab🌧🌧").base.icon)
    }

    @Test fun aListKeepsItsFirstItems() {
        val rows = list(*(1..20).map { mapOf("title" to "row $it") }.toTypedArray())
        assertEquals((1..12).map { "row $it" }, rows.items.map { it.title })
        val more = stat("more" to (1..5).map { mapOf("value" to it) }).more
        assertEquals(listOf("1", "2", "3"), more.map { it.value })
        // What isn't kept isn't checked.
        assertEquals(12, list(*((1..12).map { mapOf("title" to "row $it") } + listOf("not a row")).toTypedArray()).items.size)
    }

    @Test fun staleAfterAndRefreshStayInTheirRange() {
        fun base(vararg fields: Pair<String, Any?>) = stat(*fields).base
        assertEquals(60, base("staleAfter" to 5).staleAfter)
        assertEquals(604_800, base("staleAfter" to 9_000_000).staleAfter)
        assertEquals(7200, base("staleAfter" to 7200.0).staleAfter)
        assertEquals(10, base("refresh" to 1).refresh)
        assertEquals(3600, base("refresh" to 86_400).refresh)
        assertEquals(30, base("refresh" to 30.9).refresh)
        // Zero or less asks for none.
        assertNull(base("refresh" to 0).refresh)
        assertNull(base("staleAfter" to -1).staleAfter)
    }

    @Test fun progressStaysFromZeroToOne() {
        assertEquals(1f, stat("progress" to 3).progress)
        assertEquals(0f, stat("progress" to -0.5).progress)
        assertEquals(0.25f, stat("progress" to 0.25).progress)
    }

    // ---- taps -----------------------------------------------------------------------------

    @Test fun aTapRunsTheScriptAgainOrOpensALink() {
        assertEquals(WidgetTap(args = "add 1", label = "Add a glass"), stat("tap" to mapOf("args" to "add 1", "label" to "Add a glass")).base.tap)
        assertEquals(WidgetTap(url = "https://example.com/"), stat("tap" to mapOf("url" to " https://example.com/ ")).base.tap)
        // Arguments are one line, and a number is text.
        assertEquals(WidgetTap(args = "a b"), stat("tap" to mapOf("args" to "a\nb")).base.tap)
        assertEquals(WidgetTap(args = "2"), stat("tap" to mapOf("args" to 2)).base.tap)
    }

    /** `{ args = "", page = "log" }` runs the script with no arguments and turns home to the log. */
    @Test fun emptyArgumentsAreArguments() {
        assertEquals(WidgetTap(args = "", toLog = true), stat("tap" to mapOf("args" to "", "page" to "log")).base.tap)
        assertEquals(WidgetTap(args = ""), stat("tap" to mapOf("args" to "")).base.tap)
    }

    @Test fun aTapIsExactlyOneThing() {
        fun tap(vararg fields: Pair<String, Any?>) = refused(mapOf("value" to 1, "tap" to mapOf(*fields)))
        assertEquals("widget: tap: give args or url, not both", tap("args" to "a", "url" to "https://example.com"))
        assertEquals("widget: tap: give args or url", tap("label" to "Open"))
        assertEquals("widget: tap: give args or url", refused(mapOf("value" to 1, "tap" to emptyMap<String, Any?>())))
        assertEquals("widget: tap: page goes with args, not with url", tap("url" to "https://example.com", "page" to "log"))
        assertEquals("widget: tap: page must be 'log', not 'widgets'", tap("args" to "", "page" to "widgets"))
        assertEquals("widget: tap: url must be a web link, not 7", tap("url" to 7))
        assertEquals("widget: tap must be { args } or { url }, not 'weather'", refused(mapOf("value" to 1, "tap" to "weather")))
        assertEquals("widget: item 1 of items: tap: give args or url", refused(mapOf("items" to listOf(mapOf("title" to "a", "tap" to mapOf("label" to "x")))), WidgetKind.LIST))
    }

    // ---- what the host checks -------------------------------------------------------------

    /** The host masks secrets and removes styles in every text, and checks every link. */
    @Test fun everyTextAndEveryLinkGoesThroughTheHost() {
        val texts = ArrayList<String>()
        val links = ArrayList<String>()
        val spec = WidgetParser.parse(
            mapOf(
                "title" to "t", "icon" to "i", "tap" to mapOf("url" to "https://example.com/card", "label" to "tl"),
                "items" to listOf(mapOf("title" to "rt", "detail" to "rd", "trailing" to "rr", "icon" to "ri", "tap" to mapOf("args" to "ra"))),
                "empty" to "e",
            ),
            WidgetKind.LIST,
            text = { texts += it; it.uppercase() },
            link = { links += it; "checked:$it" },
        ) as WidgetSpec.Rows
        assertEquals(setOf("t", "i", "tl", "rt", "rd", "rr", "ri", "ra", "e"), texts.toSet())
        assertEquals(listOf("https://example.com/card"), links)
        assertEquals("T", spec.base.title)
        assertEquals(WidgetTap(url = "checked:https://example.com/card", label = "TL"), spec.base.tap)
        assertEquals(WidgetSpec.Row("RT", detail = "RD", trailing = "RR", icon = "RI", tap = WidgetTap(args = "RA")), spec.items.single())
        assertEquals("E", spec.empty)

        val stat = WidgetParser.parse(
            mapOf("value" to "v", "label" to "l", "unit" to "u", "delta" to "d", "more" to listOf(mapOf("value" to "mv", "label" to "ml"))),
            WidgetKind.STAT, text = { it.uppercase() }, link = { it },
        )
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "V", label = "L", unit = "U", delta = "D", more = listOf(WidgetSpec.Small("MV", "ML"))), stat)
        val text = WidgetParser.parse(mapOf("text" to "x", "caption" to "c"), WidgetKind.TEXT, text = { it.uppercase() }, link = { it })
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "X", caption = "C"), text)
    }

    /** A secret is masked before the cut. A cut first could leave half a secret, which nothing masks. */
    @Test fun aTextIsMaskedBeforeItIsCut() {
        val title = "x".repeat(36) + "SECRET"
        val spec = WidgetParser.parse(mapOf("value" to 1, "title" to title), WidgetKind.STAT, text = { it.replace("SECRET", "•••") }, link = { it })
        assertEquals("x".repeat(36) + "•••", spec.base.title)
    }

    /** A refused link stops the run with the host's own error. */
    @Test fun aRefusedLinkIsTheHostsError() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            WidgetParser.parse(mapOf("value" to 1, "tap" to mapOf("url" to "tel:112")), WidgetKind.STAT, text = { it }, link = { throw IllegalArgumentException("widget: not a web link") })
        }
        assertEquals("widget: not a web link", e.message)
    }

    // ---- names ----------------------------------------------------------------------------

    /**
     * A script writes `{ title = … }`, so no field is a Lua keyword. And the words `status` and
     * `notify` in a script's code switch on access items, so no field has those names.
     */
    @Test fun noFieldIsALuaKeywordOrAnAccessWord() {
        assertEquals(emptySet<String>(), WidgetParser.FIELDS intersect (ApiDocs.luaKeywords + listOf("status", "notify")).toSet())
        assertTrue(WidgetParser.FIELDS.containsAll(listOf("kind", "title", "icon", "color", "tap", "staleAfter", "refresh", "args", "url", "label", "page", "value", "more", "text", "caption", "items", "empty", "detail", "trailing")))
        assertTrue(WidgetParser.FIELDS.all { Regex("[a-z][A-Za-z]*").matches(it) })
    }

    /** Every name an error lists as known is in FIELDS, so the test above covers them all. */
    @Test fun everyKnownFieldIsInFields() {
        val listed = Regex("; use (.+)$")
        val errors = listOf(
            refused(mapOf("nope" to 1), WidgetKind.STAT), refused(mapOf("nope" to 1), WidgetKind.TEXT), refused(mapOf("nope" to 1), WidgetKind.LIST),
            refused(mapOf("value" to 1, "tap" to mapOf("nope" to 1))), refused(mapOf("value" to 1, "more" to listOf(mapOf("nope" to 1)))),
            refused(mapOf("items" to listOf(mapOf("nope" to 1))), WidgetKind.LIST),
        )
        val known = errors.flatMap { listed.find(it!!)!!.groupValues[1].split(", ") }.toSet()
        assertEquals(WidgetParser.FIELDS, known)
    }
}
