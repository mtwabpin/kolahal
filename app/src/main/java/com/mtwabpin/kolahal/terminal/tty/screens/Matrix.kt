/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import kotlin.random.Random

/**
 * `matrix`: falling streams of characters, as cmatrix draws them. Each stream falls down its own
 * column at its own speed: a bright head, the accent color behind it, fading toward its tail. Now
 * and then a character in a stream changes. It runs until you leave it.
 *
 * Streams use every other column, as cmatrix does, so the rain doesn't crowd. The characters are
 * half-width katakana, digits and a few signs; the painter fits a fallback font's katakana into
 * its cell.
 */
class MatrixScreen : TtyScreen {
    override val name = "matrix"
    override val title = "Matrix rain"
    override val fps = 30

    private var random: Random = Random(0)
    private var cols = 0
    private var rows = 0

    // One stream per used column. While a stream waits to start, [wait] counts down.
    private var head = IntArray(0)
    private var length = IntArray(0)
    private var period = IntArray(0)
    private var elapsed = IntArray(0)
    private var wait = IntArray(0)

    override fun start(env: ScreenEnv) {
        random = env.random
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols.coerceAtLeast(1)
        this.rows = rows.coerceAtLeast(1)
        val streams = (this.cols + 1) / 2
        head = IntArray(streams)
        length = IntArray(streams)
        period = IntArray(streams)
        elapsed = IntArray(streams)
        // At first the streams start over 2 seconds, so the screen fills from the top.
        wait = IntArray(streams) { random.nextInt(0, 2_000) }
        for (i in 0 until streams) head[i] = IDLE
    }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        val delta = deltaMs.coerceIn(0L, MAX_STEP_MS).toInt()
        for (i in head.indices) {
            if (head[i] == IDLE) {
                wait[i] -= delta
                if (wait[i] > 0) continue
                begin(i)
            }
            elapsed[i] += delta
            while (head[i] != IDLE && elapsed[i] >= period[i]) {
                elapsed[i] -= period[i]
                step(grid, i)
            }
        }
        glitch(grid)
        return true
    }

    /** A new stream in column [i] * 2: it enters at the top on its first step. */
    private fun begin(i: Int) {
        head[i] = -1
        length[i] = random.nextInt(4, (rows * 3 / 4).coerceAtLeast(5) + 1)
        period[i] = random.nextInt(35, 120)
        elapsed[i] = period[i]
    }

    /** Moves stream [i] down one row: a new head, the old head in the accent, the tail fades and goes. */
    private fun step(grid: CellBuffer, i: Int) {
        val col = i * 2
        val h = ++head[i]
        val len = length[i]
        restyle(grid, col, h - 1, NEAR)
        restyle(grid, col, h - 2, BODY)
        restyle(grid, col, h - len * 2 / 3, FADED)
        if (h < rows) grid.put(col, h, GLYPHS[random.nextInt(GLYPHS.size)], HEAD)
        grid.put(col, h - len, CellBuffer.EMPTY, BODY)
        if (h - len >= rows) {
            head[i] = IDLE
            wait[i] = random.nextInt(0, 1_500)
        }
    }

    /** Gives the character at [col], [row] another style, if there is one. */
    private fun restyle(grid: CellBuffer, col: Int, row: Int, style: CellStyle) {
        val g = grid.glyphAt(col, row)
        if (g > 0) grid.put(col, row, g, style)
    }

    /** A few characters in the streams change, as in the film. */
    private fun glitch(grid: CellBuffer) {
        repeat(cols * rows / 300 + 1) {
            val c = random.nextInt(cols)
            val r = random.nextInt(rows)
            if (grid.glyphAt(c, r) > 0) grid.put(c, r, GLYPHS[random.nextInt(GLYPHS.size)], grid.styleAt(c, r))
        }
    }

    private companion object {
        const val IDLE = Int.MIN_VALUE
        const val MAX_STEP_MS = 200L

        val HEAD = CellStyle.of(Ink.FG, flags = CellStyle.BOLD)
        val NEAR = CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD)
        val BODY = CellStyle.of(Ink.PROMPT)
        val FADED = CellStyle.of(Ink.PROMPT, flags = CellStyle.FAINT)

        /** Half-width katakana (U+FF66–FF9D), digits and the signs of the film's rain. */
        val GLYPHS: IntArray = ((0xFF66..0xFF9D).toList() + "0123456789Z:.=*+-<>|".map { it.code }).toIntArray()
    }
}
