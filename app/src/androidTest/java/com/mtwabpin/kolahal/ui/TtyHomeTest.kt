package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home on the character grid (`home.style tty`): the prompt, the suggestions, the key bar, the
 * header, the long-press sheet and the app selector, with the GUI's tags, words and calls.
 */
@RunWith(AndroidJUnit4::class)
class TtyHomeTest {
    @get:Rule
    val vms = TestViewModels()

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val configReset = ConfigReset()

    @get:Rule
    val lines = LogLinesSince()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val logs = LogRepository.get(app)

    /** The view home is drawn in, for putting the keyboard away ([keyboardDown]). */
    private var view: android.view.View? = null

    private fun show(): TerminalViewModel {
        config.set(Keys.HOME_STYLE, "tty")
        val vm = vms.terminal(app)
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            KolahalTheme {
                Box(
                    Modifier
                        .size(360.dp, 700.dp)
                        .consumeWindowInsets(WindowInsets.safeDrawing)
                ) {
                    TerminalScreen(vm)
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tty-home").fetchSemanticsNodes().isNotEmpty() }
        return vm
    }

    /**
     * Puts the keyboard away, as a person does before sliding on the edge: home focuses the prompt,
     * and the selector's strip shows only while the keyboard is down, in both styles.
     */
    private fun keyboardDown() {
        val v = view ?: return
        val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
        compose.runOnUiThread {
            val window = (v.context as? android.app.Activity)?.window ?: return@runOnUiThread
            androidx.core.view.WindowCompat.getInsetsController(window, v).hide(ime)
        }
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(v)?.isVisible(ime) != true }
    }

    /** A node whose words are [count] texts or more (a suggestion reads its name, then its description). */
    private fun hasTexts(count: Int) = SemanticsMatcher("reads $count texts") { node ->
        (node.config.getOrNull(SemanticsProperties.Text)?.count { it.isNotBlank() } ?: 0) >= count
    }

    /** Typing goes into the field under the grid's row, and ✕ clears it, as in the GUI. */
    @Test
    fun thePromptTypes() {
        val vm = show()
        compose.onNodeWithTag("prompt-quick").assertExists()
        compose.onNodeWithTag("keybar-toggle").assertExists()
        compose.onNodeWithTag("prompt-clear").assertDoesNotExist()
        compose.onNodeWithTag("prompt", useUnmergedTree = true).performTextInput("echo grid")
        compose.runOnIdle { assertEquals("echo grid", vm.input.text) }
        compose.onNodeWithTag("prompt-clear").performClick()
        compose.runOnIdle { assertEquals("", vm.input.text) }
    }

    /** A command's suggestion reads its name and its description; a tap picks it. */
    @Test
    fun suggestionsShowWithTheirDescriptions() {
        val vm = show()
        compose.onNodeWithTag("prompt", useUnmergedTree = true).performTextInput("hel")
        compose.onNodeWithTag("suggestions").assertExists()
        val help = hasClickAction() and hasText("help") and hasAnyAncestor(hasTestTag("suggestions"))
        compose.waitUntil(5_000) { compose.onAllNodes(help).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(help and hasTexts(2)).fetchSemanticsNodes().let { assertTrue("no description beside help", it.isNotEmpty()) }
        compose.onAllNodes(help)[0].performClick()
        compose.runOnIdle { assertTrue(vm.input.text, vm.input.text.startsWith("help")) }
    }

    /** The chips style shows the same suggestions as a strip. */
    @Test
    fun chipsShowToo() {
        config.set(Keys.SUGGESTIONS_STYLE, "chips")
        show()
        compose.onNodeWithTag("prompt", useUnmergedTree = true).performTextInput("hel")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("help") and hasAnyAncestor(hasTestTag("suggestions"))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A long press on a line opens its menu on the grid: the same actions, and a tap runs one and closes it. */
    @Test
    fun theSheetOpens() {
        val id = logs.add(LogKind.OUTPUT, "sheet ${System.nanoTime()}")
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("line-$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("line-$id").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Copy text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Delete line").assertExists()
        compose.onNodeWithText("Copy text").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Copy text").fetchSemanticsNodes().isEmpty() }
    }

    /** `$` opens the quick actions on the grid. */
    @Test
    fun quickActionsOpen() {
        show()
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Quick actions").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The app selector opens on the grid: a slide along the edge brings its list in. */
    @Test
    fun theSelectorOpens() {
        config.set(Keys.SELECTOR_RAIL, "on")
        show()
        keyboardDown()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("app-selector").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveBy(Offset(0f, 20.dp.toPx()), delayMillis = 50)
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("app-selector-panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
    }

    /** The header's parts and the key bar are on the grid, with the GUI's tags. */
    @Test
    fun theHeaderAndTheKeyBarShow() {
        config.set(Keys.HEADER_SHOW, "on")
        config.set(Keys.KEYBAR_SHOW, "on")
        config.set(Keys.KEYBAR_AUTO, "off")
        config.set(Keys.KEYBAR, "ESC,TAB,UP,DOWN")
        val vm = show()
        compose.onNodeWithTag("header").assertExists()
        compose.onNodeWithTag("header-time").assertExists()
        assertTrue(compose.onAllNodesWithTag("keybar").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("prompt", useUnmergedTree = true).performTextInput("ab")
        // A key's semantic click presses it once: ESC clears the prompt.
        compose.onNodeWithTag("key-esc").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals("", vm.input.text) }
    }
}
