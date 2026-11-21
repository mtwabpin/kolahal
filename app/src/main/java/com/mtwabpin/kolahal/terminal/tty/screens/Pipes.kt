/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import kotlin.random.Random

/**
 * `pipes`: pipes that grow across the screen and turn at random, as pipes.sh draws them. A pipe
 * that leaves one edge comes back at the other in a new color. When the screen is about full, it
 * clears and the pipes go on. It runs until you leave it.
 *
 * The pipes are heavy box-drawing lines, which the painter draws as geometry, so they join at
 * every font size and line height.
 */
class PipesScreen : TtyScreen {
    override val name = "pipes"
    override val title = "Pipes"
    override val fps = 30

    private var random: Random = Random(0)
    private var cols = 0
    private var rows = 0

    private val x = IntArray(PIPES)
    private val y = IntArray(PIPES)
    private val dir = IntArray(PIPES)
    private val ink = IntArray(PIPES)
    private val straight = IntArray(PIPES)
    private var elapsed = 0L
    private var drawn = 0

    override fun start(env: ScreenEnv) {
        random = env.random
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols.coerceAtLeast(1)
        this.rows = rows.coerceAtLeast(1)
        for (i in 0 until PIPES) {
            x[i] = random.nextInt(this.cols)
            y[i] = random.nextInt(this.rows)
            dir[i] = random.nextInt(4)
            ink[i] = random.nextInt(INKS.size)
            straight[i] = 0
        }
        drawn = 0
    }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        elapsed += deltaMs.coerceIn(0L, 200L)
        while (elapsed >= STEP_MS) {
            elapsed -= STEP_MS
            for (i in 0 until PIPES) step(grid, i)
            drawn += PIPES
            // pipes.sh clears after a set number of pieces; here, once the screen is about full.
            if (drawn >= cols * rows) {
                grid.clear()
                drawn = 0
            }
        }
        return true
    }

    /** Draws pipe [i]'s next piece where it is, then moves it on. */
    private fun step(grid: CellBuffer, i: Int) {
        val from = dir[i]
        val to = if (straight[i] >= MIN_STRAIGHT && random.nextInt(100) < TURN_PERCENT) {
            (from + if (random.nextBoolean()) 1 else 3) % 4
        } else from
        straight[i] = if (to == from) straight[i] + 1 else 0
        grid.put(x[i], y[i], SHAPES[from * 4 + to], STYLES[ink[i]])
        dir[i] = to
        x[i] += DX[to]
        y[i] += DY[to]
        // Out at one edge, in at the other, in a new color.
        var wrapped = true
        when {
            x[i] < 0 -> x[i] = cols - 1
            x[i] >= cols -> x[i] = 0
            y[i] < 0 -> y[i] = rows - 1
            y[i] >= rows -> y[i] = 0
            else -> wrapped = false
        }
        if (wrapped) ink[i] = random.nextInt(INKS.size)
    }

    private companion object {
        const val PIPES = 2
        const val STEP_MS = 25L
        const val MIN_STRAIGHT = 2
        const val TURN_PERCENT = 13

        // Directions: 0 up, 1 right, 2 down, 3 left.
        val DX = intArrayOf(0, 1, 0, -1)
        val DY = intArrayOf(-1, 0, 1, 0)

        /**
         * The piece for a pipe going [from] and then [to], at [from] * 4 + [to]: straight on, or the
         * corner that joins the side it came in by to the side it goes out by. A pipe never turns back.
         */
        val SHAPES = intArrayOf(
            0x2503, 0x250F, 0x2503, 0x2513, // going up: ┃ ┏ · ┓
            0x251B, 0x2501, 0x2513, 0x2501, // going right: ┛ ━ ┓ ·
            0x2503, 0x2517, 0x2503, 0x251B, // going down: · ┗ ┃ ┛
            0x2517, 0x2501, 0x250F, 0x2501, // going left: ┗ · ┏ ━
        )

        val INKS = listOf(Ink.PROMPT, Ink.INFO, Ink.LINK, Ink.SCRIPT, Ink.GOOD, Ink.WARN, Ink.ERROR, Ink.PRIVATE, Ink.WORK)
        val STYLES = INKS.map { CellStyle.of(it) }.toTypedArray()
    }
}
