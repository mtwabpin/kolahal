package com.mtwabpin.kolahal.feeds

import android.content.Context

/**
 * Changes to your subscriptions, the same from the `feed` command and the Feeds screen. Each one
 * changes the stored feeds ([FeedRepository]) and the fetch schedule ([FeedScheduler]) together, so
 * the two never disagree about which feeds are fetched, or how often.
 */
class FeedSubscriptions(private val context: Context) {
    private val repo = FeedRepository.get(context)

    /** A feed after [subscribe]: [isNew] is false when the address was already subscribed. */
    data class Subscribed(val feed: Feed, val isNew: Boolean)

    /**
     * Subscribes to [url], and fetches it now. An address already subscribed (after normalising it, as
     * the repository stores it) gives that feed, and is not fetched again.
     * @throws IllegalArgumentException when the address isn't an http(s) URL.
     */
    fun subscribe(url: String): Subscribed {
        val normalized = FeedUrl.normalize(url) ?: throw IllegalArgumentException("Not an http(s) URL: $url")
        repo.byUrl(normalized)?.let { return Subscribed(it, isNew = false) }
        val feed = repo.add(normalized)
        FeedScheduler.sync(context)
        FeedScheduler.fetchNow(context, feed.id)
        return Subscribed(feed, isNew = true)
    }

    /** Moves feed [id] to [url] and fetches it there ([FeedRepository.setUrl]). */
    fun moveTo(id: Long, url: String): Feed = repo.setUrl(id, url).also { FeedScheduler.fetchNow(context, it.id) }

    fun remove(id: Long) {
        repo.remove(id)
        FeedScheduler.sync(context)
    }

    fun setEnabled(id: Long, on: Boolean) {
        repo.setEnabled(id, on)
        FeedScheduler.sync(context)
    }

    /** Sets how often feed [id] is fetched. Returns the interval it gets: never under [FeedScheduler.MIN_INTERVAL_MIN]. */
    fun setInterval(id: Long, minutes: Int): Int {
        repo.setInterval(id, minutes)
        FeedScheduler.sync(context)
        return minutes.coerceAtLeast(FeedScheduler.MIN_INTERVAL_MIN)
    }

    /** Subscribes to the feeds in an OPML file, and fetches the new ones. Returns how many were new. */
    fun importOpml(text: String): Int {
        val added = repo.importOpml(text)
        FeedScheduler.sync(context)
        if (added > 0) FeedScheduler.fetchNow(context)
        return added
    }
}
