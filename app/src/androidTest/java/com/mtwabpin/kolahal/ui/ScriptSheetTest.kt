package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptOptions
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.SecretStore
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A script's sheet in the Scripts tab: what it says about the script, its switches, timers, secrets,
 * options, rename and delete, and the dialogs those open; and Add script → New script.
 */
@RunWith(AndroidJUnit4::class)
class ScriptSheetTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val library = ScriptLibrary(app)
    private val logs = LogRepository.get(app)
    private var logStart = 0L
    private var started = false
    private var exported: Script? = null
    private lateinit var vm: TerminalViewModel

    @Before fun setUp() {
        // The test's own names: a script of the phone's own by one of them is left alone.
        assumeTrue("a script by a test's name exists already", NAMES.none { scripts.byName(it) != null })
        logStart = logs.maxId()
        started = true
    }

    @After fun tearDown() {
        if (!started) return
        NAMES.forEach { n -> scripts.byName(n)?.let { ScriptScheduler.cancel(app, it.id); scripts.delete(it.id) } }
        library.forget("countdown")
        logs.recent(200).filter { it.id > logStart && (it.source in NAMES || NAMES.any { n -> n in it.text }) }.forEach { logs.delete(it.id) }
    }

    private fun create(code: String, name: String = "sstest", enabled: Boolean = true) = scripts.create(name, ScriptLang.LUA, code, enabled)

    /** The Scripts tab, as it opens. */
    private fun showScripts() {
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent { ScriptsScreen(vm, onImportFiles = {}, onExportScript = { exported = it }) }
    }

    /** The Scripts tab with [name]'s sheet open. */
    private fun openSheet(name: String = "sstest") {
        showScripts()
        val card = hasTestTag("script-card") and hasText("$name.lua")
        compose.waitUntil(5_000) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitForText("Runs every")
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitFor(what: String, ms: Long = 5_000, check: () -> Boolean) {
        compose.waitUntil(ms) { check() }
        assertTrue(what, check())
    }

    /** The dialog titled [title]: the sheet under it is a dialog too. */
    private fun dialog(title: String) = isDialog() and hasAnyDescendant(hasText(title))

    /** [text] in a dialog, or in the one titled [title] where the sheet has the same text. */
    private fun inDialog(text: String, title: String? = null) =
        compose.onNode(hasText(text) and hasAnyAncestor(if (title == null) isDialog() else dialog(title)))

    private fun script(name: String = "sstest") = scripts.byName(name)!!

    /** The sheet names the script, its language, version, author, kind and description, and that it never ran. */
    @Test fun theSheetSaysWhatTheScriptIs() {
        create("-- @command\n-- @title Sheet test\n-- @desc Says hello\n-- @version 1.2\n-- @author Tester\nlocal x = 1")
        openSheet()
        compose.onNodeWithText("Lua · v1.2 · by Tester · command · Says hello").assertExists()
        compose.onNodeWithText("Never run").assertExists()
        compose.onNodeWithText("Runs on its timers and events, and from the prompt").assertExists()
    }

    /** Enabled switches the script off, and on again. */
    @Test fun enabledSwitchesItOffAndOn() {
        create("local x = 1")
        openSheet()
        compose.onNodeWithText("Runs on its timers and events").assertExists()
        compose.onNodeWithText("Enabled").performClick()
        waitFor("off") { !script().enabled }
        compose.onNodeWithText("Enabled").performClick()
        waitFor("on again") { script().enabled }
    }

    /** Switching on a script that asks for something asks first, each item with a switch: what you switch off stays off. */
    @Test fun switchingOnAsksForWhatItUses() {
        create("-- @uses clipboard\nclipboard.get()", enabled = false)
        openSheet()
        compose.onNodeWithText("Enabled").performClick()
        compose.onNodeWithText("Turn on sstest.lua?").assertExists()
        inDialog("It requests:").assertExists()
        inDialog("Cancel").performClick()
        compose.onNodeWithText("Turn on sstest.lua?").assertDoesNotExist()
        assertFalse(script().enabled)

        compose.onNodeWithText("Enabled").performClick()
        inDialog("Clipboard", title = "Turn on sstest.lua?").performClick()
        inDialog("Turn on").performClick()
        waitFor("on") { script().enabled }
        assertEquals(false, script().access["clipboard"])
        assertFalse(script().grants.allows("clipboard"))
    }

    /** The Access and Events switches change what the script may do and what it runs on. */
    @Test fun theAccessSwitchesChangeWhatItMayDo() {
        create("-- @uses clipboard\n-- @on unlock\nif false then notify(\"a\", http.get(\"https://example.com\").body) end")
        openSheet()
        compose.onNodeWithText("Internet").performScrollTo().performClick()
        waitFor("internet off") { !script().grants.allows("internet") }
        compose.onNodeWithText("Clipboard").performScrollTo().performClick()
        waitFor("clipboard off") { !script().grants.allows("clipboard") }
        compose.onNodeWithText("Post notifications").performScrollTo().performClick()
        waitFor("notifications on") { script().notify }
        compose.onNodeWithText("Runs on unlock").performScrollTo().performClick()
        waitFor("unlock off") { !script().grants.allows("unlock") }
    }

    /** Runs every sets the script's timer. */
    @Test fun runsEverySetsTheTimer() {
        create("local x = 1")
        openSheet()
        compose.onNodeWithText("Runs every").performScrollTo().performClick()
        compose.onNodeWithText("1 hour").performClick()
        waitFor("every hour") { script().intervalMin == 60 }
    }

    /** Daily at takes times as typed, sorted, and says what is wrong with one it can't read. */
    @Test fun dailyAtTakesTimesAndRefusesBadOnes() {
        create("local x = 1")
        openSheet()
        val box = compose.onNode(hasSetTextAction())
        box.performScrollTo().performTextReplacement("25:00")
        compose.onNodeWithContentDescription("Set times").performClick()
        compose.onNodeWithText("Use HH:mm, separated by commas").assertExists()
        assertEquals(emptyList<String>(), script().atTimes)

        box.performTextReplacement("18:30, 08:00")
        compose.onNodeWithText("Use HH:mm, separated by commas").assertDoesNotExist()
        box.performImeAction()
        waitFor("two daily times") { script().atTimes == listOf("08:00", "18:30") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction() and hasText("08:00, 18:30")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Set times").assertDoesNotExist()
    }

    /** The clock beside Daily at adds the time picked to the script's times. */
    @Test fun theClockAddsATime() {
        create("local x = 1")
        openSheet()
        compose.onNodeWithContentDescription("Add a time").performScrollTo().performClick()
        inDialog("Add").performClick()
        waitFor("08:00 daily") { script().atTimes == listOf("08:00") }
        compose.onNode(hasSetTextAction()).assert(hasText("08:00"))
    }

    /** Delete asks first: Cancel keeps the script, Delete removes it and closes the sheet. */
    @Test fun deleteAsksFirst() {
        create("local x = 1")
        openSheet()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete sstest.lua?").assertExists()
        compose.onNodeWithText("Its timers, stored data and status line are removed too. Export it first to keep a copy.").assertExists()
        inDialog("Cancel").performClick()
        compose.onNodeWithText("Delete sstest.lua?").assertDoesNotExist()
        assertNotNull(scripts.byName("sstest"))

        compose.onNodeWithText("Delete").performScrollTo().performClick()
        inDialog("Delete", title = "Delete sstest.lua?").performClick()
        waitFor("deleted") { scripts.byName("sstest") == null }
        waitForText("Deleted sstest")
        compose.onNodeWithText("Runs every").assertDoesNotExist()
    }

    /** Rename checks the new name as it is typed, then renames the script. */
    @Test fun renameChecksTheNameThenRenames() {
        create("local x = 1")
        create("local y = 2", name = "sstother")
        openSheet()
        compose.onNodeWithContentDescription("Rename").performClick()
        compose.onNodeWithText("Rename sstest.lua").assertExists()
        val box = compose.onNode(hasSetTextAction() and hasAnyAncestor(dialog("Rename sstest.lua")))
        box.performTextReplacement("echo")
        inDialog("'echo' is a built-in command").assertExists()
        inDialog("Rename").assertIsNotEnabled()
        box.performTextReplacement("sstother")
        inDialog("There is already a script named sstother").assertExists()
        inDialog("Rename").assertIsNotEnabled()
        box.performTextReplacement("sstnew")
        inDialog("Rename").assertIsEnabled().performClick()
        waitFor("renamed") { scripts.byName("sstnew") != null }
        assertNull(scripts.byName("sstest"))
        waitForText("Renamed sstest.lua to sstnew.lua")
    }

    /** A secret it declares is set in a masked box, then shows as set; Clear forgets it. */
    @Test fun aSecretIsSetAndCleared() {
        create("-- @secret SST_KEY example.com Your key for the test\nlocal k = secrets.get(\"SST_KEY\")")
        val secret = ScriptMeta.secrets(script().code).single()
        openSheet()
        val row = hasText("SST_KEY") and hasText("Your key for the test · sent only to example.com")
        compose.onNode(row and hasText("Not set")).performScrollTo().performClick()
        compose.onNodeWithText("SST_KEY for sstest").assertExists()
        inDialog("Save").assertIsNotEnabled()
        compose.onNodeWithTag("secret-value").performTextInput("abc")
        inDialog("Save").assertIsNotEnabled()
        compose.onNodeWithTag("secret-value").performTextInput("d1234")
        compose.onNodeWithContentDescription("Show it").performClick()
        compose.onNodeWithContentDescription("Hide it").assertExists()
        inDialog("Save").assertIsEnabled().performClick()
        waitFor("set") { SecretStore(app).isSet("sstest", secret) }
        compose.waitUntil(5_000) { compose.onAllNodes(row and hasText("Set")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(row).performClick()
        inDialog("Set · type a new one to replace it").assertExists()
        inDialog("Clear").performClick()
        waitFor("cleared") { !SecretStore(app).isSet("sstest", secret) }
        compose.waitUntil(5_000) { compose.onAllNodes(row and hasText("Not set")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** An option shows its value; a new one is kept, Reset puts the default back, and a choice is picked from its chips. */
    @Test fun anOptionIsChangedAndReset() {
        create("-- @option count 5 How many to show\n-- @option units metric|imperial Units to use\nlocal n = options.count")
        val declared = ScriptMeta.options(script().code)
        fun value(name: String) = ScriptOptions(app).values("sstest", declared)[name]
        openSheet()
        val count = hasText("count") and hasText("How many to show")
        compose.onNode(count and hasText("5")).performScrollTo().performClick()
        compose.onNodeWithText("count for sstest").assertExists()
        inDialog("Default: 5").assertExists()
        inDialog("Reset").assertDoesNotExist()
        compose.onNodeWithTag("option-value").performTextReplacement("9")
        inDialog("Save").performClick()
        waitFor("count 9") { value("count") == "9" }
        compose.waitUntil(5_000) { compose.onAllNodes(count and hasText("9")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(count).performClick()
        inDialog("Reset").performClick()
        waitFor("count back to 5") { value("count") == "5" && "count" !in ScriptOptions(app).changed("sstest", declared) }

        val units = hasText("units") and hasText("Units to use")
        compose.onNode(units and hasText("metric")).performScrollTo().performClick()
        inDialog("imperial").performClick()
        inDialog("Save").performClick()
        waitFor("imperial") { value("units") == "imperial" }
        compose.waitUntil(5_000) { compose.onAllNodes(units and hasText("imperial")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** An edited library copy offers the library version back, and asks first, as your edits go. */
    @Test fun anEditedLibraryCopyResetsAfterAsking() {
        val entry = library.entries.single { it.name == "countdown" }
        val s = library.install(entry)
        scripts.saveCode(s.id, library.code(entry) + "\n-- mine", byUser = true)
        openSheet("countdown")
        compose.onNodeWithText("Compare").performScrollTo().assertExists()
        compose.onNodeWithText("Reset to library version").performScrollTo().performClick()
        compose.onNodeWithText("Reset countdown.lua?").assertExists()
        assertTrue(script("countdown").code.endsWith("-- mine"))
        inDialog("Reset").performClick()
        waitFor("the library version") { script("countdown").code == library.code(entry) }
        compose.onNodeWithText("Reset to library version").assertDoesNotExist()
    }

    /** Export hands the script to the file picker, and Edit opens it in the editor. */
    @Test fun exportAndEditHandTheScriptOn() {
        val s = create("local x = 1")
        openSheet()
        compose.onNodeWithText("Export").performClick()
        compose.runOnIdle { assertEquals(s.id, exported?.id) }
        compose.onNodeWithText("Edit").performClick()
        compose.runOnIdle { assertEquals(s.id, vm.editing.value?.id) }
        compose.onNodeWithText("Runs every").assertDoesNotExist()
    }

    /** Run starts the script at once and says so; the sheet then says when it last ran. */
    @Test fun runStartsItAndTheSheetSaysWhen() {
        create("local x = 1")
        openSheet()
        compose.onNodeWithText("Run").performClick()
        waitForText("Running sstest…")
        waitFor("a run", ms = 30_000) { script().lastRun != null }
        waitForText("Last run just now · ok", substring = true)
    }

    /** New script checks the name as it is typed, and creates the script in the language and kind chosen. */
    @Test fun newScriptChecksTheNameThenCreates() {
        create("local y = 2", name = "sstother")
        showScripts()
        // The button's label is a node of its own, unmerged.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add script", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add script", useUnmergedTree = true).performClick()
        waitForText("New script")
        compose.onNodeWithText("New script").performClick()
        val box = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        box.performTextInput("echo")
        inDialog("'echo' is a built-in command").assertExists()
        inDialog("Create").assertIsNotEnabled()
        box.performTextReplacement("sstother")
        inDialog("You already have a script named sstother").assertExists()
        inDialog("Create").assertIsNotEnabled()
        box.performTextReplacement("sstnew")
        compose.onNodeWithTag("choice JavaScript").performClick()
        compose.onAllNodes(isToggleable() and hasAnyAncestor(isDialog()))
            .filterToOne(!hasTestTag("choice Lua") and !hasTestTag("choice JavaScript")).performClick()
        inDialog("Create").assertIsEnabled().performClick()
        waitFor("created") { scripts.byName("sstnew") != null }
        val made = script("sstnew")
        assertEquals(ScriptLang.JS, made.lang)
        assertTrue(made.code, ScriptMeta.isCommand(made.code))
        compose.runOnIdle { assertEquals(made.id, vm.editing.value?.id) }
    }

    private companion object {
        /** Every script a test may leave: removed after each. */
        val NAMES = listOf("sstest", "sstnew", "sstother", "countdown")
    }
}
