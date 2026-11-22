/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlin.math.ceil
import kotlin.math.floor

/** Height of the "+3 more" line under a list, in dp. */
private const val MORE_DP = 20

/**
 * `list`: as many rows as fit, then "+3 more", with its [WidgetSpec.Lead] over them when it has one.
 * - Each row is in a lightly tinted container: large corners at the ends of the list, small ones
 *   between rows, and [ROW_GAP_DP] between two ([segmentShape]).
 * - A row is 44 dp tall when it can be tapped or has a second line, and 32 dp otherwise. A tap row
 *   is a little under the 48 dp of a button: a list is read more than it is tapped, and a 4x2 card
 *   then shows three rows.
 * - When every row fits, the rows grow to share the height evenly, up to 56 dp
 *   ([WidgetWords.rowHeight]). Three days of a forecast then fill a 2x2 card. Rows under a lead
 *   keep their height, as the mockup's Reminders card has them.
 * - A narrow list (under 200 dp) leaves the second lines out.
 * - Rows with a range have a bar between their words and their trailing text ([RangeBars]).
 * - "+3 more" is the line under the rows.
 * - A large list has the card's name over its rows ([CardName]): "Groceries".
 * - The lead: in a large box, the big value and its label over the rows. A small or a wide box
 *   has room for the lead alone.
 */
@Composable
internal fun RowsBody(spec: WidgetSpec.Rows, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val lead = spec.lead
    val look = LocalWidgetLook.current
    when {
        lead == null && spec.items.isEmpty() && box == WidgetSizes.Box.LARGE -> SplitWhole {
            Column(Modifier.fillMaxSize()) {
                // An empty large list says which list it is over its words ("All read"), as it does over its rows.
                CardName()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    QuietLine(
                        spec.empty ?: WidgetWords.NOTHING
                    )
                }
            }
        }

        lead == null && spec.items.isEmpty() -> QuietLine(spec.empty ?: WidgetWords.NOTHING)
        look.untitled() -> RowsLook(spec, box, onTap, look)
        lead == null && box == WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            CardName(Modifier.padding(bottom = 8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                RowList(
                    spec.items,
                    onTap,
                    roleColor(spec.base)
                )
            }
        }

        lead == null -> RowList(spec.items, onTap, roleColor(spec.base))
        box != WidgetSizes.Box.LARGE -> {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (box == WidgetSizes.Box.SMALL) Alignment.Center else Alignment.CenterStart
            ) {
                LeadLine(lead, valueStyle(box), centered = box == WidgetSizes.Box.SMALL)
            }
        }

        else -> Column(Modifier.fillMaxSize()) {
            LeadLine(lead, MaterialTheme.typography.headlineMedium, centered = false)
            if (spec.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Under a lead the rows keep their height, as the mockup's Reminders card has them.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    RowList(
                        spec.items,
                        onTap,
                        roleColor(spec.base),
                        fill = false
                    )
                }
            }
        }
    }
}

/** A list's lead: the value big, its unit small, and the label under them. */
@Composable
private fun LeadLine(lead: WidgetSpec.Lead, style: TextStyle, centered: Boolean) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Row {
            BigText(
                lead.value, style, Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            )
            lead.unit?.let {
                Text(
                    it,
                    style = type.titleMedium,
                    color = muted,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .alignByBaseline()
                )
            }
        }
        lead.label?.let {
            Text(
                it,
                style = type.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A list's rows, as many as fit the box, then "+3 more" ([RowsBody]). With [fill], rows that all fit
 * share the box's height. [role] is the card's color, for the bars of rows with a range.
 */
@Composable
private fun RowList(
    items: List<WidgetSpec.Row>,
    onTap: ((WidgetTap) -> Unit)?,
    role: Color,
    fill: Boolean = true
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val details = maxWidth >= 200.dp && items.any { it.detail != null }
        val rowDp = when {
            items.any { it.tap != null } -> TAP_ROW_DP
            details -> TAP_ROW_DP
            else -> 32
        }
        val heightDp = maxHeight.value.toInt()
        val shown = WidgetWords.rowsShown(items.size, heightDp, rowDp, MORE_DP, ROW_GAP_DP)
        val more = items.size - shown
        // All rows drawn: they share the height, so a short list fills the card.
        val rowHeight = if (fill) WidgetWords.rowHeight(
            items.size,
            shown,
            maxHeight.value,
            rowDp,
            ROW_GAP_DP
        ).dp else rowDp.dp
        val rows = items.take(shown)
        val (bars, ends) = listBars(rows, maxWidth, details)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp)) {
            rows.forEachIndexed { i, row ->
                key(i) {
                    ListRow(
                        row,
                        rowHeight,
                        details,
                        segmentShape(i, shown),
                        onTap,
                        bars,
                        ends,
                        role
                    )
                }
            }
            if (WidgetWords.showsMore(items.size, shown, heightDp, rowDp, MORE_DP, ROW_GAP_DP)) {
                // With [LocalListOpen], the line's whole width opens the list. Its touches reach 48 dp
                // (Compose's minimum), more than the line is tall.
                val open = LocalListOpen.current
                Text(
                    "+$more more",
                    style = MaterialTheme.typography.labelMedium,
                    color = lookColors().muted,
                    maxLines = 1,
                    modifier = Modifier
                        .then(
                            if (open == null) Modifier else Modifier
                                .fillMaxWidth()
                                .opensList()
                        )
                        .height(MORE_DP.dp)
                        .wrapContentHeight(),
                )
            }
        }
    }
}

/** The height of a list's or an agenda's row that can be tapped, or that has two lines, in dp. */
internal const val TAP_ROW_DP = 44

/** The room between two rows of a list or of a clock, in dp. */
internal const val ROW_GAP_DP = 2

/**
 * The shape of row [index] of [count] in a segmented list: 16 dp corners at the list's two ends,
 * 4 dp between rows, as Settings groups its rows ([segShapes]) in less room.
 */
internal fun segmentShape(index: Int, count: Int): Shape {
    val outer = 16.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** The tint of a segmented row ([segmentShape]): a little lighter than the card. */
@Composable
internal fun segmentColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

/**
 * One row of a list, in its container of [shape]. Its color is on its icon, or else on a dot at
 * its start. The text at its end is dim: the color says what the row is, not what its number is.
 * A row with a tap takes the press before the card does, so it hands a long-press on to the card
 * ([LocalCardLongPress]).
 *
 * A row with `done` has a checkbox at its start, in the row's color.
 * - The box takes no tap of its own. A tap anywhere on the row is the row's tap, and the row is
 *   the checkbox for a screen reader.
 * - The box shows what the description says. Kolahal does not tick it on a tap: the script (or the
 *   built-in widget) shows the new state.
 *
 * With [bars], the row has its range's bar before its trailing text ([RangeSlot]). Without
 * [RangeBars.marks], its icon or its color's dot gives way to the bar. [ends] is the room at the
 * row's ends ([listBars]). [role] is the card's color: the bar's, when the row has none of its own.
 */
@Composable
private fun ListRow(
    row: WidgetSpec.Row,
    height: Dp,
    details: Boolean,
    shape: Shape,
    onTap: ((WidgetTap) -> Unit)?,
    bars: RangeBars?,
    ends: Dp,
    role: Color
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val done = row.done
    val color = row.color?.let { colorOf(it) }
    val look = LocalWidgetLook.current
    // Tonal has its rows in a lighter tint of its card, and its quiet text a little brighter.
    val colors = lookColors(look, role)
    val muted = colors.muted
    val tint = if (look == WidgetLook.TONAL) colors.tile else segmentColor()
    val press = rowPress(row, onTap)
    val marks = bars?.marks ?: true
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(tint)
            .then(press)
            .padding(start = if (done != null) 0.dp else ends, end = ends),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = row.icon.takeIf { marks }
        if (done != null) RowCheck(
            done,
            color ?: scheme.primary,
            Modifier.padding(start = 8.dp, end = 8.dp)
        )
        if (icon != null) {
            // Small and quiet, as in the mockup: the row's text says what it is, the icon only helps.
            WidgetIconText(
                icon,
                type.titleMedium,
                color ?: muted,
                size = 18.dp,
                modifier = Modifier.padding(end = 10.dp)
            )
        } else if (done == null && color != null && marks) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                cardText(row.title),
                style = type.bodyMedium,
                color = scheme.onSurface,
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
        if (bars != null) {
            // The row's tint is see-through: the ring around a value's dot has the tint over the card.
            RangeSlot(
                row,
                bars,
                color ?: role,
                tint.compositeOver(colors.card),
                type.labelLarge,
                muted
            )
        } else {
            row.trailing?.let {
                Text(
                    cardText(it),
                    style = type.labelLarge,
                    color = muted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** The room at the ends of a row of a Plain or Tonal list. */
private val ROW_PAD = 12.dp

/** The same, where the list's range bars need the room: a 2×2 card then has room for them. */
private val RANGE_ROW_PAD = 8.dp

/**
 * The bars of a Plain or Tonal list's [rows], in rows [width] wide ([RangeBars]), and the room at a
 * row's ends with them.
 * - A checkbox takes 34 dp from a row's start, and stays. An icon takes 28 dp more and a color's
 *   dot 18: those give way to the bars.
 * - Less room at the ends ([RANGE_ROW_PAD]) comes before the icons give way.
 * - Without bars, the rows keep [ROW_PAD].
 */
@Composable
private fun listBars(
    rows: List<WidgetSpec.Row>,
    width: Dp,
    details: Boolean
): Pair<RangeBars?, Dp> {
    if (rows.none { it.range != null }) return null to ROW_PAD
    val type = MaterialTheme.typography
    val icon = iconWidth(rows, type.titleMedium) + 10.dp
    // A row's second line sits under its title, in the same column: it must fit too.
    val titles = widestText(rows.map { it.title }, type.bodyMedium)
    val seconds =
        widestText(if (details) rows.mapNotNull { it.detail } else emptyList(), type.bodySmall)
    val words = maxOf(titles, seconds)
    val trailing = widestText(rows.mapNotNull { it.trailing }, type.labelLarge)
    fun mark(r: WidgetSpec.Row) = when {
        r.icon != null -> icon
        r.done == null && r.color != null -> 18.dp
        else -> 0.dp
    }

    fun fit(ends: Dp): RangeBars? {
        fun start(r: WidgetSpec.Row) = if (r.done != null) 34.dp else ends
        val bare = rows.maxOf { start(it) }
        return rangeBars(
            rows,
            room = width - ends - bare,
            words = words,
            trailing = trailing,
            mark = rows.maxOf { start(it) + mark(it) } - bare)
    }
    fit(ROW_PAD)?.takeIf { it.marks }?.let { return it to ROW_PAD }
    fit(RANGE_ROW_PAD)?.let { return it to RANGE_ROW_PAD }
    return null to ROW_PAD
}

/**
 * How a list draws its rows' ranges ([WidgetSpec.Row.range]): one [scale] for the rows it shows
 * ([WidgetWords.rangeScale]), and one place for every bar, so the bars line up and compare.
 * - [bar] is the bars' length. Each row has it between its words and its trailing text.
 * - [trailing] is the width of the column the trailing texts share, at the row's end (0 for none).
 * - [marks] is false when the rows' icons give way to the bars ([WidgetWords.rangeFit]).
 */
@Immutable
internal data class RangeBars(
    val scale: Pair<Float, Float>,
    val bar: Dp,
    val trailing: Dp,
    val marks: Boolean
)

/**
 * The bars of the [rows] a list shows, or null when none of them has a range, or when the rows
 * leave no room for a bar ([WidgetWords.rangeFit]).
 * - [room] is a row's width for its icon, its words, its bar and its trailing text.
 * - [words] and [trailing] are the widest of the rows' words and trailing texts ([widestText]).
 * - [mark] is what a row's icon takes, with its gap: the icons give way first.
 */
internal fun rangeBars(
    rows: List<WidgetSpec.Row>,
    room: Dp,
    words: Dp,
    trailing: Dp,
    mark: Dp
): RangeBars? {
    val scale = WidgetWords.rangeScale(rows.map { it.range }) ?: return null
    val fit =
        WidgetWords.rangeFit(room.value, words.value, trailing.value, mark.value) ?: return null
    // A whole number of dp, rounded down: the words keep all of their room.
    return RangeBars(scale, floor(fit.barDp).dp, trailing, fit.marks)
}

/**
 * The width of the widest of [texts] in [style], each on one line with its styles ([widgetText]),
 * rounded up to a whole dp and one more: a text laid out in it is never cut. 0 for no texts.
 */
@Composable
internal fun widestText(texts: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, style, measurer, density) {
        val px = texts.maxOfOrNull {
            measurer.measure(
                widgetText(it),
                style,
                softWrap = false,
                maxLines = 1
            ).size.width
        }
        if (px == null) 0.dp else with(density) { ceil(px.toDp().value).dp } + 1.dp
    }
}

/**
 * The width of the widest icon of [rows], drawn as [WidgetIconText] draws it: 18 dp for an icon of
 * the list, and the text's own width for an emoji or a letter in [style]. 0 for rows without icons.
 */
@Composable
internal fun iconWidth(rows: List<WidgetSpec.Row>, style: TextStyle): Dp {
    val icons = rows.mapNotNull { it.icon }
    if (icons.isEmpty()) return 0.dp
    return maxOf(18.dp, widestText(icons.filter { widgetIconDrawable(it) == null }, style))
}

/**
 * A row's place for its range ([RangeBars]): its bar, or the room for one when the row has none, then
 * its trailing text in [style] and [muted], at the end of the column the rows share for those.
 * [color] is the bar's, and [under] the color beneath it, for the ring around a value's dot.
 */
@Composable
internal fun RangeSlot(
    row: WidgetSpec.Row,
    bars: RangeBars,
    color: Color,
    under: Color,
    style: TextStyle,
    muted: Color
) {
    val gap = WidgetWords.RANGE_GAP_DP.dp
    Box(
        Modifier
            .padding(start = gap)
            .width(bars.bar)
    ) {
        row.range?.let { RangeBar(it, bars.scale, color, under, Modifier.fillMaxWidth()) }
    }
    if (bars.trailing > 0.dp) {
        Box(
            Modifier
                .padding(start = gap)
                .width(bars.trailing),
            contentAlignment = Alignment.CenterEnd
        ) {
            row.trailing?.let {
                Text(
                    cardText(it),
                    style = style,
                    color = muted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/** A range bar's line ([RangeBar]): 4 dp, as the mockup draws it. */
private val RANGE_LINE = 4.dp

/** A range's value is a dot this wide, with a ring of [RANGE_RING] around it. */
private val RANGE_DOT = 8.dp
private val RANGE_RING = 2.dp

/**
 * One row's [range] on its list's [scale] ([WidgetWords.rangePlace]): a capsule from its low to its
 * high, on a track as long as the scale. Both are [RANGE_LINE] thick with round ends, in [color]
 * on a lighter tone of it.
 * - A value is a dot in [color], with a ring in [under] (the color beneath the bar), so it stays
 *   clear of the capsule, as a chart's last point does ([Plot]).
 * - A range of one number is as long as the line is thick, so it still shows.
 * - Right to left, the scale runs from the right.
 * - It stands still, and it is no item for a screen reader: the row's text says the numbers.
 */
@Composable
internal fun RangeBar(
    range: WidgetSpec.Range,
    scale: Pair<Float, Float>,
    color: Color,
    under: Color,
    modifier: Modifier = Modifier
) {
    val place = WidgetWords.rangePlace(range, scale)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier
            .height(RANGE_DOT + RANGE_RING * 2)
            .testTag("range-bar")
    ) {
        val line = RANGE_LINE.toPx()
        val y = size.height / 2
        val round = CornerRadius(line / 2)
        fun x(f: Float) = size.width * (if (rtl) 1f - f else f)
        drawRoundRect(
            color.copy(alpha = 0.24f),
            Offset(0f, y - line / 2),
            Size(size.width, line),
            round
        )
        val a = minOf(x(place.start), x(place.end))
        val b = maxOf(x(place.start), x(place.end))
        val length = maxOf(b - a, line).coerceAtMost(size.width)
        val from = ((a + b - length) / 2).coerceIn(0f, size.width - length)
        drawRoundRect(color, Offset(from, y - line / 2), Size(length, line), round)
        place.value?.let { v ->
            val dot = Offset(x(v), y)
            drawCircle(under, radius = RANGE_DOT.toPx() / 2 + RANGE_RING.toPx(), center = dot)
            drawCircle(color, radius = RANGE_DOT.toPx() / 2, center = dot)
        }
    }
}

/**
 * The press of a list's [row] with a tap: its own tap, and the card's long-press handed on
 * ([LocalCardLongPress]). A row with `done` is a checkbox for a screen reader, with its state. A
 * row whose range has no trailing text beside it says the range in words ([WidgetWords.rowRange]).
 * No press for a row without a tap, or where nothing takes taps.
 */
@Composable
internal fun rowPress(row: WidgetSpec.Row, onTap: ((WidgetTap) -> Unit)?): Modifier {
    val tap = row.tap
    val done = row.done
    val longPress = LocalCardLongPress.current
    val range = WidgetWords.rowRange(row)
    return if (tap != null && onTap != null) {
        Modifier
            .combinedClickable(
                onClickLabel = tap.label,
                role = if (done != null) Role.Checkbox else Role.Button,
                onLongClickLabel = if (longPress != null) "arrange widgets" else null,
                onLongClick = longPress,
                onClick = { onTap(tap) },
            )
            .then(
                if (done == null && range == null) Modifier
                else Modifier.semantics {
                    if (done != null) toggleableState = ToggleableState(done)
                    // Said after the row's words. A checkbox then says its state in words too.
                    if (range != null) stateDescription =
                        listOfNotNull(range, WidgetWords.doneWord(done)).joinToString(", ")
                },
            )
    } else {
        Modifier
    }
}

/**
 * A row's checkbox, as the mockup draws it: a rounded square, 18 dp. Open: an outline in the
 * quiet text color. Done: filled with [color], with a check. The row says its state to a screen reader.
 */
@Composable
internal fun RowCheck(done: Boolean, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(18.dp)
            .clip(shape)
            .then(
                if (done) Modifier.background(color) else Modifier.border(
                    2.dp,
                    scheme.onSurfaceVariant,
                    shape
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(
            painterResource(R.drawable.wi_check),
            contentDescription = null,
            tint = scheme.surfaceContainerHigh,
            modifier = Modifier.size(14.dp)
        )
    }
}
