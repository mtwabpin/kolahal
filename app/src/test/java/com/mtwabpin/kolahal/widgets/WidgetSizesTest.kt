package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizesTest {
    /** A page 360 dp wide is 344 dp inside its margins. A cell there is 80 dp wide, so a row is the least a row may be. */
    @Test fun aRowIsAtLeastItsMinimum() {
        val page = 344
        assertEquals(80, WidgetLayout.widthDp(page, WidgetSize.S1X1.span))
        assertEquals(WidgetSizes.MIN_ROW_DP, WidgetSizes.heightDp(WidgetSize.S1X1, page))
        assertEquals(88, WidgetSizes.heightDp(WidgetSize.S2X1, page))
        assertEquals(88, WidgetSizes.heightDp(WidgetSize.S4X1, page))
        assertEquals(2 * 88 + 8, WidgetSizes.heightDp(WidgetSize.S2X2, page))
        assertEquals(2 * 88 + 8, WidgetSizes.heightDp(WidgetSize.S4X2, page))
        assertEquals(4 * 88 + 3 * 8, WidgetSizes.heightDp(WidgetSize.S4X4, page))
        // A page of no width, as before the first layout.
        assertEquals(88, WidgetSizes.heightDp(WidgetSize.S1X1, 0))
    }

    /** On a wide page a row is as tall as a cell is wide, so 1×1 is square. */
    @Test fun aRowIsAsTallAsACellIsWide() {
        val page = 600
        val cell = WidgetLayout.widthDp(page, 3)
        assertEquals(144, cell)
        assertEquals(cell, WidgetSizes.heightDp(WidgetSize.S1X1, page))
        assertEquals(2 * cell + WidgetLayout.GAP_DP, WidgetSizes.heightDp(WidgetSize.S2X2, page))
        assertEquals(4 * cell + 3 * WidgetLayout.GAP_DP, WidgetSizes.heightDp(WidgetSize.S4X4, page))
    }

    /** Two 1-row widgets, one over the other with the gap between, are as tall as one 2-row widget beside them. */
    @Test fun rowsLineUpWithTheGap() {
        for (page in listOf(0, 300, 344, 395, 600, 1200)) {
            val one = WidgetSizes.heightDp(WidgetSize.S2X1, page)
            assertEquals(2 * one + WidgetLayout.GAP_DP, WidgetSizes.heightDp(WidgetSize.S2X2, page))
            assertEquals(2 * WidgetSizes.heightDp(WidgetSize.S4X2, page) + WidgetLayout.GAP_DP, WidgetSizes.heightDp(WidgetSize.S4X4, page))
        }
    }

    /** Every size starts no smaller than a script widget may be, and no taller than a widget may be. */
    @Test fun everySizeStartsWithinTheLimits() {
        for (size in WidgetSize.entries) {
            assertTrue(size.span >= WidgetSizes.MIN_SPAN)
            assertTrue(size.span <= WidgetLayout.COLUMNS)
            for (page in listOf(0, 344, 600)) {
                assertTrue(WidgetSizes.heightDp(size, page) >= WidgetSizes.MIN_HEIGHT_DP)
                assertTrue(WidgetSizes.heightDp(size, page) <= WidgetLayout.MAX_DP)
            }
        }
        assertTrue(WidgetSizes.MIN_SPAN >= WidgetLayout.MIN_SPAN)
        assertTrue(WidgetSizes.MIN_HEIGHT_DP >= WidgetLayout.MIN_DP)
    }

    /** The card draws for the box it gets: large when it is tall, else wide when it is wide, else small. */
    @Test fun theBoxDecidesTheLayout() {
        assertEquals(WidgetSizes.Box.SMALL, WidgetSizes.box(80, 88))
        assertEquals(WidgetSizes.Box.SMALL, WidgetSizes.box(149, 149))
        assertEquals(WidgetSizes.Box.WIDE, WidgetSizes.box(150, 149))
        assertEquals(WidgetSizes.Box.WIDE, WidgetSizes.box(344, 88))
        assertEquals(WidgetSizes.Box.LARGE, WidgetSizes.box(149, 150))
        assertEquals(WidgetSizes.Box.LARGE, WidgetSizes.box(80, 400))
        assertEquals(WidgetSizes.Box.LARGE, WidgetSizes.box(344, 184))
        assertEquals(WidgetSizes.Box.SMALL, WidgetSizes.box(0, 0))
        // The sizes as placed on a 360 dp phone.
        val page = 344
        fun box(size: WidgetSize) = WidgetSizes.box(WidgetLayout.widthDp(page, size.span), WidgetSizes.heightDp(size, page))
        assertEquals(WidgetSizes.Box.SMALL, box(WidgetSize.S1X1))
        assertEquals(WidgetSizes.Box.WIDE, box(WidgetSize.S2X1))
        assertEquals(WidgetSizes.Box.LARGE, box(WidgetSize.S2X2))
        assertEquals(WidgetSizes.Box.WIDE, box(WidgetSize.S4X1))
        assertEquals(WidgetSizes.Box.LARGE, box(WidgetSize.S4X2))
        assertEquals(WidgetSizes.Box.LARGE, box(WidgetSize.S4X4))
    }
}
