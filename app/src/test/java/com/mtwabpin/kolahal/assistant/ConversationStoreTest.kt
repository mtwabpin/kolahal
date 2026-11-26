package com.mtwabpin.kolahal.assistant

import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.Stores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conversations kept on the phone: the newest fifty, each one whole, deleted one by one or all at once. */
class ConversationStoreTest {
    private class MapShelf : Shelf {
        val rows = LinkedHashMap<String, String>()
        override fun get(key: String) = rows[key]
        override fun set(key: String, value: String?) {
            if (value == null) rows.remove(key) else rows[key] = value
        }
        override fun keys() = rows.keys.toSet()
    }

    private fun conversation(id: Int, updated: Long = id.toLong(), question: String = "Question $id") = Conversation(
        id.toString(), id.toLong(), updated, "anthropic", "claude-sonnet-5-5",
        listOf(Entry(Kind.QUESTION, question, notes = "renderer note"), Entry(Kind.RICH, "[{\"version\":\"v0.9\"}]"), Entry(Kind.ACTION, "{\"action\":{}}"), Entry(Kind.PLAIN, "Fine")),
    )

    @Test
    fun aConversationComesBackWhole() {
        val store = ConversationStore(MapShelf())
        store.save(conversation(7, question = "Plan a trip\nwith details"))
        val back = store.load("7")!!
        assertEquals(listOf(Kind.QUESTION, Kind.RICH, Kind.ACTION, Kind.PLAIN), back.entries.map { it.kind })
        assertEquals("renderer note", back.entries[0].notes)
        // The model gets the note before the question; the page shows the question alone.
        assertEquals("renderer note\nPlan a trip\nwith details", back.entries[0].turn.text)
        assertEquals(Role.USER, back.entries[2].turn.role)
        assertEquals(Role.ASSISTANT, back.entries[3].turn.role)
        assertEquals("anthropic", back.service)
        assertEquals(listOf(Summary("7", "Plan a trip", 7, 7, "anthropic", "claude-sonnet-5-5")), store.list())
    }

    @Test
    fun theNewestFiftyAreKept() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        (1..55).forEach { store.save(conversation(it)) }
        val list = store.list()
        assertEquals(ConversationStore.CAP, list.size)
        assertEquals(50, ConversationStore.CAP)
        assertEquals((55 downTo 6).map { it.toString() }, list.map { it.id })
        assertNull(store.load("5"))
        // The rows of the ones dropped are gone too: fifty conversations and the index.
        assertEquals(51, shelf.rows.size)
        // One used again goes back to the top.
        store.save(conversation(6, updated = 100))
        assertEquals("6", store.list().first().id)
        assertEquals(50, store.list().size)
    }

    @Test
    fun deleteOneOrAll() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        (1..3).forEach { store.save(conversation(it)) }
        store.delete("2")
        assertEquals(listOf("3", "1"), store.list().map { it.id })
        assertNull(store.load("2"))
        assertEquals(2, store.deleteAll())
        assertEquals(emptyList<Summary>(), store.list())
        assertTrue(shelf.rows.isEmpty())
        assertEquals(0, store.deleteAll())
    }

    /** A conversation too big for one row loses its oldest turns, a question and its answer at a time. */
    @Test
    fun aHugeConversationKeepsItsNewestTurns() {
        val store = ConversationStore(MapShelf())
        val big = "x".repeat(KvStore.MAX_VALUE / 3)
        val entries = (1..4).flatMap { listOf(Entry(Kind.QUESTION, "q$it"), Entry(Kind.PLAIN, big)) }
        store.save(Conversation("9", 1, 2, "openai", "gpt-6-luna", entries))
        val back = store.load("9")!!
        assertTrue(back.entries.size < entries.size)
        assertEquals(Kind.QUESTION, back.entries.first().kind)
        assertEquals("q4", back.entries[back.entries.size - 2].text)
    }

    /** A row that can't be read is skipped, not a crash; an unknown kind is left out. */
    @Test
    fun aDamagedRowIsSkipped() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        shelf.rows["index"] = "not json"
        shelf.rows["c:1"] = "{"
        assertEquals(emptyList<Summary>(), store.list())
        assertNull(store.load("1"))
        val odd = Conversation.fromJson("""{"id":"2","started":1,"updated":1,"service":"google","model":"m","entries":[{"kind":"LATER","text":"?"},{"kind":"QUESTION","text":"hi"}]}""")!!
        assertEquals(listOf(Kind.QUESTION), odd.entries.map { it.kind })
    }

    /** Kept on this phone only: a scope of its own, never in a backup or the cloud. */
    @Test
    fun theyStayOnThisPhone() {
        assertTrue(Stores.ASSISTANT in Stores.THIS_PHONE)
        assertTrue(Stores.ASSISTANT in Stores.SCOPES)
    }
}
