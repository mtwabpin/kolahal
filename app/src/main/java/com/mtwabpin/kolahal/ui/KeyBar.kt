package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.terminal.KeyBarKey

/**
 * Drawn icons for the symbol keys. ESC and TAB stay as words.
 * As text, ⇤ ⏎ ⌫ and similar symbols come from fallback fonts and look uneven.
 * ⇤ is drawn as ⇥ mirrored.
 */
private fun KeyBarKey.icon(): Int? = when (this) {
    KeyBarKey.UP -> R.drawable.ic_arrow_up
    KeyBarKey.DOWN -> R.drawable.ic_arrow_down
    KeyBarKey.LEFT -> R.drawable.ic_arrow_back
    KeyBarKey.RIGHT -> R.drawable.ic_arrow_forward
    KeyBarKey.HOME, KeyBarKey.END -> R.drawable.ic_keyboard_tab
    KeyBarKey.ENTER -> R.drawable.ic_keyboard_return
    KeyBarKey.KBD -> R.drawable.ic_keyboard
    KeyBarKey.SPACE -> R.drawable.ic_space_bar
    KeyBarKey.BKSP -> R.drawable.ic_backspace
    KeyBarKey.ESC, KeyBarKey.TAB -> null
}

/** Marks a cancelled key press. It is different from the null that the timeout returns. */
private object CANCELLED

/** Keys that repeat while held. */
private val REPEATING = setOf(KeyBarKey.UP, KeyBarKey.DOWN, KeyBarKey.LEFT, KeyBarKey.RIGHT, KeyBarKey.BKSP)

/**
 * On-screen keys that touch keyboards don't have: history, cursor movement and a few symbols.
 * - The keys share the width while each gets at least [SHARED_KEY_MIN]. With more keys, the row
 *   scrolls, and a fade shows that more keys are hidden.
 * - Arrows and backspace repeat while held.
 * - Dragging sideways across a row that is not scrolling moves the cursor.
 */
@Composable
internal fun KeyBar(specs: List<String>, rowHeight: Dp, onKey: (KeyBarKey) -> Unit, onText: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Term.surface),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        specs.filter { it.isNotBlank() }.forEach { KeyRow(it, rowHeight, onKey, onText) }
    }
}

@Composable
private fun KeyRow(spec: String, rowHeight: Dp, onKey: (KeyBarKey) -> Unit, onText: (String) -> Unit) {
    val keys = remember(spec) {
        spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { token -> KeyBarKey.of(token) to token }
    }
    val haptics = LocalHapticFeedback.current
    // Read the callbacks through updated state. A key's gesture handler below lives longer than the
    // composition that created it.
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnText by rememberUpdatedState(onText)
    val press: (KeyBarKey?, String) -> Unit = { named, token ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (named != null) currentOnKey(named) else currentOnText(token)
    }
    val bar = Modifier
        .fillMaxWidth()
        .height(rowHeight)
        .testTag("keybar")
        .background(Term.surface)

    @Composable
    fun Key(named: KeyBarKey?, token: String, modifier: Modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxHeight()
                .pointerInput(named, token) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (named !in REPEATING) {
                            // Plain keys act on release, so a drag or scroll that starts on ⏎ does nothing.
                            if (waitForUpOrCancellation() != null) press(named, token)
                            return@awaitEachGesture
                        }
                        // Hold to repeat: act now, then after 400ms, then every 50ms. A drag or scroll
                        // consumes the pointer (waitForUpOrCancellation returns null), and that must
                        // stop the repeat.
                        press(named, token)
                        var delayMs = 400L
                        while (true) {
                            val outcome = withTimeoutOrNull(delayMs) { waitForUpOrCancellation() ?: CANCELLED }
                            if (outcome != null) break // released or cancelled; null means still held
                            press(named, token)
                            delayMs = 50L
                        }
                    }
                },
        ) {
            // The padding is inside the touch area (after pointerInput), so the whole key can be tapped.
            val icon = named?.icon()
            if (icon != null) {
                Image(
                    painterResource(icon),
                    contentDescription = named.name.lowercase(),
                    colorFilter = ColorFilter.tint(Term.fg),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size((Term.fontSize + 4).dp)
                        .then(if (named == KeyBarKey.HOME) Modifier.scale(-1f, 1f) else Modifier),
                )
            } else {
                Text(
                    named?.label ?: token,
                    style = Term.text.copy(color = Term.fg, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth / keys.size >= SHARED_KEY_MIN) {
            Row(
                bar.pointerInput(Unit) {
                    var acc = 0f
                    val step = 14.dp.toPx()
                    detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                        change.consume()
                        acc += dx
                        while (acc >= step) { currentOnKey(KeyBarKey.RIGHT); acc -= step }
                        while (acc <= -step) { currentOnKey(KeyBarKey.LEFT); acc += step }
                    }
                }
            ) {
                keys.forEach { (named, token) -> Key(named, token, Modifier.weight(1f)) }
            }
        } else {
            Box(bar) {
                LazyRow(Modifier.fillMaxSize()) {
                    itemsIndexed(keys) { _, (named, token) ->
                        Key(named, token, Modifier.widthIn(min = 48.dp))
                    }
                }
                // A fade on the right edge shows that more keys are available by scrolling.
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(24.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Term.surface)))
                )
            }
        }
    }
}

/**
 * The narrowest a key gets while the keys share the row: about a keyboard key's width. On a 360 dp
 * screen, 10 keys fit; the default row has 9.
 */
private val SHARED_KEY_MIN = 36.dp
