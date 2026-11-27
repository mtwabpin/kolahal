@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.mtwabpin.kolahal.ui.home

import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.TestWidgets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home's pages in an order of their own (`home.pages`), and the Page order screen that sets it. */
@RunWith(AndroidJUnit4::class)
class HomePageOrderTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val config = Config.get(instrumentation.targetContext)

    @After
    fun tearDown() = listOf(Keys.HOME_PAGES, Keys.WIDGETS_SHOW, Keys.WIDGETS_START, Keys.SELECTOR_RAIL)
        .plus(if (BuildConfig.ASSISTANT) listOf(Keys.ASSISTANT_PAGE) else emptyList())
        .forEach(config::reset)

    private fun shown(tag: String) = runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { shown(tag) }

    /** No page but the log shows (homePageShown is null until home first turns, when the test pressed Home after it settled). */
    private fun TerminalViewModel.waitForLog() = compose.waitUntil(10_000) {
        homePageShown in listOf(null, "log") && listOf("widgets-page", "widgets-page-2").none(::shown)
    }

    /** The labels of the pager's page actions: the page on its left and the one on its right. */
    private fun neighbors(): Pair<String?, String?> {
        val c = compose.onNodeWithTag("home-pager").fetchSemanticsNode().config
        val left = if (SemanticsActions.PageLeft in c) c[SemanticsActions.PageLeft].label else null
        val right = if (SemanticsActions.PageRight in c) c[SemanticsActions.PageRight].label else null
        return left to right
    }

    private fun waitForNeighbors(left: String?, right: String?) = compose.waitUntil(10_000) {
        runCatching { neighbors() == (left to right) }.getOrDefault(false)
    }

    private fun start(): Pair<ActivityScenario<MainActivity>, TerminalViewModel> {
        instrumentation.targetContext.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("intro_shown", true).commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: TerminalViewModel
        scenario.onActivity {
            vm = ViewModelProvider(it)[TerminalViewModel::class.java]
            vm.onHomePressed()
        }
        return scenario to vm
    }

    /**
     * Two widgets pages, the Assistant between widgets page 1 and the log, page 2 right of the log: the
     * pager shows them in that order, Home returns to the log from each, `widget page 2` reaches page 2.
     */
    @Test
    fun thePagesFollowTheOrder() {
        val assistant = BuildConfig.ASSISTANT
        config.set(Keys.WIDGETS_SHOW, "on")
        if (assistant) config.set(Keys.ASSISTANT_PAGE, "on")
        val t = TestWidgets()
        try {
            val second = HomeOrder.idOf(t.host.addPage()!!)
            config.set(Keys.HOME_PAGES, "widgets,assistant,log,$second")
            val (scenario, vm) = start()
            scenario.use {
                vm.waitForLog()
                // Left to right: Page 1, Assistant, Log, Page 2.
                val beforeLog = if (assistant) "Assistant" else "Page 1"
                waitForNeighbors(beforeLog, "Page 2")
                compose.onNodeWithTag("home-pager").performSemanticsAction(SemanticsActions.PageRight)
                waitForTag("widgets-page-2")
                waitForNeighbors("Log", null)
                // The page dots on both widgets pages count every page home shows, in its order:
                // page 1 is the first, page 2 the last.
                val count = if (assistant) 4 else 3
                compose.onNodeWithContentDescription("Page $count of $count").assertExists()
                compose.onNodeWithContentDescription("Page 1 of $count").assertExists()
                instrumentation.runOnMainSync { vm.onHomePressed() }
                vm.waitForLog()
                waitForNeighbors(beforeLog, "Page 2")
                if (assistant) {
                    compose.onNodeWithTag("home-pager").performCustomAccessibilityActionWithLabel("Assistant")
                    waitForNeighbors("Page 1", "Log")
                    instrumentation.runOnMainSync { vm.onHomePressed() }
                    waitForNeighbors("Assistant", "Page 2")
                    compose.onNodeWithTag("home-pager").performSemanticsAction(SemanticsActions.PageLeft)
                    waitForNeighbors("Page 1", "Log")
                }
                compose.onNodeWithTag("home-pager").performSemanticsAction(SemanticsActions.PageLeft)
                waitForTag("widgets-page")
                waitForNeighbors(null, if (assistant) "Assistant" else "Log")
                // Back goes to the log in one step, from the far end.
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                vm.waitForLog()
                waitForNeighbors(beforeLog, "Page 2")
                // `widget page 2` reaches page 2 wherever it is.
                instrumentation.runOnMainSync { vm.runCommand("widget page 2") }
                waitForTag("widgets-page-2")
                instrumentation.runOnMainSync { vm.onHomePressed() }
                vm.waitForLog()
            }
        } finally {
            t.close()
        }
    }

    /** With widgets.start, Home returns to widgets page 1 wherever the order puts it. */
    @Test
    fun homeReturnsToWidgetsPageOneWhereverItIs() {
        config.set(Keys.WIDGETS_SHOW, "on")
        config.set(Keys.WIDGETS_START, "on")
        config.set(Keys.SELECTOR_RAIL, "on")
        config.set(Keys.HOME_PAGES, "log,widgets")
        val (scenario, vm) = start()
        scenario.use {
            waitForTag("widgets-page")
            waitForNeighbors("Log", null)
            compose.onNodeWithTag("home-pager").performSemanticsAction(SemanticsActions.PageLeft)
            vm.waitForLog()
            waitForNeighbors(null, "Widgets")
            instrumentation.runOnMainSync { vm.onHomePressed() }
            waitForTag("widgets-page")
        }
    }

    /** The Assistant's page keeps the keyboard between two other pages, as it does at the end. */
    @Test
    fun theAssistantKeepsTheKeyboardInTheMiddle() {
        assumeTrue(BuildConfig.ASSISTANT)
        config.set(Keys.WIDGETS_SHOW, "on")
        config.set(Keys.ASSISTANT_PAGE, "on")
        config.set(Keys.HOME_PAGES, "widgets,assistant,log")
        val (scenario, vm) = start()
        scenario.use {
            vm.waitForLog()
            compose.onNodeWithTag("home-pager").performCustomAccessibilityActionWithLabel("Assistant")
            waitForNeighbors("Widgets", "Log")
            lateinit var view: View
            scenario.onActivity { view = it.window.decorView }
            val field = listOf("assistant-key", "assistant-ask").first { tag ->
                runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess
            }
            compose.onNodeWithTag(field).performClick()
            val ime = WindowInsetsCompat.Type.ime()
            fun imeUp() = ViewCompat.getRootWindowInsets(view)?.isVisible(ime) == true
            compose.waitUntil(10_000) { imeUp() }
            // The terminal hides the keyboard over a page without fields of its own within a frame or two.
            Thread.sleep(1_500)
            compose.waitForIdle()
            assertTrue("the keyboard stays up on the Assistant's page", imeUp())
        }
    }

    /** A screen opened from the Assistant's page, with no widgets pages, returns to the Assistant (it had asked for widgets page 1: place -1). */
    @Test
    fun backFromAScreenReturnsToTheAssistant() {
        assumeTrue(BuildConfig.ASSISTANT)
        config.set(Keys.WIDGETS_SHOW, "off")
        config.set(Keys.ASSISTANT_PAGE, "on")
        val (scenario, vm) = start()
        scenario.use {
            vm.waitForLog()
            waitForNeighbors("Assistant", null)
            compose.onNodeWithTag("home-pager").performCustomAccessibilityActionWithLabel("Assistant")
            waitForNeighbors(null, "Log")
            instrumentation.runOnMainSync { vm.openScreen(Screen.SETTINGS) }
            compose.waitForIdle()
            instrumentation.runOnMainSync { vm.closeScreen() }
            waitForNeighbors(null, "Log")
            assertEquals("assistant", vm.homePageShown)
        }
    }

    /** The widgets pages turned off while Settings is open over one: Back opens the log, beside the Assistant. */
    @Test
    fun aWidgetsPageGoneWhileAwayOpensTheLog() {
        assumeTrue(BuildConfig.ASSISTANT)
        config.set(Keys.WIDGETS_SHOW, "on")
        config.set(Keys.ASSISTANT_PAGE, "on")
        val (scenario, vm) = start()
        scenario.use {
            vm.waitForLog()
            instrumentation.runOnMainSync { vm.runCommand("widget page") }
            waitForTag("widgets-page")
            compose.waitUntil(10_000) { vm.homePageShown == "widgets" }
            instrumentation.runOnMainSync {
                vm.settingsCategory.value = "widgets"
                vm.openScreen(Screen.SETTINGS)
            }
            compose.waitForIdle()
            config.set(Keys.WIDGETS_SHOW, "off")
            instrumentation.runOnMainSync { vm.closeScreen() }
            vm.waitForLog()
            waitForNeighbors("Assistant", null)
            compose.waitUntil(10_000) { vm.homePageShown == "log" }
        }
    }

    /** Page order lists the pages; a move sets home.pages, and Reset to default empties it. */
    @Test
    fun pageOrderMovesAPageAndResets() {
        config.set(Keys.WIDGETS_SHOW, "on")
        val (scenario, vm) = start()
        scenario.use {
            vm.waitForLog()
            // From Settings → Widgets.
            instrumentation.runOnMainSync {
                vm.settingsCategory.value = "widgets"
                vm.openScreen(Screen.SETTINGS)
            }
            compose.onNodeWithText("Page order").performClick()
            compose.waitUntil(5_000) { vm.screen.value == Screen.HOME_PAGES }
            compose.onNodeWithTag("page-widgets").assertIsDisplayed()
            compose.onNodeWithTag("page-log").assertIsDisplayed()
            compose.onNodeWithTag("home-pages-reset").assertIsNotEnabled()
            // Widgets page 1 is left of the log: moving the log up puts it first.
            compose.onNodeWithTag("page-log").performCustomAccessibilityActionWithLabel("Move up")
            compose.waitUntil(5_000) { config[Keys.HOME_PAGES] == "log,widgets" }
            compose.onNodeWithTag("home-pages-reset").assertIsEnabled().performClick()
            compose.waitUntil(5_000) { config.isDefault(Keys.HOME_PAGES) }
            assertEquals("", config[Keys.HOME_PAGES])
        }
    }
}
