package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.script.ScreenFrame
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A script's screen as home runs it ([ScriptScreen]), with the script made up: the messages both ways,
 * and what a script drew put on the grid ([FramePlayer]).
 */
class ScriptScreenTest {
    private class FakeLink : ScriptScreen.Link {
        val said = ArrayList<String>()
        override fun attach(cols: Int, rows: Int) = add("attach $cols×$rows")
        override fun resize(cols: Int, rows: Int) = add("resize $cols×$rows")
        override fun tick(timeMs: Long, deltaMs: Long) = add("tick $timeMs +$deltaMs")
        override fun key(key: String) = add("key $key")
        override fun tap(col: Int, row: Int) = add("tap $col,$row")
        override fun alive() = add("alive")
        override fun close() = add("close")
        private fun add(what: String) {
            said += what
        }
    }

    private val link = FakeLink()

    private fun screen(fps: Int = 10, keys: Boolean = false, taps: Boolean = false) =
        ScriptScreen("script:abc", "Life", fps, keys, taps, link)

    private fun env(cols: Int = 10, rows: Int = 4) = ScreenEnv(cols, rows, Random(1), { 0L }, clock24 = true)

    /** A frame of the script's, with [ops]. */
    private fun frame(vararg ops: Int, texts: List<String> = emptyList(), cols: Int = 10, rows: Int = 4, tick: Boolean = false) =
        ScreenFrame(cols, rows, ops, texts, tick)

    private fun start(screen: ScriptScreen): CellBuffer {
        val grid = CellBuffer(10, 4)
        screen.start(env())
        return grid
    }

    // ---- messages ------------------------------------------------------------------------------

    /** The screen asks for a tick a frame, and the next one only after the script answered the last: the time that went by is in the next. */
    @Test
    fun ticksWaitForTheScriptsFrame() {
        val screen = screen()
        val grid = start(screen)
        assertEquals(listOf("attach 10×4"), link.said)
        assertTrue(screen.frame(grid, 100, 100))
        // The script is slow: no tick while its frame isn't back.
        assertTrue(screen.frame(grid, 200, 100))
        assertTrue(screen.frame(grid, 300, 100))
        assertEquals(listOf("attach 10×4", "tick 100 +100"), link.said)
        screen.received(frame(tick = true))
        assertTrue(screen.frame(grid, 400, 100))
        // 300 ms went by since the last tick.
        assertEquals(listOf("attach 10×4", "tick 100 +100", "tick 400 +300"), link.said)
        // A frame that answers a key or a tap doesn't answer the tick that is out.
        screen.received(frame(tick = false))
        assertTrue(screen.frame(grid, 500, 100))
        assertEquals(3, link.said.size)
        screen.received(frame(tick = true))
        assertTrue(screen.frame(grid, 600, 100))
        assertEquals("tick 600 +200", link.said.last())
    }

    /** A script that wants no frames gets no ticks, but home says it is still there now and then. */
    @Test
    fun aScriptWithoutFramesIsToldHomeIsThere() {
        val screen = screen(fps = 0)
        assertEquals(ScriptScreen.IDLE_FPS, screen.fps)
        val grid = start(screen)
        var t = 0L
        while (t < 60_000) {
            t += 1_000
            assertTrue(screen.frame(grid, t, 1_000))
        }
        assertEquals(listOf("alive", "alive", "alive", "alive"), link.said.drop(1))
        assertEquals(30, screen(fps = 30).fps)
    }

    /** What the script drew is put on the grid at the next frame. */
    @Test
    fun theDrawingShowsAtTheNextFrame() {
        val screen = screen()
        val grid = start(screen)
        screen.received(frame(ScreenFrame.PUT, 1, 0, 0, 0, texts = listOf("hi")))
        assertEquals("          ", grid.rowText(0))
        assertTrue(screen.frame(grid, 33, 33))
        assertEquals(" hi       ", grid.rowText(0))
        // Frames come in order, each where the last one left the grid.
        screen.received(frame(ScreenFrame.CLEAR, 0))
        screen.received(frame(ScreenFrame.PUT, 0, 1, 0, 0, texts = listOf("two")))
        assertTrue(screen.frame(grid, 66, 33))
        assertEquals(listOf("          ", "two       "), (0..1).map(grid::rowText))
    }

    /** A frame drawn for another size (the grid was turned meanwhile) is left out, but still answers its tick. */
    @Test
    fun aFrameOfAnotherSizeIsLeftOut() {
        val screen = screen()
        val grid = start(screen)
        assertTrue(screen.frame(grid, 33, 33))
        screen.received(frame(ScreenFrame.PUT, 0, 0, 0, 0, texts = listOf("old"), cols = 20, rows = 8, tick = true))
        screen.resize(10, 4)
        assertTrue(screen.frame(grid, 66, 33))
        assertEquals("          ", grid.rowText(0))
        assertEquals("tick 66 +33", link.said.last())
        assertEquals("resize 10×4", link.said[2])
    }

    @Test
    fun anEndedScreenClosesAtTheNextFrame() {
        val screen = screen()
        val grid = start(screen)
        assertTrue(screen.frame(grid, 33, 33))
        screen.received(frame(ScreenFrame.PUT, 0, 0, 0, 0, texts = listOf("bye")))
        screen.ended()
        // The last of what it drew still lands, then the host closes it.
        assertFalse(screen.frame(grid, 66, 33))
        assertEquals("bye", grid.rowText(0).trimEnd())
        screen.stop()
        assertEquals("close", link.said.last())
    }

    /** Without the script's own functions, a tap or a key closes it, as the host does for every screen. */
    @Test
    fun withoutItsFunctionsATapAndAKeyClose() {
        val screen = screen()
        start(screen)
        assertFalse(screen.tap(1, 1))
        assertFalse(screen.key(TtyKey.Typed('x')))
        assertFalse(screen.key(TtyKey.Named.ENTER))
        assertEquals(1, link.said.size)
    }

    /** With them, the script gets each key and tap, and decides (it closes by ending). */
    @Test
    fun withItsFunctionsTheScriptGetsThem() {
        val screen = screen(keys = true, taps = true)
        start(screen)
        assertTrue(screen.tap(1, 2))
        assertTrue(screen.tap(-3, 99))
        assertTrue(screen.key(TtyKey.Typed('x')))
        assertTrue(screen.key(TtyKey.Named.SPACE))
        // A key that means nothing (Shift) is swallowed, and the screen stays.
        assertTrue(screen.key(TtyKey.Named.OTHER))
        assertEquals(listOf("tap 1,2", "tap 0,3", "key x", "key space"), link.said.drop(1))
    }

    @Test
    fun everyKeyHasAName() {
        assertEquals("a", ScriptScreen.keyName(TtyKey.Typed('a')))
        assertEquals("A", ScriptScreen.keyName(TtyKey.Typed('A')))
        val names = TtyKey.Named.entries.map { ScriptScreen.keyName(it) }
        assertEquals(
            listOf("enter", "escape", "space", "up", "down", "left", "right", "tab", "delete", null),
            names
        )
        assertEquals(names.filterNotNull().size, names.filterNotNull().toSet().size)
        assertNull(ScriptScreen.keyName(TtyKey.Named.OTHER))
    }

    // ---- drawing --------------------------------------------------------------------------------

    private fun grid(cols: Int = 10, rows: Int = 4, vararg ops: Int, texts: List<String> = emptyList()): CellBuffer =
        CellBuffer(cols, rows).also { FramePlayer.play(ScreenFrame(cols, rows, ops, texts, false), it) }

    private fun style(fg: ColorRole? = null, bg: ColorRole? = null, flags: Int = 0) = ScreenFrame.style(fg, bg, flags)

    /** A style is in the theme's inks, as everywhere on the grid. */
    @Test
    fun stylesAreInInks() {
        assertEquals(CellStyle.PLAIN, FramePlayer.style(0))
        assertEquals(CellStyle.of(Ink.PROMPT, Ink.BG, CellStyle.BOLD), FramePlayer.style(style(ColorRole.ACCENT, flags = ScreenFrame.BOLD)))
        assertEquals(
            CellStyle.of(Ink.ERROR, Ink.DIM, CellStyle.INVERSE),
            FramePlayer.style(style(ColorRole.BAD, ColorRole.MUTED, ScreenFrame.INVERSE))
        )
        // The flags are a cell's own bits: one by one, they come out as the same thing.
        val flags = listOf(CellStyle.BOLD, CellStyle.FAINT, CellStyle.ITALIC, CellStyle.UNDERLINE, CellStyle.STRIKE, CellStyle.INVERSE)
        assertEquals(flags.reduce(Int::or), ScreenFrame.FLAG_MASK)
        assertEquals(flags, listOf(ScreenFrame.BOLD, ScreenFrame.FAINT, ScreenFrame.ITALIC, ScreenFrame.UNDERLINE, ScreenFrame.STRIKE, ScreenFrame.INVERSE))
    }

    @Test
    fun aTextGoesWhereItWasPut() {
        val g = grid(ops = intArrayOf(ScreenFrame.PUT, 2, 1, 0, style(ColorRole.GOOD)), texts = listOf("hi"))
        assertEquals("  hi      ", g.rowText(1))
        assertEquals(CellStyle.of(Ink.GOOD), g.styleAt(2, 1))
        assertEquals(CellStyle.PLAIN, g.styleAt(1, 1))
        // Line breaks go to the next row, at the same column.
        val lines = grid(ops = intArrayOf(ScreenFrame.PUT, 1, -1, 0, 0), texts = listOf("a\nb\nccc"))
        assertEquals(listOf(" b        ", " ccc      "), (0..1).map(lines::rowText))
    }

    /** Wide characters take two cells, and one that has no room at the edge is blank. */
    @Test
    fun wideCharactersTakeTwoCells() {
        val g = grid(ops = intArrayOf(ScreenFrame.PUT, 0, 0, 0, 0), texts = listOf("a界b"))
        assertEquals("a界b      ", g.rowText(0))
        assertEquals(CellBuffer.WIDE_TAIL, g.glyphAt(2, 0))
        val edge = grid(ops = intArrayOf(ScreenFrame.PUT, 8, 0, 0, 0), texts = listOf("x界"))
        assertEquals(10, edge.cols)
        assertEquals("        x ", edge.rowText(0))
        assertEquals(10, CellWidth.total(edge.rowText(0)))
    }

    @Test
    fun aFillClearAndABarAndABox() {
        val blocks = grid(
            ops = intArrayOf(
                ScreenFrame.FILL, 1, 1, 4, 2, '#'.code, 0,
                ScreenFrame.BAR, 0, 3, 4, 20, style(ColorRole.ACCENT),
            )
        )
        assertEquals(listOf("          ", " ####     ", " ####     "), (0..2).map(blocks::rowText))
        // 20 eighths of a cell in four: two full, one half, and the rest blank.
        assertEquals("██▌ ".padEnd(10), blocks.rowText(3))
        assertEquals(CellStyle.of(Ink.PROMPT), blocks.styleAt(1, 3))

        val boxed = grid(ops = intArrayOf(ScreenFrame.BOX, 0, 0, 5, 3, 0))
        assertEquals(listOf("┌───┐     ", "│   │     ", "└───┘     "), (0..2).map(boxed::rowText))

        val cleared = grid(
            ops = intArrayOf(
                ScreenFrame.PUT, 0, 0, 0, 0,
                ScreenFrame.CLEAR, style(bg = ColorRole.BAD),
            ),
            texts = listOf("gone")
        )
        assertEquals(" ".repeat(10), cleared.rowText(0))
        assertEquals(CellStyle.of(Ink.FG, Ink.ERROR), cleared.styleAt(3, 2))
    }

    /** A space fills with blank cells, so their background shows; a wide character fills every second cell. */
    @Test
    fun fillingWithBlanksAndWideCharacters() {
        val blank = grid(ops = intArrayOf(ScreenFrame.FILL, 0, 0, 3, 1, ' '.code, style(bg = ColorRole.GOOD)))
        assertEquals(CellBuffer.EMPTY, blank.glyphAt(0, 0))
        assertEquals(CellStyle.of(Ink.FG, Ink.GOOD), blank.styleAt(2, 0))
        val wide = grid(ops = intArrayOf(ScreenFrame.FILL, 0, 0, 6, 2, 0x754C, 0))
        assertEquals("界界界    ", wide.rowText(0))
        assertEquals("界界界    ", wide.rowText(1))
    }

    /** A box needs two cells each way, and what is off the grid is cut off. */
    @Test
    fun edgesAreCutOff() {
        val tiny = grid(ops = intArrayOf(ScreenFrame.BOX, 0, 0, 1, 4, 0, ScreenFrame.BOX, 4, 0, 2, 1, 0))
        assertEquals(" ".repeat(10), tiny.rowText(0))
        val partly = grid(ops = intArrayOf(ScreenFrame.BOX, -2, -1, 8, 3, 0))
        assertEquals("     │    ", partly.rowText(0))
        assertEquals("─────┘    ", partly.rowText(1))
        val off = grid(ops = intArrayOf(ScreenFrame.PUT, 50, 2, 0, 0, ScreenFrame.BAR, -1, 0, 4, 32, 0), texts = listOf("nope"))
        assertEquals("███       ", off.rowText(0))
        assertEquals(" ".repeat(10), off.rowText(2))
    }

    /** Numbers that make no sense stop the frame, and never fail: a text that isn't there, a call cut short, a code that isn't one. */
    @Test
    fun aBrokenFrameStopsQuietly() {
        for (ops in listOf(
            intArrayOf(ScreenFrame.PUT, 0, 0, 5, 0),
            intArrayOf(ScreenFrame.PUT, 0, 0, -1, 0),
            intArrayOf(ScreenFrame.PUT, 0, 0),
            intArrayOf(ScreenFrame.CLEAR),
            intArrayOf(99),
            intArrayOf(0),
            IntArray(0),
        )) assertEquals(" ".repeat(10), grid(ops = ops, texts = listOf("x")).rowText(0))
        // What is before a broken call is drawn.
        assertEquals("x", grid(ops = intArrayOf(ScreenFrame.PUT, 0, 0, 0, 0, 99), texts = listOf("x")).rowText(0).trim())
    }

    /** The same drawing is the same grid, whatever it was drawn over. */
    @Test
    fun aClearStartsAfresh() {
        val a = CellBuffer(10, 4).also { it.put(0, 0, "界界界界界", CellStyle.PLAIN) }
        val b = CellBuffer(10, 4)
        for (g in listOf(a, b)) FramePlayer.play(
            ScreenFrame(10, 4, intArrayOf(ScreenFrame.CLEAR, 0, ScreenFrame.PUT, 1, 0, 0, 0), listOf("hi"), false), g
        )
        assertEquals(b.dump(), a.dump())
    }
}
