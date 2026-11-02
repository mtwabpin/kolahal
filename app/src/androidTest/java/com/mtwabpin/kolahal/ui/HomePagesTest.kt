package com.mtwabpin.kolahal.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Home's two pages: the log, and the widgets page beside it (`widgets.page`), which can come first (`widgets.start`). */
@RunWith(AndroidJUnit4::class)
class HomePagesTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val config = Config.get(instrumentation.targetContext)

    @After fun tearDown() = listOf(Keys.WIDGETS_PAGE, Keys.WIDGETS_START, Keys.SELECTOR_RAIL).forEach(config::reset)

    private fun widgetsPage() = compose.onNodeWithTag("widgets-page")

    /** The first start after an install can take a while on an emulator. */
    private fun waitFor(shown: Boolean) = compose.waitUntil(10_000) {
        runCatching { if (shown) widgetsPage().assertIsDisplayed() else widgetsPage().assertIsNotDisplayed() }.isSuccess
    }

    @Test fun homeOpensOnTheWidgetsPageAndTypingBringsUpTheLog() {
        config.set(Keys.WIDGETS_PAGE, "left")
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
            compose.onNodeWithText("+ Add").assertDoesNotExist()
            // The app selector on both pages, the same: as far down, and as high. Where each starts and how
            // high it is, not its bounds: the log page is off screen, and those are cut to the screen.
            fun strips() = compose.onAllNodesWithTag("app-selector-strip", useUnmergedTree = true).fetchSemanticsNodes().map { it.positionInRoot.y to it.size.height }
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
            // Off again: home is the log, and the widgets page has its title row back.
            config.reset(Keys.WIDGETS_START)
            waitFor(shown = false)
            compose.onNodeWithText("+ Add").assertExists()
        }
    }
}
