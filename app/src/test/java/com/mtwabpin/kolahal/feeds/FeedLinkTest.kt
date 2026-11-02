package com.mtwabpin.kolahal.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedLinkTest {
    private val base = "https://example.com/blog/feed.xml"

    @Test
    fun keepsAbsoluteHttpLinks() {
        assertEquals("https://a.example/post?id=1", FeedFetcher.safeLink("https://a.example/post?id=1", base))
        assertEquals("http://a.example/x", FeedFetcher.safeLink("  http://a.example/x \n", base))
        assertEquals("https://a.example/", FeedFetcher.safeLink("HTTPS://A.example/", base))
    }

    @Test
    fun resolvesRelativeLinksAgainstTheFeed() {
        assertEquals("https://example.com/blog/post-1", FeedFetcher.safeLink("post-1", base))
        assertEquals("https://example.com/p/2", FeedFetcher.safeLink("/p/2", base))
        assertEquals("https://other.example/x", FeedFetcher.safeLink("//other.example/x", base))
        assertEquals("https://example.com/blog/feed.xml?page=2", FeedFetcher.safeLink("?page=2", base))
    }

    @Test
    fun dropsNonHttpSchemes() {
        assertNull(FeedFetcher.safeLink("javascript:alert(1)", base))
        assertNull(FeedFetcher.safeLink("JavaScript:alert(1)", base))
        assertNull(FeedFetcher.safeLink("intent://scan/#Intent;scheme=zxing;end", base))
        assertNull(FeedFetcher.safeLink("file:///sdcard/x", base))
        assertNull(FeedFetcher.safeLink("content://com.example/x", base))
        assertNull(FeedFetcher.safeLink("mailto:a@b.c", base))
        assertNull(FeedFetcher.safeLink("data:text/html,<b>x</b>", base))
    }

    @Test
    fun dropsEmptyAndUnresolvable() {
        assertNull(FeedFetcher.safeLink(null, base))
        assertNull(FeedFetcher.safeLink("   ", base))
        assertNull(FeedFetcher.safeLink("relative", "not a url"))
        assertEquals("https://a.example/", FeedFetcher.safeLink("https://a.example/", "not a url"))
    }

    @Test
    fun notificationIdIsStablePerFeed() {
        // The same id on every run, so a feed's new notification replaces its last one.
        assertEquals("feed:7".hashCode(), FeedWorker.notificationId(7))
        assertNotEquals(FeedWorker.notificationId(7), FeedWorker.notificationId(8))
    }

    @Test
    fun schedulerPeriodHonoursTheMinimumInterval() {
        assertEquals(FeedScheduler.MIN_INTERVAL_MIN.toLong(), FeedScheduler.PERIOD_MIN)
    }
}
