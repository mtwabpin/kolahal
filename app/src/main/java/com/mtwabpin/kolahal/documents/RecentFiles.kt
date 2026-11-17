package com.mtwabpin.kolahal.documents

import org.json.JSONArray
import org.json.JSONObject

/**
 * The files opened in the editor lately, newest first: at most [MAX]. Only files that Kolahal may
 * open again are listed ([Documents.remember]).
 *
 * The list is kept as JSON text. [load] reads it and [store] writes it: the editor's preferences
 * on the phone, a variable in tests.
 */
class RecentFiles(private val load: () -> String?, private val store: (String) -> Unit) {
    data class Entry(val uri: String, val name: String, val opened: Long)

    @Synchronized
    fun list(): List<Entry> = runCatching {
        val all = JSONArray(load() ?: return emptyList())
        (0 until all.length()).map { all.getJSONObject(it) }.map { Entry(it.getString("uri"), it.getString("name"), it.getLong("opened")) }
    }.getOrDefault(emptyList())

    private fun save(entries: List<Entry>) {
        store(JSONArray(entries.map { JSONObject().put("uri", it.uri).put("name", it.name).put("opened", it.opened) }).toString())
    }

    /**
     * Puts the file first, with its [name] as it is now. Returns the files that fell off the end of
     * the list, so that the caller can give their permission back.
     */
    @Synchronized
    fun add(uri: String, name: String, now: Long): List<Entry> {
        val all = listOf(Entry(uri, name, now)) + list().filter { it.uri != uri }
        save(all.take(MAX))
        return all.drop(MAX)
    }

    /** Takes the file off the list. False: it was not on it. */
    @Synchronized
    fun remove(uri: String): Boolean {
        val all = list()
        val rest = all.filter { it.uri != uri }
        if (rest.size == all.size) return false
        save(rest)
        return true
    }

    companion object {
        /** Android keeps a limited number of file permissions for an app, and a longer list is not read. */
        const val MAX = 12
    }
}
