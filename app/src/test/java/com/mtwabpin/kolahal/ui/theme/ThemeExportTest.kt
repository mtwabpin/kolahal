package com.mtwabpin.kolahal.ui.theme

import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.MaterialYou
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * site/src/data/themes.json, the export of the built-in themes that every picture drawn off the phone
 * takes its colors from (docs/pictures.org), matches [Palettes]. When this fails, run
 * `node tools/export-themes.mjs` and draw the pictures again.
 */
class ThemeExportTest {
    /** The export, from where the tests run: `app/` under Gradle, the repository's root in some IDEs. */
    private val export: JSONObject by lazy {
        val path = "site/src/data/themes.json"
        val file = listOf(File("../$path"), File(path)).firstOrNull { it.exists() }
            ?: error("$path isn't here or one folder up")
        JSONObject(file.readText())
    }

    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

    /** Every built-in theme, material-you without its variations; the default; the roles in the builder's order. */
    @Test
    fun theExportHasEveryBuiltInTheme() {
        val expected = Keys.BUILT_IN_THEMES.filter { it !in MaterialYou.VARIANTS || it == MaterialYou.NAME }
        assertEquals(expected.toSet(), export.getJSONObject("themes").keys().asSequence().toSet())
        assertEquals(Keys.THEME.default, export.getString("default"))
        val roles = export.getJSONArray("roles")
        assertEquals(CustomThemes.ROLES, List(roles.length()) { roles.getString(it) })
    }

    /** Each theme's dark and light palette has the app's color in every role (material-you: Material's standard colors). */
    @Test
    fun eachPaletteIsTheApps() {
        val themes = export.getJSONObject("themes")
        for (name in themes.keys()) {
            for ((mode, dark) in listOf("dark" to true, "light" to false)) {
                val colors = themes.getJSONObject(name).getJSONObject(mode)
                val app = Palettes.colorsOf(Palettes.named(name, dark))
                assertEquals("$name $mode: roles", app.keys, colors.keys().asSequence().toSet())
                for ((role, argb) in app) assertEquals("$name $mode $role", hex(argb), colors.getString(role))
            }
        }
    }
}
