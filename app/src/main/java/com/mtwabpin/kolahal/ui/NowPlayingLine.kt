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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.control.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Header line "♪ Title — Artist  ⏭" (drawn icons) over a bar for how far along the track is: tap
 * toggles play/pause, long-press or ⏭ skips to the next track.
 */
@Composable
internal fun NowPlayingLine(
    np: NowPlaying,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    val small = Term.text.copy(fontSize = (Term.fontSize - 1).sp)

    // How far along: worked out every 250 ms, but stored only when the bar's filled part changes by a
    // pixel, and read only to draw the bar. An hour-long track moves it a pixel every few seconds,
    // and each change used to build and measure the whole line again.
    val barWidth = remember { intArrayOf(0) }
    var progress by remember(np) { mutableFloatStateOf(np.progressNow()) }
    fun update() {
        val p = np.progressNow()
        val w = barWidth[0]
        if (w == 0 || (w * p).roundToInt() != (w * progress).roundToInt()) progress = p
    }

    // Update position locally without requiring MediaRepository events. Keyed on all of np: a seek,
    // a repeat or buffering ending leave the title and `playing` as they were. Only while on screen:
    // a delay doesn't stop with the activity, and music often plays with the screen off.
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
            modifier = Modifier.fillMaxWidth()
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
                    style = small.copy(color = Term.dim),
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

        // Playback progress track. A new width is drawn at once, not at the next pixel's worth of track.
        ProgressTrack({ progress }, Modifier.onSizeChanged { barWidth[0] = it.width; progress = np.progressNow() })
    }
}

/** How far along [NowPlaying] is now, 0 to 1; 0 when the length or position isn't known. */
private fun NowPlaying.progressNow(): Float {
    val position = positionNow()
    return if (duration > 0 && position >= 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** A thin bar, filled as far as [fraction] (0 to 1). */
@Composable
internal fun ProgressTrack(fraction: Float) = ProgressTrack({ fraction })

/**
 * A thin bar, filled as far as [fraction] (0 to 1), read only while drawing: a change draws the bar
 * again, without building or measuring anything. The fill is as wide as a child at
 * `fillMaxWidth(fraction)` would be: whole pixels, from the start side.
 */
@Composable
internal fun ProgressTrack(fraction: () -> Float, modifier: Modifier = Modifier) {
    val fill = Term.prompt
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
