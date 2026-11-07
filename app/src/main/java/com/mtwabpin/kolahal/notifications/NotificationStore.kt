package com.mtwabpin.kolahal.notifications

import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** In-memory feed of recent notifications, newest first. It exists only in the UI process. */
object NotificationStore {
    const val CAP = 200
    /** Maximum characters of each notification's text in the copy that scripts get. */
    private const val MAX_TEXT = 2000

    private val _items = MutableStateFlow<List<Notif>>(emptyList())
    val items: StateFlow<List<Notif>> = _items.asStateFlow()

    /** contentIntents by key. They are kept outside [Notif] so that [Notif] stays a plain value. */
    private val intents = ConcurrentHashMap<String, PendingIntent>()

    /** Adds a notification, or replaces the one with the same key, and moves it to the top. */
    fun put(n: Notif, intent: PendingIntent?) {
        if (intent != null) intents[n.key] = intent else intents.remove(n.key)
        _items.update { list ->
            val out = ArrayList<Notif>(minOf(list.size + 1, CAP))
            out += n
            list.filterTo(out) { it.key != n.key }
            out.take(CAP).also { kept -> dropIntentsNotIn(kept) }
        }
    }

    /**
     * Replaces the whole feed when the listener connects. Entries already in memory that are no
     * longer active stay below the active ones, marked as removed.
     */
    fun replaceAll(active: List<Pair<Notif, PendingIntent?>>) {
        intents.clear()
        active.forEach { (n, pi) -> if (pi != null) intents[n.key] = pi }
        val keys = active.mapTo(HashSet()) { it.first.key }
        _items.update { list ->
            val fresh = active.map { it.first }.sortedByDescending { it.postTime }
            (fresh + list.filter { it.key !in keys }.map { it.copy(removed = true) }).take(CAP)
        }
    }

    fun markRemoved(key: String) {
        intents.remove(key)
        _items.update { list -> list.map { if (it.key == key && !it.removed) it.copy(removed = true) else it } }
    }

    fun find(key: String): Notif? = _items.value.firstOrNull { it.key == key }

    internal fun intent(key: String): PendingIntent? = intents[key]

    fun active(): List<Notif> = _items.value.filter { !it.removed }

    /** The view for scripts: simple maps, newest first. */
    fun recent(limit: Int = 20, includeRemoved: Boolean = true, skipProfile: (Long) -> Boolean = { false }): List<Map<String, Any?>> =
        _items.value.asSequence()
            .filter { (includeRemoved || !it.removed) && !skipProfile(it.userSerial) }
            .take(limit.coerceIn(0, CAP))
            .map {
                mapOf(
                    "pkg" to it.pkg,
                    "app" to it.app,
                    "title" to it.title,
                    // A long conversation's full text would make the copy for scripts huge.
                    "text" to it.text?.let { t -> if (t.length > MAX_TEXT) t.take(MAX_TEXT) + "…" else t },
                    "time" to it.postTime,
                )
            }
            .toList()

    fun clear() {
        intents.clear()
        _items.value = emptyList()
    }

    private fun dropIntentsNotIn(kept: List<Notif>) {
        if (intents.size <= kept.size) return
        val keys = kept.mapTo(HashSet()) { it.key }
        intents.keys.retainAll(keys)
    }
}
