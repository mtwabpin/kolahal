/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.terminal.WidgetFeed
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetDrag
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetPlacement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * A widget held after a long-press, and moved by the finger. Positions are in pixels on the page,
 * from its top left; [window] is the finger in the window.
 */
internal class Dragged(val key: String, val from: Offset, val start: Offset, scroll: Int) {
    /** Where the finger was at the last touch event. */
    var finger by mutableStateOf(start)

    /** How far the page was scrolled then. When it scrolls while the finger rests, the page moves under it. */
    var scrollAt by mutableIntStateOf(scroll)
    var window by mutableStateOf(Offset.Zero)

    /** How far the widget has moved from where it was lifted, with the page scrolled to [scroll]. */
    fun moved(scroll: Int): Offset = finger - start + Offset(0f, (scroll - scrollAt).toFloat())
}

/** Where a dragged widget [w] lands if it is dropped now. */
private data class Drop(val w: PlacedWidget, val column: Int, val topDp: Int)

/**
 * The widgets of the page, where they are, to arrange: [panel] in reading order, from [all].
 * - A tap on a widget chooses it ([onSelect]); a tap between widgets puts the toolbar away.
 * - The chosen one ([selected]) has an outline and handles. A drag on a handle resizes it, live
 *   ([WidgetHost.resizeTo]): [resizing] holds its key meanwhile.
 * - A long-press lifts a widget ([dragged]). It follows the finger, a dashed box shows where it
 *   lands, the widgets in the way move down to where they would go, and the page scrolls when the
 *   finger is near the top or bottom of [shown]. When it is let go, it moves there
 *   ([WidgetHost.moveTo]), or on the Remove target ([overRemove]) [onRemove] asks first.
 * - Each widget has actions for a screen reader: Move up, Move down, Move left, Move right, Wider,
 *   Narrower, Taller, Shorter, what the widget itself holds ([editOf]: Options, Edit shortcuts or
 *   Widget settings, as in its toolbar) and Remove.
 * - The chosen widget is scrolled into view, clear of the bars ([topRoom], [bottomRoom]), when it is
 *   chosen, when the screen comes back to it, and each time it moves or changes size.
 * - With [onEdge] (more than one page), a widget held at the screen's start or end edge asks for the
 *   page before or after, one each [EDGE_HOLD_MS] while it stays there. [onEdge] moves it there and
 *   shows that page; the drag goes on, and the widget lands where it is let go.
 */
@Composable
internal fun ArrangedPage(
    widgets: WidgetHost,
    all: List<PlacedWidget>,
    panel: List<PlacedWidget>,
    selected: String?,
    onSelect: (String?) -> Unit,
    dragged: Dragged?,
    onDrag: (Dragged?) -> Unit,
    resizing: String?,
    onResize: (String?) -> Unit,
    page: ScrollState,
    shown: () -> Rect,
    overRemove: () -> Boolean,
    labelOf: (PlacedWidget) -> String,
    profiles: (PlacedWidget) -> ProfileKind?,
    feed: WidgetFeed,
    providersVersion: Int,
    topRoom: Dp,
    bottomRoom: Dp,
    onRemove: (PlacedWidget) -> Unit,
    editOf: (PlacedWidget) -> ToolbarEdit?,
    onEdge: ((key: String, step: Int) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    var pageWidth by remember { mutableIntStateOf(0) }
    val gapPx = with(density) { WidgetLayout.GAP_DP.dp.roundToPx() }
    val grid = remember(pageWidth, gapPx, density.density, rtl) {
        WidgetDrag(
            pageWidth,
            gapPx,
            density.density,
            rtl
        )
    }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Where the held widget lands if it is let go now, and where the others would go then. It goes
    // at most just under the lowest other widget: otherwise, held at the bottom, the page would grow
    // and scroll for ever.
    val lowest = remember(
        panel,
        dragged?.key
    ) { WidgetPlacement.pageHeightDp(panel.filter { it.key != dragged?.key }) + WidgetLayout.GAP_DP }
    // Dropped near another widget's top or bottom, the held widget's top goes onto that line.
    val lines = remember(panel, dragged?.key) { WidgetPlacement.lines(panel, dragged?.key) }

    fun dropAt(d: Dragged, w: PlacedWidget, g: WidgetDrag): Drop {
        val at = d.from + d.moved(page.value)
        return Drop(w, g.dropColumn(at.x, w.span), g.dropTopDp(at.y, lines).coerceAtMost(lowest))
    }

    val drop by remember(panel, grid, dragged, lowest) {
        derivedStateOf {
            val d = dragged ?: return@derivedStateOf null
            val w = panel.firstOrNull { it.key == d.key } ?: return@derivedStateOf null
            dropAt(d, w, grid)
        }
    }
    val preview = remember(all, drop) {
        drop?.let { d ->
            WidgetPlacement.moveTo(
                all,
                d.w.key,
                d.column,
                d.topDp
            ).associateBy { it.key }
        }
    }
    // A tick each time the landing place moves to another column or row of the page.
    val rowDp = remember(pageWidth) { widgets.rowDp() + WidgetLayout.GAP_DP }
    val landing = drop?.let { it.column to it.topDp / rowDp }
    var ticked by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(landing) {
        if (landing != null && ticked != null && landing != ticked) haptics.performHapticFeedback(
            HapticFeedbackType.SegmentTick
        )
        ticked = landing
    }
    // While a widget is held near the top or bottom of the page, the page scrolls. Not while it is over Remove.
    val edge = with(density) { 64.dp.toPx() }
    val step = with(density) { 12.dp.toPx() }
    val latestShown by rememberUpdatedState(shown)
    val latestOverRemove by rememberUpdatedState(overRemove)
    LaunchedEffect(dragged) {
        val d = dragged ?: return@LaunchedEffect
        // Each frame while it is there, until the page can scroll no further. Nothing waits for frames otherwise.
        snapshotFlow {
            if (latestOverRemove()) 0f else latestShown().let {
                WidgetDrag.edgeScroll(
                    d.window.y,
                    it.top,
                    it.bottom,
                    edge,
                    step
                )
            }
        }
            .collectLatest { by ->
                while (by != 0f && page.scrollBy(by) != 0f) withFrameNanos { }
            }
    }

    // Held at the screen's start or end edge: the page before or after, one at a time while it stays.
    // -1 is the page before, as the tabs run from the start edge. Not while it is over Remove.
    val sides = with(density) { 32.dp.toPx() }
    val screenWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val latestEdge by rememberUpdatedState(onEdge)
    LaunchedEffect(dragged) {
        val d = dragged ?: return@LaunchedEffect
        snapshotFlow {
            val x = d.window.x
            val toStart = if (rtl) 1 else -1
            when {
                latestEdge == null || latestOverRemove() -> 0
                x < sides -> toStart
                x > screenWidth - sides -> -toStart
                else -> 0
            }
        }.collectLatest { step ->
            if (step == 0) return@collectLatest
            while (true) {
                delay(EDGE_HOLD_MS)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                latestEdge?.invoke(d.key, step)
            }
        }
    }

    val latestPanel by rememberUpdatedState(panel)
    val latestGrid by rememberUpdatedState(grid)
    val latestDropAt by rememberUpdatedState { d: Dragged, w: PlacedWidget -> dropAt(d, w, grid) }
    val latestSelected by rememberUpdatedState(selected)
    val touch = with(density) { 48.dp.toPx() }
    val guides = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val screenPx = LocalWindowInfo.current.containerSize.height.toFloat()
    Box(
        Modifier
            .fillMaxWidth()
            // Room for the chosen widget's outline under the top bar.
            .padding(top = 8.dp)
            .onSizeChanged {
                pageWidth = it.width
                // The page width in dp: Square, the sizes and the rows are based on it, as on the widgets page.
                widgets.measuredPageDp = with(density) { it.width.toDp().value.toInt() }
            }
            .onGloballyPositioned { coords = it }
            // While a widget is held, the twelve columns show, down to the bottom of the screen.
            .then(if (dragged != null) Modifier.columnGuides(grid, guides, screenPx) else Modifier)
            .pointerInput(Unit) {
                // On the Initial pass, before the cards: a handle and a held widget take the touch
                // from the card under it and from the scrolling page.
                awaitEachGesture {
                    val down =
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val g = latestGrid
                    val chosenNow = latestPanel.firstOrNull { it.key == latestSelected }
                    val handle =
                        chosenNow?.let { g.handleAt(it, down.position.x, down.position.y, touch) }
                    if (chosenNow != null && handle != null) {
                        down.consume()
                        onResize(chosenNow.key)
                        widgets.startResize()
                        try {
                            val minSpan = widgets.minSpanOf(chosenNow)
                            var last = chosenNow.span to chosenNow.heightDp
                            while (true) {
                                val change =
                                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                        ?: break
                                change.consume()
                                if (!change.pressed) break
                                val by = change.position - down.position
                                val span =
                                    if (handle == WidgetDrag.Handle.BOTTOM) chosenNow.span else g.resizeSpan(
                                        chosenNow.column,
                                        chosenNow.span,
                                        by.x,
                                        minSpan
                                    )
                                val height =
                                    if (handle == WidgetDrag.Handle.END) chosenNow.heightDp else g.resizeHeightDp(
                                        chosenNow.heightDp,
                                        by.y
                                    )
                                if (span to height != last) {
                                    if (span != last.first) haptics.performHapticFeedback(
                                        HapticFeedbackType.SegmentTick
                                    )
                                    last = span to height
                                    widgets.resizeTo(chosenNow.key, span, height)
                                }
                            }
                        } finally {
                            widgets.endResize()
                            onResize(null)
                        }
                        return@awaitEachGesture
                    }
                    val w = g.widgetAt(latestPanel, down.position.x, down.position.y)
                    if (w == null) {
                        // A tap between widgets puts the toolbar away.
                        if (waitForUpOrCancellation() != null) onSelect(null)
                        return@awaitEachGesture
                    }
                    // Held without moving for a long-press: the widget is lifted. Moved or let go first: a scroll or a tap.
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change =
                                awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                    ?: break
                            if (!change.pressed || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        }
                        false
                    } ?: true
                    if (!held) return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val d = Dragged(
                        w.key,
                        Offset(g.leftPx(w.column, w.span).toFloat(), g.px(w.topDp)),
                        down.position,
                        page.value
                    )
                    d.window = coords?.localToWindow(down.position) ?: Offset.Zero
                    onDrag(d)
                    var dropped = false
                    try {
                        while (true) {
                            val change =
                                awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                    ?: break
                            change.consume()
                            d.finger = change.position
                            d.scrollAt = page.value
                            coords?.let { d.window = it.localToWindow(change.position) }
                            if (!change.pressed) {
                                dropped = true
                                break
                            }
                        }
                    } finally {
                        if (dropped) {
                            if (latestOverRemove()) {
                                onRemove(w)
                            } else {
                                val p = latestDropAt(d, w)
                                widgets.moveTo(w.key, p.column, p.topDp)
                            }
                            onSelect(w.key)
                        }
                        onDrag(null)
                    }
                }
            },
    ) {
        // Room under the lowest widget, for the lower half of its bottom handle.
        WidgetGrid(
            WidgetLayout.GAP_DP.dp, Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Room the bars leave, read when it scrolls, not when the scroll starts: the toolbar comes up as a widget is chosen.
            val topRoomPx by rememberUpdatedState(with(density) { (topRoom + 8.dp).toPx() })
            val bottomRoomPx by rememberUpdatedState(with(density) { (bottomRoom + 8.dp).toPx() })
            panel.forEach { w ->
                key(w.key) {
                    val lifted = dragged?.key == w.key
                    // The others show where they would go if the held widget were let go now.
                    val target = preview?.get(w.key)?.takeIf { !lifted }?.topDp ?: w.topDp
                    val top by animateIntAsState(
                        target,
                        if (dragged != null) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
                        label = "top"
                    )
                    val bring = remember { BringIntoViewRequester() }
                    var size by remember { mutableStateOf(IntSize.Zero) }
                    // The chosen widget is scrolled into view when it is chosen (the toolbar coming up
                    // could hide it) and again each time it moves or changes size: in a long page it
                    // would go off the screen. Also when the screen comes back from the card's settings
                    // page, as the chosen card is composed again. After a frame, so its new place and
                    // the toolbar are laid out first. Not while a handle is held: the page would move
                    // under the finger.
                    if (selected == w.key && dragged == null && resizing == null) LaunchedEffect(
                        w.column,
                        w.topDp,
                        w.span,
                        w.heightDp
                    ) {
                        withFrameNanos { }
                        bring.bringIntoView(
                            Rect(
                                0f,
                                -topRoomPx,
                                size.width.toFloat(),
                                size.height + bottomRoomPx
                            )
                        )
                    }
                    val minHeight = remember(w.key, providersVersion) { widgets.minHeightOf(w) }
                    val minSpan = remember(w.key, providersVersion) { widgets.minSpanOf(w) }
                    ArrangedCard(
                        modifier = Modifier
                            .gridPlace(w.column, w.span, top)
                            .zIndex(if (lifted) 1f else 0f)
                            .then(
                                if (lifted && dragged != null) Modifier.lifted(
                                    dragged,
                                    page
                                ) else Modifier
                            )
                            .bringIntoViewRequester(bring)
                            .onSizeChanged { size = it },
                        w = w,
                        label = labelOf(w),
                        selected = selected == w.key,
                        lifted = lifted,
                        pausedKind = profiles(w),
                        feed = feed,
                        actions = screenReaderActions(
                            w,
                            widgets,
                            rtl,
                            minHeight,
                            minSpan,
                            editOf(w)
                        ) { onRemove(w) },
                        onSelect = { onSelect(w.key) },
                    )
                }
            }
            // Where the held widget lands.
            drop?.let { d ->
                Box(
                    Modifier
                        .gridPlace(d.column, d.w.span, d.topDp)
                        .height(d.w.heightDp.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            RoundedCornerShape(28.dp)
                        )
                        .dashedBorder(MaterialTheme.colorScheme.primary, 2.dp, 28.dp),
                )
            }
        }
        val marked = panel.firstOrNull { it.key == selected }
        if (marked != null && dragged == null && pageWidth > 0) SelectionMarks(
            marked,
            grid,
            Modifier.matchParentSize()
        )
    }
}

/** A held widget: it follows the finger, raised above the page with a shadow. */
private fun Modifier.lifted(d: Dragged, page: ScrollState): Modifier = graphicsLayer {
    val moved = d.moved(page.value)
    translationX = moved.x
    translationY = moved.y
    shadowElevation = 16.dp.toPx()
    shape = RoundedCornerShape(28.dp)
    clip = false
}

/** The twelve columns of the page, drawn behind it in [color], [below] pixels further down than the page. */
private fun Modifier.columnGuides(grid: WidgetDrag, color: Color, below: Float): Modifier =
    drawBehind {
        repeat(WidgetLayout.COLUMNS) { i ->
            drawRect(
                color,
                Offset(grid.leftPx(i, 1).toFloat(), 0f),
                Size(grid.widthPx(1).toFloat(), size.height + below)
            )
        }
    }

/** A dashed outline [width] wide, with corners [radius] round. */
internal fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
    val line = width.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(line / 2, line / 2),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius(radius.toPx() - line / 2),
        style = Stroke(
            line,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        ),
    )
}

/**
 * What a screen reader can do with [w]: move it a row up or down, a column left or right, make it a
 * column wider or narrower (not under [minSpan]), a step (8 dp) taller or shorter (not under
 * [minHeight]), what it holds ([edit]: the toolbar's Options, Edit shortcuts or Widget settings),
 * and remove it ([onRemove], which asks first). Only what can happen is offered.
 */
private fun screenReaderActions(
    w: PlacedWidget,
    widgets: WidgetHost,
    rtl: Boolean,
    minHeight: Int,
    minSpan: Int,
    edit: ToolbarEdit?,
    onRemove: () -> Unit
): List<CustomAccessibilityAction> {
    fun act(label: String, run: () -> Unit) = CustomAccessibilityAction(label) { run(); true }
    // Columns count from the start edge: on the right in a right-to-left language.
    val left = if (rtl) 1 else -1
    val columns = 0..(WidgetLayout.COLUMNS - w.span)
    return buildList {
        if (w.topDp > 0) add(act("Move up") { widgets.nudge(w.key, 0, -1) })
        add(act("Move down") { widgets.nudge(w.key, 0, 1) })
        if (w.column + left in columns) add(act("Move left") { widgets.nudge(w.key, left, 0) })
        if (w.column - left in columns) add(act("Move right") { widgets.nudge(w.key, -left, 0) })
        if (w.span < WidgetLayout.COLUMNS) add(act("Wider") { widgets.resizeWidth(w.key, 1) })
        if (w.span > minSpan) add(act("Narrower") {
            widgets.resizeWidth(
                w.key,
                -1
            )
        })
        if (w.heightDp < WidgetLayout.MAX_DP) add(act("Taller") {
            widgets.resize(
                w.key,
                WidgetLayout.STEP_DP
            )
        })
        if (w.heightDp > minHeight) add(act("Shorter") {
            widgets.resize(
                w.key,
                -WidgetLayout.STEP_DP
            )
        })
        if (edit != null) add(act(edit.label, edit.onClick))
        add(act("Remove", onRemove))
    }
}

/**
 * The outline of the chosen widget [w], and its handles: a bar on the middle of its end edge and one
 * on the middle of its bottom edge, on the outline. [grid] says where [w] is.
 */
@Composable
private fun SelectionMarks(w: PlacedWidget, grid: WidgetDrag, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val left = grid.leftPx(w.column, w.span).toFloat()
        val right = left + grid.widthPx(w.span)
        val top = grid.px(w.topDp)
        val bottom = top + grid.px(w.heightDp)
        val line = 2.dp.toPx()
        // The outline is 5 dp outside the widget: its line's middle is 4 dp out.
        val out = 4.dp.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(left - out, top - out),
            size = Size(right - left + 2 * out, bottom - top + 2 * out),
            cornerRadius = CornerRadius(32.dp.toPx() - line / 2),
            style = Stroke(line),
        )
        val long = 28.dp.toPx()
        val thick = 6.dp.toPx()
        val round = CornerRadius(3.dp.toPx())
        val end = if (grid.rtl) left - out else right + out
        drawRoundRect(
            color,
            Offset(end - thick / 2, (top + bottom - long) / 2),
            Size(thick, long),
            round
        )
        drawRoundRect(
            color,
            Offset((left + right - long) / 2, bottom + out - thick / 2),
            Size(long, thick),
            round
        )
    }
}

/**
 * One widget as it shows on the widgets page. A tap anywhere on it chooses it ([onSelect]): no tap
 * reaches the widget itself. A card Kolahal draws reads its script from [feed], as it does on the
 * widgets page. [actions] are a screen reader's.
 *
 * A screen reader stops once on it, on the part over it that has its name and [actions]: what the
 * widget shows is hidden from it, and so are an app widget's own buttons ([HostedWidget]'s arranged).
 */
@Composable
private fun ArrangedCard(
    w: PlacedWidget,
    label: String,
    selected: Boolean,
    lifted: Boolean,
    pausedKind: ProfileKind?,
    feed: WidgetFeed,
    actions: List<CustomAccessibilityAction>,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (lifted) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    shape
                ) else Modifier
            )
    ) {
        // Only the part over it speaks to a screen reader: what the widget shows is hidden from it.
        Box(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
        ) {
            if (w.source != null) {
                // A shape chosen in the toolbar settles here as it does on the widgets page.
                CompositionLocalProvider(LocalArranging provides true) {
                    SourceWidget(w, w.heightDp.dp, actions = null, feed = feed)
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HostedWidget(w, w.heightDp.dp, pausedKind, onLongPress = {}, arranged = true) { why ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(w.heightDp.dp)
                                .alpha(0.6f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // A locked private space's widget: its label is the same words as [why].
                                if (pausedKind != ProfileKind.PRIVATE) Text(
                                    label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    why,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                    customActions = actions
                }
                .clickable(onClickLabel = "change $label", onClick = onSelect),
        )
    }
}

/** How long a held widget stays at the screen's edge before the next page shows ([ArrangedPage]'s onEdge). */
internal const val EDGE_HOLD_MS = 700L
