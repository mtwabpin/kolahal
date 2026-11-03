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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mtwabpin.kolahal.terminal.Gutter
import com.mtwabpin.kolahal.terminal.LogRow
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

/** How a line shows its time, from `log.time`: `groups` on a group's first line and when the minute changes. */
enum class TimeMode { OFF, COMMANDS, GROUPS, ALL }

/** [today]: [rememberToday], so "Yesterday" moves at midnight under a launcher that stays on screen for days. */
@Composable
fun DayDivider(row: LogRow.Day, today: LocalDate) {
    // Made each time, not remembered: the dividers follow a change of language.
    val label =
        if (row.day == today.minusDays(1)) "Yesterday"
        else row.day.format(DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0]))
    Text(
        "── $label ──",
        style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 1).sp),
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

/** A stable colour per script name, so interleaved feeds are easy to tell apart. */
fun scriptColor(name: String?): Color {
    if (name == null) return Term.script
    val p = Term.palette
    val choices = listOf(p.script, p.link, p.info, p.work, p.private, p.prompt)
    return choices[Math.floorMod(name.hashCode(), choices.size)]
}

/**
 * One log entry. A thin gutter bar groups command blocks (prompt colour), timer output (script
 * colour) and errors; old output is dimmed; the time sits right-aligned so it never shifts text.
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
    /** Words a log search found: marked where the line shows them. */
    marks: List<String> = emptyList(),
) {
    val entry = row.entry
    // Swipe right: copy. Swipe left: delete (the terminal offers Undo). Past a third of the width.
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
    // A notification line's saved status-bar icon replaces its app name at the end.
    val context = LocalContext.current
    val iconKey = entry.ctx.values[LogContext.ICON]?.takeIf { row.showSource && entry.kind == LogKind.SCRIPT && NotifIcons.has(context, it) }
    val inline = remember(iconKey) { iconKey?.let(::notifIconContent) ?: emptyMap() }

    // log.line-height sets the padding above and below a line too: up to 3dp each, less as the
    // height nears one line of text, none at it. Below that, the leading above the first line and
    // under the last goes as well, so lines get as short as the text itself allows.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textLine = with(density) { (Term.fontSize * TEXT_LINE).sp.toDp() }
    // In sp: the line height follows the phone's text size, as the text does, so a larger system
    // text size doesn't squeeze the lines (22 is 22dp at the usual size).
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
    val lineText = if (isGrid || desc != null) null else remember(entry, Term.palette, indents, row.unread, iconKey, marks, row.showSource) {
        markMatches(renderLine(entry, indents, row.unread, iconKey != null, row.showSource), marks)
    }
    // A dimmed line whose parts can't overlap fades part by part, which draws the same pixels.
    val byPart = dim && row.dimmed && !highlighted && !isGrid && image == null && marks.isEmpty() &&
        remember(lineText, entry.text, desc) { if (lineText != null) fadesByPart(lineText) else fadesByPart(AnnotatedString(entry.text)) && fadesByPart(AnnotatedString(desc!!)) }
    // Pressed, focused or under a pointer, and for a moment after: the ripple or highlight drawn over
    // the letters would fade with them part by part, a shade off. Faded whole meanwhile, as it was.
    val touch = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var touched by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (byPart) androidx.compose.runtime.LaunchedEffect(touch) {
        // Started again (the line dimmed anew): nothing is pressed yet.
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
            // A quick tap's ripple finishes growing, then fades: done well within this.
            else settle = launch { kotlinx.coroutines.delay(RIPPLE_DONE_MS); touched = false }
        }
    }
    // Which way the line is swiped, if at all: what it shows changes only when this does, not on
    // every frame the line moves (the offset and the reveal read the swipe as they lay out and draw).
    val swiped by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.sign(swipe.value) } }
    Box(Modifier.padding(top = topGap)) {
    // What the swipe will do, revealed as the line moves: drawn only where the line has moved away
    // from, so the line needs no background of its own and home's page (or wallpaper) shows through.
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
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
                interactionSource = touch,
                indication = androidx.compose.foundation.LocalIndication.current,
                onLongClickLabel = "actions",
                onClick = { onTap(entry) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(entry)
                },
            )
            // Room for the gutter bar; without it the text lines up with the header and dividers.
            .padding(start = if (gutter) 10.dp else 0.dp, top = padding, bottom = padding),
    ) {
        Box(Modifier.weight(1f)) {
            when {
                // Parsed once per entry (it decodes JSON and looks up every app), and again when the
                // apps change: a chip uninstalled or hidden from it goes.
                isGrid -> {
                    val installed by com.mtwabpin.kolahal.apps.AppRepository.get(context).apps.collectAsStateWithLifecycle()
                    AppGrid(remember(entry.id, entry.ctx, installed) { apps(entry) }, onAppTap, onAppLongPress)
                }
                desc != null -> TwoColumns(entry, desc, marks, dense)
                else -> {
                    val text = lineText!!
                    if (image == null) Text(text, style = entry.style().dense(dense), inlineContent = inline)
                    else Column {
                        if (entry.text.isNotEmpty()) Text(text, style = entry.style().dense(dense), inlineContent = inline)
                        LogImage(image, Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
        if (showTime) {
            Spacer(Modifier.width(8.dp))
            Text(
                remember(entry.ts, timeFormat) { timeFormat.format(Date(entry.ts)) },
                style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp),
                // Beside the text line, not halfway down the image.
                modifier = Modifier.then(if (image != null) Modifier.align(Alignment.Top) else Modifier).padding(top = 2.dp),
            )
        }
    }
    }
}

/** The bar beside a line: commands in the prompt colour, timer output in the script colour, errors red. */
fun gutterColor(gutter: Gutter): Color = when (gutter) {
    Gutter.COMMAND -> Term.prompt
    Gutter.SCRIPT -> Term.script
    Gutter.ERROR -> Term.error
}.copy(alpha = 0.45f)

/** A dimmed line (log.dim): drawn whole into a buffer, then faded. */
private val DIMMED = Modifier.alpha(0.62f)

/**
 * A dimmed line drawn faded part by part, with no buffer: the buffer is drawn again on every frame
 * the log moves, a couple of dozen times a frame with a screen of dimmed lines. Only for a line
 * whose parts don't overlap ([fadesByPart]), where both ways give the same pixels.
 */
private val DIMMED_BY_PART = Modifier.graphicsLayer(alpha = 0.62f, clip = true, compositingStrategy = CompositingStrategy.ModulateAlpha)

/** How long after a press, focus or pointer leaves a dimmed line it fades whole: its ripple's growing and fading. */
private const val RIPPLE_DONE_MS = 450L

/**
 * Whether [text] can be faded part by part and look as faded whole: letters that never overlap
 * (plain Latin, digits, common punctuation, not joined scripts or accents built from marks, not box
 * drawing, nor slanted into the next as italics can be), and nothing drawn over or under them (a
 * background, an underline, a shadow).
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

/** `help`, `config` and `script ls` rows: name on the left, description wrapping on the right. */
@Composable
private fun TwoColumns(entry: LogEntry, desc: String, marks: List<String> = emptyList(), dense: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            markMatches(AnnotatedString(entry.text), marks),
            style = entry.style().copy(color = Term.fg, fontWeight = FontWeight.Bold).dense(dense),
            modifier = Modifier.weight(0.46f).padding(end = 8.dp),
        )
        Text(markMatches(AnnotatedString(desc), marks), style = Term.text.copy(color = Term.dim).dense(dense), modifier = Modifier.weight(0.54f))
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
