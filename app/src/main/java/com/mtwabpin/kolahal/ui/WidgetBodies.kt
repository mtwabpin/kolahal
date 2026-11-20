package com.mtwabpin.kolahal.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The content of the kinds with parts of their own to tap or to tick: buttons, toggles, clock,
 * agenda and media. [KindBody] calls them. The first six kinds are in WidgetKinds.kt.
 *
 * Rules for every body here:
 * - With a null `onTap`, nothing is clickable (the Widgets screen).
 * - Time comes from the header's clock ([rememberNow]). A tick every second runs only while the
 *   widgets page is in view ([LocalWidgetsShown]) and the body shows seconds.
 * - Nothing moves for ever, and nothing but drawing runs on the main thread.
 * - Each body draws the card's look ([LocalWidgetLook]). Plain is the card as it was. Tonal puts
 *   the parts on the look's tile; Bold, Split and Shape have layouts of their own, and the card's
 *   frame leaves its title to them ([LocalUntitled]). A shape that morphs runs only while it does,
 *   and only its drawing runs again.
 */

// ---- shared by buttons and toggles ----------------------------------------------------------

/** A button or a switch grows with its cell up to this height. A taller cell leaves room around it. */
private val MAX_CONTROL = 64.dp

/** The widest a button or a switch gets: one switch on a 4×1 card sits in the middle instead of stretching across it. */
private val MAX_CONTROL_WIDTH = 200.dp

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
                when (interaction) {
                    is PressInteraction.Press -> {
                        press.held = false
                        delay(timeout)
                        press.held = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                    // The press ended with no click (the finger left, or another screen opened): there is no click to swallow.
                    is PressInteraction.Cancel -> press.held = false
                    else -> {}
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
 * - The grid sits in the middle of the box. A cell is at most [MAX_CONTROL_WIDTH] wide, so a single item
 *   on a wide card doesn't stretch across it.
 * - A box lower than one row (a large font makes the card's title line taller) does not press the
 *   row under 48 dp. The row keeps its height and reaches down into the card's padding.
 * - With nothing to lay out it says "Nothing to show": a built-in card can have no buttons yet.
 */
@Composable
private fun ControlGrid(count: Int, maxColumns: Int, cell: @Composable (index: Int, width: Dp, height: Dp) -> Unit) {
    if (count <= 0) {
        QuietLine(WidgetWords.NOTHING)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val grid = WidgetWords.grid(count, maxWidth.value.toInt(), maxHeight.value.toInt(), maxColumns)
        val gap = WidgetWords.CONTROL_GAP_DP.dp
        val width = ((maxWidth - gap * (grid.columns - 1)) / grid.columns).coerceAtMost(MAX_CONTROL_WIDTH)
        val rowWidth = width * grid.columns + gap * (grid.columns - 1)
        val height = ((maxHeight - gap * (grid.rows - 1)) / grid.rows).coerceIn(WidgetWords.CONTROL_DP.dp, MAX_CONTROL)
        val all = height * grid.rows + gap * (grid.rows - 1)
        // Measured with no limit on its height, and kept at the top when it is taller than the box.
        Column(Modifier.width(rowWidth).wrapContentHeight(Alignment.Top, unbounded = true).height(all), verticalArrangement = Arrangement.spacedBy(gap)) {
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
 *
 * A switch's [state] ("On" or "Off") is under its label in a tall cell, when the label is alone or
 * beside the icon. A screen reader hears the state from the switch itself, so not from these words.
 *
 * With [working], a sign that something is going on takes the icon's place ([WorkingSign]). A
 * switch shows it while it waits for its script.
 *
 * The icon is in [iconColour] where a look gives it one (Tonal and Split: the accent), else in the text's colour.
 */
@Composable
private fun ControlContent(icon: String?, label: String, width: Dp, height: Dp, state: String? = null, working: Boolean = false, iconColour: Color? = null) {
    val lead: (@Composable () -> Unit)? = when {
        working -> { { WorkingSign() } }
        icon != null -> { { WidgetIconText(icon, MaterialTheme.typography.titleMedium, colour = iconColour ?: LocalContentColor.current) } }
        else -> null
    }
    val tall = height >= TWO_LINES
    val shownState = state.takeIf { tall }
    when {
        lead == null && shownState != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ControlLabel(label, lines = 1)
            StateWord(shownState)
        }
        lead == null -> ControlLabel(label, lines = if (tall) 2 else 1)
        width >= SIDE_BY_SIDE -> Row(verticalAlignment = Alignment.CenterVertically) {
            lead()
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (shownState != null) Alignment.Start else Alignment.CenterHorizontally) {
                ControlLabel(label, lines = 1, textAlign = if (shownState != null) TextAlign.Start else TextAlign.Center)
                if (shownState != null) StateWord(shownState)
            }
        }
        tall -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            lead()
            if (iconColour != null) Spacer(Modifier.height(4.dp))
            ControlLabel(label, lines = 1)
        }
        else -> Box(Modifier.semantics { contentDescription = label }) { lead() }
    }
}

/** A switch's state under its label: "On" or "Off", small and a little dim. */
@Composable
private fun StateWord(state: String) {
    Text(
        state,
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.8f),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/**
 * The sign of a switch that waits for its script: the loading shape, as tall as an icon.
 * - The shape changes form for as long as it is drawn. So it is drawn only while the widgets page
 *   is in view, and a switch waits for 10 seconds at most.
 * - It is no item for a screen reader: the switch says "working" itself.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkingSign() {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (cardSeen()) LoadingIndicator(Modifier.fillMaxSize().clearAndSetSemantics {}, color = LocalContentColor.current)
        else Text("…", style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

private val LABEL_SIZE = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 1.sp)

@Composable
private fun ControlLabel(label: String, lines: Int, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Center) {
    Text(
        label,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        autoSize = LABEL_SIZE,
        maxLines = lines,
        softWrap = lines > 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

/** How much of the text colour an off switch has as its fill: a little lighter than the card. */
private const val OFF_TINT = 0.10f

/** Less padding than a Material button has: a cell can be as narrow as 48 dp. */
private val CONTROL_PADDING = PaddingValues(horizontal = 6.dp, vertical = 2.dp)

// ---- shared by the looks --------------------------------------------------------------------

/** The least size of a button or a switch. */
private val CONTROL_MIN = WidgetWords.CONTROL_DP.dp

/** The size of a Shape button's shape where its cell has room: 56 dp, as the mockup has it. */
private val SHAPE_CONTROL = 56.dp

/** The size of a Shape button's shape with its label inside: room for a word. */
private val SHAPE_TEXT_CONTROL = 80.dp

/** The outline of a whole Shape button or switch, label and all: what a press's ripple fills. */
private val SHAPE_ITEM = RoundedCornerShape(16.dp)

/** A Split tile while pressed: its outside corners go in to the inside ones. */
private val SPLIT_PRESSED = RoundedCornerShape(SplitCorners.INNER)

/**
 * The shapes Shape's buttons take, one after the other, as the mockup draws a card of eight:
 * a cookie, a clover, a sunny, a wavy circle, a four-sided cookie, a circle, a burst and a square.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val BUTTON_SHAPES: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie12Sided,
        MaterialShapes.Cookie4Sided, MaterialShapes.Circle, MaterialShapes.SoftBurst, MaterialShapes.Square,
    )
}

/** A button's shape into the one it takes while pressed: a circle, or a square for a circle. Made once for each shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun pressMorph(shape: RoundedPolygon): Morph = PRESS_MORPHS.getOrPut(shape) {
    Morph(shape, if (shape == MaterialShapes.Circle) MaterialShapes.Square else MaterialShapes.Circle)
}

private val PRESS_MORPHS = HashMap<RoundedPolygon, Morph>()

/** A Shape switch: a circle while off into a cookie while on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val TOGGLE_MORPH: Morph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }

/** Shape's play or pause: a cookie while playing into a rounded square while paused. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val PLAY_MORPH: Morph by lazy { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Square) }

/**
 * Fills the space behind with [morph] at [progress] (0 its first shape, 1 its second), centred and
 * stretched to the size, as `toShape` draws a Material shape. The colour goes from [from] to [to]
 * with it. The progress is read while drawing: a morph that runs draws again and composes nothing,
 * and the path is made again only when the progress or the size has changed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Modifier.morphFill(morph: Morph, from: Color, to: Color = from, progress: () -> Float): Modifier = drawWithCache {
    val path = Path()
    val stretch = Matrix().apply { scale(size.width, size.height) }
    var made = Float.NaN
    onDrawBehind {
        val p = progress()
        if (p != made) {
            morph.toPath(p, path)
            path.transform(stretch)
            path.translate(size.center - path.getBounds().center)
            made = p
        }
        drawPath(path, if (from == to) from else lerp(from, to, p.coerceIn(0f, 1f)))
    }
}

/**
 * The grid of Split and Shape: as [WidgetWords.grid], but three or more in a box tall enough for
 * two rows go in two rows. A 2×2 card then has tiles of a sensible shape, not three tall thin ones.
 */
private fun lookGrid(count: Int, widthDp: Int, heightDp: Int, maxColumns: Int): WidgetWords.Grid {
    val twoRows = count >= 3 && heightDp >= 2 * WidgetWords.CONTROL_DP + WidgetWords.CONTROL_GAP_DP
    return WidgetWords.grid(count, widthDp, heightDp, if (twoRows) minOf(maxColumns, (count + 1) / 2) else maxColumns)
}

/**
 * Split's grid of buttons or switches:[count] tiles that fill the box, 4 dp apart, with 28 dp
 * corners where they touch the card's edge and 8 dp inside ([SplitCorners.inGrid]). The rows are
 * as [WidgetWords.grid] lays them out; a short last row has wider tiles, so the grid stays a
 * rectangle. [cell] draws item `index` in its `tile` shape, `width` by `height`.
 */
@Composable
private fun SplitGrid(count: Int, maxColumns: Int, cell: @Composable (index: Int, tile: Shape, width: Dp, height: Dp) -> Unit) {
    if (count <= 0) {
        QuietLine(WidgetWords.NOTHING)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = lookGrid(count, maxWidth.value.toInt(), maxHeight.value.toInt(), maxColumns)
        val gap = SplitCorners.GAP
        val boxWidth = maxWidth
        val height = ((maxHeight - gap * (grid.rows - 1)) / grid.rows).coerceAtLeast(CONTROL_MIN)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until grid.rows) {
                val first = row * grid.columns
                val inRow = minOf(grid.columns, grid.shown - first)
                val width = (boxWidth - gap * (inRow - 1)) / inRow
                Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until inRow) {
                        val index = first + column
                        Box(Modifier.weight(1f).fillMaxHeight()) { key(index) { cell(index, SplitCorners.inGrid(index, grid.shown, grid.columns), width, height) } }
                    }
                }
            }
        }
    }
}

/**
 * Shape's grid of buttons or switches: as [ControlGrid] lays them out, but a cell may be as tall as
 * its share of the box, for a shape with a label under it, and each item sits in the middle of its cell.
 */
@Composable
private fun ShapeGrid(count: Int, maxColumns: Int, cell: @Composable (index: Int, width: Dp, height: Dp) -> Unit) {
    if (count <= 0) {
        QuietLine(WidgetWords.NOTHING)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val grid = lookGrid(count, maxWidth.value.toInt(), maxHeight.value.toInt(), maxColumns)
        val gap = WidgetWords.CONTROL_GAP_DP.dp
        val width = (maxWidth - gap * (grid.columns - 1)) / grid.columns
        val height = ((maxHeight - gap * (grid.rows - 1)) / grid.rows).coerceIn(CONTROL_MIN, SHAPE_CONTROL + LABEL_UNDER + 8.dp)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until grid.rows) {
                Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until grid.columns) {
                        val index = row * grid.columns + column
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { if (index < grid.shown) key(index) { cell(index, width, height) } }
                    }
                }
            }
        }
    }
}

// ---- buttons --------------------------------------------------------------------------------

/**
 * `buttons`: a grid of buttons, each with a label and an icon.
 * - 2 fit in a card of 2×1, 4 in 2×2 and in 4×1, 8 in 4×2. A button is never under 48 dp, so a
 *   smaller card shows the first ones only.
 * - A button's own colour wins over the card's. With neither, it is tinted with the accent.
 * - A tap hands the button's action to [onTap]. A long-press is the card's long-press.
 * - Tonal: the buttons are a light tint of the text colour on the card's colour, the icon in the accent.
 * - Split: the buttons are the card's tiles ([SplitGrid]).
 * - Shape: each button is a Material shape with its label under it ([ShapeButton]).
 */
@Composable
internal fun ButtonsBody(spec: WidgetSpec.Buttons, onTap: ((WidgetTap) -> Unit)?) {
    when (LocalWidgetLook.current) {
        WidgetLook.SPLIT -> SplitGrid(spec.items.size, maxColumns = 4) { index, tile, width, height ->
            val button = spec.items[index]
            CardButton(button, (button.color ?: spec.base.color)?.let { colourOf(it) }, width, height, onTap, tile)
        }
        WidgetLook.SHAPE -> ShapeGrid(spec.items.size, maxColumns = 4) { index, width, height ->
            val button = spec.items[index]
            // With no colour of their own, the shapes take the theme's three colours in turn, as the mockup has them.
            val role = (button.color ?: spec.base.color)?.let { colourOf(it) } ?: shapeRole(index)
            ShapeButton(button, role, index, width, height, onTap)
        }
        else -> ControlGrid(spec.items.size, maxColumns = 4) { index, width, height ->
            val button = spec.items[index]
            CardButton(button, (button.color ?: spec.base.color)?.let { colourOf(it) }, width, height, onTap)
        }
    }
}

/** A Tonal button's corners: squarer than Plain's pill, as the mockup draws them. */
private val TONAL_BUTTON = RoundedCornerShape(20.dp)

/**
 * One button. With a null [onTap] it takes no tap, and looks as it does on the page: the Widgets
 * screen shows the card as it is. With a [tile] shape it is one of Split's tiles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardButton(button: WidgetSpec.Button, colour: Color?, width: Dp, height: Dp, onTap: ((WidgetTap) -> Unit)?, tile: Shape? = null) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val look = LocalWidgetLook.current
    val looks = lookColors(look)
    val plain = tile == null && look != WidgetLook.TONAL
    // With no colour of its own, a button is tinted with the accent, and its text keeps the text colour.
    // Tonal and Split put it on the look's tile, with the icon in the button's colour or the accent.
    val container = if (plain) (colour ?: roleColour(null)).copy(alpha = 0.2f) else looks.tile
    val content = if (plain) colour ?: MaterialTheme.colorScheme.onSurface else looks.content
    val shapes = when {
        tile != null -> ButtonDefaults.shapes(shape = tile, pressedShape = SPLIT_PRESSED)
        look == WidgetLook.TONAL -> ButtonDefaults.shapes(shape = TONAL_BUTTON)
        else -> ButtonDefaults.shapes()
    }
    FilledTonalButton(
        onClick = { press.click { onTap?.invoke(button.tap) } },
        shapes = shapes,
        modifier = Modifier.fillMaxSize().tapLabel(button.tap.label),
        enabled = onTap != null,
        colors = tonal.copy(containerColor = container, contentColor = content, disabledContainerColor = container, disabledContentColor = content),
        contentPadding = CONTROL_PADDING,
        interactionSource = source,
    ) {
        ControlContent(button.icon, button.label, width, height, iconColour = if (plain) null else colour ?: roleColour(null))
    }
}

/** The theme's colours that Shape's buttons take in turn: primary, tertiary, secondary. */
@Composable
private fun shapeRole(index: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> scheme.primary
        1 -> scheme.tertiary
        else -> scheme.secondary
    }
}

/** The room a label takes under a Shape button. */
private val LABEL_UNDER = 18.dp

/**
 * One button of the Shape look: a Material shape ([BUTTON_SHAPES], one after the other) in its
 * [role]'s container, with the icon in the role's colour and the label under it where the cell has
 * room. Without an icon the label is inside the shape.
 * - The whole of it, label too, is one Material button, so a tap on the label is a tap on the button.
 * - A press morphs the shape into another one, and back on release. Only the drawing runs again.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeButton(button: WidgetSpec.Button, role: Color, index: Int, width: Dp, height: Dp, onTap: ((WidgetTap) -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val pressed by source.collectIsPressedAsState()
    val morphed = animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    val morph = remember(index) { pressMorph(BUTTON_SHAPES[index % BUTTON_SHAPES.size]) }
    val fill = roleContainer(role)
    val labelled = button.icon != null && height >= CONTROL_MIN + LABEL_UNDER
    // A label inside the shape needs a bigger one: as big as the cell, up to 80 dp.
    val most = if (button.icon == null) SHAPE_TEXT_CONTROL else SHAPE_CONTROL
    val side = minOf(most, width, if (labelled) height - LABEL_UNDER else height).coerceAtLeast(CONTROL_MIN)
    val colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = role, disabledContainerColor = Color.Transparent, disabledContentColor = role)
    Button(
        onClick = { press.click { onTap?.invoke(button.tap) } },
        shapes = ButtonDefaults.shapes(shape = SHAPE_ITEM),
        modifier = Modifier.fillMaxSize().tapLabel(button.tap.label),
        enabled = onTap != null,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
        interactionSource = source,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(side).morphFill(morph, fill) { morphed.value }, contentAlignment = Alignment.Center) {
                val icon = button.icon
                if (icon != null) {
                    // Without its label under it, the shape says the label to a screen reader.
                    val said = if (labelled) Modifier else Modifier.semantics { contentDescription = button.label }
                    WidgetIconText(icon, MaterialTheme.typography.titleMedium, colour = role, size = 22.dp, modifier = said)
                } else {
                    ControlLabel(button.label, lines = 2, modifier = Modifier.padding(horizontal = side / 6))
                }
            }
            if (labelled) {
                Text(
                    button.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(LABEL_UNDER).wrapContentHeight(),
                )
            }
        }
    }
}

// ---- toggles --------------------------------------------------------------------------------

/** The switch that waits for its script: which one, and the card's stamp when it was tapped. */
private data class Waiting(val index: Int, val stamp: Long)

/**
 * `toggles`: switches, each with a label, an icon and a state. Kolahal never guesses the new
 * state: the script changes the thing and reports it.
 * 1. A tap hands the switch's action to [onTap], and the switch shows "working". Its state stays
 *    as the script last reported it.
 * 2. The script runs and shows the new state. The card's [stamp] moves when the run ends, whether
 *    it showed something or failed, and the switch stops waiting. [stamp] is watched and not the
 *    description, because a run that reports the same state stores an equal description.
 * 3. With no answer in 10 seconds ([WidgetWords.WORKING_MS]) the switch stops waiting too. It
 *    shows the state it had, and a failed run is in the card's footer.
 *
 * - A switch that waits takes no second tap. And a tap that comes within 2 seconds of the last one
 *   with the same arguments does nothing: the view model starts no run for it and does not say so,
 *   so "working" would wait for a run that never comes.
 * - Up to six in a row and two rows, each at least 48 dp: a small card shows the first ones only.
 */
@Composable
internal fun TogglesBody(spec: WidgetSpec.Toggles, onTap: ((WidgetTap) -> Unit)?, stamp: Long) {
    var waiting by remember { mutableStateOf<Waiting?>(null) }
    val lastTaps = remember { HashMap<String, Long>() }
    // The run ended: nothing waits any more.
    LaunchedEffect(stamp) { waiting = null }
    LaunchedEffect(waiting) {
        if (waiting != null) {
            delay(WidgetWords.WORKING_MS)
            waiting = null
        }
    }
    val working = waiting?.takeIf { it.stamp == stamp }?.index
    fun clickOf(index: Int): (() -> Unit)? = onTap?.let { tap ->
        {
            val toggle = spec.items[index]
            val args = toggle.tap.args.orEmpty()
            val at = SystemClock.elapsedRealtime()
            if (working != index && !WidgetWords.tooSoon(lastTaps[args], at)) {
                lastTaps[args] = at
                waiting = Waiting(index, stamp)
                tap(toggle.tap)
            }
        }
    }
    val role = roleColour(spec.base)
    when (LocalWidgetLook.current) {
        WidgetLook.SPLIT -> SplitGrid(spec.items.size, maxColumns = 6) { index, tile, width, height ->
            CardToggle(spec.items[index], working == index, width, height, clickOf(index), role, tile)
        }
        WidgetLook.SHAPE -> ShapeGrid(spec.items.size, maxColumns = 6) { index, width, height ->
            ShapeToggle(spec.items[index], working == index, width, height, clickOf(index), role)
        }
        else -> ControlGrid(spec.items.size, maxColumns = 6) { index, width, height ->
            CardToggle(spec.items[index], working == index, width, height, clickOf(index), role)
        }
    }
}

/**
 * One switch: a Material toggle button, filled while on. Off, it is a light tint of the text colour.
 * A tall one says "On" or "Off" under its label ([ControlContent]).
 * - With [working] the loading shape takes its icon's place, and a screen reader hears "working".
 * - With a null [onClick] it takes no tap, and looks as it does on the page.
 * - A long-press is the card's long-press.
 * - Tonal: off is the look's tile on the card's colour, and on is the card's [cardRole] colour.
 * - With a [tile] shape it is one of Split's tiles: on in the [cardRole] colour, and it says "On" or "Off".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardToggle(toggle: WidgetSpec.Toggle, working: Boolean, width: Dp, height: Dp, onClick: (() -> Unit)?, cardRole: Color, tile: Shape? = null) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val look = LocalWidgetLook.current
    val looks = lookColors(look, cardRole)
    val plain = tile == null && look != WidgetLook.TONAL
    val off = MaterialTheme.colorScheme.onSurface
    val colors = ToggleButtonDefaults.colors().let {
        // Off is a light tint of the text colour: Material's own is darker than the card.
        val offContainer = if (plain) off.copy(alpha = OFF_TINT) else looks.tile
        val onContainer = if (plain) it.checkedContainerColor else looks.accent
        val onContent = if (plain) it.checkedContentColor else looks.onAccent
        it.copy(
            containerColor = offContainer,
            contentColor = off,
            checkedContainerColor = onContainer,
            checkedContentColor = onContent,
            // A switch that takes no tap keeps the colours of its state.
            disabledContainerColor = if (toggle.on) onContainer else offContainer,
            disabledContentColor = if (toggle.on) onContent else off,
        )
    }
    val state = if (toggle.on) "On" else "Off"
    val semantics = Modifier.fillMaxSize().tapLabel(toggle.tap.label).semantics {
        role = Role.Switch
        if (working) stateDescription = "working"
    }
    if (tile != null) {
        ToggleButton(
            checked = toggle.on,
            onCheckedChange = { press.click { onClick?.invoke() } },
            modifier = semantics,
            enabled = onClick != null,
            shapes = ToggleButtonShapes(tile, SPLIT_PRESSED, tile),
            colors = colors,
            contentPadding = PaddingValues(horizontal = 12.dp),
            interactionSource = source,
        ) {
            SplitToggleContent(toggle, state, width, height, working, looks)
        }
        return
    }
    ToggleButton(
        checked = toggle.on,
        onCheckedChange = { press.click { onClick?.invoke() } },
        modifier = semantics,
        enabled = onClick != null,
        colors = colors,
        contentPadding = CONTROL_PADDING,
        interactionSource = source,
    ) {
        ControlContent(toggle.icon, toggle.label, width, height, state = state, working = working)
    }
}

/**
 * The inside of one of Split's switches, at the start of its tile: the icon, the label in bold and
 * "On" or "Off" under it, as the mockup has them. A low tile leaves the icon out, a narrow one
 * ([SIDE_BY_SIDE]) is drawn as the other looks draw a switch ([ControlContent]).
 */
@Composable
private fun RowScope.SplitToggleContent(toggle: WidgetSpec.Toggle, state: String, width: Dp, height: Dp, working: Boolean, looks: LookColors) {
    if (width < SIDE_BY_SIDE && height < SPLIT_TOGGLE_TALL) {
        ControlContent(toggle.icon, toggle.label, width, height, state = state, working = working)
        return
    }
    val type = MaterialTheme.typography
    Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.Center) {
        if (height >= SPLIT_TOGGLE_TALL) {
            when {
                working -> WorkingSign()
                toggle.icon != null -> WidgetIconText(toggle.icon, type.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(toggle.label, style = type.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            state,
            style = type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (toggle.on) LocalContentColor.current else looks.muted,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** From this height a Split switch has its icon over its label. */
private val SPLIT_TOGGLE_TALL = 76.dp

/**
 * One switch of the Shape look: a 48 dp shape with the icon, its label and "On" or "Off" beside it
 * where the cell is wide enough, or its label under it where the cell is tall enough.
 * - On, the shape is a cookie in the card's [cardRole] colour; off, a circle a little lighter than the
 *   card. A change of state morphs one into the other. Only the drawing runs again while it does.
 * - The whole of it is one Material toggle button, so a tap on the label is a tap on the switch.
 * - Without an icon, the shape has the label's first letter.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeToggle(toggle: WidgetSpec.Toggle, working: Boolean, width: Dp, height: Dp, onClick: (() -> Unit)?, cardRole: Color) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val scheme = MaterialTheme.colorScheme
    val looks = lookColors(WidgetLook.SHAPE, cardRole)
    val on = animateFloatAsState(if (toggle.on) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "toggle")
    val content = scheme.onSurface
    val clear = ToggleButtonDefaults.colors().copy(
        containerColor = Color.Transparent,
        contentColor = content,
        checkedContainerColor = Color.Transparent,
        checkedContentColor = content,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = content,
    )
    val beside = width >= CONTROL_MIN + SHAPE_LABEL_MIN
    val under = !beside && height >= CONTROL_MIN + LABEL_UNDER
    val label = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    ToggleButton(
        checked = toggle.on,
        onCheckedChange = { press.click { onClick?.invoke() } },
        modifier = Modifier.tapLabel(toggle.tap.label).semantics {
            role = Role.Switch
            if (working) stateDescription = "working"
            // With no label to read, the shape says it.
            if (!beside && !under) contentDescription = toggle.label
        },
        enabled = onClick != null,
        shapes = ToggleButtonShapes(SHAPE_ITEM, SHAPE_ITEM, SHAPE_ITEM),
        colors = clear,
        // Room inside the ripple's round corners, so they cut no letter.
        contentPadding = if (under) PaddingValues(horizontal = 6.dp) else PaddingValues(end = if (beside) 4.dp else 0.dp),
        interactionSource = source,
    ) {
        val tint = if (toggle.on) looks.onAccent else content
        val shape: @Composable () -> Unit = {
            Box(Modifier.size(CONTROL_MIN).morphFill(TOGGLE_MORPH, scheme.surfaceContainerHighest, looks.accent) { on.value }, contentAlignment = Alignment.Center) {
                val icon = toggle.icon
                when {
                    working -> CompositionLocalProvider(LocalContentColor provides tint) { WorkingSign() }
                    icon != null -> WidgetIconText(icon, MaterialTheme.typography.titleMedium, colour = tint)
                    else -> Text(WidgetWords.badgeLetter(toggle.label).orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = tint, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
        if (under) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                shape()
                Text(toggle.label, style = label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(LABEL_UNDER).widthIn(max = width - 12.dp).wrapContentHeight())
            }
            return@ToggleButton
        }
        shape()
        if (beside) {
            // A long label takes two lines beside the shape: "Do not" over "disturb".
            Column(Modifier.padding(start = 8.dp).widthIn(max = width - CONTROL_MIN - 12.dp)) {
                Text(toggle.label, style = label.copy(lineHeight = 15.sp), color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (toggle.on) "On" else "Off",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (toggle.on) FontWeight.Bold else FontWeight.Normal),
                    color = if (toggle.on) looks.accent else looks.muted,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** The least room for a Shape switch's label beside its shape. */
private val SHAPE_LABEL_MIN = 48.dp

// ---- clock ----------------------------------------------------------------------------------

/** The room under a clock's time for its place, in dp. */
private const val PLACE_DP = 16

/**
 * From this width a large clock has a strip of the day for each place ([ClockStrips]), in dp: a
 * 4×2 card on a phone 360 dp wide or more.
 */
private const val STRIPS_FROM_DP = 300

/**
 * `clock`: the time in up to four places. Kolahal does the ticking: the script gave the places
 * once, and no run is needed as time passes.
 * - Under or beside each place, its difference from the phone's time: "5 h 30 min behind",
 *   "Same time", "Here", with its day when that is not the day here. Each place gets the longest
 *   form that fits whole, down to "−5:30" ([WidgetWords.placeLines]).
 * - Small, or one place: that place's time, large, over its name.
 * - Wide: the places side by side, as many as fit ([WidgetWords.clockColumns]).
 * - Large: one row for each place, its name at the start and its time at the end. A row with room
 *   for two lines has the difference under the name ([PlaceAndDifference]).
 * - Large and wide (a 4×2 card), in Plain and Tonal: the rows also have a strip of the day
 *   ([ClockStrips]). [onTap] null takes no tap on the strips (the Widgets screen).
 * - The time follows Kolahal's 24-hour setting. It moves by the minute, with the header's clock.
 *   With `seconds` it moves by the second, but only while the widgets page is in view.
 * - The size of the digits comes from the room, never from the text: a clock that ticks does not
 *   change its size, and its digits are never measured. A place's words are, to pick the form
 *   that fits, and only again when they or their room change.
 */
@Composable
internal fun ClockBody(spec: WidgetSpec.Clock, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)? = null) {
    val (_, zoneChanges) = rememberNow()
    val here = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val format = remember(clock24, spec.seconds) { DateTimeFormatter.ofPattern(WidgetWords.clockPattern(clock24, spec.seconds), Locale.getDefault()) }
    val now = rememberMillis(seconds = spec.seconds)
    val places = remember(spec.zones) { WidgetWords.places(spec.zones) }
    // The times change with each tick; the places and their days rarely do. The card is laid out
    // from the places, and only the times are drawn again at a tick ([ClockTime]).
    val ticking = remember(places, here, format) { derivedStateOf { WidgetWords.zoneTimesOf(places, now.value, here, format) } }
    val times by remember(ticking) { derivedStateOf { ticking.value.map { it.copy(time = "") } } }
    fun time(i: Int): () -> String = { ticking.value.getOrNull(i)?.time.orEmpty() }
    if (times.isEmpty()) {
        QuietLine(WidgetWords.NOTHING, small = box == WidgetSizes.Box.SMALL)
        return
    }
    val chars = WidgetWords.clockChars(clock24, spec.seconds)
    val zone = places.firstOrNull()?.second ?: here
    when (LocalWidgetLook.current) {
        WidgetLook.BOLD -> return BoldClock(times, ::time, zone, now, box, chars, roleColour(spec.base))
        WidgetLook.SPLIT -> return SplitClock(times, ::time, box, chars)
        WidgetLook.SHAPE -> return ShapeClock(times, ::time, zone, now, box, roleColour(spec.base))
        else -> {}
    }
    val tile = lookColors().tile
    val type = MaterialTheme.typography
    val nameStyle = type.titleMedium.copy(fontWeight = FontWeight.Bold)
    // A row this tall has the place's name over its difference, at the phone's font size.
    val twoLines = linesDp(nameStyle, type.bodySmall)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        // The digits are as tall as the box allows over the place's name: a line is about 1.25 of its font size.
        val fits = (height - PLACE_DP) * 4 / 5
        when {
            box == WidgetSizes.Box.SMALL || times.size == 1 -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = if (box == WidgetSizes.Box.SMALL) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                ClockTime(time(0), WidgetWords.clockTextDp(width, chars, minOf(fits, 40)))
                PlaceLabel(times[0])
            }
            box == WidgetSizes.Box.WIDE -> {
                val columns = WidgetWords.clockColumns(times.size, width, chars)
                val gap = WidgetWords.CONTROL_GAP_DP
                val cell = (width - gap * (columns - 1)) / columns
                val size = WidgetWords.clockTextDp(cell, chars, minOf(fits, 28))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap.dp), verticalAlignment = Alignment.CenterVertically) {
                    times.take(columns).forEachIndexed { i, time ->
                        key(i) {
                            Column(Modifier.weight(1f)) {
                                ClockTime(time(i), size)
                                PlaceLabel(time)
                            }
                        }
                    }
                }
            }
            width >= STRIPS_FROM_DP -> ClockStrips(places, here, format, now, clock24, chars, roleColour(spec.base), tappable = onTap != null, width, height)
            else -> {
                val gap = ROW_GAP_DP
                val rows = WidgetWords.clockRows(times.size, height, gap)
                val row = ((height + gap) / rows - gap).coerceIn(WidgetWords.CLOCK_ROW_DP, 48)
                // The time gets up to half the width inside the row's padding. The place's name has the rest.
                val size = WidgetWords.clockTextDp((width - 24) / 2, chars, minOf(row * 3 / 5, 24))
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                    times.take(rows).forEachIndexed { i, time ->
                        key(i) {
                            Row(
                                Modifier.fillMaxWidth().height(row.dp).clip(segmentShape(i, rows)).background(tile).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f).padding(end = 8.dp)) {
                                    // A tall row has the difference on a second line, as the mockup has it.
                                    if (row >= twoLines) PlaceAndDifference(time, nameStyle, type.bodySmall)
                                    else PlaceLabel(time, style = type.labelLarge.copy(fontWeight = FontWeight.Bold), colour = MaterialTheme.colorScheme.onSurface)
                                }
                                ClockTime(time(i), size)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The height of a line in each of [styles], one under the other, at the phone's font size: in whole dp. */
@Composable
private fun linesDp(vararg styles: TextStyle): Int {
    val density = LocalDensity.current
    val dp = styles.sumOf { style ->
        val line = if (style.lineHeight.isSp) style.lineHeight else style.fontSize * 1.25f
        with(density) { line.toDp().value.toDouble() }
    }
    return kotlin.math.ceil(dp).toInt()
}

/** The least width of a large clock's day strips, and the room between them and the times, in dp. */
private const val STRIP_MIN_DP = 140
private const val STRIP_GAP_DP = 12

/** The room between a large clock's day strips and the hours under them, in dp. */
private const val MARKS_GAP_DP = 2

/**
 * A large clock wide enough for day strips (a 4×2 card), in Plain and Tonal, as the mockup has it:
 * a row for each place with its name over its difference, its time, and a strip of the day here
 * from 00 to 24, in the parts of the day at the place ([WidgetWords.dayStrip]). The hours are
 * under the strips ([HourMarks]).
 * - One line crosses all the strips at the moment shown ([DayStrips]).
 * - A tap on a strip moves the line to the nearest hour, and every place's time and difference
 *   follow. The title line then has that hour and a "Now" button ([ReportTitleAction]). The
 *   button, a second tap on the line, or leaving the page or home brings back now.
 * - Taps only: a sideways drag turns the page. [tappable] false takes no tap at all.
 * - A row too low for two lines has the difference beside the name, in the form that fits.
 * - A screen reader hears the card's description: each place, its time now and its difference.
 */
@Composable
private fun ClockStrips(
    places: List<Pair<WidgetSpec.Zone, ZoneId>>,
    here: ZoneId,
    format: DateTimeFormatter,
    now: State<Long>,
    clock24: Boolean,
    chars: Int,
    role: Color,
    tappable: Boolean,
    widthDp: Int,
    heightDp: Int,
) {
    // The hour a tap picked, from 0 to 24 of today here, or null for now.
    var picked by remember { mutableStateOf<Int?>(null) }
    // Leaving the page, or home, brings back now.
    val seen = cardSeen()
    LaunchedEffect(seen) { if (!seen) picked = null }
    LifecycleStartEffect(Unit) { onStopOrDispose { picked = null } }
    val today by remember(here, now) { derivedStateOf { Instant.ofEpochMilli(now.value).atZone(here).toLocalDate() } }
    // The moment shown: the hour picked, or now.
    val moment = remember(here, now) { derivedStateOf { picked?.let { WidgetWords.hourMoment(today, it, here) } ?: now.value } }
    val ticking = remember(places, here, format, moment) { derivedStateOf { WidgetWords.zoneTimesOf(places, moment.value, here, format) } }
    val times by remember(ticking) { derivedStateOf { ticking.value.map { it.copy(time = "") } } }
    fun time(i: Int): () -> String = { ticking.value.getOrNull(i)?.time.orEmpty() }
    // The line moves by the minute, also when the clock shows seconds.
    val minute = remember(moment) { derivedStateOf { moment.value / 60_000 } }
    val hour = picked
    val at = remember(hour, today, here, clock24) {
        hour?.let { "At " + Instant.ofEpochMilli(WidgetWords.hourMoment(today, it, here)).atZone(here).format(TimeFormats.clockFormat(clock24)) }
    }
    ReportTitleAction(remember(at) { at?.let { TitleAction("Now", it) { picked = null } } })

    val type = MaterialTheme.typography
    val nameStyle = type.labelMedium.copy(fontWeight = FontWeight.Bold)
    val lineStyle = type.labelSmall
    val markStyle = type.labelSmall
    val gap = ROW_GAP_DP
    val room = heightDp - linesDp(markStyle) - MARKS_GAP_DP
    val least = maxOf(WidgetWords.CLOCK_ROW_DP, linesDp(nameStyle))
    val rows = WidgetWords.clockRows(times.size, room, gap, least)
    val row = ((room + gap) / rows - gap).coerceIn(least, maxOf(least, 48))
    val twoLines = row >= linesDp(nameStyle, lineStyle)
    val stripDp = maxOf(widthDp * 45 / 100, STRIP_MIN_DP)
    // The time gets up to half the room beside the strips. The place's name has the rest.
    val size = WidgetWords.clockTextDp((widthDp - stripDp - STRIP_GAP_DP) * 45 / 100, chars, minOf(row * 3 / 5, 20))
    val zones = remember(places, rows) { places.take(rows).map { it.second } }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(end = STRIP_GAP_DP.dp), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
            times.take(rows).forEachIndexed { i, place ->
                key(i) {
                    Row(Modifier.fillMaxWidth().height(row.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).padding(end = 8.dp)) {
                            if (twoLines) PlaceAndDifference(place, nameStyle, lineStyle)
                            else PlaceLabel(place, style = nameStyle, colour = MaterialTheme.colorScheme.onSurface)
                        }
                        ClockTime(time(i), size)
                    }
                }
            }
        }
        Column(Modifier.width(stripDp.dp)) {
            DayStrips(
                zones, today, here, row, gap, role,
                at = { WidgetWords.stripAt(minute.value * 60_000, today, here) },
                onPick = if (tappable) { { h: Int -> picked = if (h == picked) null else h } } else null,
            )
            HourMarks(clock24, markStyle, Modifier.padding(top = MARKS_GAP_DP.dp))
        }
    }
}

/**
 * A large clock's day strips: one for each of [zones] in a row [rowDp] tall, [gapDp] apart, and one
 * line across them all at [at], from 0 (00:00) to 1 (24:00).
 * - A strip is [day] here in the parts of the day at its place: night, day and working hours, in
 *   tones of the card's [role] colour ([stripShades]). It runs from the right in a right-to-left page.
 * - The line is in the text colour with a ring of the card's colour, so it shows on every part.
 *   [at] is read while drawing: the minute's tick draws the line again and composes nothing.
 * - With [onPick], a tap picks the nearest hour ([WidgetWords.hourAt]), and a long-press is the card's.
 * - It is no item for a screen reader: the card's description says what it shows.
 */
@Composable
private fun DayStrips(zones: List<ZoneId>, day: LocalDate, here: ZoneId, rowDp: Int, gapDp: Int, role: Color, at: () -> Float, onPick: ((Int) -> Unit)?) {
    val strips = remember(zones, day, here) { zones.map { WidgetWords.dayStrip(day, here, it) } }
    val scheme = MaterialTheme.colorScheme
    val colours = lookColors()
    val shades = remember(scheme.surface, colours.card, role) { stripShades(scheme.surface, colours.card, role) }
    val outline = scheme.outlineVariant
    val ink = scheme.onSurface
    val ring = colours.card
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val longPress = LocalCardLongPress.current
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    val taps = if (onPick == null) Modifier else Modifier.pointerInput(rtl, longPress) {
        detectTapGestures(
            onLongPress = longPress?.let { press ->
                { _: Offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    press()
                }
            },
            onTap = { tap -> pick?.invoke(WidgetWords.hourAt(tap.x, size.width.toFloat(), rtl)) },
        )
    }
    val height = (rowDp * strips.size + gapDp * (strips.size - 1)).dp
    Spacer(
        Modifier.fillMaxWidth().height(height).testTag("clock-strips").then(taps).drawWithCache {
            val rowPx = rowDp.dp.toPx()
            val step = rowPx + gapDp.dp.toPx()
            val tall = (rowPx * 0.45f).coerceIn(10.dp.toPx(), 16.dp.toPx())
            val corner = CornerRadius(tall / 3)
            val tops = List(strips.size) { it * step + (rowPx - tall) / 2 }
            val outlines = tops.map { top -> Path().apply { addRoundRect(RoundRect(0f, top, size.width, top + tall, corner)) } }
            fun x(along: Float) = size.width * if (rtl) 1f - along else along
            val hair = 1.dp.toPx()
            val line = 2.dp.toPx()
            val from = (tops.firstOrNull() ?: 0f) - 4.dp.toPx()
            val to = (tops.lastOrNull() ?: 0f) + tall + 4.dp.toPx()
            onDrawBehind {
                strips.forEachIndexed { i, stretches ->
                    clipPath(outlines[i]) {
                        for (s in stretches) {
                            val a = x(s.from)
                            val b = x(s.to)
                            // A pixel more on each side, so two parts meet with no seam. The strip's outline clips them.
                            drawRect(shades.of(s.part), Offset(minOf(a, b) - 1f, tops[i]), Size(abs(b - a) + 2f, tall))
                        }
                    }
                    drawPath(outlines[i], outline, style = Stroke(hair))
                }
                if (strips.isNotEmpty()) {
                    val lx = x(at()).coerceIn(line, size.width - line)
                    drawLine(ring, Offset(lx, from), Offset(lx, to), strokeWidth = line + 3.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(ink, Offset(lx, from), Offset(lx, to), strokeWidth = line, cap = StrokeCap.Round)
                }
            }
        },
    )
}

/**
 * The hours under a clock's day strips ([WidgetWords.hourMarks]), each centred on its place on the
 * strip, the first and the last at its ends. Where they would touch (a narrow strip, a large font
 * size), only 00, 12 and 24 are written. They are no item for a screen reader.
 */
@Composable
private fun HourMarks(clock24: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    val marks = remember(clock24) { WidgetWords.hourMarks(clock24) }
    val colour = lookColors().muted
    Layout(
        content = { marks.forEach { Text(it, style = style, color = colour, maxLines = 1, softWrap = false) } },
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val last = placeables.size - 1
        val xs = placeables.mapIndexed { i, p -> (width * i / last.toFloat() - p.width / 2f).roundToInt().coerceIn(0, maxOf(0, width - p.width)) }
        val apart = 4.dp.roundToPx()
        val crowded = (1..last).any { xs[it] < xs[it - 1] + placeables[it - 1].width + apart }
        val shown = if (crowded) listOf(0, last / 2, last) else placeables.indices.toList()
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            for (i in shown) placeables[i].placeRelative(xs[i], 0)
        }
    }
}

/**
 * A clock's [time], as a value ([asValue]), with digits [sizeDp] tall whatever the phone's font
 * size is: the size was made to fit the box. The digits are all one width where the font can do
 * that, so the text does not shake as it ticks. It reads the time itself, so a tick draws this
 * text again, not the whole card.
 */
@Composable
private fun ClockTime(time: () -> String, sizeDp: Int, colour: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    val size = with(LocalDensity.current) { sizeDp.dp.toSp() }
    val base = MaterialTheme.typography.titleLarge
    val style = remember(base, size, bold) {
        if (bold) base.copy(fontSize = size, lineHeight = size).asBold() else base.copy(fontSize = size, lineHeight = size * 1.25f).asValue()
    }
    Text(time(), style = style, color = colour, maxLines = 1, softWrap = false)
}

/**
 * [style] for the Bold look's main value: the heaviest weight, tighter, with digits of one width.
 * Kolahal has no narrow variable font, so the value is as large as its room allows instead.
 */
private fun TextStyle.asBold(): TextStyle = copy(fontWeight = FontWeight.Black, letterSpacing = (-0.03).em, fontFeatureSettings = "tnum")

/** Black type is wider than the size a clock's digits are measured for ([WidgetWords.clockTextDp]): it gets this much of the width. */
private const val BOLD_WIDTH = 0.9f

/**
 * The Bold clock: the first place's time at display size in the card's [accent], under its name
 * and its date. The other places are small, under it in a large box and beside it in a wide one,
 * as many as fit.
 */
@Composable
private fun BoldClock(times: List<WidgetWords.ZoneTime>, time: (Int) -> () -> String, zone: ZoneId, now: State<Long>, box: WidgetSizes.Box, chars: Int, accent: Color) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        val first = times[0]
        when (box) {
            WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                ClockTime(time(0), WidgetWords.clockTextDp((width * BOLD_WIDTH).toInt(), chars, (height - PLACE_DP) * 4 / 5), accent, bold = true)
                PlaceLabel(first)
            }
            WidgetSizes.Box.WIDE -> {
                // The others need about 72 dp each beside the main time, which keeps at least 120.
                val beside = ((width - 120) / 80).coerceIn(0, times.size - 1)
                val mainWidth = width - beside * 80
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PlaceAndDate(first, zone, now)
                        ClockTime(time(0), WidgetWords.clockTextDp((mainWidth * BOLD_WIDTH).toInt(), chars, height - 20), accent, bold = true)
                    }
                    for (i in 1..beside) key(i) { SmallPlace(times[i], time(i), Modifier.width(72.dp).padding(start = 8.dp)) }
                }
            }
            WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
                PlaceAndDate(first, zone, now)
                val others = (times.size - 1).coerceAtMost(maxOf(1, (width + 16) / 88))
                // The big time takes what the place line and the row of the others leave.
                val room = height - 20 - if (others > 0) 44 else 0
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    ClockTime(time(0), WidgetWords.clockTextDp((width * BOLD_WIDTH).toInt(), chars, minOf(room, 80)), accent, bold = true)
                }
                if (others > 0) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 1..others) key(i) { SmallPlace(times[i], time(i), Modifier.weight(1f, fill = false)) }
                }
            }
        }
    }
}

/** The first place's name in bold and its date, dim: "Pune · Fri 20 Nov". The date moves with the clock's tick. */
@Composable
private fun PlaceAndDate(time: WidgetWords.ZoneTime, zone: ZoneId, now: State<Long>) {
    val type = MaterialTheme.typography
    Row {
        Text(time.label, style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        DateOf(zone, now, type.labelMedium, lookColors().muted, prefix = " · ")
    }
}

/** The date at [zone] ("Fri 20 Nov"). It reads the time itself, so a tick draws this text again, not the card. */
@Composable
private fun DateOf(zone: ZoneId, now: State<Long>, style: TextStyle, colour: Color, prefix: String = "") {
    val format = remember { TimeFormats.dayFormat() }
    val day = Instant.ofEpochMilli(now.value).atZone(zone).toLocalDate()
    val text = remember(day) { prefix + day.format(format) }
    Text(text, style = style, color = colour, maxLines = 1, softWrap = false)
}

/** One of the other places of a clock, small: its name dim over its time. */
@Composable
private fun SmallPlace(place: WidgetWords.ZoneTime, time: () -> String, modifier: Modifier = Modifier) {
    Column(modifier) {
        PlaceLabel(place)
        ClockTime(time, 16)
    }
}

/**
 * The Split clock: the first place in a big tile, its name over its time, and the others in small
 * tiles under it (large) or beside it (wide), as many as fit at 64 dp each.
 */
@Composable
private fun SplitClock(times: List<WidgetWords.ZoneTime>, time: (Int) -> () -> String, box: WidgetSizes.Box, chars: Int) {
    val tile = lookColors().tile
    val gap = SplitCorners.GAP
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        when (box) {
            WidgetSizes.Box.LARGE -> {
                val others = (times.size - 1).coerceAtMost(maxOf(1, (width + 4) / 68)).coerceAtMost(3)
                // The big tile takes three fifths of the height, as the mockup has it.
                val bigDp = if (others > 0) (height - 4) * 3 / 5 else height
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    val big = if (others > 0) SplitCorners.tile(topStart = true, topEnd = true, bottomEnd = false, bottomStart = false) else SplitCorners.inColumn(0, 1)
                    Column(Modifier.fillMaxWidth().height(bigDp.dp).clip(big).background(tile).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(times[0].label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        ClockTime(time(0), WidgetWords.clockTextDp(width - 32, chars, minOf(44, (bigDp - 40) * 4 / 5)))
                    }
                    if (others > 0) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        val cell = (width - 4 * (others - 1)) / others
                        for (i in 1..others) key(i) {
                            val shape = SplitCorners.tile(topStart = false, topEnd = false, bottomEnd = i == others, bottomStart = i == 1)
                            SplitPlace(times[i], time(i), WidgetWords.clockTextDp(cell - 16, chars, 18), shape, tile, Modifier.weight(1f))
                        }
                    }
                }
            }
            WidgetSizes.Box.WIDE -> {
                val others = (times.size - 1).coerceAtMost(maxOf(0, (width - 96) / 68)).coerceAtMost(3)
                val count = others + 1
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    // The first tile is half as wide again as the others.
                    val firstDp = if (others > 0) ((width - 4 * others) * 3 / (3 + 2 * others)) else width
                    SplitPlace(times[0], time(0), WidgetWords.clockTextDp(firstDp - 24, chars, minOf(32, (height - 24) * 4 / 5)), SplitCorners.inRow(0, count), tile, Modifier.weight(1.5f), start = true)
                    for (i in 1..others) key(i) {
                        SplitPlace(times[i], time(i), WidgetWords.clockTextDp((firstDp * 2 / 3) - 16, chars, 18), SplitCorners.inRow(i, count), tile, Modifier.weight(1f))
                    }
                }
            }
            WidgetSizes.Box.SMALL -> SplitPlace(times[0], time(0), WidgetWords.clockTextDp(width - 16, chars, (height - PLACE_DP) * 4 / 5), SplitCorners.inColumn(0, 1), tile, Modifier.fillMaxSize())
        }
    }
}

/** One of Split's clock tiles: the place's name, dim, over its time, in the middle or at the [start]. */
@Composable
private fun SplitPlace(place: WidgetWords.ZoneTime, time: () -> String, sizeDp: Int, shape: Shape, tile: Color, modifier: Modifier, start: Boolean = false) {
    Column(
        modifier.fillMaxHeight().clip(shape).background(tile).padding(horizontal = if (start) 12.dp else 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        PlaceLabel(place)
        ClockTime(time, sizeDp)
    }
}

/**
 * The Shape clock.
 * - In a square box the card is the shape ([LocalCardShaped]), and this draws an analog dial in it
 *   with the first place's name ([ClockDial]).
 * - Wide: the dial in a small cookie at the start, the times beside it in words: "Pune 14:32" over
 *   "London 09:02 · New York 04:02".
 * - Large but not square: the dial at the start, a row for each place beside it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeClock(times: List<WidgetWords.ZoneTime>, time: (Int) -> () -> String, zone: ZoneId, now: State<Long>, box: WidgetSizes.Box, accent: Color) {
    val looks = lookColors(WidgetLook.SHAPE, accent)
    if (LocalCardShaped.current) {
        ClockDial(times[0].label, zone, now, looks, Modifier.fillMaxSize(), filled = false)
        return
    }
    val type = MaterialTheme.typography
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxHeight = maxHeight
        if (box == WidgetSizes.Box.SMALL) {
            ClockDial(null, zone, now, looks, Modifier.fillMaxSize(), filled = true)
            return@BoxWithConstraints
        }
        val side = minOf(maxHeight, if (box == WidgetSizes.Box.WIDE) 72.dp else 140.dp, maxWidth / 3)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ClockDial(null, zone, now, looks, Modifier.size(side), filled = true)
            if (box == WidgetSizes.Box.WIDE) {
                Column(Modifier.weight(1f)) {
                    val first = time(0)
                    Text("${times[0].label} ${first()}", style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = looks.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (times.size > 1) {
                        val others = (1 until times.size).map { it to time(it) }
                        Text(others.joinToString(" · ") { (i, t) -> "${times[i].label} ${t()}" }, style = type.labelMedium, color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = times.size.coerceAtMost(maxOf(1, (boxHeight.value.toInt() + 8) / 56))
                    for (i in 0 until rows) key(i) {
                        Column {
                            PlaceLabel(times[i], style = type.labelMedium)
                            ClockTime(time(i), 22)
                        }
                    }
                }
            }
        }
    }
}

/** The shape a Shape clock's dial is drawn in when the card is not the shape itself. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DIAL_SHAPE get() = MaterialShapes.Cookie9Sided

/**
 * An analog dial at the time [now] in [zone], as the mockup draws it in a 100-unit square: four dots
 * for 12, 3, 6 and 9, the hour hand in the text colour, the minute hand in the accent, a dot in
 * the middle, and the place's [name] over the middle.
 * - It reads the time while drawing, so the clock's tick (once a minute) draws the dial again and
 *   composes nothing. It has no second hand: nothing moves between two ticks.
 * - With [filled] it draws its own cookie first; in a card that is the shape, the card is the cookie.
 * - It is no item for a screen reader: the card's description says the time.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClockDial(name: String?, zone: ZoneId, now: State<Long>, looks: LookColors, modifier: Modifier, filled: Boolean) {
    val hour = looks.content
    val minute = looks.accent
    val dots = looks.muted
    val fill = if (filled) Modifier.background(looks.shapeFill, DIAL_SHAPE.toShape()) else Modifier
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side).then(fill).drawBehind {
            val unit = size.minDimension / 100f
            val middle = center
            for (k in 0 until 4) {
                val a = k * PI / 2
                drawCircle(dots, 1.8f * unit, middle + Offset((sin(a) * 34 * unit).toFloat(), (-cos(a) * 34 * unit).toFloat()))
            }
            val at = Instant.ofEpochMilli(now.value).atZone(zone)
            val minutes = at.minute
            val hours = at.hour % 12 + minutes / 60f
            fun hand(turns: Float, length: Float, width: Float, colour: Color) {
                val a = turns * 2 * PI
                val end = middle + Offset((sin(a) * length * unit).toFloat(), (-cos(a) * length * unit).toFloat())
                drawLine(colour, middle, end, strokeWidth = width * unit, cap = StrokeCap.Round)
            }
            hand(hours / 12f, 20f, 5f, hour)
            hand(minutes / 60f, 30f, 3.5f, minute)
            drawCircle(hour, 3.5f * unit, middle)
        }.clearAndSetSemantics {})
        if (name != null && side >= 96.dp) {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = hour,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = -side * 0.17f).widthIn(max = side * 0.5f).clearAndSetSemantics {},
            )
        }
    }
}

/**
 * A place's name in bold, over its difference from the phone's time in dim words: the first of its
 * lines ([WidgetWords.placeLines]) that fits whole, "5 h 30 min behind" or "−5:30".
 */
@Composable
private fun PlaceAndDifference(time: WidgetWords.ZoneTime, nameStyle: TextStyle, lineStyle: TextStyle) {
    val lines = remember(time) { WidgetWords.placeLines(time) }
    Column {
        Text(time.label, style = nameStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FittingLine(lines, lineStyle, lookColors().muted)
    }
}

/**
 * The first of [lines] that fits whole on one line in the width it gets, in [style] at the phone's
 * font size. With none that fits, the last one, cut with "…". Numbers such as "−5:30 −1" read from
 * the left in a right-to-left page too.
 */
@Composable
private fun FittingLine(lines: List<String>, style: TextStyle, colour: Color) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val room = constraints.maxWidth
        val text = remember(lines, style, room, measurer) {
            lines.firstOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= room } ?: lines.last()
        }
        Text(text, style = style.copy(textDirection = TextDirection.ContentOrLtr), color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A place's name in [colour], and beside it, dim, the first of its lines ([WidgetWords.placeLines])
 * that fits whole with the name: "London · 5 h 30 min behind", "London −5:30", "Pune · Here".
 * - Where none fits, only the place's day is beside the name, as "+1" or "−1", and the name is cut
 *   with "…" before the day is. A place on the day here then has its name alone.
 * - The words are a text of their own after the name, so they follow it in a right-to-left page.
 */
@Composable
private fun PlaceLabel(time: WidgetWords.ZoneTime, style: TextStyle = MaterialTheme.typography.labelSmall, colour: Color = lookColors().muted) {
    val muted = lookColors().muted
    val measurer = rememberTextMeasurer()
    val lines = remember(time) { WidgetWords.placeLines(time) }
    // A dot before words, a space before a number.
    fun gapBefore(words: String) = if (words.first().isLetter()) " · " else " "
    BoxWithConstraints {
        val room = constraints.maxWidth
        val words = remember(lines, time.label, style, room, measurer) {
            fun width(text: String) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            val name = width(time.label)
            lines.firstOrNull { name + width(gapBefore(it)) + width(it) <= room }
        } ?: time.dayShort
        Row {
            Text(time.label, style = style, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (words != null) {
                Text(gapBefore(words), style = style, color = muted, maxLines = 1, softWrap = false)
                Text(words, style = style.copy(textDirection = TextDirection.ContentOrLtr), color = muted, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---- agenda ---------------------------------------------------------------------------------

/**
 * Whether Kolahal writes a time of day in 24-hour form: its own setting, which the header and the
 * log follow too. It is read again when a setting changes.
 */
@Composable
internal fun rememberClock24(): Boolean {
    val context = LocalContext.current.applicationContext
    val config = remember(context) { Config.get(context) }
    val version by config.version.collectAsStateWithLifecycle()
    return remember(config, version) { config[Keys.CLOCK_24H] }
}

/** Height of a day's heading and of the "+3 more" line, in dp. */
private const val AGENDA_HEAD_DP = 16
private const val AGENDA_MORE_DP = 20

/** The least height of an event's own line under a heading, in dp: one line of text, or two in a narrow box. */
private const val AGENDA_LINE_DP = 28
private const val AGENDA_NARROW_LINE_DP = 32

/** Under this width an event has its time under its title, not beside it. */
private val AGENDA_NARROW = 120.dp

/** From this width an event has its place at its end. */
private val AGENDA_PLACES = 200.dp

/**
 * `agenda`: events under "Today", "Tomorrow" and dates, as many as fit, then "+3 more".
 * - Kolahal writes the times, in the form the setting says, and drops an event when it has ended.
 *   That happens on the minute's tick ([rememberNow]), with no run of the script and no tick of
 *   its own.
 * - An event is one line: its time, a dot in its colour if it has one, its title, and its place at its end in a
 *   box that has room. An event that can be tapped is 44 dp tall ([TAP_ROW_DP]), others 32 dp.
 * - A day's heading ("TODAY") is at the top of its first event's row, in the card's colour. That
 *   row is as tall as a tap row needs, or as the heading and the event's line need, whichever is
 *   more: 44 dp. So a 4x2 card has two events of today and one of tomorrow, as the mockup has
 *   them, and every event keeps a 44 dp tap. A tap on the heading is a tap on that event.
 * - "+3 more" is at the end of the card's title line ([ReportMore]). When nothing is left out, a
 *   card 200 dp wide or more has today's date there ("Sat 14 Nov"). A card without a title line
 *   has "+3 more" under the events.
 * - With nothing to show it says the description's `empty` text.
 */
@Composable
internal fun AgendaBody(spec: WidgetSpec.Agenda, onTap: ((WidgetTap) -> Unit)?) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val days = remember(spec, minute, zone, clock24) { WidgetWords.agenda(spec, minute.time / 1000, zone, clock24) }
    if (days.isEmpty()) {
        QuietLine(spec.empty ?: WidgetWords.NO_EVENTS)
        return
    }
    val look = LocalWidgetLook.current
    if (look == WidgetLook.BOLD || look == WidgetLook.SPLIT || look == WidgetLook.SHAPE) {
        DatedAgenda(look, days, minute.time, zone, clock24, roleColour(spec.base), onTap)
        return
    }
    val colour = roleColour(spec.base)
    val muted = lookColors().muted
    val inTitle = LocalMoreNote.current != null
    val date = remember(minute, zone) { java.time.Instant.ofEpochMilli(minute.time).atZone(zone).format(TimeFormats.dayFormat()) }
    // Tonal puts each event on a tile of its own, 2 dp from the next, as a list's rows are.
    val tile = if (look == WidgetLook.TONAL) lookColors().tile else null
    val gapDp = if (tile != null) ROW_GAP_DP else 0
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < AGENDA_NARROW
        val places = maxWidth >= AGENDA_PLACES
        val rowDp = if (days.any { day -> day.lines.any { it.event.tap != null } }) TAP_ROW_DP else 32
        // A day's first row has its heading at its top.
        val headedDp = maxOf(rowDp, AGENDA_HEAD_DP + if (narrow) AGENDA_NARROW_LINE_DP else AGENDA_LINE_DP)
        val heightDp = maxHeight.value.toInt()
        val total = days.sumOf { it.lines.size }
        val moreDp = if (inTitle) 0 else AGENDA_MORE_DP
        // Each row takes its gap too, and the last one has none after it.
        val shown = WidgetWords.agendaShown(days.map { it.lines.size }, heightDp + gapDp, headedDp - rowDp, rowDp + gapDp, moreDp)
        // Today's date only on a wide card (4x2): on a narrower one it would take the title's room.
        ReportMore(if (shown < total) "+${total - shown} more" else date.takeIf { places })
        // The time column is as wide as the longest time in this form, so the titles line up.
        val timeWidth = if (clock24) 48.dp else 68.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gapDp.dp)) {
            var left = shown
            var usedDp = 0
            var n = 0
            for ((d, day) in days.withIndex()) {
                if (left <= 0) break
                val lines = day.lines.take(left)
                left -= lines.size
                usedDp += headedDp + (lines.size - 1) * rowDp + lines.size * gapDp
                key(d) {
                    lines.forEachIndexed { i, line ->
                        val back = tile?.let { it to segmentShape(n, shown) }
                        n++
                        key(i) {
                            if (i == 0) AgendaRow(line, headedDp.dp, places, narrow, timeWidth, onTap, back) { DayHeading(day.label, colour) }
                            else AgendaRow(line, rowDp.dp, places, narrow, timeWidth, onTap, back)
                        }
                    }
                }
            }
            if (!inTitle && shown < total && usedDp + AGENDA_MORE_DP <= heightDp) {
                Text("+${total - shown} more", style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, modifier = Modifier.height(AGENDA_MORE_DP.dp).wrapContentHeight())
            }
        }
    }
}

/** A day's heading in an agenda: "TODAY", "TOMORROW", "TUE 20 OCT", in bold capitals in the card's [colour]. */
@Composable
private fun DayHeading(label: String, colour: Color) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
        color = colour,
        maxLines = 1,
        modifier = Modifier.height(AGENDA_HEAD_DP.dp).wrapContentHeight(),
    )
}

/**
 * One event: its time, a dot in its colour (none without one), its title, and its place at its end
 * when [places]. A day's first event has the day's [heading] at its top, inside its row.
 * A [narrow] row has the time under the title and no dot. An event with a tap takes the press
 * before the card does, so it hands a long-press on to the card, as a list's row does.
 * With [tile] (Tonal) the row is on a tile of that colour and shape, its text inside the tile's padding.
 */
@Composable
private fun AgendaRow(
    line: WidgetWords.AgendaLine,
    height: Dp,
    places: Boolean,
    narrow: Boolean,
    timeWidth: Dp,
    onTap: ((WidgetTap) -> Unit)?,
    tile: Pair<Color, Shape>? = null,
    heading: (@Composable () -> Unit)? = null,
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val event = line.event
    val colour = event.color?.let { colourOf(it) }
    val press = eventPress(event.tap, onTap)
    // Small corners: a larger one would cut the first letter of a heading at the row's top.
    val back = if (tile != null) {
        Modifier.clip(tile.second).background(tile.first).then(press).padding(horizontal = 12.dp, vertical = if (heading != null) 3.dp else 0.dp)
    } else {
        Modifier.clip(RoundedCornerShape(6.dp)).then(press)
    }
    Column(Modifier.fillMaxWidth().height(height).then(back)) {
        heading?.invoke()
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (narrow) {
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = type.bodySmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line.time, style = type.labelSmall, color = colour ?: lookColors().muted, maxLines = 1, softWrap = false)
                }
            } else {
                Text(line.time, style = type.labelLarge, color = lookColors().muted, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = timeWidth).padding(end = 8.dp))
                if (colour != null) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(colour))
                Text(event.title, style = type.bodyMedium.copy(fontWeight = FontWeight.Medium), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (places) event.where?.let {
                    Text(it, style = type.bodySmall, color = lookColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 120.dp))
                }
            }
        }
    }
}

/**
 * The tap of an event with an action of its own: it takes the press before the card does, so it
 * hands a long-press on to the card, as a list's row does. Nothing without an action or an [onTap].
 */
@Composable
private fun eventPress(tap: WidgetTap?, onTap: ((WidgetTap) -> Unit)?): Modifier {
    val longPress = LocalCardLongPress.current
    return if (tap != null && onTap != null) {
        Modifier.combinedClickable(
            onClickLabel = tap.label,
            role = Role.Button,
            onLongClickLabel = if (longPress != null) "arrange widgets" else null,
            onLongClick = longPress,
            onClick = { onTap(tap) },
        )
    } else {
        Modifier
    }
}

/** One event of a dated agenda ([DatedAgenda]), with its day: [today] or the day's [label] ("Tomorrow"). */
private class DatedLine(val line: WidgetWords.AgendaLine, val label: String, val today: Boolean)

/** The height of a dated agenda's event, in dp: a row that keeps its tap. */
private const val DATED_ROW_DP = 42

/** From this width a dated agenda has today's date at the start, beside the events; a narrower one has it on top. */
private val DATED_SIDE = 240.dp

/**
 * The agenda of the Bold, Split and Shape looks: today's date takes a place of its own, and the
 * events are rows of 42 dp beside it (or under it in a narrow box), with no day headings.
 * - Bold: the date in a block of the card's [role] colour, the weekday and the month in capitals
 *   around the day at display size. Today's times are in the role colour.
 * - Shape: the date in a sunny shape of the role colour.
 * - Split: the date in a tile of its own, and each event in a tile with its time in a chip.
 * - An event of another day says so: "Gym · tomorrow", dim (Split: "Tomorrow" at its end).
 * - As many events as fit are shown, never under 42 dp; "+3 more" is left out. Each keeps its tap.
 */
@Composable
private fun DatedAgenda(look: WidgetLook, days: List<WidgetWords.AgendaDay>, nowMs: Long, zone: ZoneId, clock24: Boolean, role: Color, onTap: ((WidgetTap) -> Unit)?) {
    val lines = remember(days) { days.flatMap { day -> day.lines.map { DatedLine(it, day.label, day.label == WidgetWords.TODAY) } } }
    val today = remember(nowMs, zone) { Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() }
    val locale = Locale.getDefault()
    val weekday = remember(today) { today.format(DateTimeFormatter.ofPattern("EEE", locale)) }
    val month = remember(today) { today.format(DateTimeFormatter.ofPattern("MMM", locale)) }
    val dayNumber = today.dayOfMonth.toString()
    val looks = lookColors(look, role)
    val timeWidth = if (clock24) 44.dp else 64.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val side = maxWidth >= DATED_SIDE
        val heightDp = maxHeight.value.toInt()
        if (look == WidgetLook.SPLIT) {
            SplitAgenda(lines, weekday, dayNumber, month, side, heightDp, looks, onTap)
            return@BoxWithConstraints
        }
        if (side) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (look == WidgetLook.BOLD) {
                    BoldDate(weekday, dayNumber, month.takeIf { heightDp >= 120 }, looks, Modifier.width(minOf(96.dp, boxWidth / 4)).fillMaxHeight())
                } else {
                    SunnyDate(weekday.takeIf { heightDp >= 88 }, dayNumber, looks, minOf(boxHeight, 120.dp, boxWidth / 3))
                }
                DatedRows(lines, (heightDp / DATED_ROW_DP).coerceAtLeast(1), timeWidth, looks, look == WidgetLook.BOLD, onTap, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (look == WidgetLook.BOLD) {
                        Text(dayNumber, style = MaterialTheme.typography.displaySmall.asBold(), color = looks.accent, maxLines = 1)
                    } else {
                        SunnyDate(null, dayNumber, looks, 52.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(weekday.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.08.em), color = looks.content, maxLines = 1)
                        Text(month.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.08.em), color = looks.muted, maxLines = 1)
                    }
                }
                DatedRows(lines, ((heightDp - 56) / DATED_ROW_DP).coerceAtLeast(1), timeWidth, looks, look == WidgetLook.BOLD, onTap, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Bold's date: a block of the role colour, "FRI" over the day at display size over "NOV" ([month], left out when low). */
@Composable
private fun BoldDate(weekday: String, day: String, month: String?, looks: LookColors, modifier: Modifier) {
    val caps = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.08.em)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(20.dp)).background(looks.accent), contentAlignment = Alignment.Center) {
        // The day is as big as the block allows: up to 76 dp, as the mockup has it.
        val sizeDp = minOf(76f, maxWidth.value * 0.62f, (maxHeight.value - if (month != null) 48 else 28) * 0.9f)
        val size = with(LocalDensity.current) { sizeDp.dp.toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekday.uppercase(), style = caps, color = looks.onAccent, maxLines = 1)
            Text(day, style = MaterialTheme.typography.displayLarge.copy(fontSize = size, lineHeight = size).asBold(), color = looks.onAccent, maxLines = 1)
            if (month != null) Text(month.uppercase(), style = caps, color = looks.onAccent, maxLines = 1)
        }
    }
}

/** Shape's date: the day in a sunny shape of the role colour, [side] across, under the weekday where there is room. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SunnyDate(weekday: String?, day: String, looks: LookColors, side: Dp) {
    val size = with(LocalDensity.current) { (side * 0.4f).toSp() }
    Box(Modifier.size(side).background(looks.accent, MaterialShapes.Sunny.toShape()), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (weekday != null) {
                Text(weekday.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.08.em), color = looks.onAccent, maxLines = 1)
            }
            Text(day, style = MaterialTheme.typography.displaySmall.copy(fontSize = size, lineHeight = size).asBold(), color = looks.onAccent, maxLines = 1)
        }
    }
}

/**
 * The events of a dated agenda: the first [count] of [lines], 42 dp each, centred in their room.
 * Today's time is in the role colour and bold; an event of another day is dim, with its day after
 * its title.
 */
@Composable
private fun DatedRows(lines: List<DatedLine>, count: Int, timeWidth: Dp, looks: LookColors, bold: Boolean, onTap: ((WidgetTap) -> Unit)?, modifier: Modifier) {
    val type = MaterialTheme.typography
    val titleStyle = if (bold) type.titleSmall.copy(fontWeight = FontWeight.Bold) else type.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val timeStyle = (if (bold) type.titleSmall else type.labelLarge).asValue()
    Column(modifier, verticalArrangement = Arrangement.Center) {
        lines.take(count).forEachIndexed { i, d ->
            key(i) {
                val ink = if (d.today) looks.content else looks.muted
                Row(
                    Modifier.fillMaxWidth().height(DATED_ROW_DP.dp).clip(RoundedCornerShape(8.dp)).then(eventPress(d.line.event.tap, onTap)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(d.line.time, style = timeStyle, color = if (d.today) looks.accent else looks.muted, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = timeWidth).padding(end = 10.dp))
                    val title = if (d.today) d.line.event.title else "${d.line.event.title} · ${dayWord(d.label)}"
                    Text(title, style = titleStyle, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** A day's label as a word inside a line: "tomorrow", or a date as it is ("Tue 20 Oct"). */
private fun dayWord(label: String): String = if (label == WidgetWords.TOMORROW) label.lowercase() else label

/**
 * Split's agenda: today's date in a tile at the start ("Fri" over "20" over "Nov"), or along the top
 * of a narrow box, and each event in a tile of its own with its time in a chip. Today's chips are in
 * the role's container; another day's are grey, with the day at the tile's end.
 */
@Composable
private fun SplitAgenda(lines: List<DatedLine>, weekday: String, day: String, month: String, side: Boolean, heightDp: Int, looks: LookColors, onTap: ((WidgetTap) -> Unit)?) {
    val gap = SplitCorners.GAP
    val type = MaterialTheme.typography
    val dim = type.labelMedium.copy(fontWeight = FontWeight.Bold)
    if (side) {
        val count = ((heightDp + 4) / (DATED_ROW_DP + 4)).coerceIn(1, maxOf(1, lines.size))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(
                Modifier.width(84.dp).fillMaxHeight().clip(SplitCorners.tile(topStart = true, topEnd = false, bottomEnd = false, bottomStart = true)).background(looks.tile),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekday, style = dim, color = looks.muted, maxLines = 1)
                Text(day, style = type.headlineLarge.asValue(), color = looks.content, maxLines = 1)
                if (heightDp >= 120) Text(month, style = dim, color = looks.muted, maxLines = 1)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 0 until count) key(i) {
                    val shape = SplitCorners.tile(topStart = false, topEnd = i == 0, bottomEnd = i == count - 1, bottomStart = false)
                    SplitEvent(lines.getOrNull(i), shape, looks, onTap, Modifier.weight(1f))
                }
            }
        }
    } else {
        val count = ((heightDp - 48 + 4) / (DATED_ROW_DP + 4)).coerceIn(1, maxOf(1, lines.size))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(SplitCorners.tile(topStart = true, topEnd = true, bottomEnd = false, bottomStart = false)).background(looks.tile).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(day, style = type.titleLarge.asValue(), color = looks.content, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text("$weekday $month", style = dim, color = looks.muted, maxLines = 1)
            }
            for (i in 0 until count) key(i) {
                val shape = SplitCorners.tile(topStart = false, topEnd = false, bottomEnd = i == count - 1, bottomStart = i == count - 1)
                SplitEvent(lines.getOrNull(i), shape, looks, onTap, Modifier.weight(1f), dayAtEnd = false)
            }
        }
    }
}

/**
 * One event's tile in Split's agenda: its time in a chip, its title, and its day at the end when
 * it is not today. A narrow box has no room for the day: [dayAtEnd] false leaves it out, and the
 * grey chip says the event is not today.
 */
@Composable
private fun SplitEvent(d: DatedLine?, shape: Shape, looks: LookColors, onTap: ((WidgetTap) -> Unit)?, modifier: Modifier, dayAtEnd: Boolean = true) {
    val type = MaterialTheme.typography
    Row(
        modifier.fillMaxWidth().clip(shape).background(looks.tile).then(eventPress(d?.line?.event?.tap, onTap)).padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (d == null) return@Row
        val chip = if (d.today) roleContainer(looks.accent) else MaterialTheme.colorScheme.surfaceContainerHighest
        Text(
            d.line.time,
            style = type.labelMedium.copy(fontWeight = FontWeight.ExtraBold).asValue(),
            color = looks.content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(chip).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(d.line.event.title, style = type.bodyMedium.copy(fontWeight = FontWeight.Bold), color = looks.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!d.today && dayAtEnd) Text(d.label, style = type.labelSmall, color = looks.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}

// ---- the time, for media, the clock and the countdown ----------------------------------------

/**
 * The time now, in milliseconds.
 * - It moves by the minute, with the header's clock ([rememberNow]). That tick also comes when
 *   home is back on screen and when the clock is set.
 * - With [seconds] it moves every second ([EverySecond]).
 * - It is a state: read it where the time is drawn, so a tick draws that again, not the card.
 */
@Composable
private fun rememberMillis(seconds: Boolean): State<Long> {
    val (minute, _) = rememberNow()
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(minute) { now.longValue = System.currentTimeMillis() }
    EverySecond(seconds) { now.longValue = it }
    return now
}

/**
 * Calls [onSecond] with the time in milliseconds when each second starts.
 * - Only while [on], the card can be seen ([cardSeen]) and home is on screen. A card nobody sees
 *   ticks for nothing.
 * - The clock, media and countdown cards use it.
 */
@Composable
internal fun EverySecond(on: Boolean, onSecond: (Long) -> Unit) {
    if (!on || !cardSeen()) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tick by rememberUpdatedState(onSecond)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val ms = System.currentTimeMillis()
                tick(ms)
                // Wake when the next second starts.
                delay(1000 - ms % 1000)
            }
        }
    }
}

// ---- media ----------------------------------------------------------------------------------

private val PREV = WidgetTap(args = WidgetWords.MEDIA_PREV)
private val TOGGLE = WidgetTap(args = WidgetWords.MEDIA_TOGGLE)
private val NEXT = WidgetTap(args = WidgetWords.MEDIA_NEXT)

/** In a large box, the bar needs this much height, and the two times under it this much. */
private val MEDIA_BAR_FROM = 104.dp
private val MEDIA_TIMES_FROM = 122.dp

/**
 * `media`: what is playing, with its controls. Only Kolahal makes this kind.
 * - Small: play or pause alone.
 * - Wide: the title in bold, the artist and the app, with a bar for the position under them, and the
 *   buttons at the end. A narrow box has fewer buttons, never smaller ones ([WidgetWords.mediaButtons]).
 *   Play or pause is wider than the others where there is room.
 * - Large: the same in rows, with the position and the length under the bar.
 * - The three buttons hand `prev`, `toggle` and `next` to [onTap]. A tap elsewhere is the card's.
 * - The position moves every second only while something plays, its length is known and the page
 *   is in view. The bar stands still between ticks: it is no wave, because a wave is drawn on every frame.
 * - Tonal: play or pause in the card's colour, the other two on the look's tile.
 * - Split and Shape have layouts of their own ([SplitMedia], [ShapeMedia]).
 */
@Composable
internal fun MediaBody(spec: WidgetSpec.Media, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val colour = roleColour(spec.base)
    when (LocalWidgetLook.current) {
        WidgetLook.SPLIT -> return SplitMedia(spec, box, colour, onTap)
        WidgetLook.SHAPE -> return ShapeMedia(spec, box, colour, onTap)
        else -> {}
    }
    if (box == WidgetSizes.Box.SMALL) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MediaButtons(1, WidgetWords.mediaPlayDp(maxWidth.value.toInt(), besideText = false, count = 1), spec.playing, onTap, colour)
        }
        return
    }
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val title = WidgetWords.mediaTitle(spec)
    val line = WidgetWords.mediaLine(spec)
    val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
    // The bar moves in steps, so it is drawn again only when it has moved one.
    val step by remember(spec) { derivedStateOf { WidgetWords.mediaFraction(spec, now.value)?.let(WidgetWords::barStep) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fraction = step
        val width = maxWidth.value.toInt()
        val tall = maxHeight
        if (box == WidgetSizes.Box.WIDE) {
            // The buttons are measured first, so the texts get what is left and are cut, not the buttons.
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = lookColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (fraction != null && tall >= TWO_LINES) LevelBar(fraction, moving = spec.playing, colour, Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(WidgetWords.CONTROL_GAP_DP.dp))
                val count = WidgetWords.mediaButtons(width, besideText = true)
                MediaButtons(count, WidgetWords.mediaPlayDp(width, besideText = true, count), spec.playing, onTap, colour)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Text(title, style = type.titleMedium.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = lookColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (fraction != null && tall >= MEDIA_BAR_FROM) {
                        Spacer(Modifier.weight(1f))
                        LevelBar(fraction, moving = spec.playing, colour)
                        if (tall >= MEDIA_TIMES_FROM) Row(Modifier.fillMaxWidth()) {
                            MediaPosition(spec, now)
                            Spacer(Modifier.weight(1f))
                            Text(WidgetWords.mediaTime(spec.duration), style = type.labelSmall, color = lookColors().muted, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    val count = WidgetWords.mediaButtons(width, besideText = false)
                    MediaButtons(count, WidgetWords.mediaPlayDp(width, besideText = false, count), spec.playing, onTap, colour)
                }
            }
        }
    }
}

/**
 * [count] of the three buttons, in a row: play or pause alone, then with next, then with previous
 * too. Play or pause is [playDp] wide ([WidgetWords.mediaPlayDp]). [role] is the card's colour,
 * for the looks that use it.
 */
@Composable
private fun MediaButtons(count: Int, playDp: Int, playing: Boolean, onTap: ((WidgetTap) -> Unit)?, role: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count >= 3) MediaButton(PREV, R.drawable.ic_skip_next, "Previous", onTap, role, mirrored = true)
        MediaButton(TOGGLE, playIcon(playing), playWord(playing), onTap, role, widthDp = playDp, strong = true)
        if (count >= 2) MediaButton(NEXT, R.drawable.ic_skip_next, "Next", onTap, role)
    }
}

private fun playIcon(playing: Boolean) = if (playing) R.drawable.ic_pause else R.drawable.ic_play
private fun playWord(playing: Boolean) = if (playing) "Pause" else "Play"

/**
 * One of the media buttons, 48 dp tall and [widthDp] wide, or as [size] says. [said] is its name
 * for a screen reader.
 * - [strong]: the filled button, for play or pause. Its corners are squarer: a wide one is not a pill.
 *   The others are tonal and round.
 * - [mirrored]: the icon is turned to point back. "Previous" is the "next" icon, mirrored.
 * - With a null [onTap] it takes no tap, and looks as it does on the page.
 * - Tonal and Split: play or pause is in the card's [role] colour, the others on the look's tile.
 *   Shape: the others are on the theme's highest surface. A [tile] shape makes it one of Split's tiles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaButton(
    tap: WidgetTap,
    icon: Int,
    said: String,
    onTap: ((WidgetTap) -> Unit)?,
    role: Color,
    widthDp: Int = WidgetWords.CONTROL_DP,
    strong: Boolean = false,
    mirrored: Boolean = false,
    size: Modifier? = null,
    tile: Shape? = null,
) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val onClick = { press.click { onTap?.invoke(tap) } }
    val modifier = size ?: Modifier.size(width = widthDp.dp, height = WidgetWords.CONTROL_DP.dp)
    val drawn: @Composable () -> Unit = { Icon(painterResource(icon), said, if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier) }
    val look = LocalWidgetLook.current
    val looks = lookColors(look, role)
    val own = look == WidgetLook.TONAL || look == WidgetLook.SPLIT || look == WidgetLook.SHAPE
    if (strong) {
        val colors = IconButtonDefaults.filledIconButtonColors().let { if (own) it.copy(containerColor = looks.accent, contentColor = looks.onAccent) else it }
            .let { it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor) }
        val shapes = IconButtonDefaults.shapes(shape = tile ?: IconButtonDefaults.mediumSquareShape)
        FilledIconButton(onClick = onClick, shapes = shapes, modifier = modifier, enabled = onTap != null, colors = colors, interactionSource = source, content = drawn)
    } else {
        val container = if (look == WidgetLook.SHAPE) MaterialTheme.colorScheme.surfaceContainerHighest else looks.tile
        val colors = IconButtonDefaults.filledTonalIconButtonColors().let { if (own) it.copy(containerColor = container, contentColor = looks.content) else it }
            .let { it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor) }
        val shapes = if (tile != null) IconButtonDefaults.shapes(shape = tile) else IconButtonDefaults.shapes()
        FilledTonalIconButton(onClick = onClick, shapes = shapes, modifier = modifier, enabled = onTap != null, colors = colors, interactionSource = source, content = drawn)
    }
}

/** Where the song is: "1:05". It reads the time itself, so a tick draws this text again, not the card. */
@Composable
private fun MediaPosition(spec: WidgetSpec.Media, now: State<Long>) {
    Text(WidgetWords.mediaTime(WidgetWords.mediaPosition(spec, now.value)), style = MaterialTheme.typography.labelSmall, color = lookColors().muted, maxLines = 1)
}

/** Where the song is, of how long it is: "2:41 / 5:12", or the position alone when the length is not known. It reads the time itself. */
@Composable
private fun MediaPositionOf(spec: WidgetSpec.Media, now: State<Long>, colour: Color) {
    val at = WidgetWords.mediaTime(WidgetWords.mediaPosition(spec, now.value))
    val text = if (spec.duration > 0) "$at / ${WidgetWords.mediaTime(spec.duration)}" else at
    Text(text, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = colour, maxLines = 1, softWrap = false)
}

/** The height of Split's row of media buttons, as the mockup has it. */
private val SPLIT_MEDIA_ROW = 56.dp

/**
 * Split's media card.
 * - Large: a tile with the card's title and the position at its top, the song and the artist, and
 *   the bar at its foot; under it a row of three tiles: previous, play or pause (wider, in the
 *   card's [role] colour) and next.
 * - Wide: the song's tile at the start and the button tiles beside it, as many as fit.
 * - Small: play or pause is the whole card.
 */
@Composable
private fun SplitMedia(spec: WidgetSpec.Media, box: WidgetSizes.Box, role: Color, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val looks = lookColors(WidgetLook.SPLIT, role)
    val gap = SplitCorners.GAP
    if (box == WidgetSizes.Box.SMALL) {
        MediaButton(TOGGLE, playIcon(spec.playing), playWord(spec.playing), onTap, role, strong = true, size = Modifier.fillMaxSize(), tile = SplitCorners.inColumn(0, 1))
        return
    }
    val title = WidgetWords.mediaTitle(spec)
    val line = WidgetWords.mediaLine(spec)
    val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
    val step by remember(spec) { derivedStateOf { WidgetWords.mediaFraction(spec, now.value)?.let(WidgetWords::barStep) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fraction = step
        val boxHeight = maxHeight
        if (box == WidgetSizes.Box.WIDE) {
            val count = WidgetWords.mediaButtons(maxWidth.value.toInt(), besideText = true)
            val tiles = count + 1
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(SplitCorners.inRow(0, tiles)).background(looks.tile).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(title, style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = looks.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (fraction != null && boxHeight >= TWO_LINES + 16.dp) LevelBar(fraction, moving = spec.playing, role, Modifier.padding(top = 6.dp))
                }
                var at = 1
                if (count >= 3) MediaButton(PREV, R.drawable.ic_skip_next, "Previous", onTap, role, mirrored = true, size = Modifier.width(56.dp).fillMaxHeight(), tile = SplitCorners.inRow(at++, tiles))
                MediaButton(TOGGLE, playIcon(spec.playing), playWord(spec.playing), onTap, role, strong = true, size = Modifier.width(72.dp).fillMaxHeight(), tile = SplitCorners.inRow(at++, tiles))
                if (count >= 2) MediaButton(NEXT, R.drawable.ic_skip_next, "Next", onTap, role, size = Modifier.width(56.dp).fillMaxHeight(), tile = SplitCorners.inRow(at, tiles))
            }
            return@BoxWithConstraints
        }
        val count = WidgetWords.mediaButtons(maxWidth.value.toInt(), besideText = false)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(SplitCorners.tile(topStart = true, topEnd = true, bottomEnd = false, bottomStart = false)).background(looks.tile)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocalUntitled.current?.let {
                        Text(it, style = type.labelSmall.copy(fontWeight = FontWeight.Bold), color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    } ?: Spacer(Modifier.weight(1f))
                    if (spec.duration > 0 || spec.playing) MediaPositionOf(spec, now, looks.muted)
                }
                Text(title, style = type.titleMedium.copy(fontWeight = FontWeight.Bold), color = looks.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                if (fraction != null) LevelBar(fraction, moving = spec.playing, role)
            }
            Row(Modifier.fillMaxWidth().height(SPLIT_MEDIA_ROW), horizontalArrangement = Arrangement.spacedBy(gap)) {
                val bottom = { first: Boolean, last: Boolean -> SplitCorners.tile(topStart = false, topEnd = false, bottomEnd = last, bottomStart = first) }
                if (count >= 3) MediaButton(PREV, R.drawable.ic_skip_next, "Previous", onTap, role, mirrored = true, size = Modifier.weight(1f).fillMaxHeight(), tile = bottom(true, false))
                MediaButton(TOGGLE, playIcon(spec.playing), playWord(spec.playing), onTap, role, strong = true, size = Modifier.weight(1.6f).fillMaxHeight(), tile = bottom(count < 3, count < 2))
                if (count >= 2) MediaButton(NEXT, R.drawable.ic_skip_next, "Next", onTap, role, size = Modifier.weight(1f).fillMaxHeight(), tile = bottom(false, true))
            }
        }
    }
}

/**
 * Shape's media card: play or pause is a big button in a cookie ([PlayShape]), with the song beside
 * it. A wavy bar shows the position while something plays (a wave that stands still: nothing is
 * drawn per frame), a flat one while paused.
 * - Large: the cookie at the start; the card's title, the song, the artist, the bar, and previous
 *   and next with the position beside them. A narrow box has the cookie and the song on top.
 * - Wide: the cookie, the song, and previous and next at the end where they fit.
 * - Small: the cookie alone.
 */
@Composable
private fun ShapeMedia(spec: WidgetSpec.Media, box: WidgetSizes.Box, role: Color, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val looks = lookColors(WidgetLook.SHAPE, role)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        if (box == WidgetSizes.Box.SMALL) {
            PlayShape(spec.playing, minOf(maxWidth, maxHeight), role, onTap)
            return@BoxWithConstraints
        }
        val title = WidgetWords.mediaTitle(spec)
        val line = WidgetWords.mediaLine(spec)
        val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
        val step by remember(spec) { derivedStateOf { WidgetWords.mediaFraction(spec, now.value)?.let(WidgetWords::barStep) } }
        val fraction = step
        val bar: @Composable (Modifier) -> Unit = { m -> if (fraction != null) LevelBar(fraction, moving = spec.playing, role, m) }
        val prevNext: @Composable () -> Unit = {
            MediaButton(PREV, R.drawable.ic_skip_next, "Previous", onTap, role, mirrored = true)
            MediaButton(NEXT, R.drawable.ic_skip_next, "Next", onTap, role)
        }
        val untitled = LocalUntitled.current
        val songTitle = type.titleMedium.copy(fontWeight = FontWeight.Bold)
        when {
            box == WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayShape(spec.playing, minOf(boxHeight, 72.dp), role, onTap)
                Column(Modifier.weight(1f)) {
                    Text(title, style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = looks.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (boxHeight >= TWO_LINES + 8.dp) bar(Modifier.padding(top = 6.dp))
                }
                // Previous and next need 100 dp beside the cookie and at least 96 dp of song.
                if (boxWidth >= 72.dp + 96.dp + 124.dp) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { prevNext() }
            }
            maxWidth >= DATED_SIDE -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlayShape(spec.playing, minOf(boxHeight, 104.dp, boxWidth / 3), role, onTap)
                Column(Modifier.weight(1f)) {
                    if (untitled != null) Text(untitled, style = type.labelSmall.copy(fontWeight = FontWeight.Bold), color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(title, style = songTitle, color = looks.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    bar(Modifier.padding(top = 10.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        prevNext()
                        Spacer(Modifier.weight(1f))
                        if (spec.duration > 0 || spec.playing) MediaPositionOf(spec, now, looks.muted)
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayShape(spec.playing, 64.dp, role, onTap)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = looks.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = looks.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                bar(Modifier)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    prevNext()
                    Spacer(Modifier.weight(1f))
                    if (spec.duration > 0 || spec.playing) MediaPositionOf(spec, now, looks.muted)
                }
            }
        }
    }
}

/**
 * Shape's play or pause: a filled button [side] across in the card's [role] colour. Playing, it is
 * a cookie; paused, a rounded square. A change morphs one into the other, as Material's expressive
 * player does, and only the drawing runs again while it does. Its icon is two fifths of its size.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayShape(playing: Boolean, side: Dp, role: Color, onTap: ((WidgetTap) -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val looks = lookColors(WidgetLook.SHAPE, role)
    val paused = animateFloatAsState(if (playing) 0f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "play")
    val side48 = side.coerceAtLeast(CONTROL_MIN)
    val colors = IconButtonDefaults.filledIconButtonColors().copy(
        containerColor = Color.Transparent,
        contentColor = looks.onAccent,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = looks.onAccent,
    )
    FilledIconButton(
        onClick = { press.click { onTap?.invoke(TOGGLE) } },
        shapes = IconButtonDefaults.shapes(shape = CircleShape),
        modifier = Modifier.size(side48).morphFill(PLAY_MORPH, looks.accent) { paused.value },
        enabled = onTap != null,
        colors = colors,
        interactionSource = source,
    ) {
        Icon(painterResource(playIcon(playing)), playWord(playing), Modifier.size(side48 * 0.4f))
    }
}
