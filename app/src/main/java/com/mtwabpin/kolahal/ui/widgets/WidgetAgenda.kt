/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLocale

// ---- agenda ---------------------------------------------------------------------------------

/**
 * Whether Kolahal writes a time of day in 24-hour form: its own setting, which the header and the
 * log follow too. It is read again when a setting changes.
 */
@Composable
internal fun rememberClock24(): Boolean {
    val context = LocalContext.current.applicationContext
    val config = remember(context) { Config.get(context) }
    val version by config.version.collectAsStateWithLifecycle()
    return remember(config, version) { config[Keys.CLOCK_24H] }
}

/** Height of a day's heading and of the "+3 more" line, in dp. A larger font makes the heading taller ([WidgetWords.textRowDp]). */
private const val AGENDA_HEAD_DP = 16
private const val AGENDA_MORE_DP = 20

/**
 * The least height of an event's own line under a heading, in dp: one line of text, or two in a
 * narrow box. A larger font makes it as tall as its text, when that is taller.
 */
private const val AGENDA_LINE_DP = 28
private const val AGENDA_NARROW_LINE_DP = 32

/** Under this width an event has its time under its title, not beside it. */
private val AGENDA_NARROW = 120.dp

/** From this width an event has its place at its end. */
private val AGENDA_PLACES = 200.dp

/**
 * `agenda`: events under "Today", "Tomorrow" and dates, as many as fit, then "+3 more".
 * - Kolahal writes the times, in the form the setting says, and drops an event when it has ended.
 *   That happens on the minute's tick ([rememberNow]), with no run of the script and no tick of
 *   its own.
 * - An event is one line: its time, a dot in its color if it has one, its title, and its place at its end in a
 *   box that has room. An event that can be tapped is 44 dp tall ([TAP_ROW_DP]), others 32 dp.
 *   With a larger font, rows and headings are as tall as their text ([WidgetWords.textRowDp]).
 * - A day's heading ("TODAY") is at the top of its first event's row, in the card's color. That
 *   row is as tall as a tap row needs, or as the heading and the event's line need, whichever is
 *   more: 44 dp. So a 4x2 card has two events of today and one of tomorrow, as the mockup has
 *   them, and every event keeps a 44 dp tap. A tap on the heading is a tap on that event.
 * - "+3 more" is the line under the events.
 * - With nothing to show it says the description's `empty` text.
 * - The Now look draws what is on now, the free time and what is next ([NowAgenda]), for [box].
 */
@Composable
internal fun AgendaBody(spec: WidgetSpec.Agenda, onTap: ((WidgetTap) -> Unit)?, box: WidgetSizes.Box) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val days = remember(spec, minute, zone, clock24) {
        WidgetWords.agenda(
            spec,
            minute.time / 1000,
            zone,
            clock24
        )
    }
    if (days.isEmpty()) {
        QuietLine(spec.empty ?: WidgetWords.NO_EVENTS)
        return
    }
    val look = LocalWidgetLook.current
    if (look == WidgetLook.NOW) {
        NowAgenda(spec, minute.time / 1000, zone, clock24, box, onTap)
        return
    }
    if (look == WidgetLook.BOLD || look == WidgetLook.SPLIT || look == WidgetLook.SHAPE) {
        DatedAgenda(look, days, minute.time, zone, clock24, roleColor(spec.base), onTap)
        return
    }
    val color = roleColor(spec.base)
    val muted = lookColors().muted
    // Tonal puts each event on a tile of its own, 2 dp from the next, as a list's rows are.
    val tile = if (look == WidgetLook.TONAL) lookColors().tile else null
    val gapDp = if (tile != null) ROW_GAP_DP else 0
    val type = MaterialTheme.typography
    // An event's text at the phone's font size: its title over its time when narrow, else one line.
    val narrowLinesDp = linesDp(type.bodySmall, type.labelSmall)
    val lineDp = maxOf(linesDp(type.labelLarge), linesDp(type.bodyMedium))
    val headDp = WidgetWords.textRowDp(AGENDA_HEAD_DP, linesDp(type.labelMedium))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < AGENDA_NARROW
        val places = maxWidth >= AGENDA_PLACES
        val textDp = if (narrow) narrowLinesDp else lineDp
        val rowDp = WidgetWords.textRowDp(
            if (days.any { day -> day.lines.any { it.event.tap != null } }) TAP_ROW_DP else 32,
            textDp
        )
        // A day's first row has its heading at its top.
        val headedDp = maxOf(
            rowDp,
            headDp + WidgetWords.textRowDp(
                if (narrow) AGENDA_NARROW_LINE_DP else AGENDA_LINE_DP,
                textDp
            )
        )
        val heightDp = maxHeight.value.toInt()
        val total = days.sumOf { it.lines.size }
        val moreDp = AGENDA_MORE_DP
        // Each row takes its gap too, and the last one has none after it.
        val shown = WidgetWords.agendaShown(
            days.map { it.lines.size },
            heightDp + gapDp,
            headedDp - rowDp,
            rowDp + gapDp,
            moreDp
        )
        // The time column is as wide as the longest time in this form, so the titles line up.
        val timeWidth = if (clock24) 48.dp else 68.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gapDp.dp)) {
            var left = shown
            var usedDp = 0
            var n = 0
            for ((d, day) in days.withIndex()) {
                if (left <= 0) break
                val lines = day.lines.take(left)
                left -= lines.size
                usedDp += headedDp + (lines.size - 1) * rowDp + lines.size * gapDp
                key(d) {
                    lines.forEachIndexed { i, line ->
                        val back = tile?.let { it to segmentShape(n, shown) }
                        n++
                        key(i) {
                            if (i == 0) AgendaRow(
                                line,
                                headedDp.dp,
                                places,
                                narrow,
                                timeWidth,
                                onTap,
                                back
                            ) { DayHeading(day.label, color, headDp) }
                            else AgendaRow(line, rowDp.dp, places, narrow, timeWidth, onTap, back)
                        }
                    }
                }
            }
            if (shown < total && usedDp + AGENDA_MORE_DP <= heightDp) {
                Text(
                    "+${total - shown} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                    modifier = Modifier
                        .height(AGENDA_MORE_DP.dp)
                        .wrapContentHeight()
                )
            }
        }
    }
}

/** A day's heading in an agenda: "TODAY", "TOMORROW", "TUE 20 OCT", in bold capitals in the card's [color], [heightDp] tall. */
@Composable
private fun DayHeading(label: String, color: Color, heightDp: Int) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em
        ),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .height(heightDp.dp)
            .wrapContentHeight(),
    )
}

/**
 * One event: its time, a dot in its color (none without one), its title, and its place at its end
 * when [places]. A day's first event has the day's [heading] at its top, inside its row.
 * A [narrow] row has the time under the title and no dot. An event with a tap takes the press
 * before the card does, so it hands a long-press on to the card, as a list's row does.
 * With [tile] (Tonal) the row is on a tile of that color and shape, its text inside the tile's padding.
 */
@Composable
private fun AgendaRow(
    line: WidgetWords.AgendaLine,
    height: Dp,
    places: Boolean,
    narrow: Boolean,
    timeWidth: Dp,
    onTap: ((WidgetTap) -> Unit)?,
    tile: Pair<Color, Shape>? = null,
    heading: (@Composable () -> Unit)? = null,
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val event = line.event
    val color = event.color?.let { colorOf(it) }
    val press = Modifier.eventPress(event.tap, onTap)
    // Small corners: a larger one would cut the first letter of a heading at the row's top.
    val back = if (tile != null) {
        Modifier
            .clip(tile.second)
            .background(tile.first)
            .then(press)
            .padding(horizontal = 12.dp, vertical = if (heading != null) 3.dp else 0.dp)
    } else {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(press)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .then(back)
    ) {
        heading?.invoke()
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f), verticalAlignment = Alignment.CenterVertically
        ) {
            if (narrow) {
                Column(Modifier.weight(1f)) {
                    Text(
                        event.title,
                        style = type.bodySmall,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        line.time,
                        style = type.labelSmall,
                        color = color ?: lookColors().muted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Text(
                    line.time,
                    style = type.labelLarge,
                    color = lookColors().muted,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .widthIn(min = timeWidth)
                        .padding(end = 8.dp)
                )
                if (color != null) Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    event.title,
                    style = type.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (places) event.where?.let {
                    Text(
                        it,
                        style = type.bodySmall,
                        color = lookColors().muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .widthIn(max = 120.dp)
                    )
                }
            }
        }
    }
}

/**
 * The tap of an event with an action of its own: it takes the press before the card does, so it
 * hands a long-press on to the card, as a list's row does. Nothing without an action or an [onTap].
 */
@Composable
internal fun Modifier.eventPress(tap: WidgetTap?, onTap: ((WidgetTap) -> Unit)?): Modifier {
    val longPress = LocalCardLongPress.current
    return if (tap != null && onTap != null) {
        combinedClickable(
            onClickLabel = tap.label,
            role = Role.Button,
            onLongClickLabel = if (longPress != null) "arrange widgets" else null,
            onLongClick = longPress,
            onClick = { onTap(tap) },
        )
    } else {
        this
    }
}

/** One event of a dated agenda ([DatedAgenda]), with its day: [today] or the day's [label] ("Tomorrow"). */
private class DatedLine(val line: WidgetWords.AgendaLine, val label: String, val today: Boolean)

/** The height of a dated agenda's event, in dp: a row that keeps its tap. */
private const val DATED_ROW_DP = 42

/** From this width a dated agenda has today's date at the start, beside the events; a narrower one has it on top. */
internal val DATED_SIDE = 240.dp

/**
 * The agenda of the Bold, Split and Shape looks: today's date takes a place of its own, and the
 * events are rows of 42 dp beside it (or under it in a narrow box), with no day headings.
 * - Bold: the date in a block of the card's [role] color, the weekday and the month in capitals
 *   around the day at display size. Today's times are in the role color.
 * - Shape: the date in a sunny shape of the role color.
 * - Split: the date in a tile of its own, and each event in a tile with its time in a chip.
 * - An event of another day says so: "Gym · tomorrow", dim (Split: "Tomorrow" at its end).
 * - As many events as fit are shown, never under 42 dp; "+3 more" is left out. Each keeps its tap.
 */
@Composable
private fun DatedAgenda(
    look: WidgetLook,
    days: List<WidgetWords.AgendaDay>,
    nowMs: Long,
    zone: ZoneId,
    clock24: Boolean,
    role: Color,
    onTap: ((WidgetTap) -> Unit)?
) {
    val lines = remember(days) {
        days.flatMap { day ->
            day.lines.map {
                DatedLine(
                    it,
                    day.label,
                    day.label == WidgetWords.TODAY
                )
            }
        }
    }
    val today = remember(nowMs, zone) { Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() }
    val locale = LocalLocale.current.platformLocale
    val weekday = remember(today) { today.format(DateTimeFormatter.ofPattern("EEE", locale)) }
    val month = remember(today) { today.format(DateTimeFormatter.ofPattern("MMM", locale)) }
    val dayNumber = today.dayOfMonth.toString()
    val looks = lookColors(look, role)
    val timeWidth = if (clock24) 44.dp else 64.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val side = maxWidth >= DATED_SIDE
        val heightDp = maxHeight.value.toInt()
        if (look == WidgetLook.SPLIT) {
            SplitAgenda(lines, weekday, dayNumber, month, side, heightDp, looks, onTap)
            return@BoxWithConstraints
        }
        if (side) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (look == WidgetLook.BOLD) {
                    BoldDate(
                        weekday,
                        dayNumber,
                        month.takeIf { heightDp >= 120 },
                        looks,
                        Modifier
                            .width(minOf(96.dp, boxWidth / 4))
                            .fillMaxHeight()
                    )
                } else {
                    SunnyDate(
                        weekday.takeIf { heightDp >= 88 },
                        dayNumber,
                        looks,
                        minOf(boxHeight, 120.dp, boxWidth / 3)
                    )
                }
                DatedRows(
                    lines,
                    (heightDp / DATED_ROW_DP).coerceAtLeast(1),
                    timeWidth,
                    looks,
                    look == WidgetLook.BOLD,
                    onTap,
                    Modifier.weight(1f)
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (look == WidgetLook.BOLD) {
                        Text(
                            dayNumber,
                            style = MaterialTheme.typography.displaySmall.asBold(),
                            color = looks.accent,
                            maxLines = 1
                        )
                    } else {
                        SunnyDate(null, dayNumber, looks, 52.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            weekday.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.08.em
                            ),
                            color = looks.content,
                            maxLines = 1
                        )
                        Text(
                            month.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.08.em
                            ),
                            color = looks.muted,
                            maxLines = 1
                        )
                    }
                }
                DatedRows(
                    lines,
                    ((heightDp - 56) / DATED_ROW_DP).coerceAtLeast(1),
                    timeWidth,
                    looks,
                    look == WidgetLook.BOLD,
                    onTap,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Bold's date: a block of the role color, "FRI" over the day at display size over "NOV" ([month], left out when low). */
@Composable
private fun BoldDate(
    weekday: String,
    day: String,
    month: String?,
    looks: LookColors,
    modifier: Modifier
) {
    val caps = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.08.em
    )
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(looks.accent),
        contentAlignment = Alignment.Center
    ) {
        // The day is as big as the block allows: up to 76 dp, as the mockup has it.
        val sizeDp = minOf(
            76f,
            maxWidth.value * 0.62f,
            (maxHeight.value - if (month != null) 48 else 28) * 0.9f
        )
        val size = with(LocalDensity.current) { sizeDp.dp.toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekday.uppercase(), style = caps, color = looks.onAccent, maxLines = 1)
            Text(
                day,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = size,
                    lineHeight = size
                ).asBold(),
                color = looks.onAccent,
                maxLines = 1
            )
            if (month != null) Text(
                month.uppercase(),
                style = caps,
                color = looks.onAccent,
                maxLines = 1
            )
        }
    }
}

/** Shape's date: the day in a sunny shape of the role color, [side] across, under the weekday where there is room. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SunnyDate(weekday: String?, day: String, looks: LookColors, side: Dp) {
    val size = with(LocalDensity.current) { (side * 0.4f).toSp() }
    Box(
        Modifier
            .size(side)
            .background(looks.accent, MaterialShapes.Sunny.toShape()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (weekday != null) {
                Text(
                    weekday.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.08.em
                    ),
                    color = looks.onAccent,
                    maxLines = 1
                )
            }
            Text(
                day,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = size,
                    lineHeight = size
                ).asBold(),
                color = looks.onAccent,
                maxLines = 1
            )
        }
    }
}

/**
 * The events of a dated agenda: the first [count] of [lines], 42 dp each, centered in their room.
 * Today's time is in the role color and bold; an event of another day is dim, with its day after
 * its title.
 */
@Composable
private fun DatedRows(
    lines: List<DatedLine>,
    count: Int,
    timeWidth: Dp,
    looks: LookColors,
    bold: Boolean,
    onTap: ((WidgetTap) -> Unit)?,
    modifier: Modifier
) {
    val type = MaterialTheme.typography
    val titleStyle =
        if (bold) type.titleSmallEmphasized else type.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold
        )
    val timeStyle = (if (bold) type.titleSmall else type.labelLarge).asValue()
    Column(modifier, verticalArrangement = Arrangement.Center) {
        lines.take(count).forEachIndexed { i, d ->
            key(i) {
                val ink = if (d.today) looks.content else looks.muted
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DATED_ROW_DP.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .eventPress(d.line.event.tap, onTap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        d.line.time,
                        style = timeStyle,
                        color = if (d.today) looks.accent else looks.muted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .widthIn(min = timeWidth)
                            .padding(end = 10.dp)
                    )
                    val title =
                        if (d.today) d.line.event.title else "${d.line.event.title} · ${dayWord(d.label)}"
                    Text(
                        title,
                        style = titleStyle,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** A day's label as a word inside a line: "tomorrow", or a date as it is ("Tue 20 Oct"). */
private fun dayWord(label: String): String =
    if (label == WidgetWords.TOMORROW) label.lowercase() else label

/**
 * Split's agenda: today's date in a tile at the start ("Fri" over "20" over "Nov"), or along the top
 * of a narrow box, and each event in a tile of its own with its time in a chip. Today's chips are in
 * the role's container; another day's are gray, with the day at the tile's end.
 */
@Composable
private fun SplitAgenda(
    lines: List<DatedLine>,
    weekday: String,
    day: String,
    month: String,
    side: Boolean,
    heightDp: Int,
    looks: LookColors,
    onTap: ((WidgetTap) -> Unit)?
) {
    val gap = SplitCorners.GAP
    val type = MaterialTheme.typography
    val dim = type.labelMediumEmphasized
    if (side) {
        val count = ((heightDp + 4) / (DATED_ROW_DP + 4)).coerceIn(1, maxOf(1, lines.size))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(
                Modifier
                    .width(84.dp)
                    .fillMaxHeight()
                    .clip(
                        SplitCorners.tile(
                            topStart = true,
                            topEnd = false,
                            bottomEnd = false,
                            bottomStart = true
                        )
                    )
                    .background(looks.tile),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekday, style = dim, color = looks.muted, maxLines = 1)
                Text(day, style = type.headlineLarge.asValue(), color = looks.content, maxLines = 1)
                if (heightDp >= 120) Text(month, style = dim, color = looks.muted, maxLines = 1)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                for (i in 0 until count) key(i) {
                    val shape = SplitCorners.tile(
                        topStart = false,
                        topEnd = i == 0,
                        bottomEnd = i == count - 1,
                        bottomStart = false
                    )
                    SplitEvent(lines.getOrNull(i), shape, looks, onTap, Modifier.weight(1f))
                }
            }
        }
    } else {
        val count = ((heightDp - 48 + 4) / (DATED_ROW_DP + 4)).coerceIn(1, maxOf(1, lines.size))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(
                        SplitCorners.tile(
                            topStart = true,
                            topEnd = true,
                            bottomEnd = false,
                            bottomStart = false
                        )
                    )
                    .background(looks.tile)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(day, style = type.titleLarge.asValue(), color = looks.content, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text("$weekday $month", style = dim, color = looks.muted, maxLines = 1)
            }
            for (i in 0 until count) key(i) {
                val shape = SplitCorners.tile(
                    topStart = false,
                    topEnd = false,
                    bottomEnd = i == count - 1,
                    bottomStart = i == count - 1
                )
                SplitEvent(
                    lines.getOrNull(i),
                    shape,
                    looks,
                    onTap,
                    Modifier.weight(1f),
                    dayAtEnd = false
                )
            }
        }
    }
}

/**
 * One event's tile in Split's agenda: its time in a chip, its title, and its day at the end when
 * it is not today. A narrow box has no room for the day: [dayAtEnd] false leaves it out, and the
 * gray chip says the event is not today.
 */
@Composable
private fun SplitEvent(
    d: DatedLine?,
    shape: Shape,
    looks: LookColors,
    onTap: ((WidgetTap) -> Unit)?,
    modifier: Modifier,
    dayAtEnd: Boolean = true
) {
    val type = MaterialTheme.typography
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(looks.tile)
            .eventPress(d?.line?.event?.tap, onTap)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (d == null) return@Row
        val chip =
            if (d.today) roleContainer(looks.accent) else MaterialTheme.colorScheme.surfaceContainerHighest
        Text(
            d.line.time,
            style = type.labelMedium.copy(fontWeight = FontWeight.ExtraBold).asValue(),
            color = looks.content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(chip)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            d.line.event.title,
            style = type.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = looks.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!d.today && dayAtEnd) Text(
            d.label,
            style = type.labelSmall,
            color = looks.muted,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
