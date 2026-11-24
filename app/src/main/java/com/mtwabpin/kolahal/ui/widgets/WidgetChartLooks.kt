/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

// ---- chart -----------------------------------------------------------------------------------

/** `chart` in Bold, Split or Shape ([ChartBody] draws Plain and Tonal). */
@Composable
internal fun ChartLook(
    spec: WidgetSpec.Chart,
    box: WidgetSizes.Box,
    look: WidgetLook,
    color: Color
) {
    when {
        look == WidgetLook.BOLD -> BoldChart(spec, box, color)
        look == WidgetLook.SPLIT -> SplitChart(spec, box, color)
        LocalCardShaped.current -> ShapedChart(spec, box, color)
        else -> ShapeBesideChart(spec, box, color)
    }
}

/** From this width, a large card has a chart's value and its plot side by side. Under it, the plot goes under the value. */
internal val CHART_SIDE_FROM = 260.dp

/**
 * Bold's chart: the title small, the value as big as it fits in the role's color with its label,
 * and the points as round pills, the latest in the role's color. A wide large card has the pills
 * beside the value, with the first and the last label under them. A chart of dots has its dots
 * where the pills go ([Dots]): a calendar in a large card, the last seven days in the others.
 */
@Composable
private fun BoldChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.BOLD, color)
    val title = LocalUntitled.current
    val bars = spec.style == "bars"
    val rest = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelStyle = type.labelLarge.copy(fontWeight = FontWeight.Bold)
    val plot: @Composable (List<String>, Dp, Dp, Modifier) -> Unit =
        { labels, thick, gap, modifier ->
            if (spec.style == "dots") Dots(
                spec,
                color,
                weeks = box == WidgetSizes.Box.LARGE,
                modifier
            )
            else PillChart(
                spec.points,
                bars,
                spec.min,
                spec.max,
                color,
                rest,
                labels,
                thick,
                gap,
                modifier
            )
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val valueWidth = min(maxWidth * 0.36f, 130.dp)
        when {
            box == WidgetSizes.Box.LARGE && maxWidth >= CHART_SIDE_FROM -> Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(valueWidth)
                        .fillMaxHeight()
                ) {
                    SmallTitle(title, c.content)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomStart
                    ) { spec.value?.let { BoldText(it, 92.sp, color) } }
                    spec.label?.let {
                        Text(
                            it,
                            style = labelStyle,
                            color = c.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                plot(
                    spec.labels,
                    18.dp,
                    11.dp,
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            box == WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
                SmallTitle(title, c.content)
                spec.value?.let { BoldText(it, 48.sp, color, Modifier.padding(top = 6.dp)) }
                spec.label?.let {
                    Text(
                        it,
                        style = labelStyle,
                        color = c.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                plot(
                    spec.labels,
                    14.dp,
                    8.dp,
                    Modifier
                        .padding(top = 8.dp)
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            box == WidgetSizes.Box.WIDE -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                ) {
                    SmallTitle(title, c.content)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomStart
                    ) { spec.value?.let { BoldText(it, 56.sp, color) } }
                }
                plot(
                    emptyList(),
                    12.dp,
                    6.dp,
                    Modifier
                        .padding(start = 12.dp)
                        .weight(0.55f)
                        .fillMaxHeight()
                )
            }

            else -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                spec.value?.let { BoldText(it, 32.sp, color) }
                plot(
                    emptyList(),
                    8.dp,
                    4.dp,
                    Modifier
                        .padding(top = 4.dp)
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Split's chart: a tile with the title, the value and its label beside (or over) a tile with the
 * plot, and the first and the last label under the plot. A chart of dots has its dots in the
 * plot's tile ([Dots]): a calendar in a large card, the last seven days in the others.
 */
@Composable
private fun SplitChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, color)
    val title = LocalUntitled.current
    val bars = spec.style == "bars"
    val dots = spec.style == "dots"
    val gap = SplitCorners.GAP
    val small = box == WidgetSizes.Box.SMALL
    val valueTile: @Composable (Modifier, Shape, TextStyle) -> Unit = { modifier, shape, style ->
        Tile(shape, c.tile, modifier) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (small) 8.dp else 14.dp,
                        vertical = if (box == WidgetSizes.Box.LARGE) 10.dp else 8.dp
                    ),
                verticalArrangement = if (small) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start
            ) {
                if (!small) {
                    Text(
                        title.orEmpty(),
                        style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = c.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                }
                spec.value?.let { BigText(it, style) }
                if (!small) spec.label?.let {
                    Text(
                        it,
                        style = type.bodySmall,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    val plotTile: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        Tile(shape, c.tile, modifier) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = if (small) 8.dp else 16.dp,
                        bottom = if (small) 8.dp else 10.dp
                    )
            ) {
                if (dots) Dots(
                    spec,
                    color,
                    weeks = box == WidgetSizes.Box.LARGE,
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                else Plot(
                    spec.points,
                    bars,
                    spec.min,
                    spec.max,
                    color,
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    surface = c.tile
                )
                // Dots have the days of the week instead.
                if (!dots && box == WidgetSizes.Box.LARGE && spec.labels.isNotEmpty()) Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        spec.labels.first(),
                        style = type.labelSmall,
                        color = c.muted,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    if (spec.labels.size > 1) Text(
                        spec.labels.last(),
                        style = type.labelSmall,
                        color = c.muted,
                        maxLines = 1
                    )
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val valueWidth = min(maxWidth * 0.36f, 124.dp)
        when {
            box == WidgetSizes.Box.LARGE && maxWidth >= CHART_SIDE_FROM -> Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                valueTile(
                    Modifier
                        .width(valueWidth)
                        .fillMaxHeight(),
                    SplitCorners.inRow(0, 2),
                    type.displaySmall.copy(fontSize = 52.sp)
                )
                plotTile(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(), SplitCorners.inRow(1, 2)
                )
            }

            box == WidgetSizes.Box.LARGE -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                valueTile(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    SplitCorners.inColumn(0, 2),
                    type.headlineMedium
                )
                plotTile(
                    Modifier
                        .fillMaxWidth()
                        .weight(1.1f), SplitCorners.inColumn(1, 2)
                )
            }

            box == WidgetSizes.Box.WIDE -> Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                valueTile(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    SplitCorners.inRow(0, 2),
                    type.headlineSmall
                )
                plotTile(
                    Modifier
                        .weight(1.2f)
                        .fillMaxHeight(), SplitCorners.inRow(1, 2)
                )
            }

            else -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                valueTile(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    SplitCorners.inColumn(0, 2),
                    type.titleLarge
                )
                plotTile(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f), SplitCorners.inColumn(1, 2)
                )
            }
        }
    }
}

/**
 * Shape's chart in a card that is a cookie: the value and its label. A chart without a value has
 * its plot in the cookie, or its dots ([Dots]). A habit's week is a flower instead ([HabitFlower]).
 */
@Composable
private fun ShapedChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, color: Color) {
    if (ShapeSteps.weekFlower(spec.style, spec.points)) return HabitFlower(spec, box, color)
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    val small = box == WidgetSizes.Box.SMALL
    val value = spec.value
    ShapeLines {
        if (value != null) {
            BigText(
                value,
                if (small) type.titleLarge else type.displayMedium.copy(fontSize = 46.sp)
            )
            if (!small) spec.label?.let {
                Text(
                    it,
                    style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        } else if (spec.style == "dots") {
            Dots(
                spec, color, weeks = !small, Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            Plot(
                spec.points,
                spec.style == "bars",
                spec.min,
                spec.max,
                color,
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                surface = c.shapeFill
            )
        }
    }
}

/**
 * Shape's chart in a wide card: the value in a cookie or the shape chosen for the card ([cardShape]), with its label in a large one, and beside
 * it the points: round pills for bars, the latest in the role's color, or the line, or dots
 * ([Dots]): a calendar in a large card, the last seven days in a 1-row one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    val large = box == WidgetSizes.Box.LARGE
    BesideShape(cardShape(MaterialShapes.Cookie12Sided), c.shapeFill, inShape = { side ->
        Column(
            Modifier.padding(horizontal = side / 7),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            spec.value?.let { BigText(it, shapeValueStyle(side)) }
            if (large) spec.label?.let {
                Text(
                    it,
                    style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }) {
        if (!large) (spec.label ?: LocalUntitled.current)?.let {
            Text(
                it,
                style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (spec.style == "bars") {
            PillChart(
                spec.points,
                true,
                spec.min,
                spec.max,
                color,
                MaterialTheme.colorScheme.surfaceContainerHighest,
                if (large) spec.labels else emptyList(),
                14.dp,
                8.dp,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else if (spec.style == "dots") {
            Dots(
                spec, color, weeks = large, Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            Plot(
                spec.points,
                false,
                spec.min,
                spec.max,
                color,
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                surface = c.card
            )
            if (large && spec.labels.isNotEmpty()) Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(spec.labels.first(), style = type.labelSmall, color = c.muted, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (spec.labels.size > 1) Text(
                    spec.labels.last(),
                    style = type.labelSmall,
                    color = c.muted,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Bold's chart, and Shape's beside its value: a round-ended pill for each point, at most [thick]
 * wide with [gap] between, grouped at the end of the room. The latest pill is in [accent], the
 * others in [rest]. The first and the last of [labels] are under the group's ends, the last in
 * bold. A line's points get a scale with room under the lowest, so each pill shows its height.
 */
@Composable
private fun PillChart(
    points: List<Float>,
    bars: Boolean,
    min: Float?,
    max: Float?,
    accent: Color,
    rest: Color,
    labels: List<String>,
    thick: Dp,
    gap: Dp,
    modifier: Modifier = Modifier
) {
    val type = MaterialTheme.typography
    val scale = remember(points, bars, min, max) {
        val (low, high) = WidgetWords.scale(points, min, max, bars)
        if (bars) low to high else (low - (high - low) / 3) to high
    }
    BoxWithConstraints(modifier) {
        val n = points.size.coerceAtLeast(1)
        val full = thick * n + gap * (n - 1)
        val factor = if (full > maxWidth) maxWidth / full else 1f
        val pill = thick * factor
        val space = gap * factor
        val group = full * factor
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.End) {
            Canvas(
                Modifier
                    .width(group)
                    .weight(1f)
            ) {
                if (points.isEmpty() || size.height <= 0f) return@Canvas
                val (low, high) = scale
                val p = pill.toPx()
                val s = space.toPx()
                val round = CornerRadius(p / 2)
                points.forEachIndexed { i, v ->
                    // A value at the bottom of the scale is still a round dot.
                    val h = (size.height * WidgetWords.fraction(v - low, high - low)).coerceIn(
                        p,
                        size.height
                    )
                    drawRoundRect(
                        if (i == points.lastIndex) accent else rest,
                        Offset(i * (p + s), size.height - h),
                        Size(p, h),
                        round
                    )
                }
            }
            if (labels.isNotEmpty()) Row(
                Modifier
                    .width(group)
                    .padding(top = 4.dp)
            ) {
                Text(
                    labels.first(),
                    style = type.labelSmall,
                    color = lookColors().muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                if (labels.size > 1) Text(
                    labels.last(),
                    style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
