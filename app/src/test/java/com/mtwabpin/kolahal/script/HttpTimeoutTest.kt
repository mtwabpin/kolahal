package com.mtwabpin.kolahal.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** A request's `timeout` ([HostApi.requestTimeoutMs]) and the room a run makes for it ([HostApi.stretchedEnd]). */
class HttpTimeoutTest {
    @Test
    fun missingKeepsTheUsualWait() {
        assertNull(HostApi.requestTimeoutMs(null))
    }

    @Test
    fun secondsBecomeMilliseconds() {
        assertEquals(45_000L, HostApi.requestTimeoutMs(45L))
        assertEquals(45_000L, HostApi.requestTimeoutMs(45))
        assertEquals(2_500L, HostApi.requestTimeoutMs(2.5))
    }

    @Test
    fun cappedAtSixtySeconds() {
        assertEquals(60_000L, HostApi.requestTimeoutMs(120L))
        assertEquals(60_000L, HostApi.requestTimeoutMs(Double.POSITIVE_INFINITY))
        assertEquals(1_000L, HostApi.requestTimeoutMs(0))
        assertEquals(1_000L, HostApi.requestTimeoutMs(-5.0))
    }

    @Test
    fun notANumberIsAnError() {
        for (v in listOf("60", true, Double.NaN)) {
            try {
                HostApi.requestTimeoutMs(v)
                fail("expected an error for $v")
            } catch (e: IllegalArgumentException) {
                assertEquals("http.request: timeout is a number of seconds", e.message)
            }
        }
    }

    @Test
    fun aRunMakesRoomForTheWait() {
        val start = 1_000_000L
        val usualEnd = start + ScriptWorker.TIMEOUT_MS
        // A minute's wait asked a second in: the wait and ten seconds after it.
        assertEquals(start + 71_000, HostApi.stretchedEnd(start + 1_000, 60_000, usualEnd, start))
        // A wait that fits in the run changes nothing.
        assertEquals(usualEnd, HostApi.stretchedEnd(start + 1_000, 20_000, usualEnd, start))
        // Never past twice the usual limit, however many requests ask.
        assertEquals(start + HostApi.MAX_RUN_MS, HostApi.stretchedEnd(start + 100_000, 60_000, start + 110_000, start))
        // Never earlier than it was.
        assertEquals(start + 110_000, HostApi.stretchedEnd(start + 1_000, 1_000, start + 110_000, start))
    }
}
