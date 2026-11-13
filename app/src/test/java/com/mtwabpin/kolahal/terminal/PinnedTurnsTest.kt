package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.StatusLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pinned header's turns, its dots, and which items run again. */
class PinnedTurnsTest {
    @Test fun dotsShowForTwoUpToTheSetting() {
        assertFalse("one item needs none", PinnedTurns.showsDots(1, 3))
        assertTrue(PinnedTurns.showsDots(2, 3))
        assertTrue(PinnedTurns.showsDots(3, 3))
        assertFalse("more than the setting: none", PinnedTurns.showsDots(4, 3))
        assertTrue(PinnedTurns.showsDots(7, 10))
        assertFalse("0: never", PinnedTurns.showsDots(2, 0))
    }

    @Test fun turnsGoRoundInOrder() {
        val keys = listOf("media", "script:cricket", "script:football")
        assertEquals("script:cricket", PinnedTurns.next(keys, "media"))
        assertEquals("script:football", PinnedTurns.next(keys, "script:cricket"))
        assertEquals("media", PinnedTurns.next(keys, "script:football"))
        assertEquals("media", PinnedTurns.next(listOf("media"), "media"))
        assertNull(PinnedTurns.next(emptyList(), null))
    }

    /** An item that went is shown as the first, and the turn after it is the second. */
    @Test fun aGoneItemFallsBackToTheFirst() {
        val keys = listOf("script:a", "script:b", "script:c")
        assertEquals(0, PinnedTurns.index(keys, "script:gone"))
        assertEquals(0, PinnedTurns.index(keys, null))
        assertEquals(2, PinnedTurns.index(keys, "script:c"))
        assertEquals("script:b", PinnedTurns.next(keys, "script:gone"))
    }

    @Test fun onlyItemsAskingForARefreshRunAgain() {
        fun pin(name: String, refresh: Int?) = StatusLine(name, "x", null, refresh)
        val pins = listOf(pin("still", null), pin("cricket", 10), pin("football", 30))
        // Never run yet: at once.
        assertEquals(listOf("cricket", "football"), PinnedTurns.due(pins, emptyMap(), 1_000_000))
        val last = mapOf("cricket" to 1_000_000L, "football" to 1_000_000L)
        assertEquals(emptyList<String>(), PinnedTurns.due(pins, last, 1_009_999))
        assertEquals(listOf("cricket"), PinnedTurns.due(pins, last, 1_010_000))
        assertEquals(listOf("cricket", "football"), PinnedTurns.due(pins, last, 1_030_000))
    }
}
