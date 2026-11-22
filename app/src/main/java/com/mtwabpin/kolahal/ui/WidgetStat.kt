/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * `stat`: one big value with its label.
 * - Small: the value and the label, centered.
 * - Wide: the value with its change at the start, and a column at the end: the label over the
 *   first smaller value. The others go between them, as many as fit. A box under 150 dp has no
 *   room for that column: the label goes under the value, and a bar takes the label's place.
 * - Large: the value and its label at the top. The smaller values and the bar are at the bottom.
 * - With no label, the card's name ([LocalUntitled]) is its label: there is no title line.
 */
@Composable
internal fun StatBody(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val look = LocalWidgetLook.current
    if (look.untitled()) {
        StatLook(spec, box, look)
        return
    }
    val type = MaterialTheme.typography
    // Plain, or Tonal: the same layout, with chips and quiet text in the Tonal card's colors.
    val muted = lookColors(look).muted
    val label = spec.label ?: LocalUntitled.current
    if (box == WidgetSizes.Box.SMALL) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigText(WidgetWords.joined(spec.value, spec.unit), valueStyle(box))
            label?.let {
                Text(
                    cardText(it),
                    style = type.labelSmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }
    val color = roleColor(spec.base)
    val large = box == WidgetSizes.Box.LARGE
    val hasChange = spec.trend != null || spec.delta != null
    val value: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier) {
            BigText(
                spec.value,
                valueStyle(box),
                Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            )
            spec.unit?.let {
                Text(
                    it,
                    style = type.titleMedium,
                    color = muted,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .alignByBaseline()
                )
            }
            // A large card has the change under the value, beside the label, as the mockup has it.
            if (hasChange && !large) ChangeText(
                spec.trend,
                spec.delta,
                color,
                type.labelLarge,
                Modifier
                    .padding(start = 8.dp)
                    .alignByBaseline()
            )
        }
    }
    if (large) {
        Column(Modifier.fillMaxSize()) {
            value(Modifier)
            if (label != null || hasChange) Row(verticalAlignment = Alignment.CenterVertically) {
                label?.let {
                    Text(
                        cardText(it),
                        style = type.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (hasChange) ChangeText(
                    spec.trend,
                    spec.delta,
                    color,
                    type.titleSmall,
                    Modifier.padding(start = if (label != null) 8.dp else 0.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (spec.more.isNotEmpty()) {
                if (look == WidgetLook.TONAL) {
                    // Tonal has the smaller values in chips of a lighter tint, as the mockup has them.
                    val tile = lookColors(look).tile
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        spec.more.forEach {
                            SmallValue(
                                it,
                                muted,
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tile)
                                    .padding(vertical = 6.dp),
                                centered = true
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        spec.more.forEach {
                            SmallValue(
                                it,
                                muted,
                                Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, color, Modifier.padding(top = 8.dp)) }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth >= STAT_SIDE_FROM
        val first = spec.more.firstOrNull()
        // A smaller value between the big one and the end column needs about 64 dp, after 112 dp
        // for the big one and 96 dp for the column.
        val column = if (side && (label != null || first != null)) 96.dp else 0.dp
        val between = if (side) spec.more.drop(1) else spec.more
        val half = maxWidth / 2
        val beside = ((maxWidth - 112.dp - column) / 64.dp).toInt().coerceIn(0, between.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    value(Modifier)
                    if (!side && spec.progress == null) label?.let {
                        Text(
                            cardText(it),
                            style = type.bodySmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                between.take(beside).forEach {
                    SmallValue(
                        it,
                        muted,
                        Modifier
                            .padding(start = 12.dp)
                            .widthIn(max = 72.dp)
                    )
                }
                if (column > 0.dp) {
                    Column(
                        Modifier
                            .padding(start = 12.dp)
                            .widthIn(max = half),
                        horizontalAlignment = Alignment.End
                    ) {
                        label?.let {
                            // Over a smaller value the label is the column's heading. Alone it is a quiet note, on up to two lines.
                            if (first != null) Text(
                                cardText(it),
                                style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            else Text(
                                cardText(it),
                                style = type.bodySmall,
                                color = muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                        }
                        first?.let {
                            Text(
                                WidgetWords.joined(it.value, it.label),
                                style = type.bodySmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, color, Modifier.padding(top = 4.dp)) }
        }
    }
}

/** From this width, a wide stat has a column at its end for its label and its first smaller value. */
private val STAT_SIDE_FROM = 150.dp

/**
 * A stat's change in [color]: a drawn arrow for up or down (a text arrow for flat), then the
 * delta in bold. The arrow is as tall as the text.
 */
@Composable
internal fun ChangeText(
    trend: String?,
    delta: String?,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val drawn = when (trend) {
        "up" -> R.drawable.ic_arrow_up
        "down" -> R.drawable.ic_arrow_down
        else -> null
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (drawn != null) Icon(
            painterResource(drawn),
            null,
            tint = color,
            modifier = Modifier.size(with(LocalDensity.current) { style.fontSize.toDp() })
        )
        else WidgetWords.arrow(trend)?.let { Text(it, style = style, color = color, maxLines = 1) }
        delta?.let {
            Text(
                it,
                style = style.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

/**
 * One of a stat's smaller values, over its label in [muted]. With [centered], both are centered.
 * - The label starts with a capital here, on a line of its own. Where a look writes it after the
 *   value on one line ("62% humidity"), it keeps the script's case.
 */
@Composable
internal fun SmallValue(
    small: WidgetSpec.Small,
    muted: Color,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            small.value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        small.label?.let {
            Text(
                StyledText.capitalized(it),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
