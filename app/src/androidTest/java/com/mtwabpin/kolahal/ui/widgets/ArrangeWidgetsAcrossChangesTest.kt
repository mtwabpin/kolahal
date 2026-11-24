package com.mtwabpin.kolahal.ui.widgets

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arrange widgets in home's own activity: closed with its back arrow, it stays closed through a dark
 * mode change, a change of home style and a recreated activity (as a rotation would make one, if
 * Android made one); open, it stays open through them.
 */
@RunWith(AndroidJUnit4::class)
class ArrangeWidgetsAcrossChangesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val config = Config.get(instrumentation.targetContext)

    @After
    fun tearDown() {
        config.reset(Keys.HOME_STYLE)
    }

    /** Home's activity, on home, and its view model. Not a first run: the tour would open over home. */
    private fun launch(): ActivityScenario<MainActivity> {
        instrumentation.targetContext.getSharedPreferences(
            "launcher",
            android.content.Context.MODE_PRIVATE
        )
            .edit().putBoolean("intro_shown", true).commit()
        config.set(Keys.HOME_STYLE, "gui")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { vmOf(it).onHomePressed() }
        return scenario
    }

    private fun vmOf(activity: MainActivity): TerminalViewModel =
        ViewModelProvider(activity)[TerminalViewModel::class.java]

    private fun vm(scenario: ActivityScenario<MainActivity>): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        scenario.onActivity { vm = vmOf(it) }
        return vm
    }

    /** The top bar's Add widget is Arrange widgets' own: the widgets page says "Add a widget". */
    private fun arrangeShown() = runCatching {
        compose.onAllNodesWithContentDescription("Add widget").fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    private fun logShown() = runCatching {
        compose.onAllNodesWithTag("log").fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    /** Home is on top, in the screen state and on the screen. */
    private fun assertHome(scenario: ActivityScenario<MainActivity>, after: String) {
        compose.waitUntil(10_000) { logShown() && !arrangeShown() }
        assertEquals(after, Screen.TERMINAL, vm(scenario).screen.value)
    }

    /** Arrange widgets is on top, in the screen state and on the screen. */
    private fun assertArrange(scenario: ActivityScenario<MainActivity>, after: String) {
        compose.waitUntil(10_000) { arrangeShown() }
        assertEquals(after, Screen.WIDGETS, vm(scenario).screen.value)
    }

    /** What the activity gets when the phone's dark mode is switched: its configuration, with the other night mode. */
    private fun switchDarkMode(scenario: ActivityScenario<MainActivity>) = scenario.onActivity { a ->
        val changed = Configuration(a.resources.configuration)
        val night = changed.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val other =
            if (night == Configuration.UI_MODE_NIGHT_YES) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        changed.uiMode = (changed.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or other
        a.onConfigurationChanged(changed)
    }

    @Test
    fun closedWithTheBackArrowItStaysClosed() {
        launch().use { scenario ->
            assertHome(scenario, "at start")
            val model = vm(scenario)
            instrumentation.runOnMainSync { model.openScreen(Screen.WIDGETS) }
            assertArrange(scenario, "opened")
            compose.onNodeWithContentDescription("Back").performClick()
            assertHome(scenario, "after the back arrow")
            switchDarkMode(scenario)
            assertHome(scenario, "after a dark mode change")
            config.set(Keys.HOME_STYLE, "tty")
            assertHome(scenario, "after home.style tty")
            config.set(Keys.HOME_STYLE, "gui")
            assertHome(scenario, "after home.style gui")
            scenario.recreate()
            assertHome(scenario, "after the activity was made again")
        }
    }

    @Test
    fun openItStaysOpen() {
        launch().use { scenario ->
            assertHome(scenario, "at start")
            val model = vm(scenario)
            instrumentation.runOnMainSync { model.openScreen(Screen.WIDGETS) }
            assertArrange(scenario, "opened")
            switchDarkMode(scenario)
            assertArrange(scenario, "after a dark mode change")
            config.set(Keys.HOME_STYLE, "tty")
            assertArrange(scenario, "after home.style tty")
            scenario.recreate()
            assertArrange(scenario, "after the activity was made again")
            compose.onNodeWithContentDescription("Back").performClick()
            assertHome(scenario, "after the back arrow")
        }
    }
}
