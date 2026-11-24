/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.Target
import com.mtwabpin.kolahal.terminal.linksPerPiece
import com.mtwabpin.kolahal.terminal.spansOf
import kotlin.math.roundToInt

/**
 * What follows a row's words ([LineModel.blocks]) on the grid, each from column `left` to `right`
 * (exclusive) and from a row down; each returns the row after it.
 * - Text: muted, list items with their bullets in the accent color.
 * - Tables: in box drawing (`log.tables boxed`) or in plain columns.
 * - Bars: tracks in block eighths, their texts in columns so the tracks line up.
 * - Big numbers: bold at the grid's size, their labels muted under them.
 * - Column charts: two rows of block eighths, the newest column in the accent color.
 * Box drawing and blocks are characters here; the painter draws them as shapes, so they join.
 */
internal object Blocks {
    /** The least cells a table column gets before the table turns into lines of "name: value". */
    private const val MIN_COLUMN = 3

    /** The least cells a bar's track gets. */
    private const val MIN_TRACK = 4

    /** The least cells a big number's label is cut to, "…" included, so it still reads. */
    private const val MIN_LABEL = 10

    private val FG = CellStyle.of(Ink.FG)
    private val BOLD = CellStyle.of(Ink.FG, flags = CellStyle.BOLD)

    fun layout(w: BlockWriter, block: LineModel.Block, row: Int, left: Int, right: Int, boxed: Boolean, marks: List<String>): Int {
        // The block's links, piece by piece, in the order LineModel.targets lists them.
        val links = linksPerPiece(spansOf(block).map { it.text to it.url })
        var k = 0
        val next = { links.getOrNull(k++) }
        return when (block) {
            is LineModel.Block.Text -> text(w, block, row, left, right, marks, next)
            is LineModel.Block.Table -> if (block.rows.isEmpty()) row else table(w, block.rows, row, left, right, boxed, next)
            is LineModel.Block.Meters -> meters(w, block.rows, row, left, right, next)
            is LineModel.Block.Stats -> stats(w, block.items, row, left, right, next)
            is LineModel.Block.Chart -> chart(w, block.columns, row, left, right)
        }
    }

    /** Body text between and after the other blocks: muted, a list item's wrapped rows under its text. */
    private fun text(w: BlockWriter, b: LineModel.Block.Text, top: Int, left: Int, right: Int, marks: List<String>, link: () -> Target.Link?): Int {
        var row = top
        for (p in b.paras) {
            val segs = ArrayList<Seg>()
            var first = left
            var rest = left
            if (p.level > 0) {
                first += 2 * (p.level - 1)
                val prefix = "${p.prefix} "
                segs += Seg(prefix, CellStyle.of(Ink.PROMPT))
                rest = first + CellWidth.total(prefix)
            }
            for (piece in p.pieces) segs += seg(piece.text, piece.styles, piece.ink, DIM, link())
            row = w.wrap(segs, row, first, rest, right, marks)
        }
        return row
    }

    /**
     * A table. Columns are as wide as their widest cell; when that is too wide, the widest columns
     * give way and their cells wrap. If even [MIN_COLUMN] cells a column don't fit, each row
     * becomes lines of "name: value".
     * - Boxed: `┌─┬─┐`, `│` between cells, `╞═╪═╡` under the header, `├─┼─┤` between rows, `└─┴─┘`.
     * - Plain: two spaces between columns, the header underlined.
     * The header is bold; a cell lined up on the right (numbers) keeps to the right when it fits.
     */
    private fun table(w: BlockWriter, rows: List<StyledText.Row>, top: Int, left: Int, right: Int, boxed: Boolean, link: () -> Target.Link?): Int {
        val n = rows.maxOf { it.cells.size }.coerceAtLeast(1)
        // Every cell's pieces, links taken in reading order.
        val cells = rows.map { r -> r.cells.map { c -> c.spans.map { s -> spanSeg(s, if (r.header) BOLD else FG, Ink.FG, link()) } } }
        val widths = IntArray(n) { c -> cells.maxOf { r -> r.getOrNull(c)?.sumOf { shownWidth(it.text) } ?: 0 }.coerceAtLeast(1) }
        val frame = if (boxed) 3 * n + 1 else 2 * (n - 1)
        val room = right - left - frame
        while (widths.sum() > room) {
            val widest = widths.indices.maxBy { widths[it] }
            if (widths[widest] <= MIN_COLUMN) return records(w, rows, cells, top, left, right)
            widths[widest]--
        }
        val rule = DIM
        // Where each column's text starts.
        val starts = IntArray(n)
        var x = left + if (boxed) 2 else 0
        for (c in 0 until n) {
            starts[c] = x
            x += widths[c] + if (boxed) 3 else 2
        }
        var row = top
        if (boxed) row = border(w, row, left, widths, '┌', '─', '┬', '┐', rule)
        for ((r, line) in rows.withIndex()) {
            var end = row + 1
            for (c in 0 until n) {
                val segs = cells[r].getOrNull(c) ?: continue
                val shown = if (!boxed && line.header) segs.map { Seg(it.text, it.style.with(CellStyle.UNDERLINE), it.link) } else segs
                val width = shown.sumOf { shownWidth(it.text) }
                val from = if (line.cells[c].right && width <= widths[c]) starts[c] + widths[c] - width else starts[c]
                end = maxOf(end, w.wrap(shown, row, from, starts[c], starts[c] + widths[c]))
            }
            if (boxed) for (rr in row until end) {
                w.put(left, rr, "│", rule)
                for (c in 0 until n) w.put(starts[c] + widths[c] + 1, rr, "│", rule)
            }
            row = end
            if (boxed && r < rows.lastIndex) row = if (line.header) {
                border(w, row, left, widths, '╞', '═', '╪', '╡', rule)
            } else border(w, row, left, widths, '├', '─', '┼', '┤', rule)
        }
        if (boxed) row = border(w, row, left, widths, '└', '─', '┴', '┘', rule)
        return row
    }

    /** A table's border row: `┌───┬──┐` and the like, each column's width plus a cell on each side. */
    private fun border(w: BlockWriter, row: Int, left: Int, widths: IntArray, start: Char, line: Char, cross: Char, end: Char, style: CellStyle): Int {
        val b = StringBuilder().append(start)
        for ((i, width) in widths.withIndex()) {
            repeat(width + 2) { b.append(line) }
            b.append(if (i == widths.lastIndex) end else cross)
        }
        w.put(left, row, b.toString(), style)
        return row + 1
    }

    /** A table too wide for the grid: each row as lines of "name: value", a blank row between rows. */
    private fun records(w: BlockWriter, rows: List<StyledText.Row>, cells: List<List<List<Seg>>>, top: Int, left: Int, right: Int): Int {
        val hasHeader = rows.first().header
        val names = if (hasHeader) rows.first().cells.map { c -> c.spans.joinToString("") { it.text } } else emptyList()
        var row = top
        for (r in (if (hasHeader) 1 else 0) until rows.size) {
            if (row > top) row++
            for ((c, segs) in cells[r].withIndex()) {
                val name = names.getOrNull(c)?.takeIf { it.isNotBlank() }
                val all = if (name == null) segs else listOf(Seg("$name: ", DIM)) + segs
                row = w.wrap(all, row, left, left + 2, right)
            }
        }
        // A header with no rows under it still shows, with its links.
        if (hasHeader && rows.size == 1) row = w.wrap(cells[0].flatten(), row, left, left, right)
        return row
    }

    /**
     * Bars that follow each other, lined up as one: their labels, the texts before the tracks
     * (lined up on the right, against them), the tracks, and the texts after them, each column as
     * wide as its widest text. The tracks take the rest. The label is muted, the numbers in the
     * text color.
     */
    private fun meters(w: BlockWriter, meters: List<StyledText.Meter>, top: Int, left: Int, right: Int, link: () -> Target.Link?): Int {
        val parts = meters.map { m ->
            Triple(
                m.label.map { spanSeg(it, DIM, Ink.FG, link()) },
                m.start.map { spanSeg(it, FG, Ink.FG, link()) },
                m.end.map { spanSeg(it, FG, Ink.FG, link()) },
            )
        }
        val room = right - left
        val cap = (room / 4).coerceAtLeast(1)
        fun width(pick: (Triple<List<Seg>, List<Seg>, List<Seg>>) -> List<Seg>) =
            parts.maxOf { p -> pick(p).sumOf { shownWidth(it.text) } }.coerceAtMost(cap)

        val labels = width { it.first }
        val starts = width { it.second }
        val ends = width { it.third }
        val gaps = listOf(labels, starts, ends).count { it > 0 }
        val track = (room - labels - starts - ends - gaps).coerceAtLeast(MIN_TRACK + 2)
        for ((i, m) in meters.withIndex()) {
            val row = top + i
            var x = left
            val (label, start, end) = parts[i]
            if (labels > 0) {
                w.line(label, row, x, x + labels)
                x += labels + 1
            }
            if (starts > 0) {
                val width = start.sumOf { shownWidth(it.text) }
                w.line(start, row, x + (starts - width).coerceAtLeast(0), x + starts)
                x += starts + 1
            }
            track(w, m, row, x, x + track)
            x += track + 1
            if (ends > 0) w.line(end, row, x, minOf(x + ends, right))
        }
        return top + meters.size
    }

    /**
     * A bar's track from column [from] to [to] (exclusive) on [row]: `▕████▌   ▏`. The ends are
     * muted; the fill is in the bar's color (the accent without one), in eighths of a cell. A range
     * that starts inside a cell starts at a half cell. A range of no width still shows, as an eighth.
     */
    fun track(w: BlockWriter, m: StyledText.Meter, row: Int, from: Int, to: Int) {
        val inner = to - from - 2
        if (inner < 1) return
        w.put(from, row, "▕", DIM)
        w.put(to - 1, row, "▏", DIM)
        val total = inner * 8
        val a = (m.from.coerceIn(0f, 1f) * total).roundToInt()
        val b = maxOf((m.to.coerceIn(0f, 1f) * total).roundToInt(), a + 1).coerceAtMost(total)
        if (m.to <= 0f) return
        val fill = CellStyle.of(Ink.of(m.color ?: StyledText.ColorRole.ACCENT))
        val cells = StringBuilder()
        for (c in 0 until inner) {
            val lo = maxOf(a, c * 8) - c * 8
            val hi = minOf(b, c * 8 + 8) - c * 8
            cells.append(
                when {
                    hi <= lo -> ' '
                    lo == 0 && hi == 8 -> '█'
                    // Filled from the cell's left: the left eighths.
                    lo == 0 -> LEFT_EIGHTHS[hi]
                    // Filled to the cell's right: a right half, or the right eighth.
                    hi == 8 -> if (lo <= 2) '█' else if (lo <= 5) '▐' else '▕'
                    // A short range inside one cell.
                    else -> if (lo + hi < 8) '▌' else '▐'
                }
            )
        }
        w.put(from + 1, row, cells.toString(), fill)
    }

    /** The left eighths of a cell: none, ▏ (one eighth) to ▉ (seven), █. */
    private const val LEFT_EIGHTHS = " ▏▎▍▌▋▊▉█"

    /**
     * Big numbers: each value bold on one row and its label muted under it, side by side, three
     * cells apart.
     * - When they don't fit on one row, the longest label is cut with "…" so they do, never below
     *   its value's width or [MIN_LABEL]. Values are never cut.
     * - When that isn't enough, what doesn't fit goes on to a second pair of rows.
     */
    private fun stats(w: BlockWriter, items: List<StyledText.Stat>, top: Int, left: Int, right: Int, link: () -> Target.Link?): Int {
        if (items.isEmpty()) return top
        val hasLabels = items.any { it.label.isNotEmpty() }
        val step = if (hasLabels) 2 else 1
        // Links in reading order: each value, then its label.
        val parts = items.map { s ->
            s.value.map { spanSeg(it, BOLD, Ink.FG, link()) } to s.label.map { spanSeg(it, DIM, Ink.FG, link()) }
        }
        val values = parts.map { (value, _) -> value.sumOf { shownWidth(it.text) } }
        val labels = parts.map { (_, label) -> label.sumOf { shownWidth(it.text) } }
        val widths = IntArray(parts.size) { maxOf(values[it], labels[it]) }
        val over = widths.sum() + 3 * (parts.size - 1) - (right - left)
        if (over > 0) {
            val longest = labels.indices.maxBy { labels[it] }
            val cut = labels[longest] - over
            if (cut >= maxOf(values[longest], MIN_LABEL)) widths[longest] = cut
        }
        var row = top
        var x = left
        for ((i, part) in parts.withIndex()) {
            val (value, label) = part
            val width = widths[i]
            if (x > left && x + width > right) {
                x = left
                row += step
            }
            w.line(value, row, x, right)
            if (label.isNotEmpty()) w.line(label, row + 1, x, minOf(x + width, right))
            x += width + 3
        }
        w.reach(row + step - 1)
        return row + step
    }

    /**
     * A small column chart: two rows of block eighths (16 steps), a cell per column, or two when
     * there is room; the newest column in the accent color, the others faint; slots still to come
     * as a muted `·`. Its labels are spread along the row under it. When there are more columns
     * than cells, the newest ones show.
     */
    private fun chart(w: BlockWriter, columns: StyledText.Columns, top: Int, left: Int, right: Int): Int {
        val n = columns.heights.size
        if (n == 0) return top
        val room = (right - left).coerceAtLeast(1)
        val per = if (n * 2 <= room) 2 else 1
        val shown = minOf(n, room / per)
        val from = n - shown
        val newest = if (columns.newest) columns.heights.indexOfLast { it != null } else -1
        val accent = CellStyle.of(Ink.PROMPT)
        val faint = accent.with(CellStyle.FAINT)
        for (i in from until n) {
            val x = left + (i - from) * per
            val h = columns.heights[i]
            if (h == null) {
                w.put(x, top + 1, "·", DIM)
                continue
            }
            // A value of 0 still shows a sliver, so a quiet hour is not a gap.
            val level = (h.coerceIn(0f, 1f) * 16).roundToInt().coerceIn(1, 16)
            val style = if (i == newest) accent else faint
            val bottom = LOWER_BLOCKS[minOf(level, 8)].toString().repeat(per)
            w.put(x, top + 1, bottom, style)
            if (level > 8) w.put(x, top, LOWER_BLOCKS[level - 8].toString().repeat(per), style)
        }
        w.reach(top + 1)
        var row = top + 2
        val labels = columns.labels.map { l -> l.joinToString("") { it.text } }.filter { it.isNotEmpty() }
        if (labels.isNotEmpty()) {
            val widths = labels.map(CellWidth::total)
            val width = minOf(maxOf(shown * per, widths.sum() + labels.size - 1), room)
            val gap = if (labels.size > 1) (width - widths.sum()).coerceAtLeast(labels.size - 1) / (labels.size - 1).toFloat() else 0f
            var x = left.toFloat()
            for ((i, label) in labels.withIndex()) {
                // The last label ends at the chart's right edge.
                val at = if (i == labels.lastIndex && labels.size > 1) left + width - widths[i] else x.roundToInt()
                w.line(listOf(Seg(label, DIM)), row, at.coerceAtLeast(left), right)
                x += widths[i] + gap
            }
            row++
        }
        return row
    }

    /** The lower eighths of a cell: none, ▁ (one eighth) to █. */
    private const val LOWER_BLOCKS = " ▁▂▃▄▅▆▇█"
}
