package com.mtwabpin.kolahal.assistant

import android.content.Context
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

    fun toJson(): String = JSONObject()
        .put("id", id).put("started", started).put("updated", updated)
        .put("service", service).put("model", model)
        .put("entries", JSONArray().apply {
            entries.forEach { e -> put(JSONObject().put("kind", e.kind.name).put("text", e.text).apply { e.notes?.let { put("notes", it) } }) }
        })
        .toString()

    companion object {
        fun fromJson(text: String): Conversation? = runCatching {
            val o = JSONObject(text)
            val list = o.getJSONArray("entries")
            Conversation(
                o.getString("id"), o.getLong("started"), o.getLong("updated"), o.getString("service"), o.getString("model"),
                (0 until list.length()).map { list.getJSONObject(it) }.mapNotNull { e ->
                    val kind = runCatching { Kind.valueOf(e.getString("kind")) }.getOrNull() ?: return@mapNotNull null
                    Entry(kind, e.getString("text"), e.optString("notes").takeIf { it.isNotEmpty() })
                },
            )
        }.getOrNull()
    }
}

/** A conversation as the history lists it, without its entries. */
data class Summary(val id: String, val title: String, val started: Long, val updated: Long, val service: String, val model: String)

/** Where [ConversationStore] keeps its rows: kolahal.db on the phone, a map in tests. */
interface Shelf {
    fun get(key: String): String?
    fun set(key: String, value: String?)
    fun keys(): Set<String>
}

/** The [Shelf] in kolahal.db: the [Stores.ASSISTANT] scope, which stays on this phone. */
class KvShelf(context: Context) : Shelf {
    private val kv = KvStore(context, Stores.ASSISTANT)
    override fun get(key: String) = kv.get(key)
    override fun set(key: String, value: String?) = kv.set(key, value)
    override fun keys(): Set<String> = kv.all().keys
}

/**
 * The conversations kept on this phone, the newest [cap] of them. Each is a row of its own, and an
 * index row lists them, so the history reads one row. Not in a backup, not in Android's cloud backup
 * ([Stores.ASSISTANT]). Blocking: call off the main thread.
 */
class ConversationStore(private val shelf: Shelf, private val cap: Int = CAP) {
    /** The conversations kept, the one used last first. */
    @Synchronized
    fun list(): List<Summary> = index().sortedByDescending { it.updated }

    @Synchronized
    fun load(id: String): Conversation? = shelf.get(ROW + id)?.let(Conversation::fromJson)

    /**
     * Keeps [c], in place of the one with its id. Past [cap], the ones used longest ago go. A conversation
     * too big for a row loses its oldest entries until it fits.
     */
    @Synchronized
    fun save(c: Conversation) {
        var kept = c
        var json = kept.toJson()
        while (json.toByteArray().size > KvStore.MAX_VALUE && kept.entries.size > 2) {
            kept = Conversation(c.id, c.started, c.updated, c.service, c.model, kept.entries.drop(2))
            json = kept.toJson()
        }
        if (json.toByteArray().size > KvStore.MAX_VALUE) return
        shelf.set(ROW + c.id, json)
        val all = (index().filter { it.id != c.id } + Summary(c.id, c.title, c.started, c.updated, c.service, c.model))
            .sortedByDescending { it.updated }
        all.drop(cap).forEach { shelf.set(ROW + it.id, null) }
        writeIndex(all.take(cap))
    }

    @Synchronized
    fun delete(id: String) {
        shelf.set(ROW + id, null)
        writeIndex(index().filter { it.id != id })
    }

    /** Deletes every conversation, and anything else in the store. Returns how many there were. */
    @Synchronized
    fun deleteAll(): Int {
        val n = index().size
        shelf.keys().forEach { shelf.set(it, null) }
        return n
    }

    private fun index(): List<Summary> = runCatching {
        val a = JSONArray(shelf.get(INDEX) ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Summary(it.getString("id"), it.getString("title"), it.getLong("started"), it.getLong("updated"), it.getString("service"), it.getString("model"))
        }
    }.getOrDefault(emptyList())

    private fun writeIndex(list: List<Summary>) {
        shelf.set(INDEX, JSONArray().apply {
            list.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title).put("started", it.started).put("updated", it.updated)
                    .put("service", it.service).put("model", it.model))
            }
        }.toString())
    }

    companion object {
        /** How many conversations are kept: the newest fifty. The setting's line says so. */
        const val CAP = 50
        private const val INDEX = "index"
        private const val ROW = "c:"
    }
}
