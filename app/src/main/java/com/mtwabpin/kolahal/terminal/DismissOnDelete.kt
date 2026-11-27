/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener

/**
 * Deleting a notification's line from the log also dismisses the notification (`notifs.dismiss-on-delete`).
 * Pure, so the rule is tested on the JVM (DismissOnDeleteTest).
 * - Only a line the user deletes: a swipe (and TalkBack's Delete), or **Delete line** in its long-press menu.
 *   Clearing the log, deleting a command with its output, and lines that leave the log by themselves (its
 *   limits) dismiss nothing. `notifs clear` dismisses them all.
 * - A dismissed notification can't come back. After a swipe it goes only once the delete's Undo is no longer
 *   offered ([UndoOffer.expire]). Undo leaves it in the shade, and so does the app's process ending before then.
 *   **Delete line** has no Undo, so it dismisses at once.
 * - Nothing with the setting off, or without notification access.
 * - One that can't be dismissed (an ongoing one: music, a call) stays, and one already gone is no error and
 *   says nothing ([com.mtwabpin.kolahal.notifications.NotificationActions.dismissIfClearable]).
 * - Only while the notification showing is the one the line is about ([about]). One key holds many lines: a
 *   chat's later messages post the same notification again, and so does a reminder the next day. Deleting an
 *   older line leaves the newer notification in the shade.
 *
 * The key and the post time are enough: the notification's content is never read for this.
 */
object DismissOnDelete {
    /**
     * How much later than its line a notification may have been posted and still be the one the line is about.
     * The system stamps a post before the listener hears of it, and the line is written after, so a line's own
     * post is never later than the line. This only absorbs a clock that moved a little; a message posted within
     * it counts as the line's.
     */
    const val SLACK_MS = 1_000L

    /**
     * Whether the notification showing now, posted at [postTime], is the one a line written at [lineTime] is
     * about: not posted again since (a later message in the same chat, a reminder posted again).
     *
     * The post time is checked when the notification is dismissed, not when the line is deleted. So a newer
     * one that arrives while a swipe's Undo is offered stays too, and a newer one whose app writes no line
     * (muted in the log) is never taken for the line's. "The newest line with the key" would get both wrong.
     */
    fun about(lineTime: Long, postTime: Long): Boolean = postTime <= lineTime + SLACK_MS

    /**
     * The key of the notification that deleting a line with [ctx] dismisses. Null for a line that isn't a
     * notification's, with the setting [on] false, or without notification [access] (asked only for a notification's line).
     */
    fun keyOf(ctx: LogContext, on: Boolean, access: () -> Boolean): String? {
        val key = ctx.values[KolahalNotificationListener.CTX_KEY] ?: return null
        return key.takeIf { on && access() }
    }

    /**
     * The Undo offered for a swiped line: [undo] puts the line back. Once Undo is no longer offered (it timed out,
     * or another offer took its place), the notification [key] is [dismiss]ed, if there is one. [dismiss] checks
     * that it is still the line's ([about]): it runs then, not now.
     */
    fun undoOffer(text: String, key: String?, dismiss: (String) -> Unit, undo: () -> Unit): UndoOffer =
        UndoOffer(text, expire = { key?.let(dismiss) }, undo = undo)
}
