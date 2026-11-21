package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGateTest {
    private var clock = 10_000L
    /** Blocks the gate holds back, each with the time it is due. */
    private val due = ArrayList<Pair<Long, () -> Unit>>()
    private val gate = TapGate(2_000, later = { ms, block -> due += (clock + ms) to block }) { clock }

    /** Moves the clock to [to], running the held-back blocks that are due by then. */
    private fun advance(to: Long) {
        while (true) {
            val next = due.filter { it.first <= to }.minByOrNull { it.first } ?: break
            due.remove(next)
            clock = next.first
            next.second()
        }
        clock = to
    }

    /** Two quick taps on a widget's button run twice: the second when the first one's 2 seconds end. */
    @Test fun aQuickSecondTapWaitsAndRuns() {
        val runs = ArrayList<Long>()
        gate.runOrWait("widget:counter:add") { runs += clock }
        clock += 300
        gate.runOrWait("widget:counter:add") { runs += clock }
        assertEquals(listOf(10_000L), runs)
        advance(13_000)
        assertEquals(listOf(10_000L, 12_000L), runs)
    }

    /** One tap waits at most: a third quick tap is dropped. */
    @Test fun aThirdQuickTapIsDropped() {
        var runs = 0
        repeat(3) { gate.runOrWait("widget:counter:add") { runs++ }; clock += 200 }
        advance(20_000)
        assertEquals(2, runs)
    }

    /** The waiting tap starts a new window when it runs: a tap just after it waits again. */
    @Test fun theWaitingTapStartsANewWindow() {
        val runs = ArrayList<Long>()
        gate.runOrWait("widget:counter:add") { runs += clock }
        clock += 500
        gate.runOrWait("widget:counter:add") { runs += clock }
        advance(12_100)
        gate.runOrWait("widget:counter:add") { runs += clock }
        assertEquals(listOf(10_000L, 12_000L), runs)
        advance(15_000)
        assertEquals(listOf(10_000L, 12_000L, 14_000L), runs)
    }

    /** A tap after the window runs at once, as with allow. */
    @Test fun aLateTapRunsAtOnce() {
        var runs = 0
        gate.runOrWait("widget:counter:add") { runs++ }
        clock += 2_000
        gate.runOrWait("widget:counter:add") { runs++ }
        assertEquals(2, runs)
        assertTrue(due.isEmpty())
    }

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
