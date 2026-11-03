package com.mtwabpin.kolahal.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.ScriptActions
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Scripts → Get more scripts, from the list kept from the last download and code handed to the
 * screen as if downloaded: the list, its search, a script's sheet, and installing, updating and
 * replacing one. Nothing goes online.
 */
@RunWith(AndroidJUnit4::class)
class GetScriptsScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val catalog = ScriptCatalog(app)
    // Where ScriptCatalog keeps the list it downloaded, and when it did.
    private val cacheFile = File(app.filesDir, "script-catalog.json")
    private val prefs = app.getSharedPreferences("script_catalog", Context.MODE_PRIVATE)
    private val hadCache = cacheFile.takeIf { it.exists() }?.readBytes()
    private val hadFetched = prefs.getLong("fetched", -1).takeIf { it >= 0 }

    private val tide = Shared(
        "gstide", ScriptLang.LUA, "Tide times", "The next high and low tide", "ann", "1.0", listOf("sea", "weather"),
        code = "-- @command\n-- @desc The next high and low tide\nlog(\"high tide at 14:02\")\n",
        command = true,
    )
    private val tideNext = tide.copy(version = "1.1", code = tide.code + "log(\"low tide at 20:15\")\n")
    private val joke = Shared(
        "gsjoke", ScriptLang.JS, "Joke of the day", "A joke each morning", "bo", "2.1", listOf("fun"),
        code = "// @every 1d\nlog('knock knock')\n", every = 1440, at = listOf("07:00"), on = listOf("unlock"),
    )
    private val clip = Shared(
        "gsclip", ScriptLang.LUA, "Clipboard cleaner", "Trims what you copy", "cy", "0.3", listOf("tools"),
        code = "-- @uses clipboard\nlocal s = clipboard.get()\n", uses = listOf("clipboard"),
    )
    private val newer = Shared("gsnewer", ScriptLang.LUA, "From the future", "Needs a newer Kolahal", "di", "1.0", emptyList(), code = "log(1)\n", requires = "99.0")
    private val taken = Shared("help", ScriptLang.LUA, "Help me", "Its name is a command's", "ed", "1.0", emptyList(), code = "log(2)\n")

    @After fun tearDown() {
        listOf(tide, joke, clip, newer).forEach { s ->
            scripts.byName(s.name)?.let { scripts.delete(it.id) }
            catalog.forget(s.name)
        }
        if (hadCache != null) cacheFile.writeBytes(hadCache) else cacheFile.delete()
        prefs.edit { if (hadFetched != null) putLong("fetched", hadFetched) else remove("fetched") }
    }

    /** A script in the shared list, with the code the list's checksum is for. */
    private data class Shared(
        val name: String,
        val lang: ScriptLang,
        val title: String,
        val desc: String,
        val author: String,
        val version: String,
        val tags: List<String>,
        val code: String,
        val command: Boolean = false,
        val every: Int? = null,
        val at: List<String> = emptyList(),
        val on: List<String> = emptyList(),
        val uses: List<String> = emptyList(),
        val requires: String? = null,
    ) {
        val sha: String = MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = "$name.${lang.ext}"

        fun json() = JSONObject().apply {
            put("name", name); put("lang", lang.ext); put("path", "scripts/$name/$file")
            put("title", title); put("desc", desc); put("author", author); put("version", version)
            put("tags", JSONArray(tags)); put("uses", JSONArray(uses)); put("on", JSONArray(on)); put("at", JSONArray(at))
            put("command", command); every?.let { put("every", it) }; requires?.let { put("requires", it) }
            put("sha256", sha)
        }

        fun entry() = ScriptCatalog.Entry(name, lang, "scripts/$name/$file", title, desc, author, version, tags, uses, on, command, every, at, requires, sha)
    }

    /** The list as last downloaded a moment ago: kept for an hour, so it is read without going online. */
    private fun listed(vararg shared: Shared) {
        cacheFile.writeText(JSONObject().put("format", 1).put("scripts", JSONArray(shared.map { it.json() })).toString())
        prefs.edit { putLong("fetched", System.currentTimeMillis()) }
        assertEquals(shared.map { it.name }, catalog.cached()?.map { it.name })
    }

    /** The screen, opened as Scripts opens it; [downloaded] code is there as if fetched for the sheet. */
    private fun show(vararg downloaded: Shared): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            @Suppress("UNCHECKED_CAST")
            val code = ScriptActions::class.java.getDeclaredField("sharedCode").apply { isAccessible = true }.get(vm.scriptActions) as MutableMap<String, String>
            downloaded.forEach { code[it.sha] = it.code }
            vm.openScreen(Screen.GET_SCRIPTS)
        }
        compose.setContent { GetScriptsScreen(vm) }
        compose.waitUntil(5_000) { vm.scriptActions.sharedScripts.value.let { !it.loading && it.entries.isNotEmpty() } }
        return vm
    }

    private fun row(title: String, vararg texts: String): SemanticsMatcher =
        texts.fold(hasText(title) and hasClickAction()) { m, t -> m and hasText(t) }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String) = waitFor(hasText(text))

    /** Taps the button [label] once it can be tapped: a sheet's buttons wait for the script's code. */
    private fun tapWhenEnabled(label: String) {
        val button = hasText(label) and hasClickAction() and isEnabled()
        waitFor(button)
        compose.onNode(button).performScrollTo().performClick()
    }

    private fun search(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    /** Each shared script by its title, with its file, how it runs, what it does and whether it can be installed. */
    @Test fun theListShowsEachScriptAndHowItRuns() {
        listed(tide, joke, newer, taken)
        show()
        waitFor(row("Help me", "help.lua", "Its name is a command's · Name taken"))
        waitFor(row("Tide times", "gstide.lua · command", "The next high and low tide"))
        waitFor(row("Joke of the day", "gsjoke.js · every 1d · daily 07:00 · on unlock", "A joke each morning"))
        waitFor(row("From the future", "gsnewer.lua", "Needs a newer Kolahal · Needs Kolahal 99.0"))
        compose.onNodeWithText("Shared in ${ScriptCatalog.SOURCE} on GitHub").assertExists()
    }

    /**
     * The search matches names, authors and tags, every word: two words that each match a different
     * script find neither. Nothing found says so.
     */
    @Test fun theSearchMatchesNamesAuthorsAndTags() {
        listed(tide, joke)
        show()
        waitForText("Tide times")
        search("sea")
        compose.onNodeWithText("Tide times").assertExists()
        compose.onNodeWithText("Joke of the day").assertDoesNotExist()
        search("bo fun")
        compose.onNodeWithText("Joke of the day").assertExists()
        compose.onNodeWithText("Tide times").assertDoesNotExist()
        search("gstide weather")
        compose.onNodeWithText("Tide times").assertExists()
        search("sea fun")
        compose.onNodeWithText("No script matches “sea fun”.").assertExists()
        compose.onNodeWithText("Tide times").assertDoesNotExist()
        compose.onNodeWithText("Joke of the day").assertDoesNotExist()
        search("zzqx")
        compose.onNodeWithText("No script matches “zzqx”.").assertExists()
    }

    /** An empty list says nothing is shared yet. */
    @Test fun anEmptyListSaysSo() {
        cacheFile.writeText("""{"format":1,"scripts":[]}""")
        prefs.edit { putLong("fetched", System.currentTimeMillis()) }
        assertEquals(emptyList<ScriptCatalog.Entry>(), catalog.cached())
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread { vm = vms.terminal(app); vm.openScreen(Screen.GET_SCRIPTS) }
        compose.setContent { GetScriptsScreen(vm) }
        waitForText("No scripts shared yet.")
    }

    /**
     * A list that couldn't be read again says why, still shows the scripts it had, and offers to try
     * again. The failed read's state (the entries from before, and the error) is set here, as nothing
     * goes online: that a failed read keeps them isn't tried.
     */
    @Test fun aListThatCantBeReadSaysWhyAndShowsTheLastOne() {
        listed(tide)
        val vm = show()
        waitForText("Tide times")
        compose.runOnUiThread {
            @Suppress("UNCHECKED_CAST")
            val shared = ScriptActions::class.java.getDeclaredField("_shared").apply { isAccessible = true }.get(vm.scriptActions) as MutableStateFlow<ScriptActions.SharedScripts>
            shared.value = ScriptActions.SharedScripts(shared.value.entries, error = "HTTP 503 from mtwabpin.github.io")
        }
        waitForText("Can't read the list: HTTP 503 from mtwabpin.github.io")
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Tide times").assertExists()
    }

    /** A script's sheet says what it is, how it runs, what it uses and shows its code before Install. */
    @Test fun aScriptsSheetShowsItBeforeInstalling() {
        listed(tide)
        show(tide)
        waitForText("Tide times")
        compose.onNodeWithText("Tide times").performClick()
        waitForText("gstide.lua · v1.0 · by ann")
        compose.onNodeWithText("Runs: command").assertExists()
        compose.onNodeWithText("Uses: nothing private").assertExists()
        compose.onNodeWithText("Tags: sea, weather").assertExists()
        // The code is read for the sheet: once it's there, Install can be tapped.
        waitForText(tide.code)
        compose.onNodeWithText(tide.code).performScrollTo().assertExists()
        compose.onNodeWithText("Install").performScrollTo().assertIsEnabled()
        assertTrue(scripts.byName(tide.name) == null)
    }

    /** One that uses nothing private installs at the tap, says how to run it, and shows as Installed. */
    @Test fun installingOneThatUsesNothingPrivateTakesOneTap() {
        listed(tide)
        show(tide)
        waitForText("Tide times")
        compose.onNodeWithText("Tide times").performClick()
        tapWhenEnabled("Install")
        // Said once the install is done: the script is made off and switched on after.
        waitForText("Installed gstide.lua — run it as 'gstide'")
        val s = scripts.byName(tide.name)!!
        assertEquals(tide.code, s.code)
        assertTrue(s.enabled)
        waitFor(hasText("Installed") and hasClickAction())
        compose.onNode(hasText("Installed") and hasClickAction()).assertIsNotEnabled()
        waitForText("The next high and low tide · Installed")
    }

    /** Installs [clip] from its sheet, switching clipboard off in the dialog when [switchOff]. */
    private fun installClip(switchOff: Boolean) {
        listed(clip)
        show(clip)
        waitForText("Clipboard cleaner")
        compose.onNodeWithText("Clipboard cleaner").performClick()
        waitForText("Uses: clipboard")
        tapWhenEnabled("Install")
        waitForText("Install gsclip.lua?")
        compose.onNodeWithText("It requests:").assertExists()
        if (switchOff) compose.onNode(hasText("Clipboard") and hasClickAction()).performClick()
        val inDialog = hasAnyAncestor(hasAnyDescendant(hasText("It requests:")))
        compose.onNode(hasText("Install") and hasClickAction() and inDialog).performClick()
        // Said once the install is done, with its answers and its switch in.
        waitForText("Installed gsclip.lua")
    }

    /** One that asks for something lists it to switch off before installing; it installs with that answer. */
    @Test fun installingOneThatAsksLetsYouChoose() {
        installClip(switchOff = true)
        val s = scripts.byName(clip.name)!!
        assertEquals(clip.code, s.code)
        assertTrue(s.enabled)
        assertFalse("clipboard was switched off before installing", s.grants.allows("clipboard"))
    }

    /** Left on in the dialog, what it asks for is allowed. */
    @Test fun installingWithItLeftOnAllowsIt() {
        installClip(switchOff = false)
        val s = scripts.byName(clip.name)!!
        assertTrue(s.enabled)
        assertTrue("clipboard was left on", s.grants.allows("clipboard"))
    }

    /** A newer version of one installed from here shows as an update, and Update brings its code. */
    @Test fun anUpdateShowsItsVersionAndBringsTheNewCode() {
        catalog.install(tide.entry(), tide.code)
        listed(tideNext)
        show(tideNext)
        waitForText("The next high and low tide · Update to 1.1")
        compose.onNodeWithText("Tide times").performClick()
        tapWhenEnabled("Update to 1.1")
        compose.waitUntil(5_000) { scripts.byName(tide.name)?.code == tideNext.code }
        waitForText("Updated gstide.lua to 1.1")
        waitForText("The next high and low tide · Installed")
    }

    /** A copy you edited is replaced only after asking: Cancel keeps your edits, Replace brings the shared code. */
    @Test fun anEditedCopyIsReplacedOnlyAfterAsking() {
        val installed = catalog.install(tide.entry(), tide.code)
        val mine = tide.code + "-- mine\n"
        scripts.saveCode(installed.id, mine, byUser = true)
        listed(tide)
        show(tide)
        waitForText("The next high and low tide · Installed, edited")
        compose.onNodeWithText("Tide times").performClick()
        tapWhenEnabled("Replace your edited copy")
        waitForText("Replace gstide.lua?")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Replace gstide.lua?").assertDoesNotExist()
        assertEquals(mine, scripts.byName(tide.name)!!.code)
        compose.onNodeWithText("Replace your edited copy").performClick()
        compose.onNodeWithText("Replace").performClick()
        compose.waitUntil(5_000) { scripts.byName(tide.name)?.code == tide.code }
        waitForText("The next high and low tide · Installed")
    }
}
