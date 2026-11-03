package com.mtwabpin.kolahal.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.productivity.ReminderScheduler
import com.mtwabpin.kolahal.productivity.ReminderStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kolahal's own notifications: a script's shows its title and text and opens what it links to, a
 * reminder offers Snooze and Done, and nothing is posted on a channel you turned off.
 */
@RunWith(AndroidJUnit4::class)
class NotifierTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val reminders = ReminderStore.get(context)
    private val logs = LogRepository.get(context)
    private val before = logs.maxId()

    @Before fun setUp() {
        // As the app asks for on first use. Not taken back after: Android stops an app whose permission is revoked.
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        Notifier.createChannels(context)
    }

    @After fun tearDown() {
        mine().forEach { nm.cancel(it.tag, it.id) }
        nm.deleteNotificationChannel("kn-blocked")
        reminders.all().filter { it.text.startsWith("kn ") }.forEach { ReminderScheduler.cancel(context, it.id); reminders.remove(it.id) }
        logs.recent(200).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    /** This test's notifications showing now. */
    private fun mine(): List<StatusBarNotification> =
        nm.activeNotifications.filter { it.tag?.startsWith("kn-") == true || (it.tag == Notifier.CHANNEL_REMINDERS && it.id in REMINDER_IDS) }

    private fun waitUntil(what: String, ms: Long = 8_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    private fun shown(tag: String, id: Int): StatusBarNotification? = nm.activeNotifications.firstOrNull { it.tag == tag && it.id == id }

    private fun Notification.text(key: String) = extras.getCharSequence(key)?.toString()

    /** Script notifications, reminders and scheduled runs each have a channel of their own, at their own importance. */
    @Test fun theThreeChannelsAreThere() {
        val scripts = nm.getNotificationChannel(Notifier.CHANNEL_SCRIPTS)
        assertEquals(context.getString(R.string.channel_scripts), scripts.name)
        assertEquals(context.getString(R.string.channel_scripts_desc), scripts.description)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, scripts.importance)
        val reminders = nm.getNotificationChannel(Notifier.CHANNEL_REMINDERS)
        assertEquals(context.getString(R.string.channel_reminders), reminders.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, reminders.importance)
        val running = nm.getNotificationChannel(Notifier.CHANNEL_RUNNING)
        assertEquals(context.getString(R.string.channel_running), running.name)
        assertEquals(NotificationManager.IMPORTANCE_MIN, running.importance)
    }

    /** A channel turned off posts nothing; one not made yet is no reason not to. */
    @Test fun aChannelTurnedOffCantPost() {
        assertTrue(Notifier.canPost(context))
        assertTrue(Notifier.canPost(context, "kn-not-made"))
        nm.createNotificationChannel(NotificationChannel("kn-blocked", "kn blocked", NotificationManager.IMPORTANCE_NONE))
        assertFalse(Notifier.canPost(context, "kn-blocked"))
    }

    /** A script's notification shows its title, its text in full and the script's name, opens the launcher or its link, and goes when tapped. */
    @Test fun aScriptsNotificationShowsWhatItSays() {
        assertTrue(Notifier.post(context, "kn-feed", "kn title", "kn body", url = null))
        val id = ("kn-feed" + "kn title" + "kn body").hashCode()
        waitUntil("it shows") { shown("kn-feed", id) != null }
        val n = shown("kn-feed", id)!!.notification
        assertEquals(Notifier.CHANNEL_SCRIPTS, n.channelId)
        assertEquals("kn title", n.text(Notification.EXTRA_TITLE))
        assertEquals("kn body", n.text(Notification.EXTRA_TEXT))
        assertEquals("kn body", n.text(Notification.EXTRA_BIG_TEXT))
        assertEquals("kn-feed", n.text(Notification.EXTRA_SUB_TEXT))
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(existing(id, Intent(context, MainActivity::class.java)), n.contentIntent)

        assertTrue(Notifier.post(context, "kn-link", "kn title", "kn body", url = "https://example.com/kn"))
        val linked = ("kn-link" + "kn title" + "kn body").hashCode()
        waitUntil("the linked one shows") { shown("kn-link", linked) != null }
        assertEquals(existing(linked, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/kn"))), shown("kn-link", linked)!!.notification.contentIntent)
    }

    /**
     * The tap Android already holds for [requestCode] and an intent like [intent] (action, data and
     * component; extras and flags aren't compared). Null when there is none.
     */
    private fun existing(requestCode: Int, intent: Intent): PendingIntent? =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            .also { assertTrue("no such tap for $intent", it != null) }

    /** With an id, a notification replaces the one before it (one per feed); without, each different text is one of its own. */
    @Test fun anIdReplacesTheOneBefore() {
        Notifier.post(context, "kn-one", "kn feed", "kn first", url = null, id = 7)
        Notifier.post(context, "kn-one", "kn feed", "kn second", url = null, id = 7)
        waitUntil("the second replaced the first") { shown("kn-one", 7)?.notification?.text(Notification.EXTRA_TEXT) == "kn second" }
        assertEquals(1, nm.activeNotifications.count { it.tag == "kn-one" })

        Notifier.post(context, "kn-many", "kn feed", "kn first", url = null)
        Notifier.post(context, "kn-many", "kn feed", "kn second", url = null)
        waitUntil("both show") { nm.activeNotifications.count { it.tag == "kn-many" } == 2 }
    }

    /** A reminder shows its text, says it's a reminder and offers Snooze 10 min, Snooze 1 hour and Done; cancelled, it goes. */
    @Test fun aReminderOffersSnoozeAndDone() {
        assertTrue(Notifier.postReminder(context, REMINDER_IDS[0].toLong(), "kn call back"))
        waitUntil("it shows") { shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[0]) != null }
        val n = shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[0])!!.notification
        assertEquals(Notifier.CHANNEL_REMINDERS, n.channelId)
        assertEquals("kn call back", n.text(Notification.EXTRA_TITLE))
        assertEquals("Reminder", n.text(Notification.EXTRA_TEXT))
        assertEquals(Notification.CATEGORY_REMINDER, n.category)
        assertEquals(listOf("Snooze 10 min", "Snooze 1 hour", "Done"), n.actions.map { it.title.toString() })

        Notifier.cancelReminder(context, REMINDER_IDS[0].toLong())
        waitUntil("it goes") { shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[0]) == null }
    }

    /** Done takes the reminder away and sets nothing again: for a second after, none is set. */
    @Test fun doneTakesTheReminderAway() {
        Notifier.postReminder(context, REMINDER_IDS[1].toLong(), "kn done")
        waitUntil("it shows") { shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[1]) != null }
        shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[1])!!.notification.actions[2].actionIntent.send()
        waitUntil("it goes") { shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[1]) == null }
        // A Done that wrongly snoozed would set it again after the notification went.
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue("set again", reminders.all().none { it.text == "kn done" })
            Thread.sleep(50)
        }
    }

    /** Snooze 1 hour takes it away and sets it again an hour from now, saying so in the log. */
    @Test fun snoozeSetsItAgainLater() {
        Notifier.postReminder(context, REMINDER_IDS[2].toLong(), "kn snooze")
        waitUntil("it shows") { shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[2]) != null }
        val tapped = System.currentTimeMillis()
        shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[2])!!.notification.actions[1].actionIntent.send()
        waitUntil("it goes") { shown(Notifier.CHANNEL_REMINDERS, REMINDER_IDS[2]) == null }
        waitUntil("set again") { reminders.all().any { it.text == "kn snooze" } }
        val again = reminders.all().single { it.text == "kn snooze" }
        assertTrue("${again.at - tapped} ms from now", again.at - tapped in 59 * 60_000L..61 * 60_000L)
        waitUntil("the log says so") { logs.recent(50).any { it.id > before && it.text == "Snoozed for 1h: kn snooze" } }
    }

    private companion object {
        val REMINDER_IDS = listOf(900_001, 900_002, 900_003)
    }
}
