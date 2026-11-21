package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap

/*
 * The kinds' bodies in the looks without a title line: Bold, Split and Shape ([WidgetLook]). Plain
 * and Tonal draw in WidgetKinds.kt. Each body gets the card's title from [LocalUntitled] and places
 * it as small text where it fits, or leaves it to the screen reader. The card's description says
 * every field, also those a look leaves out.
 */

// ---- pieces the looks share ------------------------------------------------------------------

/** The title of a card without a title line, as a small bold line in [colour]. */
@Composable
private fun SmallTitle(text: String?, colour: Color, modifier: Modifier = Modifier, lines: Int = 1) {
    if (text == null) return
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colour, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** A Split tile: [shape] on [colour], with [press] inside its corners, so a ripple keeps to them. */
@Composable
private fun Tile(shape: Shape, colour: Color, modifier: Modifier = Modifier, press: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(shape).background(colour).then(press), content = content)
}

/** A Material shape [side] across, filled with [fill], with [content] in its middle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapedBox(polygon: RoundedPolygon, fill: Color, side: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.size(side).clip(polygon.toShape()).background(fill), contentAlignment = Alignment.Center, content = content)
}

/**
 * A Material shape [side] across, empty in the theme's highest surface and filled from the bottom
 * up to [fraction] in [colour]: Shape's level. [content] is drawn over the fill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapedLevel(polygon: RoundedPolygon, fraction: Float, colour: Color, side: Dp, content: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.size(side).clip(polygon.toShape()).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fraction).background(colour))
        content()
    }
}

/** The big value's type in a shape [side] across: about a third of the side. */
@Composable
private fun shapeValueStyle(side: Dp, share: Float = 0.35f): TextStyle =
    MaterialTheme.typography.displayLarge.copy(fontSize = (side.value * share).sp, lineHeight = 1.15.em)

/**
 * A few short lines centred in a card that is one shape ([LocalCardShaped]). A shape's points and
 * bites take about a sixth of each side, so the lines keep inside that.
 */
@Composable
private fun ShapeLines(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(maxWidth * 0.16f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * Shape outside a square box: the main value in a shape at the start, the rest beside it. The shape
 * is as tall as the card's room (about 72 dp in a 1-row card), up to 140 dp, and at most half the width.
 */
@Composable
private fun BesideShape(polygon: RoundedPolygon, fill: Color, inShape: @Composable BoxScope.(Dp) -> Unit, beside: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxHeight, 140.dp, maxWidth / 2)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ShapedBox(polygon, fill, side) { inShape(side) }
            Column(Modifier.padding(start = 14.dp).weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, content = beside)
        }
    }
}

/** A value in Bold's type, [size] at most, in [colour], with its [unit] smaller after it. */
@Composable
private fun BoldValue(value: String, unit: String?, size: TextUnit, colour: Color, unitColour: Color = MaterialTheme.colorScheme.onSurface) {
    Row {
        BoldText(value, size, colour, Modifier.weight(1f, fill = false).alignByBaseline())
        unit?.let {
            val unitSize = maxOf(size.value * 0.22f, 12f).sp
            Text(it, style = MaterialTheme.typography.titleMedium.copy(fontSize = unitSize, fontWeight = FontWeight.ExtraBold), color = unitColour, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp).alignByBaseline())
        }
    }
}

/** A row of a title in [colour] and the card's [icon] in its [role] colour at the end: the top of a Split tile. */
@Composable
private fun TitleAndIcon(title: String?, icon: String?, colour: Color, role: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title.orEmpty(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        icon?.let { WidgetIconText(it, MaterialTheme.typography.titleMedium, role, size = 18.dp, modifier = Modifier.padding(start = 6.dp)) }
    }
}

/** The card's [icon] in its [role] colour on a cookie of the role's container, [side] across: Bold's badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconCookie(icon: String, role: Color, side: Dp) {
    ShapedBox(MaterialShapes.Cookie12Sided, roleContainer(role), side) { WidgetIconText(icon, MaterialTheme.typography.titleMedium, role, size = side / 2) }
}

/** "68%" as "68" and its unit "%", so the unit can be smaller. Any other text is all value. */
private fun splitPercent(text: String): Pair<String, String?> =
    if (text.length > 1 && text.endsWith("%")) text.dropLast(1) to "%" else text to null

/**
 * Bold's chart, and Shape's beside its value: a round-ended pill for each point, at most [thick]
 * wide with [gap] between, grouped at the end of the room. The latest pill is in [accent], the
 * others in [rest]. The first and the last of [labels] are under the group's ends, the last in
 * bold. A line's points get a scale with room under the lowest, so each pill shows its height.
 */
@Composable
private fun PillChart(points: List<Float>, bars: Boolean, min: Float?, max: Float?, accent: Color, rest: Color, labels: List<String>, thick: Dp, gap: Dp, modifier: Modifier = Modifier) {
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
            Canvas(Modifier.width(group).weight(1f)) {
                if (points.isEmpty() || size.height <= 0f) return@Canvas
                val (low, high) = scale
                val p = pill.toPx()
                val s = space.toPx()
                val round = CornerRadius(p / 2)
                points.forEachIndexed { i, v ->
                    // A value at the bottom of the scale is still a round dot.
                    val h = (size.height * WidgetWords.fraction(v - low, high - low)).coerceIn(p, size.height)
                    drawRoundRect(if (i == points.lastIndex) accent else rest, Offset(i * (p + s), size.height - h), Size(p, h), round)
                }
            }
            if (labels.isNotEmpty()) Row(Modifier.width(group).padding(top = 4.dp)) {
                Text(labels.first(), style = type.labelSmall, color = lookColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                if (labels.size > 1) Text(labels.last(), style = type.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** A thin ring from the top, [fraction] of the way round in [colour] over [track], [side] across. It is drawn once per value. */
@Composable
private fun ThinRing(fraction: Float, colour: Color, track: Color, side: Dp, line: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(side)) {
        val stroke = line.toPx()
        val inset = stroke / 2
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
        drawArc(colour, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ---- stat ------------------------------------------------------------------------------------

/** `stat` in Bold, Split or Shape ([StatBody] draws Plain and Tonal). */
@Composable
internal fun StatLook(spec: WidgetSpec.Stat, box: WidgetSizes.Box, look: WidgetLook) {
    when {
        look == WidgetLook.BOLD -> BoldStat(spec, box)
        look == WidgetLook.SPLIT -> SplitStat(spec, box)
        LocalCardShaped.current -> ShapedStat(spec, box)
        else -> ShapeBesideStat(spec, box)
    }
}

/**
 * Bold's stat: the title small at the top with the icon on a cookie at its end, the value as big as
 * it fits in the role's colour, and the label in bold under it, with the change or the smaller
 * values ("28° · 19°") at the end of that line.
 */
@Composable
private fun BoldStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    val title = LocalUntitled.current
    val hasChange = spec.trend != null || spec.delta != null
    val rest = spec.more.joinToString(" · ") { it.value }
    val end: @Composable () -> Unit = {
        if (hasChange) ChangeText(spec.trend, spec.delta, role, type.labelLarge)
        else if (rest.isNotEmpty()) Text(rest, style = type.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Top) {
                SmallTitle(title, c.content, Modifier.weight(1f).padding(top = 4.dp))
                spec.base.icon?.let { IconCookie(it, role, 40.dp) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { BoldValue(spec.value, spec.unit, 88.sp, role) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                spec.label?.let { Text(cardText(it), style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(start = 8.dp)) { end() }
            }
            spec.progress?.let { LevelBar(it, spec.moving, role, Modifier.padding(top = 8.dp)) }
        }
        WidgetSizes.Box.WIDE -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SmallTitle(title, c.content)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { BoldValue(spec.value, spec.unit, 64.sp, role) }
                }
                // The label's column takes at most about two fifths of the width: the value is the main thing.
                Column(Modifier.padding(start = 12.dp).weight(0.7f, fill = false), horizontalAlignment = Alignment.End) {
                    spec.label?.let { Text(cardText(it), style = type.labelLarge.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End) }
                    end()
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, role, Modifier.padding(top = 4.dp)) }
        }
        WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { BoldText(WidgetWords.joined(spec.value, spec.unit), 48.sp, role) }
            spec.label?.let { Text(cardText(it), style = type.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * Split's stat: a big tile with the title, the icon, the value and the label, over a row of small
 * tiles, one for each smaller value (three at most). A 1-row card has the smaller values beside
 * the value, as many as fit; a small one has the label in a tile under the value.
 */
@Composable
private fun SplitStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SPLIT, role)
    val title = LocalUntitled.current
    val gap = SplitCorners.GAP
    val hasChange = spec.trend != null || spec.delta != null
    val value: @Composable (TextStyle) -> Unit = { style ->
        Row {
            BigText(spec.value, style, Modifier.weight(1f, fill = false).alignByBaseline())
            spec.unit?.let { Text(it, style = type.titleMedium, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 2.dp).alignByBaseline()) }
            if (hasChange) ChangeText(spec.trend, spec.delta, role, type.labelLarge, Modifier.padding(start = 8.dp).alignByBaseline())
        }
    }
    when (box) {
        WidgetSizes.Box.LARGE -> {
            val more = spec.more.take(3)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Tile(SplitCorners.inColumn(0, if (more.isEmpty()) 1 else 2), c.tile, Modifier.fillMaxWidth().weight(1.9f)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        TitleAndIcon(title, spec.base.icon, c.content, role)
                        Spacer(Modifier.weight(1f))
                        value(type.displaySmall.copy(fontSize = 44.sp))
                        spec.label?.let { Text(cardText(it), style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                        spec.progress?.let { LevelBar(it, spec.moving, role, Modifier.padding(top = 6.dp)) }
                    }
                }
                if (more.isNotEmpty()) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    more.forEachIndexed { i, small ->
                        key(i) {
                            Tile(SplitCorners.tile(false, false, i == more.lastIndex, i == 0), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                                SmallValue(small, c.muted, Modifier.align(Alignment.Center).padding(horizontal = 4.dp), centred = true)
                            }
                        }
                    }
                }
            }
        }
        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit = ((maxWidth - 112.dp) / (64.dp + gap)).toInt().coerceIn(0, minOf(3, spec.more.size))
            val more = spec.more.take(fit)
            val count = more.size + 1
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(SplitCorners.inRow(0, count), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        TitleAndIcon(title, spec.base.icon, c.content, role)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) { value(type.headlineSmall) }
                        val progress = spec.progress
                        if (progress != null) LevelBar(progress, spec.moving, role)
                        else spec.label?.let { Text(cardText(it), style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                more.forEachIndexed { i, small ->
                    key(i) {
                        Tile(SplitCorners.inRow(i + 1, count), c.tile, Modifier.width(64.dp).fillMaxHeight()) {
                            SmallValue(small, c.muted, Modifier.align(Alignment.Center).padding(horizontal = 4.dp), centred = true)
                        }
                    }
                }
            }
        }
        WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            val label = spec.label
            Tile(SplitCorners.inColumn(0, if (label != null) 2 else 1), c.tile, Modifier.fillMaxWidth().weight(1.6f)) {
                BigText(WidgetWords.joined(spec.value, spec.unit), type.titleLarge, Modifier.align(Alignment.Center).padding(horizontal = 8.dp))
            }
            if (label != null) Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth().weight(1f)) {
                Text(cardText(label), style = type.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp))
            }
        }
    }
}

/** A stat's label and the card's title on one line: "Sunny · Pune". */
@Composable
private fun labelAndTitle(label: String?, title: String?): AnnotatedString? {
    val styled = label?.let { cardText(it) }
    return when {
        styled != null && title != null -> styled + AnnotatedString(" · $title")
        styled != null -> styled
        else -> title?.let(::AnnotatedString)
    }
}

/** Shape's stat in a card that is a cookie: the icon, the value, and "Sunny · Pune". A small card has the value and the label. */
@Composable
private fun ShapedStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val small = box == WidgetSizes.Box.SMALL
    val line = labelAndTitle(spec.label, LocalUntitled.current.takeIf { !small })
    ShapeLines {
        if (!small) spec.base.icon?.let { WidgetIconText(it, type.titleMedium, role, size = 22.dp) }
        BigText(WidgetWords.joined(spec.value, spec.unit), if (small) type.titleLarge else type.displayMedium.copy(fontSize = 46.sp))
        line?.let { Text(it, style = type.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
    }
}

/** Shape's stat in a wide card: the value in a cookie, and beside it "Sunny · Pune" over the smaller values. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val head = labelAndTitle(spec.label, LocalUntitled.current)
    val more = spec.more.joinToString(" · ") { WidgetWords.joined(it.value, it.label) }
    val large = box == WidgetSizes.Box.LARGE
    BesideShape(MaterialShapes.Cookie12Sided, c.shapeFill, inShape = { side ->
        BigText(WidgetWords.joined(spec.value, spec.unit), shapeValueStyle(side), Modifier.padding(horizontal = side / 7))
    }) {
        head?.let { Text(it, style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = if (large || more.isEmpty()) 2 else 1, overflow = TextOverflow.Ellipsis) }
        if (more.isNotEmpty()) Text(more, style = type.bodySmall, color = c.muted, maxLines = if (large) 3 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        if (spec.trend != null || spec.delta != null) ChangeText(spec.trend, spec.delta, role, type.labelLarge, Modifier.padding(top = 4.dp))
        spec.progress?.let { LevelBar(it, spec.moving, role, Modifier.padding(top = 6.dp)) }
    }
}

// ---- text ------------------------------------------------------------------------------------

/** `text` in Bold, Split or Shape ([TextBody] draws Plain and Tonal). A text's card is never one shape. */
@Composable
internal fun TextLook(spec: WidgetSpec.Text, box: WidgetSizes.Box, look: WidgetLook) {
    when (look) {
        WidgetLook.BOLD -> BoldTextBody(spec, box)
        WidgetLook.SPLIT -> SplitTextBody(spec, box)
        else -> ShapeTextBody(spec, box)
    }
}

/** A text that fills its room: as large as [largest], and smaller until all of it fits, down to 11 sp. */
@Composable
private fun FittedText(text: AnnotatedString, style: TextStyle, largest: TextUnit, colour: Color, modifier: Modifier = Modifier) {
    val auto = remember(largest) { TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = largest, stepSize = 1.sp) }
    Text(text, style = style, color = colour, autoSize = auto, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Bold's text: the quote large in Bold's narrow type, and its caption (or the title) in a chip of the role's container. */
@Composable
private fun BoldTextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    val largest = when (box) {
        WidgetSizes.Box.LARGE -> 34.sp
        WidgetSizes.Box.WIDE -> 24.sp
        WidgetSizes.Box.SMALL -> 20.sp
    }
    val style = remember(largest) { boldValueStyle(largest, FontWeight.ExtraBold).copy(lineHeight = 1.05.em, letterSpacing = (-0.01).em, fontFeatureSettings = null) }
    val chip = spec.caption ?: LocalUntitled.current
    Column(Modifier.fillMaxSize()) {
        FittedText(cardText(spec.text), style, largest, c.content, Modifier.weight(1f).fillMaxWidth())
        if (box == WidgetSizes.Box.LARGE && chip != null) {
            Text(
                cardText(chip),
                style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(roleContainer(role)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** The first letter of [text] on a cookie of the [role] colour, [side] across: who a quote is by. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LetterCookie(text: String, role: Color, side: Dp) {
    ShapedBox(MaterialShapes.Cookie9Sided, role, side) {
        Text(WidgetWords.badgeLetter(text).orEmpty(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = lookColors().onAccent, maxLines = 1)
    }
}

/** Split's text: the text in a big tile, over a tile with its caption (or the title) after its first letter on a cookie. */
@Composable
private fun SplitTextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SPLIT, role)
    val title = LocalUntitled.current
    val who = spec.caption ?: title
    val gap = SplitCorners.GAP
    val quote = cardText(spec.text)
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Tile(SplitCorners.inColumn(0, if (who != null) 2 else 1), c.tile, Modifier.fillMaxWidth().weight(1f)) {
                FittedText(quote, type.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold), 20.sp, c.content, Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp, vertical = 14.dp))
            }
            if (who != null) BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp)) {
                // The title too, at the end, where the tile has room for both.
                val both = spec.caption != null && title != null && maxWidth >= 260.dp
                Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        LetterCookie(who, role, 30.dp)
                        Text(cardText(who), style = type.labelLarge.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        if (both) Text(title.orEmpty(), style = type.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 140.dp))
                    }
                }
            }
        }
        WidgetSizes.Box.WIDE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Tile(SplitCorners.inColumn(0, if (who != null) 2 else 1), c.tile, Modifier.fillMaxWidth().weight(1f)) {
                FittedText(quote, type.bodyMedium.copy(fontWeight = FontWeight.SemiBold), 16.sp, c.content, Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp, vertical = 6.dp))
            }
            if (who != null) Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth().height(26.dp)) {
                Text(cardText(who), style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
            }
        }
        WidgetSizes.Box.SMALL -> Tile(SplitCorners.inColumn(0, 1), c.tile, Modifier.fillMaxSize()) {
            FittedText(quote, type.bodySmall.copy(fontWeight = FontWeight.SemiBold), 14.sp, c.content, Modifier.align(Alignment.Center).padding(10.dp))
        }
    }
}

/** The card's icon, or else the first letter of its title, in the [role] colour on a clover of the role's container. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextMark(icon: String?, title: String?, role: Color, side: Dp) {
    ShapedBox(MaterialShapes.Clover4Leaf, roleContainer(role), side) {
        if (icon != null) WidgetIconText(icon, MaterialTheme.typography.titleMedium, role, size = side * 0.46f)
        else Text(title?.let(WidgetWords::badgeLetter).orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = role)
    }
}

/** Shape's text: the card's icon on a clover, the text, and its caption under it. A small card has the text alone. */
@Composable
private fun ShapeTextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val title = LocalUntitled.current
    val quote = cardText(spec.text)
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            TextMark(spec.base.icon, title, role, 48.dp)
            FittedText(quote, type.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold), 20.sp, c.content, Modifier.padding(top = 12.dp).weight(1f).fillMaxWidth())
            spec.caption?.let { Text(cardText(it), style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        }
        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = min(maxHeight, 56.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TextMark(spec.base.icon, title, role, side)
                Column(Modifier.padding(start = 12.dp).weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    FittedText(quote, type.bodyMedium.copy(fontWeight = FontWeight.SemiBold), 16.sp, c.content, Modifier.weight(1f, fill = false))
                    spec.caption?.let { Text(cardText(it), style = type.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        WidgetSizes.Box.SMALL -> FittedText(quote, type.bodySmall, 13.sp, c.content, Modifier.fillMaxSize())
    }
}

// ---- list ------------------------------------------------------------------------------------

/** `list` in Bold, Split or Shape ([RowsBody] draws Plain and Tonal, and an empty list). A list's card is never one shape. */
@Composable
internal fun RowsLook(spec: WidgetSpec.Rows, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?, look: WidgetLook) {
    when (look) {
        WidgetLook.BOLD -> BoldRows(spec, box, onTap)
        WidgetLook.SPLIT -> SplitRows(spec, box, onTap)
        else -> ShapeRows(spec, box, onTap)
    }
}

/** The height of one of Bold's lines under its lead row. */
private val BOLD_LINE = 42.dp

/**
 * Bold's list: the title small in the role's colour with "+3 more" at its end, the first row (or
 * the list's lead) large, then as many rows as fit as plain lines. Every row keeps its own tap.
 * Rows with a range have a bar ([boldBars]): a line before its end text, the first row at the end
 * of its second line, all in one column.
 */
@Composable
private fun BoldRows(spec: WidgetSpec.Rows, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    val title = LocalUntitled.current
    val lead = spec.lead
    // Without a lead, the first row is the lead.
    val first = if (lead == null) spec.items.firstOrNull() else null
    val rest = if (first != null) spec.items.drop(1) else spec.items
    val lineStyle = type.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val endStyle = type.labelMedium.copy(fontWeight = FontWeight.Bold)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The lead row gets room for two lines and its second line: the rows fit under that.
        val lines = if (box == WidgetSizes.Box.LARGE) ((maxHeight - 16.dp - 82.dp) / BOLD_LINE).toInt().coerceIn(0, rest.size) else 0
        val more = rest.size - lines
        if (box == WidgetSizes.Box.SMALL) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (first != null) {
                    FittedText(cardText(first.title), boldValueStyle(18.sp, FontWeight.ExtraBold).copy(lineHeight = 1.05.em, textAlign = TextAlign.Center, fontFeatureSettings = null), 18.sp, c.content, Modifier.weight(1f, fill = false).then(rowPress(first, onTap)))
                } else if (lead != null) {
                    BoldValue(lead.value, lead.unit, 40.sp, role)
                }
                if (more > 0) Text("+$more more", style = type.labelSmall, color = c.muted, maxLines = 1, modifier = Modifier.opensList())
            }
            return@BoxWithConstraints
        }
        val shown = rest.take(lines)
        // The first row's second line: its detail and its end text.
        val second = first?.let { listOfNotNull(it.detail, it.trailing).joinToString(" · ") }.orEmpty()
        val (bars, firstBar) = boldBars(first, second, shown, maxWidth, lineStyle, endStyle)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title.orEmpty(), style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = role, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (more > 0) Text("+$more more", style = type.labelMedium, color = c.muted, maxLines = 1, modifier = Modifier.opensList().padding(start = 8.dp))
            }
            val large = box == WidgetSizes.Box.LARGE
            if (first != null) {
                Column(Modifier.fillMaxWidth().padding(top = if (large) 8.dp else 2.dp).then(rowPress(first, onTap))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        first.done?.let { RowCheck(it, first.color?.let { k -> colourOf(k) } ?: role, Modifier.padding(end = 8.dp)) }
                        Text(
                            cardText(first.title),
                            style = boldValueStyle(if (large) 26.sp else 20.sp, FontWeight.ExtraBold).copy(lineHeight = 1.05.em, fontFeatureSettings = null),
                            color = c.content,
                            maxLines = if (large) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (bars != null && firstBar) {
                        // The bar in the lines' column: its end text is in the line already, so that column stays empty.
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(cardText(second), style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            RangeSlot(first.copy(trailing = null), bars, first.color?.let { colourOf(it) } ?: role, c.card, endStyle, c.muted)
                        }
                    } else if (second.isNotEmpty()) {
                        Text(cardText(second), style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else if (lead != null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { BoldValue(lead.value, lead.unit, if (large) 56.sp else 40.sp, role) }
                lead.label?.let { Text(it, style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) }
            }
            if (first != null) Spacer(Modifier.weight(1f))
            val divider = MaterialTheme.colorScheme.outlineVariant
            shown.forEachIndexed { i, row ->
                key(i) {
                    Row(
                        Modifier.fillMaxWidth().height(BOLD_LINE)
                            .drawBehind { drawLine(divider, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                            .then(rowPress(row, onTap)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.done?.let { RowCheck(it, row.color?.let { k -> colourOf(k) } ?: role, Modifier.padding(end = 8.dp)) }
                        Text(cardText(row.title), style = lineStyle, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (bars != null) RangeSlot(row, bars, row.color?.let { colourOf(it) } ?: role, c.card, endStyle, c.muted)
                        else row.trailing?.let { Text(cardText(it), style = endStyle, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * The bars of Bold's list ([RangeBars]), [width] wide: its [lines] under the [first] row, and that
 * row itself, which has its bar at the end of its [second] line. True with the bars when the first
 * row has its bar too.
 * - All the bars are in one column. So the first row's second line counts as words, as the lines'
 *   titles do: a checkbox takes 26 dp before a line's title.
 * - When the second line leaves no room, only the lines have bars. Bold's rows have no icons.
 */
@Composable
private fun boldBars(first: WidgetSpec.Row?, second: String, lines: List<WidgetSpec.Row>, width: Dp, lineStyle: TextStyle, endStyle: TextStyle): Pair<RangeBars?, Boolean> {
    val rows = listOfNotNull(first) + lines
    if (rows.none { it.range != null }) return null to false
    val check = if (lines.any { it.done != null }) 26.dp else 0.dp
    val titles = if (lines.isEmpty()) 0.dp else widestText(lines.map { it.title }, lineStyle) + check
    val trailing = widestText(lines.mapNotNull { it.trailing }, endStyle)
    if (first?.range != null) {
        val words = maxOf(titles, widestText(listOf(second), MaterialTheme.typography.bodySmall))
        rangeBars(rows, width, words, trailing, 0.dp)?.let { return it to true }
    }
    if (lines.none { it.range != null }) return null to false
    return rangeBars(rows, width, titles, trailing, 0.dp) to false
}

/**
 * What starts a row of Split or Shape: its checkbox, or its icon, or else its rank. The rank is on
 * [shape], [side] across: the first in the role's colour, a row with a colour in that colour, the
 * others in the role's container ([roleContainer]). With [inShape], an icon sits on that shape too.
 */
@Composable
private fun RowMark(index: Int, row: WidgetSpec.Row, role: Color, shape: Shape, side: Dp, inShape: Boolean) {
    val type = MaterialTheme.typography
    val colours = lookColors()
    val colour = row.color?.let { colourOf(it) }
    val done = row.done
    val icon = row.icon
    when {
        done != null -> RowCheck(done, colour ?: role)
        icon != null && !inShape -> WidgetIconText(icon, type.titleMedium, colour ?: colours.muted, size = 18.dp)
        else -> {
            val strong = colour != null || index == 0
            // The others are on the role's container: the card's highest surface is too close to the card to see.
            val container = roleContainer(role)
            val fill = if (icon != null) container else colour ?: if (index == 0) role else container
            Box(Modifier.size(side).background(fill, shape), contentAlignment = Alignment.Center) {
                if (icon != null) WidgetIconText(icon, type.titleMedium, colour ?: role, size = side / 2)
                else Text("${index + 1}", style = type.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = if (strong) colours.onAccent else colours.content, maxLines = 1)
            }
        }
    }
}

/**
 * A row's title, with its second line under it when [details] has room for one, and its end text in [muted].
 * With [bars], the row's range has its bar before the end text ([RangeSlot]): in the row's colour or
 * the card's [role], with [under] the colour beneath it.
 */
@Composable
private fun RowWords(
    row: WidgetSpec.Row,
    details: Boolean,
    content: Color,
    muted: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    bars: RangeBars? = null,
    role: Color = Color.Unspecified,
    under: Color = Color.Unspecified,
) {
    val type = MaterialTheme.typography
    val end = type.labelMedium.copy(fontWeight = FontWeight.Bold)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(cardText(row.title), style = style, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details) row.detail?.let { Text(cardText(it), style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (bars != null) RangeSlot(row, bars, row.color?.let { colourOf(it) } ?: role, under, end, muted)
        else row.trailing?.let { Text(cardText(it), style = end, color = muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp)) }
    }
}

/**
 * The bars of Split's or Shape's [rows] ([RangeBars]), [width] wide from a row's start to the end of
 * its words. Where a row's words start:
 * - with a checkbox, [checked] from its start. The checkbox stays.
 * - with its mark ([RowMark]), [start], the mark and [gap]. The mark is a rank [side] across, or an
 *   icon: in a shape of that side with [inShape], else as wide as it is drawn. The marks give way
 *   to the bars.
 * - without, [start].
 */
@Composable
private fun lookBars(rows: List<WidgetSpec.Row>, width: Dp, start: Dp, checked: Dp, side: Dp, gap: Dp, style: TextStyle, inShape: Boolean, details: Boolean): RangeBars? {
    if (rows.none { it.range != null }) return null
    val type = MaterialTheme.typography
    val icon = if (inShape) side else iconWidth(rows, type.titleMedium)
    fun marked(r: WidgetSpec.Row) = when {
        r.done != null -> checked
        r.icon != null -> start + icon + gap
        else -> start + side + gap
    }
    val bare = rows.maxOf { if (it.done != null) checked else start }
    return rangeBars(
        rows,
        room = width - bare,
        // A row's second line sits under its title, in the same column: it must fit too.
        words = maxOf(widestText(rows.map { it.title }, style), widestText(if (details) rows.mapNotNull { it.detail } else emptyList(), type.bodySmall)),
        trailing = widestText(rows.mapNotNull { it.trailing }, type.labelMedium.copy(fontWeight = FontWeight.Bold)),
        mark = rows.maxOf { marked(it) } - bare,
    )
}

/** A list's lead as Split writes it: the value big with its unit, and the label under them. */
@Composable
private fun LeadWords(lead: WidgetSpec.Lead, style: TextStyle, muted: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        BigText(WidgetWords.joined(lead.value, lead.unit), style)
        lead.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * Split's list: a header tile with the title and "+3 more", then a tile for each row that fits,
 * with its rank on a round chip. A 1-row card has the title in a tile at the start and the rows
 * beside it; a small one has the rows alone. Rows with a range have a bar before their end text
 * ([RangeBars]), where the tiles have room: the ranks give way first. A small card's rows have
 * their titles alone, so no bars either.
 */
@Composable
private fun SplitRows(spec: WidgetSpec.Rows, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SPLIT, role)
    val title = LocalUntitled.current
    val lead = spec.lead
    val items = spec.items
    val gap = SplitCorners.GAP
    val rowStyle = type.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val details = maxWidth >= 200.dp
        when (box) {
            WidgetSizes.Box.LARGE -> {
                val leadRoom = if (lead != null) 64.dp + gap else 0.dp
                val fit = ((maxHeight - 40.dp - leadRoom) / (44.dp + gap)).toInt().coerceIn(0, items.size)
                val more = items.size - fit
                val count = 1 + (if (lead != null) 1 else 0) + fit
                // A row's words start 10 dp in, after its mark: a check at 6 dp, or a rank at 10 dp. Its end has 16 dp.
                val bars = lookBars(items.take(fit), maxWidth - 16.dp, start = 10.dp, checked = 34.dp, side = 24.dp, gap = 10.dp, rowStyle, inShape = false, details = details)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    // The last tile takes what is left, so the tiles reach the card's bottom.
                    Tile(SplitCorners.inColumn(0, count), c.tile, Modifier.fillMaxWidth().then(if (count == 1) Modifier.weight(1f) else Modifier.height(40.dp))) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title.orEmpty(), style = type.labelLarge.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (more > 0) Text("+$more more", style = type.labelMedium, color = c.muted, maxLines = 1, modifier = Modifier.opensList().padding(start = 8.dp))
                        }
                    }
                    if (lead != null) Tile(SplitCorners.inColumn(1, count), c.tile, Modifier.fillMaxWidth().then(if (fit == 0) Modifier.weight(1f) else Modifier.height(64.dp))) {
                        LeadWords(lead, type.headlineMedium, c.muted, Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp))
                    }
                    items.take(fit).forEachIndexed { i, row ->
                        key(i) {
                            val at = count - fit + i
                            Tile(SplitCorners.inColumn(at, count), c.tile, Modifier.fillMaxWidth().weight(1f), press = rowPress(row, onTap)) {
                                Row(Modifier.fillMaxSize().padding(start = if (row.done != null) 6.dp else 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val marked = bars?.marks != false || row.done != null
                                    if (marked) RowMark(i, row, role, CircleShape, 24.dp, inShape = false)
                                    RowWords(row, details, c.content, c.muted, rowStyle, Modifier.padding(start = if (marked) 10.dp else 0.dp), bars, role, c.tile)
                                }
                            }
                        }
                    }
                }
            }
            WidgetSizes.Box.WIDE -> {
                val fit = ((maxHeight + gap) / (36.dp + gap)).toInt().coerceIn(1, items.size.coerceAtLeast(1))
                val more = items.size - (if (lead != null) 0 else fit)
                val head = min(maxWidth * 0.32f, 120.dp)
                // The rows' tiles are what the title's tile leaves. A row's words start 8 dp in, after its mark; its end has 14 dp.
                val bars = if (lead != null) null else lookBars(items.take(fit), maxWidth - head - gap - 14.dp, start = 8.dp, checked = 34.dp, side = 22.dp, gap = 8.dp, rowStyle, inShape = false, details = false)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(SplitCorners.inRow(0, 2), c.tile, Modifier.fillMaxHeight().width(head)) {
                        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp)) {
                            Text(title.orEmpty(), style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (more > 0) Text("+$more more", style = type.labelSmall, color = c.muted, maxLines = 1)
                        }
                    }
                    if (lead != null) {
                        Tile(SplitCorners.inRow(1, 2), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                            LeadWords(lead, type.headlineSmall, c.muted, Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
                        }
                    } else {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            items.take(fit).forEachIndexed { i, row ->
                                key(i) {
                                    Tile(SplitCorners.tile(false, i == 0, i == fit - 1, false), c.tile, Modifier.fillMaxWidth().weight(1f), press = rowPress(row, onTap)) {
                                        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            val marked = bars?.marks != false || row.done != null
                                            if (marked) RowMark(i, row, role, CircleShape, 22.dp, inShape = false)
                                            RowWords(row, false, c.content, c.muted, rowStyle, Modifier.padding(start = if (marked) 8.dp else 0.dp), bars, role, c.tile)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            WidgetSizes.Box.SMALL -> {
                if (lead != null) {
                    Tile(SplitCorners.inColumn(0, 1), c.tile, Modifier.fillMaxSize()) {
                        BigText(WidgetWords.joined(lead.value, lead.unit), type.titleLarge, Modifier.align(Alignment.Center).padding(horizontal = 8.dp))
                    }
                } else {
                    val fit = ((maxHeight + gap) / (32.dp + gap)).toInt().coerceIn(1, items.size)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        items.take(fit).forEachIndexed { i, row ->
                            key(i) {
                                Tile(SplitCorners.inColumn(i, fit), c.tile, Modifier.fillMaxWidth().weight(1f), press = rowPress(row, onTap)) {
                                    Text(cardText(row.title), style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shape's list: the title with "+3 more" (in a large card), then as many rows as fit, each with
 * its rank in a small shape: a cookie, a clover, a sun, in turn. A list's lead is in a cookie.
 * Rows with a range have a bar before their end text ([RangeBars]) where they have room: the
 * shapes give way first.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeRows(spec: WidgetSpec.Rows, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val title = LocalUntitled.current
    val lead = spec.lead
    val items = spec.items
    val large = box == WidgetSizes.Box.LARGE
    val rowStyle = type.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight = when (box) {
            WidgetSizes.Box.LARGE -> 44.dp
            WidgetSizes.Box.WIDE -> 36.dp
            WidgetSizes.Box.SMALL -> 30.dp
        }
        val mark = when (box) {
            WidgetSizes.Box.LARGE -> 32.dp
            WidgetSizes.Box.WIDE -> 28.dp
            WidgetSizes.Box.SMALL -> 22.dp
        }
        val head = if (large) 30.dp else 0.dp
        // A small or a wide card with a lead has room for the lead alone.
        val leadRoom = if (lead == null) 0.dp else if (large) 64.dp else maxHeight
        val fit = ((maxHeight - head - leadRoom) / rowHeight).toInt().coerceIn(0, items.size)
        val more = items.size - fit
        val details = large && maxWidth >= 200.dp
        val leadHeight = minOf(leadRoom, maxHeight)
        val leadSide = minOf(leadHeight, 64.dp)
        val style = if (box == WidgetSizes.Box.SMALL) type.bodySmall.copy(fontWeight = FontWeight.SemiBold) else rowStyle
        // A row's words start 10 dp after its shape, or after its checkbox.
        val bars = lookBars(items.take(fit), maxWidth, start = 0.dp, checked = 28.dp, side = mark, gap = 10.dp, style, inShape = true, details = details)
        Column(Modifier.fillMaxSize()) {
            if (large) {
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title.orEmpty(), style = type.labelLarge.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (more > 0) Text("+$more more", style = type.labelMedium, color = c.muted, maxLines = 1, modifier = Modifier.opensList().padding(start = 8.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
            if (lead != null) {
                Row(Modifier.fillMaxWidth().height(leadHeight), verticalAlignment = Alignment.CenterVertically) {
                    ShapedBox(MaterialShapes.Cookie12Sided, c.shapeFill, leadSide) { BigText(lead.value, shapeValueStyle(leadSide), Modifier.padding(horizontal = leadSide / 7)) }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        lead.unit?.let { Text(it, style = type.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1) }
                        lead.label?.let { Text(it, style = type.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            items.take(fit).forEachIndexed { i, row ->
                key(i) {
                    Row(Modifier.fillMaxWidth().height(rowHeight).then(rowPress(row, onTap)), verticalAlignment = Alignment.CenterVertically) {
                        val marked = bars?.marks != false || row.done != null
                        if (marked) RowMark(i, row, role, rankPolygon(i).toShape(), mark, inShape = true)
                        RowWords(row, details, c.content, c.muted, style, Modifier.padding(start = if (marked) 10.dp else 0.dp), bars, role, c.card)
                    }
                }
            }
        }
    }
}

// ---- progress --------------------------------------------------------------------------------

/** `progress` in Bold, Split or Shape ([ProgressBody] draws Plain and Tonal). */
@Composable
internal fun ProgressLook(spec: WidgetSpec.Progress, box: WidgetSizes.Box, look: WidgetLook, colour: Color) {
    when {
        spec.items.isNotEmpty() -> when (look) {
            WidgetLook.BOLD -> BoldBars(spec, box, colour)
            WidgetLook.SPLIT -> SplitBars(spec, colour)
            else -> ShapeBars(spec, colour)
        }
        look == WidgetLook.BOLD -> BoldProgress(spec, box)
        look == WidgetLook.SPLIT -> SplitProgress(spec, box, colour)
        LocalCardShaped.current -> ShapedProgress(spec, box, colour)
        else -> ShapeBesideProgress(spec, colour)
    }
}

/**
 * Bold's progress: the card fills from the bottom to the level (the frame draws that), the title
 * small at the top, the number as big as it fits with "%" smaller, and the label under it.
 */
@Composable
private fun BoldProgress(spec: WidgetSpec.Progress, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.BOLD, roleColour(spec.base))
    val title = LocalUntitled.current
    val (number, unit) = splitPercent(spec.text ?: WidgetWords.percent(spec.value, spec.max))
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            SmallTitle(title, c.content)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { BoldValue(number, unit, 92.sp, c.content) }
            spec.label?.let { RingLabel(it, spec.labelIcon, type.labelLarge.copy(fontWeight = FontWeight.SemiBold), c.content, Modifier.padding(top = 8.dp)) }
        }
        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) { BoldValue(number, unit, 64.sp, c.content) }
            Column(Modifier.padding(start = 12.dp).widthIn(max = 120.dp), horizontalAlignment = Alignment.End) {
                SmallTitle(title, c.content)
                spec.label?.let { RingLabel(it, spec.labelIcon, type.labelMedium, c.content) }
            }
        }
        WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { BoldValue(number, unit, 48.sp, c.content) }
            spec.label?.let { Text(it, style = type.labelSmall, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Bold's bars: the title small, then each bar with its label and its text in Bold's type over it, as many as fit. */
@Composable
private fun BoldBars(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.BOLD, colour)
    val title = LocalUntitled.current.takeIf { box == WidgetSizes.Box.LARGE }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = ((maxHeight - if (title != null) 20.dp else 0.dp) / 30.dp).toInt().coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            SmallTitle(title, c.content)
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    val tint = bar.color?.let { colourOf(it) } ?: colour
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(bar.label.orEmpty(), style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            BoldText(bar.text ?: WidgetWords.percent(bar.value, bar.max), 18.sp, tint, Modifier.padding(start = 8.dp))
                        }
                        LevelBar(WidgetWords.fraction(bar.value, bar.max), spec.moving, tint, Modifier.padding(top = 2.dp))
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
private fun SplitProgress(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, colour)
    val title = LocalUntitled.current
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val level: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        Tile(shape, c.tile, modifier) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fraction).background(roleContainer(colour)))
            BigText(text, if (box == WidgetSizes.Box.LARGE) type.headlineMedium else type.titleLarge, Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 8.dp, end = 8.dp))
            spec.labelIcon?.let { Icon(painterResource(it), contentDescription = null, tint = c.content, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).size(20.dp)) }
        }
    }
    if (box == WidgetSizes.Box.SMALL) {
        level(Modifier.fillMaxSize(), SplitCorners.inRow(0, 1))
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(SplitCorners.GAP)) {
        level(Modifier.weight(1f).fillMaxHeight(), SplitCorners.inRow(0, 2))
        Tile(SplitCorners.inRow(1, 2), c.tile, Modifier.weight(if (box == WidgetSizes.Box.LARGE) 1f else 1.4f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                SmallTitle(title, c.content, lines = 2)
                Spacer(Modifier.weight(1f))
                spec.label?.let { Text(it, style = type.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Split's bars: a tile for each bar, side by side, each filled from the bottom to its level, with its percent at the top and its label at the bottom. */
@Composable
private fun SplitBars(spec: WidgetSpec.Progress, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, colour)
    val gap = SplitCorners.GAP
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = ((maxWidth + gap) / (56.dp + gap)).toInt().coerceIn(1, spec.items.size)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    val tint = bar.color?.let { colourOf(it) } ?: colour
                    Tile(SplitCorners.inRow(i, count), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(WidgetWords.fraction(bar.value, bar.max)).background(roleContainer(tint)))
                        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BigText(WidgetWords.percent(bar.value, bar.max), type.titleMedium)
                            Spacer(Modifier.weight(1f))
                            bar.label?.let { Text(it, style = type.labelSmall, color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
        }
    }
}

/** On a fill that reaches past [fraction] of the way up, text goes in the colour that reads on the fill. */
@Composable
private fun onLevel(fraction: Float, colours: LookColors): Color = if (fraction >= 0.5f) colours.onAccent else colours.content

/** Shape's progress in a card that is a clover: the clover fills from the bottom to the level, with the label above and the value on the fill. */
@Composable
private fun ShapedProgress(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, colour)
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val small = box == WidgetSizes.Box.SMALL
    val line = spec.label ?: LocalUntitled.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fraction).background(colour))
        if (!small) line?.let {
            Text(it, style = type.labelLarge.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.TopCenter).padding(top = side * 0.15f, start = side * 0.22f, end = side * 0.22f))
        }
        // A little under the middle, where the fill reaches at half.
        BigText(text, if (small) type.titleMedium else type.displaySmall, Modifier.align(Alignment.Center).padding(top = side * 0.12f, start = side * 0.18f, end = side * 0.18f), color = onLevel(fraction, c))
    }
}

/** Shape's progress in a wide card: a clover filled to the level with the value on it, and beside it the title over the label. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideProgress(spec: WidgetSpec.Progress, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, colour)
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    val title = LocalUntitled.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxHeight, 140.dp, maxWidth / 2)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ShapedLevel(MaterialShapes.Clover4Leaf, fraction, colour, side) {
                BigText(text, shapeValueStyle(side, 0.25f), Modifier.align(Alignment.Center).padding(top = side * 0.18f, start = side / 6, end = side / 6), color = onLevel(fraction, c))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                SmallTitle(title, c.content)
                spec.label?.let { RingLabel(it, spec.labelIcon, type.bodySmall, c.muted, Modifier.padding(top = 3.dp)) }
            }
        }
    }
}

/** Shape's bars: a row for each bar, as many as fit, with a small clover filled to its level, its label, and its text. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBars(spec: WidgetSpec.Progress, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, colour)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = (maxHeight / 30.dp).toInt().coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapedLevel(MaterialShapes.Clover4Leaf, WidgetWords.fraction(bar.value, bar.max), bar.color?.let { colourOf(it) } ?: colour, 26.dp)
                        Text(bar.label.orEmpty(), style = type.bodySmall, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        Text(bar.text ?: WidgetWords.percent(bar.value, bar.max), style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

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
internal fun CountdownLook(spec: WidgetSpec.Countdown, box: WidgetSizes.Box, look: WidgetLook, count: State<WidgetWords.Count>, bar: Float?, moment: String?, ends: Pair<String, String>?, now: LongState) {
    when {
        look == WidgetLook.BOLD -> BoldCountdown(spec, box, count, bar, moment)
        look == WidgetLook.SPLIT -> SplitCountdown(spec, box, count, bar, moment, now)
        LocalCardShaped.current -> ShapedCountdown(spec, box, count, bar)
        else -> ShapeBesideCountdown(spec, count, bar, moment, ends)
    }
}

/** The time left in Bold's type, [size] at most, in [colour], its unit smaller after it. Only this is drawn again at a tick. */
@Composable
private fun BoldCount(count: State<WidgetWords.Count>, size: TextUnit, colour: Color) {
    val left = count.value
    BoldValue(StyledText.capitalised(left.value), left.unit, size, colour)
}

/** The time left as a value in [style], its unit in [unitStyle] after it, or under it when [stacked]. Only this is drawn again at a tick. */
@Composable
private fun CountWords(count: State<WidgetWords.Count>, style: TextStyle, unitStyle: TextStyle, unitColour: Color, stacked: Boolean = false, modifier: Modifier = Modifier) {
    val left = count.value
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(StyledText.capitalised(left.value), style)
            left.unit?.let { Text(it, style = unitStyle, color = unitColour, maxLines = 1, softWrap = false) }
        }
    } else {
        Row(modifier) {
            BigText(StyledText.capitalised(left.value), style, Modifier.weight(1f, fill = false).alignByBaseline())
            left.unit?.let { Text(it, style = unitStyle, color = unitColour, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp).alignByBaseline()) }
        }
    }
}

/** Bold's countdown: the title small with the moment at its end, the number as big as it fits in the role's colour, and the bar or the label under it. */
@Composable
private fun BoldCountdown(spec: WidgetSpec.Countdown, box: WidgetSizes.Box, count: State<WidgetWords.Count>, bar: Float?, moment: String?) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    val title = LocalUntitled.current
    val top: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallTitle(title, c.content, Modifier.weight(1f))
            moment?.let { Text(it, style = type.labelMedium, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp)) }
        }
    }
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            top()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { BoldCount(count, 96.sp, role) }
            if (bar != null) LevelBar(bar, moving = false, role, Modifier.padding(top = 10.dp), wavy = true)
            else spec.label?.let { Text(it, style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
        }
        WidgetSizes.Box.WIDE -> Column(Modifier.fillMaxSize()) {
            top()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { BoldCount(count, 56.sp, role) }
            bar?.let { LevelBar(it, moving = false, role, Modifier.padding(top = 4.dp), wavy = true) }
        }
        WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { BoldCount(count, 44.sp, role) }
            spec.label?.let { Text(it, style = type.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * Split's countdown: a big tile with the title, the icon and the time left, over small tiles. With
 * days left they have the hours and the minutes after the days ("07 hours", "41 minutes"); else the
 * moment and the label. A 1-row card has the moment in a tile beside the time; a small one under it.
 */
@Composable
private fun SplitCountdown(spec: WidgetSpec.Countdown, box: WidgetSizes.Box, count: State<WidgetWords.Count>, bar: Float?, moment: String?, now: LongState) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
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
                        listOf("%02d".format((left % 86_400) / 3_600) to "Hours", "%02d".format((left % 3_600) / 60) to "Minutes")
                    } else {
                        listOfNotNull(moment?.let { it to (spec.label ?: "") })
                    }
                }
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Tile(SplitCorners.inColumn(0, if (parts.isEmpty()) 1 else 2), c.tile, Modifier.fillMaxWidth().weight(1.6f)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        TitleAndIcon(title, spec.base.icon, c.content, role)
                        Spacer(Modifier.weight(1f))
                        CountWords(count, type.displaySmall.copy(fontSize = 48.sp), unitStyle, c.content)
                        bar?.let { LevelBar(it, moving = false, role, Modifier.padding(top = 6.dp), wavy = true) }
                    }
                }
                if (parts.isNotEmpty()) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    parts.forEachIndexed { i, (value, caption) ->
                        key(i) {
                            Tile(SplitCorners.tile(false, false, i == parts.lastIndex, i == 0), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                                Column(Modifier.align(Alignment.Center).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    BigText(value, type.titleLarge)
                                    if (caption.isNotEmpty()) Text(caption, style = type.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tile(SplitCorners.inRow(0, 2), c.tile, Modifier.weight(1.5f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    TitleAndIcon(title, spec.base.icon, c.content, role)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) { CountWords(count, type.headlineMedium, unitStyle, c.content) }
                    bar?.let { LevelBar(it, moving = false, role, wavy = true) }
                }
            }
            Tile(SplitCorners.inRow(1, 2), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    moment?.let { BigText(it, type.titleMedium) }
                    spec.label?.let { Text(it, style = type.labelSmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                }
            }
        }
        WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Tile(SplitCorners.inColumn(0, if (moment != null) 2 else 1), c.tile, Modifier.fillMaxWidth().weight(1.5f)) {
                CountWords(count, type.titleLarge, type.labelSmall.copy(fontWeight = FontWeight.Bold), c.content, stacked = true, modifier = Modifier.align(Alignment.Center).padding(horizontal = 6.dp))
            }
            moment?.let {
                Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth().weight(1f)) {
                    Text(it, style = type.labelSmall.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** Shape's countdown in a card that is a sun: the number, its unit, the label, and a thin ring for the wait. A small card has the number and its unit. */
@Composable
private fun ShapedCountdown(spec: WidgetSpec.Countdown, box: WidgetSizes.Box, count: State<WidgetWords.Count>, bar: Float?) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val small = box == WidgetSizes.Box.SMALL
    val line = (spec.label ?: LocalUntitled.current).takeIf { !small }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth
        bar?.let { ThinRing(it, role, c.tile, side * 0.76f, (side * 0.025f).coerceAtLeast(2.dp), Modifier.align(Alignment.Center)) }
        Column(Modifier.align(Alignment.Center).padding(horizontal = side * 0.2f), horizontalAlignment = Alignment.CenterHorizontally) {
            CountWords(count, if (small) type.titleLarge else type.displayMedium.copy(fontSize = 50.sp), (if (small) type.labelSmall else type.labelLarge).copy(fontWeight = FontWeight.Bold), c.content, stacked = true)
            line?.let { Text(it, style = type.labelSmall, color = c.content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/** Shape's countdown in a wide card: the number and its unit in a sun, and beside it the title over the bar (with its ends) or the label and the moment. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideCountdown(spec: WidgetSpec.Countdown, count: State<WidgetWords.Count>, bar: Float?, moment: String?, ends: Pair<String, String>?) {
    val type = MaterialTheme.typography
    val role = roleColour(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    BesideShape(MaterialShapes.Sunny, c.shapeFill, inShape = { side ->
        CountWords(count, shapeValueStyle(side, 0.3f), type.labelSmall.copy(fontWeight = FontWeight.Bold), c.content, stacked = true, modifier = Modifier.padding(horizontal = side / 6))
    }) {
        SmallTitle(LocalUntitled.current, c.content)
        if (bar != null) {
            LevelBar(bar, moving = false, role, Modifier.padding(top = 8.dp), wavy = true)
            ends?.let { (start, end) ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(start, style = type.bodySmall, color = c.muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(end, style = type.bodySmall, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else {
            val words = listOfNotNull(spec.label, moment).joinToString(" · ")
            if (words.isNotEmpty()) Text(words, style = type.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ---- chart -----------------------------------------------------------------------------------

/** `chart` in Bold, Split or Shape ([ChartBody] draws Plain and Tonal). */
@Composable
internal fun ChartLook(spec: WidgetSpec.Chart, box: WidgetSizes.Box, look: WidgetLook, colour: Color) {
    when {
        look == WidgetLook.BOLD -> BoldChart(spec, box, colour)
        look == WidgetLook.SPLIT -> SplitChart(spec, box, colour)
        LocalCardShaped.current -> ShapedChart(spec, box, colour)
        else -> ShapeBesideChart(spec, box, colour)
    }
}

/** From this width, a large card has a chart's value and its plot side by side. Under it, the plot goes under the value. */
internal val CHART_SIDE_FROM = 260.dp

/**
 * Bold's chart: the title small, the value as big as it fits in the role's colour with its label,
 * and the points as round pills, the latest in the role's colour. A wide large card has the pills
 * beside the value, with the first and the last label under them. A chart of dots has its dots
 * where the pills go ([Dots]): a calendar in a large card, the last seven days in the others.
 */
@Composable
private fun BoldChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.BOLD, colour)
    val title = LocalUntitled.current
    val bars = spec.style == "bars"
    val rest = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelStyle = type.labelLarge.copy(fontWeight = FontWeight.Bold)
    val plot: @Composable (List<String>, Dp, Dp, Modifier) -> Unit = { labels, thick, gap, modifier ->
        if (spec.style == "dots") Dots(spec, colour, weeks = box == WidgetSizes.Box.LARGE, modifier)
        else PillChart(spec.points, bars, spec.min, spec.max, colour, rest, labels, thick, gap, modifier)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val valueWidth = min(maxWidth * 0.36f, 130.dp)
        when {
            box == WidgetSizes.Box.LARGE && maxWidth >= CHART_SIDE_FROM -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(valueWidth).fillMaxHeight()) {
                    SmallTitle(title, c.content)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { spec.value?.let { BoldText(it, 92.sp, colour) } }
                    spec.label?.let { Text(it, style = labelStyle, color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) }
                }
                plot(spec.labels, 18.dp, 11.dp, Modifier.padding(start = 16.dp).weight(1f).fillMaxHeight())
            }
            box == WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
                SmallTitle(title, c.content)
                spec.value?.let { BoldText(it, 48.sp, colour, Modifier.padding(top = 6.dp)) }
                spec.label?.let { Text(it, style = labelStyle, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                plot(spec.labels, 14.dp, 8.dp, Modifier.padding(top = 8.dp).weight(1f).fillMaxWidth())
            }
            box == WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.45f).fillMaxHeight()) {
                    SmallTitle(title, c.content)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) { spec.value?.let { BoldText(it, 56.sp, colour) } }
                }
                plot(emptyList(), 12.dp, 6.dp, Modifier.padding(start = 12.dp).weight(0.55f).fillMaxHeight())
            }
            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                spec.value?.let { BoldText(it, 32.sp, colour) }
                plot(emptyList(), 8.dp, 4.dp, Modifier.padding(top = 4.dp).weight(1f).fillMaxWidth())
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
private fun SplitChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, colour)
    val title = LocalUntitled.current
    val bars = spec.style == "bars"
    val dots = spec.style == "dots"
    val gap = SplitCorners.GAP
    val small = box == WidgetSizes.Box.SMALL
    val valueTile: @Composable (Modifier, Shape, TextStyle) -> Unit = { modifier, shape, style ->
        Tile(shape, c.tile, modifier) {
            Column(Modifier.fillMaxSize().padding(horizontal = if (small) 8.dp else 14.dp, vertical = if (box == WidgetSizes.Box.LARGE) 10.dp else 8.dp), verticalArrangement = if (small) Arrangement.Center else Arrangement.Top, horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start) {
                if (!small) {
                    Text(title.orEmpty(), style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                }
                spec.value?.let { BigText(it, style) }
                if (!small) spec.label?.let { Text(it, style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    val plotTile: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        Tile(shape, c.tile, modifier) {
            Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = if (small) 8.dp else 16.dp, bottom = if (small) 8.dp else 10.dp)) {
                if (dots) Dots(spec, colour, weeks = box == WidgetSizes.Box.LARGE, Modifier.weight(1f).fillMaxWidth())
                else Plot(spec.points, bars, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxWidth(), surface = c.tile)
                // Dots have the days of the week instead.
                if (!dots && box == WidgetSizes.Box.LARGE && spec.labels.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(spec.labels.first(), style = type.labelSmall, color = c.muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (spec.labels.size > 1) Text(spec.labels.last(), style = type.labelSmall, color = c.muted, maxLines = 1)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val valueWidth = min(maxWidth * 0.36f, 124.dp)
        when {
            box == WidgetSizes.Box.LARGE && maxWidth >= CHART_SIDE_FROM -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                valueTile(Modifier.width(valueWidth).fillMaxHeight(), SplitCorners.inRow(0, 2), type.displaySmall.copy(fontSize = 52.sp))
                plotTile(Modifier.weight(1f).fillMaxHeight(), SplitCorners.inRow(1, 2))
            }
            box == WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                valueTile(Modifier.fillMaxWidth().weight(1f), SplitCorners.inColumn(0, 2), type.headlineMedium)
                plotTile(Modifier.fillMaxWidth().weight(1.1f), SplitCorners.inColumn(1, 2))
            }
            box == WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                valueTile(Modifier.weight(1f).fillMaxHeight(), SplitCorners.inRow(0, 2), type.headlineSmall)
                plotTile(Modifier.weight(1.2f).fillMaxHeight(), SplitCorners.inRow(1, 2))
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                valueTile(Modifier.fillMaxWidth().weight(1f), SplitCorners.inColumn(0, 2), type.titleLarge)
                plotTile(Modifier.fillMaxWidth().weight(1f), SplitCorners.inColumn(1, 2))
            }
        }
    }
}

/**
 * Shape's chart in a card that is a cookie: the value and its label. A chart without a value has
 * its plot in the cookie, or its dots ([Dots]).
 */
@Composable
private fun ShapedChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, colour)
    val small = box == WidgetSizes.Box.SMALL
    val value = spec.value
    ShapeLines {
        if (value != null) {
            BigText(value, if (small) type.titleLarge else type.displayMedium.copy(fontSize = 46.sp))
            if (!small) spec.label?.let { Text(it, style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        } else if (spec.style == "dots") {
            Dots(spec, colour, weeks = !small, Modifier.weight(1f).fillMaxWidth())
        } else {
            Plot(spec.points, spec.style == "bars", spec.min, spec.max, colour, Modifier.weight(1f).fillMaxWidth(), surface = c.shapeFill)
        }
    }
}

/**
 * Shape's chart in a wide card: the value in a cookie (with its label in a large one), and beside
 * it the points: round pills for bars, the latest in the role's colour, or the line, or dots
 * ([Dots]): a calendar in a large card, the last seven days in a 1-row one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideChart(spec: WidgetSpec.Chart, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, colour)
    val large = box == WidgetSizes.Box.LARGE
    BesideShape(MaterialShapes.Cookie12Sided, c.shapeFill, inShape = { side ->
        Column(Modifier.padding(horizontal = side / 7), horizontalAlignment = Alignment.CenterHorizontally) {
            spec.value?.let { BigText(it, shapeValueStyle(side)) }
            if (large) spec.label?.let { Text(it, style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
        }
    }) {
        if (!large) (spec.label ?: LocalUntitled.current)?.let { Text(it, style = type.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp)) }
        if (spec.style == "bars") {
            PillChart(spec.points, true, spec.min, spec.max, colour, MaterialTheme.colorScheme.surfaceContainerHighest, if (large) spec.labels else emptyList(), 14.dp, 8.dp, Modifier.weight(1f).fillMaxWidth())
        } else if (spec.style == "dots") {
            Dots(spec, colour, weeks = large, Modifier.weight(1f).fillMaxWidth())
        } else {
            Plot(spec.points, false, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxWidth(), surface = c.card)
            if (large && spec.labels.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(spec.labels.first(), style = type.labelSmall, color = c.muted, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (spec.labels.size > 1) Text(spec.labels.last(), style = type.labelSmall, color = c.muted, maxLines = 1)
            }
        }
    }
}
