package com.mtwabpin.kolahal.feeds

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A fetch you ask for, through the worker: what it logs, marks read and posts once new items come. */
@RunWith(AndroidJUnit4::class)
class FeedWorkerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repo = FeedRepository.get(context)
    private val logs = LogRepository.get(context)
    private val config = Config.get(context)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val before = logs.maxId()

    /** A feed server on this phone: every request gets [body]. */
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    @Volatile private var body = ""

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use { c ->
                    val reader = c.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    val bytes = body.toByteArray()
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: application/rss+xml\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    runCatching { c.getOutputStream().write(head.toByteArray() + bytes) }
                }
            }
        }
    }

    // Switched off, so a scheduled fetch leaves it to the test; a fetch of one feed runs all the same.
    private val feed = repo.add("http://127.0.0.1:${socket.localPort}/fwtest-${System.nanoTime()}.xml").also { repo.setEnabled(it.id, false) }

    @After fun tearDown() {
        listOf(Keys.FEEDS_BACKLOG_READ, Keys.FEEDS_LOG_LINES, Keys.FEEDS_READ_SHOWN).forEach(config::reset)
        notifications.activeNotifications.filter { it.id == FeedWorker.notificationId(feed.id) }.forEach { notifications.cancel(it.tag, it.id) }
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("feeds-now-${feed.id}").result.get()
        repo.remove(feed.id)
        socket.close()
        logs.recent(200).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    private fun rss(vararg ids: String) =
        "<rss version=\"2.0\"><channel><title>Fwtest</title>" + ids.joinToString("") { "<item><title>Post $it</title><link>https://example.com/$it</link><guid>$it</guid></item>" } + "</channel></rss>"

    private fun mine(): List<LogEntry> = logs.recent(200).filter { it.id > before && it.ctx.values[FeedWorker.CTX_FEED] == feed.id.toString() }

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 30_000
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(100)
        assertTrue(what, check())
    }

    /**
     * With feeds.backlog-read the first fetch marks what was there read and says so; the next logs
     * up to feeds.log-lines new items and a "+N more" line, counts the shown ones read
     * (feeds.read-shown), and posts one notification for the batch.
     */
    @Test fun newItemsAreLoggedUpToTheLimitAndPosted() {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        config.set(Keys.FEEDS_BACKLOG_READ, "on")
        config.set(Keys.FEEDS_LOG_LINES, "2")
        config.set(Keys.FEEDS_READ_SHOWN, "on")
        repo.setNotify(feed.id, true)

        body = rss("a", "b")
        FeedScheduler.fetchNow(context, feed.id)
        waitFor("the backlog line") { mine().any { it.text == "Fwtest: subscribed · 2 earlier posts marked read" } }
        assertEquals(0, repo.unreadCount(feed.id))

        body = rss("e", "d", "c", "a", "b")
        FeedScheduler.fetchNow(context, feed.id)
        waitFor("the +1 more line") { mine().any { it.text == "+1 more" } }
        assertEquals(listOf("Post e", "Post d", "+1 more"), mine().filter { it.kind == LogKind.SCRIPT }.map { it.text })
        // The two shown count as read; the one behind "+1 more" doesn't.
        assertEquals(1, repo.unreadCount(feed.id))
        waitFor("the notification") { notifications.activeNotifications.any { it.id == FeedWorker.notificationId(feed.id) } }
        val posted = notifications.activeNotifications.single { it.id == FeedWorker.notificationId(feed.id) }.notification
        assertEquals("Fwtest", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("3 new — Post e", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }
}
