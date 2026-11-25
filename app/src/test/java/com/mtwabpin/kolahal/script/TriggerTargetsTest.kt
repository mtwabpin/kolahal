package com.mtwabpin.kolahal.script

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which scripts an event runs ([TriggerDispatcher.targets]): a notification starts a script whose
 * `@on notification` names apps only when it comes from one of them. The others queue no run.
 */
class TriggerTargetsTest {
    private fun sub(id: Long, code: String, values: Map<String, Boolean> = mapOf("notifications" to true)) =
        TriggerDispatcher.Subscription.of(id, code, ScriptAccess.grants(code, values))

    private val subs = listOf(
        sub(1, "-- @on notification com.example.chat\n-- @uses notifications"),
        sub(2, "-- @on notification\n-- @uses notifications"),
        sub(3, "// @on notification com.example.chat org.example.mail, unlock\n// @uses notifications"),
        sub(4, "-- @on unlock"),
    )

    @Test
    fun aNotificationRunsTheScriptsForItsApp() {
        assertEquals(listOf(1L, 2L, 3L), TriggerDispatcher.targets(subs, "notification", "com.example.chat"))
        assertEquals(listOf(2L, 3L), TriggerDispatcher.targets(subs, "notification", "org.example.mail"))
    }

    @Test
    fun anotherAppRunsOnlyTheScriptsForEveryApp() {
        assertEquals(listOf(2L), TriggerDispatcher.targets(subs, "notification", "net.example.other"))
        assertEquals(listOf(2L), TriggerDispatcher.targets(subs, "notification", null))
    }

    /** The names concern notifications only: another event runs the script as before. */
    @Test
    fun otherEventsIgnoreTheNames() {
        assertEquals(listOf(3L, 4L), TriggerDispatcher.targets(subs, "unlock", null))
    }

    /** Naming its app changes nothing about access: without notifications allowed, no notification runs it. */
    @Test
    fun itStillNeedsNotificationsAllowed() {
        val code = "-- @on notification com.example.chat\n-- @uses notifications"
        val denied = sub(5, code, values = emptyMap())
        assertEquals(emptyList<Long>(), TriggerDispatcher.targets(listOf(denied), "notification", "com.example.chat"))
        val off = sub(6, code, values = mapOf("notifications" to true, "notification" to false))
        assertEquals(emptyList<Long>(), TriggerDispatcher.targets(listOf(off), "notification", "com.example.chat"))
    }

    /** A name that isn't a package matches no app: the script runs for none until it is fixed. */
    @Test
    fun aBadNameMatchesNoApp() {
        val bad = sub(7, "-- @on notification com.example/chat\n-- @uses notifications")
        assertEquals(emptyList<Long>(), TriggerDispatcher.targets(listOf(bad), "notification", "com.example/chat"))
    }
}
