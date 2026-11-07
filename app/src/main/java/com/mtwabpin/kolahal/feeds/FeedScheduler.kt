package com.mtwabpin.kolahal.feeds

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * One periodic job ("feeds") checks which feeds are due, each by its own interval. The job runs as
 * often as the most frequent feed. It runs in the main process as a plain CoroutineWorker.
 */
object FeedScheduler {
    const val UNIQUE_NAME = "feeds"
    private const val NOW_NAME = "feeds-now"
    const val PERIOD_MIN = 15L

    /** Minimum interval for a feed. It is WorkManager's periodic minimum, and it also keeps us polite to servers. */
    const val MIN_INTERVAL_MIN = 15

    private val network by lazy { Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build() }

    /**
     * Enqueues the periodic check when any feed exists, and cancels it when there is none. It is safe
     * to call often. To avoid waking up with nothing to do, the check:
     * - runs as often as the most frequent feed, but not more often than every [PERIOD_MIN] minutes
     * - with `feeds.wifi`, runs only on unmetered networks
     */
    fun sync(context: Context) {
        val wm = WorkManager.getInstance(context)
        val feeds = FeedRepository.get(context).feeds().filter { it.enabled }
        if (feeds.isEmpty()) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val period = feeds.minOf { it.intervalMin }.toLong().coerceAtLeast(PERIOD_MIN)
        val wifiOnly = com.mtwabpin.kolahal.data.Config.get(context)[com.mtwabpin.kolahal.data.Keys.FEEDS_WIFI]
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<FeedWorker>(period, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(UNIQUE_NAME)
            .build()
        // UPDATE, not REPLACE: a new sync keeps the schedule. Installs that enqueued the old
        // 30-minute period still get the new period.
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Fetches [feedId], or every enabled feed, right away and ignores intervals. If a fetch is already
     * running or waiting for a network, it keeps its place and this one runs after it. So a second
     * `feed fetch` or a changed address is fetched too. Unchanged feeds answer the second time with
     * "not modified".
     */
    fun fetchNow(context: Context, feedId: Long? = null) {
        val request = OneTimeWorkRequestBuilder<FeedWorker>()
            .setConstraints(network)
            .setInputData(
                workDataOf(
                    FeedWorker.KEY_FEED_ID to (feedId ?: -1L), FeedWorker.KEY_FORCE to true,
                    // For `feed fetch`, the results are shown as the command's output.
                    FeedWorker.KEY_FROM to (com.mtwabpin.kolahal.data.LogRepository.get(context).currentCause ?: -1L),
                ),
            )
            .addTag(UNIQUE_NAME)
            .build()
        val name = if (feedId == null) NOW_NAME else "$NOW_NAME-$feedId"
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** True when a scheduled run at [now] should fetch [feed]. */
    fun isDue(feed: Feed, now: Long = System.currentTimeMillis()): Boolean =
        isDue(feed.enabled, feed.lastFetch, feed.intervalMin, now)

    /**
     * Pure due check. A small slack lets every 15-minute run pick up a feed with a 15-minute
     * interval, even when WorkManager runs a little early.
     */
    fun isDue(enabled: Boolean, lastFetch: Long?, intervalMin: Int, now: Long): Boolean {
        if (!enabled) return false
        if (lastFetch == null) return true
        val interval = intervalMin.coerceAtLeast(MIN_INTERVAL_MIN) * 60_000L
        return now - lastFetch >= interval - SLACK_MS
    }

    private const val SLACK_MS = 3 * 60_000L
}
