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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StatusLine
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** What the pinned header shows in turn: what's playing, then the status lines you pinned. */
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

/** The pinned header's turns and runs, apart from Compose. */
internal object PinnedTurns {
    /** Whether the dots show for [count] items, with `header.pinned-dots` at [upTo]: one item needs none. */
    fun showsDots(count: Int, upTo: Int) = count in 2..upTo

    /** Which of [keys] shows for [current]: its own place, or the first once it has gone. */
    fun index(keys: List<String>, current: String?) = keys.indexOf(current).coerceAtLeast(0)

    /** The one after [current], back to the first after the last. */
    fun next(keys: List<String>, current: String?): String? =
        if (keys.isEmpty()) null else keys[(index(keys, current) + 1) % keys.size]

    /**
     * The lines to run again at [now] (ms): those asking for a refresh whose last run ([lastRun], by
     * name, on the same clock) is that many seconds ago or more, or that haven't run yet.
     */
    fun due(lines: List<StatusLine>, lastRun: Map<String, Long>, now: Long): List<String> = lines.mapNotNull { p ->
        val every = p.refresh ?: return@mapNotNull null
        val last = lastRun[p.name]
        p.name.takeIf { last == null || now - last >= every * 1000L }
    }
}

/** Height of the pinned header's line, as the now playing line's. */
private val PINNED_ROW = 48.dp

/**
 * The pinned header: a line under the header (or at the top, with the header off) for what's
 * playing and the status lines of the scripts you pinned. With more than one they take turns,
 * sliding up every `header.pinned-seconds`; a finger on the line keeps the one showing. Dots at its
 * end say which one shows, for up to `header.pinned-dots` items. A changed text shows in place. With
 * nothing to show it takes no room. While it's on screen, the lines that ask to run again are run
 * ([onRefresh], each second).
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
    // `now` ticks each minute, so a paused session drops out after 30 minutes.
    val (now, _) = rememberNow()
    val seconds = remember(version) { config[Keys.HEADER_PINNED_SECONDS] }
    val dotsUpTo = remember(version) { config[Keys.HEADER_PINNED_DOTS] }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val refresh by rememberUpdatedState(onRefresh)
    if (lines.any { it.refresh != null }) LaunchedEffect(lifecycle) {
        // Only while home is on screen: a delay doesn't stop with the activity.
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

    // Each turn is the whole time: a text that changes doesn't start it again, a release does.
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
    // Room at the line's end for the dots, which stay put as the items slide.
    val dotsRoom = if (dots) (items.size * 8 + 4).dp else 0.dp
    Box(
        Modifier
            .fillMaxWidth()
            .testTag("pinned-header")
            // Watched, not taken: the item's own tap and long-press still get the touch.
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

/** A pinned script's status line: its name, then its text; its bar only with a progress. */
@Composable
private fun PinLine(line: StatusLine, onTap: (String) -> Unit, onLongPress: (String) -> Unit, endPadding: Dp) {
    Column(Modifier.fillMaxWidth().testTag("pinned-${line.name}")) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Term.dim)) { append("${line.name} ▸ ") }
                withStyle(SpanStyle(color = Term.script)) { append(line.text) }
            },
            style = Term.text.copy(fontSize = (Term.fontSize - 1).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onLongClickLabel = "actions", onLongClick = { onLongPress(line.name) }) { onTap(line.name) }
                .heightIn(min = PINNED_ROW)
                .wrapContentHeight()
                .padding(end = endPadding),
        )
        // The bar's room stays without one: the line is as tall for every item, and the log doesn't move.
        val progress = line.progress
        if (progress != null) ProgressTrack(progress) else Spacer(Modifier.height(2.dp))
    }
}

/** Which of [count] items shows: [index]'s dot in the accent colour. */
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
