package com.mtwabpin.kolahal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The `rose-pine` theme uses Rosé Pine's colours (rosepinetheme.com), and both versions read well. */
class RosePineThemeTest {
    // Rosé Pine's main palette, by its own numbers.
    private val base = Color(0xFF191724)
    private val overlay = Color(0xFF26233A)
    private val subtle = Color(0xFF908CAA)
    private val text = Color(0xFFE0DEF4)
    private val love = Color(0xFFEB6F92)
    private val gold = Color(0xFFF6C177)
    private val rose = Color(0xFFEBBCBA)
    private val pine = Color(0xFF31748F)
    private val foam = Color(0xFF9CCFD8)
    private val iris = Color(0xFFC4A7E7)

    // Rosé Pine Dawn.
    private val dawnBase = Color(0xFFFAF4ED)
    private val dawnOverlay = Color(0xFFF2E9E1)
    private val dawnText = Color(0xFF464261)
    private val dawnPine = Color(0xFF286983)

    /** A colour's place on the colour wheel, in degrees. */
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
        assertEquals(Palettes.rosePine, Palettes.named("rose-pine"))
        assertEquals(Palettes.rosePine, Palettes.named("rose-pine", dark = true))
        assertEquals(Palettes.rosePineLight, Palettes.named("rose-pine", dark = false))
    }

    @Test
    fun theDarkVersionIsRosePine() {
        val p = Palettes.rosePine
        assertEquals(base, p.bg)
        assertEquals(overlay, p.surface)
        assertEquals(text, p.fg)
        assertEquals(subtle, p.dim)
        assertEquals(rose, p.prompt)
        assertEquals(rose, p.personal)
        assertEquals(love, p.error)
        assertEquals(foam, p.info)
        assertEquals(foam, p.link)
        assertEquals(gold, p.script)
        assertEquals(gold, p.work)
        assertEquals(gold, p.warn)
        assertEquals(iris, p.private)
        assertEquals(pine, p.good)
    }

    /** Dawn behind, Dawn's text, and each role in the hue of its Dawn colour, darker where Dawn's is hard to read. */
    @Test
    fun theLightVersionIsDawn() {
        val p = Palettes.rosePineLight
        assertEquals(dawnBase, p.bg)
        assertEquals(dawnOverlay, p.surface)
        assertEquals(dawnText, p.fg)
        assertEquals(dawnPine, p.good)
        val dawnSubtle = Color(0xFF797593)
        val dawnLove = Color(0xFFB4637A)
        val dawnGold = Color(0xFFEA9D34)
        val dawnRose = Color(0xFFD7827E)
        val dawnFoam = Color(0xFF56949F)
        val dawnIris = Color(0xFF907AA9)
        val dawn = mapOf(
            "muted" to dawnSubtle,
            "accent" to dawnRose,
            "personal" to dawnRose,
            "error" to dawnLove,
            "info" to dawnFoam,
            "link" to dawnFoam,
            "script" to dawnGold,
            "work" to dawnGold,
            "warn" to dawnGold,
            "private" to dawnIris,
        )
        val light = mapOf("muted" to p.dim) + accents(p).toMap()
        assertEquals(light.keys - "good", dawn.keys)
        for ((role, own) in dawn) {
            val c = light.getValue(role)
            val apart = abs(hue(own) - hue(c)).let { minOf(it, 360 - it) }
            assertTrue("$role: the hue moved $apart degrees", apart < 2f)
            assertTrue("$role is darker", c.luminance() < own.luminance())
        }
    }

    /** Text and muted text read well in both versions, and so does each accent but pine, Rosé Pine's dark green. */
    @Test
    fun bothVersionsAreReadable() {
        for ((version, p) in listOf(
            "dark" to Palettes.rosePine,
            "light" to Palettes.rosePineLight
        )) {
            assertTrue("$version text", contrast(p.fg, p.bg) >= 7f)
            assertTrue("$version muted", contrast(p.dim, p.bg) >= 4.5f)
            assertTrue(
                "$version: muted text is fainter than text",
                contrast(p.dim, p.bg) < contrast(p.fg, p.bg) - 2f
            )
            for ((role, c) in accents(p)) {
                val least = if (version == "dark" && role == "good") 3f else 4.5f
                assertTrue("$version $role", contrast(c, p.bg) >= least)
            }
        }
    }
}
