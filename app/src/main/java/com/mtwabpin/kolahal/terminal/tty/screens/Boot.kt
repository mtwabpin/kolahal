/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle

/**
 * `boot`: a short startup sequence in the style of a Linux boot log, typed out line by line, with
 * real counts from this phone ([ScreenData]): apps, scripts, the theme, the font, today's unlocks.
 * It takes [TYPE_MS] to type and stays [HOLD_MS] more, 1.2 s in all, then closes by itself.
 */
class BootScreen : TtyScreen {
    override val name = "boot"
    override val title = "Startup sequence"
    override val fps = 60

    private var cols = 0
    private var rows = 0
    private var lines: List<String> = emptyList()
    private var total = 0

    override fun start(env: ScreenEnv) {
        lines = bootLines(env.data)
        total = lines.sumOf { it.length }
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols
        this.rows = rows
    }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        val typed = if (timeMs >= TYPE_MS) total else (total * timeMs / TYPE_MS).toInt()
        draw(grid, typed)
        return timeMs < TYPE_MS + HOLD_MS
    }

    /** The first [typed] characters of the sequence; a line gets its OK once it is all there. */
    private fun draw(grid: CellBuffer, typed: Int) {
        // A short screen shows the last lines, as a boot log scrolls.
        var left = typed
        var shown = 0
        while (shown < lines.size && left > 0) left -= lines[shown++].length
        val first = (shown - rows).coerceAtLeast(0)
        left = typed
        for (i in 0 until shown) {
            val line = lines[i]
            val count = minOf(left, line.length)
            left -= line.length
            if (i < first) continue
            val row = i - first
            if (i == 0) {
                grid.put(0, row, line, HEADER, end = count)
                continue
            }
            val done = count == line.length
            grid.put(0, row, if (done) "[" else " ", PLAIN)
            grid.put(1, row, if (done) "  OK  " else "      ", OK)
            grid.put(7, row, if (done) "]" else " ", PLAIN)
            grid.put(9, row, line, PLAIN, end = count)
        }
    }

    companion object {
        const val TYPE_MS = 900L
        const val HOLD_MS = 300L

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
