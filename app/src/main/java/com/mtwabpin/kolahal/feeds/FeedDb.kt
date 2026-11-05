package com.mtwabpin.kolahal.feeds

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Feed subscriptions and their fetched items. Kept apart from `kolahal.db` so the feed schema
 * can evolve without touching the log/script database. WAL lets the fetch worker's thread write
 * while home reads (both in the main process).
 */
class FeedDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS feeds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT,
                added_at INTEGER NOT NULL,
                last_fetch INTEGER,
                last_status TEXT,
                etag TEXT,
                last_modified TEXT,
                notify INTEGER NOT NULL DEFAULT 0,
                interval_min INTEGER NOT NULL DEFAULT 60,
                enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                feed_id INTEGER NOT NULL REFERENCES feeds(id) ON DELETE CASCADE,
                guid TEXT NOT NULL,
                title TEXT NOT NULL,
                link TEXT,
                published_text TEXT,
                summary TEXT,
                fetched_at INTEGER NOT NULL,
                read INTEGER NOT NULL DEFAULT 0,
                UNIQUE (feed_id, guid)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS items_feed_read ON items(feed_id, read)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        private const val NAME = "feeds.db"
        private const val VERSION = 1

        /** Items kept per feed; the oldest read ones go first. */
        const val MAX_ITEMS_PER_FEED = 300

        @Volatile
        private var instance: FeedDb? = null

        fun get(context: Context): FeedDb =
            instance ?: synchronized(this) { instance ?: FeedDb(context).also { instance = it } }
    }
}
