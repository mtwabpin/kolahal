package com.mtwabpin.kolahal.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit

/**
 * The log's marks: where its view starts and what you've seen, as line ids, and two facts about it.
 * - [CLEARED_UP_TO]: `clear` hid the lines up to here. [PURGE_UP_TO]: those lines go for good once
 *   the clear's undo expires.
 * - [HIDDEN_UP_TO]: lines up to here are out of view (`fresh`, `log.fresh`) until shown again.
 * - [SEEN_UP_TO]: the newest line seen when home was left, for unread markers.
 * - [HISTORY_FROM]: ↑ recall starts after here (`clear history`).
 * - [TRIMMED_AT]: when the log was last trimmed (ms). [FILTER]: `log only …`, kept for the next start.
 *
 * They live in kolahal.db beside the lines they point at (kv scope [Stores.LOG_MARKS]). A backup and a
 * move to a new phone carry both together, and both processes read the same values. Before 0.5.8 they
 * were in the `logs` prefs ([Stores.OLD_LOGS]): [moveFromPrefs] brings them here once.
 *
 * Home reads them for each page of the log, some on the main thread. So with [inMemory] (the main
 * process) they are kept in memory, read on a thread of their own as preferences are. Only the main
 * process changes them; the :script process reads the database each time.
 *
 * Never call these from inside a database transaction: a change takes this object's lock, then a
 * transaction of its own. A thread holding a transaction while it waits for the lock could deadlock
 * with one that holds the lock and waits for the database.
 */
class LogMarks(context: Context, private val inMemory: Boolean) {
    private val app = context.applicationContext
    private val kv = KvStore(app, Stores.LOG_MARKS)
    private val lock = Any()
    private var memory: MutableMap<String, String>? = null

    init {
        // Read ahead, so the first read on the main thread doesn't open the database there.
        if (inMemory) Thread({ synchronized(lock) { loaded() } }, "log-marks").start()
    }

    /** Under [lock]: the marks in memory, moved from the old prefs and read the first time. */
    private fun loaded(): MutableMap<String, String> = memory ?: run {
        moveFromPrefs()
        HashMap(kv.all())
    }.also { memory = it }

    fun string(key: String): String? = if (inMemory) synchronized(lock) { loaded()[key] } else kv.get(key)

    fun long(key: String, default: Long = 0L): Long = string(key)?.toLongOrNull() ?: default

    /** Sets [values] together, in one transaction. A null value removes its mark. */
    fun set(vararg values: Pair<String, Any?>) {
        val strings = values.associate { (k, v) -> k to v?.toString() }
        synchronized(lock) {
            kv.setAll(strings)
            if (inMemory) loaded().apply { strings.forEach { (k, v) -> if (v == null) remove(k) else put(k, v) } }
        }
    }

    /**
     * After a restore: the marks the backup brought, in the database or in the old prefs (a backup
     * from before 0.5.8), now with no id past [newest], the newest line in the log. The lines past a
     * mark stayed behind or are gone; left as it is, the mark would hide the next lines logged.
     */
    fun restored(newest: Long) = synchronized(lock) {
        moveFromPrefs()
        kv.setAll(clamped(kv.all(), newest))
        memory = null
    }

    /** Every mark, for a test to put back after it ([replaceAll]). */
    @VisibleForTesting
    internal fun all(): Map<String, String> = synchronized(lock) { if (inMemory) HashMap(loaded()) else kv.all() }

    /** Puts back what [all] gave: a mark not in [values] goes. */
    @VisibleForTesting
    internal fun replaceAll(values: Map<String, String>) = synchronized(lock) {
        kv.setAll(kv.all().keys.associateWith { null } + values)
        memory = null
    }

    /**
     * Marks from before 0.5.8 come over from the `logs` prefs, then the file is emptied. They win
     * over marks already here: the file has some only after that update, or after a restored backup
     * from before it. Only the main process moves them.
     */
    @VisibleForTesting
    internal fun moveFromPrefs(): Unit = synchronized(lock) {
        if (!inMemory) return
        val prefs = Stores.OLD_LOGS.open(app)
        val old = prefs.all.takeIf { it.isNotEmpty() } ?: return
        kv.setAll(fromPrefs(old))
        prefs.edit(commit = true) { clear() }
        // Read again on the next use, with what moved.
        memory = null
    }

    companion object {
        const val CLEARED_UP_TO = "cleared_up_to"
        const val HIDDEN_UP_TO = "hidden_up_to"
        const val PURGE_UP_TO = "purge_up_to"
        const val SEEN_UP_TO = "seen_up_to"
        const val HISTORY_FROM = "history_from"
        const val TRIMMED_AT = "trimmed_at"
        const val FILTER = "filter"

        /** The marks that are line ids. */
        val ID_KEYS = setOf(CLEARED_UP_TO, HIDDEN_UP_TO, PURGE_UP_TO, SEEN_UP_TO, HISTORY_FROM)
        private val KEYS = ID_KEYS + TRIMMED_AT + FILTER

        /**
         * The old prefs' values as marks. A number is kept, and so is text that reads as one (an
         * edited backup can hold either); the filter is text. Anything else is left out.
         */
        internal fun fromPrefs(old: Map<String, *>): Map<String, String> = old.mapNotNull { (key, value) ->
            if (key !in KEYS) return@mapNotNull null
            val text = when (value) {
                is Number -> value.toLong().toString()
                is String -> if (key == FILTER) value else value.trim().toLongOrNull()?.toString()
                else -> null
            }
            text?.let { key to it }
        }.toMap()

        /**
         * What to change in [marks] so that no id is past [newest]: an id past it comes down to it, and
         * one that isn't a number goes (null). A time and the filter aren't ids, and stay.
         */
        internal fun clamped(marks: Map<String, String>, newest: Long): Map<String, String?> = marks.mapNotNull { (key, value) ->
            if (key !in ID_KEYS) return@mapNotNull null
            val id = value.toLongOrNull() ?: return@mapNotNull key to null
            if (id > newest) key to newest.toString() else null
        }.toMap()
    }
}
