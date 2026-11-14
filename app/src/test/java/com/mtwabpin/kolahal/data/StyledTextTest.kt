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

    /** fmt.table: one line per row; each cell keeps its pieces and its side; the header is marked. */
    @Test fun tables() {
        val t = StyledText.table(
            listOf(listOf("Coin", "Price"), listOf("BTC", StyledText.colour(StyledText.Colour.GOOD, "64,210")), listOf("ETH")),
            header = true, align = "lr",
        )
        val rows = StyledText.lines(t).map { it.row!! }
        assertEquals(listOf(true, false, false), rows.map { it.header })
        assertEquals(listOf("Coin", "Price"), rows[0].cells.map { c -> c.spans.joinToString("") { it.text } })
        assertEquals(listOf(false, true), rows[1].cells.map { it.right })
        assertEquals(StyledText.Colour.GOOD, rows[1].cells[1].spans.single().colour)
        // A short row gets empty cells, so every row has as many as the widest.
        assertEquals(2, rows[2].cells.size)
        assertEquals("Coin\tPrice\nBTC\t64,210\nETH\t", StyledText.plain(t))
    }

    /** A cell is one line, styles stay in their cell, and a table in a cell can't add rows or cells. */
    @Test fun aCellStaysACell() {
        val inner = StyledText.table(listOf(listOf("x", "y")), header = false)
        val t = StyledText.table(listOf(listOf(StyledText.open(Style.BOLD) + "open", "two\nlines", inner)), header = false)
        val row = StyledText.lines(t).single().row!!
        assertEquals(3, row.cells.size)
        assertEquals(setOf(Style.BOLD), row.cells[0].spans.single().styles)
        assertEquals(emptySet<Style>(), row.cells[1].spans.single().styles)
        assertEquals("two lines", row.cells[1].spans.single().text)
        assertEquals("xy", row.cells[2].spans.joinToString("") { it.text })
    }

    /** Up to 8 columns and 50 rows besides the header; more rows end with a "+N more" line. */
    @Test fun aTableHasLimits() {
        val t = StyledText.table(List(60) { r -> List(10) { c -> "$r.$c" } }, header = true)
        val lines = StyledText.lines(t)
        assertEquals(52, lines.size)
        assertEquals(8, lines.first().row!!.cells.size)
        assertEquals(null, lines.last().row)
        assertEquals("+9 more", lines.last().spans.single().text)
    }

    /** A body splits before its table: the text above it, then the table and what follows. */
    @Test fun aBodySplitsAtItsTable() {
        val table = StyledText.table(listOf(listOf("a")), header = false)
        assertEquals("plain" to null, StyledText.splitAtBlocks("plain"))
        assertEquals("above" to "$table\nbelow", StyledText.splitAtBlocks("above\n$table\nbelow"))
        assertTrue(StyledText.hasTable(table))
        assertFalse(StyledText.hasTable(StyledText.list(listOf("a"), numbered = false)))
        assertEquals("", StyledText.table(emptyList(), header = true))
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

/** fmt.tag, fmt.link, fmt.bar, fmt.bars and fmt.ranges: what the log draws, and the plain text everything else sees. */
class StyledTextBlocksTest {
    private val good = StyledText.Colour.GOOD

    @Test fun aTagIsAStyleWithAColour() {
        val text = StyledText.tag(StyledText.Colour.BAD, "LIVE\nnow") + " IND v NZ"
        val (tag, rest) = StyledText.lines(text).single().spans
        assertEquals("LIVE now", tag.text)
        assertEquals(setOf(Style.TAG), tag.styles)
        assertEquals(StyledText.Colour.BAD, tag.colour)
        assertEquals(" IND v NZ", rest.text)
        assertEquals("LIVE now IND v NZ", StyledText.plain(text))
    }

    @Test fun aLinkCarriesItsAddressAndShowsItsText() {
        val text = "412 " + StyledText.link("https://example.org/a b", "A story") + " · " + StyledText.link("https://news.ycombinator.com/item?id=1", "138 comments")
        val spans = StyledText.lines(text).single().spans
        assertEquals(listOf("412 ", "A story", " · ", "138 comments"), spans.map { it.text })
        assertEquals(listOf(null, "https://example.org/ab", null, "https://news.ycombinator.com/item?id=1"), spans.map { it.url })
        assertEquals("412 A story · 138 comments", StyledText.plain(text))
        assertEquals("412 A story · 138 comments", StyledText.strip(text))
        assertEquals(listOf("https://example.org/ab", "https://news.ycombinator.com/item?id=1"), StyledText.links(text))
    }

    /** Styles work inside a link's text; a link inside a link's text is dropped. */
    @Test fun linksKeepStylesAndDontNest() {
        val inner = StyledText.link("https://b.example", "inner")
        val text = StyledText.link("https://a.example", StyledText.wrap(Style.BOLD, "bold") + " and " + inner)
        val spans = StyledText.lines(text).single().spans
        assertEquals(listOf("bold", " and inner"), spans.map { it.text })
        assertEquals(setOf("https://a.example"), spans.map { it.url }.toSet())
        assertEquals(setOf(Style.BOLD), spans[0].styles)
    }

    /** The log checks each address: one it keeps can change, one it drops leaves its text as plain text. */
    @Test fun mapLinksChangesOrDropsAddresses() {
        val text = StyledText.link("javascript:alert(1)", "bad") + " " + StyledText.link("https://ok.example", "ok") + " end"
        val mapped = StyledText.mapLinks(text) { if (it.startsWith("https://")) "$it/" else null }
        val spans = StyledText.lines(mapped).single().spans
        assertEquals(listOf("bad ", "ok", " end"), spans.map { it.text })
        assertEquals(listOf(null, "https://ok.example/", null), spans.map { it.url })
        assertEquals(listOf("https://ok.example/"), StyledText.links(mapped))
    }

    @Test fun aBarIsALineWithNoText() {
        val body = "Unhealthy\n" + StyledText.bar(168.0, 300.0, StyledText.Colour.BAD)
        val (text, bar) = StyledText.lines(body)
        assertEquals("Unhealthy", text.spans.single().text)
        val m = bar.meter!!
        assertEquals(0f, m.from)
        assertEquals(0.56f, m.to, 0.001f)
        assertEquals(StyledText.Colour.BAD, m.colour)
        assertEquals("Unhealthy", StyledText.plain(body))
        assertEquals("Unhealthy" to StyledText.bar(168.0, 300.0, StyledText.Colour.BAD), StyledText.splitAtBlocks(body))
        assertTrue(StyledText.hasBlock(body))
        assertFalse(StyledText.hasTable(body))
    }

    /** Past the end, below zero, not a number, no scale: the bar stays between empty and full. */
    @Test fun aBarStaysInItsTrack() {
        fun to(s: String) = StyledText.lines(s).single().meter!!.to
        assertEquals(1f, to(StyledText.bar(5.0)))
        assertEquals(0f, to(StyledText.bar(-1.0)))
        assertEquals(0f, to(StyledText.bar(Double.NaN)))
        assertEquals(0f, to(StyledText.bar(3.0, 0.0)))
        assertEquals(0.5f, to(StyledText.bar(0.5)))
    }

    @Test fun barsShareOneScale() {
        val body = StyledText.bars(listOf(StyledText.BarRow("Mon", 42.0), StyledText.BarRow("Tue", 21.0, "21 unlocks")), colour = good)
        val meters = StyledText.lines(body).map { it.meter!! }
        assertEquals(listOf(1f, 0.5f), meters.map { it.to })
        assertEquals(listOf("Mon", "Tue"), meters.map { m -> m.label.joinToString("") { it.text } })
        assertEquals(listOf("42", "21 unlocks"), meters.map { m -> m.end.joinToString("") { it.text } })
        assertEquals(setOf(good), meters.map { it.colour }.toSet())
        assertEquals("Mon\t42\nTue\t21 unlocks", StyledText.plain(body))
        // A set top.
        assertEquals(0.42f, StyledText.lines(StyledText.bars(listOf(StyledText.BarRow("a", 42.0)), max = 100.0)).single().meter!!.to, 0.001f)
    }

    @Test fun rangesRunFromLowToHigh() {
        val body = StyledText.ranges(
            listOf(StyledText.RangeRow("Sat", 23.0, 31.0), StyledText.RangeRow("Sun", 30.0, 22.0), StyledText.RangeRow("Mon", 20.0, 25.0)),
            unit = "°",
        )
        val meters = StyledText.lines(body).map { it.meter!! }
        // The scale runs from the lowest low (20) to the highest high (31). A row given high first is turned round.
        assertEquals(3f / 11, meters[0].from, 0.001f)
        assertEquals(1f, meters[0].to, 0.001f)
        assertEquals(2f / 11, meters[1].from, 0.001f)
        assertEquals(10f / 11, meters[1].to, 0.001f)
        assertEquals(0f, meters[2].from)
        assertEquals("Sat\t23°\t31°\nSun\t22°\t30°\nMon\t20°\t25°", StyledText.plain(body))
    }

    /** A bar's texts are one line each and can't start rows or bars; more than 50 rows end with "+N more". */
    @Test fun barsHaveLimits() {
        val body = StyledText.bars(List(60) { StyledText.BarRow("row\n$it" + StyledText.bar(1.0), it.toDouble()) })
        val lines = StyledText.lines(body)
        assertEquals(51, lines.size)
        assertEquals("row 0", lines.first().meter!!.label.joinToString("") { it.text })
        assertEquals("+10 more", lines.last().spans.single().text)
        assertEquals("", StyledText.bars(emptyList()))
    }

    @Test fun numbersReadAsWritten() {
        assertEquals("42", StyledText.number(42.0))
        assertEquals("3.5", StyledText.number(3.5))
        assertEquals("0.125", StyledText.number(0.1249999))
        assertEquals("-2", StyledText.number(-2.0))
    }

    /** fmt.stats: values with their labels, as a line of its own; plain text reads "24° Pune · 60% humidity". */
    @Test fun statsAreALineOfBigNumbers() {
        val body = "Now\n" + StyledText.stats(listOf("24°" to "Pune, clear", StyledText.colour(StyledText.Colour.GOOD, "60%") to "humidity", "12" to "")) + "\nlater"
        val lines = StyledText.lines(body)
        val stats = lines[1].stats!!
        assertEquals(listOf("24°", "60%", "12"), stats.map { s -> s.value.joinToString("") { it.text } })
        assertEquals(listOf("Pune, clear", "humidity", ""), stats.map { s -> s.label.joinToString("") { it.text } })
        assertEquals(StyledText.Colour.GOOD, stats[1].value.single().colour)
        assertEquals("Now\n24° Pune, clear · 60% humidity · 12\nlater", StyledText.plain(body))
        assertTrue(StyledText.hasBlock(body))
        assertEquals("Now" to StyledText.stats(listOf("24°" to "Pune, clear", StyledText.colour(StyledText.Colour.GOOD, "60%") to "humidity", "12" to "")) + "\nlater", StyledText.splitAtBlocks(body))
        // Up to 5; a value is one line.
        assertEquals(5, StyledText.lines(StyledText.stats(List(7) { "v$it\nx" to "l" })).single().stats!!.size)
        assertEquals("v0 x", StyledText.lines(StyledText.stats(listOf("v0\nx" to "l"))).single().stats!!.single().value.single().text)
        assertEquals("", StyledText.stats(emptyList()))
    }

    // ---- styled text outside the log: a widget's text ----------------------------------------

    private fun bold(s: String) = StyledText.wrap(Style.BOLD, s)
    private fun italic(s: String) = StyledText.wrap(Style.ITALIC, s)
    private fun green(s: String) = StyledText.colour(StyledText.Colour.GOOD, s)

    /** Each piece of [text] as (words, styles, colour). */
    private fun pieces(text: String) = StyledText.lines(text).flatMap { it.spans }.map { Triple(it.text, it.styles, it.colour) }

    /** The styles of each piece of [text], by its words. */
    private fun stylesIn(text: String) = StyledText.lines(text).flatMap { it.spans }.associate { it.text to it.styles }

    /** inline keeps bold, italic, underline, strike and colours, and what the text shows stays the same. */
    @Test fun inlineKeepsWeightSlantLinesAndColour() {
        val text = "a " + bold("b") + " " + italic("c") + " " + StyledText.wrap(Style.UNDERLINE, "d") + " " + StyledText.wrap(Style.STRIKE, "e") + " " + green("f") + " g"
        val kept = StyledText.inline(text)
        assertEquals("a b c d e f g", StyledText.plain(kept))
        assertEquals(
            listOf(
                Triple("a ", emptySet(), null), Triple("b", setOf(Style.BOLD), null), Triple(" ", emptySet(), null), Triple("c", setOf(Style.ITALIC), null),
                Triple(" ", emptySet(), null), Triple("d", setOf(Style.UNDERLINE), null), Triple(" ", emptySet(), null), Triple("e", setOf(Style.STRIKE), null),
                Triple(" ", emptySet(), null), Triple("f", emptySet<Style>(), StyledText.Colour.GOOD), Triple(" g", emptySet(), null),
            ),
            pieces(kept),
        )
        // Plain text is left alone: the same object comes back.
        assertSame("no styles", StyledText.inline("no styles"))
        // Every colour role stays.
        StyledText.Colour.entries.forEach { assertEquals(it, pieces(StyledText.inline(StyledText.colour(it, "x"))).single().third) }
    }

    /** What only the log draws loses its marks and keeps its words: a link, a tag, a code chip, a table, a bar, big numbers, a list. */
    @Test fun inlineDropsWhatOnlyTheLogDraws() {
        val link = StyledText.inline("see " + StyledText.link("https://example.com/x", bold("the page")) + " now")
        assertEquals("see the page now", StyledText.plain(link))
        assertEquals(emptyList<String>(), StyledText.links(link))
        assertFalse(link.contains("example.com"))
        assertEquals(setOf(Style.BOLD), stylesIn(link)["the page"])

        val tag = StyledText.inline(StyledText.tag(StyledText.Colour.BAD, "LIVE") + " " + StyledText.wrap(Style.CODE, "v1.2"))
        assertEquals("LIVE v1.2", StyledText.plain(tag))
        // The tag's colour stays. Its chip and the code's shade go.
        assertEquals(listOf(Triple("LIVE", emptySet<Style>(), StyledText.Colour.BAD), Triple(" v1.2", emptySet(), null)), pieces(tag))

        val blocks = "Top\n" + StyledText.table(listOf(listOf("a", bold("b")), listOf("c", "d")), header = false) + "\n" +
            StyledText.meter(0.0, 0.5, StyledText.Colour.GOOD, "Overs", "", "40/50") + "\n" + StyledText.bar(0.5) + "\n" +
            StyledText.stats(listOf("24°" to "Pune")) + "\n" + StyledText.list(listOf("one", italic("two")), numbered = true)
        val kept = StyledText.inline(blocks)
        assertEquals(StyledText.plain(blocks), StyledText.plain(kept))
        assertEquals("Top\na\tb\nc\td\nOvers\t40/50\n24° Pune\n1. one\n2. two", StyledText.plain(kept))
        assertFalse(StyledText.hasBlock(kept))
        assertTrue(StyledText.lines(kept).all { it.level == 0 && it.row == null && it.meter == null && it.stats == null })
        // A style inside a list item stays. A style inside a table cell goes with the table.
        assertEquals(setOf(Style.ITALIC), stylesIn(kept)["two"])
        assertTrue(pieces(kept).none { it.first == "b" })
    }

    /** inline gives one fixed form: run again on its own result, it changes nothing. Nothing is left open. */
    @Test fun inlineGivesTheSameTextWhenRunTwice() {
        val nested = bold("a " + italic("b " + green("c") + " d") + " e") + green("f " + StyledText.colour(StyledText.Colour.BAD, "g") + " h")
        val open = StyledText.open(Style.BOLD) + "left open\n" + StyledText.open(Style.ITALIC) + "second line" + StyledText.close(Style.STRIKE)
        val stray = "x" + StyledText.close(Style.BOLD) + "y" + StyledText.item(1) + "z" + StyledText.link("https://example.com", "") + bold("")
        val mixed = StyledText.tag(StyledText.Colour.INFO, "NEW") + StyledText.link("https://example.com", green("go")) + "\n" + StyledText.list(listOf(bold("x")), numbered = false, level = 2)
        for (text in listOf(nested, open, stray, mixed, bold("a") + bold("b") + " " + bold("c"), "plain", "", "a\n\nb", green("multi\nline"))) {
            val once = StyledText.inline(text)
            assertEquals(once, StyledText.inline(once))
            assertEquals(StyledText.plain(text), StyledText.plain(once))
            // take leaves the form alone too.
            assertEquals(once, StyledText.take(once, 1000))
        }
        // Pieces with the same styles are one piece.
        assertEquals(bold("ab") + " " + bold("c"), StyledText.inline(bold("a") + bold("b") + " " + bold("c")))
        // A style left open ends with its line, and goes on in the next: each line's piece is closed.
        assertEquals(bold("left open") + "\n" + StyledText.wrap(Style.BOLD, StyledText.wrap(Style.ITALIC, "second line")), StyledText.inline(open))
        // Styles are written in one order, the colour outside.
        assertEquals(StyledText.inline(bold(italic(green("x")))), StyledText.inline(green(italic(bold("x")))))
        assertEquals(green(bold(italic("x"))), StyledText.inline(italic(bold(green("x")))))
        // A marker with no words leaves nothing.
        assertEquals("xyz", StyledText.inline(stray))
    }

    /** Only the first styled pieces keep their styles, so markers can't make a short text long. */
    @Test fun inlineKeepsALimitedNumberOfStyledPieces() {
        val many = (1..50).joinToString(" ") { bold("w$it") }
        val kept = StyledText.inline(many)
        assertEquals(StyledText.plain(many), StyledText.plain(kept))
        assertEquals(32, pieces(kept).count { it.second.isNotEmpty() })
        assertEquals(setOf(Style.BOLD), stylesIn(kept)["w32"])
        assertTrue(StyledText.plain(kept).endsWith("w49 w50"))
        assertEquals(kept, StyledText.inline(kept))
        assertEquals(bold("a") + " b c", StyledText.inline(bold("a") + " " + italic("b") + " " + green("c"), maxRuns = 1))
        assertEquals("a b", StyledText.inline(bold("a") + " " + italic("b"), maxRuns = 0))
        // 500 characters in the most styled pieces stay far under the 4000 units a stored text may have.
        val worst = (1..32).joinToString("") { StyledText.colour(StyledText.Colour.WARN, bold(italic(StyledText.wrap(Style.UNDERLINE, StyledText.wrap(Style.STRIKE, "x"))))) + "y" } + "z".repeat(436)
        assertEquals(500, StyledText.plain(worst).length)
        assertTrue(StyledText.inline(worst).length <= 500 + 32 * 20)
    }

    /** take counts what shows, not the markers, and ends a longer text in "…" with nothing left open. */
    @Test fun takeCutsWhatShowsAndClosesItsStyles() {
        // Plain text is cut as before.
        assertSame("short", StyledText.take("short", 5))
        assertEquals("abcd…", StyledText.take("abcdefgh", 5))
        assertEquals("a…", StyledText.take("a😀bc", 3))
        // Markers are not counted: 5 characters show, so nothing is cut.
        assertEquals(bold("ab") + green("cde"), StyledText.take(bold("ab") + green("cde"), 5))
        // A cut inside a styled piece: the "…" is in the piece, and the piece is closed.
        val cut = StyledText.take(bold("abcdef") + " tail", 4)
        assertEquals(bold("abc…"), cut)
        assertEquals("abc…", StyledText.plain(cut))
        // A cut between two pieces: the "…" goes with the piece before.
        assertEquals(bold("abc…"), StyledText.take(bold("abc") + italic("defgh"), 4))
        assertEquals("ab" + italic("c…"), StyledText.take("ab" + italic("cdefgh"), 4))
        // A cut never ends in half an emoji, also inside a styled piece.
        assertEquals(green("ab…"), StyledText.take(green("ab😀cd"), 4))
        // What shows is never longer than the limit.
        val long = (1..40).joinToString(" ") { if (it % 2 == 0) bold("word$it") else green("word$it") }
        for (max in listOf(1, 2, 7, 24, 60, 200)) {
            val taken = StyledText.take(StyledText.inline(long), max)
            assertEquals(max, StyledText.plain(taken).length)
            assertTrue(StyledText.plain(taken).endsWith("…"))
            assertEquals(taken, StyledText.inline(taken))
        }
        // Text that only the log draws comes back in inline's form, cut or not.
        assertEquals("go", StyledText.take(StyledText.link("https://example.com", "go"), 10))
        assertEquals("LIV…", StyledText.plain(StyledText.take(StyledText.tag(StyledText.Colour.BAD, "LIVE NOW"), 4)))
    }

    /** hasMarker tells a marker from other private-use characters, which plain text may hold. */
    @Test fun hasMarkerFindsMarkersOnly() {
        assertTrue(StyledText.hasMarker(bold("a")))
        assertTrue(StyledText.hasMarker("a" + StyledText.close(Style.TAG)))
        assertTrue(StyledText.hasMarker(StyledText.link("https://example.com", "x")))
        assertTrue(StyledText.hasMarker(StyledText.bar(0.5)))
        assertFalse(StyledText.hasMarker("plain 😀 text"))
        assertFalse(StyledText.hasMarker(""))
        // A character next to the markers' range is no marker, though it starts with the same half.
        val near = String(Character.toChars(0x10FB80))
        assertTrue(StyledText.has(near))
        assertFalse(StyledText.hasMarker("a${near}b"))
        assertEquals("a${near}b", StyledText.inline("a${near}b"))
    }
}
