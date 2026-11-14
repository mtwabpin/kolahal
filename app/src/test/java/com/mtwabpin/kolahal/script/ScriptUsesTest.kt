package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `@uses` items ([ScriptMeta.USES]), and the five that let a script read your own lists:
 * `todos`, `notes`, `reminders`, `calendar` and `feeds`.
 * - An item is off until you allow it.
 * - `script get` names it before the script is switched on.
 * - Every item has its words for the access and install dialogs, and its calls for the editor.
 */
class ScriptUsesTest {
    private val lists = listOf("todos", "notes", "reminders", "calendar", "feeds")

    /** A script that declares every list, and calls each one. */
    private val code = """
        -- @uses todos, notes, reminders
        -- @uses calendar feeds
        local open = todos.list()
        todos.setDone(open[1].id, true)
        log(#notes.list(), #reminders.list(), #calendar.events(7), #feeds.unread())
    """.trimIndent()

    @Test fun theListsComeAfterTheOlderItems() {
        assertEquals(listOf("notifications", "clipboard", "apps", "commands", "dnd", "work") + lists, ScriptMeta.USES)
    }

    @Test fun aUsesLineNamesThem() {
        assertEquals(lists.toSet(), ScriptMeta.uses(code))
        assertEquals(setOf("todos"), ScriptMeta.uses("// @uses Todos, todo, note\n"))
        assertTrue(ScriptMeta.uses("log(todos.list())").isEmpty())
    }

    /** Declared is not allowed: each item is off until you switch it on, and one that is not declared can't be on. */
    @Test fun eachItemIsOffUntilAllowed() {
        for (item in ScriptMeta.USES) {
            assertFalse("$item, not decided", ScriptAccess.grants("-- @uses $item\n", emptyMap()).allows(item))
            assertFalse("$item, switched off", ScriptAccess.grants("-- @uses $item\n", mapOf(item to false)).allows(item))
            assertTrue("$item, allowed", ScriptAccess.grants("-- @uses $item\n", mapOf(item to true)).allows(item))
            // A stored "on" for an item the code no longer declares gives nothing.
            assertFalse("$item, not declared", ScriptAccess.grants("log(1)", mapOf(item to true)).allows(item))
        }
        val undecided = ScriptAccess.grants(code, emptyMap())
        assertEquals(lists, undecided.undecided)
        assertTrue(lists.none(undecided::allows))
        // One switch allows one item, and no other.
        val one = ScriptAccess.grants(code, mapOf("notes" to true))
        assertEquals(listOf("notes"), lists.filter(one::allows))
    }

    /**
     * `script get` switches a shared script on only when it asks for nothing ([ScriptAccess.asksFor]).
     * A script that reads a list asks for it, so it arrives switched off, and the list is named.
     */
    @Test fun aSharedScriptThatReadsAListArrivesSwitchedOff() {
        for (item in lists) assertEquals(listOf(item), ScriptAccess.asksFor("-- @uses $item\nlog(1)"))
        assertEquals(lists, ScriptAccess.asksFor(code))
        assertEquals(listOf("internet", "calendar"), ScriptAccess.asksFor("// @uses calendar\nawait http.get(u)"))
        // The call without its line asks for nothing, and gives nothing: the run stops with an error.
        assertTrue(ScriptAccess.asksFor("log(#todos.list())").isEmpty())
    }

    /** Reading a list is no use of the internet, the header or a widget. */
    @Test fun aListIsNoOtherItem() {
        assertEquals(lists, ScriptAccess.items(code))
        assertEquals(lists, ScriptAccess.switchable(code))
        assertFalse(ScriptMeta.usesInternet(code))
        assertFalse(ScriptMeta.setsStatus(code))
        assertFalse(ScriptMeta.postsNotifications(code))
    }

    /** The access switches and the install dialog show an item by its label and help. An event's words would be wrong. */
    @Test fun everyItemHasItsOwnWords() {
        for (item in ScriptMeta.USES) {
            assertFalse("$item has no label", ScriptAccess.label(item).startsWith("Runs on"))
            assertFalse("$item has no help", ScriptAccess.help(item).startsWith("Runs when"))
            assertTrue("$item: the help says what off means", "; when off, " in ScriptAccess.help(item))
        }
        assertEquals(
            listOf("Your to-dos", "Your notes", "Your reminders", "Your calendar", "Your feeds"),
            lists.map(ScriptAccess::label),
        )
        // `@uses todos` covers reading and ticking, so its help says both.
        assertTrue(ScriptAccess.help("todos"), "marks to-dos done or open" in ScriptAccess.help("todos"))
        // No item has the name of an event, or of an item Kolahal reads from the code.
        val others = ScriptMeta.EVENTS + listOf(ScriptAccess.INTERNET, ScriptAccess.HEADER, ScriptAccess.WIDGET, "notify")
        assertEquals(emptyList<String>(), ScriptMeta.USES.filter { it in others })
        assertEquals(ScriptMeta.USES.size, ScriptMeta.USES.toSet().size)
    }

    /** The editor knows the calls of every item, so it can say which `@uses` line is missing. */
    @Test fun theEditorKnowsEachItemsCalls() {
        assertEquals(ScriptMeta.USES.toSet(), ScriptCheck.NEEDS.keys)
        assertTrue(ScriptCheck.check(code, ScriptLang.LUA).none { "@uses" in it.text })
        val calls = mapOf(
            "todos.list()" to "todos", "todos.setDone(3, true)" to "todos", "notes.list(5)" to "notes",
            "reminders.list()" to "reminders", "calendar.events()" to "calendar", "feeds.unread(20)" to "feeds",
        )
        for ((call, item) in calls) {
            val name = call.substringBefore('(')
            assertEquals(listOf("$name needs a `-- @uses $item` line"), ScriptCheck.check("local x = $call", ScriptLang.LUA).map { it.text })
            assertEquals(listOf("$name needs a `// @uses $item` line"), ScriptCheck.check("const x = $call;", ScriptLang.JS).map { it.text })
            assertTrue(call, ScriptCheck.check("-- @uses $item\nlocal x = $call", ScriptLang.LUA).none { "@uses" in it.text })
        }
    }

    /** A table of the script's own with one of these names is not the API's: only the calls themselves count. */
    @Test fun aScriptsOwnTableIsNotACall() {
        val own = "local notes = { count = 2 }\nlog(notes.count)\nlocal feeds = {}\nfeeds.total = 1\nlog(self.todos.list, my_calendar.events)"
        assertTrue(ScriptCheck.check(own, ScriptLang.LUA).none { "@uses" in it.text })
    }
}
