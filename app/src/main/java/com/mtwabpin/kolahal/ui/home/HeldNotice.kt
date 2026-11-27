/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

/**
 * The notice over the bottom of the log ("Line deleted" with Undo, "Screen cleared", "Copied"): its words stay
 * while it fades out after the offer has ended, when there are none to read any more. Both home styles hold one.
 */
internal class HeldNotice {
    /** The words shown, the last ones while it fades out. */
    var text: String = ""
        private set

    /** Whether Undo shows after them. */
    var undo: Boolean = false
        private set

    /** Takes what shows [now] (null: nothing), and says whether the notice shows. Null keeps the last words. */
    fun update(now: String?, undo: Boolean): Boolean {
        if (now == null) return false
        text = now
        this.undo = undo
        return true
    }
}
