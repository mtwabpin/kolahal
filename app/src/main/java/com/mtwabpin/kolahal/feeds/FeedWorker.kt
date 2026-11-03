package com.mtwabpin.kolahal.feeds

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.notify.Notifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fetches due feeds (or the requested one when forced) and reports new items to the terminal log
 * and, for feeds with notify on, as one notification per feed. Runs in the main process.
 */
class FeedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = FeedRepository.get(ctx)
        val force = inputData.getBoolean(KEY_FORCE, false)
        val feedId = inputData.getLong(KEY_FEED_ID, -1L)

        // feeds.wifi: scheduled runs wait for an unmetered network; a fetch you asked for always runs.
        if (!force && Config.get(ctx)[Keys.FEEDS_WIFI] &&
            ctx.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered != false
        ) return Result.success()

        val now = System.currentTimeMillis()
        val targets = when {
            feedId >= 0 -> listOfNotNull(repo.byId(feedId))
            force -> repo.feeds().filter { it.enabled }
            else -> repo.feeds().filter { FeedScheduler.isDue(it, now) }
        }
        if (targets.isEmpty()) return Result.success()

        // Android stops a worker after about ten minutes, and what it had fetched by then would be
        // stored but never reported: report what is done before that; the rest is due again.
        val fetcher = FeedFetcher(ctx)
        // A few at a time: each mostly waits on the network, and a feed that doesn't answer no longer
        // holds back the rest. Each result keeps its feed's place, so the report's order is the same.
        val results = arrayOfNulls<FeedFetcher.Result>(targets.size)
        kotlinx.coroutines.withTimeoutOrNull(FETCH_DEADLINE_MS.milliseconds) {
            val slots = Semaphore(PARALLEL_FETCHES)
            coroutineScope {
                targets.forEachIndexed { i, feed -> launch { slots.withPermit { results[i] = fetcher.fetchOne(feed) } } }
            }
        }
        report(ctx, results.filterNotNull(), explicit = force || feedId >= 0, from = inputData.getLong(KEY_FROM, -1L).takeIf { it >= 0 })
        return Result.success()
    }

    /** Up to `feeds.log-lines` lines per feed; [from], the `feed fetch` line they answer. */
    private fun report(ctx: Context, results: List<FeedFetcher.Result>, explicit: Boolean, from: Long?) {
        val config = Config.get(ctx)
        val repo = FeedRepository.get(ctx)
        val toLog = config[Keys.FEEDS_LOG]
        val maxLines = config[Keys.FEEDS_LOG_LINES]
        val shownRead = config[Keys.FEEDS_READ_SHOWN]
        val lines = ArrayList<LogRepository.Pending>()
        val ts = System.currentTimeMillis()
        for ((fetched, newItems, error, first) in results) {
            // Re-read so a title learned during this fetch is used.
            val feed = repo.byId(fetched.id) ?: fetched
            val source = feed.shortName
            val feedCtx = mapOf(CTX_FEED to feed.id.toString()) + (from?.let { mapOf(LogContext.FROM to it.toString()) } ?: emptyMap())
            if (error != null) {
                // Scheduled failures stay in last_status; only a fetch the user asked for is logged.
                if (explicit) {
                    lines += LogRepository.Pending(
                        LogKind.ERROR, "$source: $error", source,
                        LogContext(feedCtx + (LogContext.URL to feed.url)), ts,
                    )
                }
                continue
            }
            if (newItems.isEmpty()) continue
            // feeds.backlog-read: what a feed already had when you subscribed is not "new" to you.
            if (first && config[Keys.FEEDS_BACKLOG_READ]) {
                repo.markRead(newItems.map { it.id })
                if (toLog) lines += LogRepository.Pending(
                    LogKind.INFO, "$source: subscribed · ${newItems.size} earlier posts marked read", source,
                    LogContext.of(LogContext.COMMAND to "feed all ${feed.id}") + feedCtx, ts,
                )
                continue
            }
            if (toLog) {
                val shown = newItems.take(maxLines)
                for ((id, _, _, title, link) in shown) {
                    lines += LogRepository.Pending(
                        LogKind.SCRIPT,
                        title,
                        source,
                        LogContext.of(LogContext.URL to link, LogContext.COPY to title, CTX_ITEM to id.toString()) + feedCtx,
                        ts,
                    )
                }
                if (shownRead) repo.markRead(shown.map { it.id })
                val more = newItems.size - shown.size
                if (more > 0) {
                    lines += LogRepository.Pending(
                        LogKind.SCRIPT, "+$more more", source,
                        LogContext.of(LogContext.COMMAND to "feed read ${feed.id}") + feedCtx, ts,
                    )
                }
            }
            // A notification problem (bad link, system refusal) must not lose the log lines.
            if (feed.notify) runCatching { notify(ctx, feed, newItems) }
        }
        if (lines.isEmpty()) return
        val logs = LogRepository.get(ctx)
        logs.addAll(lines)
        logs.trim()
    }

    private fun notify(ctx: Context, feed: Feed, items: List<FeedItem>) {
        val first = items.first()
        val title = feed.title?.takeIf { it.isNotBlank() } ?: feed.shortName
        val body = if (items.size == 1) first.title else "${items.size} new — ${first.title}"
        // Stable id per feed: a newer batch replaces the previous notification instead of stacking.
        Notifier.post(ctx, "feed-${feed.id}", title, body, first.link, id = notificationId(feed.id))
    }

    companion object {
        const val KEY_FEED_ID = "feed_id"
        const val KEY_FORCE = "force"
        /** Fetching stops here, well before the system stops the worker. */
        private const val FETCH_DEADLINE_MS = 8 * 60_000L

        /** Feeds fetched at once: OkHttp allows five a host, and each body can be up to 3 MB in memory. */
        private const val PARALLEL_FETCHES = 4
        const val KEY_FROM = "from"

        /** Log context key carrying the feed id on feed lines. */
        const val CTX_FEED = "feed"

        /** Log context key carrying the item id: opening the line marks that item read. */
        const val CTX_ITEM = "feed_item"

        /** Notification id for [feedId]; stable across runs so each feed has at most one. */
        internal fun notificationId(feedId: Long): Int = ("feed:$feedId").hashCode()
    }
}
