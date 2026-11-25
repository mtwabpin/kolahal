package com.mtwabpin.kolahal.terminal

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogKept
import com.mtwabpin.kolahal.NotificationAccess
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotifPrefs
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitForValue
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting a notification's line from home also dismisses the notification (`notifs.dismiss-on-delete`): after a
 * swipe once its Undo is no longer offered, never on Undo; with Delete line at once; not with the setting off, and
 * not on a clear. The notifications are the shell's, another app as far as Kolahal knows.
 */
@RunWith(AndroidJUnit4::class)
class DeletingANotificationsLineTest {
    @get:Rule
    val vms = TestViewModels()

    // A clear's Undo going deletes the phone's own lines too: the whole log comes back after each test.
    @get:Rule
    val logKept = LogKept()

    @get:Rule
    val configReset = ConfigReset()

    @get:Rule
    val access = NotificationAccess()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val config = Config.get(app)
    private val prefs = NotifPrefs.get(app)
    private var shellWasMuted = false
    private var before = 0L

    @Before
    fun setUp() {
        shellWasMuted = prefs.isMuted(SHELL)
        prefs.unmute(SHELL)
        access.set(true)
        waitUntil("notification access", ms = 15_000) { KolahalNotificationListener.connected != null }
        before = logs.maxId()
    }

    @After
    fun tearDown() {
        if (shellWasMuted) prefs.mute(SHELL) else prefs.unmute(SHELL)
        // The test's notifications go while access is on.
        val listener = KolahalNotificationListener.connected ?: return
        fun posted() =
            runCatching { listener.activeNotifications.orEmpty() }.getOrDefault(emptyArray())
                .filter { it.packageName == SHELL && it.tag?.startsWith("dd-") == true }
        posted().forEach { listener.cancelNotification(it.key) }
        waitUntil("the test's notifications go") { posted().isEmpty() }
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** Posts a notification from the shell; no spaces in any part. */
    private fun post(tag: String, title: String, text: String) =
        shell("cmd notification post -i @android:drawable/ic_dialog_alert -t $title $tag $text")

    /** Whether the system shows the shell's notification [tag], whoever listens. */
    private fun showing(tag: String) =
        shell("cmd notification list").lines().any { "|$SHELL|" in it && it.contains("|$tag|") }

    /** Posts a notification and returns its line, once it is in the log. */
    private fun posted(tag: String, title: String, text: String): LogEntry {
        post(tag, title, text)
        waitUntil("it shows") { showing(tag) }
        return waitForValue("its line") {
            logs.recent(400).firstOrNull { it.id > before && StyledText.plain(it.text) == "$title — $text" }
        }
    }

    /** A line that isn't a notification's, to swipe: its Undo takes the place of the one before. */
    private fun plainLine(text: String): LogEntry {
        val id = logs.add(LogKind.INFO, text)
        return logs.recent(400).single { it.id == id }
    }

    /** **Delete line** in [line]'s long-press menu, as home offers it. */
    private fun deleteLine(vm: TerminalViewModel, line: LogEntry) = vm.actionsFor(line).single { it.label == "Delete line" }

    /** [tag] still shows for about a second. */
    private fun staysShowing(tag: String) {
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue("$tag was dismissed", showing(tag))
            Thread.sleep(100)
        }
    }

    /** A swipe dismisses nothing while Undo is offered; once another delete takes its place, the notification goes. */
    @Test
    fun aSwipeDismissesOnceUndoIsGone() {
        val alex = posted("dd-tag-1", "dd-alex", "dd-hello")
        val maya = posted("dd-tag-2", "dd-maya", "dd-lunch")
        val vm = vms.terminal(app)

        main { vm.swipeDelete(alex) }
        waitUntil("its line goes") { logs.recent(400).none { it.id == alex.id } }
        assertEquals("Line deleted", vm.undo.value?.text)
        staysShowing("dd-tag-1")

        main { vm.swipeDelete(maya) }
        waitUntil("the first one is dismissed") { !showing("dd-tag-1") }
        staysShowing("dd-tag-2")

        // Undo brings the line back and leaves its notification where it is.
        main { vm.undo() }
        waitUntil("the line is back") { logs.recent(400).any { it.id == maya.id } }
        assertNull(vm.undo.value)
        staysShowing("dd-tag-2")
    }

    /** Undo offered for a few seconds, then gone by itself: the notification goes then. */
    @Test
    fun theUndoTimingOutDismissesIt() {
        val alex = posted("dd-tag-3", "dd-alex", "dd-later")
        val vm = vms.terminal(app)
        main { vm.swipeDelete(alex) }
        staysShowing("dd-tag-3")
        waitUntil("dismissed when Undo is gone", ms = 15_000) { vm.undo.value == null && !showing("dd-tag-3") }
    }

    /** Delete line in the long-press menu has no Undo: the notification goes at once. */
    @Test
    fun deleteLineDismissesAtOnce() {
        val alex = posted("dd-tag-4", "dd-alex", "dd-menu")
        val vm = vms.terminal(app)
        main { deleteLine(vm, alex).run() }
        waitUntil("dismissed") { !showing("dd-tag-4") }
        assertTrue(logs.recent(400).none { it.id == alex.id })
    }

    /** With the setting off, deleting a line only removes it from the log. */
    @Test
    fun withTheSettingOffItStays() {
        config.set(Keys.NOTIFS_DISMISS_ON_DELETE, "off")
        val alex = posted("dd-tag-5", "dd-alex", "dd-kept")
        val vm = vms.terminal(app)
        main { vm.swipeDelete(alex) }
        main { vm.swipeDelete(plainLine("dd-plain-1")) }
        staysShowing("dd-tag-5")
        val maya = posted("dd-tag-6", "dd-maya", "dd-kept")
        main { deleteLine(vm, maya).run() }
        waitUntil("its line goes") { logs.recent(400).none { it.id == maya.id } }
        staysShowing("dd-tag-6")
    }

    /** A clear takes the lines away, and its Undo going deletes them, but no notification is dismissed. */
    @Test
    fun aClearDismissesNothing() {
        posted("dd-tag-7", "dd-alex", "dd-cleared")
        val vm = vms.terminal(app)
        main { vm.clearScreen() }
        waitUntil("the clear's Undo") { vm.undo.value?.text == "Screen cleared" }
        // Another delete ends the clear's Undo: the cleared lines are deleted then.
        main { vm.swipeDelete(plainLine("dd-plain-2")) }
        staysShowing("dd-tag-7")
    }

    private companion object {
        const val SHELL = "com.android.shell"
    }
}
