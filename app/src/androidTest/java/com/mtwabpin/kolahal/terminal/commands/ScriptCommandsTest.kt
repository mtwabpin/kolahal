package com.mtwabpin.kolahal.terminal.commands

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.script.SecretStore
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `script` and `status`: what each part prints and what it changes. */
@RunWith(AndroidJUnit4::class)
class ScriptCommandsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val scripts = ScriptRepository.get(context)
    private val statuses = KvStore(context, Stores.STATUS)
    private val asked = mutableListOf<Pair<String, String>>()
    private val opened = mutableListOf<String>()

    private val commands = CommandRunner(context, object : TerminalUiStub() {
        override fun openEditor(script: Script) {
            opened += script.name
        }

        override fun askSecret(script: Script, name: String) {
            asked += script.name to name
        }
    })

    /** Every script made here starts with this, so [tearDown] finds them. */
    private val prefix = "sctest"

    /**
     * Lines last, after [tearDown], once the scripts and their timers are gone: commands (the history)
     * too, as a later class reads the log and the history built from it.
     */
    @get:Rule
    val logLines = LogLinesSince()

    @After
    fun tearDown() {
        for (s in scripts.all().filter { it.name.startsWith(prefix) }) {
            ScriptScheduler.cancel(context, s.id)
            scripts.delete(s.id)
            com.mtwabpin.kolahal.data.StatusLines.clear(context, s.name)
            SecretStore(context).let { store ->
                com.mtwabpin.kolahal.script.ScriptMeta.secrets(s.code)
                    .forEach { store.set(s.name, it.name, null, it.hosts) }
            }
        }
    }

    private fun create(
        suffix: String,
        code: String,
        enabled: Boolean = true,
        lang: ScriptLang = ScriptLang.LUA
    ): Script =
        scripts.create(prefix + suffix, lang, code, enabled)

    /** Runs [line] and returns the lines it printed, its own line aside. */
    private fun lines(line: String) = commands.lines(line)

    private fun run(line: String) = commands.run(line)

    /** `status` shows what the header shows: lines of scripts that are on and may set one. */
    @Test
    fun statusListsShownLinesAndClearsThem() {
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

    /** `script pin` and `unpin`: the line shows at the top, marked in `status`; one without a status line can't be pinned. */
    @Test
    fun pinAndUnpin() {
        val s = create("pin", "status('x')")
        statuses.set(s.name, "NZ 136/8")
        assertTrue("${s.name} ▸ NZ 136/8" in run("status"))
        assertEquals(listOf("${s.name}: pinned to the top"), run("script pin ${s.name}"))
        assertTrue(scripts.byId(s.id)!!.grants.allows(ScriptAccess.HEADER))
        // Pinned: a tag after the line.
        assertTrue("${s.name} ▸ NZ 136/8 pinned" in run("status").map(com.mtwabpin.kolahal.data.StyledText::plain))
        assertEquals(listOf("${s.name}: unpinned"), run("script unpin ${s.name}"))
        assertFalse(scripts.byId(s.id)!!.grants.allows(ScriptAccess.HEADER))
        // Its line stays: only the top of home no longer shows it.
        assertEquals("NZ 136/8", statuses.all()[s.name])
        val plain = create("pin2", "return 1")
        assertEquals(
            listOf("${plain.name} has no status line to pin\nIt never calls status()"),
            run("script pin ${plain.name}")
        )
    }

    /** The library lists its scripts; one installs, shows as installed, and an edited copy is replaced only when asked. */
    @Test
    fun libraryInstallsAndKeepsYourEdits() {
        val library = ScriptLibrary(context)
        // One that isn't on the emulator already, under a name the test owns: rename after installing.
        val entry = library.entries.first { e -> scripts.byName(e.name) == null }
        val listed = run("script lib")
        assertTrue(
            listed.toString(),
            listed.any { it.startsWith("${entry.name}.${entry.lang.ext}") })
        try {
            val installed = run("script install ${entry.name}")
            assertTrue(
                installed.toString(),
                installed.any { it.startsWith("Installed ${entry.name}.${entry.lang.ext}") })
            assertTrue(run("script lib").any { com.mtwabpin.kolahal.data.StyledText.plain(it) == "${entry.name}.${entry.lang.ext} installed" })
            val s = scripts.byName(entry.name)!!
            scripts.saveCode(s.id, s.code + "\n-- mine", byUser = true)
            assertTrue(
                run("script install ${entry.name}").single()
                    .contains("differs from the library\n'script install ${entry.name} --replace' replaces it")
            )
            assertEquals(
                "script install ${entry.name} --replace",
                lines("script install ${entry.name}").single().ctx.values[LogContext.COPY]
            )
            assertTrue(scripts.byName(entry.name)!!.code.endsWith("-- mine"))
            run("script install ${entry.name} --replace")
            assertEquals(library.code(entry), scripts.byName(entry.name)!!.code)
            val missing = lines("script install nosuchscript").single()
            assertEquals("No library script 'nosuchscript'\n'script lib' lists them", missing.text)
            assertEquals("script lib", missing.ctx.values[LogContext.COPY])
        } finally {
            scripts.byName(entry.name)
                ?.let { ScriptScheduler.cancel(context, it.id); scripts.delete(it.id) }
        }
    }

    @Test
    fun newChecksTheName() {
        assertEquals(
            listOf("Invalid script name\nUse lowercase letters, digits, - and _, up to 40"),
            run("script new Bad!")
        )
        assertTrue(run("script new").single().startsWith("Usage: script new"))
        run("script new ${prefix}js.js")
        assertEquals(ScriptLang.JS, scripts.byName("${prefix}js")!!.lang)
        assertEquals(listOf("${prefix}js"), opened)
        val exists = lines("script new ${prefix}js").single()
        assertEquals("Script '${prefix}js' already exists\n'script edit ${prefix}js' opens it", exists.text)
        assertEquals("script edit ${prefix}js", exists.ctx.values[LogContext.COPY])
    }

    /** Timers, notifications and the switch, each said back. */
    @Test
    fun everyNotifyAndTheSwitch() {
        val s = create(
            "sw",
            "-- @uses clipboard\nlocal x = clipboard.get()\nnotify('x')",
            enabled = false
        )
        // A timer set switches it on, and asks for what it declares that nobody allowed yet.
        assertEquals(
            listOf(
                "${s.name} requests clipboard\nTap to allow",
                "${s.name}: runs every 15m\nThe Android minimum is 15m"
            ),
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
     * The internet together with private data, said under the line where access is given: the line
     * that asks to allow it, and `script on`. A script with the internet off gets neither.
     */
    @Test
    fun theInternetWithPrivateDataIsSaidWhereItIsGiven() {
        val pair = "\nIt can read your clipboard and use the internet\nOnly allow both for a script you trust"
        val s = create("pair", "-- @uses clipboard\nlocal r = http.get('https://example.com')", enabled = false)
        // A timer switches it on without allowing what it declares: the line that asks says what allowing it means.
        assertEquals(
            listOf("${s.name} requests clipboard\nTap to allow$pair", "${s.name}: runs every 1h"),
            run("script every ${s.name} 1h"),
        )
        run("script off ${s.name}")
        assertEquals("${s.name}: turned on$pair", run("script on ${s.name}").first())
        run("script deny ${s.name} internet")
        run("script off ${s.name}")
        assertEquals("${s.name}: turned on", run("script on ${s.name}").first())
    }

    /**
     * A script's secrets: listed, set (not too short), and cleared; a name it doesn't declare is refused.
     * A value typed on the line, too short or not, is in no line of the log (its command line neither)
     * and so not in the history.
     */
    @Test
    fun secrets() {
        val s = create(
            "sec",
            "-- @secret API_KEY example.com The key\nlocal k = secrets.get('API_KEY')"
        )
        assertTrue(
            run("script secret ${s.name}").map(com.mtwabpin.kolahal.data.StyledText::plain)
                .any { it.startsWith("API_KEY") && it.endsWith("not set") })
        // No value on the line: the masked box.
        run("script secret ${s.name} API_KEY")
        assertEquals(listOf(s.name to "API_KEY"), asked)
        val short = "zq7"
        val value = "zq7Kx9secret"
        assertEquals(
            listOf("API_KEY must be at least ${SecretStore.MIN_LENGTH} characters"),
            run("script secret ${s.name} API_KEY $short")
        )
        assertTrue(
            run("script secret ${s.name} API_KEY $value").single()
                .startsWith("${s.name}: API_KEY set")
        )
        val logged = logs.recent(400, afterId = logLines.start)
        assertTrue(
            logged.joinToString { it.text },
            logged.none { e -> short in e.text || e.ctx.values.values.any { short in it } })
        val history = logs.commandHistory()
        assertTrue(history.toString(), history.none { short in it })
        assertTrue(
            run("script secret ${s.name}").map(com.mtwabpin.kolahal.data.StyledText::plain)
                .any { it.startsWith("API_KEY") && it.endsWith("set") && !it.endsWith("not set") })
        assertEquals(
            listOf("${s.name}: API_KEY cleared"),
            run("script secret ${s.name} API_KEY --clear")
        )
        assertEquals(
            listOf("${s.name} has no @secret NOPE\nIt has: API_KEY"),
            run("script secret ${s.name} NOPE x")
        )
        val none = create("sec2", "return 1")
        assertEquals(listOf("${none.name} declares no @secret"), run("script secret ${none.name}"))
    }

    /** Access switches: listed by what declares them, turned on and off, and nothing it doesn't have. */
    @Test
    fun accessAllowAndDeny() {
        val s = create(
            "acc",
            "-- @uses clipboard\n-- @on unlock\nlocal r = http.get('https://example.com')\nstatus('x')"
        )
        val listed = run("script access ${s.name}")
        for (item in listOf(
            "internet",
            "header",
            "clipboard",
            "unlock"
        )) assertTrue("$item: $listed", listed.any { it.startsWith(item) })
        // The internet together with the clipboard: said under the first line.
        val pair = "\nIt can read your clipboard and use the internet\nOnly allow both for a script you trust"
        assertTrue(listed.first(), listed.first().endsWith("changes one$pair"))
        assertEquals(listOf("${s.name}: clipboard off"), run("script deny ${s.name} clipboard"))
        assertFalse(run("script access ${s.name}").first().endsWith(pair))
        assertFalse(scripts.byId(s.id)!!.grants.allows("clipboard"))
        statuses.set(s.name, "line")
        // Pinned until it isn't; its line stays either way.
        assertEquals(listOf("${s.name}: header on"), run("script allow ${s.name} header"))
        assertTrue(scripts.byId(s.id)!!.grants.allows(ScriptAccess.HEADER))
        run("script deny ${s.name} header")
        assertFalse(scripts.byId(s.id)!!.grants.allows(ScriptAccess.HEADER))
        assertEquals("line", statuses.all()[s.name])
        // Allowing the clipboard again, with the internet on, says what the pair can do.
        assertEquals(
            listOf("${s.name}: clipboard on, unlock on$pair"),
            run("script allow ${s.name} clipboard unlock")
        )
        // The internet off, and on again: the line says it too.
        assertEquals(listOf("${s.name}: internet off"), run("script deny ${s.name} internet"))
        assertEquals(listOf("${s.name}: internet on$pair"), run("script allow ${s.name} internet"))
        assertTrue(scripts.byId(s.id)!!.grants.allows("unlock"))
        assertEquals(
            listOf("${s.name} doesn't declare @uses dnd"),
            run("script deny ${s.name} dnd")
        )
        assertTrue(
            run("script deny ${s.name} bogus").single()
                .startsWith("No 'bogus' for ${s.name}\nIt has: internet header clipboard unlock")
        )
        assertTrue(run("script allow ${s.name}").single().startsWith("Usage: script allow"))
        val plain = create("acc2", "return 1")
        assertTrue(
            run("script access ${plain.name}").single()
                .endsWith("has no access switches\nNo internet, status line to pin, notifications, @uses or @on")
        )
    }

    /** Renamed, shown, compared and deleted; the same name, a bad one, or none is said. */
    @Test
    fun renameShowCompareAndDelete() {
        val s = create("mv", "local a = 1\nreturn a")
        assertEquals(
            listOf("${s.name} already has that name"),
            run("script mv ${s.name} ${s.name}")
        )
        assertTrue(run("script mv ${s.name} Bad!").single().startsWith("Invalid script name\n"))
        assertTrue(run("script mv ${s.name}").single().startsWith("Usage: script mv"))
        val to = "${prefix}moved"
        assertEquals(listOf("Renamed ${s.name}.lua to $to.lua"), run("script mv ${s.name} $to.lua"))
        assertNull(scripts.byName(s.name))
        val shown = lines("script cat $to")
        assertEquals(listOf("  1  local a = 1", "  2  return a"), shown.map { it.text })
        // Copy text on the first line copies the script, not the numbered line.
        assertEquals("local a = 1\nreturn a", shown.first().ctx.values[LogContext.COPY])
        assertEquals(
            listOf("$to.lua isn't from the library\nThere is nothing to compare it with"),
            run("script diff $to")
        )
        assertEquals(listOf("Deleted $to"), run("script rm $to"))
        assertNull(scripts.byName(to))
        val missing = lines("script rm $to").single()
        assertEquals("No script named '$to'\n'script ls' lists them", missing.text)
        assertEquals("script ls", missing.ctx.values[LogContext.COPY])
        assertEquals(listOf("Script name required"), run("script run"))
        // Every subcommand, those that take no script's name too.
        val said = run("script frobnicate x").single()
        assertTrue(
            said,
            said.startsWith("Usage: script <subcommand> <name>\nSubcommands: ls lib install new ")
        )
        for (sub in listOf("set", "dir", "export", "import")) assertTrue(
            "$sub: $said",
            " $sub " in said || said.endsWith(" $sub")
        )
    }

    /** A source of one's own, as `owner/name`. */
    private val source =
        (ScriptSources.parse("sctestowner/scripts") as ScriptSources.Parsed.Ok).source

    /** A shared script in a list, with a checksum that is never downloaded. */
    private fun listed(vararg names: String) = org.json.JSONObject().put("format", 1).put(
        "scripts",
        org.json.JSONArray(names.map { n ->
            org.json.JSONObject().put("name", n).put("lang", "lua").put("path", "scripts/$n/$n.lua")
                .put("title", "Title of $n").put("sha256", "d".repeat(64))
        }),
    ).toString()

    /** Lines logged after [from] (a background run's too) whose plain text is [text], once there is one. */
    private fun waitForLine(from: Long, text: String) = run {
        var found: List<com.mtwabpin.kolahal.data.LogEntry> = emptyList()
        waitUntil("a line '$text'") {
            found = logs.recent(400, afterId = from)
                .filter { com.mtwabpin.kolahal.data.StyledText.plain(it.text) == text }
            found.isNotEmpty()
        }
        found
    }

    /**
     * `script source add` adds a source with a warning and reads it; `script sources` lists it after
     * kolahal-scripts; `script source rm` removes it, and kolahal-scripts can't be removed.
     */
    @Test
    fun sourcesAreAddedListedAndRemoved() {
        val catalog = ScriptCatalog(context)
        try {
            // Its list as read a moment ago, so adding it reads nothing online.
            catalog.keep(source, listed("sctestmoon"))
            val from = logs.maxId()
            val added = run("script source add sctestowner/scripts")
            assertEquals(
                "Added sctestowner/scripts\nIt reads https://sctestowner.github.io/scripts/index.json",
                added[0]
            )
            assertEquals(
                "warning Kolahal doesn't check scripts from sctestowner/scripts\nOnly add repositories you trust",
                com.mtwabpin.kolahal.data.StyledText.plain(added[1]),
            )
            val count = waitForLine(from, "sctestowner/scripts has 1 script\n'script get' lists them")
            assertEquals("script get", count.single().ctx.values[LogContext.COPY])
            assertEquals(listOf(source.index), catalog.added())
            assertEquals(
                listOf("sctestowner/scripts is a source already"),
                run("script source add https://sctestowner.github.io/scripts/index.json")
            )

            val listed = lines("script sources").drop(1)
            assertEquals(
                listOf(ScriptCatalog.SOURCE, "sctestowner/scripts"),
                listed.map { com.mtwabpin.kolahal.data.StyledText.plain(it.text) })
            val desc = listed[1].ctx.values[LogContext.DESC].orEmpty()
            assertTrue(
                desc,
                desc.startsWith("${source.index} · not checked by Kolahal · 1 script · read ")
            )

            assertEquals(
                listOf("${ScriptCatalog.SOURCE} can't be removed"),
                run("script source rm ${ScriptCatalog.SOURCE}")
            )
            assertEquals(
                listOf("No source 'nobody/here'\n'script sources' lists them"),
                run("script source rm nobody/here")
            )
            assertEquals(
                listOf("Removed sctestowner/scripts\nThe scripts you installed from it stay"),
                run("script source rm sctestowner/scripts")
            )
            assertEquals(emptyList<String>(), catalog.added())
            assertEquals(
                listOf("Only https:// links"),
                run("script source add http://example.com/index.json")
            )
        } finally {
            catalog.add(source)
            catalog.remove(source.name)
        }
    }

    /**
     * `script get` lists every source's scripts; one that another source has first is called by
     * `source/name`, and names its source. A source that isn't there says so.
     */
    @Test
    fun scriptGetListsEverySource() {
        val catalog = ScriptCatalog(context)
        val cache = java.io.File(context.filesDir, "script-catalog.json")
        val prefs = Stores.SCRIPT_CATALOG.open(context)
        val hadCache = cache.takeIf { it.exists() }?.readBytes()
        val hadFetched = prefs.getLong("fetched", -1).takeIf { it >= 0 }
        try {
            catalog.keep(ScriptSources.DEFAULT, listed("sctestsun"))
            catalog.keep(source, listed("sctestsun", "sctestmoon"))
            catalog.add(source)
            val from = logs.maxId()
            run("script get")
            // The moon comes last, so once it is there, both suns are too.
            val moon = waitForLine(from, "sctestmoon.lua")
            val rows = waitForLine(from, "sctestsun.lua") + moon
            assertEquals(
                listOf(
                    "script get sctestsun",
                    "script get sctestowner/scripts/sctestsun",
                    "script get sctestmoon"
                ),
                rows.map { it.ctx.values[LogContext.COMMAND] },
            )
            assertEquals(
                listOf(
                    "Title of sctestsun",
                    "from sctestowner/scripts · Title of sctestsun",
                    "from sctestowner/scripts · Title of sctestmoon"
                ), rows.map { it.ctx.values[LogContext.DESC] })
            val missing = logs.maxId()
            run("script get nobody/here/sctestsun")
            val noSource = waitForLine(missing, "No source 'nobody/here'\n'script sources' lists them")
            assertEquals("script sources", noSource.single().ctx.values[LogContext.COPY])
        } finally {
            catalog.add(source)
            catalog.remove(source.name)
            if (hadCache != null) cache.writeBytes(hadCache) else cache.delete()
            prefs.edit().apply {
                if (hadFetched != null) putLong(
                    "fetched",
                    hadFetched
                ) else remove("fetched")
            }.commit()
        }
    }

    /** The list says each script's timer, kind, events and switch; triggers name known and unknown events. */
    @Test
    fun listsAndTriggers() {
        val s = create("ls", "-- @on unlock, bogusevent\nreturn 1", enabled = false)
        // Switched off: a tag after its name says so.
        val row =
            lines("script ls").single { com.mtwabpin.kolahal.data.StyledText.plain(it.text) == "${s.name}.lua off" }
        val desc = row.ctx.values[LogContext.DESC].orEmpty()
        assertTrue(
            desc,
            desc.startsWith("manual") && "on unlock" in desc && desc.endsWith("never run")
        )
        // The internet together with private data: a tag after the name, as the Scripts screen marks it.
        val pair = create("ls2", "-- @uses notes\nlocal r = http.get('https://example.com')")
        assertTrue(
            lines("script ls").any { com.mtwabpin.kolahal.data.StyledText.plain(it.text) == "${pair.name}.lua internet and private data" })
        val triggers = lines("script triggers")
        val mine =
            triggers.single { it.text == "${s.name}.lua" }.ctx.values[LogContext.DESC].orEmpty()
        assertTrue(mine, "on unlock" in mine && "unknown: bogusevent" in mine && "off" in mine)
        assertTrue(triggers.last().text.startsWith("Events: "))
    }

    /**
     * A command script that's off doesn't run by its name: it says so, with a tap to turn it on, and
     * two seconds later it has logged nothing and has never run.
     */
    @Test
    fun anOffCommandSaysItsOff() {
        val s = create("cmd", "-- @command\nlog('sctest-cmd ran')", enabled = false)
        val said = lines(s.name)
        assertEquals(listOf("${s.name} is off\nTap to turn it on"), said.map { it.text })
        assertEquals("script on ${s.name}", said.single().ctx.values[LogContext.RUN])
        // Nothing is coming, so there's nothing to wait on: give a wrong run the time to show.
        Thread.sleep(2_000)
        val later = logs.recent(400, afterId = logLines.start)
        assertTrue(
            later.joinToString { it.text },
            later.none { it.text == "sctest-cmd ran" || it.source == s.name })
        assertNull(scripts.byId(s.id)!!.lastRun)
    }

    /**
     * A run you start that shows nothing (as hn does with no new stories) says it finished, so
     * "Running …" isn't left without an answer. One that logs a line says nothing more.
     */
    @Test
    fun aRunThatShowsNothingSaysSo() {
        val quiet = create("quiet", "local x = 1")
        val loud = create("loud", "log('sctest-loud ran')")
        run("script run ${quiet.name}")
        run("script run ${loud.name}")
        com.mtwabpin.kolahal.waitUntil(
            "both runs end",
            30_000
        ) { scripts.byId(quiet.id)!!.lastRun != null && scripts.byId(loud.id)!!.lastRun != null }
        com.mtwabpin.kolahal.waitUntil("the quiet run says so", 5_000) {
            logs.recent(
                400,
                afterId = logLines.start
            ).any { it.text == "${quiet.name} ran: nothing to show" }
        }
        val later = logs.recent(400, afterId = logLines.start)
        assertTrue(later.joinToString { it.text }, later.any { it.text == "sctest-loud ran" })
        assertTrue(
            later.joinToString { it.text },
            later.none { it.text == "${loud.name} ran: nothing to show" })
    }
}
