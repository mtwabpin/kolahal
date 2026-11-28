/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Room under the notice over the log's bottom ("Line deleted" with Undo, "Copied"): while it shows, the log scrolls
 * on past its newest line by up to the notice's height, so the line it covers comes up above it. Nothing moves when
 * the notice comes, as the room is taken only by a scroll; when it goes, the log settles back down to its bottom.
 *
 * The room is the log lifted by [lift] px, not a scroll of the list: the list stays at its newest line, so its rules
 * (it follows the newest line unless the user scrolled up: [LogScroll]) hold as they are, and a log too short to
 * scroll gets the room too. A scroll that the list can't take at its bottom lifts it, and a scroll back down lowers
 * it before the list scrolls ([modifier]). Both home styles have one.
 */
@Stable
internal class NoticeRoom {
    /** How far the log is lifted, in px: 0 to [max]. A settle's spring may take it a little under 0. */
    var lift by mutableFloatStateOf(0f)
        internal set

    /** The notice's height in px: the most the log lifts. */
    var max by mutableIntStateOf(0)

    /** Whether the notice shows, so the log may lift. */
    internal var open = false

    private val connection = object : NestedScrollConnection {
        // A scroll down lowers the log first, then the list scrolls with what is left.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val (next, used) = lower(lift, available.y)
            lift = next
            return Offset(0f, used)
        }

        // A scroll up that the list couldn't take (it is at its newest line) lifts the log.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!open) return Offset.Zero
            val (next, used) = raise(lift, max.toFloat(), available.y)
            lift = next
            return Offset(0f, used)
        }
    }

    /** For the log's list, after its tag: it is clipped to its box, takes the scrolls above, and is drawn lifted. */
    val modifier: Modifier = Modifier
        .clipToBounds()
        .nestedScroll(connection)
        .graphicsLayer { translationY = -lift }

    companion object {
        /**
         * A scroll of [dy] px (down is positive) with the log lifted by [lift]: the lift after it, and how much of
         * [dy] that took. Down lowers the log, never under 0; anything else takes nothing.
         */
        fun lower(lift: Float, dy: Float): Pair<Float, Float> {
            if (dy <= 0f || lift <= 0f) return lift to 0f
            val used = minOf(lift, dy)
            return lift - used to used
        }

        /**
         * What is left of a scroll of [dy] px after the list took its part: up (negative) lifts the log, up to [max].
         * The lift after it, and how much of [dy] that took (negative, as [dy]).
         */
        fun raise(lift: Float, max: Float, dy: Float): Pair<Float, Float> {
            if (dy >= 0f || lift >= max) return lift to 0f
            val added = minOf(max - lift, -dy)
            return lift + added to -added
        }
    }
}

/**
 * The log's [NoticeRoom] while the notice shows ([open]). When it goes, a lifted log settles back down on the theme's
 * fast spatial spring; with animations off it is down at once.
 */
@Composable
internal fun rememberNoticeRoom(open: Boolean): NoticeRoom {
    val room = remember { NoticeRoom() }
    room.open = open
    val settle = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(open) {
        if (!open && room.lift != 0f) animate(room.lift, 0f, animationSpec = settle) { value, _ -> room.lift = value }
    }
    return room
}
