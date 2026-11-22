/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap

// ---- list ------------------------------------------------------------------------------------

/** `list` in Bold, Split or Shape ([RowsBody] draws Plain and Tonal, and an empty list). A list's card is never one shape. */
@Composable
internal fun RowsLook(
    spec: WidgetSpec.Rows,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?,
    look: WidgetLook
) {
    when (look) {
        WidgetLook.BOLD -> BoldRows(spec, box, onTap)
        WidgetLook.SPLIT -> SplitRows(spec, box, onTap)
        else -> ShapeRows(spec, box, onTap)
    }
}

/** The height of one of Bold's lines under its lead row. */
private val BOLD_LINE = 42.dp

/**
 * Bold's list: the title small in the role's color with "+3 more" at its end, the first row (or
 * the list's lead) large, then as many rows as fit as plain lines. Every row keeps its own tap.
 * Rows with a range have a bar ([boldBars]): a line before its end text, the first row at the end
 * of its second line, all in one column.
 */
@Composable
private fun BoldRows(spec: WidgetSpec.Rows, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
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
        val lines =
            if (box == WidgetSizes.Box.LARGE) ((maxHeight - 16.dp - 82.dp) / BOLD_LINE).toInt()
                .coerceIn(0, rest.size) else 0
        val more = rest.size - lines
        if (box == WidgetSizes.Box.SMALL) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (first != null) {
                    FittedText(
                        cardText(first.title),
                        boldValueStyle(18.sp, FontWeight.ExtraBold).copy(
                            lineHeight = 1.05.em,
                            textAlign = TextAlign.Center,
                            fontFeatureSettings = null
                        ),
                        18.sp,
                        c.content,
                        Modifier
                            .weight(1f, fill = false)
                            .then(rowPress(first, onTap))
                    )
                } else if (lead != null) {
                    BoldValue(lead.value, lead.unit, 40.sp, role)
                }
                if (more > 0) Text(
                    "+$more more",
                    style = type.labelSmall,
                    color = c.muted,
                    maxLines = 1,
                    modifier = Modifier.opensList()
                )
            }
            return@BoxWithConstraints
        }
        val shown = rest.take(lines)
        // The first row's second line: its detail and its end text.
        val second =
            first?.let { listOfNotNull(it.detail, it.trailing).joinToString(" · ") }.orEmpty()
        val (bars, firstBar) = boldBars(first, second, shown, maxWidth, lineStyle, endStyle)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.orEmpty(),
                    style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = role,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (more > 0) Text(
                    "+$more more",
                    style = type.labelMedium,
                    color = c.muted,
                    maxLines = 1,
                    modifier = Modifier
                        .opensList()
                        .padding(start = 8.dp)
                )
            }
            val large = box == WidgetSizes.Box.LARGE
            if (first != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (large) 8.dp else 2.dp)
                        .then(rowPress(first, onTap))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        first.done?.let {
                            RowCheck(
                                it,
                                first.color?.let { k -> colorOf(k) } ?: role,
                                Modifier.padding(end = 8.dp))
                        }
                        Text(
                            cardText(first.title),
                            style = boldValueStyle(
                                if (large) 26.sp else 20.sp,
                                FontWeight.ExtraBold
                            ).copy(lineHeight = 1.05.em, fontFeatureSettings = null),
                            color = c.content,
                            maxLines = if (large) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (bars != null && firstBar) {
                        // The bar in the lines' column: its end text is in the line already, so that column stays empty.
                        Row(
                            Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                cardText(second),
                                style = type.bodySmall,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            RangeSlot(
                                first.copy(trailing = null),
                                bars,
                                first.color?.let { colorOf(it) } ?: role,
                                c.card,
                                endStyle,
                                c.muted)
                        }
                    } else if (second.isNotEmpty()) {
                        Text(
                            cardText(second),
                            style = type.bodySmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else if (lead != null) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomStart
                ) { BoldValue(lead.value, lead.unit, if (large) 56.sp else 40.sp, role) }
                lead.label?.let {
                    Text(
                        it,
                        style = type.bodySmall,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }
            if (first != null) Spacer(Modifier.weight(1f))
            val divider = MaterialTheme.colorScheme.outlineVariant
            shown.forEachIndexed { i, row ->
                key(i) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(BOLD_LINE)
                            .drawBehind {
                                drawLine(
                                    divider,
                                    Offset(0f, 0f),
                                    Offset(size.width, 0f),
                                    1.dp.toPx()
                                )
                            }
                            .then(rowPress(row, onTap)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.done?.let {
                            RowCheck(
                                it,
                                row.color?.let { k -> colorOf(k) } ?: role,
                                Modifier.padding(end = 8.dp))
                        }
                        Text(
                            cardText(row.title),
                            style = lineStyle,
                            color = c.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (bars != null) RangeSlot(
                            row,
                            bars,
                            row.color?.let { colorOf(it) } ?: role,
                            c.card,
                            endStyle,
                            c.muted)
                        else row.trailing?.let {
                            Text(
                                cardText(it),
                                style = endStyle,
                                color = c.muted,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
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
private fun boldBars(
    first: WidgetSpec.Row?,
    second: String,
    lines: List<WidgetSpec.Row>,
    width: Dp,
    lineStyle: TextStyle,
    endStyle: TextStyle
): Pair<RangeBars?, Boolean> {
    val rows = listOfNotNull(first) + lines
    if (rows.none { it.range != null }) return null to false
    val check = if (lines.any { it.done != null }) 26.dp else 0.dp
    val titles =
        if (lines.isEmpty()) 0.dp else widestText(lines.map { it.title }, lineStyle) + check
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
 * [shape], [side] across: the first in the role's color, a row with a color in that color, the
 * others in the role's container ([roleContainer]). With [inShape], an icon sits on that shape too.
 */
@Composable
private fun RowMark(
    index: Int,
    row: WidgetSpec.Row,
    role: Color,
    shape: Shape,
    side: Dp,
    inShape: Boolean
) {
    val type = MaterialTheme.typography
    val colors = lookColors()
    val color = row.color?.let { colorOf(it) }
    val done = row.done
    val icon = row.icon
    when {
        done != null -> RowCheck(done, color ?: role)
        icon != null && !inShape -> WidgetIconText(
            icon,
            type.titleMedium,
            color ?: colors.muted,
            size = 18.dp
        )

        else -> {
            val strong = color != null || index == 0
            // The others are on the role's container: the card's highest surface is too close to the card to see.
            val container = roleContainer(role)
            val fill =
                if (icon != null) container else color ?: if (index == 0) role else container
            Box(
                Modifier
                    .size(side)
                    .background(fill, shape), contentAlignment = Alignment.Center
            ) {
                if (icon != null) WidgetIconText(
                    icon,
                    type.titleMedium,
                    color ?: role,
                    size = side / 2
                )
                else Text(
                    "${index + 1}",
                    style = type.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (strong) colors.onAccent else colors.content,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A row's title, with its second line under it when [details] has room for one, and its end text in [muted].
 * With [bars], the row's range has its bar before the end text ([RangeSlot]): in the row's color or
 * the card's [role], with [under] the color beneath it.
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
            Text(
                cardText(row.title),
                style = style,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (details) row.detail?.let {
                Text(
                    cardText(it),
                    style = type.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (bars != null) RangeSlot(
            row,
            bars,
            row.color?.let { colorOf(it) } ?: role,
            under,
            end,
            muted)
        else row.trailing?.let {
            Text(
                cardText(it),
                style = end,
                color = muted,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
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
private fun lookBars(
    rows: List<WidgetSpec.Row>,
    width: Dp,
    start: Dp,
    checked: Dp,
    side: Dp,
    gap: Dp,
    style: TextStyle,
    inShape: Boolean,
    details: Boolean
): RangeBars? {
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
        words = maxOf(
            widestText(rows.map { it.title }, style),
            widestText(if (details) rows.mapNotNull { it.detail } else emptyList(),
                type.bodySmall)),
        trailing = widestText(
            rows.mapNotNull { it.trailing },
            type.labelMedium.copy(fontWeight = FontWeight.Bold)
        ),
        mark = rows.maxOf { marked(it) } - bare,
    )
}

/** A list's lead as Split writes it: the value big with its unit, and the label under them. */
@Composable
private fun LeadWords(
    lead: WidgetSpec.Lead,
    style: TextStyle,
    muted: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        BigText(WidgetWords.joined(lead.value, lead.unit), style)
        lead.label?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    val role = roleColor(spec.base)
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
                val fit =
                    ((maxHeight - 40.dp - leadRoom) / (44.dp + gap)).toInt().coerceIn(0, items.size)
                val more = items.size - fit
                val count = 1 + (if (lead != null) 1 else 0) + fit
                // A row's words start 10 dp in, after its mark: a check at 6 dp, or a rank at 10 dp. Its end has 16 dp.
                val bars = lookBars(
                    items.take(fit),
                    maxWidth - 16.dp,
                    start = 10.dp,
                    checked = 34.dp,
                    side = 24.dp,
                    gap = 10.dp,
                    rowStyle,
                    inShape = false,
                    details = details
                )
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    // The last tile takes what is left, so the tiles reach the card's bottom.
                    Tile(
                        SplitCorners.inColumn(0, count),
                        c.tile,
                        Modifier
                            .fillMaxWidth()
                            .then(if (count == 1) Modifier.weight(1f) else Modifier.height(40.dp))
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                title.orEmpty(),
                                style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = c.content,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (more > 0) Text(
                                "+$more more",
                                style = type.labelMedium,
                                color = c.muted,
                                maxLines = 1,
                                modifier = Modifier
                                    .opensList()
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                    if (lead != null) Tile(
                        SplitCorners.inColumn(1, count),
                        c.tile,
                        Modifier
                            .fillMaxWidth()
                            .then(if (fit == 0) Modifier.weight(1f) else Modifier.height(64.dp))
                    ) {
                        LeadWords(
                            lead,
                            type.headlineMedium,
                            c.muted,
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(horizontal = 16.dp)
                        )
                    }
                    items.take(fit).forEachIndexed { i, row ->
                        key(i) {
                            val at = count - fit + i
                            Tile(
                                SplitCorners.inColumn(at, count),
                                c.tile,
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                press = rowPress(row, onTap)
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(
                                            start = if (row.done != null) 6.dp else 10.dp,
                                            end = 16.dp
                                        ), verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val marked = bars?.marks != false || row.done != null
                                    if (marked) RowMark(
                                        i,
                                        row,
                                        role,
                                        CircleShape,
                                        24.dp,
                                        inShape = false
                                    )
                                    RowWords(
                                        row,
                                        details,
                                        c.content,
                                        c.muted,
                                        rowStyle,
                                        Modifier.padding(start = if (marked) 10.dp else 0.dp),
                                        bars,
                                        role,
                                        c.tile
                                    )
                                }
                            }
                        }
                    }
                }
            }

            WidgetSizes.Box.WIDE -> {
                val fit = ((maxHeight + gap) / (36.dp + gap)).toInt()
                    .coerceIn(1, items.size.coerceAtLeast(1))
                val more = items.size - (if (lead != null) 0 else fit)
                val head = min(maxWidth * 0.32f, 120.dp)
                // The rows' tiles are what the title's tile leaves. A row's words start 8 dp in, after its mark; its end has 14 dp.
                val bars = if (lead != null) null else lookBars(
                    items.take(fit),
                    maxWidth - head - gap - 14.dp,
                    start = 8.dp,
                    checked = 34.dp,
                    side = 22.dp,
                    gap = 8.dp,
                    rowStyle,
                    inShape = false,
                    details = false
                )
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(
                        SplitCorners.inRow(0, 2), c.tile, Modifier
                            .fillMaxHeight()
                            .width(head)
                    ) {
                        Column(
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                title.orEmpty(),
                                style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = c.content,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (more > 0) Text(
                                "+$more more",
                                style = type.labelSmall,
                                color = c.muted,
                                maxLines = 1
                            )
                        }
                    }
                    if (lead != null) {
                        Tile(
                            SplitCorners.inRow(1, 2),
                            c.tile,
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            LeadWords(
                                lead,
                                type.headlineSmall,
                                c.muted,
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(horizontal = 14.dp)
                            )
                        }
                    } else {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            items.take(fit).forEachIndexed { i, row ->
                                key(i) {
                                    Tile(
                                        SplitCorners.tile(false, i == 0, i == fit - 1, false),
                                        c.tile,
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        press = rowPress(row, onTap)
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(start = 8.dp, end = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val marked = bars?.marks != false || row.done != null
                                            if (marked) RowMark(
                                                i,
                                                row,
                                                role,
                                                CircleShape,
                                                22.dp,
                                                inShape = false
                                            )
                                            RowWords(
                                                row,
                                                false,
                                                c.content,
                                                c.muted,
                                                rowStyle,
                                                Modifier.padding(start = if (marked) 8.dp else 0.dp),
                                                bars,
                                                role,
                                                c.tile
                                            )
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
                        BigText(
                            WidgetWords.joined(lead.value, lead.unit),
                            type.titleLarge,
                            Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 8.dp)
                        )
                    }
                } else {
                    val fit = ((maxHeight + gap) / (32.dp + gap)).toInt().coerceIn(1, items.size)
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        items.take(fit).forEachIndexed { i, row ->
                            key(i) {
                                Tile(
                                    SplitCorners.inColumn(i, fit),
                                    c.tile,
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    press = rowPress(row, onTap)
                                ) {
                                    Text(
                                        cardText(row.title),
                                        style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = c.content,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(horizontal = 12.dp)
                                    )
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
    val role = roleColor(spec.base)
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
        val style =
            if (box == WidgetSizes.Box.SMALL) type.bodySmall.copy(fontWeight = FontWeight.SemiBold) else rowStyle
        // A row's words start 10 dp after its shape, or after its checkbox.
        val bars = lookBars(
            items.take(fit),
            maxWidth,
            start = 0.dp,
            checked = 28.dp,
            side = mark,
            gap = 10.dp,
            style,
            inShape = true,
            details = details
        )
        Column(Modifier.fillMaxSize()) {
            if (large) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title.orEmpty(),
                        style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (more > 0) Text(
                        "+$more more",
                        style = type.labelMedium,
                        color = c.muted,
                        maxLines = 1,
                        modifier = Modifier
                            .opensList()
                            .padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (lead != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(leadHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShapedBox(
                        MaterialShapes.Cookie12Sided,
                        c.shapeFill,
                        leadSide
                    ) {
                        BigText(
                            lead.value,
                            shapeValueStyle(leadSide),
                            Modifier.padding(horizontal = leadSide / 7)
                        )
                    }
                    Column(
                        Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        lead.unit?.let {
                            Text(
                                it,
                                style = type.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = c.content,
                                maxLines = 1
                            )
                        }
                        lead.label?.let {
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
            items.take(fit).forEachIndexed { i, row ->
                key(i) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .then(rowPress(row, onTap)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val marked = bars?.marks != false || row.done != null
                        if (marked) RowMark(
                            i,
                            row,
                            role,
                            rankPolygon(i).toShape(),
                            mark,
                            inShape = true
                        )
                        RowWords(
                            row,
                            details,
                            c.content,
                            c.muted,
                            style,
                            Modifier.padding(start = if (marked) 10.dp else 0.dp),
                            bars,
                            role,
                            c.card
                        )
                    }
                }
            }
        }
    }
}
