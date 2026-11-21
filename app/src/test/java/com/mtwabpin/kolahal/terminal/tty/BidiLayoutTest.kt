package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogCorpus
import com.mtwabpin.kolahal.terminal.Target
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.lineModel
import com.mtwabpin.kolahal.terminal.targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Right-to-left text and a right-to-left frame on the grid: rows in the order the bidi algorithm
 * shows them, words of joining scripts as whole pieces, and a frame that turns around (the gutter
 * on the right, the time on the left, tables and bars mirrored) with its targets and spots.
 */
class BidiLayoutTest {
    private val options = LineOptions(TimeMode.ALL, foldLines = 8)
    private var ids = 50_000L

    private fun model(text: String, kind: LogKind = LogKind.OUTPUT): LineModel {
        val e = LogEntry(ids++, LogCorpus.AT, kind, null, text, LogContext(emptyMap()))
        return lineModel(LogCorpus.row(LogCorpus.Line(text, e)), options)
    }

    private fun layout(text: String, cols: Int = 40, rtl: Boolean = false, time: String? = null): CellBlock =
        LineLayout.layout(model(text), LogLook(cols, rtl = rtl), time)

    private fun CellBlock.row(r: Int) = grid.rowText(r).trimEnd()

    /** Hebrew alone: its letters show from right to left, after the gutter. */
    @Test
    fun hebrewReadsRightToLeft() {
        assertEquals("│ םלוע םולש", layout("שלום עולם").row(0))
    }

    /** A Hebrew word inside English stays in place, its letters turned; the English around it reads as it is. */
    @Test
    fun mixedTextKeepsEachRunsDirection() {
        assertEquals("│ Hello םולש world", layout("Hello שלום world").row(0))
        // A line that starts in Hebrew is a right-to-left paragraph: the English goes to its left.
        assertEquals("│ Hello םולש", layout("שלום Hello").row(0))
        // Numbers keep their order inside right-to-left text.
        assertEquals("│ 123 םולש", layout("שלום 123").row(0))
    }

    /** Brackets in right-to-left text are mirrored, so they still open and close around their words. */
    @Test
    fun bracketsMirrorInRightToLeftText() {
        assertEquals("│ (םלוע) םולש", layout("שלום (עולם)").row(0))
    }

    /** Arabic is drawn a word at a time, shaped: each word one piece over the cells its letters take. */
    @Test
    fun arabicWordsAreShapedRuns() {
        val block = layout("مرحبا بالعالم")
        val runs = block.grid.shaped
        assertEquals(runs.toString(), 2, runs.size)
        // Right to left: the second word shows first, on the left.
        assertEquals("بالعالم", runs[0].text)
        assertEquals(2, runs[0].col)
        assertEquals(7, runs[0].cells)
        assertTrue(runs[0].rtl)
        assertEquals("مرحبا", runs[1].text)
        assertEquals(10, runs[1].col)
        assertEquals(5, runs[1].cells)
        // The cells still hold the letters, in the order they show, for search and tests.
        assertEquals("│ ملاعلاب ابحرم", block.row(0))
    }

    /** Plain left-to-right text has no shaped runs and lays out as it always did. */
    @Test
    fun latinTextIsUnchanged() {
        val block = layout("Torch is on")
        assertTrue(block.grid.shaped.isEmpty())
        assertEquals("│ Torch is on", block.row(0))
    }

    /** A long Hebrew line wraps at its spaces in reading order: its first words are on its first row. */
    @Test
    fun rightToLeftTextWrapsInReadingOrder() {
        val block = layout("אחת שתיים שלוש ארבע חמש", cols = 16)
        val first = block.row(0)
        assertTrue(first, first.contains("תחא"))
        assertFalse(first, first.contains("שמח"))
        assertTrue(block.dump(), (1 until block.rows).any { block.row(it).contains("שמח") })
        for (r in 0 until block.rows) assertTrue(block.dump(), CellWidth.total(block.grid.rowText(r)) <= 16)
    }

    /** In a right-to-left frame the gutter is the last column and the time the first; the text reads as it is, against the gutter. */
    @Test
    fun theFrameTurnsAroundInRightToLeft() {
        val m = model("torch on", LogKind.COMMAND)
        val block = LineLayout.layout(m, LogLook(40, rtl = true), "14:02")
        assertEquals("14:02" + " ".repeat(23) + "$ torch on │", block.row(0))
        assertEquals(com.mtwabpin.kolahal.terminal.Ink.PROMPT, block.grid.styleAt(28, 0).fg)
        assertTrue(block.grid.styleAt(30, 0).has(CellStyle.BOLD))
    }

    /** A table in a right-to-left frame: its first column on the right, its box still closed and joined. */
    @Test
    fun tablesMirror() {
        val ltr = LineLayout.layout(lineModel(LogCorpus.row(LogCorpus.table), options), LogLook(40), null)
        val rtl = LineLayout.layout(lineModel(LogCorpus.row(LogCorpus.table), options), LogLook(40, rtl = true), null)
        val top = (0 until rtl.rows).first { rtl.row(it).contains('┌') || rtl.row(it).contains('┐') }
        val border = rtl.row(top).removeSuffix("│").trim()
        assertTrue(border, border.startsWith("┌") && border.endsWith("┐"))
        val header = (0 until rtl.rows).first { rtl.row(it).contains("Coin") }
        val text = rtl.row(header)
        assertTrue(text, text.indexOf("Price") < text.indexOf("Coin"))
        val ltrHeader = ltr.row((0 until ltr.rows).first { ltr.row(it).contains("Coin") })
        assertTrue(ltrHeader, ltrHeader.indexOf("Coin") < ltrHeader.indexOf("Price"))
        // The rule under the header has its joints turned: ╞ on the left, ╡ on the right.
        val rule = rtl.row((0 until rtl.rows).first { rtl.row(it).contains('═') }).removeSuffix("│").trim()
        assertTrue(rule, rule.startsWith("╞") && rule.endsWith("╡"))
    }

    /** A bar in a right-to-left frame fills from the right; a partial block with no mirror character is flagged for the painter. */
    @Test
    fun barsFillFromTheRight() {
        val m = lineModel(LogCorpus.row(LogCorpus.bar), options)
        val ltr = LineLayout.layout(m, LogLook(40), null)
        val rtl = LineLayout.layout(m, LogLook(40, rtl = true), null)
        val r = (0 until ltr.rows).first { ltr.row(it).contains('█') }
        val ltrRow = ltr.grid.rowText(r)
        val rtlRow = rtl.grid.rowText(r)
        // The fill touches the track's start: after ▕ on the left in LTR, before ▏ on the right in RTL.
        assertEquals('█', ltrRow[ltrRow.indexOf('▕') + 1])
        assertEquals('█', rtlRow[rtlRow.lastIndexOf('▏') - 1])
        for (c in 0 until rtl.cols) {
            val g = rtl.grid.glyphAt(c, r)
            if (g in listOf(0x2589, 0x258A, 0x258B, 0x258D, 0x258E)) assertTrue(rtl.grid.styleAt(c, r).has(CellStyle.MIRROR))
        }
    }

    /** Every corpus row lays out in a right-to-left frame: within the width, the gutter last, every target hit where it shows. */
    @Test
    fun everyRowLaysOutRightToLeft() {
        for (line in LogCorpus.all) for (cols in listOf(32, 40, 80)) {
            val m = lineModel(LogCorpus.row(line), options)
            val apps = (m.body as? LineModel.Body.AppGrid)?.apps?.map { GridApp(it.pkg.substringAfterLast('.')) }.orEmpty()
            val block = LineLayout.layout(m, LogLook(cols, rtl = true), "14:02", foldOpen = true, apps = apps, initials = m.person?.let { "AL" })
            val name = "${line.name} at $cols"
            for (r in 0 until block.rows) {
                assertTrue(name, CellWidth.total(block.grid.rowText(r)) <= cols)
                assertEquals("$name, row $r", '│', block.grid.rowText(r)[cols - 1])
            }
            val shown = block.targets.map { it.target }.toSet() - Target.Fold
            assertEquals(name, m.targets().toSet() - Target.Line, shown)
            for (t in block.targets) for (rect in t.rects) {
                assertTrue("$name: $t", rect.col >= 0 && rect.col + rect.cols <= cols)
                assertEquals("$name: $t", t.target, block.hitAt(rect.col, rect.row))
            }
        }
    }

    /** A link's target moves with its words when the frame turns: its cells hold the link's text. */
    @Test
    fun linksFollowTheirWords() {
        val m = lineModel(LogCorpus.row(LogCorpus.table), options)
        val block = LineLayout.layout(m, LogLook(40, rtl = true), null)
        val link = block.targets.first { it.target is Target.Link }
        val r = link.rects.first()
        val shown = block.grid.rowText(r.row).substring(r.col, r.col + r.cols)
        assertTrue(shown, "example feed".startsWith(shown.trim()) || shown.trim().startsWith("example"))
    }

    /** The key bar in a right-to-left frame: the keys in reverse order, each spot over its key. */
    @Test
    fun keyBarTurnsAround() {
        val row = KeyBarCells.row(listOf("a", "b", "c"), 12, 3)
        val turned = row.mirrored
        assertEquals("abc", row.text().filter { it.isLetter() })
        assertEquals("cba", turned.text().filter { it.isLetter() })
        val a = turned.spot(0)!!.rect
        val c = turned.spot(2)!!.rect
        assertTrue("the first key is on the right", a.col > c.col)
        assertEquals('a', turned.text()[a.col + a.cols / 2])
    }

    /** The prompt in a right-to-left frame: `$` on the right, the buttons on the left, the typed text in its order. */
    @Test
    fun promptTurnsAround() {
        val cells = PromptCells.row("ls -la", 6, 6, cursor = false, first = 0, cols = 30, keyBarOn = true).mirrored
        val text = cells.text()
        assertTrue(text, text.trimEnd().endsWith("$"))
        assertTrue(text, text.contains("ls -la"))
        val quick = cells.spot(PromptCells.QUICK)!!.rect
        val keys = cells.spot(PromptCells.KEYS)!!.rect
        assertEquals(28, quick.col)
        assertEquals(0, keys.col)
        // The text area keeps its width: the text starts at its left edge, where the field's text is.
        assertEquals('l'.code, cells.grid.glyphAt(30 - PromptCells.LEAD - PromptCells.textCols(30, clear = true), 0))
    }

    /** A sheet's row turned around: its box closes, and its words read as they are. */
    @Test
    fun sheetRowsTurnAround() {
        val top = SheetCells.top("Actions", 24).mirrored.text()
        assertTrue(top, top.startsWith("┌") && top.endsWith("┐") && top.contains("Actions"))
        val line = SheetCells.line("Copy text", CellStyle.of(com.mtwabpin.kolahal.terminal.Ink.FG), 24).mirrored.text()
        assertTrue(line, line.contains("Copy text") && line.startsWith("│") && line.endsWith("│"))
        val buttons = SheetCells.buttons(listOf("Cancel" to true, "Save" to true), 30)
        val turned = buttons.mirrored
        assertTrue(turned.text(), turned.text().indexOf("Save") < turned.text().indexOf("Cancel"))
        assertTrue(turned.spot(0)!!.rect.col > turned.spot(1)!!.rect.col)
    }

    /** Box shapes and their mirror images. */
    @Test
    fun boxShapesMirror() {
        val pairs = listOf("┌┐", "└┘", "├┤", "╞╡", "╭╮", "╰╯", "▕▏", "▌▐", "╱╲", "▖▗", "╴╶")
        for (p in pairs) {
            assertEquals(p, p[1].code, BoxShapes.mirror(p[0].code))
            assertEquals(p, p[0].code, BoxShapes.mirror(p[1].code))
        }
        for (same in "─│┼┬┴═║█▀▄░") assertEquals("$same", same.code, BoxShapes.mirror(same.code))
        assertEquals(0, BoxShapes.mirror('▎'.code))
    }

    /** What needs the bidi algorithm or shaping, and what doesn't. */
    @Test
    fun whatNeedsBidi() {
        assertFalse(BidiText.needs("Torch is on · 82% ▕███▏ 日本 😀"))
        assertTrue(BidiText.needs("שלום"))
        assertTrue(BidiText.needs("مرحبا"))
        assertTrue(BidiText.needs("नमस्ते"))
        assertTrue(BidiText.needs("สวัสดี"))
        assertEquals(')'.code, BidiText.mirror('('.code))
        assertEquals('a'.code, BidiText.mirror('a'.code))
    }
}
