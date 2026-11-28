/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.LifecycleStartEffect
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import com.mtwabpin.kolahal.widgets.cardBadge
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- clock ----------------------------------------------------------------------------------

/** The room under a clock's time for its place, in dp. */
private const val PLACE_DP = 16

/**
 * From this width a large clock has a strip of the day for each place ([ClockStrips]), in dp: a
 * 4×2 card on a phone 360 dp wide or more.
 */
private const val STRIPS_FROM_DP = 300

/**
 * `clock`: the time in up to four places. Kolahal does the ticking: the script gave the places
 * once, and no run is needed as time passes.
 * - Under or beside each place, its difference from the phone's time: "5 h 30 min behind",
 *   "Same time", "Here", with its day when that is not the day here. Each place gets the longest
 *   form that fits whole, down to "−5:30" ([WidgetWords.placeLines]).
 * - Small, or one place: that place's time, large, over its name.
 * - Wide: the places side by side, as many as fit ([WidgetWords.clockColumns]).
 * - Large: one row for each place, its name at the start and its time at the end. A row with room
 *   for two lines has the difference under the name ([PlaceAndDifference]).
 * - Large and wide (a 4×2 card), in Plain and Tonal: the rows also have a strip of the day
 *   ([ClockStrips]). [onTap] null takes no tap on the strips (the Widgets screen).
 * - The card's badge ([cardBadge]) is at the end of the first place's first line: its time where
 *   the time is over the name, else its name. It goes where the line can't hold it beside the
 *   place ([BadgedLine]). A small card, Poster and a card that is one shape have none.
 * - The time follows Kolahal's 24-hour setting. It moves by the minute, with the header's clock.
 *   With `seconds` it moves by the second, but only while the widgets page is in view.
 * - The size of the digits comes from the room, never from the text: a clock that ticks does not
 *   change its size, and its digits are never measured. A place's words are, to pick the form
 *   that fits, and only again when they or their room change.
 */
@Composable
internal fun ClockBody(
    spec: WidgetSpec.Clock,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)? = null
) {
    val (_, zoneChanges) = rememberNow()
    val here = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val format = remember(clock24, spec.seconds) {
        DateTimeFormatter.ofPattern(
            WidgetWords.clockPattern(
                clock24,
                spec.seconds
            ), Locale.getDefault()
        )
    }
    val now = rememberMillis(seconds = spec.seconds)
    val places = remember(spec.zones) { WidgetWords.places(spec.zones) }
    // The times change with each tick; the places and their days rarely do. The card is laid out
    // from the places, and only the times are drawn again at a tick ([ClockTime]).
    val ticking = remember(places, here, format) {
        derivedStateOf {
            WidgetWords.zoneTimesOf(
                places,
                now.value,
                here,
                format
            )
        }
    }
    val times by remember(ticking) { derivedStateOf { ticking.value.map { it.copy(time = "") } } }
    fun time(i: Int): () -> String = { ticking.value.getOrNull(i)?.time.orEmpty() }
    if (times.isEmpty()) {
        QuietLine(WidgetWords.NOTHING, small = box == WidgetSizes.Box.SMALL)
        return
    }
    val chars = WidgetWords.clockChars(clock24, spec.seconds)
    val zone = places.firstOrNull()?.second ?: here
    // A small card has no room for the badge ([BadgedLine]); Poster's time fills the card.
    val badge = spec.cardBadge.takeUnless { box == WidgetSizes.Box.SMALL }
    when (LocalWidgetLook.current) {
        WidgetLook.POSTER -> return PosterClock(times, ::time, zone, now, box, roleColor(spec.base))
        WidgetLook.BOLD -> return BoldClock(
            times,
            ::time,
            zone,
            now,
            box,
            chars,
            roleColor(spec.base),
            badge
        )

        WidgetLook.SPLIT -> return SplitClock(times, ::time, box, chars, badge, roleColor(spec.base))
        WidgetLook.SHAPE -> return ShapeClock(times, ::time, zone, now, box, roleColor(spec.base), badge)
        else -> {}
    }
    val tile = lookColors().tile
    val type = MaterialTheme.typography
    val nameStyle = type.titleMediumEmphasized
    // A row this tall has the place's name over its difference, at the phone's font size.
    val twoLines = linesDp(nameStyle, type.bodySmall)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        // The digits are as tall as the box allows over the place's name: a line is about 1.25 of its font size.
        val fits = (height - PLACE_DP) * 4 / 5
        when {
            box == WidgetSizes.Box.SMALL || times.size == 1 -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = if (box == WidgetSizes.Box.SMALL) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                BadgedLine(badge, roleColor(spec.base)) {
                    ClockTime(time(0), WidgetWords.clockTextDp(width, chars, minOf(fits, 40)))
                }
                PlaceLabel(times[0])
            }

            box == WidgetSizes.Box.WIDE -> {
                val columns = WidgetWords.clockColumns(times.size, width, chars)
                val gap = WidgetWords.CONTROL_GAP_DP
                val cell = (width - gap * (columns - 1)) / columns
                val size = WidgetWords.clockTextDp(cell, chars, minOf(fits, 28))
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(gap.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    times.take(columns).forEachIndexed { i, time ->
                        key(i) {
                            Column(Modifier.weight(1f)) {
                                // The badge is at the end of the first place's time.
                                BadgedLine(badge.takeIf { i == 0 }, roleColor(spec.base)) {
                                    ClockTime(time(i), size)
                                }
                                PlaceLabel(time)
                            }
                        }
                    }
                }
            }

            width >= STRIPS_FROM_DP -> ClockStrips(
                places,
                here,
                format,
                now,
                clock24,
                chars,
                roleColor(spec.base),
                tappable = onTap != null,
                width,
                height,
                badge
            )

            else -> {
                val gap = ROW_GAP_DP
                val rows = WidgetWords.clockRows(times.size, height, gap)
                val row = ((height + gap) / rows - gap).coerceIn(WidgetWords.CLOCK_ROW_DP, 48)
                // The time gets up to half the width inside the row's padding. The place's name has the rest.
                val size = WidgetWords.clockTextDp((width - 24) / 2, chars, minOf(row * 3 / 5, 24))
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                    times.take(rows).forEachIndexed { i, time ->
                        key(i) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(row.dp)
                                    .clip(segmentShape(i, rows))
                                    .background(tile)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    // A tall row has the difference on a second line, as the mockup has it.
                                    // The badge is at the end of the first place's name.
                                    val badged = badge.takeIf { i == 0 }
                                    if (row >= twoLines) PlaceAndDifference(
                                        time,
                                        nameStyle,
                                        type.bodySmall,
                                        badged,
                                        roleColor(spec.base)
                                    )
                                    else BadgedLine(badged, roleColor(spec.base)) {
                                        PlaceLabel(
                                            time,
                                            style = type.labelLargeEmphasized,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                ClockTime(time(i), size)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The height of a line in each of [styles], one under the other, at the phone's font size: in whole dp.
 * Media has its own, `linesHeight` in WidgetMedia.kt, which counts 20 sp for a style with no line height.
 */
@Composable
internal fun linesDp(vararg styles: TextStyle): Int {
    val density = LocalDensity.current
    val dp = styles.sumOf { style ->
        // Android scales large text by less than the font size setting, and a line's height with its
        // text: the line keeps its proportion to the font size, as the text's own layout has it.
        with(density) {
            if (style.lineHeight.isSp && style.fontSize.isSp && style.fontSize.value > 0f) {
                style.fontSize.toDp().value.toDouble() * style.lineHeight.value / style.fontSize.value
            } else {
                (if (style.lineHeight.isSp) style.lineHeight else style.fontSize * 1.25f).toDp().value.toDouble()
            }
        }
    }
    return kotlin.math.ceil(dp).toInt()
}

/** The least width of a large clock's day strips, and the room between them and the times, in dp. */
private const val STRIP_MIN_DP = 140
private const val STRIP_GAP_DP = 12

/** The room between a large clock's day strips and the hours under them, in dp. */
private const val MARKS_GAP_DP = 2

/**
 * A large clock wide enough for day strips (a 4×2 card), in Plain and Tonal, as the mockup has it:
 * a row for each place with its name over its difference, its time, and a strip of the day here
 * from 00 to 24, in the parts of the day at the place ([WidgetWords.dayStrip]). The hours are
 * under the strips ([HourMarks]).
 * - One line crosses all the strips at the moment shown ([DayStrips]).
 * - A tap on a strip moves the line to the nearest hour, and every place's time and difference
 *   follow. A sideways drag that starts on the strips moves the line with the finger, hour by hour.
 *   The line under the strips then has that hour and a Now button ([PickedHour]) in place of the
 *   hours. Now, a second tap on the line, or leaving the page or home brings back now.
 * - The page turns by a sideways swipe beside or below the strips; one that goes mostly up or
 *   down scrolls it. [tappable] false takes no tap and no drag at all.
 * - A row too low for two lines has the difference beside the name, in the form that fits.
 * - A screen reader hears the card's description: each place, its time now and its difference.
 */
@Composable
private fun ClockStrips(
    places: List<Pair<WidgetSpec.Zone, ZoneId>>,
    here: ZoneId,
    format: DateTimeFormatter,
    now: State<Long>,
    clock24: Boolean,
    chars: Int,
    role: Color,
    tappable: Boolean,
    widthDp: Int,
    heightDp: Int,
    badge: String? = null,
) {
    // The hour a tap picked, from 0 to 24 of today here, or null for now.
    var picked by remember { mutableStateOf<Int?>(null) }
    // Leaving the page, or home, brings back now.
    val seen = cardSeen()
    LaunchedEffect(seen) { if (!seen) picked = null }
    LifecycleStartEffect(Unit) { onStopOrDispose { picked = null } }
    val today by remember(here, now) {
        derivedStateOf {
            Instant.ofEpochMilli(now.value).atZone(here).toLocalDate()
        }
    }
    // A new day starts at now: an hour picked yesterday would be one of today's without a word.
    LaunchedEffect(today) { picked = null }
    // The moment shown: the hour picked, or now.
    val moment = remember(here, now) {
        derivedStateOf {
            picked?.let {
                WidgetWords.hourMoment(
                    today,
                    it,
                    here
                )
            } ?: now.value
        }
    }
    val ticking = remember(places, here, format, moment) {
        derivedStateOf {
            WidgetWords.zoneTimesOf(
                places,
                moment.value,
                here,
                format
            )
        }
    }
    val times by remember(ticking) { derivedStateOf { ticking.value.map { it.copy(time = "") } } }
    fun time(i: Int): () -> String = { ticking.value.getOrNull(i)?.time.orEmpty() }
    // The line moves by the minute, also when the clock shows seconds.
    val minute = remember(moment) { derivedStateOf { moment.value / 60_000 } }
    val hour = picked
    val at = remember(hour, today, here, clock24) {
        hour?.let {
            "At " + Instant.ofEpochMilli(WidgetWords.hourMoment(today, it, here)).atZone(here)
                .format(TimeFormats.clockFormat(clock24))
        }
    }

    val type = MaterialTheme.typography
    val nameStyle = type.labelMediumEmphasized
    val lineStyle = type.labelSmall
    val markStyle = type.labelSmall
    val gap = ROW_GAP_DP
    val room = heightDp - linesDp(markStyle) - MARKS_GAP_DP
    val least = maxOf(WidgetWords.CLOCK_ROW_DP, linesDp(nameStyle))
    val rows = WidgetWords.clockRows(times.size, room, gap, least)
    val row = ((room + gap) / rows - gap).coerceIn(least, maxOf(least, 48))
    val twoLines = row >= linesDp(nameStyle, lineStyle)
    val stripDp = maxOf(widthDp * 45 / 100, STRIP_MIN_DP)
    // The time gets up to half the room beside the strips. The place's name has the rest.
    val size = WidgetWords.clockTextDp(
        (widthDp - stripDp - STRIP_GAP_DP) * 45 / 100,
        chars,
        minOf(row * 3 / 5, 20)
    )
    val zones = remember(places, rows) { places.take(rows).map { it.second } }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = STRIP_GAP_DP.dp),
            verticalArrangement = Arrangement.spacedBy(gap.dp)
        ) {
            times.take(rows).forEachIndexed { i, place ->
                key(i) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(row.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            // The badge is at the end of the first place's name.
                            val badged = badge.takeIf { i == 0 }
                            if (twoLines) PlaceAndDifference(place, nameStyle, lineStyle, badged, role)
                            else BadgedLine(badged, role) {
                                PlaceLabel(
                                    place,
                                    style = nameStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        ClockTime(time(i), size)
                    }
                }
            }
        }
        Column(Modifier.width(stripDp.dp)) {
            DayStrips(
                zones, today, here, row, gap, role,
                at = { WidgetWords.stripAt(minute.value * 60_000, today, here) },
                onPick = if (tappable) {
                    { h: Int? -> picked = h }
                } else null,
                picking = picked != null,
            )
            // No title line: while an hour is picked, the hour and Now take the hours' line, and its gap.
            if (at != null) PickedHour(at, linesDp(markStyle) + MARKS_GAP_DP, role) {
                picked = null
            }
            else HourMarks(clock24, markStyle, Modifier.padding(top = MARKS_GAP_DP.dp))
        }
    }
}

/**
 * The line under a large clock's strips while a tap has picked an hour ([ClockStrips]): the hour
 * ("At 15:00") and a small Now button that brings back now ([onNow]), [heightDp] tall as the hours'
 * line and its gap are. The button is a tonal one as a widget's buttons are, and Compose gives its
 * touch area 48 dp all the same.
 */
@Composable
private fun PickedHour(at: String, heightDp: Int, role: Color, onNow: () -> Unit) {
    val looks = lookColors()
    Row(
        Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            at,
            style = MaterialTheme.typography.labelSmall,
            color = looks.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val container =
            if (LocalWidgetLook.current == WidgetLook.TONAL) looks.tile else role.copy(alpha = 0.2f)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FilledTonalButton(
                onClick = onNow,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(heightDp.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = container,
                    contentColor = looks.content
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(
                    "Now",
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * A large clock's day strips: one for each of [zones] in a row [rowDp] tall, [gapDp] apart, and one
 * line across them all at [at], from 0 (00:00) to 1 (24:00).
 * - A strip is [day] here in the parts of the day at its place: night, day and working hours, in
 *   tones of the card's [role] color ([stripShades]). It runs from the right in a right-to-left page.
 * - The line is in the text color with a ring of the card's color, so it shows on every part.
 *   [at] is read while drawing: the minute's tick draws the line again and composes nothing.
 * - With [onPick], a tap picks the nearest hour ([WidgetWords.pickedHourAt]), and a long-press is the card's.
 *   A drag that goes sideways past the touch slop is the strips' (the page doesn't turn): the line
 *   follows the finger hour by hour, with a light tick at each hour. One that goes up or down first
 *   is left to the page, as Compose's own horizontal drag does.
 * - It is no item for a screen reader: the card's description says what it shows.
 */
@Composable
private fun DayStrips(
    zones: List<ZoneId>,
    day: LocalDate,
    here: ZoneId,
    rowDp: Int,
    gapDp: Int,
    role: Color,
    at: () -> Float,
    onPick: ((Int?) -> Unit)?,
    picking: Boolean = false
) {
    val strips = remember(zones, day, here) { zones.map { WidgetWords.dayStrip(day, here, it) } }
    val scheme = MaterialTheme.colorScheme
    // The card's own colors: on Tonal the card is tinted by this card's role, not the theme's accent.
    val colors = lookColors(role = role)
    val shades = remember(scheme.surface, colors.card, role) {
        stripShades(
            scheme.surface,
            colors.card,
            role
        )
    }
    val outline = scheme.outlineVariant
    val ink = scheme.onSurface
    val ring = colors.card
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val longPress = LocalCardLongPress.current
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    val picked by rememberUpdatedState(picking)
    val lineAt by rememberUpdatedState(at)
    val taps = if (onPick == null) Modifier else Modifier.pointerInput(rtl, longPress) {
        detectTapGestures(
            onLongPress = longPress?.let { press ->
                { _: Offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    press()
                }
            },
            onTap = { tap ->
                // A tap on the line, with an hour picked, goes back to now; anywhere else picks the nearest hour, 00:00 to 23:00.
                val along = lineAt().coerceIn(0f, 1f)
                val lineX = size.width * if (rtl) 1f - along else along
                if (picked && abs(tap.x - lineX) <= 12.dp.toPx()) pick?.invoke(null)
                else pick?.invoke(WidgetWords.pickedHourAt(tap.x, size.width.toFloat(), rtl))
            },
        )
    }.pointerInput(rtl) {
        // The hour the drag shows, to tick only when it changes.
        var shown = -1
        fun follow(x: Float) {
            val h = WidgetWords.pickedHourAt(x, size.width.toFloat(), rtl)
            if (h == shown) return
            shown = h
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            pick?.invoke(h)
        }
        detectHorizontalDragGestures(
            onDragStart = { at ->
                shown = -1
                follow(at.x)
            },
        ) { change, _ ->
            change.consume()
            follow(change.position.x)
        }
    }
    val height = (rowDp * strips.size + gapDp * (strips.size - 1)).dp
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(height)
            .testTag("clock-strips")
            .then(taps)
            .drawWithCache {
                val rowPx = rowDp.dp.toPx()
                val step = rowPx + gapDp.dp.toPx()
                val tall = (rowPx * 0.45f).coerceIn(10.dp.toPx(), 16.dp.toPx())
                val corner = CornerRadius(tall / 3)
                val tops = List(strips.size) { it * step + (rowPx - tall) / 2 }
                val outlines = tops.map { top ->
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                0f,
                                top,
                                size.width,
                                top + tall,
                                corner
                            )
                        )
                    }
                }

                fun x(along: Float) = size.width * if (rtl) 1f - along else along
                val hair = 1.dp.toPx()
                val line = 2.dp.toPx()
                val from = (tops.firstOrNull() ?: 0f) - 4.dp.toPx()
                val to = (tops.lastOrNull() ?: 0f) + tall + 4.dp.toPx()
                onDrawBehind {
                    strips.forEachIndexed { i, stretches ->
                        clipPath(outlines[i]) {
                            for ((begin, end, part) in stretches) {
                                val a = x(begin)
                                val b = x(end)
                                // A pixel more on each side, so two parts meet with no seam. The strip's outline clips them.
                                drawRect(
                                    shades.of(part),
                                    Offset(minOf(a, b) - 1f, tops[i]),
                                    Size(abs(b - a) + 2f, tall)
                                )
                            }
                        }
                        drawPath(outlines[i], outline, style = Stroke(hair))
                    }
                    if (strips.isNotEmpty()) {
                        val lx = x(at()).coerceIn(line, size.width - line)
                        drawLine(
                            ring,
                            Offset(lx, from),
                            Offset(lx, to),
                            strokeWidth = line + 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            ink,
                            Offset(lx, from),
                            Offset(lx, to),
                            strokeWidth = line,
                            cap = StrokeCap.Round
                        )
                    }
                }
            },
    )
}

/**
 * The hours under a clock's day strips ([WidgetWords.hourMarks]), each centered on its place on the
 * strip, the first and the last at its ends. Where they would touch (a narrow strip, a large font
 * size), only 00, 12 and 24 are written. They are no item for a screen reader.
 */
@Composable
private fun HourMarks(clock24: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    val marks = remember(clock24) { WidgetWords.hourMarks(clock24) }
    val color = lookColors().muted
    Layout(
        content = {
            marks.forEach {
                Text(
                    it,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val last = placeables.size - 1
        val xs = placeables.mapIndexed { i, p ->
            (width * i / last.toFloat() - p.width / 2f).roundToInt()
                .coerceIn(0, maxOf(0, width - p.width))
        }
        val apart = 4.dp.roundToPx()
        val crowded = (1..last).any { xs[it] < xs[it - 1] + placeables[it - 1].width + apart }
        val shown = if (crowded) listOf(0, last / 2, last) else placeables.indices.toList()
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            for (i in shown) placeables[i].placeRelative(xs[i], 0)
        }
    }
}

/**
 * A clock's [time], as a value ([asValue]), with digits [sizeDp] tall whatever the phone's font
 * size is: the size was made to fit the box. The digits are all one width where the font can do
 * that, so the text does not shake as it ticks. It reads the time itself, so a tick draws this
 * text again, not the whole card.
 */
@Composable
private fun ClockTime(
    time: () -> String,
    sizeDp: Int,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    val size = with(LocalDensity.current) { sizeDp.dp.toSp() }
    val base = MaterialTheme.typography.titleLarge
    val style = remember(base, size, bold) {
        if (bold) base.copy(fontSize = size, lineHeight = size)
            .asBold() else base.copy(fontSize = size, lineHeight = size * 1.25f).asValue()
    }
    Text(time(), style = style, color = color, maxLines = 1, softWrap = false)
}

/**
 * [style] for the Bold look's main value: the heaviest weight, tighter, with digits of one width.
 * Kolahal has no narrow variable font, so the value is as large as its room allows instead.
 */
internal fun TextStyle.asBold(): TextStyle =
    copy(fontWeight = FontWeight.Black, letterSpacing = (-0.03).em, fontFeatureSettings = "tnum")

/** Black type is wider than the size a clock's digits are measured for ([WidgetWords.clockTextDp]): it gets this much of the width. */
private const val BOLD_WIDTH = 0.9f

/**
 * The Bold clock: the first place's time at display size in the card's [accent], under its name
 * and its date. The other places are small, under it in a large box and beside it in a wide one,
 * as many as fit. A [badge] is at the end of the name's line, the top end of the card in a large box.
 */
@Composable
private fun BoldClock(
    times: List<WidgetWords.ZoneTime>,
    time: (Int) -> () -> String,
    zone: ZoneId,
    now: State<Long>,
    box: WidgetSizes.Box,
    chars: Int,
    accent: Color,
    badge: String? = null
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        val first = times[0]
        when (box) {
            WidgetSizes.Box.SMALL -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ClockTime(
                    time(0),
                    WidgetWords.clockTextDp(
                        (width * BOLD_WIDTH).toInt(),
                        chars,
                        (height - PLACE_DP) * 4 / 5
                    ),
                    accent,
                    bold = true
                )
                PlaceLabel(first)
            }

            WidgetSizes.Box.WIDE -> {
                // The others need about 72 dp each beside the main time, which keeps at least 120.
                val beside = ((width - 120) / 80).coerceIn(0, times.size - 1)
                val mainWidth = width - beside * 80
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BadgedLine(badge, accent) { PlaceAndDate(first, zone, now) }
                        ClockTime(
                            time(0),
                            WidgetWords.clockTextDp(
                                (mainWidth * BOLD_WIDTH).toInt(),
                                chars,
                                height - 20
                            ),
                            accent,
                            bold = true
                        )
                    }
                    for (i in 1..beside) key(i) {
                        SmallPlace(
                            times[i],
                            time(i),
                            Modifier
                                .width(72.dp)
                                .padding(start = 8.dp)
                        )
                    }
                }
            }

            WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
                BadgedLine(badge, accent) { PlaceAndDate(first, zone, now) }
                val others = (times.size - 1).coerceAtMost(maxOf(1, (width + 16) / 88))
                // The big time takes what the place line and the row of the others leave.
                val room = height - 20 - if (others > 0) 44 else 0
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(), contentAlignment = Alignment.CenterStart
                ) {
                    ClockTime(
                        time(0),
                        WidgetWords.clockTextDp(
                            (width * BOLD_WIDTH).toInt(),
                            chars,
                            minOf(room, 80)
                        ),
                        accent,
                        bold = true
                    )
                }
                if (others > 0) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 1..others) key(i) {
                        SmallPlace(
                            times[i],
                            time(i),
                            Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

/** The first place's name in bold and its date, dim: "Tokyo · Fri 20 Nov". The date moves with the clock's tick. */
@Composable
private fun PlaceAndDate(time: WidgetWords.ZoneTime, zone: ZoneId, now: State<Long>) {
    val type = MaterialTheme.typography
    Row {
        Text(
            time.label,
            style = type.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        DateOf(zone, now, type.labelMedium, lookColors().muted, prefix = " · ")
    }
}

/** The date at [zone] ("Fri 20 Nov"). It reads the time itself, so a tick draws this text again, not the card. */
@Composable
private fun DateOf(
    zone: ZoneId,
    now: State<Long>,
    style: TextStyle,
    color: Color,
    prefix: String = ""
) {
    val format = remember { TimeFormats.dayFormat() }
    val day = Instant.ofEpochMilli(now.value).atZone(zone).toLocalDate()
    val text = remember(day) { prefix + day.format(format) }
    Text(text, style = style, color = color, maxLines = 1, softWrap = false)
}

/** One of the other places of a clock, small: its name dim over its time. */
@Composable
private fun SmallPlace(
    place: WidgetWords.ZoneTime,
    time: () -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        PlaceLabel(place)
        ClockTime(time, 16)
    }
}

/**
 * The Split clock: the first place in a big tile, its name over its time, and the others in small
 * tiles under it (large) or beside it (wide), as many as fit at 64 dp each. A [badge] in [role]
 * is at the top end of the first tile, at the end of the place's name.
 */
@Composable
private fun SplitClock(
    times: List<WidgetWords.ZoneTime>,
    time: (Int) -> () -> String,
    box: WidgetSizes.Box,
    chars: Int,
    badge: String? = null,
    role: Color = Color.Unspecified
) {
    val tile = lookColors().tile
    val gap = SplitCorners.GAP
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        when (box) {
            WidgetSizes.Box.LARGE -> {
                val others =
                    (times.size - 1).coerceAtMost(maxOf(1, (width + 4) / 68)).coerceAtMost(3)
                // The big tile takes three fifths of the height, as the mockup has it.
                val bigDp = if (others > 0) (height - 4) * 3 / 5 else height
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    val big = if (others > 0) SplitCorners.tile(
                        topStart = true,
                        topEnd = true,
                        bottomEnd = false,
                        bottomStart = false
                    ) else SplitCorners.inColumn(0, 1)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(bigDp.dp)
                            .clip(big)
                            .background(tile)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BadgedLine(badge, role) {
                            Text(
                                times[0].label,
                                style = MaterialTheme.typography.labelMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        ClockTime(
                            time(0),
                            WidgetWords.clockTextDp(
                                width - 32,
                                chars,
                                minOf(44, (bigDp - 40) * 4 / 5)
                            )
                        )
                    }
                    if (others > 0) Row(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        val cell = (width - 4 * (others - 1)) / others
                        for (i in 1..others) key(i) {
                            val shape = SplitCorners.tile(
                                topStart = false,
                                topEnd = false,
                                bottomEnd = i == others,
                                bottomStart = i == 1
                            )
                            SplitPlace(
                                times[i],
                                time(i),
                                WidgetWords.clockTextDp(cell - 16, chars, 18),
                                shape,
                                tile,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            WidgetSizes.Box.WIDE -> {
                val others =
                    (times.size - 1).coerceAtMost(maxOf(0, (width - 96) / 68)).coerceAtMost(3)
                val count = others + 1
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    // The first tile is half as wide again as the others.
                    val firstDp =
                        if (others > 0) ((width - 4 * others) * 3 / (3 + 2 * others)) else width
                    SplitPlace(
                        times[0],
                        time(0),
                        WidgetWords.clockTextDp(
                            firstDp - 24,
                            chars,
                            minOf(32, (height - 24) * 4 / 5)
                        ),
                        SplitCorners.inRow(0, count),
                        tile,
                        Modifier.weight(1.5f),
                        start = true,
                        badge = badge,
                        role = role
                    )
                    for (i in 1..others) key(i) {
                        SplitPlace(
                            times[i],
                            time(i),
                            WidgetWords.clockTextDp((firstDp * 2 / 3) - 16, chars, 18),
                            SplitCorners.inRow(i, count),
                            tile,
                            Modifier.weight(1f)
                        )
                    }
                }
            }

            WidgetSizes.Box.SMALL -> SplitPlace(
                times[0],
                time(0),
                WidgetWords.clockTextDp(width - 16, chars, (height - PLACE_DP) * 4 / 5),
                SplitCorners.inColumn(0, 1),
                tile,
                Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * One of Split's clock tiles: the place's name, dim, over its time, in the middle or at the [start].
 * A [badge] in [role] is at the end of the name's line, where it fits beside the name ([BadgedLine]).
 */
@Composable
private fun SplitPlace(
    place: WidgetWords.ZoneTime,
    time: () -> String,
    sizeDp: Int,
    shape: Shape,
    tile: Color,
    modifier: Modifier,
    start: Boolean = false,
    badge: String? = null,
    role: Color = Color.Unspecified
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(tile)
            .padding(horizontal = if (start) 12.dp else 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        BadgedLine(badge, role) { PlaceLabel(place) }
        ClockTime(time, sizeDp)
    }
}

/**
 * The Shape clock.
 * - In a square box the card is the shape ([LocalCardShaped]), and this draws an analog dial in it
 *   with the first place's name ([ClockDial]).
 * - Wide: the dial in a small cookie at the start, the times beside it in words: "Tokyo 14:32" over
 *   "London 09:02 · New York 04:02".
 * - Large but not square: the dial at the start, a row for each place beside it.
 * - A [badge] is at the end of the first place's line, beside the dial. A card that is the shape,
 *   and a small one, have none.
 */
@Composable
private fun ShapeClock(
    times: List<WidgetWords.ZoneTime>,
    time: (Int) -> () -> String,
    zone: ZoneId,
    now: State<Long>,
    box: WidgetSizes.Box,
    accent: Color,
    badge: String? = null
) {
    val looks = lookColors(WidgetLook.SHAPE, accent)
    if (LocalCardShaped.current) {
        ClockDial(times[0].label, zone, now, looks, Modifier.fillMaxSize(), filled = false)
        return
    }
    val type = MaterialTheme.typography
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxHeight = maxHeight
        if (box == WidgetSizes.Box.SMALL) {
            ClockDial(null, zone, now, looks, Modifier.fillMaxSize(), filled = true)
            return@BoxWithConstraints
        }
        val side =
            minOf(maxHeight, if (box == WidgetSizes.Box.WIDE) 72.dp else 140.dp, maxWidth / 3)
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ClockDial(null, zone, now, looks, Modifier.size(side), filled = true)
            if (box == WidgetSizes.Box.WIDE) {
                Column(Modifier.weight(1f)) {
                    val first = time(0)
                    BadgedLine(badge, accent) {
                        Text(
                            "${times[0].label} ${first()}",
                            style = type.titleSmallEmphasized,
                            color = looks.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (times.size > 1) {
                        val others = (1 until times.size).map { it to time(it) }
                        Text(
                            others.joinToString(" · ") { (i, t) -> "${times[i].label} ${t()}" },
                            style = type.labelMedium,
                            color = looks.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = times.size.coerceAtMost(maxOf(1, (boxHeight.value.toInt() + 8) / 56))
                    for (i in 0 until rows) key(i) {
                        Column {
                            BadgedLine(badge.takeIf { i == 0 }, accent) {
                                PlaceLabel(times[i], style = type.labelMedium)
                            }
                            ClockTime(time(i), 22)
                        }
                    }
                }
            }
        }
    }
}

/**
 * An analog dial at the time [now] in [zone], as the mockup draws it in a 100-unit square: four dots
 * for 12, 3, 6 and 9, the hour hand in the text color, the minute hand in the accent, a dot in
 * the middle, and the place's [name] above the middle.
 * - While the hour hand passes through the name, the name is as far under the middle
 *   ([DialLabel]). The minute hand doesn't move it: the name is over the hands, with a ring of the
 *   card's color around its letters, so it reads over the minute hand.
 * - It reads the time while drawing, and the name's place while placing it, so the clock's tick
 *   (once a minute) draws the dial again and composes nothing. It has no second hand: nothing moves
 *   between two ticks.
 * - With [filled] it draws its own cookie first, or the shape chosen for the card ([cardShape]); in a
 *   card that is the shape, the card is the cookie.
 * - It is no item for a screen reader: the card's description says the time.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClockDial(
    name: String?,
    zone: ZoneId,
    now: State<Long>,
    looks: LookColors,
    modifier: Modifier,
    filled: Boolean
) {
    val hour = looks.content
    val minute = looks.accent
    val dots = looks.muted
    val fill = if (filled) Modifier.background(looks.shapeFill, cardShape().toShape()) else Modifier
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(side)
                .then(fill)
                .drawBehind {
                    val unit = size.minDimension / 100f
                    val middle = center
                    for (k in 0 until 4) {
                        val a = k * PI / 2
                        drawCircle(
                            dots,
                            1.8f * unit,
                            middle + Offset(
                                (sin(a) * 34 * unit).toFloat(),
                                (-cos(a) * 34 * unit).toFloat()
                            )
                        )
                    }
                    val at = Instant.ofEpochMilli(now.value).atZone(zone)
                    val minutes = at.minute
                    fun hand(turns: Float, length: Float, width: Float, color: Color) {
                        val a = turns * 2 * PI
                        val end = middle + Offset(
                            (sin(a) * length * unit).toFloat(),
                            (-cos(a) * length * unit).toFloat()
                        )
                        drawLine(
                            color,
                            middle,
                            end,
                            strokeWidth = width * unit,
                            cap = StrokeCap.Round
                        )
                    }
                    hand(
                        DialLabel.hourTurns(at.hour, minutes),
                        DialLabel.HOUR_LENGTH,
                        DialLabel.HOUR_WIDTH,
                        hour
                    )
                    hand(minutes / 60f, DialLabel.MINUTE_LENGTH, DialLabel.MINUTE_WIDTH, minute)
                    drawCircle(hour, 3.5f * unit, middle)
                }
                .clearAndSetSemantics {})
        if (name != null && side >= 96.dp) {
            val style = MaterialTheme.typography.labelMediumEmphasized
            val ring = with(LocalDensity.current) { 3.dp.toPx() }
            val ringStyle = remember(style, ring) {
                style.copy(drawStyle = Stroke(width = ring, join = StrokeJoin.Round))
            }
            Box(
                Modifier
                    .widthIn(max = side * 0.5f)
                    .layout { measurable, constraints ->
                        val label = measurable.measure(constraints)
                        layout(label.width, label.height) {
                            // Placed with the time read here: the minute's tick places the name again and composes nothing.
                            val unit = side.toPx() / 100f
                            val at = Instant.ofEpochMilli(now.value).atZone(zone)
                            val y = DialLabel.labelY(
                                DialLabel.hourTurns(at.hour, at.minute),
                                label.width / unit,
                                label.height / unit
                            )
                            label.place(0, (y * unit).roundToInt())
                        }
                    }
                    .clearAndSetSemantics {},
            ) {
                // A ring of the card's color around the letters, under them: they read over a hand.
                Text(
                    name,
                    style = ringStyle,
                    color = looks.shapeFill,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(name, style = style, color = hour, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A place's name in bold, over its difference from the phone's time in dim words: the first of its
 * lines ([WidgetWords.placeLines]) that fits whole, "5 h 30 min behind" or "−5:30". A [badge] in
 * [role] is at the end of the name's line, where it fits beside the name ([BadgedLine]).
 */
@Composable
private fun PlaceAndDifference(
    time: WidgetWords.ZoneTime,
    nameStyle: TextStyle,
    lineStyle: TextStyle,
    badge: String? = null,
    role: Color = Color.Unspecified
) {
    val lines = remember(time) { WidgetWords.placeLines(time) }
    Column {
        BadgedLine(badge, role) {
            Text(
                time.label,
                style = nameStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FittingLine(lines, lineStyle, lookColors().muted)
    }
}

/**
 * The first of [lines] that fits whole on one line in the width it gets, in [style] at the phone's
 * font size. With none that fits, the last one, cut with "…". Numbers such as "−5:30 −1" read from
 * the left in a right-to-left page too.
 */
@Composable
private fun FittingLine(lines: List<String>, style: TextStyle, color: Color) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val room = constraints.maxWidth
        val text = remember(lines, style, room, measurer) {
            lines.firstOrNull {
                measurer.measure(
                    it,
                    style,
                    maxLines = 1,
                    softWrap = false
                ).size.width <= room
            } ?: lines.last()
        }
        Text(
            text,
            style = style.copy(textDirection = TextDirection.ContentOrLtr),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A place's name in [color], and beside it, dim, the first of its lines ([WidgetWords.placeLines])
 * that fits whole with the name: "London · 9 h behind", "London −9:00", "Tokyo · Here".
 * - Where none fits, only the place's day is beside the name, as "+1" or "−1", and the name is cut
 *   with "…" before the day is. A place on the day here then has its name alone.
 * - The words are a text of their own after the name, so they follow it in a right-to-left page.
 */
@Composable
private fun PlaceLabel(
    time: WidgetWords.ZoneTime,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = lookColors().muted
) {
    val muted = lookColors().muted
    val measurer = rememberTextMeasurer()
    val lines = remember(time) { WidgetWords.placeLines(time) }

    // A dot before words, a space before a number.
    fun gapBefore(words: String) = if (words.first() in "+−±") " " else " · "
    BoxWithConstraints {
        val room = constraints.maxWidth
        val words = remember(lines, time.label, style, room, measurer) {
            fun width(text: String) =
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.width

            val name = width(time.label)
            lines.firstOrNull { name + width(gapBefore(it)) + width(it) <= room }
        } ?: time.dayShort
        Row {
            Text(
                time.label,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (words != null) {
                Text(gapBefore(words), style = style, color = muted, maxLines = 1, softWrap = false)
                Text(
                    words,
                    style = style.copy(textDirection = TextDirection.ContentOrLtr),
                    color = muted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
