package com.mtwabpin.kolahal.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** How much of a line near the top of the screen its rounded corners hide. */
class ScreenCornersTest {
    /** A phone 1080 px wide whose corners have a radius of 101 px, centered 101 px from each edge. */
    private val left = ScreenCorner(101, 101, 101)
    private val right = ScreenCorner(101, 1080 - 101, 101)

    /** Its bottom corners, on a screen 2340 px tall: centered 101 px up from the bottom. */
    private val bottomLeft = ScreenCorner(101, 101, 2340 - 101)
    private val bottomRight = ScreenCorner(101, 1080 - 101, 2340 - 101)

    /** The header's text from 24 to 58 px: at 24 px the curve is 101 − √(101² − 77²) ≈ 35.6 px in. */
    @Test
    fun aLineBesideTheCameraLosesItsEnds() {
        assertEquals(36, ScreenCorners.hiddenLeft(left, 24, 58))
        assertEquals(36, ScreenCorners.hiddenRight(right, 24, 58, edge = 1080))
        // Lower text loses less: from 30 px, 101 − √(101² − 71²) ≈ 29.2.
        assertEquals(30, ScreenCorners.hiddenLeft(left, 30, 64))
        // Only the span's top counts, in whichever order it comes.
        assertEquals(36, ScreenCorners.hiddenLeft(left, 58, 24))
    }

    /** Measured from a line's own ends: a line that starts 10 px in loses 10 px less. */
    @Test
    fun fromTheLinesEnds() {
        assertEquals(26, ScreenCorners.hiddenLeft(left, 24, 58, edge = 10))
        assertEquals(26, ScreenCorners.hiddenRight(right, 24, 58, edge = 1070))
        // A line that starts past the curve loses nothing.
        assertEquals(0, ScreenCorners.hiddenLeft(left, 24, 58, edge = 40))
        assertEquals(0, ScreenCorners.hiddenRight(right, 24, 58, edge = 1040))
    }

    @Test
    fun belowTheCornerNothingIsHidden() {
        assertEquals(0, ScreenCorners.hiddenLeft(left, 101, 140))
        assertEquals(0, ScreenCorners.hiddenRight(right, 101, 140, edge = 1080))
        assertEquals(0, ScreenCorners.hiddenLeft(left, 300, 340))
    }

    @Test
    fun squareCornersHideNothing() {
        assertEquals(0, ScreenCorners.hiddenLeft(null, 0, 40))
        assertEquals(0, ScreenCorners.hiddenRight(null, 0, 40, edge = 1080))
        assertEquals(0, ScreenCorners.hiddenLeft(ScreenCorner(0, 0, 0), 0, 40))
        assertEquals(0, ScreenCorners.hiddenRight(ScreenCorner(0, 1080, 0), 0, 40, edge = 1080))
    }

    /** Rows above the circle are outside it: the corner hides them out to its center. */
    @Test
    fun aboveTheCircleTheWholeSquareIsHidden() {
        val low = ScreenCorner(101, 101, 300)
        assertEquals(101, ScreenCorners.hiddenLeft(low, 24, 58))
        assertEquals(101, ScreenCorners.hiddenRight(ScreenCorner(101, 979, 300), 24, 58, edge = 1080))
    }

    /**
     * The prompt's text from 2261 to 2313 px, the line at the screen's bottom: at 2313 the curve is
     * 101 − √(101² − 74²) ≈ 32.3 px in.
     */
    @Test
    fun aLineAtTheBottomLosesItsEnds() {
        assertEquals(33, ScreenCorners.hiddenBottomLeft(bottomLeft, 2261, 2313))
        assertEquals(33, ScreenCorners.hiddenBottomRight(bottomRight, 2261, 2313, edge = 1080))
        // Only the span's bottom counts, in whichever order it comes.
        assertEquals(33, ScreenCorners.hiddenBottomLeft(bottomLeft, 2313, 2261))
        // Above a gesture line of 63 px the text ends at 2250, 11 px under the center: the curve is ≈ 0.6 px in.
        assertEquals(1, ScreenCorners.hiddenBottomLeft(bottomLeft, 2198, 2250))
        assertEquals(1, ScreenCorners.hiddenBottomRight(bottomRight, 2198, 2250, edge = 1080))
        // Measured from a line's own ends.
        assertEquals(23, ScreenCorners.hiddenBottomLeft(bottomLeft, 2261, 2313, edge = 10))
        assertEquals(23, ScreenCorners.hiddenBottomRight(bottomRight, 2261, 2313, edge = 1070))
    }

    /** A line above the bottom corners' centers (the keyboard up, or room under it) loses nothing. */
    @Test
    fun aboveTheBottomCornersNothingIsHidden() {
        assertEquals(0, ScreenCorners.hiddenBottomLeft(bottomLeft, 2180, 2239))
        assertEquals(0, ScreenCorners.hiddenBottomRight(bottomRight, 2180, 2239, edge = 1080))
        assertEquals(0, ScreenCorners.hiddenBottomLeft(bottomLeft, 1400, 1452))
        assertEquals(0, ScreenCorners.hiddenBottomLeft(null, 2261, 2313))
        assertEquals(0, ScreenCorners.hiddenBottomRight(ScreenCorner(0, 1080, 2340), 2261, 2313, edge = 1080))
        // A top corner hides nothing at the bottom, and a bottom corner nothing at the top.
        assertEquals(0, ScreenCorners.hiddenLeft(left, 2261, 2313))
        assertEquals(0, ScreenCorners.hiddenBottomLeft(bottomLeft, 24, 58))
    }

    /** Rows under a bottom corner's circle are outside it: the corner hides them out to its center. */
    @Test
    fun underTheCircleTheWholeSquareIsHidden() {
        assertEquals(101, ScreenCorners.hiddenBottomLeft(ScreenCorner(101, 101, 2000), 2200, 2250))
        assertEquals(101, ScreenCorners.hiddenBottomRight(ScreenCorner(101, 979, 2000), 2200, 2250, edge = 1080))
    }
}
