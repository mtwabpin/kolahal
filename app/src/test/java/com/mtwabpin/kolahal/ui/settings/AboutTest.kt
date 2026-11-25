package com.mtwabpin.kolahal.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings → About: the home app's row. */
class AboutTest {
    /** Android's dialog is asked only while Kolahal isn't the home app, and only where Android has it. */
    @Test
    fun theDialogIsOnlyForMakingKolahalTheHomeApp() {
        assertTrue(asksForHome(isHome = false, sdk = 29))
        assertTrue(asksForHome(isHome = false, sdk = 36))
        // Android 9 has no dialog: its page for the home app opens.
        assertFalse(asksForHome(isHome = false, sdk = 28))
        // Once Kolahal is the home app the dialog does nothing: the page is where another is chosen.
        assertFalse(asksForHome(isHome = true, sdk = 36))
        assertFalse(asksForHome(isHome = true, sdk = 28))
    }

    /** The row says which of the two states it is in, and what a tap does in each. */
    @Test
    fun theRowSaysBothStates() {
        assertNotEquals(homeAppLine(true), homeAppLine(false))
        assertTrue(homeAppLine(true).contains("is your home app"))
        assertTrue(homeAppLine(true).endsWith("tap to change"))
        assertTrue(homeAppLine(false).contains("isn't your home app"))
        assertTrue(homeAppLine(false).endsWith("tap to make it"))
    }
}
