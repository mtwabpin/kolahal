package com.mtwabpin.kolahal.ui.shaders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the buttons of a shader's details keep their words, and when they are icons ([wordsFit]). */
class SheetActionsTest {
    // A phone 384 dp wide leaves the sheet's line about 336 dp, with 12 dp between two buttons, and a
    // pressed button takes 15 % of its width from a neighbor.
    private fun fit(count: Int, widest: Float, room: Float = 336f) = wordsFit(room, widest, count, gap = 12f, grows = 0.15f)

    /** Check, Share and Copy fit with their words; with Remove, a longer word and a fourth button, they don't. */
    @Test
    fun threeFitOnAPhoneAndFourDont() {
        // "Share" as a button is about 80 dp wide, "Remove" about 92.
        assertTrue(fit(3, 80f))
        assertFalse(fit(4, 92f))
        // Four short words don't fit either: each button has 75 dp.
        assertFalse(fit(4, 80f))
    }

    /**
     * With Edit, a shader that comes with Kolahal has four buttons and one of the user's five: on a phone both are
     * icons. A wide screen has room for five short words.
     */
    @Test
    fun withEditFourAndFiveAreIconsOnAPhone() {
        // "Edit" as a button is about 70 dp wide, so "Share" is still the widest of a bundled one's four.
        assertFalse(fit(4, 80f))
        assertFalse(fit(5, 92f))
        assertFalse(fit(5, 70f))
        // Five in 600 dp have 110 dp each, 94 beside a pressed one.
        assertTrue(fit(5, 92f, room = 600f))
    }

    /** A wide screen has room for four, and a very narrow one not even for three. */
    @Test
    fun theRoomDecides() {
        assertTrue(fit(4, 92f, room = 600f))
        assertFalse(fit(3, 80f, room = 280f))
    }

    /** A neighbor of a pressed button gives up some width, and must still hold its word. */
    @Test
    fun aPressedNeighborsWordStillFits() {
        // Three in 300 dp have 92 dp each: enough for a word of 80 at rest, not for one of 90 beside a pressed button.
        assertTrue(wordsFit(300f, 78f, 3, gap = 12f, grows = 0.15f))
        assertFalse(wordsFit(300f, 90f, 3, gap = 12f, grows = 0.15f))
        assertTrue(wordsFit(300f, 90f, 3, gap = 12f, grows = 0f))
    }

    @Test
    fun noneFits() = assertTrue(wordsFit(0f, 80f, 0, gap = 12f, grows = 0.15f))
}
