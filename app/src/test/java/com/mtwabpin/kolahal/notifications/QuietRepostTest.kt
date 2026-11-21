package com.mtwabpin.kolahal.notifications

import android.app.Notification
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener.Companion.isMessage
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener.Companion.isNewLine
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener.Companion.isQuietRepost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietRepostTest {
    private val alertOnce = Notification.FLAG_ONLY_ALERT_ONCE

    @Test
    fun newKeysAlwaysCount() {
        assertFalse(
            isQuietRepost(
                known = false,
                flags = alertOnce,
                progressMax = 100,
                indeterminate = true
            )
        )
        assertFalse(
            isQuietRepost(
                known = false,
                flags = alertOnce,
                progressMax = 0,
                indeterminate = false,
                message = true,
                changed = false
            )
        )
    }

    @Test
    fun alertOnceRepostIsQuiet() =
        assertTrue(
            isQuietRepost(
                known = true,
                flags = alertOnce,
                progressMax = 0,
                indeterminate = false
            )
        )

    @Test
    fun progressRepostIsQuiet() {
        assertTrue(isQuietRepost(known = true, flags = 0, progressMax = 100, indeterminate = false))
        assertTrue(isQuietRepost(known = true, flags = 0, progressMax = 0, indeterminate = true))
    }

    @Test
    fun ordinaryRepostStillCounts() =
        assertFalse(
            isQuietRepost(
                known = true,
                flags = Notification.FLAG_AUTO_CANCEL,
                progressMax = 0,
                indeterminate = false
            )
        )

    /** A conversation's next message arrives as an alert-once update of the same notification. */
    @Test
    fun aMessageWithNewTextCounts() =
        assertFalse(
            isQuietRepost(
                known = true,
                flags = alertOnce,
                progressMax = 0,
                indeterminate = false,
                message = true,
                changed = true
            )
        )

    @Test
    fun aMessageRepostedUnchangedIsQuiet() =
        assertTrue(
            isQuietRepost(
                known = true,
                flags = alertOnce,
                progressMax = 0,
                indeterminate = false,
                message = true,
                changed = false
            )
        )

    /** A timer or a player changes its text every second; that isn't news. */
    @Test
    fun changedTextOutsideAMessageIsQuiet() =
        assertTrue(
            isQuietRepost(
                known = true,
                flags = alertOnce,
                progressMax = 0,
                indeterminate = false,
                message = false,
                changed = true
            )
        )

    @Test
    fun aMessageWithProgressIsStillQuiet() =
        assertTrue(
            isQuietRepost(
                known = true,
                flags = alertOnce,
                progressMax = 10,
                indeterminate = false,
                message = true,
                changed = true
            )
        )

    /** "are you free?" then "never mind, call me now" ten seconds later: both reach the log. */
    @Test
    fun everyNewMessageIsALine() {
        val first = 1_000_000L to "Alice — are you free?"
        assertTrue(
            isNewLine(
                first,
                "Alice — never mind, call me now",
                first.first + 10_000,
                message = true
            )
        )
        assertFalse(isNewLine(first, "Alice — are you free?", first.first + 10_000, message = true))
    }

    /** Anything else re-posted with new text keeps its first line for 30 seconds. */
    @Test
    fun otherUpdatesWaitHalfAMinute() {
        val first = 1_000_000L to "Mail — 3 new emails"
        assertFalse(isNewLine(first, "Mail — 4 new emails", first.first + 10_000, message = false))
        assertTrue(isNewLine(first, "Mail — 4 new emails", first.first + 30_000, message = false))
        assertTrue(isNewLine(null, "Mail — 4 new emails", first.first, message = false))
    }

    @Test
    fun messagesAreTheMessagingStyleOrTheMessageCategory() {
        assertTrue(isMessage("android.app.Notification\$MessagingStyle", null))
        assertTrue(
            isMessage(
                "android.app.Notification\$BigTextStyle",
                Notification.CATEGORY_MESSAGE
            )
        )
        assertFalse(
            isMessage(
                "android.app.Notification\$BigTextStyle",
                Notification.CATEGORY_EMAIL
            )
        )
        assertFalse(isMessage(null, null))
    }
}
