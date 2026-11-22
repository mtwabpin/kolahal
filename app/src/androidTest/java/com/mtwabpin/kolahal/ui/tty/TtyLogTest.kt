package com.mtwabpin.kolahal.ui.tty

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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
        // widgets.page is on the left by default: a swipe right copies.
        compose.onNodeWithTag("line-$id").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Copied").fetchSemanticsNodes().isNotEmpty() }
    }
}
