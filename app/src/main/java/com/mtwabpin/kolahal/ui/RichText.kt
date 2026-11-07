package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A [Text] that also draws the chips behind its tags (`fmt.tag`). [appendSpans] marks each tag
 * with [TAG_CHIP]. Links in [text] are tappable as they are in any [Text].
 */
@Composable
internal fun RichText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    // Each tag's range and colour, read once for this text.
    val chips = remember(text) { text.getStringAnnotations(TAG_CHIP, 0, text.length).mapNotNull { a -> a.item.toIntOrNull()?.let { Chip(a.start, a.end, Color(it)) } } }
    if (chips.isEmpty()) {
        Text(text, modifier, style = style, inlineContent = inlineContent, maxLines = maxLines, softWrap = softWrap, overflow = overflow)
        return
    }
    // The text's layout, for the chips. Not state: the text lays out before it draws, and a new
    // layout redraws the text and its chips together, so reading it in draw needs no extra frame.
    val layout = remember { LayoutHolder() }
    val dark = Term.bg.luminance() < 0.5f
    val size = if (style.fontSize.isSpecified()) style.fontSize else Term.fontSize.sp
    Text(
        text,
        modifier.drawBehind { layout.value?.let { drawChips(it, chips, dark, (size * TAG_SIZE).toPx()) } },
        style = style,
        inlineContent = inlineContent,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        onTextLayout = { layout.value = it },
    )
}

private fun TextUnit.isSpecified() = this != TextUnit.Unspecified

/** A tag's range in its text and its colour ([TAG_CHIP]). */
private class Chip(val start: Int, val end: Int, val colour: Color)

/** Holds a text's last layout, outside the snapshot system: see [RichText]. */
private class LayoutHolder(var value: TextLayoutResult? = null)

/**
 * The chip behind each tag: a rounded box a little wider than its text, shaded in the tag's
 * colour, on each row the tag covers. [em] is the tag's text size in pixels.
 */
private fun DrawScope.drawChips(layout: TextLayoutResult, chips: List<Chip>, dark: Boolean, em: Float) {
    val padX = 3.dp.toPx()
    val radius = CornerRadius(4.dp.toPx())
    val length = layout.layoutInput.text.length
    for (chip in chips) {
        if (chip.start >= chip.end || chip.end > length) continue
        val colour = chip.colour
        val fill = colour.copy(alpha = if (dark) 0.24f else 0.14f)
        val first = layout.getLineForOffset(chip.start)
        val last = layout.getLineForOffset(chip.end - 1)
        for (line in first..last) {
            val s = maxOf(chip.start, layout.getLineStart(line))
            val e = minOf(chip.end, layout.getLineEnd(line, visibleEnd = true))
            if (s >= e) continue
            val a = layout.getBoundingBox(s)
            val b = layout.getBoundingBox(e - 1)
            val left = minOf(a.left, b.left) - padX
            val right = maxOf(a.right, b.right) + padX
            // Around the tag's letters, not the whole line: from a little above a capital to a little below the baseline.
            val baseline = layout.getLineBaseline(line)
            drawRoundRect(fill, Offset(left, baseline - em * 0.92f), Size(right - left, em * 1.22f), radius)
        }
    }
}

/**
 * The part of a line's body from its first table or bar on ([StyledText.splitAtBlocks]), drawn
 * under the line's text: each table as a [LogTable], bars that follow each other as [Meters], and
 * the text between and after them as body text. [start] lines it up with the title; [bullet] is
 * the width of "• ", for list items.
 */
@Composable
internal fun RichBody(rest: String, style: TextStyle, start: Dp, bullet: TextUnit) {
    val blocks = remember(rest) { blocks(rest) }
    Column(Modifier.padding(start = start)) {
        for (block in blocks) when (block) {
            is Block.Table -> LogTable(block.rows, style, Modifier.padding(vertical = 4.dp))
            is Block.Bars -> Meters(block.meters, style, Modifier.padding(vertical = 2.dp))
            is Block.Text -> RichText(remember(block.raw, Term.palette) { buildAnnotatedString { appendBody(block.raw, 0.sp, bullet) } }, style = style)
        }
    }
}

private sealed interface Block {
    class Table(val rows: List<StyledText.Row>) : Block
    class Bars(val meters: List<StyledText.Meter>) : Block
    class Text(val raw: String) : Block
}

/** [rest] as tables, runs of bars and runs of text, in order. */
private fun blocks(rest: String): List<Block> {
    val out = ArrayList<Block>()
    val text = ArrayList<String>()
    val rows = ArrayList<StyledText.Row>()
    val meters = ArrayList<StyledText.Meter>()
    fun endText() { if (text.isNotEmpty()) out += Block.Text(text.joinToString("\n")); text.clear() }
    fun endTable() { if (rows.isNotEmpty()) out += Block.Table(rows.toList()); rows.clear() }
    fun endBars() { if (meters.isNotEmpty()) out += Block.Bars(meters.toList()); meters.clear() }
    for (raw in rest.split('\n')) {
        val line = if (StyledText.hasBlock(raw)) StyledText.lines(raw).single() else null
        when {
            line?.row != null -> { endText(); endBars(); rows += line.row }
            line?.meter != null -> { endText(); endTable(); meters += line.meter }
            else -> { endTable(); endBars(); text += raw }
        }
    }
    endText()
    endTable()
    endBars()
    return out
}

/** How tall a bar's track is, and the least room it gets beside its texts. */
private val TRACK = 6.dp
private val TRACK_MIN = 40.dp
private val COLUMN_GAP = 8.dp

/**
 * Bars on lines that follow each other (`fmt.bar`, `fmt.bars`, `fmt.ranges`), lined up in
 * columns: the labels, the texts before the tracks, the tracks, and the texts after them. Each
 * column is as wide as its widest text, and the tracks take the rest of the width. The label is in
 * the body's colour, the numbers in the text colour, each with its own styles.
 */
@Composable
internal fun Meters(meters: List<StyledText.Meter>, style: TextStyle, modifier: Modifier = Modifier) {
    if (meters.isEmpty()) return
    val labels = meters.any { it.label.isNotEmpty() }
    val starts = meters.any { it.start.isNotEmpty() }
    val ends = meters.any { it.end.isNotEmpty() }
    val columns = listOf(labels, starts, true, ends).count { it }
    val spoken = remember(meters) { meters.joinToString(". ", transform = ::spoken) }
    val palette = Term.palette
    Layout(
        content = {
            for (m in meters) {
                if (labels) MeterText(m.label, style, palette)
                if (starts) MeterText(m.start, style.copy(color = Term.fg), palette)
                Track(m)
                if (ends) MeterText(m.end, style.copy(color = Term.fg), palette)
            }
        },
        modifier = modifier.testTag("log-bars").clearAndSetSemantics { contentDescription = spoken },
    ) { measurables, constraints ->
        val gap = COLUMN_GAP.roundToPx()
        val line = style.lineHeight.takeIf { it.isSp }?.toPx()?.roundToInt() ?: (style.fontSize.toPx() * 1.36f).roundToInt()
        // Texts first, at their own width; then each column is as wide as its widest text.
        val texts = HashMap<Int, androidx.compose.ui.layout.Placeable>()
        val trackColumn = listOf(labels, starts).count { it }
        for (i in measurables.indices) if (i % columns != trackColumn) texts[i] = measurables[i].measure(Constraints())
        val widths = IntArray(columns) { c -> if (c == trackColumn) 0 else meters.indices.maxOf { r -> texts[r * columns + c]?.width ?: 0 } }
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else widths.sum() + gap * (columns - 1) + TRACK_MIN.roundToPx() * 4
        val track = (maxWidth - widths.sum() - gap * (columns - 1)).coerceAtLeast(TRACK_MIN.roundToPx())
        val trackHeight = TRACK.roundToPx()
        val tracks = meters.indices.map { r -> measurables[r * columns + trackColumn].measure(Constraints.fixed(track, trackHeight)) }
        val heights = IntArray(meters.size) { r ->
            maxOf(line, (0 until columns).maxOf { c -> texts[r * columns + c]?.height ?: trackHeight })
        }
        layout(minOf(maxWidth, widths.sum() + track + gap * (columns - 1)).coerceAtLeast(0), heights.sum()) {
            var y = 0
            for (r in meters.indices) {
                var x = 0
                for (c in 0 until columns) {
                    val w = if (c == trackColumn) track else widths[c]
                    val p = if (c == trackColumn) tracks[r] else texts[r * columns + c]!!
                    // The text before the track lines up on the right, against it; the others on the left.
                    val px = if (starts && c == trackColumn - 1) x + w - p.width else x
                    p.place(px, y + (heights[r] - p.height) / 2)
                    x += w + gap
                }
                y += heights[r]
            }
        }
    }
}

@Composable
private fun MeterText(spans: List<StyledText.Span>, style: TextStyle, palette: Palette) {
    val text = remember(spans, palette, style.color) { buildAnnotatedString { appendSpans(spans, SpanStyle(color = style.color), Term.fg) } }
    RichText(text, style, maxLines = 1, softWrap = false)
}

/** A bar's track: a light shade of its colour, and the filled part from its start to its end in the colour itself. */
@Composable
internal fun Track(m: StyledText.Meter, modifier: Modifier = Modifier) {
    val colour = colourOf(m.colour ?: StyledText.Colour.ACCENT)
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(colour.copy(alpha = 0.2f), cornerRadius = r)
        if (m.to <= 0f) return@Canvas
        // A range of no width (low and high the same) still shows, as a dot.
        val left = m.from * size.width
        val width = maxOf((m.to - m.from) * size.width, size.height)
        val x = minOf(left, size.width - width).coerceAtLeast(0f)
        drawRoundRect(colour, Offset(x, 0f), Size(width, size.height), r)
    }
}

/** What a screen reader says for a bar: its texts, or how full it is. */
private fun spoken(m: StyledText.Meter): String {
    fun words(spans: List<StyledText.Span>) = spans.joinToString("") { it.text }
    val parts = listOf(words(m.label), words(m.start), words(m.end)).filter { it.isNotBlank() }
    if (parts.isNotEmpty()) return parts.joinToString(" ")
    return "${((m.to - m.from) * 100).roundToInt()} percent"
}

/**
 * A line's own bar ([com.mtwabpin.kolahal.data.LogContext.BAR]): built-in commands use it, as
 * `from,to,role`, to draw a bar after a line's text (`unlocks`, `usage`, `volume`).
 */
internal fun lineBar(value: String?): StyledText.Meter? {
    val parts = value?.split(',') ?: return null
    val from = parts.getOrNull(0)?.toFloatOrNull() ?: return null
    val to = parts.getOrNull(1)?.toFloatOrNull() ?: return null
    if (from.isNaN() || to.isNaN()) return null
    val a = from.coerceIn(0f, 1f)
    return StyledText.Meter(a, to.coerceIn(a, 1f), parts.getOrNull(2)?.let(StyledText.Colour::named), emptyList(), emptyList(), emptyList())
}

/**
 * [text], then a bar ([lineBar]) that takes the rest of the row. The text gets the width it needs,
 * leaving the bar at least [TRACK_MIN]. Rows with text of the same width (padded, in the terminal
 * font) have their bars line up.
 */
@Composable
internal fun WithBar(bar: StyledText.Meter, modifier: Modifier = Modifier, text: @Composable () -> Unit) {
    Layout(
        content = {
            Box { text() }
            Track(bar)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = COLUMN_GAP.roundToPx()
        val min = TRACK_MIN.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else min * 4
        val shown = measurables[0].measure(Constraints(maxWidth = (width - gap - min).coerceAtLeast(0)))
        val height = TRACK.roundToPx()
        val track = measurables[1].measure(Constraints.fixed((width - shown.width - gap).coerceAtLeast(min), height))
        layout(width, shown.height) {
            shown.place(0, 0)
            // Beside the first line of the text.
            val firstLine = minOf(shown.height, (Term.fontSize * TEXT_LINE).sp.roundToPx())
            track.place(shown.width + gap, (firstLine - height) / 2)
        }
    }
}

/** Lines past [Term.foldLines] fold only when this many more would be hidden: a fold never hides a line or two. */
private const val FOLD_SLACK = 2

/**
 * A line's content, folded when it is taller than [Term.foldLines] lines of [style]'s text: the
 * first lines show, then "▾ N more lines". A tap on it shows the whole line, with "▴ Show less"
 * under it. A line stays open while it is in the log's memory (scrolled away and back).
 * [enabled]: false for a search result, where the matching words must show.
 */
@Composable
internal fun Foldable(id: Long, style: TextStyle, enabled: Boolean = true, content: @Composable () -> Unit) {
    val lines = Term.foldLines
    if (!enabled || lines <= 0) return content()
    var open by rememberSaveable(id) { mutableStateOf(false) }
    val toggle = remember(id) { { open = !open } }
    val density = LocalDensity.current
    val line = with(density) { (style.lineHeight.takeIf { it.isSp } ?: (Term.fontSize * TEXT_LINE).sp).toPx() }
    SubcomposeLayout(Modifier.testTag("fold-$id")) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        // content is given as it is, so a measure without a new content recomposes nothing. It is one
        // layout (LogLine passes a Column).
        val body = subcompose(FoldSlot.BODY, content).first().measure(loose)
        val limit = (line * lines).roundToInt()
        val folds = body.height > limit + line * FOLD_SLACK
        val shown = if (folds && !open) limit else body.height
        val hidden = ceil((body.height - limit) / line).toInt()
        val link = if (!folds) null else subcompose(FoldSlot.LINK) { FoldLink(open, hidden, toggle) }.first().measure(constraints.copy(minHeight = 0))
        layout(maxOf(body.width, link?.width ?: 0), shown + (link?.height ?: 0)) {
            if (shown < body.height) body.placeWithLayer(0, 0) { clip = true; shape = TopPart(shown.toFloat()) } else body.place(0, 0)
            link?.place(0, shown)
        }
    }
}

private enum class FoldSlot { BODY, LINK }

/** Clips to the top [height] pixels: the part of a folded line that shows. Equal ones are equal, so placing it again changes no outline. */
private data class TopPart(private val height: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(0f, 0f, size.width, minOf(height, size.height)))
}

@Composable
private fun FoldLink(open: Boolean, hidden: Int, onClick: () -> Unit) {
    Text(
        if (open) "▴ Show less" else "▾ $hidden more ${if (hidden == 1) "line" else "lines"}",
        style = Term.small.copy(color = Term.link),
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = if (open) "fold the line" else "show the whole line", onClick = onClick)
            .heightIn(min = 32.dp)
            .wrapContentHeight()
            .padding(end = 12.dp)
            .testTag("fold-link"),
    )
}
