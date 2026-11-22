package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogCorpus
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.lineModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The decode effect: every cell ends on its real character, and nothing moves on the way there. */
class DecodeTest {
    private val cols = 40

    private fun block(line: LogCorpus.Line) =
        LineLayout.layout(lineModel(LogCorpus.row(line), LineOptions(TimeMode.ALL, 8)), LogLook(cols), "14:02")

    private fun block(text: String) =
        block(LogCorpus.Line(text, LogEntry(70_000, LogCorpus.AT, LogKind.OUTPUT, null, text, LogContext(emptyMap()))))

    /** A decode to all of [block]. */
    private fun decodeOf(block: CellBlock): Decode {
        val d = Decode(block.cols, block.rows)
        for (r in 0 until block.rows) d.target.copyRow(block.grid, r, r)
        d.target.shaped = block.grid.shaped
        d.scan()
        return d
    }

    /** The grid as it is before the effect: the text. */
    private fun gridOf(d: Decode) = CellBuffer(d.cols, d.rows).also { d.frame(it, 0, all = true) }

    /** [d]'s rows decode one after the other; returns when the last one is done. */
    private fun schedule(d: Decode, seed: Int = 5): Int {
        val random = Random(seed)
        d.seed = seed
        for (r in 0 until d.rows) d.line(r, r * 40, random, if (r % 2 == 0) Ink.WARN else null)
        return (d.rows - 1) * 40 + Decode.LINE_MS
    }

    private fun assertSettled(name: String, d: Decode, grid: CellBuffer) {
        for (r in 0 until d.rows) assertTrue("$name: row $r is '${grid.rowText(r)}'", grid.sameRow(r, d.target, r))
    }

    /** The cells [grid] fills are just those of the text: the same blanks, the same wide characters, the same shapes. */
    private fun assertNothingMoved(name: String, d: Decode, grid: CellBuffer) {
        for (r in 0 until d.rows) for (c in 0 until d.cols) {
            val g = grid.glyphAt(c, r)
            val real = d.target.glyphAt(c, r)
            assertEquals("$name at $c,$r", real == CellBuffer.EMPTY || real == 0x20, g == CellBuffer.EMPTY || g == 0x20)
            assertEquals("$name at $c,$r", real == CellBuffer.WIDE_TAIL, g == CellBuffer.WIDE_TAIL)
            if (real == CellBuffer.WIDE_TAIL) continue
            assertEquals("$name at $c,$r", d.target.cellsOf(real), grid.cellsOf(g))
            if (BoxShapes.isDrawn(real)) assertEquals("$name at $c,$r", real, g)
        }
    }

    @Test
    fun everyKindOfLineEndsAsItWasAndNeverMoves() {
        for (line in LogCorpus.all) {
            val d = decodeOf(block(line))
            val grid = gridOf(d)
            assertSettled(line.name, d, grid)
            val end = schedule(d)
            var t = 0
            while (t <= end) {
                d.frame(grid, t)
                assertNothingMoved("${line.name} at $t", d, grid)
                t += 33
            }
            assertFalse(line.name, d.frame(grid, end))
            assertSettled(line.name, d, grid)
        }
    }

    /** The characters do change on the way, and the tint shows; once settled, nothing is drawn again. */
    @Test
    fun itScramblesThenRests() {
        val d = decodeOf(block("Battery 82% charging, 40 minutes to full"))
        val grid = gridOf(d)
        val end = schedule(d)
        val before = grid.dump()
        assertTrue(d.frame(grid, 100))
        assertNotEquals(before, grid.dump())
        var tinted = false
        for (c in 0 until d.cols) if (grid.styleAt(c, 0).fg == Ink.WARN) tinted = true
        assertTrue(tinted)
        assertFalse(d.frame(grid, end))
        assertEquals(before, grid.dump())
        grid.dirty.clear()
        assertFalse(d.frame(grid, end + 500))
        assertTrue(grid.dirty.isEmpty)
    }

    /** A wide character passes through wide ones only. */
    @Test
    fun wideCharactersStayWide() {
        val d = decodeOf(block("漢字 かな 🎉 party 👩🏽‍💻"))
        val grid = gridOf(d)
        val end = schedule(d)
        var changed = false
        for (t in 0..end step 25) {
            d.frame(grid, t)
            assertNothingMoved("at $t", d, grid)
            if (grid.dump() != d.target.dump()) changed = true
        }
        assertTrue(changed)
        assertSettled("the end", d, grid)
    }

    /** A word whose letters join keeps its letters at every moment, and is faint as a whole or not at all. */
    @Test
    fun joiningWordsStayWhole() {
        for (text in listOf("مرحبا بالعالم", "नमस्ते दुनिया", "สวัสดี ชาวโลก")) {
            val d = decodeOf(block(text))
            val grid = gridOf(d)
            val runs = d.target.shaped
            assertTrue(text, runs.isNotEmpty())
            val end = schedule(d)
            var faint = false
            for (t in 0..end step 25) {
                d.frame(grid, t)
                for (run in runs) {
                    val styles = (run.col until run.col + run.cells).map { grid.styleAt(it, run.row).has(CellStyle.FAINT) }.toSet()
                    assertEquals("$run at $t", 1, styles.size)
                    faint = faint || true in styles
                    for (c in run.col until run.col + run.cells) {
                        assertEquals(d.target.glyphAt(c, run.row) > 0, grid.glyphAt(c, run.row) > 0)
                        if (d.target.glyphAt(c, run.row) > 0) assertEquals(d.target.glyphAt(c, run.row), grid.glyphAt(c, run.row))
                    }
                }
            }
            assertTrue(text, faint)
            assertSettled(text, d, grid)
        }
    }

    /** The same seed gives the same cells at the same time, whatever frames came before; another gives others. */
    @Test
    fun itIsDeterministic() {
        val text = "The quick brown fox jumps over the lazy dog"
        fun at(seed: Int, t: Int, stepped: Boolean): String {
            val d = decodeOf(block(text))
            val grid = gridOf(d)
            schedule(d, seed)
            if (stepped) for (s in 0 until t step 10) d.frame(grid, s)
            d.frame(grid, t)
            return grid.dump()
        }
        assertEquals(at(1, 300, stepped = true), at(1, 300, stepped = false))
        assertNotEquals(at(1, 300, stepped = false), at(2, 300, stepped = false))
    }

    /** Hidden, a cell shows nothing before its time, and the rows can be drawn from a later one on. */
    @Test
    fun cellsCanComeOneByOne() {
        val d = Decode(5, 2)
        d.target.put(0, 0, "a b", CellStyle.PLAIN)
        d.target.put(0, 1, "cd", CellStyle.PLAIN)
        d.scan()
        for (c in 0 until 3) d.cell(c, 0, c * 100, c * 100 + 50)
        d.cell(0, 1, 500, 550)
        val grid = CellBuffer(5, 2)
        d.frame(grid, 60, all = true, hidden = true)
        assertEquals("a", grid.rowText(0).trimEnd())
        assertTrue(d.frame(grid, 260, all = true, hidden = true))
        assertEquals("a b", grid.rowText(0).trimEnd())
        assertEquals("", grid.rowText(1).trimEnd())
        assertFalse(d.frame(grid, 600, all = true, hidden = true, first = 1))
        // The second character has no time, so it never comes.
        assertEquals("c", grid.rowText(0).trimEnd())
    }
}
