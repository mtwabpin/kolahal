/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

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
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

// ---- countdown -------------------------------------------------------------------------------

/**
 * `countdown` in Bold, Split or Shape ([CountdownBody] draws Plain and Tonal, and does the ticking).
 * - [count] is the time left, read only where it is drawn, so a tick draws the number again and no more.
 * - [bar] is how much of the wait is over, in steps, or null without a start.
 * - [moment] is the moment in words ("20 Dec", or "17:25" on the day), [ends] the start and the
 *   moment under a bar.
 * - [now] is the time in seconds, for Split's hours and minutes.
 */
@Composable
internal fun CountdownLook(
    spec: WidgetSpec.Countdown,
    box: WidgetSizes.Box,
    look: WidgetLook,
    count: State<WidgetWords.Count>,
    bar: Float?,
    moment: String?,
    ends: Pair<String, String>?,
    now: LongState
) {
    when {
        look == WidgetLook.BOLD -> BoldCountdown(spec, box, count, bar, moment)
        look == WidgetLook.SPLIT -> SplitCountdown(spec, box, count, bar, moment, now)
        LocalCardShaped.current -> ShapedCountdown(spec, box, count, bar)
        else -> ShapeBesideCountdown(spec, count, bar, moment, ends)
    }
}

/** The time left in Bold's type, [size] at most, in [color], its unit smaller after it. Only this is drawn again at a tick. */
@Composable
private fun BoldCount(count: State<WidgetWords.Count>, size: TextUnit, color: Color) {
    val left = count.value
    BoldValue(StyledText.capitalized(left.value), left.unit, size, color)
}

/** The time left as a value in [style], its unit in [unitStyle] after it, or under it when [stacked]. Only this is drawn again at a tick. */
@Composable
private fun CountWords(
    count: State<WidgetWords.Count>,
    style: TextStyle,
    unitStyle: TextStyle,
    unitColor: Color,
    modifier: Modifier = Modifier,
    stacked: Boolean = false
) {
    val left = count.value
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(StyledText.capitalized(left.value), style)
            left.unit?.let {
                Text(
                    it,
                    style = unitStyle,
                    color = unitColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    } else {
        Row(modifier) {
            BigText(
                StyledText.capitalized(left.value),
                style,
                Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            )
            left.unit?.let {
                Text(
                    it,
                    style = unitStyle,
                    color = unitColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .alignByBaseline()
                )
            }
        }
    }
}

/** Bold's countdown: the title small with the moment at its end, the number as big as it fits in the role's color, and the bar or the label under it. */
@Composable
private fun BoldCountdown(
    spec: WidgetSpec.Countdown,
    box: WidgetSizes.Box,
    count: State<WidgetWords.Count>,
    bar: Float?,
    moment: String?
) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    val title = LocalUntitled.current
    val top: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallTitle(title, c.content, Modifier.weight(1f))
            moment?.let {
                Text(
                    it,
                    style = type.labelMedium,
                    color = c.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            top()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) { BoldCount(count, 96.sp, role) }
            if (bar != null) LevelBar(
                bar,
                moving = false,
                role,
                Modifier.padding(top = 10.dp),
                wavy = true
            )
            else spec.label?.let {
                Text(
                    it,
                    style = type.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        WidgetSizes.Box.WIDE -> Column(Modifier.fillMaxSize()) {
            top()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) { BoldCount(count, 56.sp, role) }
            bar?.let {
                LevelBar(
                    it,
                    moving = false,
                    role,
                    Modifier.padding(top = 4.dp),
                    wavy = true
                )
            }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoldCount(
                    count,
                    44.sp,
                    role
                )
            }
            spec.label?.let {
                Text(
                    it,
                    style = type.labelSmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Split's countdown: a big tile with the title, the icon and the time left, over small tiles. With
 * days left they have the hours and the minutes after the days ("07 hours", "41 minutes"); else the
 * moment and the label. A 1-row card has the moment in a tile beside the time; a small one under it.
 */
@Composable
private fun SplitCountdown(
    spec: WidgetSpec.Countdown,
    box: WidgetSizes.Box,
    count: State<WidgetWords.Count>,
    bar: Float?,
    moment: String?,
    now: LongState
) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SPLIT, role)
    val title = LocalUntitled.current
    val gap = SplitCorners.GAP
    val unitStyle = type.labelLarge.copy(fontWeight = FontWeight.Bold)
    when (box) {
        WidgetSizes.Box.LARGE -> {
            // With days left: the hours and the minutes after them. They change with the minute.
            val parts by remember(spec.to, count) {
                derivedStateOf {
                    if (count.value.unit?.startsWith("day") == true) {
                        val left = kotlin.math.abs(spec.to - now.longValue)
                        listOf(
                            "%02d".format((left % 86_400) / 3_600) to "Hours",
                            "%02d".format((left % 3_600) / 60) to "Minutes"
                        )
                    } else {
                        listOfNotNull(moment?.let { it to (spec.label ?: "") })
                    }
                }
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Tile(
                    SplitCorners.inColumn(0, if (parts.isEmpty()) 1 else 2),
                    c.tile,
                    Modifier
                        .fillMaxWidth()
                        .weight(1.6f)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        TitleAndIcon(title, spec.base.icon, c.content, role)
                        Spacer(Modifier.weight(1f))
                        CountWords(
                            count,
                            type.displaySmall.copy(fontSize = 48.sp),
                            unitStyle,
                            c.content
                        )
                        bar?.let {
                            LevelBar(
                                it,
                                moving = false,
                                role,
                                Modifier.padding(top = 6.dp),
                                wavy = true
                            )
                        }
                    }
                }
                if (parts.isNotEmpty()) Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    parts.forEachIndexed { i, (value, caption) ->
                        key(i) {
                            Tile(
                                SplitCorners.tile(topStart = false, topEnd = false, bottomEnd = i == parts.lastIndex, bottomStart = i == 0),
                                c.tile,
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    BigText(value, type.titleLarge)
                                    if (caption.isNotEmpty()) Text(
                                        caption,
                                        style = type.labelSmall,
                                        color = c.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        WidgetSizes.Box.WIDE -> Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Tile(
                SplitCorners.inRow(0, 2), c.tile, Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    TitleAndIcon(title, spec.base.icon, c.content, role)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) { CountWords(count, type.headlineMedium, unitStyle, c.content) }
                    bar?.let { LevelBar(it, moving = false, role, wavy = true) }
                }
            }
            Tile(
                SplitCorners.inRow(1, 2), c.tile, Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    moment?.let { BigText(it, type.titleMedium) }
                    spec.label?.let {
                        Text(
                            it,
                            style = type.labelSmall,
                            color = c.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Tile(
                SplitCorners.inColumn(0, if (moment != null) 2 else 1),
                c.tile,
                Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
            ) {
                CountWords(
                    count,
                    type.titleLarge,
                    type.labelSmall.copy(fontWeight = FontWeight.Bold),
                    c.content,
                    stacked = true,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 6.dp)
                )
            }
            moment?.let {
                Tile(
                    SplitCorners.inColumn(1, 2), c.tile, Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        it,
                        style = type.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = c.content,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

/** Shape's countdown in a card that is a sun: the number, its unit, the label, and a thin ring for the wait. A small card has the number and its unit. */
@Composable
private fun ShapedCountdown(
    spec: WidgetSpec.Countdown,
    box: WidgetSizes.Box,
    count: State<WidgetWords.Count>,
    bar: Float?
) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val small = box == WidgetSizes.Box.SMALL
    val line = (spec.label ?: LocalUntitled.current).takeIf { !small }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth
        bar?.let {
            ThinRing(
                it,
                role,
                c.tile,
                side * 0.76f,
                (side * 0.025f).coerceAtLeast(2.dp),
                Modifier.align(Alignment.Center)
            )
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = side * 0.2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CountWords(
                count,
                if (small) type.titleLarge else type.displayMedium.copy(fontSize = 50.sp),
                (if (small) type.labelSmall else type.labelLarge).copy(fontWeight = FontWeight.Bold),
                c.content,
                stacked = true
            )
            line?.let {
                Text(
                    it,
                    style = type.labelSmall,
                    color = c.content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Shape's countdown in a wide card: the number and its unit in a sun, and beside it the title over the bar (with its ends) or the label and the moment. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideCountdown(
    spec: WidgetSpec.Countdown,
    count: State<WidgetWords.Count>,
    bar: Float?,
    moment: String?,
    ends: Pair<String, String>?
) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    BesideShape(MaterialShapes.Sunny, c.shapeFill, inShape = { side ->
        CountWords(
            count,
            shapeValueStyle(side, 0.3f),
            type.labelSmall.copy(fontWeight = FontWeight.Bold),
            c.content,
            stacked = true,
            modifier = Modifier.padding(horizontal = side / 6)
        )
    }) {
        SmallTitle(LocalUntitled.current, c.content)
        if (bar != null) {
            LevelBar(bar, moving = false, role, Modifier.padding(top = 8.dp), wavy = true)
            ends?.let { (start, end) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    Text(start, style = type.bodySmall, color = c.muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(
                        end,
                        style = type.bodySmall,
                        color = c.muted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        } else {
            val words = listOfNotNull(spec.label, moment).joinToString(" · ")
            if (words.isNotEmpty()) Text(
                words,
                style = type.bodySmall,
                color = c.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** A thin ring from the top, [fraction] of the way round in [color] over [track], [side] across. It is drawn once per value. */
@Composable
private fun ThinRing(
    fraction: Float,
    color: Color,
    track: Color,
    side: Dp,
    line: Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(side)) {
        val stroke = line.toPx()
        val inset = stroke / 2
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
        drawArc(
            color,
            -90f,
            360f * fraction.coerceIn(0f, 1f),
            false,
            Offset(inset, inset),
            arc,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}
