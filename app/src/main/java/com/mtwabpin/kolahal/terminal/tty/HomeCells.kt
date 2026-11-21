/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink
import kotlin.math.roundToInt

/** Something on one of home's parts that takes a tap: [id] names it (each layout says what its ids are), [rect] is where it is. */
data class Spot(val id: Int, val rect: CellRect) {
    fun contains(col: Int, row: Int) =
        col >= rect.col && col < rect.col + rect.cols && row >= rect.row && row < rect.row + rect.rows
}

/**
 * One of home's parts on the grid: the header, the pinned line, the prompt, a suggestion, a row of
 * keys, a band, a sheet's row. Its [grid] of cells, and the [spots] a tap can hit. It never changes
 * once built, so it is laid out once for what it shows and painted as it is on every frame.
 */
class HomeCells(val grid: CellBuffer, val spots: List<Spot> = emptyList()) {
    val cols: Int get() = grid.cols
    val rows: Int get() = grid.rows

    /** The spot named [id], or null when it isn't shown. */
    fun spot(id: Int): Spot? = spots.firstOrNull { it.id == id }

    /** The spot on the cell [col], [row], or null. */
    fun spotAt(col: Int, row: Int): Spot? = spots.firstOrNull { it.contains(col, row) }

    /** Row [row] as text, trailing spaces cut. */
    fun text(row: Int = 0): String = grid.rowText(row).trimEnd()

    /** The rows as text, one per line. For tests. */
    fun dump(): String = (0 until rows).joinToString("\n") { text(it) }

    override fun toString() = dump()
}

/**
 * Writes one of home's parts: the text through a [BlockWriter] (so it wraps and cuts as the log
 * does), and the spots beside it. [rows] is the least height; writing further down adds rows.
 */
internal class HomeWriter(cols: Int, rows: Int = 1) {
    val w = BlockWriter(cols.coerceAtLeast(1)).also { it.reach(rows.coerceAtLeast(1) - 1) }
    val cols: Int get() = w.cols
    private val spots = ArrayList<Spot>()

    /** Writes [text] from [col] on [row]; returns the column after it. */
    fun put(col: Int, row: Int, text: String, style: CellStyle): Int = w.put(col, row, text, style)

    /** Writes [segs] from [col] on [row], cut with "…" at [right]. */
    fun line(segs: List<Seg>, row: Int, col: Int, right: Int = cols): Int = w.line(segs, row, col, right)

    /** Writes [text] in [style] from [col] on [row], cut with "…" at [right]. */
    fun line(text: String, style: CellStyle, row: Int, col: Int, right: Int = cols): Int =
        w.line(listOf(Seg(text, style)), row, col, right)

    /** Writes [segs] as a paragraph wrapped between [first] (its first row) or [rest] and [right]; returns the row after it. */
    fun wrap(segs: List<Seg>, row: Int, first: Int, rest: Int, right: Int = cols): Int = w.wrap(segs, row, first, rest, right)

    /** [n] blank cells from [col] on [row] in [style]: a background ([style]'s bg, or its ink when inverse). */
    fun blank(col: Int, row: Int, n: Int, style: CellStyle) {
        if (n > 0) w.repeat(col, row, ' ', n, style)
    }

    /** Copies [from]'s cells (and its spots, moved with them) to [col], [row] on. Blank cells with no background stay as they are. */
    fun copy(from: HomeCells, col: Int, row: Int) {
        val g = from.grid
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val glyph = g.glyphAt(c, r)
            val style = g.styleAt(c, r)
            when {
                glyph == CellBuffer.WIDE_TAIL -> Unit
                glyph == CellBuffer.EMPTY -> if (style != CellStyle.PLAIN) put(col + c, row + r, " ", style)
                glyph > 0 -> put(col + c, row + r, String(Character.toChars(glyph)), style)
                else -> put(col + c, row + r, g.clusterOf(glyph), style)
            }
        }
        for (s in from.spots) spot(s.id, s.rect.col + col, s.rect.row + row, s.rect.cols, s.rect.rows)
    }

    /** A spot named [id] over [cols] × [rows] cells from [col], [row]. */
    fun spot(id: Int, col: Int, row: Int, cols: Int, rows: Int = 1) {
        val c = col.coerceAtLeast(0)
        val n = (col + cols).coerceAtMost(this.cols) - c
        if (n > 0 && rows > 0) spots += Spot(id, CellRect(c, row, n, rows))
    }

    fun build(): HomeCells = HomeCells(w.build().grid, spots.toList())
}

/** The left eighths of a cell: none, ▏ (one eighth) to ▉ (seven), █. */
private const val LEFT_EIGHTHS = " ▏▎▍▌▋▊▉█"

/** [cells] cells filled to [fraction] (0 to 1) in eighths of a cell, from [col] on [row], in [fill]. */
internal fun HomeWriter.eighths(col: Int, row: Int, cells: Int, fraction: Float, fill: CellStyle) {
    if (cells <= 0) return
    val total = (fraction.coerceIn(0f, 1f) * cells * 8).roundToInt()
    val text = StringBuilder(cells)
    for (c in 0 until cells) text.append(LEFT_EIGHTHS[(total - c * 8).coerceIn(0, 8)])
    put(col, row, text.toString(), fill)
}

/**
 * A thin progress line of [cells] cells from [col] on [row]: `━` in [fill] up to [fraction], `─`
 * muted after it. Whole cells, so it lines up with the text above it.
 */
internal fun HomeWriter.lineBar(col: Int, row: Int, cells: Int, fraction: Float, fill: CellStyle) {
    if (cells <= 0) return
    val done = (fraction.coerceIn(0f, 1f) * cells).roundToInt()
    if (done > 0) w.repeat(col, row, '━', done, fill)
    if (cells - done > 0) w.repeat(col + done, row, '─', cells - done, CellStyle.of(Ink.DIM, flags = CellStyle.FAINT))
}

/**
 * A progress bar as thin as an eighth of a cell, along the top of [cells] cells from [col] on
 * [row]: `▔` in [fill] up to [fraction], muted after it. Only the top of the row is drawn, so the
 * row can sit right under the text it belongs to and take little more room than the GUI's bar.
 */
internal fun HomeWriter.thinBar(col: Int, row: Int, cells: Int, fraction: Float, fill: CellStyle) {
    if (cells <= 0) return
    val done = (fraction.coerceIn(0f, 1f) * cells).roundToInt()
    if (done > 0) w.repeat(col, row, '▔', done, fill)
    if (cells - done > 0) w.repeat(col + done, row, '▔', cells - done, CellStyle.of(Ink.DIM, flags = CellStyle.FAINT))
}

/** How many cells of [cells] a progress line fills at [fraction]: it is drawn again only when this changes. */
fun filledCells(fraction: Float, cells: Int): Int = (fraction.coerceIn(0f, 1f) * cells).roundToInt()

/** [text] cut to [width] cells with "…" when longer. */
internal fun cut(text: String, width: Int): String {
    if (width <= 0) return ""
    if (CellWidth.total(text) <= width) return text
    val out = StringBuilder()
    var used = 0
    var i = 0
    while (i < text.length) {
        val e = CellWidth.clusterEnd(text, i)
        val w = CellWidth.width(text, i, e)
        if (used + w > width - 1) break
        out.append(text, i, e)
        used += w
        i = e
    }
    return out.append('…').toString()
}

/** The same text with its line breaks and tabs as spaces: a home part's row is one line. */
internal fun oneLine(text: String): String =
    if (text.none { it == '\n' || it == '\t' || it == '\r' }) text else text.replace(Regex("[\r\n\t]+"), " ")
