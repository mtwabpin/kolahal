package com.mtwabpin.kolahal.ui.scripts

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.LibraryTag
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scripts → Add script → From the library: what each ready-made script is, its search and its
 * chips, installing one, and your copies that differ.
 */
@RunWith(AndroidJUnit4::class)
class ScriptLibraryScreenTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()

    // With its activity, for Back.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val library = ScriptLibrary(app)
    private val logs = LogRepository.get(app)
    private val entry by lazy { library.entries.single { it.name == "countdown" } }
    private var logStart = 0L
    private var started = false
    private lateinit var vm: TerminalViewModel

    @Before
    fun setUp() {
        // Every test starts with countdown not installed; a copy of the phone's own is left alone.
        assumeTrue("countdown is installed already", scripts.byName("countdown") == null)
        logStart = logs.maxId()
        started = true
    }

    @After
    fun tearDown() {
        if (!started) return
        scripts.byName("countdown")
            ?.let { ScriptScheduler.cancel(app, it.id); scripts.delete(it.id) }
        library.forget("countdown")
        logs.recent(200).filter { it.id > logStart && "countdown" in it.text }
            .forEach { logs.delete(it.id) }
    }

    /** The library page. With [restoration], it can be torn down and restored as after a rotation. */
    private fun show(restoration: StateRestorationTester? = null) {
        vm = vms.terminal(app)
        if (restoration != null) restoration.setContent { ScriptLibraryScreen(vm) }
        else compose.setContent { ScriptLibraryScreen(vm) }
        // In a full run, with the log and scripts earlier classes left, the first load is slower.
        runCatching {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Countdowns").fetchSemanticsNodes().isNotEmpty()
            }
        }
            .onFailure { throw AssertionError("no countdown row; shown: " + shownTexts(), it) }
    }

    private fun shownTexts() =
        compose.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
            .distinct().take(40)

    /** A button in the countdown row. */
    private fun inCountdownRow(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasText("Countdowns")))

    /** Waits for the countdown row to say [word] (Installed, Edited): plain text, part of the row itself. */
    private fun countdownRowSays(word: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Countdowns") and hasText(word)).fetchSemanticsNodes()
                .isNotEmpty()
        }

    /**
     * It opens at its first script. With the rows read after the first frame, "Get more scripts" shown
     * alone first kept its place as they came above it, and the list opened at its end (seen in a full run).
     */
    @Test
    fun itOpensAtItsFirstScript() {
        show()
        compose.onNode(hasText(library.entries.first().file + " · ", substring = true))
            .assertIsDisplayed()
    }

    /** Each row says the script's file and how it runs; a tap opens its description. */
    @Test
    fun aRowSaysHowTheScriptRunsAndOpensItsDescription() {
        show()
        compose.onNodeWithText("countdown.lua · command").assertExists()
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("charge-reminder.lua · on charger, unplug"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("hn.lua · every 1h"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Countdowns"))
        compose.onNodeWithText(entry.description).assertDoesNotExist()
        compose.onNodeWithText("Countdowns").performClick()
        compose.onNodeWithText(entry.description).assertExists()
        compose.onNodeWithText("Countdowns").performClick()
        compose.onNodeWithText(entry.description).assertDoesNotExist()
    }

    /** The search field. */
    private fun field() = compose.onNode(hasSetTextAction())

    /** The chip of [tag]. The chips are in one row that scrolls sideways: scroll to one before a tap on it. */
    private fun chip(tag: LibraryTag) = compose.onNodeWithTag("tag:${tag.id}")

    /** How many library scripts have [tag]: the number on its chip while nothing is typed. */
    private fun count(tag: LibraryTag) = library.entries.count { tag in it.tags }

    /*
     * The search and the chips are checked on the first rows: Air quality, Charge reminder and
     * Countdowns are in view when the page opens. So a row that is left out is seen to go. A row
     * further down the list would not be there to begin with.
     */

    /**
     * The search keeps the scripts that have every word typed, in the name, the title or the
     * description. The chips then count what it found. The × button shows every script again.
     */
    @Test
    fun theSearchKeepsTheScriptsThatHaveEveryWord() {
        show()
        compose.onNodeWithText("Air quality").assertIsDisplayed()
        chip(LibraryTag.WIDGET).assert(hasText("Widget ${count(LibraryTag.WIDGET)}"))
        // The name and a word of the description, in any case.
        field().performTextInput("COUNTDOWN birthday")
        compose.onNodeWithText("Countdowns").assertIsDisplayed()
        compose.onNodeWithText("Air quality").assertDoesNotExist()
        compose.onNodeWithText("Charge reminder").assertDoesNotExist()
        chip(LibraryTag.WIDGET).assert(hasText("Widget 1"))
        chip(LibraryTag.TEMPLATE).assert(hasText("Template 0"))

        field().performTextReplacement("zzqx")
        compose.onNodeWithText("No script matches “zzqx”").assertIsDisplayed()
        compose.onNodeWithText("Countdowns").assertDoesNotExist()
        // No chip is on, so the × of the field is enough: no Show all.
        compose.onNodeWithText("Show all").assertDoesNotExist()
        compose.onNodeWithText("Get more scripts").assertExists()
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.onNodeWithText("Air quality").assertIsDisplayed()
        compose.onNodeWithText("Countdowns").assertIsDisplayed()
        chip(LibraryTag.WIDGET).assert(hasText("Widget ${count(LibraryTag.WIDGET)}"))
    }

    /** A chip keeps the scripts with its tag. A second chip adds its scripts. A tap on a chip that is on turns it off. */
    @Test
    fun aChipKeepsItsScriptsAndASecondOneAddsIts() {
        show()
        compose.onNodeWithText("Air quality").assertIsDisplayed()
        chip(LibraryTag.EVENT).assert(hasText("Event ${count(LibraryTag.EVENT)}"))
            .assertIsNotSelected()
        chip(LibraryTag.EVENT).performScrollTo().performClick()
        chip(LibraryTag.EVENT).assertIsSelected()
        // Charge reminder runs when the charger is plugged in. Air quality and Countdowns run on no event.
        compose.onNodeWithText("Charge reminder").assertIsDisplayed()
        compose.onNodeWithText("Unlocks").assertIsDisplayed()
        compose.onNodeWithText("Air quality").assertDoesNotExist()
        compose.onNodeWithText("Countdowns").assertDoesNotExist()
        // The number on a chip is its own: the other chips don't change it.
        chip(LibraryTag.WIDGET).assert(hasText("Widget ${count(LibraryTag.WIDGET)}"))

        // Widget too: the widgets come back, and the scripts that run on an event stay.
        chip(LibraryTag.WIDGET).performScrollTo().performClick()
        compose.onNodeWithText("Air quality").assertIsDisplayed()
        compose.onNodeWithText("Charge reminder").assertIsDisplayed()
        compose.onNodeWithText("Countdowns").assertIsDisplayed()

        chip(LibraryTag.WIDGET).performClick()
        chip(LibraryTag.WIDGET).assertIsNotSelected()
        compose.onNodeWithText("Air quality").assertDoesNotExist()
        chip(LibraryTag.EVENT).performScrollTo().performClick()
        compose.onNodeWithText("Air quality").assertIsDisplayed()
        compose.onNodeWithText("Countdowns").assertIsDisplayed()
    }

    /**
     * The search and the chips work together: the scripts the search finds among those the chips
     * keep. With none left, the page names the chips that are on, and Show all empties both.
     */
    @Test
    fun theSearchAndTheChipsWorkTogether() {
        show()
        chip(LibraryTag.TEMPLATE).performScrollTo().performClick()
        compose.onNodeWithText("Air quality").assertDoesNotExist()
        compose.onNodeWithText("Template: API key").assertIsDisplayed()
        field().performTextInput("event")
        compose.onNodeWithText("Template: event").assertIsDisplayed()
        compose.onNodeWithText("Template: event (JavaScript)").assertIsDisplayed()
        compose.onNodeWithText("Template: API key").assertDoesNotExist()

        // The search finds Countdowns, but it is no template.
        field().performTextReplacement("countdown")
        compose.onNodeWithText("No Template script matches “countdown”").assertIsDisplayed()
        compose.onNodeWithText("Countdowns").assertDoesNotExist()
        compose.onNodeWithText("Show all").performClick()
        compose.onNodeWithText("Air quality").assertIsDisplayed()
        compose.onNodeWithText("Countdowns").assertIsDisplayed()
        chip(LibraryTag.TEMPLATE).assertIsNotSelected()
        // The field is empty again: it shows what to type.
        compose.onNodeWithText("Search the library").assertExists()
    }

    /** Back empties the search first. The next Back closes the page. */
    @Test
    fun backEmptiesTheSearchFirst() {
        vm = vms.terminal(app)
        compose.runOnUiThread { vm.openScreen(Screen.SCRIPTS); vm.openScreen(Screen.SCRIPT_LIBRARY) }
        compose.setContent { ScriptLibraryScreen(vm) }
        compose.waitForText("Countdowns", ms = 15_000)
        field().performTextInput("countdown")
        compose.onNodeWithText("Air quality").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Air quality").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Screen.SCRIPT_LIBRARY, vm.screen.value) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(Screen.SCRIPTS, vm.screen.value) }
    }

    /** The search and the chips that are on come back when the page is rebuilt, as after a rotation. */
    @Test
    fun theSearchAndTheChipsSurviveARotation() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        chip(LibraryTag.WIDGET).performScrollTo().performClick()
        field().performTextInput("days")
        compose.onNodeWithText("Countdowns").assertIsDisplayed()
        compose.onNodeWithText("Air quality").assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForText("Countdowns", ms = 15_000)
        compose.onNodeWithText("Air quality").assertDoesNotExist()
        chip(LibraryTag.WIDGET).assertIsSelected()
        field().assert(hasText("days"))
    }

    /** Install adds the script with the timers it suggests, and the row then says it is installed. */
    @Test
    fun installAddsTheScriptWithItsTimers() {
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

    /**
     * The code button opens the script to read before installing it: how it runs, what it uses, and
     * its code as the library has it, read-only. Install there installs it.
     */
    @Test
    fun theCodeButtonOpensTheScriptToReadBeforeInstalling() {
        show()
        compose.onNodeWithTag("read countdown").performClick()
        compose.waitForText("Runs: command · daily 00:05")
        compose.onNodeWithText("Uses: nothing private").assertExists()
        compose.onNodeWithText("Internet: no").assertExists()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("read-only-code").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(library.code(entry)) and hasAnyAncestor(hasTestTag("read-only-code")))
            .assertExists()
        // Nothing to type in: the code can only be read.
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("read-only-code")))
            .assertCountEquals(0)
        assertNull(scripts.byName("countdown"))
        compose.onNode(hasText("Install") and hasAnyAncestor(hasTestTag("library-sheet-install")))
            .performClick()
        compose.waitUntil(5_000) { scripts.byName("countdown") != null }
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                hasText("Installed") and hasAnyAncestor(
                    hasTestTag("library-sheet-install")
                )
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(library.code(entry), scripts.byName("countdown")!!.code)
    }

    /** A copy from an older Kolahal, never edited, comes first and offers Update, which brings this version's at once. */
    @Test
    fun anOlderCopyUpdates() {
        val old = "-- @command\n-- @title Countdowns\nprint('older countdown')\n"
        scripts.create("countdown", entry.lang, old)
        app.getSharedPreferences("script_library", Context.MODE_PRIVATE).edit()
            .putString("installed:countdown", old).commit()
        assertEquals(ScriptLibrary.State.UPDATE_AVAILABLE, library.state(entry, scripts.all()))
        show()
        // The row shows before the scripts are read, as not installed: Update comes once they are.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Update") and hasAnyAncestor(hasText("Countdowns")))
                .fetchSemanticsNodes().isNotEmpty()
        }
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
    @Test
    fun anEditedCopyComesFirstWithCompareAndReset() {
        val s = library.install(entry)
        val mine = library.code(entry) + "\n-- mine"
        scripts.saveCode(s.id, mine, byUser = true)
        show()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Countdowns") and hasText("Edited. Nothing newer."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // The group is above the library's first script, which isn't in it.
        val header =
            compose.onNodeWithText("Your copy differs", substring = true).getUnclippedBoundsInRoot()
        val row = compose.onNode(hasText("Countdowns") and hasText("Edited. Nothing newer."))
            .getUnclippedBoundsInRoot()
        assertTrue("the group's header above its row: $header, $row", header.bottom <= row.top)
        compose.onNodeWithText("All scripts").assertExists()
        inCountdownRow("Install").assertDoesNotExist()

        compose.onNode(hasContentDescription("Compare") and hasAnyAncestor(hasText("Countdowns")))
            .performClick()
        compose.waitForNode(hasTestTag("compare"))
        compose.onNodeWithText("-- mine").assertExists()
        compose.onNode(hasContentDescription("Close") and hasAnyAncestor(hasTestTag("compare")))
            .performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("compare")).fetchSemanticsNodes().isEmpty()
        }

        inCountdownRow("Reset").performClick()
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        assertEquals(mine, scripts.byName("countdown")!!.code)
        compose.onNode(hasText("Reset") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { scripts.byName("countdown")?.code == library.code(entry) }
        compose.waitForText("countdown.lua is back to the library version")
        countdownRowSays("Installed")
    }

    /** Back returns to the page the library was opened from. */
    @Test
    fun backReturnsToScripts() {
        vm = vms.terminal(app)
        compose.runOnUiThread { vm.openScreen(Screen.SCRIPTS); vm.openScreen(Screen.SCRIPT_LIBRARY) }
        compose.setContent { ScriptLibraryScreen(vm) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Screen.SCRIPTS, vm.screen.value) }
    }
}
