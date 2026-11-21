package com.mtwabpin.kolahal.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mtwabpin.kolahal.data.MaterialYou
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `material-you` theme: a Material colour scheme as a terminal palette ([paletteFrom]). */
class MaterialYouPalettesTest {
    private val primary = Color(0xFF010101)
    private val secondary = Color(0xFF020202)
    private val tertiary = Color(0xFF030303)
    private val error = Color(0xFF040404)
    private val surface = Color(0xFF050505)
    private val surfaceContainer = Color(0xFF060606)
    private val onSurface = Color(0xFF070707)
    private val onSurfaceVariant = Color(0xFF080808)
    private val outline = Color(0xFF090909)

    /** A scheme with its own colour in every role the palette takes, so a role taken from the wrong place shows. */
    private val scheme = darkColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        error = error,
        surface = surface,
        surfaceContainer = surfaceContainer,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
    )

    @Test
    fun eachColourComesFromItsMaterialRole() {
        val p = paletteFrom(scheme)
        assertEquals(surface, p.bg)
        assertEquals(surfaceContainer, p.surface)
        assertEquals(onSurface, p.fg)
        assertEquals(outline, p.dim)
        assertEquals(onSurfaceVariant, p.script)
        assertEquals(error, p.error)
        // Three accents, two jobs each.
        assertEquals(primary, p.prompt)
        assertEquals(primary, p.personal)
        assertEquals(secondary, p.info)
        assertEquals(secondary, p.private)
        assertEquals(tertiary, p.link)
        assertEquals(tertiary, p.work)
    }

    /**
     * The variations move only the background and the shade under cards: soft one step lighter,
     * tinted with the primary colour, black on black (its light version is the plain one). Text and
     * accents are the plain theme's, and muted text still reads.
     */
    @Test
    fun theVariationsMoveOnlyTheBackground() {
        for (s in listOf(darkColorScheme(), lightColorScheme())) {
            val plain = paletteFrom(s)
            val dark = s.surface.luminance() < 0.5f
            for (v in MaterialYou.VARIANTS) {
                val p = paletteFrom(s, v)
                assertEquals("$v", plain.copy(bg = p.bg, surface = p.surface, dim = p.dim), p)
                assertTrue("$v muted", contrast(p.dim, p.bg) >= 4f)
            }
            assertEquals(
                s.surfaceContainer to s.surfaceContainerHigh,
                paletteFrom(s, MaterialYou.SOFT).let { it.bg to it.surface })
            assertNotEquals(plain.bg, paletteFrom(s, MaterialYou.TINTED).bg)
            assertEquals(if (dark) Color.Black else plain.bg, paletteFrom(s, MaterialYou.BLACK).bg)
        }
    }

    /** Good and warn don't come from the wallpaper: one green and one amber for dark schemes, another pair for light ones. */
    @Test
    fun goodAndWarnAreFixed() {
        val dark = paletteFrom(darkColorScheme())
        val light = paletteFrom(lightColorScheme())
        assertEquals(dark.good, paletteFrom(scheme).good)
        assertEquals(dark.warn, paletteFrom(scheme).warn)
        assertEquals(
            light.good,
            paletteFrom(lightColorScheme(primary = primary, tertiary = tertiary)).good
        )
        assertEquals(
            light.warn,
            paletteFrom(lightColorScheme(primary = primary, tertiary = tertiary)).warn
        )
        assertNotEquals(dark.good, light.good)
        assertNotEquals(dark.warn, light.warn)
        for (p in listOf(dark, light)) {
            assertTrue("good is green", p.good.green > p.good.red && p.good.green > p.good.blue)
            assertTrue("warn is amber", p.warn.red > p.warn.green && p.warn.green > p.warn.blue)
            assertNotEquals("good isn't the error colour", p.error, p.good)
        }
    }

    /** [schemeFrom] makes the GUI screens' colours from the palette. They get the scheme's own accents back. */
    @Test
    fun theGuiScreensGetTheSchemesAccentsBack() {
        for (s in listOf(scheme, darkColorScheme(), lightColorScheme())) {
            val back = schemeFrom(paletteFrom(s))
            assertEquals(s.primary, back.primary)
            assertEquals(s.secondary, back.secondary)
            assertEquals(s.tertiary, back.tertiary)
            assertEquals(s.error, back.error)
            assertEquals(s.surface, back.surface)
            assertEquals(s.onSurface, back.onSurface)
            assertEquals(s.outline, back.outline)
        }
    }

    /** Material's standard colours, the ones Android 9 to 11 shows: both versions read well. */
    @Test
    fun materialsStandardColoursAreReadable() {
        for ((version, p) in listOf(
            "dark" to paletteFrom(darkColorScheme()),
            "light" to paletteFrom(lightColorScheme())
        )) {
            assertTrue("$version text", contrast(p.fg, p.bg) >= 7f)
            assertTrue("$version muted", contrast(p.dim, p.bg) >= 4f)
            assertTrue(
                "$version: muted text is fainter than script output",
                contrast(p.dim, p.bg) < contrast(p.script, p.bg)
            )
            assertTrue(
                "$version: script output is fainter than text",
                contrast(p.script, p.bg) < contrast(p.fg, p.bg)
            )
            for ((role, c) in accents(p)) assertTrue("$version $role", contrast(c, p.bg) >= 4.5f)
            assertNotEquals("$version: the surface stands out", p.bg, p.surface)
        }
    }

    /**
     * The website draws this theme in Material's standard colours (site/src/components/ThemeCards.astro
     * and the themes guide). If a new Material version changes them, change the website too.
     */
    @Test
    fun theWebsiteShowsMaterialsStandardColours() {
        fun shown(p: Palette) = listOf(p.bg, p.fg, p.dim, p.prompt, p.info, p.link, p.error)
        assertEquals(
            listOf(
                0xFF141218,
                0xFFE6E0E9,
                0xFF938F99,
                0xFFD0BCFF,
                0xFFCCC2DC,
                0xFFEFB8C8,
                0xFFF2B8B5
            ).map { Color(it) },
            shown(paletteFrom(darkColorScheme())),
        )
        assertEquals(
            listOf(
                0xFFFEF7FF,
                0xFF1D1B20,
                0xFF79747E,
                0xFF6750A4,
                0xFF625B71,
                0xFF7D5260,
                0xFFB3261E
            ).map { Color(it) },
            shown(paletteFrom(lightColorScheme())),
        )
    }

    /**
     * Before the phone's colours are read, and on Android 9 to 11 where there are none, the theme
     * shows Material's standard colours. A unit test runs with no phone, so it sees just that.
     */
    @Test
    fun withoutWallpaperColoursTheThemeShowsMaterialsStandardColours() {
        assertEquals(paletteFrom(darkColorScheme()), MaterialYouPalettes.of(dark = true))
        assertEquals(paletteFrom(lightColorScheme()), MaterialYouPalettes.of(dark = false))
        assertEquals(MaterialYouPalettes.of(dark = true), Palettes.named(MaterialYou.NAME))
        assertEquals(
            MaterialYouPalettes.of(dark = false),
            Palettes.named(MaterialYou.NAME, dark = false)
        )
        // The theme builder can start from it, like from any other theme.
        assertEquals(
            Palettes.colorsOf(MaterialYouPalettes.of(dark = true)),
            Palettes.themeColors(MaterialYou.NAME).filterKeys { !it.startsWith("light-") })
    }

    /** The theme's card says what the phone lacks, only for this theme and only where it lacks it. */
    @Test
    fun theCardSaysWhenThePhoneHasNoWallpaperColours() {
        assertEquals("Android 12+", ChoiceLabels.themeNote(MaterialYou.NAME, supported = false))
        assertNull(ChoiceLabels.themeNote(MaterialYou.NAME, supported = true))
        assertNull(ChoiceLabels.themeNote("nord", supported = false))
    }
}
