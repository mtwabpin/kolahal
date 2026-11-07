package com.mtwabpin.kolahal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StatusLine
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

/** The pinned header's rotation and refresh logic. It does not depend on Compose. */
internal object PinnedTurns {
    /** Whether the dots show for [count] items, with `header.pinned-dots` set to [upTo]. One item needs no dots. */
    fun showsDots(count: Int, upTo: Int) = count in 2..upTo

    /** The index to show for [current]: its own index, or the first one if it is gone. */
    fun index(keys: List<String>, current: String?) = keys.indexOf(current).coerceAtLeast(0)

    /** The key after [current]. After the last one, it wraps to the first. */
    fun next(keys: List<String>, current: String?): String? =
        if (keys.isEmpty()) null else keys[(index(keys, current) + 1) % keys.size]

    /**
     * Returns the names of the lines to run again at [now] (ms). A line is due if it asks for a
     * refresh and either has not run yet, or its last run ([lastRun], by name, same clock) was at
     * least that many seconds ago.
     */
    fun due(lines: List<StatusLine>, lastRun: Map<String, Long>, now: Long): List<String> = lines.mapNotNull { p ->
        val every = p.refresh ?: return@mapNotNull null
        val last = lastRun[p.name]
        p.name.takeIf { last == null || now - last >= every * 1000L }
    }
}

/**
 * Height of a pinned item's text row. The now playing line uses it too, so the items take turns
 * without moving the log. With the bar and the padding, the whole line is 30 dp.
 * - It is short, so the bar sits just under the text.
 * - Taps on the padding still reach the item: Compose sends a tap near a small target to it, when
 *   nothing else is under the finger.
 */
internal val PINNED_ROW = 24.dp

/**
 * The pinned header: a line under the header (or at the top, when the header is off). It shows what
 * is playing and the status lines of pinned scripts.
 * - With more than one item, they take turns and slide up every `header.pinned-seconds`.
 * - While a finger is on the line, the current item stays.
 * - Dots at the end show which item is visible, for up to `header.pinned-dots` items.
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
) {
    // `now` updates every minute, so a paused media session leaves the header after 30 minutes.
    val (now, _) = rememberNow()
    val seconds = remember(version) { config[Keys.HEADER_PINNED_SECONDS] }
    val dotsUpTo = remember(version) { config[Keys.HEADER_PINNED_DOTS] }
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
    LaunchedEffect(keys, seconds, held, lifecycle) {
        if (keys.size < 2 || held) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(seconds.seconds)
                current = PinnedTurns.next(keys, current ?: keys.first())
            }
        }
    }

    val dots = PinnedTurns.showsDots(items.size, dotsUpTo)
    // Space at the end of the line for the dots. The dots stay in place while the items slide.
    val dotsRoom = if (dots) (items.size * 8 + 4).dp else 0.dp
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
            }
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        AnimatedContent(
            targetState = items[index],
            contentKey = { it.key },
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "pinned",
        ) { item ->
            when (item) {
                is PinnedItem.Media -> NowPlayingLine(item.np, onMediaToggle, onMediaNext, endPadding = dotsRoom)
                is PinnedItem.Script -> PinLine(item.line, onPinTap, onPinLongPress, dotsRoom)
            }
        }
        if (dots) PinnedDots(items.size, index, Modifier.align(Alignment.TopEnd).height(PINNED_ROW))
    }
}

/**
 * A pinned script's status line: its text alone, so the line has room for it. A screen reader
 * hears the script's name first, and the long-press menu names it. The progress bar shows only
 * when there is a progress value.
 */
@Composable
private fun PinLine(line: StatusLine, onTap: (String) -> Unit, onLongPress: (String) -> Unit, endPadding: Dp) {
    // The whole item takes the tap, the bar too: the row is short.
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("pinned-${line.name}")
            .combinedClickable(onLongClickLabel = "actions", onLongClick = { onLongPress(line.name) }) { onTap(line.name) }
            .semantics { contentDescription = "${line.name}: ${line.text}" },
    ) {
        Text(
            line.text,
            style = Term.small.copy(color = Term.script),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PINNED_ROW)
                .wrapContentHeight()
                .padding(end = endPadding),
        )
        // Keep the bar's space even without a bar, so every item has the same height and the log does not move.
        val progress = line.progress
        if (progress != null) ProgressTrack(progress, line.colour?.let(::colourOf) ?: Term.prompt) else Spacer(Modifier.height(2.dp))
    }
}

/** Shows which of [count] items is visible: the dot at [index] uses the accent colour. */
@Composable
private fun PinnedDots(count: Int, index: Int, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag("pinned-dots").semantics { contentDescription = "${index + 1} of $count" },
    ) {
        repeat(count) { i ->
            Box(Modifier.size(4.dp).clip(CircleShape).background(if (i == index) Term.prompt else Term.dim.copy(alpha = 0.45f)))
        }
    }
}
