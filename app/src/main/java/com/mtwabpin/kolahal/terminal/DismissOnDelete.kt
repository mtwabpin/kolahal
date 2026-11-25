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
 *
 * The key is enough: the notification's content is never read for this.
 */
object DismissOnDelete {
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
     * or another offer took its place), the notification [key] is [dismiss]ed, if there is one.
     */
    fun undoOffer(text: String, key: String?, dismiss: (String) -> Unit, undo: () -> Unit): UndoOffer =
        UndoOffer(text, expire = { key?.let(dismiss) }, undo = undo)
}
