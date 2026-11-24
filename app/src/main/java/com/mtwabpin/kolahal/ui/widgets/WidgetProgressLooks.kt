/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

// ---- progress --------------------------------------------------------------------------------

/** `progress` in Bold, Split or Shape ([ProgressBody] draws Plain and Tonal). */
@Composable
internal fun ProgressLook(
    spec: WidgetSpec.Progress,
    box: WidgetSizes.Box,
    look: WidgetLook,
    color: Color
) {
    when {
        spec.items.isNotEmpty() -> when (look) {
            WidgetLook.BOLD -> BoldBars(spec, box, color)
            WidgetLook.SPLIT -> SplitBars(spec, color)
            else -> ShapeBars(spec, color)
        }

        look == WidgetLook.BOLD -> BoldProgress(spec, box)
        look == WidgetLook.SPLIT -> SplitProgress(spec, box, color)
        LocalCardShaped.current -> ShapedProgress(spec, box, color)
        else -> ShapeBesideProgress(spec, color)
    }
}

/**
 * Bold's progress: the card fills from the bottom to the level (the frame draws that), the title
 * small at the top, the number as big as it fits with "%" smaller, and the label under it.
 */
@Composable
private fun BoldProgress(spec: WidgetSpec.Progress, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.BOLD, roleColor(spec.base))
    val title = LocalUntitled.current
    val (number, unit) = splitPercent(spec.text ?: WidgetWords.percent(spec.value, spec.max))
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            SmallTitle(title, c.content)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) { BoldValue(number, unit, 92.sp, c.content) }
            spec.label?.let {
                RingLabel(
                    it,
                    spec.labelIcon,
                    type.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    c.content,
                    Modifier.padding(top = 8.dp)
                )
            }
        }

        WidgetSizes.Box.WIDE -> Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) { BoldValue(number, unit, 64.sp, c.content) }
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .widthIn(max = 120.dp),
                horizontalAlignment = Alignment.End
            ) {
                SmallTitle(title, c.content)
                spec.label?.let { RingLabel(it, spec.labelIcon, type.labelMedium, c.content) }
            }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoldValue(
                    number,
                    unit,
                    48.sp,
                    c.content
                )
            }
            spec.label?.let {
                Text(
                    it,
                    style = type.labelSmall,
                    color = c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Bold's bars: the title small, then each bar with its label and its text in Bold's type over it, as many as fit. */
@Composable
private fun BoldBars(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.BOLD, color)
    val title = LocalUntitled.current.takeIf { box == WidgetSizes.Box.LARGE }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = ((maxHeight - if (title != null) 20.dp else 0.dp) / 30.dp).toInt()
            .coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            SmallTitle(title, c.content)
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    val tint = bar.color?.let { colorOf(it) } ?: color
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                bar.label.orEmpty(),
                                style = type.bodySmall,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            BoldText(
                                bar.text ?: WidgetWords.percent(bar.value, bar.max),
                                18.sp,
                                tint,
                                Modifier.padding(start = 8.dp)
                            )
                        }
                        LevelBar(
                            WidgetWords.fraction(bar.value, bar.max),
                            spec.moving,
                            tint,
                            Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Split's progress: a tile that fills from the bottom to the level, with the number at its top, beside
 * a tile with the title and the label. A small card has the level tile alone.
 */
@Composable
private fun SplitProgress(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, color)
    val title = LocalUntitled.current
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val level: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        Tile(shape, c.tile, modifier) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .background(roleContainer(color))
            )
            BigText(
                text,
                if (box == WidgetSizes.Box.LARGE) type.headlineMedium else type.titleLarge,
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 8.dp, end = 8.dp)
            )
            spec.labelIcon?.let {
                Icon(
                    painterResource(it),
                    contentDescription = null,
                    tint = c.content,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                        .size(20.dp)
                )
            }
        }
    }
    if (box == WidgetSizes.Box.SMALL) {
        level(Modifier.fillMaxSize(), SplitCorners.inRow(0, 1))
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(SplitCorners.GAP)) {
        level(
            Modifier
                .weight(1f)
                .fillMaxHeight(), SplitCorners.inRow(0, 2)
        )
        Tile(
            SplitCorners.inRow(1, 2),
            c.tile,
            Modifier
                .weight(if (box == WidgetSizes.Box.LARGE) 1f else 1.4f)
                .fillMaxHeight()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                SmallTitle(title, c.content, lines = 2)
                Spacer(Modifier.weight(1f))
                spec.label?.let {
                    Text(
                        it,
                        style = type.bodySmall,
                        color = c.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Split's bars: a tile for each bar, side by side, each filled from the bottom to its level, with its percent at the top and its label at the bottom. */
@Composable
private fun SplitBars(spec: WidgetSpec.Progress, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, color)
    val gap = SplitCorners.GAP
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = ((maxWidth + gap) / (56.dp + gap)).toInt().coerceIn(1, spec.items.size)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    val tint = bar.color?.let { colorOf(it) } ?: color
                    Tile(
                        SplitCorners.inRow(i, count),
                        c.tile,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(WidgetWords.fraction(bar.value, bar.max))
                                .background(roleContainer(tint))
                        )
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BigText(WidgetWords.percent(bar.value, bar.max), type.titleMedium)
                            Spacer(Modifier.weight(1f))
                            bar.label?.let {
                                Text(
                                    it,
                                    style = type.labelSmall,
                                    color = c.content,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [text] over Shape's level, in the box it shares with a fill that reaches [fraction] of the way up:
 * drawn twice, each clipped at the fill's line. On each side of the line it is in the content color
 * or the fill's "on" color, whichever reads better there ([inkOn]): over the fill (the accent) and
 * over the empty part (the highest container). So a number across the line reads on both sides, in
 * every theme. A screen reader hears it once.
 */
@Composable
private fun BoxScope.OnLevel(
    fraction: Float,
    colors: LookColors,
    text: @Composable BoxScope.(Color) -> Unit
) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier
            .matchParentSize()
            .levelClip(fraction, overFill = false)
    ) { text(inkOn(empty, colors.content, colors.onAccent)) }
    Box(
        Modifier
            .matchParentSize()
            .levelClip(fraction, overFill = true)
            .clearAndSetSemantics {}
    ) { text(inkOn(colors.accent, colors.onAccent, colors.content)) }
}

/** Draws only the part over a fill that reaches [fraction] of the way up ([overFill]), or only the part above it. */
private fun Modifier.levelClip(fraction: Float, overFill: Boolean): Modifier = drawWithContent {
    val line = size.height * (1f - fraction.coerceIn(0f, 1f))
    clipRect(
        top = if (overFill) line else 0f,
        bottom = if (overFill) size.height else line
    ) { this@drawWithContent.drawContent() }
}

/**
 * Shape's progress in a card that is a clover, or the shape chosen for it: the shape fills from the
 * bottom to the level, with the label above and the value on the fill.
 */
@Composable
private fun ShapedProgress(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val small = box == WidgetSizes.Box.SMALL
    val line = spec.label ?: LocalUntitled.current
    val extra = cardInset()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .background(color)
        )
        if (!small) line?.let {
            Text(
                it,
                style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = side * (0.15f + extra),
                        start = side * (0.22f + extra),
                        end = side * (0.22f + extra)
                    )
            )
        }
        // A little under the middle, where the fill reaches at half.
        OnLevel(fraction, c) { ink ->
            BigText(
                text,
                if (small) type.titleMedium else type.displaySmall,
                Modifier
                    .align(Alignment.Center)
                    .padding(top = side * 0.12f, start = side * (0.18f + extra), end = side * (0.18f + extra)),
                color = ink
            )
        }
    }
}

/**
 * Shape's progress in a wide card: a clover (or the shape chosen for the card, [cardShape]) filled
 * to the level with the value on it, and beside it the title over the label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideProgress(spec: WidgetSpec.Progress, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val title = LocalUntitled.current
    val own = cardShape(MaterialShapes.Clover4Leaf)
    val extra = cardInset()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxHeight, 140.dp, maxWidth / 2)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ShapedLevel(batteryPolygon(spec) ?: own, fraction, color, side) {
                OnLevel(fraction, c) { ink ->
                    BigText(
                        text,
                        shapeValueStyle(side, 0.25f),
                        Modifier
                            .align(Alignment.Center)
                            .padding(
                                top = side * 0.18f,
                                start = side * (1f / 6 + extra),
                                end = side * (1f / 6 + extra)
                            ),
                        color = ink
                    )
                }
            }
            Column(
                Modifier
                    .padding(start = 14.dp)
                    .weight(1f)
            ) {
                SmallTitle(title, c.content)
                spec.label?.let {
                    RingLabel(
                        it,
                        spec.labelIcon,
                        type.bodySmall,
                        c.muted,
                        Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}

/** Shape's bars: a row for each bar, as many as fit, with a small clover filled to its level, its label, and its text. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBars(spec: WidgetSpec.Progress, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = (maxHeight / 30.dp).toInt().coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapedLevel(
                            MaterialShapes.Clover4Leaf,
                            WidgetWords.fraction(bar.value, bar.max),
                            bar.color?.let { colorOf(it) } ?: color,
                            26.dp)
                        Text(
                            bar.label.orEmpty(),
                            style = type.bodySmall,
                            color = c.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f)
                        )
                        Text(
                            bar.text ?: WidgetWords.percent(bar.value, bar.max),
                            style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.content,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A Material shape [side] across, empty in the theme's highest surface and filled from the bottom
 * up to [fraction] in [color]: Shape's level. [content] is drawn over the fill. A new [polygon]
 * (the battery's, as its level changes) morphs from the one before and settles.
 */
@Composable
private fun ShapedLevel(
    polygon: RoundedPolygon,
    fraction: Float,
    color: Color,
    side: Dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        Modifier
            .size(side)
            .settledShape(rememberSettle(polygon), MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .background(color)
        )
        content()
    }
}

/** "68%" as "68" and its unit "%", so the unit can be smaller. Any other text is all value. */
private fun splitPercent(text: String): Pair<String, String?> =
    if (text.length > 1 && text.endsWith("%")) text.dropLast(1) to "%" else text to null
