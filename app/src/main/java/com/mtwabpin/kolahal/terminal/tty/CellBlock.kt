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
 * row is not folded. [foldedApps] is how many apps a capped apps grid hides behind its "+N more"
 * row; 0 when it is open or has no cap.
 */
class CellBlock(val grid: CellBuffer, val targets: List<CellTarget>, val foldedRows: Int = 0, val foldedApps: Int = 0) {
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
 *
 * Columns count from the frame's start: the left edge, or the right edge in a right-to-left
 * frame. Layout never needs to know which: [build] with `mirrored` turns the columns around.
 * - A *unit* ([newUnit]) is text that keeps its order when the frame turns: a row of a paragraph,
 *   whose letters the bidi algorithm has already put in the order they show ([wrap]). It moves to
 *   the other side as a whole.
 * - Anything else written on its own moves to the other side; a run of box drawing and blocks is
 *   also turned around, cell by cell, each shape mirrored (`┌` becomes `┐`), so a table's first
 *   column is on the right and a bar fills from the right.
 */
internal class BlockWriter(val cols: Int) {
    private class Put(
        val col: Int,
        val row: Int,
        val text: String,
        val style: CellStyle,
        val unit: Int,
        val shaped: Shaped?,
    )

    /** A word of a joining script: its text as written, and its direction. Its cells hold it as it shows. */
    class Shaped(val text: String, val rtl: Boolean)

    private class Mark(val rect: CellRect, val unit: Int)

    private val puts = ArrayList<Put>()
    private val targets = LinkedHashMap<Target, ArrayList<Mark>>()

    /** For each unit, the columns it is known to take besides its writes ([span]): from, to. */
    private val spans = HashMap<Int, IntArray>()
    private var units = 0

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

    /** A new unit: writes with it keep their order when the frame turns. */
    fun newUnit(): Int = units++

    /** [unit] takes the columns from [from] to [to] (exclusive), written or not: a text field's whole width. */
    fun span(unit: Int, from: Int, to: Int) {
        val s = spans.getOrPut(unit) { intArrayOf(from, to) }
        s[0] = minOf(s[0], from)
        s[1] = maxOf(s[1], to)
    }

    /**
     * Writes [text] from [col] on [row]; returns the column after it. Cells past the right edge are
     * cut. [unit]: the unit it belongs to, or [NO_UNIT]. [shaped]: it is one word of a joining script.
     */
    fun put(col: Int, row: Int, text: String, style: CellStyle, unit: Int = NO_UNIT, shaped: Shaped? = null): Int {
        val after = col + CellWidth.total(text)
        reach(row)
        if (row >= keepRows || text.isEmpty()) return after
        puts += Put(col, row, text, style, unit, shaped)
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
    fun mark(target: Target, col: Int, row: Int, cols: Int, rows: Int = 1, unit: Int = NO_UNIT) {
        if (cols <= 0 || row >= keepRows) return
        val rects = targets.getOrPut(target) { ArrayList() }
        val last = rects.lastOrNull()
        val r = last?.rect
        if (r != null && last.unit == unit && rows == 1 && r.rows == 1 && r.row == row && r.col + r.cols == col) {
            rects[rects.lastIndex] = Mark(CellRect(r.col, row, r.cols + cols, 1), unit)
        } else rects += Mark(CellRect(col, row, cols, rows), unit)
    }

    /** Drops everything from [row] on: the end of a folded line. */
    fun cut(row: Int) {
        puts.removeAll { it.row >= row }
        val iterator = targets.entries.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.value.removeAll { it.rect.row >= row }
            if (e.value.isEmpty()) iterator.remove()
        }
        if (rows > row) rows = row
        for (r in row until ends.size) ends[r] = 0
    }

    /**
     * The block, with [flags] added to every cell's style (the faint of an old line). [mirrored]:
     * the frame runs right to left, so every column is counted from the right edge instead.
     */
    fun build(flags: Int = 0, foldedRows: Int = 0, mirrored: Boolean = false, foldedApps: Int = 0): CellBlock {
        val grid = CellBuffer(cols, rows.coerceAtLeast(1))
        val shaped = ArrayList<ShapedRun>()
        val from = IntArray(units) { Int.MAX_VALUE }
        val to = IntArray(units) { Int.MIN_VALUE }
        if (mirrored) {
            for (p in puts) if (p.unit >= 0) {
                from[p.unit] = minOf(from[p.unit], p.col)
                to[p.unit] = maxOf(to[p.unit], p.col + CellWidth.total(p.text))
            }
            for ((u, s) in spans) if (u in 0 until units) {
                from[u] = minOf(from[u], s[0])
                to[u] = maxOf(to[u], s[1])
            }
        }
        builtMirrored = mirrored
        unitFrom = from
        unitTo = to
        for (p in puts) {
            val style = if (flags == 0) p.style else p.style.with(flags)
            val width = CellWidth.total(p.text)
            if (mirrored && p.unit < 0 && isShapes(p.text)) {
                turnShapes(grid, p.col, p.row, p.text, style)
                continue
            }
            val col = place(p.col, width, p.unit)
            grid.put(col, p.row, p.text, style)
            p.shaped?.let { shaped += ShapedRun(p.row, col, width, it.text, it.rtl) }
        }
        grid.dirty.clear()
        if (shaped.isNotEmpty()) grid.shaped = shaped.sortedWith(compareBy({ it.row }, { it.col }))
        val built = targets.map { (t, marks) ->
            CellTarget(t, marks.map { m -> m.rect.copy(col = place(m.rect.col, m.rect.cols, m.unit)) })
        }
        return CellBlock(grid, built, foldedRows, foldedApps)
    }

    private var builtMirrored = false
    private var unitFrom = IntArray(0)
    private var unitTo = IntArray(0)

    /**
     * Where a write of [width] cells at [col] (of [unit], or none) went in the grid [build] made
     * last: the same column, or counted from the right in a mirrored frame.
     */
    fun place(col: Int, width: Int, unit: Int = NO_UNIT): Int = when {
        !builtMirrored -> col
        unit >= 0 && unit < unitFrom.size && unitFrom[unit] != Int.MAX_VALUE -> cols - unitTo[unit] + (col - unitFrom[unit])
        else -> cols - col - width
    }

    /**
     * Writes all of this into [other], moved by [dc] columns and [dr] rows: the same writes, units,
     * words and targets, so they turn with [other]'s frame as they would have with this one's.
     */
    fun copyInto(other: BlockWriter, dc: Int, dr: Int) {
        val base = other.units
        other.units += units
        fun moved(unit: Int) = if (unit >= 0) unit + base else NO_UNIT
        for (p in puts) other.put(p.col + dc, p.row + dr, p.text, p.style, moved(p.unit), p.shaped)
        for ((u, s) in spans) other.span(u + base, s[0] + dc, s[1] + dc)
        for ((t, marks) in targets) for (m in marks) {
            other.mark(t, m.rect.col + dc, m.rect.row + dr, m.rect.cols, m.rect.rows, moved(m.unit))
        }
        other.reach(rows - 1 + dr)
    }

    /**
     * Writes [text], box drawing, blocks and spaces from [col] (counted from the right), turned
     * around for a right-to-left frame: the last cell first, each shape mirrored.
     */
    private fun turnShapes(grid: CellBuffer, col: Int, row: Int, text: String, style: CellStyle) {
        var c = col
        var i = 0
        while (i < text.length) {
            val e = CellWidth.clusterEnd(text, i)
            val w = CellWidth.width(text, i, e)
            val cp = Character.codePointAt(text, i)
            val m = BoxShapes.mirror(cp)
            val x = cols - c - w
            when {
                m == 0 -> grid.put(x, row, cp, style.with(CellStyle.MIRROR))
                m == cp -> grid.put(x, row, text, style, i, e)
                else -> grid.put(x, row, m, style)
            }
            c += w
            i = e
        }
    }

    companion object {
        /** A write that belongs to no unit. */
        const val NO_UNIT = -1

        /** True when [text] is only box drawing, blocks and spaces. */
        private fun isShapes(text: String): Boolean {
            if (text.isEmpty()) return false
            var shape = false
            for (ch in text) {
                if (ch == ' ') continue
                if (!BoxShapes.isDrawn(ch.code)) return false
                shape = true
            }
            return shape
        }
    }
}
