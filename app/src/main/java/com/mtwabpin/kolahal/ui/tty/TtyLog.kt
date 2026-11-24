/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.HomeStyles
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.Scramble
import com.mtwabpin.kolahal.terminal.Target
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.lineModel
import com.mtwabpin.kolahal.terminal.spoken
import com.mtwabpin.kolahal.terminal.tty.BlockCache
import com.mtwabpin.kolahal.terminal.tty.CellBlock
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellRect
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import com.mtwabpin.kolahal.terminal.tty.GridApp
import com.mtwabpin.kolahal.terminal.tty.LineLayout
import com.mtwabpin.kolahal.terminal.tty.LogLook
import com.mtwabpin.kolahal.terminal.tty.screens.ShownLogSource
import com.mtwabpin.kolahal.ui.home.FoldState
import com.mtwabpin.kolahal.ui.home.HomeCalls
import com.mtwabpin.kolahal.ui.home.HomeState
import com.mtwabpin.kolahal.ui.home.foundIn
import com.mtwabpin.kolahal.ui.home.initials
import com.mtwabpin.kolahal.ui.theme.Term
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import kotlin.math.sign

/** How near a target a tap must be to hit it (cells are small). `log.taller-taps` is the gui style's ([HomeStyles.GUI_ONLY]). */
private val SLOP = 24.dp

/** Lines laid out and kept: the screen's worth many times over, so a scroll back lays out nothing. */
private const val KEPT_LINES = 400

/** How long a finger rests on a line before it shows as pressed. */
private const val PRESS_SHOWS_MS = 80L

/**
 * Home's log on the character grid (`home.style tty`): the same rows, newest at the bottom, in the
 * same list state as the GUI's log, so a switch keeps where the log is scrolled. Each line is a
 * block of cells ([LineLayout]) laid out once and painted on every frame; a tap goes to what is
 * under the finger, as in the GUI: the line, a link, a button, an app, the fold.
 * - [rows]: the log's rows, newest first.
 * - [modifier]: the GUI's modifier for its log list ([com.mtwabpin.kolahal.ui.home.LogArea]).
 */
@Composable
internal fun TtyLog(
    rows: List<LogRow>,
    state: HomeState,
    calls: HomeCalls,
    config: Config,
    configVersion: Int,
    hiddenLines: Int,
    rowsLoaded: Boolean,
    timeFormat: SimpleDateFormat,
    today: LocalDate,
    deleteRight: Boolean,
    modifier: Modifier,
) {
    val folds = state.folds
    val lineCalls = remember(calls, folds) {
        TtyLineCalls(
            onTap = calls.onTap,
            onLongPress = calls.onLineLongPress,
            onOpenUrl = calls.onOpenUrl,
            onAction = calls.onAction,
            onAppTap = calls.onAppTap,
            onAppLongPress = calls.onAppLongPress,
            onToggleFold = folds::toggle,
            onSwipeCopy = calls.onSwipeCopy,
            onSwipeDelete = calls.onSwipeDelete,
        )
    }
    TtyList(
        rows = rows,
        list = state.scroll.list,
        search = null,
        folds = folds,
        shownLog = calls.shownLog,
        highlight = state.menu?.entryId to state.scroll.flash,
        calls = lineCalls,
        apps = calls.apps,
        config = config,
        configVersion = configVersion,
        hiddenLines = hiddenLines,
        rowsLoaded = rowsLoaded,
        onShowHidden = calls.onShowHidden,
        timeFormat = timeFormat,
        today = today,
        deleteRight = deleteRight,
        modifier = modifier,
    )
}

/**
 * A log search's results on the grid, in place of the log: the matching lines, newest at the
 * bottom, each with its time, the search's words marked, and where words the line doesn't show
 * were found under it. A tap shows the line in the log; long press and swipes work as in the log.
 */
@Composable
internal fun TtySearchResults(
    search: TerminalViewModel.LogSearch,
    menuEntryId: Long?,
    calls: HomeCalls,
    config: Config,
    configVersion: Int,
    timeFormat: SimpleDateFormat,
    today: LocalDate,
    deleteRight: Boolean,
    modifier: Modifier,
) {
    val reversed = remember(search.rows) { search.rows.asReversed() }
    val list = rememberLazyListState()
    // When the words change, scroll back to the newest match, next to the prompt.
    LaunchedEffect(search.words) { list.scrollToItem(0) }
    val lineCalls = remember(calls) {
        TtyLineCalls(
            onTap = calls.onShowInLog,
            onLongPress = calls.onLineLongPress,
            onOpenUrl = calls.onOpenUrl,
            // As in the GUI's results: a line's buttons work in the log, not here.
            onAction = { _, _ -> },
            onAppTap = calls.onAppTap,
            onAppLongPress = calls.onAppLongPress,
            onToggleFold = {},
            onSwipeCopy = calls.onSwipeCopy,
            onSwipeDelete = calls.onSwipeDelete,
        )
    }
    TtyList(
        rows = reversed,
        list = list,
        search = search.words,
        folds = null,
        shownLog = calls.shownLog,
        highlight = menuEntryId to null,
        calls = lineCalls,
        apps = calls.apps,
        config = config,
        configVersion = configVersion,
        hiddenLines = 0,
        rowsLoaded = true,
        onShowHidden = {},
        timeFormat = timeFormat,
        today = today,
        deleteRight = deleteRight,
        modifier = modifier.testTag("log-search"),
    )
}

/**
 * The list both draw: [rows] newest first, each a block of cells on one grid. [search] is a
 * search's words (then the lines show their time, never fold or dim, and say where words were
 * found), or null for the log. [highlight] is the line whose menu is open and the line a search
 * result jumped to: both get the surface color, as in the GUI.
 */
@Composable
private fun TtyList(
    rows: List<LogRow>,
    list: LazyListState,
    search: List<String>?,
    folds: FoldState?,
    shownLog: ShownLogSource?,
    highlight: Pair<Long?, Long?>,
    calls: TtyLineCalls,
    apps: (LogEntry) -> List<AppEntry>,
    config: Config,
    configVersion: Int,
    hiddenLines: Int,
    rowsLoaded: Boolean,
    onShowHidden: () -> Unit,
    timeFormat: SimpleDateFormat,
    today: LocalDate,
    deleteRight: Boolean,
    modifier: Modifier,
) {
    val grid = rememberTtyGrid()
    val m = grid.metrics
    val density = LocalDensity.current
    val context = LocalContext.current
    // An apps grid finds its apps again when apps are installed or removed, as the GUI's chips do.
    val installed by AppRepository.get(context).apps.collectAsStateWithLifecycle()
    // Lines laid out, the most recently shown kept ([KEPT_LINES]): bounded, so hours of scrolling stay
    // flat, and let go with the screen's other caches when home is away or memory is short.
    val cache = remember { TtyCaches.add(BlockCache<LineKey, TtyLine>(KEPT_LINES)) }
    val labels = remember { SwipeLabels() }
    val timeMode = remember(configVersion) { TimeMode.of(config[Keys.SHOW_TIMESTAMPS]) }
    val gutter = remember(configVersion) { config[Keys.LOG_GUTTER] }
    val dim = remember(configVersion) { config[Keys.LOG_DIM] }
    val groupGap = remember(configVersion) { config[Keys.LOG_SPACING] }
    val options = remember(search, timeMode, Term.foldLines) {
        LineOptions(if (search != null) TimeMode.ALL else timeMode, Term.foldLines)
    }
    val marks = search.orEmpty()
    // In a right-to-left layout the grid turns: the gutter on the right, the time on the left.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The lines on screen, for `screen decode`.
    val report = shownLogReport(
        shownLog, list, rows, search, folds, apps, config, hiddenLines, timeFormat, today, cache,
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else m.cellW * 40
        val margin = with(density) { GRID_MARGIN.roundToPx() }
        val cols = m.cols((width - 2 * margin).coerceAtLeast(m.cellW))
        val originX = ((width - cols * m.cellW) / 2).coerceAtLeast(0)
        // log.spacing is one empty line of the grid above a command and its output, whatever its dp
        // (HomeStyles.ttyGroupGapRows), and none at 0: every line stays on the grid. A list's items have
        // no gap between them (HomeStyles.TTY_ITEM_GAP_DP), whatever log.item-spacing says. Search
        // results have no gaps.
        val groupGapPx = if (search != null) 0 else HomeStyles.ttyGroupGapRows(groupGap) * m.cellH
        val itemGapPx = if (search != null) 0 else with(density) { HomeStyles.TTY_ITEM_GAP_DP.dp.roundToPx() }
        val look = LogLook(
            cols = cols,
            gutter = gutter,
            dim = dim && search == null,
            boxedTables = Term.boxedTables,
            foldLines = if (search != null) 0 else Term.foldLines,
            itemGapRows = 0,
            rtl = rtl,
        )
        LazyColumn(
            state = list,
            reverseLayout = true,
            modifier = modifier.then(report),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            // Not before the first read: a cold start would flash it over a log that isn't empty.
            if (search == null && rows.isEmpty() && hiddenLines == 0 && rowsLoaded) item(key = EMPTY_HINT_KEY) {
                val block = remember(cols, rtl) { LineLayout.lines(cols, EMPTY_HINT, rtl) }
                TtyText(block, grid, originX, Modifier
                    .testTag("empty-hint")
                    .semantics { text = AnnotatedString(EMPTY_HINT.joinToString("\n") { it.first }.trim()) })
            }
            items(rows, key = { it.key }, contentType = { it.javaClass }) { row ->
                when (row) {
                    is LogRow.Day -> {
                        // Built on each composition, not remembered, so the dividers follow a change of language.
                        val label = dayLabel(row, today, LocalConfiguration.current.locales[0])
                        val block = remember(label, cols, rtl) { LineLayout.day(label, cols, rtl) }
                        TtyText(block, grid, originX, Modifier.semantics {
                            heading()
                            text = AnnotatedString(label)
                        })
                    }

                    is LogRow.Unread -> if (search == null) {
                        val block = remember(row.count, cols, rtl) { LineLayout.unread(row.count, cols, rtl) }
                        TtyText(block, grid, originX, Modifier.semantics {
                            text = AnnotatedString("${row.count} new since you left")
                        })
                    }

                    is LogRow.Entry -> {
                        val open = folds?.isOpen(row.entry.id) == true
                        val appsGrid = LogContext.APPS in row.entry.ctx.values
                        val key = LineKey(row, look, options, open, marks, Same(timeFormat), if (appsGrid) Same(installed) else null)
                        val line = remember(key) {
                            cache.get(key) { layoutLine(row, options, look, open, marks, timeFormat, apps, search != null) }
                        }
                        val id = row.entry.id
                        TtyLineRow(
                            line = line,
                            grid = grid,
                            originX = originX,
                            gapPx = when (line.model.gapAbove) {
                                LineModel.Gap.GROUP -> groupGapPx
                                LineModel.Gap.ITEM -> itemGapPx
                                else -> 0
                            },
                            // The gutter runs through an item's gap, as the GUI's color bar does.
                            gapGutter = if (gutter && line.model.gapAbove == LineModel.Gap.ITEM) {
                                grid.palette.text(LineLayout.gutterStyle(line.model.gutter))
                            } else 0,
                            highlighted = highlight.first == id || highlight.second == id,
                            deleteRight = deleteRight,
                            calls = calls,
                            labels = labels,
                        )
                    }
                }
            }
            // log.fresh: older output is hidden, not deleted. The last item is the top of the reversed list.
            if (search == null && hiddenLines > 0) item(key = HIDDEN_LINES_KEY) {
                val text = hiddenLinesText(hiddenLines)
                val block = remember(text, cols, rtl) { hiddenLinesBlock(hiddenLines, cols, rtl) }
                TtyText(
                    block, grid, originX, Modifier
                        .clickable(onClickLabel = "show earlier output") { onShowHidden() }
                        .semantics { this.text = AnnotatedString(text) })
            }
        }
    }
}

/** The empty log's hints: what to do, then the other tips, muted. */
internal val EMPTY_HINT = listOf(
    "" to CellStyle.PLAIN,
    "Type an app name or a command" to CellStyle.PLAIN,
    "" to CellStyle.PLAIN,
    "help lists everything\n↑ recalls history\n/ searches the log\nTap a line to use it, long-press for more\n⚙ settings" to CellStyle.of(Ink.DIM),
)

private val MUTED = CellStyle.of(Ink.DIM)

private fun hiddenLinesText(n: Int) = "▴ $n earlier lines · tap to show"

/** The row for the [n] lines that log.fresh hides, under a blank one. */
internal fun hiddenLinesBlock(n: Int, cols: Int, rtl: Boolean): CellBlock =
    LineLayout.lines(cols, listOf("" to CellStyle.PLAIN, hiddenLinesText(n) to MUTED), rtl)

/** [row]'s line, laid out: its model, its time as shown, an apps grid's apps, and its cells. */
internal fun layoutLine(
    row: LogRow.Entry,
    options: LineOptions,
    look: LogLook,
    open: Boolean,
    marks: List<String>,
    format: SimpleDateFormat,
    apps: (LogEntry) -> List<AppEntry>,
    found: Boolean,
): TtyLine {
    // No icons on the grid: a notification's line ends with its app's name.
    val model = lineModel(row, options)
    // A scrambled line (`scramble`): its time and its apps' names are scrambled with it.
    val scramble = Scramble.of(row.entry)
    val id = row.entry.id
    val time = model.time?.let { format.format(Date(it)) }?.let { t -> scramble?.line(t, id, Scramble.TIME) ?: t }
    val shown = if (model.body is LineModel.Body.AppGrid) apps(model.entry) else emptyList()
    val block = LineLayout.layout(
        model = model,
        look = look,
        time = time,
        foldOpen = open,
        marks = marks,
        apps = shown.mapIndexed { i, app ->
            val kind = app.profile.kind
            GridApp(
                label = appLabel(app, scramble, id, i),
                tag = if (kind == ProfileKind.PERSONAL) null else (scramble?.line(kind.tag, id, Scramble.APP) ?: kind.tag) + if (app.profile.quiet) "⏸" else "",
                tagInk = Ink.of(kind),
                quiet = app.profile.quiet,
            )
        },
        initials = model.person?.let { initials(it.name) },
        footer = if (found) foundIn(model.entry, marks) else null,
    )
    return TtyLine(model, block, time, shown)
}

/** App [i] of line [id]'s grid by its name, scrambled with the line when it is ([scramble]). */
private fun appLabel(app: AppEntry, scramble: Scramble?, id: Long, i: Int): String =
    scramble?.line(app.label, id, Scramble.APP * 100 + i) ?: app.label

/** One log line as the TTY log shows it: its model, its cells, its time as shown, and an apps grid's apps. */
@Immutable
internal class TtyLine(val model: LineModel, val block: CellBlock, val time: String?, val apps: List<AppEntry>)

/** What a line's cells depend on: the row, the settings, the grid's width, whether it is open, a search's words. */
internal data class LineKey(
    val row: LogRow.Entry,
    val look: LogLook,
    val options: LineOptions,
    val foldOpen: Boolean,
    val marks: List<String>,
    val format: Same<SimpleDateFormat>,
    /** The installed apps, for an apps grid; null for other lines. */
    val installed: Same<List<AppEntry>>?,
)

/**
 * [value] compared by identity. A time format compares its whole calendar to be equal, and a list
 * of apps hashes every app; both are made again when they change, so the same object is enough.
 */
internal class Same<T : Any>(val value: T) {
    override fun equals(other: Any?) = other is Same<*> && other.value === value
    override fun hashCode() = System.identityHashCode(value)
}

/**
 * The logs' caches of laid-out lines, let go with the screen's other caches
 * ([com.mtwabpin.kolahal.LauncherApp.keepsInMemory]): when home is away a while, or memory is short.
 * Held weakly, so a log that is gone takes its cache with it. Main thread only, as the trim is.
 */
internal object TtyCaches {
    private val live = java.util.WeakHashMap<BlockCache<*, *>, Unit>()

    init {
        com.mtwabpin.kolahal.LauncherApp.keepsInMemory { live.keys.toList().forEach { it.clear() } }
    }

    fun <C : BlockCache<*, *>> add(cache: C): C = cache.also { live[it] = Unit }
}

/**
 * What a tap, a long press or a swipe on a line does, by target: the same calls the GUI makes.
 * - The line: its tap; a long press opens its menu.
 * - A link opens; a button runs its action; the fold opens or folds the line.
 * - An app launches; a long press opens its menu.
 * A long press on anything but an app opens the line's menu.
 */
@Immutable
internal class TtyLineCalls(
    private val onTap: (LogEntry) -> Unit,
    private val onLongPress: (LogEntry) -> Unit,
    private val onOpenUrl: (String) -> Unit,
    private val onAction: (LogEntry, Int) -> Unit,
    private val onAppTap: (AppEntry) -> Unit,
    private val onAppLongPress: (AppEntry) -> Unit,
    private val onToggleFold: (Long) -> Unit,
    val onSwipeCopy: (LogEntry) -> Unit,
    val onSwipeDelete: (LogEntry) -> Unit,
) {
    fun tap(line: TtyLine, target: Target) {
        val entry = line.model.entry
        when (target) {
            Target.Line -> onTap(entry)
            is Target.Link -> onOpenUrl(target.url)
            is Target.Button -> onAction(entry, target.index)
            is Target.App -> line.apps.getOrNull(target.index)?.let(onAppTap) ?: onTap(entry)
            Target.Fold -> onToggleFold(entry.id)
        }
    }

    fun longPress(line: TtyLine, target: Target) {
        val app = (target as? Target.App)?.let { line.apps.getOrNull(it.index) }
        if (app != null) onAppLongPress(app) else onLongPress(line.model.entry)
    }
}

/**
 * One log line on the grid: its block of cells, [gapPx] below the line above it (a group's or an
 * item's gap, in dp as `log.spacing` and `log.item-spacing` say). [gapGutter], when not 0, is the
 * color of the gutter, drawn down through an item's gap where the `│` cells would be.
 * - A tap goes to the target under the finger, or the nearest one within reach, else the line.
 *   While pressed, the target (or the whole line) is on the surface color.
 * - A swipe slides it by whole cells and uncovers "⧉ Copy" or "✕ Delete"; past a third of the
 *   width it copies or deletes, as in the GUI ([deleteRight] swaps the sides).
 * - TalkBack reads it as one row (what [LineModel.spoken] says), with its links, buttons, fold,
 *   Copy and Delete as actions. Buttons, apps and the fold are also nodes of their own, where
 *   they show, so switch access reaches them.
 */
@Composable
private fun TtyLineRow(
    line: TtyLine,
    grid: TtyGrid,
    originX: Int,
    gapPx: Int,
    gapGutter: Int,
    highlighted: Boolean,
    deleteRight: Boolean,
    calls: TtyLineCalls,
    labels: SwipeLabels,
) {
    val m = grid.metrics
    val block = line.block
    val entry = line.model.entry
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf<Target?>(null) }
    val slop = with(density) { SLOP.toPx() }
    val current by rememberUpdatedState(line)
    val currentCalls by rememberUpdatedState(calls)
    val copySide = if (deleteRight) -1f else 1f
    val clip = remember { android.graphics.Rect() }
    val surface = Color(grid.palette.color(Ink.SURFACE))

    // The line slides by whole cells.
    fun shift(): Int = (swipe.value / m.cellW).toInt() * m.cellW
    fun targetAt(o: Offset): Target = current.block.hit(o.x - originX - shift(), o.y, m.cellW, m.cellH, slop)

    Box(
        Modifier
            .drawBehind {
                // Before the padding, so it reaches into the gap: the same line BoxGlyphs draws for │.
                if (gapGutter != 0 && gapPx > 0) {
                    // The gutter's column: the first, or the last in a right-to-left layout.
                    val col = if (layoutDirection == LayoutDirection.Rtl) block.cols - 1 else 0
                    val left = originX + shift() + col * m.cellW + m.cellW / 2 - m.line / 2
                    drawRect(Color(gapGutter), Offset(left.toFloat(), 0f), Size(m.line.toFloat(), gapPx.toFloat()))
                }
            }
            .padding(top = with(density) { gapPx.toDp() })
            .fillMaxWidth()
            .cellHeight(block.rows * m.cellH)
            .pointerInput(entry.id, copySide) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val width = size.width
                        val limit = width / 3f
                        scope.launch {
                            // Distance toward the copy side: positive copies, negative deletes.
                            val toCopy = swipe.value * copySide
                            val e = current.model.entry
                            when {
                                toCopy > limit -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentCalls.onSwipeCopy(e)
                                    swipe.animateTo(0f)
                                }

                                toCopy < -limit -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // The line slides out of sight the way it was swiped.
                                    swipe.animateTo(-copySide * width)
                                    currentCalls.onSwipeDelete(e)
                                }

                                else -> swipe.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + dx) }
                }
            }
            .pointerInput(entry.id, m, originX, slop) {
                detectTapGestures(
                    onPress = { o ->
                        // Shown after a moment, so a finger that starts a scroll doesn't flash the lines it touches.
                        val show = scope.launch {
                            delay(PRESS_SHOWS_MS)
                            pressed = targetAt(o)
                        }
                        tryAwaitRelease()
                        show.cancel()
                        pressed = null
                    },
                    onLongPress = { o ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentCalls.longPress(current, targetAt(o))
                    },
                    onTap = { o -> currentCalls.tap(current, targetAt(o)) },
                )
            }
            .testTag("line-${entry.id}")
            .semantics {
                text = AnnotatedString(line.model.spoken(line.time))
                onClick { calls.tap(line, Target.Line); true }
                onLongClick(label = "actions") { calls.longPress(line, Target.Line); true }
                customActions = accessibilityActions(line, calls)
            }
            .drawBehind {
                val shift = shift()
                val s = swipe.value
                if (s != 0f) swipeLabel(grid, labels, s, shift, copySide, block, originX)
                val x = (originX + shift).toFloat()
                val p = pressed
                if (highlighted || p == Target.Line) {
                    drawRect(surface, Offset(shift.toFloat(), 0f), size)
                } else if (p != null) {
                    // By index: drawing allocates nothing, not even an iterator.
                    val targets = block.targets
                    var rects: List<CellRect>? = null
                    for (t in 0 until targets.size) if (targets[t].target == p) {
                        rects = targets[t].rects
                        break
                    }
                    if (rects != null) for (i in 0 until rects.size) {
                        val r = rects[i]
                        drawRect(
                            surface,
                            Offset(x + r.col * m.cellW, (r.row * m.cellH).toFloat()),
                            Size((r.cols * m.cellW).toFloat(), (r.rows * m.cellH).toFloat()),
                        )
                    }
                }
                paint(grid, block.grid, x, clip)
            },
    ) {
        // Buttons, apps and the fold as nodes of their own, where they show, for switch access and
        // tests. They take no touches: the line's own handling hits the same targets.
        for (t in block.targets) {
            val target = t.target
            if (target is Target.Link || target == Target.Line) continue
            val r = t.rects.first()
            val label = when (target) {
                is Target.Button -> target.label
                is Target.App -> line.apps.getOrNull(target.index)
                    ?.let { appLabel(it, Scramble.of(entry), entry.id, target.index) }.orEmpty()
                else -> foldLabel(block)
            }
            Spacer(
                Modifier
                    // Absolute: the cells are already where a right-to-left layout has them.
                    .absoluteOffset { IntOffset(originX + shift() + r.col * m.cellW, r.row * m.cellH) }
                    .cellSize(r.cols * m.cellW, r.rows * m.cellH)
                    .testTag(
                        when (target) {
                            is Target.Button -> "line-action-${target.index}"
                            is Target.App -> "line-app-${target.index}"
                            else -> "fold-link"
                        }
                    )
                    .semantics {
                        role = Role.Button
                        text = AnnotatedString(label)
                        // What TalkBack says it does, as the GUI's pill, chip and fold link say it.
                        onClick(label = nodeClickLabel(target, block)) { calls.tap(line, target); true }
                        // An app's chip has its menu, as in the GUI.
                        if (target is Target.App) onLongClick(label = "actions") { calls.longPress(line, target); true }
                    },
            )
        }
    }
}

/**
 * What a tap on a line's button, app or fold does, in TalkBack's words: the GUI's click labels (a
 * button's label, the fold link's "show the whole line" or "fold the line"), so both read alike.
 */
internal fun nodeClickLabel(target: Target, block: CellBlock): String? = when (target) {
    is Target.Button -> target.label
    Target.Fold -> if (block.foldedRows > 0) "show the whole line" else "fold the line"
    else -> null
}

/** What the fold's row says: how many rows are hidden, or that the line folds again. */
private fun foldLabel(block: CellBlock): String {
    val n = block.foldedRows
    return if (n > 0) "▾ $n more ${if (n == 1) "line" else "lines"}" else "▴ Show less"
}

/**
 * TalkBack's actions on a line ([LineLayout.actions]): open each link, each button, open or fold
 * it, Copy and Delete (a swipe's two).
 */
private fun accessibilityActions(line: TtyLine, calls: TtyLineCalls): List<CustomAccessibilityAction> =
    LineLayout.actions(line.block).map { (target, label) ->
        CustomAccessibilityAction(label) {
            when {
                target != null -> calls.tap(line, target)
                label == "Copy" -> calls.onSwipeCopy(line.model.entry)
                else -> calls.onSwipeDelete(line.model.entry)
            }
            true
        }
    }

/** The label a swiped line uncovers, in the cells it left: the sign on the outer edge, where the line moved from. */
private fun DrawScope.swipeLabel(grid: TtyGrid, labels: SwipeLabels, s: Float, shift: Int, copySide: Float, block: CellBlock, originX: Int) {
    val m = grid.metrics
    val copies = sign(s) == copySide
    val label = labels.of(copies, right = s > 0)
    val canvas = drawContext.canvas.nativeCanvas
    val y = ((block.rows - 1) / 2 * m.cellH).toFloat()
    canvas.save()
    if (s > 0) {
        canvas.clipRect(0f, 0f, shift.toFloat(), size.height)
        grid.painter.draw(canvas, label, originX.toFloat(), y)
    } else {
        canvas.clipRect(size.width + shift, 0f, size.width, size.height)
        grid.painter.draw(canvas, label, size.width - originX - label.cols * m.cellW, y)
    }
    canvas.restore()
}

/** "⧉ Copy" and "✕ Delete" as cells, with the sign on either side: what a swiped line uncovers. */
private class SwipeLabels {
    private fun label(text: String, ink: Ink) =
        CellBuffer(CellWidth.total(text), 1).apply { put(0, 0, text, CellStyle.of(ink)) }

    private val copyRight = label("⧉ Copy", Ink.PROMPT)
    private val copyLeft = label("Copy ⧉", Ink.PROMPT)
    private val deleteRight = label("✕ Delete", Ink.ERROR)
    private val deleteLeft = label("Delete ✕", Ink.ERROR)

    /** The label for a swipe that copies (or deletes), to the [right] (or left). */
    fun of(copies: Boolean, right: Boolean): CellBuffer = when {
        copies && right -> copyRight
        copies -> copyLeft
        right -> deleteRight
        else -> deleteLeft
    }
}

/** A block that only shows: a day, the unread divider, the empty log's hints. */
@Composable
private fun TtyText(block: CellBlock, grid: TtyGrid, originX: Int, modifier: Modifier = Modifier) {
    val clip = remember { android.graphics.Rect() }
    Spacer(
        Modifier
            .fillMaxWidth()
            .cellHeight(block.rows * grid.metrics.cellH)
            .then(modifier)
            .drawBehind { paint(grid, block.grid, originX.toFloat(), clip) },
    )
}

/** Paints the rows of [cells] that are inside the canvas's clip, from [x]. [clip] is reused, so nothing is allocated. */
private fun DrawScope.paint(grid: TtyGrid, cells: CellBuffer, x: Float, clip: android.graphics.Rect) {
    val canvas = drawContext.canvas.nativeCanvas
    if (!canvas.getClipBounds(clip)) return
    val h = grid.metrics.cellH
    grid.painter.draw(canvas, cells, x, 0f, clip.top / h, (clip.bottom + h - 1) / h)
}

/** Exactly [px] pixels tall, so rows stay whole cells (no rounding through dp). */
private fun Modifier.cellHeight(px: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = px, maxHeight = px))
    layout(placeable.width, px) { placeable.place(0, 0) }
}

/** Exactly [width] × [height] pixels. */
private fun Modifier.cellSize(width: Int, height: Int) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}
