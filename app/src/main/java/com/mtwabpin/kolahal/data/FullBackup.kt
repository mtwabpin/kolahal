package com.mtwabpin.kolahal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.core.content.edit
import androidx.core.database.sqlite.transaction
import com.mtwabpin.kolahal.feeds.FeedDb
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.SecretStore

/**
 * The raw half of a backup: the preference files and database tables that hold user data ([PREFS],
 * [TABLES]), so a restore brings back everything (todos, notes, reminders, feeds and their read
 * state, script stores, the log, launch history, unlock history, notification settings…).
 *
 * The readable sections of [Backup] (config, aliases, apps, scripts) are applied after this, so
 * hand edits to them win. Things that can't move between devices are left out: the picked script
 * folder (a storage permission), WorkManager's own schedule state (rebuilt on restore) and the
 * phone's boot `@on boot` last fired for ("triggers"): an older one, restored, would read as a new
 * boot and run those scripts. One in a backup from before is left out of the restore too.
 */
object FullBackup {
    /** SharedPreferences files that hold user data. */
    private val PREFS = listOf(
        "config", "aliases", "apps", "app_usage", "notif_prefs", "script_library", "script_catalog", "unlocks", "launcher", "logs",
    )

    private class Table(val db: (Context) -> SQLiteDatabase, val name: String)

    private val TABLES = listOf(
        Table({ Db.get(it).writableDatabase }, "logs"),
        Table({ Db.get(it).writableDatabase }, "kv"),
        Table({ Db.get(it).writableDatabase }, "todos"),
        Table({ Db.get(it).writableDatabase }, "notes"),
        Table({ Db.get(it).writableDatabase }, "reminders"),
        Table({ FeedDb.get(it).writableDatabase }, "feeds"),
        Table({ FeedDb.get(it).writableDatabase }, "items"),
    )

    /** kv scopes are "script:<id>"; ids differ between devices, so backups key them by script name. */
    private const val SCRIPT_SCOPE = "script:"
    private val BUILT_INS get() = com.mtwabpin.kolahal.terminal.CommandCatalog.RESERVED_NAMES

    /**
     * kv scopes about this phone: the last call and the apps it has seen (another phone's would log
     * every app as installed or updated), daily notes and settings copied for the :script process.
     * They don't go into a backup, and a restore keeps this phone's.
     */
    private val DEVICE_SCOPES = listOf("calls", "packages", "worker", SharedSettings.SCOPE)

    fun export(context: Context): Map<String, Any?> {
        // Rows too big for a cursor would make the whole export fail.
        runCatching { KvStore.repairOversized(context) }
        val scriptNames = ScriptRepository.get(context).all().associate { ScriptRepository.scopeOf(it.id) to it.name }
        val tables = LinkedHashMap<String, Any?>()
        val scriptKv = LinkedHashMap<String, MutableMap<String, String?>>()
        val serials = privateSerials(context)
        for (t in TABLES) {
            val rows = query(t.db(context), t.name)
            tables[t.name] = if (t.name == "logs") rows.filterNot { private(it, serials) } else if (t.name != "kv") rows else rows.filter { row ->
                val scope = row["scope"] as? String ?: return@filter false
                // The copy of recent notifications kept for scripts stays on the device.
                if (scope == KolahalNotificationListener.MIRROR_SCOPE || scope in DEVICE_SCOPES) return@filter false
                // Script secrets never leave the phone (and only its Keystore key can read them anyway).
                if (scope.startsWith(SecretStore.SCOPE_PREFIX)) return@filter false
                if (!scope.startsWith(SCRIPT_SCOPE)) return@filter true
                // Stores of deleted scripts are dropped; the rest move under the script's name, but for
                // what http.cached keeps: only a cache, and one from before 0.4.9 can hold a key.
                val k = row["k"].toString()
                if (!k.startsWith(HostApi.HTTP_CACHE)) scriptNames[scope]?.let { name -> scriptKv.getOrPut(name) { LinkedHashMap() }[k] = row["v"] as? String }
                false
            }
        }
        return mapOf(
            "prefs" to PREFS.associateWith { file -> exportPrefs(context, file).let { if (file in APP_PREFS) withoutPrivateApps(it, serials) else it } },
            "tables" to tables,
            "scriptStores" to scriptKv,
        )
    }

    /** Replaces preferences and tables with the backup's. Returns how many rows were restored. */
    fun importRaw(context: Context, data: Map<*, *>): Int {
        var rows = 0
        val tables = data["tables"] as? Map<*, *>
        for (t in TABLES) {
            // A backup never brings secrets: only this phone's are kept.
            val list = (tables?.get(t.name) as? List<*>)?.filterIsInstance<Map<*, *>>()
                ?.filterNot { t.name == "kv" && it["scope"]?.toString().orEmpty().let { s -> s.startsWith(SecretStore.SCOPE_PREFIX) || s in DEVICE_SCOPES } } ?: continue
            val db = t.db(context)
            db.transaction {
                if (t.name == "kv") {
                    // Keep this device's script stores (re-attached by name afterwards) and script secrets.
                    db.delete(
                        "kv", "scope NOT LIKE ? AND scope NOT LIKE ? AND scope NOT IN (${DEVICE_SCOPES.joinToString { "?" }})",
                        arrayOf("$SCRIPT_SCOPE%", "${SecretStore.SCOPE_PREFIX}%") + DEVICE_SCOPES,
                    )
                } else {
                    db.delete(t.name, null, null)
                }
                // Only this device's columns: an unknown key would fail the insert halfway through a restore.
                val columns = db.rawQuery("PRAGMA table_info(${t.name})", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
                }
                for (row in list) {
                    // A backup from 0.4.1 or before has run headers, which are no longer written.
                    if (t.name == "logs" && LogContext.RUN_HEADER in LogContext.fromJson(row["ctx"] as? String).values) continue
                    val clean = when (t.name) {
                        "logs" -> withSafeCtx(row)
                        "reminders" -> withReadableRepeat(row)
                        else -> row
                    }
                    if (db.insertWithOnConflict(t.name, null, values(clean, columns), SQLiteDatabase.CONFLICT_REPLACE) != -1L) rows++
                }
            }
        }
        // After the tables: the ids the log saved are measured against the lines now in it.
        (data["prefs"] as? Map<*, *>)?.forEach { (file, entries) ->
            if (file !is String || file !in PREFS || entries !is Map<*, *>) return@forEach
            val safe = when (file) {
                // Same rule as `alias`: an alias never shadows a built-in command.
                "aliases" -> entries.filterKeys { it?.toString()?.lowercase() !in BUILT_INS }
                "logs" -> logIdsWithin(entries, LogRepository.get(context).maxId())
                else -> entries
            }
            importPrefs(context, file, safe)
        }
        return rows
    }

    /**
     * A backup's "logs" preferences with no line's id past [newest], the newest line now in the log.
     * They can be past it: the lines up to them stayed behind (notification and call lines) or are
     * gone (cleared, trimmed, swiped), and new lines, numbered on from [newest], would stay out of
     * view until their ids got there. Restored lines stay hidden or shown just as they were.
     */
    internal fun logIdsWithin(entries: Map<*, *>, newest: Long): Map<*, *> = entries.mapValues { (key, tagged) ->
        val id = (tagged as? Map<*, *>)?.get("l") as? Number
        if (key in LogRepository.ID_KEYS && id != null && id.toLong() > newest) mapOf("l" to newest) else tagged
    }

    /** After scripts exist (by name), puts each script's store back under its new id. */
    fun importScriptStores(context: Context, data: Map<*, *>) {
        val stores = data["scriptStores"] as? Map<*, *> ?: return
        val repo = ScriptRepository.get(context)
        stores.forEach { (name, entries) ->
            val script = repo.byName(name.toString()) ?: return@forEach
            val kv = KvStore(context, ScriptRepository.scopeOf(script.id))
            // runCatching: an oversized value (e.g. an old cached HTTP body) is skipped, not fatal.
            (entries as? Map<*, *>)?.forEach { (k, v) -> if (k != null) runCatching { kv.set(k.toString(), v?.toString()) } }
        }
    }

    // ---- preferences: values are tagged so ints, longs and string sets survive JSON --------------

    private fun exportPrefs(context: Context, file: String): Map<String, Any?> =
        context.getSharedPreferences(file, Context.MODE_PRIVATE).all.mapValues { (_, v) ->
            when (v) {
                is Boolean -> mapOf("b" to v)
                is Int -> mapOf("i" to v)
                is Long -> mapOf("l" to v)
                is Float -> mapOf("f" to v.toDouble())
                is Set<*> -> mapOf("set" to v.map { it.toString() })
                else -> mapOf("s" to v?.toString())
            }
        }

    private fun importPrefs(context: Context, file: String, entries: Map<*, *>) {
        // commit, not apply: the app restarts right after a restore.
        context.getSharedPreferences(file, Context.MODE_PRIVATE).edit(commit = true) {
            clear()
            entries.forEach { (k, tagged) ->
                val key = k?.toString() ?: return@forEach
                val m = tagged as? Map<*, *> ?: return@forEach
                when {
                    "b" in m -> putBoolean(key, m["b"] == true)
                    "i" in m -> putInt(key, (m["i"] as? Number)?.toInt() ?: 0)
                    "l" in m -> putLong(key, (m["l"] as? Number)?.toLong() ?: 0L)
                    "f" in m -> putFloat(key, (m["f"] as? Number)?.toFloat() ?: 0f)
                    "set" in m -> putStringSet(key, (m["set"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet())
                    else -> putString(key, m["s"]?.toString())
                }
            }
        }
    }

    // ---- tables ------------------------------------------------------------------------------

    private fun query(db: SQLiteDatabase, table: String): List<Map<String, Any?>> =
        db.rawQuery("SELECT * FROM $table", null).use { c ->
            val out = ArrayList<Map<String, Any?>>(c.count)
            while (c.moveToNext()) {
                val row = LinkedHashMap<String, Any?>()
                for (i in 0 until c.columnCount) {
                    row[c.getColumnName(i)] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_BLOB -> mapOf("base64" to Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
                        else -> c.getString(i)
                    }
                }
                out += row
            }
            out
        }

    /**
     * Lines with other people's messages and numbers stay on the device: notifications, calls,
     * anything that carries a number, and the `call` and `sms` lines you type. So do lines from a
     * private space, known by the profile's serial or, as the space may be locked or gone by now,
     * by the `[S]` after the app's name.
     */
    internal fun private(row: Map<String, Any?>, privateSerials: Set<Long>): Boolean {
        if (row["source"] == com.mtwabpin.kolahal.calls.CallEvents.SOURCE) return true
        val text = row["text"] as? String ?: ""
        if (row["kind"] == LogKind.COMMAND.key && com.mtwabpin.kolahal.terminal.InstantRun.canonical(text.trim().substringBefore(' ')) in TYPED_TO_SOMEONE) return true
        val ctx = LogContext.fromJson(row["ctx"] as? String)
        if (ctx.values.containsKey(KolahalNotificationListener.CTX_KEY) || ctx.values.containsKey(LogContext.PHONE)) return true
        if (ctx.userSerial?.let { it in privateSerials } == true) return true
        // A grid of apps has only their names in its text; the [S] is in what it copies.
        return PRIVATE_TAG.containsMatchIn(text) || PRIVATE_TAG.containsMatchIn(ctx.values[LogContext.COPY].orEmpty())
    }

    private val TYPED_TO_SOMEONE = setOf("call", "sms")

    private val PRIVATE_TAG = Regex(" \\[${com.mtwabpin.kolahal.apps.ProfileKind.PRIVATE.tag}](?= \\S*$|$)", RegexOption.MULTILINE)

    /** Preference files that name apps by key, `<component>#<serial>`. */
    private val APP_PREFS = setOf("apps", "app_usage")

    /** Whether [key], an app's, is one in a private space. */
    internal fun isPrivateApp(key: String, serials: Set<Long>) = key.substringAfterLast('#', "").toLongOrNull() in serials

    /** [entries] of an [APP_PREFS] file, as [exportPrefs] tags them, with no app of a private space. */
    internal fun withoutPrivateApps(entries: Map<String, Any?>, serials: Set<Long>): Map<String, Any?> =
        entries.filterKeys { !isPrivateApp(it, serials) }.mapValues { (key, tagged) ->
            val pinned = (tagged as? Map<*, *>)?.get("s") as? String
            if (key == "pinned" && pinned != null) mapOf("s" to pinned.split('\n').filterNot { isPrivateApp(it, serials) }.joinToString("\n")) else tagged
        }

    /** The private space's serials, when this phone has one and Kolahal may see it. */
    internal fun privateSerials(context: Context): Set<Long> = runCatching {
        val launcher = context.getSystemService(android.content.pm.LauncherApps::class.java)
        val users = context.getSystemService(android.os.UserManager::class.java)
        val me = android.os.Process.myUserHandle()
        launcher.profiles
            .filter { com.mtwabpin.kolahal.apps.AppRepository.kindOf(launcher, it, me) == com.mtwabpin.kolahal.apps.ProfileKind.PRIVATE }
            .map { users.getSerialNumberForUser(it) }
            .toSet()
    }.getOrDefault(emptySet())

    /** A repeat this version can't read: the reminder still fires, once. */
    private fun withReadableRepeat(row: Map<*, *>): Map<*, *> {
        val rule = row["repeat_rule"] as? String ?: return row
        return if (com.mtwabpin.kolahal.productivity.Repeat.isValid(rule)) row else row.toMutableMap().apply { put("repeat_rule", null) }
    }

    /**
     * A restored line must not do more on a tap than the line it claims to be: no commands that
     * run at once, no app shortcuts, and links only to the web, maps or the store.
     */
    private fun withSafeCtx(row: Map<*, *>): Map<*, *> {
        val ctx = LogContext.fromJson(row["ctx"] as? String)
        if (ctx.isEmpty) return row
        return row.toMutableMap().apply { put("ctx", safeCtx(ctx).toJson()) }
    }

    /**
     * [ctx] with only what a restored line may do on a tap: no command that runs, no app shortcut,
     * links to the web, maps or the store, and a contact only from the contacts app.
     */
    internal fun safeCtx(ctx: LogContext): LogContext = LogContext(ctx.values.filter { (k, v) ->
        when (k) {
            LogContext.RUN, LogContext.SHORTCUT, LogContext.COMMAND -> false
            LogContext.URL -> com.mtwabpin.kolahal.script.Net.webUrl(v) != null || v.startsWith("geo:") || v.startsWith("market:")
            LogContext.IMAGE -> com.mtwabpin.kolahal.script.Net.webUrl(v) != null
            LogContext.CONTACT -> v.startsWith("content://com.android.contacts/")
            else -> true
        }
    })

    private fun values(row: Map<*, *>, columns: Set<String>) = ContentValues().apply {
        row.forEach { (k, v) ->
            val col = k?.toString()?.takeIf { it in columns } ?: return@forEach
            when (v) {
                null -> putNull(col)
                is Boolean -> put(col, if (v) 1 else 0)
                is Int -> put(col, v)
                is Long -> put(col, v)
                is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 9e15) put(col, v.toLong()) else put(col, v)
                is Number -> put(col, v.toLong())
                is Map<*, *> -> (v["base64"] as? String)?.let { put(col, Base64.decode(it, Base64.NO_WRAP)) }
                else -> put(col, v.toString())
            }
        }
    }
}
