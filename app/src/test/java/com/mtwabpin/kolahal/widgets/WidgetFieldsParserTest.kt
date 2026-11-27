package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fields of Kolahal 0.5.22: a badge and a footer on a card, a stat's target, a button's and a
 * switch's value, a progress card's stage details and a versus side's detail. Each is optional, has
 * its limit, and is stored and read back. A card stored with one reads on an older Kolahal, which
 * skips the keys it doesn't know.
 */
class WidgetFieldsParserTest {
    private fun parse(kind: WidgetKind, raw: Map<String, Any?>): WidgetSpec =
        WidgetParser.parse(raw, kind, { it }, { it })

    private fun refused(kind: WidgetKind, raw: Map<String, Any?>): String? =
        assertThrows(IllegalArgumentException::class.java) { parse(kind, raw) }.message

    private fun stored(spec: WidgetSpec): WidgetSpec? = WidgetJson.decode(WidgetJson.encode(spec))

    private val stat = mapOf("value" to "6,240")
    private val button = mapOf("label" to "Warmer", "tap" to mapOf("args" to "up"))
    private val switch = mapOf("label" to "Heating", "on" to true, "tap" to mapOf("args" to "heat"))
    private val stages = mapOf("stages" to listOf("Ordered", "Shipped", "Out", "Delivered"), "stage" to 3)
    private val sides = listOf(mapOf("name" to "North"), mapOf("name" to "South"))

    @Test
    fun aBadgeIsAFewCharactersOnEveryKindButButtonsAndSwitches() {
        assertEquals("live", parse(WidgetKind.STAT, stat + ("badge" to "live")).base.badge)
        assertEquals("3", parse(WidgetKind.LIST, mapOf("items" to emptyList<Any>(), "badge" to 3)).base.badge)
        val long = parse(WidgetKind.CHART, mapOf("points" to listOf(1, 2), "badge" to "Saturday!")).base.badge!!
        assertEquals(WidgetParser.MAX_BADGE, long.length)
        assertTrue(long.endsWith("…"))
        assertNull(parse(WidgetKind.STAT, stat).base.badge)
        assertEquals(
            "widget: item 1 of items: no field 'badge'; use label, icon, color, tap, value",
            refused(WidgetKind.BUTTONS, mapOf("items" to listOf(button + ("badge" to "3"))))
        )
        assertTrue(
            refused(WidgetKind.BUTTONS, mapOf("badge" to "3", "items" to listOf(button)))!!
                .startsWith("widget: a buttons widget has no field 'badge'; use kind, title, icon, color, shape, tap, staleAfter, refresh, note, footer, items")
        )
        assertTrue(
            refused(WidgetKind.TOGGLES, mapOf("badge" to "3", "items" to listOf(switch)))!!
                .startsWith("widget: a toggles widget has no field 'badge'")
        )
        assertEquals("widget: badge must be text, not a table", refused(WidgetKind.STAT, stat + ("badge" to mapOf("a" to 1))))
    }

    /** A versus card keeps its badge as its own, as before every kind had one: its base has none. */
    @Test
    fun aVersusCardKeepsItsOwnBadge() {
        val spec = parse(WidgetKind.VERSUS, mapOf("sides" to sides, "score" to "2–1", "badge" to "67'")) as WidgetSpec.Versus
        assertEquals("67'", spec.badge)
        assertNull(spec.base.badge)
        assertEquals("67'", spec.cardBadge)
        assertEquals(spec, stored(spec))
    }

    @Test
    fun aFooterIsOneLineOfPlainTextOnEveryKind() {
        assertEquals("City sensors", parse(WidgetKind.STAT, stat + ("footer" to "city sensors")).base.footer)
        assertEquals("Source", parse(WidgetKind.BUTTONS, mapOf("footer" to "Source", "items" to listOf(button))).base.footer)
        assertEquals("A b", parse(WidgetKind.TEXT, mapOf("text" to "x", "footer" to "a\nb")).base.footer)
        val long = parse(WidgetKind.TEXT, mapOf("text" to "x", "footer" to "x".repeat(100))).base.footer!!
        assertEquals(WidgetParser.MAX_FOOTER, long.length)
        assertTrue(long.endsWith("…"))
        assertNull(parse(WidgetKind.TEXT, mapOf("text" to "x", "footer" to "")).base.footer)
        assertEquals("widget: footer must be text, not true", refused(WidgetKind.STAT, stat + ("footer" to true)))
    }

    @Test
    fun aTargetLetsProgressGoPastIt() {
        val over = parse(WidgetKind.STAT, stat + mapOf("target" to 8000, "progress" to 1.12)) as WidgetSpec.Stat
        assertEquals("8000", over.target)
        assertEquals(1.12f, over.progress)
        assertEquals(2f, (parse(WidgetKind.STAT, stat + mapOf("target" to "8,000", "progress" to 3)) as WidgetSpec.Stat).progress)
        // Without a target, progress is held at 1, as before.
        assertEquals(1f, (parse(WidgetKind.STAT, stat + ("progress" to 1.12)) as WidgetSpec.Stat).progress)
        assertNull((parse(WidgetKind.STAT, stat) as WidgetSpec.Stat).target)
        val long = (parse(WidgetKind.STAT, stat + ("target" to "1".repeat(30))) as WidgetSpec.Stat).target!!
        assertEquals(WidgetParser.MAX_TARGET, long.length)
        assertEquals(
            "widget: progress must be a number from 0 to 2, not 'lots'",
            refused(WidgetKind.STAT, stat + mapOf("target" to "8,000", "progress" to "lots"))
        )
        assertEquals("widget: progress must be a number from 0 to 1, not 'lots'", refused(WidgetKind.STAT, stat + ("progress" to "lots")))
        assertEquals("widget: target must be text, not a list", refused(WidgetKind.STAT, stat + ("target" to listOf(1))))
    }

    @Test
    fun aButtonAndASwitchTakeAShortValue() {
        val b = (parse(WidgetKind.BUTTONS, mapOf("items" to listOf(button + ("value" to "21°")))) as WidgetSpec.Buttons).items.single()
        assertEquals("21°", b.value)
        val s = (parse(WidgetKind.TOGGLES, mapOf("items" to listOf(switch + ("value" to 40)))) as WidgetSpec.Toggles).items.single()
        assertEquals("40", s.value)
        val long = (parse(WidgetKind.TOGGLES, mapOf("items" to listOf(switch + ("value" to "a very long reading")))) as WidgetSpec.Toggles).items.single().value!!
        assertEquals(WidgetParser.MAX_ITEM_VALUE, long.length)
        assertNull((parse(WidgetKind.BUTTONS, mapOf("items" to listOf(button))) as WidgetSpec.Buttons).items.single().value)
        assertEquals(
            "widget: item 1 of items: value must be text, not true",
            refused(WidgetKind.TOGGLES, mapOf("items" to listOf(switch + ("value" to true))))
        )
    }

    @Test
    fun stageDetailsAreALineUnderEachStage() {
        val spec = parse(WidgetKind.PROGRESS, stages + ("details" to listOf("mon 09:12", "", "Today 07:05"))) as WidgetSpec.Progress
        assertEquals(listOf("Mon 09:12", "", "Today 07:05"), spec.details)
        // More than the stages: the first ones are kept.
        val more = parse(WidgetKind.PROGRESS, stages + ("details" to List(6) { "d$it" })) as WidgetSpec.Progress
        assertEquals(4, more.details.size)
        val long = parse(WidgetKind.PROGRESS, stages + ("details" to listOf("x".repeat(40)))) as WidgetSpec.Progress
        assertEquals(WidgetParser.MAX_STAGE_DETAIL, long.details.single().length)
        assertEquals(emptyList<String>(), (parse(WidgetKind.PROGRESS, stages) as WidgetSpec.Progress).details)
        assertEquals(
            "widget: details go with stages: a line under each stage's name",
            refused(WidgetKind.PROGRESS, mapOf("value" to 0.5, "details" to listOf("a")))
        )
        assertEquals("widget: details must be a list of texts, not 'Mon'", refused(WidgetKind.PROGRESS, stages + ("details" to "Mon")))
    }

    @Test
    fun aSideTakesADetail() {
        val spec = parse(
            WidgetKind.VERSUS,
            mapOf("sides" to listOf(mapOf("name" to "North", "detail" to "3rd · 41 pts"), mapOf("name" to "South")), "score" to "2–1")
        ) as WidgetSpec.Versus
        assertEquals("3rd · 41 pts", spec.sides[0].detail)
        assertNull(spec.sides[1].detail)
        val long = parse(
            WidgetKind.VERSUS,
            mapOf("sides" to listOf(mapOf("name" to "N", "detail" to "x".repeat(40)), mapOf("name" to "S")), "score" to "0–0")
        ) as WidgetSpec.Versus
        assertEquals(WidgetParser.MAX_SIDE_DETAIL, long.sides[0].detail!!.length)
        assertEquals(
            "widget: item 1 of sides: detail must be text, not a table",
            refused(WidgetKind.VERSUS, mapOf("sides" to listOf(mapOf("name" to "N", "detail" to mapOf("a" to 1)), mapOf("name" to "S")), "score" to "0–0"))
        )
    }

    /** Each field is stored and read back as `widget.show` checked it. */
    @Test
    fun theFieldsAreStored() {
        val specs = listOf(
            parse(WidgetKind.STAT, stat + mapOf("badge" to "live", "footer" to "City sensors", "target" to "8,000", "progress" to 1.5)),
            parse(WidgetKind.BUTTONS, mapOf("footer" to "Home", "items" to listOf(button + ("value" to "21°")))),
            parse(WidgetKind.TOGGLES, mapOf("items" to listOf(switch + ("value" to "40%")))),
            parse(WidgetKind.PROGRESS, stages + mapOf("details" to listOf("Mon", "Tue"), "badge" to "2")),
            parse(WidgetKind.VERSUS, mapOf("sides" to listOf(mapOf("name" to "N", "detail" to "3rd"), mapOf("name" to "S")), "score" to "1–0", "badge" to "FT")),
        )
        for (spec in specs) assertEquals(spec, stored(spec))
    }

    /** A stored card is checked again: a stat's progress past 1 needs its target; buttons and switches drop a badge. */
    @Test
    fun theStoreChecksThemAgain() {
        fun read(json: String) = WidgetJson.decode(json)
        val over = read("""{"v":1,"kind":"stat","value":"9","progress":1.5}""") as WidgetSpec.Stat
        assertEquals(1f, over.progress)
        assertEquals(1.5f, (read("""{"v":1,"kind":"stat","value":"9","progress":1.5,"target":"8"}""") as WidgetSpec.Stat).progress)
        val buttons = read("""{"v":1,"kind":"buttons","badge":"3","items":[{"label":"Go","tap":{"args":""}}]}""")
        assertNotNull(buttons)
        assertNull(buttons!!.base.badge)
        assertNull(read("""{"v":1,"kind":"stat","value":"9","footer":3}"""))
        assertNull(read("""{"v":1,"kind":"progress","value":0.5,"stages":["A","B"],"stage":1,"details":[1]}"""))
        // A detail is cut to the stages there are.
        val p = read("""{"v":1,"kind":"progress","value":0.5,"stages":["A","B"],"stage":1,"details":["x","y","z"]}""") as WidgetSpec.Progress
        assertEquals(listOf("X", "Y"), p.details)
    }

    /**
     * An older Kolahal reads the stored form key by key and skips the keys it doesn't know. So a card
     * stored with these fields loads there as the same card without them: this checks that the keys
     * an older reader knows are still all there, with the same values.
     */
    @Test
    fun aCardWithTheFieldsKeepsEveryOlderKey() {
        val spec = parse(WidgetKind.STAT, stat + mapOf("label" to "Steps", "badge" to "live", "footer" to "Watch", "target" to "8,000", "progress" to 0.78))
        val with = Json.parse(WidgetJson.encode(spec)) as Map<*, *>
        val without = Json.parse(WidgetJson.encode(parse(WidgetKind.STAT, stat + mapOf("label" to "Steps", "progress" to 0.78)))) as Map<*, *>
        assertEquals(without, with - setOf("badge", "footer", "target"))
    }
}
