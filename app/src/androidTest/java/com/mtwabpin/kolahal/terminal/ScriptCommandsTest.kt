package com.mtwabpin.kolahal.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.SecretStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** `script` and `status`: what each part prints and what it changes. */
@RunWith(AndroidJUnit4::class)
class ScriptCommandsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val scripts = ScriptRepository.get(context)
    private val statuses = KvStore(context, HostApi.STATUS_SCOPE)
    private val asked = mutableListOf<Pair<String, String>>()
    private val opened = mutableListOf<String>()

    private val ui = object : TerminalUi {
        override fun pickScriptFolder() = Unit
        override fun requestFontImport() = Unit
        override fun openScreen(screen: Screen) = Unit
        override fun requestContactsPermission() = Unit
        override fun openEditor(script: Script) { opened += script.name }
        override fun askSecret(script: Script, name: String) { asked += script.name to name }
        override fun requestNotificationPermission() = Unit
        override fun clearScreen() = Unit
    }
    private val processor = CommandProcessor(context, ui)

    /** Every script made here starts with this, so [tearDown] finds them. */
    private val prefix = "sctest"

    /** The log's newest line before the test: [tearDown] deletes what came after, commands (the history) too. */
    private val start = logs.maxId()

    @After fun tearDown() {
        try {
            for (s in scripts.all().filter { it.name.startsWith(prefix) }) {
                ScriptScheduler.cancel(context, s.id)
                scripts.delete(s.id)
                com.mtwabpin.kolahal.data.Pins.clearLines(context, s.name)
                SecretStore(context).let { store -> com.mtwabpin.kolahal.script.ScriptMeta.secrets(s.code).forEach { store.set(s.name, it.name, null, it.hosts) } }
            }
        } finally {
            // Lines last, once the scripts and their timers are gone: a later class reads the log and
            // the history built from it.
            while (true) {
                val mine = logs.recent(400, afterId = start)
                if (mine.isEmpty()) break
                mine.forEach { logs.delete(it.id) }
            }
        }
    }

    private fun create(suffix: String, code: String, enabled: Boolean = true, lang: ScriptLang = ScriptLang.LUA): Script =
        scripts.create(prefix + suffix, lang, code, enabled)

    /** Runs [line] and returns the lines it printed, its own line aside. */
    private fun lines(line: String): List<LogEntry> {
        val before = logs.maxId()
        processor.run(line)
        return logs.recent(400).filter { it.id > before && it.kind != LogKind.COMMAND }
    }

    private fun run(line: String): List<String> = lines(line).map { it.text }

    /** `status` shows what the header shows: lines of scripts that are on and may set one. */
    @Test fun statusListsShownLinesAndClearsThem() {
        val shown = create("st", "status('42 deg')")
        val off = create("st2", "status('hidden')", enabled = false)
        statuses.set(shown.name, "42 deg")
        statuses.set(off.name, "hidden")
        val listed = run("status")
        assertTrue(listed.toString(), "${shown.name} ▸ 42 deg" in listed)
        assertFalse(listed.toString(), listed.any { it.startsWith(off.name) })
        assertEquals(listOf("Cleared status: ${shown.name}"), run("status clear ${shown.name}"))
        assertNull(statuses.all()[shown.name])
        assertEquals("hidden", statuses.all()[off.name])
        assertTrue(run("status bogus").single().startsWith("Usage: status"))
    }

    /** Pinned header items list with the status lines, marked, and `status clear` takes them too. */
    @Test fun statusListsPinnedItemsAndClearsThem() {
        val s = create("pin", "pin('x')")
        val pins = KvStore(context, com.mtwabpin.kolahal.data.Pins.SCOPE)
        pins.set(s.name, com.mtwabpin.kolahal.data.Pins.encode(com.mtwabpin.kolahal.data.Pin(s.name, "NZ 136/8", 0.5f, 30, 1)))
        assertTrue(run("status").toString(), "${s.name} ▸ NZ 136/8 · pinned" in run("status"))
        run("status clear")
        assertNull(pins.get(s.name))
        assertEquals(listOf("No status lines or pinned items — scripts set them with status(text) and pin(text)"), run("status"))
    }

    /** The library lists its scripts; one installs, shows as installed, and an edited copy is replaced only when asked. */
    @Test fun libraryInstallsAndKeepsYourEdits() {
        val library = ScriptLibrary(context)
        // One that isn't on the emulator already, under a name the test owns: rename after installing.
        val entry = library.entries.first { e -> scripts.byName(e.name) == null }
        val listed = run("script lib")
        assertTrue(listed.toString(), listed.any { it.startsWith("${entry.name}.${entry.lang.ext}") })
        try {
            val installed = run("script install ${entry.name}")
            assertTrue(installed.toString(), installed.any { it.startsWith("Installed ${entry.name}.${entry.lang.ext}") })
            assertTrue(run("script lib").any { it == "${entry.name}.${entry.lang.ext} ✓" })
            val s = scripts.byName(entry.name)!!
            scripts.saveCode(s.id, s.code + "\n-- mine", byUser = true)
            assertTrue(run("script install ${entry.name}").single().contains("differs from the library version"))
            assertTrue(scripts.byName(entry.name)!!.code.endsWith("-- mine"))
            run("script install ${entry.name} --replace")
            assertEquals(library.code(entry), scripts.byName(entry.name)!!.code)
            assertTrue(run("script install nosuchscript").single().startsWith("No library script 'nosuchscript'"))
        } finally {
            scripts.byName(entry.name)?.let { ScriptScheduler.cancel(context, it.id); scripts.delete(it.id) }
        }
    }

    @Test fun newChecksTheName() {
        assertTrue(run("script new Bad!").single().startsWith("Script names:"))
        assertTrue(run("script new").single().startsWith("Usage: script new"))
        run("script new ${prefix}js.js")
        assertEquals(ScriptLang.JS, scripts.byName("${prefix}js")!!.lang)
        assertEquals(listOf("${prefix}js"), opened)
        assertTrue(run("script new ${prefix}js").single().contains("already exists"))
    }

    /** Timers, notifications and the switch, each said back. */
    @Test fun everyNotifyAndTheSwitch() {
        val s = create("sw", "-- @uses clipboard\nlocal x = clipboard.get()\nnotify('x')", enabled = false)
        // A timer set switches it on, and asks for what it declares that nobody allowed yet.
        assertEquals(
            listOf("${s.name} requests clipboard — tap to allow", "${s.name}: runs every 15m (Android minimum is 15m)"),
            run("script every ${s.name} 5m"),
        )
        assertTrue(scripts.byId(s.id)!!.enabled)
        assertEquals(listOf("${s.name}: timer off"), run("script every ${s.name} off"))
        assertEquals(0, scripts.byId(s.id)!!.intervalMin)
        assertTrue(run("script every ${s.name} soon").single().startsWith("Usage: script every"))
        assertEquals(listOf("${s.name}: notifications on"), run("script notify ${s.name} on"))
        assertTrue(scripts.byId(s.id)!!.notify)
        assertEquals(listOf("${s.name}: notifications off"), run("script notify ${s.name} off"))
        assertTrue(run("script notify ${s.name} maybe").single().startsWith("Usage: script notify"))
        assertEquals(listOf("${s.name}: turned off"), run("script off ${s.name}"))
        assertFalse(scripts.byId(s.id)!!.enabled)
        val on = run("script on ${s.name}")
        assertEquals("${s.name}: turned on", on.first())
        assertTrue(on.toString(), on.any { it.startsWith("${s.name} can use: clipboard") })
        assertTrue(scripts.byId(s.id)!!.grants.allows("clipboard"))
    }

    /**
     * A script's secrets: listed, set (not too short), and cleared; a name it doesn't declare is refused.
     * A value typed on the line, too short or not, is in no line of the log (its command line neither)
     * and so not in the history.
     */
    @Test fun secrets() {
        val s = create("sec", "-- @secret API_KEY example.com The key\nlocal k = secrets.get('API_KEY')")
        assertTrue(run("script secret ${s.name}").any { it.startsWith("API_KEY") && it.endsWith("not set") })
        // No value on the line: the masked box.
        run("script secret ${s.name} API_KEY")
        assertEquals(listOf(s.name to "API_KEY"), asked)
        val short = "zq7"
        val value = "zq7Kx9secret"
        assertEquals(listOf("API_KEY must be at least ${SecretStore.MIN_LENGTH} characters"), run("script secret ${s.name} API_KEY $short"))
        assertTrue(run("script secret ${s.name} API_KEY $value").single().startsWith("${s.name}: API_KEY set"))
        val logged = logs.recent(400, afterId = start)
        assertTrue(logged.joinToString { it.text }, logged.none { e -> short in e.text || e.ctx.values.values.any { short in it } })
        val history = logs.commandHistory()
        assertTrue(history.toString(), history.none { short in it })
        assertTrue(run("script secret ${s.name}").any { it.startsWith("API_KEY") && it.endsWith("set") && !it.endsWith("not set") })
        assertEquals(listOf("${s.name}: API_KEY cleared"), run("script secret ${s.name} API_KEY --clear"))
        assertEquals(listOf("${s.name} has no @secret NOPE — it has: API_KEY"), run("script secret ${s.name} NOPE x"))
        val none = create("sec2", "return 1")
        assertEquals(listOf("${none.name} declares no @secret"), run("script secret ${none.name}"))
    }

    /** Access switches: listed by what declares them, turned on and off, and nothing it doesn't have. */
    @Test fun accessAllowAndDeny() {
        val s = create("acc", "-- @uses clipboard\n-- @on unlock\nlocal r = http.get('https://example.com')\nstatus('x')")
        val listed = run("script access ${s.name}")
        for (item in listOf("internet", "header", "clipboard", "unlock")) assertTrue("$item: $listed", listed.any { it.startsWith(item) })
        assertEquals(listOf("${s.name}: clipboard off"), run("script deny ${s.name} clipboard"))
        assertFalse(scripts.byId(s.id)!!.grants.allows("clipboard"))
        statuses.set(s.name, "line")
        run("script deny ${s.name} header")
        // Its line leaves the header at once.
        assertNull(statuses.all()[s.name])
        assertEquals(listOf("${s.name}: clipboard on, unlock on"), run("script allow ${s.name} clipboard unlock"))
        assertTrue(scripts.byId(s.id)!!.grants.allows("unlock"))
        assertEquals(listOf("${s.name} doesn't declare @uses dnd"), run("script deny ${s.name} dnd"))
        assertTrue(run("script deny ${s.name} bogus").single().startsWith("No 'bogus' for ${s.name} — it has: internet header clipboard unlock"))
        assertTrue(run("script allow ${s.name}").single().startsWith("Usage: script allow"))
        val plain = create("acc2", "return 1")
        assertTrue(run("script access ${plain.name}").single().endsWith("has no access switches: no internet, header line, notifications, @uses or @on"))
    }

    /** Renamed, shown, compared and deleted; the same name, a bad one, or none is said. */
    @Test fun renameShowCompareAndDelete() {
        val s = create("mv", "local a = 1\nreturn a")
        assertEquals(listOf("${s.name} already has that name"), run("script mv ${s.name} ${s.name}"))
        assertTrue(run("script mv ${s.name} Bad!").single().startsWith("Script names:"))
        assertTrue(run("script mv ${s.name}").single().startsWith("Usage: script mv"))
        val to = "${prefix}moved"
        assertEquals(listOf("Renamed ${s.name}.lua to $to.lua"), run("script mv ${s.name} $to.lua"))
        assertNull(scripts.byName(s.name))
        val shown = lines("script cat $to")
        assertEquals(listOf("  1  local a = 1", "  2  return a"), shown.map { it.text })
        // Copy text on the first line copies the script, not the numbered line.
        assertEquals("local a = 1\nreturn a", shown.first().ctx.values[LogContext.COPY])
        assertEquals(listOf("$to.lua isn't from the library, so there is nothing to compare it with"), run("script diff $to"))
        assertEquals(listOf("Deleted $to"), run("script rm $to"))
        assertNull(scripts.byName(to))
        assertEquals(listOf("No script named '$to' — 'script ls'"), run("script rm $to"))
        assertEquals(listOf("Script name required"), run("script run"))
        assertTrue(run("script frobnicate x").single().startsWith("Usage: script [ls|new|"))
    }

    /** The list says each script's timer, kind, events and switch; triggers name known and unknown events. */
    @Test fun listsAndTriggers() {
        val s = create("ls", "-- @on unlock, bogusevent\nreturn 1", enabled = false)
        val row = lines("script ls").single { it.text == "${s.name}.lua" }
        val desc = row.ctx.values[LogContext.DESC].orEmpty()
        assertTrue(desc, desc.startsWith("manual") && "on unlock" in desc && "off" in desc && desc.endsWith("never run"))
        val triggers = lines("script triggers")
        val mine = triggers.single { it.text == "${s.name}.lua" }.ctx.values[LogContext.DESC].orEmpty()
        assertTrue(mine, "on unlock" in mine && "unknown: bogusevent" in mine && "off" in mine)
        assertTrue(triggers.last().text.startsWith("Events: "))
    }

    /**
     * A command script that's off doesn't run by its name: it says so, with a tap to turn it on, and
     * two seconds later it has logged nothing and has never run.
     */
    @Test fun anOffCommandSaysItsOff() {
        val s = create("cmd", "-- @command\nlog('sctest-cmd ran')", enabled = false)
        val said = lines(s.name)
        assertEquals(listOf("${s.name} is off — tap to turn it on"), said.map { it.text })
        assertEquals("script on ${s.name}", said.single().ctx.values[LogContext.RUN])
        // Nothing is coming, so there's nothing to wait on: give a wrong run the time to show.
        Thread.sleep(2_000)
        val later = logs.recent(400, afterId = start)
        assertTrue(later.joinToString { it.text }, later.none { it.text == "sctest-cmd ran" || it.source == s.name })
        assertNull(scripts.byId(s.id)!!.lastRun)
    }
}
