package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the home style changes outside home's drawing: which settings apply. */
class HomeStylesTest {
    @Test
    fun theStyleIsTtyOnlyByItsName() {
        assertTrue(HomeStyles.isTty("tty"))
        assertFalse(HomeStyles.isTty("gui"))
        assertFalse(HomeStyles.isTty(Keys.HOME_STYLE.default))
        assertTrue(HomeStyles.TTY in Keys.HOME_STYLE.choices)
    }

    /** log.item-spacing does nothing on the grid: Settings and `config` leave it out there, and only there. */
    @Test
    fun itemSpacingIsAGuiSetting() {
        assertFalse(HomeStyles.applies(Keys.LOG_ITEM_SPACING, tty = true))
        assertTrue(HomeStyles.applies(Keys.LOG_ITEM_SPACING, tty = false))
        // The gap above each command and its output stays in both.
        assertTrue(HomeStyles.applies(Keys.LOG_SPACING, tty = true))
        assertEquals(Keys.ALL.size - HomeStyles.GUI_ONLY.size, Keys.ALL.count { HomeStyles.applies(it, tty = true) })
        assertTrue(Keys.ALL.all { HomeStyles.applies(it, tty = false) })
        // The grid's own gap: none between a list's items.
        assertEquals(0, HomeStyles.TTY_ITEM_GAP_DP)
    }

    /** log.taller-taps makes a line taller, which a grid of whole rows can't show: it is a gui setting too. */
    @Test
    fun tallerTapsIsAGuiSetting() {
        assertFalse(HomeStyles.applies(Keys.LOG_TALLER_TAPS, tty = true))
        assertTrue(HomeStyles.applies(Keys.LOG_TALLER_TAPS, tty = false))
        assertEquals(setOf("log.item-spacing", "log.taller-taps"), HomeStyles.GUI_ONLY)
    }

    /** On the grid log.spacing is a whole line or nothing: any value but 0 is one empty line. */
    @Test
    fun spacingIsOneLineOnTheGrid() {
        assertEquals(0, HomeStyles.ttyGroupGapRows(0))
        for (dp in listOf(1, Keys.LOG_SPACING.default, 20, Keys.LOG_SPACING.range.last)) {
            assertEquals("$dp dp", 1, HomeStyles.ttyGroupGapRows(dp))
        }
    }
}
