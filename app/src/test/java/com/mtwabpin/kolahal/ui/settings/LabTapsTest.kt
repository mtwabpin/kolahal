package com.mtwabpin.kolahal.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tapping Settings → About → Version 8 times in one go turns Lab on ([LabTaps]). */
class LabTapsTest {
    /** Taps [n] times, [gap] ms apart, from [start], with Lab off: what each said. */
    private fun LabTaps.taps(n: Int, start: Long = 1_000L, gap: Long = 300L) = (0 until n).map { tap(start + it * gap, on = false) }

    @Test
    fun eightTapsCountDownTheLastThreeAndTurnLabOn() {
        val said = LabTaps().taps(8)
        assertEquals(
            listOf(null, null, null, null, "3 more taps to turn on Lab", "2 more taps to turn on Lab", "1 more tap to turn on Lab", "Lab is on"),
            said.map { it.message },
        )
        assertEquals(listOf(false, false, false, false, false, false, false, true), said.map { it.turnsOn })
    }

    @Test
    fun aPauseStartsTheCountAgain() {
        val taps = LabTaps()
        taps.taps(6)
        // Longer than PAUSE_MS after the sixth tap: the first of a new go.
        val after = taps.tap(1_000L + 5 * 300L + LabTaps.PAUSE_MS + 1, on = false)
        assertNull(after.message)
        assertFalse(after.turnsOn)
        // A pause of exactly PAUSE_MS is still the same go.
        val same = LabTaps()
        same.taps(5)
        assertEquals("2 more taps to turn on Lab", same.tap(1_000L + 4 * 300L + LabTaps.PAUSE_MS, on = false).message)
    }

    @Test
    fun whileLabIsOnEachTapSaysSo() {
        val taps = LabTaps()
        repeat(10) {
            val t = taps.tap(1_000L + it * 100L, on = true)
            assertEquals(LabTaps.ALREADY_ON, t.message)
            assertFalse(t.turnsOn)
        }
        assertEquals("Lab is already on", LabTaps.ALREADY_ON)
    }

    @Test
    fun theCountStartsAgainAfterLabTurnsOn() {
        val taps = LabTaps()
        assertTrue(taps.taps(8).last().turnsOn)
        // Turned off again at once: 8 more taps are needed.
        val again = taps.taps(8, start = 5_000L)
        assertEquals(listOf(7), again.indices.filter { again[it].turnsOn })
    }

    @Test
    fun aClockThatStepsBackStartsAgain() {
        val taps = LabTaps()
        taps.taps(6)
        assertNull(taps.tap(10L, on = false).message)
    }
}
