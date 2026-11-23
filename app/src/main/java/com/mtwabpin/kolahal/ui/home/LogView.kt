/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.shownText
import com.mtwabpin.kolahal.notifications.NotifIcons
import com.mtwabpin.kolahal.terminal.Gutter
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.Scramble
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.lineModel
import com.mtwabpin.kolahal.terminal.plainOf
import com.mtwabpin.kolahal.ui.theme.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date

/** [today] comes from [rememberToday], so "Yesterday" moves at midnight even when the launcher stays on screen for days. */
@Composable
fun DayDivider(row: LogRow.Day, today: LocalDate) {
    // Built on each composition, not remembered, so the dividers follow a change of language.
    val label =
        if (row.day == today.minusDays(1)) "Yesterday"
        else row.day.format(com.mtwabpin.kolahal.data.TimeFormats.dayFormat(LocalConfiguration.current.locales[0]))
    LogText(
        "── ${row.scramble?.text(label, Scramble.DAY) ?: label} ──",
        style = Term.small,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

/** "── 3 new since you left ──" above the first unread script line. */
@Composable
fun UnreadDivider(row: LogRow.Unread) {
    LogText(
        "── ${row.count} new since you left ──",
        style = Term.text.copy(
            color = Term.prompt,
            fontSize = (Term.fontSize - 1).sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

/** A fixed color for each script name, so mixed feeds are easy to tell apart. */
fun scriptColor(name: String): Color {
    val p = Term.palette
    val choices = listOf(p.script, p.link, p.info, p.work, p.private, p.prompt)
    return choices[Math.floorMod(name.hashCode(), choices.size)]
}

/**
 * One log entry on its own, as a search result or a guide's example shows it: the [LogLine] of
 * [row]'s model. It keeps whether a long line is open itself.
 */
@Composable
fun LogLine(
    row: LogRow.Entry,
    timeMode: TimeMode,
    timeFormat: SimpleDateFormat,
    dim: Boolean,
    topGap: Dp,
    itemGap: Dp = 0.dp,
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
    deleteRight: Boolean = false,
    onAction: (LogEntry, Int) -> Unit = { _, _ -> },
    marks: List<String> = emptyList(),
) {
    // A line stays open while it is in the list's memory (scrolled away and back).
    var open by rememberSaveable(row.entry.id) { mutableStateOf(false) }
    LogLine(
        model = rememberLineModel(row, LineOptions(timeMode, Term.foldLines)),
        timeFormat = timeFormat,
        dim = dim,
        topGap = topGap,
        itemGap = itemGap,
        gutter = gutter,
        indents = indents,
        highlighted = highlighted,
        apps = apps,
        onTap = onTap,
        onLongPress = onLongPress,
        onAppTap = onAppTap,
        onAppLongPress = onAppLongPress,
        onSwipeCopy = onSwipeCopy,
        onSwipeDelete = onSwipeDelete,
        deleteRight = deleteRight,
        onAction = onAction,
        marks = marks,
        foldOpen = open,
        onToggleFold = { open = !open },
    )
}

/**
 * [row]'s [LineModel], kept while the row and [options] stay the same. Whether a notification's
 * icon was saved is read on each composition: it may be saved after its line shows.
 */
@Composable
internal fun rememberLineModel(row: LogRow.Entry, options: LineOptions): LineModel {
    val context = LocalContext.current
    val iconSaved =
        row.entry.ctx.values[LogContext.ICON]?.let { Scramble.isPicture(it) || NotifIcons.has(context, it) } == true
    return remember(row, options, iconSaved) { lineModel(row, options) { iconSaved } }
}

/**
 * One log row, drawn from its [model] ([lineModel]).
 * - A thin gutter bar marks command blocks (prompt color), timer output (script color) and errors.
 * - Old output is dimmed.
 * - The time is right-aligned, so it never shifts the text.
 * - A long line folds (`log.fold`): [foldOpen] says whether it is open, [onToggleFold] opens or folds it.
 */
@Composable
fun LogLine(
    model: LineModel,
    timeFormat: SimpleDateFormat,
    dim: Boolean,
    topGap: Dp,
    /** Space above the line inside it, so the gutter bar runs through it: an item of a list ([LineModel.Gap.ITEM]). */
    itemGap: Dp,
    gutter: Boolean,
    indents: LineIndents,
    highlighted: Boolean,
    apps: (LogEntry) -> List<AppEntry>,
    onTap: (LogEntry) -> Unit,
    onLongPress: (LogEntry) -> Unit,
    onAppTap: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onSwipeCopy: (LogEntry) -> Unit,
    onSwipeDelete: (LogEntry) -> Unit,
    /**
     * A swipe right deletes and a swipe left copies, the other way round from the default. On with
     * the widgets page on the right (`widgets.page`): a swipe toward that page copies, never deletes.
     */
    deleteRight: Boolean,
    /** A tap on button `index` under a script's line (`ctx.actions`). */
    onAction: (LogEntry, Int) -> Unit,
    /** Words a log search found. They are highlighted where the line shows them. */
    marks: List<String>,
    foldOpen: Boolean,
    onToggleFold: () -> Unit,
) {
    val entry = model.entry
    val body = model.body
    // Swipe right to copy, left to delete (the terminal offers Undo), or the other way with [deleteRight].
    // The swipe must pass a third of the width.
    val swipe = remember { androidx.compose.animation.core.Animatable(0f) }
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    var rowWidth by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val gutterColor = gutterColor(model.gutter)
    val context = LocalContext.current
    // On a notification line, the saved status-bar icon replaces the app name at the end.
    val iconKey = (model.source as? LineModel.Source.Icon)?.key
    val inline = remember(iconKey) { iconKey?.let(::notifIconContent) ?: emptyMap() }

    // log.line-height also sets the padding above and below a line:
    // - up to 3dp on each side;
    // - less as the height gets close to one line of text, and none when it equals it;
    // - below that, the space above the first line and under the last is removed too, so lines
    //   become as short as the text allows.
    val density = LocalDensity.current
    val textLine = with(density) { (Term.fontSize * TEXT_LINE).sp.toDp() }
    // The line height is in sp, so it follows the phone's text size like the text does. A larger
    // system text size then doesn't squeeze the lines. At the default size, 22 is 22dp.
    val lineHeight = with(density) { Term.lineHeight.sp.toDp() }
    val padding = ((lineHeight - textLine) / 2).coerceIn(0.dp, LINE_PADDING.dp)
    val dense = lineHeight + 0.5.dp < textLine
    // Lines that do something get a larger touch target than plain output (log.taller-taps).
    val minHeight = when {
        !Term.tallerTaps -> lineHeight
        body is LineModel.Body.TwoColumns || body is LineModel.Body.NameColumns -> lineHeight + 14.dp
        model.tappable -> lineHeight + 10.dp
        else -> lineHeight
    }
    // The font settings are not keys here: indents is rebuilt from them, so it carries the change.
    val lineText = if (body !is LineModel.Body.Words) null else remember(
        body,
        model.source,
        Term.palette,
        indents,
        marks
    ) {
        markMatches(renderLine(body, indents, model.source), marks)
    }
    // A dimmed line whose parts can't overlap fades part by part; this draws the same pixels as
    // fading it whole. A picture is a single draw placed below its text, not behind it, so it is
    // fine. The apps grid is excluded because its chips put text on a colored background.
    // A script's tables and bars and what follows them are drawn under the line's text (RichBody),
    // and a built-in command's bar after it.
    val blocks = model.blocks
    val bar = model.bar
    val buttons = model.buttons
    val byPart =
        dim && model.dimmed && !highlighted && body !is LineModel.Body.AppGrid && blocks.isEmpty() && bar == null && buttons.isEmpty() && marks.isEmpty() &&
                remember(lineText, body) {
                    when (body) {
                        is LineModel.Body.Words -> fadesByPart(lineText!!)
                        is LineModel.Body.TwoColumns -> fadesByPart(AnnotatedString(body.name)) && fadesByPart(
                            styledText(body.desc)
                        )

                        is LineModel.Body.NameColumns -> fadesByPart(AnnotatedString(body.name)) && fadesByPart(
                            styledText(body.desc)
                        ) && fadesByPart(AnnotatedString(body.args.orEmpty()))

                        is LineModel.Body.AppGrid -> false
                    }
                }
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
                is androidx.compose.foundation.interaction.HoverInteraction.Exit -> active =
                    (active - 1).coerceAtLeast(0)

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
    // The sign of the swipe that copies: right (1) by default, left (-1) with [deleteRight].
    val copySide = if (deleteRight) -1f else 1f
    Box(Modifier.padding(top = topGap)) {
        // The swipe action label, revealed as the line moves. It is drawn only in the space the line has
        // left, so the line needs no background of its own and home's page (or wallpaper) shows through.
        if (swiped != 0f) {
            // The sign always goes on the outer edge, where the line moved from.
            val copies = swiped == copySide
            Text(
                when {
                    copies && swiped > 0 -> "⧉ Copy"
                    copies -> "Copy ⧉"
                    swiped > 0 -> "✕ Delete"
                    else -> "Delete ✕"
                },
                style = Term.text.copy(color = if (copies) Term.prompt else Term.error),
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val s = swipe.value
                        clipRect(
                            left = if (s > 0) 0f else size.width + s,
                            right = if (s > 0) s else size.width
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp),
                textAlign = if (swiped > 0) androidx.compose.ui.text.style.TextAlign.Left else androidx.compose.ui.text.style.TextAlign.Right,
            )
        }
        // When a line is taller than its text (log.taller-taps), the text stays centered vertically.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .onSizeChanged { rowWidth = it.width.coerceAtLeast(1) }
                .offset { androidx.compose.ui.unit.IntOffset(swipe.value.toInt(), 0) }
                .pointerInput(entry.id, copySide) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val limit = rowWidth / 3f
                            swipeScope.launch {
                                // Distance toward the copy side: positive copies, negative deletes.
                                val toCopy = swipe.value * copySide
                                when {
                                    toCopy > limit -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSwipeCopy(entry)
                                        swipe.animateTo(0f)
                                    }

                                    toCopy < -limit -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // The line slides out of sight the way it was swiped.
                                        swipe.animateTo(-copySide * rowWidth)
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
                .heightIn(min = minHeight + itemGap)
                .testTag("line-${entry.id}")
                .then(if (!dim || !model.dimmed) Modifier else if (byPart && !touched) DIMMED_BY_PART else DIMMED)
                .background(if (highlighted) Term.surface else Color.Transparent)
                .then(if (!gutter) Modifier else Modifier.drawBehind {
                    drawRect(
                        gutterColor,
                        topLeft = Offset.Zero,
                        size = Size(2.dp.toPx(), size.height)
                    )
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
                .padding(
                    start = if (gutter) 10.dp else 0.dp,
                    top = padding + itemGap,
                    bottom = padding
                ),
        ) {
            // The time sits on the first line's baseline, even when text wraps or a picture is added below.
            // The row doesn't align by baseline: asking a line with inline icons for its baseline, as a line
            // scrolled into view, crashed Compose ("LayoutNode not found in RectList"). Both texts start at
            // the top, and the time moves down by the difference between the two fonts' first baselines,
            // measured once for each pair of styles (TimeShift).
            val lineStyle = lineTextStyle(model.mono).dense(dense)
            val timeStyle =
                Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp).dense(dense)
            val timeShift = TimeShift.of(lineStyle, timeStyle)
            Row(Modifier.fillMaxWidth()) {
                // A person's line (a call, a contact) starts with their picture: 36 dp beside a call and its
                // details, 24 dp beside a one-line contact.
                model.person?.let { person ->
                    Avatar(
                        person.name,
                        person.photo,
                        if (person.big) 36.dp else 24.dp,
                        Modifier.align(Alignment.CenterVertically)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    when (body) {
                        // Parsed once per entry (it decodes JSON and looks up every app), and again when the
                        // apps change, so a chip for an app that was uninstalled or hidden disappears.
                        is LineModel.Body.AppGrid -> {
                            val installed by com.mtwabpin.kolahal.apps.AppRepository.get(context).apps.collectAsStateWithLifecycle()
                            AppGrid(
                                remember(entry.id, entry.ctx, installed) { apps(entry) },
                                onAppTap,
                                onAppLongPress,
                                Scramble.of(entry),
                                entry.id,
                            )
                        }

                        is LineModel.Body.NameColumns -> NameColumns(body, model.mono, marks, dense)
                        is LineModel.Body.TwoColumns -> TwoColumns(body, model.mono, marks, dense, bar)
                        is LineModel.Body.Words -> {
                            val text = lineText!!
                            val words: @Composable (AnnotatedString) -> Unit =
                                { t ->
                                    if (bar != null) WithBar(bar) {
                                        RichText(
                                            t,
                                            style = lineStyle,
                                            inlineContent = inline
                                        )
                                    }
                                    else RichText(t, style = lineStyle, inlineContent = inline)
                                }
                            // A long line folds (log.fold). Only a line long enough to need it is measured
                            // for it, and never a typed command or a search result (its words must show).
                            // A character of the terminal font is 0.6 of its size wide; the log loses about
                            // 60 dp to its padding, the gutter and the time.
                            val screen =
                                with(density) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp().value }
                            val charDp = with(density) { (Term.fontSize * 0.6f).sp.toDp().value }
                            val lineChars = ((screen - 60) / charDp).toInt().coerceAtLeast(16)
                            val mayFold = remember(
                                model,
                                lineChars,
                                Term.foldLines
                            ) {
                                model.mayFold && tallEnough(entry, lineChars)
                            } && marks.isEmpty()
                            // While folded, a very long line lays out only its start (and not its body): all of a
                            // 64 K line takes tens of ms, in one frame, for the few lines that show.
                            val keep = maxOf(FOLDED_MIN_CHARS, Term.foldLines * lineChars * 4)
                            val head = remember(
                                text,
                                keep,
                                mayFold
                            ) {
                                if (mayFold && text.length > keep * 2) text.subSequence(
                                    0,
                                    keepWhole(text.text, keep)
                                ) else null
                            }
                            val cutLines = remember(head, blocks, lineChars) {
                                if (head == null) 0 else estimatedLines(
                                    text.text.substring(head.length),
                                    lineChars
                                ) + (if (blocks.isEmpty()) 0 else estimatedLines(
                                    plainOf(blocks),
                                    lineChars
                                ))
                            }
                            if (model.image == null && blocks.isEmpty() && !mayFold && buttons.isEmpty()) words(
                                text
                            )
                            else Column {
                                Foldable(
                                    model.id,
                                    lineStyle,
                                    open = foldOpen,
                                    onToggle = onToggleFold,
                                    enabled = mayFold,
                                    cutLines = cutLines
                                ) { open ->
                                    val cut = head != null && !open
                                    Column {
                                        // A command's line can be its big numbers alone, with no words above them.
                                        if (!body.blank) words(
                                            if (cut) head else text
                                        )
                                        if (!cut && blocks.isNotEmpty()) {
                                            val start =
                                                with(density) { (if (model.unread) indents.bullet else 0.sp).toDp() }
                                            RichBody(
                                                blocks,
                                                lineStyle.copy(color = Term.dim),
                                                start,
                                                indents.bullet
                                            )
                                        }
                                    }
                                }
                                // The picture stays in sight under a folded line.
                                model.image?.let { LogImage(it, Modifier.padding(vertical = 4.dp)) }
                                if (buttons.isNotEmpty()) ActionPills(entry, buttons, onAction)
                            }
                        }
                    }
                }
                model.time?.let { time ->
                    Spacer(Modifier.width(8.dp))
                    // A scrambled line's time is scrambled with it.
                    val scramble = Scramble.of(entry)
                    LogText(
                        remember(time, timeFormat, scramble) {
                            timeFormat.format(Date(time)).let { t ->
                                scramble?.line(t, entry.id, Scramble.TIME) ?: t
                            }
                        },
                        // Trimmed like the line, so it never makes the line taller.
                        style = timeStyle,
                        modifier = Modifier.padding(top = timeShift),
                    )
                }
            }
        }
    }
}

/** The color of the bar beside a line: prompt color for commands, script color for timer output, error color (red) for errors. */
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
private val DIMMED_BY_PART = Modifier.graphicsLayer(
    alpha = DIM_ALPHA,
    clip = true,
    compositingStrategy = CompositingStrategy.ModulateAlpha
)

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

/**
 * Whether [entry] could be tall enough to fold (`log.fold`), from an estimate of its lines as
 * they show ([lineChars] characters fit on a line). Only such a line pays for the fold's
 * measuring; the fold itself then decides from the real height.
 * - It counts the text as it reads ([shownText]): no style marks, no link addresses.
 * - Each line wraps at [lineChars], and a little sooner, because lines break between words.
 * - A table row or a bar is a line of its own.
 * - A line that just fits stays out: the fold needs more than log.fold + [FOLD_SLACK] lines.
 */
private fun tallEnough(entry: LogEntry, lineChars: Int): Boolean {
    val lines = Term.foldLines
    if (lines <= 0) return false
    // A rough upper bound, cheap enough for every line: 1.15 for the words that wrap early.
    return estimatedLines(entry.shownText, lineChars) * 1.15 > lines + FOLD_SLACK
}

/** About how many lines [text] takes, [lineChars] to a line. */
private fun estimatedLines(text: String, lineChars: Int): Int =
    text.splitToSequence('\n').sumOf { maxOf(1, (it.length + lineChars - 1) / lineChars) }

/** [keep], or one less, so the cut doesn't split a character made of two chars (an emoji). */
private fun keepWhole(text: String, keep: Int): Int =
    if (Character.isHighSurrogate(text[keep - 1])) keep - 1 else keep

/** A folded line keeps at least this many characters before it leaves out the rest. */
private const val FOLDED_MIN_CHARS = 2_000

/**
 * A row of two [columns] (config, usage, scripts). A [bar] goes in the second column, before its
 * text, so the bars of rows line up. [mono]: the row is in the terminal font ([LineModel.mono]).
 */
@Composable
private fun TwoColumns(
    columns: LineModel.Body.TwoColumns,
    mono: Boolean,
    marks: List<String>,
    dense: Boolean,
    bar: StyledText.Meter?
) {
    Row(Modifier.fillMaxWidth()) {
        // Beside a bar (usage, toggles), the name stays on one line and ends with "…" when too long.
        RichText(
            markMatches(remember(columns.name, Term.palette) { styledText(columns.name) }, marks),
            style = lineTextStyle(mono).copy(color = Term.fg, fontWeight = FontWeight.Bold).dense(dense),
            modifier = Modifier
                .weight(0.46f)
                .padding(end = 8.dp),
            maxLines = if (bar != null) 1 else Int.MAX_VALUE,
            overflow = if (bar != null) androidx.compose.ui.text.style.TextOverflow.Ellipsis else androidx.compose.ui.text.style.TextOverflow.Clip,
        )
        val descText = @Composable { modifier: Modifier ->
            LogText(
                markMatches(AnnotatedString(columns.desc), marks),
                style = Term.text.copy(color = Term.dim).dense(dense),
                modifier = modifier
            )
        }
        if (bar == null) descText(Modifier.weight(0.54f))
        else Row(Modifier.weight(0.54f), verticalAlignment = Alignment.CenterVertically) {
            Track(
                bar, Modifier
                    .weight(0.5f)
                    .height(6.dp)
            )
            Spacer(Modifier.width(8.dp))
            descText(Modifier.weight(0.5f))
        }
    }
}

/**
 * A row of `help` (or of the uses in `help <command>`): a bold name on the left, in a column
 * just wide enough for the list's longest name ([LineModel.Body.NameColumns.nameWidthOf]), so
 * names never wrap. Its description is on the right, with its arguments muted and a little
 * smaller under it.
 * - The column takes at most [NAME_COL_MAX] of the width, so a very long name still leaves room.
 * - Lines wrap only at a space or after "|" ([breaksAtSpaces]).
 */
@Composable
private fun NameColumns(
    columns: LineModel.Body.NameColumns,
    mono: Boolean,
    marks: List<String>,
    dense: Boolean
) {
    val nameStyle =
        lineTextStyle(mono).copy(color = Term.fg, fontWeight = FontWeight.Bold).dense(dense)
    val gap = 8.dp
    val want =
        NameWidth.px(columns.nameWidthOf, nameStyle) + with(LocalDensity.current) { gap.roundToPx() }
    val args = columns.args
    Row(Modifier.fillMaxWidth()) {
        RichText(
            markMatches(
                remember(columns.name) { AnnotatedString(breaksAtSpaces(columns.name)) },
                marks
            ),
            style = nameStyle,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val width = minOf(want, (constraints.maxWidth * NAME_COL_MAX).toInt())
                    val placeable =
                        measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(width, placeable.height) { placeable.place(0, 0) }
                }
                .padding(end = gap),
        )
        Column(Modifier.weight(1f)) {
            LogText(
                markMatches(
                    remember(columns.desc, Term.palette) { styledText(breaksAtSpaces(columns.desc)) },
                    marks
                ), style = Term.text.copy(color = Term.fg).dense(dense)
            )
            if (!args.isNullOrEmpty()) {
                LogText(
                    markMatches(AnnotatedString(breaksAtSpaces(args)), marks),
                    style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 1).sp)
                        .dense(dense),
                )
            }
        }
    }
}

/** The most of a row's width that the name column of [NameColumns] takes. */
private const val NAME_COL_MAX = 0.5f

/**
 * The width in px of a [NameColumns] column: its text on one line. Kept for each text and style,
 * so a scroll measures nothing; a measurer is made only for a text not seen yet.
 */
private object NameWidth {
    private data class Key(
        val text: String,
        val style: TextStyle,
        val density: Float,
        val fontScale: Float
    )

    private val cache = HashMap<Key, Int>()

    /** Most widths kept: a few lists, times each font size pinched to. */
    private const val MAX = 64

    @Composable
    fun px(text: String, style: TextStyle): Int {
        val density = LocalDensity.current
        val fonts = LocalFontFamilyResolver.current
        val direction = LocalLayoutDirection.current
        val key =
            Key(text, style.copy(color = Color.Unspecified), density.density, density.fontScale)
        return synchronized(cache) {
            cache[key] ?: run {
                val measured = TextMeasurer(fonts, density, direction, cacheSize = 0).measure(
                    text,
                    style,
                    softWrap = false,
                    maxLines = 1
                )
                // Rounded up, so the last letter is never cut off.
                (measured.size.width + 1).also {
                    if (cache.size >= MAX) cache.clear()
                    cache[key] = it
                }
            }
        }
    }
}

/** `apps` output: one line of chips instead of one line per app. With [scramble], their names are scrambled as line [id] is. */
@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    onTap: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    scramble: Scramble?,
    id: Long,
) {
    val haptics = LocalHapticFeedback.current
    if (apps.isEmpty()) {
        LogText("(apps no longer installed)", style = Term.text.copy(color = Term.dim))
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        apps.forEachIndexed { i, app ->
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
                    LogText(
                        (scramble?.line(app.profile.kind.tag, id, Scramble.APP) ?: app.profile.kind.tag) + if (app.profile.quiet) "⏸" else "",
                        style = Term.text.copy(
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = (Term.fontSize - 2).sp
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                LogText(scramble?.line(app.label, id, Scramble.APP * 100 + i) ?: app.label, style = Term.text)
            }
        }
    }
}

@Composable
internal fun NotifIcon(key: String) {
    // A scrambled line's icon ([Scramble.PICTURE]): a plain block in its place.
    if (Scramble.isPicture(key)) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(Term.dim, RoundedCornerShape(2.dp))
                .testTag("scrambled-icon")
        )
        return
    }
    val context = LocalContext.current
    val icon by produceState(NotifIcons.cached(key), key) {
        if (value == null) value =
            withContext(Dispatchers.IO) { NotifIcons.load(context.applicationContext, key) }
    }
    icon?.let {
        Image(
            it,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Term.dim),
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * How far down the time goes so its baseline meets the line's first one: the first baselines of
 * the two styles, measured on a "0", apart.
 * - Kept for each pair of styles, so a scroll measures nothing. A measurer is made only for a pair
 *   not seen yet.
 * - Colors don't move a baseline, so they are left out of the key: a new theme measures nothing.
 */
internal object TimeShift {
    private data class Key(
        val line: TextStyle,
        val time: TextStyle,
        val density: Float,
        val fontScale: Float,
        val direction: LayoutDirection
    )

    private val cache = HashMap<Key, Dp>()

    /** Most pairs seen in a session: a few kinds of line, times each font size pinched to. */
    private const val MAX_PAIRS = 64

    @Composable
    fun of(line: TextStyle, time: TextStyle): Dp {
        val density = LocalDensity.current
        val fonts = LocalFontFamilyResolver.current
        val direction = LocalLayoutDirection.current
        val key = Key(
            line.copy(color = Color.Unspecified),
            time.copy(color = Color.Unspecified),
            density.density,
            density.fontScale,
            direction
        )
        return synchronized(cache) {
            cache[key] ?: run {
                val measurer = TextMeasurer(fonts, density, direction, cacheSize = 0)
                val gap = measurer.measure("0", line).firstBaseline - measurer.measure(
                    "0",
                    time
                ).firstBaseline
                with(density) { gap.coerceAtLeast(0f).toDp() }.also {
                    if (cache.size >= MAX_PAIRS) cache.clear()
                    cache[key] = it
                }
            }
        }
    }
}
