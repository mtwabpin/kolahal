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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon

/*
 * The kinds' bodies in the looks without a title line: Bold, Split and Shape ([WidgetLook]). This
 * file has the pieces they share; each kind's looks are in a file of their own (WidgetStatLooks.kt,
 * WidgetListLooks.kt, …). Plain and Tonal draw in the kind's own file (WidgetStat.kt,
 * WidgetList.kt, …). Each body gets the card's title from [LocalUntitled] and places
 * it as small text where it fits, or leaves it to the screen reader. The card's description says
 * every field, also those a look leaves out.
 */

// ---- pieces the looks share ------------------------------------------------------------------

/** The title of a card without a title line, as a small bold line in [color]. */
@Composable
internal fun SmallTitle(
    text: String?,
    color: Color,
    modifier: Modifier = Modifier,
    lines: Int = 1
) {
    if (text == null) return
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        ),
        color = color,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** A Split tile: [shape] on [color], with [press] inside its corners, so a ripple keeps to them. */
@Composable
internal fun Tile(
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    press: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(shape)
            .background(color)
            .then(press)
    ) {
        CompositionLocalProvider(LocalInTile provides true) { content() }
    }
}

/** A Material shape [side] across, filled with [fill], with [content] in its middle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShapedBox(
    polygon: RoundedPolygon,
    fill: Color,
    side: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .size(side)
            .clip(polygon.toShape())
            .background(fill),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** The big value's type in a shape [side] across: about a third of the side. */
@Composable
internal fun shapeValueStyle(side: Dp, share: Float = 0.35f): TextStyle =
    MaterialTheme.typography.displayLarge.copy(
        fontSize = (side.value * share).sp,
        lineHeight = 1.15.em
    )

/**
 * A few short lines centered in a card that is one shape ([LocalCardShaped]). A shape's points and
 * bites take about a sixth of each side, so the lines keep inside that; a burst's take more ([cardInset]).
 */
@Composable
internal fun ShapeLines(content: @Composable ColumnScope.() -> Unit) {
    val extra = cardInset()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(maxWidth * (0.16f + extra)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/**
 * Shape outside a square box: the main value in a shape at the start, the rest beside it. The shape
 * is as tall as the card's room (about 72 dp in a 1-row card), up to 140 dp, and at most half the width.
 * In a shape chosen for the card that cuts deeper, what is in it keeps more room ([cardInset]).
 */
@Composable
internal fun BesideShape(
    polygon: RoundedPolygon,
    fill: Color,
    inShape: @Composable BoxScope.(Dp) -> Unit,
    beside: @Composable ColumnScope.() -> Unit
) {
    val extra = cardInset()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxHeight, 140.dp, maxWidth / 2)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ShapedBox(polygon, fill, side) {
                if (extra > 0f) Box(Modifier.padding(side * extra), contentAlignment = Alignment.Center) { inShape(side) }
                else inShape(side)
            }
            Column(
                Modifier
                    .padding(start = 14.dp)
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                content = beside
            )
        }
    }
}

/** A value in Bold's type, [size] at most, in [color], with its [unit] smaller after it. With [rolls], its digits roll ([RollingText]). */
@Composable
internal fun BoldValue(
    value: String,
    unit: String?,
    size: TextUnit,
    color: Color,
    unitColor: Color = MaterialTheme.colorScheme.onSurface,
    rolls: Boolean = false
) {
    Row {
        val place = Modifier
            .weight(1f, fill = false)
            .alignByBaseline()
        if (rolls) RollingText(value) { roll, laid -> BoldText(value, size, color, place.then(roll), laid) }
        else BoldText(value, size, color, place)
        unit?.let {
            val unitSize = maxOf(size.value * 0.22f, 12f).sp
            Text(
                it,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = unitSize,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = unitColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .alignByBaseline()
            )
        }
    }
}

/** A row of a title in [color] and the card's [icon] in its [role] color at the end: the top of a Split tile. */
@Composable
internal fun TitleAndIcon(title: String?, icon: String?, color: Color, role: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.orEmpty(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        icon?.let {
            WidgetIconText(
                it,
                MaterialTheme.typography.titleMedium,
                role,
                size = 18.dp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** A text that fills its room: as large as [largest], and smaller until all of it fits, down to 11 sp. */
@Composable
internal fun FittedText(
    text: AnnotatedString,
    style: TextStyle,
    largest: TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val auto = remember(largest) {
        TextAutoSize.StepBased(
            minFontSize = 11.sp,
            maxFontSize = largest,
            stepSize = 1.sp
        )
    }
    Text(
        text,
        style = style,
        color = color,
        autoSize = auto,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
