package com.mtwabpin.kolahal.terminal

import android.app.Notification
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotificationActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting a notification's line dismisses the notification ([DismissOnDelete]): only once the delete's Undo is
 * gone, never on Undo, not for an ongoing one, not for one posted again since the line (a chat's later message),
 * not with the setting off or without notification access.
 */
class DismissOnDeleteTest {
    private val message = LogContext.of(
        LogContext.PKG to "com.example.chat",
        LogContext.COPY to "Alex — See you at six",
        KolahalNotificationListener.CTX_KEY to "0|com.example.chat|7|null|10001",
    )
    private val key = "0|com.example.chat|7|null|10001"

    /** What the offer's dismissal and its Undo did. */
    private val dismissed = ArrayList<String>()
    private var restored = 0
    private fun offer(key: String?) =
        DismissOnDelete.undoOffer("Line deleted", key, dismiss = { dismissed += it }) { restored++ }

    @Test
    fun aNotificationsLineGivesItsKey() {
        assertEquals(key, DismissOnDelete.keyOf(message, on = true) { true })
    }

    /** It is on by default: people who want the old behavior turn it off. */
    @Test
    fun theSettingIsOnByDefault() {
        assertTrue(Keys.NOTIFS_DISMISS_ON_DELETE.default)
        assertEquals("notifs.dismiss-on-delete", Keys.NOTIFS_DISMISS_ON_DELETE.key)
    }

    @Test
    fun notWithTheSettingOff() {
        assertNull(DismissOnDelete.keyOf(message, on = false) { true })
    }

    @Test
    fun notWithoutNotificationAccess() {
        assertNull(DismissOnDelete.keyOf(message, on = true) { false })
    }

    /** Another line dismisses nothing, and Android isn't asked about access for it. */
    @Test
    fun anotherLineHasNoKey() {
        var asked = false
        val command = LogContext.of(LogContext.COMMAND to "notifs")
        assertNull(DismissOnDelete.keyOf(command, on = true) { asked = true; true })
        assertNull(DismissOnDelete.keyOf(LogContext.EMPTY, on = true) { asked = true; true })
        assertFalse(asked)
    }

    /** The notification goes once Undo is no longer offered: the offer timed out, or another took its place. */
    @Test
    fun dismissedOnceUndoIsGone() {
        val offer = offer(key)
        assertEquals("Line deleted", offer.text)
        assertTrue("nothing goes while Undo is offered", dismissed.isEmpty())
        offer.expire()
        assertEquals(listOf(key), dismissed)
        assertEquals(0, restored)
    }

    /** Undo puts the line back and leaves the notification where it is. */
    @Test
    fun undoLeavesTheNotification() {
        val offer = offer(key)
        offer.undo()
        assertEquals(1, restored)
        assertTrue(dismissed.isEmpty())
    }

    /** A line that isn't a notification's, or the setting off, or no access: its Undo going dismisses nothing. */
    @Test
    fun noKeyDismissesNothing() {
        offer(null).expire()
        offer(DismissOnDelete.keyOf(message, on = false) { true }).expire()
        offer(DismissOnDelete.keyOf(message, on = true) { false }).expire()
        assertTrue(dismissed.isEmpty())
    }

    /**
     * A chat's three messages are three lines with one key, the notification posted again for each. Only the
     * newest line is about the notification showing: deleting an older one leaves the newer message in the shade.
     */
    @Test
    fun onlyTheLineAboutWhatShowsDismissesIt() {
        // Each line is written a moment after its post: the listener hears of it after the system stamps it.
        val posts = listOf(1_000_000L, 1_060_000L, 1_125_000L)
        val lines = posts.map { it + 40 }
        val showing = posts.last()
        assertEquals(listOf(false, false, true), lines.map { DismissOnDelete.about(it, showing) })
        // Before the second message came, the first line was the one showing.
        assertTrue(DismissOnDelete.about(lines[0], posts[0]))
    }

    /** A reminder posted again (the next day, or after it was dismissed) is newer than yesterday's line. */
    @Test
    fun aNotificationPostedAgainStays() {
        val line = 1_000_040L
        assertFalse(DismissOnDelete.about(line, line + 86_400_000L))
        // Its new line is about it.
        assertTrue(DismissOnDelete.about(line + 86_400_040L, line + 86_400_000L))
    }

    /** A clock that moved a little doesn't keep a line from its own notification; a second later is another post. */
    @Test
    fun aSmallSlack() {
        val line = 5_000_000L
        assertTrue(DismissOnDelete.about(line, line))
        assertTrue(DismissOnDelete.about(line, line + DismissOnDelete.SLACK_MS))
        assertFalse(DismissOnDelete.about(line, line + DismissOnDelete.SLACK_MS + 1))
    }

    /**
     * The check runs when the notification would go, not when the line is deleted: a message that comes while
     * the swipe's Undo is offered keeps the notification in the shade.
     */
    @Test
    fun aMessageDuringUndoKeepsTheNotification() {
        val line = 2_000_040L
        var showing = 2_000_000L
        val offer = DismissOnDelete.undoOffer("Line deleted", key, dismiss = { k ->
            if (DismissOnDelete.about(line, showing)) dismissed += k
        }) { restored++ }
        showing = 2_003_000L
        offer.expire()
        assertTrue(dismissed.isEmpty())
    }

    /** An ongoing notification (music, a call) can't be dismissed: only its line goes. Others can. */
    @Test
    fun anOngoingOneStays() {
        assertTrue(NotificationActions.clearable(0))
        assertTrue(NotificationActions.clearable(Notification.FLAG_AUTO_CANCEL or Notification.FLAG_ONLY_ALERT_ONCE))
        assertFalse(NotificationActions.clearable(Notification.FLAG_ONGOING_EVENT))
        assertFalse(NotificationActions.clearable(Notification.FLAG_NO_CLEAR))
        assertFalse(NotificationActions.clearable(Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_ONGOING_EVENT))
    }
}
