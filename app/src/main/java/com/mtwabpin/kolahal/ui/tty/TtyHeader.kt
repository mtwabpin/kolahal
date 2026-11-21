/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.HeaderTarget
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.PinnedTurns
import com.mtwabpin.kolahal.terminal.headerParts
import com.mtwabpin.kolahal.terminal.tty.HeaderCells
import com.mtwabpin.kolahal.terminal.tty.PinnedCells
import com.mtwabpin.kolahal.terminal.tty.filledCells
import com.mtwabpin.kolahal.ui.PinnedItem
import com.mtwabpin.kolahal.ui.rememberNow
import com.mtwabpin.kolahal.ui.rememberScreenReader
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Room kept clear on each side of the camera's cutout, as the GUI's header keeps. */
private val CUTOUT_GAP = 4.dp

/**
 * The header on the grid ([HeaderCells]): one row of cells with `header.padding` above and below
 * it, placed as the GUI's header is (beside the camera in full screen, else under the status bar,
 * or below both with `header.below-bar`). A part that a camera cutout would cover moves aside.
 * Each part is a node of its own (`header-<part>`), with the GUI's tap and words. A swipe down on
 * the row runs `gesture.swipe-down`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TtyHeader(
    status: Status,
    unlocks: Int,
    profiles: List<Profile>,
    todoCount: Int,
    config: Config,
    version: Int,
    onTarget: (HeaderTarget) -> Unit,
    onProfileTap: (Profile) -> Unit,
    onSwipeDown: () -> Unit,
) {
    val frame = LocalTtyFrame.current ?: return
    val (now, zone) = rememberNow()
    val parts = remember(now, zone, status, unlocks, profiles, todoCount, version) {
        headerParts(status, unlocks, profiles, todoCount, config, now.time)
    }
    val belowBar = remember(version) { config[Keys.HEADER_BELOW_BAR] }
    val padding = remember(version) { config[Keys.HEADER_PADDING].dp }
    val density = LocalDensity.current
    val barTop = WindowInsets.statusBars.getTop(density)
    // The band beside the camera: the cutout's height when the status bar is hidden.
    val band = if (belowBar) 0 else (WindowInsets.displayCutout.getTop(density) - barTop).coerceAtLeast(0)
    val topInsets = if (belowBar) WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout) else WindowInsets.statusBars
    val height = maxOf(band, frame.cellH + 2 * with(density) { padding.roundToPx() })
    val view = LocalView.current
    val cutouts = remember(band, barTop, view) {
        if (barTop > 0 || band == 0) emptyList()
        else view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty().filter { it.top < band }.map { it.left..it.right }
    }
    // The row's x in the window, to move the cutouts into the row's own pixels.
    var rowX by remember { mutableFloatStateOf(0f) }
    val gap = with(density) { CUTOUT_GAP.roundToPx() }
    val shift = rowX.roundToInt()
    val zones = remember(cutouts, shift, frame) {
        HeaderCells.zones(frame.cols, frame.originX, frame.cellW, cutouts.map { (it.first - shift)..(it.last - shift) }, gap)
    }
    // The zones are columns on the screen; the row is laid out from its start, which in a
    // right-to-left layout is the right edge, so they are turned around for it.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cells = remember(parts, frame, zones, rtl) {
        HeaderCells.row(parts, frame.cols, if (rtl) zones.map { (frame.cols - 1 - it.last)..(frame.cols - 1 - it.first) }.reversed() else zones)
    }
    val all = remember(parts) { parts.left + parts.right }
    val swipeDown by rememberUpdatedState(onSwipeDown)
    TtyBand(
        cells,
        height,
        Modifier
            .windowInsetsPadding(topInsets.only(WindowInsetsSides.Top))
            .testTag("header")
            .onPlaced { rowX = it.positionInWindow().x }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 24.dp.toPx()) swipeDown() },
                ) { _, dy -> total += dy }
            },
    ) { spot ->
        val part = all.getOrNull(spot.id) ?: return@TtyBand null
        SpotAction(
            onClick = {
                val profile = part.profile
                if (profile != null) onProfileTap(profile) else part.target?.let(onTarget)
            },
            tag = "header-${part.tag}",
            text = part.text.ifEmpty { null },
            description = part.description,
            clickLabel = part.label,
            role = null,
        )
    }
}

/**
 * The pinned line on the grid ([PinnedCells]): what's playing and the pinned scripts' status lines,
 * taking turns as the GUI's do ([PinnedTurns]), each its text row and its progress row under it,
 * with `header.pinned-bar-above` and `-below` of room around the progress row. The dots stay at
 * the row's end while the items slide. The same taps, long presses and words as the GUI's.
 */
@Composable
internal fun TtyPinned(
    nowPlaying: NowPlaying?,
    lines: List<StatusLine>,
    config: Config,
    version: Int,
    onMediaToggle: () -> Unit,
    onMediaNext: () -> Unit,
    onPinTap: (String) -> Unit,
    onPinLongPress: (String) -> Unit,
    onRefresh: () -> Unit,
    onMediaLongPress: (NowPlaying) -> Unit,
    screenReader: Boolean = rememberScreenReader(),
) {
    val frame = LocalTtyFrame.current ?: return
    val (now, _) = rememberNow()
    val seconds = remember(version) { config[Keys.HEADER_PINNED_SECONDS] }
    val dotsUpTo = remember(version) { config[Keys.HEADER_PINNED_DOTS] }
    val density = LocalDensity.current
    val barAbove = with(density) { remember(version) { config[Keys.HEADER_PINNED_BAR_ABOVE] }.dp.roundToPx() }
    val barBelow = with(density) { remember(version) { config[Keys.HEADER_PINNED_BAR_BELOW] }.dp.roundToPx() }
    val topGap = with(density) { 4.dp.roundToPx() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val refresh by rememberUpdatedState(onRefresh)
    if (lines.any { it.refresh != null }) LaunchedEffect(lifecycle) {
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
    // The dots take at least 48 dp, so they are easy to hit.
    val dotsCells = if (dots) maxOf(PinnedCells.dotsWidth(items.size) + 1, (with(density) { 48.dp.roundToPx() } + frame.cellW - 1) / frame.cellW) else 0
    // The bar row draws only its top eighth (▔), so the item ends there, as tall as the GUI's nearly.
    val itemHeight = topGap + frame.cellH + barAbove + (frame.cellH + 7) / 8 + barBelow
    Box(
        Modifier
            .fillMaxWidth()
            .testTag("pinned-header")
            // Only watch the touches, so the item's own tap and long press still work.
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
        ) { item ->
            when (item) {
                is PinnedItem.Media -> TtyMediaItem(item.np, frame, dotsCells, itemHeight, topGap, barAbove, onMediaToggle, onMediaNext, onMediaLongPress)
                is PinnedItem.Script -> {
                    val line = item.line
                    val cells = remember(line, frame, dotsCells) {
                        PinnedCells.script(line.text, line.color?.let(Ink::of) ?: Ink.SCRIPT, line.progress, frame.cols, dotsCells)
                    }
                    PinnedRows(cells, frame, itemHeight, topGap, barAbove) {
                        SpotAction(
                            onClick = { onPinTap(line.name) },
                            tag = "pinned-${line.name}",
                            text = line.text,
                            description = "${line.name}: ${line.text}",
                            onLongClick = { onPinLongPress(line.name) },
                            longClickLabel = "actions",
                            role = null,
                        )
                    }
                }
            }
        }
        if (dots) {
            val cells = remember(items.size, index) { PinnedCells.dots(items.size, index) }
            // At the end of the row: on the left in a right-to-left layout.
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val x = if (rtl) frame.originX else frame.originX + (frame.cols - cells.cols) * frame.cellW
            Box(
                Modifier
                    .fillMaxWidth()
                    .exactHeight(itemHeight)
                    .drawBehind { paintCells(frame.grid, (if (rtl) cells.mirrored else cells).grid, x.toFloat(), topGap.toFloat()) },
            ) {
                val left = frame.originX + (frame.cols - dotsCells) * frame.cellW
                SpotNode(
                    SpotAction(
                        onClick = { current = PinnedTurns.next(keys, keys[index]) },
                        tag = "pinned-dots",
                        description = "${index + 1} of ${items.size}",
                        clickLabel = "Next item",
                    ),
                    left, 0, frame.widthPx - left, itemHeight,
                ) {}
            }
        }
    }
}

/** What's playing: its line and ⏭, its track's progress under them, moving while it plays. */
@Composable
private fun TtyMediaItem(
    np: NowPlaying,
    frame: TtyFrame,
    room: Int,
    height: Int,
    topGap: Int,
    barAbove: Int,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onLongPress: (NowPlaying) -> Unit,
) {
    // The progress in whole cells: the row is laid out again only when a cell fills.
    var filled by remember(np, frame) { mutableStateOf(filledCells(np.progressNow(), frame.cols)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(np, lifecycle, frame) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (np.playing) {
                filled = filledCells(np.progressNow(), frame.cols)
                delay(500.milliseconds)
            }
            filled = filledCells(np.progressNow(), frame.cols)
        }
    }
    val cells = remember(np.line, np.playing, filled, frame, room) { PinnedCells.media(np.line, np.playing, filled, frame.cols, room) }
    PinnedRows(cells, frame, height, topGap, barAbove) { spot ->
        if (spot.id == PinnedCells.NEXT) SpotAction(
            onClick = onNext,
            description = "Next",
            clickLabel = "next track",
        ) else SpotAction(
            onClick = onToggle,
            tag = "header-media",
            text = np.line,
            clickLabel = if (np.playing) "Pause" else "Play",
            onLongClick = { onLongPress(np) },
            longClickLabel = "Actions",
            role = null,
        )
    }
}

/** A pinned item's two rows: its text [topGap] from the top, and its progress row [barAbove] under it. */
@Composable
private fun PinnedRows(
    cells: com.mtwabpin.kolahal.terminal.tty.HomeCells,
    frame: TtyFrame,
    height: Int,
    topGap: Int,
    barAbove: Int,
    action: (com.mtwabpin.kolahal.terminal.tty.Spot) -> SpotAction?,
) {
    val m = frame.grid.metrics
    Box(
        Modifier
            .fillMaxWidth()
            .exactHeight(height)
            .drawBehind {
                val x = frame.originX.toFloat()
                // Turned around in a right-to-left layout; the nodes' offsets turn with the layout.
                val cells = if (layoutDirection == LayoutDirection.Rtl) cells.mirrored else cells
                paintCells(frame.grid, cells.grid, x, topGap.toFloat(), 0, 1)
                // Row 1 drawn [barAbove] lower than it would be under row 0.
                paintCells(frame.grid, cells.grid, x, (topGap + barAbove).toFloat(), 1, 2)
            },
    ) {
        for (s in cells.spots) {
            val a = action(s) ?: continue
            val left = frame.originX + s.rect.col * m.cellW
            // The item takes the whole height; ⏭ takes it too, out to the screen's edge when it is last.
            val right = if (s.rect.col + s.rect.cols >= frame.cols) frame.widthPx else left + s.rect.cols * m.cellW
            SpotNode(a, if (s.rect.col == 0) 0 else left, 0, right - if (s.rect.col == 0) 0 else left, height) {}
        }
    }
}

/** How far along [NowPlaying] is, from 0 to 1; 0 when the length or the position is unknown. */
private fun NowPlaying.progressNow(): Float {
    val position = positionNow()
    return if (duration > 0 && position >= 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}
