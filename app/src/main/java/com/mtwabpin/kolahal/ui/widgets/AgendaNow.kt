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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import java.time.ZoneId

// ---- an agenda's Now look: now, next, and the free time ---------------------------------------

/** From this width a wide card has now, the free time and next side by side; a narrower one has them one under the other. */
private val NOW_SIDE = 260.dp

/**
 * The least height of a line of the day: an event that can't be tapped, free time, and a later
 * day's heading. One that can is as tall as a list's row to tap ([TAP_ROW_DP]).
 */
private const val NOW_EVENT_DP = 32
private const val NOW_FREE_DP = 20
private const val NOW_HEAD_DP = 24
private const val NOW_MORE_DP = 20

/** The column of an event's dot, with the dashed line of the free time under it. */
private val DOT_COLUMN = 16.dp

/**
 * The Now look of an agenda ([WidgetLook.NOW]), for Kolahal's Agenda card and the `agenda` kind:
 * - A wide card: what is on now, how far along it is as a bar, and when it ends ("Now · until
 *   11:30"); a chip with the free time until the next event ("free 1 h 30 min"); and the next
 *   event with its time and its place. With nothing on now, the free time takes the first place ("Free ·
 *   until 13:00", "1 h 30 min"). When today has nothing more, it says so, or names today's all-day
 *   event, and the next event is on a later day ("Tomorrow · 09:00"). A narrow wide card has the
 *   first place over a line with the next event.
 * - A small card: the first place alone.
 * - A large card: the day, under its heading ("Today · Tue 24 Nov", "3 h free"), with each event's
 *   time in a chip and its length at the end, and the free time between two events said on a line
 *   of its own ("free · 1 h 30 min"). Later days follow under their headings, as many lines as fit,
 *   then "+3 more".
 * - It moves with the minute's tick ([AgendaBody] reads it): the bar fills, the free time shrinks,
 *   an event that ends leaves. All-day events are never on now.
 * - Each event keeps its tap. A screen reader hears the agenda's events ([WidgetWords.description])
 *   and then what is on now and the free time ([AgendaFree.said]).
 */
@Composable
internal fun NowAgenda(
    spec: WidgetSpec.Agenda,
    now: Long,
    zone: ZoneId,
    clock24: Boolean,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?
) {
    val locale = LocalLocale.current.platformLocale
    val moment = remember(spec, now, zone, clock24, locale) { AgendaFree.moment(spec, now, zone, clock24, locale) }
    val said = remember(moment) { AgendaFree.said(moment) }
    val c = lookColors(WidgetLook.NOW, cardRoleColor(spec.base))
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics { if (said != null) contentDescription = said }
    ) {
        val width = maxWidth
        val heightDp = maxHeight.value.toInt()
        when {
            box == WidgetSizes.Box.LARGE -> NowDay(spec, now, zone, clock24, locale, heightDp, c, onTap)
            box == WidgetSizes.Box.WIDE && width >= NOW_SIDE -> NowRow(moment, c, onTap)
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                FirstPlace(moment, c, onTap, Modifier.fillMaxWidth())
                // A wide card has the next event on a line under it; a small one has no room.
                val next = moment.next
                if (box == WidgetSizes.Box.WIDE && next != null) Text(
                    nextLabel(next) + " " + next.event.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** "Next · 13:00", or with the day of a later one: "Tomorrow · 09:00". */
private fun nextLabel(next: AgendaFree.Next): String = "${next.day ?: "Next"} · ${next.time}"

/** A wide card: the first place, the free time in a chip when something is on now, and the next event. */
@Composable
private fun NowRow(moment: AgendaFree.Moment, c: LookColors, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        FirstPlace(moment, c, onTap, Modifier.weight(1f).fillMaxHeight())
        val free = moment.free?.takeIf { moment.now != null && it >= AgendaFree.LEAST_FREE }
        if (free != null) FreeChip(free, c, Modifier.padding(horizontal = 12.dp))
        else Spacer(Modifier.width(16.dp))
        val next = moment.next
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .eventPress(next?.event?.tap, onTap),
            verticalArrangement = Arrangement.Center
        ) {
            if (next != null) {
                Text(nextLabel(next), style = type.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                EventTitle(next.event.title, c.content)
                next.event.where?.let {
                    Text(it, style = type.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("Next", style = type.labelMedium, color = c.muted, maxLines = 1)
                EventTitle(NO_MORE, c.muted)
            }
        }
    }
}

/** What a card says where an event would be, when there is none. */
private const val NO_MORE = "No more events"

/**
 * The first place of a wide or small card: what is on now with its bar; or with nothing on now,
 * the free time until the next event today; or today's first event (an all-day one); or "No more
 * events".
 */
@Composable
private fun FirstPlace(moment: AgendaFree.Moment, c: LookColors, onTap: ((WidgetTap) -> Unit)?, modifier: Modifier) {
    val type = MaterialTheme.typography
    val on = moment.now
    val free = moment.free
    val next = moment.next
    val first = moment.first
    // A tap opens the event the place is about: the free time's is the next one.
    val tap = when {
        on != null -> on.event.tap
        free != null -> next?.event?.tap
        else -> first?.event?.tap
    }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .eventPress(tap, onTap),
        verticalArrangement = Arrangement.Center
    ) {
        when {
            on != null -> {
                Text("Now · until ${on.until}", style = type.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                EventTitle(on.event.title, c.content)
                LevelBar(on.fraction, moving = false, c.accent, Modifier.padding(top = 8.dp))
            }

            free != null && next != null -> {
                Text("Free · until ${next.time}", style = type.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BigText(AgendaFree.span(free), type.headlineSmall, color = c.accent)
            }

            first != null -> {
                Text("${WidgetWords.TODAY} · ${first.time}", style = type.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                EventTitle(first.event.title, c.content)
            }

            else -> {
                Text(WidgetWords.TODAY, style = type.labelMedium, color = c.muted, maxLines = 1)
                EventTitle(NO_MORE, c.muted)
            }
        }
    }
}

/** An event's title on a wide or small card: bold, on one line. */
@Composable
private fun EventTitle(title: String, color: Color) {
    Text(
        title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** The free time between now's event and the next: "free" over "1 h 30 min", in a dashed pill. */
@Composable
private fun FreeChip(seconds: Long, c: LookColors, modifier: Modifier) {
    val type = MaterialTheme.typography
    val outline = c.muted.copy(alpha = 0.8f)
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.tile)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 1.5.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            drawRoundRect(
                outline,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx() - stroke / 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, pathEffect = dash)
            )
        }
        Column(
            Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("free", style = type.labelMedium, color = c.content, maxLines = 1)
            Text(
                AgendaFree.span(seconds),
                style = type.labelLargeEmphasized.asValue(),
                color = c.content,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A large card: the day's heading with today's free time at its end, then the events with the
 * free time between them ([AgendaFree.day]), and later days under their headings, as many lines
 * as fit in [heightDp], then "+3 more".
 */
@Composable
private fun NowDay(
    spec: WidgetSpec.Agenda,
    now: Long,
    zone: ZoneId,
    clock24: Boolean,
    locale: java.util.Locale,
    heightDp: Int,
    c: LookColors,
    onTap: ((WidgetTap) -> Unit)?
) {
    val type = MaterialTheme.typography
    val lines = remember(spec, now, zone, clock24, locale) { AgendaFree.day(spec, now, zone, clock24, locale) }
    val freeToday = remember(lines) { AgendaFree.freeToday(lines) }
    // Rows grow with the font size ([WidgetWords.textRowDp]). The card's heading is as tall as its
    // line needs, no more: at 28 dp a 4x2 card, about 163 dp inside, missed the day's third event by 1 dp.
    val firstHeadDp = WidgetWords.textRowDp(NOW_HEAD_DP, linesDp(type.titleSmall))
    val headDp = WidgetWords.textRowDp(NOW_HEAD_DP, linesDp(type.labelMedium))
    val taps = spec.events.any { it.tap != null }
    val eventDp = WidgetWords.textRowDp(if (taps) TAP_ROW_DP else NOW_EVENT_DP, linesDp(type.bodyLarge))
    val freeDp = WidgetWords.textRowDp(NOW_FREE_DP, linesDp(type.labelMedium))
    val moreDp = WidgetWords.textRowDp(NOW_MORE_DP, linesDp(type.labelMedium))
    // With a larger font the first heading can be taller: it counts as a heading and the room it has over the others.
    val shown = AgendaFree.shown(lines, heightDp - (firstHeadDp - headDp), headDp, eventDp, freeDp, moreDp)
    val left = lines.count { it is AgendaFree.Line.Event } - lines.take(shown).count { it is AgendaFree.Line.Event }
    // The time chips are as wide as the longest time in this form, so the titles line up.
    val chipWidth = if (clock24) 52.dp else 72.dp
    Column(Modifier.fillMaxSize()) {
        lines.take(shown).forEachIndexed { i, line ->
            key(i) {
                when (line) {
                    is AgendaFree.Line.Head -> if (i == 0) Row(
                        Modifier
                            .fillMaxWidth()
                            .height(firstHeadDp.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            line.label,
                            style = type.titleSmallEmphasized,
                            color = c.content,
                            maxLines = 1
                        )
                        Text(
                            " · ${line.date}",
                            style = type.bodyMedium,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (freeToday > 0) Text(
                            "${AgendaFree.span(freeToday)} free",
                            style = type.labelMedium,
                            color = c.muted,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else Text(
                        "${line.label} · ${line.date}",
                        style = type.labelMediumEmphasized,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .height(headDp.dp)
                            .wrapContentHeight(Alignment.Bottom)
                    )

                    is AgendaFree.Line.Event -> DayEvent(line, eventDp.dp, chipWidth, c, onTap)
                    is AgendaFree.Line.Free -> FreeLine(line.seconds, freeDp.dp, c)
                }
            }
        }
        if (left > 0) Text(
            "+$left more",
            style = type.labelMedium,
            color = c.muted,
            maxLines = 1,
            modifier = Modifier
                .height(moreDp.dp)
                .wrapContentHeight()
        )
    }
}

/**
 * One event of the day: a dot in its color (none without one), its time in a chip (in the card's
 * container while it is on now), its title, and its length at the end. It keeps its tap.
 */
@Composable
private fun DayEvent(
    line: AgendaFree.Line.Event,
    height: Dp,
    chipWidth: Dp,
    c: LookColors,
    onTap: ((WidgetTap) -> Unit)?
) {
    val type = MaterialTheme.typography
    val event = line.line.event
    val color = event.color?.let { colorOf(it) }
    val on = line.line.time == "now"
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .eventPress(event.tap, onTap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(DOT_COLUMN)) {
            if (color != null) Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            line.line.time,
            style = type.labelLargeEmphasized.asValue(),
            color = c.content,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = chipWidth)
                .clip(RoundedCornerShape(12.dp))
                .background(if (on) roleContainer(c.accent) else c.tile)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Text(
            event.title,
            style = type.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = c.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
        )
        line.length?.let {
            Text(it, style = type.labelMedium, color = c.muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Free time between two events: a dashed line under the dots, and "free · 1 h 30 min" in the card's color. */
@Composable
private fun FreeLine(seconds: Long, height: Dp, c: LookColors) {
    val dash = c.muted.copy(alpha = 0.6f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            Modifier
                .width(DOT_COLUMN)
                .fillMaxHeight()
        ) {
            // Under the middle of an event's dot, 4 dp in.
            val x = 4.dp.toPx()
            drawLine(
                dash,
                Offset(x, 0f),
                Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            )
        }
        Text(
            "free · ${AgendaFree.span(seconds)}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = c.accent,
            maxLines = 1
        )
    }
}
