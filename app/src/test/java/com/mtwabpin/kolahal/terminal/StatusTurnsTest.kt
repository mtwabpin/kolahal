package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.StatusLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a Status card's line runs again for its `refresh`, on its own and beside the pinned header ([StatusTurns]). */
class StatusTurnsTest {
    @Test fun aRefreshIsDueAtOnceAndThenWhenItIsOld() {
        // It has not run for its refresh yet: now.
        assertEquals(0L, StatusTurns.waitMs(30, null, 5_000L))
        // It ran 10 seconds ago, and asks for every 30.
        assertEquals(20_000L, StatusTurns.waitMs(30, 100_000L, 110_000L))
        assertEquals(0L, StatusTurns.waitMs(30, 100_000L, 130_000L))
        assertEquals(0L, StatusTurns.waitMs(30, 100_000L, 900_000L))
        // Just after a run, the whole gap is still to wait. So the card's loop never spins.
        assertEquals(30_000L, StatusTurns.waitMs(30, 100_000L, 100_000L))
    }

    @Test fun aRefreshIsNeverFasterThanAStatusLineAllows() {
        assertEquals(10_000L, StatusTurns.waitMs(1, 100_000L, 100_000L))
        assertEquals(10_000L, StatusTurns.waitMs(0, 100_000L, 100_000L))
        assertEquals(3_600_000L, StatusTurns.waitMs(99_999, 100_000L, 100_000L))
        // A last run in the future (the clock started again) waits one gap at most.
        assertEquals(30_000L, StatusTurns.waitMs(30, 500_000L, 100_000L))
    }

    /** A card that asks again and again runs its script once in each period. */
    @Test fun aCardRunsItsScriptOnceInEachPeriod() {
        val runs = HashMap<String, Long>()
        assertTrue("never ran: now", StatusTurns.due(runs, "cricket", 30, 100_000L))
        assertEquals(mapOf("cricket" to 100_000L), runs)
        assertFalse(StatusTurns.due(runs, "cricket", 30, 100_000L))
        assertFalse(StatusTurns.due(runs, "cricket", 30, 129_999L))
        assertEquals("a question that is not due changes nothing", mapOf("cricket" to 100_000L), runs)
        assertTrue(StatusTurns.due(runs, "cricket", 30, 130_000L))
        assertEquals(mapOf("cricket" to 130_000L), runs)
        // Another script has its own time.
        assertTrue(StatusTurns.due(runs, "football", 30, 131_000L))
    }

    /**
     * A script that is pinned and also has a Status card: the header and the card go by one table
     * of last runs, as in the view model. So the script runs once in each period, not twice.
     */
    @Test fun aPinnedLineAndItsCardShareOneTimer() {
        val line = StatusLine("cricket", "NZ 136/8", refresh = 30)
        val runs = HashMap<String, Long>()
        var count = 0
        /** The header's question, once a second (TerminalViewModel.refreshPinned). */
        fun header(now: Long) = PinnedTurns.due(listOf(line), runs, now).forEach { runs[it] = now; count++ }
        /** The card's question. */
        fun card(now: Long) { if (StatusTurns.due(runs, line.name, 30, now)) count++ }

        // The header asks first. The card, a moment later, finds the run done and waits the rest of the period.
        header(100_000L)
        card(100_200L)
        assertEquals(1, count)
        assertEquals(29_800L, StatusTurns.waitMs(30, runs[line.name], 100_200L))
        // The card asks first at the next period. The header then leaves the line alone.
        card(130_000L)
        header(130_500L)
        header(131_500L)
        assertEquals(2, count)
        // Both ask for two more minutes: one run each 30 seconds.
        for (t in 132_000L..250_000L step 500L) { header(t); card(t + 100) }
        assertEquals(6, count)
    }
}
