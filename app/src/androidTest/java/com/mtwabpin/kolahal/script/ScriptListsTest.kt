package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.feeds.Feed
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.NoteStore
import com.mtwabpin.kolahal.productivity.ReminderStore
import com.mtwabpin.kolahal.productivity.TodoStore
import com.mtwabpin.kolahal.waitForValue
import com.mtwabpin.kolahal.waitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A script's reads of your own lists: `todos`, `notes`, `reminders`, `calendar` and `feeds`
 * ([HostData]).
 * - Without its `@uses` line a call is an error, and nothing is read or changed.
 * - With the line and its switch off, a call gives an empty list, and `todos.setDone` changes nothing.
 * - Allowed, both languages read the same rows.
 * - A todo ticked in a real run, in the `:script` process, reaches home: the todo store's count
 *   of writes rises here in the main process.
 * - `feeds.unread` reads the feed database from the `:script` process.
 *
 * Every row the tests add has [mark] in its text, and is taken away again.
 */
@RunWith(AndroidJUnit4::class)
class ScriptListsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val todos = TodoStore.get(context)
    private val notes = NoteStore.get(context)
    private val reminders = ReminderStore.get(context)
    private val feeds = FeedRepository.get(context)
    private val logs = LogRepository.get(context)
    @get:Rule val logLines = LogLinesSince()

    /** In every text a test adds, so its rows are told from yours. Letters and digits only: it goes into script code. */
    private val mark = "sl" + System.nanoTime() % 100_000_000

    private val madeTodos = mutableListOf<Long>()
    private val madeNotes = mutableListOf<Long>()
    private val madeReminders = mutableListOf<Long>()
    private val madeFeeds = mutableListOf<Long>()
    private val madeScripts = mutableListOf<Long>()

    /**
     * What a run that died half way left behind: this class's scripts and feeds. Their names are
     * only ever a test's (`sltest…`, `https://example.com/sl<digits>.xml`), so they are safe to
     * take away. A left todo, note or reminder stays: its text could be yours.
     */
    @Before fun clearLeftovers() {
        scripts.all().filter { it.name.startsWith(SCRIPTS) }.forEach { removeScript(it.id) }
        feeds.feeds().filter { TEST_FEED.matches(it.url) }.forEach { feeds.remove(it.id) }
    }

    private fun removeScript(id: Long) {
        ScriptScheduler.cancel(context, id)
        scripts.delete(id)
    }

    @After fun tearDown() {
        madeScripts.forEach(::removeScript)
        madeTodos.forEach { todos.remove(it) }
        madeNotes.forEach { notes.remove(it) }
        madeReminders.forEach { reminders.remove(it) }
        madeFeeds.forEach { feeds.remove(it) }
    }

    private fun todo(text: String, done: Boolean = false): Long =
        todos.add("$mark $text").also { madeTodos += it; if (done) todos.setDone(it, true) }

    private fun note(text: String): Long = notes.add("$mark $text").also { madeNotes += it }

    /** A reminder in the store only: no alarm is set for it, so it never fires. */
    private fun reminder(text: String, at: Long, repeat: String? = null): Long =
        reminders.add(at, "$mark $text", repeat, repeat?.let { "09:00" }).id.also { madeReminders += it }

    /** A feed named after [mark], switched off so that nothing fetches it, with two items. The first one is read. */
    private fun feed(): Feed {
        val feed = feeds.add("https://example.com/$mark.xml", "Feed $mark")
        madeFeeds += feed.id
        feeds.setEnabled(feed.id, false)
        feeds.insertItems(
            feed.id,
            listOf(
                FeedRepository.NewItem("new-$mark", "New $mark", "https://example.com/new", "Sat, 14 Nov 2026 10:00:00 GMT", "A summary"),
                FeedRepository.NewItem("old-$mark", "Old $mark", null, null, null),
            ),
        )
        feeds.markRead(feeds.items(feed.id).first { it.guid == "old-$mark" }.id)
        return feed
    }

    /**
     * A saved script's host: [declared] as its `@uses` line names them, each allowed unless it is
     * in [off]. A new one for every run: a JavaScript run ends its host.
     */
    private fun host(vararg declared: String, off: Set<String> = emptySet()) = HostApi(
        context, "sltest", scriptId = null, notifyEnabled = false,
        grants = ScriptAccess.Grants(declared.toSet(), declared.associateWith { it !in off }),
    )

    private val all = arrayOf("todos", "notes", "reminders", "calendar", "feeds")

    /** What [code] gives, as the prompt would print it. */
    private fun lua(code: String, host: HostApi = host(*all)): String? = runBlocking { LuaEngine().run(code, "t", host, 10_000, true) }

    private fun js(code: String, host: HostApi = host(*all)): String? = runBlocking { JsEngine().run(code, "t", host, 10_000, true) }

    private fun luaError(code: String, host: HostApi = host(*all)): String =
        runCatching { lua(code, host) }.exceptionOrNull()?.let { it.message ?: it.toString() }.orEmpty()

    private fun jsError(code: String, host: HostApi = host(*all)): String =
        runCatching { js(code, host) }.exceptionOrNull()?.let { it.message ?: it.toString() }.orEmpty()

    /** A saved script, switched on with everything it declares allowed, as the script manager leaves it. */
    private fun script(suffix: String, code: String, lang: ScriptLang = ScriptLang.LUA): Script =
        scripts.create(SCRIPTS + suffix, lang, code).also { madeScripts += it.id }

    /** The run's own line that starts with [start], once it is in the log. A real run writes it from the `:script` process. */
    private fun lineOf(s: Script, start: String): String = waitForValue("${s.name} logged no line that starts with $start", 30_000) {
        logs.recent(400, afterId = logLines.start).map { StyledText.plain(it.text) }.firstOrNull { it.startsWith(start) }
    }

    // ---- no @uses line ------------------------------------------------------------------

    /** Every call is an error without its `@uses` line, in both languages. Another item's line doesn't help. */
    @Test fun withoutItsUsesLineACallIsAnError() {
        val calls = listOf(
            "todos.list()" to "todos", "todos.setDone(1, true)" to "todos", "notes.list()" to "notes",
            "reminders.list()" to "reminders", "calendar.events()" to "calendar", "feeds.unread()" to "feeds",
        )
        for ((call, item) in calls) {
            val others = all.filter { it != item }.toTypedArray()
            for (h in listOf({ host() }, { host(*others) }, { host("clipboard", "apps") })) {
                assertTrue("lua $call: ${luaError(call, h())}", luaError(call, h()).contains("needs `@uses $item`"))
                assertTrue("js $call: ${jsError(call, h())}", jsError(call, h()).contains("needs `@uses $item`"))
            }
        }
    }

    /** A script without `@uses todos` can't tick a todo: the call is an error, and the todo stays open. */
    @Test fun withoutItsUsesLineNothingIsTicked() {
        val id = todo("stays open")
        val before = todos.changes.value
        assertTrue(luaError("todos.setDone($id, true)", host("notes")).contains("needs `@uses todos`"))
        assertTrue(jsError("todos.setDone($id, true)", host()).contains("needs `@uses todos`"))
        assertFalse(todos.all().first { it.id == id }.done)
        assertEquals(before, todos.changes.value)
    }

    // ---- switched off -------------------------------------------------------------------

    /** With the line and its switch off, every list is empty though each has rows, and nothing is ticked. */
    @Test fun aSwitchedOffItemGivesAnEmptyList() {
        val id = todo("not for this script")
        note("a private note")
        reminder("a private reminder", System.currentTimeMillis() + 3_600_000)
        feed()
        fun off() = host(*all, off = all.toSet())
        for (call in listOf("todos.list()", "notes.list(50)", "reminders.list()", "calendar.events(30)", "feeds.unread(50)")) {
            assertEquals("lua $call", "0", lua("#$call", off()))
            assertEquals("js $call", "0", js("$call.length", off()))
        }
        assertEquals("false", lua("todos.setDone($id, true)", off()))
        assertEquals("false", js("todos.setDone($id, true)", off()))
        assertFalse(todos.all().first { it.id == id }.done)
        // One switch is one list: with only notes on, the todos stay unseen.
        val notesOnly = { host(*all, off = all.toSet() - "notes") }
        assertEquals("0", lua("#todos.list()", notesOnly()))
        assertEquals("true", lua("#notes.list(50) > 0", notesOnly()))
        // The script can ask which it is.
        assertEquals("false", lua("allowed('todos')", off()))
        assertEquals("true", lua("allowed('notes')", notesOnly()))
    }

    // ---- allowed: what the lists hold ---------------------------------------------------

    /** The same rows in both languages: open todos first, an id that is a whole number, and true or false for done. */
    @Test fun aScriptReadsYourTodos() {
        val open = todo("open")
        val done = todo("done", done = true)
        val expected = "$open|$mark open|false;$done|$mark done|true"
        val luaCode = """
            local out = {}
            for _, t in ipairs(todos.list()) do
              if t.text:find("$mark", 1, true) then out[#out + 1] = t.id .. "|" .. t.text .. "|" .. tostring(t.done) end
            end
            return table.concat(out, ";")
        """.trimIndent()
        assertEquals(expected, lua(luaCode))
        assertEquals(expected, js("todos.list().filter(t => t.text.includes('$mark')).map(t => t.id + '|' + t.text + '|' + t.done).join(';')"))
        // In JavaScript an id is a plain number, so it can go into a widget and into JSON. (A BigInt can't: JSON.stringify throws.)
        assertEquals(
            "number true boolean;number true boolean",
            js("todos.list().filter(t => t.text.includes('$mark')).map(t => typeof t.id + ' ' + Number.isInteger(t.id) + ' ' + typeof t.done).join(';')"),
        )
        val json = js("JSON.stringify(todos.list())").orEmpty()
        assertTrue(json, Regex("\"id\":$open[,}]").containsMatchIn(json) && Regex("\"id\":$done[,}]").containsMatchIn(json))
        assertEquals("true", lua("math.type(todos.list()[1].id) == 'integer'"))
        // Code typed at the prompt may use everything.
        assertEquals(expected, lua(luaCode, HostApi(context, "repl", scriptId = null, notifyEnabled = false)))
    }

    /** Notes come newest first, with the time in seconds. Reminders come soonest first, and say how one repeats. */
    @Test fun aScriptReadsYourNotesAndReminders() {
        note("first")
        note("second\nline two")
        val newest = "notes.list(50).filter(n => n.text.includes('$mark')).map(n => n.text).join(';')"
        assertEquals("$mark second\nline two;$mark first", js(newest))
        assertEquals("true", lua("local n = notes.list(1)[1]; return math.abs(os.time() - n.created) < 300 and math.type(n.id) == 'integer'"))
        assertEquals("1", lua("#notes.list(1)"))

        val soon = System.currentTimeMillis() + 3_600_000
        reminder("later", soon + 60_000, repeat = "day")
        reminder("sooner", soon)
        val luaCode = """
            local out = {}
            for _, r in ipairs(reminders.list()) do
              if r.text:find("$mark", 1, true) then out[#out + 1] = r.text .. "|" .. r.at .. "|" .. tostring(r.repeats) end
            end
            return table.concat(out, ";")
        """.trimIndent()
        assertEquals("$mark sooner|${soon / 1000}|nil;$mark later|${(soon + 60_000) / 1000}|every day", lua(luaCode))
        assertEquals(
            "$mark sooner|undefined;$mark later|every day",
            js("reminders.list().filter(r => r.text.includes('$mark')).map(r => r.text + '|' + r.repeats).join(';')"),
        )
        // In JavaScript a time is a plain whole number of seconds, as a widget's `to` and `starts` take it.
        assertEquals("number true", js("typeof notes.list(1)[0].created + ' ' + Number.isInteger(notes.list(1)[0].created)"))
        assertEquals(
            "${soon / 1000}",
            js("String(reminders.list().find(r => r.text.includes('$mark sooner')).at)"),
        )
        assertTrue(js("JSON.stringify([notes.list(50), reminders.list()])").orEmpty().contains("$mark sooner"))
    }

    /** A count that is no number is an error that says what to pass. */
    @Test fun aWrongCountIsAnError() {
        assertTrue(luaError("notes.list('many')"), luaError("notes.list('many')").contains("notes.list: n must be a number from 1 to 50"))
        assertTrue(jsError("calendar.events('week')"), jsError("calendar.events('week')").contains("calendar.events: days must be a number from 1 to 30"))
        assertTrue(jsError("feeds.unread({})"), jsError("feeds.unread({})").contains("feeds.unread: n must be a number from 1 to 50"))
    }

    // ---- todos.setDone --------------------------------------------------------------------

    /** It ticks one todo and says whether it changed. The id may be text, as a widget's tap gives it back. */
    @Test fun setDoneChangesThatTodoAndSaysSo() {
        val id = todo("tick me")
        val other = todo("not me")
        val before = todos.changes.value
        assertEquals("true", lua("todos.setDone($id, true)"))
        assertTrue(todos.all().first { it.id == id }.done)
        // So already: nothing changes, and no write is counted.
        assertEquals("false", js("todos.setDone($id, true)"))
        assertEquals("true", js("todos.setDone('$id', false)"))
        assertFalse(todos.all().first { it.id == id }.done)
        assertEquals("true", lua("todos.setDone('$id', true)"))
        assertEquals(before + 3, todos.changes.value)
        // No todo has this id.
        assertEquals("false", lua("todos.setDone(${Long.MAX_VALUE / 4096}, true)"))
        // A wrong call is an error, and ticks nothing.
        assertTrue(luaError("todos.setDone('first', true)").contains("id must be a todo's id"))
        assertTrue(luaError("todos.setDone($other)").contains("done must be true or false"))
        assertTrue(jsError("todos.setDone($other, 'yes')").contains("done must be true or false"))
        assertTrue(jsError("todos.setDone()").contains("id must be a todo's id"))
        assertFalse(todos.all().first { it.id == other }.done)
        assertEquals(before + 3, todos.changes.value)
    }

    /** The store's own guarded write: true only when the todo changed. */
    @Test fun markChangesOnlyWhatIsNotSoAlready() {
        val id = todo("mark me")
        val before = todos.changes.value
        assertFalse(todos.mark(id, false))
        assertTrue(todos.mark(id, true))
        assertFalse(todos.mark(id, true))
        assertTrue(todos.mark(id, false))
        assertFalse(todos.mark(-1, true))
        assertEquals(before + 2, todos.changes.value)
        todos.changedElsewhere()
        assertEquals(before + 3, todos.changes.value)
    }

    /** A run changes 20 todos at most. Later calls change nothing, and the first of them says so in the log. */
    @Test fun aRunTicksTwentyTimesAtMost() {
        val id = todo("back and forth")
        val h = host("todos")
        val changed = lua("local n = 0; for i = 1, 25 do if todos.setDone($id, i % 2 == 1) then n = n + 1 end end; return n", h)
        assertEquals(HostData.MAX_TODO_CHANGES.toString(), changed)
        h.flush()
        val errors = logs.recent(400, afterId = logLines.start).filter { it.kind == LogKind.ERROR }.map { it.text }
        assertEquals(errors.toString(), 1, errors.count { "more than 20 calls in one run" in it })
        // 20 changes from open: open again.
        assertFalse(todos.all().first { it.id == id }.done)
        // The next run counts from nothing.
        assertEquals("true", lua("todos.setDone($id, true)"))
    }

    /**
     * A real run, in the `:script` process, ticks a todo. Home's todo store counts its own writes
     * only, so the run tells it with a message, and the count rises here: the Todos widget and the
     * todo list then read the list again.
     */
    @Test fun aTodoTickedInTheScriptProcessReachesHome() {
        val id = todo("tick me from a run")
        val s = script("tick", "-- @uses todos\nlog('$mark ticked ' .. tostring(todos.setDone($id, true)))")
        assertTrue(s.grants.allows("todos"))
        val before = todos.changes.value
        ScriptScheduler.runNow(context, s)
        assertEquals("$mark ticked true", lineOf(s, "$mark ticked"))
        assertTrue(todos.all().first { it.id == id }.done)
        waitUntil("the todo store in the main process did not hear of the change") { todos.changes.value > before }
    }

    /** A real run with the todos switch off ticks nothing. */
    @Test fun aRunWithTodosOffTicksNothing() {
        val id = todo("stays open in a run")
        val s = script("off", "-- @uses todos\nlog('$mark ticked ' .. tostring(todos.setDone($id, true)) .. ' of ' .. #todos.list())")
        scripts.setAccess(s.id, "todos", false)
        ScriptScheduler.runNow(context, scripts.byId(s.id)!!)
        assertEquals("$mark ticked false of 0", lineOf(s, "$mark ticked"))
        assertFalse(todos.all().first { it.id == id }.done)
    }

    // ---- feeds ----------------------------------------------------------------------------

    /** Unread items only, with the feed's name and the fields of an item of rss.fetch. Nothing is marked read. */
    @Test fun aScriptReadsTheUnreadItemsOfYourFeeds() {
        val feed = feed()
        val code = "feeds.unread(50).filter(i => i.feed === 'Feed $mark').map(i => [i.id, i.title, i.link, i.date, i.summary].join('|')).join(';')"
        assertEquals("new-$mark|New $mark|https://example.com/new|Sat, 14 Nov 2026 10:00:00 GMT|A summary", js(code))
        assertEquals("true", lua("local i = feeds.unread(1)[1]; return i ~= nil and i.feed ~= nil and i.id ~= nil"))
        assertEquals(1, feeds.unreadCount(feed.id))
        // The feed's address is in no field.
        assertEquals("false", js("JSON.stringify(feeds.unread(50)).includes('$mark.xml')"))
    }

    /** A real run reads the feed database from the `:script` process, where it was never opened before. */
    @Test fun theScriptProcessReadsTheFeeds() {
        feed()
        val code = """
            -- @uses feeds
            local out = {}
            for _, i in ipairs(feeds.unread(50)) do
              if i.feed == "Feed $mark" then out[#out + 1] = i.id .. "/" .. i.title .. "/" .. tostring(i.summary) end
            end
            log("$mark unread: " .. table.concat(out, ";"))
        """.trimIndent()
        val s = script("feeds", code)
        assertTrue(s.grants.allows("feeds"))
        // It makes no request, so it asks for no internet access.
        assertEquals(listOf("feeds"), ScriptAccess.items(s.code))
        ScriptScheduler.runNow(context, s)
        assertEquals("$mark unread: new-$mark/New $mark/A summary", lineOf(s, "$mark unread:"))
        // The main process still reads and writes its database after that.
        val feedId = madeFeeds.single()
        feeds.markAllRead(feedId)
        assertEquals(0, feeds.unreadCount(feedId))
    }

    // ---- calendar -------------------------------------------------------------------------

    /** Without Android's calendar permission the list is empty: no error, and no question to you. */
    @Test fun withoutTheCalendarPermissionTheListIsEmpty() {
        assumeFalse("Kolahal has the calendar permission on this device", Agenda.hasPermission(context))
        assertEquals("0", lua("#calendar.events()"))
        assertEquals("0", lua("#calendar.events(30)"))
        assertEquals("[]", js("JSON.stringify(calendar.events(7))"))
    }

    /** With or without the permission, the call gives a list, and every event has what an agenda widget takes. */
    @Test fun theCalendarGivesAListOfEvents() {
        val ok = """
            local list = calendar.events(30)
            for _, e in ipairs(list) do
              if type(e.title) ~= "string" or math.type(e.starts) ~= "integer" or math.type(e.ends) ~= "integer" or e.ends < e.starts or type(e.allDay) ~= "boolean" then
                return "bad event"
              end
            end
            return #list <= 100
        """.trimIndent()
        assertEquals("true", lua(ok))
        assertNotNull(js("calendar.events().length"))
        // As JSON: a number that JavaScript can't write (a BigInt) would throw here.
        assertTrue(js("JSON.stringify(calendar.events(30))").orEmpty().startsWith("["))
    }

    private companion object {
        /** Every script of this class has a name that starts so. */
        const val SCRIPTS = "sltest"

        /** The address of a feed that [feed] made, in this run or an earlier one. */
        val TEST_FEED = Regex("""https://example\.com/sl\d+\.xml""")
    }
}
