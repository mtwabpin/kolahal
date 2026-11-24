package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** What `widget.show` accepts and refuses for the `versus` and `sky` kinds, and what is stored of them. */
class VersusAndSkyParserTest {
    private fun parse(raw: Map<String, Any?>, kind: WidgetKind) =
        WidgetParser.parse(raw, kind, { it }, { it })

    private fun refused(raw: Map<String, Any?>, kind: WidgetKind): String? =
        assertThrows(IllegalArgumentException::class.java) { parse(raw, kind) }.message

    private val two = listOf(mapOf("name" to "Lisbon"), mapOf("name" to "Paris"))

    private fun versus(vararg fields: Pair<String, Any?>) =
        parse(mapOf("sides" to two, "score" to "2–1") + fields, WidgetKind.VERSUS) as WidgetSpec.Versus

    private fun noVersus(vararg fields: Pair<String, Any?>) =
        refused(mapOf("sides" to two, "score" to "2–1") + fields, WidgetKind.VERSUS)

    private val rises = 1_800_000_000L
    private val sets = rises + 36_000

    private fun sky(vararg fields: Pair<String, Any?>) =
        parse(mapOf("rises" to rises, "sets" to sets) + fields, WidgetKind.SKY) as WidgetSpec.Sky

    private fun noSky(vararg fields: Pair<String, Any?>) =
        refused(mapOf("rises" to rises, "sets" to sets) + fields, WidgetKind.SKY)

    // ---- versus ----------------------------------------------------------------------------

    @Test
    fun aVersusWithAllItsFields() {
        val spec = parse(
            mapOf(
                "kind" to "versus",
                "title" to "League",
                "sides" to listOf(
                    mapOf("name" to "Lisbon", "mark" to "L", "color" to "accent", "value" to 58, "text" to "58%"),
                    mapOf("name" to "Paris", "mark" to "P", "color" to "link", "value" to 42),
                ),
                "score" to "2–1",
                "turn" to 2,
                "moving" to true,
                "line" to "64' Goal",
                "badge" to "67'",
            ),
            WidgetKind.STAT
        )
        assertEquals(
            WidgetSpec.Versus(
                WidgetBase(title = "League"),
                listOf(
                    WidgetSpec.Side("Lisbon", "L", StyledText.ColorRole.ACCENT, 58f, "58%"),
                    WidgetSpec.Side("Paris", "P", StyledText.ColorRole.LINK, 42f),
                ),
                score = "2–1",
                turn = 2,
                moving = true,
                line = "64' Goal",
                badge = "67'",
            ),
            spec
        )
    }

    @Test
    fun aVersusNeedsTwoSidesEachWithAName() {
        assertEquals(
            "widget: sides is needed: the two sides, each { name, mark, color, value, text }",
            refused(mapOf("score" to "1–0"), WidgetKind.VERSUS)
        )
        assertEquals(
            "widget: sides has 1 side: give 2",
            refused(mapOf("sides" to two.take(1), "score" to "1–0"), WidgetKind.VERSUS)
        )
        assertEquals(
            "widget: sides has 3 sides: give 2",
            refused(mapOf("sides" to two + two.take(1), "score" to "1–0"), WidgetKind.VERSUS)
        )
        assertEquals(
            "widget: item 2 of sides: name is needed: the side's name, such as a player or a team",
            refused(mapOf("sides" to listOf(mapOf("name" to "A"), mapOf("mark" to "B")), "score" to "1–0"), WidgetKind.VERSUS)
        )
        assertEquals(
            "widget: score is needed: the score between the sides, as text, such as \"2–1\"",
            refused(mapOf("sides" to two), WidgetKind.VERSUS)
        )
        // An empty name or score, or one of blanks only, is refused as a missing one, as an image's alt is.
        assertEquals(
            "widget: item 1 of sides: name is needed: the side's name, such as a player or a team",
            refused(mapOf("sides" to listOf(mapOf("name" to " "), mapOf("name" to "B")), "score" to "1–0"), WidgetKind.VERSUS)
        )
        assertEquals(
            "widget: score is needed: the score between the sides, as text, such as \"2–1\"",
            refused(mapOf("sides" to two, "score" to ""), WidgetKind.VERSUS)
        )
    }

    /** A mark is a letter or two, as seen; a longer one is cut there, with no "…". */
    @Test
    fun aMarkIsALetterOrTwo() {
        fun mark(given: Any) = versus(
            "sides" to listOf(mapOf("name" to "A", "mark" to given), mapOf("name" to "B"))
        ).sides[0].mark
        assertEquals("N", mark("N"))
        assertEquals("Ca", mark("Carlsen"))
        assertEquals("♔", mark("♔"))
        assertEquals("7", mark(7))
        assertNull(mark("  "))
    }

    @Test
    fun aSidesValueIsZeroOrMore() {
        val sides = { a: Any, b: Any -> listOf(mapOf("name" to "A", "value" to a), mapOf("name" to "B", "value" to b)) }
        assertEquals(listOf(0f, 3490f), versus("sides" to sides(0, 3490)).sides.map { it.value })
        assertEquals("widget: item 1 of sides: value must be 0 or more, not -1", noVersus("sides" to sides(-1, 2)))
        assertEquals(
            "widget: item 2 of sides: value must be a number, 0 or more, not 'lots'",
            noVersus("sides" to sides(1, "lots"))
        )
    }

    @Test
    fun theTurnIsOneOrTwo() {
        assertEquals(1, versus("turn" to 1).turn)
        assertEquals(2, versus("turn" to 2.0).turn)
        assertNull(versus().turn)
        assertEquals("widget: turn must be 1 or 2, the side to move, not 3", noVersus("turn" to 3))
        assertEquals("widget: turn must be 1 or 2, the side to move, not 0", noVersus("turn" to 0))
        assertEquals("widget: turn must be 1 or 2, the side to move, not 1.5", noVersus("turn" to 1.5))
        assertEquals("widget: turn must be 1 or 2, the side to move, not 'white'", noVersus("turn" to "white"))
    }

    /** Texts are cut at their limits: the name and the score at 24, a figure at 12, the badge at 8, the line at 60. */
    @Test
    fun aVersusTextIsCutAtItsLimit() {
        val long = "x".repeat(100)
        val spec = versus(
            "sides" to listOf(mapOf("name" to long, "text" to long), mapOf("name" to "B")),
            "badge" to long,
            "line" to long,
            "score" to long,
        )
        assertEquals(WidgetParser.MAX_SHORT, spec.sides[0].name.length)
        assertEquals(WidgetParser.MAX_UNIT, spec.sides[0].text!!.length)
        assertEquals(WidgetParser.MAX_BADGE, spec.badge!!.length)
        assertEquals(WidgetParser.MAX_LABEL, spec.line!!.length)
        assertEquals(WidgetParser.MAX_SHORT, spec.score.length)
    }

    /** The last line keeps the styles a card draws, and starts with a capital as a label does. */
    @Test
    fun theLastLineKeepsItsStyles() {
        val line = StyledText.color(StyledText.ColorRole.ACCENT, "goal") + " · possession"
        val kept = versus("line" to line).line!!
        assertEquals("Goal · possession", StyledText.plain(kept))
        assertEquals(StyledText.inline(kept), kept)
        // Every other text is plain.
        assertEquals("Lisbon", versus("sides" to listOf(mapOf("name" to StyledText.wrap(StyledText.Style.BOLD, "Lisbon")), mapOf("name" to "B"))).sides[0].name)
    }

    @Test
    fun aVersusComesBackFromItsStoredForm() {
        val spec = versus(
            "sides" to listOf(mapOf("name" to "A", "value" to 1), mapOf("name" to "B", "value" to 2)),
            "turn" to 1,
            "moving" to true,
        )
        assertEquals(spec, WidgetJson.decode(WidgetJson.encode(spec)))
    }

    /** The stored form is checked again: one side, a third side or a turn of 3 reads back as nothing. */
    @Test
    fun anOddStoredVersusIsNothing() {
        val spec = versus()

        @Suppress("UNCHECKED_CAST")
        fun changed(change: MutableMap<String, Any?>.() -> Unit): String {
            val map = (Json.parse(WidgetJson.encode(spec)) as Map<String, Any?>).toMutableMap()
            map.change()
            return Json.stringify(map)
        }
        assertNotNull(WidgetJson.decode(changed { }))
        assertNull(WidgetJson.decode(changed { put("sides", listOf(mapOf("name" to "A"))) }))
        assertNull(WidgetJson.decode(changed { put("sides", List(3) { mapOf("name" to "A") }) }))
        assertNull(WidgetJson.decode(changed { put("turn", 3) }))
        assertNull(WidgetJson.decode(changed { remove("score") }))
        // A value below 0 is brought to 0, as the parser never stores one.
        val low = WidgetJson.decode(changed {
            put("sides", listOf(mapOf("name" to "A", "value" to -5), mapOf("name" to "B", "value" to 1)))
        }) as WidgetSpec.Versus
        assertEquals(0f, low.sides[0].value)
    }

    // ---- sky -------------------------------------------------------------------------------

    @Test
    fun aSkyWithAllItsFields() {
        val spec = parse(
            mapOf(
                "kind" to "sky",
                "title" to "Lisbon",
                "rises" to rises,
                "sets" to sets + 0.9,
                "phase" to 0.78,
                "waxing" to true,
                "phases" to listOf(
                    mapOf("phase" to 1, "waxing" to true, "at" to rises + 300_000),
                    mapOf("phase" to 0.5, "at" to rises + 900_000),
                ),
            ),
            WidgetKind.STAT
        )
        assertEquals(
            WidgetSpec.Sky(
                WidgetBase(title = "Lisbon"),
                rises = rises,
                sets = sets,
                phase = 0.78f,
                waxing = true,
                phases = listOf(
                    WidgetSpec.Phase(1f, waxing = true, at = rises + 300_000),
                    WidgetSpec.Phase(0.5f, waxing = false, at = rises + 900_000),
                ),
            ),
            spec
        )
    }

    @Test
    fun aSkyNeedsItsRiseAndItsSet() {
        assertEquals(
            "widget: rises is needed: when the sun or the moon rises, as a time in seconds (os.time() in Lua, Date.now() / 1000 in JavaScript)",
            refused(mapOf("sets" to sets), WidgetKind.SKY)
        )
        assertEquals(
            "widget: sets is needed: when it sets, as a time in seconds",
            refused(mapOf("rises" to rises), WidgetKind.SKY)
        )
        assertEquals("widget: sets must be after rises", noSky("sets" to rises))
        assertEquals("widget: sets must be after rises", noSky("sets" to rises - 60))
        assertEquals(
            "widget: rises must be a time in seconds, not in milliseconds: divide it by 1000",
            noSky("rises" to rises * 1000)
        )
    }

    /** The sun sets less than a day after it rises; the moon less than one of its days (24 h 50 min) after. */
    @Test
    fun aSkySetsWithinADay() {
        assertEquals(rises + 86_399, sky("sets" to rises + 86_399).sets)
        assertEquals("widget: sets must be less than a day after rises", noSky("sets" to rises + 86_400))
        assertEquals(rises + 89_427, sky("sets" to rises + 89_427, "phase" to 0.5).sets)
        assertEquals(
            "widget: sets must be less than a day after rises",
            noSky("sets" to rises + WidgetParser.MOON_DAY, "phase" to 0.5)
        )
    }

    @Test
    fun aPhaseIsHeldBetweenZeroAndOne() {
        assertEquals(1f, sky("phase" to 1.4).phase)
        assertEquals(0f, sky("phase" to -0.2).phase)
        assertNull(sky().phase)
        assertEquals(
            "widget: phase must be a number from 0 to 1, the lit part of the moon, not 'full'",
            noSky("phase" to "full")
        )
    }

    /** The next phases go with the moon's phase now; the first 4 are kept, and each needs its phase and its time. */
    @Test
    fun thePhasesGoWithThePhase() {
        val one = mapOf("phase" to 0.5, "at" to rises + 100_000)
        assertEquals(
            "widget: phases go with phase: give the moon's phase now too",
            noSky("phases" to listOf(one))
        )
        assertEquals(4, sky("phase" to 0.2, "phases" to List(6) { one }).phases.size)
        assertEquals(
            "widget: item 1 of phases: at is needed: when the phase comes, as a time in seconds",
            noSky("phase" to 0.2, "phases" to listOf(mapOf("phase" to 0.5)))
        )
        assertEquals(
            "widget: item 1 of phases: phase is needed: the lit part of the moon, from 0 to 1",
            noSky("phase" to 0.2, "phases" to listOf(mapOf("at" to rises)))
        )
    }

    @Test
    fun aSkyComesBackFromItsStoredForm() {
        val spec = sky("phase" to 0.3, "phases" to listOf(mapOf("phase" to 0, "at" to rises + 5)))
        assertEquals(spec, WidgetJson.decode(WidgetJson.encode(spec)))
        assertEquals(sky(), WidgetJson.decode(WidgetJson.encode(sky())))
    }

    /** The stored form is checked again: a set before the rise, or more than a day after it, reads back as nothing. */
    @Test
    fun anOddStoredSkyIsNothing() {
        val spec = sky()

        @Suppress("UNCHECKED_CAST")
        fun changed(change: MutableMap<String, Any?>.() -> Unit): String {
            val map = (Json.parse(WidgetJson.encode(spec)) as Map<String, Any?>).toMutableMap()
            map.change()
            return Json.stringify(map)
        }
        assertNotNull(WidgetJson.decode(changed { }))
        assertNull(WidgetJson.decode(changed { put("sets", rises) }))
        assertNull(WidgetJson.decode(changed { put("sets", rises + 86_400) }))
        assertNull(WidgetJson.decode(changed { remove("rises") }))
        assertNull(WidgetJson.decode(changed { put("phases", listOf(mapOf("phase" to 0.5))) }))
    }
}
