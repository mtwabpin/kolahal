package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where the header's time, date and items go beside the camera, and what's left out. */
class HeaderLayoutTest {
    @Test fun aCutoutSplitsTheLine() {
        assertEquals(listOf(10..989), HeaderLayout.zones(1000, 10, emptyList(), 5))
        // A hole in the middle, kept 5 clear on each side.
        assertEquals(listOf(10..474, 526..989), HeaderLayout.zones(1000, 10, listOf(480..520), 5))
        // One in the corner leaves nothing before it.
        assertEquals(listOf(46..989), HeaderLayout.zones(1000, 10, listOf(0..40), 5))
    }

    /** The time and date start the first stretch; the items end the last one, in their order. */
    @Test fun theTimeGoesLeftAndTheItemsRight() {
        val p = HeaderLayout.place(listOf(10..474, 526..989), time = 100, date = 120, items = listOf(60, 40, 80), spacing = 10)
        assertEquals(10, p.time)
        assertEquals(120, p.date)
        assertEquals(listOf(790, 860, 910), p.items)
    }

    /** Without a cutout: one stretch, the items after the time and date, against its end. */
    @Test fun withoutACutoutTheyShareTheLine() {
        val p = HeaderLayout.place(listOf(10..409), time = 100, date = 120, items = listOf(60, 80), spacing = 10)
        assertEquals(10, p.time)
        assertEquals(listOf(260, 330), p.items)
    }

    /** What doesn't fit goes: the second item first, then the first, the battery last. */
    @Test fun itemsAreLeftOutFromTheSecond() {
        val zones = listOf(10..409)
        // 240 for the time and date, 160 left: 60 + 10 + 80 fit, 50 more don't.
        var p = HeaderLayout.place(zones, time = 100, date = 120, items = listOf(60, 50, 80), spacing = 10)
        assertEquals(listOf(260, null, 330), p.items)
        p = HeaderLayout.place(zones, time = 100, date = 120, items = listOf(90, 50, 80), spacing = 10)
        assertEquals(listOf(null, null, 330), p.items)
        p = HeaderLayout.place(zones, time = 100, date = 120, items = listOf(90, 50, 200), spacing = 10)
        assertEquals(listOf(null, null, null), p.items)
    }

    /** With only room for the time before the camera, the date goes rather than the time moving past it. */
    @Test fun theDateGoesBeforeTheTimeMoves() {
        val p = HeaderLayout.place(listOf(10..160, 200..400), time = 100, date = 120, items = listOf(60), spacing = 10)
        assertEquals(10, p.time)
        assertNull(p.date)
        assertEquals(listOf(341), p.items)
    }

    /** A cutout in the corner: the time starts after it, and the items still end the line. */
    @Test fun aCornerCutoutMovesTheTime() {
        val zones = HeaderLayout.zones(500, 10, listOf(0..40), 5)
        val p = HeaderLayout.place(zones, time = 100, date = 120, items = listOf(60), spacing = 10)
        assertEquals(46, p.time)
        assertEquals(156, p.date)
        assertEquals(listOf(430), p.items)
    }
}
