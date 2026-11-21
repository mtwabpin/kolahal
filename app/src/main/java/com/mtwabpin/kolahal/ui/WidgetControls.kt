/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

// ---- shared by buttons and toggles ----------------------------------------------------------

/** A button or a switch grows with its cell up to this height. A taller cell leaves room around it. */
private val MAX_CONTROL = 64.dp

/** The widest a button or a switch gets: one switch on a 4×1 card sits in the middle instead of stretching across it. */
private val MAX_CONTROL_WIDTH = 200.dp

/** From this width, a button has its icon and its label side by side. */
private val SIDE_BY_SIDE = 104.dp

/** From this height, a button has its icon over its label, or a label of two lines. */
internal val TWO_LINES = 56.dp

/**
 * What a Material button inside a card does with a press that is held. A Material button takes the
 * press before the card does and has no long-press of its own. So a long-press on a card full of
 * buttons would do nothing.
 * - [held] is true after a press was held for the long-press time. The card's long-press has run
 *   then, and the click that follows the release must do nothing: [click] swallows it.
 */
internal class CardPress {
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
internal fun cardPress(source: InteractionSource): CardPress {
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
private fun Modifier.tapLabel(label: String?): Modifier =
    if (label == null) this else semantics { onClick(label = label, action = null) }

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
private fun ControlGrid(
    count: Int,
    maxColumns: Int,
    cell: @Composable (index: Int, width: Dp, height: Dp) -> Unit
) {
    if (count <= 0) {
        QuietLine(WidgetWords.NOTHING)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val grid =
            WidgetWords.grid(count, maxWidth.value.toInt(), maxHeight.value.toInt(), maxColumns)
        val gap = WidgetWords.CONTROL_GAP_DP.dp
        val width =
            ((maxWidth - gap * (grid.columns - 1)) / grid.columns).coerceAtMost(MAX_CONTROL_WIDTH)
        val rowWidth = width * grid.columns + gap * (grid.columns - 1)
        val height = ((maxHeight - gap * (grid.rows - 1)) / grid.rows).coerceIn(
            WidgetWords.CONTROL_DP.dp,
            MAX_CONTROL
        )
        val all = height * grid.rows + gap * (grid.rows - 1)
        // Measured with no limit on its height, and kept at the top when it is taller than the box.
        Column(
            Modifier
                .width(rowWidth)
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(all),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            for (row in 0 until grid.rows) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(height),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    for (column in 0 until grid.columns) {
                        val index = row * grid.columns + column
                        // An empty cell keeps the last row's buttons as wide as the others.
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) { if (index < grid.shown) key(index) { cell(index, width, height) } }
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
private fun ControlContent(
    icon: String?,
    label: String,
    width: Dp,
    height: Dp,
    state: String? = null,
    working: Boolean = false,
    iconColour: Color? = null
) {
    val lead: (@Composable () -> Unit)? = when {
        working -> {
            { WorkingSign() }
        }

        icon != null -> {
            {
                WidgetIconText(
                    icon,
                    MaterialTheme.typography.titleMedium,
                    colour = iconColour ?: LocalContentColor.current
                )
            }
        }

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
            Column(
                Modifier.weight(1f, fill = false),
                horizontalAlignment = if (shownState != null) Alignment.Start else Alignment.CenterHorizontally
            ) {
                ControlLabel(
                    label,
                    lines = 1,
                    textAlign = if (shownState != null) TextAlign.Start else TextAlign.Center
                )
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
        if (cardSeen()) LoadingIndicator(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {},
            color = LocalContentColor.current
        )
        else Text("…", style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

private val LABEL_SIZE =
    TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 1.sp)

@Composable
private fun ControlLabel(
    label: String,
    lines: Int,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center
) {
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
internal val CONTROL_MIN = WidgetWords.CONTROL_DP.dp

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
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Circle,
        MaterialShapes.SoftBurst,
        MaterialShapes.Square,
    )
}

/** A button's shape into the one it takes while pressed: a circle, or a square for a circle. Made once for each shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun pressMorph(shape: RoundedPolygon): Morph = PRESS_MORPHS.getOrPut(shape) {
    Morph(
        shape,
        if (shape == MaterialShapes.Circle) MaterialShapes.Square else MaterialShapes.Circle
    )
}

private val PRESS_MORPHS = HashMap<RoundedPolygon, Morph>()

/** A Shape switch: a circle while off into a cookie while on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val TOGGLE_MORPH: Morph by lazy {
    Morph(
        MaterialShapes.Circle,
        MaterialShapes.Cookie12Sided
    )
}

/** Shape's play or pause: a cookie while playing into a rounded square while paused. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val PLAY_MORPH: Morph by lazy { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Square) }

/**
 * Fills the space behind with [morph] at [progress] (0 its first shape, 1 its second), centred and
 * stretched to the size, as `toShape` draws a Material shape. The colour goes from [from] to [to]
 * with it. The progress is read while drawing: a morph that runs draws again and composes nothing,
 * and the path is made again only when the progress or the size has changed. With an [image], the
 * shape is the square picture stretched to the size and darkened ([ART_SCRIM]), not the colour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Modifier.morphFill(
    morph: Morph,
    from: Color,
    to: Color = from,
    image: ImageBitmap? = null,
    progress: () -> Float
): Modifier = drawWithCache {
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
        if (image == null) {
            drawPath(path, if (from == to) from else lerp(from, to, p.coerceIn(0f, 1f)))
        } else {
            clipPath(path) {
                drawImage(
                    image,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                )
                drawRect(ART_SCRIM)
            }
        }
    }
}

/**
 * The grid of Split and Shape: as [WidgetWords.grid], but three or more in a box tall enough for
 * two rows go in two rows. A 2×2 card then has tiles of a sensible shape, not three tall thin ones.
 */
private fun lookGrid(count: Int, widthDp: Int, heightDp: Int, maxColumns: Int): WidgetWords.Grid {
    val twoRows = count >= 3 && heightDp >= 2 * WidgetWords.CONTROL_DP + WidgetWords.CONTROL_GAP_DP
    return WidgetWords.grid(
        count,
        widthDp,
        heightDp,
        if (twoRows) minOf(maxColumns, (count + 1) / 2) else maxColumns
    )
}

/**
 * Split's grid of buttons or switches:[count] tiles that fill the box, 4 dp apart, with 28 dp
 * corners where they touch the card's edge and 8 dp inside ([SplitCorners.inGrid]). The rows are
 * as [WidgetWords.grid] lays them out; a short last row has wider tiles, so the grid stays a
 * rectangle. [cell] draws item `index` in its `tile` shape, `width` by `height`.
 */
@Composable
private fun SplitGrid(
    count: Int,
    maxColumns: Int,
    cell: @Composable (index: Int, tile: Shape, width: Dp, height: Dp) -> Unit
) {
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(height),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    for (column in 0 until inRow) {
                        val index = first + column
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            key(index) {
                                cell(
                                    index,
                                    SplitCorners.inGrid(index, grid.shown, grid.columns),
                                    width,
                                    height
                                )
                            }
                        }
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
private fun ShapeGrid(
    count: Int,
    maxColumns: Int,
    cell: @Composable (index: Int, width: Dp, height: Dp) -> Unit
) {
    if (count <= 0) {
        QuietLine(WidgetWords.NOTHING)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val grid = lookGrid(count, maxWidth.value.toInt(), maxHeight.value.toInt(), maxColumns)
        val gap = WidgetWords.CONTROL_GAP_DP.dp
        val width = (maxWidth - gap * (grid.columns - 1)) / grid.columns
        val height = ((maxHeight - gap * (grid.rows - 1)) / grid.rows).coerceIn(
            CONTROL_MIN,
            SHAPE_CONTROL + LABEL_UNDER + 8.dp
        )
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until grid.rows) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(height),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    for (column in 0 until grid.columns) {
                        val index = row * grid.columns + column
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) { if (index < grid.shown) key(index) { cell(index, width, height) } }
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
        WidgetLook.SPLIT -> SplitGrid(
            spec.items.size,
            maxColumns = 4
        ) { index, tile, width, height ->
            val button = spec.items[index]
            CardButton(
                button,
                (button.color ?: spec.base.color)?.let { colourOf(it) },
                width,
                height,
                onTap,
                tile
            )
        }

        WidgetLook.SHAPE -> ShapeGrid(spec.items.size, maxColumns = 4) { index, width, height ->
            val button = spec.items[index]
            // With no colour of their own, the shapes take the theme's three colours in turn, as the mockup has them.
            val role = (button.color ?: spec.base.color)?.let { colourOf(it) } ?: shapeRole(index)
            ShapeButton(button, role, index, width, height, onTap)
        }

        else -> ControlGrid(spec.items.size, maxColumns = 4) { index, width, height ->
            val button = spec.items[index]
            CardButton(
                button,
                (button.color ?: spec.base.color)?.let { colourOf(it) },
                width,
                height,
                onTap
            )
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
private fun CardButton(
    button: WidgetSpec.Button,
    colour: Color?,
    width: Dp,
    height: Dp,
    onTap: ((WidgetTap) -> Unit)?,
    tile: Shape? = null
) {
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
        modifier = Modifier
            .fillMaxSize()
            .tapLabel(button.tap.label),
        enabled = onTap != null,
        colors = tonal.copy(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        ),
        contentPadding = CONTROL_PADDING,
        interactionSource = source,
    ) {
        ControlContent(
            button.icon,
            button.label,
            width,
            height,
            iconColour = if (plain) null else colour ?: roleColour(null)
        )
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
private fun ShapeButton(
    button: WidgetSpec.Button,
    role: Color,
    index: Int,
    width: Dp,
    height: Dp,
    onTap: ((WidgetTap) -> Unit)?
) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val pressed by source.collectIsPressedAsState()
    val morphed = animateFloatAsState(
        if (pressed) 1f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "press"
    )
    val morph = remember(index) { pressMorph(BUTTON_SHAPES[index % BUTTON_SHAPES.size]) }
    val fill = roleContainer(role)
    val labelled = button.icon != null && height >= CONTROL_MIN + LABEL_UNDER
    // A label inside the shape needs a bigger one: as big as the cell, up to 80 dp.
    val most = if (button.icon == null) SHAPE_TEXT_CONTROL else SHAPE_CONTROL
    val side = minOf(
        most,
        width,
        if (labelled) height - LABEL_UNDER else height
    ).coerceAtLeast(CONTROL_MIN)
    val colors = ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        contentColor = role,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = role
    )
    Button(
        onClick = { press.click { onTap?.invoke(button.tap) } },
        shapes = ButtonDefaults.shapes(shape = SHAPE_ITEM),
        modifier = Modifier
            .fillMaxSize()
            .tapLabel(button.tap.label),
        enabled = onTap != null,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
        interactionSource = source,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(side)
                    .morphFill(morph, fill) { morphed.value },
                contentAlignment = Alignment.Center
            ) {
                val icon = button.icon
                if (icon != null) {
                    // Without its label under it, the shape says the label to a screen reader.
                    val said = if (labelled) Modifier else Modifier.semantics {
                        contentDescription = button.label
                    }
                    WidgetIconText(
                        icon,
                        MaterialTheme.typography.titleMedium,
                        colour = role,
                        size = 22.dp,
                        modifier = said
                    )
                } else {
                    ControlLabel(
                        button.label,
                        lines = 2,
                        modifier = Modifier.padding(horizontal = side / 6)
                    )
                }
            }
            if (labelled) {
                Text(
                    button.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .height(LABEL_UNDER)
                        .wrapContentHeight(),
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
 *   with the same arguments does nothing here: the view model would hold it until the 2 seconds are
 *   over and run it then ([com.mtwabpin.kolahal.terminal.TapGate.runOrWait]), and a switch flips
 *   once for a quick double tap, not twice.
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
        WidgetLook.SPLIT -> SplitGrid(
            spec.items.size,
            maxColumns = 6
        ) { index, tile, width, height ->
            CardToggle(
                spec.items[index],
                working == index,
                width,
                height,
                clickOf(index),
                role,
                tile
            )
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
private fun CardToggle(
    toggle: WidgetSpec.Toggle,
    working: Boolean,
    width: Dp,
    height: Dp,
    onClick: (() -> Unit)?,
    cardRole: Color,
    tile: Shape? = null
) {
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
    val semantics = Modifier
        .fillMaxSize()
        .tapLabel(toggle.tap.label)
        .semantics {
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
private fun RowScope.SplitToggleContent(
    toggle: WidgetSpec.Toggle,
    state: String,
    width: Dp,
    height: Dp,
    working: Boolean,
    looks: LookColors
) {
    if (width < SIDE_BY_SIDE && height < SPLIT_TOGGLE_TALL) {
        ControlContent(toggle.icon, toggle.label, width, height, state = state, working = working)
        return
    }
    val type = MaterialTheme.typography
    Column(
        Modifier
            .weight(1f)
            .padding(start = 4.dp), verticalArrangement = Arrangement.Center
    ) {
        if (height >= SPLIT_TOGGLE_TALL) {
            when {
                working -> WorkingSign()
                toggle.icon != null -> WidgetIconText(toggle.icon, type.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(
            toggle.label,
            style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
private fun ShapeToggle(
    toggle: WidgetSpec.Toggle,
    working: Boolean,
    width: Dp,
    height: Dp,
    onClick: (() -> Unit)?,
    cardRole: Color
) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val scheme = MaterialTheme.colorScheme
    val looks = lookColors(WidgetLook.SHAPE, cardRole)
    val on = animateFloatAsState(
        if (toggle.on) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "toggle"
    )
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
        modifier = Modifier
            .tapLabel(toggle.tap.label)
            .semantics {
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
            Box(
                Modifier
                    .size(CONTROL_MIN)
                    .morphFill(
                        TOGGLE_MORPH,
                        scheme.surfaceContainerHighest,
                        looks.accent
                    ) { on.value },
                contentAlignment = Alignment.Center
            ) {
                val icon = toggle.icon
                when {
                    working -> CompositionLocalProvider(LocalContentColor provides tint) { WorkingSign() }
                    icon != null -> WidgetIconText(
                        icon,
                        MaterialTheme.typography.titleMedium,
                        colour = tint
                    )

                    else -> Text(
                        WidgetWords.badgeLetter(toggle.label).orEmpty(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = tint,
                        modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
        if (under) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                shape()
                Text(
                    toggle.label,
                    style = label,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .height(LABEL_UNDER)
                        .widthIn(max = width - 12.dp)
                        .wrapContentHeight()
                )
            }
            return@ToggleButton
        }
        shape()
        if (beside) {
            // A long label takes two lines beside the shape: "Do not" over "disturb".
            Column(
                Modifier
                    .padding(start = 8.dp)
                    .widthIn(max = width - CONTROL_MIN - 12.dp)
            ) {
                Text(
                    toggle.label,
                    style = label.copy(lineHeight = 15.sp),
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
