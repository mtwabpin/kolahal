package com.mtwabpin.kolahal.ui.tty

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.home.TerminalScreen
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home's log on the character grid (`home.style tty`): the same rows, with the GUI's tags, read as
 * the GUI reads them, and the same taps and swipes. The rest of home is still the GUI's.
 */
@RunWith(AndroidJUnit4::class)
class TtyLogTest {
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

    private fun show(): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent {
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
        return vm
    }

    private fun waitForLine(id: Long) = compose.waitUntil(5_000) {
        compose.onAllNodesWithTag("line-$id").fetchSemanticsNodes().isNotEmpty()
    }

    /** Text nodes inside the line [id]: the GUI draws its words as text, the grid draws them itself. */
    private fun textsIn(id: Long, text: String) = compose.onAllNodes(
        hasText(text, substring = true) and hasAnyAncestor(hasTestTag("line-$id")),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()

    /** The line [id] reads [text] (TalkBack, and tests, find it by its words). */
    private fun hasWords(text: String) = SemanticsMatcher("reads “$text”") { node ->
        node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .any { it.text.contains(text) }
    }

    /** Each kind of line shows under its tag, reading what the GUI reads. */
    @Test
    fun theLogsLinesAreOnTheGrid() {
        config.set(Keys.HOME_STYLE, "tty")
        val mark = "grid ${System.nanoTime()}"
        val command = logs.add(LogKind.COMMAND, "echo $mark")
        val output = logs.add(LogKind.OUTPUT, "$mark output\nits details")
        val table = logs.add(
            LogKind.OUTPUT,
            "$mark table\n" + StyledText.table(
                listOf(listOf("Coin", "Price"), listOf("BTC", "64,210")),
                header = true,
                align = "lr",
            )
        )
        show()
        waitForLine(table)
        compose.onNodeWithTag("line-$command").assert(hasWords("$ echo $mark"))
        compose.onNodeWithTag("line-$output").assert(hasWords("$mark output"))
        compose.onNodeWithTag("line-$output").assert(hasWords("its details"))
        // A table reads as words: each cell after its column's name.
        compose.onNodeWithTag("line-$table").assert(hasWords("Coin BTC, Price 64,210"))
        // The grid draws the words itself: no text nodes inside the line.
        assertTrue(textsIn(output, mark).isEmpty())
    }

    /**
     * On the grid `log.spacing` is a whole line or nothing: above a command there is one empty row at any
     * value but 0, the same at 5 dp and at 40, and none at 0.
     */
    @Test
    fun spacingIsOneEmptyLineOrNone() {
        config.set(Keys.HOME_STYLE, "tty")
        val mark = "gap ${System.nanoTime()}"
        val before = logs.add(LogKind.OUTPUT, "$mark before")
        val command = logs.add(LogKind.COMMAND, "echo $mark")
        show()
        waitForLine(command)
        fun bottom(id: Long) = compose.onNodeWithTag("line-$id").fetchSemanticsNode().boundsInRoot.bottom
        // From the end of the line before the command to the end of the command's one row: the row, and the gap above it.
        fun step(spacing: Int): Int {
            compose.runOnIdle { config.set(Keys.LOG_SPACING, spacing.toString()) }
            compose.waitForIdle()
            return kotlin.math.round(bottom(command) - bottom(before)).toInt()
        }
        val none = step(0)
        assertTrue("a row has a height: $none", none > 0)
        assertEquals("one empty row at the default", 2 * none, step(Keys.LOG_SPACING.default))
        assertEquals("one empty row at the most", 2 * none, step(Keys.LOG_SPACING.range.last))
        assertEquals("one empty row at 1 dp", 2 * none, step(1))
        assertEquals("none again at 0", none, step(0))
    }

    /** `config set home.style` switches at once, both ways, and the lines stay. */
    @Test
    fun theStyleSwitchesWithoutARestart() {
        val mark = "switch ${System.nanoTime()}"
        val id = logs.add(LogKind.OUTPUT, mark)
        show()
        waitForLine(id)
        assertTrue(textsIn(id, mark).isNotEmpty())
        compose.runOnIdle { config.set(Keys.HOME_STYLE, "tty") }
        compose.waitUntil(5_000) { textsIn(id, mark).isEmpty() }
        compose.onNodeWithTag("line-$id").assert(hasWords(mark))
        compose.runOnIdle { config.set(Keys.HOME_STYLE, "gui") }
        compose.waitUntil(5_000) { textsIn(id, mark).isNotEmpty() }
    }

    /** A tall line folds after `log.fold` rows; a tap on its fold row opens it, and another folds it again. */
    @Test
    fun aTallLineFoldsAndOpens() {
        config.set(Keys.HOME_STYLE, "tty")
        val body = (1..40).joinToString("\n") { "row $it" }
        val id = logs.add(
            LogKind.SCRIPT,
            "Notes ${System.nanoTime()}",
            "notes",
            LogContext(mapOf(LogContext.TITLE to "1", LogContext.BODY to body)),
        )
        show()
        waitForLine(id)
        val fold = compose.onNode(
            hasTestTag("fold-link") and hasAnyAncestor(hasTestTag("line-$id")),
            useUnmergedTree = true,
        )
        fold.assert(hasText("more lines", substring = true))
        fold.performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                hasText("▴ Show less") and hasAnyAncestor(hasTestTag("line-$id")),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A swipe copies the line as in the GUI: "Copied" shows where Undo shows. */
    @Test
    fun aSwipeCopies() {
        config.set(Keys.HOME_STYLE, "tty")
        val id = logs.add(LogKind.OUTPUT, "copy me ${System.nanoTime()}")
        show()
        waitForLine(id)
        // The widgets pages are left of the log by default: a swipe right copies.
        compose.onNodeWithTag("line-$id").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Copied").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The notice band is drawn over the grid's bottom rows: no row moves when "Copied" or Undo comes and goes. */
    @Test
    fun theUndoOfferFloatsOverTheGrid() {
        config.set(Keys.HOME_STYLE, "tty")
        val token = "grid overlay ${System.nanoTime()}"
        val ids = (0 until 3).map { logs.add(LogKind.OUTPUT, "$token $it") }
        val vm = show()
        try {
            com.mtwabpin.kolahal.ui.home.assertNoticeFloats(compose, vm, ids)
        } finally {
            compose.runOnIdle { vm.undo() }
            ids.forEach { logs.delete(it) }
        }
    }

    /** As in the GUI: the row under the notice band scrolls up above it while the band shows, and back after. */
    @Test
    fun theRowUnderTheNoticeScrollsUpAboveIt() {
        config.set(Keys.HOME_STYLE, "tty")
        val token = "grid room ${System.nanoTime()}"
        val ids = (0 until 3).map { logs.add(LogKind.OUTPUT, "$token $it") }
        val vm = show()
        try {
            com.mtwabpin.kolahal.ui.home.assertRoomUnderNotice(compose, vm, ids)
        } finally {
            compose.runOnIdle { vm.undo() }
            ids.forEach { logs.delete(it) }
        }
    }

    /** What a screen reader offers on the line [id] after its links, buttons and fold: its swipes, by name. */
    private fun actions(id: Long): List<String> =
        compose.onNodeWithTag("line-$id").fetchSemanticsNode().config
            .getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

    /**
     * Set to star and fresh, the grid's swipes do that, and a screen reader offers them by name, fresh not on
     * the first line shown. "▴ N earlier lines" brings the lines a fresh hid back. With both none a line offers
     * neither and stays where it is.
     */
    @Test
    fun swipesSetToStarAndFreshDoThatOnTheGrid() {
        config.set(Keys.HOME_STYLE, "tty")
        config.set(Keys.LOG_SWIPE_RIGHT, "star")
        config.set(Keys.LOG_SWIPE_LEFT, "fresh")
        logs.hideScreen(logs.maxId())
        val hidden = logs.hiddenCount()
        val token = "grid swipes ${System.nanoTime()}"
        val ids = (0 until 3).map { logs.add(LogKind.OUTPUT, "$token $it") }
        show()
        waitForLine(ids[2])
        assertEquals(listOf("Star line", "Fresh from here"), actions(ids[2]))
        assertEquals(listOf("Star line"), actions(ids[0]))

        compose.onNodeWithTag("line-${ids[2]}").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Starred").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { logs.recent(10).any { it.id == ids[2] && it.starred } }
        compose.waitUntil(5_000) { actions(ids[2]).firstOrNull() == "Unstar line" }

        compose.onNodeWithTag("line-${ids[2]}").performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f) }
        val row = "▴ ${hidden + 2} earlier lines · tap to show"
        compose.waitUntil(5_000) { compose.onAllNodesWithText(row).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithTag("line-${ids[0]}").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(row).performClick()
        waitForLine(ids[0])

        compose.runOnIdle {
            config.set(Keys.LOG_SWIPE_RIGHT, "none")
            config.set(Keys.LOG_SWIPE_LEFT, "none")
        }
        compose.waitUntil(5_000) { actions(ids[1]).isEmpty() }
        compose.onNodeWithTag("line-${ids[1]}").performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f) }
        compose.waitForIdle()
        waitForLine(ids[1])
        assertTrue(compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty())
    }
}
