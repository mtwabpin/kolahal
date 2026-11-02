package com.mtwabpin.kolahal.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Single SQLite database shared by the UI process and the `:script` process.
 * WAL mode plus SQLite's file locking make concurrent access from both processes safe.
 */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                kind TEXT NOT NULL,
                source TEXT,
                text TEXT NOT NULL,
                ctx TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS logs_kind_ts ON logs(kind, ts)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scripts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                lang TEXT NOT NULL,
                code TEXT NOT NULL,
                interval_min INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                notify INTEGER NOT NULL DEFAULT 0,
                last_run INTEGER,
                last_status TEXT,
                at_times TEXT,
                access TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS kv (
                scope TEXT NOT NULL,
                k TEXT NOT NULL,
                v TEXT,
                PRIMARY KEY (scope, k)
            )
            """.trimIndent()
        )
        createProductivityTables(db)
    }

    /** `todo`, `note` and `remind` (schema version 3). */
    private fun createProductivityTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                created INTEGER NOT NULL,
                done_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                created INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                at INTEGER NOT NULL,
                text TEXT NOT NULL,
                created INTEGER NOT NULL,
                repeat_rule TEXT,
                repeat_time TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Both processes may open the database right after an update: each step once.
        if (oldVersion < 2 && !hasColumn(db, "scripts", "at_times")) db.execSQL("ALTER TABLE scripts ADD COLUMN at_times TEXT")
        if (oldVersion < 3) createProductivityTables(db)
        if (oldVersion < 4) addScriptAccess(db)
        if (oldVersion < 5) dropRunHeaders(db)
        // Repeating reminders (version 6): how one repeats, null for once.
        if (oldVersion < 6 && !hasColumn(db, "reminders", "repeat_rule")) db.execSQL("ALTER TABLE reminders ADD COLUMN repeat_rule TEXT")
        // The time a repeating reminder was asked for (version 7), so a daylight-saving gap or a trip doesn't move it for good.
        if (oldVersion < 7 && !hasColumn(db, "reminders", "repeat_time")) db.execSQL("ALTER TABLE reminders ADD COLUMN repeat_time TEXT")
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.any { it == column }
        }

    /**
     * Per-script access switches (schema version 4, [com.mtwabpin.kolahal.script.ScriptAccess]).
     * Before, switching a script on allowed everything it declared with `@uses`: switched-on
     * scripts keep that; switched-off ones ask when they are switched on.
     */
    private fun addScriptAccess(db: SQLiteDatabase) {
        if (!hasColumn(db, "scripts", "access")) db.execSQL("ALTER TABLE scripts ADD COLUMN access TEXT")
        db.query("scripts", arrayOf("id", "code"), "enabled = 1", null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val uses = com.mtwabpin.kolahal.script.ScriptMeta.uses(c.getString(1))
                if (uses.isEmpty()) continue
                val access = com.mtwabpin.kolahal.script.ScriptAccess.encode(uses.associateWith { true })
                db.update("scripts", android.content.ContentValues().apply { put("access", access) }, "id = ?", arrayOf(c.getLong(0).toString()))
            }
        }
    }

    companion object {
        private const val NAME = "kolahal.db"
        private const val VERSION = 7

        /** Run headers (`── hn · 22:40 ──`) are gone (version 5): every line shows its time and its script. */
        internal fun dropRunHeaders(db: SQLiteDatabase): Int =
            db.delete("logs", "kind = ? AND ctx LIKE ?", arrayOf(LogKind.INFO.key, "%\"${LogContext.RUN_HEADER}\"%"))

        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(context).also { instance = it } }
    }
}
