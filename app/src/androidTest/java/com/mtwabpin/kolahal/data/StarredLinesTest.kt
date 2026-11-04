package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.terminal.LogFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Starred lines: kept past `log.keep-lines` and `log.keep-days` and through a clear, and found by a search. */
@RunWith(AndroidJUnit4::class)
class StarredLinesTest {
    private val logs = LogRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val token = "star${System.nanoTime()}"

    /** This test's lines, oldest first: a search finds starred ones a clear took off the screen too. */
    private fun mine() = logs.search(listOf(token), limit = 5_000).entries.filter { it.text.startsWith(token) }.sortedBy { it.id }

    @After fun tearDown() = mine().forEach { logs.delete(it.id) }

    @Test fun aTrimKeepsStarredLinesAndDoesntCountThem() {
        val ids = (0 until 520).map { logs.add(LogKind.OUTPUT, "$token output $it") }
        listOf(0, 5, 515).forEach { logs.setStarred(ids[it], true) }
        logs.trim(keep = 500)
        val left = mine()
        // The 517 others keep their newest 500: 1 to 4 and 6 to 18 go.
        assertEquals(503, left.size)
        assertEquals(listOf("$token output 0", "$token output 5", "$token output 19"), left.take(3).map { it.text })
        assertEquals(listOf(true, true, false), left.take(3).map { it.starred })
        assertTrue(left.single { it.id == ids[515] }.starred)
        // Nothing past the cap is left: a second trim takes nothing.
        logs.trim(keep = 500)
        assertEquals(503, mine().size)
    }

    @Test fun theAgeTrimKeepsStarredLines() {
        val kept = logs.add(LogKind.OUTPUT, "$token kept")
        logs.add(LogKind.OUTPUT, "$token aged")
        logs.setStarred(kept, true)
        Thread.sleep(5)
        logs.trim(keep = 50_000, maxAgeDays = 0)
        assertEquals(listOf("$token kept"), mine().map { it.text })
    }

    @Test fun anUnstarredLineGoesAsTheOthersDo() {
        val id = logs.add(LogKind.OUTPUT, "$token line")
        logs.setStarred(id, true)
        assertTrue(mine().single().starred)
        logs.setStarred(id, false)
        assertFalse(mine().single().starred)
        Thread.sleep(5)
        logs.trim(keep = 50_000, maxAgeDays = 0)
        assertTrue(mine().isEmpty())
    }

    /** A clear takes a starred line off the screen without deleting it; `@starred` still finds it. */
    @Test fun aClearKeepsStarredLinesOffTheScreen() {
        val starred = logs.add(LogKind.OUTPUT, "$token starred")
        logs.add(LogKind.OUTPUT, "$token plain")
        logs.setStarred(starred, true)
        val cleared = logs.clearScreen()
        logs.purgeCleared(cleared.upTo)
        assertTrue(logs.recent(limit = 200).none { it.text.startsWith(token) })
        assertTrue(logs.isCleared(starred))
        assertEquals(listOf("$token starred"), mine().map { it.text })
        val only = LogFilter.OfKind(LogFilter.Kind.STARRED)
        assertTrue(logs.search(emptyList(), match = only::matches).entries.any { it.id == starred })
        assertTrue(logs.search(listOf(token, "starred")).entries.any { it.id == starred })
    }

    /** A launch line moves down when the app is launched again, but a starred one stays where it is. */
    @Test fun aRepeatedLineLeavesTheStarredOne() {
        val first = logs.addReplacing(LogKind.INFO, "$token Opened Maps")
        logs.setStarred(first, true)
        val second = logs.addReplacing(LogKind.INFO, "$token Opened Maps")
        assertEquals(listOf(first, second), mine().map { it.id })
        val third = logs.addReplacing(LogKind.INFO, "$token Opened Maps")
        assertEquals(listOf(first, third), mine().map { it.id })
    }

    /** Home reads its page again for a star, and a deleted starred line that comes back with Undo is still starred. */
    @Test fun aStarRereadsThePageAndUndoKeepsIt() {
        val id = logs.add(LogKind.OUTPUT, "$token line")
        val structure = logs.structure
        logs.setStarred(id, true)
        assertTrue(logs.structure > structure)
        val line = mine().single()
        logs.delete(id)
        assertTrue(mine().isEmpty())
        logs.restore(line)
        assertTrue(mine().single().starred)
    }
}
