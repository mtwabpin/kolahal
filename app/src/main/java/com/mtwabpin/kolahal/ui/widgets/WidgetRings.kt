/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/*
 * `progress` with `style = "rings"`: the bars of `items` as rings inside each other, the first
 * outside, each in its own color with a dim track. A ring that is `moving` (or all of them, with
 * the card's `moving`) is a wave while the page is in view.
 */

/** A ring's color: its own role's, or the card's [color]. */
private fun tintOf(bar: WidgetSpec.Bar, color: Color): Color = bar.color?.let { colorOf(it) } ?: color

/**
 * Rings in [look], for [box].
 * - Plain, Tonal, Bold and Shape: a 2×2 has the rings with a line of their values under them (the
 *   first 3). A 4×2 has the rings at the start, and a row for each: its name, its value large and
 *   its scale ("of 8,000"). A 1-row card has the rings at the start and a short row for each
 *   beside them, as many as fit. A 1×1 is the rings alone.
 * - Bold writes the values in its narrow type, larger. Shape sets the rings in a cookie, and marks
 *   each value with a small shape where the others have a dot.
 * - Split: a tile for the rings, and a tile for their values; a 4×2 has a tile for each ring's row.
 */
@Composable
internal fun RingsBody(
    spec: WidgetSpec.Progress,
    box: WidgetSizes.Box,
    look: WidgetLook,
    color: Color
) {
    if (look == WidgetLook.SPLIT) SplitRings(spec, box, color)
    else PlainRings(spec, box, color, bold = look == WidgetLook.BOLD, shaped = look == WidgetLook.SHAPE)
}

/** How the rows and values of rings are written: Bold's type, and Shape's marks. */
private data class RingType(val bold: Boolean, val shaped: Boolean)

@Composable
private fun PlainRings(
    spec: WidgetSpec.Progress,
    box: WidgetSizes.Box,
    color: Color,
    bold: Boolean,
    shaped: Boolean
) {
    val c = lookColors()
    val how = RingType(bold, shaped)
    val rings: @Composable (Dp) -> Unit = { side -> RingsIn(spec, color, side, shaped) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        when {
            box == WidgetSizes.Box.LARGE && maxWidth > maxHeight * 1.5f -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rings(ProgressStyles.ringsSide(boxWidth.value, boxHeight.value).dp)
                RingRows(
                    spec, color, c, how,
                    Modifier
                        .padding(start = 20.dp)
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            box == WidgetSizes.Box.LARGE -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { rings(min(maxWidth, maxHeight)) }
                RingLegend(
                    spec, color, c, how,
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                )
            }

            box == WidgetSizes.Box.WIDE -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rings(min(boxHeight, boxWidth * 0.45f))
                RingList(
                    spec, color, c, how,
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                rings(min(boxWidth, boxHeight))
            }
        }
    }
}

/**
 * Split's rings: a tile for the rings, and their values in a tile beside them (1 row) or under them
 * (2×2). A 4×2 has a tile for each ring's row, as many as fit, beside the rings' tile. A 1×1 has the
 * rings' tile alone.
 */
@Composable
private fun SplitRings(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val c = lookColors(WidgetLook.SPLIT, color)
    val how = RingType(bold = false, shaped = false)
    val gap = SplitCorners.GAP
    val rings: @Composable (Modifier, androidx.compose.ui.graphics.Shape) -> Unit = { modifier, shape ->
        Tile(shape, c.tile, modifier) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) { NestedRings(spec, color, min(maxWidth, maxHeight)) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        when {
            box == WidgetSizes.Box.SMALL -> rings(Modifier.fillMaxSize(), SplitCorners.inRow(0, 1))
            box == WidgetSizes.Box.WIDE -> Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                rings(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f), SplitCorners.inRow(0, 2)
                )
                Tile(
                    SplitCorners.inRow(1, 2),
                    c.tile,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    RingList(
                        spec, color, c, how,
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            maxWidth > maxHeight * 1.5f -> Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                // The rings' tile leaves the rows' tiles room for "6,240 of 8,000".
                rings(
                    Modifier
                        .fillMaxHeight()
                        .width(ProgressStyles.ringsSide(boxWidth.value, boxHeight.value).dp),
                    SplitCorners.tile(true, false, false, true)
                )
                // A tile for each ring, as many as fit: each needs a row of the large type, with
                // room for its scale to go under its value.
                val tall = with(LocalDensity.current) { 40.sp.toDp() }
                val count = ((boxHeight + gap) / (tall + gap)).toInt().coerceIn(1, spec.items.size)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    spec.items.take(count).forEachIndexed { i, bar ->
                        key(i) {
                            Tile(
                                SplitCorners.tile(false, i == 0, i == count - 1, false),
                                c.tile,
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                RingRow(
                                    i, bar, color, c, how, NAME_SHARE,
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }

            else -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                rings(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(), SplitCorners.inColumn(0, 2)
                )
                Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth()) {
                    RingLegend(
                        spec, color, c, how,
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/** The rings [side] across; in Shape ([shaped]) set in a cookie, or the shape chosen for the card ([cardShape]), that is [side] across. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RingsIn(spec: WidgetSpec.Progress, color: Color, side: Dp, shaped: Boolean) {
    if (shaped) {
        ShapedBox(cardShape(MaterialShapes.Cookie12Sided), MaterialTheme.colorScheme.surfaceContainerHighest, side) {
            NestedRings(spec, color, side * 0.74f)
        }
    } else NestedRings(spec, color, side)
}

/** The rings of [spec]'s items inside each other, [side] across, the first outside ([ProgressStyles.ringSides]). */
@Composable
private fun NestedRings(spec: WidgetSpec.Progress, color: Color, side: Dp) {
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        ProgressStyles.ringSides(side.value, spec.items.size).forEachIndexed { i, (ring, line) ->
            key(i) {
                val bar = spec.items[i]
                NestRing(
                    WidgetWords.fraction(bar.value, bar.max),
                    spec.moving || bar.moving,
                    tintOf(bar, color),
                    ring.dp,
                    line.dp
                )
            }
        }
    }
}

/**
 * One of the nested rings: as the card's [Ring], [side] across with a line [line] wide. It moves
 * to a new value on the theme's spring, is a wave only with [moving] and in view, and is no item
 * for a screen reader.
 */
@Composable
private fun NestRing(fraction: Float, moving: Boolean, color: Color, side: Dp, line: Dp) {
    val shown by animateFloatAsState(
        fraction,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "ring"
    )
    val stroke = with(LocalDensity.current) { Stroke(width = line.toPx(), cap = StrokeCap.Round) }
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

/** What marks ring [index]'s value: a dot in [tint], or in Shape ([RingType.shaped]) a small shape. */
@Composable
private fun RingMark(index: Int, tint: Color, size: Dp, how: RingType) {
    if (how.shaped) ShapedBox(rankPolygon(index), tint, size) {}
    else Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint)
    )
}

/**
 * Under the rings of a 2×2: a mark and the value of each of the first 3 rings ([ProgressStyles]
 * says them all to a screen reader). A value is the ring's text, or its percent.
 */
@Composable
private fun RingLegend(
    spec: WidgetSpec.Progress,
    color: Color,
    c: LookColors,
    how: RingType,
    modifier: Modifier = Modifier
) {
    val shown = spec.items.take(LEGEND)
    Row(
        modifier,
        horizontalArrangement = if (shown.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEachIndexed { i, bar ->
            key(i) {
                Row(
                    Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tint = tintOf(bar, color)
                    RingMark(i, tint, 8.dp, how)
                    val value = bar.text ?: WidgetWords.percent(bar.value, bar.max)
                    if (how.bold) BoldText(value, 18.sp, tint, Modifier.padding(start = 5.dp))
                    else Text(
                        value,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }
        }
    }
}

/** A 2×2 has room under its rings for this many values. */
private const val LEGEND = 3

/**
 * Beside the rings of a 1-row card: a short row for each ring, as many as fit: a mark, its name and
 * its value (its text, or its percent).
 */
@Composable
private fun RingList(
    spec: WidgetSpec.Progress,
    color: Color,
    c: LookColors,
    how: RingType,
    modifier: Modifier = Modifier
) {
    val type = MaterialTheme.typography
    BoxWithConstraints(modifier) {
        val tall = with(LocalDensity.current) { 18.sp.toDp() }
        val count = (maxHeight / tall).toInt().coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val tint = tintOf(bar, color)
                        RingMark(i, tint, 7.dp, how)
                        Text(
                            bar.label.orEmpty(),
                            style = type.bodySmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .weight(1f)
                        )
                        val value = bar.text ?: WidgetWords.percent(bar.value, bar.max)
                        if (how.bold) BoldText(value, 16.sp, tint, Modifier.padding(start = 6.dp))
                        else Text(
                            value,
                            style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = c.content,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Beside the rings of a 4×2: a row for each ring, as many as fit ([FitColumn]): a mark, its name,
 * its value large in its color, and its scale ("of 8,000"), under the value when the line is full.
 * The names take a quarter of the width, so the values line up.
 */
@Composable
private fun RingRows(
    spec: WidgetSpec.Progress,
    color: Color,
    c: LookColors,
    how: RingType,
    modifier: Modifier = Modifier
) {
    FitColumn(modifier) {
        spec.items.forEachIndexed { i, bar ->
            key(i) { RingRow(i, bar, color, c, how, NAME_SHARE, Modifier.fillMaxWidth()) }
        }
    }
}

/** A ring's name takes this share of its row, so the values of the rows line up. */
private const val NAME_SHARE = 0.26f

/**
 * Rows one under the other, as many as fit in the height, from the first ([ProgressStyles.rowsThatFit]),
 * with even room around them. Each row is as tall as it is: one whose scale went under its value
 * takes two lines. The rows that don't fit are not drawn.
 */
@Composable
private fun FitColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val room = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val rows = placeables.take(ProgressStyles.rowsThatFit(placeables.map { it.height }, room))
        val used = rows.sumOf { it.height }
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else used
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else rows.maxOfOrNull { it.width } ?: 0
        val space = (height - used).coerceAtLeast(0) / (rows.size + 1)
        layout(width, height) {
            var y = space
            rows.forEach {
                it.placeRelative(0, y)
                y += it.height + space
            }
        }
    }
}

/**
 * A ring's [value] with its [scale] after it on one line, on one baseline, when both fit the width
 * ([ProgressStyles.fitsInLine]); else the scale goes under the value, so neither is cut.
 */
@Composable
private fun ValueThenScale(
    value: @Composable () -> Unit,
    scale: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(listOf(value, scale), modifier) { (valueParts, scaleParts), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val v = valueParts.first().measure(loose)
        val s = scaleParts.first().measure(loose)
        val gap = 5.dp.roundToPx()
        if (ProgressStyles.fitsInLine(v.width, gap, s.width, constraints.maxWidth)) {
            val vb = v[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: v.height
            val sb = s[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: s.height
            val line = maxOf(vb, sb)
            val height = maxOf(line - vb + v.height, line - sb + s.height)
            layout(v.width + gap + s.width, height) {
                v.placeRelative(0, line - vb)
                s.placeRelative(v.width + gap, line - sb)
            }
        } else {
            layout(maxOf(v.width, s.width), v.height + s.height) {
                v.placeRelative(0, 0)
                s.placeRelative(0, v.height)
            }
        }
    }
}

/** One ring's row: a mark, its name in [nameShare] of the width, its value large in its color, and its scale after it or under it ([ValueThenScale]). */
@Composable
private fun RingRow(
    index: Int,
    bar: WidgetSpec.Bar,
    color: Color,
    c: LookColors,
    how: RingType,
    nameShare: Float,
    modifier: Modifier = Modifier
) {
    val type = MaterialTheme.typography
    val tint = tintOf(bar, color)
    BoxWithConstraints(modifier) {
        val nameWidth = maxWidth * nameShare
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingMark(index, tint, 9.dp, how)
            Text(
                bar.label.orEmpty(),
                style = type.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(nameWidth)
            )
            val value = ProgressStyles.ringValue(bar)
            ValueThenScale(
                value = {
                    if (how.bold) BoldText(value, 28.sp, tint)
                    else Text(
                        value,
                        style = type.titleLarge.asValue(),
                        color = tint,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                scale = {
                    Text(
                        ProgressStyles.ringOf(bar),
                        style = type.bodySmall,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
