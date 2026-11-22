/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import com.mtwabpin.kolahal.terminal.tty.Decode
import com.mtwabpin.kolahal.terminal.tty.LineLayout
import com.mtwabpin.kolahal.terminal.tty.ShapedRun
import kotlin.random.Random

/**
 * `decode`: the log lines on home scramble and settle again ([Decode]), where home shows them. All
 * of them first, from the top down; then, at once, a wave from somewhere else ([Wave]), never the same
 * one twice in a row, and so on for as long as it is open. A wave takes a part of the lines at a time,
 * so the rest can be read.
 *
 * It shows what home's log shows and nothing else: it reads home again now and then ([READ_MS];
 * more often at the start and after a pause), so a line that comes in decodes in, and one that home
 * no longer shows goes. Between waves it needs no frame but for that ([restMs]).
 *
 * Now and then it all moves by a cell, so a screen that shows it for hours doesn't burn in.
 */
class DecodeScreen : TtyScreen, PlacedRows {
    override val name = "decode"
    override val title = "Decoding log"
    override val fps = 24

    /** The still frame is the plain text, read again now and then as home changes. */
    override val stillEveryMs = READ_MS

    private enum class Wave { ALL, BAND, UP, SCATTERED, COLUMNS }

    private lateinit var env: ScreenEnv
    private val random: Random get() = env.random

    private var cols = 0
    private var rows = 0

    /** The lines as they show, and room to lay out the next ones. */
    private var decode = Decode(1, 1)
    private var spare = Decode(1, 1)

    /** The rows that show a line, and where each one is. */
    private var count = 0
    private var tops = IntArray(0)
    private var left = 0

    /** Home's lines were read. */
    private var known = false

    /** The time of the last frame, when home is read next, and until when that is often. */
    private var now = 0L
    private var nextRead = 0L
    private var oftenUntil = 0L

    /** When home's log was last there. */
    private var lastSeen = 0L

    private var running = false
    private var waveAt = 0L
    private var nextWave = 0L
    private var waves = 0
    private var lastWave = Wave.ALL

    /** The place in [SHIFT_COLS] and [SHIFT_ROWS]. */
    private var shift = 0

    override fun start(env: ScreenEnv) {
        this.env = env
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols.coerceAtLeast(1)
        this.rows = rows.coerceAtLeast(1)
        decode = Decode(this.cols, this.rows)
        spare = Decode(this.cols, this.rows)
        tops = IntArray(this.rows)
        count = 0
        known = false
        running = false
        lastSeen = now
        resumed()
    }

    override fun resumed() {
        nextRead = 0L
        oftenUntil = now + OFTEN_FOR_MS
    }

    override val rowLeft: Int get() = left + SHIFT_COLS[shift] * env.cellW

    override fun rowTop(row: Int): Int =
        if (row < count) tops[row] + SHIFT_ROWS[shift] * env.cellH else PlacedRows.UNPLACED

    override val restMs: Long
        get() = when {
            !known -> OFTEN_MS
            running -> 0L
            else -> (minOf(nextWave, nextRead) - now).coerceAtLeast(0L)
        }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        now = timeMs
        if (timeMs >= nextRead) {
            read(grid, decodeNew = true)
            nextRead = timeMs + if (timeMs < oftenUntil) OFTEN_MS else READ_MS
        }
        if (!known) return true
        if (!running && timeMs >= nextWave) begin(grid)
        if (running && !decode.frame(grid, (timeMs - waveAt).toInt())) {
            running = false
            decode.clear()
            nextWave = timeMs + REST_MS + random.nextInt(REST_MORE_MS)
        }
        return true
    }

    override fun still(grid: CellBuffer) = read(grid, decodeNew = false)

    /**
     * Reads the lines home shows now and puts them on [grid], if anything changed. Lines that were
     * there go on as they are, a wave included, wherever they moved to; with [decodeNew], other
     * lines decode in.
     */
    private fun read(grid: CellBuffer, decodeNew: Boolean) {
        val log = env.log()?.takeIf { it.rows.isNotEmpty() }
        // Home's list may not be laid out yet, or for a moment.
        if (log == null && decodeNew && now - lastSeen < WAIT_MS) return
        if (log != null) lastSeen = now
        val shown = log ?: nothing()
        // More rows than the grid has: those nearest the prompt.
        val from = (shown.rows.size - rows).coerceAtLeast(0)
        val n = shown.rows.size - from
        val next = spare
        val shaped = ArrayList<ShapedRun>()
        var moved = !known || n != count || left != shown.left
        left = shown.left
        for (r in 0 until rows) {
            if (r >= n) {
                next.target.fill(0, r, cols, 1, CellBuffer.EMPTY, CellStyle.PLAIN)
                continue
            }
            val line = shown.rows[from + r]
            next.target.copyRow(line.block.grid, line.row, r)
            for (run in line.block.grid.shaped) {
                if (run.row == line.row) shaped += ShapedRun(r, run.col, run.cells, run.text, run.rtl)
            }
            val top = shown.top + line.top
            if (tops[r] != top) moved = true
            tops[r] = top
        }
        if (!known || n != count || !same(next, n)) replace(grid, next, n, shaped, decodeNew && known)
        if (moved) grid.dirty.set(0, grid.rows)
        if (!known) {
            known = true
            nextWave = now + FIRST_MS
        }
    }

    private fun same(next: Decode, n: Int): Boolean {
        for (r in 0 until n) if (!next.target.sameRow(r, decode.target, r)) return false
        return true
    }

    /** [next]'s [n] lines take the place of those on [grid]. */
    private fun replace(grid: CellBuffer, next: Decode, n: Int, shaped: List<ShapedRun>, decodeNew: Boolean) {
        next.target.shaped = shaped
        next.scan()
        next.clear()
        next.seed = decode.seed
        if (!running) waveAt = now
        val t = (now - waveAt).toInt()
        val taken = BooleanArray(count)
        var fresh = 0
        for (r in 0 until n) {
            val old = (0 until count).firstOrNull { !taken[it] && next.target.sameRow(r, decode.target, it) }
            if (old != null) {
                taken[old] = true
                if (running) next.carry(decode, old, r)
            } else if (decodeNew && next.hasText(r)) {
                next.line(r, t + fresh++ * NEW_STEP_MS, random, tint())
            }
        }
        spare = decode
        decode = next
        count = n
        if (fresh > 0) running = true
        grid.shaped = shaped
        decode.frame(grid, t, all = true)
        grid.dirty.set(0, grid.rows)
    }

    /** One short line in the middle, for when home's log shows nothing. */
    private fun nothing(): ShownLog {
        val width = CellWidth.total(NOTHING)
        val block = LineLayout.lines(width, listOf(NOTHING to CellStyle.of(Ink.DIM)))
        val col = ((cols - width) / 2).coerceAtLeast(0)
        return ShownLog(col * env.cellW, rows / 2 * env.cellH, listOf(ShownRow(block, 0, 0)))
    }

    /** Starts the next wave. */
    private fun begin(grid: CellBuffer) {
        if (waves > 0 && waves % SHIFT_EVERY == 0) {
            shift = (shift + 1) % SHIFT_COLS.size
            grid.dirty.set(0, grid.rows)
        }
        val others = Wave.entries.filter { it != Wave.ALL && it != lastWave }
        val wave = if (waves == 0) Wave.ALL else others[random.nextInt(others.size)]
        lastWave = wave
        waves++
        decode.clear()
        decode.seed = random.nextInt()
        when (wave) {
            // Every line, quickly, from the top down.
            Wave.ALL -> for (r in 0 until count) line(r, r * step(ALL_STEP_MS, 1_600, count) + random.nextInt(80))
            // A band of a few lines sweeps down.
            Wave.BAND -> for (r in 0 until count) line(r, r * step(110, 4_200, count))
            // From the prompt up.
            Wave.UP -> for (r in 0 until count) line(r, (count - 1 - r) * step(80, 3_000, count))
            // A third of the lines, here and there; at least one.
            Wave.SCATTERED -> {
                val one = random.nextInt(count.coerceAtLeast(1))
                for (r in 0 until count) if (r == one || random.nextInt(3) == 0) line(r, random.nextInt(3_000))
            }
            // Column by column, from the left or the right.
            Wave.COLUMNS -> columns(fromRight = random.nextBoolean())
        }
        waveAt = now
        running = true
    }

    private fun line(row: Int, at: Int) = decode.line(row, at, random, tint())

    private fun columns(fromRight: Boolean) {
        val step = step(45, 2_600, cols)
        for (r in 0 until count) {
            if (!decode.hasText(r)) continue
            for (c in 0 until cols) {
                val at = (if (fromRight) cols - 1 - c else c) * step + random.nextInt(200)
                decode.cell(c, r, at, at + 220 + random.nextInt(260))
            }
        }
    }

    /** [most] ms from one to the next of [n], less when all of them would take more than [inAll]. */
    private fun step(most: Int, inAll: Int, n: Int): Int = minOf(most, inAll / n.coerceAtLeast(1))

    /** Some lines flicker in the warning or the accent color as they settle. */
    private fun tint(): Ink? = if (random.nextInt(5) > 0) null else if (random.nextBoolean()) Ink.WARN else Ink.PROMPT

    companion object {
        /** The text stays as it is this long before the first wave. */
        const val FIRST_MS = 400L

        /** A wave takes this long at most. */
        const val WAVE_MAX_MS = 6_000L

        /** Between waves: a breath, at least this long, and up to [REST_MORE_MS] more. */
        const val REST_MS = 200L
        const val REST_MORE_MS = 500

        /** How often home's lines are read, and how often for [OFTEN_FOR_MS] at the start and after a pause. */
        const val READ_MS = 1_000L
        const val OFTEN_MS = 100L
        const val OFTEN_FOR_MS = 2_000L

        /** How long home's log may be missing before that is what shows. */
        const val WAIT_MS = 1_000L

        private const val ALL_STEP_MS = 40
        private const val NEW_STEP_MS = 60
        private const val NOTHING = "The log is empty"

        /** Every so many waves, all of it moves to the next of these places. */
        private const val SHIFT_EVERY = 10
        private val SHIFT_COLS = intArrayOf(0, 1, 1, 0, -1, -1, -1, 0, 1)
        private val SHIFT_ROWS = intArrayOf(0, 0, -1, -1, -1, 0, 1, 1, 1)
    }
}
