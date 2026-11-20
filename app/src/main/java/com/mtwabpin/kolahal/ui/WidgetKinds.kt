package com.mtwabpin.kolahal.ui

import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import com.mtwabpin.kolahal.R
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import java.time.ZoneId


/** The colour of a widget's badge, bar and chart: its role's colour in this theme, or the accent. */
internal fun roleColour(base: WidgetBase?): Color = colourOf(base?.color ?: StyledText.Colour.ACCENT)

/** Height of the "+3 more" line under a list, in dp. */
private const val MORE_DP = 20

/** The thickness of a flat bar's line ([LevelBar]). */
private val BAR_LINE = 6.dp

/** The thickness of a wave's line ([LevelBar]): Material's own. */
private val WAVE_LINE = 4.dp

/** The length of one wave of a bar ([LevelBar]), as the mockup draws it. A moving wave moves one length a second. */
private val WAVELENGTH = 20.dp

/** A wave of no height: a bar or ring that stands still. A wave would say "working". */
private val STILL: (Float) -> Float = { 0f }

/**
 * Draws the content of [spec] for the [box] it gets, inside a card ([WidgetCard]). It fills the
 * space it is given and draws no frame and no title.
 * - Each kind has a small, a wide and a large layout. A small one leaves parts out; the card's
 *   description for a screen reader still has them.
 * - [onTap] gets the action of a row, a button, a switch or an event. With null, nothing inside
 *   the content takes a tap (the Widgets screen).
 * - [stamp] is a number that changes when the content was filled again, also with the same
 *   description. A switch that waits for its script watches it. 0 for a card that has none.
 */
@Composable
internal fun KindBody(spec: WidgetSpec, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?, stamp: Long = 0) {
    when (spec) {
        is WidgetSpec.Stat -> StatBody(spec, box)
        is WidgetSpec.Text -> TextBody(spec, box)
        is WidgetSpec.Rows -> RowsBody(spec, box, onTap)
        is WidgetSpec.Progress -> ProgressBody(spec, box)
        is WidgetSpec.Chart -> ChartBody(spec, box)
        is WidgetSpec.Countdown -> CountdownBody(spec, box)
        is WidgetSpec.Buttons -> ButtonsBody(spec, onTap)
        is WidgetSpec.Toggles -> TogglesBody(spec, onTap, stamp)
        is WidgetSpec.Clock -> ClockBody(spec, box)
        is WidgetSpec.Agenda -> AgendaBody(spec, onTap)
        is WidgetSpec.Image -> ImageBody(spec, box)
        is WidgetSpec.Media -> MediaBody(spec, box, onTap)
    }
}

/**
 * [text] as a card draws it, with its styles ([widgetText]). Only a few fields may have styles
 * ([com.mtwabpin.kolahal.widgets.WidgetParser.STYLED]). It is made again only when the text or the
 * theme's colours change.
 */
@Composable
internal fun cardText(text: String): AnnotatedString = remember(text, Term.palette) { widgetText(text) }

/** One line in the middle of the body, for "Nothing to show" and the like. */
@Composable
internal fun QuietLine(text: String, small: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = lookColors().muted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A bar from 0 to 1 in [colour]. It moves to a new value on the theme's spring.
 * - With [moving], and while the widgets page is in view, it is a wave that moves: something is
 *   in progress.
 * - With [wavy] it is a wave that stands still, as the mockup draws a countdown. Its wave speed is
 *   0, so it costs nothing per frame: the indicator moves its wave only with a speed above 0. Its
 *   height changes once, on the theme's spring, when the bar passes 10% or 95%.
 * - Any other bar is flat, also with a wave speed of 0. With any other speed the indicator asks
 *   for every frame, also while its wave has no height.
 * - A flat bar's line is 6 dp, not Material's 4: a card is seen from afar. A wave keeps Material's
 *   4 dp, so it has room to wave in the 10 dp container.
 * - It is not an item for a screen reader: the card's description says the value.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LevelBar(fraction: Float, moving: Boolean, colour: Color, modifier: Modifier = Modifier, wavy: Boolean = false) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "level")
    val runs = moving && cardSeen()
    val waves = runs || wavy
    val line = if (moving || wavy) WAVE_LINE else BAR_LINE
    val stroke = with(LocalDensity.current) { Stroke(width = line.toPx(), cap = StrokeCap.Round) }
    LinearWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
        color = colour,
        trackColor = colour.copy(alpha = 0.24f),
        stroke = stroke,
        trackStroke = stroke,
        // No dot at the track's end: the bar's end says where it stops.
        stopSize = 0.dp,
        // Both lambdas are the same objects every time, so a new frame of the card does not throw the indicator's drawing away.
        amplitude = if (waves) WavyProgressIndicatorDefaults.indicatorAmplitude else STILL,
        wavelength = WAVELENGTH,
        waveSpeed = if (runs) WAVELENGTH else 0.dp,
    )
}

/** The big text of a kind, by the room it has. */
@Composable
internal fun valueStyle(box: WidgetSizes.Box) = when (box) {
    WidgetSizes.Box.SMALL -> MaterialTheme.typography.titleLarge
    WidgetSizes.Box.WIDE -> MaterialTheme.typography.headlineSmall
    WidgetSizes.Box.LARGE -> MaterialTheme.typography.headlineLarge
}

/**
 * [style] for a value: extra bold and a little tighter, with digits of one width. A number that
 * ticks does not shake, and a value stands out from the words around it.
 */
internal fun TextStyle.asValue(): TextStyle = copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")

/**
 * A kind's big text, on one line, as a value ([asValue]). A text too long for its room gets
 * smaller, down to 12 sp, before it is cut with "…". A text whose digits alone changed (a countdown
 * that ticks) keeps its size ([SteadyAutoSize]).
 */
@Composable
internal fun BigText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val size = remember(style) {
        val largest = style.fontSize.value.let { if (it.isNaN()) 22f else maxOf(it, 12f) }
        SteadyAutoSize(TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = largest.sp, stepSize = 1.sp))
    }
    val value = remember(style) { style.asValue() }
    Text(text, modifier = modifier, color = color, autoSize = size, style = value, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/**
 * Bold's big value, [size] at most, on one line in Bold's narrow black type ([boldValueStyle]). As
 * [BigText], it gets smaller to fit its room, down to 12 sp, and keeps its size while only its
 * digits change.
 */
@Composable
internal fun BoldText(text: String, size: TextUnit, colour: Color, modifier: Modifier = Modifier) {
    val auto = remember(size) { SteadyAutoSize(TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = maxOf(size.value, 12f).sp, stepSize = 2.sp)) }
    val style = remember(size) { boldValueStyle(size) }
    // The line is as tall as the type, so a digit's top or bottom may stand out of it a little: not clipped.
    Text(text, modifier = modifier, color = colour, autoSize = auto, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
}

/**
 * `stat`: one big value with its label.
 * - Small: the value and the label, centred.
 * - Wide: the value with its change at the start, and a column at the end: the label over the
 *   first smaller value. The others go between them, as many as fit. A box under 150 dp has no
 *   room for that column: the label goes under the value, and a bar takes the label's place.
 * - Large: the value and its label at the top. The smaller values and the bar are at the bottom.
 */
@Composable
private fun StatBody(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val look = LocalWidgetLook.current
    if (look.untitled()) {
        StatLook(spec, box, look)
        return
    }
    val type = MaterialTheme.typography
    // Plain, or Tonal: the same layout, with chips and quiet text in the Tonal card's colours.
    val muted = lookColors(look).muted
    if (box == WidgetSizes.Box.SMALL) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(WidgetWords.joined(spec.value, spec.unit), valueStyle(box))
            spec.label?.let { Text(cardText(it), style = type.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        return
    }
    val colour = roleColour(spec.base)
    val large = box == WidgetSizes.Box.LARGE
    val hasChange = spec.trend != null || spec.delta != null
    val value: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier) {
            BigText(spec.value, valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
            spec.unit?.let {
                Text(it, style = type.titleMedium, color = muted, maxLines = 1, modifier = Modifier.padding(start = 2.dp).alignByBaseline())
            }
            // A large card has the change under the value, beside the label, as the mockup has it.
            if (hasChange && !large) ChangeText(spec.trend, spec.delta, colour, type.labelLarge, Modifier.padding(start = 8.dp).alignByBaseline())
        }
    }
    if (large) {
        Column(Modifier.fillMaxSize()) {
            value(Modifier)
            if (spec.label != null || hasChange) Row(verticalAlignment = Alignment.CenterVertically) {
                spec.label?.let { Text(cardText(it), style = type.bodyMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                if (hasChange) ChangeText(spec.trend, spec.delta, colour, type.titleSmall, Modifier.padding(start = if (spec.label != null) 8.dp else 0.dp))
            }
            Spacer(Modifier.weight(1f))
            if (spec.more.isNotEmpty()) {
                if (look == WidgetLook.TONAL) {
                    // Tonal has the smaller values in chips of a lighter tint, as the mockup has them.
                    val tile = lookColors(look).tile
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        spec.more.forEach { SmallValue(it, muted, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(tile).padding(vertical = 6.dp), centred = true) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        spec.more.forEach { SmallValue(it, muted, Modifier.weight(1f, fill = false)) }
                    }
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, colour, Modifier.padding(top = 8.dp)) }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth >= STAT_SIDE_FROM
        val first = spec.more.firstOrNull()
        // A smaller value between the big one and the end column needs about 64 dp, after 112 dp
        // for the big one and 96 dp for the column.
        val column = if (side && (spec.label != null || first != null)) 96.dp else 0.dp
        val between = if (side) spec.more.drop(1) else spec.more
        val half = maxWidth / 2
        val beside = ((maxWidth - 112.dp - column) / 64.dp).toInt().coerceIn(0, between.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    value(Modifier)
                    if (!side && spec.progress == null) spec.label?.let {
                        Text(cardText(it), style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                between.take(beside).forEach { SmallValue(it, muted, Modifier.padding(start = 12.dp).widthIn(max = 72.dp)) }
                if (column > 0.dp) {
                    Column(Modifier.padding(start = 12.dp).widthIn(max = half), horizontalAlignment = Alignment.End) {
                        spec.label?.let {
                            // Over a smaller value the label is the column's heading. Alone it is a quiet note, on up to two lines.
                            if (first != null) Text(cardText(it), style = type.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else Text(cardText(it), style = type.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                        }
                        first?.let {
                            Text(WidgetWords.joined(it.value, it.label), style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, colour, Modifier.padding(top = 4.dp)) }
        }
    }
}

/** From this width, a wide stat has a column at its end for its label and its first smaller value. */
private val STAT_SIDE_FROM = 150.dp

/**
 * A stat's change in [colour]: a drawn arrow for up or down (a text arrow for flat), then the
 * delta in bold. The arrow is as tall as the text.
 */
@Composable
internal fun ChangeText(trend: String?, delta: String?, colour: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val drawn = when (trend) {
        "up" -> R.drawable.ic_arrow_up
        "down" -> R.drawable.ic_arrow_down
        else -> null
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (drawn != null) Icon(painterResource(drawn), null, tint = colour, modifier = Modifier.size(with(LocalDensity.current) { style.fontSize.toDp() }))
        else WidgetWords.arrow(trend)?.let { Text(it, style = style, color = colour, maxLines = 1) }
        delta?.let { Text(it, style = style.copy(fontWeight = FontWeight.Bold), color = colour, maxLines = 1, modifier = Modifier.padding(start = 2.dp)) }
    }
}

/** One of a stat's smaller values, over its label in [muted]. With [centred], both are centred. */
@Composable
internal fun SmallValue(small: WidgetSpec.Small, muted: Color, modifier: Modifier = Modifier, centred: Boolean = false) {
    Column(modifier, horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
        Text(small.value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        small.label?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `text`: a few lines, cut with "…" where the box ends, and a caption under them. A small box has no
 * caption. A large box has the text in bold, as the mockup has it: there it is the card's main thing.
 */
@Composable
private fun TextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val look = LocalWidgetLook.current
    if (look.untitled()) {
        TextLook(spec, box, look)
        return
    }
    val type = MaterialTheme.typography
    val style = when (box) {
        WidgetSizes.Box.SMALL -> type.bodySmall
        WidgetSizes.Box.WIDE -> type.bodyMedium
        WidgetSizes.Box.LARGE -> type.titleMedium.copy(fontWeight = FontWeight.Bold)
    }
    Column(Modifier.fillMaxSize()) {
        // The text gets the height that is left, and its last line that fits ends in "…".
        Text(cardText(spec.text), style = style, color = MaterialTheme.colorScheme.onSurface, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).fillMaxWidth())
        if (box != WidgetSizes.Box.SMALL) spec.caption?.let {
            Text(cardText(it), style = type.labelMedium, color = lookColors(look).muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `list`: as many rows as fit, then "+3 more", with its [WidgetSpec.Lead] over them when it has one.
 * - Each row is in a lightly tinted container: large corners at the ends of the list, small ones
 *   between rows, and [ROW_GAP_DP] between two ([segmentShape]).
 * - A row is 44 dp tall when it can be tapped or has a second line, and 32 dp otherwise. A tap row
 *   is a little under the 48 dp of a button: a list is read more than it is tapped, and a 4x2 card
 *   then shows three rows.
 * - When every row fits, the rows grow to share the height evenly, up to 56 dp
 *   ([WidgetWords.rowHeight]). Three days of a forecast then fill a 2x2 card. Rows under a lead
 *   keep their height, as the mockup's Reminders card has them.
 * - A narrow list (under 200 dp) leaves the second lines out.
 * - "+3 more" is at the end of the card's title line ([ReportMore]). A card without one has the
 *   line under the rows.
 * - The lead: in a large box, the big value and its label over the rows. A small or a wide box
 *   has room for the lead alone, and counts the rows as "+3 more".
 */
@Composable
private fun RowsBody(spec: WidgetSpec.Rows, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val lead = spec.lead
    val look = LocalWidgetLook.current
    when {
        lead == null && spec.items.isEmpty() -> QuietLine(spec.empty ?: WidgetWords.NOTHING)
        look.untitled() -> RowsLook(spec, box, onTap, look)
        lead == null -> RowList(spec.items, onTap)
        box != WidgetSizes.Box.LARGE -> {
            ReportMore(if (spec.items.isNotEmpty()) "+${spec.items.size} more" else null)
            Box(Modifier.fillMaxSize(), contentAlignment = if (box == WidgetSizes.Box.SMALL) Alignment.Center else Alignment.CenterStart) {
                LeadLine(lead, valueStyle(box), centred = box == WidgetSizes.Box.SMALL)
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            LeadLine(lead, MaterialTheme.typography.headlineMedium, centred = false)
            if (spec.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Under a lead the rows keep their height, as the mockup's Reminders card has them.
                Box(Modifier.weight(1f).fillMaxWidth()) { RowList(spec.items, onTap, fill = false) }
            }
        }
    }
}

/** A list's lead: the value big, its unit small, and the label under them. */
@Composable
private fun LeadLine(lead: WidgetSpec.Lead, style: TextStyle, centred: Boolean) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    Column(horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
        Row {
            BigText(lead.value, style, Modifier.weight(1f, fill = false).alignByBaseline())
            lead.unit?.let { Text(it, style = type.titleMedium, color = muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp).alignByBaseline()) }
        }
        lead.label?.let { Text(it, style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A list's rows, as many as fit the box, then "+3 more" ([RowsBody]). With [fill], rows that all fit share the box's height. */
@Composable
private fun RowList(items: List<WidgetSpec.Row>, onTap: ((WidgetTap) -> Unit)?, fill: Boolean = true) {
    val inTitle = LocalMoreNote.current != null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val details = maxWidth >= 200.dp && items.any { it.detail != null }
        val rowDp = when {
            items.any { it.tap != null } -> TAP_ROW_DP
            details -> TAP_ROW_DP
            else -> 32
        }
        val heightDp = maxHeight.value.toInt()
        val moreDp = if (inTitle) 0 else MORE_DP
        val shown = WidgetWords.rowsShown(items.size, heightDp, rowDp, moreDp, ROW_GAP_DP)
        val more = items.size - shown
        // All rows drawn: they share the height, so a short list fills the card.
        val rowHeight = if (fill) WidgetWords.rowHeight(items.size, shown, maxHeight.value, rowDp, ROW_GAP_DP).dp else rowDp.dp
        ReportMore(if (more > 0) "+$more more" else null)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp)) {
            items.take(shown).forEachIndexed { i, row ->
                key(i) { ListRow(row, rowHeight, details, segmentShape(i, shown), onTap) }
            }
            if (!inTitle && WidgetWords.showsMore(items.size, shown, heightDp, rowDp, MORE_DP, ROW_GAP_DP)) {
                Text(
                    "+$more more",
                    style = MaterialTheme.typography.labelMedium,
                    color = lookColors().muted,
                    maxLines = 1,
                    modifier = Modifier.height(MORE_DP.dp).wrapContentHeight(),
                )
            }
        }
    }
}

/** The height of a list's or an agenda's row that can be tapped, or that has two lines, in dp. */
internal const val TAP_ROW_DP = 44

/** The room between two rows of a list or of a clock, in dp. */
internal const val ROW_GAP_DP = 2

/**
 * The shape of row [index] of [count] in a segmented list: 16 dp corners at the list's two ends,
 * 4 dp between rows, as Settings groups its rows ([segShapes]) in less room.
 */
internal fun segmentShape(index: Int, count: Int): Shape {
    val outer = 16.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** The tint of a segmented row ([segmentShape]): a little lighter than the card. */
@Composable
internal fun segmentColour(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

/**
 * One row of a list, in its container of [shape]. Its colour is on its icon, or else on a dot at
 * its start. The text at its end is dim: the colour says what the row is, not what its number is.
 * A row with a tap takes the press before the card does, so it hands a long-press on to the card
 * ([LocalCardLongPress]).
 *
 * A row with `done` has a checkbox at its start, in the row's colour.
 * - The box takes no tap of its own. A tap anywhere on the row is the row's tap, and the row is
 *   the checkbox for a screen reader.
 * - The box shows what the description says. Kolahal does not tick it on a tap: the script (or the
 *   built-in widget) shows the new state.
 */
@Composable
private fun ListRow(row: WidgetSpec.Row, height: Dp, details: Boolean, shape: Shape, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val done = row.done
    val colour = row.color?.let { colourOf(it) }
    // Tonal has its rows in a lighter tint of its card, and its quiet text a little brighter.
    val colours = lookColors()
    val muted = colours.muted
    val press = rowPress(row, onTap)
    Row(
        Modifier.fillMaxWidth().height(height).clip(shape).background(if (LocalWidgetLook.current == WidgetLook.TONAL) colours.tile else segmentColour()).then(press)
            .padding(start = if (done != null) 0.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = row.icon
        if (done != null) RowCheck(done, colour ?: scheme.primary, Modifier.padding(start = 8.dp, end = 8.dp))
        if (icon != null) {
            // Small and quiet, as in the mockup: the row's text says what it is, the icon only helps.
            WidgetIconText(icon, type.titleMedium, colour ?: muted, size = 18.dp, modifier = Modifier.padding(end = 10.dp))
        } else if (done == null && colour != null) {
            Box(Modifier.padding(end = 10.dp).size(8.dp).clip(CircleShape).background(colour))
        }
        Column(Modifier.weight(1f)) {
            Text(cardText(row.title), style = type.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details) row.detail?.let {
                Text(cardText(it), style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        row.trailing?.let {
            Text(cardText(it), style = type.labelLarge, color = muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * The press of a list's [row] with a tap: its own tap, and the card's long-press handed on
 * ([LocalCardLongPress]). A row with `done` is a checkbox for a screen reader, with its state. No
 * press for a row without a tap, or where nothing takes taps.
 */
@Composable
internal fun rowPress(row: WidgetSpec.Row, onTap: ((WidgetTap) -> Unit)?): Modifier {
    val tap = row.tap
    val done = row.done
    val longPress = LocalCardLongPress.current
    return if (tap != null && onTap != null) {
        Modifier.combinedClickable(
            onClickLabel = tap.label,
            role = if (done != null) Role.Checkbox else Role.Button,
            onLongClickLabel = if (longPress != null) "arrange widgets" else null,
            onLongClick = longPress,
            onClick = { onTap(tap) },
        ).then(if (done != null) Modifier.semantics { toggleableState = ToggleableState(done) } else Modifier)
    } else {
        Modifier
    }
}

/**
 * `progress`: how far along something is.
 * - A ring with its text in the middle, or a bar. With no `style`, a wide box gets the bar and the
 *   others get the ring.
 * - With `items`, it is several bars, each with its label and its text while there is room.
 * - With `moving`, the ring and the bars are waves while the page is in view.
 */
@Composable
private fun ProgressBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box) {
    val colour = roleColour(spec.base)
    val look = LocalWidgetLook.current
    when {
        look.untitled() -> ProgressLook(spec, box, look, colour)
        spec.items.isNotEmpty() -> BarsBody(spec, colour)
        spec.style == "ring" || (spec.style != "bar" && box != WidgetSizes.Box.WIDE) -> RingBody(spec, box, colour)
        else -> BarBody(spec, box, colour)
    }
}

/**
 * A ring from 0 to 1 in [colour], [side] across. It moves to a new value on the theme's spring.
 * Like [LevelBar], it is a wave only with [moving] and in view, and it is no item for a screen reader.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Ring(fraction: Float, moving: Boolean, colour: Color, side: Dp) {
    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "ring")
    // A bigger ring gets a thicker line: an eighth of its side.
    val stroke = with(LocalDensity.current) { Stroke(width = (side / 8).coerceIn(4.dp, 14.dp).toPx(), cap = StrokeCap.Round) }
    val wave = moving && cardSeen()
    CircularWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = Modifier.size(side).clearAndSetSemantics {},
        color = colour,
        trackColor = colour.copy(alpha = 0.24f),
        stroke = stroke,
        trackStroke = stroke,
        amplitude = if (wave) WavyProgressIndicatorDefaults.indicatorAmplitude else STILL,
        wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
        waveSpeed = if (wave) WavyProgressIndicatorDefaults.CircularWavelength else 0.dp,
    )
}

/**
 * The ring.
 * - Small and large: as big as the box allows, with its text inside. Large has the label under it,
 *   in bold and in the card's colour.
 * - Wide: the ring at the start. With no title line ([LocalUntitled]), as the mockup has it: the
 *   text inside the ring, and the title in bold beside it over the label. With a title line: the
 *   text and the label beside the ring.
 */
@Composable
private fun RingBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val tonal = LocalWidgetLook.current == WidgetLook.TONAL
    val muted = lookColors().muted
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val untitled = LocalUntitled.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (box == WidgetSizes.Box.WIDE) {
            // Without a title line the title shares the row, so the ring takes less of the width.
            val side = (if (untitled != null) min(maxHeight, maxWidth * 0.42f) else min(maxHeight, maxWidth / 2)).coerceAtLeast(24.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (untitled != null) {
                    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                        Ring(fraction, spec.moving, colour, side)
                        BigText(text, if (side >= 96.dp) type.titleLarge else type.titleSmall, Modifier.padding(horizontal = side / 5))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(cardText(untitled), style = type.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        spec.label?.let { RingLabel(it, spec.labelIcon, type.bodyMedium, muted) }
                    }
                } else {
                    Ring(fraction, spec.moving, colour, side)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        BigText(text, type.titleLarge)
                        spec.label?.let { RingLabel(it, spec.labelIcon, type.bodySmall, muted) }
                    }
                }
            }
        } else {
            val label = spec.label.takeIf { box == WidgetSizes.Box.LARGE }
            val labelRoom = if (label != null) 24.dp else 0.dp
            val side = min(maxWidth, maxHeight - labelRoom).coerceAtLeast(24.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    Ring(fraction, spec.moving, colour, side)
                    // The text stays clear of the ring's line, which is an eighth of the side.
                    BigText(text, if (side >= 96.dp) type.titleLarge else type.titleSmall, Modifier.padding(horizontal = side / 5))
                }
                // The label in bold and in the card's colour, as the mockup has it: it says what the number means.
                // On Tonal's card of that colour it is in the text colour.
                if (label != null) {
                    RingLabel(label, spec.labelIcon, type.titleSmall.copy(fontWeight = FontWeight.Bold), if (tonal) MaterialTheme.colorScheme.onSurface else colour, Modifier.height(labelRoom).wrapContentHeight())
                }
            }
        }
    }
}

/** A ring's label in [style] and [colour], after its [icon] (a drawable) when it has one: the Battery card's network. */
@Composable
internal fun RingLabel(label: String, icon: Int?, style: TextStyle, colour: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            // As tall as the text's font size, in the text's colour.
            val side = with(LocalDensity.current) { style.fontSize.toDp() }
            Icon(painterResource(icon), contentDescription = null, tint = colour, modifier = Modifier.padding(end = 6.dp).size(side))
        }
        Text(label, style = style, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One bar under its text. The label is beside the text. A large box has the text at its top and
 * the bar at its bottom; the others have both in the middle.
 */
@Composable
private fun BarBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val large = box == WidgetSizes.Box.LARGE
    Column(Modifier.fillMaxSize(), verticalArrangement = if (large) Arrangement.SpaceBetween else Arrangement.Center) {
        Row {
            BigText(spec.text ?: WidgetWords.percent(spec.value, spec.max), valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
            if (box != WidgetSizes.Box.SMALL) spec.label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = lookColors().muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 96.dp).alignByBaseline(),
                )
            }
        }
        LevelBar(WidgetWords.fraction(spec.value, spec.max), spec.moving, colour, Modifier.padding(top = 4.dp))
    }
}

/** A bar with its label and its text over it needs this much height, in dp. */
internal const val NAMED_BAR_DP = 30

/** A bar alone needs this much. */
private const val BAR_DP = 14

/**
 * Several bars. Each has its label and its text over it when the box is tall enough for all of
 * them. A lower box draws the bars alone, as many as fit. A bar's own colour wins over the card's.
 */
@Composable
private fun BarsBody(spec: WidgetSpec.Progress, colour: Color) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val muted = lookColors().muted
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val named = maxHeight >= NAMED_BAR_DP.dp * spec.items.size
        val count = if (named) spec.items.size else (maxHeight / BAR_DP.dp).toInt().coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    Column {
                        if (named) Row {
                            Text(bar.label.orEmpty(), style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(bar.text ?: WidgetWords.percent(bar.value, bar.max), style = type.labelMedium, color = scheme.onSurface, maxLines = 1)
                        }
                        LevelBar(WidgetWords.fraction(bar.value, bar.max), spec.moving, bar.color?.let { colourOf(it) } ?: colour)
                    }
                }
            }
        }
    }
}

/**
 * `countdown`: the time to a moment, or since it. Kolahal does the ticking: the script gave the
 * moment once, and no run is needed as time passes.
 * - The time moves by the minute, with the header's clock ([rememberNow]).
 * - It moves by the second ([EverySecond]) only while the text shows seconds and the card can be seen.
 *   A tick draws the number again ([CountdownValue]), not the whole card.
 * - The number is big and its unit small: "36 days" ([WidgetWords.Count]).
 * - With a start (`from`), a bar runs from the start to the moment. In a wide box the bar takes the
 *   label's place. A small box has no bar. A large box has the time at its top and the bar at its
 *   bottom, with the start and the moment under its ends: "1 Oct" and "20 Dec"
 *   ([WidgetWords.countdownEnds]).
 * - The bar is a wave that stands still ([LevelBar] with `wavy`). A countdown that waits is not at
 *   work. A wave that moves is drawn again on every frame, for days; a still one costs nothing.
 * - The bar moves in steps ([WidgetWords.barStep]), so a clock that ticks does not draw it again
 *   every second.
 */
@Composable
private fun CountdownBody(spec: WidgetSpec.Countdown, box: WidgetSizes.Box) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    // The time now, in seconds. Only what changes with it reads it: the count and the bar's step.
    val now = remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    // The minute's tick. It also comes when home is back on screen, and when the clock is set.
    LaunchedEffect(minute) { now.longValue = System.currentTimeMillis() / 1000 }
    val count = remember(spec.to, spec.style, spec.reached, zone) {
        derivedStateOf { WidgetWords.countdown(spec.to, now.longValue, spec.style, spec.reached, zone) }
    }
    val ticks by remember(count) { derivedStateOf { count.value.ticks } }
    EverySecond(ticks) { now.longValue = it / 1000 }
    val small = box == WidgetSizes.Box.SMALL
    val large = box == WidgetSizes.Box.LARGE
    val type = MaterialTheme.typography
    val look = LocalWidgetLook.current
    val muted = lookColors(look).muted
    // Shape draws the wait as a ring around a small card too.
    val barless = small && look != WidgetLook.SHAPE
    val bar by remember(barless, spec.from, spec.to) {
        derivedStateOf { if (barless) null else WidgetWords.countdownFraction(spec.from, spec.to, now.longValue)?.let(WidgetWords::barStep) }
    }
    val clock24 = rememberClock24()
    // A large box writes the start and the moment under the bar's ends. They change at most once a day.
    // Bold, Split and Shape write the moment in any box, also without a start: then it is the day
    // ("27 Dec"), or the time on the day itself.
    val from = spec.from
    val day by remember { derivedStateOf { now.longValue / 86_400 } }
    val ends = if ((large && from != null) || look.untitled()) {
        remember(from, spec.to, zone, clock24, day) {
            val at = Snapshot.withoutReadObservation { now.longValue }
            WidgetWords.countdownEnds(from ?: at, spec.to, at, zone, clock24)
        }
    } else {
        null
    }
    if (look.untitled()) {
        CountdownLook(spec, box, look, count, bar, ends?.second, ends.takeIf { from != null }, now)
        return
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = if (large) Arrangement.Top else Arrangement.Center,
        horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        CountdownValue(count, large, box)
        if (bar == null || large) spec.label?.let {
            // A small box has room for two short lines: "to 20 / December".
            Text(it, style = if (large) type.bodyMedium else type.bodySmall, color = muted, maxLines = if (small) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = if (small) TextAlign.Center else TextAlign.Start)
        }
        bar?.let { fraction ->
            // A large box has the bar at its bottom.
            if (large) Spacer(Modifier.weight(1f))
            // A wave that stands still: a countdown waits, it is not at work.
            LevelBar(fraction, moving = false, roleColour(spec.base), Modifier.padding(top = 4.dp), wavy = true)
            ends?.let { (start, end) ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(start, style = type.bodySmall, color = muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(end, style = type.bodySmall, color = muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** A countdown's number big and its unit small: "36 days". It alone is drawn again at each tick. */
@Composable
private fun CountdownValue(count: State<WidgetWords.Count>, large: Boolean, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val now = count.value
    Row {
        BigText(now.value, if (large) type.displayMedium else valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
        now.unit?.let {
            val unitStyle = if (large) type.titleLarge.copy(fontWeight = FontWeight.Bold) else type.titleMedium
            Text(it, style = unitStyle, color = lookColors().muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp).alignByBaseline())
        }
    }
}

/**
 * `chart`: numbers over time as a line or as bars, with a headline.
 * - Small: the headline over a small plot.
 * - Wide: the headline with a small plot beside it.
 * - Large: the value big, its label under it (as a stat has them), the plot, and under the plot
 *   the labels of the first and the last point.
 * The plot has no words of its own. The card's description says the chart for a screen reader.
 */
@Composable
private fun ChartBody(spec: WidgetSpec.Chart, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val colour = roleColour(spec.base)
    val look = LocalWidgetLook.current
    if (look.untitled()) {
        ChartLook(spec, box, look, colour)
        return
    }
    val colours = lookColors(look, colour)
    val muted = colours.muted
    val bars = spec.style == "bars"
    val value = spec.value
    val label = spec.label
    // The ring around the line's last dot is in the card's colour.
    val surface = colours.card
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            // The value on its own line with its label under it, as a stat has them.
            if (value != null) BigText(value, valueStyle(box))
            if (label != null) Text(label, style = type.bodyMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Plot(spec.points, bars, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), surface)
            if (spec.labels.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(spec.labels.first(), style = type.labelSmall, color = muted, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (spec.labels.size > 1) Text(spec.labels.last(), style = type.labelSmall, color = muted, maxLines = 1)
            }
        }
        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (value != null || label != null) Column(Modifier.padding(end = 12.dp).widthIn(max = 120.dp)) {
                if (value != null) BigText(value, type.titleLarge)
                if (label != null) Text(label, style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Plot(spec.points, bars, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxHeight(), surface)
        }
        WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (value != null) BigText(value, type.titleMedium)
            Plot(spec.points, bars, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxWidth(), surface)
        }
    }
}

/**
 * The plot of a chart, on a Canvas, in [colour]. There is no chart library.
 * - A line is 2 dp, with a wash under it. A dot marks the latest point. The dot has a ring in
 *   the card's colour, so it stays clear of the line.
 * - A bar is at most 24 dp thick, with 4 dp between bars (less when there are many). The latest
 *   bar is in full colour, the others lighter. A bar is round at its top and square at the
 *   baseline, which is a hairline.
 * - The scale is [min] to [max], or what the data gives ([WidgetWords.scale]). A value outside the
 *   scale is drawn at its edge.
 * - [surface] is the colour under the plot, for the dot's ring.
 */
@Composable
internal fun Plot(points: List<Float>, bars: Boolean, min: Float?, max: Float?, colour: Color, modifier: Modifier = Modifier, surface: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val scale = remember(points, bars, min, max) { WidgetWords.scale(points, min, max, bars) }
    Canvas(modifier) {
        if (points.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val (low, high) = scale
        val line = 2.dp.toPx()
        // How high a value sits, from 0 at the bottom of the scale to 1 at its top.
        fun level(v: Float) = WidgetWords.fraction(v - low, high - low)
        if (bars) {
            // 4 dp between bars, less when there are many: a bar keeps two thirds of its slot.
            val slot = size.width / points.size
            val gap = minOf(4.dp.toPx(), slot / 3)
            val thick = (slot - gap).coerceIn(1f, 24.dp.toPx())
            val round = CornerRadius(minOf(5.dp.toPx(), thick / 2))
            // The latest bar is in full colour, the others lighter: the latest value is the news.
            val before = Path()
            val latest = Path()
            points.forEachIndexed { i, v ->
                val x = i * slot + (slot - thick) / 2
                // A value at the bottom of the scale still shows as a sliver.
                val top = (size.height * (1f - level(v))).coerceAtMost(size.height - line)
                (if (i == points.lastIndex) latest else before).addRoundRect(RoundRect(Rect(x, top, x + thick, size.height), topLeft = round, topRight = round))
            }
            drawPath(before, colour.copy(alpha = 0.4f))
            drawPath(latest, colour)
            drawLine(hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        } else {
            val dot = 4.dp.toPx()
            // Room for the dot and its ring at the end, the top and the bottom.
            val pad = dot + line
            val left = line / 2
            val right = (size.width - pad).coerceAtLeast(left)
            val tall = (size.height - 2 * pad).coerceAtLeast(0f)
            fun x(i: Int) = if (points.size == 1) right else left + (right - left) * i / (points.size - 1)
            fun y(v: Float) = pad + tall * (1f - level(v))
            if (points.size > 1) {
                val path = Path()
                val wash = Path()
                wash.moveTo(left, size.height)
                points.forEachIndexed { i, v ->
                    if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                    wash.lineTo(x(i), y(v))
                }
                wash.lineTo(right, size.height)
                wash.close()
                drawPath(wash, colour.copy(alpha = 0.2f))
                drawPath(path, colour, style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val end = Offset(x(points.lastIndex), y(points.last()))
            drawCircle(surface, radius = dot + line, center = end)
            drawCircle(colour, radius = dot, center = end)
        }
    }
}

/**
 * A row's checkbox, as the mockup draws it: a rounded square, 18 dp. Open: an outline in the
 * quiet text colour. Done: filled with [colour], with a check. The row says its state to a screen reader.
 */
@Composable
internal fun RowCheck(done: Boolean, colour: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(18.dp).clip(shape)
            .then(if (done) Modifier.background(colour) else Modifier.border(2.dp, scheme.onSurfaceVariant, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(painterResource(R.drawable.wi_check), contentDescription = null, tint = scheme.surfaceContainerHigh, modifier = Modifier.size(14.dp))
    }
}

/**
 * [inner]'s size for a text, kept while only the text's digits change. Fitting a text lays it out
 * several times. A countdown that ticks would be fitted again every second, for the same size:
 * a value's digits are all one width ([asValue] has "tnum").
 */
private class SteadyAutoSize(private val inner: TextAutoSize) : TextAutoSize {
    /** What the size was found for: the room, the density, the font scale, and the text with each digit as 0. */
    private var fitted: List<Any>? = null
    private var size: TextUnit = TextUnit.Unspecified

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val shape = String(CharArray(text.length) { if (text[it].isDigit()) '0' else text[it] })
        val key = listOf(constraints, density, fontScale, shape)
        if (key == fitted && size.isSpecified) return size
        return with(inner) { getFontSize(constraints, text) }.also {
            fitted = key
            size = it
        }
    }

    // One for each BigText: it keeps that text's size.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
