package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.Pipes
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository.Pending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `grep` on Android's regex engine, which is native: the one under the JVM tests isn't. */
class GrepOnDeviceTest {
    private fun grep(pattern: String, text: String) =
        Pipes.apply(listOf(Pending(LogKind.OUTPUT, text, null, LogContext.EMPTY, 0L)), listOf("grep $pattern"))

    /** This pattern backtracks for several seconds on this line, then ends by itself. */
    @Test fun aRunawayPatternStopsBeingWaitedFor() {
        val started = System.currentTimeMillis()
        val failure = grep("(a+)+x", "a".repeat(26) + "!").exceptionOrNull()
        val took = System.currentTimeMillis() - started
        assertTrue("$failure", failure?.message.orEmpty().contains("too slow"))
        // Given up on at its limit (with room for a slow device), not after all the backtracking.
        assertTrue("took ${took}ms", took < 10_000)
        // The next one isn't held up.
        assertEquals(listOf("alpha"), grep("alp", "alpha").getOrThrow().map { it.text })
    }
}
