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

    private val sky = Color(0xFFE8E2DF)
    private val ridge = Color(0xFFE1DBD9)
    private val sun = Color(0xFF9C5E5B)
    private val ink = Color(0xFF464261)

    @Test
    fun theIconsColorsOnADarkTheme() {
        // Rose Pine's page.
        val dark = Color(0xFF191724)
        assertTrue(EmptyPageArt.isDark(dark))
        assertEquals(EmptyPageArt.ICON, EmptyPageArt.colors(dark, sky, ridge, sun, ink))
        assertEquals(Color(0xFF3A3350), EmptyPageArt.ICON.sky)
    }

    @Test
    fun theThemesRolesOnALightTheme() {
        // Rose Pine Dawn's page.
        val light = Color(0xFFFAF4ED)
        assertFalse(EmptyPageArt.isDark(light))
        assertEquals(EmptyPageArt.Colors(sky = sky, sun = sun, ridge = ridge, ink = ink), EmptyPageArt.colors(light, sky, ridge, sun, ink))
    }
}
