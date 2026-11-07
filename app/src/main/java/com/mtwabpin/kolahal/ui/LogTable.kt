package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText

/**
 * The part of a script line's body from its first table on ([StyledText.splitAtTable]), drawn under
 * the line's text: each table as a [LogTable], and the text between and after them as body text.
 * [start] lines it up with the title; [bullet] is the width of "• ", for list items.
 */
@Composable
internal fun TableBody(rest: String, style: TextStyle, start: Dp, bullet: TextUnit) {
    val blocks = remember(rest) { blocks(rest) }
    Column(Modifier.padding(start = start)) {
        for (block in blocks) when (block) {
            is Block.Table -> LogTable(block.rows, style, Modifier.padding(vertical = 4.dp))
            is Block.Text -> Text(remember(block.raw, Term.palette) { buildAnnotatedString { appendBody(block.raw, 0.sp, bullet) } }, style = style)
        }
    }
}

private sealed interface Block {
    class Table(val rows: List<StyledText.Row>) : Block
    class Text(val raw: String) : Block
}

/** [rest] as tables and runs of text, in order. */
private fun blocks(rest: String): List<Block> {
    val out = ArrayList<Block>()
    val text = ArrayList<String>()
    val rows = ArrayList<StyledText.Row>()
    fun endText() { if (text.isNotEmpty()) out += Block.Text(text.joinToString("\n")); text.clear() }
    fun endTable() { if (rows.isNotEmpty()) out += Block.Table(rows.toList()); rows.clear() }
    for (raw in rest.split('\n')) {
        val row = if (StyledText.hasTable(raw)) StyledText.lines(raw).single().row else null
        if (row != null) { endText(); rows += row } else { endTable(); text += raw }
    }
    endText()
    endTable()
    return out
}

/**
 * A table in a script line (`fmt.table`), in the terminal font so its columns line up.
 * - `log.tables boxed` (the default): on the theme's surface colour, with lines between rows.
 * - `log.tables plain`: columns only.
 * The header row is muted, the cells are in the text colour with their own styles. A table wider
 * than the line scrolls sideways, and its edge fades while there is more to see.
 */
@Composable
internal fun LogTable(rows: List<StyledText.Row>, style: TextStyle, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val boxed = Term.boxedTables
    val columns = rows.maxOf { it.cells.size }
    val scroll = rememberScrollState()
    val cellX = if (boxed) 8.dp else 0.dp
    val cellY = if (boxed) 4.dp else 1.dp
    val gap = if (boxed) 0.dp else 14.dp
    val rule = Term.dim.copy(alpha = 0.25f)
    val headerRule = Term.dim.copy(alpha = 0.45f)
    val hasHeader = rows.first().header
    // A screen reader hears a row as "Coin BTC, Price 64,210": each cell after its column's name.
    val spoken = remember(rows) {
        val names = if (hasHeader) rows.first().cells.map { c -> c.spans.joinToString("") { it.text } } else emptyList()
        rows.drop(if (hasHeader) 1 else 0).joinToString(". ") { row ->
            row.cells.mapIndexed { i, c -> listOfNotNull(names.getOrNull(i)?.takeIf { it.isNotBlank() }, c.spans.joinToString("") { it.text }).joinToString(" ") }.joinToString(", ")
        }
    }
    Row(
        modifier
            .testTag("log-table")
            // The table is read once, as [spoken]. Without the clear, the line around it would also
            // read each cell's text.
            .clearAndSetSemantics { contentDescription = spoken }
            // The fade at the edge: a mask over the table, so it works on any background (wallpaper too).
            // The mask needs its own layer, and only a table that scrolls has a fade.
            .graphicsLayer { compositingStrategy = if (scroll.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                if (scroll.value < scroll.maxValue) {
                    val w = 28.dp.toPx()
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                        topLeft = Offset(size.width - w, 0f), blendMode = BlendMode.DstIn,
                    )
                }
            }
            .horizontalScroll(scroll),
    ) {
        // Where each row starts, from the layout below, for the lines between rows.
        val rowTops = remember(rows.size) { IntArray(rows.size + 1) }
        val right = remember(rows) { BooleanArray(columns) { c -> rows.any { it.cells.getOrNull(c)?.right == true } } }
        Layout(
            content = {
                for (row in rows) for (c in 0 until columns) {
                    val cell = row.cells.getOrNull(c)
                    val base = SpanStyle(color = if (row.header) Term.dim else Term.fg)
                    Text(
                        remember(cell, row.header, Term.palette) { buildAnnotatedString { cell?.let { appendSpans(it.spans, base, Term.fg) } } },
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(horizontal = cellX, vertical = cellY),
                    )
                }
            },
            modifier = Modifier
                .then(if (boxed) Modifier.clip(RoundedCornerShape(8.dp)).background(Term.surface) else Modifier)
                .drawBehind {
                    if (!boxed) return@drawBehind
                    for (i in 1 until rows.size) {
                        val y = rowTops[i].toFloat()
                        drawLine(if (hasHeader && i == 1) headerRule else rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                },
        ) { measurables, _ ->
            // A grid: each column as wide as its widest cell, each row as tall as its tallest one.
            // Cells can differ in height (a symbol from a fallback font is taller), so each is centred in its row.
            val cells = measurables.map { it.measure(Constraints()) }
            val widths = IntArray(columns) { c -> rows.indices.maxOf { r -> cells[r * columns + c].width } }
            val heights = IntArray(rows.size) { r -> (0 until columns).maxOf { c -> cells[r * columns + c].height } }
            val gapPx = gap.roundToPx()
            for (r in rows.indices) rowTops[r + 1] = rowTops[r] + heights[r]
            layout(widths.sum() + gapPx * (columns - 1), rowTops[rows.size]) {
                for (r in rows.indices) {
                    var x = 0
                    for (c in 0 until columns) {
                        val cell = cells[r * columns + c]
                        cell.place(if (right[c]) x + widths[c] - cell.width else x, rowTops[r] + (heights[r] - cell.height) / 2)
                        x += widths[c] + gapPx
                    }
                }
            }
        }
        // Room after the last column, so the fade doesn't cover it once it has scrolled into view.
        Spacer(Modifier.width(4.dp))
    }
}
