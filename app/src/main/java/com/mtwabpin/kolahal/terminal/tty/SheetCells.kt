/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink

/**
 * A menu or sheet on the grid: a box drawn with box-drawing characters, its title in the top
 * border, the line's details inside, then one row per action. A row of the box is one row of
 * cells; the painter carries the box's sides through any room a row has above and below its cells,
 * so actions can be finger-sized and the box still closes.
 *
 * ```
 * ┌─ countdown · today 6:55:12 ──────────┐
 * │ Open link                            │
 * │ Copy text                            │   ← the focused action: inverse
 * ├──────────────────────────────────────┤   ← before the destructive ones
 * │ Delete                               │
 * └──────────────────────────────────────┘
 * ```
 */
object SheetCells {
    /** What a row of the sheet is. Each [Kind.ACTION] row has its action's index in [action]. */
    class Row(val kind: Kind, val cells: HomeCells, val action: Int = -1)

    enum class Kind { BORDER, TEXT, ACTION }

    private val FRAME = CellStyle.of(Ink.DIM)

    /** The top border, with [title] in it when there is one: `┌─ title ───┐`. */
    fun top(title: String?, cols: Int): HomeCells {
        val w = HomeWriter(cols)
        w.put(0, 0, "┌", FRAME)
        var col = 1
        if (!title.isNullOrBlank() && cols > 6) {
            col = w.put(col, 0, "─ ", FRAME)
            col = w.line(oneLine(title.trim()), CellStyle.of(Ink.FG, flags = CellStyle.BOLD), 0, col, cols - 3)
            col = w.put(col, 0, " ", FRAME)
        }
        w.w.repeat(col, 0, '─', cols - 1 - col, FRAME)
        w.put(cols - 1, 0, "┐", FRAME)
        return w.build()
    }

    /** The bottom border `└───┘`, or a divider `├───┤` inside the box. */
    fun rule(cols: Int, bottom: Boolean): HomeCells {
        val w = HomeWriter(cols)
        w.put(0, 0, if (bottom) "└" else "├", FRAME)
        w.w.repeat(1, 0, '─', cols - 2, FRAME)
        w.put(cols - 1, 0, if (bottom) "┘" else "┤", FRAME)
        return w.build()
    }

    /**
     * A row inside the box: `│ text │`, [text] in [style], cut with "…". With [focused], the
     * inside of the row is inverse. [id] makes the row a spot (an action's), -1 for none.
     */
    fun line(text: String, style: CellStyle, cols: Int, focused: Boolean = false, id: Int = -1): HomeCells {
        val w = HomeWriter(cols)
        w.put(0, 0, "│", FRAME)
        w.put(cols - 1, 0, "│", FRAME)
        val shown = if (focused) style.with(CellStyle.INVERSE) else style
        if (focused) w.blank(1, 0, cols - 2, shown)
        w.line(oneLine(text), shown, 0, 2, cols - 2)
        if (id >= 0) w.spot(id, 0, 0, cols)
        return w.build()
    }

    /** [inner] (laid out [cols] − 4 wide) inside the box: `│ ` before it, ` │` after it. Its spots move with it. */
    fun boxed(inner: HomeCells, cols: Int): HomeCells {
        val w = HomeWriter(cols, inner.rows)
        for (r in 0 until inner.rows) {
            w.put(0, r, "│", FRAME)
            w.put(cols - 1, r, "│", FRAME)
        }
        w.copy(inner, 2, 0)
        return w.build()
    }

    /** The cells inside the box, between `│ ` and ` │`. */
    fun inner(cols: Int) = (cols - 4).coerceAtLeast(1)

    /**
     * Buttons in a row inside the box, from its right: `[ Cancel ]  [ Save ]`. Each is a spot with
     * its index in [labels]; a button that can't be used now ([enabled] false) is faint and no spot.
     */
    fun buttons(labels: List<Pair<String, Boolean>>, cols: Int): HomeCells {
        val inner = HomeWriter(inner(cols))
        var x = inner.cols
        for (i in labels.indices.reversed()) {
            val (label, enabled) = labels[i]
            val text = "[ $label ]"
            val width = CellWidth.total(text)
            if (x - width < 0) break
            x -= width
            inner.put(x, 0, text, CellStyle.of(Ink.PROMPT, flags = if (enabled) CellStyle.BOLD else CellStyle.FAINT))
            if (enabled) inner.spot(i, x, 0, width)
            x -= 2
        }
        return boxed(inner.build(), cols)
    }

    /** Text inside the box, wrapped over up to [maxRows] rows, each `│ … │`; the last row cut with "…". */
    fun text(text: String, style: CellStyle, cols: Int, maxRows: Int): HomeCells {
        val inner = (cols - 4).coerceAtLeast(1)
        val w = HomeWriter(inner)
        val rows = w.wrap(listOf(Seg(text.trim(), style)), 0, 0, 0, inner)
        val wrapped = w.build().grid
        val shown = rows.coerceIn(1, maxRows)
        val out = HomeWriter(cols, shown)
        for (r in 0 until shown) {
            out.put(0, r, "│", FRAME)
            out.put(cols - 1, r, "│", FRAME)
            val l = wrapped.rowText(r).trimEnd()
            // More text than rows: the last row ends with "…".
            val line = if (r == shown - 1 && rows > shown) cut(l, inner - 1).removeSuffix("…") + "…" else l
            out.put(2, r, line, style)
        }
        return out.build()
    }

    /**
     * The whole sheet, top to bottom: the border with [title], [detail] (muted) and [url] (in the
     * link color) when there are any, a divider, the [actions] (label and whether it is
     * destructive) with a divider before the first destructive one, in the error color, and the
     * bottom border. [focused] is the action that keys move to (inverse), -1 for none.
     */
    fun sheet(
        title: String?,
        detail: String?,
        url: String?,
        actions: List<Pair<String, Boolean>>,
        cols: Int,
        focused: Int = -1,
    ): List<Row> = buildList {
        add(Row(Kind.BORDER, top(title, cols)))
        var texts = false
        if (!detail.isNullOrBlank()) {
            add(Row(Kind.TEXT, text(detail, CellStyle.of(Ink.DIM), cols, 1)))
            texts = true
        }
        if (!url.isNullOrBlank()) {
            add(Row(Kind.TEXT, text(url, CellStyle.of(Ink.LINK, flags = CellStyle.UNDERLINE), cols, 3)))
            texts = true
        }
        if (texts && actions.isNotEmpty()) add(Row(Kind.BORDER, rule(cols, bottom = false)))
        var divided = false
        for ((i, a) in actions.withIndex()) {
            val (label, destructive) = a
            if (destructive && !divided && i > 0) add(Row(Kind.BORDER, rule(cols, bottom = false)))
            if (destructive) divided = true
            val style = CellStyle.of(if (destructive) Ink.ERROR else Ink.FG)
            add(Row(Kind.ACTION, line(label, style, cols, focused = i == focused, id = i), i))
        }
        add(Row(Kind.BORDER, rule(cols, bottom = true)))
    }
}
