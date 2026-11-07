package com.mtwabpin.kolahal.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.control.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * The pinned header's music line "♪ Title — Artist  ⏭" (drawn icons), above a track progress bar.
 * - A tap toggles play/pause.
 * - A long press or ⏭ skips to the next track.
 * - [endPadding] keeps the end of the line free for the pinned header's dots. The bar continues
 *   under them.
 */
@Composable
internal fun NowPlayingLine(
    np: NowPlaying,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    endPadding: Dp = 0.dp,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("header-media")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(end = endPadding)
        ) {
            val iconSize = (Term.fontSize + 2).dp

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = onNext
                    )
                    .heightIn(min = 48.dp)
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
                    .heightIn(min = 48.dp)
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

        // The playback progress bar. When the width changes, it redraws at once, without waiting for the
        // next pixel of progress.
        ProgressTrack({ progress }, Modifier.onSizeChanged { barWidth[0] = it.width; progress = np.progressNow() })
    }
}

/** The current progress of [NowPlaying], from 0 to 1. It is 0 if the length or position is unknown. */
private fun NowPlaying.progressNow(): Float {
    val position = positionNow()
    return if (duration > 0 && position >= 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** A thin bar, filled up to [fraction] (0 to 1). */
@Composable
internal fun ProgressTrack(fraction: Float, fill: Color = Term.prompt) = ProgressTrack({ fraction }, fill = fill)

/**
 * A thin bar, filled up to [fraction] (0 to 1), in [fill] (the theme's accent unless a script picks a role).
 * - [fraction] is read only while drawing, so a change only redraws the bar. Nothing is rebuilt or
 *   measured again.
 * - The fill has the same width as a child with `fillMaxWidth(fraction)`: whole pixels, from the
 *   start side.
 */
@Composable
internal fun ProgressTrack(fraction: () -> Float, modifier: Modifier = Modifier, fill: Color = Term.prompt) {
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
