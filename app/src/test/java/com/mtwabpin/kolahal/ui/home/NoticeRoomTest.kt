package com.mtwabpin.kolahal.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a scroll lifts the log into the room under the notice, and lowers it again. */
class NoticeRoomTest {
    @Test
    fun aScrollUpTheListCannotTakeLiftsTheLogUpToTheNotice() {
        // Up is negative, as a finger moving up the screen.
        assertEquals(30f to -30f, NoticeRoom.raise(0f, 120f, -30f))
        assertEquals(120f to -20f, NoticeRoom.raise(100f, 120f, -50f))
        // At the notice's height, the rest goes on to the list's overscroll.
        assertEquals(120f to 0f, NoticeRoom.raise(120f, 120f, -10f))
        // Down lifts nothing.
        assertEquals(40f to 0f, NoticeRoom.raise(40f, 120f, 25f))
    }

    @Test
    fun aScrollDownLowersTheLogBeforeTheListScrolls() {
        assertEquals(10f to 30f, NoticeRoom.lower(40f, 30f))
        // Lowered all the way: the rest is the list's.
        assertEquals(0f to 40f, NoticeRoom.lower(40f, 100f))
        // Not lifted, or a scroll up: the list takes it all.
        assertEquals(0f to 0f, NoticeRoom.lower(0f, 30f))
        assertEquals(40f to 0f, NoticeRoom.lower(40f, -30f))
        // A settle's spring a little under 0 isn't lowered further.
        assertEquals(-2f to 0f, NoticeRoom.lower(-2f, 30f))
    }
}
