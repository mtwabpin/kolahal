package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.terminal.LineSwipe.COPY
import com.mtwabpin.kolahal.terminal.LineSwipe.DELETE
import com.mtwabpin.kolahal.terminal.LineSwipe.FRESH
import com.mtwabpin.kolahal.terminal.LineSwipe.NONE
import com.mtwabpin.kolahal.terminal.LineSwipe.RUN
import com.mtwabpin.kolahal.terminal.LineSwipe.STAR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a swipe on a log line does (`log.swipe-left`, `log.swipe-right`): the values and their defaults, which
 * side does what with and without the log first (the defaults trade, a choice doesn't), what a line moving each
 * way shows, and run on a line without a command.
 */
class LineSwipesTest {
    private fun line(text: String, kind: LogKind = LogKind.OUTPUT, starred: Boolean = false, ctx: LogContext = LogContext()) =
        LogEntry(1, 0, kind, null, text, ctx, starred)

    @Test
    fun theSettingsOfferEveryActionAndDefaultToTodaysSwipes() {
        assertEquals(listOf("copy", "delete", "star", "fresh", "run", "none"), Keys.LOG_SWIPE_LEFT.choices)
        assertEquals(LineSwipe.IDS, Keys.LOG_SWIPE_LEFT.choices)
        assertEquals(LineSwipe.IDS, Keys.LOG_SWIPE_RIGHT.choices)
        assertEquals("delete", Keys.LOG_SWIPE_LEFT.default)
        assertEquals("copy", Keys.LOG_SWIPE_RIGHT.default)
        assertEquals(LineSwipes(DELETE, COPY), LineSwipes.DEFAULT)
        for (key in listOf(Keys.LOG_SWIPE_LEFT, Keys.LOG_SWIPE_RIGHT)) {
            assertTrue(key in Keys.ALL)
            for (id in key.choices) assertNotNull("${key.key} $id", key.notes[id])
            // The value as typed: `config set log.swipe-left Star`.
            assertEquals("star", key.parse("Star"))
            assertEquals(null, key.parse("paste"))
        }
        // Every id names its action, and an unknown one does nothing.
        for (s in LineSwipe.entries) assertEquals(s, LineSwipe.of(s.id))
        assertEquals(NONE, LineSwipe.of("paste"))
    }

    /** The defaults: right copies and left deletes, the other way round with the log first, mirrored in a right-to-left layout. */
    @Test
    fun theDefaultsFollowThePageOrder() {
        val d = Keys.LOG_SWIPE_LEFT.default
        val c = Keys.LOG_SWIPE_RIGHT.default
        assertEquals(LineSwipes(DELETE, COPY), LineSwipes.of(d, c, pagesRight = false, rtl = false))
        assertEquals(LineSwipes(COPY, DELETE), LineSwipes.of(d, c, pagesRight = true, rtl = false))
        assertEquals(LineSwipes(COPY, DELETE), LineSwipes.of(d, c, pagesRight = false, rtl = true))
        assertEquals(LineSwipes(DELETE, COPY), LineSwipes.of(d, c, pagesRight = true, rtl = true))
    }

    /** Once either side is chosen, left is left and right is right, whatever the page order and the layout. */
    @Test
    fun aChoiceIsTheSideItNames() {
        val ids = LineSwipe.IDS
        for (left in ids) for (right in ids) {
            if (left == Keys.LOG_SWIPE_LEFT.default && right == Keys.LOG_SWIPE_RIGHT.default) continue
            for (pagesRight in listOf(false, true)) for (rtl in listOf(false, true)) {
                assertEquals(
                    "$left $right pagesRight=$pagesRight rtl=$rtl",
                    LineSwipes(LineSwipe.of(left), LineSwipe.of(right)),
                    LineSwipes.of(left, right, pagesRight, rtl),
                )
            }
        }
        // One side changed: the other keeps its own default, not the one it had traded for.
        assertEquals(LineSwipes(STAR, COPY), LineSwipes.of("star", "copy", pagesRight = true, rtl = false))
        assertEquals(LineSwipes(DELETE, FRESH), LineSwipes.of("delete", "fresh", pagesRight = true, rtl = false))
    }

    /** A drag right does the right side's action, left the left side's; none keeps the line from moving that way. */
    @Test
    fun aDragDoesItsSidesAction() {
        val s = LineSwipes(FRESH, STAR)
        assertEquals(STAR, s.toward(12f))
        assertEquals(FRESH, s.toward(-12f))
        assertEquals(NONE, s.toward(0f))
        val half = LineSwipes(NONE, COPY)
        assertTrue(half.any)
        assertEquals(0f, half.reach(-40f))
        assertEquals(40f, half.reach(40f))
        assertFalse(LineSwipes(NONE, NONE).any)
        // What a screen reader offers: each action once, none never.
        assertEquals(listOf(COPY, DELETE), LineSwipes.DEFAULT.both)
        assertEquals(listOf(COPY, DELETE), LineSwipes(COPY, DELETE).both)
        assertEquals(listOf(STAR), LineSwipes(STAR, STAR).both)
        assertEquals(listOf(COPY), half.both)
        assertEquals(emptyList<LineSwipe>(), LineSwipes(NONE, NONE).both)
    }

    /**
     * A line takes a drag only toward a side that does something on it: with left set to run, an output line
     * leaves a left drag to the pager (the page turns) and doesn't move that way, while a command's line takes it.
     */
    @Test
    fun aSideThatDoesNothingOnTheLineLeavesTheDragToThePager() {
        val s = LineSwipes(RUN, COPY)
        val output = line("Battery 81%")
        val command = line("battery", LogKind.COMMAND)
        fun on(e: LogEntry): (LineSwipe) -> Boolean = { it.doesOn(e) }
        assertFalse(s.takes(-12f, on(output)))
        assertTrue(s.takes(12f, on(output)))
        assertTrue(s.takes(-12f, on(command)))
        assertEquals(0f, s.reach(-40f, on(output)))
        assertEquals(40f, s.reach(40f, on(output)))
        assertEquals(-40f, s.reach(-40f, on(command)))
        // None never takes a drag, whatever the line; nor does a drag that went nowhere.
        assertFalse(LineSwipes(NONE, COPY).takes(-12f) { true })
        assertFalse(LineSwipes.DEFAULT.takes(0f) { true })
        // Fresh on the first line shown: the log says it does nothing there, so the page turns.
        assertFalse(LineSwipes(FRESH, COPY).takes(-12f) { it != FRESH })
    }

    /** Run needs the line's command, as the menu's Run again does: on any other line it does nothing. */
    @Test
    fun runOnlyOnALineWithACommand() {
        val output = line("Battery 81%")
        val command = line("battery", LogKind.COMMAND)
        val marked = line("Opened Maps", ctx = LogContext.of(LogContext.COMMAND to "maps"))
        assertFalse(RUN.doesOn(output))
        assertTrue(RUN.doesOn(command))
        assertTrue(RUN.doesOn(marked))
        assertEquals("battery", LineSwipe.commandOf(command))
        assertEquals("maps", LineSwipe.commandOf(marked))
        assertEquals(null, LineSwipe.commandOf(output))
        // The others do something on every line; none on none.
        for (s in listOf(COPY, DELETE, STAR, FRESH)) assertTrue(s.doesOn(output))
        assertFalse(NONE.doesOn(command))
    }

    /** What shows behind a moving line: the icon on the outer edge, Unstar on a starred line. */
    @Test
    fun aMovingLineSaysWhatItWillDo() {
        val plain = line("a line")
        val starred = line("a line", starred = true)
        assertEquals("⧉ Copy", COPY.label(plain, right = true))
        assertEquals("Copy ⧉", COPY.label(plain, right = false))
        assertEquals("Delete ✕", DELETE.label(plain, right = false))
        assertEquals("★ Star", STAR.label(plain, right = true))
        assertEquals("☆ Unstar", STAR.label(starred, right = true))
        assertEquals("Fresh ▴", FRESH.label(plain, right = false))
        assertEquals("▸ Run", RUN.label(plain, right = true))
        assertTrue(DELETE.destructive)
        assertTrue(LineSwipe.entries.filter { it.destructive } == listOf(DELETE))
        // A screen reader's words are the menu's.
        assertEquals("Star line", STAR.spoken(plain))
        assertEquals("Unstar line", STAR.spoken(starred))
        assertEquals("Fresh from here", FRESH.spoken(plain))
        assertEquals("Run again", RUN.spoken(plain))
    }

    /** Each key has its title in Settings and its line in the reference. */
    @Test
    fun theKeysHaveTitlesAndReferenceLines() {
        val reference = File(sourceFile("src").absoluteFile.parentFile.parentFile, "site/src/content/docs/reference/config.md")
            .readText()
        for (key in listOf(Keys.LOG_SWIPE_LEFT, Keys.LOG_SWIPE_RIGHT)) {
            assertNotNull(key.key, SettingTitles[key.key])
            assertTrue(key.key, reference.lines().any { it.startsWith("| `${key.key}` |") })
        }
        assertEquals("Swipe a log line left", SettingTitles[Keys.LOG_SWIPE_LEFT.key])
        assertEquals("Swipe a log line right", SettingTitles[Keys.LOG_SWIPE_RIGHT.key])
    }
}
