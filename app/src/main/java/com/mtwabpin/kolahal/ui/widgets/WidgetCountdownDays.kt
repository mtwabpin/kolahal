/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---- a countdown that counts days: a dot a day, and a calendar page ----------------------------

/** The most room between two dots' middles, and the least: under it the dots are left out. */
private const val DOT_PITCH_MOST = 18f
private const val DOT_PITCH_LEAST = 7f

/** A dot still to come, one that went out, and today's ring, as parts of the room between dots (the mockup's 5, 2.5 and 7.5 of 16). */
private const val DOT_LEFT = 0.31f
private const val DOT_GONE = 0.15f
private const val DOT_RING = 0.46f

/**
 * `countdown` with `style = "dots"` or `"page"` ([CountdownDays]), in every look but Poster, which
 * keeps its number ([CountdownBody] sends it here).
 * - `dots`: the days left as a grid of dots, today's ringed, the days over (with `from`) as small
 *   faint dots; the number and "days left" under them, and the label. Over 60 days a dot is a week
 *   and the card says so; over 60 weeks the card has the number alone.
 * - `page`: the moment's date as a calendar page, the month on a band in the card's color and the
 *   day large, with the card's name, "In 26 days" and the weekday beside or under it. A large card
 *   has the bar from `from`.
 * - The words and the dots change at midnight and, on the moment's day, when the moment passes.
 *   They are made again on the minute's tick ([rememberNow]), with no run of the script.
 */
@Composable
internal fun CountdownDaysBody(spec: WidgetSpec.Countdown, box: WidgetSizes.Box) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val now = minute.time / 1000
    val clock24 = rememberClock24()
    val look = LocalWidgetLook.current
    val role = roleColor(spec.base)
    if (spec.style == CountdownDays.PAGE) {
        val locale = LocalLocale.current.platformLocale
        val page = remember(spec.to, spec.reached, minute, zone, clock24, locale) {
            CountdownDays.page(spec.to, now, zone, spec.reached, clock24, locale)
        }
        val bar = remember(spec.from, spec.to, minute) {
            WidgetWords.countdownFraction(spec.from, spec.to, now)?.let(WidgetWords::barStep)
        }
        PageCountdown(page, bar, box, look, role)
    } else {
        val dots = remember(spec.to, spec.from, minute, zone) { CountdownDays.dots(spec.to, spec.from, now, zone) }
        val count = remember(spec.to, spec.reached, minute, zone) { CountdownDays.count(spec.to, now, zone, spec.reached) }
        val moment = remember(spec.to, minute, zone, clock24) {
            WidgetWords.countdownEnds(now, spec.to, now, zone, clock24).second
        }
        // The label, and when a dot is a week, that it is.
        val under = listOfNotNull(spec.label, CountdownDays.A_DOT_A_WEEK.takeIf { dots?.week == true })
            .joinToString(" · ").ifEmpty { null }
        DotsCountdown(dots, count, moment, under, box, look, role)
    }
}

// ---- dots ----------------------------------------------------------------------------------------

/** The dots countdown in [look]: a ring of dots in Shape's square card, the dots beside a circle in its other cards, tiles in Split. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DotsCountdown(
    dots: CountdownDays.Dots?,
    count: WidgetWords.Count,
    moment: String,
    under: String?,
    box: WidgetSizes.Box,
    look: WidgetLook,
    role: Color
) {
    val c = lookColors(look, role)
    val type = MaterialTheme.typography
    val title = LocalUntitled.current
    when {
        look == WidgetLook.SHAPE && LocalCardShaped.current -> DotsRing(dots, count, title, box, c)
        look == WidgetLook.SHAPE -> BesideShape(MaterialShapes.Circle, c.shapeFill, inShape = { side ->
            DaysCount(count, shapeValueStyle(side, 0.32f), c.content, stacked = true, modifier = Modifier.padding(horizontal = side / 8))
        }) {
            SmallTitle(title, c.content)
            if (dots != null) DayDots(dots, c.accent, c.content, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
            else under?.let { Text(it, style = type.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }

        look == WidgetLook.SPLIT -> SplitDots(dots, count, moment, under, box, c)
        else -> PlainDots(dots, count, moment, under, box, look == WidgetLook.BOLD, c)
    }
}

/**
 * Plain, Tonal and Bold: a large card has the name and the moment at the top, the dots, then the
 * number with "days left" and the label (the mockup); a wide one the number and its words at the
 * start and the dots beside; a small one the number alone. Bold has the number in its type, in the
 * card's color.
 */
@Composable
private fun PlainDots(
    dots: CountdownDays.Dots?,
    count: WidgetWords.Count,
    moment: String,
    under: String?,
    box: WidgetSizes.Box,
    bold: Boolean,
    c: LookColors
) {
    val type = MaterialTheme.typography
    val title = LocalUntitled.current
    val value = StyledText.capitalized(count.value)
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallTitle(title, c.content, Modifier.weight(1f))
                Text(moment, style = type.labelMedium, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                if (dots != null) DayDots(dots, c.accent, c.content, Modifier.fillMaxSize())
            }
            if (bold) BoldValue(value, count.unit, 56.sp, valueInk(c.accent, c.card), c.content)
            else DaysCount(count, type.displaySmall, c.content)
            under?.let {
                Text(it, style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            val fits = dots != null &&
                    CountdownDays.grid(dots.count, width.value / 2, height.value, DOT_PITCH_MOST).pitch >= DOT_PITCH_LEAST
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SmallTitle(title, c.content)
                    if (bold) BoldValue(value, null, 44.sp, valueInk(c.accent, c.card))
                    else BigText(value, type.headlineMedium, color = c.content)
                    UnitAndLabel(count.unit, under, c.muted)
                }
                if (fits && dots != null) DayDots(
                    dots,
                    c.accent,
                    c.content,
                    Modifier
                        .width(width / 2)
                        .fillMaxHeight()
                        .padding(start = 12.dp)
                )
            }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (bold) BoldValue(value, null, 40.sp, valueInk(c.accent, c.card))
            else BigText(value, type.titleLarge, color = c.content)
            count.unit?.let { Text(it, style = type.labelSmall, color = c.muted, maxLines = 1) }
        }
    }
}

/**
 * The line under a narrow card's number: "days left · A dot a day" when it fits on the line whole,
 * else [unit] alone ("days left"), never cut after the unit. Without a [unit] (on the day), the
 * [label] alone, cut with "…" if it must be.
 */
@Composable
private fun UnitAndLabel(unit: String?, label: String?, color: Color) {
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = constraints.maxWidth
        val both = listOfNotNull(unit, label).joinToString(" · ")
        val words = remember(both, unit, style, room) {
            if (unit == null || label == null) both
            else if (measurer.measure(both, style, maxLines = 1, softWrap = false).size.width <= room) both
            else unit
        }
        if (words.isNotEmpty()) Text(words, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Split's dots: the name, the moment and the dots in one tile, the number and its words in another; a small card has the number in one tile. */
@Composable
private fun SplitDots(
    dots: CountdownDays.Dots?,
    count: WidgetWords.Count,
    moment: String,
    under: String?,
    box: WidgetSizes.Box,
    c: LookColors
) {
    val type = MaterialTheme.typography
    val title = LocalUntitled.current
    val gap = SplitCorners.GAP
    val numberTile: @Composable (Shape, Modifier) -> Unit = { shape, modifier ->
        Tile(shape, c.tile, modifier) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (box == WidgetSizes.Box.LARGE) {
                    DaysCount(count, type.headlineLarge, c.content)
                    under?.let { Text(it, style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    // A narrow tile has no room for "days left" beside the number: it goes under it, with the label.
                    BigText(StyledText.capitalized(count.value), type.headlineMedium, color = c.content)
                    UnitAndLabel(count.unit, under, c.muted)
                }
            }
        }
    }
    when {
        box == WidgetSizes.Box.SMALL || dots == null -> numberTile(SplitCorners.inColumn(0, 1), Modifier.fillMaxSize())
        box == WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Tile(SplitCorners.inColumn(0, 2), c.tile, Modifier.fillMaxWidth().weight(1.5f)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SmallTitle(title, c.content, Modifier.weight(1f))
                        Text(moment, style = type.labelMedium, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                    DayDots(dots, c.accent, c.content, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
                }
            }
            numberTile(SplitCorners.inColumn(1, 2), Modifier.fillMaxWidth().weight(1f))
        }

        else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            numberTile(SplitCorners.inRow(0, 2), Modifier.weight(1f).fillMaxHeight())
            Tile(SplitCorners.inRow(1, 2), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                DayDots(dots, c.accent, c.content, Modifier.fillMaxSize().padding(10.dp))
            }
        }
    }
}

/** Shape's square card: a circle of the role's container with the dots around its edge, from the top clockwise, and the number in the middle. */
@Composable
private fun DotsRing(
    dots: CountdownDays.Dots?,
    count: WidgetWords.Count,
    title: String?,
    box: WidgetSizes.Box,
    c: LookColors
) {
    val type = MaterialTheme.typography
    val small = box == WidgetSizes.Box.SMALL
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = min(maxWidth.value, maxHeight.value).dp
        Box(
            Modifier
                .align(Alignment.Center)
                .size(side)
                .clip(CircleShape)
                .background(c.shapeFill)
        )
        if (dots != null) Canvas(
            Modifier
                .align(Alignment.Center)
                .size(side)
                .clearAndSetSemantics {}
        ) {
            val radius = size.minDimension / 2 * 0.84f
            val step = 2 * PI.toFloat() / dots.count
            val pitch = min(radius * step, DOT_PITCH_MOST.dp.toPx())
            for (i in 0 until dots.count) {
                val angle = -PI.toFloat() / 2 + i * step
                val at = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
                dot(dots, i, at, pitch, c.accent, c.content)
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = side * 0.22f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DaysCount(count, if (small) type.titleLarge else type.displayMedium.copy(fontSize = 44.sp), c.content, stacked = true)
            if (!small) title?.let {
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

/**
 * The dots in a grid as large as fits ([CountdownDays.grid]), centered: a dot still to come in
 * [color], one that went out small and faint, today's ringed in [ring]. Nothing when they would be
 * too small to see. Right to left the days go from the right ([CountdownDays.dotX]). A screen
 * reader hears the card's words, not the dots.
 */
@Composable
private fun DayDots(dots: CountdownDays.Dots, color: Color, ring: Color, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val grid = CountdownDays.grid(dots.count, size.width, size.height, DOT_PITCH_MOST.dp.toPx())
        if (grid.pitch < DOT_PITCH_LEAST.dp.toPx()) return@Canvas
        val top = (size.height - grid.rows * grid.pitch) / 2
        val rtl = layoutDirection == LayoutDirection.Rtl
        for (i in 0 until dots.count) {
            val at = Offset(
                CountdownDays.dotX(i, grid, size.width, rtl),
                top + (i / grid.columns + 0.5f) * grid.pitch
            )
            dot(dots, i, at, grid.pitch, color, ring)
        }
    }
}

/** Dot [i] of [dots] at [at], [pitch] from the next: gone, still to come, and ringed when it is today. */
private fun DrawScope.dot(dots: CountdownDays.Dots, i: Int, at: Offset, pitch: Float, color: Color, ring: Color) {
    if (i < dots.gone) drawCircle(color.copy(alpha = 0.35f), pitch * DOT_GONE, at)
    else drawCircle(color, pitch * DOT_LEFT, at)
    if (i == dots.today) drawCircle(ring, pitch * DOT_RING, at, style = Stroke(maxOf(1.dp.toPx(), pitch * 0.09f)))
}

/** The number in [style] and its unit ("days left") smaller after it, or under it when [stacked]. */
@Composable
private fun DaysCount(
    count: WidgetWords.Count,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    stacked: Boolean = false
) {
    val type = MaterialTheme.typography
    val unitStyle = type.titleSmallEmphasized
    val value = StyledText.capitalized(count.value)
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(value, style, color = color)
            count.unit?.let { Text(it, style = type.labelSmallEmphasized, color = color, maxLines = 1) }
        }
    } else {
        Row(modifier) {
            BigText(value, style, Modifier.weight(1f, fill = false).alignByBaseline(), color = color)
            count.unit?.let {
                Text(
                    it,
                    style = unitStyle,
                    color = color,
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

// ---- page ----------------------------------------------------------------------------------------

/**
 * The page countdown in [look]. Plain, Tonal and Bold: the page on a tile at the start, the card's
 * name, "In 26 days" in the card's color and the weekday beside it (the mockup); a large square
 * card has the page and the name at the top, "In 26 days" large at the bottom and the bar under it.
 * Bold has the page on the role's container and the day in its type. Split puts the page and the
 * words in tiles of their own. Shape cuts the page to a four-sided cookie.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageCountdown(page: CountdownDays.Page, bar: Float?, box: WidgetSizes.Box, look: WidgetLook, role: Color) {
    val c = lookColors(look, role)
    val title = LocalUntitled.current
    val bold = look == WidgetLook.BOLD
    val paper = when (look) {
        WidgetLook.BOLD -> roleContainer(role)
        WidgetLook.SPLIT -> segmentColor()
        else -> c.tile
    }
    when {
        look == WidgetLook.SHAPE && LocalCardShaped.current ->
            CalendarPage(page, c, c.shapeFill, MaterialShapes.Cookie4Sided.toShape(), Modifier.fillMaxSize(), foot = page.left)

        look == WidgetLook.SHAPE -> BesideShape(MaterialShapes.Cookie4Sided, c.shapeFill, inShape = {
            CalendarPage(page, c, Color.Transparent, null, Modifier.fillMaxSize())
        }) { PageWords(page, title, c, null, large = false) }

        look == WidgetLook.SPLIT -> SplitPage(page, bar, box, c, paper)
        box == WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CalendarPage(page, c, paper, RoundedCornerShape(14.dp), Modifier.weight(1f).fillMaxWidth(0.8f), bold = bold)
            Text(
                page.left,
                style = MaterialTheme.typography.labelSmallEmphasized,
                color = c.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            val stacked = box == WidgetSizes.Box.LARGE && width < height * 1.6f
            if (stacked) {
                // A square card: the page and the name at the top, when it is at the bottom.
                val side = min(min(width.value * 0.5f, height.value * 0.55f), 96f).dp
                Column(Modifier.fillMaxSize()) {
                    Row {
                        CalendarPage(page, c, paper, RoundedCornerShape(16.dp), Modifier.size(side * 0.94f, side), bold = bold)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            SmallTitle(title, c.content, lines = 2)
                            Text(page.weekday, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    BigText(page.left, MaterialTheme.typography.headlineSmall, color = c.accent)
                    bar?.let { LevelBar(it, moving = false, c.accent, Modifier.padding(top = 6.dp), wavy = true) }
                }
            } else {
                // A wide card: the page at the start, as tall as the card, up to 120 dp.
                val side = min(height.value, 120f).dp
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    CalendarPage(page, c, paper, RoundedCornerShape(16.dp), Modifier.size(side * 0.94f, side), bold = bold)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        PageWords(page, title, c, bar.takeIf { width >= 280.dp }, large = box == WidgetSizes.Box.LARGE)
                    }
                }
            }
        }
    }
}

/** The words beside a page: the card's name, "In 26 days" in the card's color, the weekday, and the bar under them when given. */
@Composable
private fun PageWords(page: CountdownDays.Page, title: String?, c: LookColors, bar: Float?, large: Boolean) {
    val type = MaterialTheme.typography
    title?.let {
        Text(
            it,
            style = type.titleSmallEmphasized,
            color = c.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Text(
        page.left,
        style = (if (large) type.headlineSmall else type.titleMedium).copy(fontWeight = FontWeight.Bold),
        color = c.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(page.weekday, style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    bar?.let { LevelBar(it, moving = false, c.accent, Modifier.padding(top = 6.dp), wavy = true) }
}

/** Split's page: the page in a tile of its own at the start, the words in the next; a large square card has "In 26 days" and the bar in a tile under them. */
@Composable
private fun SplitPage(page: CountdownDays.Page, bar: Float?, box: WidgetSizes.Box, c: LookColors, paper: Color) {
    val type = MaterialTheme.typography
    val title = LocalUntitled.current
    val gap = SplitCorners.GAP
    if (box == WidgetSizes.Box.SMALL) {
        Tile(SplitCorners.inColumn(0, 1), c.tile, Modifier.fillMaxSize()) {
            CalendarPage(page, c, paper, RoundedCornerShape(12.dp), Modifier.fillMaxSize().padding(10.dp))
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        val stacked = box == WidgetSizes.Box.LARGE && width < height * 1.6f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth().weight(1.4f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                val pageSide = if (stacked) (height.value * 0.58f).dp else height
                Tile(
                    SplitCorners.tile(topStart = true, topEnd = false, bottomEnd = false, bottomStart = !stacked),
                    c.tile,
                    Modifier.width(min(pageSide.value, width.value * 0.45f).dp).fillMaxHeight()
                ) {
                    CalendarPage(page, c, paper, RoundedCornerShape(12.dp), Modifier.fillMaxSize().padding(8.dp))
                }
                Tile(
                    SplitCorners.tile(topStart = false, topEnd = true, bottomEnd = !stacked, bottomStart = false),
                    c.tile,
                    Modifier.weight(1f).fillMaxHeight()
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp)) {
                        if (stacked) {
                            SmallTitle(title, c.content, lines = 2)
                            Text(page.weekday, style = type.bodySmall, color = c.muted, maxLines = 1)
                        } else {
                            PageWords(page, title, c, bar.takeIf { width >= 280.dp }, large = false)
                        }
                    }
                }
            }
            if (stacked) Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp)) {
                    BigText(page.left, type.headlineSmall, color = c.accent)
                    bar?.let { LevelBar(it, moving = false, c.accent, Modifier.padding(top = 4.dp), wavy = true) }
                }
            }
        }
    }
}

/**
 * A calendar page: the month on a band of the card's color at the top, the day large under it,
 * on [paper] cut to [shape] (none: the shape around it cuts it). With [foot], a short line under
 * the day ("In 26 days"). [bold] sets the day in Bold's type. The sizes follow the page's own.
 */
@Composable
private fun CalendarPage(
    page: CountdownDays.Page,
    c: LookColors,
    paper: Color,
    shape: Shape?,
    modifier: Modifier,
    foot: String? = null,
    bold: Boolean = false
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        (if (shape != null) modifier.clip(shape) else modifier).background(paper)
    ) {
        val width = maxWidth
        val height = maxHeight
        // Cut by a shape around it (Shape's wide card, no [shape] of its own): the shape's top edge dips
        // in the middle, so the band is deeper and the month smaller and low in it, clear of the dip.
        val cut = shape == null
        val band = height * when {
            cut -> 0.38f
            foot != null -> 0.3f
            else -> 0.27f
        }
        val monthSize = with(density) { min(band.value * (if (cut) 0.4f else 0.5f), 16f).dp.toSp() }
        val daySize = min(height.value * (if (foot != null) 0.36f else 0.5f), width.value * 0.5f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // In a shape the month sits at the band's bottom, where the shape is wide enough for it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(band)
                    .background(c.accent),
                contentAlignment = if (shape == null || foot != null) Alignment.BottomCenter else Alignment.Center
            ) {
                Text(
                    page.month,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = monthSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.em
                    ),
                    color = c.onAccent,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        bottom = when {
                            cut -> band * 0.14f
                            foot != null -> band * 0.12f
                            else -> 0.dp
                        }
                    )
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val size = with(density) { daySize.dp.toSp() }
                if (bold) BoldText(page.day, size, c.content)
                else {
                    Text(
                        page.day,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = size, lineHeight = size).asValue(),
                        color = c.content,
                        maxLines = 1
                    )
                }
            }
            foot?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = width * 0.18f, end = width * 0.18f, bottom = height * 0.16f)
                )
            }
        }
    }
}
