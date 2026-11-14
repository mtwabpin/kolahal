package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.KolahalWidgetView
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetOrder
import com.mtwabpin.kolahal.widgets.WidgetLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged


/** Returns the kind of the profile with [serial] if it is paused/locked, else null. A paused/locked profile's widget shows a placeholder instead. */
internal fun List<Profile>.pausedKind(serial: Long): ProfileKind? = firstOrNull { it.serial == serial }?.takeIf { it.quiet }?.kind

/**
 * Renders an app's placed widget [w] at [height] via the shared AppWidgetHost. A long-press anywhere
 * on it calls [onLongPress]. When its profile is paused, [placeholder] is shown instead.
 *
 * A widget Kolahal draws itself ([PlacedWidget.source]) has no app widget id, and is not drawn here.
 * The widgets page and the Widgets screen draw its card themselves ([SourceWidget]).
 */
@Composable
internal fun HostedWidget(
    w: PlacedWidget,
    height: Dp,
    pausedKind: ProfileKind?,
    onLongPress: (() -> Unit)?,
    placeholder: @Composable (String) -> Unit,
) {
    val id = w.id ?: return
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val providersVersion by widgets.providersVersion.collectAsStateWithLifecycle()
    if (pausedKind != null) {
        placeholder(if (pausedKind == ProfileKind.PRIVATE) "private space locked" else "${pausedKind.label} profile paused")
        return
    }
    val info = remember(id, providersVersion) { widgets.cachedInfo(id) }
    if (info == null) {
        placeholder("widget unavailable")
        return
    }
    val longPress by rememberUpdatedState(onLongPress)
    key(id) {
        AndroidView(
            factory = { ctx ->
                // Home is rebuilt after Settings or the Widgets page, but the widget's view is reused.
                widgets.view(ctx, id, info).apply {
                    (this as? KolahalWidgetView)?.onLongPress = { longPress?.invoke() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}


/**
 * Lays out widgets in page order. Each widget is [spans] twelfths of the width ([WidgetLayout.COLUMNS]).
 * - Widgets sit side by side while they fit. The next one that does not fit starts a new row.
 * - [gap] separates widgets horizontally and vertically.
 * - A row is as tall as its tallest widget. Shorter widgets align to the row's top.
 * - Rows start at the start edge, so they run right to left in a right-to-left language.
 * - Give one child per widget.
 */
@Composable
internal fun WidgetFlow(spans: List<Int>, gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val page = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val rows = WidgetLayout.rows(List(measurables.size) { spans.getOrElse(it) { WidgetLayout.COLUMNS } })
        val placeables = measurables.mapIndexed { i, m ->
            val span = spans.getOrElse(i) { WidgetLayout.COLUMNS }.coerceIn(WidgetLayout.MIN_SPAN, WidgetLayout.COLUMNS)
            val width = ((page + gapPx) * span / WidgetLayout.COLUMNS - gapPx).coerceAtLeast(0)
            m.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
        }
        val rowHeights = rows.map { row -> row.maxOf { placeables[it].height } }
        val height = rowHeights.sum() + gapPx * (rows.size - 1).coerceAtLeast(0)
        layout(page, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            rows.forEachIndexed { r, row ->
                var x = 0
                row.forEach { i ->
                    placeables[i].placeRelative(x, y)
                    x += placeables[i].width + gapPx
                }
                y += rowHeights[r] + gapPx
            }
        }
    }
}

/**
 * The widgets page beside the terminal (`widgets.page`). It shows every widget placed on the
 * "Widgets page" at its width and height ([WidgetFlow]), in a scrolling list, in the terminal's
 * colours. Add and Edit open the GUI pages.
 */
@Composable
fun WidgetPanel(vm: TerminalViewModel) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val panel = remember(all) { WidgetOrder.inLocation(all, WidgetLocation.PANEL) }
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val version by vm.config.version.collectAsStateWithLifecycle()
    // When home opens on this page (widgets.start), it has no title row.
    val first = remember(version) { Keys.widgetsFirst(vm.config) }
    var menu by remember { mutableStateOf<ActionMenu?>(null) }
    val menuScope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().page(wallpaper = remember(version) { vm.config[Keys.WIDGETS_WALLPAPER] }).testTag("widgets-page")) {
        // As home's first page, it has no title row. A long-press on a widget opens the screen to arrange widgets.
        // While the app selector is open, the widgets fade out, so only the page's background shows behind
        // the list, as the log does on its page.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - vm.widgetsSelectorFade.floatValue }) {
            WidgetPanelContent(vm, widgets, panel, profiles, density, titleRow = !first)
        }
        // The app selector is on this page too, whenever it is on (selector.rail): HomeAppSelector shows nothing otherwise.
        run {
            // The list uses the whole page, from under the status bar (the clock stays clear) to above the
            // navigation bar: there is no header or prompt here. The strip stays where the log has it, between
            // the header and the prompt, so selector.position and selector.height put it under the same thumb
            // on both pages.
            var top by remember { mutableFloatStateOf(0f) }
            val area = vm.selectorArea
            Box(Modifier.fillMaxSize().cutoutSafePadding().navigationBarsPadding().onGloballyPositioned { top = it.positionInWindow().y }) {
                HomeAppSelector(
                    vm,
                    fade = vm.widgetsSelectorFade,
                    stripArea = area?.let { (it.top - top)..(it.bottom - top) },
                    onLongPress = { app ->
                        // An app's shortcuts come from the system. Build them off the main thread and show them when ready.
                        menuScope.launch { menu = ActionMenu(app.label, withContext(Dispatchers.IO) { vm.appActions(app) }, null) }
                    },
                )
            }
            menu?.let { ActionSheet(it, onDismiss = { menu = null }) }
        }
    }
}

/** The widgets page content: its title row (if [titleRow]) and the widgets, or a hint when there are none. */
@Composable
private fun WidgetPanelContent(
    vm: TerminalViewModel,
    widgets: WidgetHost,
    panel: List<PlacedWidget>,
    profiles: List<Profile>,
    density: androidx.compose.ui.unit.Density,
    titleRow: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .cutoutSafePadding()
            .navigationBarsPadding(),
    ) {
        if (titleRow) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text("Widgets", style = Term.text.copy(color = Term.prompt, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(
                "+ Add",
                style = Term.text.copy(color = Term.prompt),
                modifier = Modifier
                    .clickable { vm.openScreen(Screen.WIDGET_PICKER) }
                    .heightIn(min = 48.dp)
                    .wrapContentHeight()
                    .padding(horizontal = 12.dp),
            )
            Text(
                "Edit",
                style = Term.text.copy(color = Term.dim),
                modifier = Modifier
                    .clickable { vm.openScreen(Screen.WIDGETS) }
                    .heightIn(min = 48.dp)
                    .wrapContentHeight()
                    .padding(start = 12.dp),
            )
        }
        if (panel.isEmpty()) {
            if (titleRow) {
                Text(
                    "No widgets here yet · tap + Add",
                    style = Term.text.copy(color = Term.dim),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                )
            } else {
                // There is no + Add button above, so tapping this line adds a widget.
                Text(
                    "No widgets here yet · tap to add one",
                    style = Term.text.copy(color = Term.dim),
                    modifier = Modifier
                        .clickable { vm.openScreen(Screen.WIDGET_PICKER) }
                        .heightIn(min = 48.dp)
                        .wrapContentHeight()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                )
            }
            return@Column
        }
        WidgetFlow(
            panel.map { it.span },
            WidgetLayout.GAP_DP.dp,
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = WidgetLayout.GAP_DP.dp, vertical = 4.dp)
                // The page width in dp. Square and the sizes reported to widgets are based on it.
                .onSizeChanged { widgets.measuredPageDp = with(density) { it.width.toDp().value.toInt() } },
        ) {
            panel.forEach { w ->
                key(w.key) {
                    // Give the flow exactly one child per widget, whatever HostedWidget shows.
                    Box {
                        // A script's widget is a card that Kolahal draws. Its taps go to the view model.
                        if (w.source != null) SourceWidget(w, w.heightDp.dp, vm.widgetActions, vm.widgetFeed)
                        else HostedWidget(
                            w = w,
                            height = w.heightDp.dp,
                            pausedKind = profiles.pausedKind(w.profileSerial),
                            onLongPress = { vm.openScreen(Screen.WIDGETS) },
                        ) { why ->
                            Text(
                                "[$why] ${w.packageName}",
                                style = Term.text.copy(color = Term.dim),
                                modifier = Modifier.fillMaxWidth().alpha(0.7f).padding(vertical = 6.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
