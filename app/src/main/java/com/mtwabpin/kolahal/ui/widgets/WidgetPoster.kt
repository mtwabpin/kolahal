/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The Poster look ([WidgetLook.POSTER]) for stat, countdown and clock: the value so large that the
 * card's edge crops it, with the label small in a corner. The type is the picture.
 */

/** The size the value is measured at, before it is scaled to its room. */
private val REF_SIZE = 100.sp

/** How tall a digit is, as a share of the type's size, when the type can't be measured: Roboto's cap height ([rememberDigitInk]). */
private const val INK = 0.72f

/** The space between a clock's hours and its minutes, as a share of the type's size. */
private const val LINE_GAP = 0.1f

/**
 * How large a poster's value is, so the card's edges crop it, but never so much that a digit stops
 * reading as itself or as another digit. Pure: all sizes are in pixels.
 * - Only the end edge and the bottom edge crop. The value starts inside the card, under or beside
 *   the label, so its start and its top are whole.
 * - At the end, at most [CROP_END] of the last character's width goes past the edge.
 * - At the bottom, at most [CROP_BOTTOM] of the last line's digits go past the edge: a tenth, so the
 *   foot of a 2 or the bowl of a 3 stays.
 * - The value is as large as both allow: one of the two edges crops it by its whole share, unless
 *   the value has no room at all. A value whose width sets its size stands lower ([drop]), so the
 *   bottom edge crops it by its share too: it never floats with empty room under it.
 */
object PosterFit {
    const val CROP_END = 0.2f
    const val CROP_BOTTOM = 0.1f

    /** [text] with each digit as 0: what a value is measured as, so it keeps its size while its digits change. */
    fun template(text: String): String = String(CharArray(text.length) { if (text[it].isDigit()) '0' else text[it] })

    /**
     * The scale for a value whose lines are [widths] wide with a last character [lasts] wide, whose
     * digits are [ink] tall, with [step] from one line's top to the next's, all at one size; in a
     * room [roomW] wide (from the value's start to the card's end) and [roomH] tall (from the
     * value's top to the card's bottom). 0 when there is no room or no value.
     */
    fun scale(roomW: Float, roomH: Float, widths: List<Float>, lasts: List<Float>, ink: Float, step: Float): Float {
        if (widths.isEmpty() || widths.size != lasts.size || roomW <= 0f || roomH <= 0f || ink <= 0f) return 0f
        val byWidth = widths.indices.minOf { i ->
            val reach = widths[i] - CROP_END * lasts[i]
            if (reach > 0f) roomW / reach else Float.MAX_VALUE
        }
        val byHeight = roomH / ((widths.size - 1) * step + ink * (1f - CROP_BOTTOM))
        return minOf(byWidth, byHeight)
    }

    /** How far under its room's top a value at [scale] goes, so the bottom edge crops its last line by [CROP_BOTTOM]. 0 when its height set its size. */
    fun drop(scale: Float, roomH: Float, lines: Int, ink: Float, step: Float): Float =
        (roomH - ((lines - 1) * step + ink * (1f - CROP_BOTTOM)) * scale).coerceAtLeast(0f)

    /** What the edges crop at [scale]: the share of a last character past the end, and of the last line's digits past the bottom. */
    data class Crop(val end: Float, val bottom: Float)

    /** The [Crop] of a value laid out as [scale] has it, with the same measures. */
    fun crop(scale: Float, roomW: Float, roomH: Float, widths: List<Float>, lasts: List<Float>, ink: Float, step: Float): Crop {
        val end = widths.indices.maxOf { i ->
            val over = widths[i] * scale - roomW
            if (lasts[i] > 0f) (over / (lasts[i] * scale)).coerceAtLeast(0f) else 0f
        }
        val over = ((widths.size - 1) * step + ink) * scale - roomH
        return Crop(end, (over / (ink * scale)).coerceAtLeast(0f))
    }
}

/**
 * How tall [style]'s digits stand over their baseline, as a share of its size: measured on the type
 * itself, so the crop is true for any font. [INK] when it can't be measured.
 */
@Composable
private fun rememberDigitInk(style: TextStyle): Float {
    val resolver = LocalFontFamilyResolver.current
    return remember(style, resolver) {
        runCatching {
            val face = resolver.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as? Typeface
                ?: return@runCatching INK
            val paint = Paint().apply {
                typeface = face
                textSize = 1000f
            }
            val bounds = Rect()
            paint.getTextBounds("0123456789", 0, 10, bounds)
            (-bounds.top / 1000f).takeIf { it in 0.5f..1f } ?: INK
        }.getOrDefault(INK)
    }
}

/** The poster's type: the heaviest weight, tight, digits of one width, and a line no taller than the type. */
@Composable
private fun posterStyle(): TextStyle {
    val base = MaterialTheme.typography.displayLarge
    return remember(base) {
        base.asBold().copy(
            fontSize = REF_SIZE,
            lineHeight = 1.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
    }
}

/**
 * A poster: [lines] (one, or a clock's hours over its minutes) in [color], as large as [PosterFit]
 * allows, with [title] small in the top start corner, and [note] and [change] small at the end of
 * the title's line.
 * - Small and large: the title's line at the top, the value under it from the start edge.
 * - Wide: that, or the title over the note in a column at the start and the value beside it,
 *   whichever lets the value be larger.
 * - With [rolls], the first line's digits roll to a new value ([RollingText]).
 * - The value is measured with its digits as 0 ([PosterFit.template]), so a value that ticks keeps
 *   its size. The card's description has the whole value for a screen reader; the card clips the rest.
 */
@Composable
internal fun PosterCard(
    lines: List<String>,
    box: WidgetSizes.Box,
    color: Color,
    title: String?,
    note: String?,
    rolls: Boolean = false,
    change: (@Composable () -> Unit)? = null,
) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.POSTER)
    // The value in its color's hue, moved just far enough to read on the card ([valueInk]).
    val valueColor = valueInk(color, c.card)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = posterStyle()
    val digitInk = rememberDigitInk(style)
    val titleStyle = type.labelMediumEmphasized
    val noteStyle = type.labelMedium
    val templates = lines.map(PosterFit::template)
    val measured = remember(templates, style, density, measurer) {
        templates.map { measurer.measure(it, style, maxLines = 1, softWrap = false) }
    }
    // A line of the small text, at the phone's font size.
    val headPx = remember(titleStyle, density, measurer) { measurer.measure("Ag", titleStyle).size.height.toFloat() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val px = density.density
        val side = (if (box == WidgetSizes.Box.SMALL) 12 else 16) * px
        val top = when (box) {
            WidgetSizes.Box.SMALL -> 10
            WidgetSizes.Box.WIDE -> 12
            WidgetSizes.Box.LARGE -> 14
        } * px
        val refPx = with(density) { REF_SIZE.toPx() }
        val widths = measured.map { it.size.width.toFloat() }
        val lasts = measured.map { r ->
            val n = r.layoutInput.text.length
            if (n == 0) 0f else r.getBoundingBox(n - 1).width
        }
        val ink = digitInk * refPx
        val step = (digitInk + LINE_GAP) * refPx
        val headed = title != null || note != null || change != null
        // Under the title's line, from the start edge.
        val underTop = if (headed) top + headPx + 4 * px else top
        val under = PosterFit.scale(w - side, h - underTop, widths, lasts, ink, step)
        // Beside a column of two fifths of the width (wide only).
        val column = w * 0.4f
        val beside = if (box == WidgetSizes.Box.WIDE && headed) {
            PosterFit.scale(w - column, h - top, widths, lasts, ink, step)
        } else 0f
        val besideIt = beside > under
        val scale = if (besideIt) beside else under
        val valueX = if (besideIt) column else side
        val roomTop = if (besideIt) top else underTop
        val valueTop = roomTop + PosterFit.drop(scale, h - roomTop, lines.size, ink, step)
        if (scale > 0f) {
            val big = style.copy(fontSize = REF_SIZE * scale)
            // From a line's top to its digits' top.
            val lead = (measured.first().firstBaseline - ink) * scale
            lines.forEachIndexed { i, line ->
                val at = Modifier.placedAt(valueX.roundToInt(), (valueTop + i * step * scale - lead).roundToInt())
                if (rolls && i == 0) RollingText(line) { roll, laid -> PosterLine(line, big, valueColor, at.then(roll), laid) }
                else PosterLine(line, big, valueColor, at)
            }
        }
        val titleText: @Composable (Int) -> Unit = { maxLines ->
            title?.let {
                Text(
                    cardText(it),
                    style = titleStyle,
                    color = c.content,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // In the narrow column beside the value, the note's parts go one under the other ("days" over "20 Dec").
        val noteText: @Composable (Boolean) -> Unit = { stacked ->
            note?.let {
                Text(
                    if (stacked) it.replace(" · ", "\n") else it,
                    style = noteStyle,
                    color = c.muted,
                    maxLines = if (stacked) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            change?.invoke()
        }
        if (besideIt) {
            val room = with(density) { (column - side - 8 * px).coerceAtLeast(0f).toDp() }
            Column(
                Modifier
                    .placedAt(side.roundToInt(), top.roundToInt(), fixedHeight = (h - 2 * top).roundToInt())
                    .width(room),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                titleText(2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    noteText(true)
                }
            }
        } else if (headed) {
            val room = with(density) { (w - 2 * side).coerceAtLeast(0f).toDp() }
            Row(
                Modifier
                    .placedAt(side.roundToInt(), top.roundToInt())
                    .width(room),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.weight(1f)) { titleText(1) }
                noteText(false)
            }
        }
    }
}

/** One line of a poster's value: never cut, never wrapped; the card's edge crops it. */
@Composable
private fun PosterLine(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier,
        onTextLayout = onTextLayout
    )
}

/**
 * What this is on, measured as large as it wants (or [fixedHeight] tall), and placed at [x], [y]
 * from its room's top start, also past its room's end or bottom: the card clips it.
 */
private fun Modifier.placedAt(x: Int, y: Int, fixedHeight: Int? = null): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        if (fixedHeight != null) Constraints(minHeight = fixedHeight.coerceAtLeast(0), maxHeight = fixedHeight.coerceAtLeast(0))
        else Constraints()
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(x, y) }
}

// ---- the kinds ------------------------------------------------------------------------------

/** Poster's stat: the value; the label (or the card's name) small at the top, with the unit and the change at its end. */
@Composable
internal fun PosterStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val role = roleColor(spec.base)
    val hasChange = spec.trend != null || spec.delta != null
    PosterCard(
        listOf(spec.value),
        box,
        role,
        title = spec.label ?: LocalUntitled.current,
        note = spec.unit,
        rolls = true,
        change = if (hasChange) {
            { ChangeText(spec.trend, spec.delta, role, MaterialTheme.typography.labelMedium) }
        } else null,
    )
}

/**
 * Poster's countdown: the number left; the label (or the card's name) at the top, with the unit
 * and the moment as its note: "days · 20 Dec". The note shares the title's line, so it says less
 * where that line is short, rather than be cut:
 * - Wide: both, one under the other beside the value ([PosterCard]).
 * - With a label of the script's own, small and large: the unit alone ("days"), so the label has
 *   the line's room. The label often says the day ("to 31 December").
 * - With no label (the card's name at the top): small, the moment alone ("20 Dec"); large, both.
 * - On the day, with no unit: the moment (its time).
 */
@Composable
internal fun PosterCountdown(spec: WidgetSpec.Countdown, box: WidgetSizes.Box, count: State<WidgetWords.Count>, moment: String?) {
    val left = count.value
    val note = when {
        box == WidgetSizes.Box.WIDE -> listOfNotNull(left.unit, moment)
        spec.label != null -> listOfNotNull(left.unit ?: moment)
        box == WidgetSizes.Box.SMALL -> listOfNotNull(moment ?: left.unit)
        else -> listOfNotNull(left.unit, moment)
    }.joinToString(" · ").ifEmpty { null }
    PosterCard(
        listOf(StyledText.capitalized(left.value)),
        box,
        roleColor(spec.base),
        title = spec.label ?: LocalUntitled.current,
        note = note,
    )
}

/**
 * Poster's clock: the first place's time; its name at the top, with the day there ("Wed"). A large
 * card has the hours over the minutes ([posterClockLines]).
 */
@Composable
internal fun PosterClock(
    times: List<WidgetWords.ZoneTime>,
    time: (Int) -> () -> String,
    zone: ZoneId,
    now: State<Long>,
    box: WidgetSizes.Box,
    accent: Color
) {
    val shown = time(0)()
    val format = remember { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    val day = Instant.ofEpochMilli(now.value).atZone(zone).format(format)
    PosterCard(
        if (box == WidgetSizes.Box.LARGE) posterClockLines(shown) else listOf(shown),
        box,
        accent,
        title = times.first().label,
        note = day,
    )
}

/** A time on two lines, the hours over the rest: "21:08" is "21" over "08", "9:08 PM" is "9" over "08 PM". One line for any other text. */
internal fun posterClockLines(time: String): List<String> {
    val m = Regex("""^(\d{1,2})[:.](.+)$""").find(time) ?: return listOf(time)
    return listOf(m.groupValues[1], m.groupValues[2])
}
