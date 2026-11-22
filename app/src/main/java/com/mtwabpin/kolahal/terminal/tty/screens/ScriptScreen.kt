/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScreenFrame
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth

/**
 * A script's own screen (`tty.screen`), as home runs it. The script draws in its own process, so this
 * only passes messages both ways ([Link]): home's frames, keys and taps to the script, and what the
 * script drew onto the grid, when the next frame comes round. Nothing here waits for the script.
 *
 * - A tick is sent once the frame of the last one came back, so a script that is slow draws fewer
 *   frames, and its ticks carry the time that went by.
 * - A tap closes the screen unless the script has `tap`, and a key unless it has `key`: the function
 *   then gets it, and may close the screen by returning false. Back always closes it (the host).
 * - It runs whatever "Remove animations" says, as the clock does: what it shows is up to the script.
 *
 * All of it runs on the main thread, the messages of [Link] too.
 */
class ScriptScreen(
    override val name: String,
    override val title: String,
    private val wantedFps: Int,
    private val keys: Boolean,
    private val taps: Boolean,
    private val link: Link,
) : TtyScreen {
    /** The script, in the other process. */
    interface Link {
        fun attach(cols: Int, rows: Int)
        fun resize(cols: Int, rows: Int)
        fun tick(timeMs: Long, deltaMs: Long)
        fun key(key: String)
        fun tap(col: Int, row: Int)

        /** Nothing to say, but home still shows the screen. */
        fun alive()
        fun close()
    }

    /** A script that wants no frames is asked for its drawing this often. */
    override val fps: Int = if (wantedFps <= 0) IDLE_FPS else wantedFps
    override val animated: Boolean get() = false

    private val frames = ArrayDeque<ScreenFrame>()
    private var cols = 1
    private var rows = 1

    /** A tick is out, and its frame not back yet. */
    private var waiting = false
    private var lastTick = -1L
    private var lastSent = 0L
    private var ended = false

    /** A frame of the script's. It shows when the host draws next. */
    fun received(frame: ScreenFrame) {
        frames.addLast(frame)
        if (frame.tick) waiting = false
    }

    /** The script's screen ended: the host closes it. */
    fun ended() {
        ended = true
    }

    override fun start(env: ScreenEnv) {
        cols = env.cols
        rows = env.rows
        link.attach(cols, rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols
        this.rows = rows
        link.resize(cols, rows)
    }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        while (true) {
            val next = frames.removeFirstOrNull() ?: break
            // One drawn for another size (just before a rotation) is left out: the script draws again.
            if (next.cols == grid.cols && next.rows == grid.rows) FramePlayer.play(next, grid)
        }
        if (ended) return false
        if (wantedFps > 0) {
            if (!waiting) {
                link.tick(timeMs, if (lastTick < 0) deltaMs else timeMs - lastTick)
                lastTick = timeMs
                lastSent = timeMs
                waiting = true
            }
        } else if (timeMs - lastSent >= ALIVE_MS) {
            lastSent = timeMs
            link.alive()
        }
        return true
    }

    /** What it shows is the script's own: no still frame stands for it. */
    override fun still(grid: CellBuffer) = Unit

    override fun key(key: TtyKey): Boolean {
        if (!keys) return false
        keyName(key)?.let(link::key)
        return true
    }

    override fun tap(col: Int, row: Int): Boolean {
        if (!taps) return false
        link.tap(col.coerceIn(0, cols - 1), row.coerceIn(0, rows - 1))
        return true
    }

    override fun stop() = link.close()

    companion object {
        /** How often a script without frames is asked for drawing it did since (in answer to a tap, say). */
        const val IDLE_FPS = 10

        /** With nothing else to say, home tells a script without frames that it is still there this often. */
        const val ALIVE_MS = 15_000L

        /**
         * The name a script gets for [key]: the character it types, or `enter`, `escape`, `space`,
         * `up`, `down`, `left`, `right`, `tab` or `delete`. Null for a key that a script has no use
         * for (Shift), which does nothing then.
         */
        fun keyName(key: TtyKey): String? = when (key) {
            is TtyKey.Typed -> key.char.toString()
            TtyKey.Named.ENTER -> "enter"
            TtyKey.Named.ESCAPE -> "escape"
            TtyKey.Named.SPACE -> "space"
            TtyKey.Named.UP -> "up"
            TtyKey.Named.DOWN -> "down"
            TtyKey.Named.LEFT -> "left"
            TtyKey.Named.RIGHT -> "right"
            TtyKey.Named.TAB -> "tab"
            TtyKey.Named.DELETE -> "delete"
            else -> null
        }
    }
}

/**
 * Draws what a script drew ([ScreenFrame]) on the grid. Only here are the widths of its characters
 * known. What is off the grid is cut off ([CellBuffer]), and a wide character at its edge becomes
 * blank. A frame that isn't well formed stops where it goes wrong, and never throws.
 */
object FramePlayer {
    fun play(frame: ScreenFrame, grid: CellBuffer) {
        val ops = frame.ops
        var i = 0
        while (i < ops.size) {
            val count = ScreenFrame.operands(ops[i])
            if (count == 0 || i + count >= ops.size) return
            if (!draw(frame, grid, ops, i)) return
            i += count + 1
        }
    }

    /** One call, whose code is at [i]. False when it isn't one. */
    private fun draw(frame: ScreenFrame, grid: CellBuffer, ops: IntArray, i: Int): Boolean {
        when (ops[i]) {
            ScreenFrame.CLEAR -> grid.clear(style(ops[i + 1]))
            ScreenFrame.PUT -> {
                val text = frame.texts.getOrNull(ops[i + 3]) ?: return false
                put(grid, ops[i + 1], ops[i + 2], text, style(ops[i + 4]))
            }

            ScreenFrame.FILL -> fill(grid, ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4], ops[i + 5], style(ops[i + 6]))
            ScreenFrame.BOX -> box(grid, ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4], style(ops[i + 5]))
            ScreenFrame.BAR -> bar(grid, ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4], style(ops[i + 5]))
            else -> return false
        }
        return true
    }

    /** [text] from [col] on [row]; a line break goes to the same column on the next row. */
    private fun put(grid: CellBuffer, col: Int, row: Int, text: String, style: CellStyle) {
        var r = row
        var at = 0
        while (true) {
            val end = text.indexOf('\n', at)
            grid.put(col, r, text, style, at, if (end < 0) text.length else end)
            if (end < 0) return
            at = end + 1
            r++
        }
    }

    private fun fill(grid: CellBuffer, col: Int, row: Int, w: Int, h: Int, cp: Int, style: CellStyle) {
        // Never more than the grid: the rest is cut off anyway.
        val cols = w.coerceIn(0, grid.cols)
        val rows = h.coerceIn(0, grid.rows)
        when (if (cp > 0) CellWidth.ofCodePoint(cp) else 0) {
            // A wide character: one every two cells.
            2 -> for (r in row until row + rows) for (c in col until col + cols - 1 step 2) grid.put(c, r, cp, style)
            1 -> grid.fill(col, row, cols, rows, if (cp == ' '.code) CellBuffer.EMPTY else cp, style)
            else -> grid.fill(col, row, cols, rows, CellBuffer.EMPTY, style)
        }
    }

    /** A frame of light lines, when it has room for one. */
    private fun box(grid: CellBuffer, col: Int, row: Int, w: Int, h: Int, style: CellStyle) {
        if (w < 2 || h < 2) return
        val right = col + w - 1
        val bottom = row + h - 1
        grid.fill(col + 1, row, w - 2, 1, 0x2500, style)
        grid.fill(col + 1, bottom, w - 2, 1, 0x2500, style)
        grid.fill(col, row + 1, 1, h - 2, 0x2502, style)
        grid.fill(right, row + 1, 1, h - 2, 0x2502, style)
        grid.put(col, row, 0x250C, style)
        grid.put(right, row, 0x2510, style)
        grid.put(col, bottom, 0x2514, style)
        grid.put(right, bottom, 0x2518, style)
    }

    /** [w] cells, [filled] eighths of a cell of them filled from the left. The rest is blank. */
    private fun bar(grid: CellBuffer, col: Int, row: Int, w: Int, filled: Int, style: CellStyle) {
        for (c in maxOf(0, -col) until minOf(w, grid.cols - col)) {
            val eighths = (filled - c * 8).coerceIn(0, 8)
            grid.put(col + c, row, if (eighths == 0) CellBuffer.EMPTY else EIGHTHS[eighths].code, style)
        }
    }

    /** A style of the script's, in the theme's inks. */
    fun style(bits: Int): CellStyle = CellStyle.of(
        ScreenFrame.fg(bits).ink(Ink.FG),
        ScreenFrame.bg(bits).ink(Ink.BG),
        bits and ScreenFrame.FLAG_MASK,
    )

    private fun StyledText.ColorRole?.ink(none: Ink): Ink = if (this == null) none else Ink.of(this)

    /** The left eighths of a cell: ▏ (one eighth) to █ (all of it). */
    private const val EIGHTHS = " ▏▎▍▌▋▊▉█"
}
