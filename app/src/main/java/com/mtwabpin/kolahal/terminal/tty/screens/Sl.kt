/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle

/**
 * `sl`: a steam locomotive crosses the screen once, right to left, with smoke from its funnel,
 * as the Unix joke for a mistyped `ls` does. Then the screen closes by itself.
 */
class SlScreen : TtyScreen {
    override val name = "sl"
    override val title = "Steam locomotive"
    override val fps = 30

    private var cols = 0
    private var rows = 0
    private var x = 0
    private var top = 0
    private var steps = 0
    private var elapsed = 0L

    // Smoke puffs: where each is and how old (-1: none).
    private val puffCol = IntArray(PUFFS)
    private val puffRow = IntArray(PUFFS)
    private val puffAge = IntArray(PUFFS) { -1 }

    override fun start(env: ScreenEnv) {
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols
        this.rows = rows
        // Just off the right edge, a little below the middle so the smoke has room above.
        if (steps == 0) x = cols
        top = ((rows - TRAIN.size) / 2 + 1).coerceIn(0, (rows - TRAIN.size).coerceAtLeast(0))
    }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        elapsed += deltaMs.coerceIn(0L, 200L)
        var moved = false
        while (elapsed >= STEP_MS) {
            elapsed -= STEP_MS
            step()
            moved = true
        }
        if (moved || steps == 0) draw(grid)
        return x + WIDTH > 0 || puffAge.any { it >= 0 }
    }

    /** Over the middle of the screen. */
    override fun still(grid: CellBuffer) {
        x = (cols - WIDTH) / 2
        steps = 1
        for (i in 0 until PUFFS) puffAge[i] = -1
        repeat(3) { puff(it * 3) }
        draw(grid)
    }

    private fun step() {
        steps++
        x--
        for (i in 0 until PUFFS) if (puffAge[i] >= 0) {
            puffAge[i]++
            if (puffAge[i] % 2 == 0) puffRow[i]--
            if (puffAge[i] >= SMOKE.size * 2) puffAge[i] = -1
        }
        if (steps % 4 == 0 && x + FUNNEL in 0 until cols) puff(0)
    }

    /** A new puff of smoke over the funnel, [age] steps old. */
    private fun puff(age: Int) {
        val i = puffAge.indexOfFirst { it < 0 }
        if (i < 0) return
        puffCol[i] = x + FUNNEL - 1
        puffRow[i] = top - 1 - age / 2
        puffAge[i] = age
    }

    private fun draw(grid: CellBuffer) {
        grid.clear()
        for (i in 0 until PUFFS) if (puffAge[i] >= 0) {
            grid.put(puffCol[i], puffRow[i], SMOKE[puffAge[i] / 2], SMOKE_STYLE)
        }
        for (r in TRAIN.indices) {
            val line = if (r == TRAIN.size - 1) WHEELS[(steps / 2) % WHEELS.size] else TRAIN[r]
            grid.put(x, top + r, line, if (r == TRAIN.size - 1) WHEEL_STYLE else BODY_STYLE)
        }
    }

    private companion object {
        const val STEP_MS = 45L
        const val PUFFS = 12

        /** The engine faces left: its funnel, boiler and cab, then the tender. The last row is the wheels. */
        val TRAIN = listOf(
            "    ___                                  ",
            "   |___|          _______                ",
            " ___| |__________|  [ ] |   ___________  ",
            "|  ________________     |-|           |  ",
            "|_|________________|____|-|___________|  ",
            " \\(O)==(O)==(O)/   (O)      (O)     (O)  ",
        )

        /** The wheels row in turn, so the rods seem to move. */
        val WHEELS = listOf(
            " \\(O)==(O)==(O)/   (O)      (O)     (O)  ",
            " \\(O)--(O)--(O)/   (O)      (O)     (O)  ",
            " \\(O)=-(O)=-(O)/   (O)      (O)     (O)  ",
        )

        val WIDTH = TRAIN.maxOf { it.length }

        /** The column of the funnel's middle, from the engine's left edge. */
        const val FUNNEL = 5

        /** A puff, from new to nearly gone. Each look lasts two steps. */
        val SMOKE = listOf("(@@)", "(@)", "( )", "()", "o", ".")

        val BODY_STYLE = CellStyle.of(Ink.FG)
        val WHEEL_STYLE = CellStyle.of(Ink.PROMPT)
        val SMOKE_STYLE = CellStyle.of(Ink.DIM)
    }
}
