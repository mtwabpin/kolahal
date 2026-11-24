/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The least room a sparkline is drawn in: under it, the card leaves the line out. */
internal val SPARK_LEAST = 16.dp

/**
 * The card's room around its content at the start and the end, and under it in a 1-row card: what a
 * sparkline reaches past its own room to touch the card's edges. Plain, Tonal and Bold have this room
 * in a wide or a large box (CardLayout and UntitledLayout in ScriptWidgets.kt).
 */
internal val SPARK_SIDE = 16.dp
internal val SPARK_DOWN_WIDE = 12.dp
internal val SPARK_DOWN_LARGE = 14.dp

/** How strong a sparkline is: faint, so the number and its label read over it. */
private const val SPARK_ALPHA = 0.6f

/**
 * A stat's recent numbers ([com.mtwabpin.kolahal.widgets.WidgetSpec.Stat.points]) as a soft line
 * with a wash under it that fades out: the chart's own drawing ([Plot]) in [color], faint.
 * - It fills the room [modifier] gives it, under the value, so it never crosses the number. It
 *   reaches [side] past that room at the start and the end, and [down] past its bottom: the card's
 *   or the tile's room around its content, so the line runs from edge to edge, and the card's
 *   corners clip it.
 * - [surface] is the color around the line's last dot.
 * - Nothing with fewer than 2 points, or in a room less than [SPARK_LEAST] tall (a large font, or
 *   a card with much else in it). It is no item for a screen reader: the card's description says
 *   the numbers ([WidgetWords.description]).
 */
@Composable
internal fun Sparkline(
    points: List<Float>,
    color: Color,
    surface: Color,
    modifier: Modifier,
    side: Dp = 0.dp,
    down: Dp = 0.dp
) {
    if (points.size < 2) return
    Plot(
        points,
        bars = false,
        min = null,
        max = null,
        color = color,
        modifier = modifier
            .reaching(side, down)
            .graphicsLayer { alpha = SPARK_ALPHA },
        surface = surface,
        soft = true
    )
}

/** What this is on is measured [side] wider at each end and [down] taller, and placed from [side] before its room's start. */
private fun Modifier.reaching(side: Dp, down: Dp): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
    val s = side.roundToPx()
    val d = down.roundToPx()
    val roomy = width > 0 && height >= SPARK_LEAST.roundToPx()
    val placeable = measurable.measure(
        if (roomy) Constraints.fixed(width + 2 * s, height + d) else Constraints.fixed(0, 0)
    )
    layout(width, height) { placeable.placeRelative(-s, 0) }
}
