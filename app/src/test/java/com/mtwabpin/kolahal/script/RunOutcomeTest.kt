package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.ScriptRepository
import org.junit.Assert.assertEquals
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
