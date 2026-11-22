package com.mtwabpin.kolahal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The `nord` theme uses Nord's colors (nordtheme.com), and both versions read well. */
class NordThemeTest {
    // Polar Night, Snow Storm, Frost and Aurora, by Nord's own numbers.
    private val nord0 = Color(0xFF2E3440)
    private val nord1 = Color(0xFF3B4252)
    private val nord4 = Color(0xFFD8DEE9)
    private val nord6 = Color(0xFFECEFF4)
    private val nord7 = Color(0xFF8FBCBB)
    private val nord8 = Color(0xFF88C0D0)
    private val nord9 = Color(0xFF81A1C1)
    private val nord11 = Color(0xFFBF616A)
    private val nord12 = Color(0xFFD08770)
    private val nord13 = Color(0xFFEBCB8B)
    private val nord14 = Color(0xFFA3BE8C)
    private val nord15 = Color(0xFFB48EAD)

    /** A color's place on the color wheel, in degrees. */
    private fun hue(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val range = max - minOf(c.red, c.green, c.blue)
        return 60 * when (max) {
            c.red -> ((c.green - c.blue) / range).mod(6f)
            c.green -> (c.blue - c.red) / range + 2
            else -> (c.red - c.green) / range + 4
        }
    }

    @Test
    fun theNameGivesBothVersions() {
        assertEquals(Palettes.nord, Palettes.named("nord"))
        assertEquals(Palettes.nord, Palettes.named("nord", dark = true))
        assertEquals(Palettes.nordLight, Palettes.named("nord", dark = false))
    }

    @Test
    fun theDarkVersionIsNord() {
        val p = Palettes.nord
        assertEquals(nord0, p.bg)
        assertEquals(nord1, p.surface)
        assertEquals(nord4, p.fg)
        assertEquals(nord8, p.prompt)
        assertEquals(nord8, p.personal)
        assertEquals(nord7, p.info)
        assertEquals(nord9, p.link)
        assertEquals(nord11, p.error)
        assertEquals(nord12, p.work)
        assertEquals(nord13, p.script)
        assertEquals(nord13, p.warn)
        assertEquals(nord14, p.good)
        assertEquals(nord15, p.private)
    }

    /** Snow Storm behind, Polar Night text, and each accent in the hue it has in the dark version, only darker. */
    @Test
    fun theLightVersionKeepsNordsHues() {
        val dark = Palettes.nord
        val light = Palettes.nordLight
        assertEquals(nord6, light.bg)
        assertEquals(nord4, light.surface)
        assertEquals(nord0, light.fg)
        for ((d, l) in accents(dark).zip(accents(light))) {
            val role = d.first
            val apart = abs(hue(d.second) - hue(l.second)).let { minOf(it, 360 - it) }
            assertTrue("$role: the hue moved $apart degrees", apart < 2f)
            assertTrue("$role is darker", l.second.luminance() < d.second.luminance())
        }
    }

    /** The dark version too: the other tests check only the light ones. */
    @Test
    fun bothVersionsAreReadable() {
        for ((version, p) in listOf("dark" to Palettes.nord, "light" to Palettes.nordLight)) {
            assertTrue("$version text", contrast(p.fg, p.bg) >= 7f)
            assertTrue("$version muted", contrast(p.dim, p.bg) >= 4f)
            assertTrue(
                "$version: muted text is fainter than text",
                contrast(p.dim, p.bg) < contrast(p.fg, p.bg) - 2f
            )
            for ((role, c) in accents(p)) assertTrue("$version $role", contrast(c, p.bg) >= 3f)
        }
    }
}
