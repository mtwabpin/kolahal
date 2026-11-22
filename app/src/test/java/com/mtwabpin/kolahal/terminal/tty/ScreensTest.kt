package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.terminal.tty.screens.BootScreen
import com.mtwabpin.kolahal.terminal.tty.screens.ClockScreen
import com.mtwabpin.kolahal.terminal.tty.screens.ScreenData
import com.mtwabpin.kolahal.terminal.tty.screens.ScreenEnv
import com.mtwabpin.kolahal.terminal.tty.screens.SlScreen
import com.mtwabpin.kolahal.terminal.tty.screens.TtyScreen
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

/** The full-screen screens, stepped frame by frame with a seeded chance and a set clock. */
class ScreensTest {
    private val noon = ZonedDateTime.of(2026, 11, 21, 12, 34, 56, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun env(
        cols: Int,
        rows: Int,
        seed: Int = 7,
        now: () -> Long = { noon },
        clock24: Boolean = true,
        data: ScreenData = ScreenData(),
    ) = ScreenEnv(cols, rows, Random(seed), now, clock24, data, ZoneOffset.UTC, Locale.UK)

    /** Starts [screen] on a fresh grid and runs it for up to [ms] at 30 frames a second; returns the grid and whether it is still going. */
    private fun runFor(screen: TtyScreen, env: ScreenEnv, ms: Long): Pair<CellBuffer, Boolean> {
        val grid = CellBuffer(env.cols, env.rows)
        screen.start(env)
        var t = 0L
        var going = true
        while (t < ms && going) {
            t += 33
            going = screen.frame(grid, t, 33)
        }
        return grid to going
    }

    /** Each row of [grid] is exactly as wide as the grid. */
    private fun assertWhole(name: String, grid: CellBuffer) {
        val rows = grid.dump().split('\n')
        assertEquals(name, grid.rows, rows.size)
        rows.forEach { assertEquals("$name: '$it'", grid.cols, CellWidth.total(it)) }
    }

    /**
     * `screen <name>` opens every screen; `matrix`, `pipes` and `sl` are also commands of their own.
     * `clock`, `decode`, `top` and `boot` are not, so typing `clock` still opens the clock app, and "top"
     * stays a plain word.
     */
    @Test
    fun theCommandsAndTheScreensAgree() {
        assertEquals(listOf("screen", "matrix", "pipes", "sl", "shader"), CommandCatalog.HELP_SECTIONS["screens"])
        // shader is in the section too: it draws over the screens and the tty home, it isn't a screen.
        for (name in CommandCatalog.HELP_SECTIONS.getValue("screens") - setOf("screen", "shader")) {
            assertTrue(name, name in TtyScreens.names)
        }
        val commands = CommandCatalog.COMMANDS.map { it.name }.toSet()
        assertTrue("screen" in commands)
        assertFalse("clock" in commands)
        assertFalse("decode" in commands)
        assertFalse("top" in commands)
        assertFalse("boot" in commands)
        // The screen command's help names every screen.
        val help = CommandCatalog.COMMANDS.first { it.name == "screen" }.help
        for (name in TtyScreens.names) assertTrue(name, name in help)
        for (name in TtyScreens.names) assertEquals(name, TtyScreens.named(name)?.name)
        assertNull(TtyScreens.named("ls"))
    }

    /** Every screen runs at a tiny, a phone and a huge size without failing, and keeps to its grid. */
    @Test
    fun everyScreenRunsAtEverySize() {
        for (name in TtyScreens.names) for ((cols, rows) in listOf(1 to 1, 20 to 10, 45 to 38, 200 to 60)) {
            val (grid, _) = runFor(TtyScreens.named(name)!!, env(cols, rows), 10_000)
            assertWhole("$name at $cols×$rows", grid)
        }
    }

    @Test
    fun aScreenGoesOnAfterAResize() {
        for (name in TtyScreens.names) {
            val screen = TtyScreens.named(name)!!
            val (grid, _) = runFor(screen, env(40, 20), 500)
            grid.resize(30, 50)
            screen.resize(30, 50)
            repeat(30) { screen.frame(grid, 500L + it * 33, 33) }
            assertWhole("$name after a resize", grid)
        }
    }

    @Test
    fun theSameSeedGivesTheSameFrames() {
        for (name in listOf("matrix", "pipes")) {
            val a = runFor(TtyScreens.named(name)!!, env(30, 20, seed = 3), 3_000).first.dump()
            val b = runFor(TtyScreens.named(name)!!, env(30, 20, seed = 3), 3_000).first.dump()
            val c = runFor(TtyScreens.named(name)!!, env(30, 20, seed = 4), 3_000).first.dump()
            assertEquals(name, a, b)
            assertTrue(name, a != c)
        }
    }

    @Test
    fun matrixRainsInEveryOtherColumn() {
        val (grid, going) = runFor(TtyScreens.named("matrix")!!, env(30, 20), 3_000)
        assertTrue(going)
        var drawn = 0
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            val g = grid.glyphAt(c, r)
            if (g == CellBuffer.EMPTY) continue
            drawn++
            assertEquals("column $c", 0, c % 2)
            assertTrue(g in 0xFF66..0xFF9D || g in 0x21..0x7E)
        }
        assertTrue("only $drawn cells", drawn > 30)
    }

    @Test
    fun pipesAreHeavyLines() {
        val (grid, _) = runFor(TtyScreens.named("pipes")!!, env(30, 20), 2_000)
        val pieces = setOf(0x2501, 0x2503, 0x250F, 0x2513, 0x2517, 0x251B)
        var drawn = 0
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            val g = grid.glyphAt(c, r)
            if (g == CellBuffer.EMPTY) continue
            drawn++
            assertTrue("U+%04X".format(g), g in pieces)
        }
        assertTrue(drawn > 20)
    }

    @Test
    fun slCrossesOnceAndEnds() {
        for ((cols, rows) in listOf(1 to 1, 20 to 10, 200 to 60)) {
            val screen = SlScreen()
            val (_, going) = runFor(screen, env(cols, rows), 60_000)
            assertFalse("sl at $cols×$rows is still going", going)
        }
        // Part way, the engine is on the screen.
        val (grid, going) = runFor(SlScreen(), env(60, 20), 1_500)
        assertTrue(going)
        assertTrue(grid.dump(), "(O)" in grid.dump())
    }

    @Test
    fun slStandsStillInTheMiddle() {
        val screen = SlScreen()
        val grid = CellBuffer(60, 20)
        screen.start(env(60, 20))
        screen.still(grid)
        assertTrue(grid.dump().lines().any { it.trim().startsWith("\\(O)") })
    }

    @Test
    fun bootTypesItsFactsAndEnds() {
        val data = ScreenData(
            version = "0.5.17", android = "Android 16", apps = 142, workApps = 3, scripts = 12, scriptsOn = 9,
            theme = "green", dark = true, font = "jetbrains-mono", fontSize = 14, unlocks = 1,
        )
        val (grid, going) = runFor(BootScreen(), env(60, 20, data = data), 10_000)
        assertFalse(going)
        val text = grid.dump().lines().map { it.trimEnd() }
        assertEquals("Starting Kolahal 0.5.17 on Android 16", text[0])
        assertEquals("[  OK  ] Loaded 142 apps (3 work)", text[1])
        assertEquals("[  OK  ] Loaded 12 scripts (9 on)", text[2])
        assertEquals("[  OK  ] Applied theme green (dark)", text[3])
        assertEquals("[  OK  ] Set font jetbrains-mono, 14 sp", text[4])
        assertEquals("[  OK  ] Counted 1 unlock today", text[5])
        assertEquals("[  OK  ] Reached the prompt", text[6])
        // It ends right after its 1.2 s.
        val (_, stillGoing) = runFor(BootScreen(), env(60, 20, data = data), BootScreen.TYPE_MS + BootScreen.HOLD_MS - 50)
        assertTrue(stillGoing)
    }

    @Test
    fun bootIsTypedOut() {
        // A third of the way through, the first line is half typed and nothing is OK yet.
        val (grid, going) = runFor(BootScreen(), env(60, 20), 300)
        assertTrue(going)
        val first = grid.rowText(0).trimEnd()
        assertTrue(first, first.isNotEmpty() && first.length < "Starting Kolahal".length)
        assertFalse(grid.dump(), "OK" in grid.dump())
    }

    /** Its characters pass through others first, and all have settled before it ends. On a short screen it scrolls to its last lines. */
    @Test
    fun bootDecodes() {
        val data = ScreenData(version = "0.5.17", apps = 142, scripts = 12, scriptsOn = 9, theme = "green", unlocks = 3)
        val lines = BootScreen.bootLines(data)
        val full = listOf(lines[0]) + lines.drop(1).map { "[  OK  ] $it" }
        val screen = BootScreen()
        val grid = CellBuffer(60, 20)
        screen.start(env(60, 20, data = data))
        var scrambled = false
        for (t in 0L..BootScreen.TYPE_MS step 33L) {
            screen.frame(grid, t, 33)
            assertWhole("boot at $t", grid)
            for ((r, line) in full.withIndex()) for (c in 0 until grid.cols) {
                val g = grid.glyphAt(c, r)
                if (g == CellBuffer.EMPTY) continue
                assertTrue("something at $c after '$line'", c < line.length)
                if (g != line[c].code) scrambled = true
            }
        }
        assertTrue(scrambled)
        screen.frame(grid, BootScreen.TYPE_MS + 200, 33)
        assertEquals(full, grid.dump().lines().map { it.trimEnd() }.take(full.size))
        val (short, _) = runFor(BootScreen(), env(60, 3, data = data), 10_000)
        assertEquals(full.takeLast(3), short.dump().lines().map { it.trimEnd() })
    }

    @Test
    fun clockShowsTheTimeInBigDigitsAndTheDateUnder() {
        val (grid, _) = runFor(ClockScreen(), env(40, 12), 100)
        val rows = grid.dump().lines().map { it.trimEnd() }
        // 12:34 (no room for the seconds at 40 columns), each pixel two cells wide.
        assertEquals("", rows[0])
        assertEquals("", rows[1])
        assertEquals("       ██  ██████      ██████  ██  ██", rows[2])
        assertEquals("       ██      ██  ██      ██  ██  ██", rows[3])
        assertEquals("       ██  ██████      ██████  ██████", rows[4])
        assertEquals("       ██  ██      ██      ██      ██", rows[5])
        assertEquals("       ██  ██████      ██████      ██", rows[6])
        assertEquals("", rows[7])
        assertEquals("Saturday 21 November 2026", rows[8].trim())
    }

    @Test
    fun clockShowsSecondsWhenTheyFit() {
        val (grid, _) = runFor(ClockScreen(), env(60, 12), 100)
        // 12:34:56 is 27 pixels across, 54 cells from column 3: the 6's top bar ends in column 56.
        // Without seconds, the clock would end in column 46.
        val top = grid.dump().lines().first { it.isNotBlank() }
        assertEquals(57, top.trimEnd().length)
    }

    @Test
    fun clockIn12HourTimeHasAmOrPm() {
        val oneFive = ZonedDateTime.of(2026, 11, 21, 13, 5, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val (grid, _) = runFor(ClockScreen(), env(40, 12, now = { oneFive }, clock24 = false), 100)
        val rows = grid.dump().lines().map { it.trimEnd() }
        assertTrue(rows[2], rows[2].endsWith("PM"))
        // 1:05 keeps the place of a first digit: nothing is drawn there.
        assertTrue(rows[2], rows[2].substring(0, 7).isBlank())
        assertEquals("Saturday 21 November 2026", rows[8].trim())
    }

    @Test
    fun clockChangesNoCellWithinTheMinute() {
        var now = noon
        val screen = ClockScreen()
        val grid = CellBuffer(40, 12)
        screen.start(env(40, 12, now = { now }))
        screen.frame(grid, 0, 0)
        grid.dirty.clear()
        now += 2_000
        screen.frame(grid, 2_000, 2_000)
        assertTrue(grid.dirty.isEmpty)
        now += 60_000
        screen.frame(grid, 62_000, 60_000)
        assertFalse(grid.dirty.isEmpty)
    }

    @Test
    fun stillFramesShowSomething() {
        for (name in listOf("matrix", "pipes", "decode", "sl", "boot")) {
            val screen = TtyScreens.named(name)!!
            val grid = CellBuffer(40, 20)
            screen.start(env(40, 20))
            screen.still(grid)
            assertTrue(name, grid.dump().isNotBlank())
        }
    }
}
