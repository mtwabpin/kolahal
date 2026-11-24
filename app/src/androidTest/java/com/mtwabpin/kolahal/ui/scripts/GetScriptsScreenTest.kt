package com.mtwabpin.kolahal.ui.scripts

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.ScriptActions
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
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
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule
    val compose = createComposeRule()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val catalog = ScriptCatalog(app)

    // Where ScriptCatalog keeps the list it downloaded, and when it did.
    private val cacheFile = File(app.filesDir, "script-catalog.json")
    private val prefs = app.getSharedPreferences("script_catalog", Context.MODE_PRIVATE)
    private val hadCache = cacheFile.takeIf { it.exists() }?.readBytes()
    private val hadFetched = prefs.getLong("fetched", -1).takeIf { it >= 0 }

    private val tide = Shared(
        "gstide",
        ScriptLang.LUA,
        "Tide times",
        "The next high and low tide",
        "ann",
        "1.0",
        listOf("sea", "weather"),
        code = "-- @command\n-- @desc The next high and low tide\nlog(\"high tide at 14:02\")\n",
        command = true,
    )
    private val tideNext =
        tide.copy(version = "1.1", code = tide.code + "log(\"low tide at 20:15\")\n")
    private val joke = Shared(
        "gsjoke",
        ScriptLang.JS,
        "Joke of the day",
        "A joke each morning",
        "bo",
        "2.1",
        listOf("fun"),
        code = "// @every 1d\nlog('knock knock')\n",
        every = 1440,
        at = listOf("07:00"),
        on = listOf("unlock"),
    )
    private val clip = Shared(
        "gsclip",
        ScriptLang.LUA,
        "Clipboard cleaner",
        "Trims what you copy",
        "cy",
        "0.3",
        listOf("tools"),
        code = "-- @uses clipboard\nlocal s = clipboard.get()\n",
        uses = listOf("clipboard"),
    )
    private val newer = Shared(
        "gsnewer",
        ScriptLang.LUA,
        "From the future",
        "Needs a newer Kolahal",
        "di",
        "1.0",
        emptyList(),
        code = "log(1)\n",
        requires = "99.0"
    )
    private val taken = Shared(
        "help",
        ScriptLang.LUA,
        "Help me",
        "Its name is a command's",
        "ed",
        "1.0",
        emptyList(),
        code = "log(2)\n"
    )

    // A source of one's own, as `owner/name`. Its list is kept as if read a moment ago.
    private val other = (ScriptSources.parse("gsowner/gsscripts") as ScriptSources.Parsed.Ok).source
    private val tideOther = tide.copy(
        title = "Tide tables",
        desc = "High and low water, another way",
        author = "gu",
        code = tide.code + "-- tables\n"
    )
    private val moon = Shared(
        "gsmoon",
        ScriptLang.LUA,
        "Moon phase",
        "Tonight's moon",
        "gu",
        "1.0",
        listOf("sky"),
        code = "log('full moon')\n"
    )

    @After
    fun tearDown() {
        // Adding it first makes sure its list is forgotten too, also when the test only kept its list.
        catalog.add(other)
        catalog.remove(other.name)
        listOf(tide, joke, clip, newer, moon).forEach { s ->
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
        val sha: String = MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = "$name.${lang.ext}"

        fun json() = JSONObject().apply {
            put("name", name); put("lang", lang.ext); put("path", "scripts/$name/$file")
            put("title", title); put("desc", desc); put("author", author); put("version", version)
            put("tags", JSONArray(tags)); put("uses", JSONArray(uses)); put(
            "on",
            JSONArray(on)
        ); put("at", JSONArray(at))
            put("command", command); every?.let {
            put(
                "every",
                it
            )
        }; requires?.let { put("requires", it) }
            put("sha256", sha)
        }

        fun entry() = ScriptCatalog.Entry(
            name,
            lang,
            "scripts/$name/$file",
            title,
            desc,
            author,
            version,
            tags,
            uses,
            on,
            command,
            every,
            at,
            requires,
            sha
        )
    }

    /** The list as last downloaded a moment ago: kept for an hour, so it is read without going online. */
    private fun listed(vararg shared: Shared) {
        cacheFile.writeText(
            JSONObject().put("format", 1).put("scripts", JSONArray(shared.map { it.json() }))
                .toString()
        )
        prefs.edit { putLong("fetched", System.currentTimeMillis()) }
        assertEquals(shared.map { it.name }, catalog.cached()?.map { it.name })
    }

    private fun json(vararg shared: Shared) =
        JSONObject().put("format", 1).put("scripts", JSONArray(shared.map { it.json() })).toString()

    /** [source]'s list as read a moment ago, and [source] added. */
    private fun listedIn(source: ScriptSources.Source, vararg shared: Shared) {
        catalog.keep(source, json(*shared))
        catalog.add(source)
        assertEquals(shared.map { it.name }, catalog.cached(source)?.map { it.name })
    }

    /** Saves [node] as [file] in the app's external files, under sources-shots, to look at. */
    private fun shot(node: SemanticsNodeInteraction, file: String) {
        val out = checkNotNull(app.getExternalFilesDir("sources-shots")) { "no external files" }
        File(out, file).outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** The screen, opened as Scripts opens it; [downloaded] code is there as if fetched for the sheet. */
    private fun show(vararg downloaded: Shared): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            val code = vm.scriptActions.sharedCode
            downloaded.forEach { code[it.sha] = it.code }
            vm.openScreen(Screen.GET_SCRIPTS)
        }
        compose.setContent { GetScriptsScreen(vm) }
        compose.waitUntil(5_000) { vm.scriptActions.sharedScripts.value.let { !it.loading && it.entries.isNotEmpty() } }
        return vm
    }

    private fun row(title: String, vararg texts: String): SemanticsMatcher =
        texts.fold(hasText(title) and hasClickAction()) { m, t -> m and hasText(t) }

    /**
     * Taps the button [label] once it can be tapped: a sheet's buttons wait for the script's code,
     * read off the main thread, and in a full run that can take a while.
     */
    private fun tapWhenEnabled(label: String) {
        val button = hasText(label) and hasClickAction() and isEnabled()
        compose.waitForNode(button, 15_000)
        compose.onNode(button).performScrollTo().performClick()
    }

    private fun search(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    /** Each shared script by its title, with its file, how it runs, what it does and whether it can be installed. */
    @Test
    fun theListShowsEachScriptAndHowItRuns() {
        listed(tide, joke, newer, taken)
        show()
        compose.waitForNode(row("Help me", "help.lua", "Its name is a command's · Name taken"))
        compose.waitForNode(row("Tide times", "gstide.lua · command", "The next high and low tide"))
        compose.waitForNode(
            row(
                "Joke of the day",
                "gsjoke.js · every 1d · daily 07:00 · on unlock",
                "A joke each morning"
            )
        )
        compose.waitForNode(
            row(
                "From the future",
                "gsnewer.lua",
                "Needs a newer Kolahal · Needs Kolahal 99.0"
            )
        )
        compose.onNodeWithText("Shared in ${ScriptCatalog.SOURCE} on GitHub").assertExists()
    }

    /**
     * The repository has Kolahal's own library too. Those scripts are left out here: they are under
     * Add script → From the library, where a copy of one is installed and updated. Here, each would
     * say "Name taken".
     */
    @Test
    fun theAppsOwnScriptsAreLeftOut() {
        val own = Shared(
            "weather",
            ScriptLang.LUA,
            "Weather",
            "Kolahal's own",
            "Kolahal",
            "1.0",
            emptyList(),
            "log('weather')"
        )
        cacheFile.writeText(
            JSONObject().put("format", 1)
                .put("scripts", JSONArray(listOf(tide, own).map { it.json() })).toString()
        )
        prefs.edit { putLong("fetched", System.currentTimeMillis()) }
        assertEquals(listOf(tide.name), catalog.cached()?.map { it.name })
        show()
        compose.waitForNode(row("Tide times", "gstide.lua · command", "The next high and low tide"))
        compose.onNodeWithText("Kolahal's own").assertDoesNotExist()
    }

    /**
     * The search matches names, authors and tags, every word: two words that each match a different
     * script find neither. Nothing found says so.
     */
    @Test
    fun theSearchMatchesNamesAuthorsAndTags() {
        listed(tide, joke)
        show()
        compose.waitForText("Tide times")
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
    @Test
    fun anEmptyListSaysSo() {
        cacheFile.writeText("""{"format":1,"scripts":[]}""")
        prefs.edit { putLong("fetched", System.currentTimeMillis()) }
        assertEquals(emptyList<ScriptCatalog.Entry>(), catalog.cached())
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread { vm = vms.terminal(app); vm.openScreen(Screen.GET_SCRIPTS) }
        compose.setContent { GetScriptsScreen(vm) }
        compose.waitForText("No scripts shared yet.")
    }

    /**
     * A list that couldn't be read again says why, still shows the scripts it had, and offers to try
     * again. The failed read's state (the entries from before, and the error) is set here, as nothing
     * goes online: that a failed read keeps them isn't tried.
     */
    @Test
    fun aListThatCantBeReadSaysWhyAndShowsTheLastOne() {
        listed(tide)
        val vm = show()
        compose.waitForText("Tide times")
        compose.runOnUiThread {
            val shared = vm.scriptActions._shared
            shared.value =
                ScriptActions.SharedScripts(shared.value.sources.map { it.copy(error = "HTTP 503 from mtwabpin.github.io") })
        }
        compose.waitForText("Can't read ${ScriptCatalog.SOURCE}: HTTP 503 from mtwabpin.github.io")
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Tide times").assertExists()
    }

    /** A script's sheet says what it is, how it runs, what it uses and shows its code before Install. */
    @Test
    fun aScriptsSheetShowsItBeforeInstalling() {
        listed(tide)
        show(tide)
        compose.waitForText("Tide times")
        compose.onNodeWithText("Tide times").performClick()
        compose.waitForText("gstide.lua · v1.0 · by ann")
        compose.onNodeWithText("Runs: command").assertExists()
        compose.onNodeWithText("Uses: nothing private").assertExists()
        compose.onNodeWithText("Tags: sea, weather").assertExists()
        // The code is read for the sheet: once it's there, Install can be tapped.
        compose.waitForText(tide.code)
        compose.onNodeWithText(tide.code).performScrollTo().assertExists()
        compose.onNodeWithText("Install").performScrollTo().assertIsEnabled()
        assertTrue(scripts.byName(tide.name) == null)
    }

    /** One that uses nothing private installs at the tap, says how to run it, and shows as Installed. */
    @Test
    fun installingOneThatUsesNothingPrivateTakesOneTap() {
        listed(tide)
        show(tide)
        compose.waitForText("Tide times")
        compose.onNodeWithText("Tide times").performClick()
        tapWhenEnabled("Install")
        // Said once the install is done: the script is made off and switched on after.
        compose.waitForText("Installed gstide.lua\nRun it as 'gstide'")
        val s = scripts.byName(tide.name)!!
        assertEquals(tide.code, s.code)
        assertTrue(s.enabled)
        compose.waitForNode(hasText("Installed") and hasClickAction())
        compose.onNode(hasText("Installed") and hasClickAction()).assertIsNotEnabled()
        compose.waitForText("The next high and low tide · Installed")
    }

    /** Installs [clip] from its sheet, switching clipboard off in the dialog when [switchOff]. */
    private fun installClip(switchOff: Boolean) {
        listed(clip)
        show(clip)
        compose.waitForText("Clipboard cleaner")
        compose.onNodeWithText("Clipboard cleaner").performClick()
        compose.waitForText("Uses: clipboard")
        tapWhenEnabled("Install")
        compose.waitForText("Install gsclip.lua?")
        compose.onNodeWithText("It requests:").assertExists()
        if (switchOff) compose.onNode(hasText("Clipboard") and hasClickAction()).performClick()
        val inDialog = hasAnyAncestor(hasAnyDescendant(hasText("It requests:")))
        compose.onNode(hasText("Install") and hasClickAction() and inDialog).performClick()
        // Said once the install is done, with its answers and its switch in.
        compose.waitForText("Installed gsclip.lua")
    }

    /** One that asks for something lists it to switch off before installing; it installs with that answer. */
    @Test
    fun installingOneThatAsksLetsYouChoose() {
        installClip(switchOff = true)
        val s = scripts.byName(clip.name)!!
        assertEquals(clip.code, s.code)
        assertTrue(s.enabled)
        assertFalse("clipboard was switched off before installing", s.grants.allows("clipboard"))
    }

    /** Left on in the dialog, what it asks for is allowed. */
    @Test
    fun installingWithItLeftOnAllowsIt() {
        installClip(switchOff = false)
        val s = scripts.byName(clip.name)!!
        assertTrue(s.enabled)
        assertTrue("clipboard was left on", s.grants.allows("clipboard"))
    }

    /** A newer version of one installed from here shows as an update, and Update brings its code. */
    @Test
    fun anUpdateShowsItsVersionAndBringsTheNewCode() {
        catalog.install(tide.entry(), tide.code)
        listed(tideNext)
        show(tideNext)
        compose.waitForText("The next high and low tide · Update to 1.1")
        compose.onNodeWithText("Tide times").performClick()
        compose.waitForText("gstide.lua · v1.1 · by ann")
        tapWhenEnabled("Update to 1.1")
        compose.waitUntil(5_000) { scripts.byName(tide.name)?.code == tideNext.code }
        compose.waitForText("Updated gstide.lua to 1.1")
        compose.waitForText("The next high and low tide · Installed")
    }

    /** A copy you edited is replaced only after asking: Cancel keeps your edits, Replace brings the shared code. */
    @Test
    fun anEditedCopyIsReplacedOnlyAfterAsking() {
        val installed = catalog.install(tide.entry(), tide.code)
        val mine = tide.code + "-- mine\n"
        scripts.saveCode(installed.id, mine, byUser = true)
        listed(tide)
        show(tide)
        compose.waitForText("The next high and low tide · Installed, edited")
        compose.onNodeWithText("Tide times").performClick()
        compose.waitForText("gstide.lua · v1.0 · by ann")
        tapWhenEnabled("Replace your edited copy")
        compose.waitForText("Replace gstide.lua?")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Replace gstide.lua?").assertDoesNotExist()
        assertEquals(mine, scripts.byName(tide.name)!!.code)
        compose.onNodeWithText("Replace your edited copy").performClick()
        compose.onNodeWithText("Replace").performClick()
        compose.waitUntil(5_000) { scripts.byName(tide.name)?.code == tide.code }
        compose.waitForText("The next high and low tide · Installed")
    }

    // ---- sources of one's own ----------------------------------------------------------

    /** The scripts of a source you added show after kolahal-scripts', each saying where it is from. A name in both shows twice. */
    @Test
    fun aSourceYouAddedListsItsScriptsWithItsName() {
        listed(tide, joke)
        listedIn(other, tideOther, moon)
        show()
        compose.waitForNode(row("Tide times", "gstide.lua · command", "The next high and low tide"))
        compose.waitForNode(
            row(
                "Tide tables",
                "High and low water, another way · From gsowner/gsscripts"
            )
        )
        compose.waitForNode(row("Moon phase", "Tonight's moon · From gsowner/gsscripts"))
        compose.onNodeWithText("Shared in ${ScriptCatalog.SOURCE} and 1 source you added")
            .assertExists()
        compose.onNodeWithText(
            "Every script in ${ScriptCatalog.SOURCE} is reviewed",
            substring = true
        ).assertExists()
        shot(compose.onRoot(), "get-more-scripts-two-sources.png")
    }

    /** A name installed from one source says so in the other, and its sheet says where it came from. */
    @Test
    fun aNameInstalledFromAnotherSourceSaysSo() {
        catalog.install(tide.entry(), tide.code)
        listed(tide)
        listedIn(other, tideOther)
        show()
        compose.waitForNode(row("Tide times", "The next high and low tide · Installed"))
        compose.waitForNode(
            row(
                "Tide tables",
                "High and low water, another way · From gsowner/gsscripts · Installed from ${ScriptCatalog.SOURCE}"
            )
        )
        compose.onNodeWithText("Tide tables").performClick()
        compose.waitForText("You installed gstide from ${ScriptCatalog.SOURCE}.")
        compose.onNodeWithText("From: gsowner/gsscripts, a source you added").assertExists()
    }

    /** A script from a source you added installs from there, and updates follow that source. */
    @Test
    fun aScriptFromASourceYouAddedRemembersIt() {
        listed(tide)
        listedIn(other, moon)
        show(moon)
        compose.waitForText("Moon phase")
        compose.onNodeWithText("Moon phase").performClick()
        compose.waitForText("From: gsowner/gsscripts, a source you added")
        tapWhenEnabled("Install")
        compose.waitForText("Installed gsmoon.lua")
        assertEquals(other.name, catalog.installedFrom(moon.name))
        compose.waitForText("Tonight's moon · From gsowner/gsscripts · Installed")
    }

    /**
     * A source's list for this version of Kolahal shows as any other, and its files are read from
     * the version's folder, where the list came from.
     */
    @Test
    fun aVersionsListReadsItsFilesFromItsFolder() {
        val folder = checkNotNull(ScriptSources.folderOf(BuildConfig.VERSION_NAME))
        listed(tide)
        catalog.keep(other, json(moon), folder)
        catalog.add(other)
        val e = checkNotNull(catalog.cached(other)).single()
        assertEquals(folder, e.folder)
        assertEquals("${other.base}$folder/scripts/gsmoon/gsmoon.lua", catalog.address(e, e.path))
        // kolahal-scripts' list, kept alone as a Kolahal up to 0.5.16 kept it, is its general list.
        val general = checkNotNull(catalog.cached()).first { it.name == tide.name }
        assertEquals(null, general.folder)
        assertEquals("${ScriptSources.DEFAULT.base}scripts/gstide/gstide.lua", catalog.address(general, general.path))
        show(moon)
        compose.waitForText("Moon phase")
        compose.onNodeWithText("Moon phase").performClick()
        tapWhenEnabled("Install")
        compose.waitForText("Installed gsmoon.lua")
        assertEquals(other.name, catalog.installedFrom(moon.name))
        compose.waitForText("Tonight's moon · From gsowner/gsscripts · Installed")
    }

    /** A source that can't be read says why, and the other sources' scripts still show. */
    @Test
    fun aSourceThatCantBeReadDoesntHideTheOthers() {
        listed(tide)
        listedIn(other, moon)
        val vm = show()
        compose.waitForText("Moon phase")
        compose.runOnUiThread {
            val shared = vm.scriptActions._shared
            shared.value = ScriptActions.SharedScripts(
                shared.value.sources.map {
                    if (it.source == other) it.copy(
                        entries = emptyList(),
                        error = "HTTP 404 from gsowner.github.io"
                    ) else it
                },
            )
        }
        compose.waitForText("Can't read gsowner/gsscripts: HTTP 404 from gsowner.github.io")
        compose.onNodeWithText("Tide times").assertExists()
        compose.onNodeWithText("Moon phase").assertDoesNotExist()
    }

    /** Sources, in the top bar, opens the list of sources. */
    @Test
    fun sourcesOpensFromTheTopBar() {
        listed(tide)
        val vm = show()
        compose.onNode(hasText("Sources") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { vm.screen.value == Screen.SCRIPT_SOURCES }
    }

    /** The Sources page, opened as Get more scripts opens it. */
    private fun showSources(): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.GET_SCRIPTS)
            vm.openScreen(Screen.SCRIPT_SOURCES)
        }
        compose.setContent { ScriptSourcesScreen(vm) }
        compose.waitUntil(5_000) { vm.scriptActions.sharedScripts.value.let { !it.loading && it.sources.isNotEmpty() } }
        return vm
    }

    /** kolahal-scripts comes first and stays; a source you added shows its address, its scripts and when it was read, and Remove takes it off. */
    @Test
    fun theSourcesPageListsEachAndRemovesOne() {
        listed(tide, joke)
        listedIn(other, tideOther, moon)
        showSources()
        compose.waitForNode(
            hasText(ScriptCatalog.SOURCE) and hasText(ScriptSources.DEFAULT.index) and hasText(
                "Reviewed · 2 scripts · Read just now"
            )
        )
        compose.waitForNode(
            hasText("gsowner/gsscripts") and hasText("https://gsowner.github.io/gsscripts/index.json") and hasText(
                "2 scripts · Read just now"
            )
        )
        // Only the one you added can be removed.
        assertEquals(
            1,
            compose.onAllNodesWithContentDescription("Remove").fetchSemanticsNodes().size
        )
        shot(compose.onRoot(), "sources.png")
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.waitForText("Removed gsowner/gsscripts")
        compose.onNodeWithText("gsowner/gsscripts").assertDoesNotExist()
        assertEquals(emptyList<String>(), catalog.added())
    }

    /** Add source shows the address it will read and the warning, then adds the source and reads it. */
    @Test
    fun addingASourceShowsWhatItReadsAndWarns() {
        listed(tide)
        // Its list is kept already, so adding it reads nothing online.
        catalog.keep(other, json(moon))
        showSources()
        compose.onNodeWithText("Add source", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("gsowner/gsscripts")
        compose.waitForText("Reads https://gsowner.github.io/gsscripts/index.json")
        compose.onNodeWithText("Kolahal doesn't check scripts from it. Only add repositories you trust.")
            .assertExists()
        shot(compose.onNode(isDialog()), "add-source.png")
        compose.onNode(hasText("Add") and hasClickAction()).performClick()
        compose.waitForText("Added gsowner/gsscripts\n1 script")
        assertEquals(listOf(other.index), catalog.added())
        compose.waitForNode(hasText("gsowner/gsscripts") and hasText("1 script · Read just now"))
    }

    /** What isn't a source says why, and can't be added. */
    @Test
    fun anAddressThatIsntASourceSaysWhy() {
        listed(tide)
        showSources()
        compose.onNodeWithText("Add source", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("http://example.com/index.json")
        compose.waitForText("Only https:// links")
        compose.onNode(hasText("Add") and hasClickAction()).assertIsNotEnabled()
    }
}
