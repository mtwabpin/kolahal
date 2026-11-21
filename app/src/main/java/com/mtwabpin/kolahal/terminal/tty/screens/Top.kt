/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import com.mtwabpin.kolahal.terminal.tty.cut
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What `top` shows at one moment: facts Kolahal has or reads cheaply, without new permissions.
 * A number that isn't known is -1, and its line says so or leaves it out.
 * - [uptimeMs]: how long Kolahal's process has run.
 * - [batteryPercent], [charging]: the battery, as the header shows it.
 * - [memTotal], [memAvailable], [lowMemory]: the phone's memory, in bytes (Android's MemoryInfo).
 * - [ownResident]: Kolahal's own memory in RAM; [heapUsed] of [heapMax]: its Java heap.
 * - [usageAccess]: whether Kolahal may read usage data. Without it, [screenTimeMs] is -1.
 * - [screenTimeMs]: today's screen time since midnight; [unlocks]: today's unlocks.
 * - [scripts]: every script, with its last run and its timers.
 */
data class TopData(
    val uptimeMs: Long = -1,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val memTotal: Long = -1,
    val memAvailable: Long = -1,
    val lowMemory: Boolean = false,
    val ownResident: Long = -1,
    val heapUsed: Long = -1,
    val heapMax: Long = -1,
    val usageAccess: Boolean = false,
    val screenTimeMs: Long = -1,
    val unlocks: Int = -1,
    val scripts: List<TopScript> = emptyList(),
)

/**
 * A script as `top` lists it: when it last ran ([lastRun], ms since 1970, null for never) and how
 * ([result], the status the script's card keeps), and its timers: every [intervalMin] minutes
 * (0 for none) and each daily time in [atTimes] ("HH:mm"). A switched-off script has no next run.
 */
data class TopScript(
    val name: String,
    val enabled: Boolean = true,
    val lastRun: Long? = null,
    val result: String? = null,
    val intervalMin: Int = 0,
    val atTimes: List<String> = emptyList(),
)

/**
 * Where `top` reads its facts, about once a second, on the main thread. So [read] must be quick:
 * it returns what is already known, and anything slow (today's screen time) is worked out
 * elsewhere and only read here.
 */
fun interface TopSource {
    fun read(): TopData

    companion object {
        /** No facts: every line says it doesn't know. */
        val NONE = TopSource { TopData() }
    }
}

/**
 * `top` (through `screen top`): a live view of this phone and Kolahal in the style of the Unix
 * `top`, refreshed each second.
 * - A header line: the time, how long Kolahal has run, and today's unlocks.
 * - Meters with bars in block eighths: the battery, the phone's memory in use, Kolahal's memory
 *   (its bar is the Java heap, the part that can run out) and today's screen time (its bar is the
 *   part of the day so far). Without usage access, a line says how to allow it.
 * - A table of scripts under an inverse header, like top's processes: when each last ran, how it
 *   ended and when it runs next by itself. The latest run is first, so a script that runs while
 *   `top` is open moves to the top. Scripts that never ran come last, the soonest timer first.
 *
 * It shows information, not motion, so it keeps going with "Remove animations" on. Any key or tap
 * leaves it, as for the other screens. Time comes from [ScreenEnv.clock] and the facts from
 * [ScreenData.top], so a test can set both.
 */
class TopScreen : TtyScreen {
    override val name = "top"
    override val title = "Top: battery, memory and scripts"

    // Twice a second, so the clock changes close to each second; a frame in the same second as
    // the last one changes nothing.
    override val fps = 2
    override val animated = false

    private lateinit var env: ScreenEnv
    private var cols = 0
    private var rows = 0
    private var shown = Long.MIN_VALUE

    override fun start(env: ScreenEnv) {
        this.env = env
        resize(env.cols, env.rows)
    }

    override fun resize(cols: Int, rows: Int) {
        this.cols = cols.coerceAtLeast(1)
        this.rows = rows.coerceAtLeast(1)
        shown = Long.MIN_VALUE
    }

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        val now = env.clock()
        val second = Math.floorDiv(now, 1_000L)
        if (second == shown) return true
        shown = second
        draw(grid, env.data.top.read(), now)
        return true
    }

    private fun draw(grid: CellBuffer, d: TopData, now: Long) {
        grid.clear()
        var row = 0
        grid.put(0, row++, header(d, now), PLAIN)
        // The meters: a label, a bar, then the numbers. Narrow, the bar goes and the numbers stay.
        val bar = (cols - LABEL - METER_TEXT).coerceAtMost(MAX_BAR).let { if (it < MIN_BAR) 0 else it }
        row = meter(grid, row, "Battery", battery(d), d.batteryPercent / 100f, batteryInk(d), bar)
        row = meter(grid, row, "Memory", memory(d), used(d.memAvailable, d.memTotal), memoryInk(d), bar)
        row = meter(grid, row, "Kolahal", own(d), fraction(d.heapUsed, d.heapMax), Ink.INFO, bar)
        row = if (d.usageAccess) {
            // The bar: the part of the day so far spent on the screen.
            val sinceMidnight = now - startOfDay(now)
            meter(grid, row, "Today", today(d), fraction(d.screenTimeMs, sinceMidnight), Ink.SCRIPT, bar)
        } else {
            grid.put(0, row, "Today", LABEL_STYLE)
            grid.put(LABEL, row, "no usage access (type usage-access)", DIM)
            row + 1
        }
        if (row + 1 < rows) table(grid, row + 1, d.scripts, now)
    }

    /** `top - 14:03:12 up 2:10, 41 unlocks`: Unix top's first line, with Kolahal's time up. */
    private fun header(d: TopData, now: Long): String = buildString {
        append("top - ").append(clock(time(now), env.clock24))
        if (d.uptimeMs >= 0) append(" up ").append(uptime(d.uptimeMs))
        if (d.unlocks >= 0) append(", ").append(count(d.unlocks, "unlock"))
    }

    /** One meter row: [label], a bar [bar] cells wide filled to [fill] (none if negative), then [text]. */
    private fun meter(grid: CellBuffer, row: Int, label: String, text: String, fill: Float, ink: Ink, bar: Int): Int {
        if (row >= rows) return row
        grid.put(0, row, label, LABEL_STYLE)
        var col = LABEL
        if (bar > 0) {
            track(grid, col, row, bar, fill, CellStyle.of(ink))
            col += bar + 1
        }
        grid.put(col, row, text, PLAIN)
        return row + 1
    }

    /**
     * A bar's track `▕████▌   ▏` from [col], [width] cells with its ends, filled to [fraction] in
     * eighths of a cell. A negative fraction (not known) leaves it empty.
     */
    private fun track(grid: CellBuffer, col: Int, row: Int, width: Int, fraction: Float, fill: CellStyle) {
        val inner = width - 2
        if (inner < 1) return
        grid.put(col, row, RIGHT_EIGHTH, DIM)
        grid.put(col + width - 1, row, LEFT_EIGHTH, DIM)
        if (fraction < 0f) return
        val total = (fraction.coerceAtMost(1f) * inner * 8).roundToInt()
        for (c in 0 until inner) {
            val eighths = (total - c * 8).coerceIn(0, 8)
            if (eighths > 0) grid.put(col + 1 + c, row, FULL_BLOCK + 8 - eighths, fill)
        }
    }

    private fun battery(d: TopData): String = when {
        d.batteryPercent < 0 -> "not known"
        d.charging && d.batteryPercent >= 100 -> "100% full"
        d.charging -> "${d.batteryPercent}% charging"
        else -> "${d.batteryPercent}% on battery"
    }

    private fun batteryInk(d: TopData): Ink = when {
        d.charging -> Ink.GOOD
        d.batteryPercent in 0..15 -> Ink.ERROR
        d.batteryPercent in 0..30 -> Ink.WARN
        else -> Ink.PROMPT
    }

    private fun memory(d: TopData): String =
        if (d.memTotal <= 0 || d.memAvailable < 0) "not known"
        else "${bytes(d.memAvailable)} free of ${bytes(d.memTotal)}" + if (d.lowMemory) ", low" else ""

    private fun memoryInk(d: TopData): Ink = when {
        d.lowMemory -> Ink.ERROR
        used(d.memAvailable, d.memTotal) > 0.9f -> Ink.WARN
        else -> Ink.PROMPT
    }

    private fun own(d: TopData): String = buildString {
        append(if (d.ownResident >= 0) bytes(d.ownResident) else "?")
        if (d.heapMax > 0 && d.heapUsed >= 0) append(", heap ").append(bytes(d.heapUsed)).append('/').append(bytes(d.heapMax))
    }

    private fun today(d: TopData): String =
        if (d.screenTimeMs < 0) "reading…" else "${duration(d.screenTimeMs)} on screen"

    /**
     * The scripts under an inverse header row from [top] down to the last row: as many as fit, the
     * latest run first. The columns that don't fit go, from the right: NEXT, then RESULT, then LAST.
     */
    private fun table(grid: CellBuffer, top: Int, scripts: List<TopScript>, now: Long) {
        val c = Columns.of(cols)
        grid.fill(0, top, cols, 1, CellBuffer.EMPTY, HEAD)
        grid.put(1, top, cut("SCRIPT", c.nameEnd - 1), HEAD)
        if (c.last >= 0) right(grid, c.last, LAST_W, top, "LAST", HEAD)
        if (c.result >= 0) grid.put(c.result, top, "RESULT", HEAD)
        if (c.next >= 0) right(grid, c.next, NEXT_W, top, "NEXT", HEAD)
        val rowsLeft = rows - top - 1
        if (rowsLeft <= 0) return
        val listed = order(scripts, now, env.zone)
        if (listed.isEmpty()) {
            grid.put(1, top + 1, "No script has run yet", DIM)
            return
        }
        for ((i, s) in listed.withIndex()) {
            if (i >= rowsLeft) break
            val row = top + 1 + i
            putCut(grid, 1, c.nameEnd, row, s.name, if (s.enabled) PLAIN else DIM)
            if (c.last >= 0) right(grid, c.last, LAST_W, row, s.lastRun?.let { age(now - it) } ?: "-", DIM)
            if (c.result >= 0) {
                val word = resultWord(s.result, s.lastRun != null)
                grid.put(c.result, row, word, CellStyle.of(resultInk(word)))
            }
            if (c.next >= 0) {
                val next = nextRun(s, now, env.zone)
                val text = when {
                    !s.enabled -> "off"
                    next == null -> "-"
                    else -> age(next - now)
                }
                right(grid, c.next, NEXT_W, row, text, DIM)
            }
        }
    }

    /**
     * Where the table's columns start on a grid [cols] wide; -1 for a column left out. The name
     * runs from column 1 to [nameEnd] (exclusive). The others go from the right, so the name keeps
     * at least [NAME_MIN] cells: NEXT first, then RESULT, then LAST.
     */
    internal data class Columns(val nameEnd: Int, val last: Int, val result: Int, val next: Int) {
        companion object {
            fun of(cols: Int): Columns {
                // All three: name, LAST, two spaces, RESULT, a space, NEXT.
                val all = cols - (LAST_W + 2 + RESULT_W + 1 + NEXT_W)
                if (all - 2 >= NAME_MIN) return Columns(all - 1, all, all + LAST_W + 2, cols - NEXT_W)
                val two = cols - (LAST_W + 2 + RESULT_W)
                if (two - 2 >= NAME_MIN) return Columns(two - 1, two, cols - RESULT_W, -1)
                val one = cols - LAST_W
                if (one - 2 >= NAME_MIN) return Columns(one - 1, one, -1, -1)
                return Columns(cols, -1, -1, -1)
            }
        }
    }

    /** [text] right-aligned in [width] cells from [col]. */
    private fun right(grid: CellBuffer, col: Int, width: Int, row: Int, text: String, style: CellStyle) {
        grid.put(col + (width - CellWidth.total(text)).coerceAtLeast(0), row, text, style)
    }

    /** [text] from [col], cut with … so it ends before [end]. */
    private fun putCut(grid: CellBuffer, col: Int, end: Int, row: Int, text: String, style: CellStyle) {
        if (end - col > 0) grid.put(col, row, cut(text, end - col), style)
    }

    private fun time(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(env.zone)

    private fun startOfDay(now: Long): Long =
        time(now).toLocalDate().atStartOfDay(env.zone).toInstant().toEpochMilli()

    companion object {
        /** Cells for a meter's label ("Battery", "Memory"…) and the space after it. */
        private const val LABEL = 8

        /** Cells kept for a meter's numbers after its bar: "2.1G free of 7.6G, low". */
        private const val METER_TEXT = 22
        private const val MIN_BAR = 6
        private const val MAX_BAR = 40

        /** The fewest cells a script's name gets before a column goes. */
        private const val NAME_MIN = 8
        private const val LAST_W = 4
        private const val RESULT_W = 7
        private const val NEXT_W = 4

        private const val FULL_BLOCK = 0x2588
        private const val LEFT_EIGHTH = 0x258F
        private const val RIGHT_EIGHTH = 0x2595

        private val PLAIN = CellStyle.of(Ink.FG)
        private val DIM = CellStyle.of(Ink.DIM)
        private val LABEL_STYLE = CellStyle.of(Ink.FG, flags = CellStyle.BOLD)
        private val HEAD = CellStyle.of(Ink.FG, flags = CellStyle.INVERSE or CellStyle.BOLD)

        /**
         * The scripts the table lists, in its order: those that ran, the latest first; then those
         * that never ran but have a timer, the soonest first. A script that never ran and has no
         * timer has nothing to show.
         */
        fun order(scripts: List<TopScript>, now: Long, zone: ZoneId): List<TopScript> {
            val ran = scripts.filter { it.lastRun != null }
                .sortedWith(compareByDescending<TopScript> { it.lastRun }.thenBy { it.name.lowercase() })
            val waiting = scripts.filter { it.lastRun == null }
                .mapNotNull { s -> nextRun(s, now, zone)?.let { s to it } }
                .sortedWith(compareBy<Pair<TopScript, Long>> { it.second }.thenBy { it.first.name.lowercase() })
                .map { it.first }
            return ran + waiting
        }

        /**
         * When [s] next runs by itself after [now]: its next daily time, or about one interval after
         * its last run, whichever comes first. Null when it is switched off or has no timer.
         */
        fun nextRun(s: TopScript, now: Long, zone: ZoneId): Long? {
            if (!s.enabled) return null
            val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            val daily = s.atTimes.mapNotNull { hhmm ->
                val t = runCatching { LocalTime.parse(hhmm.trim()) }.getOrNull() ?: return@mapNotNull null
                var next = at.toLocalDate().atTime(t).atZone(zone)
                if (!next.isAfter(at)) next = at.toLocalDate().plusDays(1).atTime(t).atZone(zone)
                next.toInstant().toEpochMilli()
            }.minOrNull()
            val every = if (s.intervalMin > 0) maxOf(now, (s.lastRun ?: now) + s.intervalMin * 60_000L) else null
            return listOfNotNull(daily, every).minOrNull()
        }

        /** A run's result in a word: ok, error, stopped or timeout; "-" for a script that never ran. */
        fun resultWord(result: String?, ran: Boolean): String = when {
            !ran && result == null -> "-"
            result == null -> "ok"
            result.startsWith("error") -> "error"
            result.startsWith("stopped") -> "stopped"
            result == "timeout" -> "timeout"
            else -> "ok"
        }

        private fun resultInk(word: String): Ink = when (word) {
            "ok" -> Ink.GOOD
            "error" -> Ink.ERROR
            "stopped", "timeout" -> Ink.WARN
            else -> Ink.DIM
        }

        /** A span as top's columns show it, in at most 4 cells: 45s, 12m, 3h, 2d (0s for none or less). */
        fun age(ms: Long): String {
            val s = (ms / 1_000L).coerceAtLeast(0L)
            return when {
                s < 60 -> "${s}s"
                s < 3_600 -> "${s / 60}m"
                s < 86_400 -> "${s / 3_600}h"
                s < 86_400L * 1_000 -> "${s / 86_400}d"
                else -> "999d"
            }
        }

        /** The time with seconds: 14:03:12, or 2:03:12 PM without [clock24] (as the clock screen says AM and PM). */
        fun clock(t: ZonedDateTime, clock24: Boolean): String {
            if (clock24) return "%02d:%02d:%02d".format(Locale.ROOT, t.hour, t.minute, t.second)
            val hour = (t.hour % 12).let { if (it == 0) 12 else it }
            return "%d:%02d:%02d %s".format(Locale.ROOT, hour, t.minute, t.second, if (t.hour >= 12) "PM" else "AM")
        }

        /** How long Kolahal has run, as top's "up": 12 min, 2:10, 3 days, 2:10. */
        fun uptime(ms: Long): String {
            val minutes = ms / 60_000L
            val days = minutes / 1_440
            val hm = "%d:%02d".format(Locale.ROOT, (minutes % 1_440) / 60, minutes % 60)
            return when {
                minutes < 60 -> "$minutes min"
                days == 0L -> hm
                days == 1L -> "1 day, $hm"
                else -> "$days days, $hm"
            }
        }

        /** Screen time as `usage` says it: 3h 12m, 45m. */
        fun duration(ms: Long): String {
            val minutes = (ms / 60_000L).coerceAtLeast(0L)
            return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
        }

        /** Bytes in the units top uses: 512K, 182M, 7.6G. */
        fun bytes(n: Long): String {
            val k = n / 1024.0
            return when {
                k < 1024 -> "${k.roundToInt()}K"
                k < 1024 * 1024 -> "${(k / 1024).roundToInt()}M"
                else -> "%.1fG".format(Locale.ROOT, k / 1024 / 1024)
            }
        }

        /** How full: the part of [total] that isn't [free], or -1 when either isn't known. */
        fun used(free: Long, total: Long): Float =
            if (total <= 0 || free < 0) -1f else (1f - free.toFloat() / total).coerceIn(0f, 1f)

        private fun fraction(part: Long, whole: Long): Float =
            if (part < 0 || whole <= 0) -1f else (part.toFloat() / whole).coerceIn(0f, 1f)

        private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
    }
}
