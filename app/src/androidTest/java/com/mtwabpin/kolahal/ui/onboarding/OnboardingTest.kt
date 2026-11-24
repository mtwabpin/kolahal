package com.mtwabpin.kolahal.ui.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The tour: how Kolahal works, and the first few settings on the way, each one tap. */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()

    @get:Rule
    val compose = createComposeRule()
    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private lateinit var vm: TerminalViewModel

    @get:Rule
    val configReset = ConfigReset()

    /** Where the tour notes that the app selector's page has opened: cleared, so each test's tour is the first. */
    private val launcher = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val selectorShown = launcher.contains("tour_selector_shown")

    @Before
    fun setUp() {
        launcher.edit().remove("tour_selector_shown").commit()
    }

    @After
    fun tearDown() {
        val edit = launcher.edit()
        if (selectorShown) edit.putBoolean("tour_selector_shown", true) else edit.remove("tour_selector_shown")
        edit.commit()
    }

    /** The tour, opened from home, on a phone-sized frame, while it is open: so it can be opened again. */
    private fun show() {
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.ONBOARDING)
        }
        compose.setContent {
            val screen by vm.screen.collectAsState()
            KolahalTheme {
                Box(
                    Modifier
                        .size(360.dp, 720.dp)
                        .consumeWindowInsets(WindowInsets.safeDrawing)
                ) { if (screen == Screen.ONBOARDING) OnboardingScreen(vm) }
            }
        }
    }

    private fun back() {
        compose.onNodeWithTag("tour-back").performClick()
        compose.waitForIdle()
    }

    private fun skip() {
        compose.onNodeWithTag("tour-skip").performClick()
        compose.waitForIdle()
    }

    private fun next() {
        compose.onNodeWithTag("tour-next").performClick()
        compose.waitForIdle()
    }

    private fun shows(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun tap(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun theTourGoesPageByPageAndEndsAtHome() {
        show()
        shows("A home screen you type into.")
        for (title in listOf(
            "Type to open",
            "Everything goes in the log",
            "Make it yours",
            "Above the keyboard",
            "The app selector",
            "Permissions",
            "You're all set"
        )) {
            next()
            shows(title)
        }
        compose.onNodeWithText("Start").assertIsDisplayed()
        next()
        assertEquals(Screen.TERMINAL, vm.screen.value)
    }

    @Test
    fun backGoesToThePageBeforeAndSkipLeaves() {
        show()
        next()
        shows("Type to open")
        compose.onNodeWithTag("tour-back").performClick()
        compose.waitForIdle()
        shows("A home screen you type into.")
        compose.onNodeWithTag("tour-skip").performClick()
        compose.waitForIdle()
        assertEquals(Screen.TERMINAL, vm.screen.value)
    }

    @Test
    fun eachSettingTakesOneTap() {
        show()
        next()
        tap("Chips")
        assertEquals("chips", config[Keys.SUGGESTIONS_STYLE])
        tap("Apps")
        assertEquals("apps", config[Keys.LAUNCH_INSTANT])
        tap("All")
        assertEquals("all", config[Keys.LAUNCH_INSTANT])
        next(); next()
        shows("Make it yours")
        tap("Amber")
        assertEquals("amber", config[Keys.THEME])
        tap("16")
        assertEquals(16, config[Keys.FONT_SIZE])
        tap("24-hour")
        assertTrue(config[Keys.CLOCK_24H])
        next()
        tap("Key bar")
        assertTrue(config[Keys.KEYBAR_SHOW])
        tap("Most-used apps")
        assertTrue(config[Keys.SUGGEST_IDLE])
        next()
        tap("Left edge")
        assertTrue(config[Keys.SELECTOR_RAIL])
        assertEquals("left", config[Keys.SELECTOR_SIDE])
        tap("Off")
        assertFalse(config[Keys.SELECTOR_RAIL])
    }

    private fun caption(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    /** Typing: the caption under the preview says what each choice does. */
    @Test
    fun theTypingPreviewSaysWhatEachChoiceDoes() {
        show()
        next()
        tap("Off")
        caption("Off: nothing runs until you press Enter or tap a suggestion.")
        tap("Apps")
        caption("Apps: an app opens once only one match is left; commands require Enter.")
    }

    /** The log's lines answer a tap and a long-press in the caption. */
    @Test
    fun theLogsLinesCanBeTried() {
        show()
        next(); next()
        compose.onNodeWithTag("tour-try-line 0").performClick()
        caption("Tap: runs cal again.")
        compose.onNodeWithTag("tour-try-line 0").performTouchInput { longClick() }
        caption("Long-press: run again, edit in the prompt, copy, delete, and when it ran.")
    }

    /** The look preview's clock follows the clock setting. */
    @Test
    fun theClockPreviewFollowsTheSetting() {
        show()
        repeat(3) { next() }
        // The header is off at first; its clock shows once it's on.
        compose.onNodeWithTag("tour-clock").assertDoesNotExist()
        tap("Header")
        tap("24-hour")
        val shown = compose.onNodeWithTag("tour-clock")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertTrue(shown, Regex("\\d{2}:\\d{2}").matches(shown))
        tap("12-hour")
        val twelve = compose.onNodeWithTag("tour-clock")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertTrue(twelve, twelve.contains(' '))
    }

    /** The key bar shows in the keyboard preview once it's on. */
    @Test
    fun theKeyBarShowsInThePreview() {
        show()
        repeat(4) { next() }
        compose.onAllNodesWithText("|", useUnmergedTree = true).assertCountEquals(0)
        tap("Key bar")
        compose.onAllNodesWithText("|", useUnmergedTree = true).assertCountEquals(1)
    }

    /** The app selector's page turns it on, on the right edge, and it can be tried in the preview at once. */
    @Test
    fun theSelectorIsOnWhenItsPageOpens() {
        show()
        repeat(4) { next() }
        assertFalse(config[Keys.SELECTOR_RAIL])
        next()
        assertTrue(config[Keys.SELECTOR_RAIL])
        compose.onNodeWithTag("choice Right edge").assertIsOn()
        compose.onNodeWithTag("choice Off").assertIsOff()
        compose.onNodeWithTag("app-selector").assertExists()
        val frame = compose.onNodeWithTag("tour-selector-preview").getUnclippedBoundsInRoot()
        val strip = compose.onNodeWithTag("app-selector").getUnclippedBoundsInRoot()
        assertEquals(frame.right.value, strip.right.value, 1f)
        tap("Off")
        compose.onAllNodesWithTag("app-selector").assertCountEquals(0)
    }

    /** Off on the selector's page stays off when the person leaves the page and comes back, either way. */
    @Test
    fun offOnTheSelectorsPageStaysOff() {
        show()
        repeat(5) { next() }
        tap("Off")
        next()
        back()
        shows("The app selector")
        back()
        next()
        shows("The app selector")
        assertFalse(config[Keys.SELECTOR_RAIL])
        compose.onNodeWithTag("choice Off").assertIsOn()
        compose.onAllNodesWithTag("app-selector").assertCountEquals(0)
    }

    /** Off chosen in an earlier tour stays off when the tour runs again. */
    @Test
    fun anEarlierOffStaysOffWhenTheTourRunsAgain() {
        show()
        repeat(5) { next() }
        tap("Off")
        skip()
        assertEquals(Screen.TERMINAL, vm.screen.value)
        compose.runOnUiThread { vm.openScreen(Screen.ONBOARDING) }
        compose.waitForIdle()
        shows("A home screen you type into.")
        repeat(5) { next() }
        shows("The app selector")
        assertFalse(config[Keys.SELECTOR_RAIL])
        compose.onNodeWithTag("choice Off").assertIsOn()
    }

    /** Skipping the tour before the selector's page leaves the selector off. */
    @Test
    fun skippingBeforeTheSelectorsPageLeavesItOff() {
        show()
        repeat(4) { next() }
        shows("Above the keyboard")
        skip()
        assertEquals(Screen.TERMINAL, vm.screen.value)
        assertFalse(config[Keys.SELECTOR_RAIL])
    }

    /** The permissions page offers the ones most people want, with the rest left to the checklist. */
    @Test
    fun thePermissionsPageOffersTheHomeApp() {
        show()
        repeat(6) { next() }
        shows("Permissions")
        compose.waitUntil(5_000) { vm.setupChecks.value.isNotEmpty() }
        compose.onNodeWithText("Default home app").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tourOpensIt() {
        vm = vms.terminal(app)
        vm.runCommand("tour")
        compose.waitUntil(5_000) { vm.screen.value == Screen.ONBOARDING }
    }
}
