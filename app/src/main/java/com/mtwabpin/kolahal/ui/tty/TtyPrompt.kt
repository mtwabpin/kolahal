/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.KeyBarKey
import com.mtwabpin.kolahal.terminal.tty.HeaderCells
import com.mtwabpin.kolahal.terminal.tty.HomeCells
import com.mtwabpin.kolahal.terminal.tty.PromptCells
import com.mtwabpin.kolahal.ui.theme.LocalBottomCorners
import com.mtwabpin.kolahal.ui.theme.LocalHomePage
import com.mtwabpin.kolahal.ui.theme.ScreenCorners
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.theme.cornerX
import com.mtwabpin.kolahal.ui.theme.windowBottomCorners
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long the block cursor shows, and then doesn't, while it blinks. */
private const val BLINK_MS = 530L

/** Room kept clear past what a rounded corner hides, as the header keeps at the top. */
private val CORNER_GAP = 4.dp

/**
 * What the prompt's keys do, besides typing: the same calls the GUI's prompt makes. Enter submits,
 * ↑ and ↓ go through the history, Tab completes, Esc and Ctrl+U clear, Ctrl+L clears the screen,
 * Ctrl+A and Ctrl+E go to the start and the end.
 */
@Immutable
internal class PromptKeys(
    val onSubmit: () -> Unit,
    val onHistoryUp: () -> Unit,
    val onHistoryDown: () -> Unit,
    val onComplete: () -> Unit,
    val onEscape: () -> Unit,
    val onClearScreen: () -> Unit,
    val onKeyBar: (KeyBarKey) -> Unit,
)

/**
 * The prompt on the grid (`home.style tty`): `$ `, the typed text with a block cursor, and the
 * clear (`✕`) and key bar (`⌨`) buttons, all cells of the grid ([PromptCells]).
 *
 * The typing goes into a real text field under the text's cells, in the grid's font and size and
 * stretched to the grid's cells, with its own text and cursor clear. So keyboards, composition,
 * hardware keys, autofill and the selection's handles work as anywhere else, and the handles sit
 * on the drawn text. The field never scrolls itself: it is as wide as its text and is moved by
 * whole cells to keep the cursor in view, as the drawn text is.
 * - [value], [onValueChange]: the prompt's text; [readOnly] for Settings' preview.
 * - [height]: `prompt.height`; the row is at least as tall as its cells.
 * - [onSwipeUp]: `gesture.swipe-up` on the row, or null when none is set.
 * Where the screen's rounded bottom corners cut the row (the keyboard down, the row at the screen's
 * bottom), the cells they would cut at its ends stay empty, 4 dp clear included, as the header's do
 * at the top ([LocalBottomCorners], [PromptCells.ends]): the `$` and the buttons move in by whole
 * cells, and their targets still reach the screen's edges. A row above the corners keeps its place.
 * The tags are the GUI's: `prompt`, `prompt-quick`, `prompt-clear`, `keybar-toggle`.
 */
@Composable
internal fun TtyPrompt(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    focus: FocusRequester,
    imeSuggestions: Boolean,
    onFocus: (Boolean) -> Unit,
    height: Dp,
    keyBarOn: Boolean,
    keys: PromptKeys,
    onQuick: () -> Unit,
    onToggleKeyBar: () -> Unit,
    onTapPrompt: () -> Unit,
    onSwipeUp: (() -> Unit)?,
) {
    val frame = LocalTtyFrame.current ?: return
    val grid = frame.grid
    val m = grid.metrics
    val density = LocalDensity.current
    val rowH = maxOf(with(density) { height.roundToPx() }, m.cellH + with(density) { 4.dp.roundToPx() })
    var focused by remember { mutableStateOf(false) }
    // The screen's rounded bottom corners: a test's, else the window's, read where the row is placed.
    val view = LocalView.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val givenCorners = LocalBottomCorners.current
    val gap = with(density) { CORNER_GAP.roundToPx() }
    val homePage = LocalHomePage.current
    // The row's place in the window, once it is placed. Its x is on home's page, which slides while
    // home turns to the widgets page (cornerX), so the ends keep their place on the page as it slides.
    var placed by remember { mutableStateOf<IntRect?>(null) }
    // The cells left empty before the `$` and after the buttons. They change only when the row moves
    // into the corners or out, so the keyboard's animation builds the row again only then.
    val ends by remember(givenCorners, view, rtl, frame, rowH, gap) {
        derivedStateOf {
            val at = placed ?: return@derivedStateOf 0 to 0
            val corners = givenCorners ?: windowBottomCorners(view)
            // The rows of the cells, centered in the row.
            val top = at.top + (rowH - m.cellH) / 2
            val bottom = top + m.cellH
            val free = HeaderCells.zones(
                frame.cols,
                frame.originX,
                frame.cellW,
                emptyList(),
                gap,
                cornerLeft = ScreenCorners.hiddenBottomLeft(corners.left, top, bottom, edge = at.left),
                cornerRight = ScreenCorners.hiddenBottomRight(corners.right, top, bottom, edge = at.left + frame.widthPx),
                widthPx = frame.widthPx,
            )
            PromptCells.ends(frame.cols, free, rtl)
        }
    }
    val (lead, trail) = ends
    val text = value.text
    val clear = text.isNotEmpty()
    val width = PromptCells.textCols(frame.cols - lead - trail, clear)
    // The first column of the text that shows: it moves only as far as keeps the cursor in view.
    val last = remember { intArrayOf(0) }
    val first = remember(text, value.selection.end, width) {
        PromptCells.scroll(text, value.selection.end, width, last[0]).also { last[0] = it }
    }
    val selStart = value.selection.start
    val selEnd = value.selection.end
    val comp = value.composition
    val on = remember(text, selStart, selEnd, comp, first, frame, keyBarOn, lead, trail) {
        PromptCells.row(text, selStart, selEnd, cursor = true, first = first, cols = frame.cols, keyBarOn = keyBarOn, compStart = comp?.start ?: -1, compEnd = comp?.end ?: -1, lead = lead, trail = trail)
    }
    val off = remember(text, selStart, selEnd, comp, first, frame, keyBarOn, lead, trail) {
        PromptCells.row(text, selStart, selEnd, cursor = false, first = first, cols = frame.cols, keyBarOn = keyBarOn, compStart = comp?.start ?: -1, compEnd = comp?.end ?: -1, lead = lead, trail = trail)
    }
    // The cursor blinks while the prompt has the focus, and stays on while typing. It stands still
    // when the phone's animations are off, while a dialog or a sheet has the window's focus, and
    // while home is behind another app.
    val context = LocalContext.current
    val still = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(focused, windowFocused, value) {
        blink = true
        if (!focused || !windowFocused || still) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (true) {
                    delay(BLINK_MS)
                    blink = !blink
                }
            } finally {
                blink = true
            }
        }
    }
    val swipeUp by rememberUpdatedState(onSwipeUp)
    var pressed by remember { mutableIntStateOf(-1) }
    val surface = Color(grid.palette.color(Ink.SURFACE))
    val top = (rowH - m.cellH) / 2
    Box(
        Modifier
            .fillMaxWidth()
            .exactHeight(rowH)
            .onPlaced {
                val x = cornerX(it, homePage).roundToInt()
                val y = it.positionInWindow().y.roundToInt()
                placed = IntRect(x, y, x + it.size.width, y + it.size.height)
            }
            .then(
                if (onSwipeUp == null) Modifier else Modifier.pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -48.dp.toPx()) swipeUp?.invoke() },
                    ) { _, dy -> total += dy }
                },
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTapPrompt)
            .drawBehind {
                // Turned around in a right-to-left layout: the `$` on the right, the buttons on the left.
                val cells: HomeCells = (if (focused && blink) on else off).let { if (layoutDirection == LayoutDirection.Rtl) it.mirrored else it }
                cells.spot(pressed)?.let { s ->
                    drawRect(
                        surface,
                        Offset((frame.originX + s.rect.col * m.cellW).toFloat(), top.toFloat()),
                        Size((s.rect.cols * m.cellW).toFloat(), m.cellH.toFloat()),
                    )
                }
                paintCells(grid, cells.grid, frame.originX.toFloat(), top.toFloat())
            },
    ) {
        PromptField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            focus = focus,
            imeSuggestions = imeSuggestions,
            onFocus = { focused = it; onFocus(it) },
            keys = keys,
            frame = frame,
            x = frame.originX + (lead + PromptCells.LEAD) * m.cellW,
            width = width,
            height = rowH,
            first = first,
        )
        for (s in on.spots) {
            val a = when (s.id) {
                PromptCells.QUICK -> SpotAction(onQuick, tag = "prompt-quick", description = "Quick actions", clickLabel = "Open", toStart = true)
                PromptCells.CLEAR -> SpotAction(keys.onEscape, tag = "prompt-clear", description = "Clear", clickLabel = "clear the prompt")
                PromptCells.KEYS -> SpotAction(onToggleKeyBar, tag = "keybar-toggle", description = "Key bar", clickLabel = "toggle key bar", toEnd = true)
                else -> null
            } ?: continue
            val left = if (a.toStart) 0 else frame.originX + s.rect.col * m.cellW
            val right = if (a.toEnd) frame.widthPx else frame.originX + (s.rect.col + s.rect.cols) * m.cellW
            androidx.compose.runtime.key(s.id) {
                SpotNode(a, left, 0, right - left, rowH) { down -> pressed = if (down) s.id else -1 }
            }
        }
    }
}

/**
 * The text field under the prompt's cells: [width] cells of the row from [x], clipped there. The
 * field itself is as wide as its text and more, so it never scrolls on its own; it is moved left by
 * [first] cells, as the drawn text is. Its text, cursor and selection are clear; its handles are in
 * the accent color.
 */
@Composable
private fun PromptField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    focus: FocusRequester,
    imeSuggestions: Boolean,
    onFocus: (Boolean) -> Unit,
    keys: PromptKeys,
    frame: TtyFrame,
    x: Int,
    width: Int,
    height: Int,
    first: Int,
    mask: Boolean = false,
    tag: String = "prompt",
) {
    val m = frame.grid.metrics
    val style = gridTextStyle(frame)
    // Capped: a layout can't be wider than that, and a prompt is never as long.
    val fieldW = ((PromptCells.columnOf(value.text, value.text.length) + width + 2) * m.cellW).coerceAtMost(200_000)
    val handles = TextSelectionColors(handleColor = Color(frame.grid.palette.color(Ink.PROMPT)), backgroundColor = Color.Transparent)
    Box(
        Modifier
            .offset { IntOffset(x, 0) }
            .exactSize(width * m.cellW, height)
            .clipToBounds(),
    ) {
        // Left to right inside, as the drawn text area keeps its order in either layout.
        CompositionLocalProvider(LocalTextSelectionColors provides handles, LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Color.Transparent),
                // A secret's field is a password's, for TalkBack and the keyboard too.
                visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = imeSuggestions && !mask,
                    capitalization = KeyboardCapitalization.None,
                    // The Password type is the reliable way to turn off the keyboard's corrections and suggestions.
                    keyboardType = if (imeSuggestions && !mask) KeyboardType.Text else KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { keys.onSubmit() }),
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(Constraints(minWidth = fieldW, maxWidth = fieldW, maxHeight = height))
                        layout(constraints.maxWidth, height) { p.place(-first * m.cellW, (height - p.height) / 2) }
                    }
                    .testTag(tag)
                    .focusRequester(focus)
                    .onFocusChanged { onFocus(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.DirectionUp -> keys.onHistoryUp()
                            e.key == Key.DirectionDown -> keys.onHistoryDown()
                            e.key == Key.Tab -> keys.onComplete()
                            e.key == Key.Escape -> keys.onEscape()
                            e.key == Key.Enter || e.key == Key.NumPadEnter -> keys.onSubmit()
                            e.isCtrlPressed && e.key == Key.L -> keys.onClearScreen()
                            e.isCtrlPressed && e.key == Key.U -> keys.onEscape()
                            e.isCtrlPressed && e.key == Key.A -> keys.onKeyBar(KeyBarKey.HOME)
                            e.isCtrlPressed && e.key == Key.E -> keys.onKeyBar(KeyBarKey.END)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    },
            )
        }
    }
}

/**
 * The grid's text as a text style: its font and size, each character stretched to exactly a cell
 * (as the painter stretches it), no ligatures, and no color (the cells show the text). A field in
 * it lays its characters out where the grid draws them.
 */
@Composable
internal fun gridTextStyle(frame: TtyFrame): TextStyle {
    val scale = frame.grid.metrics.regular.textScaleX
    return remember(scale, Term.fontFamily, Term.fontSize) {
        TextStyle(
            color = Color.Transparent,
            fontFamily = Term.fontFamily,
            fontSize = Term.fontSize.sp,
            textGeometricTransform = TextGeometricTransform(scaleX = scale),
            fontFeatureSettings = "'liga' 0, 'calt' 0",
        )
    }
}

/**
 * A text field drawn on the grid inside a sheet (a script's secret): [prompt] and the typed text,
 * each character a `•` while [masked], with the same field under the cells as the prompt has.
 * [cols] wide from [x]; tag [tag].
 */
@Composable
internal fun TtyField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    masked: Boolean,
    focus: FocusRequester,
    frame: TtyFrame,
    x: Int,
    cols: Int,
    height: Int,
    prompt: String,
    tag: String,
    onSubmit: () -> Unit,
    onEscape: () -> Unit,
) {
    val m = frame.grid.metrics
    var focused by remember { mutableStateOf(false) }
    val width = (cols - PromptCells.LEAD).coerceAtLeast(1)
    val last = remember { intArrayOf(0) }
    val first = remember(value.text, value.selection.end, width) {
        PromptCells.scroll(value.text, value.selection.end, width, last[0]).also { last[0] = it }
    }
    val on = remember(value, first, cols, masked) {
        PromptCells.row(value.text, value.selection.start, value.selection.end, true, first, cols, false, mask = masked, prompt = prompt, buttons = false)
    }
    val off = remember(value, first, cols, masked) {
        PromptCells.row(value.text, value.selection.start, value.selection.end, false, first, cols, false, mask = masked, prompt = prompt, buttons = false)
    }
    val top = (height - m.cellH) / 2
    Box(
        Modifier
            .fillMaxWidth()
            .exactHeight(height)
            .drawBehind {
                val cells = (if (focused) on else off).let { if (layoutDirection == LayoutDirection.Rtl) it.mirrored else it }
                paintCells(frame.grid, cells.grid, x.toFloat(), top.toFloat())
            },
    ) {
        val keys = remember(onSubmit, onEscape) { PromptKeys(onSubmit, {}, {}, {}, onEscape, {}, {}) }
        PromptField(
            value = value,
            onValueChange = onValueChange,
            readOnly = false,
            focus = focus,
            imeSuggestions = false,
            onFocus = { focused = it },
            keys = keys,
            frame = frame,
            x = x + PromptCells.LEAD * m.cellW,
            width = width,
            height = height,
            first = first,
            mask = masked,
            tag = tag,
        )
    }
}
