package com.mtwabpin.kolahal.terminal

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fresh from a line (its menu's Fresh from here, a swipe set to fresh): the lines above it are hidden as
 * `fresh` hides them, nothing is deleted, and "▴ N earlier lines" counts them all and brings them back.
 */
@RunWith(AndroidJUnit4::class)
class FreshFromALineTest {
    @get:Rule
    val vms = TestViewModels()

    // The lines written here are deleted after, and the fresh screen the test made is undone.
    @get:Rule
    val lines = LogLinesSince()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val token = "ff${System.nanoTime()}"
    private val ids = ArrayList<Long>()

    /** What was hidden before the test's lines: the log the test found, out of the way. */
    private var before = 0

    @Before
    fun fiveLines() {
        logs.hideScreen(logs.maxId())
        before = logs.hiddenCount()
        repeat(5) { ids += logs.add(LogKind.OUTPUT, "$token $it") }
    }

    private fun shown(): List<Long> = logs.recent(50).map { it.id }.filter { it in ids }

    @Test
    fun theLinesAboveAreHiddenAndCounted() {
        assertTrue(logs.shownBefore(ids[2]))
        logs.hideAbove(ids[2])
        assertEquals(ids.drop(2), shown())
        assertEquals(before + 2, logs.hiddenCount())
        // Nothing was deleted: they come back with the others.
        logs.unhide()
        assertEquals(ids, shown())
    }

    /** From a line further down, more are hidden; from a hidden line or the first shown one, nothing changes. */
    @Test
    fun againFromALineFurtherDownHidesUpToThere() {
        logs.hideAbove(ids[1])
        assertEquals(before + 1, logs.hiddenCount())
        logs.hideAbove(ids[3])
        assertEquals(ids.drop(3), shown())
        assertEquals(before + 3, logs.hiddenCount())
        // The first shown line: nothing above it shows, so nothing changes.
        assertFalse(logs.shownBefore(ids[3]))
        logs.hideAbove(ids[3])
        assertEquals(before + 3, logs.hiddenCount())
        // A hidden line (a search can still find it): it never shows the lines hidden below it.
        assertFalse(logs.shownBefore(ids[2]))
        logs.hideAbove(ids[2])
        assertEquals(ids.drop(3), shown())
        assertEquals(before + 3, logs.hiddenCount())
    }

    /**
     * A locked private space's lines don't show: above the first line that does, they don't make the menu offer
     * Fresh from here, which would hide only them and count them in "▴ N earlier lines". Unlocked, they show.
     */
    @Test
    fun aLockedSpacesLinesAboveDontCount() {
        logs.hideScreen()
        val serial = 987_654L
        val space = LogContext.of(LogContext.USER to serial.toString())
        val locked = (0 until 2).map { logs.add(LogKind.SCRIPT, "$token private $it", ctx = space) }
        val first = logs.add(LogKind.OUTPUT, "$token shown")
        ids += locked + first
        assertFalse(logs.shownBefore(first, setOf(serial)))
        assertTrue(logs.shownBefore(first))
        // A line below shows above it either way.
        val next = logs.add(LogKind.OUTPUT, "$token next")
        ids += next
        assertTrue(logs.shownBefore(next, setOf(serial)))
    }

    /** After `fresh`, a fresh from a newer line hides up to it, and the count covers both. */
    @Test
    fun withLinesAlreadyHiddenTheCountCoversBoth() {
        logs.hideScreen()
        val later = (0 until 3).map { logs.add(LogKind.OUTPUT, "$token later $it") }
        ids += later
        assertEquals(before + 5, logs.hiddenCount())
        logs.hideAbove(later[1])
        assertEquals(later.drop(1), shown())
        assertEquals(before + 6, logs.hiddenCount())
        logs.unhide()
        assertEquals(ids, shown())
    }

    /** The menu offers it on every line but the first shown one, and it hides the lines above. A swipe does the same. */
    @Test
    fun theMenuAndASwipeFreshFromALine() {
        val vm = vms.terminal(app)
        waitUntil("the lines") { vm.entries.value.map { it.id }.containsAll(ids) }
        val entries: List<LogEntry> = ids.map { id -> vm.entries.value.first { it.id == id } }
        fun offers(e: LogEntry) = vm.actionsFor(e).any { it.label == "Fresh from here" }
        assertFalse("the first line shown", offers(entries[0]))
        assertTrue(offers(entries[2]))
        // An app held in a list has no line of its own.
        assertFalse(offers(entries[2].copy(id = -1)))
        vm.actionsFor(entries[2]).first { it.label == "Fresh from here" }.run()
        waitUntil("hidden by the menu") { vm.hiddenLines.value == before + 2 }
        waitUntil("the page from that line") { vm.entries.value.firstOrNull()?.id == ids[2] }
        // A swipe set to fresh: not on the first line shown now, and on a line below it.
        val now = vm.entries.value
        assertFalse(vm.swipeDoes(now.first { it.id == ids[2] }, LineSwipe.FRESH))
        val fourth = now.first { it.id == ids[3] }
        assertTrue(vm.swipeDoes(fourth, LineSwipe.FRESH))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.swipe(fourth, LineSwipe.FRESH) }
        waitUntil("hidden by the swipe") { vm.hiddenLines.value == before + 3 }
        // "▴ N earlier lines" brings them all back.
        vm.showHidden()
        waitUntil("all back") { vm.entries.value.map { it.id }.containsAll(ids) }
    }
}
