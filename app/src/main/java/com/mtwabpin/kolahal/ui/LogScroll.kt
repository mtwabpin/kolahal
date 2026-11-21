/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.mtwabpin.kolahal.terminal.LogRow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Where home's log is scrolled: the list's state, whether it follows the newest line, and the line
 * a search result jumped to. It sits above home's body, so it is the same whichever body draws
 * the log. [rememberLogScroll] makes one and runs its effects.
 */
@Stable
internal class LogScroll(val list: LazyListState) {
    /**
     * Whether new lines scroll into view. Only the user's scrolls change it: inserting items shifts
     * the anchor, and that must not stop following.
     */
    var followNewest by mutableStateOf(true)

    /** The line a search result jumped to, highlighted for a moment. */
    var flash by mutableStateOf<Long?>(null)

    /** Whether the list is scrolled away from the newest lines, so "Scroll to bottom" shows. */
    val awayFromBottom by derivedStateOf { list.firstVisibleItemIndex > 2 }

    /**
     * Follows the newest line again and scrolls to it. The scroll is requested for the list's next
     * layout, never forced inside this one: scrollToItem measures at once, which can happen inside
     * a layout pass under the test clock.
     */
    fun toNewest() {
        followNewest = true
        list.requestScrollToItem(0)
    }
}

/**
 * The log's [LogScroll], with its effects:
 * - it follows the newest line (index 0 with reverseLayout) unless the user scrolled up;
 * - near the oldest loaded line (the top of the reversed list) it asks for older ones ([onLoadOlder]);
 * - back at the newest lines it says so ([onAtNewest]), so older pages can be dropped;
 * - once [jumpTo]'s line is loaded, it scrolls to it, with a few newer lines below it, and
 *   highlights it briefly ([LogScroll.flash]); then [onJumpDone].
 * [rows] are the log's rows, newest first, as the list shows them.
 */
@Composable
internal fun rememberLogScroll(
    rows: List<LogRow>,
    jumpTo: Long?,
    onJumpDone: () -> Unit,
    onAtNewest: () -> Unit,
    onLoadOlder: () -> Unit,
): LogScroll {
    val list = rememberLazyListState()
    val scroll = remember(list) { LogScroll(list) }
    val atNewest by rememberUpdatedState(onAtNewest)
    val loadOlder by rememberUpdatedState(onLoadOlder)
    val jumpDone by rememberUpdatedState(onJumpDone)
    LaunchedEffect(list) {
        snapshotFlow {
            list.isScrollInProgress to
                    (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 48)
        }.collect { (scrolling, atBottom) ->
            // Also check when the list has stopped at the bottom. A fling that stops at the newest
            // line can end in the same frame it arrives, with no "scrolling and at bottom" state
            // between. Inserting items does not count as the list stopping.
            if (scrolling) scroll.followNewest = atBottom
            else if (atBottom) {
                scroll.followNewest = true
                // Back at the newest lines: older pages loaded while scrolling up can be dropped.
                atNewest()
            }
        }
    }
    // Keyed on the newest *row* (rows are built after entries), so the scroll targets the new line.
    // Skip it while a finger or a fling is still scrolling: a forced scroll would yield to those too.
    LaunchedEffect(rows.firstOrNull()?.key) {
        if (scroll.followNewest && !list.isScrollInProgress) list.requestScrollToItem(0)
    }
    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index
                ?: 0) >= info.totalItemsCount - 5
        }.collect { nearTop -> if (nearTop) loadOlder() }
    }
    LaunchedEffect(jumpTo, rows) {
        val id = jumpTo ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is LogRow.Entry && it.entry.id == id }
        if (index < 0) return@LaunchedEffect
        // If it is one of the newest lines, keep following new ones.
        scroll.followNewest = index <= 3
        list.requestScrollToItem((index - 3).coerceAtLeast(0))
        jumpDone()
        scroll.flash = id
    }
    LaunchedEffect(scroll.flash) {
        if (scroll.flash != null) {
            kotlinx.coroutines.delay(2_500.milliseconds)
            scroll.flash = null
        }
    }
    return scroll
}
