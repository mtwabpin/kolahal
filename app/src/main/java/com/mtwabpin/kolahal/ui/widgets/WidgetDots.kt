/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.graphics.shapes.Morph
import com.mtwabpin.kolahal.ui.home.rememberToday
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.sqrt
import androidx.compose.ui.platform.LocalLocale

/*
 * A chart with `style = "dots"`: a dot for each day, the last one today, laid out by [DotCalendar].
 * Plain and Tonal draw it here ([DotsBody]). Bold, Split and Shape put [Dots] where their chart goes.
 */

/** A week's row is at least this tall: a calendar has as many weeks as fit at this height. */
private val WEEK_MIN = 22.dp

/** A dot is at most this wide, however large the card. */
private val DOT_MAX = 20.dp

/** Under the value line, a row of dots needs this much height. With less, the value goes beside it. */
private val STRIP_MIN = 14.dp

/** Today's dot of yes-or-no days: a circle while the day is open, into a cookie once it is done. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val TODAY_MORPH: Morph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/**
 * `chart` with `style = "dots"`, in Plain and Tonal ([ChartBody]).
 * - Small: the value over the last seven days in a row.
 * - Wide: the value with its label after it, over the last seven days in a row, as the mockup's
 *   2×1 has them. A box too low for both (a large font) has them side by side.
 * - Large: the value line over the calendar, with as many weeks as fit. From [CHART_SIDE_FROM]
 *   wide, the value and its label are at the start and the calendar beside them.
 * The card's description says the days for a screen reader ([WidgetWords.dotsSummary]).
 */
@Composable
internal fun DotsBody(spec: WidgetSpec.Chart, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    val value = spec.value
    val label = spec.label
    val words = value != null || label != null
    when (box) {
        WidgetSizes.Box.LARGE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            if (words && maxWidth >= CHART_SIDE_FROM) {
                val start = min(maxWidth * 0.3f, 120.dp)
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(start)
                            .padding(top = 4.dp)
                    ) {
                        if (value != null) BigText(value, valueStyle(box))
                        if (label != null) Text(
                            label,
                            style = type.bodyMedium,
                            color = muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Dots(
                        spec,
                        color,
                        weeks = true,
                        Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (words) ValueLine(value, label, type.titleLarge, type.bodyMedium, muted)
                    // The days' letters keep the weeks clear of the value line: a 2×2 card has room for four weeks.
                    Dots(
                        spec, color, weeks = true, Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }

        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val line = with(LocalDensity.current) { type.titleLarge.lineHeight.toDp() }
            // Beside the words, the dots keep their room: seven days of 12 to 24 dp each. The words give way first.
            val dotsWidth = min(
                maxWidth * 0.6f,
                24.dp * DotCalendar.DAYS
            ).coerceAtLeast(12.dp * DotCalendar.DAYS)
            if (!words || maxHeight - line >= STRIP_MIN) {
                Column(Modifier.fillMaxSize()) {
                    if (words) ValueLine(value, label, type.titleLarge, type.bodySmall, muted)
                    Dots(
                        spec, color, weeks = false, Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .padding(end = 12.dp)
                            .weight(1f, fill = false)
                    ) {
                        if (value != null) BigText(value, type.titleLarge)
                        if (label != null) Text(
                            label,
                            style = type.bodySmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Dots(
                        spec, color, weeks = false, Modifier
                            .width(dotsWidth)
                            .fillMaxHeight()
                    )
                }
            }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (value != null) BigText(value, type.titleMedium)
            Dots(
                spec, color, weeks = false, Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

/** A value with its label after it, on one line: "5 Days in a row". The label gives way first. */
@Composable
private fun ValueLine(
    value: String?,
    label: String?,
    valueStyle: TextStyle,
    labelStyle: TextStyle,
    muted: Color
) {
    Row(Modifier.fillMaxWidth()) {
        if (value != null) BigText(value, valueStyle, Modifier.alignByBaseline())
        if (label != null) {
            Text(
                label,
                style = labelStyle,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = if (value != null) 6.dp else 0.dp)
                    .weight(1f, fill = false)
                    .alignByBaseline(),
            )
        }
    }
}

/**
 * The colors of the dots: [on] for a day done or a value, [off] for a day missed, [check] for
 * the check on today's cookie, [later] for the days to come, [ring] for the ring around today.
 */
@Immutable
private data class DotPaint(
    val on: Color,
    val off: Color,
    val check: Color,
    val later: Color,
    val ring: Color
)

/**
 * The points of [spec] as dots in [color], a dot for each day, the last one today ([DotCalendar]).
 * - With [weeks], a calendar: a row for each week, as many as fit at [WEEK_MIN] a row, under the
 *   first letters of the days where there is room. Without: the last seven days in one row, today
 *   at the end.
 * - Points that are all 0 or 1 ([DotCalendar.yesNo]): a day that is 1 is a filled dot, a day that
 *   is 0 a ring in the card's quiet color. So no day rests on color alone.
 * - Any other points: every dot is filled, and its area grows with its value, on the chart's scale:
 *   `min` and `max`, or else the lowest and the highest point shown.
 * - Today's dot is larger. For yes-or-no days it is a ring in [color] while it is 0, and a filled
 *   cookie with a check once it is 1. When it turns 1 while the card can be seen, it grows into the
 *   cookie on a short spring; otherwise it is simply there. With the phone's animations off,
 *   Compose ends the spring at once. For other points it has a ring around it, in the text color.
 * - The days after today in this week are small dots in the outline color.
 * - Right to left, the days go from the right.
 * - It says nothing to a screen reader: the card's description has the days in words.
 */
@Composable
internal fun Dots(
    spec: WidgetSpec.Chart,
    color: Color,
    weeks: Boolean,
    modifier: Modifier = Modifier
) {
    val points = spec.points
    if (points.isEmpty()) {
        Spacer(modifier)
        return
    }
    val today = rememberToday()
    // The last point is the day the content was set: a card that hasn't run since midnight keeps its
    // days under their own weekdays, and has no today's mark until the run brings today. Today when not known.
    val setAt = LocalContentSetAt.current
    val last = remember(setAt, today) {
        if (setAt > 0) minOf(
            java.time.Instant.ofEpochMilli(setAt).atZone(java.time.ZoneId.systemDefault())
                .toLocalDate(), today
        ) else today
    }
    val lastIsToday = last == today
    val locale = LocalLocale.current.platformLocale
    val first = remember(locale) { DotCalendar.firstDay(locale) }
    val column = DotCalendar.column(last.dayOfWeek, first)
    val yesNo = remember(points) { DotCalendar.yesNo(points) }
    val grown = animateFloatAsState(
        if (yesNo && lastIsToday && points.last() == 1f) 1f else 0f,
        if (cardSeen()) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
        label = "today",
    )
    val looks = lookColors()
    val paint = DotPaint(
        color,
        looks.muted,
        looks.onAccent,
        MaterialTheme.colorScheme.outlineVariant,
        looks.content
    )
    BoxWithConstraints(modifier) {
        if (!weeks) {
            val cells = remember(points.size) { DotCalendar.strip(points.size) }
            DotsCanvas(
                spec,
                cells,
                rows = 1,
                later = IntRange.EMPTY,
                yesNo,
                lastIsToday,
                grown,
                paint,
                Modifier.fillMaxSize()
            )
            return@BoxWithConstraints
        }
        val letters =
            with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
        val need = DotCalendar.weeks(points.size, column)
        fun fit(room: Dp) = (room / WEEK_MIN).toInt().coerceIn(1, need)
        // The days' letters only where they cost no week.
        val lettered = maxHeight - letters >= WEEK_MIN && fit(maxHeight - letters) == fit(maxHeight)
        val room = if (lettered) maxHeight - letters else maxHeight
        val rows = fit(room)
        // This week alone would show no day before its first: the last seven days in one row instead.
        if (rows == 1 && need > 1) {
            val strip = remember(points.size) { DotCalendar.strip(points.size) }
            DotsCanvas(
                spec,
                strip,
                rows = 1,
                later = IntRange.EMPTY,
                yesNo,
                lastIsToday,
                grown,
                paint,
                Modifier.fillMaxSize()
            )
            return@BoxWithConstraints
        }
        // The rows share the room, but none is taller than a day is wide.
        val row = min(room / rows, maxWidth / DotCalendar.DAYS)
        val cells =
            remember(points.size, column, rows) { DotCalendar.cells(points.size, column, rows) }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            if (lettered) DayLetters(
                first,
                if (lastIsToday) column else -1,
                locale,
                looks.muted,
                looks.content,
                Modifier.height(letters)
            )
            DotsCanvas(
                spec,
                cells,
                rows,
                DotCalendar.later(column),
                yesNo,
                lastIsToday,
                grown,
                paint,
                Modifier
                    .fillMaxWidth()
                    .height(row * rows)
            )
        }
    }
}

/** The first letters of the days over a calendar, from [first] on. Today's ([today], a column) is bold, in [strong]. A screen reader skips them. */
@Composable
private fun DayLetters(
    first: DayOfWeek,
    today: Int,
    locale: Locale,
    quiet: Color,
    strong: Color,
    modifier: Modifier
) {
    val style = MaterialTheme.typography.labelSmall
    val names = remember(first, locale) { DotCalendar.initials(first, locale) }
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        names.forEachIndexed { i, name ->
            val now = i == today
            Text(
                name,
                style = if (now) style.copy(fontWeight = FontWeight.Bold) else style,
                color = if (now) strong else quiet,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The dots of [cells] in [rows] rows of 7 columns, with small dots in the columns of [later] in
 * the last row. Only the drawing reads [grown], so today's spring draws again and composes nothing.
 */
@Composable
private fun DotsCanvas(
    spec: WidgetSpec.Chart,
    cells: List<DotCalendar.Cell>,
    rows: Int,
    later: IntRange,
    yesNo: Boolean,
    lastIsToday: Boolean,
    grown: State<Float>,
    paint: DotPaint,
    modifier: Modifier
) {
    val points = spec.points
    // The sizes' scale: the chart's own, or else the points shown.
    val scale = remember(
        points,
        cells,
        spec.min,
        spec.max
    ) { WidgetWords.scale(cells.map { points[it.index] }, spec.min, spec.max, bars = false) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier.testTag("widget-dots")) {
        if (cells.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val wide = size.width / DotCalendar.DAYS
        val tall = size.height / rows
        val dot = minOf(minOf(wide, tall) * 0.62f, DOT_MAX.toPx())
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun center(row: Int, column: Int): Offset {
            val x = (column + 0.5f) * wide
            return Offset(if (rtl) size.width - x else x, (row + 0.5f) * tall)
        }
        for (c in later) drawCircle(
            paint.later,
            maxOf(1.5.dp.toPx(), dot * 0.15f),
            center(rows - 1, c)
        )
        val (low, high) = scale
        for (cell in cells) {
            val v = points[cell.index]
            val at = center(cell.row, cell.column)
            val today = lastIsToday && cell.index == points.lastIndex
            when {
                yesNo && today -> todayMark(at, dot, grown.value, paint, path, matrix)
                yesNo && v == 1f -> drawCircle(paint.on, dot / 2, at)
                yesNo -> {
                    val line = maxOf(1.5.dp.toPx(), dot * 0.12f)
                    drawCircle(paint.off, dot / 2 - line / 2, at, style = Stroke(line))
                }

                else -> {
                    // The area grows with the value. The lowest is still a dot: about a tenth of the largest's area.
                    val most = dot / 2
                    val least = most * 0.35f
                    val f = WidgetWords.fraction(v - low, high - low)
                    drawCircle(
                        paint.on,
                        sqrt(least * least + f * (most * most - least * least)),
                        at
                    )
                    if (today) {
                        val line = maxOf(1.25.dp.toPx(), dot * 0.1f)
                        drawCircle(
                            paint.ring,
                            most + maxOf(1.5.dp.toPx(), dot * 0.15f) + line / 2,
                            at,
                            style = Stroke(line)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Today's dot of yes-or-no days at [at], where the other days' dots are [dot] wide. At [grown] 0
 * it is a ring in [DotPaint.on], larger than the others; at 1 a filled cookie with a check, larger
 * still. A spring takes [grown] a little past 1 and back, so the cookie grows past its size and settles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DrawScope.todayMark(
    at: Offset,
    dot: Float,
    grown: Float,
    paint: DotPaint,
    path: Path,
    matrix: Matrix
) {
    val done = grown.coerceIn(0f, 1f)
    val side = dot * (1.3f + 0.25f * grown)
    TODAY_MORPH.toPath(done, path)
    matrix.reset()
    matrix.scale(side, side)
    path.transform(matrix)
    path.translate(at - path.getBounds().center)
    if (done > 0f) drawPath(path, paint.on.copy(alpha = paint.on.alpha * done))
    drawPath(path, paint.on, style = Stroke(maxOf(2.dp.toPx(), dot * 0.14f)))
    if (done > 0.5f) {
        // The check as the mockup draws it, in a cookie 40 units across.
        val u = side / 40f
        val ink = paint.check.copy(alpha = paint.check.alpha * (done - 0.5f) * 2f)
        val line = maxOf(1.5.dp.toPx(), 3.4f * u)
        val corner = at + Offset(-2.5f * u, 6f * u)
        drawLine(ink, at + Offset(-8f * u, 0.5f * u), corner, line, StrokeCap.Round)
        drawLine(ink, corner, at + Offset(8f * u, -5f * u), line, StrokeCap.Round)
    }
}
