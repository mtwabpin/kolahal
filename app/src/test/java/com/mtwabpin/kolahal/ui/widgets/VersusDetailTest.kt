package com.mtwabpin.kolahal.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A versus side's detail ("3rd · 41 pts"): where it fits under the side's name, and that it gives
 * way before the last line and the badge do ([VersusWords.rowPlan], [VersusWords.detailFits]).
 */
class VersusDetailTest {
    private val plain = listOf(36, 32, 28)

    /**
     * A 4×1 card 92 dp tall is 68 dp inside; the bar takes 18. A name (20) with its detail (16)
     * fits beside the score, which keeps the plan it has without a detail.
     */
    @Test
    fun aOneRowCardHasTheDetailUnderTheName() {
        val plan = VersusWords.rowPlan(68, plain, 18, 18, sideDp = 36)
        assertEquals(VersusWords.RowPlan(1, line = true, detail = true), plan)
        assertEquals(VersusWords.rowPlan(68, plain, 18, 18), plan.copy(detail = false))
        // Without a bar or a last line, it fits too.
        assertTrue(VersusWords.rowPlan(68, plain, 0, 0, sideDp = 36).detail)
    }

    /** Where the row is short, the detail goes first: it never shows where the last line doesn't. */
    @Test
    fun theDetailGivesWayBeforeTheLastLine() {
        // At a larger font size the line gives way, and the detail with it, though the side has the height.
        val big = VersusWords.rowPlan(68, listOf(46, 41, 36), 23, 23, sideDp = 44)
        assertFalse(big.line)
        assertFalse(big.detail)
        // The name and its detail are taller than the room over the bar.
        assertFalse(VersusWords.rowPlan(48, listOf(24), 0, 18, sideDp = 36).detail)
        // Without a detail, nothing changes.
        assertEquals(VersusWords.rowPlan(68, plain, 18, 18, 16), VersusWords.rowPlan(68, plain, 18, 18, 16, 0))
    }

    /**
     * A large card's faces: the mark (40), 4 dp, the name (20) and the detail (16) need 80 dp. A
     * Split tile at 4×1 (76 dp inside) holds them over no bar, but not with a figure under them.
     */
    @Test
    fun theDetailFitsUnderTheRestOfItsColumn() {
        assertTrue(VersusWords.detailFits(96, 64, 16))
        assertFalse(VersusWords.detailFits(72, 64, 16))
        assertTrue(VersusWords.detailFits(76, 26 + 2 + 20, 16))
        assertFalse(VersusWords.detailFits(76, 26 + 2 + 20 + 20, 16))
    }

    /**
     * On a one-row card a detail shows only whole: a 371 dp row with a 133 dp middle leaves each
     * side 119 dp, 36 of them for the mark and its gap, so a 62 dp detail fits; beside a middle
     * that also holds the age (260 dp) it would be cut to a few letters, so it gives way.
     */
    @Test
    fun aOneRowDetailShowsOnlyWhole() {
        assertTrue(VersusWords.detailWhole(371f, 133f, 36f, 62f))
        assertFalse(VersusWords.detailWhole(371f, 260f, 36f, 62f))
    }
}
