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
    if (fits || pen.col >= limit) {
        pen.flush()
        return pen.col
    }
    pen.ellipsis(pen.styleAt(i.coerceAtMost(text.length - 1)))
    pen.flush()
    return pen.col
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
 * link and a row into the writer as one piece each. Each row is one unit of the writer
 * ([BlockWriter.newUnit]), so it keeps its order when the frame turns.
 *
 * Text with right-to-left letters or letters that join ([BidiText.needs]) is collected a row at a
 * time and placed when the row ends: the bidi algorithm puts the row's clusters in the order they
 * show (a bracket in right-to-left text mirrored), and each word of a joining script becomes one
 * piece the painter shapes as a whole ([BlockWriter.Shaped]). Other text is written as it comes.
 */
private class Pen(val w: BlockWriter, val segs: List<Seg>, marks: List<String>, val right: Int) {
    val text: String
    private val starts = IntArray(segs.size)
    private val marked: BooleanArray?

    /** Each character's bidi level and its paragraph's, for text that needs them; null for plain text. */
    private val levels: ByteArray?
    private val bases: ByteArray?

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
        if (BidiText.needs(text)) {
            val (l, p) = BidiText.levels(text)
            levels = l
            bases = p
        } else {
            levels = null
            bases = null
        }
    }

    var row = 0
    var col = 0
    var rowStart = 0

    private var seg = 0

    /** The current row's unit; -1 until something is written on it. */
    private var unit = -1

    private fun unit(): Int {
        if (unit < 0) unit = w.newUnit()
        return unit
    }

    // The run being collected.
    private var open = false
    private var runRow = 0
    private var runCol = 0
    private var runStart = 0
    private var runEnd = 0
    private var runCells = 0
    private var runStyle = CellStyle.PLAIN
    private var runLink: Target.Link? = null

    // The row being collected, for text with levels: each cluster's start ([GAP] for skipped
    // cells, [ELLIPSIS] for "…"), end, cells and column.
    private var n = 0
    private var cStart = IntArray(16)
    private var cEnd = IntArray(16)
    private var cCells = IntArray(16)
    private var cCol = IntArray(16)

    fun limit() = w.limit(row, right)

    /** The look of the text at [i]. */
    fun styleAt(i: Int): CellStyle {
        while (seg + 1 < segs.size && starts[seg + 1] <= i) seg++
        while (seg > 0 && starts[seg] > i) seg--
        return if (marked != null && marked[i]) MARK else segs[seg].style
    }

    fun cluster(start: Int, end: Int, cells: Int) {
        if (levels != null) {
            record(start, end, cells)
            return
        }
        val style = styleAt(start)
        val link = segs[seg].link
        if (open && (runRow != row || runStyle != style || runLink !== link || runCol + runCells != col)) flushRun()
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
        if (levels != null) {
            if (to > col) record(GAP, GAP, to - col)
            col = to
            return
        }
        flushRun()
        col = to
    }

    /** "…" after what is written, where a cut row ends: on the left of right-to-left text. */
    fun ellipsis(style: CellStyle) {
        if (levels != null) {
            ellipsisStyle = style
            record(ELLIPSIS, ELLIPSIS, 1)
            return
        }
        flushRun()
        w.put(col, row, "…", style, unit())
        col++
    }

    private var ellipsisStyle = CellStyle.PLAIN

    fun newRow(rest: Int) {
        flush()
        row++
        w.reach(row)
        col = rest
        rowStart = rest
        unit = -1
    }

    /** Writes what is collected. */
    fun flush() {
        if (levels != null) placeRow() else flushRun()
    }

    private fun flushRun() {
        if (!open) return
        open = false
        val u = unit()
        w.put(runCol, runRow, text.substring(runStart, runEnd), runStyle, u)
        runLink?.let { w.mark(it, runCol, runRow, runCells, unit = u) }
    }

    private fun record(start: Int, end: Int, cells: Int) {
        if (n == cStart.size) {
            cStart = cStart.copyOf(n * 2)
            cEnd = cEnd.copyOf(n * 2)
            cCells = cCells.copyOf(n * 2)
            cCol = cCol.copyOf(n * 2)
        }
        cStart[n] = start
        cEnd[n] = end
        cCells[n] = cells
        cCol[n] = col
        n++
        col += cells
    }

    /**
     * Places the row collected: its clusters in the order the bidi algorithm shows them, from the
     * column the row started at, in runs of one look, one link, one direction, and one joining
     * script word or none.
     */
    private fun placeRow() {
        if (n == 0) return
        val levels = levels!!
        val firstText = (0 until n).firstOrNull { cStart[it] >= 0 }
        val base = if (firstText != null) bases!![cStart[firstText]] else 0
        val lv = ByteArray(n) { k -> if (cStart[k] >= 0) levels[cStart[k]] else base }
        // Spaces at the row's end take the paragraph's level (rule L1), so they stay at its end.
        var k = n - 1
        while (k >= 0 && (cStart[k] == GAP || (cStart[k] >= 0 && text[cStart[k]] == ' '))) lv[k--] = base
        val order = Array<Any>(n) { it }
        java.text.Bidi.reorderVisually(lv, 0, order, 0, n)
        var x = cCol[0]
        val u = unit()
        // The piece being built, in the order it shows.
        val shown = StringBuilder()
        var pieceCol = x
        var pieceCells = 0
        var pieceStyle = CellStyle.PLAIN
        var pieceLink: Target.Link? = null
        var pieceRtl = false
        var pieceJoins = false
        var lo = Int.MAX_VALUE
        var hi = -1
        var last = -1
        fun end() {
            if (pieceCells == 0) return
            val shaped = if (pieceJoins && lo < hi) BlockWriter.Shaped(text.substring(lo, hi), pieceRtl) else null
            w.put(pieceCol, row, shown.toString(), pieceStyle, u, shaped)
            pieceLink?.let { w.mark(it, pieceCol, row, pieceCells, unit = u) }
            shown.setLength(0)
            pieceCells = 0
            lo = Int.MAX_VALUE
            hi = -1
            last = -1
        }
        for (o in order) {
            val c = o as Int
            val start = cStart[c]
            if (start == GAP) {
                end()
                x += cCells[c]
                continue
            }
            val rtl = lv[c].toInt() and 1 == 1
            val style = if (start == ELLIPSIS) ellipsisStyle else styleAt(start)
            val link = if (start == ELLIPSIS) null else segs[seg].link
            val joins = start >= 0 && BidiText.joins(Character.codePointAt(text, start))
            // A word of a joining script goes on while its letters follow each other in the text.
            val follows = last >= 0 && start >= 0 && if (rtl) cEnd[c] == cStart[last] else cStart[c] == cEnd[last]
            if (pieceCells > 0 && (style != pieceStyle || link !== pieceLink || rtl != pieceRtl || joins != pieceJoins || (joins && !follows))) end()
            if (pieceCells == 0) {
                pieceCol = x
                pieceStyle = style
                pieceLink = link
                pieceRtl = rtl
                pieceJoins = joins
            }
            when {
                start == ELLIPSIS -> shown.append('…')
                rtl && cEnd[c] - start == Character.charCount(Character.codePointAt(text, start)) ->
                    shown.appendCodePoint(BidiText.mirror(Character.codePointAt(text, start)))

                else -> shown.append(text, start, cEnd[c])
            }
            if (start >= 0) {
                lo = minOf(lo, start)
                hi = maxOf(hi, cEnd[c])
                last = c
            }
            pieceCells += cCells[c]
            x += cCells[c]
        }
        end()
        n = 0
    }

    private companion object {
        /** A record of skipped cells (a tab). */
        const val GAP = -1

        /** The record of a cut row's "…". */
        const val ELLIPSIS = -2
    }
}
