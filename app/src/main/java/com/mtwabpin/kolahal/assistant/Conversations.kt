package com.mtwabpin.kolahal.assistant

import android.content.Context
import com.mtwabpin.kolahal.data.Db
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.Stores
import org.json.JSONArray
import org.json.JSONObject

/** What an entry of a conversation is: what the user asked or tapped, or an answer drawn or written. */
enum class Kind(val role: Role) {
    QUESTION(Role.USER),
    /** A tap on an answer's button: the A2UI action's JSON. */
    ACTION(Role.USER),
    /** An answer in A2UI messages, drawn. */
    RICH(Role.ASSISTANT),
    /** An answer in text. */
    PLAIN(Role.ASSISTANT),
}

/**
 * One entry of a conversation. [notes] go to the model before [text] but the page doesn't show them:
 * what the renderer said about the last answer.
 */
class Entry(val kind: Kind, val text: String, val notes: String? = null) {
    val turn: Turn get() = Turn(kind.role, listOfNotNull(notes, text).joinToString("\n"))

    /** The entry as it is kept: one value of its own. */
    fun toJson(): JSONObject = JSONObject().put("kind", kind.name).put("text", text).apply { notes?.let { put("notes", it) } }

    companion object {
        /** An entry kept by [toJson], or null for one of a kind this version doesn't know. */
        fun fromJson(o: JSONObject): Entry? {
            val kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrNull() ?: return null
            return Entry(kind, o.getString("text"), o.optString("notes").takeIf { it.isNotEmpty() })
        }
    }
}

/** A conversation: its entries, and the service and model it goes on with. */
class Conversation(
    val id: String,
    val started: Long,
    val updated: Long,
    val service: String,
    val model: String,
    val entries: List<Entry>,
) {
    /** Its first question's first line, as the history lists it. */
    val title: String
        get() = entries.firstOrNull { it.kind == Kind.QUESTION }?.text?.lineSequence()?.firstOrNull()?.take(120).orEmpty()

    /**
     * The whole conversation as one JSON value: how it was kept before 0.5.23 (`c:<id>`). Read once,
     * to move it ([ConversationStore]); written by tests only.
     */
    fun toJson(): String = JSONObject()
        .put("id", id).put("started", started).put("updated", updated)
        .put("service", service).put("model", model)
        .put("entries", JSONArray().apply { entries.forEach { put(it.toJson()) } })
        .toString()

    companion object {
        fun fromJson(text: String): Conversation? = runCatching {
            val o = JSONObject(text)
            val list = o.getJSONArray("entries")
            Conversation(
                o.getString("id"), o.getLong("started"), o.getLong("updated"), o.getString("service"), o.getString("model"),
                (0 until list.length()).mapNotNull { Entry.fromJson(list.getJSONObject(it)) },
            )
        }.getOrNull()
    }
}

/** A conversation as the history lists it, without its entries. */
data class Summary(val id: String, val title: String, val started: Long, val updated: Long, val service: String, val model: String)

/** Where [ConversationStore] keeps its values: kolahal.db on the phone, a map in tests. */
interface Shelf {
    fun get(key: String): String?
    fun set(key: String, value: String?)

    /** The keys, without reading their values. */
    fun keys(): Set<String>

    /** Sets [values] in their order, a null removing its key. On the phone all of them or none. */
    fun setAll(values: Map<String, String?>) = values.forEach { (k, v) -> set(k, v) }

    /** Removes every key that starts with [prefix] (every key, for ""). */
    fun deleteStartingWith(prefix: String) = keys().filter { it.startsWith(prefix) }.forEach { set(it, null) }
}

/** The [Shelf] in kolahal.db: the [Stores.ASSISTANT] scope, which stays on this phone. */
class KvShelf(context: Context) : Shelf {
    private val app = context.applicationContext
    private val kv = KvStore(app, Stores.ASSISTANT)
    override fun get(key: String) = kv.get(key)
    override fun set(key: String, value: String?) = kv.set(key, value)
    override fun keys(): Set<String> = Db.get(app).readableDatabase
        .rawQuery("SELECT k FROM kv WHERE scope = ?", arrayOf(Stores.ASSISTANT))
        .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
    override fun setAll(values: Map<String, String?>) = kv.setAll(values)
    override fun deleteStartingWith(prefix: String) = kv.deleteStartingWith(prefix)
}

/**
 * The conversations kept on this phone, the newest [cap] of them. Not in a backup, not in Android's
 * cloud backup ([Stores.ASSISTANT]). Blocking: call off the main thread.
 *
 * Each entry is a value of its own, so no conversation is too long to keep (a value is at most
 * [KvStore.MAX_VALUE]):
 * - `index`: the summaries, which the history reads.
 * - `h:<id>`: a conversation's header: id, times, service, model, title, how many entries, and a sum
 *   of those entries, which tells [save] whether the entries kept are the ones it is given.
 * - `m:<id>:<n>`: its entry n, from 0, zero-padded ([ENTRY_DIGITS]); `m:<id>:` deletes them all.
 * - `c:<id>`: a whole conversation as one value, from before. Moved to the above on first use.
 *
 * The index is what is kept: a value of a conversation it doesn't list is left over and goes on first
 * use ([ready]). The stores of the page, the settings and the session share one lock.
 */
class ConversationStore(private val shelf: Shelf, private val cap: Int = CAP) {
    /** Whether old conversations were moved and left-over values removed, by this store. */
    private var ready = false

    /** The conversations kept, the one used last first. */
    fun list(): List<Summary> = synchronized(LOCK) {
        ready()
        index().sortedByDescending { it.updated }
    }

    fun load(id: String): Conversation? = synchronized(LOCK) {
        ready()
        val h = header(id) ?: return null
        val entries = (0 until h.count).mapNotNull { n -> shelf.get(entryKey(id, n))?.let(::entryFrom) }
        Conversation(id, h.started, h.updated, h.service, h.model, entries)
    }

    /**
     * Keeps [c], in place of the one with its id: only the entries after those already kept, then its
     * header and the index. When the entries kept aren't the start of [c]'s (one changed, or [c] has
     * fewer), every entry is written again. Past [cap], the ones used longest ago go, all their values.
     *
     * An entry too large for a value is kept as a short note that says so ([tooLarge]): returns how
     * many of the entries new in this save were.
     */
    fun save(c: Conversation): Int = synchronized(LOCK) {
        ready()
        val all = (index().filter { it.id != c.id } + Summary(c.id, c.title, c.started, c.updated, c.service, c.model))
            .sortedByDescending { it.updated }
        val kept = all.take(cap)
        val writes = LinkedHashMap<String, String?>()
        var noted = 0
        if (kept.any { it.id == c.id }) {
            val old = header(c.id)
            val sums = sums(c.entries)
            // Kept already: the entries before old.count, when they are the ones given.
            val from = if (old != null && old.count <= c.entries.size && sums[old.count] == old.sum) old.count else 0
            for (n in from until c.entries.size) {
                val (json, note) = stored(c.entries[n])
                writes[entryKey(c.id, n)] = json
                if (note && n >= (old?.count ?: 0)) noted++
            }
            for (n in c.entries.size until (old?.count ?: 0)) writes[entryKey(c.id, n)] = null
            writes[HEADER + c.id] = Header(c.id, c.started, c.updated, c.service, c.model, c.title, c.entries.size, sums.last()).toJson()
        }
        writes[INDEX] = indexJson(kept)
        shelf.setAll(writes)
        all.drop(cap).forEach { removeValues(it.id) }
        noted
    }

    /** Deletes conversation [id]: out of the index first, then every value of it. */
    fun delete(id: String) = synchronized(LOCK) {
        ready()
        shelf.set(INDEX, indexJson(index().filter { it.id != id }))
        removeValues(id)
    }

    /** Deletes every conversation, and anything else in the store. Returns how many there were. */
    fun deleteAll(): Int = synchronized(LOCK) {
        val n = index().size
        shelf.deleteStartingWith("")
        ready = true
        n
    }

    private fun removeValues(id: String) {
        shelf.set(HEADER + id, null)
        shelf.deleteStartingWith("$ENTRY$id:")
        shelf.set(OLD + id, null)
    }

    /**
     * Once per store, before anything else: conversations kept the old way are moved, and values the
     * index doesn't account for are removed. Cheap when there is nothing to do: the keys, and the headers.
     *
     * A move writes the new values first (in one go on the phone) and removes the old one last, so a
     * move stopped midway leaves the old value, and is done again, whole, the next time.
     */
    private fun ready() {
        if (ready) return
        val listed = index()
        val ids = listed.map { it.id }.toSet()
        for (key in shelf.keys().filter { it.startsWith(OLD) }) {
            val id = key.removePrefix(OLD)
            val c = if (id in ids) shelf.get(key)?.let(Conversation::fromJson) else null
            if (c != null) shelf.setAll(whole(c))
            shelf.set(key, null)
        }
        val headers = ids.associateWith { header(it) }
        // A conversation listed without a header can't be opened: it goes from the list.
        if (headers.values.any { it == null }) shelf.set(INDEX, indexJson(listed.filter { headers[it.id] != null }))
        for (key in shelf.keys()) {
            val gone = when {
                key.startsWith(HEADER) -> headers[key.removePrefix(HEADER)] == null
                key.startsWith(ENTRY) -> {
                    val id = key.removePrefix(ENTRY).substringBeforeLast(':')
                    val n = key.substringAfterLast(':').toIntOrNull()
                    val h = headers[id]
                    h == null || n == null || n >= h.count
                }
                else -> false
            }
            if (gone) shelf.set(key, null)
        }
        ready = true
    }

    /** The values of [c] whole, as moved from `c:<id>`: its entries, those past them removed, and its header. */
    private fun whole(c: Conversation): Map<String, String?> {
        val old = header(c.id)
        val writes = LinkedHashMap<String, String?>()
        c.entries.forEachIndexed { n, e -> writes[entryKey(c.id, n)] = stored(e).first }
        for (n in c.entries.size until (old?.count ?: 0)) writes[entryKey(c.id, n)] = null
        writes[HEADER + c.id] = Header(c.id, c.started, c.updated, c.service, c.model, c.title, c.entries.size, sums(c.entries).last()).toJson()
        return writes
    }

    /** [e] as its value, and whether it was too large, and so is kept as a note that says so. */
    private fun stored(e: Entry): Pair<String, Boolean> {
        val json = e.toJson().toString()
        val bytes = json.toByteArray().size
        if (bytes <= KvStore.MAX_VALUE) return json to false
        return tooLarge(e, bytes).toJson().toString() to true
    }

    /** The header of conversation [id], or null. */
    private fun header(id: String): Header? = shelf.get(HEADER + id)?.let { text ->
        runCatching {
            val o = JSONObject(text)
            Header(
                o.getString("id"), o.getLong("started"), o.getLong("updated"), o.getString("service"), o.getString("model"),
                o.getString("title"), o.getInt("count"), o.getLong("sum"),
            )
        }.getOrNull()
    }

    private fun entryFrom(text: String): Entry? = runCatching { Entry.fromJson(JSONObject(text)) }.getOrNull()

    private fun index(): List<Summary> = runCatching {
        val a = JSONArray(shelf.get(INDEX) ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Summary(it.getString("id"), it.getString("title"), it.getLong("started"), it.getLong("updated"), it.getString("service"), it.getString("model"))
        }
    }.getOrDefault(emptyList())

    private fun indexJson(list: List<Summary>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("title", it.title).put("started", it.started).put("updated", it.updated)
                .put("service", it.service).put("model", it.model))
        }
    }.toString()

    /** A conversation's header: [count] entries are kept, and [sum] is theirs ([sums]). */
    private class Header(
        val id: String, val started: Long, val updated: Long, val service: String, val model: String,
        val title: String, val count: Int, val sum: Long,
    ) {
        fun toJson(): String = JSONObject()
            .put("id", id).put("started", started).put("updated", updated).put("service", service).put("model", model)
            .put("title", title).put("count", count).put("sum", sum)
            .toString()
    }

    companion object {
        /** How many conversations are kept: the newest fifty. The setting's line says so. */
        const val CAP = 50

        /** The digits of an entry's number in its key, so the keys sort in order. */
        const val ENTRY_DIGITS = 6

        private const val INDEX = "index"
        private const val HEADER = "h:"
        private const val ENTRY = "m:"
        private const val OLD = "c:"

        /** Held by every store: the page's, the settings' and the session's write the same values. */
        private val LOCK = Any()

        internal fun entryKey(id: String, n: Int) = "$ENTRY$id:${n.toString().padStart(ENTRY_DIGITS, '0')}"

        /**
         * The sums of [entries]' starts: element n is the sum of the first n entries. Made of each
         * entry's kind, length and hash codes, which a String keeps once worked out, so a save of a
         * long conversation doesn't read its texts again.
         */
        private fun sums(entries: List<Entry>): LongArray {
            val out = LongArray(entries.size + 1)
            entries.forEachIndexed { i, e ->
                var h = out[i]
                h = h * 1_000_003L + e.kind.ordinal
                h = h * 1_000_003L + e.text.length
                h = h * 1_000_003L + e.text.hashCode()
                h = h * 1_000_003L + (e.notes?.hashCode() ?: -1)
                out[i + 1] = h
            }
            return out
        }

        /**
         * What is kept of an entry too large for a value ([bytes] of it, more than [KvStore.MAX_VALUE]): a
         * short note that says so, by the same side, so questions and answers still take turns. An answer
         * is at most what the services are asked for (25,000 tokens, about 100 KB), so only something
         * pasted or typed in comes this large.
         */
        internal fun tooLarge(e: Entry, bytes: Int): Entry {
            val what = when (e.kind) {
                Kind.QUESTION -> "This question"
                Kind.ACTION -> "This tap"
                Kind.RICH, Kind.PLAIN -> "This answer"
            }
            val mb = "%.1f".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0))
            val text = "$what was too large to keep ($mb MB, over the ${KvStore.MAX_VALUE / (1024 * 1024)} MB limit)"
            return Entry(if (e.kind.role == Role.USER) Kind.QUESTION else Kind.PLAIN, text)
        }
    }
}
