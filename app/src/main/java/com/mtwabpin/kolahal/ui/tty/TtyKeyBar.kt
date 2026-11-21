/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.KeyBarKey
import com.mtwabpin.kolahal.terminal.tty.KeyBarCells

/** Keys that repeat while held, as on the GUI's bar. */
private val REPEATING = setOf(KeyBarKey.UP, KeyBarKey.DOWN, KeyBarKey.LEFT, KeyBarKey.RIGHT, KeyBarKey.BKSP)

/** Marks a canceled key press, apart from the null a timeout gives. */
private object CANCELED

/** The narrowest a key gets while the keys share a row (the GUI's), and the span of each key once they scroll. */
private val SHARED_KEY_MIN = 36.dp
private val SCROLLED_KEY = 48.dp

/**
 * The key bar on the grid ([KeyBarCells]): its rows (`keybar.row1`, `keybar.row2`), each
 * `keybar.height` tall, the keys' labels in the middle of equal spans on the surface color, a
 * muted `│` between two keys. The keys and what they do are the GUI's:
 * - arrows and backspace repeat while held; the others act when the finger lifts on them;
 * - a sideways drag across a row that doesn't scroll moves the cursor;
 * - with more keys than fit, each takes a finger's width and the row scrolls.
 * A pressed key is inverse. Each row has the tag `keybar`, each key `key-<name>`.
 */
@Composable
internal fun TtyKeyBar(specs: List<String>, rowHeight: Dp, onKey: (KeyBarKey) -> Unit, onText: (String) -> Unit) {
    val frame = LocalTtyFrame.current ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(frame.grid.palette.color(Ink.SURFACE))),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        specs.filter { it.isNotBlank() }.forEach { TtyKeyRow(it, rowHeight, onKey, onText, frame) }
    }
}

@Composable
private fun TtyKeyRow(spec: String, rowHeight: Dp, onKey: (KeyBarKey) -> Unit, onText: (String) -> Unit, frame: TtyFrame) {
    val keys = remember(spec) { KeyBarCells.keys(spec) }
    if (keys.isEmpty()) return
    val labels = remember(keys) { keys.map { (k, t) -> KeyBarCells.label(k, t) } }
    val density = LocalDensity.current
    val m = frame.grid.metrics
    fun cells(dp: Dp) = (with(density) { dp.roundToPx() } + m.cellW - 1) / m.cellW
    val shared = cells(SHARED_KEY_MIN)
    val fits = frame.cols / keys.size >= shared
    var pressed by remember { mutableIntStateOf(-1) }
    val row = remember(labels, frame, pressed, fits) {
        KeyBarCells.row(labels, frame.cols, if (fits) shared else cells(SCROLLED_KEY), pressed)
    }
    val height = maxOf(with(density) { rowHeight.roundToPx() }, m.cellH)
    val haptics = LocalHapticFeedback.current
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnText by rememberUpdatedState(onText)
    val press: (Int) -> Unit = { i ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val (named, token) = keys[i]
        if (named != null) currentOnKey(named) else currentOnText(token)
    }
    val width = if (fits) frame.widthPx else frame.originX * 2 + row.cols * m.cellW
    val scroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxWidth()
            .exactHeight(height)
            .testTag("keybar")
            .then(
                if (fits) Modifier.pointerInput(Unit) {
                    var acc = 0f
                    val step = 14.dp.toPx()
                    detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                        change.consume()
                        acc += dx
                        while (acc >= step) {
                            currentOnKey(KeyBarKey.RIGHT); acc -= step
                        }
                        while (acc <= -step) {
                            currentOnKey(KeyBarKey.LEFT); acc += step
                        }
                    }
                } else Modifier.horizontalScroll(scroll),
            ),
    ) {
        val top = (height - m.cellH) / 2
        Box(
            Modifier
                .exactSize(width, height)
                .drawBehind {
                    // In a right-to-left layout the keys are drawn in reverse order; their nodes' offsets turn with the layout.
                    val shown = if (layoutDirection == LayoutDirection.Rtl) row.mirrored else row
                    frame.grid.painter.drawBand(drawContext.canvas.nativeCanvas, shown.grid, frame.originX.toFloat(), top.toFloat(), 0f, size.height)
                },
        ) {
            for (spot in row.spots) key(spot.id) {
                val i = spot.id
                val (named, token) = keys[i]
                // The first and last keys reach the screen's edges.
                val left = if (i == 0) 0 else frame.originX + spot.rect.col * m.cellW
                val right = if (i == keys.lastIndex) width else frame.originX + (spot.rect.col + spot.rect.cols) * m.cellW
                Box(
                    Modifier
                        .offset { IntOffset(left, 0) }
                        .exactSize(right - left, height)
                        .testTag("key-${token.lowercase()}")
                        .semantics {
                            role = Role.Button
                            if (named != null) contentDescription = named.name.lowercase() else text = AnnotatedString(token)
                            onClick { press(i); true }
                        }
                        .pointerInput(i, named, token) {
                            awaitEachGesture {
                                awaitFirstDown()
                                pressed = i
                                try {
                                    if (named !in REPEATING) {
                                        // Plain keys act on release, so a drag that starts on ⏎ does nothing.
                                        if (waitForUpOrCancellation() != null) press(i)
                                        return@awaitEachGesture
                                    }
                                    // Hold to repeat: now, after 400 ms, then every 50 ms, until the finger
                                    // lifts or a drag takes the pointer.
                                    press(i)
                                    var delayMs = 400L
                                    while (true) {
                                        val outcome = withTimeoutOrNull(delayMs) { waitForUpOrCancellation() ?: CANCELED }
                                        if (outcome != null) break
                                        press(i)
                                        delayMs = 50L
                                    }
                                } finally {
                                    pressed = -1
                                }
                            }
                        },
                )
            }
        }
    }
}
