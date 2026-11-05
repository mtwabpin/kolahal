package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.feeds.Feed
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.feeds.FeedScheduler
import com.mtwabpin.kolahal.feeds.FeedWorker
import com.mtwabpin.kolahal.terminal.CommandParsing
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Feeds tab: the subscriptions with their unread counts, a feed's sheet, adding a feed, and a
 * feed's items. Feeds here point at this phone itself (127.0.0.1, a port nothing listens on), and
 * their items are stored directly, so nothing is fetched from the internet.
 */
@RunWith(AndroidJUnit4::class)
class FeedsScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val repo = FeedRepository.get(app)
    private val logs = LogRepository.get(app)
    private val config = Config.get(app)
    private var logStart = 0L
    private var notificationsAsked = 0
    private lateinit var vm: TerminalViewModel

    @Before fun setUp() {
        logStart = logs.maxId()
    }

    @After fun tearDown() {
        val wm = WorkManager.getInstance(app)
        val mine = repo.all().filter { MARK in it.url }.map { it.id }
        // A fetch Add feed started and still to run doesn't run after the test. (The test that
        // starts one waits for it to end: a cancelled fetch already running can't be waited for.)
        mine.forEach { wm.cancelUniqueWork("feeds-now-$it").result.get() }
        mine.forEach(repo::remove)
        FeedScheduler.sync(app)
        val ids = mine.mapTo(HashSet()) { it.toString() }
        logs.recent(200).filter { it.id > logStart && it.ctx.values[FeedWorker.CTX_FEED] in ids }.forEach { logs.delete(it.id) }
        config.reset(Keys.FEEDS_BACKLOG_READ)
    }

    /** A feed of the test's: fetched just now (so no timer is due for it), with [unread] unread and [read] read items. */
    private fun feed(title: String?, unread: Int = 0, read: Int = 0, fetched: Boolean = true, link: (Int) -> String? = { null }): Feed {
        val f = repo.add("http://127.0.0.1:9/$MARK/${System.nanoTime()}.xml", title)
        val total = unread + read
        if (total > 0) {
            // Newest first, as a feed lists them: item 1 is the newest.
            val stored = repo.insertItems(f.id, (1..total).map { FeedRepository.NewItem("g$it", "${title ?: "item"} $it", link(it), null, null) })
            repo.markRead(stored.takeLast(read).map { it.id })
        }
        if (fetched) repo.recordFetch(f.id, "ok")
        return f
    }

    private fun show() {
        vm = vms.terminal(app)
        compose.setContent { FeedsScreen(vm, onRequestNotifications = { notificationsAsked++ }) }
    }

    /** [check] holds for a second: what a wrong change, still on its way, would undo. */
    private fun stays(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue(what, check())
            Thread.sleep(50)
        }
    }

    /** How often [f] is fetched, as its row says it: new feeds take feeds.interval. */
    private fun every(f: Feed) = "every " + CommandParsing.formatInterval(f.intervalMin)

    /** The subscriptions line under the title, as the screen counts them. */
    private fun subtitle(): String {
        val all = repo.all()
        return "${all.size} feed${if (all.size == 1) "" else "s"} · ${all.filter { it.enabled }.sumOf { it.unread }} unread"
    }

    /** Opens the sheet of the feed titled [title]. */
    private fun openSheet(title: String) {
        compose.waitForText(title)
        compose.onNode(hasContentDescription("Feed options") and hasAnyAncestor(hasText(title))).performClick()
        compose.waitForText("Remove feed")
    }

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    /** Taps the Add feed button and waits for its dialog's box. The button's label is a node of its own, unmerged. */
    private fun tapAddFeed() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add feed", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add feed", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
    }

    /** With no feeds, the tab says how to start, and Add a feed opens the box for an address. */
    @Test fun withNoFeedsItSaysHowToStart() {
        assumeTrue("no feeds of the phone's own", repo.all().isEmpty())
        show()
        compose.waitForText("No feeds yet", substring = true)
        compose.onNodeWithText("RSS · Atom").assertExists()
        compose.onNodeWithContentDescription("Refresh all").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Export OPML").assertIsNotEnabled()
        compose.onNodeWithText("Add a feed").performClick()
        compose.onNodeWithText("Feed URL").assertExists()
    }

    /** A feed shows its unread and stored counts, and the title line and the Feeds tab add up what is unread. */
    @Test fun aFeedShowsWhatIsUnread() {
        feed("KT Alpha", unread = 2, read = 1)
        show()
        compose.waitForText("KT Alpha")
        compose.onNodeWithText("2 / 3").assertExists()
        compose.waitForText(subtitle())
        val unread = repo.all().filter { it.enabled }.sumOf { it.unread }
        compose.waitForText("Feeds · $unread")
        compose.onNodeWithContentDescription("Refresh all").assertIsEnabled()
    }

    /** The sheet says what the feed is, and Mark all read clears its count. */
    @Test fun theSheetMarksAFeedRead() {
        val f = feed("KT Alpha", unread = 3)
        show()
        openSheet("KT Alpha")
        compose.onNodeWithText(f.url).assertExists()
        compose.onNodeWithText("3 unread of 3 · fetched just now · ok").assertExists()
        compose.onNodeWithText("Mark all read").performClick()
        compose.waitForText("Marked KT Alpha read")
        assertEquals(0, repo.unreadCount(f.id))
        compose.waitForText("0 unread of 3", substring = true)
        compose.onNodeWithText("Mark all read").assertIsNotEnabled()
    }

    /** Turning a feed's notifications on asks for the permission to post them. */
    @Test fun notificationsAskForThePermission() {
        val f = feed("KT Alpha")
        show()
        openSheet("KT Alpha")
        compose.onNodeWithText("Notifications").performClick()
        compose.waitFor("notify on") { repo.byId(f.id)!!.notify }
        assertEquals(1, notificationsAsked)
        // The second tap switches from what the sheet shows: wait for it to show on, as the row
        // under it does (the sheet's switch has no state to read).
        compose.waitForText("${every(f)} · notify", substring = true)
        compose.onNodeWithText("Notifications").performClick()
        compose.waitFor("notify off") { !repo.byId(f.id)!!.notify }
        assertEquals(1, notificationsAsked)
    }

    /** A feed switched off says so in its row, and its unread items leave the count. */
    @Test fun aFeedSwitchedOffLeavesTheCount() {
        val f = feed("KT Alpha", unread = 2)
        show()
        openSheet("KT Alpha")
        compose.onNodeWithText("Enabled").performClick()
        compose.waitFor("switched off") { !repo.byId(f.id)!!.enabled }
        compose.waitForText("${every(f)} · off", substring = true)
        // Its two unread items no longer count.
        compose.waitForText(subtitle())
    }

    /** Refresh every sets how often the feed is fetched, and its row says so. */
    @Test fun refreshEverySetsTheInterval() {
        val f = feed("KT Alpha")
        show()
        openSheet("KT Alpha")
        compose.onNodeWithText("Refresh every").performClick()
        compose.onNodeWithText("6 hours").performClick()
        compose.waitFor("every 6 hours") { repo.byId(f.id)!!.intervalMin == 360 }
        compose.waitForText("every 6h", substring = true)
    }

    /** Remove asks first: Cancel keeps the feed, Remove takes it and its items away. */
    @Test fun removeAsksFirst() {
        val f = feed("KT Alpha", unread = 2)
        show()
        openSheet("KT Alpha")
        compose.onNodeWithText("Remove feed").performClick()
        compose.onNodeWithText("Remove KT Alpha?").assertExists()
        compose.onNodeWithText("Unsubscribes and deletes its 2 stored items. Export OPML first to keep your list.").assertExists()
        inDialog("Cancel").performClick()
        compose.onNodeWithText("Remove KT Alpha?").assertDoesNotExist()
        assertNotNull(repo.byId(f.id))

        compose.onNodeWithText("Remove feed").performClick()
        inDialog("Remove").performClick()
        compose.waitFor("removed") { repo.byId(f.id) == null }
        compose.waitForText("Removed KT Alpha")
        compose.onNodeWithText("Remove feed").assertDoesNotExist()
    }

    /** Edit address in a feed's sheet: the address filled in, changed, and the feed keeps its items. */
    @Test fun aFeedsAddressCanBeEdited() {
        val f = feed("KT Typo", unread = 2)
        show()
        openSheet("KT Typo")
        compose.onNodeWithText("Edit address").performClick()
        val box = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        box.assert(hasText(f.url))
        val fixed = "http://127.0.0.1:9/$MARK/fixed.xml"
        box.performTextReplacement(fixed)
        inDialog("Save").performClick()
        compose.waitFor("moved") { repo.byId(f.id)?.url == fixed }
        compose.waitForText("KT Typo is at 127.0.0.1 now — fetching…")
        compose.onNodeWithText("Feed URL").assertDoesNotExist()
        assertEquals(2, repo.itemCount(f.id))
        compose.waitFor("the fetch done", ms = 30_000) {
            WorkManager.getInstance(app).getWorkInfosForUniqueWork("feeds-now-${f.id}").get().all { it.state.isFinished }
        }
    }

    /** Add feed takes an address, and the new feed shows in the list. */
    @Test fun anAddedFeedShowsInTheList() {
        show()
        tapAddFeed()
        val url = "http://127.0.0.1:9/$MARK/added.xml"
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(url)
        inDialog("Subscribe").performClick()
        compose.waitForText("Subscribed to 127.0.0.1 — fetching…")
        val added = repo.byUrl(url)
        assertNotNull(added)
        compose.onNodeWithText("Feed URL").assertDoesNotExist()
        // Its row: no title yet, so its address's host.
        val row = hasContentDescription("Feed options") and hasAnyAncestor(hasText("127.0.0.1"))
        compose.waitUntil(5_000) { compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        // The fetch it started fails at once (nothing listens there): over before the test is, its
        // last line, the failure, logged.
        compose.waitFor("the fetch's failure", ms = 30_000) {
            logs.recent(200).any { it.id > logStart && it.ctx.values[FeedWorker.CTX_FEED] == added!!.id.toString() }
        }
        compose.waitFor("the fetch done", ms = 10_000) {
            WorkManager.getInstance(app).getWorkInfosForUniqueWork("feeds-now-${added!!.id}").get().all { it.state.isFinished }
        }
    }

    /** What isn't an http(s) address says so, and Subscribe stays off. */
    @Test fun aWrongAddressIsRefused() {
        show()
        tapAddFeed()
        val box = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        inDialog("RSS or Atom; https:// is added if missing").assertExists()
        box.performTextInput("ftp://example.com/feed")
        inDialog("Enter an http(s) address").assertExists()
        inDialog("Subscribe").assertIsNotEnabled()
        inDialog("Cancel").performClick()
        compose.onNodeWithText("Feed URL").assertDoesNotExist()
    }

    /** An address already subscribed to says so, and nothing is added twice. */
    @Test fun aFeedIsNotAddedTwice() {
        val f = feed("KT Alpha")
        show()
        compose.waitForText("KT Alpha")
        tapAddFeed()
        val box = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        box.performTextInput(f.url)
        box.performImeAction()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Already subscribed to ${f.url}") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
        stays("added once") { repo.all().count { it.url == f.url } == 1 }
    }

    /** A feed opens on its items, newest first; Mark all read clears them, and Back returns to the list. */
    @Test fun aFeedOpensOnItsItems() {
        val f = feed("KT Alpha", unread = 2)
        show()
        compose.waitForText("KT Alpha")
        compose.onNodeWithText("KT Alpha").performClick()
        compose.waitForText("KT Alpha 1")
        val first = compose.onNodeWithText("KT Alpha 1").fetchSemanticsNode().boundsInRoot.top
        val second = compose.onNodeWithText("KT Alpha 2").fetchSemanticsNode().boundsInRoot.top
        assertTrue("the newest first: $first, $second", first < second)
        compose.onNodeWithText("2 unread · 127.0.0.1").assertExists()
        compose.onNodeWithContentDescription("Mark all read").performClick()
        compose.waitForText("0 unread · 127.0.0.1")
        assertEquals(0, repo.unreadCount(f.id))
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForText("Subscriptions")
        compose.onNodeWithText("KT Alpha 1").assertDoesNotExist()
    }

    /** A swipe either way marks an item read, and a read one unread again; a screen reader has the same. */
    @Test fun aSwipeMarksAnItemReadOrUnread() {
        val f = feed("KT Alpha", unread = 2)
        show()
        compose.waitForText("KT Alpha")
        compose.onNodeWithText("KT Alpha").performClick()
        compose.waitForText("KT Alpha 1")
        val newest = repo.items(feedId = f.id).first { it.title == "KT Alpha 1" }
        compose.onNodeWithText("KT Alpha 1").performTouchInput { swipeRight() }
        compose.waitFor("read") { repo.items(feedId = f.id).first { it.id == newest.id }.read }
        compose.waitForText("1 unread · 127.0.0.1")
        compose.onNodeWithText("KT Alpha 1").performTouchInput { swipeLeft() }
        compose.waitFor("unread again") { !repo.items(feedId = f.id).first { it.id == newest.id }.read }
        compose.waitForText("2 unread · 127.0.0.1")
        // The row is back where it was, and the hint says how.
        compose.onNodeWithText("KT Alpha 1").assertIsDisplayed()
        compose.onNodeWithText("Tap an item to open it · swipe it to mark it read or unread").assertExists()
        val unread = compose.onAllNodes(SemanticsMatcher("can be marked read") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Mark read" } == true
        }).fetchSemanticsNodes()
        compose.runOnIdle { unread.first().config[SemanticsActions.CustomActions].first { it.label == "Mark read" }.action() }
        compose.waitFor("read by its action") { repo.unreadCount(f.id) == 1 }
    }

    /** An item without a link says so, and counts as read. */
    @Test fun anItemWithoutALinkSaysSo() {
        val f = feed("KT Alpha", unread = 1)
        show()
        compose.waitForText("KT Alpha")
        compose.onNodeWithText("KT Alpha").performClick()
        compose.waitForText("KT Alpha 1")
        compose.onNodeWithText("KT Alpha 1").performClick()
        compose.waitForText("This item has no link")
        compose.waitFor("read") { repo.unreadCount(f.id) == 0 }
    }

    /** An item whose link no app opens says so, and stays unread. */
    @Test fun anItemNoAppOpensStaysUnread() {
        val f = feed("KT Alpha", unread = 1) { "kolahal-test-nothing:item-$it" }
        show()
        compose.waitForText("KT Alpha")
        compose.onNodeWithText("KT Alpha").performClick()
        compose.waitForText("KT Alpha 1")
        compose.onNodeWithText("KT Alpha 1").performClick()
        compose.waitForText("No app can open kolahal-test-nothing:item-1")
        stays("still unread") { repo.unreadCount(f.id) == 1 }
    }

    /** A feed never fetched opens on what to do, not an empty page. */
    @Test fun aFeedNeverFetchedSaysToRefresh() {
        val f = feed("KT Alpha", fetched = false)
        show()
        compose.waitForText("KT Alpha")
        compose.onNodeWithText("${every(f)} · never fetched", substring = true).assertExists()
        compose.onNodeWithText("KT Alpha").performClick()
        compose.waitForText("Not fetched yet. Tap refresh.")
    }

    /** The feeds settings sit under the subscriptions, and a switch there changes the setting. */
    @Test fun theFeedsSettingsAreInTheTab() {
        show()
        val title = "Mark existing posts read when subscribing"
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
        assertFalse(config[Keys.FEEDS_BACKLOG_READ])
        compose.onNodeWithText(title).performClick()
        compose.waitFor("feeds.backlog-read on") { config[Keys.FEEDS_BACKLOG_READ] }
    }

    private companion object {
        /** In every test feed's address, so the clean-up finds them. */
        const val MARK = "kolahal-feeds-screen-test"
    }
}
