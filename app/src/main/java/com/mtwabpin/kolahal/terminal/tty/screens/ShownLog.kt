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
    /** Each row exactly where home draws it, whole rows only. */
    fun onGrid(items: List<ShownItem>, height: Int, cellH: Int): List<ShownRow> {
        val rows = ArrayList<ShownRow>()
        for (item in items.asReversed()) for (r in 0 until item.block.rows) {
            val top = item.top + item.gap + r * cellH
            if (top >= 0 && top + cellH <= height) rows += ShownRow(item.block, r, top)
        }
        return rows
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
