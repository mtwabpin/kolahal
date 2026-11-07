package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
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
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.headerSide
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/** A side of the header in the arrangement list. [HIDDEN] holds the parts the header does not show. */
internal enum class HeaderSide(val title: String) { LEFT("Left side"), RIGHT("Right side"), HIDDEN("Not shown") }

/** A row in the header arrangement: either a side's heading or one of the parts under it. */
internal sealed interface ArrangeRow {
    val key: String

    data class Heading(val side: HeaderSide) : ArrangeRow {
        override val key get() = "side:${side.name}"
    }

    data class Part(val kind: String) : ArrangeRow {
        override val key get() = "part:$kind"
    }
}

/**
 * The header's parts as one list under three headings (left, right, not shown). This logic does not
 * depend on Compose. A part belongs to the heading above it, so moving a part past a heading moves
 * it to that side.
 */
internal object HeaderArrangement {
    /** Builds the rows from [left] and [right] (`header.left`, `header.right`). A part on both sides stays on the left. */
    fun rows(left: List<String>, right: List<String>): List<ArrangeRow> {
        val r = right.filter { it !in left }
        val hidden = Keys.HEADER_PARTS.filter { it !in left && it !in r }
        return listOf<ArrangeRow>(ArrangeRow.Heading(HeaderSide.LEFT)) + left.map { ArrangeRow.Part(it) } +
            ArrangeRow.Heading(HeaderSide.RIGHT) + r.map { ArrangeRow.Part(it) } +
            ArrangeRow.Heading(HeaderSide.HIDDEN) + hidden.map { ArrangeRow.Part(it) }
    }

    /** Returns the parts on the left and right sides, in the order of [rows]. */
    fun sides(rows: List<ArrangeRow>): Pair<List<String>, List<String>> {
        val left = mutableListOf<String>()
        val right = mutableListOf<String>()
        var side = HeaderSide.LEFT
        for (row in rows) when (row) {
            is ArrangeRow.Heading -> side = row.side
            is ArrangeRow.Part -> when (side) {
                HeaderSide.LEFT -> left += row.kind
                HeaderSide.RIGHT -> right += row.kind
                HeaderSide.HIDDEN -> Unit
            }
        }
        return left to right
    }

    /** Returns the side of the row at [index], which is the nearest heading above it. */
    fun sideOf(rows: List<ArrangeRow>, index: Int): HeaderSide =
        (rows.take(index + 1).last { it is ArrangeRow.Heading } as ArrangeRow.Heading).side

    /** Returns [rows] with the part at [from] moved to [to]. Nothing can move above the first heading, and headings do not move. */
    fun move(rows: List<ArrangeRow>, from: Int, to: Int): List<ArrangeRow> {
        val target = to.coerceIn(1, rows.lastIndex)
        if (from == target || rows.getOrNull(from) !is ArrangeRow.Part) return rows
        return rows.toMutableList().apply { add(target, removeAt(from)) }
    }

    /** Returns [rows] with the part at [index] moved to the end of [side]. */
    fun moveTo(rows: List<ArrangeRow>, index: Int, side: HeaderSide): List<ArrangeRow> {
        val part = rows.getOrNull(index) as? ArrangeRow.Part ?: return rows
        val rest = rows.toMutableList().apply { removeAt(index) }
        val heading = rest.indexOfFirst { it is ArrangeRow.Heading && it.side == side }
        val end = (heading + 1 until rest.size).firstOrNull { rest[it] is ArrangeRow.Heading } ?: rest.size
        rest.add(end, part)
        return rest
    }
}

/** For each part: its name in the arrangement, a description of what it shows, and its icon. */
private val PART_INFO: Map<String, Triple<String, String, Int>> = mapOf(
    "time" to Triple("Time", "12- or 24-hour, as the clock setting says", R.drawable.ic_schedule),
    "date" to Triple("Date", "The day and date", R.drawable.ic_calendar),
    "battery" to Triple("Battery", "Its level, green while charging", R.drawable.ic_battery),
    "alarm" to Triple("Next alarm", "Its time, and its day when it's a day or more away; only while one is set", R.drawable.ic_alarm),
    "todo" to Triple("Todos", "How many are open; only while some are", R.drawable.ic_check),
    "network" to Triple("Network", "Wi-Fi, mobile data or offline", R.drawable.ic_wifi),
    "unlocks" to Triple("Unlocks", "Today's count", R.drawable.ic_lock_open),
    "profiles" to Triple("Profiles", "Work and private, on or paused; only when you have them", R.drawable.ic_work),
    "settings" to Triple("Settings", "A button that opens Settings", R.drawable.ic_settings),
)

/**
 * The screen to arrange the header.
 * - Parts are listed under Left side, Right side and Not shown.
 * - Each part has a handle. Drag it up or down; drag it past a heading to move it to another side.
 * - A live preview of the top of home shows above the list.
 * - For screen readers, every part has Move up, Move down and a move to each side.
 */
@Composable
internal fun HeaderPartsScreen(vm: TerminalViewModel) {
    BackHandler { vm.closeScreen() }
    val config = vm.config
    var rows by remember { mutableStateOf(HeaderArrangement.rows(headerSide(config[Keys.HEADER_LEFT]), headerSide(config[Keys.HEADER_RIGHT]))) }
    fun save(new: List<ArrangeRow>) {
        if (new == rows) return
        rows = new
        val (left, right) = HeaderArrangement.sides(new)
        vm.setConfig(Keys.HEADER_LEFT, left.joinToString(",").ifEmpty { "none" })
        vm.setConfig(Keys.HEADER_RIGHT, right.joinToString(",").ifEmpty { "none" })
    }

    val list = rememberLazyListState()
    // The part being dragged, and its offset from the position where the list lays it out.
    var dragging by remember { mutableStateOf<String?>(null) }
    val offset = remember { mutableFloatStateOf(0f) }
    // A tick when a part is picked up and each time it passes another row, as the system's lists do.
    val haptics = LocalHapticFeedback.current

    /**
     * Moves the dragged part to the row under its middle, if that is another row and the part is
     * past that row's middle. Until the list lays out the last move, its rows are still in their
     * old places, so this waits for that before the next move.
     */
    fun follow() {
        val key = dragging ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val item = items.firstOrNull { it.key == key } ?: return
        if (item.index != rows.indexOfFirst { it.key == key }) return
        val middle = item.offset + offset.floatValue + item.size / 2f
        val under = items.firstOrNull { it.key != key && middle >= it.offset && middle < it.offset + it.size } ?: return
        if (under.index == 0) return
        // Require passing the other row's middle, not just touching it. Rows have different heights,
        // so a small row moved above a tall one would still overlap it and move back again.
        val half = under.offset + under.size / 2f
        if (if (under.index < item.index) middle > half else middle < half) return
        save(HeaderArrangement.move(rows, item.index, under.index))
        offset.floatValue += item.offset - under.offset
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    // While a part is held near the top or bottom of the list, the list scrolls.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val step = with(LocalDensity.current) { 6.dp.toPx() }
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val key = dragging ?: break
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = item.offset + offset.floatValue
            val bottom = top + item.size
            val by = when {
                bottom > info.viewportEndOffset - edge -> step
                top < info.viewportStartOffset + edge -> -step
                else -> 0f
            }
            if (by != 0f) offset.floatValue += list.scrollBy(by)
            // Every frame, run any move that was waiting for the list to lay out the last one.
            follow()
        }
    }

    GuiTheme {
        GuiListPage(
            title = { Text("Arrange the header") },
            subtitle = { Text("Drag a part by its handle; past a heading, it goes to that side") },
            onBack = vm::closeScreen,
            pinned = { HeaderPreview(vm) },
            state = list,
            // A drag that starts on a handle (the end of a part: the right, or the left in a
            // right-to-left language) moves the part. Anywhere else, the list scrolls. The drag is
            // tracked on the list, not on the part, because the list stays in place while the part
            // moves under the finger.
            listModifier = Modifier.pointerInput(rtl) {
                val handleWidth = 72.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val onHandle = if (rtl) down.position.x < handleWidth else down.position.x >= size.width - handleWidth
                    if (!onHandle) return@awaitEachGesture
                    val info = list.layoutInfo
                    val y = down.position.y + info.viewportStartOffset
                    val item = info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return@awaitEachGesture
                    if (rows.getOrNull(item.index) !is ArrangeRow.Part) return@awaitEachGesture
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
            rows.forEachIndexed { index, row ->
                when (row) {
                    is ArrangeRow.Heading -> item(key = row.key) {
                        GroupHeader(row.side.title)
                    }
                    is ArrangeRow.Part -> item(key = row.key) {
                        val held = dragging == row.key
                        val (title, detail, icon) = PART_INFO.getValue(row.kind)
                        val side = HeaderArrangement.sideOf(rows, index)
                        val actions = buildList {
                            if (index > 1) add(CustomAccessibilityAction("Move up") { save(HeaderArrangement.move(rows, index, index - 1)); true })
                            if (index < rows.lastIndex) add(CustomAccessibilityAction("Move down") { save(HeaderArrangement.move(rows, index, index + 1)); true })
                            HeaderSide.entries.filter { it != side }.forEach { to ->
                                add(CustomAccessibilityAction(if (to == HeaderSide.HIDDEN) "Don't show" else "Move to the ${to.title.lowercase()}") {
                                    save(HeaderArrangement.moveTo(rows, index, to)); true
                                })
                            }
                        }
                        // Not clickable: a tap does nothing, so it shows no ripple and a screen reader
                        // offers no "double-tap". The handle and the custom actions move a part.
                        SegmentedListItem(
                            colors = segColors(),
                            shapes = segShapes(0, 1),
                            leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
                            supportingContent = { Text(detail) },
                            trailingContent = {
                                Icon(
                                    painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = "Drag $title",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("drag-${row.kind}"),
                                )
                            },
                            modifier = Modifier
                                .then(if (held) Modifier.zIndex(1f).graphicsLayer { translationY = offset.floatValue }.shadow(6.dp, MaterialTheme.shapes.large) else Modifier.animateItem())
                                .padding(vertical = 2.dp)
                                .testTag("part-${row.kind}")
                                .semantics { customActions = actions },
                        ) { Text(title) }
                    }
                }
            }
            item(key = "hint") {
                Spacer(Modifier.height(8.dp))
                PageHint("Each side shows its parts from the left. What doesn't fit goes from the middle out, so the outermost parts stay longest. Terminal: config header.left, config header.right")
            }
        }
    }
}
