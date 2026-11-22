package com.mtwabpin.kolahal.ui.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A see-through picture whose lines barely show on the theme's background gets a backdrop; a picture with a background of its own never does. */
class PictureBackdropTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val clear = 0x00000000
    private val darkTheme = Color(0xFF1A1A1A)
    private val lightTheme = Color(0xFFF8F8F8)

    /** [ink] pixels on [clear] pixels: a drawing with nothing behind it. */
    private fun drawing(ink: Int, inkShare: Float = 0.2f) =
        IntArray(1000) { if (it < 1000 * inkShare) ink else clear }

    @Test
    fun aPictureWithABackgroundOfItsOwnHasNoInk() {
        assertNull(PictureInk.of(IntArray(1000) { black }))
        // A photo with a few see-through pixels at its corners still has a background.
        assertNull(PictureInk.of(IntArray(1000) { if (it < 30) clear else white }))
        assertNull(PictureInk.of(IntArray(0)))
        assertNull(PictureInk.of(IntArray(1000) { clear }))
    }

    @Test
    fun aDrawingsInkIsHowLightItsLinesAre() {
        assertEquals(0f, PictureInk.of(drawing(black))!!.luminance, 0.001f)
        assertEquals(1f, PictureInk.of(drawing(white))!!.luminance, 0.001f)
        // Half-see-through edges count for less than solid lines.
        val edges = IntArray(1000) {
            when {
                it < 100 -> black; it < 200 -> 0x40FFFFFF; else -> clear
            }
        }
        assertTrue(PictureInk.of(edges)!!.luminance < 0.3f)
    }

    @Test
    fun darkLinesOnADarkThemeGetALightBackdrop() {
        val ink = PictureInk.of(drawing(black))
        assertEquals(PICTURE_LIGHT, pictureBackdrop(ink, darkTheme))
        assertNull("they read on a light theme", pictureBackdrop(ink, lightTheme))
    }

    @Test
    fun lightLinesOnALightThemeGetADarkBackdrop() {
        val ink = PictureInk.of(drawing(white))
        assertEquals(PICTURE_DARK, pictureBackdrop(ink, lightTheme))
        assertNull("they read on a dark theme", pictureBackdrop(ink, darkTheme))
    }

    @Test
    fun aPictureWithABackgroundNeverGetsOne() {
        assertNull(pictureBackdrop(null, darkTheme))
        assertNull(pictureBackdrop(null, lightTheme))
    }
}
