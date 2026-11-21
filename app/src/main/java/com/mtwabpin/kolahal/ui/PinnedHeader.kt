/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.terminal.PinnedTurns
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** An item the pinned header shows in turn: first what is playing, then the pinned status lines. */
internal sealed interface PinnedItem {
    val key: String

    data class Media(val np: NowPlaying) : PinnedItem {
        override val key get() = MEDIA
    }

    data class Script(val line: StatusLine) : PinnedItem {
        override val key get() = "script:${line.name}"
    }

    companion object {
        const val MEDIA = "media"
    }
}

/**
 * A pinned line, from the top:
 * - [PINNED_TOP] of space,
 * - the text row ([pinnedRow]),
 * - `header.pinned-bar-above` of space, the 2 dp bar, `header.pinned-bar-below` of space.
 * At font size 14 and the default gaps that is 4 + 19 + 5 + 2 + 5 = 35 dp.
 * Taps on the space still reach the item: Compose sends a tap near a small target to it, when
 * nothing else is under the finger.
 */
internal val PINNED_TOP = 4.dp

/**
 * Height of a pinned item's text row: one line of the small text, or the now playing line's
 * largest icon if that is taller. Every item uses it, so the items take turns without moving the log.
 */
@Composable
internal fun pinnedRow(): Dp =
    with(LocalDensity.current) { maxOf(Term.small.lineHeight.toDp(), (Term.fontSize + 4).dp) }

/**
 * The pinned header: a line under the header (or at the top, when the header is off). It shows what
 * is playing and the status lines of pinned scripts.
 * - With more than one item, they take turns and slide up every `header.pinned-seconds`.
 * - While a finger is on the line, the current item stays.
 * - Dots at the end show which item is visible, for up to `header.pinned-dots` items. A tap on
 *   them shows the next item; they take a 48 dp wide touch, the line's end padding included.
 * - While a screen reader is on ([screenReader]), the items don't take turns: the item under its
 *   focus would be replaced. The dots show then whatever `header.pinned-dots` says, so the next item
 *   is one double-tap away.
 * - Changed text updates in place.
 * - With nothing to show, it takes no space.
 * - While it is on screen, it calls [onRefresh] every second to run lines that ask to run again.
 */
@Composable
internal fun PinnedHeader(
    nowPlaying: NowPlaying?,
    lines: List<StatusLine>,
    config: Config,
    version: Int,
    onMediaToggle: () -> Unit,
    onMediaNext: () -> Unit,
    onPinTap: (String) -> Unit,
    onPinLongPress: (String) -> Unit,
    onRefresh: () -> Unit = {},
    /** A long press on the music turn: its sheet ([mediaActions]). Null: it skips to the next track. */
    onMediaLongPress: ((NowPlaying) -> Unit)? = null,
    /** Whether a screen reader (touch exploration) is on. Then the items stay until the dots are tapped. */
    screenReader: Boolean = rememberScreenReader(),
) {
    // `now` updates every minute, so a paused media session leaves the header after 30 minutes.
    val (now, _) = rememberNow()
    val seconds = remember(version) { config[Keys.HEADER_PINNED_SECONDS] }
    val dotsUpTo = remember(version) { config[Keys.HEADER_PINNED_DOTS] }
    val barAbove = remember(version) { config[Keys.HEADER_PINNED_BAR_ABOVE].dp }
    val barBelow = remember(version) { config[Keys.HEADER_PINNED_BAR_BELOW].dp }
    val row = pinnedRow()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val refresh by rememberUpdatedState(onRefresh)
    if (lines.any { it.refresh != null }) LaunchedEffect(lifecycle) {
        // Run only while home is on screen. A plain delay loop would not stop with the activity.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(1.seconds)
            }
        }
    }

    val items = buildList {
        nowPlaying?.takeIf { it.showInHeader(now.time) }?.let { add(PinnedItem.Media(it)) }
        lines.forEach { add(PinnedItem.Script(it)) }
    }
    if (items.isEmpty()) return
    val keys = items.map { it.key }
    var current by remember { mutableStateOf<String?>(null) }
    var held by remember { mutableStateOf(false) }
    val index = PinnedTurns.index(keys, current)

    // Each turn lasts the full time. A text change does not restart the timer, but releasing the finger does.
    LaunchedEffect(keys, seconds, held, screenReader, lifecycle) {
        if (keys.size < 2 || held || screenReader) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(seconds.seconds)
                current = PinnedTurns.next(keys, current ?: keys.first())
            }
        }
    }

    val dots = PinnedTurns.showsDots(items.size, dotsUpTo) || (screenReader && items.size > 1)
    // Space at the end of the line for the dots. The dots stay in place while the items slide.
    // With the line's end padding it is at least 48 dp: all of it takes the dots' tap.
    val dotsRoom = if (dots) maxOf((items.size * 8 + 4).dp, DOTS_TOUCH - END_PADDING) else 0.dp
    Box(
        Modifier
            .fillMaxWidth()
            .testTag("pinned-header")
            // Only observe touches, do not consume them, so the item's own tap and long-press still work.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        held = event.changes.any { it.pressed }
                    }
                }
            },
    ) {
        AnimatedContent(
            targetState = items[index],
            contentKey = { it.key },
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "pinned",
            modifier = Modifier.padding(
                start = 12.dp,
                end = END_PADDING,
                top = PINNED_TOP,
                bottom = barBelow
            ),
        ) { item ->
            when (item) {
                is PinnedItem.Media -> NowPlayingLine(
                    item.np,
                    onMediaToggle,
                    onMediaNext,
                    endPadding = dotsRoom,
                    barAbove = barAbove,
                    onLongPress = onMediaLongPress?.let { f -> { f(item.np) } })

                is PinnedItem.Script -> PinLine(
                    item.line,
                    onPinTap,
                    onPinLongPress,
                    dotsRoom,
                    row,
                    barAbove
                )
            }
        }
        // The dots' touch: the whole height of the line, over the room kept for them and the end padding.
        if (dots) PinnedDots(
            items.size, index, row,
            onNext = { current = PinnedTurns.next(keys, keys[index]) },
            modifier = Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.End)
                .width(dotsRoom + END_PADDING),
        )
    }
}

/** The line's padding at the end. The dots' touch reaches over it to the screen's edge. */
private val END_PADDING = 12.dp

/** The width of the dots' touch: Android's least size for a touch target. */
private val DOTS_TOUCH = 48.dp

/**
 * Whether a screen reader is on: TalkBack and the like turn on touch exploration. Follows changes
 * while shown.
 */
@Composable
internal fun rememberScreenReader(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * A pinned script's status line: its text alone, so the line has room for it. A screen reader
 * hears the script's name first, and the long-press menu names it. The progress bar shows only
 * when there is a progress value.
 */
@Composable
private fun PinLine(
    line: StatusLine,
    onTap: (String) -> Unit,
    onLongPress: (String) -> Unit,
    endPadding: Dp,
    row: Dp,
    barAbove: Dp
) {
    // The whole item takes the tap, the bar too: the row is short.
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("pinned-${line.name}")
            .combinedClickable(
                onLongClickLabel = "actions",
                onLongClick = { onLongPress(line.name) }) { onTap(line.name) }
            .semantics { contentDescription = "${line.name}: ${line.text}" },
    ) {
        Text(
            line.text,
            style = Term.small.copy(color = Term.script),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = row)
                .wrapContentHeight()
                .padding(end = endPadding),
        )
        Spacer(Modifier.height(barAbove))
        // Keep the bar's space even without a bar, so every item has the same height and the log does not move.
        val progress = line.progress
        if (progress != null) ProgressTrack(
            progress,
            line.color?.let(::colorOf) ?: Term.prompt
        ) else Spacer(Modifier.height(2.dp))
    }
}

/**
 * Shows which of [count] items is visible: the dot at [index] uses the accent color. The dots sit
 * in the text row ([row] high, under [PINNED_TOP]), at the end of [modifier]'s touch area. A tap
 * anywhere in that area calls [onNext].
 */
@Composable
private fun PinnedDots(count: Int, index: Int, row: Dp, onNext: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .testTag("pinned-dots")
            .clickable(onClickLabel = "Next item", role = Role.Button, onClick = onNext)
            .semantics { contentDescription = "${index + 1} of $count" },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = PINNED_TOP, end = END_PADDING)
                .height(row),
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (i == index) Term.prompt else Term.dim.copy(alpha = 0.45f))
                )
            }
        }
    }
}
