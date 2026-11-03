package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.ScriptTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** What the Scripts screen and a script's sheet do: each switch, timer, name, file and install changes the script and says so. */
@RunWith(AndroidJUnit4::class)
class ScriptActionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)
    private val status = KvStore(context, HostApi.STATUS_SCOPE)
    private val before = logs.maxId()
    private val host = RecordingHost()
    private val state = SavedStateHandle()
    private val scope = CoroutineScope(SupervisorJob())
    private val list = MutableStateFlow<List<Script>>(emptyList())
    private val actions = ScriptActions(context, state, scope, list, host)
    private val files = File(context.cacheDir, "script-actions-test")

    private val names = listOf("sa-one", "sa-two", "sa-three", "sa-new", "sa-cmd", "sa-imp", "sa-upd", "sa-shared")
    /** Library scripts a test installs; one that was there before the test is left alone. */
    private val library = listOf("countdown", "charge-reminder").filter { repo.byName(it) == null }

    private val catalogFile = File(context.filesDir, "script-catalog.json")
    private val catalogPrefs = context.getSharedPreferences("script_catalog", Context.MODE_PRIVATE)
    private val catalogWas = catalogFile.takeIf { it.exists() }?.readText()
    private val fetchedWas = catalogPrefs.getLong("fetched", 0L)

    @After fun tearDown() {
        scope.cancel()
        for (name in names + library) {
            repo.byName(name)?.let { ScriptScheduler.cancel(context, it.id); repo.delete(it.id) }
            status.set(name, null)
        }
        status.set("sa-gone", null)
        logs.recent(400).filter { it.id > before }.forEach { logs.delete(it.id) }
        files.deleteRecursively()
        if (catalogWas != null) catalogFile.writeText(catalogWas) else catalogFile.delete()
        catalogPrefs.edit(commit = true) { putLong("fetched", fetchedWas) }
    }

    private fun create(name: String, code: String = "return 1", enabled: Boolean = true) = repo.create(name, ScriptLang.LUA, code, enabled)

    private fun now(s: Script) = repo.byId(s.id)!!

    private fun newLines(): List<LogEntry> = logs.recent(400).filter { it.id > before }

    private fun work(s: Script): List<WorkInfo.State> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork("script-${s.id}").get().map { it.state }

    private fun file(name: String, text: String) = File(files.apply { mkdirs() }, name).apply { writeText(text) }

    private fun waitUntil(what: String, ms: Long = 5_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    /** Switched on, a script may use what it asks for, and a key it needs gets a line to set it; the list shows it on. */
    @Test fun theSwitchTurnsItOnAndAsksForItsKey() {
        val s = create("sa-one", "-- @uses clipboard\n-- @secret KEY api.example.com Your key\nreturn 1", enabled = false)
        actions.setScriptEnabled(s, true)
        assertTrue(now(s).enabled)
        assertTrue(now(s).grants.allows("clipboard"))
        assertTrue(list.value.single { it.name == "sa-one" }.enabled)
        val ask = newLines().single { it.text == "sa-one needs KEY (Your key) — tap to set it" }
        assertEquals("script secret sa-one KEY", ask.ctx.values[LogContext.RUN])

        actions.setScriptEnabled(now(s), false)
        assertFalse(now(s).enabled)
        assertFalse(list.value.single { it.name == "sa-one" }.enabled)
    }

    /** The dialog's answers are kept: what you switched off stays off, the rest is allowed. */
    @Test fun turningItOnKeepsTheDialogsAnswers() {
        val s = create("sa-one", "-- @uses clipboard, apps\nreturn 1", enabled = false)
        actions.enableScript(s, mapOf("clipboard" to false))
        val on = now(s)
        assertTrue(on.enabled)
        assertFalse(on.grants.allows("clipboard"))
        assertTrue(on.grants.allows("apps"))
        assertTrue(on.grants.undecided.isEmpty())
    }

    /** Switching its header line off takes the line off the home screen now; another switch leaves it. */
    @Test fun headerLineOffTakesTheLineAway() {
        val s = create("sa-one", "status('sa line')\nreturn http.get('https://example.com')")
        status.set("sa-one", "sa line")
        actions.setScriptAccess(s, ScriptAccess.INTERNET, false)
        assertEquals("sa line", status.get("sa-one"))
        assertFalse(now(s).grants.allows(ScriptAccess.INTERNET))

        actions.setScriptAccess(now(s), ScriptAccess.HEADER, false)
        assertNull(status.get("sa-one"))
        assertFalse(now(s).grants.allows(ScriptAccess.HEADER))
    }

    /** Long-press on a header line no script has any more offers only to clear it, and clearing it does. */
    @Test fun aLineWithNoScriptCanOnlyBeCleared() {
        status.set("sa-gone", "left behind")
        val offered = actions.statusActions("sa-gone")
        assertEquals(listOf("Clear this line"), offered.map { it.label })
        offered.single().run()
        assertNull(status.get("sa-gone"))
    }

    /** Long-press on a script's header line: run it, hide its line (saying how to bring it back), or edit it. */
    @Test fun aScriptsLineOffersRunHideAndEdit() {
        val s = create("sa-one", "status('sa line')")
        status.set("sa-one", "sa line")
        val offered = actions.statusActions("sa-one")
        assertEquals(listOf("Run sa-one now", "Hide from header", "Edit sa-one.lua"), offered.map { it.label })

        offered.single { it.label == "Edit sa-one.lua" }.run()
        assertEquals(listOf(s.id), host.opened.map { it.id })

        offered.single { it.label == "Hide from header" }.run()
        assertNull(status.get("sa-one"))
        assertFalse(now(s).grants.allows(ScriptAccess.HEADER))
        val said = newLines().single { it.text.startsWith("sa-one: header line off") }
        assertEquals("sa-one: header line off — 'script allow sa-one header' restores it", said.text)
        assertEquals("sa-one", said.ctx.values[LogContext.SCRIPT])
    }

    /** Notifications on asks for Android's permission; off doesn't. */
    @Test fun notificationsOnAsksForThePermission() {
        val s = create("sa-one", "notify('t', 'b')")
        actions.setScriptNotify(s, true)
        assertTrue(now(s).notify)
        assertEquals(listOf<UiEvent>(UiEvent.RequestNotificationPermission), host.events.toList())

        actions.setScriptNotify(now(s), false)
        assertFalse(now(s).notify)
        assertEquals(1, host.events.size)
    }

    /**
     * A timer switches a script on, no shorter than 15 minutes, but allows nothing it asks for: a
     * line offers that. Timer off, its runs stop.
     */
    @Test fun aTimerSwitchesItOnButAllowsNothing() {
        val s = create("sa-one", "-- @uses clipboard\nreturn 1", enabled = false)
        actions.setScriptInterval(s, 5)
        val on = now(s)
        assertEquals(ScriptScheduler.MIN_INTERVAL_MIN, on.intervalMin)
        assertTrue(on.enabled)
        assertFalse(on.grants.allows("clipboard"))
        val ask = newLines().single { it.text == "sa-one requests clipboard — tap to allow" }
        assertEquals("script allow sa-one clipboard", ask.ctx.values[LogContext.RUN])
        waitUntil("its timer is set") { work(s).any { !it.isFinished } }

        actions.setScriptInterval(now(s), 0)
        assertEquals(0, now(s).intervalMin)
        waitUntil("its timer is gone") { work(s).all { it.isFinished } }
    }

    /** Daily times typed twice count once, in order, and switch it on asking for the rest; none at all leaves it off. */
    @Test fun dailyTimesSwitchItOnAskingForTheRest() {
        val off = create("sa-two", enabled = false)
        assertTrue(actions.setScriptTimes(off, ""))
        assertFalse(now(off).enabled)
        assertTrue(now(off).atTimes.isEmpty())

        val s = create("sa-one", "-- @uses apps\nreturn 1", enabled = false)
        assertTrue(actions.setScriptTimes(s, "21:15 7:05, 21:15"))
        assertEquals(listOf("07:05", "21:15"), now(s).atTimes)
        assertTrue(now(s).enabled)
        assertFalse(now(s).grants.allows("apps"))
        assertTrue(newLines().any { it.text == "sa-one requests apps — tap to allow" })
    }

    /** Run now says it is running, and the script's own line follows. */
    @Test fun runNowRunsItAndSaysSo() {
        val s = create("sa-one", "log('sa ran now')")
        actions.runScriptNow(s)
        assertEquals(RecordingHost.Notice("Running sa-one…", false), host.lastNotice())
        waitUntil("the script's line", ms = 20_000) { newLines().any { it.source == "sa-one" && StyledText.plain(it.text) == "sa ran now" } }
    }

    /** A rename says what it did and the list shows the new name; a bad, built-in or taken name is refused, saying why. */
    @Test fun renameSaysWhatItDidOrWhyNot() {
        val s = create("sa-one")
        create("sa-three")
        actions.renameScript(s, "sa-two")
        assertEquals(RecordingHost.Notice("Renamed sa-one.lua to sa-two.lua", false), host.lastNotice())
        assertEquals(s.id, repo.byName("sa-two")?.id)
        assertEquals(listOf("sa-three", "sa-two"), list.value.map { it.name }.filter { it.startsWith("sa-") })

        val renamed = now(s)
        actions.renameScript(renamed, "Bad Name")
        assertEquals(RecordingHost.Notice("Script names: lowercase letters, digits, - and _, up to 40", true), host.lastNotice())
        actions.renameScript(renamed, "help")
        assertEquals(RecordingHost.Notice("'help' is a built-in command", true), host.lastNotice())
        actions.renameScript(renamed, "sa-three")
        assertEquals(RecordingHost.Notice("There is already a script named sa-three", true), host.lastNotice())
        assertEquals("sa-two", now(s).name)
    }

    /** Deleted, a script goes from the list with its header line and its timer, and that is said. */
    @Test fun deleteTakesItsLineAndTimerToo() {
        val s = create("sa-one", "status('x')")
        repo.setInterval(s.id, 60)
        ScriptScheduler.sync(context, now(s))
        waitUntil("its timer is set") { work(s).any { !it.isFinished } }
        status.set("sa-one", "its line")

        actions.deleteScript(s)
        assertNull(repo.byId(s.id))
        assertNull(status.get("sa-one"))
        assertTrue(list.value.none { it.name == "sa-one" })
        assertEquals(RecordingHost.Notice("Deleted sa-one", false), host.lastNotice())
        waitUntil("its timer is gone") { work(s).all { it.isFinished } }
    }

    /** A new script, its name tidied, opens in the editor with the template for its kind. */
    @Test fun aNewScriptOpensInTheEditor() {
        actions.newScript("  Sa-New ", ScriptLang.LUA, asCommand = false)
        val plain = repo.byName("sa-new")!!
        assertEquals(ScriptTemplates.forLang(ScriptLang.LUA, "sa-new"), plain.code)

        actions.newScript("sa-cmd", ScriptLang.JS, asCommand = true)
        val command = repo.byName("sa-cmd")!!
        assertEquals(ScriptLang.JS, command.lang)
        assertTrue(command.code, ScriptMeta.isCommand(command.code))
        assertEquals(listOf("sa-new", "sa-cmd"), host.opened.map { it.name })
        assertTrue(list.value.any { it.name == "sa-cmd" })
    }

    /** A new script needs a name of its own: a bad name, a built-in's or one in use says why and opens nothing. */
    @Test fun aNewScriptNeedsAFreeName() {
        create("sa-one")
        actions.newScript("no way", ScriptLang.LUA, asCommand = false)
        actions.newScript("help", ScriptLang.LUA, asCommand = false)
        assertEquals(listOf("Names: lowercase letters, digits, - and _", "'help' is a built-in command, pick another name"), host.shown.toList())
        actions.newScript("sa-one", ScriptLang.LUA, asCommand = false)
        assertEquals(RecordingHost.Notice("Script 'sa-one' already exists", true), host.lastNotice())
        assertTrue(host.opened.isEmpty())
        assertNull(repo.byName("help"))
    }

    /** Files imported: what came in, off until turned on, and what was skipped and why; the command list is read again. */
    @Test fun importingFilesSaysWhatCameIn() {
        create("sa-upd", "return 1")
        actions.importScriptFiles(
            listOf(
                file("sa-imp.lua", "log('imported')"),
                file("sa-upd.lua", "return 2"),
                file("notes.txt", "not a script"),
                file("help.lua", "return 3"),
            ).map(Uri::fromFile),
        )
        assertEquals(
            RecordingHost.Notice(
                "Added sa-imp.lua · updated sa-upd.lua · off until you turn them on (script on <name>) · " +
                    "skipped notes.txt (not .lua / .js), help.lua ('help' is a built-in command)",
                false,
            ),
            host.lastNotice(),
        )
        assertFalse(repo.byName("sa-imp")!!.enabled)
        assertEquals("return 2", repo.byName("sa-upd")!!.code)
        assertFalse(repo.byName("sa-upd")!!.enabled)
        assertTrue(host.commandList!!.any { it.name == "sa-imp" })
    }

    /** Nothing that could be imported, or nothing new: it says so, as an error; no files at all does nothing. */
    @Test fun importingNothingNewSaysSo() {
        actions.importScriptFiles(emptyList())
        assertTrue(host.notices.isEmpty())

        actions.importScriptFiles(listOf(Uri.fromFile(file("notes.txt", "x"))))
        assertEquals(RecordingHost.Notice("Skipped notes.txt (not .lua / .js)", true), host.lastNotice())

        create("sa-one", "return 1")
        actions.importScriptFiles(listOf(Uri.fromFile(file("sa-one.lua", "return 1"))))
        assertEquals(RecordingHost.Notice("Nothing imported", true), host.lastNotice())
    }

    /** Export writes the script's code to the file picked and says so; a picker closed with nothing picked writes nothing. */
    @Test fun exportWritesTheFilePicked() {
        val s = create("sa-one", "-- mine\nreturn 42")
        val out = File(files.apply { mkdirs() }, "sa-one.lua")
        actions.beginExportScript(s)
        actions.finishExportScript(Uri.fromFile(out))
        assertEquals("-- mine\nreturn 42", out.readText())
        assertEquals(RecordingHost.Notice("Exported sa-one.lua", false), host.lastNotice())

        out.delete()
        actions.beginExportScript(s)
        actions.finishExportScript(null)
        // The pick was dropped: a late answer writes nothing either.
        actions.finishExportScript(Uri.fromFile(out))
        assertFalse(out.exists())
        assertEquals(1, host.notices.size)
    }

    /** A script deleted while the picker was open: the export fails, saying so. */
    @Test fun exportOfADeletedScriptFails() {
        val s = create("sa-one")
        actions.beginExportScript(s)
        repo.delete(s.id)
        val out = File(files.apply { mkdirs() }, "sa-one.lua")
        actions.finishExportScript(Uri.fromFile(out))
        assertEquals(listOf("export" to "script was deleted"), host.failures.toList())
        assertFalse(out.exists())
    }

    /** Installed from the library, a script is on, and a command one says how to run it. */
    @Test fun aLibraryInstallSaysHowToRunIt() {
        val entries = actions.library.entries.filter { it.name in library }
        org.junit.Assume.assumeTrue("both library scripts are installed here already", entries.size == 2)
        actions.installFromLibrary(entries.single { it.name == "countdown" })
        assertEquals(RecordingHost.Notice("Installed countdown.lua — run it as 'countdown'", false), host.lastNotice())
        actions.installFromLibrary(entries.single { it.name == "charge-reminder" })
        assertEquals(RecordingHost.Notice("Installed charge-reminder.lua", false), host.lastNotice())
        assertTrue(repo.byName("countdown")!!.enabled)
        assertTrue(repo.byName("charge-reminder")!!.enabled)
        assertTrue(host.commandList!!.map { it.name }.containsAll(listOf("countdown", "charge-reminder")))
    }

    /** A key set or cleared shows as set or not, and the log says which, never the value; Close takes the terminal's prompt for it away. */
    @Test fun aKeyIsLoggedWithoutItsValue() {
        val s = create("sa-one", "-- @secret KEY api.example.com Your key\nreturn 1")
        val key = ScriptMeta.secrets(s.code).single()
        assertTrue(actions.secretsSet(s).isEmpty())
        val version = actions.secretsVersion.value

        actions.setSecret("sa-one", key, "v4lue-1234")
        assertEquals(setOf("KEY"), actions.secretsSet(s))
        assertTrue(actions.secretsVersion.value > version)
        actions.setSecret("sa-one", key, null)
        assertTrue(actions.secretsSet(s).isEmpty())
        assertEquals(listOf("sa-one: KEY set", "sa-one: KEY cleared"), newLines().map { it.text }.filter { it.startsWith("sa-one: ") })
        assertTrue(newLines().none { "v4lue-1234" in it.text })

        actions.askSecret(s, "KEY")
        assertEquals(ScriptActions.SecretPrompt("sa-one", "KEY"), actions.secretPrompt.value)
        actions.closeSecretPrompt()
        assertNull(actions.secretPrompt.value)
    }

    /** An option shows its default until you set it, then yours, marked as yours; reset, the default again. Each change is a line. */
    @Test fun anOptionShowsYoursOrTheDefault() {
        val s = create("sa-one", "-- @option city London The city\nlog(options.city)")
        assertEquals(mapOf("city" to "London") to emptySet<String>(), actions.optionValues(s))
        val version = actions.optionsVersion.value

        actions.setOption("sa-one", "city", "Paris")
        assertEquals(mapOf("city" to "Paris") to setOf("city"), actions.optionValues(s))
        assertTrue(actions.optionsVersion.value > version)
        actions.setOption("sa-one", "city", null)
        assertEquals(mapOf("city" to "London") to emptySet<String>(), actions.optionValues(s))
        assertEquals(listOf("sa-one: city = Paris", "sa-one: city reset to default"), newLines().map { it.text }.filter { it.startsWith("sa-one: ") })
    }

    /** Get more scripts lists the copy of the list kept for the hour, without going online. */
    @Test fun getMoreScriptsListsTheCopyKept() {
        catalogFile.writeText(catalog("sa-shared", sha = "a".repeat(64)))
        catalogPrefs.edit(commit = true) { putLong("fetched", System.currentTimeMillis()) }
        actions.loadSharedScripts()
        waitUntil("the list is read") { !actions.sharedScripts.value.loading }
        val shared = actions.sharedScripts.value
        assertNull(shared.error)
        assertEquals(listOf("sa-shared"), shared.entries.map { it.name })
        assertEquals("Shared test", shared.entries.single().title)
    }

    /**
     * A shared script, its code already read for its sheet: installed it says how to run it; a newer
     * version says it updated it; one whose file doesn't declare what the list says isn't installed.
     */
    @Test fun aSharedScriptInstallsAndUpdates() {
        catalogFile.writeText(catalog("sa-shared", sha = "a".repeat(64)))
        catalogPrefs.edit(commit = true) { putLong("fetched", System.currentTimeMillis()) }
        val entry = ScriptCatalog(context).cached()!!.single()
        // What reading it for the sheet keeps, so no download is needed here.
        @Suppress("UNCHECKED_CAST")
        val read = ScriptActions::class.java.getDeclaredField("sharedCode").apply { isAccessible = true }.get(actions)
            as java.util.concurrent.ConcurrentHashMap<String, String>
        read[entry.sha256] = "-- @command\nlog('v1')"
        kotlinx.coroutines.runBlocking { assertEquals("-- @command\nlog('v1')", actions.sharedScriptCode(entry)) }

        actions.installSharedScript(entry)
        assertEquals(RecordingHost.Notice("Installed sa-shared.lua — run it as 'sa-shared'", false), host.lastNotice())
        assertTrue(repo.byName("sa-shared")!!.enabled)

        val newer = entry.copy(version = "1.1", sha256 = "b".repeat(64))
        read[newer.sha256] = "-- @command\nlog('v2')"
        actions.installSharedScript(newer)
        assertEquals(RecordingHost.Notice("Updated sa-shared.lua to 1.1", false), host.lastNotice())
        assertEquals("-- @command\nlog('v2')", repo.byName("sa-shared")!!.code)

        val wrong = entry.copy(version = "1.2", sha256 = "c".repeat(64), uses = listOf("clipboard"))
        read[wrong.sha256] = "-- @command\nlog('v3')"
        actions.installSharedScript(wrong)
        assertEquals(listOf("install" to "sa-shared doesn't match the script list; refresh it and try again"), host.failures.toList())
        assertEquals("-- @command\nlog('v2')", repo.byName("sa-shared")!!.code)
    }

    /** The scripts list is read again on request: one added elsewhere (`script new`) shows. */
    @Test fun theListIsReadAgain() {
        create("sa-one")
        assertTrue(list.value.isEmpty())
        actions.refreshScripts()
        assertTrue(list.value.any { it.name == "sa-one" })
    }

    private fun catalog(name: String, sha: String) =
        """{"format":1,"scripts":[{"name":"$name","lang":"lua","path":"scripts/$name/$name.lua","title":"Shared test",""" +
            """"desc":"A test","author":"kolahal","version":"1.0","tags":[],"uses":[],"on":[],"command":true,"sha256":"$sha"}]}"""
}
