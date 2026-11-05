package com.mtwabpin.kolahal.feeds

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What a feed keeps when it holds more than [FeedDb.MAX_ITEMS_PER_FEED] items. */
@RunWith(AndroidJUnit4::class)
class FeedPruneTest {
    private val repo = FeedRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
    // Switched off, so the scheduler doesn't fetch it from example.com meanwhile.
    private val feed = repo.add("https://example.com/prune-test-${System.nanoTime()}.xml").also { repo.setEnabled(it.id, false) }

    @After fun tearDown() = repo.remove(feed.id)

    private fun items(range: IntRange) = range.map { FeedRepository.NewItem("g$it", "Item $it", "https://example.com/$it", null, null) }

    @Test fun aReadItemTheFeedStillListsIsNotNewAgain() {
        // 300 unread items fill the feed; the newest five are what the feed lists, and you read them.
        repo.insertItems(feed.id, items(1..300))
        val listed = items(296..300)
        repo.markRead(repo.items(feed.id, limit = 400).filter { it.guid in listed.map { l -> l.guid } }.map { it.id })
        // A new post: the prune drops older unread items, never the ones the feed still lists.
        val fresh = repo.insertItems(feed.id, listed + items(301..301))
        assertEquals(listOf("g301"), fresh.map { it.guid })
        val kept = repo.items(feed.id, limit = 400).associateBy { it.guid }
        assertTrue(listed.all { it.guid in kept })
        // The next fetch of the same list finds nothing new.
        assertTrue(repo.insertItems(feed.id, listed + items(301..301)).isEmpty())
    }
}
