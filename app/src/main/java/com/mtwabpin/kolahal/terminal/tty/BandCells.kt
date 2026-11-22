/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink

/**
 * The bands between the log and the prompt, on the grid: what was just done with Undo, the update's
 * download, the search's count, the log's filter, "Kolahal isn't your home app", the preview of a
 * typed setting, and the chip that scrolls the log back to its newest line. Each is a row or two of
 * cells.
 */
object BandCells {
    /** Spot ids: Undo on the notice, ✕ on the filter, the band itself when it takes a tap. */
    const val UNDO = 0
    const val CLEAR = 1
    const val BAND = 2

    private const val MB = 1024 * 1024

    /** "Deleted 3 lines … Undo" on the surface color: [text] muted, and `Undo` in the accent color at the end when [undo]. */
    fun notice(text: String, undo: Boolean, cols: Int): HomeCells {
        val w = HomeWriter(cols)
        w.blank(0, 0, cols, CellStyle.of(Ink.FG, Ink.SURFACE))
        val label = "Undo"
        val buttonWidth = if (undo) CellWidth.total(label) + 4 else 0
        w.line(oneLine(text), CellStyle.of(Ink.DIM, Ink.SURFACE), 0, 0, cols - buttonWidth)
        if (undo) {
            val x = cols - buttonWidth
            w.put(x + 2, 0, label, CellStyle.of(Ink.PROMPT, Ink.SURFACE, CellStyle.BOLD))
            w.spot(UNDO, x, 0, buttonWidth)
        }
        return w.build()
    }

    /** "Downloading update   4 MB / 10 MB  40%", and the download's progress line under it. */
    fun download(downloaded: Long, total: Long, percent: Int, cols: Int): HomeCells {
        val w = HomeWriter(cols, 2)
        val amount = "${downloaded / MB} MB / ${total / MB} MB  $percent%"
        val amountWidth = CellWidth.total(amount)
        w.line("Downloading update", CellStyle.of(Ink.DIM), 0, 0, (cols - amountWidth - 1).coerceAtLeast(1))
        w.put((cols - amountWidth).coerceAtLeast(0), 0, amount, CellStyle.of(Ink.DIM))
        w.lineBar(0, 1, cols, percent / 100f, CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD))
        return w.build()
    }

    /** A band in the accent color, inverse, its [text] in the middle and cut to fit: the default-home and the preview bands. */
    fun accent(text: String, cols: Int, tappable: Boolean): HomeCells {
        val w = HomeWriter(cols)
        val style = CellStyle.of(Ink.PROMPT, flags = CellStyle.INVERSE or CellStyle.BOLD)
        w.blank(0, 0, cols, style)
        val shown = cut(oneLine(text), cols - 2)
        w.put(((cols - CellWidth.total(shown)) / 2).coerceAtLeast(0), 0, shown, style)
        if (tappable) w.spot(BAND, 0, 0, cols)
        return w.build()
    }

    /** Muted words over as many rows as they need: the search's count and hints. */
    fun summary(text: String, cols: Int): HomeCells {
        val w = HomeWriter(cols)
        w.wrap(listOf(Seg(text, CellStyle.of(Ink.DIM))), 0, 0, 0, cols)
        return w.build()
    }

    /** `only <filter> · 12 lines` on the surface color, and `✕` at the end that shows every line again. */
    fun filter(label: String, count: Int, cols: Int): HomeCells {
        val w = HomeWriter(cols)
        w.blank(0, 0, cols, CellStyle.of(Ink.FG, Ink.SURFACE))
        val muted = CellStyle.of(Ink.DIM, Ink.SURFACE)
        val clear = 5
        w.line(
            listOf(
                Seg("only ", muted),
                Seg(oneLine(label), CellStyle.of(Ink.PROMPT, Ink.SURFACE, CellStyle.BOLD)),
                Seg(" · $count line" + if (count == 1) "" else "s", muted),
            ),
            0, 0, cols - clear,
        )
        w.put(cols - clear + 2, 0, "✕", CellStyle.of(Ink.FG, Ink.SURFACE))
        w.spot(CLEAR, cols - clear, 0, clear)
        return w.build()
    }

    /** What the chip over the log's bottom says. */
    const val JUMP = " ↓ Scroll to bottom "

    /** The chip that scrolls back to the newest line: [JUMP] on the surface color, in the accent color. */
    fun jump(): HomeCells {
        val w = HomeWriter(CellWidth.total(JUMP))
        w.put(0, 0, JUMP, CellStyle.of(Ink.PROMPT, Ink.SURFACE, CellStyle.BOLD))
        w.spot(BAND, 0, 0, w.cols)
        return w.build()
    }
}
