/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink
import kotlin.random.Random

/**
 * The decode effect on the grid: characters pass through random ones and settle on the real one,
 * as on the website's home page. [target] holds the real text; each cell has a time it starts to
 * scramble and a time it settles ([cell], [line]), and [frame] draws the cells as they are at a
 * time. It is pure: the same schedule at the same time gives the same cells, however many frames
 * were drawn before.
 *
 * Nothing on the grid moves while it runs:
 * - A character is only ever replaced by one as wide (1 or 2 cells).
 * - Blank cells, and box drawing and blocks (the gutter, tables, bars), stay as they are.
 * - A word whose letters join (Arabic, the Indic scripts, Thai…) is never split: it is faint as a
 *   whole until it settles.
 *
 * Use: fill [target], call [scan], [clear] the schedule, give cells their times, then [frame] with
 * the time since then. It allocates when it is made and scheduled; a frame allocates nothing.
 */
class Decode(cols: Int, rows: Int) {
    /** The text it decodes to. After writing to it, call [scan]. */
    val target = CellBuffer(cols, rows)

    val cols: Int = target.cols
    val rows: Int = target.rows

    /** For each cell of [target]: [KEEP], [NARROW], [WIDE], [JOINED] or [REST]. */
    private val kinds = ByteArray(this.cols * this.rows)

    /** For each cell, when it starts to scramble and when it settles, in ms; [NEVER] without a time. */
    private val starts = IntArray(kinds.size) { NEVER }
    private val settles = IntArray(kinds.size) { NEVER }

    /** For each row, when its last cell settles; [NEVER] when it has nothing to do (any more). */
    private val ends = IntArray(this.rows) { NEVER }

    /** For each row, the ink its scrambling cells flicker in ([Ink.ordinal]), or -1. */
    private val tints = IntArray(this.rows) { -1 }

    /** Changes which characters come by; the same seed gives the same ones. */
    var seed = 0

    /** Looks at [target]'s cells again: which scramble, which stay, which are one word. */
    fun scan() {
        for (row in 0 until rows) {
            var joining = false
            for (col in 0 until cols) {
                val kind = kindOf(target, col, row)
                // Letters of a joining script beside each other are one word.
                kinds[row * cols + col] = if (kind == JOINED && joining) REST else kind
                joining = kind == JOINED
            }
        }
        for (run in target.shaped) {
            if (!target.inside(run.col, run.row)) continue
            val at = run.row * cols + run.col
            kinds[at] = JOINED
            for (c in 1 until run.cells.coerceAtMost(cols - run.col)) kinds[at + c] = REST
        }
    }

    /** No cell has a time, and no row a tint: all of it has settled. */
    fun clear() {
        starts.fill(NEVER)
        settles.fill(NEVER)
        ends.fill(NEVER)
        tints.fill(-1)
    }

    /**
     * The character that starts at [col], [row] scrambles from [at] until [until]. For the second
     * half of a wide character, or a word's later letters, this does nothing: they go with their
     * first cell. A cell that stays as it is only appears at [at], when [frame] hides what hasn't
     * started.
     */
    fun cell(col: Int, row: Int, at: Int, until: Int) {
        if (!target.inside(col, row)) return
        val i = row * cols + col
        if (kinds[i] == REST) return
        starts[i] = at
        settles[i] = maxOf(at, until)
        if (ends[row] == NEVER || settles[i] > ends[row]) ends[row] = settles[i]
    }

    /**
     * Row [row] decodes from [at], as a line does on the website: its characters all scramble, then
     * settle from left to right, each a little early or late. It takes [LINE_MS] at most. [tint]: the
     * ink it flickers in until it settles, or null for its own inks only.
     */
    fun line(row: Int, at: Int, random: Random, tint: Ink? = null) {
        if (row !in 0 until rows) return
        val first = textFrom(row)
        if (first < 0) return
        var last = cols - 1
        while (kinds[row * cols + last] <= KEEP) last--
        val span = last - first + 1
        val sweep = (SWEEP_MIN_MS + span * SWEEP_CELL_MS).coerceAtMost(SWEEP_MAX_MS)
        for (col in first..last) {
            if (kinds[row * cols + col] <= KEEP) continue
            val start = at + random.nextInt(START_JITTER_MS)
            val settled = at + SCRAMBLE_MS + (col - first) * sweep / span + random.nextInt(SETTLE_JITTER_MS)
            cell(col, row, start, settled)
        }
        tints[row] = tint?.ordinal ?: -1
    }

    /** True when row [row] has a character that can scramble. */
    fun hasText(row: Int): Boolean = row in 0 until rows && textFrom(row) >= 0

    /** The first column of row [row] whose character can scramble, or -1. */
    private fun textFrom(row: Int): Int {
        for (col in 0 until cols) if (kinds[row * cols + col] > KEEP) return col
        return -1
    }

    /** Row [row] goes on from where row [otherRow] of [other] (as wide as this one) is: the same times and tint. */
    fun carry(other: Decode, otherRow: Int, row: Int) {
        System.arraycopy(other.starts, otherRow * cols, starts, row * cols, cols)
        System.arraycopy(other.settles, otherRow * cols, settles, row * cols, cols)
        ends[row] = other.ends[otherRow]
        tints[row] = other.tints[otherRow]
    }

    /**
     * Draws the text as it is [t] ms into the schedule, with [target]'s row [first] on [grid]'s top
     * row. Returns true while a cell has yet to settle.
     * - A cell before its time shows its real character, or nothing if [hidden].
     * - A row that has settled isn't looked at again, unless [all] is set: for a grid that doesn't
     *   show the text yet, or that has scrolled.
     */
    fun frame(grid: CellBuffer, t: Int, all: Boolean = false, hidden: Boolean = false, first: Int = 0): Boolean {
        var going = false
        for (row in 0 until rows) {
            val end = ends[row]
            if (end != NEVER && t < end) going = true
            if (!all && end == NEVER) continue
            val y = row - first
            if (y in 0 until grid.rows) drawRow(grid, row, y, t, all, hidden)
            if (!all && t >= end) ends[row] = NEVER
        }
        return going
    }

    private fun drawRow(grid: CellBuffer, row: Int, y: Int, t: Int, all: Boolean, hidden: Boolean) {
        var col = 0
        while (col < cols) {
            val i = row * cols + col
            val cells = cellsAt(i, col)
            val start = starts[i]
            if (start == NEVER || t < start) {
                if (hidden) blank(grid, col, y, cells)
                else if (all) real(grid, col, row, y, cells, 0)
            } else if (t >= settles[i] || kinds[i] == KEEP) {
                real(grid, col, row, y, cells, 0)
            } else if (kinds[i] == JOINED) {
                // A word that joins can't scramble letter by letter: it is faint until it settles.
                real(grid, col, row, y, cells, CellStyle.FAINT)
            } else {
                scrambled(grid, col, row, y, t, i)
            }
            col += cells
        }
    }

    /** How many cells the character (or joined word) at cell [i] takes: the cell, and those that go with it. */
    private fun cellsAt(i: Int, col: Int): Int {
        var n = 1
        while (col + n < cols && kinds[i + n] == REST) n++
        return n
    }

    private fun real(grid: CellBuffer, col: Int, row: Int, y: Int, cells: Int, flags: Int) {
        for (c in col until col + cells) grid.copyCell(target, c, row, c, y, target.styleAt(c, row).with(flags))
    }

    private fun blank(grid: CellBuffer, col: Int, y: Int, cells: Int) {
        for (c in col until col + cells) grid.put(c, y, CellBuffer.EMPTY, CellStyle.PLAIN)
    }

    private fun scrambled(grid: CellBuffer, col: Int, row: Int, y: Int, t: Int, i: Int) {
        // Each cell changes at its own moment, so the grid never blinks as one.
        val step = (t + mix(col, row, 0) % STEP_MS) / STEP_MS
        val pick = mix(col, row, step)
        val glyphs = if (kinds[i] == WIDE) WIDE_GLYPHS else NARROW_GLYPHS
        var style = target.styleAt(col, row)
        // It flickers: the tint comes and goes from one character to the next.
        if (tints[row] >= 0 && (pick ushr 8) % 4 != 0) style = style.ink(INKS[tints[row]])
        grid.put(col, y, glyphs[pick % glyphs.size], style)
    }

    /** A number from the cell, the step and the seed, never negative. */
    private fun mix(col: Int, row: Int, step: Int): Int {
        var h = ((col + 1) * -1640531527) xor ((step + 7) * 40503) xor ((row + 3) * 9973) xor (seed * 31)
        h = (h xor (h ushr 15)) * -2048144789
        return (h xor (h ushr 13)) and 0x7FFFFFFF
    }

    companion object {
        /** A scrambling character changes about this often. */
        const val STEP_MS = 50

        /** All of a line scrambles this long before its first character settles. */
        private const val SCRAMBLE_MS = 120
        private const val START_JITTER_MS = 60
        private const val SETTLE_JITTER_MS = 120

        /** A line settles from its first to its last character in this long: more for a longer line. */
        private const val SWEEP_MIN_MS = 280
        private const val SWEEP_CELL_MS = 9
        private const val SWEEP_MAX_MS = 660

        /** The most a line takes to decode. */
        const val LINE_MS = SCRAMBLE_MS + SWEEP_MAX_MS + SETTLE_JITTER_MS

        private const val NEVER = Int.MIN_VALUE

        // What a cell is. Above KEEP, it scrambles; REST goes with the cell before it.
        private const val REST = (-1).toByte()
        private const val KEEP = 0.toByte()
        private const val NARROW = 1.toByte()
        private const val WIDE = 2.toByte()
        private const val JOINED = 3.toByte()

        private val INKS = Ink.entries.toTypedArray()

        /** Letters, digits, the terminal's signs and half-width katakana: one cell each, as on the website. */
        private val NARROW_GLYPHS: IntArray =
            "abcdefghijklmnopqrstuvwxyz0123456789$=/|{}[]<>*#%+-ｱｳｶｸｻｼﾀﾂﾅﾆﾊﾐﾑﾓﾗﾘﾜ".map { it.code }.toIntArray()

        /** Full-width katakana: two cells each. */
        private val WIDE_GLYPHS: IntArray = "アウカクサシタツナニハミムモラリワ".map { it.code }.toIntArray()

        private fun kindOf(cells: CellBuffer, col: Int, row: Int): Byte {
            val g = cells.glyphAt(col, row)
            return when {
                g == CellBuffer.WIDE_TAIL -> REST
                g == CellBuffer.EMPTY || g == 0x20 || g == 0xA0 -> KEEP
                BoxShapes.isDrawn(g) -> KEEP
                BidiText.joins(if (g > 0) g else cells.clusterOf(g).codePointAt(0)) -> JOINED
                cells.cellsOf(g) == 2 -> WIDE
                else -> NARROW
            }
        }
    }
}
