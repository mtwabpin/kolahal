package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A run's lines on their way to the log: batched, held to the run's limits, and open to `line.set`.
 * (The device's ScriptLogLimitsTest and ScriptRichLinesTest check the same through HostApi and the real log.)
 */
class RunLogTest {
    private var clock = 1_000L
    private var over = false

    /** False: the lines aren't written yet (a pipe's capture), so they get no ids. */
    private var gotIds = true
    private var nextId = 1L

    /** Every line written, in order, and every batch of changes to written lines. */
    private val written = ArrayList<LogRepository.Pending>()
    private val changed = ArrayList<Map<Long, Pair<String, LogContext>>>()

    private val base = mapOf(LogContext.SCRIPT to "s")
    private val plain = LogContext.EMPTY

    private val log = RunLog(
        "s", base, over = { over },
        write = { lines ->
            written += lines
            if (gotIds) lines.map { nextId++ } else null
        },
        change = { if (it.isNotEmpty()) changed += it },
        now = { clock },
    )

    private fun line(text: String) = log.add(LogKind.SCRIPT, text, plain)
    private fun set(number: Int, text: String) = log.set(number) { text to plain }
    private val texts get() = written.map { it.text }

    @Test fun linesWaitForAFlush() {
        assertEquals(1, line("a"))
        assertEquals(2, line("b"))
        assertEquals(emptyList<String>(), texts)
        log.flush()
        assertEquals(listOf("a", "b"), texts)
        assertEquals(listOf("s", "s"), written.map { it.source })
        assertEquals(listOf(1_000L, 1_000L), written.map { it.ts })
    }

    /** Only a script's own line has a number for `line.set`. An error line is logged and counted, but has none. */
    @Test fun onlyScriptLinesHaveANumber() {
        assertEquals(1, line("a"))
        assertEquals(0, log.add(LogKind.ERROR, "oops", LogContext(base)))
        assertEquals(2, line("b"))
        log.flush()
        assertEquals(listOf(LogKind.SCRIPT, LogKind.ERROR, LogKind.SCRIPT), written.map { it.kind })
    }

    @Test fun fiftyLinesAreWrittenAtOnce() {
        repeat(49) { line("$it") }
        assertEquals(0, written.size)
        line("49")
        assertEquals(50, written.size)
    }

    @Test fun linesAreWrittenAboutEvery300Ms() {
        line("a")
        clock += 300
        line("b")
        assertEquals(0, written.size)
        clock += 1
        line("c")
        assertEquals(listOf("a", "b", "c"), texts)
        // The wait starts again at the flush.
        clock += 300
        line("d")
        assertEquals(3, written.size)
    }

    @Test fun pastTheLineLimitTheRestAreDroppedWithOneNote() {
        repeat(HostApi.MAX_LINES_PER_RUN) { assertEquals(it + 1, line("$it")) }
        assertEquals(0, line("one too many"))
        assertEquals(0, line("and another"))
        log.flush()
        assertEquals(HostApi.MAX_LINES_PER_RUN + 1, written.size)
        val note = written.last()
        assertEquals("More than 500 lines in one run: the rest aren't logged", note.text)
        assertEquals(LogKind.INFO, note.kind)
        assertEquals(LogContext(base), note.ctx)
    }

    /** A line counts as the log stores it: its text and its context, each up to what the log keeps of a field. */
    @Test fun pastTheCharacterLimitTheRestAreDroppedWithOneNote() {
        val big = "x".repeat(1_000_000)
        // Four lines of 64K are exactly the limit, so the context's seven characters put the fourth over.
        repeat(3) { assertEquals(it + 1, log.add(LogKind.SCRIPT, big, LogContext(base))) }
        assertEquals(0, log.add(LogKind.SCRIPT, big, LogContext(base)))
        assertEquals(0, line("small"))
        log.flush()
        assertEquals(4, written.size)
        assertEquals("More than 256K characters in one run: the rest aren't logged", written.last().text)
    }

    @Test fun exactlyTheCharacterLimitIsStillLogged() {
        repeat(4) { line("x".repeat(LogRepository.MAX_FIELD)) }
        assertEquals(4 * LogRepository.MAX_FIELD, HostApi.MAX_CHARS_PER_RUN)
        log.flush()
        assertEquals(4, written.size)
        assertEquals(0, line("y"))
    }

    @Test fun afterTheRunNothingIsLogged() {
        line("a")
        over = true
        assertEquals(0, line("late"))
        log.flush()
        assertEquals(listOf("a"), texts)
    }

    @Test fun aLineStillWaitingIsChangedBeforeItIsWritten() {
        val n = line("Fetching…")
        set(n, "Fetched")
        log.flush()
        assertEquals(listOf("Fetched"), texts)
        assertTrue(changed.isEmpty())
    }

    @Test fun aWrittenLineIsChangedInPlaceAtTheNextFlush() {
        line("first")
        val n = line("Fetching…")
        log.flush()
        set(n, "Half way")
        set(n, "Fetched")
        assertTrue("changes wait for a flush", changed.isEmpty())
        log.flush()
        // The line's id (the second one written), with what it was last set to: one write.
        assertEquals(listOf(mapOf(2L to ("Fetched" to plain))), changed)
        assertEquals(listOf("first", "Fetching…"), texts)
        log.flush()
        assertEquals("nothing new, so nothing more is written", 1, changed.size)
    }

    @Test fun aChangeIsWrittenOnceTheWaitIsOver() {
        val n = line("a")
        log.flush()
        clock += 301
        set(n, "b")
        assertEquals(listOf(mapOf(1L to ("b" to plain))), changed)
    }

    /** Lines inside a pipe's capture aren't in the log, so they have no id to change. */
    @Test fun aLineThatWasNotWrittenCantBeChangedLater() {
        gotIds = false
        val n = line("a")
        log.flush()
        set(n, "b")
        log.flush()
        assertEquals(listOf("a"), texts)
        assertTrue(changed.isEmpty())
    }

    @Test fun onlyALineOfThisRunCanBeChanged() {
        line("a")
        log.add(LogKind.ERROR, "oops", plain)
        for (number in listOf(0, -1, 2, 99)) {
            // The new text is not even looked at: it could stop the run for another reason.
            val e = assertThrows(IllegalArgumentException::class.java) { log.set(number) { error("not reached") } }
            assertEquals("line.set: not a line this run logged", e.message)
        }
    }

    /** The new text is checked (links, secrets) before anything changes; what that throws reaches the script. */
    @Test fun aChangeThatIsRefusedLeavesTheLine() {
        val n = line("a")
        assertThrows(java.io.IOException::class.java) { log.set(n) { throw java.io.IOException("KEY may only be sent to api.example.com") } }
        log.flush()
        assertEquals(listOf("a"), texts)
    }

    @Test fun aRunCanChangeItsLinesSoManyTimes() {
        val n = line("0")
        repeat(HostApi.MAX_SETS_PER_RUN + 5) { set(n, "${it + 1}") }
        log.flush()
        assertEquals(listOf("${HostApi.MAX_SETS_PER_RUN}"), texts)
    }

    @Test fun theSameTextAgainDoesNotCount() {
        val n = line("same")
        repeat(HostApi.MAX_SETS_PER_RUN + 50) { set(n, "same") }
        set(n, "last")
        log.flush()
        assertEquals(listOf("last"), texts)
    }

    /** Only what a line grew by counts, and the run's character limit holds for changes too. */
    @Test fun aChangeCountsWhatTheLineGrewBy() {
        val n = line("x".repeat(10))
        repeat(3) { line("y".repeat(LogRepository.MAX_FIELD)) }
        // 10 + 3 × 64K so far, of 4 × 64K. The first line grows by all but 10 of what is left.
        set(n, "x".repeat(LogRepository.MAX_FIELD - 10))
        // A line that gets shorter gives nothing back: there are still 10 left.
        set(n, "z")
        // From 1 character to 11: it grew by 10, which fits exactly.
        set(n, "x".repeat(11))
        log.flush()
        assertEquals("x".repeat(11), texts.first())
        assertEquals(4, written.size)
        // One more character than the run has left: the change is dropped, with the note, and so is everything after.
        set(n, "x".repeat(12))
        assertEquals(0, line("more"))
        set(n, "late")
        log.flush()
        assertEquals("More than 256K characters in one run: the rest aren't logged", written.last().text)
        assertEquals(5, written.size)
        assertTrue(changed.isEmpty())
    }

    @Test fun afterTheRunNothingIsChanged() {
        val n = line("a")
        over = true
        set(n, "late")
        log.flush()
        assertEquals(listOf("a"), texts)
    }
}
