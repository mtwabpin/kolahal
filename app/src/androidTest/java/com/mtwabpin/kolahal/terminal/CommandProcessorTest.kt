package com.mtwabpin.kolahal.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.eventually
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandProcessorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val config = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val apps = AppRepository.get(context)
    private val opened = mutableListOf<Script>()

    @get:Rule val configReset = ConfigReset()
    private val commands = CommandRunner(context, object : TerminalUiStub() {
        override fun openEditor(script: Script) { opened += script }
        override fun clearScreen() { logs.clearScreen() }
    })

    @Before fun setUp() {
        apps.refresh()
        eventually(5_000) { apps.apps.value.isNotEmpty() }
    }

    @After fun tearDown() {
        prefs.setAlias("tt", null)
        ScriptRepository.get(context).byName("cptest")?.let { ScriptRepository.get(context).delete(it.id) }
    }

    /** Runs [line] and returns the non-command lines it printed. */
    private fun run(line: String) = commands.run(line)

    @Test fun echoAndUnknown() {
        assertEquals(listOf("hello world"), run("echo hello world"))
        assertTrue(run("definitely-not-an-app-zzz").single().startsWith("Command not found"))
    }

    @Test fun aliasExpands() {
        run("alias tt = echo aliased")
        assertEquals(listOf("aliased extra"), run("tt extra"))
        run("unalias tt")
        assertFalse(run("tt").any { it.contains("aliased") })
    }

    @Test fun configSetAndValidate() {
        run("config set font.size 18")
        assertEquals(18, config[Keys.FONT_SIZE])
        assertTrue(run("config set font.size 99").single().startsWith("Invalid value"))
        assertEquals(18, config[Keys.FONT_SIZE])
        run("theme amber")
        assertEquals("amber", config[Keys.THEME])
        run("config set keybar.row1 ESC,TAB,\",|")
        assertEquals("ESC,TAB,\",|", config[Keys.KEYBAR])
        run("config reset all")
        assertEquals(14, config[Keys.FONT_SIZE])
    }

    /** Names from before 0.3.0 still work in `config`, and say what the setting is called now. */
    @Test fun oldSettingNamesStillWork() {
        assertEquals(listOf("theme.name = amber", "theme is now called theme.name"), run("config set theme amber"))
        assertEquals("amber", config[Keys.THEME])
        run("config set keybar2 ESC,TAB,\",|")
        assertEquals("ESC,TAB,\",|", config[Keys.KEYBAR2])
        run("config reset theme")
        assertTrue(config.isDefault(Keys.THEME))
        // The swipe-up switch has no new name: it became gesture.swipe-up's none.
        assertTrue(run("config set gesture.swipeup.on on").single().startsWith("gesture.swipeup.on was removed"))
        assertTrue(config.isDefault(Keys.GESTURE_SWIPE_UP))
    }

    /** Settings that are gone (0.3.1) say where their job went. */
    @Test fun removedSettingsSayWhereTheyWent() {
        assertTrue(run("config set widgets.home on").single().startsWith("widgets.home was removed: widgets are on the widgets page"))
        assertTrue(run("config set theme.settings-colors system").single().startsWith("theme.settings-colors was removed: Settings always use"))
    }

    @Test fun themeTakesAThemeYouMade() {
        val themes = com.mtwabpin.kolahal.data.CustomThemes
        themes.save(context, "cptest-theme", com.mtwabpin.kolahal.ui.Palettes.colorsOf(com.mtwabpin.kolahal.ui.Palettes.mono))
        try {
            run("theme cptest-theme")
            assertEquals("cptest-theme", config[Keys.THEME])
        } finally {
            themes.delete(context, "cptest-theme")
        }
    }

    @Test fun scriptLifecycle() {
        run("script new cptest --cmd")
        val script = ScriptRepository.get(context).byName("cptest")
        assertNotNull(script)
        assertEquals(script!!.id, opened.single().id)
        run("script every cptest 5m")
        assertEquals(15, ScriptRepository.get(context).byName("cptest")!!.intervalMin)
        run("script at cptest 7:05,19:00")
        assertEquals(listOf("07:05", "19:00"), ScriptRepository.get(context).byName("cptest")!!.atTimes)
        assertTrue(run("script at cptest 25:00").single().startsWith("Invalid time"))
        run("script rm cptest")
        assertEquals(null, ScriptRepository.get(context).byName("cptest"))
        assertTrue(run("script new help").single().contains("built-in"))
    }

    /** Commas, spaces or both between the times, as the docs write them and as on an `@at` line. */
    @Test fun scriptAtForms() {
        run("script new cptest")
        fun times() = ScriptRepository.get(context).byName("cptest")!!.atTimes
        run("script at cptest 22:30, 06:30")
        assertEquals(listOf("06:30", "22:30"), times())
        run("script at cptest 9:00 18:30")
        assertEquals(listOf("09:00", "18:30"), times())
        run("script at cptest 7:05,19:00")
        assertEquals(listOf("07:05", "19:00"), times())
        // A line that's wrong changes nothing: not one good time of it, and a comma alone isn't `off`.
        assertTrue(run("script at cptest 8:00, 25:00").single().startsWith("Invalid time"))
        assertTrue(run("script at cptest ,").single().startsWith("Usage"))
        assertTrue(run("script at cptest").single().startsWith("Usage"))
        assertEquals(listOf("07:05", "19:00"), times())
        run("script at cptest off")
        assertEquals(emptyList<String>(), times())
    }

    /** Waits up to 5 s for the kept app list to reload into what [done] wants. */
    private fun appsUntil(done: () -> Boolean) = waitUntil("the app list didn't reload", 5_000, done)

    @Test fun hideAndRenameApp() {
        val app = apps.apps.value.first { it.profile.kind == com.mtwabpin.kolahal.apps.ProfileKind.PERSONAL }
        fun now() = apps.apps.value.first { it.key == app.key }
        try {
            prefs.setHidden(app.key, true)
            apps.refresh()
            appsUntil { now().hidden }
            assertFalse(apps.search(app.originalLabel).any { it.key == app.key })
            assertTrue(apps.search(app.originalLabel, includeHidden = true).any { it.key == app.key })
            prefs.setLabel(app.key, "Zz Renamed")
            apps.refresh()
            appsUntil { now().label == "Zz Renamed" }
        } finally {
            prefs.setHidden(app.key, false)
            prefs.setLabel(app.key, null)
            apps.refresh()
            // Later tests find it as it was.
            appsUntil { !now().hidden && now().label == app.originalLabel }
        }
    }
}
