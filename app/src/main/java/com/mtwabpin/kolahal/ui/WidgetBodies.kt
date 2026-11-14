package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/*
 * The content of the kinds with parts of their own to tap or to tick: buttons, toggles, clock,
 * agenda and media. [KindBody] calls them. The first six kinds are in WidgetKinds.kt.
 *
 * Rules for every body here:
 * - With a null `onTap`, nothing is clickable (the Widgets screen).
 * - Time comes from the header's clock ([rememberNow]). A tick every second runs only while the
 *   widgets page is in view ([LocalWidgetsShown]) and the body shows seconds.
 * - Nothing moves for ever, and nothing but drawing runs on the main thread.
 */

// ---- shared by buttons and toggles ----------------------------------------------------------

/** A button or a switch grows with its cell up to this height. A taller cell leaves room around it. */
private val MAX_CONTROL = 64.dp

/** From this width, a button has its icon and its label side by side. */
private val SIDE_BY_SIDE = 104.dp

/** From this height, a button has its icon over its label, or a label of two lines. */
private val TWO_LINES = 56.dp

/**
 * What a Material button inside a card does with a press that is held. A Material button takes the
 * press before the card does and has no long-press of its own. So a long-press on a card full of
 * buttons would do nothing.
 * - [held] is true after a press was held for the long-press time. The card's long-press has run
 *   then, and the click that follows the release must do nothing: [click] swallows it.
 */
private class CardPress {
    var held = false

    /** Runs [action] for a click, unless the click ends a long-press. */
    fun click(action: () -> Unit) {
        if (held) held = false else action()
    }
}

/**
 * Watches the presses of the button that uses [source]. A press held for the long-press time opens
 * what the card's long-press opens ([LocalCardLongPress]), as a long-press on a list's row does.
 * Nothing runs until a finger is on the button, and the wait ends when the finger lifts or leaves.
 */
@Composable
private fun cardPress(source: InteractionSource): CardPress {
    val press = remember { CardPress() }
    val longPress = LocalCardLongPress.current
    if (longPress != null) {
        val onLongPress by rememberUpdatedState(longPress)
        val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(source, timeout) {
            // collectLatest: a release or a cancel arrives as the next interaction, and ends the wait.
            source.interactions.collectLatest { interaction ->
                if (interaction is PressInteraction.Press) {
                    press.held = false
                    delay(timeout)
                    press.held = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            }
        }
    }
    return press
}

/** What a screen reader says a tap does ("double-tap to …"), from the action's `label`. The button keeps its own click. */
private fun Modifier.tapLabel(label: String?): Modifier = if (label == null) this else semantics { onClick(label = label, action = null) }

/**
 * Lays [count] buttons or switches out as a grid ([WidgetWords.grid]): fewer of them in a small
 * box, never smaller ones. [cell] draws item `index` to fill a cell `width` by `height`.
 */
@Composable
private fun ControlGrid(count: Int, maxColumns: Int, cell: @Composable (index: Int, width: Dp, height: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = WidgetWords.grid(count, maxWidth.value.toInt(), maxHeight.value.toInt(), maxColumns)
        if (grid.shown == 0) return@BoxWithConstraints
        val gap = WidgetWords.CONTROL_GAP_DP.dp
        val width = (maxWidth - gap * (grid.columns - 1)) / grid.columns
        val height = ((maxHeight - gap * (grid.rows - 1)) / grid.rows).coerceIn(WidgetWords.CONTROL_DP.dp, MAX_CONTROL)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)) {
            for (row in 0 until grid.rows) {
                Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until grid.columns) {
                        val index = row * grid.columns + column
                        // An empty cell keeps the last row's buttons as wide as the others.
                        Box(Modifier.weight(1f).fillMaxHeight()) { if (index < grid.shown) key(index) { cell(index, width, height) } }
                    }
                }
            }
        }
    }
}

/**
 * The inside of a button or a switch, by the room its cell has:
 * - No icon: the label, on two lines in a tall cell.
 * - A wide cell: the icon and the label side by side.
 * - A tall cell: the icon over the label.
 * - Any other cell: the icon alone. A screen reader still hears the label.
 * The label gets smaller, down to 10 sp, before it is cut with "…".
 */
@Composable
private fun ControlContent(icon: String?, label: String, width: Dp, height: Dp) {
    val type = MaterialTheme.typography
    when {
        icon == null -> ControlLabel(label, lines = if (height >= TWO_LINES) 2 else 1)
        width >= SIDE_BY_SIDE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = type.titleMedium, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            ControlLabel(label, lines = 1, Modifier.weight(1f, fill = false))
        }
        height >= TWO_LINES -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = type.titleMedium, maxLines = 1, softWrap = false)
            ControlLabel(label, lines = 1)
        }
        else -> Text(icon, style = type.titleMedium, maxLines = 1, softWrap = false, modifier = Modifier.semantics { contentDescription = label })
    }
}

private val LABEL_SIZE = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 1.sp)

@Composable
private fun ControlLabel(label: String, lines: Int, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        autoSize = LABEL_SIZE,
        maxLines = lines,
        softWrap = lines > 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/** Less padding than a Material button has: a cell can be as narrow as 48 dp. */
private val CONTROL_PADDING = PaddingValues(horizontal = 6.dp, vertical = 2.dp)

// ---- buttons --------------------------------------------------------------------------------

/**
 * `buttons`: a grid of buttons, each with a label and an icon.
 * - 2 fit in a card of 2×1, 4 in 2×2 and in 4×1, 8 in 4×2. A button is never under 48 dp, so a
 *   smaller card shows the first ones only.
 * - A button's own colour wins over the card's. With neither, it has the theme's tonal colours.
 * - A tap hands the button's action to [onTap]. A long-press is the card's long-press.
 */
@Composable
internal fun ButtonsBody(spec: WidgetSpec.Buttons, onTap: ((WidgetTap) -> Unit)?) {
    ControlGrid(spec.items.size, maxColumns = 4) { index, width, height ->
        val button = spec.items[index]
        CardButton(button, (button.color ?: spec.base.color)?.let { colourOf(it) }, width, height, onTap)
    }
}

/**
 * One button. With a null [onTap] it takes no tap, and looks as it does on the page: the Widgets
 * screen shows the card as it is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardButton(button: WidgetSpec.Button, colour: Color?, width: Dp, height: Dp, onTap: ((WidgetTap) -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val container = colour?.copy(alpha = 0.2f) ?: tonal.containerColor
    val content = colour ?: tonal.contentColor
    FilledTonalButton(
        onClick = { press.click { onTap?.invoke(button.tap) } },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxSize().tapLabel(button.tap.label),
        enabled = onTap != null,
        colors = tonal.copy(containerColor = container, contentColor = content, disabledContainerColor = container, disabledContentColor = content),
        contentPadding = CONTROL_PADDING,
        interactionSource = source,
    ) {
        ControlContent(button.icon, button.label, width, height)
    }
}

// ---- stubs ----------------------------------------------------------------------------------

/** `toggles`: switches. [stamp] changes when the script's run ends. Not drawn yet. */
@Composable
internal fun TogglesBody(spec: WidgetSpec.Toggles, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?, stamp: Long) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}

/** `clock`: the time in several places. Not drawn yet. */
@Composable
internal fun ClockBody(spec: WidgetSpec.Clock, box: WidgetSizes.Box) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}

/** `agenda`: events by day. Not drawn yet. */
@Composable
internal fun AgendaBody(spec: WidgetSpec.Agenda, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}

/** `media`: what is playing, with its controls. Not drawn yet. */
@Composable
internal fun MediaBody(spec: WidgetSpec.Media, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}
