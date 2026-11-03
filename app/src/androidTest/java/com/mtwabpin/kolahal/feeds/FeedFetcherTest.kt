package com.mtwabpin.kolahal.feeds

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.script.Net
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** A feed fetched from a web server on this phone: what is stored, what a refetch asks, and what a failure records. */
@RunWith(AndroidJUnit4::class)
class FeedFetcherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = FeedRepository.get(context)
    private val fetcher = FeedFetcher(context)
    private val server = FeedServer()
    // Switched off, so a scheduled fetch of the feeds due leaves it to the test.
    private val feed = repo.add("${server.url}fetchtest-${System.nanoTime()}.xml").also { repo.setEnabled(it.id, false) }

    @After fun tearDown() {
        repo.remove(feed.id)
        server.close()
    }

    /**
     * A web server on 127.0.0.1: answers each connection with [answer], on a thread of its own, and
     * keeps each request's header lines (names lowercased).
     */
    private class FeedServer : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        val requests = CopyOnWriteArrayList<Map<String, String>>()
        @Volatile var answer: (Socket) -> Unit = { it.getOutputStream().write(response("404 Not Found", "")) }

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) {
                        client.use { c ->
                            val reader = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                            val head = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                            requests += head.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                            // The client may hang up mid-answer (a body too large, a cancel).
                            runCatching { answer(c) }
                        }
                    }
                }
            }
        }

        override fun close() = socket.close()
    }

    /** Every request gets [status], [headers] and [body]. */
    private fun serve(status: String, body: String, vararg headers: String) {
        server.answer = { it.getOutputStream().write(response(status, body, *headers)) }
    }

    private fun stored() = repo.byId(feed.id)!!

    private fun status() = repo.all().single { it.id == feed.id }.lastStatus

    private fun fetchOne() = runBlocking { fetcher.fetchOne(stored()) }

    /**
     * The first fetch stores the items as the feed's backlog, newest first, with safe links; takes the
     * feed's title and its ETag and Last-Modified; and asks as Kolahal, for a feed, with nothing to match yet.
     */
    @Test fun theFirstFetchStoresTheItemsTheTitleAndTheValidators() {
        serve("200 OK", rss("Cptest Feed", item("p2", "Second post", "/posts/2"), item("p1", "First post", "javascript:alert(1)")), ETAG_1, MODIFIED, RSS_TYPE)
        val result = fetchOne()
        assertNull(result.error)
        assertTrue("the backlog, not news", result.first)
        // Newest first, as the feed lists them; a relative link resolved against the feed, a script link dropped.
        assertEquals(listOf("Second post", "First post"), result.newItems.map { it.title })
        assertEquals(listOf("${server.url}posts/2", null), result.newItems.map { it.link })
        assertEquals("ok · 2 items", status())
        with(stored()) {
            assertEquals("Cptest Feed", title)
            assertEquals("\"v1\"", etag)
            assertEquals(MODIFIED.substringAfter(": "), lastModified)
        }
        val asked = server.requests.single()
        assertEquals(Net.USER_AGENT, asked["user-agent"])
        assertTrue(asked["accept"], asked["accept"].orEmpty().startsWith("application/rss+xml, application/atom+xml"))
        assertFalse("nothing to match yet", "if-none-match" in asked || "if-modified-since" in asked)
    }

    /** The next fetch sends the validators back; a 304 then costs nothing and changes no item. */
    @Test fun anUnchangedFeedIsA304() {
        serve("200 OK", rss("Cptest Feed", item("p1", "First post", "/posts/1")), ETAG_1, MODIFIED, RSS_TYPE)
        fetchOne()
        serve("304 Not Modified", "")
        val result = fetchOne()
        assertNull(result.error)
        assertEquals(emptyList<FeedItem>(), result.newItems)
        assertEquals("not modified", status())
        assertEquals(1, repo.itemCount(feed.id))
        val asked = server.requests.last()
        assertEquals("\"v1\"", asked["if-none-match"])
        assertEquals(MODIFIED.substringAfter(": "), asked["if-modified-since"])
    }

    /** A later fetch returns only the items it hasn't seen, keeps the title it has, and takes the new ETag. */
    @Test fun aLaterFetchReturnsOnlyWhatIsNew() {
        serve("200 OK", rss("Cptest Feed", item("p1", "First post", "/posts/1")), ETAG_1, RSS_TYPE)
        fetchOne()
        serve("200 OK", rss("Renamed Feed", item("p2", "Second post", "/posts/2"), item("p1", "First post", "/posts/1")), "ETag: \"v2\"", RSS_TYPE)
        val result = fetchOne()
        assertFalse(result.first)
        assertEquals(listOf("Second post"), result.newItems.map { it.title })
        assertEquals("ok · 1 new", status())
        assertEquals("Cptest Feed", stored().title)
        assertEquals("\"v2\"", stored().etag)
        assertEquals(2, repo.itemCount(feed.id))
    }

    /** A failed attempt sets the feed's last fetch too: the first one that works still brings the backlog, not news. */
    @Test fun aFeedThatFailedFirstStillGetsItsBacklogAsTheFirst() {
        serve("500 Internal Server Error", "")
        assertEquals("HTTP 500", fetchOne().error)
        assertNotNull(stored().lastFetch)
        serve("200 OK", rss("Cptest Feed", item("p2", "Second post", "/posts/2"), item("p1", "First post", "/posts/1")), RSS_TYPE)
        val result = fetchOne()
        assertTrue(result.first)
        assertEquals("ok · 2 items", status())
    }

    /**
     * A server error and a document that isn't a feed are each recorded as the feed's status:
     * [FeedFetcher.fetchOne] returns the reason, [FeedFetcher.fetch] throws it, and no item is stored.
     */
    @Test fun aFailureIsRecordedAndThrown() {
        serve("503 Service Unavailable", "busy")
        val result = fetchOne()
        assertEquals("HTTP 503", result.error)
        assertEquals(emptyList<FeedItem>(), result.newItems)
        assertEquals("error: HTTP 503", status())

        serve("200 OK", "<?xml version=\"1.0\"?><note><to>you</to></note>", "Content-Type: application/xml")
        try {
            runBlocking { fetcher.fetch(stored()) }
            fail("a note isn't a feed")
        } catch (e: IOException) {
            assertEquals("not a feed", e.message)
        }
        assertEquals("error: not a feed", status())
        assertEquals(0, repo.itemCount(feed.id))
    }

    /** Over 3 MB is refused, whether the server says so up front or only sends that much. */
    @Test fun aFeedOverThreeMegabytesIsRefused() {
        server.answer = {
            it.getOutputStream().write("HTTP/1.1 200 OK\r\n$RSS_TYPE\r\nContent-Length: ${FeedFetcher.MAX_BODY_BYTES + 1}\r\nConnection: close\r\n\r\n".toByteArray())
        }
        assertEquals("feed too large", fetchOne().error)

        // No length: it reads until the cap.
        server.answer = {
            val out = it.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\n$RSS_TYPE\r\nConnection: close\r\n\r\n".toByteArray())
            val chunk = ByteArray(64 * 1024) { ' '.code.toByte() }
            repeat(FeedFetcher.MAX_BODY_BYTES / chunk.size + 2) { out.write(chunk) }
        }
        assertEquals("feed too large (> 3 MB)", fetchOne().error)
        assertEquals("error: feed too large (> 3 MB)", status())
        assertEquals(0, repo.itemCount(feed.id))
    }

    /** The text is read in the charset the server names, else the one a byte-order mark names, else the XML prolog's, else UTF-8. */
    @Test fun theCharsetComesFromTheServerOrTheProlog() {
        fun titles(body: ByteArray, vararg headers: String): List<String> {
            server.answer = { it.getOutputStream().write(response("200 OK", body, *headers)) }
            return fetchOne().also { r -> assertNull(r.error) }.newItems.map { it.title }
        }
        val latin1 = Charsets.ISO_8859_1
        assertEquals(listOf("Café one"), titles(rss("Cptest", item("c1", "Café one", "/1")).toByteArray(latin1), "Content-Type: text/xml; charset=ISO-8859-1"))
        val declared = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + rss("Cptest", item("c2", "Crème two", "/2"))
        assertEquals(listOf("Crème two"), titles(declared.toByteArray(latin1), "Content-Type: application/xml"))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + rss("Cptest", item("c3", "Brûlée three", "/3")).toByteArray()
        assertEquals(listOf("Brûlée three"), titles(bom, "Content-Type: application/xml"))
        // UTF-16 with its mark and no charset named: the mark says how to read it.
        val utf16 = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + rss("Cptest", item("c4", "Ñandú four", "/4"))
        assertEquals(listOf("Ñandú four"), titles(utf16.toByteArray(Charsets.UTF_16LE), "Content-Type: application/xml"))
        val utf16be = "\uFEFF" + rss("Cptest", item("c5", "Œuvre five", "/5"))
        assertEquals(listOf("Œuvre five"), titles(utf16be.toByteArray(Charsets.UTF_16BE), "Content-Type: application/xml"))
        // Where they disagree: the server over the prolog, the mark over the prolog.
        val serverWins = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + rss("Cptest", item("c6", "Café six", "/6"))
        assertEquals(listOf("Café six"), titles(serverWins.toByteArray(latin1), "Content-Type: text/xml; charset=ISO-8859-1"))
        val markWins = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + rss("Cptest", item("c7", "Café seven", "/7"))
        assertEquals(listOf("Café seven"), titles(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + markWins.toByteArray(), "Content-Type: application/xml"))
        // Nothing to say which: UTF-8.
        assertEquals(listOf("Café eight"), titles(rss("Cptest", item("c8", "Café eight", "/8")).toByteArray(), "Content-Type: application/xml"))
    }

    /** A feed with one item says so on its first fetch: "1 item". */
    @Test fun oneItemIsOneItem() {
        server.answer = { it.getOutputStream().write(response("200 OK", rss("One", item("o1", "Only", "/1")).toByteArray(), RSS_TYPE)) }
        assertNull(fetchOne().error)
        assertEquals("ok · 1 item", status())
    }

    /** Cancelled while it waits for the server (the worker stopped): the download is dropped, and the feed's status isn't set to an error. */
    @Test fun aCancelledFetchIsntRecordedAsAFailure() {
        val asked = CountDownLatch(1)
        val hungUp = CountDownLatch(1)
        server.answer = { c ->
            asked.countDown()
            // No answer: it waits for the client to close the connection.
            runCatching { c.getInputStream().read() }
            hungUp.countDown()
        }
        var returned: FeedFetcher.Result? = null
        runBlocking {
            val job = launch(Dispatchers.Default) { returned = fetcher.fetchOne(stored()) }
            assertTrue("the request came", asked.await(10, TimeUnit.SECONDS))
            job.cancelAndJoin()
        }
        assertNull("it threw the cancellation rather than return a result", returned)
        assertTrue("the connection was closed", hungUp.await(10, TimeUnit.SECONDS))
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertNull(status())
            Thread.sleep(100)
        }
    }

    companion object {
        const val ETAG_1 = "ETag: \"v1\""
        const val MODIFIED = "Last-Modified: Tue, 03 Nov 2026 10:00:00 GMT"
        const val RSS_TYPE = "Content-Type: application/rss+xml; charset=utf-8"

        fun response(status: String, body: String, vararg headers: String) = response(status, body.toByteArray(), *headers)

        fun response(status: String, body: ByteArray, vararg headers: String): ByteArray =
            ("HTTP/1.1 $status\r\n" + (headers.toList() + "Content-Length: ${body.size}" + "Connection: close").joinToString("") { "$it\r\n" } + "\r\n")
                .toByteArray() + body

        fun item(guid: String, title: String, link: String) = "<item><title>$title</title><link>$link</link><guid>$guid</guid></item>"

        fun rss(title: String, vararg items: String) = "<rss version=\"2.0\"><channel><title>$title</title>${items.joinToString("")}</channel></rss>"
    }
}
