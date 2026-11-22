/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.tty.BlockCache
import com.mtwabpin.kolahal.terminal.tty.CellBlock
import com.mtwabpin.kolahal.terminal.tty.LineLayout
import com.mtwabpin.kolahal.terminal.tty.LogLook
import com.mtwabpin.kolahal.terminal.tty.screens.ShownItem
import com.mtwabpin.kolahal.terminal.tty.screens.ShownLog
import com.mtwabpin.kolahal.terminal.tty.screens.ShownLogSource
import com.mtwabpin.kolahal.terminal.tty.screens.ShownRows
import com.mtwabpin.kolahal.ui.FoldState
import com.mtwabpin.kolahal.ui.Term
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tells [to] which log lines [list] shows, as cells and where they are ([ShownLog]), while the list
 * is on screen. Both lists of the tty home (the log, a search's results) use it, and the lines are
 * as they have them. Nothing is laid out until it is asked for (`screen decode`).
 *
 * The list is home's: newest first, from the bottom up, the empty log's hints and the hidden lines'
 * row under their keys ([EMPTY_HINT_KEY], [HIDDEN_LINES_KEY]).
 * - [rows]: the list's rows, newest first; [search]: a search's words, or null for the log.
 * - [lines]: the lines the list has laid out.
 *
 * Returns a modifier for the list. Null [to] (a preview): nothing is told.
 */
@Composable
internal fun shownLogReport(
    to: ShownLogSource?,
    list: LazyListState,
    rows: List<LogRow>,
    search: List<String>?,
    folds: FoldState?,
    apps: (LogEntry) -> List<AppEntry>,
    config: Config,
    hiddenLines: Int,
    timeFormat: SimpleDateFormat,
    today: LocalDate,
    lines: BlockCache<LineKey, TtyLine>,
): Modifier {
    if (to == null) return Modifier
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val place = remember { ListPlace() }
    val report = ShownLogReport(
        list, rows, search, folds, apps, config, hiddenLines, timeFormat, today, lines, place, density, rtl, locale,
        installed = { AppRepository.get(context).apps.value },
    )
    val current by rememberUpdatedState(report)
    DisposableEffect(to) {
        val mine = { cellW: Int, cellH: Int -> current.read(cellW, cellH) }
        to.reader = mine
        // Another list of home's may have taken over by now.
        onDispose { if (to.reader === mine) to.reader = null }
    }
    return place.modifier
}

/** The item keys of home's lists besides its rows'. */
internal const val EMPTY_HINT_KEY = "empty-hint"
internal const val HIDDEN_LINES_KEY = "hidden-lines"

/** Where a list is, and the modifier that finds out. */
private class ListPlace {
    var at: LayoutCoordinates? = null
    val modifier: Modifier = Modifier.onPlaced { at = it }
}

/** What [shownLogReport] reads, as it was when the list was last composed. */
private class ShownLogReport(
    private val list: LazyListState,
    private val rows: List<LogRow>,
    private val search: List<String>?,
    private val folds: FoldState?,
    private val apps: (LogEntry) -> List<AppEntry>,
    private val config: Config,
    private val hiddenLines: Int,
    private val timeFormat: SimpleDateFormat,
    private val today: LocalDate,
    private val lines: BlockCache<LineKey, TtyLine>,
    private val place: ListPlace,
    private val density: Density,
    private val rtl: Boolean,
    private val locale: Locale,
    private val installed: () -> List<AppEntry>,
) {
    fun read(cellW: Int, cellH: Int): ShownLog? {
        val at = place.at?.takeIf { it.isAttached } ?: return null
        val info = list.layoutInfo
        val width = at.size.width
        val height = at.size.height
        // The same columns as the log on the grid: whole cells within the margins, in the middle.
        val margin = with(density) { GRID_MARGIN.roundToPx() }
        val cols = ((width - 2 * margin).coerceAtLeast(cellW) / cellW).coerceAtLeast(1)
        val left = ((width - cols * cellW) / 2).coerceAtLeast(0)
        val look = LogLook(
            cols = cols,
            gutter = config[Keys.LOG_GUTTER],
            dim = config[Keys.LOG_DIM] && search == null,
            boxedTables = Term.boxedTables,
            foldLines = if (search != null) 0 else Term.foldLines,
            itemGapRows = 0,
            rtl = rtl,
        )
        val options = LineOptions(if (search != null) TimeMode.ALL else TimeMode.of(config[Keys.SHOW_TIMESTAMPS]), Term.foldLines)
        val items = info.visibleItemsInfo.mapNotNull { item ->
            val (block, gap) = line(item, look, options) ?: return@mapNotNull null
            // The list counts from its bottom, above its padding there.
            ShownItem(block, height - info.beforeContentPadding - item.offset - item.size, item.size, gap)
        }
        val window = at.positionInWindow()
        return ShownLog(
            window.x.roundToInt() + left,
            window.y.roundToInt(),
            ShownRows.onGrid(items, height, cellH),
        )
    }

    /** What [item] shows, and the gap above its line in px; null for an item that shows nothing. */
    private fun line(item: LazyListItemInfo, look: LogLook, options: LineOptions): Pair<CellBlock, Int>? {
        if (item.key == EMPTY_HINT_KEY) return LineLayout.lines(look.cols, EMPTY_HINT, rtl) to 0
        if (item.key == HIDDEN_LINES_KEY) return hiddenLinesBlock(hiddenLines, look.cols, rtl) to 0
        val row = rows.getOrNull(item.index)?.takeIf { it.key == item.key }
            ?: rows.firstOrNull { it.key == item.key }
            ?: return null
        return when (row) {
            is LogRow.Day -> LineLayout.day(dayLabel(row, today, locale), look.cols, rtl) to 0
            is LogRow.Unread -> if (search == null) LineLayout.unread(row.count, look.cols, rtl) to 0 else null
            is LogRow.Entry -> {
                val marks = search.orEmpty()
                val open = folds?.isOpen(row.entry.id) == true
                val appsGrid = LogContext.APPS in row.entry.ctx.values
                val key = LineKey(row, look, options, open, marks, Same(timeFormat), if (appsGrid) Same(installed()) else null)
                val line = lines.get(key) { layoutLine(row, options, look, open, marks, timeFormat, apps, search != null) }
                line.block to gapOf(line.model.gapAbove)
            }
        }
    }

    private fun gapOf(gap: LineModel.Gap): Int = when {
        search != null -> 0
        gap == LineModel.Gap.GROUP -> with(density) { config[Keys.LOG_SPACING].dp.roundToPx() }
        gap == LineModel.Gap.ITEM -> with(density) { config[Keys.LOG_ITEM_SPACING].dp.roundToPx() }
        else -> 0
    }
}

/** A day's divider as the log names it: "Yesterday", or its date. */
internal fun dayLabel(row: LogRow.Day, today: LocalDate, locale: Locale): String =
    if (row.day == today.minusDays(1)) "Yesterday" else row.day.format(TimeFormats.dayFormat(locale))
