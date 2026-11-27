@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.mtwabpin.kolahal.ui.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.TestWidgets
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Home's two pages: the log, and the widgets page beside it (`widgets.show`), which can come first (`widgets.start`). */
@RunWith(AndroidJUnit4::class)
class HomePagesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val config = Config.get(instrumentation.targetContext)

    @After
    fun tearDown() =
        listOf(Keys.WIDGETS_SHOW, Keys.HOME_PAGES, Keys.WIDGETS_START, Keys.SELECTOR_RAIL, Keys.LOG_SWIPE_RIGHT)
            .forEach(config::reset)

    @get:Rule
    val lines = com.mtwabpin.kolahal.LogLinesSince()

    private fun widgetsPage() = compose.onNodeWithTag("widgets-page")

    /** Waits until the node tagged [tag] shows: a widgets page beyond the first is tagged `widgets-page-2` and so on. */
    private fun waitForTag(tag: String) = compose.waitUntil(10_000) {
        runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
    }

    /** The first start after an install can take a while on an emulator. */
    private fun waitFor(shown: Boolean) = compose.waitUntil(10_000) {
        runCatching { if (shown) widgetsPage().assertIsDisplayed() else widgetsPage().assertIsNotDisplayed() }.isSuccess
    }

    @Test
    fun homeOpensOnTheWidgetsPageAndTypingBringsUpTheLog() {
        // Not a first run: right after an install, the welcome tour would open over home a moment after start.
        instrumentation.targetContext.getSharedPreferences(
            "launcher",
            android.content.Context.MODE_PRIVATE
        )
            .edit().putBoolean("intro_shown", true).commit()
        config.set(Keys.WIDGETS_SHOW, "on")
        config.set(Keys.WIDGETS_START, "on")
        config.set(Keys.SELECTOR_RAIL, "on")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: TerminalViewModel
            scenario.onActivity {
                vm = ViewModelProvider(it)[TerminalViewModel::class.java]
                vm.openScreen(Screen.TERMINAL)
            }
            waitFor(shown = true)
            // As the first page it has no title row.
            compose.onNodeWithContentDescription("Add a widget").assertDoesNotExist()
            // The app selector on both pages, the same: as far down, and as high. Where each starts and how
            // high it is, not its bounds: the log page is off screen, and those are cut to the screen.
            fun strips() = compose.onAllNodesWithTag("app-selector-strip", useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.positionInRoot.y to it.size.height }
            runCatching {
                compose.waitUntil(5_000) { strips().let { s -> s.size == 2 && abs(s[0].first - s[1].first) < 1f && s[0].second == s[1].second } }
            }
            val strips = strips()
            assertEquals("$strips", 2, strips.size)
            assertEquals("$strips", strips[0].first, strips[1].first, 1f)
            assertEquals("$strips", strips[0].second, strips[1].second)
            // A key typed there lands in the prompt: the log comes up with it.
            instrumentation.runOnMainSync { vm.onInputChange(TextFieldValue("x")) }
            waitFor(shown = false)
            // Home goes back to the widgets page.
            instrumentation.runOnMainSync { vm.onHomePressed() }
            waitFor(shown = true)
            // With the app selector off, home is the log: the widgets page would have no way to open an app.
            // widgets.start keeps its value, for when the selector is back.
            config.reset(Keys.SELECTOR_RAIL)
            waitFor(shown = false)
            assertEquals(true, config[Keys.WIDGETS_START])
            config.set(Keys.SELECTOR_RAIL, "on")
            waitFor(shown = true)
            // Off again: home is the log, and the widgets page has its title row back.
            config.reset(Keys.WIDGETS_START)
            waitFor(shown = false)
            compose.onNodeWithContentDescription("Add a widget").assertExists()
        }
    }

    /**
     * Without a swipe: a screen reader's and Switch Access's actions on the pager, named after the
     * page they turn to, and `widgets page`, turn home across. Left is the page before: with the page on
     * the left, left leads to it.
     */
    @Test
    fun pageActionsAndTheCommandTurnHomeWithoutASwipe() {
        instrumentation.targetContext.getSharedPreferences(
            "launcher",
            android.content.Context.MODE_PRIVATE
        )
            .edit().putBoolean("intro_shown", true).commit()
        config.set(Keys.WIDGETS_SHOW, "on")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: TerminalViewModel
            scenario.onActivity {
                vm = ViewModelProvider(it)[TerminalViewModel::class.java]
                vm.onHomePressed()
            }
            waitFor(shown = false)
            val pager = compose.onNodeWithTag("home-pager")
            pager.performCustomAccessibilityActionWithLabel("Widgets")
            waitFor(shown = true)
            pager.performCustomAccessibilityActionWithLabel("Log")
            waitFor(shown = false)
            pager.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.PageRight))
            pager.performSemanticsAction(SemanticsActions.PageLeft)
            waitFor(shown = true)
            pager.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.PageLeft))
            pager.performSemanticsAction(SemanticsActions.PageRight)
            waitFor(shown = false)
            // The command, as typed: home turns to the page, and stays on it.
            instrumentation.runOnMainSync { vm.runCommand("widgets page") }
            waitFor(shown = true)
            Thread.sleep(1_000)
            compose.waitForIdle()
            widgetsPage().assertIsDisplayed()
        }
    }

    /**
     * Back from a screen returns to the home page it was opened from, not to home's first page:
     * Arrange widgets, opened from the widgets page, goes back there. Home still goes to the first page.
     */
    @Test
    fun backFromAScreenReturnsToThePageItWasOpenedFrom() {
        instrumentation.targetContext.getSharedPreferences(
            "launcher",
            android.content.Context.MODE_PRIVATE
        )
            .edit().putBoolean("intro_shown", true).commit()
        config.set(Keys.WIDGETS_SHOW, "on")
        config.set(Keys.WIDGETS_START, "on")
        config.set(Keys.SELECTOR_RAIL, "on")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: TerminalViewModel
            scenario.onActivity {
                vm = ViewModelProvider(it)[TerminalViewModel::class.java]
                vm.onHomePressed()
            }
            waitFor(shown = true)
            // Away from the first page: typing brings up the log.
            instrumentation.runOnMainSync { vm.onInputChange(TextFieldValue("x")) }
            waitFor(shown = false)
            // A screen opened from the log returns to the log.
            instrumentation.runOnMainSync { vm.openScreen(Screen.WIDGETS) }
            compose.waitForIdle()
            instrumentation.runOnMainSync { vm.closeScreen() }
            waitFor(shown = false)
            // From the widgets page, back to the widgets page.
            instrumentation.runOnMainSync { vm.onHomePressed() }
            waitFor(shown = true)
            instrumentation.runOnMainSync { vm.openScreen(Screen.WIDGETS) }
            compose.waitForIdle()
            instrumentation.runOnMainSync { vm.closeScreen() }
            waitFor(shown = true)
            // Home from a screen goes to home's first page, whatever page was open before.
            instrumentation.runOnMainSync { vm.onInputChange(TextFieldValue("x")) }
            waitFor(shown = false)
            instrumentation.runOnMainSync { vm.openScreen(Screen.WIDGETS) }
            compose.waitForIdle()
            instrumentation.runOnMainSync { vm.onHomePressed() }
            waitFor(shown = true)
        }
    }

    /** The widgets page keeps where it was scrolled to: Back from Arrange widgets returns there, not to the top. */
    @Test
    fun theWidgetsPageKeepsItsScrollAcrossArrangeWidgets() {
        instrumentation.targetContext.getSharedPreferences(
            "launcher",
            android.content.Context.MODE_PRIVATE
        )
            .edit().putBoolean("intro_shown", true).commit()
        config.set(Keys.WIDGETS_SHOW, "on")
        val t = TestWidgets()
        try {
            // Tall widgets: a page longer than the screen.
            listOf(WidgetSource.BATTERY, WidgetSource.SHORTCUTS, WidgetSource.MEDIA)
                .forEach { t.host.placeBuiltin(it, WidgetSize.S4X4) }
            t.host.chosen()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var vm: TerminalViewModel
                scenario.onActivity {
                    vm = ViewModelProvider(it)[TerminalViewModel::class.java]
                    vm.onHomePressed()
                }
                waitFor(shown = false)
                instrumentation.runOnMainSync { vm.runCommand("widgets page") }
                waitFor(shown = true)
                compose.onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("widgets-page")))[0]
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f) }
                compose.waitForIdle()
                val scrolled = vm.widgetsScroll[WidgetLocation.PANEL] ?: 0
                assertTrue("the page scrolled: $scrolled", scrolled > 0)
                instrumentation.runOnMainSync { vm.openScreen(Screen.WIDGETS) }
                compose.waitForIdle()
                instrumentation.runOnMainSync { vm.closeScreen() }
                waitFor(shown = true)
                compose.waitForIdle()
                assertEquals(scrolled, vm.widgetsScroll[WidgetLocation.PANEL] ?: 0)
            }
        } finally {
            t.close()
        }
    }

    /**
     * A line's swipe set to none leaves the drag to the pager: with the widgets page left of the log, a swipe
     * right that starts on a line turns to it, and the line stays where it is.
     */
    @Test
    fun aSwipeSetToNoneOnALineTurnsThePage() {
        instrumentation.targetContext.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("intro_shown", true).commit()
        config.set(Keys.WIDGETS_SHOW, "on")
        config.set(Keys.LOG_SWIPE_RIGHT, "none")
        val logs = com.mtwabpin.kolahal.data.LogRepository.get(instrumentation.targetContext)
        val id = logs.add(com.mtwabpin.kolahal.data.LogKind.OUTPUT, "turn the page ${System.nanoTime()}")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[TerminalViewModel::class.java].onHomePressed() }
            waitFor(shown = false)
            waitForTag("line-$id")
            compose.onNodeWithTag("line-$id").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
            waitFor(shown = true)
            assertTrue(logs.recent(20).any { it.id == id })
        }
    }

    /**
     * Three pages, all on the widgets page's side: `widget page` takes a name, the pager's page actions
     * go one page at a time and name the page they turn to, and Back goes from any page straight to
     * the log.
     */
    @Test
    fun severalPagesTurnOneAtATimeAndBackGoesHome() {
        instrumentation.targetContext.getSharedPreferences(
            "launcher",
            android.content.Context.MODE_PRIVATE
        )
            .edit().putBoolean("intro_shown", true).commit()
        config.set(Keys.WIDGETS_SHOW, "on")
        val t = TestWidgets()
        try {
            assertEquals(1, t.host.pages.value.size)
            t.host.addPage()
            t.host.addPage()
            t.host.renamePage(2, "Travel")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var vm: TerminalViewModel
                scenario.onActivity {
                    vm = ViewModelProvider(it)[TerminalViewModel::class.java]
                    vm.onHomePressed()
                }
                waitFor(shown = false)
                instrumentation.runOnMainSync { vm.runCommand("widget page trav") }
                waitForTag("widgets-page-3")
                val pager = compose.onNodeWithTag("home-pager")
                // The outermost page has one page beside it, toward home.
                pager.performCustomAccessibilityActionWithLabel("Page 2")
                waitForTag("widgets-page-2")
                pager.performCustomAccessibilityActionWithLabel("Page 1")
                waitFor(shown = true)
                pager.performCustomAccessibilityActionWithLabel("Log")
                waitFor(shown = false)
                // From page 2, Back goes to the log in one step.
                instrumentation.runOnMainSync { vm.runCommand("widget page 2") }
                waitForTag("widgets-page-2")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(10_000) {
                    runCatching { compose.onNodeWithTag("widgets-page-2").assertIsNotDisplayed() }.isSuccess
                }
                waitFor(shown = false)
                assertEquals("log", vm.homePageShown)
            }
        } finally {
            t.close()
        }
    }
}
