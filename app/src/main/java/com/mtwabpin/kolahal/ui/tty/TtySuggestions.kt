/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.terminal.Suggestion
import com.mtwabpin.kolahal.terminal.tty.SuggestionCells
import com.mtwabpin.kolahal.ui.Term
import com.mtwabpin.kolahal.ui.completionHelp

/**
 * The tag of each suggestion on the grid, in the list and in the chips. Both have the GUI's tag,
 * `suggestions`, as a whole; this one tells them from the GUI's (`tty.suggestions gui`).
 */
private const val SUGGESTION_TAG = "tty-suggestion"

/**
 * `suggestions.style list` on the grid ([SuggestionCells.row]): the matches stacked above the
 * prompt, the best one at the bottom, nearest the thumb, as the GUI's list.
 * - Each row is `chips.height` tall at least, so it is easy to hit, and taller when its
 *   description wraps (`suggestions.wrap`).
 * - The row Enter acts on has its name inverse and `↵` at its end.
 * - At most 40 % of the screen tall; more matches scroll. A tap picks one; a long press on an app
 *   opens its actions.
 */
@Composable
internal fun TtySuggestionList(
    items: List<Suggestion>,
    onPick: (Suggestion) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    wrapHelp: Boolean,
) {
    if (items.isEmpty()) return
    val frame = LocalTtyFrame.current ?: return
    val density = LocalDensity.current
    val maxHeight = with(density) { (LocalWindowInfo.current.containerSize.height * 0.4f).toDp() }
    val chip = with(density) { Term.chipHeight.dp.roundToPx() }
    // The best match is what Enter acts on: back to it whenever it changes.
    val state = rememberLazyListState()
    LaunchedEffect(items.first().key) { state.scrollToItem(0) }
    LazyColumn(
        state = state,
        reverseLayout = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .testTag("suggestions"),
    ) {
        itemsIndexed(items, key = { _, s -> s.key }) { index, s ->
            TtySuggestionRow(s, index == 0 && s.enter, wrapHelp, frame, chip, onPick, onAppLongPress)
        }
    }
}

/** One row of [TtySuggestionList], laid out again only when it or the grid changes. */
@Composable
private fun TtySuggestionRow(
    s: Suggestion,
    enter: Boolean,
    wrap: Boolean,
    frame: TtyFrame,
    chipPx: Int,
    onPick: (Suggestion) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
) {
    val help = remember(s) {
        (s as? Suggestion.Completion)?.let { it.help ?: completionHelp(it.label, it.replacement) }
    }
    val cells = remember(s, help, frame, enter, wrap) { SuggestionCells.row(s, help, frame.cols, enter, wrap) }
    TtyBand(cells, maxOf(chipPx, cells.rows * frame.cellH)) {
        SpotAction(
            onClick = { onPick(s) },
            tag = SUGGESTION_TAG,
            text = (s as? Suggestion.Completion)?.big?.first ?: s.label,
            more = (s as? Suggestion.Completion)?.big?.second?.takeIf { it.isNotBlank() } ?: help,
            onLongClick = (s as? Suggestion.App)?.let { app -> { onAppLongPress(app.app) } },
            longClickLabel = if (s is Suggestion.App) "actions" else null,
            role = null,
        )
    }
}

/**
 * `suggestions.style chips` on the grid ([SuggestionCells.strip]): ` $ name ` chips on the surface
 * color, a cell apart, in one row, or two with `suggestions.rows 2` and more than three chips. The
 * chip Enter acts on is inverse. Each row is `chips.height` tall at least; the strip scrolls
 * sideways when the chips are wider than the screen, and goes back to its start when the best
 * match changes.
 */
@Composable
internal fun TtySuggestionStrip(items: List<Suggestion>, rows: Int, onPick: (Suggestion) -> Unit) {
    if (items.isEmpty()) return
    val frame = LocalTtyFrame.current ?: return
    val density = LocalDensity.current
    val chip = with(density) { Term.chipHeight.dp.roundToPx() }
    val lines = remember(items, rows) { SuggestionCells.strip(items, rows) }
    val scroll = rememberScrollState()
    LaunchedEffect(items.first().key) { scroll.scrollTo(0) }
    val m = frame.grid.metrics
    val height = maxOf(chip, m.cellH)
    val gap = with(density) { 4.dp.roundToPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .testTag("suggestions")
            .horizontalScroll(scroll),
    ) {
        Column {
            for ((r, line) in lines.withIndex()) {
                val width = frame.originX * 2 + line.cols * m.cellW
                val top = (height - m.cellH) / 2
                Box(
                    Modifier
                        .exactSize(width, height + if (r < lines.lastIndex) gap else 0)
                        // The best match at the start: on the right in a right-to-left layout.
                        .drawBehind { paintCells(frame.grid, (if (layoutDirection == LayoutDirection.Rtl) line.mirrored else line).grid, frame.originX.toFloat(), top.toFloat()) },
                ) {
                    for (spot in line.spots) key(spot.id) {
                        val s = items[spot.id]
                        SpotNode(
                            SpotAction(onClick = { onPick(s) }, tag = SUGGESTION_TAG, text = s.label, role = null),
                            frame.originX + spot.rect.col * m.cellW, 0, spot.rect.cols * m.cellW, height,
                        ) {}
                    }
                }
            }
        }
    }
}
