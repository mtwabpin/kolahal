package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogKept
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How lines reach the log: a command's output written together, and what home is told about lines that moved or went. */
@RunWith(AndroidJUnit4::class)
class LogWritesTest {
    // A clear here purges the phone's own lines too: the whole log comes back after each test.
    @get:Rule val logKept = LogKept()
    private val logs = LogRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val token = "lw${System.nanoTime()}"

    private fun mine() = logs.recent(limit = 200).filter { it.text.startsWith(token) }

    @After fun tearDown() = mine().forEach { logs.delete(it.id) }

    /** `clear` takes the screen out of view at once; Undo brings it back until its lines are deleted, commands aside. */
    @Test fun aClearCanBeUndoneUntilItsLinesGo() {
        logs.add(LogKind.OUTPUT, "$token out")
        val command = logs.add(LogKind.COMMAND, "$token cmd")
        try {
            val cleared = logs.clearScreen()
            assertTrue(mine().isEmpty())
            assertTrue(logs.undoClear(cleared))
            assertEquals(listOf("$token out", "$token cmd"), mine().map { it.text })

            val again = logs.clearScreen()
            logs.purgeCleared(again.upTo)
            assertFalse(logs.undoClear(again))
            assertTrue(mine().isEmpty())
            // The output is gone; the command stays for ↑.
            assertTrue("$token cmd" in logs.commandHistory())
        } finally {
            logs.delete(command)
        }
    }

    /** An Undo that came too late changes nothing, and a purge never takes lines brought back. */
    @Test fun aPurgeLeavesWhatAnUndoBroughtBack() {
        logs.add(LogKind.OUTPUT, "$token kept")
        val cleared = logs.clearScreen()
        assertTrue(logs.undoClear(cleared))
        logs.purgeCleared(cleared.upTo)
        assertEquals(listOf("$token kept"), mine().map { it.text })
    }

    /** A command that prints fifty lines costs one write, announced once. */
    @Test fun batchedLinesAreWrittenTogether() {
        val version = logs.version.value
        val ids = logs.batched { (0 until 5).map { logs.add(LogKind.OUTPUT, "$token $it") } }
        assertEquals(listOf(-1L, -1L, -1L, -1L, -1L), ids)
        assertEquals(version + 1, logs.version.value)
        assertEquals((0 until 5).map { "$token $it" }, mine().map { it.text })
    }

    /** A slow command still shows its output as it goes. */
    @Test fun aBatchFlushesAsItGoes() {
        logs.batched {
            logs.add(LogKind.OUTPUT, "$token first")
            // Written by itself a moment later, while the batch goes on.
            waitUntil("the first line, before the batch ends", 5_000) { mine().isNotEmpty() }
            logs.add(LogKind.OUTPUT, "$token second")
            assertEquals(listOf("$token first"), mine().map { it.text })
        }
        assertEquals(listOf("$token first", "$token second"), mine().map { it.text })
    }

    @Test fun batchedLinesCarryTheirCommand() {
        logs.withCause(42L) { logs.batched { logs.add(LogKind.OUTPUT, "$token caused") } }
        assertEquals("42", mine().single().ctx.values[LogContext.FROM])
    }

    /** A pipe inside a batch still captures, and writes its result with the batch. */
    @Test fun aCaptureInsideABatchWritesNothingItself() {
        val version = logs.version.value
        logs.batched {
            val (_, captured) = logs.capture { logs.add(LogKind.OUTPUT, "$token piped") }
            assertTrue(mine().isEmpty())
            logs.addAll(captured.map { it.copy(text = it.text + " out") })
        }
        assertEquals(listOf("$token piped out"), mine().map { it.text })
        assertEquals(version + 1, logs.version.value)
    }

    /** A launch line moved to the bottom: home drops the old one from its page instead of reading it again. */
    @Test fun aMovedLineIsReportedNotRestructured() {
        val first = logs.addReplacing(LogKind.INFO, "$token launched")
        val seq = logs.removedSeq
        val structure = logs.structure
        val second = logs.addReplacing(LogKind.INFO, "$token launched")
        assertEquals(structure, logs.structure)
        assertEquals(setOf(first), logs.removedSince(seq))
        assertFalse(second in logs.removedSince(seq)!!)
        assertEquals(listOf(second), mine().map { it.id })
        // Two readers each apply the removals from where they were.
        assertEquals(setOf(first), logs.removedSince(seq))
        assertEquals(emptySet<Long>(), logs.removedSince(logs.removedSeq))
    }

    @Test fun aDeletedLineIsReported() {
        val id = logs.add(LogKind.OUTPUT, "$token gone")
        val seq = logs.removedSeq
        val structure = logs.structure
        logs.delete(id)
        assertEquals(structure, logs.structure)
        assertEquals(setOf(id), logs.removedSince(seq))
        assertTrue(mine().isEmpty())
        // A reader that fell too far behind is told to read the page again.
        assertEquals(null, logs.removedSince(-1_000L))
    }

    /** Scrolling back reads the page before the oldest line shown, not the whole page again. */
    @Test fun anOlderPageIsReadBeforeAnId() {
        val ids = (0 until 5).map { logs.add(LogKind.OUTPUT, "$token $it") }
        assertEquals(listOf(ids[1], ids[2]), logs.recent(2, beforeId = ids[3]).map { it.id })
        assertEquals(listOf(ids[0]), logs.recent(5, beforeId = ids[1]).map { it.id }.filter { it in ids })
        assertEquals(listOf(ids[4]), logs.recent(5, afterId = ids[3]).map { it.id })
    }
}
