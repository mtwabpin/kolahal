/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.shader.ShaderPlay
import com.mtwabpin.kolahal.ui.theme.Term
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * The pinned header's music line "♪ Title — Artist  ⏭" (drawn icons), above a track progress bar.
 * - A tap toggles play/pause.
 * - ⏭ skips to the next track. A long press does [onLongPress]: on the pinned header, the sheet
 *   of [mediaActions], as a pinned script's line opens its actions. Without one it skips too.
 * - [endPadding] keeps the end of the line free for the pinned header's dots. The bar continues
 *   under them.
 * - Its row is [pinnedRow] tall and the bar [barAbove] under it, as a pinned script's line.
 */
@Composable
internal fun NowPlayingLine(
    np: NowPlaying,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    endPadding: Dp = 0.dp,
    barAbove: Dp = Keys.HEADER_PINNED_BAR_ABOVE.default.dp,
    onLongPress: (() -> Unit)? = null,
) {
    // The track progress. It is computed every 250 ms, but stored only when the filled part of the
    // bar changes by at least a pixel, and it is read only when drawing the bar. An hour-long track
    // moves the bar one pixel every few seconds. Before, each change rebuilt and re-measured the whole line.
    val barWidth = remember { intArrayOf(0) }
    var progress by remember(np) { mutableFloatStateOf(np.progressNow()) }
    fun update() {
        val p = np.progressNow()
        val w = barWidth[0]
        if (w == 0 || (w * p).roundToInt() != (w * progress).roundToInt()) progress = p
    }

    // Update the position here, without waiting for MediaRepository events.
    // - The key is the whole np, because a seek, a repeat or the end of buffering keep the same
    //   title and `playing`.
    // - It runs only while on screen. A delay doesn't stop with the activity, and music often
    //   plays with the screen off.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(np, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (np.playing) {
                update()
                delay(250.milliseconds)
            }
            update()
        }
    }

    val row = pinnedRow()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("header-media")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = endPadding)
        ) {
            val iconSize = (Term.fontSize + 2).dp

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    // A screen reader hears what a tap and a long press do: "Pause" or "Play", "Actions" or "Next track".
                    .combinedClickable(
                        onClickLabel = if (np.playing) "Pause" else "Play",
                        onLongClickLabel = if (onLongPress != null) "Actions" else "Next track",
                        onClick = onToggle,
                        onLongClick = onLongPress ?: onNext
                    )
                    .heightIn(min = row)
            ) {
                if (np.playing) {
                    TintedIcon(
                        R.drawable.ic_music_note,
                        Term.prompt,
                        iconSize,
                        "Playing"
                    )
                } else {
                    TintedIcon(
                        R.drawable.ic_pause,
                        Term.dim,
                        iconSize,
                        "Paused"
                    )
                }

                Spacer(Modifier.width(6.dp))

                Text(
                    np.line,
                    style = Term.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(
                        onClickLabel = "next track",
                        onClick = onNext
                    )
                    .heightIn(min = row)
                    .widthIn(min = 48.dp)
            ) {
                TintedIcon(
                    R.drawable.ic_skip_next,
                    Term.dim,
                    (Term.fontSize + 4).dp,
                    "Next"
                )
            }
        }

        Spacer(Modifier.height(barAbove))
        // The playback progress bar. When the width changes, it redraws at once, without waiting for the
        // next pixel of progress.
        // While it plays and the log is on screen, the filled part is a moving wave, as media players draw it.
        // A full-screen screen or the screensaver over home hides the bar, and the wave stands still there.
        val covered by ShaderPlay.covered.collectAsStateWithLifecycle()
        ProgressTrack(
            { progress },
            Modifier.onSizeChanged { barWidth[0] = it.width; progress = np.progressNow() },
            moving = np.playing && LocalTerminalShown.current && covered == 0,
        )
    }
}

/** The current progress of [NowPlaying], from 0 to 1. It is 0 if the length or position is unknown. */
private fun NowPlaying.progressNow(): Float {
    val position = positionNow()
    return if (duration > 0 && position >= 0) (position.toFloat() / duration).coerceIn(
        0f,
        1f
    ) else 0f
}

/** A thin bar, filled up to [fraction] (0 to 1). */
@Composable
internal fun ProgressTrack(fraction: Float, fill: Color = Term.prompt) =
    ProgressTrack({ fraction }, fill = fill)

/**
 * A thin bar, filled up to [fraction] (0 to 1), in [fill] (the theme's accent unless a script picks a role).
 * - [fraction] is read only while drawing, so a change only redraws the bar. Nothing is rebuilt or
 *   measured again.
 * - The fill has the same width as a child with `fillMaxWidth(fraction)`: whole pixels, from the
 *   start side.
 * - With [moving], the filled part is a wave that runs along, as media players draw it while they
 *   play ([WaveTrack]).
 */
@Composable
internal fun ProgressTrack(
    fraction: () -> Float,
    modifier: Modifier = Modifier,
    fill: Color = Term.prompt,
    moving: Boolean = false
) {
    if (moving) {
        WaveTrack(fraction, modifier, fill)
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(50))
            .background(Term.dim.copy(alpha = 0.25f))
            .drawBehind {
                val w = (size.width * fraction()).roundToInt().toFloat()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                drawRect(fill, topLeft = Offset(x, 0f), size = Size(w, size.height))
            }
    )
}

/**
 * [ProgressTrack] while something plays: the filled part is a wave that runs along, the rest the
 * usual flat line.
 * - The wave reaches into the room above and below the bar (the header's bar spacing), so the
 *   header keeps its height.
 * - Only the drawing changes from frame to frame: the phase is read while drawing, so nothing is
 *   rebuilt or measured again. It moves only while this is shown: the caller passes [ProgressTrack]
 *   `moving` only while the log is on screen and no full-screen screen covers it, and frames stop
 *   while home is hidden. One path is drawn again on each frame, not a new one.
 * - With the phone's animations off, the wave stands still.
 */
@Composable
private fun WaveTrack(fraction: () -> Float, modifier: Modifier, fill: Color) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing)),
        label = "phase",
    )
    val track = Term.dim.copy(alpha = 0.25f)
    val path = remember { Path() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                val line = size.height
                val mid = size.height / 2
                val amplitude = WAVE_AMPLITUDE.toPx()
                val wavelength = WAVE_LENGTH.toPx()
                val end = (size.width * fraction()).coerceIn(0f, size.width)
                val rtl = layoutDirection == LayoutDirection.Rtl
                fun x(at: Float) = if (rtl) size.width - at else at
                // The rest of the track, flat.
                if (end < size.width) drawLine(
                    track,
                    Offset(x(end), mid),
                    Offset(x(size.width), mid),
                    strokeWidth = line,
                    cap = StrokeCap.Round
                )
                if (end <= 0f) return@drawBehind
                // The played part, as a wave that moves towards the end.
                val shift = phase * wavelength
                path.rewind()
                var at = 0f
                val step = 1.5.dp.toPx()
                while (true) {
                    val y = mid + amplitude * sin(2 * PI.toFloat() * (at - shift) / wavelength)
                    if (at == 0f) path.moveTo(x(at), y) else path.lineTo(x(at), y)
                    if (at >= end) break
                    at = (at + step).coerceAtMost(end)
                }
                drawPath(path, fill, style = Stroke(width = line, cap = StrokeCap.Round))
            }
    )
}

/** How long the wave takes to move by one wavelength. */
private const val WAVE_PERIOD_MS = 1200

/** The wave's height either side of the bar, and its length. */
private val WAVE_AMPLITUDE = 1.5.dp
private val WAVE_LENGTH = 16.dp

/**
 * The long-press sheet of the pinned header's music turn, as a pinned script's line has its own:
 * Next track, Previous track, and Open <player> when the player's app has a screen to open.
 * A tap still plays or pauses, and ⏭ still skips.
 */
internal fun mediaActions(
    context: android.content.Context,
    np: NowPlaying
): List<com.mtwabpin.kolahal.terminal.LogAction> {
    val media = com.mtwabpin.kolahal.control.MediaRepository.get(context)
    val actions = mutableListOf(
        com.mtwabpin.kolahal.terminal.LogAction("Next track") { media.send(com.mtwabpin.kolahal.control.MediaRepository.Action.NEXT) },
        com.mtwabpin.kolahal.terminal.LogAction("Previous track") { media.send(com.mtwabpin.kolahal.control.MediaRepository.Action.PREV) },
    )
    context.packageManager.getLaunchIntentForPackage(np.pkg)?.let { open ->
        actions += com.mtwabpin.kolahal.terminal.LogAction("Open ${np.app}") {
            runCatching { context.startActivity(open.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    return actions
}
