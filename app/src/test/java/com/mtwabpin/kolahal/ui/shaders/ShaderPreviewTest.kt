package com.mtwabpin.kolahal.ui.shaders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a small box shows a whole screen ([ScreenThumb]). */
class ShaderPreviewTest {
    /** A phone's window in a box 60 dp wide: its proportions, and the density inside that fits its width in dp. */
    @Test
    fun aPhoneFitsTheBox() {
        val thumb = ScreenThumb.of(1080, 2340, 3f, 60f)
        assertEquals(1080f / 2340f, thumb.aspect, 1e-6f)
        assertEquals(1f / 6f, thumb.scale, 1e-6f)
        // The box is 180 × 390 px, the size the shader is drawn at: a sixth of the window each way.
        val boxW = 60f * 3f
        assertEquals(180f, boxW, 1e-3f)
        assertEquals(390f, boxW / thumb.aspect, 1e-3f)
        // Inside, the box is as many dp wide as the window: 360.
        assertEquals(360f, boxW / (3f * thumb.scale), 1e-3f)
    }

    /** A large phone at another density: the window's width in dp still fills the box. */
    @Test
    fun aLargePhoneFitsTheBox() {
        val thumb = ScreenThumb.of(1440, 3120, 3.5f, 60f)
        assertEquals(1440f / 3120f, thumb.aspect, 1e-6f)
        assertEquals(1440f / 3.5f, 60f * 3.5f / (3.5f * thumb.scale), 1e-2f)
    }

    /** A window on its side, or a wide screen, is stood upright: the box is never wider than tall. */
    @Test
    fun aWideWindowStandsUpright() {
        assertEquals(ScreenThumb.of(1080, 2340, 3f, 60f), ScreenThumb.of(2340, 1080, 3f, 60f))
        val wide = ScreenThumb.of(2208, 1840, 2.5f, 60f)
        assertEquals(1840f / 2208f, wide.aspect, 1e-6f)
        assertTrue(wide.aspect <= 1f)
        assertEquals(60f * 2.5f / 1840f, wide.scale, 1e-6f)
    }

    /** Before the window has a size, a phone's screen stands in for it. */
    @Test
    fun anUnknownWindowIsAPhone() {
        val thumb = ScreenThumb.of(0, 0, 3f, 60f)
        assertEquals(ScreenThumb.PHONE_W_DP / ScreenThumb.PHONE_H_DP, thumb.aspect, 1e-6f)
        assertEquals(60f / 360f, thumb.scale, 1e-6f)
        assertEquals(thumb, ScreenThumb.of(1080, 2340, 0f, 60f))
    }
}
