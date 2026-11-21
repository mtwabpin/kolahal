/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.Target
import com.mtwabpin.kolahal.terminal.markRanges

/** A piece of text in one look, and the link it is part of, if any. */
internal class Seg(val text: String, val style: CellStyle, val link: Target.Link? = null) {
    override fun toString() = "$text ($style)"
}

/** A search's match on the grid: inverse, in the warning color. */
internal val MARK = CellStyle.of(Ink.WARN, flags = CellStyle.INVERSE)

/**
 * Writes [segs] as one paragraph from [row]: its first row from column [first], the rows it wraps
 * onto from [rest], each up to [right] (exclusive), or less on the row the time takes
 * ([BlockWriter.limit]). Returns the row after its last one.
 * - It breaks at spaces. A word longer than a row breaks between two characters; a wide character
 *   (2 cells) never splits, it goes on the next row whole.
 * - A line break starts a row at [rest]. Spaces at the start of the paragraph and after a line
 *   break are kept (aligned output); spaces where a row wraps are dropped.
 * - A tab goes on to the next multiple of 8 columns.
 * - [marks]: a search's words, marked wherever they show ([MARK]).
 * - Each link's cells are marked on the writer with its target, one rectangle per row.
 */
internal fun BlockWriter.wrap(
    segs: List<Seg>,
    row: Int,
    first: Int,
    rest: Int,
    right: Int,
    marks: List<String> = emptyList(),
): Int {
    reach(row)
    if (segs.isEmpty()) return row + 1
    val pen = Pen(this, segs, marks, right)
    pen.row = row
    pen.col = first
    pen.rowStart = first
    val text = pen.text
    val n = text.length
    // No word yet on this row; a row that began by wrapping skips its leading spaces.
    var fresh = true
    var wrapped = false
    fun newRow(soft: Boolean) {
        pen.newRow(rest)
        fresh = true
        wrapped = soft
    }

    var i = 0
    while (i < n) {
        when (text[i]) {
            '\n' -> {
                newRow(false)
                i++
            }

            ' ' -> {
                when {
                    wrapped && fresh -> Unit
                    pen.col + 1 > pen.limit() -> newRow(true)
                    else -> pen.cluster(i, i + 1, 1)
                }
                i++
            }

            '\t' -> {
                val to = (pen.col / TAB + 1) * TAB
                if (to > pen.limit()) newRow(true) else pen.skipTo(to)
                i++
            }

            else -> {
                // One word: up to the next space or line break.
                var j = i
                var width = 0
                while (j < n && text[j] != ' ' && text[j] != '\n' && text[j] != '\t') {
                    val e = CellWidth.clusterEnd(text, j)
                    width += CellWidth.width(text, j, e)
                    j = e
                }
                if (!fresh && pen.col + width > pen.limit()) newRow(true)
                var k = i
                while (k < j) {
                    val e = CellWidth.clusterEnd(text, k)
                    val cells = CellWidth.width(text, k, e)
                    if (cells > 0 && pen.col + cells > pen.limit() && pen.col > pen.rowStart) newRow(true)
                    pen.cluster(k, e, cells)
                    k = e
                }
                fresh = false
                i = j
            }
        }
    }
    pen.flush()
    return pen.row + 1
}

/**
 * Writes [segs] on [row] from [col], cut at [right] (exclusive) with "…" when they don't fit.
 * Line breaks show as spaces. Returns the column after the last cell written.
 */
internal fun BlockWriter.line(
    segs: List<Seg>,
    row: Int,
    col: Int,
    right: Int,
    marks: List<String> = emptyList(),
): Int {
    reach(row)
    if (segs.isEmpty()) return col
    val pen = Pen(this, segs, marks, right)
    pen.row = row
    pen.col = col
    pen.rowStart = col
    val text = pen.text
    val limit = pen.limit()
    val fits = col + shownWidth(text) <= limit
    // Cut short, it keeps a cell for "…".
    val stop = if (fits) limit else limit - 1
    var i = 0
    while (i < text.length) {
        val e = CellWidth.clusterEnd(text, i)
        val blank = text[i] == '\n' || text[i] == '\t'
        val cells = if (blank) 1 else CellWidth.width(text, i, e)
        if (pen.col + cells > stop) break
        if (blank) pen.skipTo(pen.col + 1) else pen.cluster(i, e, cells)
        i = e
    }
    pen.flush()
    if (fits || pen.col >= limit) return pen.col
    put(pen.col, row, "…", pen.styleAt(i.coerceAtMost(text.length - 1)))
    return pen.col + 1
}

/** The cells [text] takes on one row, a line break or a tab as one. */
internal fun shownWidth(text: CharSequence): Int {
    var cells = 0
    var i = 0
    while (i < text.length) {
        val e = CellWidth.clusterEnd(text, i)
        cells += if (text[i] == '\n' || text[i] == '\t') 1 else CellWidth.width(text, i, e)
        i = e
    }
    return cells
}

/** One cell of a tab stop. */
private const val TAB = 8

/**
 * Writes clusters of [segs]' joined text one by one, and puts runs of them that share a look, a
 * link and a row into the writer as one piece each.
 */
private class Pen(val w: BlockWriter, val segs: List<Seg>, marks: List<String>, val right: Int) {
    val text: String
    private val starts = IntArray(segs.size)
    private val marked: BooleanArray?

    init {
        val b = StringBuilder()
        for ((i, s) in segs.withIndex()) {
            starts[i] = b.length
            b.append(s.text)
        }
        text = b.toString()
        marked = if (marks.isEmpty()) null else BooleanArray(text.length).also { m ->
            for (r in markRanges(text, marks)) for (k in r) if (k < m.size) m[k] = true
        }
    }

    var row = 0
    var col = 0
    var rowStart = 0

    private var seg = 0

    // The run being collected.
    private var open = false
    private var runRow = 0
    private var runCol = 0
    private var runStart = 0
    private var runEnd = 0
    private var runCells = 0
    private var runStyle = CellStyle.PLAIN
    private var runLink: Target.Link? = null

    fun limit() = w.limit(row, right)

    /** The look of the text at [i]. */
    fun styleAt(i: Int): CellStyle {
        while (seg + 1 < segs.size && starts[seg + 1] <= i) seg++
        while (seg > 0 && starts[seg] > i) seg--
        return if (marked != null && marked[i]) MARK else segs[seg].style
    }

    fun cluster(start: Int, end: Int, cells: Int) {
        val style = styleAt(start)
        val link = segs[seg].link
        if (open && (runRow != row || runStyle != style || runLink !== link || runCol + runCells != col)) flush()
        if (!open) {
            open = true
            runRow = row
            runCol = col
            runStart = start
            runCells = 0
            runStyle = style
            runLink = link
        }
        runEnd = end
        runCells += cells
        col += cells
    }

    fun skipTo(to: Int) {
        flush()
        col = to
    }

    fun newRow(rest: Int) {
        flush()
        row++
        w.reach(row)
        col = rest
        rowStart = rest
    }

    fun flush() {
        if (!open) return
        open = false
        w.put(runCol, runRow, text.substring(runStart, runEnd), runStyle)
        runLink?.let { w.mark(it, runCol, runRow, runCells) }
    }
}
