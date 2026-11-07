package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the header's two sides go beside the camera, and what's left out. */
class HeaderLayoutTest {
    @Test fun aCutoutSplitsTheLine() {
        assertEquals(listOf(10..989), HeaderLayout.zones(1000, 10, emptyList(), 5))
        // A hole in the middle, kept 5 clear on each side.
        assertEquals(listOf(10..474, 526..989), HeaderLayout.zones(1000, 10, listOf(480..520), 5))
        // One in the corner leaves nothing before it.
        assertEquals(listOf(46..989), HeaderLayout.zones(1000, 10, listOf(0..40), 5))
    }

    /** The left side starts the first stretch; the right side ends the last one, in its order. */
    @Test fun theSidesGoToTheirEnds() {
        val p = HeaderLayout.place(listOf(10..474, 526..989), left = listOf(100, 120), right = listOf(60, 40, 80), spacing = 10)
        assertEquals(listOf(10, 120), p.left)
        assertEquals(listOf(790, 860, 910), p.right)
    }

    /** Without a cutout: one stretch, the right side after the left, against its end. */
    @Test fun withoutACutoutTheyShareTheLine() {
        val p = HeaderLayout.place(listOf(10..409), left = listOf(100, 120), right = listOf(60, 80), spacing = 10)
        assertEquals(listOf(10, 120), p.left)
        assertEquals(listOf(260, 330), p.right)
    }

    /** What doesn't fit goes from the middle out: the right side's first part first, its last (outermost) last. */
    @Test fun theRightSideLosesItsInnerPartsFirst() {
        val zones = listOf(10..409)
        // 240 for the left side, 160 left: 50 + 10 + 80 fit, 60 more don't.
        var p = HeaderLayout.place(zones, left = listOf(100, 120), right = listOf(60, 50, 80), spacing = 10)
        assertEquals(listOf(null, 270, 330), p.right)
        p = HeaderLayout.place(zones, left = listOf(100, 120), right = listOf(60, 90, 80), spacing = 10)
        assertEquals(listOf(null, null, 330), p.right)
        p = HeaderLayout.place(zones, left = listOf(100, 120), right = listOf(60, 90, 200), spacing = 10)
        assertEquals(listOf(null, null, null), p.right)
    }

    /** With room for only its first part before the camera, the left side loses its last rather than moving past it. */
    @Test fun theLeftSideLosesItsLastBeforeItMoves() {
        val p = HeaderLayout.place(listOf(10..160, 200..400), left = listOf(100, 120), right = listOf(60), spacing = 10)
        assertEquals(listOf(10, null), p.left)
        assertEquals(listOf(341), p.right)
    }

    /** A cutout in the corner: the left side starts after it, and the right side still ends the line. */
    @Test fun aCornerCutoutMovesTheLeftSide() {
        val zones = HeaderLayout.zones(500, 10, listOf(0..40), 5)
        val p = HeaderLayout.place(zones, left = listOf(100, 120), right = listOf(60), spacing = 10)
        assertEquals(listOf(46, 156), p.left)
        assertEquals(listOf(430), p.right)
    }

    /** Any parts on either side: the battery on the left, the time on the right, or a side left empty. */
    @Test fun eitherSideCanHoldAnythingOrNothing() {
        var p = HeaderLayout.place(listOf(10..409), left = listOf(80), right = listOf(100), spacing = 10)
        assertEquals(listOf(10), p.left)
        assertEquals(listOf(310), p.right)
        p = HeaderLayout.place(listOf(10..409), left = emptyList(), right = listOf(100, 120), spacing = 10)
        assertEquals(emptyList<Int?>(), p.left)
        assertEquals(listOf(180, 290), p.right)
        p = HeaderLayout.place(listOf(10..409), left = listOf(100, 120), right = emptyList(), spacing = 10)
        assertEquals(listOf(10, 120), p.left)
    }

    /** The left side's first part shows even where it doesn't fit, as the time always has. */
    @Test fun theFirstPartAlwaysShows() {
        val p = HeaderLayout.place(listOf(10..59, 70..109), left = listOf(100), right = listOf(20), spacing = 10)
        assertEquals(listOf(10), p.left)
        assertEquals(listOf(90), p.right)
    }
}
