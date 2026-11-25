package com.mtwabpin.kolahal.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The internet together with private data ([ScriptAccess.privateWithInternet]): which accesses are
 * private, which pairs warn, and the words of the warning.
 * - Every access is private or not, with why, in one of two lists beside the accesses. A new one in
 *   neither fails here.
 * - What counts is what is switched on (the grants), not what the code declares.
 * - The internet is the access itself: no address is looked at.
 */
class ScriptPrivacyTest {
    /** Every access a script can have: what `@uses` names, what Kolahal reads from the code, posting notifications and the events. */
    private val accesses =
        ScriptMeta.USES + listOf(ScriptAccess.INTERNET, ScriptAccess.HEADER, ScriptAccess.WIDGET, "notify") + ScriptMeta.EVENTS

    /** Code that declares [uses] and makes a request, so it has the internet unless switched off. */
    private fun online(vararg uses: String) =
        (if (uses.isEmpty()) "" else "-- @uses ${uses.joinToString(", ")}\n") + "local r = http.get(\"https://example.com\")"

    private fun pair(code: String, values: Map<String, Boolean>) =
        ScriptAccess.privateWithInternet(ScriptAccess.grants(code, values))

    @Test
    fun everyAccessIsPrivateOrNot() {
        val private = ScriptAccess.PRIVATE.keys
        val not = ScriptAccess.NOT_PRIVATE.keys
        assertEquals("in neither list", emptyList<String>(), accesses.filter { it !in private && it !in not })
        assertEquals("in both lists", emptyList<String>(), accesses.filter { it in private && it in not })
        // Nothing listed that isn't an access: a name that went stays in neither.
        assertEquals("not an access", emptyList<String>(), (private + not).filter { it !in accesses })
        // Each says why, and the warning has words for each private one.
        for ((item, reads) in ScriptAccess.PRIVATE) {
            assertTrue(item, reads.what.isNotBlank() && reads.why.isNotBlank())
        }
        for ((item, why) in ScriptAccess.NOT_PRIVATE) assertTrue(item, why.isNotBlank())
    }

    /** What a script can read about you, and nothing that only acts or only shows. */
    @Test
    fun whatCountsAsPrivate() {
        assertEquals(
            listOf("notifications", "clipboard", "apps", "work", "todos", "notes", "reminders", "calendar", "feeds", "usage"),
            ScriptAccess.PRIVATE.keys.toList(),
        )
        for (item in listOf("commands", "dnd", "screen", "effects", "notification", ScriptAccess.INTERNET)) {
            assertFalse(item, item in ScriptAccess.PRIVATE)
        }
    }

    /** Each private item with the internet warns, named; the items that read nothing don't. */
    @Test
    fun eachPrivateItemWithTheInternetWarns() {
        for (item in ScriptAccess.PRIVATE.keys) {
            assertEquals(item, listOf(item), pair(online(item), mapOf(item to true)))
        }
        for (item in ScriptMeta.USES - ScriptAccess.PRIVATE.keys) {
            assertEquals(item, emptyList<String>(), pair(online(item), mapOf(item to true)))
        }
        // Several, in the list's order, and only those that are on.
        assertEquals(
            listOf("notifications", "notes", "usage"),
            pair(
                online("usage", "notes", "effects", "notifications", "calendar", "commands"),
                mapOf("usage" to true, "notes" to true, "effects" to true, "notifications" to true, "calendar" to false, "commands" to true),
            ),
        )
    }

    /** No internet, no warning: the code doesn't use it, or it is switched off. */
    @Test
    fun nothingWithoutTheInternet() {
        assertEquals(emptyList<String>(), pair("-- @uses notifications\nlog(#notifications.recent())", mapOf("notifications" to true)))
        assertEquals(
            emptyList<String>(),
            pair(online("notifications"), mapOf("notifications" to true, ScriptAccess.INTERNET to false))
        )
    }

    /** The internet alone, with nothing private on, warns of nothing. */
    @Test
    fun nothingWithTheInternetAlone() {
        assertEquals(emptyList<String>(), pair(online(), emptyMap()))
        assertTrue(ScriptAccess.grants(online(), emptyMap()).allows(ScriptAccess.INTERNET))
        // Pinned, notifying, an event: still nothing private.
        assertEquals(
            emptyList<String>(),
            pair("-- @on unlock, notification\n" + online() + "\nstatus('x')\nnotify('y')", mapOf(ScriptAccess.HEADER to true))
        )
    }

    /** What is switched on counts, not the header: declared and not yet allowed, or switched off, is no warning. */
    @Test
    fun theGrantsCountNotTheHeader() {
        val code = online("notifications", "clipboard")
        assertEquals(emptyList<String>(), pair(code, emptyMap()))
        assertEquals(listOf("clipboard"), pair(code, mapOf("notifications" to false, "clipboard" to true)))
        // A stored "on" for an item the code no longer declares gives nothing.
        assertEquals(emptyList<String>(), pair(online(), mapOf("notes" to true)))
    }

    /** What a dialog or a line is about to allow, with what the script has on already. */
    @Test
    fun itemsAboutToBeAllowed() {
        assertEquals(listOf("calendar"), ScriptAccess.privateWithInternet(listOf("calendar", ScriptAccess.INTERNET)))
        assertEquals(emptyList<String>(), ScriptAccess.privateWithInternet(listOf("calendar", "notes")))
        assertEquals(emptyList<String>(), ScriptAccess.privateWithInternet(listOf(ScriptAccess.INTERNET, "effects")))
    }

    /** One sentence says what the script can do, naming what it can read; one says what to do. */
    @Test
    fun theWarningNamesWhatItCanRead() {
        assertEquals(
            "This script can read your notifications and use the internet. Only allow both for a script you trust.",
            ScriptAccess.warning(listOf("notifications")),
        )
        assertEquals(
            "This script can read your clipboard and app usage, and use the internet. Only allow both for a script you trust.",
            ScriptAccess.warning(listOf("clipboard", "usage")),
        )
        assertEquals(
            "This script can read your notifications, notes and calendar, and use the internet. Only allow both for a script you trust.",
            ScriptAccess.warning(listOf("notifications", "notes", "calendar")),
        )
        assertNull(ScriptAccess.warning(emptyList()))
        // Each private item reads as a thing of yours.
        for (item in ScriptAccess.PRIVATE.keys) {
            assertTrue(item, ScriptAccess.warning(listOf(item))!!.startsWith("This script can read your ${ScriptAccess.PRIVATE.getValue(item).what} and use"))
        }
    }

    /** Under a log line, the same words as hints, one to a line, with no dot between sentences. */
    @Test
    fun theHintIsLinesUnderTheLine() {
        assertEquals(
            "\nIt can read your to-dos and use the internet\nOnly allow both for a script you trust",
            ScriptAccess.warningHint(listOf("todos")),
        )
        assertEquals("", ScriptAccess.warningHint(emptyList()))
        assertFalse(" · " in ScriptAccess.warningHint(ScriptAccess.PRIVATE.keys.toList()))
    }
}
