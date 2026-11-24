/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * `progress` with `style = "gauge"`: a thick arc from `min` to `max`, in its zones' colors, filled to
 * the value, with a knob on it. The value is large inside the arc, and the word for it (`label`)
 * is in its zone's color. Without zones the arc is in the card's color.
 */

/** The arc of a gauge in a square box: open at the bottom, as a dial. */
private const val ROUND_SWEEP = 250f

/** The arc of a gauge beside words, in a 1-row card: half a circle. */
private const val HALF_SWEEP = 180f

/** The color a progress card fills with in Bold: a gauge's value's zone, or else the card's own. */
internal fun progressTint(spec: WidgetSpec.Progress): Color =
    if (ProgressStyles.isGauge(spec)) colorOf(ProgressStyles.gaugeRole(spec) ?: StyledText.ColorRole.ACCENT)
    else roleColor(spec.base)

/**
 * A gauge in [look], for [box].
 * - Plain and Tonal: a 2×2 has the title, the dial with the value and its unit inside, and the
 *   word under it. A 1-row card has half a dial with the value, and beside it the title, the word
 *   and the unit. A 1×1 is the dial and the value. A 4×2 has the dial at the start and the words beside it.
 * - Bold: the layouts of Plain, with the value in Bold's narrow type, larger. The card doesn't
 *   fill as Bold's ring does: the arc says the level ([ProgressStyles.level]).
 * - Split: a tile with the dial, and a tile with the title and the word.
 * - Shape: the dial in the clover. A 1×1 or 2×2 card is the clover; a wider card has it at the start.
 * The word wraps to a second line, and gets smaller before it is cut ([GaugeWord]).
 */
@Composable
internal fun GaugeBody(
    spec: WidgetSpec.Progress,
    box: WidgetSizes.Box,
    look: WidgetLook,
    color: Color
) {
    when {
        look == WidgetLook.BOLD -> PlainGauge(spec, box, color, bold = true)
        look == WidgetLook.SPLIT -> SplitGauge(spec, box, color)
        look == WidgetLook.SHAPE && LocalCardShaped.current -> ShapedGauge(spec, box, color)
        look == WidgetLook.SHAPE -> ShapeBesideGauge(spec, color)
        else -> PlainGauge(spec, box, color)
    }
}

/** Plain's and Tonal's gauge, and Bold's with [bold]: its value in Bold's type. */
@Composable
private fun PlainGauge(
    spec: WidgetSpec.Progress,
    box: WidgetSizes.Box,
    color: Color,
    bold: Boolean = false
) {
    val c = lookColors()
    // On Tonal's card the word is in the text color, as a ring's label is: the card is tinted already.
    val word = if (LocalWidgetLook.current == WidgetLook.TONAL) c.content else progressTint(spec)
    val title = LocalUntitled.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        when {
            box == WidgetSizes.Box.WIDE -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GaugeDial(
                    spec, color, c.card, HALF_SWEEP,
                    Modifier
                        .width(minOf(boxWidth * 0.48f, boxHeight * 2f))
                        .fillMaxHeight(),
                    bold = bold
                )
                GaugeWords(
                    title, spec, word, c,
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )
            }

            // A 4×2: the dial at the start, the words beside it.
            box == WidgetSizes.Box.LARGE && maxWidth > maxHeight * 1.5f -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GaugeDial(spec, color, c.card, ROUND_SWEEP, Modifier.size(boxHeight), unit = true, bold = bold)
                GaugeWords(
                    title, spec, word, c,
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
                    large = true
                )
            }

            box == WidgetSizes.Box.LARGE -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SmallTitle(title, c.content, Modifier.fillMaxWidth())
                GaugeDial(
                    spec, color, c.card, ROUND_SWEEP,
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    unit = true,
                    bold = bold
                )
                spec.label?.let { GaugeWord(it, word, MaterialTheme.typography.titleSmall) }
            }

            else -> GaugeDial(spec, color, c.card, ROUND_SWEEP, Modifier.fillMaxSize(), bold = bold)
        }
    }
}

/**
 * The word for a gauge's value (its `label`) in [color], bold, [centered] or at the start. A long
 * one ("Unhealthy for sensitive groups") wraps to a second line, and gets smaller, down to 11 sp,
 * before it is cut with "…": in the height it is given too.
 */
@Composable
private fun GaugeWord(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    centered: Boolean = true
) {
    val largest = style.fontSize.value.let { if (it.isNaN()) 14f else maxOf(it, 11f) }
    val auto = remember(largest) {
        TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = largest.sp, stepSize = 1.sp)
    }
    Text(
        text,
        modifier = modifier,
        style = style.copy(fontWeight = FontWeight.Bold),
        color = color,
        autoSize = auto,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start
    )
}

/** Beside a dial: the title, the word in [word]'s color, and the unit. [large] has bigger type. */
@Composable
private fun GaugeWords(
    title: String?,
    spec: WidgetSpec.Progress,
    word: Color,
    c: LookColors,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val type = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.Center) {
        title?.let {
            Text(
                cardText(it),
                style = (if (large) type.titleMedium else type.titleSmall).copy(fontWeight = FontWeight.Bold),
                color = c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // The word takes the height the title and the unit leave, and fits itself in it.
        spec.label?.let {
            GaugeWord(
                it,
                word,
                if (large) type.headlineSmall else type.titleMedium,
                Modifier.weight(1f, fill = false),
                centered = false
            )
        }
        spec.unit?.let {
            Text(
                it,
                style = if (large) type.bodyMedium else type.bodySmall,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The dial: the arc as large as it fits in its box, with the value inside, and the unit under the
 * value with [unit]. [color] is the arc's where it has no zone; the knob stands out from [behind],
 * the color around it. With [bold] the value is in Bold's narrow type, as large as it fits. The
 * value moves to a new one on the theme's spring. The drawing is no item for a screen reader: the
 * card's description says the value.
 */
@Composable
private fun GaugeDial(
    spec: WidgetSpec.Progress,
    color: Color,
    behind: Color,
    sweep: Float,
    modifier: Modifier = Modifier,
    unit: Boolean = false,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    val shown by animateFloatAsState(
        spec.value,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "gauge"
    )
    val muted = lookColors().muted
    // The colors of the zones, read here: the drawing only looks them up.
    val zoneColors = spec.zones.associate { it.color to colorOf(it.color) }
    BoxWithConstraints(modifier) {
        val w = maxWidth.value
        val h = maxHeight.value
        // The line is a little under a third of the radius, 6 to 14 dp; the knob stands 3 dp out of it.
        val rough = ProgressStyles.gaugeFit(w, h, sweep, 10f)
        val line = (rough.radius * 0.3f).coerceIn(6f, 14f)
        val fit = ProgressStyles.gaugeFit(w, h, sweep, line / 2 + 3f)
        if (fit.radius < line) return@BoxWithConstraints
        Canvas(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}) {
            val r = fit.radius.dp.toPx()
            val center = Offset(fit.cx.dp.toPx(), fit.cy.dp.toPx())
            val width = line.dp.toPx()
            val stroke = Stroke(width = width, cap = StrokeCap.Round)
            // Parts are 3 dp apart, past their round ends.
            val gap = ((3.dp.toPx() + width) / r * 180f / PI).toFloat()
            val start = -90f - sweep / 2
            ProgressStyles.gaugeArcs(spec.min, spec.max, spec.zones, shown, start, sweep, gap)
                .forEach { arc ->
                    val tint = arc.color?.let { zoneColors[it] } ?: color
                    drawArc(
                        color = if (arc.filled) tint else tint.copy(alpha = 0.24f),
                        startAngle = arc.start,
                        sweepAngle = arc.sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(2 * r, 2 * r),
                        style = stroke
                    )
                }
            val angle = (start + sweep * ProgressStyles.gaugeFraction(shown, spec.min, spec.max)) * PI / 180
            val knob = Offset(
                center.x + r * cos(angle).toFloat(),
                center.y + r * sin(angle).toFloat()
            )
            drawCircle(behind, width / 2 + 3.dp.toPx(), knob)
            drawCircle(ink, (width / 2 - 0.5.dp.toPx()).coerceAtLeast(1f), knob)
        }
        val r = fit.radius
        val round = sweep > HALF_SWEEP
        // The value in the middle of the dial; half a dial has it above its center, inside the arc.
        val inner = r - line / 2 - 2f
        Column(
            Modifier
                .offset(
                    x = (fit.cx - inner * 0.86f).dp,
                    y = (if (round) fit.cy - inner * 0.7f else fit.cy - inner * 0.9f).dp
                )
                .size(
                    width = (inner * 1.72f).dp,
                    height = (if (round) inner * 1.4f else inner * 0.95f).dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (round) Arrangement.Center else Arrangement.Bottom
        ) {
            if (bold) BoldText(
                ProgressStyles.gaugeValue(spec),
                (inner * if (round) 0.95f else 1.25f).sp,
                ink
            ) else BigText(
                ProgressStyles.gaugeValue(spec),
                MaterialTheme.typography.displaySmall.copy(
                    fontSize = (inner * if (round) 0.7f else 1f).sp,
                    lineHeight = 1.1.em
                ),
                color = ink
            )
            if (unit && round) spec.unit?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Split's gauge: a tile with the dial and the value, and a tile with the title and the word: side
 * by side in a 1-row card, one over the other in a 2-row one. A 1×1 has the dial's tile alone.
 */
@Composable
private fun SplitGauge(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val c = lookColors(WidgetLook.SPLIT, color)
    val title = LocalUntitled.current
    val word = progressTint(spec)
    val dial: @Composable (Modifier, Shape, Float) -> Unit = { modifier, shape, sweep ->
        Tile(shape, c.tile, modifier) {
            GaugeDial(
                spec, color, c.tile, sweep,
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                unit = box == WidgetSizes.Box.LARGE
            )
        }
    }
    when (box) {
        WidgetSizes.Box.SMALL -> dial(Modifier.fillMaxSize(), SplitCorners.inRow(0, 1), ROUND_SWEEP)
        WidgetSizes.Box.WIDE -> Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(SplitCorners.GAP)
        ) {
            dial(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(), SplitCorners.inRow(0, 2), HALF_SWEEP
            )
            Tile(
                SplitCorners.inRow(1, 2),
                c.tile,
                Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
            ) {
                GaugeWords(
                    title, spec, word, c,
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        WidgetSizes.Box.LARGE -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SplitCorners.GAP)
        ) {
            dial(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(), SplitCorners.inColumn(0, 2), ROUND_SWEEP
            )
            Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    SmallTitle(title, c.content)
                    spec.label?.let { GaugeWord(it, word, MaterialTheme.typography.titleSmall, centered = false) }
                }
            }
        }
    }
}

/**
 * Shape's gauge in a card that is the clover ([LocalCardShaped]), or the shape chosen for it: the
 * dial inside the shape, clear of its leaves or points, with the word under it in a 2×2.
 */
@Composable
private fun ShapedGauge(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val c = lookColors(WidgetLook.SHAPE, color)
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest
    val extra = cardInset()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(maxWidth * (0.18f + extra)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GaugeDial(
                spec, color, fill, ROUND_SWEEP,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            if (box == WidgetSizes.Box.LARGE) spec.label?.let {
                GaugeWord(it, c.content, MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Shape's gauge in a wide card: the dial in a clover (or the shape chosen for the card, [cardShape])
 * at the start, and beside it the title, the word and the unit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideGauge(spec: WidgetSpec.Progress, color: Color) {
    val c = lookColors(WidgetLook.SHAPE, color)
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest
    val title = LocalUntitled.current
    BesideShape(
        cardShape(MaterialShapes.Clover4Leaf),
        fill,
        inShape = { side ->
            GaugeDial(
                spec, color, fill, ROUND_SWEEP,
                Modifier
                    .fillMaxSize()
                    .padding(side * 0.16f)
            )
        },
        beside = { GaugeWords(title, spec, progressTint(spec), c) }
    )
}
