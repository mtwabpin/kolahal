/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Target
import kotlin.math.max

/** Something on a block that can be tapped, and the cells it covers: one rectangle, or one per row of a link that wraps. */
class CellTarget(val target: Target, val rects: List<CellRect>) {
    fun contains(col: Int, row: Int): Boolean =
        rects.any { col >= it.col && col < it.col + it.cols && row >= it.row && row < it.row + it.rows }

    override fun toString() = "$target at $rects"
}

/**
 * One log row laid out on the grid: its cells ([grid], as many rows as it needs) and what can be
 * tapped where ([targets]). A tap anywhere else is a tap on the line ([Target.Line]).
 *
 * It never changes once built, so the log keeps it and paints it as it is on every frame.
 * [foldedRows] is how many rows a folded line hides behind its "▾ N more lines" row; 0 when the
 * row is not folded.
 */
class CellBlock(val grid: CellBuffer, val targets: List<CellTarget>, val foldedRows: Int = 0) {
    val cols: Int get() = grid.cols
    val rows: Int get() = grid.rows

    /** The target in the cell [col], [row], or null for the line itself. */
    fun targetAt(col: Int, row: Int): CellTarget? = targets.firstOrNull { it.contains(col, row) }

    /** What a tap on the cell [col], [row] does: its target, else the line. */
    fun hitAt(col: Int, row: Int): Target = targetAt(col, row)?.target ?: Target.Line

    /**
     * What a tap at [x], [y] (pixels from the block's first cell) does, on cells [cellW] × [cellH]
     * pixels. Cells are small, so a target within [slop] pixels of the finger counts: the one under
     * the finger first, else the nearest. Elsewhere the tap is the line's.
     */
    fun hit(x: Float, y: Float, cellW: Int, cellH: Int, slop: Float): Target {
        var best: Target = Target.Line
        var bestDistance = slop
        for (t in targets) for (r in t.rects) {
            val left = (r.col * cellW).toFloat()
            val top = (r.row * cellH).toFloat()
            val right = ((r.col + r.cols) * cellW).toFloat()
            val bottom = ((r.row + r.rows) * cellH).toFloat()
            val dx = max(max(left - x, x - right), 0f)
            val dy = max(max(top - y, y - bottom), 0f)
            if (dx == 0f && dy == 0f) return t.target
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d <= bestDistance) {
                best = t.target
                bestDistance = d
            }
        }
        return best
    }

    /** The rows as text, trailing spaces cut, one per line. For tests. */
    fun dump(): String = (0 until rows).joinToString("\n") { grid.rowText(it).trimEnd() }

    override fun toString() = dump()
}

/**
 * Builds a [CellBlock] of [cols] columns, growing down as rows are written. Writes are kept and
 * played into the block's grid at the end, once its height is known.
 * - [keepRows]: rows from this one on are counted but not kept (a folded line's hidden end).
 * - [limit] is where text on a row must stop: the right edge, or short of the time on the row
 *   that shows it ([reserve]).
 */
internal class BlockWriter(val cols: Int) {
    private class Put(val col: Int, val row: Int, val text: String, val style: CellStyle)

    private val puts = ArrayList<Put>()
    private val targets = LinkedHashMap<Target, ArrayList<CellRect>>()

    /** The rows written so far, or reached with [reach]. */
    var rows = 0
        private set

    /** Rows from here on are not kept. */
    var keepRows = Int.MAX_VALUE

    /** The row whose end is taken (by the time), and from which column; none when [reservedRow] is -1. */
    private var reservedRow = -1
    private var reservedFrom = cols

    /** For each row, the column after the last cell written on it. */
    private var ends = IntArray(8)

    /** Text on [row] stops before [from] (one cell short of the time). */
    fun reserve(row: Int, from: Int) {
        reservedRow = row
        reservedFrom = from
    }

    /** The column where text on [row] must stop, at most [right]. */
    fun limit(row: Int, right: Int = cols): Int = if (row == reservedRow) minOf(right, reservedFrom) else right

    /** The rows now take at least [row] + 1. */
    fun reach(row: Int) {
        if (row + 1 > rows) rows = row + 1
    }

    /** Where text on [row] ends: the column after its last written cell, 0 if none. */
    fun end(row: Int): Int = if (row < ends.size) ends[row] else 0

    /** Writes [text] from [col] on [row]; returns the column after it. Cells past the right edge are cut. */
    fun put(col: Int, row: Int, text: String, style: CellStyle): Int {
        val after = col + CellWidth.total(text)
        reach(row)
        if (row >= keepRows || text.isEmpty()) return after
        puts += Put(col, row, text, style)
        if (row >= ends.size) ends = ends.copyOf(maxOf(row + 1, ends.size * 2))
        if (after > ends[row]) ends[row] = minOf(after, cols)
        return after
    }

    /** Writes [n] copies of the one-cell character [ch] from [col] on [row]. */
    fun repeat(col: Int, row: Int, ch: Char, n: Int, style: CellStyle): Int {
        if (n <= 0) return col
        return put(col, row, ch.toString().repeat(n), style)
    }

    /** [target] covers [cols] cells from [col] on [row]. Pieces of one target on one row join into one rectangle. */
    fun mark(target: Target, col: Int, row: Int, cols: Int, rows: Int = 1) {
        if (cols <= 0 || row >= keepRows) return
        val rects = targets.getOrPut(target) { ArrayList() }
        val last = rects.lastOrNull()
        if (last != null && rows == 1 && last.rows == 1 && last.row == row && last.col + last.cols == col) {
            rects[rects.lastIndex] = CellRect(last.col, row, last.cols + cols, 1)
        } else rects += CellRect(col, row, cols, rows)
    }

    /** Drops everything from [row] on: the end of a folded line. */
    fun cut(row: Int) {
        puts.removeAll { it.row >= row }
        val iterator = targets.entries.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.value.removeAll { it.row >= row }
            if (e.value.isEmpty()) iterator.remove()
        }
        if (rows > row) rows = row
        for (r in row until ends.size) ends[r] = 0
    }

    /** The block, with [flags] added to every cell's style (the faint of an old line). */
    fun build(flags: Int = 0, foldedRows: Int = 0): CellBlock {
        val grid = CellBuffer(cols, rows.coerceAtLeast(1))
        for (p in puts) grid.put(p.col, p.row, p.text, if (flags == 0) p.style else p.style.with(flags))
        grid.dirty.clear()
        return CellBlock(grid, targets.map { (t, r) -> CellTarget(t, r.toList()) }, foldedRows)
    }
}
