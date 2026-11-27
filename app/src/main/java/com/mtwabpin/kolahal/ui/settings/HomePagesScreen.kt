/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.HomeEntry
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.GuiListPage
import com.mtwabpin.kolahal.ui.PageHint
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import com.mtwabpin.kolahal.widgets.WidgetHost

/**
 * Page order (`home.pages`): home's pages from left to right, the log, the widgets pages and the
 * Assistant's, in the order home shows them now ([HomeOrder]).
 * - Each page has a handle: drag it up or down, as in Arrange the header ([HeaderPartsScreen]).
 * - For screen readers, every page has Move up and Move down.
 * - A move applies at once. Reset to default empties the setting: the default order again.
 */
@Composable
internal fun HomePagesScreen(vm: TerminalViewModel) {
    BackHandler { vm.closeScreen() }
    val config = vm.config
    val version by config.version.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val widgetPages by remember { WidgetHost.get(context) }.pages.collectAsStateWithLifecycle()
    val widgetsOn = remember(version) { config[Keys.WIDGETS_SHOW] }
    val pages = if (widgetsOn) widgetPages else emptyList()
    val listed = remember(version) { HomeOrder.listed(config[Keys.HOME_PAGES]) }
    val current = HomeOrder.of(listed, pages, BuildConfig.ASSISTANT && config[Keys.ASSISTANT_PAGE]).entries
    // The rows as shown: a move shows at once. Reset, or a page added or removed elsewhere, comes in from the setting.
    var rows by remember { mutableStateOf(current) }
    LaunchedEffect(current) { rows = current }

    fun save(new: List<HomeEntry>) {
        if (new == rows) return
        rows = new
        vm.setConfig(Keys.HOME_PAGES, new.joinToString(",") { it.id })
    }

    fun move(from: Int, to: Int) =
        save(rows.toMutableList().apply { add(to.coerceIn(0, lastIndex), removeAt(from)) })

    val list = rememberLazyListState()
    // The page being dragged, and its offset from the position where the list lays it out.
    var dragging by remember { mutableStateOf<String?>(null) }
    val offset = remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current

    // Moves the dragged page past the row under its middle, once it is past that row's middle.
    fun follow() {
        val key = dragging ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val item = items.firstOrNull { it.key == key } ?: return
        val from = rows.indexOfFirst { it.id == key }
        if (item.index != from) return
        val middle = item.offset + offset.floatValue + item.size / 2f
        val under = items.firstOrNull {
            it.key != key && it.index < rows.size && middle >= it.offset && middle < it.offset + it.size
        } ?: return
        val half = under.offset + under.size / 2f
        if (if (under.index < item.index) middle > half else middle < half) return
        move(from, under.index)
        offset.floatValue += item.offset - under.offset
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    GuiTheme {
        GuiListPage(
            title = { Text("Page order") },
            subtitle = { Text("Home's pages from left to right. Drag a page by its handle") },
            onBack = vm::closeScreen,
            state = list,
            // A drag that starts on a handle (the end of a row) moves the page; anywhere else, the list
            // scrolls. Tracked on the list, as in Arrange the header: the list stays while the row moves.
            listModifier = Modifier.pointerInput(rtl) {
                val handleWidth = 72.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val onHandle =
                        if (rtl) down.position.x < handleWidth else down.position.x >= size.width - handleWidth
                    if (!onHandle) return@awaitEachGesture
                    val info = list.layoutInfo
                    val y = down.position.y + info.viewportStartOffset
                    val item = info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
                        ?: return@awaitEachGesture
                    if (item.index >= rows.size) return@awaitEachGesture
                    down.consume()
                    dragging = item.key as String
                    offset.floatValue = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    var last = down.position.y
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            offset.floatValue += change.position.y - last
                            last = change.position.y
                            change.consume()
                            follow()
                        }
                    } finally {
                        dragging = null
                        offset.floatValue = 0f
                    }
                }
            },
        ) {
            rows.forEachIndexed { index, entry ->
                item(key = entry.id) {
                    val held = dragging == entry.id
                    val title = HomeOrder.name(entry, pages)
                    val actions = buildList {
                        if (index > 0) add(CustomAccessibilityAction("Move up") { move(index, index - 1); true })
                        if (index < rows.lastIndex) add(CustomAccessibilityAction("Move down") { move(index, index + 1); true })
                    }
                    // Not clickable: the handle and the custom actions move a page.
                    SegmentedListItem(
                        colors = segColors(),
                        shapes = segShapes(0, 1),
                        leadingContent = {
                            Icon(
                                painterResource(
                                    when (entry) {
                                        HomeEntry.Log -> R.drawable.ic_stat_terminal
                                        HomeEntry.Assistant -> R.drawable.ic_forum
                                        is HomeEntry.Widgets -> R.drawable.ic_widgets
                                    }
                                ),
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Icon(
                                painterResource(R.drawable.ic_drag_handle),
                                contentDescription = "Drag $title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("drag-page-${entry.id}"),
                            )
                        },
                        modifier = Modifier
                            .then(
                                if (held) Modifier
                                    .zIndex(1f)
                                    .graphicsLayer { translationY = offset.floatValue }
                                    .shadow(6.dp, MaterialTheme.shapes.large) else Modifier.animateItem()
                            )
                            .padding(vertical = 2.dp)
                            .testTag("page-${entry.id}")
                            .semantics { customActions = actions },
                    ) { Text(title) }
                }
            }
            item(key = "reset") {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.resetConfig(Keys.HOME_PAGES) },
                    shapes = ButtonDefaults.shapes(),
                    enabled = listed.isNotEmpty(),
                    modifier = Modifier.testTag("home-pages-reset"),
                ) { Text("Reset to default") }
            }
            item(key = "hint") {
                PageHint("Home opens on the log, or on widgets page 1 with Start on the widgets page. Back and Home return to it. Terminal: config home.pages")
            }
        }
    }
}
