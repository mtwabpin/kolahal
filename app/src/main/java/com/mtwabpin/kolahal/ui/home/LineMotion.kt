/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mtwabpin.kolahal.terminal.UndoOffer
import kotlin.time.Duration.Companion.milliseconds

/**
 * Whether the log's lines move now: for a moment after a line is deleted and after its Undo offer ends (Undo, or
 * the offer timed out), the deleted line fades out while the lines around it close the gap, and on Undo it fades
 * in again while they open it ([lineMotion]). Everything else the log does stays at once: new lines, a command's
 * output growing, scrolling, the log filling from storage, a clear and its Undo, a filter, search.
 */
@Stable
internal class LineMotion {
    /** The lines animate their coming, going and moving. */
    var on by mutableStateOf(false)
        private set

    private var last: UndoOffer? = null

    /**
     * Takes the Undo offer [now]: the lines move from now on when it changed as [moves] says, and stop when it
     * changed otherwise. The same offer again changes nothing.
     */
    internal fun see(now: UndoOffer?) {
        if (now === last) return
        on = moves(last, now)
        last = now
    }

    /**
     * A line is about to go or come back: the lines move from now on, before the log changes, so each one has its
     * motion when the change comes. The offer that follows keeps them moving for [WINDOW] ([see]).
     */
    internal fun start() {
        on = true
    }

    internal fun stop() {
        on = false
    }

    companion object {
        /**
         * How long the lines move after the offer changes. The line goes or comes back after the log is written,
         * in a frame or a few; its animation is about a quarter of a second.
         */
        val WINDOW = 1_000.milliseconds

        /**
         * Whether the lines move when the Undo offer goes from [before] to [now]: a line's offer came (the line goes),
         * or one ended with no other in its place (it comes back on Undo; nothing happens when it timed out). A clear's
         * offer moves nothing, coming or going: every line goes or comes back at once.
         */
        fun moves(before: UndoOffer?, now: UndoOffer?): Boolean = when {
            now === before -> false
            now != null -> now.oneLine
            else -> before?.oneLine == true
        }
    }
}

/** Turns the lines' motion on for [LineMotion.WINDOW] once [undoOffer] changes as [LineMotion.moves] says. */
@Composable
internal fun LineMotion.Follow(undoOffer: UndoOffer?) {
    // Seen while composing, not in an effect: the line may go in the same frame the offer comes, and the lines
    // must have their motion by the time the list lays them out.
    see(undoOffer)
    LaunchedEffect(undoOffer) {
        if (on) {
            kotlinx.coroutines.delay(LineMotion.WINDOW)
            stop()
        }
    }
}

/**
 * A log line's item in the list: while [motion] is on, it fades in when it comes, fades out when it goes, and slides
 * to its new place when others come or go, on the theme's Expressive motion (effects for the fades, the fast
 * spatial spring for the move: each about a quarter of a second). Otherwise it is where it is at once. With the
 * phone's animations off, all of it is at once.
 */
@Composable
internal fun LazyItemScope.lineMotion(motion: LineMotion): Modifier {
    if (!motion.on) return Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    val scheme = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = scheme.defaultEffectsSpec(),
        placementSpec = scheme.fastSpatialSpec(),
        fadeOutSpec = scheme.defaultEffectsSpec(),
    )
}
