package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The versus fields of Kolahal 0.5.23: each side's own `score`, its `clock` (seconds left) and its
 * `lines`, and the card's `score` left out when every side has its own score or clock.
 */
class VersusSideFieldsParserTest {
    private fun parse(raw: Map<String, Any?>) =
        WidgetParser.parse(raw, WidgetKind.VERSUS, { it }, { it }) as WidgetSpec.Versus

    private fun refused(raw: Map<String, Any?>): String? =
        assertThrows(IllegalArgumentException::class.java) { parse(raw) }.message

    private fun sides(a: Map<String, Any?>, b: Map<String, Any?> = mapOf("name" to "South")) =
        listOf(mapOf("name" to "North") + a, b)

    @Test
    fun eachSideHasItsScoreClockAndLines() {
        val spec = parse(
            mapOf(
                "sides" to listOf(
                    mapOf("name" to "Harbour", "score" to "187/4", "text" to "18.2 ov", "lines" to listOf("ames 64* (38)", "Kahn 22* (11)")),
                    mapOf("name" to "Valley", "score" to "201/6", "clock" to 252.7),
                ),
            )
        )
        assertNull(spec.score)
        assertEquals("187/4", spec.sides[0].score)
        // Lines start with a capital, as a detail does.
        assertEquals(listOf("Ames 64* (38)", "Kahn 22* (11)"), spec.sides[0].lines)
        // A fraction of a second is dropped.
        assertEquals(252L, spec.sides[1].clock)
        assertEquals(emptyList<String>(), spec.sides[1].lines)
    }

    /** The card's score may go only where every side has its own score or clock. */
    @Test
    fun theScoreIsNeededUnlessEverySideHasItsOwn() {
        assertNull(parse(mapOf("sides" to sides(mapOf("clock" to 60), mapOf("name" to "South", "clock" to 0)))).score)
        assertEquals(
            "widget: score is needed: the score between the sides, as text, such as \"2–1\", or each side's own score or clock",
            refused(mapOf("sides" to sides(mapOf("score" to "3"))))
        )
        // With it, it is kept as given: the card splits it, or draws it whole.
        assertEquals("W/O", parse(mapOf("sides" to sides(mapOf("score" to "3"), mapOf("name" to "South", "clock" to 9)), "score" to "W/O")).score)
    }

    /** A side's score is cut at 12, a line at 24; a clock is 0 or more seconds, under 100 hours. */
    @Test
    fun theNewFieldsHaveTheirLimits() {
        val spec = parse(mapOf("sides" to sides(mapOf("score" to "1234567890123456", "lines" to listOf("x".repeat(30)))), "score" to "1–0"))
        assertEquals(WidgetParser.MAX_SIDE_SCORE, spec.sides[0].score!!.length)
        assertEquals(WidgetParser.MAX_SIDE_LINE, spec.sides[0].lines[0].length)
        assertEquals(
            "widget: item 1 of sides: clock must be 0 or more seconds left, not -1",
            refused(mapOf("sides" to sides(mapOf("clock" to -1)), "score" to "1–0"))
        )
        assertEquals(
            "widget: item 1 of sides: clock must be under 100 hours (359999 seconds at most), not 360000",
            refused(mapOf("sides" to sides(mapOf("clock" to 360_000)), "score" to "1–0"))
        )
        assertEquals(
            "widget: item 1 of sides: clock must be a number of seconds left, 0 or more, not '4:12'",
            refused(mapOf("sides" to sides(mapOf("clock" to "4:12")), "score" to "1–0"))
        )
    }

    /** Up to 3 lines; a blank one is left out, so the others close up. */
    @Test
    fun aSideHasUpToThreeLines() {
        assertEquals(
            "widget: item 2 of sides: lines has 4 texts: give 3 at most",
            refused(mapOf("sides" to sides(emptyMap(), mapOf("name" to "South", "lines" to listOf("a", "b", "c", "d"))), "score" to "1–0"))
        )
        assertEquals(
            listOf("Moss 23'", "Reed 54'"),
            parse(mapOf("sides" to sides(mapOf("lines" to listOf("Moss 23'", " ", "Reed 54'"))), "score" to "1–0")).sides[0].lines
        )
        assertEquals(
            "widget: item 1 of sides: lines must be a list of texts, not 'Moss'",
            refused(mapOf("sides" to sides(mapOf("lines" to "Moss")), "score" to "1–0"))
        )
    }

    /** The new fields are declared once: the parser's message lists them with the others. */
    @Test
    fun anUnknownSideFieldListsTheNewOnes() {
        assertEquals(
            "widget: item 1 of sides: no field 'scores'; use name, mark, color, value, text, detail, score, clock, lines",
            refused(mapOf("sides" to sides(mapOf("scores" to "1")), "score" to "1–0"))
        )
    }
}
