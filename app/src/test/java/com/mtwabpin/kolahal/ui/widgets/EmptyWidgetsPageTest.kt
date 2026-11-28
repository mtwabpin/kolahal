package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The empty widgets page's picture: when it shows and in which colors. */
class EmptyWidgetsPageTest {
    @Test
    fun aShortPageLeavesThePictureOut() {
        assertTrue(EmptyPageArt.showsPicture(480f))
        assertTrue(EmptyPageArt.showsPicture(760f))
        assertFalse(EmptyPageArt.showsPicture(479.5f))
        // A phone on its side.
        assertFalse(EmptyPageArt.showsPicture(360f))
    }

    private val high = Color(0xFF26233A)
    private val highest = Color(0xFF2A2740)
    private val lowest = Color(0xFF110F1A)
    private val sun = Color(0xFFEBBCBA)
    private val ink = Color(0xFFE0DEF4)

    @Test
    fun theThemesRolesOnADarkTheme() {
        // A dark theme's page: the ridge is the darkest container, under a lighter sky.
        val dark = Color(0xFF191724)
        assertTrue(EmptyPageArt.isDark(dark))
        assertEquals(
            EmptyPageArt.Colors(sky = high, sun = sun, ridge = lowest, ink = ink),
            EmptyPageArt.colors(dark, high, highest, lowest, sun, ink),
        )
    }

    @Test
    fun theThemesRolesOnALightTheme() {
        // A light theme's page: the ridge is the darkest container there, the highest one.
        val light = Color(0xFFFAF4ED)
        assertFalse(EmptyPageArt.isDark(light))
        assertEquals(
            EmptyPageArt.Colors(sky = high, sun = sun, ridge = highest, ink = ink),
            EmptyPageArt.colors(light, high, highest, lowest, sun, ink),
        )
    }
}
