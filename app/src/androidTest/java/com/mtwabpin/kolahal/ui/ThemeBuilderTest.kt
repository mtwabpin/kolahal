package com.mtwabpin.kolahal.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ConfigReset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Settings → Look & feel → Your themes: making, changing and deleting a theme. */
@RunWith(AndroidJUnit4::class)
class ThemeBuilderTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    @get:Rule val configReset = ConfigReset()
    private lateinit var vm: TerminalViewModel

    @After fun tearDown() {
        Term.palette = Palettes.green
        listOf("tb-test", "tb-test-2", "tb-renamed").forEach { CustomThemes.delete(app, it) }
        shared.delete()
    }

    /** The builder for [name] (null: a new theme), on a phone-sized frame, as Settings opens it. */
    private fun open(name: String?) {
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openThemeBuilder(name)
        }
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 760.dp).consumeWindowInsets(WindowInsets.safeDrawing)) { ThemeBuilderScreen(vm) }
            }
        }
    }

    private val shared = File(app.cacheDir, "shared theme.json")

    private fun name(text: String) = compose.onNodeWithTag("theme-name").performTextReplacement(text)
    private val save get() = compose.onNodeWithText("Save and apply")

    @Test fun aNewThemeStartsFromTheOneInUseAndIsUsedWhenSaved() {
        config.set(Keys.THEME, "dracula")
        open(null)
        compose.onNodeWithTag("theme-name").assert(hasText("my-theme"))
        name("tb-test")
        save.performClick()
        // Both of its versions.
        assertEquals(Palettes.themeColors("dracula"), CustomThemes.colors("tb-test"))
        assertEquals("tb-test", config[Keys.THEME])
        assertTrue(File(app.filesDir, "themes/tb-test.json").exists())
    }

    @Test fun startFromCopiesAnotherTheme() {
        open(null)
        compose.onNodeWithText("gruvbox").performScrollTo().performClick()
        name("tb-test")
        save.performClick()
        assertEquals(Palettes.themeColors("gruvbox"), CustomThemes.colors("tb-test"))
    }

    /** Dark and Light switch which version the colours change; each is saved. */
    @Test fun theLightVersionIsChangedOnItsOwn() {
        open(null)
        name("tb-test")
        compose.onNodeWithTag("version Light").performClick()
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("background")
        compose.onNodeWithTag("role background").performClick()
        compose.onNode(hasSetTextAction() and hasText("#", substring = true)).performTextReplacement("#FAFAF0")
        compose.onNodeWithText("Done").performClick()
        save.performClick()
        val saved = CustomThemes.colors("tb-test")!!
        assertEquals(0xFFFAFAF0.toInt(), CustomThemes.light(saved)!!.getValue("background"))
        assertEquals(Palettes.colorsOf(Palettes.green).getValue("background"), saved.getValue("background"))
    }

    /** A colour typed in the picker shows at once, in its row and in the preview, and is saved. */
    @Test fun aColourIsPickedByHex() {
        open(null)
        name("tb-test")
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("background")
        compose.onNodeWithTag("role background").performClick()
        compose.onNode(hasSetTextAction() and hasText("#", substring = true)).performTextReplacement("#123456")
        compose.onNodeWithText("Done").performClick()
        compose.onNode(hasText("#123456") and !hasSetTextAction(), useUnmergedTree = true).assertExists()
        // The preview's own background, left of its gutter.
        val preview = compose.onNodeWithTag("theme-preview").captureToImage().toPixelMap()
        assertEquals(Color(0xFF123456), preview[8, preview.height / 2])

        save.performClick()
        assertEquals(0xFF123456.toInt(), CustomThemes.colors("tb-test")!!.getValue("background"))
    }

    @Test fun aNameIsCheckedBeforeSaving() {
        CustomThemes.save(app, "tb-renamed", Palettes.colorsOf(Palettes.mono))
        open(null)
        name("green")
        compose.onNodeWithText("A built-in theme has this name").assertExists()
        save.assertIsNotEnabled()
        name("tb-renamed")
        compose.onNodeWithText("You already have a theme with this name").assertExists()
        save.assertIsNotEnabled()
        name("-x")
        compose.onNodeWithText("Lowercase letters, digits and hyphens, up to 24").assertExists()
        save.assertIsNotEnabled()
        // Typed as people type it: lowercased, spaces as hyphens.
        name("TB Test")
        compose.onNodeWithTag("theme-name").assert(hasText("tb-test"))
        save.assertIsEnabled()
    }

    @Test fun renamingMovesTheTheme() {
        CustomThemes.save(app, "tb-test", Palettes.colorsOf(Palettes.amber))
        config.set(Keys.THEME, "tb-test")
        open("tb-test")
        compose.onNodeWithTag("theme-name").assert(hasText("tb-test"))
        name("tb-renamed")
        save.performClick()
        assertNull(CustomThemes.colors("tb-test"))
        assertFalse(File(app.filesDir, "themes/tb-test.json").exists())
        // Saved with a light version, the same as its dark one: it looked the same in both modes before.
        val amber = Palettes.colorsOf(Palettes.amber)
        assertEquals(amber + amber.mapKeys { CustomThemes.LIGHT + it.key }, CustomThemes.colors("tb-renamed"))
        assertEquals("tb-renamed", config[Keys.THEME])
    }

    @Test fun deletingTheThemeInUseGoesBackToGreen() {
        CustomThemes.save(app, "tb-test", Palettes.colorsOf(Palettes.amber))
        config.set(Keys.THEME, "tb-test")
        open("tb-test")
        compose.onNodeWithContentDescription("Delete theme").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertNull(CustomThemes.colors("tb-test"))
        assertTrue(config.isDefault(Keys.THEME))
        assertEquals("green", config[Keys.THEME])
    }

    /** Exported, a theme's file carries its name; imported, it is kept as yours, under a free name, and used. */
    @Test fun aThemeTravelsInAFile() {
        CustomThemes.save(app, "tb-test", Palettes.colorsOf(Palettes.amber))
        val amber = CustomThemes.colors("tb-test")
        open("tb-test")
        compose.onNodeWithContentDescription("Export theme").assertExists()
        shared.delete()
        vm.exportTheme("tb-test", Uri.fromFile(shared))
        compose.waitUntil(5_000) { shared.length() > 0 }
        assertTrue(shared.readText(), shared.readText().startsWith("{\n  \"name\": \"tb-test\","))

        // On another phone: its own name, not the file's, and in use.
        CustomThemes.delete(app, "tb-test")
        vm.importTheme(Uri.fromFile(shared))
        compose.waitUntil(5_000) { config[Keys.THEME] == "tb-test" }
        assertEquals(amber, CustomThemes.colors("tb-test"))

        // A different theme of yours by that name stays: the file's takes the next free name.
        CustomThemes.save(app, "tb-test", Palettes.colorsOf(Palettes.dracula))
        vm.importTheme(Uri.fromFile(shared))
        compose.waitUntil(5_000) { config[Keys.THEME] == "tb-test-2" }
        assertEquals(amber, CustomThemes.colors("tb-test-2"))
        assertEquals(Palettes.colorsOf(Palettes.dracula), CustomThemes.colors("tb-test"))

        // Imported again: you already have it, so nothing new.
        config.set(Keys.THEME, "green")
        val before = CustomThemes.names
        File(app.cacheDir, "again.json").apply { writeText(CustomThemes.encode(Palettes.colorsOf(Palettes.dracula), "tb-test")) }.let {
            vm.importTheme(Uri.fromFile(it))
            compose.waitUntil(5_000) { config[Keys.THEME] == "tb-test" }
            it.delete()
        }
        assertEquals(before, CustomThemes.names)

        // Not a theme: nothing is kept.
        shared.writeText("{ \"background\": \"#000000\" }")
        vm.importTheme(Uri.fromFile(shared))
        compose.waitUntil(5_000) { vm.notice.value?.text?.startsWith("theme import failed") == true }
        assertEquals(before, CustomThemes.names)
    }

    /** A new theme has nothing saved to export yet. */
    @Test fun onlyASavedThemeExports() {
        open(null)
        compose.onNodeWithContentDescription("Export theme").assertDoesNotExist()
    }

    /** A saved theme is a theme like the built-in ones, and comes back from its file at start. */
    @Test fun aSavedThemeIsATheme() {
        val colours = Palettes.colorsOf(Palettes.solarized)
        CustomThemes.save(app, "tb-test", colours)
        assertTrue("tb-test" in Keys.THEME.choices)
        assertTrue(config.set(Keys.THEME, "tb-test"))
        assertEquals(Palettes.solarized, Palettes.named("tb-test"))
        // Without a light version, the same in both modes.
        assertEquals(Palettes.solarized, Palettes.named("tb-test", dark = false))
        // At start: every complete file is read, a broken one skipped.
        File(app.filesDir, "themes/tb-renamed.json").writeText("{ \"background\": \"#000000\" }")
        CustomThemes.load(app)
        assertEquals(colours, CustomThemes.colors("tb-test"))
        assertNull(CustomThemes.colors("tb-renamed"))
    }
}
