/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * `progress`: how far along something is.
 * - A ring with its text in the middle, or a bar. With no `style`, a wide box gets the bar and the
 *   others get the ring.
 * - With `items`, it is several bars, each with its label and its text while there is room. A large
 *   card has its name over them ([CardName]).
 * - With `moving`, the ring and the bars are waves while the page is in view.
 * - Stages, a gauge and rings draw in every look themselves: [StagesBody], [GaugeBody], [RingsBody].
 */
@Composable
internal fun ProgressBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box) {
    val color = roleColor(spec.base)
    val look = LocalWidgetLook.current
    when {
        ProgressStyles.isStages(spec) -> StagesBody(spec, box, look, color)
        ProgressStyles.isGauge(spec) -> GaugeBody(spec, box, look, color)
        ProgressStyles.isRings(spec) -> RingsBody(spec, box, look, color)
        look.untitled() -> ProgressLook(spec, box, look, color)
        spec.items.isNotEmpty() && box == WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            // Each bar says what it is: a large card says what they are together over them, as a list does ("Disk").
            CardName(Modifier.padding(bottom = 8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { BarsBody(spec, color) }
        }

        spec.items.isNotEmpty() -> BarsBody(spec, color)
        spec.style == "ring" || (spec.style != "bar" && box != WidgetSizes.Box.WIDE) -> RingBody(
            spec,
            box,
            color
        )

        else -> BarBody(spec, box, color)
    }
}

/**
 * A ring from 0 to 1 in [color], [side] across. It moves to a new value on the theme's spring.
 * Like [LevelBar], it is a wave only with [moving] and in view, and it is no item for a screen reader.
 */
@Composable
internal fun Ring(fraction: Float, moving: Boolean, color: Color, side: Dp) {
    val shown by animateFloatAsState(
        fraction,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "ring"
    )
    // A bigger ring gets a thicker line: an eighth of its side.
    val stroke = with(LocalDensity.current) {
        Stroke(
            width = (side / 8).coerceIn(4.dp, 14.dp).toPx(),
            cap = StrokeCap.Round
        )
    }
    val wave = moving && cardSeen()
    CircularWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = Modifier
            .size(side)
            .clearAndSetSemantics {},
        color = color,
        trackColor = color.copy(alpha = 0.24f),
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
 *   in bold and in the card's color.
 * - Wide: the ring at the start. With no title line ([LocalUntitled]), as the mockup has it: the
 *   text inside the ring, and the title in bold beside it over the label. With a title line: the
 *   text and the label beside the ring.
 */
@Composable
private fun RingBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val tonal = LocalWidgetLook.current == WidgetLook.TONAL
    val muted = lookColors().muted
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val untitled = LocalUntitled.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (box == WidgetSizes.Box.WIDE) {
            // Without a title line the title shares the row, so the ring takes less of the width.
            val side = (if (untitled != null) min(maxHeight, maxWidth * 0.42f) else min(
                maxHeight,
                maxWidth / 2
            )).coerceAtLeast(24.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (untitled != null) {
                    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                        Ring(fraction, spec.moving, color, side)
                        BigText(
                            text,
                            if (side >= 96.dp) type.titleLarge else type.titleSmall,
                            Modifier.padding(horizontal = side / 5)
                        )
                    }
                    Column(
                        Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        Text(
                            cardText(untitled),
                            style = type.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        spec.label?.let { RingLabel(it, spec.labelIcon, type.bodyMedium, muted) }
                    }
                } else {
                    Ring(fraction, spec.moving, color, side)
                    Column(
                        Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        BigText(text, type.titleLarge)
                        spec.label?.let { RingLabel(it, spec.labelIcon, type.bodySmall, muted) }
                    }
                }
            }
        } else {
            // With no label, the card's name says what the ring is.
            val label = (spec.label ?: untitled).takeIf { box == WidgetSizes.Box.LARGE }
            val labelRoom = if (label != null) 24.dp else 0.dp
            val side = min(maxWidth, maxHeight - labelRoom).coerceAtLeast(24.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    Ring(fraction, spec.moving, color, side)
                    // The text stays clear of the ring's line, which is an eighth of the side.
                    BigText(
                        text,
                        if (side >= 96.dp) type.titleLarge else type.titleSmall,
                        Modifier.padding(horizontal = side / 5)
                    )
                }
                // The label in bold and in the card's color, as the mockup has it: it says what the number means.
                // On Tonal's card of that color it is in the text color.
                if (label != null) {
                    RingLabel(
                        label,
                        spec.labelIcon,
                        type.titleSmallEmphasized,
                        if (tonal) MaterialTheme.colorScheme.onSurface else color,
                        Modifier
                            .height(labelRoom)
                            .wrapContentHeight()
                    )
                }
            }
        }
    }
}

/** A ring's label in [style] and [color], after its [icon] (a drawable) when it has one: the Battery card's network. */
@Composable
internal fun RingLabel(
    label: String,
    icon: Int?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            // As tall as the text's font size, in the text's color.
            val side = with(LocalDensity.current) { style.fontSize.toDp() }
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(side)
            )
        }
        Text(label, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One bar under its text. The label is beside the text. A large box has the text at its top and
 * the bar at its bottom; the others have both in the middle.
 */
@Composable
private fun BarBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val large = box == WidgetSizes.Box.LARGE
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = if (large) Arrangement.SpaceBetween else Arrangement.Center
    ) {
        Row {
            BigText(
                spec.text ?: WidgetWords.percent(spec.value, spec.max),
                valueStyle(box),
                Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            )
            if (box != WidgetSizes.Box.SMALL) (spec.label ?: LocalUntitled.current)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = lookColors().muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .widthIn(max = 96.dp)
                        .alignByBaseline(),
                )
            }
        }
        LevelBar(
            WidgetWords.fraction(spec.value, spec.max),
            spec.moving,
            color,
            Modifier.padding(top = 4.dp)
        )
    }
}

/** A bar with its label and its text over it needs this much height, in dp. */
internal const val NAMED_BAR_DP = 30

/** A bar alone needs this much. */
private const val BAR_DP = 14

/**
 * Several bars. Each has its label and its text over it when the box is tall enough for all of
 * them. A lower box draws the bars alone, as many as fit. A bar's own color wins over the card's.
 */
@Composable
private fun BarsBody(spec: WidgetSpec.Progress, color: Color) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val muted = lookColors().muted
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val named = maxHeight >= NAMED_BAR_DP.dp * spec.items.size
        val count = if (named) spec.items.size else (maxHeight / BAR_DP.dp).toInt()
            .coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    Column {
                        if (named) Row {
                            Text(
                                bar.label.orEmpty(),
                                style = type.bodySmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                bar.text ?: WidgetWords.percent(bar.value, bar.max),
                                style = type.labelMedium,
                                color = scheme.onSurface,
                                maxLines = 1
                            )
                        }
                        LevelBar(
                            WidgetWords.fraction(bar.value, bar.max),
                            spec.moving,
                            bar.color?.let { colorOf(it) } ?: color)
                    }
                }
            }
        }
    }
}
