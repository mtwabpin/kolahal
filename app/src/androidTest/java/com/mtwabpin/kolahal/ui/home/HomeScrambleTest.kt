package com.mtwabpin.kolahal.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.HomeScramble
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.tty.GridMetrics
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `scramble`: home with lines that hold known words and a picture shows none of the words, in the
 * tree's text and on the grid's cells, in both styles: when home is made while it is on, after a
 * scroll, for a new line, and when the screen is made again. `scramble off` brings them back.
 * It works the same with the header off (the default) and on; only with the header on does home
 * say that it is scrambled, first in the header.
 */
@RunWith(AndroidJUnit4::class)
class HomeScrambleTest {
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
    private val scramble = HomeScramble.get(app)

    private var metrics: GridMetrics? = null

    /** Counts how often the screen was made again. */
    private var made by mutableIntStateOf(0)

    @Before
    fun off() {
        scramble.turnOff()
    }

    @After
    fun offAgain() {
        scramble.turnOff()
    }

    /** Home in [style], with the header on or off (`header.show`, off by default). */
    private fun show(style: String, header: Boolean = false): TerminalViewModel {
        config.set(Keys.HOME_STYLE, style)
        config.set(Keys.HEADER_SHOW, if (header) "on" else "off")
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                metrics = rememberTtyGrid().metrics
                Box(Modifier.fillMaxSize()) { key(made) { TerminalScreen(vm) } }
            }
        }
        return vm
    }

    private fun waitForLine(id: Long) = compose.waitUntil(5_000) {
        compose.onAllNodesWithTag("line-$id").fetchSemanticsNodes().isNotEmpty()
    }

    /** What home says: the text of its tree, and on the grid the text of the cells the log shows. */
    private fun shown(vm: TerminalViewModel): String {
        val tree = compose.onRoot(useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        val m = metrics ?: return tree
        val cells = compose.runOnUiThread { vm.shownLog.read(m.cellW, m.cellH) }
            ?.rows?.joinToString("\n") { it.block.grid.rowText(it.row) }.orEmpty()
        return tree + "\n" + cells
    }

    private fun assertHidden(vm: TerminalViewModel, words: List<String>) {
        compose.waitForIdle()
        val text = shown(vm)
        for (w in words) assertFalse("'$w' shows:\n$text", text.contains(w, ignoreCase = true))
    }

    private fun scrambles(style: String, header: Boolean) {
        val mark = System.nanoTime().toString().takeLast(6)
        val words = listOf("Quokka$mark", "Wombat$mark", "Platypus", "Dingo$mark")
        logs.add(LogKind.COMMAND, "echo Quokka$mark")
        logs.add(LogKind.SCRIPT, "Wombat$mark called at $mark", source = "Platypus")
        val picture = logs.add(
            LogKind.SCRIPT,
            "Dingo$mark",
            source = "Platypus",
            ctx = LogContext.of(LogContext.IMAGE to "https://kolahal.example/dingo$mark.png"),
        )
        // On before home is made: its first frame is scrambled already. The header says so when it is on;
        // off, there is no header at all.
        scramble.turnOn()
        val vm = show(style, header)
        waitForLine(picture)
        if (header) compose.onNodeWithTag("header-scramble").assertExists()
        else compose.onNodeWithTag("header").assertDoesNotExist()
        assertHidden(vm, words)
        // The picture is a plain block, and nothing is fetched.
        if (style == "gui") {
            assertTrue(compose.onAllNodesWithTag("scrambled-picture", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }

        // A line that comes while it is on.
        val fresh = logs.add(LogKind.OUTPUT, "Narwhal$mark")
        waitForLine(fresh)
        assertHidden(vm, words + "Narwhal$mark")

        // Scrolling the log.
        val last = (0 until 30).map { logs.add(LogKind.OUTPUT, "Filler$mark $it") }.last()
        waitForLine(last)
        compose.onNodeWithTag("log").performTouchInput { swipeDown() }
        assertHidden(vm, words + "Narwhal$mark" + "Filler$mark")
        compose.onNodeWithTag("log").performTouchInput { swipeUp() }
        assertHidden(vm, words + "Narwhal$mark" + "Filler$mark")

        // The screen made again.
        compose.runOnIdle { made++ }
        waitForLine(last)
        assertHidden(vm, words + "Narwhal$mark" + "Filler$mark")

        // Off: the words come back, and the header says nothing; with the header off there is none.
        scramble.turnOff()
        compose.waitUntil(5_000) { shown(vm).contains("Filler$mark") }
        compose.onNodeWithTag("header-scramble").assertDoesNotExist()
        if (header) compose.onNodeWithTag("header").assertExists()
        else compose.onNodeWithTag("header").assertDoesNotExist()
    }

    @Test
    fun theGuiStyleShowsNoWord() = scrambles("gui", header = false)

    /**
     * Suggestions that hold the user's words: an earlier line shows its command and none of its
     * words, and a source of the log shows scrambled, in `/@` and in `log only`.
     */
    @Test
    fun suggestionsShowNoWordOfTheUsers() {
        val mark = System.nanoTime().toString().takeLast(6)
        logs.add(LogKind.COMMAND, "echo Quokka$mark")
        val line = logs.add(LogKind.SCRIPT, "Wombat$mark", source = "Platypus$mark")
        scramble.turnOn()
        val vm = show("gui")
        waitForLine(line)
        // What is typed shows in the prompt; what the rows add is scrambled, in the shape it has.
        for ((typed, row) in listOf(
            "echo Quo" to Regex("echo [A-Z][a-z]{5}[0-9]{6}"),
            "/@platy" to Regex("@[a-z]{8}[0-9]{6}"),
            "log only platy" to Regex("[A-Z][a-z]{7}[0-9]{6}"),
        )) {
            compose.runOnIdle { vm.onInputChange(TextFieldValue(typed, TextRange(typed.length))) }
            compose.waitUntil(5_000) { row.containsMatchIn(shown(vm)) }
            assertHidden(vm, listOf("Quokka$mark", "Platypus$mark", "Wombat$mark"))
        }
    }

    @Test
    fun theTtyStyleShowsNoWord() = scrambles("tty", header = false)

    @Test
    fun theGuiStyleWithTheHeaderShowsNoWord() = scrambles("gui", header = true)

    @Test
    fun theTtyStyleWithTheHeaderShowsNoWord() = scrambles("tty", header = true)
}
