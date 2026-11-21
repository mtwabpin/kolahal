/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.WidgetFeed
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.WidgetDrag
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetOrder
import com.mtwabpin.kolahal.widgets.WidgetPlacement
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows the messages of [WidgetHost] in a snackbar: each waiting message once, oldest first.
 * It works on whichever widget screen is open.
 */
@Composable
internal fun rememberWidgetMessages(widgets: WidgetHost): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        widgets.messages.collect {
            if (it.isNotEmpty()) widgets.takeMessage()?.let { m -> snackbar.showSnackbar(m) }
        }
    }
    return snackbar
}

/**
 * The name of a widget Kolahal draws itself, from its source ([WidgetSource]). Null for an app's widget.
 * - `script:weather`: the script's `@title`, else its name.
 * - `script:weather#2`, a further widget of the script: the same, then its id: "Weather 2".
 * - `builtin:battery`: the built-in widget's name ([WidgetSource.builtinTitle]), "Battery and network".
 * - A script that is gone, and a built-in widget this version does not know: the name in the source.
 *
 * [scripts] are the scripts as the view model has read them, by name ([WidgetScript]). No code is
 * read here.
 */
internal fun sourceTitle(provider: String, scripts: Map<String, WidgetScript>): String? {
    if (!WidgetSource.isSource(provider)) return null
    WidgetSource.builtinId(provider)?.let { return WidgetSource.builtinTitle(it) ?: it }
    WidgetSource.scriptRef(provider)
        ?.let { ref -> return ref.titled(scripts[ref.script]?.title ?: ref.script) }
    // A script's source whose id can't be one: the text as it is stored.
    return provider.substringAfter(':')
}

/**
 * A widget held after a long-press, and moved by the finger. Positions are in pixels on the page,
 * from its top left; [window] is the finger in the window.
 */
private class Dragged(val key: String, val from: Offset, val start: Offset, scroll: Int) {
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
 * The Widgets screen (Material 3 Expressive). It shows the widgets page as it is, live, each widget
 * where it was put ([WidgetGrid]), to arrange it.
 * - A tap chooses a widget: an outline, handles on its end and bottom edges, and its toolbar at the
 *   bottom. Dragging a handle resizes the widget, by columns and 8 dp steps.
 * - A long-press lifts a widget. Column guides show, a dashed box shows where it lands, and widgets
 *   in the way show where they will move down to. While it is held, a Remove target takes the top
 *   bar's place; dropping it there asks first.
 * - The toolbar of one of Kolahal's own widgets sets its look and its size, one tap each. An app's
 *   widget has steps for width and height (they repeat while held) and Square.
 * - The top bar has Close gaps and Add widget. A screen reader has actions on each widget: move it,
 *   resize it, remove it.
 * Entries restored from a backup show an "Add again" row.
 */
@Composable
fun WidgetsScreen(vm: TerminalViewModel, onReAdd: (ProviderEntry, PlacedWidget) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val providersVersion by widgets.providersVersion.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val version by vm.config.version.collectAsStateWithLifecycle()
    // With widgets.page off, no page next to the terminal shows the widgets, so this screen says so.
    val pageOff = remember(version) { vm.config[Keys.WIDGETS_PAGE] == "off" }
    val agendaDays = remember(version) { vm.config[Keys.AGENDA_WIDGET_DAYS] }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    // A widget just placed, or long-pressed on the widgets page, is chosen as the screen shows.
    val toChoose by widgets.toChoose.collectAsStateWithLifecycle()
    LaunchedEffect(toChoose) {
        toChoose?.let {
            selected = it
            widgets.chosen()
        }
    }
    var confirmRemove by remember { mutableStateOf<PlacedWidget?>(null) }
    // The Shortcuts widget's editor is open: from the toolbar of that widget, or because a tap on its card asked for it.
    var shortcutsEditor by rememberSaveable { mutableStateOf(false) }
    val editorAsked by vm.shortcutsEditorAsked.collectAsStateWithLifecycle()
    LaunchedEffect(editorAsked) {
        if (editorAsked) {
            shortcutsEditor = true
            vm.shortcutsEditorOpened()
        }
    }
    // The options sheet of one script widget is open, by the widget's key: from that widget's toolbar,
    // or because the picker just placed a further widget of a script and asked for it.
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    val optionsAsked by vm.widgetOptionsAsked.collectAsStateWithLifecycle()
    LaunchedEffect(optionsAsked) {
        optionsAsked?.let {
            optionsFor = it.key
            vm.widgetOptionsOpened()
        }
    }
    val snackbar = rememberWidgetMessages(widgets)
    BackHandler { if (selected != null) selected = null else vm.closeScreen() }

    val panel = remember(all) {
        WidgetPlacement.readingOrder(
            WidgetOrder.inLocation(
                all,
                WidgetLocation.PANEL
            )
        )
    }
    val pending = remember(all) { WidgetOrder.sorted(all.filter { it.pending }) }
    // Each label needs a binder call and a load from the other app, so they load off the main thread
    // ([WidgetHost.label]), again only when widgets are added or removed, not on every resize step.
    // Until then, the labels loaded before, or the provider's last part.
    val placed = remember(all) { all.map { Triple(it.key, it.id, it.provider) } }
    val labels by produceState(placed.associate { (key, id, provider) ->
        key to widgets.loadedLabel(
            id,
            provider
        )
    }, placed, providersVersion) {
        value = withContext(Dispatchers.IO) {
            placed.associate { (key, id, provider) ->
                key to widgets.label(
                    id,
                    provider
                )
            }
        }
    }
    // A widget Kolahal draws itself is named by its script's title. The view model has read the titles
    // from the code, off the main thread, and its answer changes only when a title does: not at
    // every run. They are apart from the labels above, which cost a call to another app each.
    val scripts by vm.widgetScripts.collectAsStateWithLifecycle()
    val titles = remember(placed, scripts) {
        placed.mapNotNull { (key, _, provider) ->
            sourceTitle(
                provider,
                scripts.orEmpty()
            )?.let { key to it }
        }.toMap()
    }

    fun labelOf(w: PlacedWidget): String? = titles[w.key] ?: labels[w.key]

    // The widget held after a long-press, and the one whose handle is held. Neither outlives the screen.
    var dragged by remember { mutableStateOf<Dragged?>(null) }
    var resizing by remember { mutableStateOf<String?>(null) }
    val chosen = panel.firstOrNull { it.key == selected }
    val page = rememberScrollState()

    GuiTheme {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        // Where the Remove target is in the window while a widget is held.
        var removeAt by remember { mutableStateOf(Rect.Zero) }
        val overRemove by remember { derivedStateOf { dragged?.let { removeAt.contains(it.window) } == true } }
        Scaffold(
            modifier = Modifier
                .imePadding()
                .nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Box {
                    MediumFlexibleTopAppBar(
                        title = { Text("Widgets") },
                        subtitle = {
                            Text(
                                when {
                                    pageOff -> "The widgets page is off"
                                    panel.isEmpty() -> "On the widgets page beside the terminal"
                                    else -> "${panel.size} on the widgets page"
                                }
                            )
                        },
                        navigationIcon = { BackButton(vm::closeScreen) },
                        actions = {
                            if (panel.isNotEmpty()) LabeledIconButton(
                                R.drawable.ic_vertical_align_top,
                                "Close gaps",
                                onClick = { widgets.closeGaps() })
                            LabeledIconButton(
                                R.drawable.ic_add,
                                "Add widget",
                                onClick = { vm.openScreen(Screen.WIDGET_PICKER) })
                        },
                        scrollBehavior = scroll,
                    )
                    // While a widget is held, the Remove target takes the bar's place.
                    if (dragged != null) RemoveTarget(
                        overRemove,
                        Modifier.matchParentSize()
                    ) { removeAt = it }
                }
            },
            bottomBar = {
                if (chosen != null && dragged == null) {
                    val chosenRef = WidgetSource.scriptRef(chosen.source)
                    WidgetToolbar(
                        w = chosen,
                        label = labelOf(chosen) ?: chosen.provider,
                        size = widgets.sizeOf(chosen),
                        // A built-in widget's kind is fixed; a script's is the one its @widget line names.
                        kind = WidgetSource.builtinId(chosen.source)?.let(WidgetSource::builtinKind)
                            ?: chosenRef?.let { scripts?.get(it.script)?.widget?.kind },
                        minHeight = remember(chosen.key, providersVersion) {
                            widgets.minHeightOf(
                                chosen
                            )
                        },
                        onLook = { widgets.setLook(chosen.key, it) },
                        onSize = { widgets.setSize(chosen.key, it) },
                        onHeight = { widgets.resize(chosen.key, it * WidgetLayout.STEP_DP) },
                        onWidth = { widgets.resizeWidth(chosen.key, it) },
                        onSquare = { widgets.square(chosen.key) },
                        // What the widget itself holds, if anything:
                        // - the Shortcuts widget: its list of commands;
                        // - a widget of a script that declares options: its options. The first widget's
                        //   are the script's own, and a further widget can have values of its own.
                        edit = when {
                            chosen.source == WidgetSource.builtin(WidgetSource.SHORTCUTS) -> ToolbarEdit(
                                "Edit shortcuts",
                                R.drawable.ic_edit
                            ) { shortcutsEditor = true }

                            chosenRef != null && scripts?.get(chosenRef.script)?.hasOptions == true -> ToolbarEdit(
                                "Options",
                                R.drawable.ic_tune
                            ) { optionsFor = chosenRef.key }

                            else -> null
                        },
                        onRemove = { confirmRemove = chosen },
                        onDone = { selected = null },
                        // The Agenda card: how many days it shows (widgets.agenda-days).
                        agendaDays = if (chosen.source == WidgetSource.builtin(WidgetSource.AGENDA)) agendaDays else null,
                        onAgendaDays = { vm.setConfig(Keys.AGENDA_WIDGET_DAYS, it) },
                    )
                }
            },
        ) { padding ->
            // While a widget is held the toolbar is put away, but the page keeps the room it left.
            // Otherwise a page scrolled to its end would jump under the finger.
            val liveBottom = padding.calculateBottomPadding()
            var keptBottom by remember { mutableStateOf(liveBottom) }
            SideEffect { if (dragged == null) keptBottom = liveBottom }
            var viewport by remember { mutableStateOf(Rect.Zero) }
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarScrim()
                    .onGloballyPositioned { viewport = it.boundsInWindow() }
                    .verticalScroll(page)
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = (if (dragged != null) keptBottom else liveBottom) + 16.dp
                    )
                    // The widgets get the same width as on the widgets page (its margins and side
                    // insets). Text keeps the usual margin.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = WidgetLayout.GAP_DP.dp),
            ) {
                val inset = Modifier.padding(horizontal = 16.dp - WidgetLayout.GAP_DP.dp)
                if (pageOff) {
                    Box(inset.padding(vertical = 8.dp)) {
                        ActionGroup(
                            listOf(
                                Action(
                                    R.drawable.ic_visibility,
                                    "Turn on the widgets page",
                                    "Beside the terminal: swipe sideways on the home screen to reach it"
                                ) {
                                    vm.config.set(Keys.WIDGETS_PAGE, "left")
                                },
                            )
                        )
                    }
                }
                if (panel.isEmpty() && pending.isEmpty()) {
                    EmptyNote(
                        if (pageOff) "No widgets yet." else "No widgets yet. They appear on the widgets page beside the terminal: swipe sideways on the home screen to reach it.",
                        inset,
                    )
                    Box(inset) {
                        ActionGroup(
                            listOf(
                                Action(
                                    R.drawable.ic_widgets,
                                    "Add a widget",
                                    "Clock, weather, calendar, music… from any app"
                                ) {
                                    vm.openScreen(Screen.WIDGET_PICKER)
                                },
                            )
                        )
                    }
                }
                if (panel.isNotEmpty()) {
                    ArrangedPage(
                        widgets = widgets,
                        all = all,
                        panel = panel,
                        selected = selected,
                        onSelect = { selected = it },
                        dragged = dragged,
                        onDrag = { dragged = it },
                        resizing = resizing,
                        onResize = { resizing = it },
                        page = page,
                        // The part of the window the page shows in, between the bars: held near its
                        // top or bottom, a widget scrolls the page.
                        shown = {
                            with(density) {
                                Rect(
                                    viewport.left,
                                    viewport.top + padding.calculateTopPadding().toPx(),
                                    viewport.right,
                                    viewport.bottom - liveBottom.toPx()
                                )
                            }
                        },
                        overRemove = { overRemove },
                        labelOf = { labelOf(it) ?: it.provider },
                        profiles = { profiles.pausedKind(it.profileSerial) },
                        feed = vm.widgetFeed,
                        providersVersion = providersVersion,
                        topRoom = padding.calculateTopPadding(),
                        bottomRoom = liveBottom,
                        onRemove = { confirmRemove = it },
                    )
                }
                if (pending.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    GroupHeader("Restored from backup")
                    Text(
                        "Widgets can't be copied between installs — add each one again with one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = inset.padding(start = 16.dp, bottom = 8.dp),
                    )
                    Column(
                        inset,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        pending.forEachIndexed { i, w ->
                            PendingRow(
                                w,
                                i,
                                pending.size,
                                providersVersion,
                                onReAdd = onReAdd,
                                onDismiss = { widgets.remove(w.key) })
                        }
                    }
                }
                if (panel.isNotEmpty()) {
                    PageHint(
                        "Tap a widget to change it; long-press it to move it. Terminal: widgets ls · widget add",
                        inset
                    )
                }
            }
        }

        confirmRemove?.let { w ->
            ConfirmDialog(
                "Remove ${labelOf(w) ?: "widget"}?",
                "The widget is deleted; add it again from the picker any time.",
                "Remove",
                icon = R.drawable.ic_delete,
                onDismiss = { confirmRemove = null },
            ) { confirmRemove = null; selected = null; widgets.remove(w.key) }
        }

        if (shortcutsEditor) ShortcutsEditor(vm) { shortcutsEditor = false }

        optionsFor?.let(WidgetRef::parse)
            ?.let { ref -> WidgetOptionsSheet(vm, ref) { optionsFor = null } }
    }
}

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
 *   Narrower, Taller, Shorter and Remove.
 * - The chosen widget is scrolled into view, clear of the bars ([topRoom], [bottomRoom]), when it is
 *   chosen and each time it moves or changes size.
 */
@Composable
private fun ArrangedPage(
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

    fun dropAt(d: Dragged, w: PlacedWidget, g: WidgetDrag): Drop {
        val at = d.from + d.moved(page.value)
        return Drop(w, g.dropColumn(at.x, w.span), g.dropTopDp(at.y).coerceAtMost(lowest))
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
                            val minSpan = WidgetLayout.minSpan(chosenNow.source != null)
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
                    // would go off the screen. After a frame, so its new place and the toolbar are laid
                    // out first. Not while a handle is held: the page would move under the finger.
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
                        actions = screenReaderActions(w, widgets, rtl, minHeight) { onRemove(w) },
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
private fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
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
 * column wider or narrower, a step (8 dp) taller or shorter, and remove it ([onRemove], which asks
 * first). Only what can happen is offered.
 */
private fun screenReaderActions(
    w: PlacedWidget,
    widgets: WidgetHost,
    rtl: Boolean,
    minHeight: Int,
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
        if (w.span > WidgetLayout.minSpan(w.source != null)) add(act("Narrower") {
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
 * Where a held widget can be dropped to remove it, in place of the top bar: "Remove" in a dashed
 * outline, filled while the widget is over it ([over]). [onPlaced] gets the area that counts, in
 * the window: the bar's whole row.
 */
@Composable
private fun RemoveTarget(over: Boolean, modifier: Modifier, onPlaced: (Rect) -> Unit) {
    val error = MaterialTheme.colorScheme.error
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onGloballyPositioned { onPlaced(it.boundsInWindow()) },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .height(44.dp)
                    .background(
                        if (over) error.copy(alpha = 0.16f) else Color.Transparent,
                        CircleShape
                    )
                    .dashedBorder(error, 2.dp, 22.dp)
                    .padding(horizontal = 20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Icon(painterResource(R.drawable.ic_delete), null, tint = error)
                Text(
                    "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = error
                )
            }
        }
    }
}

/** A button of the toolbar for what the selected widget itself holds: its [label] and [icon], and what a tap does. */
private class ToolbarEdit(val label: String, val icon: Int, val onClick: () -> Unit)

/**
 * One widget as it shows on the widgets page. A tap anywhere on it chooses it ([onSelect]): no tap
 * reaches the widget itself. A card Kolahal draws reads its script from [feed], as it does on the
 * widgets page. [actions] are a screen reader's.
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
        if (w.source != null) {
            SourceWidget(w, w.heightDp.dp, actions = null, feed = feed)
        } else {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                HostedWidget(w, w.heightDp.dp, pausedKind, onLongPress = {}) { why ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(w.heightDp.dp)
                            .alpha(0.6f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
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

/**
 * The controls for the chosen widget, docked at the bottom: its name and size, and Remove and Done.
 * - One of Kolahal's own widgets: its look and its size, one tap each. With [edit], a button for
 *   what the widget itself holds: the Shortcuts widget's list, or the options of a script's widget.
 * - An app's widget: width and height in steps, and Square. It draws itself, so it has no look.
 */
@Composable
private fun WidgetToolbar(
    w: PlacedWidget,
    label: String,
    size: WidgetSize?,
    kind: WidgetKind?,
    minHeight: Int,
    onLook: (WidgetLook) -> Unit,
    onSize: (WidgetSize) -> Unit,
    onHeight: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onSquare: () -> Unit,
    edit: ToolbarEdit?,
    onRemove: () -> Unit,
    onDone: () -> Unit,
    agendaDays: String? = null,
    onAgendaDays: (String) -> Unit = {},
) {
    val own = w.source != null
    // Only the looks that change this kind of card ([WidgetLook.offeredFor]); one it doesn't offer draws Plain.
    val looks = WidgetLook.offeredFor(kind)
    val drawn = w.look.drawnFor(kind)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val shape = size?.label ?: WidgetLayout.sizeLine(w.span, w.heightDp)
                    Text(
                        if (own) "$shape · ${drawn.label}" else shape,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (edit != null) {
                    Labeled(edit.label) {
                        FilledTonalIconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = edit.onClick
                        ) { Icon(painterResource(edit.icon), edit.label) }
                    }
                }
                LabeledIconButton(
                    R.drawable.ic_delete,
                    "Remove",
                    onClick = onRemove,
                    tint = MaterialTheme.colorScheme.error
                )
                Labeled("Done") {
                    FilledIconButton(shapes = IconButtonDefaults.shapes(), onClick = onDone) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            "Done"
                        )
                    }
                }
            }
            if (own) {
                ToolbarLabel("Look")
                ChoiceRow(looks.map { it.label }, drawn.label, "look ") { onLook(looks[it]) }
                ToolbarLabel("Size")
                ChoiceRow(WidgetSize.entries.map { it.label }, size?.label, "size ") {
                    onSize(
                        WidgetSize.entries[it]
                    )
                }
                if (agendaDays != null) {
                    ToolbarLabel("Days")
                    val choices = Keys.AGENDA_WIDGET_DAYS.choices
                    val labels = choices.map { it.replaceFirstChar(Char::titlecase) }
                    ChoiceRow(
                        labels,
                        labels.getOrNull(choices.indexOf(agendaDays)),
                        "days "
                    ) { onAgendaDays(choices[it]) }
                }
                Text(
                    "Drag to move. Drag an edge to resize.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                // Side by side if they fit. On a narrow screen or with large text, one below the other.
                FlowRow(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Stepper(
                        "Width",
                        WidgetLayout.spanLabel(w.span),
                        w.span > WidgetLayout.minSpan(false),
                        w.span < WidgetLayout.COLUMNS,
                        onWidth
                    )
                    Stepper(
                        "Height",
                        "${w.heightDp}",
                        w.heightDp > minHeight,
                        w.heightDp < WidgetLayout.MAX_DP,
                        onHeight
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        "Looks are for Kolahal's own widgets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onSquare
                    ) { Text("Square") }
                }
            }
        }
    }
}

/** The name of a row of the toolbar: "Look", "Size". */
@Composable
private fun ToolbarLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/**
 * [choices] as one row of connected buttons, [selected] checked (none when it is not among them).
 * A tap picks one ([onPick] with its index). Each is tagged [tagPrefix] and its text.
 */
@Composable
private fun ChoiceRow(
    choices: List<String>,
    selected: String?,
    tagPrefix: String,
    onPick: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth()
    ) {
        choices.forEachIndexed { i, choice ->
            ToggleButton(
                checked = choice == selected,
                onCheckedChange = { onPick(i) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton }
                    .testTag(tagPrefix + choice),
            ) {
                Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Shows "label − value +". Each button steps once on a tap and keeps stepping while held. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    canLess: Boolean,
    canMore: Boolean,
    onStep: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
        RepeatingButton(
            R.drawable.ic_remove,
            "Decrease ${label.lowercase()}",
            canLess
        ) { onStep(-1) }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 36.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        RepeatingButton(R.drawable.ic_add, "Increase ${label.lowercase()}", canMore) { onStep(1) }
    }
}

@Composable
private fun RepeatingButton(icon: Int, description: String, enabled: Boolean, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    // When held past the long-press delay, the button repeats. The release that ends such a hold
    // does not step again.
    // [heldSince] is reset each time a press ends. So a hold that slid off the button or reached the
    // limit doesn't block the next activation. (A TalkBack activation has no press at all.)
    var heldSince by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) {
            heldSince = 0L
            return@LaunchedEffect
        }
        heldSince = SystemClock.uptimeMillis()
        delay(400.milliseconds)
        while (true) {
            step()
            delay(70.milliseconds)
        }
    }
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        onClick = {
            if (heldSince == 0L || SystemClock.uptimeMillis() - heldSince < 400) step()
            heldSince = 0L
        },
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(painterResource(icon), description)
    }
}

@Composable
private fun PendingRow(
    w: PlacedWidget,
    index: Int,
    count: Int,
    providersVersion: Int,
    onReAdd: (ProviderEntry, PlacedWidget) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val entry by produceState<ProviderEntry?>(null, w.provider, w.profileSerial, providersVersion) {
        value = withContext(Dispatchers.IO) { widgets.findProvider(w.provider, w.profileSerial) }
    }
    val found = entry
    // Loading a label reads the other app's resources: off the main thread, once per provider.
    val label by produceState(w.provider.substringAfterLast('.'), found) {
        found?.let { f ->
            value =
                withContext(Dispatchers.IO) { f.info.loadLabel(context.packageManager).toString() }
        }
    }
    SegmentedListItem(
        colors = segColors(),
        shapes = segShapes(index, count),
        leadingContent = {
            Icon(
                painterResource(R.drawable.ic_widgets),
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        overlineContent = {
            Text(
                w.packageName,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                if (found == null) "App not installed" else WidgetLayout.sizeLine(
                    w.span,
                    w.heightDp
                )
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { found?.let { onReAdd(it, w) } },
                    enabled = found != null
                ) { Text("Add again") }
                LabeledIconButton(R.drawable.ic_delete, "Forget", onClick = onDismiss)
            }
        },
    ) { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}
