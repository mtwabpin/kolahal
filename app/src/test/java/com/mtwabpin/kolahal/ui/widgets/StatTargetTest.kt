package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.ui.widgets.StatWide.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a stat's target goes, what its bar shows past it ([StatTarget]), and the room the target and a badge take in a wide card ([StatWide]). */
class StatTargetTest {
    @Test
    fun theTargetIsWrittenAfterOf() {
        assertEquals("of 8,000", StatTarget.words("8,000"))
    }

    /** A 4×1 card has the target after the value; a 2×1 one has no room there. */
    @Test
    fun aWideCardHasItAfterTheValueFromItsWidth() {
        assertTrue(StatTarget.afterValue(363f))
        assertFalse(StatTarget.afterValue(161f))
    }

    @Test
    fun aLargeCardHasItAfterTheValueWhenTheLineHoldsBoth() {
        assertTrue(StatTarget.fitsAfter(161f, 80f, 60f))
        // 120 + 4 + 60 is more than the line.
        assertFalse(StatTarget.fitsAfter(161f, 120f, 60f))
    }

    /** Only a stat with a target goes past 1, and the mark is where the target is on the full bar. */
    @Test
    fun pastTheTargetTheBarHasAMark() {
        assertTrue(StatTarget.over("8,000", 1.12f))
        assertFalse(StatTarget.over("8,000", 1f))
        assertFalse(StatTarget.over("8,000", 0.78f))
        assertFalse(StatTarget.over(null, 1.12f))
        assertFalse(StatTarget.over("8,000", null))
        assertEquals(0.5f, StatTarget.mark(2f), 0.001f)
        assertEquals(0.893f, StatTarget.mark(1.12f), 0.001f)
    }

    /** Without a target or a badge, the split is as it always was. */
    @Test
    fun withNeitherTheSplitIsAsBefore() {
        assertEquals(StatWide.split(350f, 3, label = true), StatWide.split(350f, 3, true, targetAfter = false, targetUnder = false, badge = false))
        assertEquals(Split(column = false, between = 2, inColumn = false), StatWide.split(300f, 2, label = false))
    }

    /** A target after the value takes room from the smaller values beside it. */
    @Test
    fun aTargetAfterTheValueTakesRoomBesideIt() {
        assertEquals(Split(column = true, between = 2, inColumn = true), StatWide.split(363f, 3, label = true))
        assertEquals(Split(column = true, between = 1, inColumn = true), StatWide.split(363f, 3, label = true, targetAfter = true))
    }

    /** A target under the label takes the next smaller value's place, and needs the column even without a label. */
    @Test
    fun aTargetUnderTheLabelTakesTheNextValuesPlace() {
        assertEquals(Split(column = true, between = 0, inColumn = false), StatWide.split(161f, 2, label = true, targetUnder = true))
        assertEquals(Split(column = true, between = 0, inColumn = false), StatWide.split(161f, 0, label = false, targetUnder = true))
    }

    /** A badge sits at the top of the end column, so the card has one even without a label. */
    @Test
    fun aBadgeKeepsTheColumn() {
        assertEquals(Split(column = true, between = 1, inColumn = true), StatWide.split(300f, 2, label = false, badge = true))
        // Under 150 dp there is no column: the badge goes at the end of the value's line.
        assertEquals(Split(column = false, between = 0, inColumn = false), StatWide.split(140f, 2, label = true, badge = true))
    }
}
