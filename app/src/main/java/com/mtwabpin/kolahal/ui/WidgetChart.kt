/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * `chart`: numbers over time as a line or as bars, with a headline.
 * - Small: the headline over a small plot.
 * - Wide: the headline with a small plot beside it.
 * - Large: the value big, its label under it (as a stat has them), the plot, and under the plot
 *   the labels of the first and the last point.
 * - With `style = "dots"`, a dot for each day ([DotsBody]).
 * The plot has no words of its own. The card's description says the chart for a screen reader.
 */
@Composable
internal fun ChartBody(spec: WidgetSpec.Chart, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val color = roleColor(spec.base)
    val look = LocalWidgetLook.current
    if (look.untitled()) {
        ChartLook(spec, box, look, color)
        return
    }
    if (spec.style == "dots") {
        DotsBody(spec, box, color)
        return
    }
    val colors = lookColors(look, color)
    val muted = colors.muted
    val bars = spec.style == "bars"
    val value = spec.value
    // With no label, the card's name says what the chart is.
    val label = spec.label ?: LocalUntitled.current
    // The ring around the line's last dot is in the card's color.
    val surface = colors.card
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            // The value on its own line with its label under it, as a stat has them.
            if (value != null) BigText(value, valueStyle(box))
            if (label != null) Text(
                label,
                style = type.bodyMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Plot(
                spec.points,
                bars,
                spec.min,
                spec.max,
                color,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                surface
            )
            if (spec.labels.isNotEmpty()) Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                Text(spec.labels.first(), style = type.labelSmall, color = muted, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (spec.labels.size > 1) Text(
                    spec.labels.last(),
                    style = type.labelSmall,
                    color = muted,
                    maxLines = 1
                )
            }
        }

        WidgetSizes.Box.WIDE -> Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (value != null || label != null) Column(
                Modifier
                    .padding(end = 12.dp)
                    .widthIn(max = 120.dp)
            ) {
                if (value != null) BigText(value, type.titleLarge)
                if (label != null) Text(
                    label,
                    style = type.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Plot(
                spec.points,
                bars,
                spec.min,
                spec.max,
                color,
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                surface
            )
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (value != null) BigText(value, type.titleMedium)
            Plot(
                spec.points,
                bars,
                spec.min,
                spec.max,
                color,
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                surface
            )
        }
    }
}

/**
 * The plot of a chart, on a Canvas, in [color]. There is no chart library.
 * - A line is 2 dp, with a wash under it. A dot marks the latest point. The dot has a ring in
 *   the card's color, so it stays clear of the line.
 * - A bar is at most 24 dp thick, with 4 dp between bars (less when there are many). The latest
 *   bar is in full color, the others lighter. A bar is round at its top and square at the
 *   baseline, which is a hairline.
 * - The scale is [min] to [max], or what the data gives ([WidgetWords.scale]). A value outside the
 *   scale is drawn at its edge.
 * - [surface] is the color under the plot, for the dot's ring.
 */
@Composable
internal fun Plot(
    points: List<Float>,
    bars: Boolean,
    min: Float?,
    max: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    surface: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
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
            // The latest bar is in full color, the others lighter: the latest value is the news.
            val before = Path()
            val latest = Path()
            points.forEachIndexed { i, v ->
                val x = i * slot + (slot - thick) / 2
                // A value at the bottom of the scale still shows as a sliver.
                val top = (size.height * (1f - level(v))).coerceAtMost(size.height - line)
                (if (i == points.lastIndex) latest else before).addRoundRect(
                    RoundRect(
                        Rect(
                            x,
                            top,
                            x + thick,
                            size.height
                        ), topLeft = round, topRight = round
                    )
                )
            }
            drawPath(before, color.copy(alpha = 0.4f))
            drawPath(latest, color)
            drawLine(
                hairline,
                Offset(0f, size.height),
                Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        } else {
            val dot = 4.dp.toPx()
            // Room for the dot and its ring at the end, the top and the bottom.
            val pad = dot + line
            val left = line / 2
            val right = (size.width - pad).coerceAtLeast(left)
            val tall = (size.height - 2 * pad).coerceAtLeast(0f)
            fun x(i: Int) =
                if (points.size == 1) right else left + (right - left) * i / (points.size - 1)

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
                drawPath(wash, color.copy(alpha = 0.2f))
                drawPath(
                    path,
                    color,
                    style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            val end = Offset(x(points.lastIndex), y(points.last()))
            drawCircle(surface, radius = dot + line, center = end)
            drawCircle(color, radius = dot, center = end)
        }
    }
}
