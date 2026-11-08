package com.mtwabpin.kolahal.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
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
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Scripts → Add script → From the library: what each ready-made script is, installing one, and your copies that differ. */
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
        vm = vms.terminal(app)
        compose.setContent { ScriptLibraryScreen(vm) }
        // In a full run, with the log and scripts earlier classes left, the first load is slower.
        runCatching { compose.waitUntil(15_000) { compose.onAllNodesWithText("Countdowns").fetchSemanticsNodes().isNotEmpty() } }
            .onFailure { throw AssertionError("no countdown row; shown: " + shownTexts(), it) }
    }

    private fun shownTexts() = compose.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }.distinct().take(40)

    /** A button in the countdown row. */
    private fun inCountdownRow(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasText("Countdowns")))

    /** Waits for the countdown row to say [word] (Installed, Edited): plain text, part of the row itself. */
    private fun countdownRowSays(word: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Countdowns") and hasText(word)).fetchSemanticsNodes().isNotEmpty() }

    /**
     * It opens at its first script. With the rows read after the first frame, "Get more scripts" shown
     * alone first kept its place as they came above it, and the list opened at its end (seen in a full run).
     */
    @Test fun itOpensAtItsFirstScript() {
        show()
        compose.onNode(hasText(library.entries.first().file + " · ", substring = true)).assertIsDisplayed()
    }

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
        compose.waitForText("Installed countdown.lua — run it as 'countdown'")
        countdownRowSays("Installed")
        inCountdownRow("Install").assertDoesNotExist()
        val s = scripts.byName("countdown")!!
        assertEquals(library.code(entry), s.code)
        assertEquals(listOf("00:05"), s.atTimes)
    }

    /** A copy from an older Kolahal, never edited, comes first and offers Update, which brings this version's at once. */
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
        // It says it updated it, not that it installed it.
        compose.waitForText("countdown.lua is updated")
        countdownRowSays("Installed")
        inCountdownRow("Update").assertDoesNotExist()
    }

    /**
     * A copy you edited comes first, under "Your copy differs", with Compare and Reset instead of the
     * plain word. Reset asks first, as your edits go, then brings the library version back.
     */
    @Test fun anEditedCopyComesFirstWithCompareAndReset() {
        val s = library.install(entry)
        val mine = library.code(entry) + "\n-- mine"
        scripts.saveCode(s.id, mine, byUser = true)
        show()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Countdowns") and hasText("Edited. Nothing newer.")).fetchSemanticsNodes().isNotEmpty() }
        // The group is above the library's first script, which isn't in it.
        val header = compose.onNodeWithText("Your copy differs", substring = true).getUnclippedBoundsInRoot()
        val row = compose.onNode(hasText("Countdowns") and hasText("Edited. Nothing newer.")).getUnclippedBoundsInRoot()
        assertTrue("the group's header above its row: $header, $row", header.bottom <= row.top)
        compose.onNodeWithText("All scripts").assertExists()
        inCountdownRow("Install").assertDoesNotExist()

        compose.onNode(hasContentDescription("Compare") and hasAnyAncestor(hasText("Countdowns"))).performClick()
        compose.waitForNode(hasTestTag("compare"))
        compose.onNodeWithText("-- mine").assertExists()
        compose.onNode(hasContentDescription("Close") and hasAnyAncestor(hasTestTag("compare"))).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("compare")).fetchSemanticsNodes().isEmpty() }

        inCountdownRow("Reset").performClick()
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        assertEquals(mine, scripts.byName("countdown")!!.code)
        compose.onNode(hasText("Reset") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { scripts.byName("countdown")?.code == library.code(entry) }
        compose.waitForText("countdown.lua is back to the library version")
        countdownRowSays("Installed")
    }

    /** Back returns to the page the library was opened from. */
    @Test fun backReturnsToScripts() {
        vm = vms.terminal(app)
        compose.runOnUiThread { vm.openScreen(Screen.SCRIPTS); vm.openScreen(Screen.SCRIPT_LIBRARY) }
        compose.setContent { ScriptLibraryScreen(vm) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Screen.SCRIPTS, vm.screen.value) }
    }
}
