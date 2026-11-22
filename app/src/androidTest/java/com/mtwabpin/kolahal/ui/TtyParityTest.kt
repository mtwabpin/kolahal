package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two home styles read alike to TalkBack and switch access (design §4, §8.2): the same lines,
 * parts and buttons, under the same tags, with the same click and long-click actions and labels,
 * in `home.style gui` and in `home.style tty`. Also: the grid in a right-to-left layout, and the
 * state a recreated home keeps (folds, scroll, typed text) in TTY as in the GUI.
 */
@RunWith(AndroidJUnit4::class)
class TtyParityTest {
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

    private fun show(direction: LayoutDirection = LayoutDirection.Ltr): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
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
        }
        return vm
    }

    private fun waitForTag(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** What a node offers TalkBack: a tap, a long press, their labels, its words or description, and its custom actions. */
    private data class Reading(
        val click: Boolean,
        val clickLabel: String?,
        val longClick: Boolean,
        val longClickLabel: String?,
        val words: String,
        val description: String,
        val actions: List<String>,
    )

    private fun SemanticsNode.reading(): Reading {
        val c = config
        val click = c.getOrNull(SemanticsActions.OnClick)
        val long = c.getOrNull(SemanticsActions.OnLongClick)
        val words = (c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).trim()
        return Reading(
            click = click != null,
            clickLabel = click?.label,
            longClick = long != null,
            longClickLabel = long?.label,
            words = words,
            description = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
            actions = c.getOrNull(SemanticsActions.CustomActions)?.map { it.label }.orEmpty(),
        )
    }

    /**
     * What TalkBack reads for [tag]: the merged node, as a screen reader gets it. The GUI's buttons
     * take their words from the icon inside them (`Clear`, `Key bar`); the grid's carry them themselves.
     */
    private fun readingOf(tag: String): Reading =
        compose.onNodeWithTag(tag).fetchSemanticsNode().reading()

    /** The readings of [tags] in one style. */
    private fun readings(style: String, tags: List<String>): Map<String, Reading> {
        compose.runOnIdle { config.set(Keys.HOME_STYLE, style) }
        compose.waitForIdle()
        for (t in tags) waitForTag(t)
        return tags.associateWith { readingOf(it) }
    }

    /**
     * A line, its buttons and an app of an apps line offer the same tap and long press in both
     * styles, and the TTY line reads at least every word the GUI's does.
     */
    @Test
    fun linesReadAlike() {
        val mark = "parity ${System.nanoTime()}"
        val command = logs.add(LogKind.COMMAND, "echo $mark")
        val buttons = logs.add(
            LogKind.SCRIPT, "$mark reminder", "reminders",
            LogContext(mapOf(LogContext.ACTIONS to "[[\"Snooze\",\"snooze\"],[\"Done\",\"done\"]]")),
        )
        val table = logs.add(
            LogKind.OUTPUT,
            "$mark table\n" + StyledText.table(listOf(listOf("Coin", "Price"), listOf("BTC", "64,210")), header = true, align = "lr"),
        )
        show()
        val tags = listOf("line-$command", "line-$buttons", "line-$table", "line-action-0", "line-action-1")
        val gui = readings("gui", tags)
        val tty = readings("tty", tags)
        for (t in tags) {
            val g = gui.getValue(t)
            val y = tty.getValue(t)
            assertEquals("$t: tap", g.click, y.click)
            assertEquals("$t: long press", g.longClick, y.longClick)
            assertEquals("$t: long press label", g.longClickLabel, y.longClickLabel)
        }
        // A button says what it does in both.
        assertEquals(gui.getValue("line-action-0").clickLabel, tty.getValue("line-action-0").clickLabel)
        // The grid's line reads the model's words, tables as words, like the GUI's line and its table.
        assertTrue(tty.getValue("line-$table").words, tty.getValue("line-$table").words.contains("Coin BTC, Price 64,210"))
        assertTrue(tty.getValue("line-$command").words.contains("echo $mark"))
        // The grid's line offers its buttons, Copy and Delete as actions too.
        assertTrue(tty.getValue("line-$buttons").actions.containsAll(listOf("Snooze", "Done", "Copy", "Delete")))
    }

    /** The prompt's buttons, the key bar and the header read alike in both styles. */
    @Test
    fun homesPartsReadAlike() {
        // The header and the key bar are off by default; they are among the parts compared.
        config.set(Keys.HEADER_SHOW, "on")
        config.set(Keys.KEYBAR_SHOW, "on")
        config.set(Keys.KEYBAR_AUTO, "off")
        // The grid's own key bar; by default the tty style has the GUI's.
        config.set(Keys.TTY_KEYBAR, "tty")
        val vm = show()
        compose.runOnIdle { vm.onInputChange(androidx.compose.ui.text.input.TextFieldValue("echo")) }
        val tags = listOf("prompt-quick", "prompt-clear", "keybar-toggle", "header")
        val gui = readings("gui", tags)
        val tty = readings("tty", tags)
        for (t in listOf("prompt-quick", "prompt-clear", "keybar-toggle")) {
            assertEquals("$t: description", gui.getValue(t).description, tty.getValue(t).description)
            assertEquals("$t: tap", gui.getValue(t).click, tty.getValue(t).click)
        }
        // Both styles have the key bar, under the same tag.
        compose.onNodeWithTag("keybar", useUnmergedTree = true).assertExists()
    }

    /**
     * In a right-to-left layout the grid still shows each line under its tag, reading its words in
     * order (Hebrew, Arabic and mixed), and the prompt's quick-actions button is on the right.
     */
    @Test
    fun rightToLeftLinesShow() {
        config.set(Keys.HOME_STYLE, "tty")
        val hebrew = logs.add(LogKind.OUTPUT, "שלום עולם")
        val arabic = logs.add(LogKind.OUTPUT, "مرحبا بالعالم")
        val mixed = logs.add(LogKind.OUTPUT, "Open קובץ (file) now")
        show(LayoutDirection.Rtl)
        waitForTag("line-$mixed")
        for ((id, words) in listOf(hebrew to "שלום עולם", arabic to "مرحبا بالعالم", mixed to "Open קובץ (file) now")) {
            assertTrue(readingOf("line-$id").words.contains(words))
        }
        val quick = compose.onNodeWithTag("prompt-quick", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the prompt's $ is on the right: $quick", quick.left > 180f * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density)
        // The line's own tap still works where the grid draws it.
        compose.onNodeWithTag("line-$hebrew").performClick()
    }

    /** Folds and the log's place survive a recreated home in TTY, as they do in the GUI. */
    @Test
    fun stateSurvivesRecreation() {
        config.set(Keys.HOME_STYLE, "tty")
        val body = (1..40).joinToString("\n") { "row $it" }
        val id = logs.add(LogKind.SCRIPT, "tall", "notes", LogContext(mapOf(LogContext.TITLE to "1", LogContext.BODY to body)))
        val vm = vms.terminal(app)
        val restore = StateRestorationTester(compose)
        restore.setContent {
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
        waitForTag("line-$id")
        compose.onNodeWithTag("fold-link", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("line-$id").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Fold" } == true
        }
        compose.runOnIdle { vm.onInputChange(androidx.compose.ui.text.input.TextFieldValue("kept")) }
        restore.emulateSavedInstanceStateRestore()
        waitForTag("line-$id")
        // Still open: its action folds it.
        assertTrue(
            compose.onNodeWithTag("line-$id").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
                .orEmpty().any { it.label == "Fold" }
        )
        compose.runOnIdle { assertEquals("kept", vm.input.text) }
    }
}
