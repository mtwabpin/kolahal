package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.ScriptRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a run that threw is reported: from what stopped it ([HostApi.stoppedBy]), not from the error's text. */
class RunOutcomeTest {
    private fun outcome(
        error: Throwable,
        stop: RunStop? = null,
        repl: Boolean = false,
        line: Int? = null,
        redact: (String) -> String = { it },
    ) = runOutcome("hn", error, stop, repl, redact, { line }, timeoutS = 60)

    private val denied = RunStop.Denied(ScriptAccess.INTERNET, "internet is off for this script")

    @Test
    fun aTimeoutSaysSo() {
        val o = outcome(IllegalStateException("script timed out after 60s"), RunStop.Timeout)
        assertEquals(RunOutcome("hn: timed out after 60s", "timeout"), o)
    }

    @Test
    fun aScriptsOwnErrorAboutATimeoutIsAnError() {
        val o = outcome(RuntimeException("script timed out"))
        assertEquals("hn: script timed out", o.line)
        assertEquals("error: script timed out", o.status)
    }

    @Test
    fun aSwitchThatIsOffSaysWhichWithATapToAllowIt() {
        val o = outcome(java.io.IOException("hn:3: internet is off for this script"), denied)
        assertEquals("hn: internet is off for this script — tap to allow", o.line)
        assertEquals("stopped: internet off", o.status)
        assertEquals(mapOf(LogContext.RUN to "script allow hn internet"), o.extra)
    }

    @Test
    fun aDenialTheScriptCaughtIsNotWhatEndedTheRun() {
        val o = outcome(RuntimeException("no stories"), denied)
        assertEquals("hn: no stories", o.line)
        assertEquals("error: no stories", o.status)
    }

    @Test
    fun aTimeoutAfterACaughtDenialIsATimeout() {
        assertEquals(
            "timeout",
            outcome(RuntimeException("internet is off for this script"), RunStop.Timeout).status
        )
    }

    @Test
    fun anErrorGivesItsFirstLineAndALineToEdit() {
        val o = outcome(RuntimeException("\nbad value\nstack"), line = 12)
        assertEquals("hn: bad value (tap to edit line 12)", o.line)
        assertEquals("error: bad value (line 12)", o.status)
        assertEquals(
            mapOf(LogContext.COPY to "\nbad value\nstack", LogContext.LINE to "12"),
            o.extra
        )
    }

    /** A Lua error starts with where it happened. The line says the name once, and the line number at its end. */
    @Test
    fun aLuaErrorSaysTheNameOnce() {
        val message = "hn:12: attempt to index a nil value\nstack traceback: …"
        val o = outcome(RuntimeException(message), line = 12)
        assertEquals("hn: attempt to index a nil value (tap to edit line 12)", o.line)
        assertEquals("error: attempt to index a nil value (line 12)", o.status)
        // What is copied is the whole message, as Lua gave it.
        assertEquals(message, o.extra[LogContext.COPY])
        // An error thrown again has two places.
        assertEquals(
            "hn: no stories (tap to edit line 40)",
            outcome(RuntimeException("hn:40: hn:12: no stories"), line = 40).line
        )
        // Another script's place, or one inside the message, stays.
        assertEquals("hn: hnx:3: bad", outcome(RuntimeException("hnx:3: bad")).line)
        assertEquals("hn: see hn:3: here", outcome(RuntimeException("see hn:3: here")).line)
        // A message that is only the place keeps it: there is nothing else to say.
        assertEquals("hn: hn:12:", outcome(RuntimeException("hn:12:")).line)
    }

    /** Only a switch that stopped the run marks it: the card then leads to the Scripts screen. */
    @Test
    fun onlyASwitchThatStoppedTheRunMarksIt() {
        assertTrue(outcome(java.io.IOException("hn:3: internet is off for this script"), denied).switchOff)
        assertFalse(outcome(RuntimeException("no stories"), denied).switchOff)
        assertFalse(outcome(RuntimeException("script timed out after 60s"), RunStop.Timeout).switchOff)
        assertFalse(outcome(RuntimeException("bad value"), line = 12).switchOff)
    }

    @Test
    fun aReplRunHasNoLineToEdit() {
        val o = outcome(RuntimeException("bad value"), repl = true, line = 12)
        assertEquals("hn: bad value", o.line)
        assertEquals(mapOf(LogContext.COPY to "bad value"), o.extra)
    }

    @Test
    fun theMessageIsMasked() {
        val o = outcome(
            RuntimeException("GET https://x?key=s3cret failed"),
            redact = { it.replace("s3cret", "•••") })
        assertEquals("hn: GET https://x?key=••• failed", o.line)
        assertEquals("GET https://x?key=••• failed", o.extra[LogContext.COPY])
    }

    @Test
    fun noMessageGivesTheErrorsKind() {
        val o = outcome(IllegalArgumentException())
        assertEquals("hn: IllegalArgumentException", o.line)
        assertEquals(mapOf(LogContext.COPY to "IllegalArgumentException"), o.extra)
    }

    @Test
    fun aLongMessageIsCut() {
        val o = outcome(RuntimeException("x".repeat(500)))
        assertEquals("error: " + "x".repeat(ScriptRepository.STATUS_MAX), o.status)
    }
}
