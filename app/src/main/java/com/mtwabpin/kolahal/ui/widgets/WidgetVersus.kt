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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.ui.widgets.VersusWords.Face
import com.mtwabpin.kolahal.ui.widgets.VersusWords.Part
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

// ---- versus ---------------------------------------------------------------------------------

/** The tags of a side's mark, name and score, for tests: "versus-mark-0", "versus-name-1". */
internal fun versusTag(part: String, side: Int) = "versus-$part-$side"

/**
 * `versus`: two sides, one row each, like a scoreboard: the side's mark (a letter or two on a
 * Material shape, a cookie for the first side and a clover for the second, in the side's color),
 * its name with a dot when it is its turn, a small figure (`text`, or its clock), and its score at
 * the row's end ([VersusWords.figures]).
 * - The rows share their columns ([VersusGrid]): both sides are measured once, so marks, names,
 *   figures and scores sit on the same lines. Both scores have one size: the largest that fits the
 *   wider ([VersusWords.scoreSize]).
 * - A score that doesn't split ("W/O") is drawn whole ([VersusWords.whole]): in the status column
 *   at 4×1, the header at 4×2, over the rows at 2×2.
 * - 1×1: the marks and the scores. 2×1: the rows. 4×1: the rows and a status column at the end
 *   with the badge, the caption, the script's footer and the card's age; gone when it is empty.
 *   2×2 and 4×2: a header (the title, the badge), the rows with each side's detail under its name
 *   (2×2: its small figure too; 4×2: its lines), and the caption at the foot ([VersusWords.Face]).
 * - What has no room gives way, in [VersusWords.Part]'s order. A screen reader says it all.
 * - With both values, a thin bar of two parts under the rows; with `moving`, the turn's part is a
 *   wave that moves, and the turn's clock counts down ([ClockTicks]).
 * - Bold has the scores in Bold's type, in the card's color. Split has its parts on tiles of their
 *   own. Tonal draws the marks on the page's color: the card is the accent's container. There is
 *   no Shape look ([WidgetLook.offeredFor]): the marks are shapes in every look.
 */
@Composable
internal fun VersusBody(spec: WidgetSpec.Versus, box: WidgetSizes.Box) {
    val look = LocalWidgetLook.current
    val roles = spec.sides.mapIndexed { i, side -> colorOf(VersusWords.role(side, i)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val v = Versus(spec, roles, look, VersusWords.face(box, maxWidth.value), maxWidth, maxHeight)
        when (v.face) {
            Face.SMALL, Face.NARROW -> OneTile(v)
            Face.ROW -> RowVersus(v)
            Face.SQUARE, Face.LARGE -> LargeVersus(v)
        }
    }
}

/** What every part of a versus card draws with: the card, its look and face, and its room. */
private class Versus(
    val spec: WidgetSpec.Versus,
    val roles: List<Color>,
    val look: WidgetLook,
    val face: Face,
    val width: Dp,
    val height: Dp,
) {
    val split get() = look == WidgetLook.SPLIT
    val bold get() = look == WidgetLook.BOLD
    val whole = VersusWords.whole(spec)
    val figures = spec.sides.indices.map { VersusWords.figures(spec, it) }
    val bar = VersusWords.shares(spec) != null && face != Face.SMALL && face != Face.NARROW

    /** A tile's room inside, in Split, in dp: across and down. */
    val tileX = if (split) 10 else 0
    val tileY = if (split) 8 else 0

    /** The room between the zones: Split's gap between tiles, else a little air. */
    val zoneGap: Dp get() = if (split) SplitCorners.GAP else 6.dp

    val markDp = when (face) {
        Face.SMALL -> 20
        Face.NARROW, Face.ROW -> 24
        Face.SQUARE -> 28
        Face.LARGE -> 36
    }
    val nameGap = if (face == Face.LARGE) 10 else 8
    val rowGap = when (face) {
        Face.SQUARE -> 8
        Face.LARGE -> 10
        else -> 6
    }

    /** The scores' sizes, the largest first (sp): as a value, or in Bold's type. */
    val sizes: List<Int> = when (face) {
        Face.SMALL -> if (bold) listOf(24, 22, 20) else listOf(20, 18, 16)
        Face.NARROW, Face.ROW -> if (bold) listOf(28, 26, 24) else listOf(22, 20, 18)
        Face.SQUARE -> if (bold) listOf(32, 28, 24, 22) else listOf(24, 22, 20, 18)
        Face.LARGE -> if (bold) listOf(40, 36, 32) else listOf(28, 26, 24)
    }
}

/**
 * The scores' types at every size [VersusWords.scoreSize] may pick for [v], made here: the measure
 * that picks one is no composable.
 */
@Composable
private fun scoreTypes(v: Versus): Map<Int, TextStyle> =
    (v.sizes + (v.sizes.last() - 2 downTo 12 step 2)).associateWith { scoreStyle(v.bold, it) }

/** How tall a line of [style] is, in dp: its line height, or its size where it has none. */
private fun androidx.compose.ui.unit.Density.lineDp(style: TextStyle): Float =
    (style.lineHeight.takeIf { it.isSp } ?: style.fontSize).toDp().value

/** A score's type at [size] sp: Bold's, or a value's with a line a little taller than the type. */
@Composable
private fun scoreStyle(bold: Boolean, size: Int): TextStyle =
    if (bold) boldValueStyle(size.sp)
    else MaterialTheme.typography.headlineSmall.asValue().copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp)

/** A side's name: bold, a size larger in a 4×2. */
@Composable
private fun nameStyle(face: Face): TextStyle =
    (if (face == Face.LARGE) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
        .copy(fontWeight = FontWeight.Bold)

/** A side's small figure: digits of one width, so a clock that ticks does not shake. */
@Composable
private fun figureStyle(face: Face): TextStyle =
    (if (face == Face.LARGE) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    else MaterialTheme.typography.labelMedium).copy(fontFeatureSettings = "tnum")

/**
 * The plan of a card: the scores' [size] and the [kept] parts ([VersusWords.keep]), with a 4×1's
 * status column [statusDp] wide (0 without one) and the caption's [captionLines] there.
 */
private data class Plan(
    val size: Int,
    val kept: Set<Part>,
    val statusDp: Float = 0f,
    val captionLines: Int = 0,
    /** At 2×2, the sides whose names take two lines while [Part.NAMES] is kept. */
    val wrap: Set<Int> = emptySet(),
)

/** How wide [text] is in [style], on one line, in dp. */
private class Widths(val measure: (AnnotatedString, TextStyle) -> Float) {
    fun of(text: String?, style: TextStyle): Float = text?.let { measure(AnnotatedString(it), style) } ?: 0f
}

@Composable
private fun rememberWidths(): Widths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        Widths { text, style ->
            with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp().value }
        }
    }
}

/** The widest name, with the turn's dot after it, in dp. */
private fun Versus.nameDp(widths: Widths, style: TextStyle): Float {
    val turn = VersusWords.turnIndex(spec)
    return spec.sides.withIndex().maxOfOrNull { (i, s) -> widths.of(s.name, style) + if (turn == i) DOT_ROOM else 0f } ?: 0f
}

/** Room for the turn's dot after a name, with its gap, in dp. */
private const val DOT_ROOM = 13f

/** The wider score at [size], in dp. */
private fun Versus.scoreDp(widths: Widths, style: TextStyle): Float =
    figures.maxOfOrNull { widths.of(it.big, style) } ?: 0f

// ---- 1×1 and 2×1 ------------------------------------------------------------------------------

/** A 1×1 (the marks and the scores) or a 2×1 (the rows): one zone, one tile in Split. */
@Composable
private fun OneTile(v: Versus) {
    val widths = rememberWidths()
    val density = LocalDensity.current
    val names = nameStyle(v.face)
    val types = scoreTypes(v)
    val wholeStyle = scoreStyle(v.bold, if (v.face == Face.SMALL) 18 else 20)
    val plan = remember(v.spec, v.face, v.width, v.height, density, widths, types) {
        val row = v.width.value - 2 * v.tileX
        val high = v.height.value - 2 * v.tileY
        val nameDp = if (v.face == Face.NARROW) minOf(60f, v.nameDp(widths, names)) else 0f
        val status = if (v.face == Face.NARROW && v.whole != null) widths.of(v.whole, wholeStyle) + VersusWords.STATUS_EDGE else 0f
        val size = VersusWords.scoreSize(v.sizes) { s ->
            val type = types.getValue(s)
            val line = with(density) { lineDp(type) }
            v.scoreDp(widths, type) <= row - status - v.markDp - v.nameGap - nameDp - 12 && 2 * line + 2 <= high
        }
        Plan(size, emptySet(), status)
    }
    Zone(v, SplitCorners.inColumn(0, 1), Modifier.fillMaxSize()) {
        if (v.face == Face.SMALL && v.whole != null) {
            // A score that doesn't split, over the two marks.
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                BigText(v.whole, MaterialTheme.typography.titleLarge)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mark(v, 0)
                    Mark(v, 1)
                }
            }
        } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            VersusGrid(v, plan, Modifier.weight(1f).fillMaxHeight())
            // A 2×1's score that doesn't split, at the end, as a 4×1's status column has it.
            if (v.face == Face.NARROW && v.whole != null) {
                Spacer(Modifier.width(VersusWords.STATUS_EDGE.dp))
                Text(v.whole, style = wholeStyle, color = scoreColor(v), maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---- 4×1 --------------------------------------------------------------------------------------

/**
 * A 4×1: the rows, and at the end a status column with what the card says of the match: a whole
 * score, the badge, the caption on up to two lines, the script's footer (Plain and Tonal, as on
 * every card's foot line) and the card's age. It is as wide as what it holds, and gone when it is
 * empty. The caption gives way where the column has no height for it, and the rows' small figures
 * where the names would be cut ([VersusWords.keep]).
 */
@Composable
private fun RowVersus(v: Versus) {
    val type = MaterialTheme.typography
    val widths = rememberWidths()
    val density = LocalDensity.current
    val age = LocalCardFooter.current
    val footer = v.spec.base.footer.takeIf { !v.bold && !v.split }
    val names = nameStyle(v.face)
    val figs = figureStyle(v.face)
    val small = type.labelSmall
    val badgeStyle = type.labelMediumEmphasized
    val wholeStyle = scoreStyle(v.bold, 18)
    val types = scoreTypes(v)
    val smallDp = linesDp(small)
    val badgeDp = linesDp(badgeStyle) + 2
    val wholeDp = linesDp(wholeStyle)
    val plan = remember(v.spec, v.width, v.height, age, footer, density, widths, types) {
        val high = v.height.value - 2 * v.tileY
        val present = VersusWords.present(v.spec, v.face)
        val nameDp = v.nameDp(widths, names)
        val figureDp = v.figures.maxOfOrNull { widths.of(it.small, figs) } ?: 0f
        val ageDp = maxOf(widths.of(age?.text, small), widths.of(footer, small))
        // What the status column holds with [kept], and the rows' room beside it.
        fun status(kept: Set<Part>): Pair<Float, Float> {
            val holds = v.whole != null || age != null || footer != null ||
                    (Part.BADGE in kept && v.spec.badge != null) || (Part.CAPTION in kept && v.spec.line != null)
            if (!holds) return 0f to v.width.value - 2 * v.tileX
            val dp = VersusWords.statusDp(
                if (Part.BADGE in kept) widths.of(v.spec.badge, badgeStyle) + 16 else 0f,
                ageDp,
                if (Part.CAPTION in kept) widths.of(v.spec.line?.let(StyledText::plain), small) else 0f,
                widths.of(v.whole, wholeStyle),
            ) + if (v.split) 2 * v.tileX - VersusWords.STATUS_EDGE else 0
            return dp to v.width.value - dp - v.zoneGap.value - 2 * v.tileX
        }
        fun size(row: Float) = VersusWords.scoreSize(v.sizes) { s ->
            val t = types.getValue(s)
            val line = with(density) { lineDp(t) }
            v.scoreDp(widths, t) <= minOf(90f, row * 0.34f) && 2 * line + 2 + (if (v.bar) BAR_DP else 0) <= high
        }
        fun captionLines(kept: Set<Part>) = VersusWords.captionLines(
            high.toInt(), smallDp, 3, if (v.whole != null) wholeDp else 0,
            if (Part.BADGE in kept && v.spec.badge != null) badgeDp else 0,
            if (age != null || footer != null) smallDp else 0,
        )
        val kept = VersusWords.keep(present) { kept ->
            val (_, row) = status(kept)
            val score = v.scoreDp(widths, types.getValue(size(row)))
            (Part.CAPTION !in kept || captionLines(kept) > 0) &&
                    (Part.TEXT !in kept || VersusWords.figureFits(row, (v.markDp + v.nameGap).toFloat(), nameDp, figureDp + 10, score + 12)) &&
                    (Part.BADGE !in kept || badgeDp <= high)
        }
        val (statusDp, row) = status(kept)
        Plan(size(row), kept, statusDp, captionLines(kept))
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(v.zoneGap)) {
        val status = plan.statusDp > 0f
        Zone(v, SplitCorners.inRow(0, if (status) 2 else 1), Modifier.weight(1f).fillMaxHeight()) {
            VersusGrid(v, plan, Modifier.fillMaxSize())
        }
        if (status) Zone(v, SplitCorners.inRow(1, 2), Modifier.width(plan.statusDp.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxSize()) {
                // A rule at the column's start, but in Split, whose tile is the column.
                if (!v.split) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(ruleColor(v.look)))
                    Spacer(Modifier.width((VersusWords.STATUS_EDGE - 1).dp))
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    val c = lookColors(v.look, roleColor(v.spec.base))
                    v.whole?.let { Text(it, style = wholeStyle, color = scoreColor(v), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    v.spec.badge?.takeIf { Part.BADGE in plan.kept }?.let { CardBadge(it, roleColor(v.spec.base)) }
                    v.spec.line?.takeIf { Part.CAPTION in plan.kept && plan.captionLines > 0 }?.let {
                        Text(cardText(it), style = small, color = c.muted, maxLines = plan.captionLines, overflow = TextOverflow.Ellipsis)
                    }
                    if (footer != null) FooterRow(null, footer, age, c.muted, end = false, style = small)
                    else age?.let { FooterLine(it, color = c.muted, style = small) }
                }
            }
        }
    }
}

/** The bar under the rows with the room over it, in dp: a wave's container and 4 dp. */
private const val BAR_DP = 14

/** The rule at a 4×1's status column's start: the outline's quiet color, or the text's on Tonal's tinted card. */
@Composable
private fun ruleColor(look: WidgetLook): Color =
    if (look == WidgetLook.TONAL) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    else MaterialTheme.colorScheme.outlineVariant

// ---- 2×2 and 4×2 ------------------------------------------------------------------------------

/**
 * A 2×2 or a 4×2: a header with the title and the badge (and at 4×2 a whole score), the rows, and
 * the caption at the foot. A 2×2 name too long for its line wraps onto a second where the height
 * has room to spare, and is cut only after that. Under each name: at 2×2 its small figure and its
 * detail on one line; at 4×2 its detail, then its lines on one line, joined with " · ", and the
 * small figures have a column. A Bold or Split card writes its own age at the foot: after the caption at 4×2, under it
 * at 2×2. What doesn't fit the height gives way ([VersusWords.largeHeight]).
 */
@Composable
private fun LargeVersus(v: Versus) {
    val type = MaterialTheme.typography
    val widths = rememberWidths()
    val density = LocalDensity.current
    val age = LocalCardFooter.current
    val title = LocalUntitled.current
    val names = nameStyle(v.face)
    val c = lookColors(v.look, roleColor(v.spec.base))
    val headerStyle = if (v.bold || v.split) type.labelMedium else type.labelLargeEmphasized
    val captionStyle = type.labelMedium
    val wholeStyle = scoreStyle(v.bold, if (v.face == Face.LARGE) 18 else 20)
    val types = scoreTypes(v)
    val figs = figureStyle(v.face)
    val heights = VersusWords.Heights(
        header = maxOf(linesDp(headerStyle), linesDp(type.labelMediumEmphasized) + 2),
        line = maxOf(v.markDp, linesDp(names)),
        sub = linesDp(type.labelSmall),
        rowGap = v.rowGap,
        caption = linesDp(captionStyle),
        age = if (age != null) linesDp(type.labelSmall) else 0,
        bar = if (v.bar) BAR_DP else 0,
        whole = linesDp(wholeStyle) + 4,
        zonePad = 2 * v.tileY,
        zoneGap = v.zoneGap.value.toInt(),
    )
    val nameLine = linesDp(names)
    val plan = remember(v.spec, v.width, v.height, title, age, density, widths, heights, types, figs, nameLine) {
        val row = v.width.value - 2 * v.tileX
        val nameDp = minOf(72f, v.nameDp(widths, names))
        val room = if (v.face == Face.LARGE) 110f else row - v.markDp - v.nameGap - 12 - nameDp
        val size = VersusWords.scoreSize(v.sizes) { v.scoreDp(widths, types.getValue(it)) <= room }
        val t = types.getValue(size)
        val scoreLine = with(density) { lineDp(t) }
        val line = maxOf(heights.line, kotlin.math.ceil(scoreLine).toInt())
        // At 2×2, the names that would be cut on one line beside the scores' column take a second
        // line where the height has room for it ([Part.NAMES]).
        val turn = VersusWords.turnIndex(v.spec)
        val nameRoom = row - v.markDp - v.nameGap - v.scoreDp(widths, t) - SCORE_GAP
        val wrap = if (v.face != Face.SQUARE) emptySet() else v.spec.sides.indices.filterTo(mutableSetOf()) { i ->
            widths.of(v.spec.sides[i].name, names) + (if (turn == i) DOT_ROOM else 0f) > nameRoom
        }
        val h = heights.copy(
            line = line,
            nameExtra = v.spec.sides.indices.map { if (it in wrap) (2 * nameLine - line).coerceAtLeast(0) else 0 },
        )
        val figureDp = v.figures.maxOfOrNull { widths.of(it.small, figs) } ?: 0f
        val present = VersusWords.present(v.spec, v.face) + (if (wrap.isNotEmpty()) setOf(Part.NAMES) else emptySet())
        val kept = VersusWords.keep(present) { kept ->
            VersusWords.largeHeight(v.spec, v.face, kept, h, title != null) <= v.height.value &&
                    (v.face != Face.LARGE || Part.TEXT !in kept ||
                            VersusWords.figureFits(row, (v.markDp + v.nameGap).toFloat(), nameDp, figureDp + 10, v.scoreDp(widths, t) + 12))
        }
        Plan(size, kept, wrap = wrap)
    }
    val header = title != null || (Part.BADGE in plan.kept && v.spec.badge != null) || (v.face == Face.LARGE && v.whole != null)
    val caption = v.spec.line?.takeIf { Part.CAPTION in plan.kept }
    val foot = caption != null || age != null
    val zones = listOf(header, true, foot).count { it }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(v.zoneGap)) {
        var at = 0
        if (header) Zone(v, SplitCorners.inColumn(at++, zones), Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(heights.header.dp), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    if (v.bold || v.split) SmallTitle(title, c.muted, Modifier.weight(1f, fill = false))
                    else Text(title, style = headerStyle, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                if (v.face == Face.LARGE) v.whole?.let {
                    Text(it, style = wholeStyle, color = scoreColor(v), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
                }
                v.spec.badge?.takeIf { Part.BADGE in plan.kept }?.let { CardBadge(it, roleColor(v.spec.base), Modifier.padding(start = 8.dp)) }
            }
        }
        Zone(v, SplitCorners.inColumn(at++, zones), Modifier.weight(1f).fillMaxWidth()) {
            VersusGrid(v, plan, Modifier.fillMaxSize(), top = v.whole?.takeIf { v.face == Face.SQUARE }?.let { w ->
                { Text(w, style = wholeStyle, color = scoreColor(v), maxLines = 1, softWrap = false) }
            })
        }
        if (foot) Zone(v, SplitCorners.inColumn(at, zones), Modifier.fillMaxWidth()) {
            val line = @Composable { m: Modifier ->
                caption?.let { Text(cardText(it), style = captionStyle, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = m) }
            }
            if (v.face == Face.SQUARE) Column {
                line(Modifier)
                age?.let { FooterLine(it, color = c.muted) }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                line(Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                age?.let { FooterLine(it, Modifier.padding(start = 8.dp), c.muted) }
            }
        }
    }
}

// ---- the rows ---------------------------------------------------------------------------------

/** A zone of the card: in Split a tile of [shape] with its room inside, else the room as it is. */
@Composable
private fun Zone(v: Versus, shape: Shape, modifier: Modifier, content: @Composable () -> Unit) {
    if (!v.split) return Box(modifier) { content() }
    Tile(shape, lookColors(WidgetLook.SPLIT).tile, modifier) {
        Box(Modifier.padding(horizontal = v.tileX.dp, vertical = v.tileY.dp)) { content() }
    }
}

/** What a part of [VersusGrid] is, for its layout: [kind] of side [side], the [index]th of its kind. */
private data class Slot(val kind: Int, val side: Int = 0, val index: Int = 0)

private const val MARK = 0
private const val NAME = 1
private const val FIGURE = 2
private const val SCORE = 3
private const val SUB = 4
private const val TOP = 5
private const val BAR = 6

/**
 * The two rows in shared columns: the marks, the names, the small figures and the scores, at the
 * row's end. One layout measures both sides' parts and gives each column the width of its wider
 * part, so the columns line up; each row's parts are centered on one line. A name is cut with "…"
 * to the width the other columns leave, but at 2×2 it first takes a second line where the height
 * has room ([Plan.wrap]), with the mark and the score centered on its two lines; marks and scores
 * are never cut. The lines under a name
 * start where the name does and reach the row's end. [top] is over the rows (a 2×2's whole
 * score), the bar under them; the whole is centered in the height. A right-to-left phone mirrors it.
 */
@Composable
private fun VersusGrid(v: Versus, plan: Plan, modifier: Modifier, top: (@Composable () -> Unit)? = null) {
    val type = MaterialTheme.typography
    val c = lookColors(v.look, roleColor(v.spec.base))
    val style = scoreStyle(v.bold, plan.size)
    val turn = VersusWords.turnIndex(v.spec)
    val names = v.face != Face.SMALL
    val figures = Part.TEXT in plan.kept && (v.face == Face.ROW || v.face == Face.LARGE)
    Layout(
        modifier = modifier,
        content = {
            top?.let { Box(Modifier.layoutId(Slot(TOP))) { it() } }
            for (i in v.spec.sides.indices) {
                val side = v.spec.sides[i]
                val f = v.figures[i]
                val runs = VersusWords.clockRuns(v.spec, i)
                Box(Modifier.layoutId(Slot(MARK, i)).testTag(versusTag("mark", i))) { Mark(v, i) }
                if (names) Row(Modifier.layoutId(Slot(NAME, i)).testTag(versusTag("name", i)), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        side.name,
                        style = nameStyle(v.face),
                        color = c.content,
                        maxLines = if (i in plan.wrap && Part.NAMES in plan.kept) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (turn == i) Box(Modifier.padding(start = 6.dp).size(7.dp).background(v.roles[i], CircleShape))
                }
                if (figures) f.small?.let { small ->
                    Box(Modifier.layoutId(Slot(FIGURE, i))) {
                        Ticking(small, side.clock.takeIf { f.smallClock }, runs) {
                            Text(it, style = figureStyle(v.face), color = c.muted, maxLines = 1, softWrap = false)
                        }
                    }
                }
                f.big?.let { big ->
                    Box(Modifier.layoutId(Slot(SCORE, i)).testTag(versusTag("score", i))) {
                        Ticking(big, side.clock.takeIf { f.bigClock }, runs) {
                            Text(it, style = style, color = scoreColor(v, i), maxLines = 1, softWrap = false)
                        }
                    }
                }
                // The lines under the name.
                val subs = when (v.face) {
                    Face.SQUARE -> listOfNotNull(
                        listOfNotNull(
                            f.small?.takeIf { Part.TEXT in plan.kept },
                            side.detail?.takeIf { Part.DETAIL in plan.kept }
                        ).takeIf { it.isNotEmpty() }
                    )
                    Face.LARGE -> listOfNotNull(
                        side.detail?.takeIf { Part.DETAIL in plan.kept }?.let(::listOf),
                        side.lines.takeIf { Part.LINES in plan.kept && it.isNotEmpty() },
                    )
                    else -> emptyList()
                }
                subs.forEachIndexed { k, parts ->
                    Box(Modifier.layoutId(Slot(SUB, i, k))) {
                        // A 2×2's small figure leads its line: a clock there counts down too.
                        val clock = side.clock.takeIf { v.face == Face.SQUARE && k == 0 && f.smallClock && Part.TEXT in plan.kept }
                        Ticking(parts.first(), clock, runs) { first ->
                            Text(
                                (listOf(first) + parts.drop(1)).joinToString(" · "),
                                style = type.labelSmall,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (v.bar) Box(Modifier.layoutId(Slot(BAR))) { VersusBar(v) }
        }
    ) { parts, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val loose = Constraints(maxWidth = if (width > 0) width else Constraints.Infinity)
        fun of(kind: Int, side: Int = 0, index: Int = 0) = parts.firstOrNull { it.layoutId == Slot(kind, side, index) }
        val sides = v.spec.sides.indices
        val marks = sides.map { of(MARK, it)?.measure(loose) }
        val scores = sides.map { of(SCORE, it)?.measure(loose) }
        val figs = sides.map { of(FIGURE, it)?.measure(loose) }
        val markW = marks.maxOf { it?.width ?: 0 }
        val scoreW = scores.maxOf { it?.width ?: 0 }
        val figW = figs.maxOf { it?.width ?: 0 }
        val nameX = markW + if (names) v.nameGap.dp.roundToPx() else 0
        val scoreCol = if (scoreW > 0) scoreW + SCORE_GAP.dp.roundToPx() else 0
        val figCol = if (figW > 0) figW + FIGURE_GAP.dp.roundToPx() else 0
        val nameRoom = (width - nameX - figCol - scoreCol).coerceAtLeast(0)
        val nameList = sides.map { of(NAME, it)?.measure(Constraints(maxWidth = nameRoom)) }
        val subList = sides.map { i ->
            generateSequence(0) { it + 1 }.map { of(SUB, i, it) }.takeWhile { it != null }
                .map { it!!.measure(Constraints(maxWidth = (width - nameX).coerceAtLeast(0))) }.toList()
        }
        val topPlaced = of(TOP)?.measure(loose)
        val barPlaced = of(BAR)?.measure(Constraints.fixedWidth(width.coerceAtLeast(0)))
        val lines = sides.map { i ->
            listOfNotNull(marks[i], nameList[i], figs[i], scores[i]).maxOfOrNull { it.height } ?: 0
        }
        val rowHeights = sides.map { i -> lines[i] + subList[i].sumOf { it.height } }
        fun total(rowGap: Int, zoneGap: Int) = rowHeights.sum() + rowGap * (sides.count() - 1).coerceAtLeast(0) +
                (topPlaced?.let { it.height + zoneGap } ?: 0) + (barPlaced?.let { it.height + zoneGap } ?: 0)
        val high = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        // Where the height is short, the rows close up before anything is cut.
        var rowGap = v.rowGap.dp.roundToPx()
        var zoneGap = 4.dp.roundToPx()
        if (total(rowGap, zoneGap) > high) {
            rowGap = 2.dp.roundToPx()
            zoneGap = 2.dp.roundToPx()
        }
        val used = total(rowGap, zoneGap)
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else used
        layout(width, height) {
            var y = ((height - used) / 2).coerceAtLeast(0)
            topPlaced?.let {
                it.placeRelative((width - it.width) / 2, y)
                y += it.height + zoneGap
            }
            for (i in sides) {
                val line = lines[i]
                fun Placeable.at(x: Int) = placeRelative(x, y + (line - this.height) / 2)
                marks[i]?.at((markW - (marks[i]?.width ?: 0)) / 2)
                nameList[i]?.at(nameX)
                figs[i]?.let { it.at(width - scoreCol - it.width) }
                scores[i]?.let { it.at(width - it.width) }
                var under = y + line
                subList[i].forEach {
                    it.placeRelative(nameX, under)
                    under += it.height
                }
                y += rowHeights[i] + rowGap
            }
            barPlaced?.placeRelative(0, y - rowGap + zoneGap)
        }
    }
}

/** The room before a score and before a small figure, in dp. */
private const val SCORE_GAP = 12
private const val FIGURE_GAP = 10

/**
 * [content] with [text], or with a [clock] of seconds left that counts down when it [runs]: from
 * the moment the card's content was set ([LocalContentSetAt]), every second while the card can be
 * seen ([rememberMillis]). Only this redraws on a tick, not the card.
 */
@Composable
private fun Ticking(text: String, clock: Long?, runs: Boolean, content: @Composable (String) -> Unit) {
    val setAt = LocalContentSetAt.current
    if (clock == null || !runs || setAt <= 0L) return content(text)
    val now by rememberMillis(seconds = true)
    content(VersusWords.clockText(VersusWords.clockLeft(clock, true, setAt, now)))
}

/**
 * A score's color: the text's, or in Bold the card's color moved just far enough to read on Bold's
 * card ([valueInk]). A side's clock drawn as its score is in the side's color while it is its turn.
 */
@Composable
private fun scoreColor(v: Versus, side: Int? = null): Color {
    if (side != null && v.figures[side].bigClock && VersusWords.turnIndex(v.spec) == side) return v.roles[side]
    val role = roleColor(v.spec.base)
    return if (v.bold) valueInk(role, lookColors(WidgetLook.BOLD, role).card) else lookColors(v.look, role).content
}

/**
 * A side's mark: its letters in its color on a Material shape of the color's container. In Tonal,
 * whose card is the accent's container, the shape is on the page's color, so it never vanishes.
 */
@Composable
private fun Mark(v: Versus, index: Int) {
    val s = v.spec.sides.getOrNull(index) ?: return
    val role = v.roles[index]
    val side = v.markDp.dp
    // The letters are sized to the shape, not to the phone's font size: the shape does not grow with it.
    val size = with(LocalDensity.current) { (side * if (VersusWords.mark(s).length > 1) 0.36f else 0.46f).toSp() }
    val fill = if (v.look == WidgetLook.TONAL) MaterialTheme.colorScheme.surface else roleContainer(role)
    ShapedBox(rankPolygon(index), fill, side) {
        Text(
            VersusWords.mark(s),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold),
            color = role,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** A part of the bar is never thinner than this share, so a side with almost nothing still shows. */
private const val LEAST_SHARE = 0.04f

/**
 * The thin bar under the rows ([VersusWords.shares]): one part for each side, in its color, the
 * first from the start. With `moving`, the part of the side whose turn it is is a wave that moves
 * ([LevelBar]). The parts move to new values on the theme's spring.
 */
@Composable
private fun VersusBar(v: Versus) {
    val shares = VersusWords.shares(v.spec) ?: return
    val turn = VersusWords.turnIndex(v.spec)
    val first by animateFloatAsState(
        shares.first.coerceIn(LEAST_SHARE, 1f - LEAST_SHARE),
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "versus"
    )
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(first)) { LevelBar(1f, v.spec.moving && turn == 0, v.roles[0]) }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f - first)) { LevelBar(1f, v.spec.moving && turn == 1, v.roles[1]) }
    }
}
