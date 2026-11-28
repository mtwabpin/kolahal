package com.mtwabpin.kolahal.assistant

import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.Stores
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The conversations kept on the phone: the newest fifty, each entry a value of its own, so a long
 * conversation is kept whole; deleted one by one or all at once, nothing left over.
 */
class ConversationStoreTest {
    /** A shelf in a map, as the phone's: values over [KvStore.MAX_VALUE] are refused. It counts the values written. */
    private class MapShelf : Shelf {
        val rows = LinkedHashMap<String, String>()
        var writes = 0

        /** Stops (the app killed) when [stopAt] says so, before the write. */
        var stopAt: ((String, String?) -> Boolean)? = null

        override fun get(key: String) = rows[key]
        override fun set(key: String, value: String?) {
            if (stopAt?.invoke(key, value) == true) throw Killed()
            require((value?.toByteArray()?.size ?: 0) <= KvStore.MAX_VALUE) { "value too large" }
            writes++
            if (value == null) rows.remove(key) else rows[key] = value
        }
        override fun keys() = rows.keys.toSet()
    }

    private class Killed : RuntimeException()

    private fun conversation(id: Int, updated: Long = id.toLong(), question: String = "Question $id") = Conversation(
        id.toString(), id.toLong(), updated, "anthropic", "claude-sonnet-5-5",
        listOf(Entry(Kind.QUESTION, question, notes = "renderer note"), Entry(Kind.RICH, "[{\"version\":\"v0.9\"}]"), Entry(Kind.ACTION, "{\"action\":{}}"), Entry(Kind.PLAIN, "Fine")),
    )

    private fun assertSame(want: Conversation, got: Conversation?) {
        got!!
        assertEquals(listOf(want.id, want.started, want.updated, want.service, want.model), listOf(got.id, got.started, got.updated, got.service, got.model))
        assertEquals(want.entries.size, got.entries.size)
        want.entries.zip(got.entries).forEach { (w, g) -> assertEquals(Triple(w.kind, w.text, w.notes), Triple(g.kind, g.text, g.notes)) }
    }

    /** The values of conversation [id]. */
    private fun MapShelf.of(id: String) = rows.keys.filter { it == "h:$id" || it == "c:$id" || it.startsWith("m:$id:") }

    /** An A2UI answer of about [kb] KB, full of quotes and braces, as rich answers are. */
    private fun richAnswer(n: Int, kb: Int) = JSONArray().apply {
        repeat(kb * 1024 / 60) { put(JSONObject().put("id", "c$n-$it").put("text", "Row $it of answer $n")) }
    }.toString()

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

    /** Each entry a value: two hundred entries with large drawn answers, together far over a value's limit, come back whole. */
    @Test
    fun aLongConversationIsKeptWhole() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        val entries = (1..100).flatMap { listOf(Entry(Kind.QUESTION, "q$it"), Entry(Kind.RICH, richAnswer(it, 300))) }
        val c = Conversation("9", 1, 2, "openai", "gpt-6-luna", entries)
        assertTrue(c.toJson().toByteArray().size > 20 * KvStore.MAX_VALUE)
        assertEquals(0, store.save(c))
        assertSame(c, store.load("9"))
        assertSame(c, ConversationStore(shelf).load("9"))
        // Its keys sort in its order.
        assertEquals((0 until 200).map { ConversationStore.entryKey("9", it) }, shelf.rows.keys.filter { it.startsWith("m:9:") }.sorted())
        assertEquals("m:9:000012", ConversationStore.entryKey("9", 12))
    }

    /** A save writes the entries not kept yet, the header and the index: not the whole conversation again. */
    @Test
    fun aSaveWritesOnlyWhatIsNew() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        val first = (1..50).flatMap { listOf(Entry(Kind.QUESTION, "q$it"), Entry(Kind.RICH, richAnswer(it, 20))) }
        store.save(Conversation("5", 1, 2, "google", "m", first))
        assertEquals(100 + 2, shelf.writes)

        shelf.writes = 0
        val more = first + Entry(Kind.QUESTION, "and one more", notes = "note") + Entry(Kind.PLAIN, "Sure")
        store.save(Conversation("5", 1, 3, "google", "m", more))
        assertEquals(2 + 2, shelf.writes)
        assertSame(Conversation("5", 1, 3, "google", "m", more), store.load("5"))

        // Opened again from the store (other String instances, same texts): still only what is new.
        shelf.writes = 0
        val reopened = ConversationStore(shelf).load("5")!!
        store.save(Conversation("5", 1, 4, "google", "m", reopened.entries + Entry(Kind.ACTION, "{\"action\":{}}") + Entry(Kind.PLAIN, "Done")))
        assertEquals(2 + 2, shelf.writes)
        assertEquals(104, store.load("5")!!.entries.size)
    }

    /** When the entries kept aren't the start of those given (one changed, or fewer), it is written again whole. */
    @Test
    fun aChangedConversationIsWrittenAgain() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        val c = conversation(3)
        store.save(c)
        val changed = Conversation("3", 3, 4, c.service, c.model, c.entries.toMutableList().also { it[1] = Entry(Kind.RICH, "[{\"version\":\"v1.0\"}]") } + Entry(Kind.QUESTION, "next") + Entry(Kind.PLAIN, "ok"))
        store.save(changed)
        assertSame(changed, store.load("3"))
        val fewer = Conversation("3", 3, 5, c.service, c.model, c.entries.take(2))
        store.save(fewer)
        assertSame(fewer, store.load("3"))
        assertEquals(listOf("h:3", "m:3:000000", "m:3:000001"), shelf.of("3").sorted())
    }

    /**
     * An entry over a value's limit is kept as a note that says so, on the same side, and the save says
     * how many there were; the conversation goes on.
     */
    @Test
    fun anEntryTooLargeIsKeptAsANote() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        val huge = "\"".repeat(KvStore.MAX_VALUE / 2 + 10)
        val entries = listOf(Entry(Kind.QUESTION, "draw it"), Entry(Kind.RICH, huge), Entry(Kind.QUESTION, huge, notes = "n"), Entry(Kind.PLAIN, "fine"))
        assertEquals(2, store.save(Conversation("4", 1, 2, "anthropic", "m", entries)))
        val back = store.load("4")!!
        assertEquals(listOf(Kind.QUESTION, Kind.PLAIN, Kind.QUESTION, Kind.PLAIN), back.entries.map { it.kind })
        assertEquals("This answer was too large to keep (1.0 MB, over the 1 MB limit)", back.entries[1].text)
        assertTrue(back.entries[2].text.startsWith("This question was too large to keep"))
        assertNull(back.entries[2].notes)
        assertEquals("fine", back.entries[3].text)
        assertEquals(Role.USER, ConversationStore.tooLarge(Entry(Kind.ACTION, huge), 2 * KvStore.MAX_VALUE).turn.role)

        // Saved again with more: the note isn't counted again, and only what is new is written.
        shelf.writes = 0
        assertEquals(0, store.save(Conversation("4", 1, 3, "anthropic", "m", entries + Entry(Kind.QUESTION, "more") + Entry(Kind.PLAIN, "yes"))))
        assertEquals(4, shelf.writes)
        assertEquals(6, store.load("4")!!.entries.size)
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
        // Every value of the ones dropped is gone: fifty conversations of a header and four entries, and the index.
        (1..5).forEach { assertEquals(emptyList<String>(), shelf.of("$it")) }
        assertEquals(50 * 5 + 1, shelf.rows.size)
        // One used again goes back to the top.
        store.save(conversation(6, updated = 100))
        assertEquals("6", store.list().first().id)
        assertEquals(50, store.list().size)
        // One older than all fifty isn't kept at all.
        store.save(conversation(1, updated = 0))
        assertEquals(emptyList<String>(), shelf.of("1"))
        assertEquals(50 * 5 + 1, shelf.rows.size)
    }

    @Test
    fun deleteOneOrAll() {
        val shelf = MapShelf()
        val store = ConversationStore(shelf)
        (1..3).forEach { store.save(conversation(it)) }
        store.delete("2")
        assertEquals(listOf("3", "1"), store.list().map { it.id })
        assertNull(store.load("2"))
        assertEquals(emptyList<String>(), shelf.of("2"))
        shelf.rows["c:8"] = conversation(8).toJson()
        assertEquals(2, store.deleteAll())
        assertEquals(emptyList<Summary>(), store.list())
        assertTrue(shelf.rows.isEmpty())
        assertEquals(0, store.deleteAll())
    }

    /** Kept the old way, one value each: moved on first use, the old value removed, the same conversation exactly. */
    @Test
    fun anOldConversationIsMoved() {
        val shelf = MapShelf()
        val old = oldStore(shelf)
        val store = ConversationStore(shelf)
        assertEquals(listOf("2", "1"), store.list().map { it.id })
        assertSame(Conversation.fromJson(old.getValue("2"))!!, store.load("2"))
        assertSame(Conversation.fromJson(old.getValue("1"))!!, store.load("1"))
        // What the old load gave: the unknown kind left out, an empty note as none.
        assertEquals(listOf(Kind.QUESTION, Kind.PLAIN), store.load("2")!!.entries.map { it.kind })
        assertNull(store.load("2")!!.entries[0].notes)
        assertFalse(shelf.rows.keys.any { it.startsWith("c:") })
        assertEquals(listOf("h:2", "m:2:000000", "m:2:000001"), shelf.of("2").sorted())
        // Moved once: a store opened later writes nothing.
        shelf.writes = 0
        ConversationStore(shelf).list()
        assertEquals(0, shelf.writes)
        // It goes on as any other.
        shelf.writes = 0
        val c = store.load("1")!!
        store.save(Conversation(c.id, c.started, 9, c.service, c.model, c.entries + Entry(Kind.QUESTION, "again") + Entry(Kind.PLAIN, "ok")))
        assertEquals(4, shelf.writes)
    }

    /** The app killed while moving: the old value stays until the new ones are written, and the move is done whole the next time. */
    @Test
    fun aMoveStoppedMidwayIsDoneNextTime() {
        for (stop in listOf<(String, String?) -> Boolean>(
            { key, _ -> key == "m:2:000001" },
            { key, _ -> key == "h:2" },
            { key, value -> key == "c:2" && value == null },
            { key, value -> key == "c:1" && value == null },
        )) {
            val shelf = MapShelf()
            val old = oldStore(shelf)
            shelf.stopAt = stop
            try {
                ConversationStore(shelf).list()
                fail("not stopped")
            } catch (_: Killed) {
            }
            assertTrue(shelf.rows.keys.any { it.startsWith("c:") })
            shelf.stopAt = null
            val store = ConversationStore(shelf)
            assertSame(Conversation.fromJson(old.getValue("2"))!!, store.load("2"))
            assertSame(Conversation.fromJson(old.getValue("1"))!!, store.load("1"))
            assertFalse(shelf.rows.keys.any { it.startsWith("c:") })
            assertEquals(listOf("h:2", "m:2:000000", "m:2:000001"), shelf.of("2").sorted())
        }
    }

    /** Values the index doesn't account for are removed on first use; a conversation listed without a header leaves the list. */
    @Test
    fun leftOverValuesGo() {
        val shelf = MapShelf()
        ConversationStore(shelf).apply {
            save(conversation(1))
            save(conversation(2))
        }
        shelf.rows["h:9"] = "{}"
        shelf.rows["m:9:000000"] = "{}"
        shelf.rows["m:1:000004"] = "{}"
        shelf.rows["m:1:x"] = "{}"
        shelf.rows["c:7"] = conversation(7).toJson()
        shelf.rows.remove("h:2")
        val store = ConversationStore(shelf)
        assertEquals(listOf("1"), store.list().map { it.id })
        assertEquals(listOf("h:1", "index") + (0..3).map { ConversationStore.entryKey("1", it) }, shelf.rows.keys.sorted())
        assertSame(conversation(1), store.load("1"))
    }

    /** A value that can't be read is skipped, not a crash; an unknown kind is left out. */
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

        val other = MapShelf()
        ConversationStore(other).save(conversation(3))
        other.rows["m:3:000001"] = "{"
        assertEquals(listOf(Kind.QUESTION, Kind.ACTION, Kind.PLAIN), ConversationStore(other).load("3")!!.entries.map { it.kind })
    }

    /** Kept on this phone only: a scope of its own, never in a backup or the cloud. */
    @Test
    fun theyStayOnThisPhone() {
        assertTrue(Stores.ASSISTANT in Stores.THIS_PHONE)
        assertTrue(Stores.ASSISTANT in Stores.SCOPES)
    }

    /** Two conversations kept the way 0.5.22 kept them: `c:<id>` and the index. Returns the old values by id. */
    private fun oldStore(shelf: MapShelf): Map<String, String> {
        val one = conversation(1)
        val two = """{"id":"2","started":2,"updated":5,"service":"google","model":"m","entries":[{"kind":"QUESTION","text":"Hi","notes":""},{"kind":"LATER","text":"?"},{"kind":"PLAIN","text":"${"x\\\"y".repeat(1000)}"}]}"""
        shelf.rows["c:1"] = one.toJson()
        shelf.rows["c:2"] = two
        shelf.rows["index"] = """[{"id":"1","title":"Question 1","started":1,"updated":1,"service":"anthropic","model":"claude-sonnet-5-5"},""" +
            """{"id":"2","title":"Hi","started":2,"updated":5,"service":"google","model":"m"}]"""
        return mapOf("1" to one.toJson(), "2" to two)
    }
}
