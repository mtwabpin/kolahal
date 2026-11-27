package com.mtwabpin.kolahal.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation the page shows: while a question waits no other one is started or opened, deleting
 * it drops the waiting answer, and the renderer's restarts can't go on for ever.
 */
class OpenConversationTest {
    private fun earlier(id: String = "100") = Conversation(
        id, 100L, 200L, "anthropic", "m",
        listOf(Entry(Kind.QUESTION, "Earlier question"), Entry(Kind.PLAIN, "Earlier answer")),
    )

    private fun question(text: String = "Now") = Entry(Kind.QUESTION, text)

    /** An earlier conversation isn't opened while a question waits: the answer would be kept under its id, with both mixed. */
    @Test
    fun nothingElseOpensWhileAQuestionWaits() {
        val talk = OpenConversation()
        val ticket = talk.ask(question())!!
        assertTrue(talk.busy)
        assertFalse(talk.open(earlier()))
        assertFalse(talk.clear())
        assertNull("a second question while one waits", talk.ask(question("Again")))
        assertNull(talk.id)
        assertEquals(listOf("Now"), talk.entries.map { it.text })

        assertTrue(talk.answered(ticket, Entry(Kind.PLAIN, "Answer")))
        talk.finished(ticket)
        assertFalse(talk.busy)
        val kept = talk.kept("anthropic", "m", 500L)
        assertEquals("500", kept.id)
        assertEquals(listOf("Now", "Answer"), kept.entries.map { it.text })

        // Once answered, an earlier one opens, and goes on under its own id.
        assertTrue(talk.open(earlier()))
        assertEquals("100", talk.id)
        assertEquals(listOf("Earlier question", "Earlier answer"), talk.entries.map { it.text })
    }

    /** The open conversation deleted while a question waits: a new one starts, and the answer isn't taken or kept. */
    @Test
    fun deletingTheOpenOneDropsTheWaitingAnswer() {
        val talk = OpenConversation()
        assertTrue(talk.open(earlier()))
        val ticket = talk.ask(question())!!
        assertFalse("another conversation deleted changes nothing", talk.deleted("999"))
        assertTrue(talk.isCurrent(ticket))

        assertTrue(talk.deleted("100"))
        assertNull(talk.id)
        assertFalse(talk.busy)
        assertTrue(talk.entries.isEmpty())
        assertFalse(talk.isCurrent(ticket))
        assertFalse(talk.answered(ticket, Entry(Kind.PLAIN, "Late answer")))
        assertFalse(talk.failed(ticket))
        talk.finished(ticket)
        assertTrue(talk.entries.isEmpty())

        // A question asked after it is a conversation of its own.
        val next = talk.ask(question("Fresh"))!!
        assertTrue(talk.answered(next, Entry(Kind.PLAIN, "Fresh answer")))
        assertEquals(listOf("Fresh", "Fresh answer"), talk.kept("anthropic", "m", 700L).entries.map { it.text })
    }

    /** Every conversation deleted (in Settings): the one shown, kept before, starts again; one never kept stays. */
    @Test
    fun deletingAllForgetsAKeptOne() {
        val fresh = OpenConversation()
        fresh.ask(question())
        assertFalse(fresh.deletedAll())
        assertEquals(1, fresh.entries.size)

        val talk = OpenConversation()
        talk.open(earlier())
        val ticket = talk.ask(question())!!
        assertTrue(talk.deletedAll())
        assertNull(talk.id)
        assertFalse(talk.answered(ticket, Entry(Kind.PLAIN, "Late answer")))
    }

    /** A question with no answer isn't part of the conversation; the next one goes without it. */
    @Test
    fun aFailedQuestionIsTakenOut() {
        val talk = OpenConversation()
        val ticket = talk.ask(question("Lost"))!!
        assertTrue(talk.failed(ticket))
        talk.finished(ticket)
        assertFalse(talk.busy)
        assertTrue(talk.entries.isEmpty())
        talk.ask(question("Second"))
        assertEquals(listOf("Second"), talk.turns().map { it.text })
    }

    /** What the renderer said goes once, before the next question. */
    @Test
    fun notesGoOnceWithTheNextTurn() {
        val talk = OpenConversation()
        assertNull(talk.takeNotes())
        talk.note("""{"error":1}""")
        talk.note("""{"error":2}""")
        assertEquals("{\"error\":1}\n{\"error\":2}", talk.takeNotes())
        assertNull(talk.takeNotes())
        talk.note("x")
        talk.clear()
        assertNull("a new conversation starts without them", talk.takeNotes())
    }

    /** After the renderer stops, the newest answers are left out, more each time, and after a few times none are drawn again. */
    @Test
    fun restartsCantLoop() {
        val talk = OpenConversation()
        for (i in 1..5) {
            val t = talk.ask(question("q$i"))!!
            talk.answered(t, Entry(Kind.RICH, "r$i"))
            talk.finished(t)
        }
        assertEquals(listOf("r1", "r2", "r3", "r4"), talk.redraw().map { it.text })
        assertEquals(listOf("r1", "r2", "r3"), talk.redraw().map { it.text })
        assertEquals(listOf("r1", "r2"), talk.redraw().map { it.text })
        repeat(10) { assertTrue(talk.redraw().isEmpty()) }

        // A new answer: the count starts again, and the new one is the first left out.
        val t = talk.ask(question("q6"))!!
        talk.answered(t, Entry(Kind.RICH, "r6"))
        talk.finished(t)
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), talk.redraw().map { it.text })
        var times = 1
        while (talk.redraw().isNotEmpty()) times++
        assertTrue(times <= OpenConversation.MAX_REDRAWS)
    }
}
