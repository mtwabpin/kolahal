package com.mtwabpin.kolahal.ui.tty

import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.ui.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `tty.crt` on every built-in theme, dark and light: it shows, and text stays as readable as the theme makes it. */
class CrtLookTest {
    private class Theme(val name: String, val dark: Boolean) {
        val palette = TtyPalette(Palettes.named(name, dark))
        override fun toString() = "$name " + if (dark) "dark" else "light"
    }

    private val themes = Keys.BUILT_IN_THEMES.flatMap { listOf(Theme(it, true), Theme(it, false)) }

    /**
     * Averaged over a scanline period, every ink keeps 90 % of its contrast with the background,
     * and so does it against the vignette's darkest corner. Body text stays at 4.5:1 or more.
     */
    @Test
    fun textKeepsItsContrast() {
        for (theme in themes) for (period in 2..4) {
            val look = CrtLook.of(theme.palette, period)
            val bg = theme.palette.background
            val corner = CrtLook.over(BLACK, bg, look.vignetteAlpha)
            for (ink in CrtLook.TEXT_INKS) {
                val color = theme.palette.color(ink)
                val before = CrtLook.contrast(color, bg)
                val scanned = CrtLook.scanned(color, bg, look.scanAlpha, period)
                assertTrue("$theme $ink, period $period: $scanned of $before", scanned >= CrtLook.KEEP * before)
                assertTrue("$theme $ink in the corners", CrtLook.contrast(color, corner) >= CrtLook.KEEP * before)
            }
            val body = CrtLook.scanned(theme.palette.color(Ink.FG), bg, look.scanAlpha, period)
            assertTrue("$theme: body text at $body", body >= 4.5)
        }
    }

    /** Every theme gets scanlines that show, never stronger than the most the look takes. */
    @Test
    fun theLookShowsOnEveryTheme() {
        for (theme in themes) {
            val look = CrtLook.of(theme.palette, 3)
            assertTrue("$theme: scanlines at ${look.scanAlpha}", look.scanAlpha in 0.05f..CrtLook.MAX_SCAN)
            assertTrue("$theme: vignette at ${look.vignetteAlpha}", look.vignetteAlpha in 0.02f..CrtLook.MAX_VIGNETTE)
        }
    }

    /**
     * On a dark theme the vignette, under the text, only darkens the background behind it, so it
     * takes its most. Only dark themes glow: on a light one no text is bright.
     */
    @Test
    fun darkThemesGlowAndGetTheWholeVignette() {
        for (theme in themes) {
            val look = CrtLook.of(theme.palette, 3)
            assertEquals(theme.toString(), theme.dark, look.glow)
            if (theme.dark) assertEquals(theme.toString(), CrtLook.MAX_VIGNETTE, look.vignetteAlpha, 0f)
            else assertTrue(theme.toString(), look.vignetteAlpha < CrtLook.MAX_VIGNETTE)
        }
    }

    /** The scanline is the theme's background, so it changes nothing over the background; the vignette is black. */
    @Test
    fun theColors() {
        val palette = TtyPalette(Palettes.named("green", true))
        val look = CrtLook.of(palette, 3)
        assertEquals(palette.background and 0xFFFFFF, look.scanColor and 0xFFFFFF)
        assertEquals((look.scanAlpha * 255).toInt().toDouble(), (look.scanColor ushr 24).toDouble(), 1.0)
        assertEquals(0, look.vignetteColor and 0xFFFFFF)
        assertEquals(3, look.period)
        assertEquals(2, CrtLook.of(palette, 1).period)
        // A scanline over the background is the background.
        assertEquals(palette.background, CrtLook.over(palette.background, palette.background, look.scanAlpha))
    }

    @Test
    fun contrastIsWcags() {
        assertEquals(21.0, CrtLook.contrast(BLACK, WHITE), 0.01)
        assertEquals(1.0, CrtLook.contrast(WHITE, WHITE), 0.0)
        // Gray #777777 on white is 4.48:1.
        assertEquals(4.48, CrtLook.contrast(0xFF777777.toInt(), WHITE), 0.01)
        assertFalse(CrtLook.luminance(BLACK) > 0.0)
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
