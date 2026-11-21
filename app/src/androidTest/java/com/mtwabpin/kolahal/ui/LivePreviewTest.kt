package com.mtwabpin.kolahal.ui

import android.os.SystemClock
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Db
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.LivePreview
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A whole theme, font or font size in the prompt shows on home before Enter, and only Enter keeps it. */
@RunWith(AndroidJUnit4::class)
class LivePreviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val config = Config.get(instrumentation.targetContext)
    private val green = Palettes.named("green", true)
    private val dracula = Palettes.named("dracula", true)

    private fun reset() =
        listOf(Keys.THEME, Keys.THEME_ALWAYS_DARK, Keys.FONT, Keys.FONT_SIZE).forEach(config::reset)

    @Before
    fun setUp() {
        // A last run's reset may not have reached the disk before its process ended.
        reset()
        // The dark version whatever the emulator's mode, so the palettes are known.
        config.set(Keys.THEME_ALWAYS_DARK, "on")
    }

    @After
    fun tearDown() = reset()

    private fun of(line: String, customFont: Boolean = false) =
        LivePreview.of(line, config) { customFont }

    private fun onHome(test: (TerminalViewModel) -> Unit) =
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: TerminalViewModel
            scenario.onActivity {
                vm = ViewModelProvider(it)[TerminalViewModel::class.java]
                vm.openScreen(Screen.TERMINAL)
            }
            test(vm)
            // A line left in the prompt would stay in the view model's state for the next test.
            line(vm, "")
        }

    private fun line(vm: TerminalViewModel, text: String) =
        instrumentation.runOnMainSync { vm.onInputChange(TextFieldValue(text)) }

    private fun <T> shows(expected: T, actual: () -> T) {
        val until = SystemClock.uptimeMillis() + 5_000
        while (actual() != expected && SystemClock.uptimeMillis() < until) Thread.sleep(20)
        assertEquals(expected, actual())
    }

    @Test
    fun onlyAWholeValueNotInUseAlready() {
        for (line in listOf(
            "theme dracula",
            "  Theme   DRACULA ",
            "config set theme.name dracula",
            "set theme.name dracula",
            "config theme.name dracula",
            "config set theme dracula"
        )) {
            assertEquals(line, LivePreview(theme = "dracula"), of(line))
        }
        for (line in listOf("config set font.size 20", "set font.size 20", "config font.size 20")) {
            assertEquals(line, LivePreview(fontSize = 20), of(line))
        }
        assertEquals(LivePreview(font = "fira-code"), of("font fira-code"))
        for (line in listOf(
            "",
            "help",
            "theme",
            "theme dra",
            "theme nosuch",
            "theme green",
            "theme dracula | head",
            "theme dracula now",
            "font",
            "font ls",
            "font import",
            "font sans all",
            "font jetbrains-mono",
            "font custom",
            "config set font.size 99",
            "config set font.size 14",
            "config set font.size 20 now",
            "config reset font.size",
            "config get font.size",
            "config set log.line-height 30",
            "/theme dracula",
        )) {
            assertNull(line, of(line))
        }
        assertEquals(
            "with an imported font",
            LivePreview(font = "custom"),
            of("font custom", customFont = true)
        )
    }

    @Test
    fun typingShowsItAndChangingTheLinePutsItBack() = onHome { vm ->
        shows(green) { Term.palette }
        line(vm, "theme dracula")
        shows(dracula) { Term.palette }
        assertEquals("nothing saved", "green", config[Keys.THEME])
        line(vm, "theme dracul")
        shows(green) { Term.palette }

        line(vm, "config set font.size 20")
        shows(20) { Term.fontSize }
        assertEquals("nothing saved", 14, config[Keys.FONT_SIZE])
        line(vm, "")
        shows(14) { Term.fontSize }

        val family = Term.fontFamily
        line(vm, "font fira-code")
        val until = SystemClock.uptimeMillis() + 5_000
        while (Term.fontFamily == family && SystemClock.uptimeMillis() < until) Thread.sleep(20)
        assertNotEquals(family, Term.fontFamily)
        assertEquals("nothing saved", "jetbrains-mono", config[Keys.FONT])
        line(vm, "")
        shows(family) { Term.fontFamily }
    }

    /** Settings says what's in use, so it shows what's in use too. */
    @Test
    fun otherScreensShowWhatsInUse() = onHome { vm ->
        line(vm, "theme dracula")
        shows(dracula) { Term.palette }
        instrumentation.runOnMainSync { vm.openScreen(Screen.SETTINGS) }
        shows(green) { Term.palette }
        instrumentation.runOnMainSync { vm.openScreen(Screen.TERMINAL) }
        shows(dracula) { Term.palette }
    }

    /**
     * The prompt empties before the command has run: the old look mustn't come back in between. A
     * command's line goes in the log first, so with the database held it can't get any further, on
     * any phone: here, it's usually done too soon for the gap to show.
     */
    @Test
    fun enterKeepsItWithNoFlashOfTheOldLook() = onHome { vm ->
        shows(green) { Term.palette }
        line(vm, "theme dracula")
        shows(dracula) { Term.palette }
        // The same line last in the log wouldn't be written again.
        LogRepository.get(instrumentation.targetContext).add(LogKind.INFO, "before the command")
        val db = Db.get(instrumentation.targetContext).writableDatabase
        instrumentation.runOnMainSync {
            db.beginTransaction()
            vm.submit()
        }
        try {
            Thread.sleep(500)
            assertEquals("not saved yet", "green", config[Keys.THEME])
            assertEquals(dracula, Term.palette)
        } finally {
            instrumentation.runOnMainSync { db.endTransaction() }
        }
        shows("dracula") { config[Keys.THEME] }
        shows(dracula) { Term.palette }
    }
}
