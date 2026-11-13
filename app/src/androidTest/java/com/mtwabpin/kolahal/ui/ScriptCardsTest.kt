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
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
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
        listOf("scpills", "scplain", "countdown").forEach { n -> scripts.byName(n)?.let { scripts.delete(it.id) } }
        library.forget("countdown")
    }

    /** [text] in a dialog: the card's pill may have the same text. */
    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    /** The pill on [name]'s card. */
    private fun pill(name: String) = hasTestTag("script-pill") and hasAnyAncestor(hasTestTag("script-card") and hasText(name))

    /** What the page asked the activity for: Export sends the script to save. */
    private val sent = SentEvents()

    /** The Scripts tab; with [details], every card's details are open. */
    private fun show(details: Boolean = true): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent {
            sent.Collect(vm)
            ScriptsScreen(vm)
        }
        compose.waitUntil(5_000) { vm.scriptList.value.isNotEmpty() }
        if (details) compose.onAllNodesWithContentDescription("Show details")[0].performClick()
        return vm
    }

    @Test fun aPillSwitchesWhatTheScriptMayDo() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @uses clipboard\nif false then notify(\"a\", http.get(\"https://example.com\").body) end")
        scripts.setAccess(s.id, "clipboard", true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Internet"))
        compose.onNodeWithText("Internet").performClick()
        compose.waitFor("internet off") { !scripts.byId(s.id)!!.grants.allows("internet") }
        compose.onNodeWithText("Clipboard").performClick()
        compose.waitFor("clipboard off") { !scripts.byId(s.id)!!.grants.allows("clipboard") }
        compose.onNodeWithText("Internet").performClick()
        compose.waitFor("internet on again") { scripts.byId(s.id)!!.grants.allows("internet") }
        compose.onNodeWithText("Notifies").performClick()
        compose.waitFor("notifies on") { scripts.byId(s.id)!!.notify }
    }

    /**
     * Only what its code uses: no internet, pinning or notifications for one that doesn't go online, set a line or
     * notify. The events it runs on are on the line about when it runs, above its access.
     */
    @Test fun aCardShowsOnlyWhatTheScriptUses() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @uses clipboard\n-- @on unlock\nclipboard.set(\"x\")")
        scripts.setAccess(s.id, "clipboard", true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Unlock"))
        compose.onNodeWithText("Clipboard").assertExists()
        listOf("Internet", "Pinned", "Notifies").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        val access = compose.onNodeWithText("Clipboard").getUnclippedBoundsInRoot()
        val unlock = compose.onNodeWithText("Unlock").getUnclippedBoundsInRoot()
        assertTrue("the event's pill above the access pills: $unlock, $access", unlock.bottom <= access.top)
        // No labels: the lines have icons.
        listOf("Access", "Events").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    /**
     * The page keeps what it read of each script while it's away (the editor takes its place): a
     * script changed meanwhile shows what its code says now when the page is back.
     */
    @Test fun aScriptChangedWhileThePageWasAwayShowsItsNewCode() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @on unlock\nreturn 1")
        val vm = vms.terminal(app)
        var shown by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { if (shown) ScriptsScreen(vm) }
        fun details(event: String) {
            compose.waitUntil(5_000) { vm.scriptList.value.any { it.name == "scpills" && event.lowercase() in it.code } }
            compose.onAllNodesWithContentDescription("Show details")[0].performClick()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(event))
        }
        details("Unlock")
        compose.runOnIdle { shown = false }
        scripts.saveCode(s.id, "-- @on charger\nreturn 1", byUser = true)
        // A line logged: home reads the scripts again, as after a save.
        val line = com.mtwabpin.kolahal.data.LogRepository.get(app).add(com.mtwabpin.kolahal.data.LogKind.INFO, "scpills saved")
        try {
            compose.runOnIdle { shown = true }
            details("Charger")
            compose.onNodeWithText("Charger").assertExists()
            compose.onNodeWithText("Unlock").assertDoesNotExist()
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
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Reset to library"))
        compose.onNodeWithText("Edited by you").assertExists()
        compose.onNodeWithText("Reset to library").performClick()
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
        compose.waitFor("the copy handed to the export") { sent.all.any { it is UiEvent.ExportScript && it.script.id == s.id } }
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

    /** [scpills]'s card. */
    private val scCard = hasTestTag("script-card") and hasText("scpills.lua")

    /** The texts of [scpills]'s card, in order, without those of its buttons and pills. */
    private fun cardTexts() = compose.onNode(scCard).fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }

    /** The text of [scpills]'s summary line: the one after its file. */
    private fun summary() = cardTexts().let { it[it.indexOf(" · yours") + 1] }

    /** Opens [scpills]'s details from the closed list, and scrolls to [text]. */
    private fun openDetails(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(scCard)
        compose.onNode(hasContentDescription("Show details") and hasAnyAncestor(scCard)).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    /**
     * Open, a card says a failed run and a missing key once each, in a line of its own: the summary leaves
     * them out and the pill is hidden. The key's line has Add key, and it works.
     */
    @Test fun anOpenCardSaysEachProblemOnceWithItsButton() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @secret SCP_KEY example.com A key for the test\nlocal k = secrets.get(\"SCP_KEY\")")
        scripts.recordRun(s.id, "error: HTTP 401: invalid key")
        try {
            show(details = false)
            compose.waitForNode(scCard and hasText(" · yours"))
            // Closed: the summary has both, with the pill.
            assertTrue(summary(), summary().startsWith("Manual · Failed "))
            assertTrue(summary(), summary().endsWith(" · Needs SCP_KEY"))
            compose.onNode(pill("scpills.lua") and hasText("Add key")).assertExists()

            openDetails("SCP_KEY is not set")
            assertEquals("Manual", summary())
            compose.onNode(pill("scpills.lua")).assertDoesNotExist()
            // "Failed" once in the card: only in its line, with the error.
            val failed = cardTexts().filter { "Failed" in it }
            assertEquals(cardTexts().toString(), 1, failed.size)
            assertTrue(failed.single(), Regex("Failed .+ · HTTP 401: invalid key").matches(failed.single()))
            assertTrue(cardTexts().toString(), "SCP_KEY is not set" in cardTexts())
            assertTrue("the line about runs", "Manual" in cardTexts().drop(cardTexts().indexOf(failed.single())))

            compose.onNode(hasText("Add key") and hasClickAction() and hasAnyAncestor(scCard)).performClick()
            compose.onNodeWithText("SCP_KEY for scpills").assertExists()
            compose.onNodeWithTag("secret-value").performTextInput("abcd1234")
            inDialog("Save").performClick()
            compose.waitFor("the key set") { com.mtwabpin.kolahal.script.SecretStore(app).get("scpills", "SCP_KEY")?.value == "abcd1234" }
            // Its line goes. A key that is set doesn't show.
            compose.waitFor("its line gone") { cardTexts().none { "SCP_KEY" in it } }
            compose.onNode(hasText("Add key") and hasAnyAncestor(scCard)).assertDoesNotExist()
        } finally {
            com.mtwabpin.kolahal.script.SecretStore(app).clear("scpills")
        }
    }

    /** Each part of the summary starts with a capital, and so does each part of the line about when it runs. */
    @Test fun theSummaryPartsStartWithACapital() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @command\nlog(\"hi\")")
        scripts.setInterval(s.id, 60)
        show(details = false)
        compose.waitForNode(scCard)
        assertEquals("Command · Every 1h · Never run", summary())
        openDetails("Next ")
        assertTrue(cardTexts().toString(), cardTexts().any { Regex("Every 1h · Next about .+ · Never run").matches(it) })
    }

    /** The usage line shows for a command, and not for a script that isn't one. */
    @Test fun onlyACommandHasAUsageLine() {
        scripts.create("scpills", ScriptLang.LUA, "-- @command\n-- @usage scpills [a|b <n>]\nlog(\"hi\")")
        scripts.create("scplain", ScriptLang.LUA, "-- @usage scplain [a|b <n>]\nlog(\"hi\")")
        show(details = false)
        compose.waitForNode(scCard)
        openDetails("scpills [a|")
        // It breaks only after "|": one part per choice.
        assertTrue(cardTexts().toString(), cardTexts().containsAll(listOf("scpills [a|", "b <n>]")))
        val plain = hasTestTag("script-card") and hasText("scplain.lua")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(plain)
        compose.onNode(hasContentDescription("Show details") and hasAnyAncestor(plain)).performClick()
        // Open: the line about when it runs shows, and no usage.
        compose.waitForNode(hasContentDescription("Hide details") and hasAnyAncestor(plain))
        compose.onNode(plain and hasText("[a|", substring = true)).assertDoesNotExist()
    }

    /** An older copy you never edited: its open card says "Newer version in the library" with Update only, and Update works at once. */
    @Test fun anOpenCardUpdatesANewVersion() {
        val entry = library.entries.single { it.name == "countdown" }
        val old = "-- @command\n-- @title Countdowns\nprint('older countdown')\n"
        val s = scripts.create("countdown", entry.lang, old)
        library.record("countdown", old)
        show(details = false)
        val card = hasTestTag("script-card") and hasText("countdown.lua")
        compose.waitForNode(card)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(card)
        compose.onNode(hasContentDescription("Show details") and hasAnyAncestor(card)).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Newer version in the library"))
        compose.onNode(hasText("Updating replaces your edits")).assertDoesNotExist()
        compose.onNode(hasText("Compare") and hasAnyAncestor(card)).assertDoesNotExist()
        compose.onNode(hasText("Update") and hasClickAction() and hasAnyAncestor(card)).performClick()
        compose.waitFor("the library version") { scripts.byId(s.id)!!.code == library.code(entry) }
        compose.waitForText("countdown.lua is updated")
    }
}
