package com.mtwabpin.kolahal.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.ProfileKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private enum class Mode { CLOSED, SCRUB, SELECT, OPEN }

/**
 * Whether the selector's edge ([left], else the right) has Android's back swipe. Gesture navigation
 * reports a system gesture inset on that edge; three-button navigation does not.
 */
@Composable
internal fun hasEdgeBackGesture(left: Boolean): Boolean {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    return (if (left) insets.getLeft(density, direction) else insets.getRight(density, direction)) > 0
}

private class SelectorState {
    var mode by mutableStateOf(Mode.CLOSED)
    /** List position (rows) under the finger while it is down; NaN when there is no touch. */
    var contact by mutableFloatStateOf(Float.NaN)
    /** Where the fisheye was last centred (rows, like [contact]). When the finger leaves the list, the fisheye shrinks back at this place. */
    var lensAt by mutableFloatStateOf(Float.NaN)
    /** Scroll offset (px) set by scrubbing. */
    var scrollPx = 0f
    /**
     * Where the list wants to be (px, like [scrollPx]) so that the focused app is level with the
     * finger. Past the ends of the list, the panel itself moves the rest of the way ([shiftPx]).
     */
    var listY = 0f
    /** How far the panel is moved down (up when negative) to do what scrolling could not. */
    var shiftPx by mutableFloatStateOf(0f)
    var interaction by mutableIntStateOf(0)
    /** How full the held row is (0 to 1). It is drawn on the focused row while picking (Plan B). */
    var hold by mutableFloatStateOf(0f)
    /** Extra room at the start and end of the list (px). It keeps a tapped letter's apps where they were ([SelectorMath.keepInPlace]). */
    var padTop by mutableFloatStateOf(0f)
    var padBottom by mutableFloatStateOf(0f)
    /** True when picking on the panel's far side. Lifting the finger there opens the app's actions (`selector.actions far-side`). */
    var far by mutableStateOf(false)
    /**
     * The current letter (its section index), shown in the bubble:
     * - with `selector.browse letters`: the letter the thumb is on along the edge;
     * - with `apps`: the letter of the focused app;
     * - -1 before the first touch.
     */
    var letter by mutableIntStateOf(-1)

    fun close() {
        mode = Mode.CLOSED
        contact = Float.NaN
        hold = 0f
        far = false
        // shiftPx and letter are kept. The panel fades out where it is, as it was, and the next touch
        // places it again.
    }
}

/** How long the fisheye takes to grow or shrink back, and how long the other letters' apps take to fade back or in. */
private const val LENS_MS = 220

/** When the panel is left open, it stops this far from the strip. */
private val OPEN_GAP = 8.dp
/** How the list glides to a new letter's apps while the thumb slides along the edge. */
private val GLIDE = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
/** When the finger is this far past the strip's inner side, it picks from the list instead of sliding along the edge. */
private val LEAVE = 32.dp
private val STRIP_W = 36.dp
private val TOP_GAP = 8.dp
/** Size of the bubble next to the thumb that shows the current letter. */
private val BUBBLE = 40.dp
/** The wave letters next to the bubble sit this far from its middle. */
private val WAVE_ROOM = 38.dp
/** Distance of a letter's middle from the screen edge, at rest. */
private val LETTER_REST = 6.dp
/** Keeps the "jump to latest" button in the log's bottom-right corner reachable. */
private val BOTTOM_GAP = 72.dp
private const val IDLE_MS = 4_000L
private const val FRAME_MS = 16L

private fun LazyListState.scrollToPx(px: Float, rowPx: Int) {
    val index = (px / rowPx).toInt()
    requestScrollToItem(index, (px - index * rowPx).roundToInt())
}

/**
 * App selector on one edge of the log (`selector.rail`, `selector.side`).
 *
 * At rest it is a slim handle on a strip of the edge. The strip is `selector.height` long and sits
 * at the bottom, middle or top of the edge (`selector.position`).
 *
 * When you touch the strip, a panel slides in on the other side, away from the thumb. It uses the
 * whole height and lists every app: favourites first, then A–Z. When the strip is at the bottom of
 * the edge, favourites go last, under the thumb.
 *
 * Sliding along the edge depends on `selector.browse`:
 * - `letters`: every letter gets the same stretch of the strip. Sliding moves one letter at a time,
 *   with one tick per letter. The list glides to the letter's apps and holds still. The first app
 *   is level with the letter's place on the edge, and the other apps are dimmed. With
 *   `selector.filter`, the list shows only that letter's apps.
 * - `apps` (the default): sliding moves through the whole list by app count. The top of the strip
 *   is the first app and the bottom is the last. Each letter gets room by the square root of its
 *   app count. A slow slide aims, one app at a time (`selector.aim`). The focused row is level
 *   with the thumb.
 *
 * In both modes:
 * - The current letter shows big in a bubble next to the thumb. The letters around it bulge out
 *   in a wave.
 * - Past the ends of the list, the panel itself moves to keep the letter's apps (or the focused
 *   row) level with the thumb.
 * - Slide off the edge toward the panel to pick a row. The row and its neighbours are magnified
 *   (fisheye), and the list auto-scrolls at the panel's ends. Lift to launch, or hold still until
 *   the row fills.
 * - Lift on the edge to leave the panel open as a normal list (fling, tap, long-press). A tapped
 *   letter's apps stay next to it. A tap outside, Back, or 4 s idle closes it.
 *
 * The strip consumes all its touches, so the home pager never turns the page under it.
 *
 * With gesture navigation, Android lets an app block the back swipe on at most 200dp of an edge.
 * So a touch starts the selector only in the middle 200dp of the strip, where the handle is drawn
 * at rest. The rest of the edge belongs to Android. Once the finger is down, it can use the whole
 * length of the strip.
 */
@Composable
fun AppSelector(
    apps: List<AppEntry>,
    pinned: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 350,
    /** selector.handle: draw the A–Z handle when idle. */
    showHandle: Boolean = true,
    /** selector.letters: show each letter along the strip where its apps are, so you can see where to touch. */
    letters: Boolean = false,
    /** selector.side: the strip is on the left edge and the panel on the right. */
    left: Boolean = false,
    /** selector.position: the strip is at the edge's `bottom`, `middle` or `top`. */
    position: String = "bottom",
    /** True when this edge has Android's back swipe (gesture navigation). A touch then starts the selector only where the swipe is blocked. */
    edgeBackGesture: Boolean = hasEdgeBackGesture(left),
    /**
     * selector.actions: what lifting on the panel's far side does while picking.
     * - `far-side`: it opens the app's actions.
     * - `off`: nothing (long-press the app in the open list instead).
     * A full hold launches the app in both cases.
     */
    actions: String = "off",
    /** selector.icons: each row starts with the app's icon (Plan E). */
    icons: Boolean = false,
    /** selector.row-height: the height of each row (dp). Rows get taller when the text needs it. */
    rowHeightDp: Int = com.mtwabpin.kolahal.data.Keys.SELECTOR_ROW_HEIGHT.default,
    /** selector.aim: a slow slide moves the list one app per this many dp (apps, Plan C); 0 always glides. */
    aimDp: Int = SelectorMath.AIM_STEP,
    /** selector.aim-speed: slides slower than this (dp per second) aim. */
    aimSpeed: Int = SelectorMath.AIM_SPEED,
    /** selector.browse: `letters` moves one letter at a time along the edge; `apps` moves through the whole list by app. */
    browse: String = "letters",
    /** selector.filter: with letters, the list shows only the current letter's apps. */
    filter: Boolean = false,
    /** selector.system-font: use the phone's font for the list, the letters and the bubble, not the terminal's. */
    systemFont: Boolean = false,
    /** When true, the selector is drawn and takes touches. When false (keyboard up), nothing shows, but the list stays built. */
    active: Boolean = true,
    /** Settings' preview of selector.row-height: the list stays open, as after lifting on the edge. */
    showList: Boolean = false,
    /** Set while the list is open (0 to 1), so home fades out the log behind it. Null: dim home with a scrim instead. */
    fade: androidx.compose.runtime.MutableFloatState? = null,
) {
    val text = if (systemFont) Term.phoneText else Term.text
    // Plan F: when the strip is at the bottom of the edge, favourites go last, where the thumb rests.
    val favouritesLast = position == "bottom"
    val entries = remember(apps, pinned, favouritesLast) {
        SelectorMath.build(
            apps.filter { !it.hidden },
            label = { it.label },
            key = { it.key },
            pinned = { it.key in pinned },
            profileRank = { it.profile.kind.ordinal },
            favouritesLast = favouritesLast,
        )
    }
    val byLetter = browse == "letters"
    val onlyLetter = byLetter && filter
    val starts = remember(entries) { SelectorMath.sectionStarts(entries) }
    val letterCount = starts.size - 1
    // Apps (Plan D): each letter gets room on the strip by the square root of its app count.
    val sections = remember(entries) { SelectorMath.Sections(SelectorMath.sectionSizes(entries)) }
    // Letters: each letter gets the same stretch of the edge, as its own stop.
    val edgeMiddles = remember(letterCount, sections, byLetter) { if (byLetter) SelectorMath.letterMiddles(letterCount) else sections.middles() }
    /** The letter of each section (★, #, A–Z), in list order. Only letters that have apps are included. */
    val sectionLetters = remember(entries) { entries.filter { it.sectionStart }.map { it.section } }
    val state = remember { SelectorState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    /** The list's position (like [SelectorState.listY]) while it glides to a new letter's apps. */
    val glider = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val launch by rememberUpdatedState(onLaunch)
    val longPress by rememberUpdatedState(onLongPress)
    val appContext = LocalContext.current.applicationContext
    val letter = state.letter
    /** What the panel lists: every app, or, with selector.filter, only the current letter's apps. */
    val shown = remember(entries, starts, letter, onlyLetter) {
        if (onlyLetter && letter in 0 until starts.size - 1) entries.subList(starts[letter], starts[letter + 1]) else entries
    }
    // The bubble and the wave come out from the edge while the thumb slides along it.
    val sliding = state.mode == Mode.SCRUB && letter in sectionLetters.indices
    // The fisheye grows while the thumb picks from the list. When the finger lifts or goes back to
    // the edge, the fisheye shrinks back where it was. The other letters' apps fade back and in the
    // same way. Neither one jumps when the selector switches between sliding and the open list.
    val lensOn by remember { derivedStateOf { !state.contact.isNaN() } }
    val lens = animateFloatAsState(if (lensOn) 1f else 0f, tween(LENS_MS), label = "lens")
    val dim = animateFloatAsState(if (state.mode == Mode.SCRUB || state.mode == Mode.SELECT) 1f else 0f, tween(LENS_MS), label = "dim")
    val wave = animateFloatAsState(if (sliding) 1f else 0f, tween(140), label = "wave")
    val waveIn by wave
    // Whether the wave shows at all. The built content changes only when this changes, not on every frame of the growth.
    val waveShown by remember { derivedStateOf { wave.value > 0f } }
    /** Where the wave is centred along the strip (0 top … 1 bottom): the letter's middle. It glides to the next letter's middle. */
    val waveAt = remember { Animatable(0f) }
    LaunchedEffect(letter, sliding, edgeMiddles) {
        val to = edgeMiddles.getOrNull(letter) ?: return@LaunchedEffect
        // The wave comes out from the edge at the letter itself, then glides briefly from letter to letter.
        if (!sliding || waveIn < 0.05f) waveAt.snapTo(to) else waveAt.animateTo(to, spring(stiffness = 2500f))
    }
    val measurer = rememberTextMeasurer()
    val waveStyle = text.copy(fontSize = 9.sp, lineHeight = 10.sp)
    val waveLetters = remember(sectionLetters, waveStyle) { sectionLetters.map { measurer.measure(it.toString(), waveStyle) } }

    BackHandler(enabled = state.mode == Mode.OPEN) { state.close() }
    LaunchedEffect(showList) {
        if (showList) state.mode = Mode.OPEN else if (state.mode == Mode.OPEN) state.close()
    }
    // When left open, the list closes after a few idle seconds (scrolling counts as use). A preview never closes.
    LaunchedEffect(state.mode, state.interaction, showList) {
        if (state.mode != Mode.OPEN || showList) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) {
                delay(IDLE_MS.milliseconds)
                state.close()
            }
        }
    }

    LaunchedEffect(active) { if (!active) state.close() }
    if (!active) return

    // The panel can move past the log's edges to keep a letter's apps, or the focused app, level with the thumb.
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        // selector.height: the strip is only part of the edge, within the thumb's reach (selector.position),
        // not the whole log height. Each letter still has its stretch of the strip (its top is the first
        // letter, or with apps the first app).
        val topPx = with(density) { TOP_GAP.toPx() }
        val bottomPx = containerH - with(density) { BOTTOM_GAP.toPx() }
        val stripHPx = min(bottomPx - topPx, with(density) { heightDp.dp.toPx() })
        val stripTopPx = when (position) {
            "top" -> topPx
            "middle" -> topPx + (bottomPx - topPx - stripHPx) / 2
            else -> bottomPx - stripHPx
        }
        if (stripHPx < with(density) { 160.dp.toPx() }) return@BoxWithConstraints
        val count = entries.size
        // Rows grow with the text. At a large text size, a fixed row height would overlap the next row.
        val rowH = maxOf(rowHeightDp.dp, with(density) { (Term.fontSize * 1.36f).sp.toDp() } + 8.dp)
        val rowPx = with(density) { rowH.roundToPx() }
        // Plan E: a 26dp icon in a 38dp row. The icon grows with the row, up to 32dp.
        val iconSize = minOf(rowH - 12.dp, 32.dp)
        val iconPx = with(density) { iconSize.roundToPx() }.coerceAtLeast(1)
        // Icons load ahead, off the main thread, so a fast glide never shows an empty space where an icon goes.
        if (icons) LaunchedEffect(entries, iconPx, AppIcons.generation) {
            withContext(Dispatchers.IO) { entries.forEach { AppIcons.load(appContext, it.item, iconPx) } }
        }
        // The panel uses the whole height, whatever the strip's height. A short list (or a letter's apps)
        // sits at the bottom until it moves level with the thumb.
        val listHPx = containerH - 2 * topPx
        fun panelH(n: Int) = min(listHPx, (n * rowPx).toFloat()).coerceAtLeast(rowPx.toFloat())
        fun panelTop(n: Int) = topPx + listHPx - panelH(n)
        val panelHPx = panelH(shown.size)
        val panelTopPx = panelTop(shown.size)
        val stripWPx = with(density) { STRIP_W.toPx() }
        // The panel is on the other side from the strip:
        // - while the thumb is down, it is narrower, so the wave and the bubble have room to swing out;
        // - when left open, it reaches nearly to the strip, so a thumb at the edge can reach every row,
        //   including the top ones (a row takes a tap anywhere along it).
        val wideW = maxWidth - STRIP_W - OPEN_GAP
        val narrowW = with(density) { (containerW * 0.55f).toDp() }.coerceAtLeast(minOf(200.dp, wideW)).coerceAtMost(wideW)
        val panelW by animateDpAsState(if (state.mode == Mode.OPEN) wideW else narrowW, tween(160), label = "panel")
        val panelWPx = with(density) { panelW.toPx() }
        val panelXPx = if (left) containerW - panelWPx else 0f
        // Width used while the thumb is down.
        val touchWPx = with(density) { narrowW.toPx() }
        val touchXPx = if (left) containerW - touchWPx else 0f
        val stripXPx = if (left) 0f else containerW - stripWPx
        val exclusionPx = with(density) { 200.dp.toPx() }
        // With gesture navigation, a touch starts the selector only in the middle 200dp of the strip.
        val (zoneTopPx, zoneHPx) = SelectorMath.startZone(stripHPx, exclusionPx, edgeBackGesture)
        // How far from the edge picking starts. A thumb drifts a little while it slides along the edge.
        val leavePx = with(density) { LEAVE.toPx() }
        val slopPx = with(density) { 8.dp.toPx() }
        val edgeZonePx = with(density) { 56.dp.toPx() }
        val edgeMaxSpeed = with(density) { 1.1.dp.toPx() } // px per ms at full depth
        val pxPerDp = density.density
        val aimStepPx = aimDp * pxPerDp
        // The bubble sits between the strip and the list, as far from the edge as the gap allows.
        val bubblePx = with(density) { BUBBLE.toPx() }
        val marginPx = with(density) { 4.dp.toPx() }
        val gapPx = containerW - touchWPx - stripWPx
        val bubbleFromEdge = max(bubblePx / 2 + marginPx, min(stripWPx + gapPx / 2, stripWPx + gapPx - bubblePx / 2 - marginPx))
        val restPx = with(density) { LETTER_REST.toPx() }
        val open = state.mode != Mode.CLOSED

        // While the selector is open, it has the screen: home dims behind it.
        val dimmed = animateFloatAsState(if (open && count > 0) 1f else 0f, tween(160), label = "dim")
        if (fade != null) {
            LaunchedEffect(fade) { snapshotFlow { dimmed.value }.collect { fade.floatValue = it } }
            androidx.compose.runtime.DisposableEffect(fade) { onDispose { fade.floatValue = 0f } }
        }
        val scrim by remember { derivedStateOf { fade == null && dimmed.value > 0f } }
        if (scrim) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dimmed.value }.background(Term.bg.copy(alpha = 0.9f)).testTag("app-selector-scrim"))
        }

        // When left open, a tap anywhere else closes it, and the whole touch is swallowed.
        if (state.mode == Mode.OPEN) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            state.close()
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                                if (e.changes.none { it.pressed }) break
                            }
                        }
                    }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = open && count > 0,
            // Opaque from the first frame (no fade), so it is readable while it slides in.
            enter =
                slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { if (left) it / 4 else -it / 4 },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(160)) { if (left) it / 4 else -it / 4 },
            modifier = Modifier.offset { IntOffset(panelXPx.roundToInt(), (panelTopPx + state.shiftPx).roundToInt()) },
        ) {
            // No card: the rows stand on the dimmed home (the scrim) with no background, border or round corners.
            LazyColumn(
                state = listState,
                userScrollEnabled = state.mode == Mode.OPEN,
                contentPadding = with(density) { PaddingValues(top = state.padTop.toDp(), bottom = state.padBottom.toDp()) },
                modifier = Modifier
                    .width(panelW)
                    .height(with(density) { panelHPx.toDp() })
                    .testTag("app-selector-panel")
                    // An invisible clip. The list already hides rows above and below it (the fisheye moves rows
                    // there). This also keeps anything from drawing past its side, next to the strip.
                    .clipToBounds()
                    // A sideways drag on the open panel must not turn the home page.
                    .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume() } },
            ) {
                itemsIndexed(shown, key = { _, e -> e.key }) { index, entry ->
                    SelectorRow(
                        entry = entry,
                        index = index,
                        count = shown.size,
                        state = state,
                        rowPx = rowPx,
                        height = rowH,
                        pinned = entry.item.key in pinned,
                        icon = if (icons) iconSize else null,
                        enabled = state.mode == Mode.OPEN,
                        // A letter's few apps alone in the list: there is no room to magnify the ends.
                        fisheye = !onlyLetter,
                        lens = { lens.value },
                        dimOthers = byLetter && !onlyLetter,
                        dim = { dim.value },
                        text = text,
                        onClick = { state.close(); launch(entry.item) },
                        onLongClick = { state.close(); longPress(entry.item) },
                    )
                }
            }
        }

        // The whole length of the strip. At rest, either the handle marks where a touch starts, or the
        // letters stand at their places.
        Box(
            Modifier
                .offset { IntOffset(stripXPx.roundToInt(), stripTopPx.roundToInt()) }
                .width(STRIP_W)
                .height(with(density) { stripHPx.toDp() })
                .testTag("app-selector-strip"),
        ) {
            val touching = state.mode == Mode.SCRUB || state.mode == Mode.SELECT
            // selector.letters: each letter sits in the middle of its stretch of the strip, nudged apart where
            // they crowd. Letters outside the part where a touch may start are fainter. At rest they replace
            // the handle, in the edge's gutter, clear of the log's text. Under the thumb, the wave takes over.
            if (letters && !touching && !waveShown && count > 0) {
                val linePx = with(density) { 9.sp.toPx() }
                val gapPx = linePx + with(density) { 1.dp.toPx() }
                val placed = remember(edgeMiddles, stripHPx, gapPx) {
                    SelectorMath.labelPositions(FloatArray(edgeMiddles.size) { edgeMiddles[it] * stripHPx }, gapPx, gapPx / 2, stripHPx - gapPx / 2)
                }
                sectionLetters.forEachIndexed { i, letter ->
                    val p = placed.getOrElse(i) { Float.NaN }
                    if (!p.isNaN()) {
                        val inZone = p >= zoneTopPx && p <= zoneTopPx + zoneHPx
                        Text(
                            letter.toString(),
                            style = text.copy(color = Term.dim.copy(alpha = if (inZone) 0.8f else 0.4f), fontSize = 8.sp, lineHeight = 9.sp),
                            maxLines = 1,
                            modifier = Modifier
                                .align(if (left) Alignment.TopStart else Alignment.TopEnd)
                                .offset { IntOffset(0, (p - linePx / 2).roundToInt()) }
                                .padding(start = if (left) 3.dp else 0.dp, end = if (left) 0.dp else 3.dp)
                                .testTag("app-selector-letter"),
                        )
                    }
                }
            }
            if (showHandle && !letters && !touching) {
                // 64dp in the middle, or with gesture navigation the whole part where a touch may start.
                val handleH = if (edgeBackGesture) with(density) { zoneHPx.toDp() } else 64.dp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(start = if (left) 3.dp else 0.dp, end = if (left) 0.dp else 3.dp)
                        .graphicsLayer { alpha = if (open) 0.35f else 1f },
                ) {
                    val small = text.copy(color = Term.dim.copy(alpha = 0.45f), fontSize = 8.sp, lineHeight = 9.sp)
                    Text("A", style = small)
                    Box(
                        Modifier
                            .padding(vertical = 2.dp)
                            .width(3.dp)
                            .height(handleH)
                            .testTag("app-selector-handle")
                            .background(Term.dim.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                    )
                    Text("Z", style = small)
                }
            }
        }

        // The current letter shows big in a bubble next to the thumb. The letters around it bulge out
        // from the edge in a wave: they grow a little, and the nearest ones are pushed apart to make room.
        // So you see where you are and in which direction the letter you want is.
        if (waveShown && letter in sectionLetters.indices) {
            val unit = stripHPx * (1f - 2 * SelectorMath.DEAD) / sectionLetters.size
            val spread = SelectorMath.waveSpread(unit, with(density) { WAVE_ROOM.toPx() })
            val letterGap = with(density) { 10.dp.toPx() }
            val clear = bubblePx / 2 + with(density) { 6.dp.toPx() }
            val dim = Term.dim
            val fg = Term.fg
            // One pass per frame, in the draw phase only.
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("app-selector-wave")
                    .drawBehind {
                        val ease = waveIn
                        val c = waveAt.value * stripHPx
                        val n = waveLetters.size
                        val ys = FloatArray(n) { SelectorMath.waveY(edgeMiddles[it] * stripHPx, c, unit, spread * ease) }
                        val outs = FloatArray(n) { SelectorMath.waveOut((edgeMiddles[it] * stripHPx - c) / unit) * ease }
                        val scales = FloatArray(n) { 1f + SelectorMath.WAVE_GROW * outs[it] }
                        val drawn = SelectorMath.waveShown(ys, scales, letter, letterGap, clear * ease)
                        for (i in 0 until n) {
                            if (!drawn[i]) continue
                            val fromEdge = restPx + (bubbleFromEdge - restPx) * outs[i]
                            val layout = waveLetters[i]
                            val w = layout.size.width.toFloat()
                            val h = layout.size.height.toFloat()
                            withTransform({
                                translate((if (left) fromEdge else size.width - fromEdge) - w / 2, stripTopPx + ys[i] - h / 2)
                                scale(scales[i], scales[i], pivot = Offset(w / 2, h / 2))
                            }) {
                                drawText(layout, color = lerp(dim, fg, outs[i]), alpha = ease * (0.55f + 0.45f * outs[i]))
                            }
                        }
                    },
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        // Comes out from the edge with the wave.
                        val fromEdge = restPx + (bubbleFromEdge - restPx) * waveIn
                        val cx = if (left) fromEdge else containerW - fromEdge
                        IntOffset((cx - bubblePx / 2).roundToInt(), (stripTopPx + waveAt.value * stripHPx - bubblePx / 2).roundToInt())
                    }
                    .size(BUBBLE)
                    .graphicsLayer { scaleX = waveIn; scaleY = waveIn; alpha = waveIn }
                    .background(Term.prompt, CircleShape),
            ) {
                Text(
                    sectionLetters[letter].toString(),
                    style = text.copy(color = Term.bg, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp),
                    maxLines = 1,
                    modifier = Modifier.testTag("app-selector-bubble"),
                )
            }
        }

        // The area where a touch starts the selector:
        // - the whole strip, or
        // - with gesture navigation, only the middle 200dp, where Android blocks the back swipe. On the
        //   rest of the edge, Android's Back works as usual.
        // It owns every touch it gets, so the home pager never turns the page under it. It follows the
        // finger past its own ends, so the slide uses the whole length of the strip.
        Box(
            Modifier
                .offset { IntOffset(stripXPx.roundToInt(), (stripTopPx + zoneTopPx).roundToInt()) }
                .width(STRIP_W)
                .height(with(density) { zoneHPx.toDp() })
                .testTag("app-selector")
                .semantics { contentDescription = "app selector: slide along the edge to a letter, then onto the list to pick an app" }
                // At most 200dp: Android ignores exclusion beyond 200dp per edge.
                .systemGestureExclusion { c ->
                    val h = c.size.height.toFloat()
                    val top = ((h - exclusionPx) / 2).coerceAtLeast(0f)
                    Rect(0f, top, c.size.width.toFloat(), min(h, top + exclusionPx))
                }
                // Every value the gesture reads that can change, including the width (for example, a multi-window divider moved).
                .pointerInput(entries, rowPx, listHPx, topPx, stripTopPx, stripHPx, zoneTopPx, containerW, left, actions, aimStepPx, aimSpeed, byLetter, onlyLetter) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (count == 0) {
                            do {
                                val pressed = awaitPointerEvent().changes.onEach { it.consume() }.any { it.pressed }
                            } while (pressed)
                            return@awaitEachGesture
                        }
                        var x = down.position.x
                        var y = down.position.y + zoneTopPx
                        var selecting = false
                        var lastSection = -1
                        // The last row that gave a tick (in the list), and the last state that gave a tick (letters or list).
                        var tickedRow = -1
                        var wasSelecting = false
                        var focus = -1
                        var rowSince = SystemClock.uptimeMillis()
                        var anchorY = y
                        var anchorX = x
                        var lastFrame = rowSince
                        val farMode = actions == "far-side"
                        var holdAt = rowSince
                        // Apps (Plan C): the list's position along the edge (rows). A fast slide glides the list to
                        // where the strip maps it; a slow slide aims, one app at a time.
                        var at = sections.along(y / stripHPx)
                        var slideSpeed = -1f // dp a second; unknown until the first move
                        var lastMoveAt = down.uptimeMillis
                        var aiming = false
                        // Letters: the letter the thumb is on, and what the panel lists for it.
                        var onLetter = -1
                        var list = entries
                        // The list's glide to a new letter's apps.
                        var glide: Job? = null

                        // Draws the list [scroll] px down, and moves the panel [shift] px for what scrolling could not do.
                        fun show(scroll: Float, shift: Float) {
                            state.scrollPx = scroll
                            state.shiftPx = shift
                            listState.scrollToPx(scroll, rowPx)
                        }

                        fun update(now: Long) {
                            val cy = stripTopPx + y
                            // A new letter's apps glide in from where the list is drawn now. Off the edge, the list stays
                            // where the letter put it; a glide that is still running ends there at once.
                            var newLetter = false
                            if (selecting) glide?.cancel()
                            if (byLetter && !selecting) {
                                // Along the edge, one letter at a time. The list moves only when the letter changes.
                                val l = SelectorMath.letterAt(SelectorMath.alongLetters(y / stripHPx, letterCount), onLetter, letterCount)
                                if (l != onLetter) {
                                    newLetter = onLetter >= 0
                                    onLetter = l
                                    state.letter = l
                                    list = if (onlyLetter) entries.subList(starts[l], starts[l + 1]) else entries
                                }
                            }
                            val n = list.size
                            val panelH = panelH(n)
                            val panelTop = panelTop(n)
                            // The finger's height in the panel. It can be past the panel's ends when the strip reaches
                            // beyond the panel.
                            val yIn = cy - panelTop
                            if (!selecting) {
                                state.listY = if (byLetter) {
                                    // The letter's apps start level with its place on the edge: the first app is at the
                                    // thumb's height, so every app is a short move away. They move up only as far as needed
                                    // to stay on screen. The list holds still until the letter changes.
                                    val size = starts[onLetter + 1] - starts[onLetter]
                                    val start = if (onlyLetter) 0 else starts[onLetter]
                                    val letterY = stripTopPx + edgeMiddles[onLetter] * stripHPx
                                    val top = SelectorMath.letterTop(letterY, size, rowPx.toFloat(), topPx, topPx + listHPx)
                                    SelectorMath.levelWith(start.toFloat(), top - panelTop, rowPx.toFloat())
                                } else {
                                    // The whole list (top = first app, bottom = last app), or the aim of a slow slide, is
                                    // brought level with the finger, as Niagara does.
                                    SelectorMath.levelWith(at, yIn, rowPx.toFloat())
                                }
                            }
                            // Scroll the list, and past its ends move the panel. A letter's apps alone in the list
                            // stay on screen.
                            val maxScrollPx = SelectorMath.maxScroll(n, rowPx.toFloat(), panelH)
                            val (scroll, shift) = SelectorMath.place(state.listY, maxScrollPx)
                            val shiftOn = if (onlyLetter) SelectorMath.keepOnScreen(shift, panelH, listHPx) else shift
                            state.listY = scroll - shiftOn
                            when {
                                // Along the edge, on a new letter, the list glides there instead of jumping. (A letter's
                                // apps alone in the list are a new list, so they show at once.)
                                newLetter && !onlyLetter -> {
                                    val from = state.scrollPx - state.shiftPx
                                    val fresh = glide?.isActive != true
                                    val to = state.listY
                                    glide = scope.launch {
                                        if (fresh) glider.snapTo(from)
                                        glider.animateTo(to, GLIDE) {
                                            val (s, sh) = SelectorMath.place(value, maxScrollPx)
                                            show(s, sh)
                                        }
                                    }
                                }
                                // Still gliding to the letter the thumb is on.
                                !selecting && glide?.isActive == true -> Unit
                                else -> show(scroll, shiftOn)
                            }
                            // Picking: the app level with the finger, as it moves up and down. Along the edge with
                            // letters, no app is at the thumb yet; only the letter is.
                            state.contact = if (byLetter && !selecting) Float.NaN else SelectorMath.contact(state.listY, yIn, rowPx.toFloat())
                            if (!state.contact.isNaN()) state.lensAt = state.contact
                            val f = SelectorMath.focusedIndex(state.contact, n)
                            val section = if (f >= 0) list[f].sectionIndex else onLetter
                            if (!byLetter) state.letter = section
                            // Haptic feedback for where the finger is:
                            // - a switch between the letters and the list;
                            // - a firm tick per letter along the edge;
                            // - a light tick per app in the list.
                            when (SelectorMath.feel(selecting, wasSelecting, f, tickedRow, section, lastSection, aiming && !selecting)) {
                                SelectorMath.Feel.SWITCH -> haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                SelectorMath.Feel.APP -> haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                SelectorMath.Feel.LETTER -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                SelectorMath.Feel.NONE -> Unit
                            }
                            wasSelecting = selecting
                            tickedRow = f
                            lastSection = section
                            // Any real movement (on either axis) restarts the hold. Sliding sideways along one row
                            // toward the panel is not "holding still".
                            if (f != focus || abs(y - anchorY) > slopPx || abs(x - anchorX) > slopPx) {
                                focus = f
                                rowSince = now
                                anchorY = y
                                anchorX = x
                            }
                            // B2: on the panel's far side, lifting opens the actions instead of launching.
                            val far = farMode && selecting && SelectorMath.farSide(stripXPx + x, touchXPx, touchWPx, left)
                            if (far != state.far) {
                                if (far) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                state.far = far
                            }
                            // Plan B: the held row fills while the hold counts, and the app launches when the row is
                            // full, so the launch is never a surprise. A move restarts the hold, and the fill drains.
                            // No fill on the far side, because there lifting opens the app's actions.
                            val target = if (selecting && !far) SelectorMath.holdFill(now - rowSince) else 0f
                            state.hold = SelectorMath.holdShown(state.hold, target, now - holdAt)
                            holdAt = now
                        }

                        state.interaction++
                        state.mode = Mode.SCRUB
                        // A list that was left open in place goes back to the panel's normal placement.
                        state.padTop = 0f
                        state.padBottom = 0f
                        try {
                            update(rowSince)
                            while (true) {
                                val now = SystemClock.uptimeMillis()
                                val panelH = panelH(list.size)
                                val maxScrollPx = SelectorMath.maxScroll(list.size, rowPx.toFloat(), panelH)
                                // A letter's apps alone in the list scroll only when they don't fit.
                                val speed = if (selecting && (!onlyLetter || maxScrollPx > 0f)) {
                                    SelectorMath.edgeSpeed(stripTopPx + y - (panelTop(list.size) + state.shiftPx), panelH, edgeZonePx, edgeMaxSpeed)
                                } else 0f
                                val scrolling = speed != 0f &&
                                    !(speed < 0f && state.listY <= 0f) && !(speed > 0f && state.listY >= maxScrollPx)
                                val held = now - rowSince
                                val holding = selecting && !state.far
                                // The fill rises, then drains when the hold stops. Both update once per frame.
                                val filling = holding && held >= SelectorMath.FILL_FROM_MS
                                val draining = state.hold > 0f && !filling
                                val wait = when {
                                    scrolling || filling || draining -> FRAME_MS
                                    holding -> (SelectorMath.FILL_FROM_MS - held).coerceAtLeast(1L)
                                    else -> Long.MAX_VALUE
                                }
                                if (!scrolling) lastFrame = now
                                val event = if (wait == Long.MAX_VALUE) awaitPointerEvent() else withTimeoutOrNull(wait) { awaitPointerEvent() }
                                if (event == null) {
                                    val t = SystemClock.uptimeMillis()
                                    if (scrolling) {
                                        // Near the panel's top or bottom: keep gliding, faster the deeper the finger is.
                                        state.listY = (state.listY + speed * (t - lastFrame)).coerceIn(min(0f, state.listY), max(maxScrollPx, state.listY))
                                        lastFrame = t
                                        update(t)
                                        continue
                                    }
                                    if (holding && t - rowSince >= SelectorMath.HOLD_MS) {
                                        // Held still on a row until its fill is full: the app launches, as if the finger lifted.
                                        val app = list[focus].item
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        state.close()
                                        launch(app)
                                        break
                                    }
                                    // The fill is rising or draining.
                                    update(t)
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    state.close()
                                    break
                                }
                                if (!change.pressed) {
                                    // A cancel (for example, the system took the touch for the back gesture) arrives as
                                    // a release that is already consumed. Never launch on it.
                                    if (change.isConsumed) {
                                        state.close()
                                    } else if (selecting) {
                                        val app = list[focus].item
                                        val far = state.far
                                        state.close()
                                        if (far) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            longPress(app)
                                        } else {
                                            launch(app)
                                        }
                                    } else {
                                        // Lifted on the edge: leave the list open for a tap or a fling.
                                        // - Letters: a tapped letter's apps stay where they are, next to it. The whole list
                                        //   keeps them there with extra room at its ends, so it can still scroll to every app.
                                        // - Apps: the same. Near the list's start or end, the list was moved to reach the
                                        //   thumb; it stays there, so the app under the thumb doesn't jump away.
                                        state.contact = Float.NaN
                                        if (!onlyLetter) {
                                            if (byLetter && glide?.isActive == true) {
                                                glide.cancel()
                                                val (s, sh) = SelectorMath.place(state.listY, SelectorMath.maxScroll(list.size, rowPx.toFloat(), panelH(list.size)))
                                                show(s, sh)
                                            }
                                            val (top, bottom, scroll) = SelectorMath.keepInPlace(state.scrollPx, state.shiftPx)
                                            // Open, the list scrolls as far as the strip reaches: the first app down to the
                                            // strip's top, the last app up to its bottom, as sliding could put them.
                                            val (padTop, padBottom, kept) = SelectorMath.reachStrip(
                                                top, bottom, scroll, panelTop(list.size), panelH(list.size), stripTopPx, stripHPx,
                                            )
                                            state.padTop = padTop
                                            state.padBottom = padBottom
                                            state.listY = kept
                                            state.scrollPx = kept
                                            state.shiftPx = 0f
                                            listState.scrollToPx(kept, rowPx)
                                        }
                                        state.mode = Mode.OPEN
                                        state.interaction++
                                    }
                                    break
                                }
                                change.consume()
                                val newY = change.position.y + zoneTopPx
                                // Plan C: the speed of the slide along the edge decides between a glide and an aim
                                // (apps only; letters move one letter at a time).
                                val dt = (change.uptimeMillis - lastMoveAt).coerceAtLeast(1L)
                                val instant = abs(newY - y) / pxPerDp / dt * 1000f
                                slideSpeed = if (slideSpeed < 0f) instant else SelectorMath.smoothSpeed(slideSpeed, instant, dt)
                                lastMoveAt = change.uptimeMillis
                                val weight = if (!byLetter && aimStepPx > 0f) SelectorMath.aimWeight(slideSpeed, aimSpeed.toFloat()) else 0f
                                aiming = weight >= 0.5f
                                if (!selecting && !byLetter) {
                                    at = SelectorMath.aim(at, sections.along(y / stripHPx), sections.along(newY / stripHPx), newY - y, aimStepPx, weight, count)
                                }
                                x = change.position.x
                                y = newY
                                // Off the edge, toward the panel: the list holds still so you can pick from it.
                                // Picking starts LEAVE off the edge and ends only halfway back. So a thumb resting
                                // at the line doesn't flip between the two.
                                val off = if (left) x - stripWPx else -x
                                val wasPicking = selecting
                                selecting = if (selecting) off > leavePx / 2 else off > leavePx
                                // Back on the edge: with apps, continue from the app under the thumb, without a
                                // jump; with letters, continue from the letter the thumb is on.
                                if (wasPicking && !selecting && !byLetter && !state.contact.isNaN()) at = state.contact.coerceIn(0f, count - 0.01f)
                                state.mode = if (selecting) Mode.SELECT else Mode.SCRUB
                                update(SystemClock.uptimeMillis())
                            }
                        } catch (e: CancellationException) {
                            state.close()
                            throw e
                        } finally {
                            glide?.cancel()
                        }
                    }
                },
        )
    }
}

/** One list row: the section letter in the gutter, the profile tag in its colour, ★, and the label. */
@Composable
private fun SelectorRow(
    entry: SelectorEntry<AppEntry>,
    index: Int,
    count: Int,
    state: SelectorState,
    rowPx: Int,
    height: androidx.compose.ui.unit.Dp,
    pinned: Boolean,
    /** If not null, the app's icon at this size at the start of the row (Plan E). */
    icon: androidx.compose.ui.unit.Dp?,
    enabled: Boolean,
    /** Magnify the row at the thumb and its neighbours while picking. */
    fisheye: Boolean,
    /** How far the fisheye is out (0 to 1), easing in and out. Read while drawing. */
    lens: () -> Float,
    /** Letters: while the thumb is down, the apps of other letters fade back (unless picked). */
    dimOthers: Boolean,
    /** How far they are faded back (0 to 1), easing in and out. Read while drawing. */
    dim: () -> Float,
    /** The row's text style: the terminal's font, or the phone's font with selector.system-font. */
    text: TextStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val app = entry.item
    // Highlighted only while picking, off the edge: there lifting or holding launches it. Sliding along
    // the edge launches nothing, so no app is marked then.
    val focused by remember(index, count) {
        derivedStateOf { state.mode == Mode.SELECT && SelectorMath.focusedIndex(state.contact, count) == index }
    }
    val quiet = app.profile.quiet
    val holdColor = Term.prompt.copy(alpha = 0.3f)
    val focusColor = Term.prompt.copy(alpha = 0.14f)
    /** How much the fisheye magnifies this row now (1: not at all). Read only while laying out and drawing. */
    fun scale(): Float {
        val c = state.contact
        // While the fisheye shrinks back, use the place where it last was.
        val at = if (c.isNaN()) state.lensAt else c
        val l = lens()
        if (at.isNaN() || !fisheye || l == 0f) return 1f
        return 1f + (SelectorMath.fisheyeScale(index + 0.5f - at) - 1f) * l
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // Fisheye in the draw phase: no recomposition per frame. (Only a magnified row's width, below, needs a relayout.)
            .graphicsLayer {
                val c = state.contact
                val at = if (c.isNaN()) state.lensAt else c
                val l = lens()
                val s = scale()
                scaleX = s; scaleY = s
                // Rows past the fisheye's reach are not magnified, but still pushed apart.
                translationY = if (at.isNaN() || !fisheye || l == 0f) 0f else SelectorMath.fisheyeShift(index + 0.5f - at) * rowPx * l
                transformOrigin = TransformOrigin(0f, 0.5f)
                val faded = dimOthers && entry.sectionIndex != state.letter && SelectorMath.focusedIndex(c, count) != index
                alpha = (if (quiet) 0.45f else 1f) * (if (faded) 1f - 0.6f * dim() else 1f)
            }
            // A magnified row grows from its start. So it gets the list's width divided by its scale: magnified, it
            // still ends at the list's edge, and a long name ends with "…" earlier. Only a relayout when the scale
            // changes, no recomposition. The row is placed at the left, where the scale starts (as the scale, not
            // by layout direction).
            .layout { measurable, constraints ->
                val s = scale()
                val w = if (s > 1f && constraints.hasBoundedWidth) (constraints.maxWidth / s).toInt() else constraints.maxWidth
                val p = measurable.measure(constraints.copy(minWidth = min(constraints.minWidth, w), maxWidth = w))
                layout(constraints.maxWidth.coerceAtLeast(p.width), p.height) { p.place(0, 0) }
            }
            // While picking, the row at the thumb gets a mark. The mark fades with the fisheye.
            .drawBehind {
                val c = state.contact
                val l = lens()
                if (l > 0f && state.mode == Mode.SELECT && SelectorMath.focusedIndex(if (c.isNaN()) state.lensAt else c, count) == index) {
                    drawRoundRect(focusColor, alpha = l, cornerRadius = CornerRadius(6.dp.toPx()))
                }
            }
            // Plan B: the held row fills from its start while the hold counts (read in the draw phase only).
            .drawBehind {
                if (SelectorMath.focusedIndex(state.contact, count) == index) {
                    val p = state.hold
                    if (p > 0f) drawRoundRect(holdColor, size = Size(size.width * p, size.height), cornerRadius = CornerRadius(6.dp.toPx()))
                }
            }
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(end = 12.dp),
    ) {
        // The section letter, in the accent colour, where a section starts and on the focused row.
        // So the current letter is where you look.
        Text(
            if (entry.sectionStart || focused) entry.section.toString() else "",
            style = text.copy(
                color = if (focused) Term.prompt else if (entry.favourite) Term.prompt.copy(alpha = 0.7f) else Term.dim.copy(alpha = 0.7f),
                fontWeight = if (focused) FontWeight.Bold else null,
                fontSize = (Term.fontSize - 3).sp,
            ),
            modifier = Modifier.width(26.dp).padding(start = 10.dp),
            maxLines = 1,
        )
        // Plan E: the app's icon, as the suggestions show it. It grows with the row in the fisheye.
        if (icon != null) AppIcon(app, icon, Modifier.padding(end = 8.dp).testTag("app-selector-icon"))
        if (app.profile.kind != ProfileKind.PERSONAL) {
            Text(
                "[${app.profile.kind.tag}] ",
                style = text.copy(color = Term.profileColor(app.profile.kind), fontWeight = FontWeight.Bold, fontSize = (Term.fontSize - 2).sp),
            )
        }
        if (pinned && !entry.favourite) Text("★ ", style = text.copy(color = Term.prompt.copy(alpha = 0.7f)))
        Text(
            app.label,
            style = text.copy(
                color = if (focused) Term.prompt else Term.fg,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // B2: on the far side, lifting opens the app's actions, and the row says so.
        if (focused && state.far) {
            Text("⋯ Actions", style = text.copy(color = Term.prompt, fontSize = (Term.fontSize - 2).sp), maxLines = 1)
        }
    }
}
