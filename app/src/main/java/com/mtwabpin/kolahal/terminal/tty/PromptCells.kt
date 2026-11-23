/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink

/**
 * The prompt on the grid: `$ `, the typed text, and the clear (`✕`) and key bar (`⌨`) buttons at
 * the row's end. The text is drawn here; typing goes into a text field under the row that no one
 * sees, so keyboards, composition and selection work as they do anywhere else.
 * - The cursor is a block: the cell under it in inverse.
 * - Selected text is inverse; the word being composed is underlined.
 * - Text longer than the row scrolls by cells, as little as keeps the cursor in view ([scroll]).
 * - A secret is shown as one `•` per character ([mask]).
 * - Where the screen's rounded bottom corners cut the row, its ends move in by whole cells ([ends]).
 *
 * Spot ids: [QUICK] (the `$`), [CLEAR], [KEYS].
 */
object PromptCells {
    const val QUICK = 0
    const val CLEAR = 1
    const val KEYS = 2

    /** The cells `$ ` takes before the text. */
    const val LEAD = 2

    /** The cells each button at the end takes: the glyph with a cell of room on each side. */
    const val BUTTON = 3

    private const val CLEAR_GLYPH = "✕"

    /** ⌨ as text, never as a color emoji. */
    private const val KEYS_GLYPH = "⌨\uFE0E"

    /**
     * The cells the prompt leaves empty at its two ends where the screen's rounded corners cut the
     * row: before the `$` and after the key bar's button, as the row is laid out (left to right; in
     * a right-to-left layout the `$` is on the right). [free]: the row's columns clear of the
     * corners, as [HeaderCells.zones] gives them, left to right on the screen. The button's last
     * cell is only room around its glyph, so it may stay where a corner is. None when [free] is not
     * one stretch.
     */
    fun ends(cols: Int, free: List<IntRange>, rtl: Boolean): Pair<Int, Int> {
        val zone = free.singleOrNull() ?: return 0 to 0
        val left = zone.first.coerceIn(0, cols)
        val right = (cols - 1 - zone.last).coerceIn(0, cols)
        return if (rtl) right to (left - 1).coerceAtLeast(0) else left to (right - 1).coerceAtLeast(0)
    }

    /** The cells the text has, in a row [cols] wide, with the clear button or without it. */
    fun textCols(cols: Int, clear: Boolean): Int =
        (cols - LEAD - BUTTON - if (clear) BUTTON else 0).coerceAtLeast(1)

    /** The cells [text] takes up to [end] (a character index): the cursor's column there. */
    fun columnOf(text: String, end: Int): Int {
        var cells = 0
        var i = 0
        val stop = end.coerceIn(0, text.length)
        while (i < stop) {
            val e = CellWidth.clusterEnd(text, i)
            cells += shown(text, i, e)
            i = e
        }
        return cells
    }

    /**
     * The first column of the text that shows when the cursor is at [cursor] (a character index), in
     * [width] cells, after [previous]: unchanged while the cursor is in view, else moved just enough
     * to bring it back, as a text field scrolls. The cursor at the end needs a cell of its own.
     */
    fun scroll(text: String, cursor: Int, width: Int, previous: Int): Int {
        val at = columnOf(text, cursor)
        val total = columnOf(text, text.length) + 1
        var first = previous.coerceIn(0, (total - width).coerceAtLeast(0))
        if (at < first) first = at
        if (at >= first + width) first = at - width + 1
        return first.coerceAtLeast(0)
    }

    /**
     * The prompt's row, [cols] wide.
     * - [selStart] to [selEnd]: the selected characters (the end excluded), or where the cursor is
     *   when they are equal; [cursor] says whether the cursor shows (the prompt has the focus and
     *   the blink is on).
     * - [compStart] to [compEnd]: the characters the keyboard is still composing; -1 for none.
     * - [first]: the first column of the text that shows ([scroll]).
     * - [keyBarOn]: the key bar's button in the accent color, else muted.
     * - [buttons]: false for a field with no `✕` and `⌨` (a secret's).
     * - [lead], [trail]: cells left empty before the `$` and after the buttons ([ends]).
     */
    fun row(
        text: String,
        selStart: Int,
        selEnd: Int,
        cursor: Boolean,
        first: Int,
        cols: Int,
        keyBarOn: Boolean,
        compStart: Int = -1,
        compEnd: Int = -1,
        mask: Boolean = false,
        prompt: String = "$",
        buttons: Boolean = true,
        lead: Int = 0,
        trail: Int = 0,
    ): HomeCells {
        val w = HomeWriter(cols)
        val clear = buttons && text.isNotEmpty()
        val width = if (buttons) textCols(cols - lead - trail, clear) else (cols - lead - trail - LEAD).coerceAtLeast(1)
        w.put(lead, 0, prompt, CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD))
        if (buttons) w.spot(QUICK, lead, 0, LEAD)
        val plain = CellStyle.of(Ink.FG)
        val composition = if (compStart in 0 until compEnd) compStart until compEnd else null
        val from = minOf(selStart, selEnd)
        val to = maxOf(selStart, selEnd)
        var col = 0
        var i = 0
        var cursorDrawn = false
        // The text area keeps its order in a right-to-left frame: it moves as a whole, as wide as it is.
        val area = w.newUnit()
        w.span(area, lead + LEAD, lead + LEAD + width)
        while (i < text.length) {
            val e = CellWidth.clusterEnd(text, i)
            val cells = shown(text, i, e)
            val x = lead + LEAD + col - first
            if (col >= first && col + cells <= first + width) {
                var style = plain
                if (composition != null && i in composition) style = style.with(CellStyle.UNDERLINE)
                val selected = from != to && i >= from && i < to
                val under = cursor && from == to && i == to
                if (selected || under) style = style.with(CellStyle.INVERSE)
                if (under) cursorDrawn = true
                when {
                    mask -> w.put(x, 0, "•", style, area)
                    text[i] == '\t' -> w.put(x, 0, " ", style, area)
                    else -> w.put(x, 0, text.substring(i, e), style, area)
                }
            }
            col += cells
            i = e
        }
        // At the end of the text the cursor is a block on its own cell.
        if (cursor && !cursorDrawn && from == to) {
            val x = lead + LEAD + columnOf(text, to) - first
            if (x in lead + LEAD until lead + LEAD + width) w.put(x, 0, " ", CellStyle.of(Ink.FG, flags = CellStyle.INVERSE), area)
        }
        if (buttons) {
            var right = cols - trail
            right -= BUTTON
            w.put(right + 1, 0, KEYS_GLYPH, CellStyle.of(if (keyBarOn) Ink.PROMPT else Ink.DIM, flags = CellStyle.BOLD))
            w.spot(KEYS, right, 0, BUTTON)
            if (clear) {
                right -= BUTTON
                w.put(right + 1, 0, CLEAR_GLYPH, CellStyle.of(Ink.DIM))
                w.spot(CLEAR, right, 0, BUTTON)
            }
        }
        return w.build()
    }

    /** The cells a cluster takes in the prompt: a tab or a character that takes none still takes one, so the cursor can be on it. */
    private fun shown(text: String, start: Int, end: Int): Int =
        if (text[start] == '\t') 1 else CellWidth.width(text, start, end).coerceAtLeast(1)

    /**
     * The character index a tap on column [col] of the text area means, with the text scrolled to
     * [first]: the cluster under it, or the end of the text past it. A tap on a cluster's right
     * half goes after it, as in a text field.
     */
    fun offsetAt(text: String, col: Int, first: Int, half: Boolean = false): Int {
        val target = col + first
        var at = 0
        var i = 0
        while (i < text.length) {
            val e = CellWidth.clusterEnd(text, i)
            val cells = shown(text, i, e)
            if (target < at + cells) return if (half && cells == 1) e else i
            at += cells
            i = e
        }
        return text.length
    }
}
