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
}
