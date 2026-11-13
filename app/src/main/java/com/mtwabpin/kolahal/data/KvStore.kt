package com.mtwabpin.kolahal.data

import android.content.ContentValues
import android.content.Context
import androidx.core.database.sqlite.transaction

/** A small string key/value store. Each script gets its own scope (used by `store` and `rss.fresh`). */
class KvStore(context: Context, private val scope: String) {
    private val db = Db.get(context)

    fun all(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        db.readableDatabase.rawQuery("SELECT k, v FROM kv WHERE scope = ? ORDER BY k", arrayOf(scope)).use { c ->
            while (c.moveToNext()) c.getString(1)?.let { out[c.getString(0)] = it }
        }
        return out
    }

    fun get(key: String): String? = try {
        db.readableDatabase.rawQuery(
            "SELECT v FROM kv WHERE scope = ? AND k = ?", arrayOf(scope, key),
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: android.database.sqlite.SQLiteBlobTooBigException) {
        // Written before values were capped, and too big to read back. Drop it so the key works again.
        set(key, null)
        null
    }

    fun set(key: String, value: String?) {
        // Count bytes as stored: a million CJK characters take 3 MB, more than a cursor can read back.
        val bytes = value?.toByteArray()?.size ?: 0
        require(bytes <= MAX_VALUE) { "value too large (over ${MAX_VALUE / 1024} KB)" }
        require(key.length <= MAX_KEY) { "key too long (over $MAX_KEY characters)" }
        // A script's whole store is limited too, keys included, so a loop of store.set can't fill the phone.
        if (scope.startsWith(Stores.SCRIPT_PREFIX)) return synchronized(sizes) { setCounted(key, value, bytes) }
        write(key, value)
    }

    /**
     * [set] in a script's store, within its limits. The store's size is counted once, then kept up to
     * date by each write here: a loop of store.set doesn't read the whole store (up to 16 MB) on each
     * call. The count is read again after [SIZE_FOR_MS], as the other process may have changed the store
     * (a backup restored, the script deleted).
     */
    private fun setCounted(key: String, value: String?, bytes: Int) {
        val now = android.os.SystemClock.elapsedRealtime()
        val size = sizes[scope]?.takeIf { now - it.at < SIZE_FOR_MS } ?: countSize().also { sizes[scope] = it }
        val keyBytes = key.toByteArray().size
        val old = db.readableDatabase.rawQuery(
            "SELECT length(CAST(v AS BLOB)) + length(CAST(k AS BLOB)) FROM kv WHERE scope = ? AND k = ?", arrayOf(scope, key),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        // The store without this key.
        val keys = size.keys - (if (old != null) 1 else 0)
        val total = size.bytes - (old ?: 0L)
        if (value != null) {
            require(keys < MAX_KEYS) { "store full (over $MAX_KEYS keys)" }
            require(total + bytes + keyBytes <= MAX_SCOPE) { "store full (over ${MAX_SCOPE / (1024 * 1024)} MB)" }
        }
        write(key, value)
        sizes[scope] = if (value == null) Size(keys, total, size.at) else Size(keys + 1, total + bytes + keyBytes, size.at)
    }

    private fun countSize(): Size = db.readableDatabase.rawQuery(
        "SELECT COUNT(*), COALESCE(SUM(length(CAST(v AS BLOB)) + length(CAST(k AS BLOB))), 0) FROM kv WHERE scope = ?", arrayOf(scope),
    ).use { c ->
        c.moveToFirst()
        Size(c.getInt(0), c.getLong(1), android.os.SystemClock.elapsedRealtime())
    }

    private fun write(key: String, value: String?) {
        if (value == null) {
            db.writableDatabase.delete("kv", "scope = ? AND k = ?", arrayOf(scope, key))
        } else {
            db.writableDatabase.insertWithOnConflict("kv", null, ContentValues().apply {
                put("scope", scope)
                put("k", key)
                put("v", value)
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /**
     * Sets several keys together: all of them, or none. A null value removes its key. Not for a
     * script's store, whose size [set] counts write by write.
     */
    fun setAll(values: Map<String, String?>) {
        require(!scope.startsWith(Stores.SCRIPT_PREFIX)) { "a script's store is set one key at a time" }
        if (values.isEmpty()) return
        for ((key, value) in values) {
            require((value?.toByteArray()?.size ?: 0) <= MAX_VALUE) { "value too large (over ${MAX_VALUE / 1024} KB)" }
            require(key.length <= MAX_KEY) { "key too long (over $MAX_KEY characters)" }
        }
        db.writableDatabase.transaction { values.forEach { (key, value) -> write(key, value) } }
    }

    /** Deletes every key that starts with [prefix], except [except]. */
    fun deleteStartingWith(prefix: String, except: String? = null) {
        sizes.remove(scope)
        db.writableDatabase.delete(
            "kv", "scope = ? AND substr(k, 1, ${prefix.length}) = ? AND k != ?", arrayOf(scope, prefix, except.orEmpty()),
        )
    }

    /** A script store's number of keys and bytes, as counted at [at] and kept up to date since. */
    private class Size(val keys: Int, val bytes: Long, val at: Long)

    companion object {
        /** Script stores' sizes in this process, by scope ([setCounted]). */
        private val sizes = java.util.concurrent.ConcurrentHashMap<String, Size>()

        /** How long a counted size is trusted before the store is counted again. */
        private const val SIZE_FOR_MS = 10_000L

        /** The largest value a key may hold (bytes, as stored). It keeps every row readable through a cursor. */
        const val MAX_VALUE = 1024 * 1024

        /** The longest key (characters). Keys also count toward a script's store. */
        const val MAX_KEY = 512
        /** The number of keys, and of bytes (keys and values), that one script's store may hold. */
        const val MAX_KEYS = 1000
        const val MAX_SCOPE = 16L * 1024 * 1024

        /**
         * Trims or drops rows too big to read back (the 2 MB cursor window), left over from before
         * values were capped. Otherwise one such row breaks the log, a script key, or backup export.
         */
        fun repairOversized(context: Context) {
            sizes.clear()
            val w = Db.get(context).writableDatabase
            w.execSQL("DELETE FROM kv WHERE length(CAST(v AS BLOB)) > ?", arrayOf<Any>(MAX_VALUE))
            w.execSQL("DELETE FROM kv WHERE scope LIKE ? AND length(k) > ?", arrayOf<Any>(Stores.SCRIPT_PREFIX + "%", MAX_KEY))
            w.execSQL(
                "UPDATE scripts SET last_status = substr(last_status, 1, ?) WHERE length(last_status) > ?",
                arrayOf<Any>(ScriptRepository.STATUS_MAX, ScriptRepository.STATUS_MAX),
            )
            w.execSQL("UPDATE logs SET text = substr(text, 1, ?) WHERE length(text) > ?", arrayOf<Any>(LogRepository.MAX_FIELD, LogRepository.MAX_FIELD))
            w.execSQL("UPDATE logs SET ctx = NULL WHERE length(ctx) > ?", arrayOf<Any>(LogRepository.MAX_FIELD))
        }
    }
}
