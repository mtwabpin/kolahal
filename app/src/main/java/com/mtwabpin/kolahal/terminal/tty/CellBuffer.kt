/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import java.util.BitSet

/**
 * A dense grid of cells for full-screen screens (matrix, clock…), which change single cells every
 * frame. Each cell is two ints, its glyph and its style ([CellStyle]), so a frame allocates nothing.
 *
 * A glyph is:
 * - [EMPTY]: nothing drawn (the background only);
 * - a positive code point;
 * - [WIDE_TAIL]: the right half of a 2-cell cluster that starts in the cell to its left;
 * - a negative id of a cluster of several code points (an emoji with its skin tone, a letter with
 *   its accent). [clusterOf] gives its text. Ids are kept for the buffer's life.
 *
 * Writes outside the grid are ignored, so a screen can draw something that is partly off the edge.
 * [dirty] has a bit for each row changed since the painter last cleared it.
 */
class CellBuffer(cols: Int, rows: Int) {
    var cols: Int = cols.coerceAtLeast(1)
        private set
    var rows: Int = rows.coerceAtLeast(1)
        private set

    private var glyphs = IntArray(this.cols * this.rows)
    private var styles = IntArray(this.cols * this.rows) { CellStyle.PLAIN.bits }

    /** Rows changed since the last paint. The painter clears it. */
    val dirty = BitSet()

    /**
     * Words of a script whose letters join (Arabic, Hebrew, the Indic scripts…), sorted by row and
     * column. Their cells hold the letters one by one, in the order they show, for tests and
     * search; the painter draws each word in one piece over them instead ([ShapedRun]).
     */
    var shaped: List<ShapedRun> = emptyList()
        internal set

    private val clusters = ArrayList<String>()
    private val clusterIds = HashMap<String, Int>()

    init {
        dirty.set(0, this.rows)
    }

    /** Makes the grid [cols] × [rows], blank. A screen redraws after this. */
    fun resize(cols: Int, rows: Int) {
        val c = cols.coerceAtLeast(1)
        val r = rows.coerceAtLeast(1)
        if (c * r != glyphs.size) {
            glyphs = IntArray(c * r)
            styles = IntArray(c * r)
        }
        this.cols = c
        this.rows = r
        glyphs.fill(EMPTY)
        styles.fill(CellStyle.PLAIN.bits)
        dirty.clear()
        dirty.set(0, r)
    }

    fun inside(col: Int, row: Int) = col in 0 until cols && row in 0 until rows

    /** The glyph at [col], [row]: [EMPTY], a code point, [WIDE_TAIL] or a cluster id. */
    fun glyphAt(col: Int, row: Int): Int = if (inside(col, row)) glyphs[row * cols + col] else EMPTY

    fun styleAt(col: Int, row: Int): CellStyle =
        CellStyle(if (inside(col, row)) styles[row * cols + col] else CellStyle.PLAIN.bits)

    /** The text of a cluster id (a glyph below [WIDE_TAIL]). */
    fun clusterOf(glyph: Int): String = clusters[-glyph - 2]

    /** The cells a glyph takes: 2 for a wide one, else 1. */
    fun cellsOf(glyph: Int): Int = when {
        glyph == EMPTY || glyph == WIDE_TAIL -> 1
        glyph > 0 -> if (CellWidth.ofCodePoint(glyph) == 2) 2 else 1
        else -> if (CellWidth.of(clusterOf(glyph)) == 2) 2 else 1
    }

    /**
     * Puts the code point [cp] at [col], [row]. A wide one takes the cell to its right too.
     * [EMPTY] blanks the cell; a zero-width code point changes nothing.
     */
    fun put(col: Int, row: Int, cp: Int, style: CellStyle) {
        if (!inside(col, row)) return
        if (cp == EMPTY) return set(col, row, EMPTY, style)
        val width = CellWidth.ofCodePoint(cp)
        if (width == 0) return
        if (width == 2) putWide(col, row, cp, style) else set(col, row, cp, style)
    }

    /**
     * Writes [text] (from [start] to [end]) from [col] on row [row], one cluster per cell (two for
     * a wide one), and returns the column after it. Zero-width clusters are dropped. What falls off
     * the right edge is cut; a wide cluster that would be cut in half becomes a blank cell.
     * Allocates nothing, except the first time a cluster of several code points is seen.
     */
    fun put(col: Int, row: Int, text: CharSequence, style: CellStyle, start: Int = 0, end: Int = text.length): Int {
        var c = col
        var i = start
        while (i < end) {
            val next = CellWidth.clusterEnd(text, i).coerceAtMost(end)
            val width = CellWidth.width(text, i, next)
            if (width > 0) {
                if (row in 0 until rows && c + width > 0 && c < cols) {
                    val cp = Character.codePointAt(text, i)
                    val glyph = if (next - i == Character.charCount(cp)) cp else idOf(text, i, next)
                    if (width == 2) putWide(c, row, glyph, style) else set(c, row, glyph, style)
                }
                c += width
            }
            i = next
        }
        return c
    }

    /** Fills [cols] × [rows] cells from [col], [row] with [cp] (a 1-cell code point, or [EMPTY]). */
    fun fill(col: Int, row: Int, cols: Int, rows: Int, cp: Int, style: CellStyle) {
        val c0 = col.coerceAtLeast(0)
        val c1 = (col + cols).coerceAtMost(this.cols)
        val r0 = row.coerceAtLeast(0)
        val r1 = (row + rows).coerceAtMost(this.rows)
        for (r in r0 until r1) for (c in c0 until c1) set(c, r, cp, style)
    }

    fun fill(rect: CellRect, cp: Int, style: CellStyle) = fill(rect.col, rect.row, rect.cols, rect.rows, cp, style)

    /** Blanks every cell, in [style] (its background shows). */
    fun clear(style: CellStyle = CellStyle.PLAIN) = fill(0, 0, cols, rows, EMPTY, style)

    /** Moves every row up by [n]; the rows that come in at the bottom are blank in [style]. */
    fun scrollUp(n: Int, style: CellStyle = CellStyle.PLAIN) {
        if (n <= 0) return
        if (n >= rows) return clear(style)
        System.arraycopy(glyphs, n * cols, glyphs, 0, (rows - n) * cols)
        System.arraycopy(styles, n * cols, styles, 0, (rows - n) * cols)
        glyphs.fill(EMPTY, (rows - n) * cols, rows * cols)
        styles.fill(style.bits, (rows - n) * cols, rows * cols)
        dirty.set(0, rows)
    }

    /** Row [row] as text: blank cells as spaces, a wide cluster once. For tests and copy. */
    fun rowText(row: Int): String = buildString {
        for (c in 0 until cols) {
            when (val g = glyphAt(c, row)) {
                EMPTY -> append(' ')
                WIDE_TAIL -> Unit
                in 1..Int.MAX_VALUE -> appendCodePoint(g)
                else -> append(clusterOf(g))
            }
        }
    }

    /** Every row as text, one per line, trailing spaces kept. For tests. */
    fun dump(): String = (0 until rows).joinToString("\n") { rowText(it) }

    /** Sets one cell, and blanks the other half of any wide cluster it breaks. */
    private fun set(col: Int, row: Int, glyph: Int, style: CellStyle) {
        if (!inside(col, row)) return
        val at = row * cols + col
        val old = glyphs[at]
        // The same glyph again (a screen that redraws what is there) breaks nothing.
        if (old == glyph) return write(at, row, glyph, style.bits)
        if (old == WIDE_TAIL && col > 0) write(at - 1, row, EMPTY, styles[at - 1])
        else if (old != EMPTY && old != WIDE_TAIL && col + 1 < cols && glyphs[at + 1] == WIDE_TAIL) {
            write(at + 1, row, EMPTY, styles[at + 1])
        }
        write(at, row, glyph, style.bits)
    }

    /** A 2-cell cluster at [col]: its tail goes in the next cell. At the right edge it can't fit, so the cell is blank. */
    private fun putWide(col: Int, row: Int, glyph: Int, style: CellStyle) {
        if (col + 1 >= cols) {
            set(col, row, EMPTY, style)
            return
        }
        if (col < 0) {
            // Only its right half is on the grid.
            set(col + 1, row, EMPTY, style)
            return
        }
        set(col, row, glyph, style)
        val tail = row * cols + col + 1
        val after = glyphs[tail]
        if (after != EMPTY && after != WIDE_TAIL && col + 2 < cols && glyphs[tail + 1] == WIDE_TAIL) {
            write(tail + 1, row, EMPTY, styles[tail + 1])
        }
        write(tail, row, WIDE_TAIL, style.bits)
    }

    private fun write(at: Int, row: Int, glyph: Int, style: Int) {
        if (glyphs[at] == glyph && styles[at] == style) return
        glyphs[at] = glyph
        styles[at] = style
        dirty.set(row)
    }

    private fun idOf(text: CharSequence, start: Int, end: Int): Int {
        val cluster = text.subSequence(start, end).toString()
        clusterIds[cluster]?.let { return it }
        clusters += cluster
        val id = -(clusters.size - 1) - 2
        clusterIds[cluster] = id
        return id
    }

    companion object {
        const val EMPTY = 0
        const val WIDE_TAIL = -1
    }
}

/**
 * A word drawn in one piece over [cells] cells from [col] on [row]: [text] in the order it is
 * written (not the order it shows), and its direction ([rtl]). The painter shapes it as a whole
 * and fits it to its cells, so its letters join as they should and the grid still holds.
 */
class ShapedRun(val row: Int, val col: Int, val cells: Int, val text: String, val rtl: Boolean) {
    override fun toString() = "$text at $col,$row ($cells cells${if (rtl) ", right to left" else ""})"
}
