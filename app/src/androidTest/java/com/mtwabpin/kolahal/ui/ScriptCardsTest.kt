package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The script manager's cards: their access pills switch what a script may do; a pill offers Update, Reset or
 * Add key, Reset asks first and offers Undo; the filter sheet finds the library copies you edited, and Compare
 * shows how.
 */
@RunWith(AndroidJUnit4::class)
class ScriptCardsTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    @get:Rule val logLines = com.mtwabpin.kolahal.LogLinesSince()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val library = ScriptLibrary(app)

    @After fun tearDown() {
        listOf("scpills", "countdown").forEach { n -> scripts.byName(n)?.let { scripts.delete(it.id) } }
        library.forget("countdown")
    }

    /** [text] in a dialog: the card's pill may have the same text. */
    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    /** The pill on [name]'s card. */
    private fun pill(name: String) = hasTestTag("script-pill") and hasAnyAncestor(hasTestTag("script-card") and hasText(name))

    /** The script the page handed to its export, if any. */
    private var exported: Script? = null

    /** The Scripts tab; with [details], every card's details are open. */
    private fun show(details: Boolean = true): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent { ScriptsScreen(vm, onImportFiles = {}, onExportScript = { exported = it }) }
        compose.waitUntil(5_000) { vm.scriptList.value.isNotEmpty() }
        if (details) compose.onAllNodesWithContentDescription("Show details")[0].performClick()
        return vm
    }

    @Test fun aPillSwitchesWhatTheScriptMayDo() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @uses clipboard\nif false then notify(\"a\", http.get(\"https://example.com\").body) end")
        scripts.setAccess(s.id, "clipboard", true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("internet"))
        compose.onNodeWithText("internet").performClick()
        compose.waitFor("internet off") { !scripts.byId(s.id)!!.grants.allows("internet") }
        compose.onNodeWithText("clipboard").performClick()
        compose.waitFor("clipboard off") { !scripts.byId(s.id)!!.grants.allows("clipboard") }
        compose.onNodeWithText("internet").performClick()
        compose.waitFor("internet on again") { scripts.byId(s.id)!!.grants.allows("internet") }
        compose.onNodeWithText("notifies").performClick()
        compose.waitFor("notifies on") { scripts.byId(s.id)!!.notify }
    }

    /**
     * Only what its code uses: no internet, pinning or notifications for one that doesn't go online, set a line or
     * notify. The events it runs on have a line of their own, under its access.
     */
    @Test fun aCardShowsOnlyWhatTheScriptUses() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @uses clipboard\n-- @on unlock\nclipboard.set(\"x\")")
        scripts.setAccess(s.id, "clipboard", true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("unlock"))
        compose.onNodeWithText("clipboard").assertExists()
        listOf("internet", "pinned", "notifies").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        val access = compose.onNodeWithText("clipboard").getUnclippedBoundsInRoot()
        val events = compose.onNodeWithText("Events", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val unlock = compose.onNodeWithText("unlock").getUnclippedBoundsInRoot()
        assertTrue("the Events label under the access pills: $access, $events", events.top >= access.bottom)
        assertTrue("the event's pill under its label: $events, $unlock", unlock.top >= events.bottom)
    }

    /**
     * The page keeps what it read of each script while it's away (the editor takes its place): a
     * script changed meanwhile shows what its code says now when the page is back.
     */
    @Test fun aScriptChangedWhileThePageWasAwayShowsItsNewCode() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @on unlock\nreturn 1")
        val vm = vms.terminal(app)
        var shown by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { if (shown) ScriptsScreen(vm, onImportFiles = {}, onExportScript = {}) }
        fun details(event: String) {
            compose.waitUntil(5_000) { vm.scriptList.value.any { it.name == "scpills" && event in it.code } }
            compose.onAllNodesWithContentDescription("Show details")[0].performClick()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(event))
        }
        details("unlock")
        compose.runOnIdle { shown = false }
        scripts.saveCode(s.id, "-- @on charger\nreturn 1", byUser = true)
        // A line logged: home reads the scripts again, as after a save.
        val line = com.mtwabpin.kolahal.data.LogRepository.get(app).add(com.mtwabpin.kolahal.data.LogKind.INFO, "scpills saved")
        try {
            compose.runOnIdle { shown = true }
            details("charger")
            compose.onNodeWithText("charger").assertExists()
            compose.onNodeWithText("unlock").assertDoesNotExist()
        } finally {
            com.mtwabpin.kolahal.data.LogRepository.get(app).delete(line)
        }
    }

    /** The filter sheet's Edited keeps a library copy you edited, not a script of your own. */
    @Test fun theSheetFindsTheLibraryCopiesYouEdited() {
        val entry = library.entries.single { it.name == "countdown" }
        val s = library.install(entry)
        scripts.saveCode(s.id, library.code(entry) + "\n-- mine", byUser = true)
        scripts.create("scpills", ScriptLang.LUA, "log(\"hi\")")
        show()
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithTag("filter:edited").performScrollTo().performClick()
        compose.onNode(hasText("Show ", substring = true) and hasClickAction()).performClick()
        compose.waitForNode(hasTestTag("chosen-filters") and hasAnyDescendant(hasText("Edited")))
        compose.onNodeWithText("countdown.lua").assertExists()
        compose.onNodeWithText("scpills.lua").assertDoesNotExist()
    }

    @Test fun resetBringsTheLibrarysCopyBackAfterAsking() {
        val entry = library.entries.single { it.name == "countdown" }
        val s = library.install(entry)
        scripts.saveCode(s.id, library.code(entry) + "\n-- mine", byUser = true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Reset to library version"))
        compose.onNodeWithText("Reset to library version").performClick()
        // Edits would be lost: it asks first.
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        assertFalse(scripts.byId(s.id)!!.code == library.code(entry))
        inDialog("Reset").performClick()
        compose.waitFor("the library version") { scripts.byId(s.id)!!.code == library.code(entry) }
        assertEquals(ScriptLibrary.State.INSTALLED, library.state(entry, scripts.all()))
    }

    /** Compare shows the line only in your copy, counted, and resets from there after asking. */
    @Test fun compareShowsYourLinesAndResetsFromThere() {
        val entry = library.entries.single { it.name == "countdown" }
        val s = library.install(entry)
        scripts.saveCode(s.id, library.code(entry) + "-- mine\n", byUser = true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Compare"))
        compose.onNodeWithText("Compare").performClick()
        // The counts can come a frame after the lines: wait for them.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("+ 1 line only in yours").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("-- mine").assertExists()
        compose.onNodeWithText("- 0 lines only in the library version").assertExists()
        compose.onNode(hasText("Reset to library version") and hasAnyAncestor(hasTestTag("compare"))).performClick()
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        compose.onNodeWithText("-- mine").assertDoesNotExist()
        inDialog("Reset").performClick()
        compose.waitFor("the library version") { scripts.byId(s.id)!!.code == library.code(entry) }
    }
    /**
     * An edited copy's card says so with "Edited · Reset". Reset asks first: code only, with Compare
     * and Export there, and both leave the question open. After it, Undo puts your edits back.
     */
    @Test fun thePillResetsAfterAskingAndUndoPutsTheEditsBack() {
        val entry = library.entries.single { it.name == "countdown" }
        val s = library.install(entry)
        val mine = library.code(entry) + "\n-- mine"
        scripts.saveCode(s.id, mine, byUser = true)
        show(details = false)
        val pill = pill("countdown.lua")
        compose.waitForNode(pill and hasText("Edited") and hasText("Reset"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("script-card") and hasText("countdown.lua"))
        compose.onNode(pill).performClick()
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        compose.onNodeWithText("Only the code changes. Its timers, options, access and pinning are kept.").assertExists()
        // Compare opens over the question, and closing it comes back to it.
        inDialog("Compare first").performClick()
        compose.waitForNode(hasTestTag("compare"))
        compose.onNode(hasContentDescription("Close") and hasAnyAncestor(hasTestTag("compare"))).performClick()
        compose.waitFor("Compare closed") { compose.onAllNodes(hasTestTag("compare")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        // Export hands the copy on, and the question stays.
        inDialog("Export your copy first").performClick()
        compose.runOnIdle { assertEquals(s.id, exported?.id) }
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        assertEquals(mine, scripts.byId(s.id)!!.code)

        inDialog("Reset").performClick()
        compose.waitFor("the library version") { scripts.byId(s.id)!!.code == library.code(entry) }
        compose.waitForText("countdown.lua is back to the library version")
        compose.waitFor("the pill gone") { compose.onAllNodes(pill).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("your edits back") { scripts.byId(s.id)!!.code == mine }
        assertEquals(ScriptLibrary.State.MODIFIED, library.state(entry, scripts.all()))
        compose.waitForNode(pill and hasText("Reset"))
    }

    /** An older copy you never edited says "New version · Update", and updates at once: Undo is enough, there is nothing of yours to lose. */
    @Test fun aNewVersionUpdatesAtOnce() {
        val entry = library.entries.single { it.name == "countdown" }
        val old = "-- @command\n-- @title Countdowns\nprint('older countdown')\n"
        val s = scripts.create("countdown", entry.lang, old)
        library.record("countdown", old)
        show(details = false)
        val pill = pill("countdown.lua")
        compose.waitForNode(pill and hasText("New version") and hasText("Update"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("script-card") and hasText("countdown.lua"))
        compose.onNode(pill).performClick()
        compose.waitFor("the library version") { scripts.byId(s.id)!!.code == library.code(entry) }
        assertTrue("no question asked", compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty())
        compose.waitForText("countdown.lua is updated")
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("the older copy back") { scripts.byId(s.id)!!.code == old }
        assertEquals(ScriptLibrary.State.UPDATE_AVAILABLE, library.state(entry, scripts.all()))
    }

    /** A script that needs a key says "Add key"; it opens the key's box, and once set the pill goes. Its file line says it is yours. */
    @Test fun addKeySetsTheMissingKey() {
        scripts.create("scpills", ScriptLang.LUA, "-- @secret SCP_KEY example.com A key for the test\nlocal k = secrets.get(\"SCP_KEY\")")
        try {
            show(details = false)
            val card = hasTestTag("script-card") and hasText("scpills.lua")
            compose.waitForNode(card and hasText(" · yours"))
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(card)
            compose.onNode(pill("scpills.lua") and hasText("Add key")).performClick()
            compose.onNodeWithText("SCP_KEY for scpills").assertExists()
            compose.onNodeWithTag("secret-value").performTextInput("abcd1234")
            inDialog("Save").performClick()
            compose.waitFor("the key set") { com.mtwabpin.kolahal.script.SecretStore(app).get("scpills", "SCP_KEY")?.value == "abcd1234" }
            compose.waitFor("the pill gone") { compose.onAllNodes(pill("scpills.lua")).fetchSemanticsNodes().isEmpty() }
        } finally {
            com.mtwabpin.kolahal.script.SecretStore(app).clear("scpills")
        }
    }
}
