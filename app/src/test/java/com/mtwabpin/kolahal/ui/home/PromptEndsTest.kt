package com.mtwabpin.kolahal.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How far the prompt's ends move in for the screen's rounded bottom corners. The numbers are a phone's
 * at density 2.625: the ends keep 12 dp (32 px) from the edge, and 4 dp (11 px) past a corner.
 */
class PromptEndsTest {
    /** At the screen's bottom the corners hide 33 px over the prompt's text: each end moves 12 px in, to 44 px. */
    @Test
    fun anEndStandsPastWhatTheCornerHides() {
        assertEquals(12, PromptEnds.extra(33, 32, 11))
        assertEquals(1, PromptEnds.extra(22, 32, 11))
    }

    /** Above a gesture line the corner hides 1 px: the 12 dp are already clear of it, so nothing moves. */
    @Test
    fun aCornerWithinThePaddingMovesNothing() {
        assertEquals(0, PromptEnds.extra(1, 32, 11))
        assertEquals(0, PromptEnds.extra(21, 32, 11))
        // Square corners, or a line above them.
        assertEquals(0, PromptEnds.extra(0, 32, 11))
    }
}
