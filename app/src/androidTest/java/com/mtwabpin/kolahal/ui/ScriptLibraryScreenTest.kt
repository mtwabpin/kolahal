package com.mtwabpin.kolahal.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Scripts → Add script → From the library: what each ready-made script is, and installing or updating one. */
@RunWith(AndroidJUnit4::class)
class ScriptLibraryScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val library = ScriptLibrary(app)
    private val logs = LogRepository.get(app)
    private val entry by lazy { library.entries.single { it.name == "countdown" } }
    private var logStart = 0L
    private var started = false
    private lateinit var vm: TerminalViewModel

    @Before fun setUp() {
        // Every test starts with countdown not installed; a copy of the phone's own is left alone.
        assumeTrue("countdown is installed already", scripts.byName("countdown") == null)
        logStart = logs.maxId()
        started = true
    }

    @After fun tearDown() {
        if (!started) return
        scripts.byName("countdown")?.let { ScriptScheduler.cancel(app, it.id); scripts.delete(it.id) }
        library.forget("countdown")
        logs.recent(200).filter { it.id > logStart && "countdown" in it.text }.forEach { logs.delete(it.id) }
    }

    private fun show() {
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent { ScriptLibraryScreen(vm) }
        // In a full run, with the log and scripts earlier classes left, the first load is slower.
        runCatching { compose.waitUntil(15_000) { compose.onAllNodesWithText("Countdowns").fetchSemanticsNodes().isNotEmpty() } }
            .onFailure { throw AssertionError("no countdown row; shown: " + shownTexts(), it) }
    }

    private fun shownTexts() = compose.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }.distinct().take(40)

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** A button in the countdown row. */
    private fun inCountdownRow(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasText("Countdowns")))

    /** Waits for the countdown row to say [word] (Installed, Edited): plain text, part of the row itself. */
    private fun countdownRowSays(word: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Countdowns") and hasText(word)).fetchSemanticsNodes().isNotEmpty() }

    /** Each row says the script's file and how it runs; a tap opens its description. */
    @Test fun aRowSaysHowTheScriptRunsAndOpensItsDescription() {
        show()
        compose.onNodeWithText("countdown.lua · command").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("charge-reminder.lua · on charger, unplug"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("hn.lua · every 1h"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Countdowns"))
        compose.onNodeWithText(entry.description).assertDoesNotExist()
        compose.onNodeWithText("Countdowns").performClick()
        compose.onNodeWithText(entry.description).assertExists()
        compose.onNodeWithText("Countdowns").performClick()
        compose.onNodeWithText(entry.description).assertDoesNotExist()
    }

    /** Install adds the script with the timers it suggests, and the row then says it is installed. */
    @Test fun installAddsTheScriptWithItsTimers() {
        show()
        inCountdownRow("Install").performClick()
        compose.waitUntil(5_000) { scripts.byName("countdown") != null }
        waitForText("Installed countdown.lua — run it as 'countdown'")
        countdownRowSays("Installed")
        inCountdownRow("Install").assertDoesNotExist()
        val s = scripts.byName("countdown")!!
        assertEquals(library.code(entry), s.code)
        assertEquals(listOf("00:05"), s.atTimes)
    }

    /** A copy from an older Kolahal, never edited, offers Update, which brings this version's. */
    @Test fun anOlderCopyUpdates() {
        val old = "-- @command\n-- @title Countdowns\nprint('older countdown')\n"
        scripts.create("countdown", entry.lang, old)
        app.getSharedPreferences("script_library", Context.MODE_PRIVATE).edit().putString("installed:countdown", old).commit()
        assertEquals(ScriptLibrary.State.UPDATE_AVAILABLE, library.state(entry, scripts.all()))
        show()
        // The row shows before the scripts are read, as not installed: Update comes once they are.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Update") and hasAnyAncestor(hasText("Countdowns"))).fetchSemanticsNodes().isNotEmpty() }
        inCountdownRow("Update").performClick()
        compose.waitUntil(5_000) { scripts.byName("countdown")?.code == library.code(entry) }
        countdownRowSays("Installed")
        inCountdownRow("Update").assertDoesNotExist()
    }

    /** A copy you edited says so, and offers no button here: replacing your edits asks first, in its sheet. */
    @Test fun anEditedCopySaysEdited() {
        val s = library.install(entry)
        scripts.saveCode(s.id, library.code(entry) + "\n-- mine", byUser = true)
        show()
        countdownRowSays("Edited")
        inCountdownRow("Install").assertDoesNotExist()
        inCountdownRow("Update").assertDoesNotExist()
        // Shown, nothing replaces your edits: they're still there a second on.
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue("your edits kept", scripts.byName("countdown")!!.code.endsWith("-- mine"))
            Thread.sleep(50)
        }
    }

    /** Back returns to the page the library was opened from. */
    @Test fun backReturnsToScripts() {
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.runOnUiThread { vm.openScreen(Screen.SCRIPTS); vm.openScreen(Screen.SCRIPT_LIBRARY) }
        compose.setContent { ScriptLibraryScreen(vm) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Screen.SCRIPTS, vm.screen.value) }
    }
}
