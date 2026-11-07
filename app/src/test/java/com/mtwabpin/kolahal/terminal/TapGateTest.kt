package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGateTest {
    private var clock = 10_000L
    private val gate = TapGate(2_000) { clock }

    /** Five taps in one second run once. */
    @Test fun quickTapsCountAsOne() {
        val runs = (0 until 5).count { i -> clock = 10_000L + i * 200; gate.allow("pin:aqi") }
        assertEquals(1, runs)
    }

    /** Once 2 seconds have passed since the tap that ran, the next one runs. */
    @Test fun aTapAfterTheWindowRuns() {
        assertTrue(gate.allow("pin:aqi"))
        clock += 1_999
        assertFalse(gate.allow("pin:aqi"))
        clock += 1
        assertTrue(gate.allow("pin:aqi"))
    }

    /** A held-back tap doesn't move the window: steady taps run every 2 seconds, not never. */
    @Test fun heldBackTapsDontExtendTheWindow() {
        val runs = (0 until 20).count { i -> clock = 10_000L + i * 500; gate.allow("pin:aqi") }
        assertEquals(5, runs)
    }

    /** Each item has its own window. */
    @Test fun itemsDontHoldEachOtherBack() {
        assertTrue(gate.allow("pin:aqi"))
        assertTrue(gate.allow("pin:cricket"))
        assertTrue(gate.allow("header:todo"))
        assertFalse(gate.allow("pin:aqi"))
    }
}
