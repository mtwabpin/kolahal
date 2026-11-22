package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.screens.ScreenData
import com.mtwabpin.kolahal.terminal.tty.screens.ScreenEnv
import com.mtwabpin.kolahal.terminal.tty.screens.TopData
import com.mtwabpin.kolahal.terminal.tty.screens.TopScreen
import com.mtwabpin.kolahal.terminal.tty.screens.TopScript
import com.mtwabpin.kolahal.terminal.tty.screens.TopSource
import com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.random.Random

/** `top`: its header, its meters and its table of scripts, from facts a test sets. */
class TopScreenTest {
    private val noon = ZonedDateTime.of(2026, 11, 21, 12, 34, 56, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val mb = 1024L * 1024
    private val gb = 1024L * mb

    private val scripts = listOf(
        TopScript("weather", lastRun = noon - 12_000, result = "ok", intervalMin = 15),
        TopScript("aqi", lastRun = noon - 4 * minute, result = "error: HTTP 500 (line 3)", intervalMin = 30),
        TopScript("backup", lastRun = noon - 3 * hour, result = "ok", atTimes = listOf("21:00")),
        TopScript("news", atTimes = listOf("13:00")),
        // Never ran and has no timer: nothing to show.
        TopScript("idle"),
        TopScript("old", enabled = false, lastRun = noon - 50 * hour, result = "timeout", intervalMin = 60),
    )

    private val facts = TopData(
        uptimeMs = 2 * hour + 10 * minute + 5_000,
        batteryPercent = 76,
        memTotal = (7.6 * gb).toLong(),
        memAvailable = (2.1 * gb).toLong(),
        ownResident = 182 * mb,
        heapUsed = 34 * mb,
        heapMax = 256 * mb,
        usageAccess = true,
        screenTimeMs = 3 * hour + 12 * minute,
        unlocks = 41,
        scripts = scripts,
    )

    private class Feed(var data: TopData) : TopSource {
        var reads = 0
        override fun read(): TopData {
            reads++
            return data
        }
    }

    private class Run(val grid: CellBuffer, val screen: TopScreen, val feed: Feed, var now: Long) {
        fun frame(atMs: Long = 0) = screen.frame(grid, atMs, 500)
        fun row(r: Int) = grid.rowText(r)
    }

    private fun start(cols: Int, rows: Int, data: TopData = facts, clock24: Boolean = true): Run {
        val feed = Feed(data)
        val grid = CellBuffer(cols, rows)
        val screen = TopScreen()
        lateinit var run: Run
        screen.start(
            ScreenEnv(cols, rows, Random(1), { run.now }, clock24, ScreenData(top = feed), ZoneOffset.UTC, Locale.UK)
        )
        run = Run(grid, screen, feed, noon)
        run.frame()
        return run
    }

    /** One table row at 45 columns: the name, LAST, RESULT and NEXT in their columns. */
    private fun tableRow(name: String, last: String, result: String, next: String) =
        " " + name.padEnd(25) + " " + last.padStart(4) + "  " + result.padEnd(7) + " " + next.padStart(4)

    private fun assertWhole(name: String, grid: CellBuffer) {
        val rows = grid.dump().split('\n')
        assertEquals(name, grid.rows, rows.size)
        rows.forEach { assertEquals("$name: '$it'", grid.cols, CellWidth.total(it)) }
    }

    @Test
    fun aPhoneSizedTop() {
        val run = start(45, 20)
        assertEquals("top - 12:34:56 up 2:10, 41 unlocks", run.row(0).trimEnd())
        // Each meter: the label, a 15-cell bar, then its numbers.
        assertEquals("Battery ", run.row(1).substring(0, 8))
        assertEquals("76% on battery", run.row(1).substring(24).trimEnd())
        assertEquals("2.1G free of 7.6G", run.row(2).substring(24).trimEnd())
        assertTrue(run.row(2).startsWith("Memory  ▕"))
        assertEquals("182M, heap 34M/256M", run.row(3).substring(24).trimEnd())
        assertEquals("3h 12m on screen", run.row(4).substring(24).trimEnd())
        assertEquals("", run.row(5).trimEnd())
        assertEquals(" SCRIPT" + " ".repeat(20) + "LAST  RESULT  NEXT", run.row(6))
        assertEquals(tableRow("weather", "12s", "ok", "14m"), run.row(7))
        assertEquals(tableRow("aqi", "4m", "error", "26m"), run.row(8))
        assertEquals(tableRow("backup", "3h", "ok", "8h"), run.row(9))
        assertEquals(tableRow("old", "2d", "timeout", "off"), run.row(10))
        // Never ran: last after those that did, with its daily time.
        assertEquals(tableRow("news", "-", "-", "25m"), run.row(11))
        assertEquals("", run.row(12).trimEnd())
        assertWhole("45×20", run.grid)
    }

    /** The table's header row is inverse across the whole width, like top's. */
    @Test
    fun theTableHeaderIsInverse() {
        val run = start(45, 20)
        for (c in 0 until 45) assertTrue("column $c", run.grid.styleAt(c, 6).has(CellStyle.INVERSE))
        assertFalse(run.grid.styleAt(0, 7).has(CellStyle.INVERSE))
        assertEquals(Ink.ERROR, run.grid.styleAt(33, 8).fg)
        assertEquals(Ink.GOOD, run.grid.styleAt(33, 7).fg)
        assertEquals(Ink.WARN, run.grid.styleAt(33, 10).fg)
    }

    /** A bar is filled in eighths of a cell between muted ends: 76 % of 13 cells is 9 cells and 7 eighths. */
    @Test
    fun barsAreInEighths() {
        val run = start(45, 20)
        val g = run.grid
        assertEquals(0x2595, g.glyphAt(8, 1))
        assertEquals(0x258F, g.glyphAt(22, 1))
        assertEquals(Ink.DIM, g.styleAt(8, 1).fg)
        for (c in 9..17) assertEquals("column $c", 0x2588, g.glyphAt(c, 1))
        assertEquals(0x2589, g.glyphAt(18, 1))
        for (c in 19..21) assertEquals("column $c", CellBuffer.EMPTY, g.glyphAt(c, 1))
        assertEquals(Ink.PROMPT, g.styleAt(9, 1).fg)
        // Charging is green; low and on battery, red.
        assertEquals(Ink.GOOD, start(45, 20, facts.copy(charging = true)).grid.styleAt(9, 1).fg)
        assertEquals(Ink.ERROR, start(45, 20, facts.copy(batteryPercent = 9)).grid.styleAt(9, 1).fg)
        assertEquals("100% full", start(45, 20, facts.copy(batteryPercent = 100, charging = true)).row(1).substring(24).trimEnd())
    }

    /** A script that runs while top is open moves to the top at the next second; nothing changes within one. */
    @Test
    fun rowsSortAgainEachSecond() {
        val run = start(45, 20)
        assertTrue(run.row(11).startsWith(" news "))
        run.feed.data = facts.copy(scripts = scripts.map { if (it.name == "news") it.copy(lastRun = noon, result = "ok") else it })
        // The same second: not read again, no cell changed.
        run.grid.dirty.clear()
        run.now = noon + 400
        run.frame(400)
        assertEquals(1, run.feed.reads)
        assertTrue(run.grid.dirty.isEmpty)
        // The next second: news ran now, so it is first, and the others move down a row.
        run.now = noon + 1_000
        run.frame(1_000)
        assertEquals(2, run.feed.reads)
        assertEquals(tableRow("news", "1s", "ok", "25m"), run.row(7))
        assertEquals(tableRow("weather", "13s", "ok", "14m"), run.row(8))
        assertEquals("top - 12:34:57 up 2:10, 41 unlocks", run.row(0).trimEnd())
    }

    /** Narrow, the bars go and the columns go from the right; tiny, it still keeps to its grid. */
    @Test
    fun itFitsSmallAndLarge() {
        for ((cols, rows) in listOf(1 to 1, 8 to 3, 20 to 10, 24 to 12, 30 to 8, 45 to 38, 80 to 24, 200 to 60)) {
            assertWhole("$cols×$rows", start(cols, rows).grid)
            assertWhole("$cols×$rows without facts", start(cols, rows, TopData()).grid)
        }
        val narrow = start(20, 12)
        assertEquals("Battery 76% on batte", narrow.row(1))
        assertEquals(" SCRIPT         LAST", narrow.row(6))
        assertEquals(" weather         12s", narrow.row(7))
        val wide = start(200, 60)
        // The bar stops growing at 40 cells.
        assertEquals("76% on battery", wide.row(1).substring(8 + 40 + 1).trimEnd())
        assertTrue(wide.row(7).trimEnd().endsWith("14m"))
        assertEquals(TopScreen.Columns(15, 16, -1, -1), TopScreen.Columns.of(20))
        assertEquals(TopScreen.Columns(9, 10, 16, -1), TopScreen.Columns.of(23))
        assertEquals(TopScreen.Columns(10, 11, 17, 25), TopScreen.Columns.of(29))
    }

    @Test
    fun withoutUsageAccessALineSaysSo() {
        val run = start(45, 20, facts.copy(usageAccess = false, screenTimeMs = -1))
        assertEquals("Today   no usage access (type usage-access)", run.row(4).trimEnd())
        // Unlocks are counted without it.
        assertEquals("top - 12:34:56 up 2:10, 41 unlocks", run.row(0).trimEnd())
    }

    @Test
    fun unknownFactsSayNotKnown() {
        val run = start(45, 20, TopData(usageAccess = true))
        assertEquals("top - 12:34:56", run.row(0).trimEnd())
        assertEquals("not known", run.row(1).substring(24).trimEnd())
        assertEquals("not known", run.row(2).substring(24).trimEnd())
        assertEquals("?", run.row(3).substring(24).trimEnd())
        assertEquals("reading…", run.row(4).substring(24).trimEnd())
        assertEquals(" No script has run yet", run.row(7).trimEnd())
        // An empty bar: its ends only.
        assertEquals("▕" + " ".repeat(13) + "▏", run.row(1).substring(8, 23))
    }

    @Test
    fun twelveHourTime() {
        val run = start(45, 20, clock24 = false)
        assertEquals("top - 12:34:56 PM up 2:10, 41 unlocks", run.row(0).trimEnd())
    }

    @Test
    fun theWordsAndNumbers() {
        assertEquals("0s", TopScreen.age(-5))
        assertEquals("59s", TopScreen.age(59_999))
        assertEquals("1m", TopScreen.age(60_000))
        assertEquals("23h", TopScreen.age(24 * hour - 1))
        assertEquals("2d", TopScreen.age(50 * hour))
        assertEquals("999d", TopScreen.age(5_000L * 24 * hour))
        assertEquals("12 min", TopScreen.uptime(12 * minute + 59_000))
        assertEquals("2:05", TopScreen.uptime(2 * hour + 5 * minute))
        assertEquals("1 day, 0:00", TopScreen.uptime(24 * hour))
        assertEquals("3 days, 4:30", TopScreen.uptime(76 * hour + 30 * minute))
        assertEquals("512K", TopScreen.bytes(512 * 1024))
        assertEquals("182M", TopScreen.bytes(182 * mb))
        assertEquals("7.6G", TopScreen.bytes((7.6 * gb).toLong()))
        assertEquals("3h 12m", TopScreen.duration(3 * hour + 12 * minute))
        assertEquals("45m", TopScreen.duration(45 * minute))
        assertEquals("ok", TopScreen.resultWord("ok", true))
        assertEquals("error", TopScreen.resultWord("error: boom", true))
        assertEquals("stopped", TopScreen.resultWord("stopped: location off", true))
        assertEquals("timeout", TopScreen.resultWord("timeout", true))
        assertEquals("-", TopScreen.resultWord(null, false))
        assertEquals(0.75f, TopScreen.used(25, 100), 0.0001f)
        assertEquals(-1f, TopScreen.used(25, -1), 0f)
    }

    /** The next run: the sooner of the next daily time and one interval after the last run; never while off. */
    @Test
    fun nextRuns() {
        val utc = ZoneOffset.UTC
        assertEquals(noon + 25 * minute + 4_000, TopScreen.nextRun(TopScript("a", atTimes = listOf("13:00", "21:00")), noon, utc))
        // 09:00 has passed today: tomorrow's.
        assertEquals(noon + 20 * hour + 25 * minute + 4_000, TopScreen.nextRun(TopScript("a", atTimes = listOf("09:00")), noon, utc))
        assertEquals(noon + 5 * minute, TopScreen.nextRun(TopScript("a", lastRun = noon - 10 * minute, intervalMin = 15), noon, utc))
        // Overdue: now.
        assertEquals(noon, TopScreen.nextRun(TopScript("a", lastRun = noon - hour, intervalMin = 15), noon, utc))
        assertEquals(noon + 5 * minute, TopScreen.nextRun(TopScript("a", lastRun = noon - 10 * minute, intervalMin = 15, atTimes = listOf("21:00")), noon, utc))
        assertNull(TopScreen.nextRun(TopScript("a", enabled = false, intervalMin = 15), noon, utc))
        assertNull(TopScreen.nextRun(TopScript("a", lastRun = noon), noon, utc))
        assertNull(TopScreen.nextRun(TopScript("a", atTimes = listOf("not a time")), noon, utc))
    }

    @Test
    fun theOrder() {
        assertEquals(
            listOf("weather", "aqi", "backup", "old", "news"),
            TopScreen.order(scripts, noon, ZoneOffset.UTC).map { it.name },
        )
    }

    /** `top` is a screen of its own, reached through `screen top`, and it keeps going with animations off. */
    @Test
    fun itIsAScreen() {
        assertTrue("top" in TtyScreens.names)
        val top = TtyScreens.named("top")!!
        assertEquals("top", top.name)
        assertFalse(top.animated)
        assertTrue(top.fps in 1..4)
    }
}
