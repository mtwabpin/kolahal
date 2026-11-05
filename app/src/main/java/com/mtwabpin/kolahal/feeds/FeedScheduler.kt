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
 * One periodic job ("feeds", as often as the most frequent feed) checks which feeds are due by
 * their own interval. Runs in the main process as a plain CoroutineWorker.
 */
object FeedScheduler {
    const val UNIQUE_NAME = "feeds"
    private const val NOW_NAME = "feeds-now"
    const val PERIOD_MIN = 15L

    /** Floor for a feed's interval (WorkManager's periodic floor; also keeps us polite to servers). */
    const val MIN_INTERVAL_MIN = 15

    private val network by lazy { Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build() }

    /**
     * Enqueues the periodic check when any feed exists, cancels it when none. Safe to call often.
     * It wakes as often as the most frequent feed (no sooner than every [PERIOD_MIN] minutes),
     * and with `feeds.wifi` only on unmetered networks, instead of waking to find nothing to do.
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
        // UPDATE (not REPLACE): re-syncing keeps the schedule, but installs that enqueued the old
        // 30-minute period pick up the new one.
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Fetches [feedId] (or every enabled feed) right away, ignoring intervals. One already waiting
     * for a network or running keeps its place, and this one runs after it, so a second `feed fetch`
     * or a changed address is fetched too (unchanged feeds answer the second time with "not modified").
     */
    fun fetchNow(context: Context, feedId: Long? = null) {
        val request = OneTimeWorkRequestBuilder<FeedWorker>()
            .setConstraints(network)
            .setInputData(
                workDataOf(
                    FeedWorker.KEY_FEED_ID to (feedId ?: -1L), FeedWorker.KEY_FORCE to true,
                    // `feed fetch`: what it finds is the command's output.
                    FeedWorker.KEY_FROM to (com.mtwabpin.kolahal.data.LogRepository.get(context).currentCause ?: -1L),
                ),
            )
            .addTag(UNIQUE_NAME)
            .build()
        val name = if (feedId == null) NOW_NAME else "$NOW_NAME-$feedId"
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Whether [feed] should be fetched by a scheduled run at [now]. */
    fun isDue(feed: Feed, now: Long = System.currentTimeMillis()): Boolean =
        isDue(feed.enabled, feed.lastFetch, feed.intervalMin, now)

    /**
     * Pure due check. A small slack lets a feed on a 15-minute interval be picked up by every
     * 15-minute run even when WorkManager fires a little early.
     */
    fun isDue(enabled: Boolean, lastFetch: Long?, intervalMin: Int, now: Long): Boolean {
        if (!enabled) return false
        if (lastFetch == null) return true
        val interval = intervalMin.coerceAtLeast(MIN_INTERVAL_MIN) * 60_000L
        return now - lastFetch >= interval - SLACK_MS
    }

    private const val SLACK_MS = 3 * 60_000L
}
