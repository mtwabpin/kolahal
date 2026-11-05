package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.data.StyledText.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Styled text in log lines: what the log draws, and the plain text everything else sees. */
class StyledTextTest {
    private fun styles(text: String) = StyledText.lines(text).flatMap { it.spans }.associate { it.text to it.styles }

    @Test fun stylesWrapPiecesOfALine() {
        val text = StyledText.wrap(Style.BOLD, "12") + " new · " + StyledText.wrap(Style.CODE, "v0.11.4")
        assertTrue(StyledText.has(text))
        assertEquals(mapOf("12" to setOf(Style.BOLD), " new · " to emptySet(), "v0.11.4" to setOf(Style.CODE)), styles(text))
        assertEquals("12 new · v0.11.4", StyledText.plain(text))
    }

    @Test fun stylesNest() {
        val text = StyledText.wrap(Style.BOLD, "a " + StyledText.wrap(Style.ITALIC, "b") + " c")
        assertEquals(setOf(Style.BOLD, Style.ITALIC), styles(text)["b"])
        assertEquals(setOf(Style.BOLD), styles(text)["a "])
    }

    @Test fun plainTextIsLeftAlone() {
        val text = "Show HN: *nix tools in __50__ lines"
        assertFalse(StyledText.has(text))
        assertEquals(text, StyledText.plain(text))
        assertEquals(listOf(StyledText.Line(0, "", listOf(StyledText.Span(text, emptySet())))), StyledText.lines(text))
    }

    /** A script that cuts a styled string: the style runs on to the end, half a marker is dropped. */
    @Test fun aStringCutShortStillReads() {
        val open = StyledText.open(Style.BOLD) + "bold and on"
        assertEquals(setOf(Style.BOLD), styles(open)["bold and on"])
        val half = StyledText.wrap(Style.STRIKE, "gone").dropLast(1)
        assertEquals("gone", StyledText.plain(half))
        assertFalse(StyledText.plain(half).any { it.isSurrogate() })
    }

    @Test fun coloursByRoleNest() {
        val text = StyledText.colour(StyledText.Colour.BAD, "down " + StyledText.colour(StyledText.Colour.MUTED, "(2m)") + " now")
        val spans = StyledText.lines(text).single().spans
        assertEquals(listOf(StyledText.Colour.BAD, StyledText.Colour.MUTED, StyledText.Colour.BAD), spans.map { it.colour })
        assertEquals("down (2m) now", StyledText.plain(text))
        assertEquals(StyledText.Colour.GOOD, StyledText.Colour.named(" Good "))
        assertEquals(null, StyledText.Colour.named("#ff0000"))
    }

    @Test fun lists() {
        val bullets = StyledText.list(listOf("milk", "bread"), numbered = false)
        assertEquals("• milk\n• bread", StyledText.plain(bullets))
        assertEquals(listOf(1, 1), StyledText.lines(bullets).map { it.level })
        val numbered = StyledText.list(listOf("eggs", StyledText.wrap(Style.BOLD, "tea")), numbered = true)
        assertEquals("1. eggs\n2. tea", StyledText.plain(numbered))
        assertEquals(listOf("1.", "2."), StyledText.lines(numbered).map { it.prefix })
        assertEquals("  ◦ skim", StyledText.plain(StyledText.list(listOf("skim"), numbered = false, level = 2)))
    }

    /** Text from outside loses every marker, and nothing else: no bullet for an item mark, emoji kept. */
    @Test fun stripTakesOutTheMarkersOnly() {
        val styled = StyledText.wrap(Style.BOLD, "Win") + " a " + StyledText.colour(StyledText.Colour.BAD, "prize") + " 😀"
        assertEquals("Win a prize 😀", StyledText.strip(styled))
        assertEquals("milk", StyledText.strip(StyledText.list(listOf("milk"), numbered = false)).trim())
        assertFalse(StyledText.has(StyledText.strip(styled)))
        val plain = "No styles here 😀"
        assertSame(plain, StyledText.strip(plain))
    }
}
