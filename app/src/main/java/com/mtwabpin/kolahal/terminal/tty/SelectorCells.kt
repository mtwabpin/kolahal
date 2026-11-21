/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink

/**
 * The app selector (`selector.rail`) on the grid: its list rows, the letters along the edge, and
 * the letter beside the thumb. One font size, so nothing is magnified: the app under the thumb is
 * inverse in the accent color instead, and so is its letter.
 *
 * A row on the right edge reads from the left: the section letter, the profile's tag, `★` for a
 * favorite outside the favorites, the name, and `⋯ Actions` when lifting there opens the app's
 * actions. On the left edge ([mirrored]) the parts come in the other order, and the name is
 * aligned to the right, toward the strip.
 */
object SelectorCells {
    /** The cells the section letter takes, with its room. */
    const val LETTER = 3

    private const val ACTIONS = "⋯ Actions"

    /**
     * One row of the list, [cols] wide.
     * - [letter]: the section letter to show (where a section starts, or on the focused row), or null.
     * - [tag]: the profile's tag (`W`), in [tagInk]; null for the personal profile.
     * - [star]: a favorite shown outside the favorites. [favorite]: in the favorites (its letter is ★).
     * - [focused]: the app under the thumb. [far]: lifting now opens its actions.
     * - [quiet]: its profile is paused, so it is faint.
     */
    fun row(
        label: String,
        letter: Char?,
        tag: String?,
        tagInk: Ink,
        star: Boolean,
        favorite: Boolean,
        focused: Boolean,
        far: Boolean,
        quiet: Boolean,
        mirrored: Boolean,
        cols: Int,
    ): HomeCells {
        val w = HomeWriter(cols)
        val faint = if (quiet) CellStyle.FAINT else 0
        val letterStyle = when {
            focused -> CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD)
            favorite -> CellStyle.of(Ink.PROMPT, flags = CellStyle.FAINT)
            else -> CellStyle.of(Ink.DIM, flags = CellStyle.FAINT)
        }
        val nameStyle = if (focused) CellStyle.of(Ink.PROMPT, flags = CellStyle.INVERSE or CellStyle.BOLD or faint)
        else CellStyle.of(Ink.FG, flags = faint)
        val marks = buildList {
            if (tag != null) add(Seg("[$tag]", CellStyle.of(tagInk, flags = CellStyle.BOLD or faint)))
            if (star) add(Seg("★", CellStyle.of(Ink.PROMPT, flags = CellStyle.FAINT)))
        }
        val marksWidth = marks.sumOf { CellWidth.total(it.text) + 1 }
        val note = if (focused && far) ACTIONS else null
        val noteWidth = note?.let { CellWidth.total(it) + 1 } ?: 0
        val nameRoom = (cols - LETTER - marksWidth - noteWidth).coerceAtLeast(1)
        val name = cut(" ${oneLine(label)} ", nameRoom)
        val nameWidth = CellWidth.total(name)
        if (!mirrored) {
            letter?.let { w.put(0, 0, it.toString(), letterStyle) }
            var col = LETTER
            for (m in marks) col = w.put(col, 0, m.text, m.style) + 1
            // The name's own leading space goes in the cell of room before it.
            col = w.put(col - 1, 0, name, nameStyle)
            note?.let { w.put(col + 1, 0, it, CellStyle.of(Ink.PROMPT)) }
        } else {
            letter?.let { w.put(cols - 1, 0, it.toString(), letterStyle) }
            var col = cols - LETTER
            for (m in marks) {
                col -= CellWidth.total(m.text) + 1
                w.put(col + 1, 0, m.text, m.style)
            }
            col -= nameWidth - 1
            w.put(col, 0, name, nameStyle)
            note?.let { w.put((col - CellWidth.total(it) - 1).coerceAtLeast(0), 0, it, CellStyle.of(Ink.PROMPT)) }
        }
        w.spot(0, 0, 0, cols)
        return w.build()
    }

    /** A letter on the edge: muted, fainter when [faint] (outside where a touch starts), or bold in the text color when [current]. */
    fun letter(ch: Char, current: Boolean, faint: Boolean = false): HomeCells {
        val w = HomeWriter(1)
        val style = when {
            current -> CellStyle.of(Ink.FG, flags = CellStyle.BOLD)
            faint -> CellStyle.of(Ink.DIM, flags = CellStyle.FAINT)
            else -> CellStyle.of(Ink.DIM)
        }
        w.put(0, 0, ch.toString(), style)
        return w.build()
    }

    /** The letter beside the thumb: ` A ` inverse in the accent color, bold. */
    fun bubble(ch: Char): HomeCells {
        val w = HomeWriter(3)
        w.put(0, 0, " $ch ", CellStyle.of(Ink.PROMPT, flags = CellStyle.INVERSE or CellStyle.BOLD))
        return w.build()
    }
}
