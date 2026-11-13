package com.mtwabpin.kolahal.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** How `device.unlocks(days)` reads its argument ([unlockDays]). The counts themselves are read on a device. */
class UnlockDaysTest {
    /** No argument is the call from before: today's count as a number, not a list. */
    @Test fun noArgumentAsksForNoList() {
        assertEquals(null, unlockDays(null))
    }

    /** Lua passes every number as a double. JavaScript passes a whole number as an int or a long. */
    @Test fun aNumberIsThatManyDays() {
        assertEquals(7, unlockDays(7.0))
        assertEquals(7, unlockDays(7))
        assertEquals(7, unlockDays(7L))
        assertEquals(1, unlockDays(1))
        assertEquals(MAX_UNLOCK_DAYS, unlockDays(30))
    }

    @Test fun aFractionIsDropped() {
        assertEquals(7, unlockDays(7.9))
        assertEquals(1, unlockDays(1.5))
    }

    /** As the `unlocks` command does: at least today, at most 30 days. */
    @Test fun outOfRangeIsHeldAtTheNearestEnd() {
        assertEquals(1, unlockDays(0))
        assertEquals(1, unlockDays(-3))
        assertEquals(1, unlockDays(0.2))
        assertEquals(30, unlockDays(31))
        assertEquals(30, unlockDays(365.0))
        assertEquals(30, unlockDays(Double.POSITIVE_INFINITY))
        assertEquals(1, unlockDays(Double.NEGATIVE_INFINITY))
        assertEquals(1, unlockDays(Double.NaN))
    }

    @Test fun anythingElseIsAnErrorThatSaysWhatToPass() {
        for (wrong in listOf("7", "week", true, listOf(7), mapOf("days" to 7))) {
            val e = assertThrows(wrong.toString(), IllegalArgumentException::class.java) { unlockDays(wrong) }
            assertEquals("device.unlocks: days must be a number from 1 to 30", e.message)
        }
    }
}
