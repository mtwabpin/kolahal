/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * `clock`: the time in big digits made of block cells, as tty-clock shows it, with the date under
 * it. The digits are as big as the grid allows; the seconds show when they fit, else the clock
 * changes once a minute. Without `clock.24h`, the hours run 1 to 12 with AM or PM beside them.
 * The digits are centered as they are drawn, so a one-digit hour is in the middle too.
 *
 * The time comes from [ScreenEnv.clock], so a test can set it. A frame that falls in the same
 * second (or minute) as the last one changes no cell, and the host then repaints nothing.
 */
class ClockScreen : TtyScreen {
    override val name = "clock"
    override val title = "Clock"
    override val fps = 4
    override val animated = false

    private lateinit var env: ScreenEnv
    private var cols = 0
    private var rows = 0

    // The layout for the grid's size, chosen in [resize].
    private var seconds = false
    private var pixelW = 1
    private var pixelH = 1

    private var shown = Long.MIN_VALUE
    private lateinit var longDate: DateTimeFormatter
    private lateinit var shortDate: DateTimeFormatter

    override fun start(env: ScreenEnv) {
        this.env = env
        longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", env.locale)
        shortDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", env.locale)
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols
        this.rows = rows
        // The biggest digits that fit, with seconds if possible; a pixel is twice as wide as it
        // is tall, since a cell is about twice as tall as it is wide.
        val choices = (3 downTo 1).map { Triple(true, it * 2, it) } +
                (3 downTo 1).map { Triple(false, it * 2, it) } +
                listOf(Triple(true, 1, 1), Triple(false, 1, 1))
        val fit = choices.firstOrNull { (s, w, h) -> width(s, w) <= cols && DIGIT_ROWS * h + 2 <= rows }
            ?: Triple(false, 1, 1)
        seconds = fit.first
        pixelW = fit.second
        pixelH = fit.third
        shown = Long.MIN_VALUE
    }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        val now = env.clock()
        val unit = if (seconds) 1_000L else 60_000L
        val step = Math.floorDiv(now, unit)
        if (step == shown) return true
        shown = step
        draw(grid, now)
        return true
    }

    private fun draw(grid: CellBuffer, now: Long) {
        val time = Instant.ofEpochMilli(now).atZone(env.zone)
        var hour = time.hour
        val pm = hour >= 12
        if (!env.clock24) hour = (hour % 12).let { if (it == 0) 12 else it }
        grid.clear()
        val bigWidth = width(seconds, pixelW)
        val height = DIGIT_ROWS * pixelH + 2
        val top = ((rows - height) / 2).coerceAtLeast(0)
        // The time is centered as it is drawn: in 12-hour time a one-digit hour leaves out the
        // tens' place, and a 1 has two empty columns on its left.
        val tens = if (env.clock24 || hour >= 10) hour / 10 else -1
        val skip = (if (tens >= 0) blankColumns(tens) else 4 + blankColumns(hour % 10)) * pixelW
        val drawn = bigWidth - skip
        val left = ((cols - drawn) / 2).coerceAtLeast(0)
        var x = left - skip
        x = digit(grid, tens, x, top)
        x = digit(grid, hour % 10, x, top)
        x = colon(grid, x, top)
        x = digit(grid, time.minute / 10, x, top)
        x = digit(grid, time.minute % 10, x, top)
        if (seconds) {
            x = colon(grid, x, top)
            x = digit(grid, time.second / 10, x, top)
            digit(grid, time.second % 10, x, top)
        }
        // AM or PM hangs beside the digits, outside their centering, when it would fit beside the
        // widest time too: so it doesn't move to the date line and back as the hour changes.
        val half = if (pm) "PM" else "AM"
        val beside = !env.clock24 && (cols - bigWidth) / 2 + bigWidth + 1 + half.length <= cols
        if (beside) grid.put(left + drawn + 1, top, half, DATE)
        var date = time.format(longDate)
        if (CellWidth.total(date) > cols) date = time.format(shortDate)
        if (!env.clock24 && !beside) date = "$half · $date"
        val row = top + DIGIT_ROWS * pixelH + 1
        grid.put(((cols - CellWidth.total(date)) / 2).coerceAtLeast(0), row, date, DATE)
    }

    /** Draws digit [d] (or nothing for -1) with its left edge at [x]; returns where the next one starts. */
    private fun digit(grid: CellBuffer, d: Int, x: Int, top: Int): Int {
        if (d >= 0) {
            val font = FONT[d]
            for (r in 0 until DIGIT_ROWS) for (c in 0 until 3) {
                if (font[r * 3 + c] == '#') {
                    grid.fill(x + c * pixelW, top + r * pixelH, pixelW, pixelH, BLOCK, DIGIT)
                }
            }
        }
        return x + (3 + 1) * pixelW
    }

    /** How many columns on the left of digit [d] have no pixel: two for a 1, none for the others. */
    private fun blankColumns(d: Int): Int {
        val font = FONT[d]
        return (0 until 3).firstOrNull { c -> (0 until DIGIT_ROWS).any { r -> font[r * 3 + c] == '#' } } ?: 3
    }

    private fun colon(grid: CellBuffer, x: Int, top: Int): Int {
        grid.fill(x, top + 1 * pixelH, pixelW, pixelH, BLOCK, DIGIT)
        grid.fill(x, top + 3 * pixelH, pixelW, pixelH, BLOCK, DIGIT)
        return x + (1 + 1) * pixelW
    }

    companion object {
        private const val DIGIT_ROWS = 5
        private const val BLOCK = 0x2588
        private val DIGIT = CellStyle.of(Ink.PROMPT)
        private val DATE = CellStyle.of(Ink.DIM)

        /** Each digit is 3 × 5 pixels, row by row. */
        private val FONT = listOf(
            "#### ## ## ####", "  #  #  #  #  #", "###  #####  ###", "###  ####  ####", "# ## ####  #  #",
            "####  ###  ####", "####  #### ####", "###  #  #  #  #", "#### ##### ####", "#### ####  ####",
        )

        /** Cells across "HH:MM" or "HH:MM:SS" with pixels [pixelW] cells wide: a gap of one pixel between parts. */
        fun width(seconds: Boolean, pixelW: Int): Int =
            if (seconds) (6 * 3 + 2 * 1 + 7) * pixelW else (4 * 3 + 1 + 4) * pixelW
    }
}
