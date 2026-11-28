/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.ui.ActionMenu
import com.mtwabpin.kolahal.ui.ActionSheet
import com.mtwabpin.kolahal.ui.home.HomeAppSelector
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.theme.cutoutSafePadding
import com.mtwabpin.kolahal.ui.theme.page
import com.mtwabpin.kolahal.widgets.KolahalWidgetView
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetDrag
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetOrder
import com.mtwabpin.kolahal.widgets.WidgetPages
import com.mtwabpin.kolahal.widgets.WidgetPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Returns the kind of the profile with [serial] if it is paused/locked, else null. A paused/locked
 * profile's widget shows a placeholder instead. A private space that Android hides while it is
 * locked isn't listed: for a profile the list misses, [lockedPrivate] asks Android
 * ([WidgetHost.isLockedPrivate]).
 */
internal fun List<Profile>.pausedKind(serial: Long, lockedPrivate: (Long) -> Boolean): ProfileKind? {
    val p = firstOrNull { it.serial == serial }
        ?: return ProfileKind.PRIVATE.takeIf { lockedPrivate(serial) }
    return p.kind.takeIf { p.quiet }
}

/**
 * The kind of the profile with [serial]: as listed, else a private space when [lockedPrivate] says
 * it is one Android hides while it is locked. Null when it can't be found.
 */
internal fun List<Profile>.kindOf(serial: Long, lockedPrivate: (Long) -> Boolean): ProfileKind? =
    firstOrNull { it.serial == serial }?.kind ?: ProfileKind.PRIVATE.takeIf { lockedPrivate(serial) }

/**
 * Whether what stands for an app's widget names the app (its package, class or label), by its
 * profile's [kind] ([kindOf]): not in a private space, whether listed or hidden while it is locked.
 * A profile that can't be found (null), such as a work profile not on this phone, keeps the name.
 */
internal fun namesApp(kind: ProfileKind?): Boolean = kind != ProfileKind.PRIVATE

/** The widgets page's line for an app's widget that can't show: "[why]", then its package when it [names] the app. */
internal fun placeholderLine(why: String, packageName: String, names: Boolean): String =
    if (names) "[$why] $packageName" else "[$why]"

/**
 * What a row under "Restored from backup" calls its widget, by its profile's [kind] ([kindOf]): the
 * provider's [label], or "Private space widget" where it doesn't name the app ([namesApp]).
 */
internal fun pendingTitle(label: String, kind: ProfileKind?): String = when {
    namesApp(kind) -> label
    else -> "Private space widget"
}

/**
 * True when the profile with [serial] is listed and neither paused nor locked. A private space that
 * Android hides while it is locked isn't listed, so its widget counts as not running.
 */
internal fun List<Profile>.isRunning(serial: Long): Boolean =
    any { it.serial == serial && !it.quiet }

/** What one card on the widgets page can ask for: as [actions], but a long-press calls [arrange], which knows the card. */
private class ArrangeOnLongPress(actions: WidgetCardActions, private val arrange: () -> Unit) :
    WidgetCardActions by actions {
    override fun longPress() = arrange()
}

/**
 * Renders an app's placed widget [w] at [height] via the shared AppWidgetHost. A long-press anywhere
 * on it calls [onLongPress]. When its profile is paused, [placeholder] is shown instead. Its view is
 * visible only while some of it is in the window, so a widget nobody sees doesn't tick.
 *
 * [arranged]: it shows in Arrange widgets, where the card over it speaks for it. Its own buttons are
 * hidden from a screen reader there. The same view comes back to the widgets page, where they are not.
 *
 * A widget Kolahal draws itself ([PlacedWidget.source]) has no app widget id, and is not drawn here.
 * The widgets page and the Widgets screen draw its card themselves ([SourceWidget]).
 */
@Composable
internal fun HostedWidget(
    w: PlacedWidget,
    height: Dp,
    pausedKind: ProfileKind?,
    onLongPress: () -> Unit,
    arranged: Boolean = false,
    placeholder: @Composable (String) -> Unit,
) {
    val id = w.id ?: return
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val providersVersion by widgets.providersVersion.collectAsStateWithLifecycle()
    if (pausedKind != null) {
        placeholder(if (pausedKind == ProfileKind.PRIVATE) "Private space locked" else "${pausedKind.label.replaceFirstChar { it.titlecase() }} profile paused")
        return
    }
    val info = remember(id, providersVersion) { widgets.cachedInfo(id) }
    // Dark mode or the wallpaper's colors changed: the view is made again, in the new colors.
    val redrawn by widgets.redrawn.collectAsStateWithLifecycle()
    if (info == null) {
        placeholder(WidgetWords.UNAVAILABLE)
        return
    }
    val longPress by rememberUpdatedState(onLongPress)
    key(id, redrawn) {
        // The view is visible only while some of it is in the window. The widgets page is composed
        // beside the log, and the page scrolls: a hidden view's clocks, timers and spinners stop
        // (Android tells them through onVisibilityAggregated), so they don't ask home for frames.
        // Its visibility is set during layout, before the frame is drawn: a page that slides in shows its
        // widgets from its first pixel. The page's own signals are too late for that, or not true
        // there: LocalWidgetsShown is false during a swipe and on the Widgets screen, and
        // LocalCardOnScreen changes on the next frame.
        val hosted = remember { arrayOfNulls<View>(1) }
        AndroidView(
            factory = { ctx ->
                // Home is rebuilt after Settings or the Widgets page, but the widget's view is reused.
                widgets.view(ctx, id, info).apply {
                    (this as? KolahalWidgetView)?.onLongPress = { longPress() }
                    // Hidden until its first layout says it is in the window.
                    visibility = View.INVISIBLE
                    importantForAccessibility =
                        if (arranged) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    hosted[0] = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .onVisibilityChanged(minFractionVisible = 0f) { seen ->
                    hosted[0]?.visibility = if (seen) View.VISIBLE else View.INVISIBLE
                },
        )
    }
}


/** Where a child of [WidgetGrid] sits: its first [column], its width in columns ([span]) and its top in dp. */
private data class GridPlace(val column: Int, val span: Int, val topDp: Int) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@GridPlace
}

/**
 * Places this child of a [WidgetGrid] at [column] (of [WidgetLayout.COLUMNS], from the start edge),
 * [span] columns wide, with its top [topDp] from the top of the grid. A column or top below 0 (no
 * place yet) puts it under everything else.
 */
internal fun Modifier.gridPlace(column: Int, span: Int, topDp: Int): Modifier =
    this.then(GridPlace(column, span, topDp))

/** [w] placed in a [WidgetGrid] where its column and top say. */
internal fun Modifier.gridPlace(w: PlacedWidget): Modifier = gridPlace(w.column, w.span, w.topDp)

/**
 * Lays out widgets where they are placed ([gridPlace]): each child at its column and top, as wide as
 * its columns, as tall as it is. Widgets keep [gap] between them, so a column is a twelfth of the
 * width and the gap ([WidgetDrag]).
 * - Columns count from the start edge, so they run right to left in a right-to-left language.
 * - The grid is as tall as its lowest child: [WidgetPlacement.pageHeightDp] for the page's widgets.
 * - A child without a place goes under everything else, at the start edge.
 * - Give the children in reading order ([WidgetPlacement.readingOrder]), so a screen reader goes
 *   through them from the top, then from the start.
 */
@Composable
internal fun WidgetGrid(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val page = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val grid = WidgetDrag(page, gapPx, density)
        val places = measurables.map {
            it.parentData as? GridPlace ?: GridPlace(
                WidgetPlacement.UNPLACED,
                WidgetLayout.COLUMNS,
                WidgetPlacement.UNPLACED
            )
        }
        val placeables = measurables.mapIndexed { i, m ->
            val width = grid.widthPx(places[i].span.coerceIn(1, WidgetLayout.COLUMNS))
            m.measure(Constraints(minWidth = width, maxWidth = width))
        }
        val placed = places.map { it.column >= 0 && it.topDp >= 0 }
        val tops =
            IntArray(measurables.size) { if (placed[it]) places[it].topDp.dp.roundToPx() else 0 }
        // Those without a place go one under the other, under the lowest placed one.
        var next = placeables.indices.filter { placed[it] }
            .maxOfOrNull { tops[it] + placeables[it].height + gapPx } ?: 0
        placeables.indices.filter { !placed[it] }.forEach { i ->
            tops[i] = next
            next += placeables[i].height + gapPx
        }
        val height = placeables.indices.maxOfOrNull { tops[it] + placeables[it].height } ?: 0
        layout(page, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { i, p ->
                val column =
                    if (placed[i]) places[i].column.coerceAtMost(WidgetLayout.COLUMNS - 1) else 0
                p.placeRelative(grid.startPx(column), tops[i])
            }
        }
    }
}

/**
 * A widgets page beside the terminal (`widgets.show`): the page at [location], page 1 unless said.
 * It shows every widget placed on that page where it was put, at its width and height
 * ([WidgetGrid]), on a page that scrolls, in the terminal's colors. Its top is left free for the
 * strip home draws over all its widgets pages ([WidgetsStrip]): the page's name and the marks. A page with
 * no widgets shows [EmptyWidgetsPage]. A long-press on
 * a widget opens Arrange widgets with that widget chosen, and so does one on the line that stands for
 * a widget of a paused profile, a locked private space or a gone app.
 */
@Composable
fun WidgetPanel(vm: TerminalViewModel, location: String = WidgetLocation.PANEL) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val pages by widgets.pages.collectAsStateWithLifecycle()
    val index = WidgetPages.indexOf(pages, location).coerceAtLeast(0)
    val panel = remember(all, location) {
        WidgetPlacement.readingOrder(
            WidgetOrder.inLocation(
                all,
                location
            )
        )
    }
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val version by vm.config.version.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<ActionMenu?>(null) }
    val menuScope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .page(wallpaper = remember(version) { vm.config[Keys.WIDGETS_WALLPAPER] })
            .testTag(if (index == 0) "widgets-page" else "widgets-page-${index + 1}")
    ) {
        // A long-press on a widget opens the screen to arrange widgets. While the app selector is open, the
        // widgets fade out, so only the page's background shows behind the list, as the log does on its page.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - vm.widgetsSelectorFade.floatValue }) {
            WidgetPanelContent(vm, widgets, panel, profiles, density, location = location)
        }
        // The app selector is on this page too, whenever it is on (selector.rail): HomeAppSelector shows nothing otherwise.
        run {
            // The list uses the whole page, from under the status bar (the clock stays clear) to above the
            // navigation bar: there is no header or prompt here. The strip stays where the log has it, between
            // the header and the prompt, so selector.position and selector.height put it under the same thumb
            // on both pages.
            var top by remember { mutableFloatStateOf(0f) }
            val area = vm.selectorArea
            Box(
                Modifier
                    .fillMaxSize()
                    .cutoutSafePadding()
                    .navigationBarsPadding()
                    .onGloballyPositioned { top = it.positionInWindow().y }) {
                HomeAppSelector(
                    vm,
                    fade = vm.widgetsSelectorFade,
                    stripArea = area?.let { (it.top - top)..(it.bottom - top) },
                    onLongPress = { app ->
                        // An app's shortcuts come from the system. Build them off the main thread and show them when
                        // ready, set on the main thread (as HomeState.showMenu).
                        menuScope.launch {
                            val actions = withContext(Dispatchers.IO) { vm.appActions(app) }
                            withContext(Dispatchers.Main.immediate) { menu = ActionMenu(app.label, actions, null) }
                        }
                    },
                )
            }
            menu?.let { ActionSheet(it, onDismiss = { menu = null }) }
        }
    }
}

/**
 * A widgets page's content, under the strip ([belowWidgetsStrip]): the widgets; or, when there are none, the empty page ([EmptyWidgetsPage]) with its
 * Add a widget, and Remove page on a page other than page 1. They work on this page ([location]): a new widget
 * goes on it, Arrange widgets opens on it, and Remove page removes it.
 */
@Composable
private fun WidgetPanelContent(
    vm: TerminalViewModel,
    widgets: WidgetHost,
    panel: List<PlacedWidget>,
    profiles: List<Profile>,
    density: Density,
    location: String,
) {
    // Home is laid out again when a screen closes: the page opens scrolled where it was (vm.widgetsScroll).
    val scroll = rememberScrollState(vm.widgetsScroll[location] ?: 0)
    LaunchedEffect(scroll, location) { snapshotFlow { scroll.value }.collect { vm.widgetsScroll[location] = it } }
    val onAdd = remember(location) { { widgets.addTo = location; vm.openScreen(Screen.WIDGET_PICKER) } }
    // An empty page other than page 1 can be removed from here. Home then opens on the widgets page before it.
    val onRemove: (() -> Unit)? = remember(location) {
        if (location == WidgetLocation.PANEL) null else {
            {
                val pages = widgets.pages.value
                pages.getOrNull(WidgetPages.indexOf(pages, location) - 1)
                    ?.let { vm.homePageShown = HomeOrder.idOf(it.location) }
                widgets.removePage(location)
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .belowWidgetsStrip()
            .navigationBarsPadding()
            // How tall the widgets show without scrolling, less the grid's 4 dp above and below: in Arrange
            // widgets, a widget can be dropped down to there (WidgetHost.measuredPageHeightDp).
            .onSizeChanged {
                widgets.measuredPageHeightDp = with(density) { it.height.toDp().value.toInt() } - 2 * 4
            },
    ) {
        if (panel.size == 0) {
            EmptyWidgetsPage(onAdd, onRemove = onRemove)
            return@Column
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
        ) {
            WidgetGrid(
                WidgetLayout.GAP_DP.dp,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WidgetLayout.GAP_DP.dp, vertical = 4.dp)
                    // The page width in dp. Square and the sizes reported to widgets are based on it.
                    .onSizeChanged {
                        widgets.measuredPageDp = with(density) { it.width.toDp().value.toInt() }
                    },
            ) {
                panel.forEach { w ->
                    key(w.key) {
                        // A long-press opens the Widgets screen with this widget chosen.
                        val arrange =
                            remember(w.key) { { widgets.choose(w.key); vm.openScreen(Screen.WIDGETS) } }
                        val actions = remember(w.key, vm.widgetActions) {
                            ArrangeOnLongPress(
                                vm.widgetActions,
                                arrange
                            )
                        }
                        // Give the grid exactly one child per widget, whatever HostedWidget shows.
                        Box(Modifier.gridPlace(w)) {
                            OnScreenCard {
                                // A script's widget is a card that Kolahal draws. Its taps go to the view model.
                                if (w.source != null) SourceWidget(
                                    w,
                                    w.heightDp.dp,
                                    actions,
                                    vm.widgetFeed
                                )
                                else {
                                    // Android is asked only for a profile the list misses.
                                    val kind = remember(profiles, w.profileSerial) { profiles.kindOf(w.profileSerial, widgets::isLockedPrivate) }
                                    val paused = remember(profiles, w.profileSerial) { profiles.pausedKind(w.profileSerial, widgets::isLockedPrivate) }
                                    val haptics = LocalHapticFeedback.current
                                    HostedWidget(
                                        w = w,
                                        height = w.heightDp.dp,
                                        pausedKind = paused,
                                        onLongPress = arrange,
                                    ) { why ->
                                        // A private space hides its apps: its widget doesn't name one, also
                                        // where Android hides the space while it is locked ([namesApp]).
                                        // A long-press opens Arrange widgets, as on a card. A tap does nothing.
                                        Text(
                                            placeholderLine(why, w.packageName, namesApp(kind)),
                                            style = Term.text.copy(color = Term.dim),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .semantics { onLongClick(label = "arrange widgets") { arrange(); true } }
                                                .pointerInput(arrange) {
                                                    detectTapGestures(onLongPress = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        arrange()
                                                    })
                                                }
                                                .alpha(0.7f)
                                                .padding(vertical = 6.dp, horizontal = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
