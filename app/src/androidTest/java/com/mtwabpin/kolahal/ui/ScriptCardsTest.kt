package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The script manager's cards: their pills switch what a script may do; Reset brings the library version back; a chip
 * finds the library copies that differ, and Compare shows how.
 */
@RunWith(AndroidJUnit4::class)
class ScriptCardsTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val library = ScriptLibrary(app)

    @After fun tearDown() {
        listOf("scpills", "countdown").forEach { n -> scripts.byName(n)?.let { scripts.delete(it.id) } }
        library.forget("countdown")
    }

    private fun show(): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent { ScriptsScreen(vm, onImportFiles = {}, onExportScript = {}) }
        compose.waitUntil(5_000) { vm.scriptList.value.isNotEmpty() }
        // Every card's details.
        compose.onAllNodesWithContentDescription("Show details")[0].performClick()
        return vm
    }

    private fun waitFor(what: String, check: () -> Boolean) {
        compose.waitUntil(5_000) { check() }
        assertTrue(what, check())
    }

    @Test fun aPillSwitchesWhatTheScriptMayDo() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @uses clipboard\nif false then notify(\"a\", http.get(\"https://example.com\").body) end")
        scripts.setAccess(s.id, "clipboard", true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("internet"))
        compose.onNodeWithText("internet").performClick()
        waitFor("internet off") { !scripts.byId(s.id)!!.grants.allows("internet") }
        compose.onNodeWithText("clipboard").performClick()
        waitFor("clipboard off") { !scripts.byId(s.id)!!.grants.allows("clipboard") }
        compose.onNodeWithText("internet").performClick()
        waitFor("internet on again") { scripts.byId(s.id)!!.grants.allows("internet") }
        compose.onNodeWithText("notifies").performClick()
        waitFor("notifies on") { scripts.byId(s.id)!!.notify }
    }

    /**
     * Only what its code uses: no internet, header line or notifications for one that doesn't go online, set a line or
     * notify. The events it runs on have a line of their own, under its access.
     */
    @Test fun aCardShowsOnlyWhatTheScriptUses() {
        val s = scripts.create("scpills", ScriptLang.LUA, "-- @uses clipboard\n-- @on unlock\nclipboard.set(\"x\")")
        scripts.setAccess(s.id, "clipboard", true)
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("unlock"))
        compose.onNodeWithText("clipboard").assertExists()
        listOf("internet", "header line", "notifies").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        val access = compose.onNodeWithText("clipboard").getUnclippedBoundsInRoot()
        val events = compose.onNodeWithText("Events", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val unlock = compose.onNodeWithText("unlock").getUnclippedBoundsInRoot()
        assertTrue("the Events label under the access pills: $access, $events", events.top >= access.bottom)
        assertTrue("the event's pill under its label: $events, $unlock", unlock.top >= events.bottom)
    }

    /** Differs from the library keeps a library copy you edited, not a script of your own. */
    @Test fun aChipFindsTheLibraryCopiesThatDiffer() {
        val entry = library.entries.single { it.name == "countdown" }
        val s = library.install(entry)
        scripts.saveCode(s.id, library.code(entry) + "\n-- mine", byUser = true)
        scripts.create("scpills", ScriptLang.LUA, "log(\"hi\")")
        show()
        compose.onNodeWithText("Differs from the library", substring = true).performScrollTo().performClick()
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
        compose.onNodeWithText("Reset").performClick()
        waitFor("the library version") { scripts.byId(s.id)!!.code == library.code(entry) }
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
        compose.onNodeWithText("Reset").performClick()
        waitFor("the library version") { scripts.byId(s.id)!!.code == library.code(entry) }
    }
}
