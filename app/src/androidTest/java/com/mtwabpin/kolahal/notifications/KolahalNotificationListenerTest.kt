package com.mtwabpin.kolahal.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScriptScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With notification access on, another app's notification shows in the feed and as a log line
 * ending in its icon, once per update unless it's a chat's next message; a muted app stays out of
 * the log; scripts that read notifications get a copy, which goes when access does.
 */
@RunWith(AndroidJUnit4::class)
class KolahalNotificationListenerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext
    private val logs = LogRepository.get(app)
    private val repo = ScriptRepository.get(app)
    private val prefs = NotifPrefs.get(app)
    private val mirror = KvStore(app, KolahalNotificationListener.MIRROR_SCOPE)
    private val component = "${app.packageName}/${KolahalNotificationListener::class.java.name}"
    private val before = logs.maxId()
    private var had = false
    private var shellWasMuted = false
    private val scripts = listOf("kn-reader", "kn-trigger")

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun waitUntil(what: String, ms: Long = 10_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    private fun <T : Any> waitFor(what: String, ms: Long = 10_000, find: () -> T?): T {
        var found: T? = null
        waitUntil(what, ms) { find().also { found = it } != null }
        return found!!
    }

    /**
     * Whether access is on, as the notification service has it: the setting the access screen reads
     * is written after, so just after a change it can still say the old.
     */
    private fun granted() = app.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(app, KolahalNotificationListener::class.java))

    private fun allow() {
        shell("cmd notification allow_listener $component")
        waitUntil("notification access", ms = 15_000) { granted() && KolahalNotificationListener.connected != null }
    }

    private fun disallow() {
        shell("cmd notification disallow_listener $component")
        waitUntil("notification access off", ms = 15_000) { !granted() && KolahalNotificationListener.connected == null }
    }

    /** Posts a notification from the shell, another app as far as Kolahal knows; no spaces in any part. */
    private fun post(tag: String, title: String, text: String, style: String = "", icon: String = "ic_dialog_alert") {
        shell("cmd notification post -i @android:drawable/$icon -t $title $style $tag $text".replace(Regex(" +"), " "))
    }

    /** The key of the icon only [aNotificationIsALineEndingInItsIcon] posts; its file goes in [tearDown]. */
    private val freshIcon = NotifIcons.key(app, SHELL, android.graphics.drawable.Icon.createWithResource("android", android.R.drawable.ic_menu_compass))!!

    /** Whether the system shows the shell's notification [tag], whoever listens. */
    private fun showing(tag: String) = shell("cmd notification list").lines().any { "|$SHELL|" in it && it.contains("|$tag|") }

    private fun inFeed(title: String): Notif? = NotificationStore.items.value.firstOrNull { it.pkg == SHELL && it.title == title }

    private fun lines(text: String): List<LogEntry> = logs.recent(400).filter { it.id > before && StyledText.plain(it.text) == text }

    /** Once this has run on the main thread, where the listener hears of posts, a post seen in the feed has been handled whole. */
    private fun settled() = instrumentation.runOnMainSync { }

    @Before fun setUp() {
        had = granted()
        shellWasMuted = prefs.isMuted(SHELL)
        prefs.unmute(SHELL)
        allow()
    }

    @After fun tearDown() {
        scripts.forEach { n -> repo.byName(n)?.let { ScriptScheduler.cancel(app, it.id); repo.delete(it.id) } }
        if (shellWasMuted) prefs.mute(SHELL) else prefs.unmute(SHELL)
        try {
            // The shell's notifications go while access is on: a listener may take them away.
            if (KolahalNotificationListener.connected == null) allow()
            val listener = KolahalNotificationListener.connected!!
            fun posted() = runCatching { listener.activeNotifications.orEmpty() }.getOrDefault(emptyArray())
                .filter { it.packageName == SHELL && it.tag?.startsWith("kn-") == true }
            posted().forEach { listener.cancelNotification(it.key) }
            waitUntil("the test's notifications go") { posted().isEmpty() }
        } finally {
            logs.recent(400).filter { it.id > before }.forEach { logs.delete(it.id) }
            // Not on disk, the next process doesn't know the icon either.
            java.io.File(app.filesDir, "notif-icons/$freshIcon.png").delete()
            if (!had) disallow()
        }
    }

    /**
     * A notification is in the feed and becomes a line with its app, its text to copy and its icon,
     * saved before the line shows: an icon no other test posts, not saved before, is saved as the
     * line first appears.
     */
    @Test fun aNotificationIsALineEndingInItsIcon() {
        assertFalse("the icon was saved before the test", NotifIcons.has(app, freshIcon))
        post("kn-tag-1", "kn-title-1", "kn-text-1", icon = "ic_menu_compass")
        val n = waitFor("in the feed") { inFeed("kn-title-1") }
        assertEquals("kn-text-1", n.text)
        assertFalse(n.removed)
        var savedAsItShowed = false
        val line = waitFor("its line") {
            lines("kn-title-1 — kn-text-1").singleOrNull()?.also { savedAsItShowed = NotifIcons.has(app, freshIcon) }
        }
        assertTrue("the icon saved by the time its line shows", savedAsItShowed)
        assertEquals(LogKind.SCRIPT, line.kind)
        assertEquals(n.app, line.source)
        assertEquals(SHELL, line.ctx.values[LogContext.PKG])
        assertEquals(n.key, line.ctx.values[KolahalNotificationListener.CTX_KEY])
        assertEquals("kn-title-1 — kn-text-1", line.ctx.values[LogContext.COPY])
        val icon = n.icon
        assertNotNull("a resource icon has a key", icon)
        assertEquals(icon, line.ctx.values[LogContext.ICON])
        assertEquals(freshIcon, icon)
        assertNotNull(NotifIcons.load(app, icon!!))
    }

    /** Dismissed, it stays in the feed marked removed; posted again later with the same text, it is a new line. */
    @Test fun dismissedItIsMarkedAndAgainIsANewLine() {
        post("kn-tag-2", "kn-daily", "kn-same")
        val n = waitFor("in the feed") { inFeed("kn-daily") }
        waitUntil("its line") { lines("kn-daily — kn-same").size == 1 }

        KolahalNotificationListener.connected!!.cancelNotification(n.key)
        waitUntil("marked removed") { NotificationStore.find(n.key)?.removed == true }
        assertTrue(NotificationStore.active().none { it.key == n.key })

        post("kn-tag-2", "kn-daily", "kn-same")
        waitUntil("back in the feed") { NotificationStore.find(n.key)?.removed == false }
        waitUntil("a second line") { lines("kn-daily — kn-same").size == 2 }
    }

    /** A muted app's notifications are in the feed but not the log. */
    @Test fun aMutedAppStaysOutOfTheLog() {
        prefs.mute(SHELL)
        post("kn-tag-3", "kn-muted", "kn-quiet")
        waitFor("in the feed") { inFeed("kn-muted") }
        settled()
        prefs.unmute(SHELL)
        post("kn-tag-4", "kn-heard", "kn-loud")
        waitUntil("the next one's line") { lines("kn-heard — kn-loud").isNotEmpty() }
        assertTrue(lines("kn-muted — kn-quiet").isEmpty())
    }

    /** An update within half a minute (a count going up) keeps its first line; the feed shows the new text. */
    @Test fun anUpdateSoonAfterKeepsItsFirstLine() {
        post("kn-tag-5", "kn-upd", "kn-one")
        val n = waitFor("in the feed") { inFeed("kn-upd") }
        waitUntil("its line") { lines("kn-upd — kn-one").isNotEmpty() }
        post("kn-tag-5", "kn-upd", "kn-two")
        waitUntil("the feed has the update") { NotificationStore.find(n.key)?.text == "kn-two" }
        settled()
        post("kn-tag-6", "kn-next", "kn-other")
        waitUntil("the next one's line") { lines("kn-next — kn-other").isNotEmpty() }
        assertTrue(lines("kn-upd — kn-two").isEmpty())
        assertEquals(1, lines("kn-upd — kn-one").size)
    }

    /**
     * A chat posts its notification again for each message, soon after the last: each new message
     * is a line of its own. (The shell titles a chat "conversation: sender", its text the last message.)
     */
    @Test fun eachNewMessageInAChatIsALine() {
        val chat = "-S messaging --conversation kn-chat --message Ann:kn-hi"
        post("kn-tag-7", "kn-unused", "kn-unused", chat)
        waitUntil("the first message") { lines("kn-chat: Ann — kn-hi").isNotEmpty() }
        post("kn-tag-7", "kn-unused", "kn-unused", "$chat --message Ann:kn-yo")
        waitUntil("the next message") { lines("kn-chat: Ann — kn-yo").isNotEmpty() }
        assertEquals(1, lines("kn-chat: Ann — kn-hi").size)
    }

    /** A script that reads notifications gets a copy of the feed; with none left that does, the copy goes. */
    @Test fun scriptsThatReadNotificationsGetACopy() {
        val reader = repo.create("kn-reader", ScriptLang.LUA, "-- @uses notifications\nreturn 1")
        post("kn-tag-8", "kn-copied", "kn-text")
        waitUntil("a copy for scripts") { mirror.get(KolahalNotificationListener.MIRROR_KEY)?.contains("kn-copied") == true }

        repo.delete(reader.id)
        post("kn-tag-9", "kn-later", "kn-text")
        waitUntil("the copy goes") { mirror.get(KolahalNotificationListener.MIRROR_KEY) == null }
    }

    /** Access turned off, the copy kept for scripts goes with it. */
    @Test fun accessOffDropsTheCopy() {
        repo.create("kn-reader", ScriptLang.LUA, "-- @uses notifications\nreturn 1")
        post("kn-tag-10", "kn-kept", "kn-text")
        waitUntil("a copy for scripts") { mirror.get(KolahalNotificationListener.MIRROR_KEY)?.contains("kn-kept") == true }
        disallow()
        waitUntil("the copy goes") { mirror.get(KolahalNotificationListener.MIRROR_KEY) == null }
    }

    /** Notifications already showing when access is given are in the feed at once, but aren't logged as new. */
    @Test fun thoseShowingWhenAccessIsGivenAreInTheFeed() {
        disallow()
        post("kn-tag-11", "kn-early", "kn-text")
        waitUntil("it shows") { showing("kn-tag-11") }
        allow()
        val n = waitFor("in the feed") { inFeed("kn-early") }
        assertEquals("kn-text", n.text)
        assertFalse(n.removed)
        post("kn-tag-12", "kn-after", "kn-text")
        waitUntil("the next one's line") { lines("kn-after — kn-text").isNotEmpty() }
        assertTrue(lines("kn-early — kn-text").isEmpty())
    }

    /**
     * Access off and on again, as at each start of the process: a notification still showing, posted
     * again unchanged, keeps the line it had.
     */
    @Test fun anUnchangedRepostAfterReconnectingKeepsItsLine() {
        post("kn-tag-14", "kn-steady", "kn-text")
        waitUntil("its line") { lines("kn-steady — kn-text").size == 1 }
        disallow()
        allow()
        waitFor("in the feed again") { inFeed("kn-steady")?.takeIf { !it.removed } }
        post("kn-tag-14", "kn-steady", "kn-text")
        settled()
        post("kn-tag-15", "kn-next", "kn-text")
        waitUntil("the next one's line") { lines("kn-next — kn-text").isNotEmpty() }
        assertEquals(1, lines("kn-steady — kn-text").size)
    }

    /** A script set to run on a notification runs with it, told its title. */
    @Test fun aScriptOnNotificationRunsWithIt() {
        repo.create("kn-trigger", ScriptLang.LUA, "-- @on notification\n-- @uses notifications\nlog('kn saw ' .. event.title)")
        post("kn-tag-13", "kn-trig", "kn-text")
        waitUntil("the script's line", ms = 30_000) { logs.recent(400).any { it.id > before && it.source == "kn-trigger" && StyledText.plain(it.text) == "kn saw kn-trig" } }
    }

    private companion object {
        const val SHELL = "com.android.shell"
    }
}
