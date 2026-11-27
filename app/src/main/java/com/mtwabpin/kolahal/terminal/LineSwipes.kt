/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind

/**
 * What a swipe on a log line does (`log.swipe-left`, `log.swipe-right`), in the GUI log, the TTY log and a
 * search's results. Pure, so the rules are tested on the JVM (LineSwipesTest).
 * - The line shows the action's icon and word behind it while it moves ([label]), a delete in the error color.
 * - Past a third of the width it acts. A delete slides the line out; the others let it spring back.
 * - An action that does nothing on a line (run on a line without a command) draws nothing there and does nothing.
 * - none: the line doesn't move that way, and the drag goes to what is under the line (home's pager turns the page).
 */
enum class LineSwipe(val id: String, private val glyph: String, private val word: String) {
    COPY("copy", "⧉", "Copy"),
    DELETE("delete", "✕", "Delete"),
    STAR("star", "★", "Star"),
    FRESH("fresh", "▴", "Fresh"),
    RUN("run", "▸", "Run"),
    NONE("none", "", "");

    /** Drawn in the error color, as Delete line is in the menu. */
    val destructive: Boolean get() = this == DELETE

    /**
     * Whether it does something on [entry]: run needs the line's command (as the menu's Run again), none never
     * does. Fresh also needs a line shown above it, which only the log knows (TerminalViewModel.swipeDoes).
     */
    fun doesOn(entry: LogEntry): Boolean = when (this) {
        NONE -> false
        RUN -> commandOf(entry) != null
        else -> true
    }

    /** "⧉ Copy" for a line moving [right], "Copy ⧉" moving left: the icon on the outer edge, where the line moved from. */
    fun label(entry: LogEntry, right: Boolean): String {
        val (icon, text) = if (this == STAR && entry.starred) "☆" to "Unstar" else glyph to word
        return if (right) "$icon $text" else "$text $icon"
    }

    /** What a screen reader offers for it on [entry]: the menu's words for the same thing. */
    fun spoken(entry: LogEntry): String = when (this) {
        COPY -> "Copy"
        DELETE -> "Delete"
        STAR -> if (entry.starred) "Unstar line" else "Star line"
        FRESH -> FRESH_FROM_HERE
        RUN -> "Run again"
        NONE -> ""
    }

    companion object {
        /** The values of `log.swipe-left` and `log.swipe-right`, in the order Settings shows them. */
        val IDS: List<String> = entries.map { it.id }

        /** The menu's action, and what a screen reader calls the swipe. */
        const val FRESH_FROM_HERE = "Fresh from here"

        fun of(id: String): LineSwipe = entries.firstOrNull { it.id == id } ?: NONE

        /** The command a line runs again: the menu's Run again has the same condition. Null when it has none. */
        fun commandOf(entry: LogEntry): String? =
            entry.ctx.command ?: entry.text.takeIf { entry.kind == LogKind.COMMAND }
    }
}

/**
 * What a swipe each way does on a log line: [left] and [right] as the finger moves, on the screen.
 * - Set as `log.swipe-left` and `log.swipe-right`, each is what it says.
 * - While both are their defaults (left deletes, right copies), the two follow the page order as they always
 *   have: with the log first and pages beside it ([HomeOrder.pagesRight]), they trade, so the swipe that turns
 *   the page from anywhere else copies the line, never deletes it. In a right-to-left layout the pages, and so
 *   the defaults, are the other way round.
 */
data class LineSwipes(val left: LineSwipe, val right: LineSwipe) {
    /** What a drag by [dx] does: right for a positive one, left for a negative one, none for 0. */
    fun toward(dx: Float): LineSwipe = when {
        dx > 0f -> right
        dx < 0f -> left
        else -> LineSwipe.NONE
    }

    /** How far a line moves for a drag that took it [x]: no further than 0 toward a side that is none. */
    fun reach(x: Float): Float = if (toward(x) == LineSwipe.NONE) 0f else x

    /** Whether a line takes a sideways drag at all. With both none, it leaves every drag to what is under it. */
    val any: Boolean get() = left != LineSwipe.NONE || right != LineSwipe.NONE

    /** The actions set, each once, in the order a screen reader offers them. */
    val both: List<LineSwipe> get() = LineSwipe.entries.filter { it != LineSwipe.NONE && (it == left || it == right) }

    companion object {
        /** Right copies, left deletes: what nobody changed. */
        val DEFAULT = LineSwipes(LineSwipe.DELETE, LineSwipe.COPY)

        /** What [config] sets, in a layout that is right-to-left when [rtl] is. */
        fun of(config: Config, rtl: Boolean): LineSwipes =
            of(config[Keys.LOG_SWIPE_LEFT], config[Keys.LOG_SWIPE_RIGHT], HomeOrder.pagesRight(config), rtl)

        /**
         * The swipes [left] and [right] set. With both the defaults, the sides trade when the pages are on the
         * right of the log ([pagesRight], the log first), or on its left in a right-to-left layout.
         */
        fun of(left: String, right: String, pagesRight: Boolean, rtl: Boolean): LineSwipes {
            val l = LineSwipe.of(left)
            val r = LineSwipe.of(right)
            val defaults = left == Keys.LOG_SWIPE_LEFT.default && right == Keys.LOG_SWIPE_RIGHT.default
            return if (defaults && pagesRight != rtl) LineSwipes(r, l) else LineSwipes(l, r)
        }
    }
}
