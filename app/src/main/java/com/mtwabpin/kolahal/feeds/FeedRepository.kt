package com.mtwabpin.kolahal.feeds

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.number
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A subscription row, including the HTTP validators used for conditional GETs. */
data class Feed(
    val id: Long,
    val url: String,
    val title: String?,
    val lastFetch: Long?,
    val etag: String?,
    val lastModified: String?,
    val notify: Boolean,
    val intervalMin: Int,
    val enabled: Boolean,
) {
    /** Label for log lines and notifications. */
    val shortName: String get() = FeedUrl.shortName(title, url)
}

/** A feed with its item counts, for listings. */
data class FeedInfo(
    val id: Long,
    val url: String,
    val title: String?,
    val unread: Int,
    val total: Int,
    val lastFetch: Long?,
    val lastStatus: String?,
    val notify: Boolean,
    val intervalMin: Int,
    val enabled: Boolean,
)

data class FeedItem(
    val id: Long,
    val feedId: Long,
    val guid: String,
    val title: String,
    val link: String?,
    val publishedText: String?,
    val summary: String?,
    val fetchedAt: Long,
    val read: Boolean,
)

/** Subscriptions and items. Every write increases [version], so screens can query again. */
class FeedRepository private constructor(private val context: Context) {
    private val db = FeedDb.get(context)

    private val _version = MutableStateFlow(0)

    /** Increases on every change made in this process. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /**
     * Subscribes to [url], after normalising it. Adding an existing URL returns the existing feed.
     * @throws IllegalArgumentException when the address isn't an http(s) URL.
     */
    fun add(url: String, title: String? = null): Feed {
        val normalized = FeedUrl.normalize(url) ?: throw IllegalArgumentException("Not an http(s) URL: $url")
        byUrl(normalized)?.let { return it }
        db.writableDatabase.insertWithOnConflict("feeds", null, ContentValues().apply {
            put("url", normalized)
            put("title", title?.trim()?.takeIf { it.isNotEmpty() })
            put("added_at", System.currentTimeMillis())
            // New subscriptions take their values from the Feeds settings (feeds.interval, feeds.notify).
            val config = com.mtwabpin.kolahal.data.Config.get(context)
            put("interval_min", config[com.mtwabpin.kolahal.data.Keys.FEEDS_INTERVAL].coerceAtLeast(FeedScheduler.MIN_INTERVAL_MIN))
            put("notify", if (config[com.mtwabpin.kolahal.data.Keys.FEEDS_NOTIFY]) 1 else 0)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        changed()
        return byUrl(normalized) ?: error("Couldn't add the feed")
    }

    /**
     * Moves feed [id] to [url], after normalising it. This is for a feed subscribed with a typo.
     * - Removed: what fetches learnt from the old address (its validators, the last fetch time and status).
     * - Kept: its items, title and settings.
     * @throws IllegalArgumentException when the address isn't an http(s) URL, another feed has it,
     * or there's no feed [id].
     */
    fun setUrl(id: Long, url: String): Feed {
        val normalized = FeedUrl.normalize(url) ?: throw IllegalArgumentException("Not an http(s) URL: $url")
        val feed = byId(id) ?: throw IllegalArgumentException("No feed #$id")
        if (normalized == feed.url) return feed
        if (byUrl(normalized) != null) throw IllegalArgumentException("Already subscribed to $normalized")
        updateFeed(id) {
            put("url", normalized)
            putNull("etag")
            putNull("last_modified")
            putNull("last_fetch")
            putNull("last_status")
        }
        return byId(id) ?: error("Couldn't change the feed's address")
    }

    fun remove(id: Long) {
        val w = db.writableDatabase
        w.transaction {
            // Delete items explicitly as well as by the cascade, in case foreign keys were off for this connection.
            w.delete("items", "feed_id = ?", arrayOf(id.toString()))
            w.delete("feeds", "id = ?", arrayOf(id.toString()))
        }
        changed()
    }

    fun byId(id: Long): Feed? = queryFeeds("id = ?", arrayOf(id.toString())).firstOrNull()

    fun byUrl(url: String): Feed? = queryFeeds("url = ?", arrayOf(url)).firstOrNull()

    /** Returns every subscription (raw rows), oldest first. */
    fun feeds(): List<Feed> = queryFeeds(null, null)

    /** Returns subscriptions with unread and total counts, oldest first. */
    fun all(): List<FeedInfo> {
        val out = ArrayList<FeedInfo>()
        db.readableDatabase.rawQuery(
            """
            SELECT f.id, f.url, f.title, f.last_fetch, f.last_status, f.notify, f.interval_min, f.enabled,
                   COALESCE(SUM(CASE WHEN i.read = 0 THEN 1 ELSE 0 END), 0), COUNT(i.id)
            FROM feeds f LEFT JOIN items i ON i.feed_id = f.id
            GROUP BY f.id ORDER BY f.id
            """.trimIndent(),
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += FeedInfo(
                    id = c.getLong(0),
                    url = c.getString(1),
                    title = c.getString(2),
                    lastFetch = c.lastFetch(),
                    lastStatus = c.getString(4),
                    notify = c.getInt(5) != 0,
                    intervalMin = c.getInt(6),
                    enabled = c.getInt(7) != 0,
                    unread = c.getInt(8),
                    total = c.getInt(9),
                )
            }
        }
        return out
    }

    fun setNotify(id: Long, notify: Boolean) = updateFeed(id) { put("notify", if (notify) 1 else 0) }

    /** Sets a feed's refresh interval. It is never below [FeedScheduler.MIN_INTERVAL_MIN]. */
    fun setInterval(id: Long, minutes: Int) =
        updateFeed(id) { put("interval_min", minutes.coerceAtLeast(FeedScheduler.MIN_INTERVAL_MIN)) }

    fun setEnabled(id: Long, enabled: Boolean) = updateFeed(id) { put("enabled", if (enabled) 1 else 0) }

    /** Returns items, newest first. It can limit them to one feed, to unread items, or both. */
    fun items(feedId: Long? = null, unreadOnly: Boolean = false, limit: Int = 100): List<FeedItem> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (feedId != null) { where += "feed_id = ?"; args += feedId.toString() }
        if (unreadOnly) where += "read = 0"
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        args += limit.toString()
        val out = ArrayList<FeedItem>()
        db.readableDatabase.rawQuery(
            "SELECT $ITEM_COLUMNS FROM items $clause ORDER BY id DESC LIMIT ?",
            args.toTypedArray(),
        ).use { c -> while (c.moveToNext()) out += c.toItem() }
        return out
    }

    fun markRead(itemId: Long) {
        val n = db.writableDatabase.update(
            "items", ContentValues().apply { put("read", 1) }, "id = ? AND read = 0", arrayOf(itemId.toString()),
        )
        if (n > 0) changed()
    }

    /** Marks one item unread again. Used for a second swipe in its feed's list, or a swipe on a read item. */
    fun markUnread(itemId: Long) {
        val n = db.writableDatabase.update(
            "items", ContentValues().apply { put("read", 0) }, "id = ? AND read = 1", arrayOf(itemId.toString()),
        )
        if (n > 0) changed()
    }

    /** Marks exactly [itemIds] read, in one transaction. */
    fun markRead(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        val values = ContentValues().apply { put("read", 1) }
        val w = db.writableDatabase
        var n = 0
        w.transaction {
            for (id in itemIds.distinct()) n += w.update("items", values, "id = ? AND read = 0", arrayOf(id.toString()))
        }
        if (n > 0) changed()
    }

    /** Counts unread items, of all feeds or of one feed (the same scope as [items] with unreadOnly). */
    fun unreadCount(feedId: Long?): Int {
        val (clause, args) = if (feedId == null) "" to emptyArray() else " AND feed_id = ?" to arrayOf(feedId.toString())
        return db.readableDatabase.number("SELECT COUNT(*) FROM items WHERE read = 0$clause", *args).toInt()
    }

    /** Marks every item read, or only those of [feedId]. */
    fun markAllRead(feedId: Long? = null) {
        val values = ContentValues().apply { put("read", 1) }
        val n = if (feedId == null) {
            db.writableDatabase.update("items", values, "read = 0", null)
        } else {
            db.writableDatabase.update("items", values, "feed_id = ? AND read = 0", arrayOf(feedId.toString()))
        }
        if (n > 0) changed()
    }

    /** Counts the stored items of [feedId], read or not. 0 means no fetch has succeeded yet. */
    fun itemCount(feedId: Long): Int = db.readableDatabase.number("SELECT COUNT(*) FROM items WHERE feed_id = ?", feedId.toString()).toInt()

    // ---- used by FeedFetcher ----

    /**
     * Inserts parsed items, skips known guids, then prunes. It inserts the oldest first, so newer
     * items get higher ids. Returns the newly inserted items, newest first.
     */
    internal fun insertItems(feedId: Long, items: List<NewItem>): List<FeedItem> {
        if (items.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val inserted = ArrayList<FeedItem>()
        val w = db.writableDatabase
        w.transaction {
            for ((guid, title, link, publishedText, summary) in items.asReversed()) {
                val rowId = w.insertWithOnConflict("items", null, ContentValues().apply {
                    put("feed_id", feedId)
                    put("guid", guid)
                    put("title", title)
                    put("link", link)
                    put("published_text", publishedText)
                    put("summary", summary)
                    put("fetched_at", now)
                }, SQLiteDatabase.CONFLICT_IGNORE)
                if (rowId != -1L) {
                    inserted += FeedItem(rowId, feedId, guid, title, link, publishedText, summary, now, false)
                }
            }
            if (inserted.isNotEmpty()) prune(w, feedId, items.map { it.guid })
        }
        if (inserted.isEmpty()) return emptyList()
        changed()
        // Only items that the prune kept count as new. A "N new" line must not count dropped items.
        val kept = w.rawQuery("SELECT id FROM items WHERE feed_id = ?", arrayOf(feedId.toString())).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }
        return inserted.filter { it.id in kept }.asReversed()
    }

    internal data class NewItem(
        val guid: String,
        val title: String,
        val link: String?,
        val publishedText: String?,
        val summary: String?,
    )

    /** Records a fetch attempt. Validators are replaced only when [etag] or [lastModified] is given. */
    internal fun recordFetch(
        id: Long,
        status: String,
        etag: String? = null,
        lastModified: String? = null,
        title: String? = null,
    ) = updateFeed(id) {
        put("last_fetch", System.currentTimeMillis())
        put("last_status", status)
        etag?.let { put("etag", it) }
        lastModified?.let { put("last_modified", it) }
        title?.let { put("title", it) }
    }

    /**
     * Keeps [FeedDb.MAX_ITEMS_PER_FEED] items: unread before read, newer before older.
     * It never deletes an item that the feed still [listed] (at most
     * [com.mtwabpin.kolahal.script.RssParser.MAX_ITEMS]). If it did, the next fetch would find the
     * item missing and add it again as new, even though it was read.
     */
    private fun prune(w: SQLiteDatabase, feedId: Long, listed: List<String>) {
        w.execSQL(
            """
            DELETE FROM items WHERE feed_id = ? AND guid NOT IN (${listed.joinToString(",") { "?" }}) AND id NOT IN (
                SELECT id FROM items WHERE feed_id = ? ORDER BY read ASC, id DESC LIMIT ?
            )
            """.trimIndent(),
            (listOf<Any>(feedId) + listed + listOf(feedId, FeedDb.MAX_ITEMS_PER_FEED)).toTypedArray(),
        )
    }

    private fun updateFeed(id: Long, block: ContentValues.() -> Unit) {
        val n = db.writableDatabase.update("feeds", ContentValues().apply(block), "id = ?", arrayOf(id.toString()))
        if (n > 0) changed()
    }

    private fun queryFeeds(where: String?, args: Array<String>?): List<Feed> {
        val out = ArrayList<Feed>()
        db.readableDatabase.query(
            "feeds",
            arrayOf(
                "id", "url", "title", "last_fetch",
                "etag", "last_modified", "notify", "interval_min", "enabled",
            ),
            where, args, null, null, "id",
        ).use { c ->
            while (c.moveToNext()) {
                out += Feed(
                    id = c.getLong(0),
                    url = c.getString(1),
                    title = c.getString(2),
                    lastFetch = c.lastFetch(),
                    etag = c.getString(4),
                    lastModified = c.getString(5),
                    notify = c.getInt(6) != 0,
                    intervalMin = c.getInt(7),
                    enabled = c.getInt(8) != 0,
                )
            }
        }
        return out
    }

    private fun Cursor.toItem() = FeedItem(
        id = getLong(0),
        feedId = getLong(1),
        guid = getString(2),
        title = getString(3),
        link = getString(4),
        publishedText = getString(5),
        summary = getString(6),
        fetchedAt = getLong(7),
        read = getInt(8) != 0,
    )

    private fun Cursor.lastFetch(): Long? = if (isNull(3)) null else getLong(3)

    private fun changed() = _version.update { it + 1 }

    companion object : Singleton<FeedRepository>(::FeedRepository) {
        private const val ITEM_COLUMNS = "id, feed_id, guid, title, link, published_text, summary, fetched_at, read"
    }
}
