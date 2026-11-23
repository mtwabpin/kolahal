package com.mtwabpin.kolahal.ui.tty

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
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
import com.mtwabpin.kolahal.ui.home.TerminalScreen
import com.mtwabpin.kolahal.ui.theme.BottomCorners
import com.mtwabpin.kolahal.ui.theme.HomePage
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.theme.LocalBottomCorners
import com.mtwabpin.kolahal.ui.theme.LocalHomePage
import com.mtwabpin.kolahal.ui.theme.ScreenCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home on the character grid (`home.style tty`): the prompt, the suggestions, the key bar, the
 * header, the long-press sheet and the app selector, with the GUI's tags, words and calls.
 * Some parts are the GUI's own by default (`tty.suggestions`, `tty.selector`, `tty.keybar`,
 * `tty.quick-actions`, `tty.menu`): the tests of the grid's parts put them all on the grid first
 * ([allOnTheGrid]), and a test for each part checks both.
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

    /** Puts every part that can be the GUI's on the grid. The tests of the grid's parts start with this. */
    private fun allOnTheGrid() {
        for (key in PARTS) config.set(key, "tty")
    }

    /** Whether a node tagged [tag] shows. */
    private fun hasTag(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Waits for the grid's own version of a part, by [tag]: the `tty.*` keys switch at once. */
    private fun waitForGrid(tag: String) = compose.waitUntil(5_000) { hasTag(tag) }

    /** Whether a node that reads exactly [text] shows. */
    private fun hasWords(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Puts [text] in the prompt, the cursor at its end. */
    private fun type(vm: TerminalViewModel, text: String) {
        compose.runOnIdle { vm.onInputChange(TextFieldValue(text, TextRange(text.length))) }
        compose.waitForIdle()
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
        allOnTheGrid()
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
        allOnTheGrid()
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
        allOnTheGrid()
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
        allOnTheGrid()
        show()
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Quick actions").fetchSemanticsNodes().isNotEmpty() }
    }

    /** A tap on a quick action runs it and closes the sheet: Unlocks this week runs `unlocks 7`. */
    @Test
    fun aQuickActionRuns() {
        show()
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Unlocks this week").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Unlocks this week").performClick()
        compose.waitUntil(5_000) { logs.recent(50, afterId = lines.start).any { it.text == "unlocks 7" } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Quick actions").fetchSemanticsNodes().isEmpty() }
    }

    /**
     * A finger moves a little between down and up, and the tap still runs the action. A click
     * without a move (`performClick`, `input tap`) passed while every tap by hand was lost.
     */
    @Test
    fun aQuickActionRunsWhenTheFingerMoves() {
        show()
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Unlocks this week").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Unlocks this week").performTouchInput {
            down(center)
            moveBy(Offset(1f, 2f), delayMillis = 30)
            moveBy(Offset(1f, -1f), delayMillis = 30)
            up()
        }
        compose.waitUntil(5_000) { logs.recent(50, afterId = lines.start).any { it.text == "unlocks 7" } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Quick actions").fetchSemanticsNodes().isEmpty() }
    }

    /**
     * A tap on the sheet's border runs nothing and leaves the sheet open. The unmerged node is the
     * border's own row: merged, the title belongs to the whole dialog, whose middle is an action.
     */
    @Test
    fun aTapOnTheSheetsBorderKeepsIt() {
        show()
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Quick actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Quick actions", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f), delayMillis = 30)
            up()
        }
        compose.onNodeWithText("Quick actions", useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("Quick actions").assertExists()
        compose.onNodeWithText("Unlocks this week").assertExists()
    }

    /** The app selector opens on the grid: a slide along the edge brings its list in. */
    @Test
    fun theSelectorOpens() {
        allOnTheGrid()
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
        allOnTheGrid()
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
        compose.onNodeWithTag("key-esc").performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals("", vm.input.text) }
    }

    /** `tty.suggestions`: the GUI's list by default, the grid's once set. A tap picks in both. */
    @Test
    fun theSuggestionsAreTheGuisOrTheGrids() {
        val vm = show()
        val help = hasClickAction() and hasText("help") and hasAnyAncestor(hasTestTag("suggestions"))
        type(vm, "hel")
        compose.waitUntil(5_000) { compose.onAllNodes(help).fetchSemanticsNodes().isNotEmpty() }
        assertFalse("the grid's list shows", hasTag("tty-suggestion"))
        compose.onAllNodes(help)[0].performClick()
        compose.runOnIdle { assertTrue(vm.input.text, vm.input.text.startsWith("help")) }

        compose.runOnIdle { config.set(Keys.TTY_SUGGESTIONS, "tty") }
        type(vm, "hel")
        val onGrid = help and hasTestTag("tty-suggestion")
        compose.waitUntil(5_000) { compose.onAllNodes(onGrid).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(onGrid)[0].performClick()
        compose.runOnIdle { assertTrue(vm.input.text, vm.input.text.startsWith("help")) }
    }

    /** `tty.suggestions` holds for the chips too (`suggestions.style chips`). */
    @Test
    fun theChipsAreTheGuisOrTheGrids() {
        config.set(Keys.SUGGESTIONS_STYLE, "chips")
        val vm = show()
        val help = hasText("help") and hasAnyAncestor(hasTestTag("suggestions"))
        type(vm, "hel")
        compose.waitUntil(5_000) { compose.onAllNodes(help).fetchSemanticsNodes().isNotEmpty() }
        assertFalse("the grid's chips show", hasTag("tty-suggestion"))

        compose.runOnIdle { config.set(Keys.TTY_SUGGESTIONS, "tty") }
        compose.waitUntil(5_000) {
            compose.onAllNodes(help and hasTestTag("tty-suggestion")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * `tty.selector`: the GUI's app selector by default, the grid's once set. Both open with a slide
     * along the edge and stay open when the finger lifts there; a long press on an app opens its actions.
     */
    @Test
    fun theSelectorIsTheGuisOrTheGrids() {
        config.set(Keys.SELECTOR_RAIL, "on")
        show()
        openSelector()
        assertFalse("the grid's rows show", hasTag("tty-selector-row"))
        openAnAppsActions()

        compose.runOnIdle { config.set(Keys.TTY_SELECTOR, "tty") }
        openSelector()
        waitForGrid("tty-selector-row")
        openAnAppsActions()
    }

    /** An app's row in the open selector. */
    private val selectorRow = hasClickAction() and hasAnyAncestor(hasTestTag("app-selector-panel"))

    /** Slides along the edge and lifts there, so the list stays open. */
    private fun openSelector() {
        keyboardDown()
        compose.waitUntil(5_000) { hasTag("app-selector") }
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveBy(Offset(0f, 20.dp.toPx()), delayMillis = 50)
        }
        compose.waitUntil(5_000) { hasTag("app-selector-panel") }
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.waitUntil(5_000) { compose.onAllNodes(selectorRow).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Long-presses an app in the open selector: its actions open, and one of them closes them. */
    private fun openAnAppsActions() {
        compose.onAllNodes(selectorRow)[0].performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitUntil(5_000) { hasWords("Copy package name") }
        compose.onNodeWithText("Copy package name").performClick()
        compose.waitUntil(5_000) { !hasWords("Copy package name") }
    }

    /** `tty.keybar`: the GUI's bar under the grid's prompt by default, the grid's once set. A key works in both. */
    @Test
    fun theKeyBarIsTheGuisOrTheGrids() {
        config.set(Keys.KEYBAR_SHOW, "on")
        config.set(Keys.KEYBAR_AUTO, "off")
        config.set(Keys.KEYBAR, "ESC,TAB,UP,DOWN")
        val vm = show()
        compose.waitUntil(5_000) { hasTag("keybar") }
        assertFalse("the grid's bar shows", hasTag("tty-keybar"))
        // The bar is right under the grid's prompt, across the whole of home.
        val quick = compose.onNodeWithTag("prompt-quick").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("keybar").fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithTag("tty-home").fetchSemanticsNode().boundsInRoot
        assertTrue("the bar starts at ${bar.top}, the prompt ends at ${quick.bottom}", bar.top >= quick.bottom - 1f)
        assertEquals(home.left, bar.left, 1f)
        assertEquals(home.right, bar.right, 1f)
        type(vm, "ab")
        // The GUI's keys are words and icons, pressed by touch.
        compose.onNode(hasText("ESC") and hasAnyAncestor(hasTestTag("keybar")), useUnmergedTree = true)
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals("", vm.input.text) }

        compose.runOnIdle { config.set(Keys.TTY_KEYBAR, "tty") }
        waitForGrid("tty-keybar")
        type(vm, "ab")
        compose.onNodeWithTag("key-esc").performTouchInput { click() }
        compose.runOnIdle { assertEquals("", vm.input.text) }
    }

    /**
     * `tty.quick-actions`: the GUI's bottom sheet by default, a box on the grid once set. A tap runs
     * an action in both, and `tty.menu` doesn't change them.
     */
    @Test
    fun theQuickActionsAreTheGuisOrTheGrids() {
        config.set(Keys.TTY_MENU, "tty")
        show()
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.waitUntil(5_000) { hasWords("Unlocks this week") }
        assertFalse("the grid's box shows", hasTag("tty-sheet"))
        compose.onNodeWithText("Unlocks this week").performClick()
        compose.waitUntil(5_000) { !hasWords("Quick actions") }

        compose.runOnIdle { config.set(Keys.TTY_QUICK_ACTIONS, "tty") }
        compose.onNodeWithTag("prompt-quick").performClick()
        waitForGrid("tty-sheet")
        compose.onNodeWithText("Unlocks this week").performClick()
        compose.waitUntil(5_000) { !hasTag("tty-sheet") }
    }

    /**
     * `tty.menu`: a line's long press opens the GUI's bottom sheet by default, a box on the grid once
     * set. A tap runs an action in both, and `tty.quick-actions` doesn't change them.
     */
    @Test
    fun theLinesMenuIsTheGuisOrTheGrids() {
        config.set(Keys.TTY_QUICK_ACTIONS, "tty")
        val id = logs.add(LogKind.OUTPUT, "menu ${System.nanoTime()}")
        show()
        compose.waitUntil(5_000) { hasTag("line-$id") }
        compose.onNodeWithTag("line-$id").performTouchInput { longClick() }
        compose.waitUntil(5_000) { hasWords("Copy text") }
        assertFalse("the grid's box shows", hasTag("tty-sheet"))
        compose.onNodeWithText("Copy text").performClick()
        compose.waitUntil(5_000) { !hasWords("Copy text") }

        compose.runOnIdle { config.set(Keys.TTY_MENU, "tty") }
        compose.onNodeWithTag("line-$id").performTouchInput { longClick() }
        waitForGrid("tty-sheet")
        compose.onNodeWithText("Copy text").performClick()
        compose.waitUntil(5_000) { !hasTag("tty-sheet") }
    }

    /** In the gui style the `tty.*` keys change nothing: every part is the GUI's. */
    @Test
    fun theGuiStyleHasNoPartOnTheGrid() {
        allOnTheGrid()
        config.set(Keys.KEYBAR_SHOW, "on")
        config.set(Keys.KEYBAR_AUTO, "off")
        val vm = show()
        waitForGrid("tty-keybar")
        compose.runOnIdle { config.set(Keys.HOME_STYLE, "gui") }
        compose.waitUntil(5_000) { !hasTag("tty-home") }
        assertTrue("no key bar", hasTag("keybar"))
        assertFalse("the grid's bar shows", hasTag("tty-keybar"))
        type(vm, "hel")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("help") and hasAnyAncestor(hasTestTag("suggestions")))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse("the grid's list shows", hasTag("tty-suggestion"))
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.waitUntil(5_000) { hasWords("Quick actions") }
        assertFalse("the grid's box shows", hasTag("tty-sheet"))
    }

    /**
     * Rounded screen corners at the bottom: where they cut the prompt's row, the cells they would cut
     * at its ends stay empty, so the `$` and the text move in by whole cells and the field under them
     * with them. The targets still reach the row's ends. A row above the corners keeps its place.
     */
    @Test
    fun theBottomCornersMoveThePromptsEndsIn() {
        config.set(Keys.HOME_STYLE, "tty")
        val vm = vms.terminal(app)
        var corners by mutableStateOf<BottomCorners?>(BottomCorners.NONE)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalBottomCorners provides corners) {
                    Box(
                        Modifier
                            .size(360.dp, 700.dp)
                            .consumeWindowInsets(WindowInsets.safeDrawing)
                    ) {
                        TerminalScreen(vm)
                    }
                }
            }
        }
        compose.waitUntil(5_000) { hasTag("tty-home") }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val field = bounds("prompt")
        val quick = bounds("prompt-quick")
        val toggle = bounds("keybar-toggle")
        assertTrue("the text starts at ${field.left}", field.left < 150f)
        // Corners of 100 px centered 150 px in from each side, near the window's top: every row lower
        // than 101 px is past their circles, so they hide 150 px at each end of the row.
        val widthPx = with(compose.density) { 360.dp.roundToPx() }
        compose.runOnIdle { corners = BottomCorners(ScreenCorner(100, 150, 1), ScreenCorner(100, widthPx - 150, 1)) }
        compose.waitForIdle()
        val moved = bounds("prompt")
        // The `$` stands past the 150 px, and the text after it.
        assertTrue("the text starts at ${moved.left}", moved.left > 150f)
        // The buttons stand before the right corner's 150 px, and the text before them.
        assertTrue("the text ends at ${moved.right}", moved.right < widthPx - 150f)
        assertEquals(quick.left, bounds("prompt-quick").left, 1f)
        assertEquals(toggle.right, bounds("keybar-toggle").right, 1f)
        // Typing still goes into the field.
        type(vm, "ab")
        compose.onNodeWithTag("prompt", useUnmergedTree = true).performTextInput("c")
        compose.runOnIdle { assertEquals("abc", vm.input.text) }
        // The same corners far under the row: it keeps its place.
        type(vm, "")
        compose.runOnIdle {
            corners = BottomCorners(ScreenCorner(100, 150, 100_000), ScreenCorner(100, widthPx - 150, 100_000))
        }
        compose.waitForIdle()
        assertEquals(field.left, bounds("prompt").left, 1f)
    }

    /**
     * While home turns to or from the widgets page, the log's page slides. The corners belong to the
     * screen, so the cells left empty at the row's ends stay those of the page at rest, and the row
     * moves with the page ([LocalHomePage]).
     */
    @Test
    fun thePromptsEndsMoveWithTheirPage() {
        config.set(Keys.HOME_STYLE, "tty")
        val vm = vms.terminal(app)
        val widthPx = with(compose.density) { 360.dp.roundToPx() }
        // Corners that hide 150 px at each end of every row, as in the test above.
        val corners = BottomCorners(ScreenCorner(100, 150, 1), ScreenCorner(100, widthPx - 150, 1))
        val page = HomePage()
        var shift by mutableIntStateOf(0)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalBottomCorners provides corners, LocalHomePage provides page) {
                    Box(
                        Modifier
                            .size(360.dp, 700.dp)
                            .consumeWindowInsets(WindowInsets.safeDrawing)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .offset { IntOffset(shift, 0) }
                                .onPlaced { page.coordinates = it },
                        ) {
                            TerminalScreen(vm)
                        }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { hasTag("tty-home") }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x
        val field = bounds("prompt")
        assertTrue("the text starts at $field", field > 150f)
        // Halfway through a turn either way: the text moves with the page, by as much as it does.
        for (s in listOf(widthPx / 2, -widthPx / 2)) {
            compose.runOnIdle { shift = s }
            compose.waitForIdle()
            assertEquals(field + s, bounds("prompt"), 1f)
        }
    }

    private companion object {
        /** The parts that can be the GUI's in the tty style. */
        val PARTS = listOf(
            Keys.TTY_SUGGESTIONS,
            Keys.TTY_SELECTOR,
            Keys.TTY_KEYBAR,
            Keys.TTY_QUICK_ACTIONS,
            Keys.TTY_MENU,
        )
    }
}
