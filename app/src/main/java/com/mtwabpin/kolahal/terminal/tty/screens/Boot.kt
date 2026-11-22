/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import com.mtwabpin.kolahal.terminal.tty.Decode
import kotlin.random.Random

/**
 * `boot`: a short startup sequence in the style of a Linux boot log, typed out line by line, with
 * real counts from this phone ([ScreenData]): apps, scripts, the theme, the font, today's unlocks.
 * Each character passes through a few others as it comes ([Decode]). It takes [TYPE_MS] to type and
 * stays [HOLD_MS] more, 1.2 s in all, then closes by itself.
 */
class BootScreen : TtyScreen {
    override val name = "boot"
    override val title = "Startup sequence"
    override val fps = 60

    private var rows = 0
    private var lines: List<String> = emptyList()
    private var random: Random = Random(0)
    private var decode = Decode(1, 1)

    /** When each line's first character comes. */
    private var lineAt = IntArray(0)

    override fun start(env: ScreenEnv) {
        lines = bootLines(env.data)
        random = env.random
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.rows = rows
        decode = Decode(cols, lines.size)
        for ((row, line) in lines.withIndex()) {
            if (row > 0) {
                decode.target.put(0, row, "[", PLAIN)
                decode.target.put(1, row, "  OK  ", OK)
                decode.target.put(7, row, "]", PLAIN)
            }
            decode.target.put(textFrom(row), row, line, if (row == 0) HEADER else PLAIN)
        }
        decode.scan()
        decode.seed = random.nextInt()
        val total = lines.sumOf { it.length }
        lineAt = IntArray(lines.size)
        var typed = 0
        for ((row, line) in lines.withIndex()) {
            lineAt[row] = at(typed + 1, total)
            var col = textFrom(row)
            var i = 0
            while (i < line.length) {
                val end = CellWidth.clusterEnd(line, i)
                cell(col, row, at(typed + end, total))
                col += CellWidth.width(line, i, end)
                i = end
            }
            typed += line.length
            // A line gets its OK once it is all there.
            if (row > 0) for (c in 0 until TAG_CELLS) cell(c, row, at(typed, total))
        }
    }

    /** When the sequence's [n]th character of [total] comes. */
    private fun at(n: Int, total: Int): Int = (TYPE_MS * n / total).toInt()

    /** The first column of a line's text: the first line has no OK before it. */
    private fun textFrom(row: Int) = if (row == 0) 0 else TAG_CELLS + 1

    private fun cell(col: Int, row: Int, at: Int) = decode.cell(col, row, at, at + SETTLE_MS + random.nextInt(SETTLE_MORE_MS))

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        val t = timeMs.toInt()
        // A short screen shows the last lines, as a boot log scrolls.
        val first = (lineAt.count { it <= t } - rows).coerceAtLeast(0)
        decode.frame(grid, t, all = true, hidden = true, first = first)
        return timeMs < TYPE_MS + HOLD_MS
    }

    companion object {
        const val TYPE_MS = 900L
        const val HOLD_MS = 300L

        /** A character settles this long after it comes, and up to [SETTLE_MORE_MS] more: before the sequence ends. */
        private const val SETTLE_MS = 80
        private const val SETTLE_MORE_MS = 120

        /** The cells of `[  OK  ]`. */
        private const val TAG_CELLS = 8

        private val HEADER = CellStyle.of(Ink.FG, flags = CellStyle.BOLD)
        private val PLAIN = CellStyle.of(Ink.FG)
        private val OK = CellStyle.of(Ink.GOOD, flags = CellStyle.BOLD)

        /** The sequence: a first line, then one line per fact that is known. */
        fun bootLines(d: ScreenData): List<String> = buildList {
            add(
                "Starting Kolahal" + (if (d.version.isNotEmpty()) " ${d.version}" else "") +
                        (if (d.android.isNotEmpty()) " on ${d.android}" else "")
            )
            if (d.apps > 0) {
                val others = listOfNotNull(
                    "${d.workApps} work".takeIf { d.workApps > 0 },
                    "${d.privateApps} private".takeIf { d.privateApps > 0 },
                )
                add("Loaded ${count(d.apps, "app")}" + if (others.isEmpty()) "" else " (${others.joinToString(", ")})")
            }
            if (d.scripts > 0) add("Loaded ${count(d.scripts, "script")} (${d.scriptsOn} on)")
            if (d.theme.isNotEmpty()) add("Applied theme ${d.theme} (${if (d.dark) "dark" else "light"})")
            if (d.font.isNotEmpty()) add("Set font ${d.font}" + if (d.fontSize > 0) ", ${d.fontSize} sp" else "")
            if (d.unlocks > 0) add("Counted ${count(d.unlocks, "unlock")} today")
            add("Reached the prompt")
        }

        private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
    }
}
