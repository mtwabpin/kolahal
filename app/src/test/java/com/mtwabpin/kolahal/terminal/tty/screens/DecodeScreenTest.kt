package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.tty.CellBlock
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.LineLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** `decode`: home's lines, where home has them, in waves, one after the other. */
class DecodeScreenTest {
    private val cols = 30
    private val rows = 20
    private val cellW = 10
    private val cellH = 20
    private val words = List(9) { "Line $it of the log, as home has it".take(16 + it).trimEnd() }

    /** Home with these lines, one row each, 24 px apart from 100 px down, 12 px from the left. */
    private fun home(lines: List<String>) = ShownLog(12, 100, lines.mapIndexed { i, text -> ShownRow(blockOf(text), 0, i * 24) })

    private val blocks = HashMap<String, CellBlock>()
    private fun blockOf(text: String) = blocks.getOrPut(text) { LineLayout.lines(cols, listOf(text to CellStyle.PLAIN)) }

    private inner class Run(var log: ShownLog?, seed: Int = 7, rows: Int = this@DecodeScreenTest.rows) {
        val screen = DecodeScreen()
        val grid = CellBuffer(cols, rows)
        var t = 0L

        init {
            screen.start(ScreenEnv(cols, rows, Random(seed), { 0L }, true, cellW = cellW, cellH = cellH, log = { this@Run.log }))
        }

        /** One frame, when the host would draw the next one. */
        fun step() {
            t += maxOf(42L, screen.restMs)
            assertTrue(screen.frame(grid, t, 42))
        }

        fun until(ms: Long, each: () -> Unit = {}) {
            while (t < ms) {
                step()
                each()
            }
        }

        /** On to the moment between two waves, when all of it is plain text. */
        fun settle() {
            do step() while (screen.restMs == 0L)
        }

        fun text(): List<String> = grid.dump().lines().map { it.trimEnd() }.dropLastWhile { it.isEmpty() }
    }

    @Test
    fun itOpensWithHomesLinesWhereHomeHasThem() {
        val run = Run(home(words))
        run.step()
        assertEquals(words, run.text())
        assertEquals(12, run.screen.rowLeft)
        for (i in words.indices) assertEquals(100 + i * 24, run.screen.rowTop(i))
        // The rows with no line are where any grid has them.
        assertEquals(PlacedRows.UNPLACED, run.screen.rowTop(words.size))
    }

    /** Every wave ends in time with the plain text, scrambles on the way, and the rests draw nothing. */
    @Test
    fun wavesAndRestsTakeTurns() {
        val run = Run(home(words))
        val lengths = ArrayList<Long>()
        val rests = ArrayList<Long>()
        val places = HashSet<Pair<Int, Int>>()
        var since = 0L
        var wasRunning = false
        var scrambled = 0
        while (run.t < 300_000) {
            run.grid.dirty.clear()
            run.step()
            val running = run.screen.restMs == 0L
            if (!running && !wasRunning && run.t > 200) assertTrue("at ${run.t}", run.grid.dirty.isEmpty)
            if (running != wasRunning) {
                (if (running) rests else lengths) += run.t - since
                since = run.t
                wasRunning = running
            }
            if (!running) assertEquals("at ${run.t}", words, run.text())
            else if (run.text() != words) scrambled++
            // The same cells at every moment: the blanks stay blank.
            for (r in words.indices) for (c in 0 until cols) {
                val blank = words[r].getOrElse(c) { ' ' } == ' '
                val g = run.grid.glyphAt(c, r)
                assertEquals("$c,$r at ${run.t}", blank, g == CellBuffer.EMPTY || g == 0x20)
            }
            places += run.screen.rowLeft to run.screen.rowTop(0)
        }
        // It goes on for as long as it is open: five minutes of waves, and hardly anything between them.
        assertTrue("${lengths.size} waves", lengths.size > 60)
        assertTrue("${rests.sum()} ms of rest", rests.sum() < 300_000 / 3)
        assertTrue("$scrambled frames", scrambled > 100)
        for (ms in lengths) assertTrue("a wave of $ms ms", ms <= DecodeScreen.WAVE_MAX_MS)
        // The first wait is shorter, so the effect shows at once.
        for (ms in rests.drop(1)) {
            assertTrue("a rest of $ms ms", ms >= DecodeScreen.REST_MS && ms <= DecodeScreen.REST_MS + DecodeScreen.REST_MORE_MS + 200)
        }
        // It all stays where it is: nothing moves between waves.
        assertEquals(setOf(12 to 100), places.toSet())
    }

    @Test
    fun theSameSeedGivesTheSameFrames() {
        fun at(seed: Int, ms: Long) = Run(home(words), seed).also { it.until(ms) }.grid.dump()
        assertEquals(at(3, 900), at(3, 900))
        assertNotEquals(at(3, 900), at(4, 900))
    }

    /** A line that comes in decodes in; the others stay as they are, where home now has them. */
    @Test
    fun aNewLineDecodesIn() {
        val run = Run(home(words))
        run.until(6_000)
        run.settle()
        assertEquals(words, run.text())
        val more = words + "Something happened"
        run.log = ShownLog(12, 76, home(more).rows)
        var scrambled = false
        val until = run.t + 3_000
        run.until(until) {
            val text = run.text()
            if (text.size > words.size && text.last() != more.last()) scrambled = true
        }
        assertTrue(scrambled)
        run.settle()
        assertEquals(more, run.text())
        assertEquals(76, run.screen.rowTop(0))
    }

    /** A line that home no longer shows (it was deleted, or its private space is locked) goes at the next read. */
    @Test
    fun aLineThatHomeHidesGoes() {
        val run = Run(home(words))
        run.until(3_000)
        run.log = home(words.drop(3))
        run.until(run.t + DecodeScreen.READ_MS + 50)
        assertEquals(words.size - 3, run.text().size)
        run.until(7_000)
        run.settle()
        assertEquals(words.drop(3), run.text())
        // After a pause it reads home at once.
        run.log = home(words.drop(5))
        run.screen.resumed()
        run.screen.frame(run.grid, run.t + 42, 42)
        assertEquals(words.drop(5), run.text())
    }

    /** More rows than the grid has: those nearest the prompt. */
    @Test
    fun aSmallGridShowsTheLowestLines() {
        val run = Run(home(words), rows = 5)
        run.step()
        assertEquals(words.takeLast(5), run.text())
        assertEquals(100 + 4 * 24, run.screen.rowTop(0))
        run.until(6_000)
        run.settle()
        assertEquals(words.takeLast(5), run.text())
    }

    /** Nothing to show: home's log isn't there yet, then one line says so. */
    @Test
    fun nothingShowsAShortLine() {
        val run = Run(null)
        run.until(500)
        assertEquals(emptyList<String>(), run.text())
        run.until(1_300)
        run.settle()
        assertEquals(listOf("The log is empty"), run.text())
        // Once home's lines are there, they show.
        run.log = home(words)
        run.until(8_000)
        run.settle()
        assertEquals(words, run.text())
    }

    /** With animations off it is the plain text, and follows home without any motion. */
    @Test
    fun theStillFrameIsThePlainText() {
        val run = Run(home(words))
        run.screen.still(run.grid)
        assertEquals(words, run.text())
        run.grid.dirty.clear()
        run.screen.still(run.grid)
        assertTrue(run.grid.dirty.isEmpty)
        run.log = home(words.drop(2))
        run.screen.still(run.grid)
        assertEquals(words.drop(2), run.text())
        assertTrue(run.screen.stillEveryMs > 0)
    }

    /** A joined word is painted whole: the grid gets the words of the rows it shows. */
    @Test
    fun joiningWordsAreOnTheGrid() {
        val run = Run(home(listOf("first", "مرحبا بالعالم", "last")))
        run.step()
        assertEquals(2, run.grid.shaped.size)
        assertTrue(run.grid.shaped.all { it.row == 1 })
        run.log = home(listOf("مرحبا بالعالم", "last"))
        run.until(run.t + 1_500)
        assertTrue(run.grid.shaped.all { it.row == 0 })
    }

    /** On the grid, each whole row where home draws it: not those cut off at the top or the bottom. */
    @Test
    fun rowsOnTheGridAreWhereHomeHasThem() {
        val three = LineLayout.lines(cols, listOf("a\nb\nc" to CellStyle.PLAIN))
        assertEquals(3, three.rows)
        // Newest first: one item at the bottom, one that is partly above the top.
        val items = listOf(ShownItem(blockOf("new"), 140, 25, 5), ShownItem(three, -10, 65, 5), ShownItem(blockOf("cut"), 190, 25, 0))
        val shown = ShownRows.onGrid(items, height = 200, cellH = 20)
        assertEquals(listOf(1 to 15, 2 to 35, 0 to 145), shown.map { it.row to it.top })
    }
}
