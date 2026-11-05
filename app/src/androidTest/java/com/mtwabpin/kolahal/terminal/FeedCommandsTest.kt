package com.mtwabpin.kolahal.terminal

import android.app.Instrumentation
import android.content.Intent
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.feeds.Feed
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.feeds.FeedScheduler
import com.mtwabpin.kolahal.feeds.FeedWorker
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotifPrefs
import com.mtwabpin.kolahal.notifications.Notif
import com.mtwabpin.kolahal.notifications.NotificationActions
import com.mtwabpin.kolahal.notifications.NotificationStore
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What `feed`, `rss` and `notifs` print, and what they change; feeds point at a port nothing answers on. */
@RunWith(AndroidJUnit4::class)
class FeedCommandsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val logs = LogRepository.get(context)
    private val feeds = FeedRepository.get(context)
    private val notifPrefs = NotifPrefs.get(context)
    // Last, once the feeds are gone and nothing more can come from them; whatever went wrong before.
    @get:Rule val logLines = LogLinesSince()
    private val listener = "${context.packageName}/${KolahalNotificationListener::class.java.name}"
    private val accessBefore = NotificationActions.isEnabled(context)
    private val mutedBefore = notifPrefs.muted.value
    private val logToTerminalBefore = notifPrefs.logToTerminal
    private val storeBefore = NotificationStore.items.value
    private val intentsBefore = storeBefore.associate { it.key to NotificationStore.intent(it.key) }

    private var asked = 0
    private val screens = mutableListOf<Screen>()
    private val commands = CommandRunner(context, object : TerminalUiStub() {
        override fun openScreen(screen: Screen) { screens += screen }
        override fun askNotificationAccess() { asked++ }
    })

    @After fun tearDown() {
        feeds.feeds().filter { it.url.startsWith(BASE) }.forEach { feeds.remove(it.id) }
        FeedScheduler.sync(context)
        (notifPrefs.muted.value - mutedBefore).forEach(notifPrefs::unmute)
        notifPrefs.logToTerminal = logToTerminalBefore
        // Access first: a listener binding as it comes back replaces the list with what is showing.
        if (NotificationActions.isEnabled(context) != accessBefore) {
            shell("cmd notification ${if (accessBefore) "allow" else "disallow"}_listener $listener")
        }
        settleListener()
        NotificationStore.clear()
        storeBefore.asReversed().forEach { NotificationStore.put(it, intentsBefore[it.key]) }
    }

    /**
     * Waits a few seconds at most for the listener to be bound as access says. It can stay bound a
     * while after access goes, so a listener still there isn't a failure.
     */
    private fun settleListener(ms: Long = 5_000) {
        val until = System.currentTimeMillis() + ms
        while ((KolahalNotificationListener.connected != null) != NotificationActions.isEnabled(context) &&
            System.currentTimeMillis() < until
        ) Thread.sleep(50)
    }

    private fun commandId(line: String) = commands.commandId(line)

    /** Runs [line] and returns the lines it printed: the ones marked as its output. */
    private fun lines(line: String) = commands.ownLines(line)

    private fun run(line: String): List<String> = lines(line).map { it.text }

    /** A feed at [path] on a port nothing listens on: any fetch of it fails at once. */
    private fun feed(path: String, title: String? = null): Feed = feeds.add("$BASE$path", title)

    /** [n] items, newest first: `Llat item 1` is the newest. */
    private fun items(feed: Feed, n: Int, link: (Int) -> String? = { "${BASE}item/$it" }) =
        feeds.insertItems(feed.id, (1..n).map { FeedRepository.NewItem("llat-$it", "Llat item $it", link(it), null, null) })

    /** The error line a failed fetch of [name] logs after [after], once it has. */
    private fun failure(name: String, after: Long, ms: Long = 30_000): LogEntry {
        fun find() = logs.recent(200, afterId = after).firstOrNull { it.kind == LogKind.ERROR && it.text.startsWith("$name: ") }
        waitUntil("the fetch of $name fails", ms) { find() != null }
        return find()!!
    }

    /** Catches browser and settings screens the commands open, so none really opens. */
    private fun <T> catching(actions: Set<String>, opened: MutableList<Intent>, block: () -> T): T {
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action !in actions || intent.scheme?.startsWith("kolahal-") == true) return null
                opened += intent
                return Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            return block()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** Access on and the listener bound, so its first list, from what is really showing, is in. */
    private fun allowNotifications() {
        if (!NotificationActions.isEnabled(context)) shell("cmd notification allow_listener $listener")
        waitUntil("notification access", ms = 15_000) { NotificationActions.isEnabled(context) && KolahalNotificationListener.connected != null }
    }

    /** `feed add` subscribes, says it is fetching, and the failed fetch comes as the command's own line; the same address twice is one feed. */
    @Test fun feedAddSubscribesAndFetches() {
        val before = logs.maxId()
        val said = lines("feed add ${BASE}feed").filterNot { it.kind == LogKind.ERROR }
        val feed = feeds.byUrl("${BASE}feed")!!
        assertEquals(listOf("Subscribed #${feed.id} ${BASE}feed — fetching…"), said.map { it.text })
        assertEquals(feed.url, said.single().ctx.url)
        assertEquals(60, feed.intervalMin)
        val failed = failure("127.0.0.1", before)
        assertEquals(commandId("feed add ${BASE}feed"), failed.ctx.values[LogContext.FROM])
        assertEquals(feed.id.toString(), failed.ctx.values[FeedWorker.CTX_FEED])
        assertEquals(feed.url, failed.ctx.url)

        // Another spelling of the same address. Its fetch runs after the first's; none is still to
        // come when the test ends.
        assertEquals("Subscribed #${feed.id} ${BASE}feed — fetching…", run("feed add HTTP://127.0.0.1:9/feed#top").first())
        assertEquals(1, feeds.feeds().count { it.url.startsWith(BASE) })
        val work = androidx.work.WorkManager.getInstance(context)
        waitUntil("no fetch to come", ms = 30_000) { work.getWorkInfosForUniqueWork("feeds-now-${feed.id}").get().all { it.state.isFinished } }

        assertEquals(listOf("Usage: feed add <url>"), run("feed add"))
        assertEquals(listOf("Not an http(s) URL: ftp://127.0.0.1/feed"), run("feed add ftp://127.0.0.1/feed"))
    }

    /** `feed ls` is a line per feed, tapped to read it, with its counts and settings beside it; `feeds` alone opens the manager. */
    @Test fun feedListShowsEachFeedWithItsCounts() {
        if (feeds.all().isEmpty()) assertEquals(listOf("No feeds yet — feed add <url> (or 'feeds' for the manager and OPML import)"), run("feed"))
        val news = feed("news", title = "Llat News")
        items(news, 3)
        feeds.markRead(feeds.items(news.id).last().id)
        val quiet = feed("quiet")
        feeds.setNotify(quiet.id, true)
        feeds.setInterval(quiet.id, 120)
        feeds.setEnabled(quiet.id, false)

        val listed = lines("feed ls")
        val first = listed.single { it.text == "#${news.id} Llat News" }
        assertEquals("feed read ${news.id}", first.ctx.command)
        assertEquals(news.url, first.ctx.copy)
        assertTrue(first.ctx.values[LogContext.DESC], first.ctx.values[LogContext.DESC]!!.startsWith("2/3 unread · every 1h"))
        val second = listed.single { it.text == "#${quiet.id} ${BASE}quiet" }
        assertTrue(second.ctx.values[LogContext.DESC], second.ctx.values[LogContext.DESC]!!.startsWith("0/0 unread · every 2h · notify · off"))
        assertEquals("Tap a feed to read it · feed read = all unread · feed fetch = refresh now", listed.last().text)
        assertEquals(listed.map { it.text }, run("feed list"))
        assertEquals(listed.map { it.text }, run("feed"))
        assertEquals(listed.map { it.text }, run("feeds ls"))

        assertEquals(emptyList<String>(), run("feeds"))
        assertEquals(listOf(Screen.FEEDS), screens)
    }

    /** `feed read` shows the unread items once, each a link, and marks them read; `feed all` shows them again. */
    @Test fun feedReadShowsUnreadItemsOnce() {
        val daily = feed("daily", title = "Llat Daily")
        val stored = items(daily, 3)
        val shown = lines("feed read ${daily.id}")
        assertEquals(listOf("Llat item 1", "Llat item 2", "Llat item 3"), shown.map { it.text })
        for ((line, item) in shown.zip(stored)) {
            assertEquals(LogKind.SCRIPT, line.kind)
            assertEquals("Llat Daily", line.source)
            assertEquals(item.link, line.ctx.url)
            assertEquals(item.title, line.ctx.copy)
            assertEquals(item.id.toString(), line.ctx.values[FeedWorker.CTX_ITEM])
        }
        assertEquals(0, feeds.unreadCount(daily.id))
        assertEquals(listOf("Nothing unread"), run("feed read ${daily.id}"))
        assertEquals(listOf("Llat item 1", "Llat item 2", "Llat item 3"), run("feed all ${daily.id}"))
        val empty = feed("empty")
        assertEquals(listOf("No items"), run("feed all ${empty.id}"))
    }

    /** Over forty unread, `feed read` shows forty and says how many are left; the rest stay unread for next time. */
    @Test fun feedReadStopsAtFortyAndSaysHowManyAreLeft() {
        val many = feed("many")
        items(many, 45)
        val first = run("feed read ${many.id}")
        assertEquals(41, first.size)
        assertEquals("Llat item 1", first.first())
        assertEquals("5 more unread — feed read ${many.id} again", first.last())
        assertEquals(5, feeds.unreadCount(many.id))
        assertEquals((41..45).map { "Llat item $it" }, run("feed read ${many.id}"))
        assertEquals(0, feeds.unreadCount(many.id))
    }

    /** Exactly forty unread: `feed read` shows all forty and no line about more. */
    @Test fun feedReadOfExactlyFortySaysNoMore() {
        val forty = feed("forty")
        items(forty, 40)
        assertEquals((1..40).map { "Llat item $it" }, run("feed read ${forty.id}"))
        assertEquals(0, feeds.unreadCount(forty.id))
    }

    /** `feed read` with no id shows every feed's unread items, each under its own feed, and marks them all read. */
    @Test fun feedReadWithNoIdReadsEveryFeed() {
        assumeTrue("other feeds on the emulator have unread items", feeds.unreadCount(null) == 0)
        val one = feed("one", title = "Llat One")
        items(one, 2)
        val two = feed("two", title = "Llat Two")
        feeds.insertItems(two.id, listOf(FeedRepository.NewItem("llat-two", "Llat two's item", "${BASE}two/1", null, null)))
        val shown = lines("feed read")
        assertEquals(
            setOf("Llat item 1" to "Llat One", "Llat item 2" to "Llat One", "Llat two's item" to "Llat Two"),
            shown.map { it.text to it.source }.toSet(),
        )
        assertEquals(3, shown.size)
        assertEquals(0, feeds.unreadCount(null))
    }

    /** `feed open` opens the five newest unread links in the browser and marks only those read; a link nothing opens stays unread. */
    @Test fun feedOpenOpensTheNewestUnreadLinks() {
        val f = feed("open")
        items(f, 7) { if (it == 7) null else "${BASE}item/$it" }
        val opened = mutableListOf<Intent>()
        catching(setOf(Intent.ACTION_VIEW), opened) {
            assertEquals(listOf("Opened 5 items · 2 still unread"), run("feed open ${f.id}"))
            assertEquals((1..5).map { "${BASE}item/$it" }, opened.map { it.dataString })
            assertEquals(2, feeds.unreadCount(f.id))
            assertEquals(listOf("Opened 1 item · 1 still unread"), run("feed open ${f.id}"))
            assertEquals("${BASE}item/6", opened.last().dataString)
            // The one left has no link.
            assertEquals(listOf("Nothing unread"), run("feed open ${f.id}"))
            assertEquals(1, feeds.unreadCount(f.id))

            val bad = feed("bad")
            items(bad, 1) { "kolahal-none:nowhere" }
            assertEquals(listOf("Opened 0 items (1 couldn't be opened) · 1 still unread"), run("feed open ${bad.id}"))
            assertEquals(1, feeds.unreadCount(bad.id))
        }
        assertEquals(6, opened.size)
    }

    /** `feed notify`, `every`, `off` and `on` change one feed and say so; a wrong id or a missing one says how. */
    @Test fun feedSettingsChangeOneFeed() {
        val f = feed("set")
        // On, then off: whichever a new feed starts with, each one is a change.
        assertEquals(listOf("Feed #${f.id} notifications on"), run("feed notify ${f.id}"))
        assertTrue(feeds.byId(f.id)!!.notify)
        assertEquals(listOf("Feed #${f.id} notifications off"), run("feed notify ${f.id} off"))
        assertFalse(feeds.byId(f.id)!!.notify)
        assertEquals(listOf("Feed #${f.id} notifications on"), run("feed notify ${f.id} on"))
        assertTrue(feeds.byId(f.id)!!.notify)

        assertEquals(listOf("Feed #${f.id} every 2h"), run("feed every ${f.id} 2h"))
        assertEquals(120, feeds.byId(f.id)!!.intervalMin)
        assertEquals(listOf("Feed #${f.id} every 15m"), run("feed every ${f.id} 5m"))
        assertEquals(15, feeds.byId(f.id)!!.intervalMin)
        val every = listOf("Usage: feed every <id> <30m|2h|…>")
        assertEquals(every, run("feed every ${f.id} soon"))
        assertEquals(every, run("feed every ${f.id} off"))
        assertEquals(15, feeds.byId(f.id)!!.intervalMin)

        assertEquals(listOf("Feed #${f.id} off"), run("feed off ${f.id}"))
        assertFalse(feeds.byId(f.id)!!.enabled)
        assertEquals(listOf("Feed #${f.id} on"), run("feed on ${f.id}"))
        assertTrue(feeds.byId(f.id)!!.enabled)

        assertEquals(listOf("Usage: feed off <id>"), run("feed off"))
        assertEquals(listOf("Usage: feed notify <id> on|off"), run("feed notify"))
        assertEquals(listOf("No feed #999999 — 'feeds' lists them"), run("feed on 999999"))
    }

    /** `feed rm` removes the feed and its items; then the id is unknown, and an unknown subcommand shows every one. */
    @Test fun feedRmRemovesAFeedAndItsItems() {
        val f = feed("rm")
        items(f, 2)
        assertEquals(listOf("Removed feed #${f.id}"), run("feed rm ${f.id}"))
        assertNull(feeds.byId(f.id))
        assertEquals(0, feeds.itemCount(f.id))
        assertEquals(listOf("No feed #${f.id} — 'feeds' lists them"), run("feed remove ${f.id}"))
        assertEquals(listOf("Usage: feed rm <id>"), run("feed rm"))
        assertEquals(listOf("Usage: feed rm <id>"), run("feed rm first"))
        assertEquals(listOf("Usage: feed [add <url>|url <id> <url>|ls|rm|fetch|read|all|open|notify|every|on|off] [<id>]"), run("feed bogus"))
    }

    /**
     * `feed url` moves a feed to another address, as for a typo: its items stay, what fetching the
     * old one learnt goes. One another feed has, one that isn't an address, or no feed, is said.
     */
    @Test fun feedUrlChangesAFeedsAddress() {
        val f = feed("typo")
        items(f, 2)
        feeds.recordFetch(f.id, "error: 404", etag = "\"old\"")
        val other = feed("other")
        val fixed = "${BASE}fixed"
        assertEquals(listOf("Feed #${f.id} is at $fixed — fetching…"), run("feed url ${f.id} $fixed").take(1))
        val moved = feeds.byId(f.id)!!
        assertEquals(fixed, moved.url)
        assertNull(moved.etag)
        assertEquals(2, feeds.itemCount(f.id))
        assertTrue(feeds.all().first { it.id == f.id }.lastStatus != "error: 404")
        assertEquals(listOf("Already subscribed to ${other.url}"), run("feed url ${f.id} ${other.url}"))
        assertEquals(listOf("Not an http(s) URL: ftp://x"), run("feed url ${f.id} ftp://x"))
        assertEquals(listOf("No feed #999999 — 'feeds' lists them"), run("feed url 999999 $fixed"))
        assertEquals(listOf("Usage: feed url <id> <address>"), run("feed url ${f.id}"))
    }

    /** `feed fetch` says which feed it fetches, or all; a feed that can't be reached says so under that command. */
    @Test fun feedFetchFetchesOneOrAll() {
        val f = feed("fetch", title = "Llat Fetch")
        val before = logs.maxId()
        assertEquals("Fetching feed #${f.id}…", run("feed fetch ${f.id}").first())
        assertEquals(commandId("feed fetch ${f.id}"), failure("Llat Fetch", before).ctx.values[LogContext.FROM])
        val again = logs.maxId()
        assertEquals("Fetching all feeds…", run("feed refresh").first())
        assertEquals(commandId("feed refresh"), failure("Llat Fetch", again).ctx.values[LogContext.FROM])
    }

    /**
     * `rss` with no address says how; an address it can't read ends in an error line from `rss`, and
     * subscribes to nothing. (A feed it can read isn't tried: the tests reach no server.)
     */
    @Test fun rssWithNoAddressOrOneItCantRead() {
        assertEquals(listOf("Usage: rss <url>"), run("rss"))
        val before = logs.maxId()
        commands.processor.run("rss ${BASE}feed")
        val command = commandId("rss ${BASE}feed")
        fun failed() = logs.recent(200, afterId = before).firstOrNull { it.kind == LogKind.ERROR && it.source == "rss" }
        waitUntil("the script's error", ms = 30_000) { failed() != null }
        assertTrue(failed()!!.text, failed()!!.text.startsWith("rss: "))
        assertEquals(command, failed()!!.ctx.values[LogContext.FROM])
        assertEquals(0, feeds.feeds().count { it.url.startsWith(BASE) })
    }

    /** With notification access off, `notifs` says to tap to allow it, and `notifs enable` asks first. */
    @Test fun notifsNeedNotificationAccess() {
        if (NotificationActions.isEnabled(context)) {
            shell("cmd notification disallow_listener $listener")
            waitUntil("access off") { !NotificationActions.isEnabled(context) }
            settleListener()
        }
        for (typed in listOf("notifs", "notifs muted", "notifications-feed all")) {
            val off = lines(typed).single()
            assertEquals("Notification access is off — tap to allow it", off.text)
            assertEquals(LogKind.ERROR, off.kind)
            assertEquals("notifs enable", off.ctx.values[LogContext.RUN])
        }
        assertEquals(emptyList<String>(), run("notifs enable"))
        assertEquals(1, asked)
    }

    /** `notifs` lists the notifications showing, `notifs all` the dismissed ones too, and `notifs clear` empties the list. */
    @Test fun notifsListsAndClears() {
        allowNotifications()
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val now = System.currentTimeMillis()
        val live = Notif("0|llat|1", SETTINGS, serial, "Settings", "Llat title", "Llat body", now)
        val gone = Notif("0|llat|2", SETTINGS, serial, "Settings", "Llat gone", null, now - 1_000, removed = true)
        // The listener replaces the list with what is really showing as it connects, at any moment
        // after access is on: the two are added again until both lists keep them.
        var shown: List<LogEntry>
        var all: List<String>
        val until = System.currentTimeMillis() + 10_000
        do {
            NotificationStore.put(gone, null)
            NotificationStore.put(live, null)
            shown = lines("notifs")
            all = run("notifs all")
            if (shown.any { it.text == "Llat title — Llat body" } && "Llat gone" in all) break
            Thread.sleep(200)
        } while (System.currentTimeMillis() < until)
        val line = shown.single { it.text == "Llat title — Llat body" }
        assertEquals(LogKind.SCRIPT, line.kind)
        assertEquals("Settings", line.source)
        assertEquals(SETTINGS, line.ctx.pkg)
        assertEquals(serial, line.ctx.userSerial)
        assertEquals("Llat title — Llat body", line.ctx.copy)
        assertEquals(live.key, line.ctx.values[KolahalNotificationListener.CTX_KEY])
        assertTrue(shown.none { it.text == "Llat gone" })
        assertTrue(all.toString(), all.containsAll(listOf("Llat title — Llat body", "Llat gone")))

        // This dismisses every clearable notification on the device and deletes the copy kept for
        // scripts, not only the test's two: fine on the emulator, which the tests run on.
        assertEquals(listOf("Notifications cleared"), run("notifs clear"))
        assertNull(NotificationStore.find(live.key))
        assertNull(NotificationStore.find(gone.key))
    }

    /** `notifs mute` keeps an app's notifications out of the log until `unmute`; `notifs log off` keeps them all out. */
    @Test fun notifsMuteAndLog() {
        allowNotifications()
        assertEquals(listOf("Settings: notifications won't appear in the log"), run("notifs mute Settings"))
        assertTrue(SETTINGS in notifPrefs.muted.value)
        val muted = lines("notifs muted").single { it.text == "Settings" }
        assertEquals("notifs unmute $SETTINGS", muted.ctx.command)
        assertEquals(listOf("Settings: notifications will appear in the log"), run("notifs unmute Settings"))
        assertFalse(SETTINGS in notifPrefs.muted.value)
        if (mutedBefore.isEmpty()) assertEquals(listOf("No muted apps"), run("notifs muted"))
        assertEquals(listOf("No app matches 'zzqqxx'"), run("notifs mute zzqqxx"))

        assertEquals(listOf("Notifications no longer appear in the log"), run("notifs log off"))
        assertFalse(notifPrefs.logToTerminal)
        assertEquals(listOf("Notifications appear in the log"), run("notifs log on"))
        assertTrue(notifPrefs.logToTerminal)
        assertEquals(listOf("Usage: notifs [all|enable|mute <app>|unmute <app>|muted|log [on|off]|clear]"), run("notifs bogus"))

        // On already, `enable` opens Android's screen, to turn it off there.
        val opened = mutableListOf<Intent>()
        catching(setOf(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), opened) {
            assertEquals(emptyList<String>(), run("notifs enable"))
        }
        assertEquals(1, opened.size)
        assertEquals(0, asked)
    }

    private companion object {
        const val BASE = "http://127.0.0.1:9/"
        const val SETTINGS = "com.android.settings"
    }
}
