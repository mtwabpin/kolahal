/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.tty.CellBlock

/** Row [row] of a line's cells ([block]), with its top [top] px below the log's top. */
class ShownRow(val block: CellBlock, val row: Int, val top: Int)

/**
 * The part of home's log that is on screen, as cells, for the screen that takes it over (`decode`).
 * Its first column starts at [left] px and the log's top is at [top] px: in the window when home
 * makes it, in the screen's area when a screen reads it ([ScreenEnv.log]). [rows] go from the top
 * down; only rows that home shows whole are there.
 */
class ShownLog(val left: Int, val top: Int, val rows: List<ShownRow>) {
    /** The same rows, with [dx], [dy] px taken off. */
    fun minus(dx: Int, dy: Int) = ShownLog(left - dx, top - dy, rows)
}

/**
 * A line of home's log as its list has it on screen: laid out on the grid as [block], in an item
 * [height] px tall whose top is [top] px below the log's, with [gap] px of it above the line.
 */
class ShownItem(val block: CellBlock, val top: Int, val height: Int, val gap: Int)

/** Which rows of cells show, and where, for a list's items, newest (lowest) first. Rows are [cellH] px tall, the log [height]. */
object ShownRows {
    /** Items drawn on the grid (`home.style tty`): each row exactly where home draws it, whole rows only. */
    fun onGrid(items: List<ShownItem>, height: Int, cellH: Int): List<ShownRow> {
        val rows = ArrayList<ShownRow>()
        for (item in items.asReversed()) for (r in 0 until item.block.rows) {
            val top = item.top + item.gap + r * cellH
            if (top >= 0 && top + cellH <= height) rows += ShownRow(item.block, r, top)
        }
        return rows
    }

    /**
     * Items drawn as text (`home.style gui`), laid out on the grid from the bottom up with the same
     * gaps. A line takes other rows there than here, so of a line that is only partly on screen as
     * large a part is taken; what doesn't fit at the top is left out.
     */
    fun stacked(items: List<ShownItem>, height: Int, cellH: Int): List<ShownRow> {
        val rows = ArrayList<ShownRow>()
        var bottom = minOf(height, items.firstOrNull()?.let { it.top + it.height } ?: height)
        for (item in items) {
            val n = item.block.rows
            val lineTop = item.top + item.gap
            val lineHeight = (item.height - item.gap).coerceAtLeast(1)
            val shownFrom = (maxOf(lineTop, 0) - lineTop).coerceIn(0, lineHeight)
            val shownTo = (minOf(lineTop + lineHeight, height) - lineTop).coerceIn(0, lineHeight)
            // Rounded inwards, so nothing shows that home itself doesn't.
            val from = (shownFrom.toLong() * n + lineHeight - 1) / lineHeight
            val to = shownTo.toLong() * n / lineHeight
            for (r in to.toInt() - 1 downTo from.toInt()) {
                bottom -= cellH
                if (bottom < 0) return rows.asReversed()
                rows += ShownRow(item.block, r, bottom)
            }
            bottom -= item.gap
        }
        return rows.asReversed()
    }
}

/**
 * Where a screen over home gets the log that shows there ([ShownLog]). Home's log sets [reader]
 * while it is on screen; the screen reads it with its cell's size in px. Main thread only.
 */
class ShownLogSource {
    var reader: ((cellW: Int, cellH: Int) -> ShownLog?)? = null

    /** What home's log shows now, or null when it isn't on screen (yet). */
    fun read(cellW: Int, cellH: Int): ShownLog? = reader?.invoke(cellW, cellH)
}
