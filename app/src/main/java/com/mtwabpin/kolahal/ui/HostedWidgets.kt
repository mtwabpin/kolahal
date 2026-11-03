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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.apps.AppRepository
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


/** Profile state for a widget: paused/locked profiles get a placeholder instead of the widget. */
internal fun List<Profile>.pausedKind(serial: Long): ProfileKind? = firstOrNull { it.serial == serial }?.takeIf { it.quiet }?.kind

/**
 * Renders placed widget [w] at [height] via the shared AppWidgetHost. A long-press anywhere on it
 * calls [onLongPress]. When its profile is paused, [placeholder] is shown instead.
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
                // Home is built again after Settings or the Widgets page; the widget's view is kept.
                widgets.view(ctx, id, info).apply {
                    (this as? KolahalWidgetView)?.onLongPress = { longPress?.invoke() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}


/**
 * Widgets in page order, [spans] twelfths of the width each ([WidgetLayout.COLUMNS]): side by side
 * while they fit, a new row when the next one doesn't, [gap] between them across and down. A row is
 * as tall as its tallest widget, the others at its top. Rows run from the start: right to left in a
 * right-to-left language. One child per widget.
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
 * The widgets page beside the terminal (`widgets.page`): every widget placed on the "Widgets page",
 * at its width and height ([WidgetFlow]), scrolling, in the terminal's colours. Add / edit jump to
 * the GUI pages.
 */
@Composable
fun WidgetPanel(vm: TerminalViewModel) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val panel = remember(all) { WidgetOrder.inLocation(all, WidgetLocation.PANEL) }
    val profiles by remember { AppRepository.get(context).profiles }.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val version by vm.config.version.collectAsStateWithLifecycle()
    // Home opens here (widgets.start): the app selector is here too, as on the log.
    val first = remember(version) { vm.config[Keys.WIDGETS_START] }
    var menu by remember { mutableStateOf<ActionMenu?>(null) }
    val menuScope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().page(wallpaper = remember(version) { vm.config[Keys.WIDGETS_WALLPAPER] }).testTag("widgets-page")) {
        // As home's first page it has no title row: a widget's long-press arranges them.
        WidgetPanelContent(vm, widgets, panel, profiles, density, titleRow = !first)
        if (first) {
            // Where the log has its selector, between its header and prompt: the same edges and height,
            // so selector.position and the selector's height put it in the same place on both pages.
            var top by remember { mutableFloatStateOf(0f) }
            val area = vm.selectorArea
            Box(Modifier.fillMaxSize().cutoutSafePadding().navigationBarsPadding().onGloballyPositioned { top = it.positionInWindow().y }) {
                Box(
                    if (area == null) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth().offset { IntOffset(0, (area.top - top).roundToInt()) }.height(with(density) { area.height.toDp() })
                ) {
                    HomeAppSelector(vm, onLongPress = { app ->
                        // An app's shortcuts come from the system: built off the main thread, shown when ready.
                        menuScope.launch { menu = ActionMenu(app.label, withContext(Dispatchers.IO) { vm.appActions(app) }, null) }
                    })
                }
            }
            menu?.let { ActionSheet(it, onDismiss = { menu = null }) }
        }
    }
}

/** The widgets page's own content: its title row ([titleRow]) and the widgets, or what to do with none. */
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
                // No + Add above it: the line itself adds one.
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
                // What Square and the sizes told to widgets go by.
                .onSizeChanged { widgets.measuredPageDp = with(density) { it.width.toDp().value.toInt() } },
        ) {
            panel.forEach { w ->
                key(w.key) {
                    // One child per widget for the flow, whatever HostedWidget shows.
                    Box {
                        HostedWidget(
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
