package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.mtwabpin.kolahal.notifications.NotifIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.wrapContentHeight
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.Gutter
import com.mtwabpin.kolahal.terminal.LogRow
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

/** Which lines show their time, from `log.time`. With `groups`, a group's first line shows it, and so does a line where the minute changes. */
enum class TimeMode { OFF, COMMANDS, GROUPS, ALL }

/** [today] comes from [rememberToday], so "Yesterday" moves at midnight even when the launcher stays on screen for days. */
@Composable
fun DayDivider(row: LogRow.Day, today: LocalDate) {
    // Built on each composition, not remembered, so the dividers follow a change of language.
    val label =
        if (row.day == today.minusDays(1)) "Yesterday"
        else row.day.format(DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0]))
    Text(
        "── $label ──",
        style = Term.small,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

/** "── 3 new since you left ──" above the first unread script line. */
@Composable
fun UnreadDivider(row: LogRow.Unread) {
    Text(
        "── ${row.count} new since you left ──",
        style = Term.text.copy(color = Term.prompt, fontSize = (Term.fontSize - 1).sp, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

/** A fixed colour for each script name, so mixed feeds are easy to tell apart. */
fun scriptColor(name: String?): Color {
    if (name == null) return Term.script
    val p = Term.palette
    val choices = listOf(p.script, p.link, p.info, p.work, p.private, p.prompt)
    return choices[Math.floorMod(name.hashCode(), choices.size)]
}

/**
 * One log entry.
 * - A thin gutter bar marks command blocks (prompt colour), timer output (script colour) and errors.
 * - Old output is dimmed.
 * - The time is right-aligned, so it never shifts the text.
 */
@Composable
fun LogLine(
    row: LogRow.Entry,
    timeMode: TimeMode,
    timeFormat: SimpleDateFormat,
    dim: Boolean,
    topGap: Dp,
    gutter: Boolean = true,
    indents: LineIndents,
    highlighted: Boolean,
    apps: (LogEntry) -> List<AppEntry>,
    onTap: (LogEntry) -> Unit,
    onLongPress: (LogEntry) -> Unit,
    onAppTap: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onSwipeCopy: (LogEntry) -> Unit = {},
    onSwipeDelete: (LogEntry) -> Unit = {},
    /** Words a log search found. They are highlighted where the line shows them. */
    marks: List<String> = emptyList(),
) {
    val entry = row.entry
    // Swipe right to copy, left to delete (the terminal offers Undo). The swipe must pass a third of the width.
    val swipe = remember { androidx.compose.animation.core.Animatable(0f) }
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    var rowWidth by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val gutterColor = gutterColor(row.gutter)
    val showTime = when (timeMode) {
        TimeMode.ALL -> true
        TimeMode.COMMANDS -> entry.kind == LogKind.COMMAND
        TimeMode.GROUPS -> row.showTime
        TimeMode.OFF -> false
    }
    val desc = entry.ctx.values[LogContext.DESC]
    val isGrid = entry.ctx.values[LogContext.APPS] != null
    val image = entry.ctx.image
    // On a notification line, the saved status-bar icon replaces the app name at the end.
    val context = LocalContext.current
    val iconKey = entry.ctx.values[LogContext.ICON]?.takeIf { row.showSource && entry.kind == LogKind.SCRIPT && NotifIcons.has(context, it) }
    val inline = remember(iconKey) { iconKey?.let(::notifIconContent) ?: emptyMap() }

    // log.line-height also sets the padding above and below a line:
    // - up to 3dp on each side;
    // - less as the height gets close to one line of text, and none when it equals it;
    // - below that, the space above the first line and under the last is removed too, so lines
    //   become as short as the text allows.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textLine = with(density) { (Term.fontSize * TEXT_LINE).sp.toDp() }
    // The line height is in sp, so it follows the phone's text size like the text does. A larger
    // system text size then doesn't squeeze the lines. At the default size, 22 is 22dp.
    val lineHeight = with(density) { Term.lineHeight.sp.toDp() }
    val padding = ((lineHeight - textLine) / 2).coerceIn(0.dp, LINE_PADDING.dp)
    val dense = lineHeight + 0.5.dp < textLine
    // Lines that do something get a larger touch target than plain output (log.taller-taps).
    val actionable = entry.kind == LogKind.COMMAND || entry.ctx.values.keys.any { it in ACTIONABLE_KEYS }
    val minHeight = when {
        !Term.tallerTaps -> lineHeight
        desc != null -> lineHeight + 14.dp
        actionable -> lineHeight + 10.dp
        else -> lineHeight
    }
    // The font settings are not keys here: indents is rebuilt from them, so it carries the change.
    val lineText = if (isGrid || desc != null) null else remember(entry, Term.palette, indents, row.unread, iconKey, marks, row.showSource) {
        markMatches(renderLine(entry, indents, row.unread, iconKey != null, row.showSource), marks)
    }
    // A dimmed line whose parts can't overlap fades part by part; this draws the same pixels as
    // fading it whole. A picture is a single draw placed below its text, not behind it, so it is
    // fine. The apps grid is excluded because its chips put text on a coloured background.
    // A script's tables and bars and what follows them, drawn under the line's text (RichBody).
    val richRest = remember(entry.ctx) { if (entry.kind == LogKind.SCRIPT) entry.ctx.body?.let { StyledText.splitAtBlocks(it).second } else null }
    // A built-in command's bar after the line's text (unlocks, usage, volume).
    val bar = remember(entry.ctx) { if (entry.kind == LogKind.OUTPUT || entry.kind == LogKind.INFO) lineBar(entry.ctx.values[LogContext.BAR]) else null }
    val byPart = dim && row.dimmed && !highlighted && !isGrid && richRest == null && bar == null && marks.isEmpty() &&
        remember(lineText, entry.text, desc) { if (lineText != null) fadesByPart(lineText) else fadesByPart(AnnotatedString(entry.text)) && fadesByPart(AnnotatedString(desc!!)) }
    // While the line is pressed, focused or under a pointer, and for a moment after, it fades whole.
    // Fading part by part would fade the ripple or highlight over the letters too, and the shade
    // would be slightly wrong.
    val touch = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var touched by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (byPart) androidx.compose.runtime.LaunchedEffect(touch) {
        // Restarted because the line was dimmed again: nothing is pressed yet.
        touched = false
        var active = 0
        var settle: kotlinx.coroutines.Job? = null
        touch.interactions.collect { i ->
            when (i) {
                is androidx.compose.foundation.interaction.PressInteraction.Press,
                is androidx.compose.foundation.interaction.FocusInteraction.Focus,
                is androidx.compose.foundation.interaction.HoverInteraction.Enter -> active++
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel,
                is androidx.compose.foundation.interaction.FocusInteraction.Unfocus,
                is androidx.compose.foundation.interaction.HoverInteraction.Exit -> active = (active - 1).coerceAtLeast(0)
                else -> return@collect
            }
            settle?.cancel()
            if (active > 0) touched = true
            // A quick tap's ripple grows, then fades. Both finish well within this delay.
            else settle = launch { kotlinx.coroutines.delay(RIPPLE_DONE_MS); touched = false }
        }
    }
    // The swipe direction (or 0). The content changes only when the direction changes, not on every
    // frame the line moves. The offset and the reveal read the swipe value during layout and draw.
    val swiped by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.sign(swipe.value) } }
    Box(Modifier.padding(top = topGap)) {
    // The swipe action label, revealed as the line moves. It is drawn only in the space the line has
    // left, so the line needs no background of its own and home's page (or wallpaper) shows through.
    if (swiped != 0f) {
        Text(
            if (swiped > 0) "⧉ Copy" else "Delete ✕",
            style = Term.text.copy(color = if (swiped > 0) Term.prompt else Term.error),
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val s = swipe.value
                    clipRect(left = if (s > 0) 0f else size.width + s, right = if (s > 0) s else size.width) {
                        this@drawWithContent.drawContent()
                    }
                }
                .wrapContentHeight()
                .padding(horizontal = 16.dp),
            textAlign = if (swiped > 0) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End,
        )
    }
    // When a line is taller than its text (log.taller-taps), the text stays centred vertically.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .onSizeChanged { rowWidth = it.width.coerceAtLeast(1) }
            .offset { androidx.compose.ui.unit.IntOffset(swipe.value.toInt(), 0) }
            .pointerInput(entry.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val limit = rowWidth / 3f
                        swipeScope.launch {
                            when {
                                swipe.value > limit -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSwipeCopy(entry)
                                    swipe.animateTo(0f)
                                }
                                swipe.value < -limit -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    swipe.animateTo(-rowWidth.toFloat())
                                    onSwipeDelete(entry)
                                }
                                else -> swipe.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { swipeScope.launch { swipe.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    swipeScope.launch { swipe.snapTo(swipe.value + dx) }
                }
            }
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .testTag("line-${entry.id}")
            .then(if (!dim || !row.dimmed) Modifier else if (byPart && !touched) DIMMED_BY_PART else DIMMED)
            .background(if (highlighted) Term.surface else Color.Transparent)
            .then(if (!gutter) Modifier else Modifier.drawBehind {
                drawRect(gutterColor, topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height))
            })
            .combinedClickable(
                // Pass a source only when it is watched. Without one, the ripple is created at the first press, not when the line appears.
                interactionSource = if (byPart) touch else null,
                indication = androidx.compose.foundation.LocalIndication.current,
                onLongClickLabel = "actions",
                onClick = { onTap(entry) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(entry)
                },
            )
            // Room for the gutter bar. Without the bar, the text lines up with the header and dividers.
            .padding(start = if (gutter) 10.dp else 0.dp, top = padding, bottom = padding),
    ) {
    // The time sits on the first line's baseline, even when text wraps or a picture is added below.
    // The row doesn't align by baseline: asking a line with inline icons for its baseline, as a line
    // scrolled into view, crashed Compose ("LayoutNode not found in RectList"). Both texts start at
    // the top, and the time moves down by the difference between the two fonts' first baselines,
    // measured once for each pair of styles (TimeShift).
    val lineStyle = entry.style().dense(dense)
    val timeStyle = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp).dense(dense)
    val timeShift = TimeShift.of(lineStyle, timeStyle)
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) {
            when {
                // Parsed once per entry (it decodes JSON and looks up every app), and again when the
                // apps change, so a chip for an app that was uninstalled or hidden disappears.
                isGrid -> {
                    val installed by com.mtwabpin.kolahal.apps.AppRepository.get(context).apps.collectAsStateWithLifecycle()
                    AppGrid(remember(entry.id, entry.ctx, installed) { apps(entry) }, onAppTap, onAppLongPress)
                }
                desc != null -> TwoColumns(entry, desc, marks, dense, bar)
                else -> {
                    val text = lineText!!
                    val style = entry.style().dense(dense)
                    val words: @Composable () -> Unit = {
                        if (bar != null) WithBar(bar) { RichText(text, style = style, inlineContent = inline) }
                        else RichText(text, style = style, inlineContent = inline)
                    }
                    // A long line folds (log.fold). Only a line long enough to need it is measured
                    // for it, and never a typed command or a search result (its words must show).
                    val mayFold = remember(entry) { entry.kind != LogKind.COMMAND && mayFold(entry) } && marks.isEmpty()
                    if (image == null && richRest == null && !mayFold) words()
                    else Column {
                        Foldable(entry.id, style, enabled = mayFold) {
                            Column {
                                if (entry.text.isNotEmpty()) words()
                                richRest?.let { rest ->
                                    val start = with(density) { (if (row.unread) indents.bullet else 0.sp).toDp() }
                                    RichBody(rest, style.copy(color = Term.dim), start, indents.bullet)
                                }
                            }
                        }
                        // The picture stays in sight under a folded line.
                        image?.let { LogImage(it, Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
        }
        if (showTime) {
            Spacer(Modifier.width(8.dp))
            Text(
                remember(entry.ts, timeFormat) { timeFormat.format(Date(entry.ts)) },
                // Trimmed like the line, so it never makes the line taller.
                style = timeStyle,
                modifier = Modifier.padding(top = timeShift),
            )
        }
    }
    }
    }
}

/** The colour of the bar beside a line: prompt colour for commands, script colour for timer output, error colour (red) for errors. */
fun gutterColor(gutter: Gutter): Color = when (gutter) {
    Gutter.COMMAND -> Term.prompt
    Gutter.SCRIPT -> Term.script
    Gutter.ERROR -> Term.error
}.copy(alpha = 0.45f)

/** The opacity of a dimmed line (log.dim). */
private const val DIM_ALPHA = 0.62f

/** A dimmed line: drawn whole into a buffer, then the buffer is faded. */
private val DIMMED = Modifier.alpha(DIM_ALPHA)

/**
 * A dimmed line where each part is faded on its own, with no buffer. A buffer is redrawn on every
 * frame the log moves: a couple of dozen times per frame when the screen is full of dimmed lines.
 * Use this only for a line whose parts don't overlap ([fadesByPart]); there both ways give the
 * same pixels.
 */
private val DIMMED_BY_PART = Modifier.graphicsLayer(alpha = DIM_ALPHA, clip = true, compositingStrategy = CompositingStrategy.ModulateAlpha)

/** How long a dimmed line keeps fading whole after a press, focus or pointer leaves it: the time its ripple takes to grow and fade. */
private const val RIPPLE_DONE_MS = 450L

/**
 * Whether [text] looks the same faded part by part as faded whole. This needs:
 * - letters that never overlap: plain Latin, digits, common punctuation. Not joined scripts,
 *   accents built from marks, box drawing, or italics (which can lean into the next letter);
 * - nothing drawn over or under the letters: no background, underline or shadow.
 */
internal fun fadesByPart(text: AnnotatedString): Boolean =
    text.text.all { c -> c == '\n' || c in ' '..'~' || c in '\u00A0'..'\u024F' || c in '\u2010'..'\u2027' } &&
        text.spanStyles.none {
            it.item.background.isSpecified || it.item.textDecoration != null || it.item.shadow != null ||
                it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic
        }

private val ACTIONABLE_KEYS = setOf(
    LogContext.URL, LogContext.PKG, LogContext.COMMAND, LogContext.RUN, LogContext.SCRIPT, LogContext.APPS, LogContext.LINE,
    LogContext.PHONE, LogContext.SHORTCUT, "notif", LogContext.NOTE, LogContext.EVENT, LogContext.REMINDER,
)

/** Rows of `help`, `config` and `script ls`: the name on the left, the description wrapping on the right. */
/**
 * Whether [entry] could be tall enough to fold (`log.fold`): many lines, or much text. Shorter
 * lines skip the fold's measuring. A table row or a bar is a line of its own.
 */
private fun mayFold(entry: LogEntry): Boolean {
    val lines = Term.foldLines
    if (lines <= 0) return false
    val text = entry.text.length + (entry.ctx.body?.length ?: 0)
    val breaks = entry.text.count { it == '\n' } + (entry.ctx.body?.count { it == '\n' } ?: 0)
    return breaks >= lines || text > lines * FOLD_CHARS_PER_LINE
}

/** Fewer characters than fit on a line of a narrow phone, so a line that wraps that much is measured for its fold. */
private const val FOLD_CHARS_PER_LINE = 20

/** A row of two columns (help, config, usage). A [bar] goes in the second column, before its text, so the bars of rows line up. */
@Composable
private fun TwoColumns(entry: LogEntry, desc: String, marks: List<String> = emptyList(), dense: Boolean = false, bar: StyledText.Meter? = null) {
    Row(Modifier.fillMaxWidth()) {
        RichText(
            markMatches(remember(entry.text, Term.palette) { styledText(entry.text) }, marks),
            style = entry.style().copy(color = Term.fg, fontWeight = FontWeight.Bold).dense(dense),
            modifier = Modifier.weight(0.46f).padding(end = 8.dp),
        )
        val descText = @Composable { modifier: Modifier ->
            Text(markMatches(AnnotatedString(desc), marks), style = Term.text.copy(color = Term.dim).dense(dense), modifier = modifier)
        }
        if (bar == null) descText(Modifier.weight(0.54f))
        else Row(Modifier.weight(0.54f), verticalAlignment = Alignment.CenterVertically) {
            Track(bar, Modifier.weight(0.5f).height(6.dp))
            Spacer(Modifier.width(8.dp))
            descText(Modifier.weight(0.5f))
        }
    }
}

/** `apps` output: one line of chips instead of one line per app. */
@Composable
private fun AppGrid(apps: List<AppEntry>, onTap: (AppEntry) -> Unit, onLongPress: (AppEntry) -> Unit) {
    val haptics = LocalHapticFeedback.current
    if (apps.isEmpty()) {
        Text("(apps no longer installed)", style = Term.text.copy(color = Term.dim))
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        apps.forEach { app ->
            val color = Term.profileColor(app.profile.kind)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = Term.chipHeight.dp)
                    .alpha(if (app.profile.quiet) 0.45f else 1f)
                    .background(color.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                    .combinedClickable(
                        onClick = { onTap(app) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(app)
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                if (app.profile.kind != ProfileKind.PERSONAL) {
                    Text(
                        app.profile.kind.tag + if (app.profile.quiet) "⏸" else "",
                        style = Term.text.copy(color = color, fontWeight = FontWeight.Bold, fontSize = (Term.fontSize - 2).sp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(app.label, style = Term.text)
            }
        }
    }
}

@Composable
internal fun NotifIcon(key: String) {
    val context = LocalContext.current
    val icon by produceState(NotifIcons.cached(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { NotifIcons.load(context.applicationContext, key) }
    }
    icon?.let { Image(it, contentDescription = null, colorFilter = ColorFilter.tint(Term.dim), modifier = Modifier.fillMaxSize()) }
}

/**
 * How far down the time goes so its baseline meets the line's first one: the first baselines of
 * the two styles, measured on a "0", apart.
 * - Kept for each pair of styles, so a scroll measures nothing. A measurer is made only for a pair
 *   not seen yet.
 * - Colours don't move a baseline, so they are left out of the key: a new theme measures nothing.
 */
internal object TimeShift {
    private data class Key(val line: TextStyle, val time: TextStyle, val density: Float, val fontScale: Float, val direction: LayoutDirection)

    private val cache = HashMap<Key, Dp>()

    /** Most pairs seen in a session: a few kinds of line, times each font size pinched to. */
    private const val MAX_PAIRS = 64

    @Composable
    fun of(line: TextStyle, time: TextStyle): Dp {
        val density = LocalDensity.current
        val fonts = LocalFontFamilyResolver.current
        val direction = LocalLayoutDirection.current
        val key = Key(line.copy(color = Color.Unspecified), time.copy(color = Color.Unspecified), density.density, density.fontScale, direction)
        return synchronized(cache) {
            cache[key] ?: run {
                val measurer = TextMeasurer(fonts, density, direction, cacheSize = 0)
                val gap = measurer.measure("0", line).firstBaseline - measurer.measure("0", time).firstBaseline
                with(density) { gap.coerceAtLeast(0f).toDp() }.also {
                    if (cache.size >= MAX_PAIRS) cache.clear()
                    cache[key] = it
                }
            }
        }
    }
}
