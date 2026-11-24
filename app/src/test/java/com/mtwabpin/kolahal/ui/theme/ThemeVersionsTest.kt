package com.mtwabpin.kolahal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.ui.settings.ChoiceLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How far apart two colors are in brightness, from 1 (the same) to 21 (black on white). Text needs more than its background. */
internal fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}

/** A palette's colored roles by name: every role but the background, the surface, the text and the muted text. */
internal fun accents(p: Palette) = listOf(
    "accent" to p.prompt,
    "error" to p.error,
    "info" to p.info,
    "link" to p.link,
    "script" to p.script,
    "personal" to p.personal,
    "work" to p.work,
    "private" to p.private,
    "good" to p.good,
    "warn" to p.warn,
)

/** Every built-in theme has a dark and a light version, and both read well. */
class ThemeVersionsTest {
    @Test
    fun eachThemeHasADarkAndALightVersion() {
        for (name in Keys.BUILT_IN_THEMES) {
            val dark = Palettes.named(name, dark = true)
            val light = Palettes.named(name, dark = false)
            assertNotEquals(name, dark, light)
            assertTrue("$name: dark background", dark.bg.luminance() < 0.2f)
            assertTrue("$name: light background", light.bg.luminance() > 0.7f)
        }
        assertEquals(Palettes.gruvbox, Palettes.named("gruvbox"))
        assertEquals(
            "an unknown name is the default theme",
            Palettes.named(Keys.THEME.default, dark = false),
            Palettes.named("no-such-theme", dark = false),
        )
    }

    /**
     * rose-pine is the default theme: it comes first in the list, and the window shows its page
     * before the theme is read (start_window.xml in res/values and res/values-night), with bar icons
     * that show on it.
     */
    @Test
    fun rosePineIsTheDefault() {
        assertEquals("rose-pine", Keys.THEME.default)
        assertEquals(Keys.THEME.default, Keys.BUILT_IN_THEMES.first())
        for ((dir, dark) in listOf("values" to false, "values-night" to true)) {
            val xml = sourceFile("src/main/res/$dir/start_window.xml").readText()
            val hex = Regex("""<color name="start_background">#([0-9A-F]{6})</color>""").find(xml)
                ?.groupValues?.get(1) ?: error("$dir: no start_background")
            assertEquals(dir, Palettes.named(Keys.THEME.default, dark).bg, Color(0xFF000000 or hex.toLong(16)))
            assertTrue(dir, xml.contains("""<bool name="start_light_bars">${!dark}</bool>"""))
        }
    }

    @Test
    fun theLightVersionsAreReadable() {
        for (name in Keys.BUILT_IN_THEMES) {
            val p = Palettes.named(name, dark = false)
            assertTrue("$name text", contrast(p.fg, p.bg) >= 7f)
            assertTrue("$name muted", contrast(p.dim, p.bg) >= 4f)
            for ((role, c) in accents(p)) assertTrue("$name $role", contrast(c, p.bg) >= 3f)
        }
    }

    /** `theme <name>` and the theme cards take every built-in theme, and each has a name to show. */
    @Test
    fun everyBuiltInThemeCanBePicked() {
        assertEquals(
            listOf(
                "rose-pine",
                "green",
                "amber",
                "mono",
                "dracula",
                "solarized",
                "gruvbox",
                "gruvbox-material",
                "nord",
                "material-you",
                "material-you-soft",
                "material-you-tinted",
                "material-you-black"
            ),
            Keys.BUILT_IN_THEMES,
        )
        for (name in Keys.BUILT_IN_THEMES) assertEquals(name, Keys.THEME.parse(name))
        assertEquals("Nord", ChoiceLabels.theme("nord"))
        assertEquals("Rosé Pine", ChoiceLabels.theme("rose-pine"))
        assertEquals("Material You", ChoiceLabels.theme("material-you"))
        assertEquals("Material You Soft", ChoiceLabels.theme("material-you-soft"))
        assertEquals("Gruvbox Material", ChoiceLabels.theme("gruvbox-material"))
    }

    @Test
    fun theBuilderGetsBothVersions() {
        val colors = Palettes.themeColors("dracula")
        assertEquals(
            Palettes.colorsOf(Palettes.dracula),
            colors.filterKeys { it in CustomThemes.ROLES })
        assertEquals(Palettes.colorsOf(Palettes.draculaLight), CustomThemes.light(colors))
    }
}
