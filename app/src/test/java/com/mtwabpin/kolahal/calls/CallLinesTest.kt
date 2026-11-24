package com.mtwabpin.kolahal.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One line in the log for each call, however many rows the phone's call log has for it ([CallLines]). */
class CallLinesTest {
    private fun row(id: Long, number: String = "555 0100", date: Long = 1_000L, duration: Long = 0) =
        CallEvents.Call(id, number, name = null, type = 2, date = date, durationSec = duration, presentation = 1, lookupUri = null)

    /** New calls each get a line, oldest first, and the next look starts after the highest id. */
    @Test
    fun newCallsGetALineEach() {
        val plan = CallLines.plan(listOf(row(12, date = 3_000), row(11, date = 2_000), row(10, date = 1_000)), emptySet())
        assertEquals(listOf(10L, 11L, 12L), plan.fresh.map { it.id })
        assertTrue(plan.again.isEmpty())
        assertEquals(12L, plan.last)
    }

    /**
     * A phone that writes a call when it starts and again when it ends, under a new id: the second
     * row is the same call, so it changes the call's line and adds none.
     */
    @Test
    fun aCallWrittenAgainKeepsItsOneLine() {
        val started = row(20, duration = 0)
        val first = CallLines.plan(listOf(started), emptySet())
        assertEquals(listOf(20L), first.fresh.map { it.id })
        val logged = setOf(CallLines.key(started))
        val ended = CallLines.plan(listOf(row(21, duration = 95)), logged)
        assertTrue(ended.fresh.isEmpty())
        assertEquals(listOf(95L), ended.again.map { it.durationSec })
        assertEquals(21L, ended.last)
    }

    /** Several rows for one call in one look: one line, with what the newest row says. */
    @Test
    fun rowsOfOneCallInOneLookAreOneLine() {
        val plan = CallLines.plan(listOf(row(30, duration = 0), row(32, duration = 40), row(31, duration = 10)), emptySet())
        assertEquals(listOf(32L), plan.fresh.map { it.id })
        assertEquals(40L, plan.fresh.single().durationSec)
        assertEquals(32L, plan.last)
    }

    /** Two calls with one person are two calls: they began at different times. And two people at one time are two. */
    @Test
    fun anotherCallIsAnotherLine() {
        val logged = setOf(CallLines.key(row(40, date = 1_000)))
        val plan = CallLines.plan(listOf(row(41, date = 61_000), row(42, number = "555 0199", date = 1_000)), logged)
        assertEquals(listOf(41L, 42L), plan.fresh.map { it.id })
        assertTrue(plan.again.isEmpty())
    }

    /** The mark is the highest id, also when the rows come newest call first and an older call has the newer row. */
    @Test
    fun theMarkIsTheHighestIdInAnyOrder() {
        val plan = CallLines.plan(listOf(row(50, date = 9_000), row(53, date = 1_000), row(51, date = 5_000)), emptySet())
        assertEquals(53L, plan.last)
        assertEquals(listOf(50L, 51L, 53L), plan.fresh.map { it.id })
    }

    /** What is kept is when a call began and a hash, never the number; only the newest calls are kept, and it reads back. */
    @Test
    fun theKeptCallsHoldNoNumber() {
        val call = row(60, number = "+1 555 0100", date = 123_456L)
        assertFalse("555" in CallLines.key(call))
        assertTrue(CallLines.key(call).startsWith("123456|"))
        val lines = LinkedHashMap<String, Long>()
        for (i in 1..CallLines.KEPT + 5) lines[CallLines.key(row(i.toLong(), date = i * 1_000L))] = 1_000L + i
        val kept = CallLines.decode(CallLines.encode(lines))
        assertEquals(CallLines.KEPT, kept.size)
        assertEquals(lines.entries.toList().takeLast(CallLines.KEPT).map { it.key to it.value }, kept.entries.map { it.key to it.value })
        assertTrue(CallLines.decode(null).isEmpty())
        assertTrue(CallLines.decode("broken line\nno-id\t").isEmpty())
    }
}
