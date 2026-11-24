package com.mtwabpin.kolahal.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tour turns the app selector on the first time its page opens, and never against a choice after that. */
class TourSelectorTest {
    @Test
    fun theFirstOpeningTurnsItOn() {
        assertTrue(TourSelector.turnsOn(shown = false, on = false))
    }

    @Test
    fun anOpeningAfterThatLeavesItAsItIs() {
        assertFalse(TourSelector.turnsOn(shown = true, on = false))
        assertFalse(TourSelector.turnsOn(shown = true, on = true))
    }

    @Test
    fun aSelectorAlreadyOnStaysAsItIs() {
        assertFalse(TourSelector.turnsOn(shown = false, on = true))
    }
}
