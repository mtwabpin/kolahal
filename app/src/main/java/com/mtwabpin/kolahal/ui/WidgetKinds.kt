package com.mtwabpin.kolahal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The words of a widget: what a card says, and what a screen reader hears. Pure, so it is unit
 * tested. Times are in seconds, as a script gives them.
 */
internal object WidgetWords {
    const val NOTHING = "Nothing to show"
    const val UNAVAILABLE = "widget unavailable"

    /** What a card says when no script has its name any more. */
    const val REMOVED = "Script removed"

    /** What a card says while its script is switched off. */
    const val OFF = "Script is off"

    /** What a card says when its script's code has no `@widget` line any more. */
    const val NO_WIDGET = "Script has no widget now"

    /** A number without a needless ".0": 9, 2.5, 3.14. At most two decimals. */
    fun num(v: Float): String {
        if (v.isNaN() || v.isInfinite()) return "–"
        val r = Math.round(v * 100.0) / 100.0
        return if (r == floor(r) && abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    /** A value with its unit: "24°C", "68%", but "12 km". A unit that starts with a letter gets a space. */
    fun joined(value: String, unit: String?): String = when {
        unit.isNullOrEmpty() -> value
        unit.first().isLetter() -> "$value $unit"
        else -> value + unit
    }

    /** How full a bar or ring is, from 0 to 1. A scale of nothing is empty. */
    fun fraction(value: Float, max: Float): Float =
        if (value.isNaN() || max.isNaN() || max <= 0f) 0f else (value / max).coerceIn(0f, 1f)

    fun percent(value: Float, max: Float): String = "${(fraction(value, max) * 100).roundToInt()}%"

    /** The arrow beside a change. An unknown trend has none. */
    fun arrow(trend: String?): String? = when (trend) {
        "up" -> "↑"
        "down" -> "↓"
        "flat" -> "→"
        else -> null
    }

    /** The same for a screen reader, which may not say an arrow. */
    fun trendWord(trend: String?): String? = when (trend) {
        "up" -> "up"
        "down" -> "down"
        "flat" -> "no change"
        else -> null
    }

    /** A row's checkbox for a screen reader: "done", "not done", or nothing for a row without one. */
    fun doneWord(done: Boolean?): String? = when (done) {
        true -> "done"
        false -> "not done"
        null -> null
    }

    /** A chart in words, for a screen reader: "7 values, from 3 to 12, the latest 9". */
    fun chartSummary(points: List<Float>): String = when (points.size) {
        0 -> "No values"
        1 -> "1 value: ${num(points[0])}"
        else -> "${points.size} values, from ${num(points.min())} to ${num(points.max())}, the latest ${num(points.last())}"
    }

    /**
     * A countdown's text in two parts: the [value] ("12") and its [unit] ("days"), or null for a
     * text with no number ("today", "03:25:10"). A card draws the value big and the unit small.
     * [ticks] is true when the text shows seconds, so it must be drawn again every second.
     */
    data class Count(val value: String, val unit: String?, val ticks: Boolean) {
        /** The whole text: "12 days". */
        val text: String get() = if (unit == null) value else "$value $unit"
    }

    private const val HOUR = 3600L
    private const val DAY = 86_400L

    /** A length of time by its largest unit, as a number and its unit: "12" and "days". "under a minute" has no number. */
    private fun spanParts(seconds: Long): Pair<String, String?> = when {
        seconds >= DAY -> (seconds / DAY).let { "$it" to if (it == 1L) "day" else "days" }
        seconds >= HOUR -> (seconds / HOUR).let { "$it" to if (it == 1L) "hour" else "hours" }
        seconds >= 60 -> "${seconds / 60}" to "min"
        else -> "under a minute" to null
    }

    /** A length of time by its largest unit: "12 days", "1 hour", "5 min". */
    fun span(seconds: Long): String = spanParts(seconds).let { (n, unit) -> if (unit == null) n else "$n $unit" }

    /** A length of time as a clock: "03:25:10". Past two days, the days come first: "12d 03:25:10". */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val days = if (s > 2 * DAY) s / DAY else 0
        val rest = s - days * DAY
        val time = String.format(Locale.ROOT, "%02d:%02d:%02d", rest / HOUR, rest % HOUR / 60, rest % 60)
        return if (days > 0) "${days}d $time" else time
    }

    /**
     * What a countdown to [to] says at [now].
     * - Before the moment: "12 days" ([style] "days"), or "03:25:10" ([style] "clock"). With no
     *   style, it counts days while the moment is more than 48 hours away, and is a clock after that.
     * - On the day of the moment, once it has passed: [reached], or "today".
     * - On later days: the time since, as "3 days ago".
     */
    fun countdown(to: Long, now: Long, style: String?, reached: String?, zone: ZoneId): Count {
        val left = to - now
        if (left > 0) {
            val clock = style == "clock" || (style != "days" && left <= 48 * HOUR)
            if (clock) return Count(clock(left), null, ticks = true)
            val (n, unit) = spanParts(left)
            return Count(n, unit, ticks = false)
        }
        fun day(t: Long) = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
        if (day(to) == day(now)) return Count(reached ?: "today", null, ticks = false)
        val (n, unit) = spanParts(-left)
        // "under a minute ago" has no number: all of it is the value.
        return if (unit == null) Count("$n ago", null, ticks = false) else Count(n, "$unit ago", ticks = false)
    }

    /** How far a countdown that started at [from] has come, from 0 to 1. Null without a start, or with one after the moment. */
    fun countdownFraction(from: Long?, to: Long, now: Long): Float? =
        if (from == null || to <= from) null else ((now - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    /**
     * [fraction] in steps of a thousandth: about one pixel of a bar. A countdown's fraction changes
     * a little every second. Its bar gets the same value until it has moved a step, so it is not
     * drawn again, and does not start its spring again, every second.
     */
    fun barStep(fraction: Float): Float = if (fraction.isNaN()) 0f else (fraction.coerceIn(0f, 1f) * 1000).roundToInt() / 1000f

    /**
     * The scale of a chart: its lowest and its highest value. [min] and [max] win. Without them the
     * data decides.
     * - Bars stand on zero when no value is below it, so their heights can be compared.
     * - A line of one height (one point, or all the same) gets room around it and sits in the middle.
     * - The scale always has some height.
     */
    fun scale(points: List<Float>, min: Float?, max: Float?, bars: Boolean): Pair<Float, Float> {
        val real = points.filter { it.isFinite() }
        val low = min ?: (real.minOrNull() ?: 0f).let { if (bars) minOf(0f, it) else it }
        val high = max ?: real.maxOrNull() ?: 0f
        return when {
            high > low -> low to high
            !bars && min == null && max == null -> low - 0.5f to high + 0.5f
            else -> low to low + 1f
        }
    }

    /**
     * The whole card in one sentence, for a screen reader: "Weather: 24°C, Sunny, 68% humidity".
     * It has everything in the description, also what a small card leaves out. A countdown is said
     * in words ("5 hours"), never as a running clock.
     * - [now] (seconds) and [zone] count for the kinds that tell the time ([timed]).
     * - [clock24] and [locale] say how a time of day is written, for an agenda and a clock.
     */
    fun description(title: String?, spec: WidgetSpec, now: Long, zone: ZoneId, clock24: Boolean = false, locale: Locale = Locale.getDefault()): String {
        fun words(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" ")
        val parts = buildList<String?> {
            when (spec) {
                is WidgetSpec.Stat -> {
                    add(joined(spec.value, spec.unit))
                    add(spec.label)
                    add(words(trendWord(spec.trend), spec.delta))
                    spec.more.forEach { add(words(it.value, it.label)) }
                    add(spec.progress?.let { percent(it, 1f) })
                }
                is WidgetSpec.Text -> {
                    add(spec.text)
                    add(spec.caption)
                }
                is WidgetSpec.Rows -> {
                    if (spec.items.isEmpty()) add(spec.empty ?: NOTHING)
                    spec.items.forEach { r -> add(listOfNotNull(r.title, r.detail, r.trailing, doneWord(r.done)).joinToString(", ")) }
                }
                is WidgetSpec.Progress -> {
                    if (spec.items.isEmpty()) add(spec.text ?: percent(spec.value, spec.max))
                    spec.items.forEach { b -> add(words(b.label, b.text ?: percent(b.value, b.max))) }
                    add(spec.label)
                }
                is WidgetSpec.Chart -> {
                    add(spec.value)
                    add(spec.label)
                    add(spec.summary ?: chartSummary(spec.points))
                }
                is WidgetSpec.Countdown -> {
                    add(countdown(spec.to, now, "days", spec.reached, zone).text)
                    add(spec.label)
                }
                // Each button and each switch is an item of its own for a screen reader. The card names them.
                is WidgetSpec.Buttons -> spec.items.forEach { add(it.label) }
                is WidgetSpec.Toggles -> spec.items.forEach { add(words(it.label, if (it.on) "on" else "off")) }
                is WidgetSpec.Clock -> {
                    // Without seconds: the words are made again by the minute.
                    val format = DateTimeFormatter.ofPattern(clockPattern(clock24, seconds = false), locale)
                    zoneTimes(spec.zones, now * 1000, zone, format).forEach { add(words(it.label, it.time, it.day)) }
                }
                is WidgetSpec.Agenda -> {
                    // What the card shows at this moment: an event that has ended is not said either.
                    val days = agenda(spec, now, zone, clock24, locale)
                    if (days.isEmpty()) add(spec.empty ?: NO_EVENTS)
                    days.forEach { day ->
                        add(day.label)
                        day.lines.forEach { add(listOfNotNull(words(it.time, it.event.title), it.event.where).joinToString(", ")) }
                    }
                }
                // The words for the picture, not the picture: they are the same whether it has loaded or not.
                is WidgetSpec.Image -> {
                    add(spec.alt)
                    add(spec.caption)
                }
                is WidgetSpec.Media -> {
                    add(spec.title)
                    add(spec.artist)
                    add(spec.app)
                    add(if (spec.playing) "playing" else "paused")
                }
            }
        }
        // A few fields may be styled ([WidgetParser.STYLED]). A screen reader hears their plain words.
        val said = StyledText.plain(parts.filterNot { it.isNullOrBlank() }.joinToString(", "))
        return if (title.isNullOrBlank()) said else "$title: $said"
    }

    /**
     * True for a kind whose words change as time passes: the card's description for a screen reader
     * is then made again every minute, with the moment. For any other kind the moment is not read.
     */
    fun timed(spec: WidgetSpec?): Boolean = spec is WidgetSpec.Countdown || spec is WidgetSpec.Clock || spec is WidgetSpec.Agenda

    /** What an image card says while its picture is on its way. */
    const val PICTURE_LOADING = "Loading picture…"

    /** What an image card says when its picture can't be fetched or isn't a picture. */
    const val PICTURE_UNAVAILABLE = "Picture unavailable"

    /**
     * What an image card says in the place of its picture. Null when it has the picture ([loaded]).
     * - [failed]: [PICTURE_UNAVAILABLE].
     * - Not there yet, and the widgets page is [inView]: [PICTURE_LOADING]. The fetch is on its way.
     * - Not there, and the page is not in view: the picture's [alt] text. Nothing is fetched then,
     *   so the card never says "Loading" for ever.
     */
    fun pictureNote(loaded: Boolean, failed: Boolean, inView: Boolean, alt: String): String? = when {
        loaded -> null
        failed -> PICTURE_UNAVAILABLE
        inView -> PICTURE_LOADING
        else -> alt
    }

    /** The size a picture is decoded at: at most [widthPx] wide, with at most [maxPixels] pixels. */
    data class PictureSize(val widthPx: Int, val maxPixels: Float)

    /** A place is counted in steps of this many pixels, so a place that is one pixel wider needs no new decode. */
    private const val PICTURE_STEP = 16

    /** The largest side a place counts with, in pixels. No card is larger, and the numbers stay small. */
    private const val PICTURE_SIDE = 4096

    /**
     * The size to decode a picture at for a place [widthPx] by [heightPx] on the card.
     * - At most twice as wide as the place, and at most twice its pixels. A picture that is wider
     *   or taller than its place is cropped, so it needs more than the place has.
     * - That caps its memory: 4 bytes a pixel, so at most 8 bytes for each pixel of the place.
     */
    fun pictureSize(widthPx: Int, heightPx: Int): PictureSize {
        fun stepped(px: Int) = (px.coerceIn(1, PICTURE_SIDE) + PICTURE_STEP - 1) / PICTURE_STEP * PICTURE_STEP
        val w = stepped(widthPx)
        val h = stepped(heightPx)
        return PictureSize(widthPx = w * 2, maxPixels = 2f * w * h)
    }

    /** A grid of buttons or switches: [columns] across and [rows] down, with the first [shown] of them in it. */
    data class Grid(val columns: Int, val rows: Int, val shown: Int)

    /** A button or a switch is at least this wide and this tall, in dp: the least a finger hits well. */
    const val CONTROL_DP = 48

    /** The room between two buttons or switches, in dp. */
    const val CONTROL_GAP_DP = 8

    /**
     * The grid for [count] buttons or switches in a box of [widthDp] by [heightDp].
     * - A cell is at least [CONTROL_DP] wide and tall. A box too small for all shows fewer of them,
     *   never smaller ones. The first ones are kept.
     * - There are at most [maxColumns] in a row, and at most two rows.
     * - With two rows they are spread evenly: five are three over two.
     */
    fun grid(count: Int, widthDp: Int, heightDp: Int, maxColumns: Int): Grid {
        if (count <= 0) return Grid(0, 0, 0)
        val cell = CONTROL_DP + CONTROL_GAP_DP
        val across = ((widthDp + CONTROL_GAP_DP) / cell).coerceIn(1, maxOf(1, maxColumns))
        val down = ((heightDp + CONTROL_GAP_DP) / cell).coerceIn(1, 2)
        val shown = minOf(count, across * down)
        val rows = if (shown <= across) 1 else down
        return Grid(columns = (shown + rows - 1) / rows, rows = rows, shown = shown)
    }

    /**
     * One place of a clock as its card writes it. [day] is "tomorrow" or "yesterday" when the day
     * there is not the day here, and [dayShort] the same in little room: "+1" or "-1".
     */
    data class ZoneTime(val label: String, val time: String, val day: String?, val dayShort: String?)

    /** A place's name. Without a label it is the last part of the zone's name: "Asia/Kolkata" is "Kolkata". */
    fun zoneLabel(zone: WidgetSpec.Zone): String = zone.label ?: zone.zone.substringAfterLast('/').replace('_', ' ')

    /** The pattern of a clock's time: "14:30" or "2:30 PM", and "14:30:05" or "2:30:05 PM" with [seconds]. */
    fun clockPattern(clock24: Boolean, seconds: Boolean): String = when {
        !seconds -> TimeFormats.clock(clock24)
        clock24 -> "HH:mm:ss"
        else -> "h:mm:ss a"
    }

    /** The most characters a time in [clockPattern] has: "22:30" is 5, "10:30:05 PM" is 11. */
    fun clockChars(clock24: Boolean, seconds: Boolean): Int = (if (clock24) 5 else 8) + (if (seconds) 3 else 0)

    /**
     * The time at each of [zones] at the moment [now] (milliseconds), written with [format], and
     * its day as seen from [here]. A zone the phone does not know is left out: the parser has
     * refused such a zone already, so this is only a guard.
     */
    fun zoneTimes(zones: List<WidgetSpec.Zone>, now: Long, here: ZoneId, format: DateTimeFormatter): List<ZoneTime> {
        val moment = Instant.ofEpochMilli(now)
        val today = moment.atZone(here).toLocalDate()
        return zones.mapNotNull { zone ->
            val id = runCatching { ZoneId.of(zone.zone) }.getOrNull() ?: return@mapNotNull null
            val there = moment.atZone(id)
            val day = there.toLocalDate()
            when {
                day.isAfter(today) -> ZoneTime(zoneLabel(zone), there.format(format), "tomorrow", "+1")
                day.isBefore(today) -> ZoneTime(zoneLabel(zone), there.format(format), "yesterday", "-1")
                else -> ZoneTime(zoneLabel(zone), there.format(format), null, null)
            }
        }
    }

    /** A digit is about this much of its font size wide. */
    private const val DIGIT_WIDTH = 0.62f

    /** The smallest font size of a clock's time, in dp. */
    private const val CLOCK_MIN_DP = 12

    /**
     * The font size, in dp, at which a time of [chars] characters fills [widthDp]: never under 12,
     * and never over [maxDp]. The size comes from the room and not from the text, so it does not
     * change as the digits do.
     */
    fun clockTextDp(widthDp: Int, chars: Int, maxDp: Int): Int =
        (widthDp / (chars.coerceAtLeast(1) * DIGIT_WIDTH)).toInt().coerceIn(CLOCK_MIN_DP, maxOf(CLOCK_MIN_DP, maxDp))

    /**
     * How many of [count] places a wide box [widthDp] wide shows side by side. Each needs room for
     * its time at the smallest size, and at least 48 dp. The first places are kept.
     */
    fun clockColumns(count: Int, widthDp: Int, chars: Int): Int {
        val cell = maxOf(CONTROL_DP, Math.round(chars * DIGIT_WIDTH * CLOCK_MIN_DP + 0.5f))
        return ((widthDp + CONTROL_GAP_DP) / (cell + CONTROL_GAP_DP)).coerceIn(1, maxOf(1, count))
    }

    /** A row of a clock in a large box is at least this tall, in dp. */
    const val CLOCK_ROW_DP = 28

    /** How many of [count] places a large box [heightDp] tall shows as rows, with [gapDp] between two. */
    fun clockRows(count: Int, heightDp: Int, gapDp: Int = 0): Int = ((heightDp + gapDp) / (CLOCK_ROW_DP + gapDp)).coerceIn(1, maxOf(1, count))

    /** What an agenda says with no event to show, when its description has no `empty` text. */
    const val NO_EVENTS = "No events"

    /** The headings of an agenda's first two days. Later days have their date. */
    const val TODAY = "Today"
    const val TOMORROW = "Tomorrow"

    /** One event of an agenda as its card writes it: [time] is "09:30", "all day" or "now". */
    data class AgendaLine(val time: String, val event: WidgetSpec.Event)

    /** One day of an agenda: [label] is "Today", "Tomorrow" or the day ("Tue 20 Oct"). It has at least one line. */
    data class AgendaDay(val label: String, val lines: List<AgendaLine>)

    /**
     * What an agenda shows at [now] (seconds): its events by day, in the time zone [zone].
     * - The days are today and the ones after it, as many as the description's `days`.
     * - An event that has ended is left out, with no run of the script. One with no `ends` is over
     *   when its day is.
     * - An event that began on an earlier day and still goes on is under Today.
     * - In a day, all-day events come first, then the others by their start.
     * - A time is written as [clock24] says ("14:30" or "2:30 PM"). An event with an end that has
     *   begun says "now".
     */
    fun agenda(spec: WidgetSpec.Agenda, now: Long, zone: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): List<AgendaDay> {
        fun at(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(zone)
        val today = at(now).toLocalDate()
        val last = today.plusDays(spec.days.coerceIn(1, 7) - 1L)
        val clock = TimeFormats.clockFormat(clock24, locale)
        val byDay = java.util.TreeMap<LocalDate, MutableList<WidgetSpec.Event>>()
        for (event in spec.events) {
            val began = at(event.starts).toLocalDate()
            val ends = event.ends
            val over = if (ends != null) ends <= now else began.isBefore(today)
            if (over) continue
            val day = if (began.isBefore(today)) today else began
            if (!day.isAfter(last)) byDay.getOrPut(day) { ArrayList() } += event
        }
        return byDay.map { (day, events) ->
            val label = when (day) {
                today -> TODAY
                today.plusDays(1) -> TOMORROW
                else -> day.format(TimeFormats.dayFormat(locale))
            }
            AgendaDay(
                label,
                // A stable sort: events at the same moment stay in the script's order.
                events.sortedWith(compareBy({ !it.allDay }, { it.starts })).map { e ->
                    val time = when {
                        e.allDay -> "all day"
                        e.ends != null && e.starts <= now -> "now"
                        else -> at(e.starts).format(clock)
                    }
                    AgendaLine(time, e)
                },
            )
        }
    }

    /**
     * How many of an agenda's events to draw in [heightDp]. [perDay] is the number of events of each
     * day. A day's heading is [headDp] tall and an event [rowDp].
     * - A heading is never drawn without an event under it.
     * - When events are left out, [moreDp] is kept free for the "+3 more" line.
     * - With any event at all, the first one is drawn, however low the box.
     */
    fun agendaShown(perDay: List<Int>, heightDp: Int, headDp: Int, rowDp: Int, moreDp: Int): Int {
        val total = perDay.sum()
        var used = 0
        var shown = 0
        for (count in perDay) {
            for (i in 0 until count) {
                val cost = rowDp + if (i == 0) headDp else 0
                val after = if (shown + 1 < total) moreDp else 0
                if (used + cost + after > heightDp) return maxOf(shown, 1)
                used += cost
                shown++
            }
        }
        return shown
    }

    /** The words a media card's three buttons hand to `onTap`, as a tap's `args`. */
    const val MEDIA_PREV = "prev"
    const val MEDIA_TOGGLE = "toggle"
    const val MEDIA_NEXT = "next"

    /** The big line of a media card: the title, or the app's name when the player gives none. */
    fun mediaTitle(spec: WidgetSpec.Media): String = spec.title?.takeIf { it.isNotBlank() } ?: spec.app

    /** The small line of a media card: "Artist · App". The app is left out where it is the big line. */
    fun mediaLine(spec: WidgetSpec.Media): String =
        listOfNotNull(spec.artist?.takeIf { it.isNotBlank() }, spec.app.takeIf { !spec.title.isNullOrBlank() && it.isNotBlank() }).joinToString(" · ")

    /**
     * Where playback is at [now], in milliseconds: from 0 to the duration, when that is known.
     * It moves only while playing, at the player's speed, from the last position the player gave.
     */
    fun mediaPosition(spec: WidgetSpec.Media, now: Long): Long {
        val speed = spec.speed.takeIf { it.isFinite() } ?: 1f
        val moved = if (spec.playing) ((now - spec.positionAt).coerceAtLeast(0) * speed.toDouble()) else 0.0
        val at = (spec.position + moved).coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
        return if (spec.duration > 0) at.coerceAtMost(spec.duration) else at
    }

    /** How far through the track playback is at [now], from 0 to 1. Null when the length is not known. */
    fun mediaFraction(spec: WidgetSpec.Media, now: Long): Float? =
        if (spec.duration > 0) (mediaPosition(spec, now).toDouble() / spec.duration).toFloat().coerceIn(0f, 1f) else null

    /** A position or a length as players write it: "0:07", "3:45", "1:02:03". */
    fun mediaTime(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return if (s >= HOUR) String.format(Locale.ROOT, "%d:%02d:%02d", s / HOUR, s % HOUR / 60, s % 60)
        else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    /** The least width of a media card's texts beside its buttons, in dp. */
    private const val MEDIA_TEXT_DP = 96

    /**
     * How many of a media card's three buttons a box [widthDp] wide has: 3 (previous, play or pause,
     * next), 2 (play or pause, next) or 1 (play or pause). A button is 48 dp, with 4 dp between two.
     * With [besideText] the texts are in the same row and keep [MEDIA_TEXT_DP].
     */
    fun mediaButtons(widthDp: Int, besideText: Boolean): Int = ((mediaRoom(widthDp, besideText) + 4) / (CONTROL_DP + 4)).coerceIn(1, 3)

    /** The width the buttons of a media card [widthDp] wide have, in dp. */
    private fun mediaRoom(widthDp: Int, besideText: Boolean) = widthDp - if (besideText) MEDIA_TEXT_DP + CONTROL_GAP_DP else 0

    /** The play or pause button is up to this much wider than the others, in dp, where there is room. */
    private const val PLAY_EXTRA_DP = 16

    /**
     * How wide the play or pause button is, in dp, beside [count] - 1 others ([mediaButtons]): 48 dp
     * like the others, and up to [PLAY_EXTRA_DP] more with the room that is left. It is the main one.
     */
    fun mediaPlayDp(widthDp: Int, besideText: Boolean, count: Int): Int {
        val buttons = count * CONTROL_DP + (count - 1) * 4
        return CONTROL_DP + (mediaRoom(widthDp, besideText) - buttons).coerceIn(0, PLAY_EXTRA_DP)
    }

    /** How long a switch shows "working" after a tap, with no answer from its script, in milliseconds. */
    const val WORKING_MS = 10_000L

    /** The view model starts at most one run in this time for a tap with the same arguments, in milliseconds. */
    const val TAP_WINDOW_MS = 2_000L

    /**
     * True for a tap at [now] that comes too soon after the last one with the same arguments, at
     * [last] (null for none). The view model starts no run for such a tap, so a switch does not
     * show "working" for it. Both times are on one clock that never goes back.
     */
    fun tooSoon(last: Long?, now: Long): Boolean = last != null && now - last in 0 until TAP_WINDOW_MS

    /** What a wide card takes from its height for its padding (24 dp) and its title line (20 dp), in dp. */
    const val WIDE_CHROME_DP = 44

    /**
     * A wide card this tall has room for its title line over two lines of text and a caption
     * (56 dp), or over a row of buttons with a little room around them, in dp. A card of one row
     * (92 dp on most phones) is lower.
     */
    const val TITLED_DP = WIDE_CHROME_DP + 56

    /**
     * Whether a wide card [heightDp] tall draws its title line over [spec]'s content. The line has
     * the badge, the title and the footer. A card with a [footer] ("Failed 10:42", "2 h ago")
     * always has it: a card has no other place for the footer. A screen reader hears the title
     * either way.
     * - Buttons, switches and a text leave the line out under [TITLED_DP]: a button needs 48 dp, and
     *   a text would get one line.
     * - Bars with their names leave it out when it would cost them their names.
     * - A media card never has it. Its own first line is the track's title, and "Now playing" over
     *   that says nothing new.
     * - Any other kind has it.
     */
    fun wideTitle(spec: WidgetSpec?, heightDp: Int, footer: Boolean = false): Boolean = when (spec) {
        is WidgetSpec.Buttons, is WidgetSpec.Toggles, is WidgetSpec.Text -> footer || heightDp >= TITLED_DP
        is WidgetSpec.Media -> false
        is WidgetSpec.Progress -> footer || heightDp - WIDE_CHROME_DP >= NAMED_BAR_DP * spec.items.size
        else -> true
    }

    /** The small line of a card about its content. [said] is the same for a screen reader, with whole words. */
    data class Footer(val text: String, val said: String, val failed: Boolean)

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /**
     * How long ago [then] was at [now], both in milliseconds: "just now", "5 min ago", "2 h ago",
     * "3 d ago". With [whole], in whole words: "5 minutes ago", "1 hour ago".
     */
    fun age(then: Long, now: Long, whole: Boolean = false): String {
        val seconds = (now - then) / 1000
        fun words(n: Long, short: String, long: String) = if (!whole) "$n $short ago" else if (n == 1L) "1 $long ago" else "$n ${long}s ago"
        return when {
            seconds < 60 -> "just now"
            seconds < HOUR -> words(seconds / 60, "min", "minute")
            seconds < DAY -> words(seconds / HOUR, "h", "hour")
            else -> words(seconds / DAY, "d", "day")
        }
    }

    /**
     * The footer of a card at [now] (milliseconds), or null for a card with nothing to say.
     * - The last run failed: "Failed 10:42", or "Failed 2 d ago" when that was not today. The content
     *   that was there stays on the card.
     * - The content is [old]: its age, "2 h ago".
     * - Current content has no footer.
     */
    fun footer(hasContent: Boolean, old: Boolean, setAt: Long, triedAt: Long, failed: Boolean, now: Long, zone: ZoneId): Footer? {
        if (failed) {
            val at = Instant.ofEpochMilli(triedAt).atZone(zone)
            val today = at.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return if (today) Footer("Failed ${HH_MM.format(at)}", "Failed at ${HH_MM.format(at)}", failed = true)
            else Footer("Failed ${age(triedAt, now)}", "Failed ${age(triedAt, now, whole = true)}", failed = true)
        }
        return if (hasContent && old) Footer(age(setAt, now), "Updated ${age(setAt, now, whole = true)}", failed = false) else null
    }

    /** The first line of an error with something on it, for a card with nothing else to show. */
    fun firstLine(error: String?): String? = error?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

    /**
     * The badge of a large card whose content has no icon: the first letter or digit of [title], as
     * a capital. Null for a title with none.
     */
    fun badgeLetter(title: String): String? = title.firstOrNull { it.isLetterOrDigit() }?.uppercase()

    /** The end of a failed run's log line: a tap on that line opens the editor at the line. */
    private val EDIT_TAIL = Regex("""\s*\(tap to edit line (\d+)\)$""")

    /** The end of the log line of a run that a switch stopped: a tap on that line allows the item. */
    private const val ALLOW_TAIL = "— tap to allow"

    /**
     * A failed run's error as a card shows it: its first line ([firstLine]) without what only the
     * log line can do. The stored error is the log line's text. A tap on that line edits the script
     * or allows an item. A tap on the card only runs the script again, so the card promises no more.
     * - "(tap to edit line 7)" at the end becomes "(line 7)".
     * - "— tap to allow" at the end is left out.
     */
    fun cardError(error: String?): String? {
        val line = firstLine(error) ?: return null
        return line.replace(EDIT_TAIL) { " (line ${it.groupValues[1]})" }.removeSuffix(ALLOW_TAIL).trim().ifEmpty { null }
    }

    /**
     * How many of [count] rows to draw in [heightDp], each [rowDp] tall, with [gapDp] between two.
     * When rows are left out, [moreDp] is kept free for the "+3 more" line, as long as one row still
     * fits above it. A card with a title line says "+3 more" there, and keeps no room: [moreDp] 0.
     */
    fun rowsShown(count: Int, heightDp: Int, rowDp: Int, moreDp: Int, gapDp: Int = 0): Int {
        if (count <= 0 || rowDp <= 0) return 0
        val step = rowDp + gapDp
        // n rows take n * step - gapDp.
        if (count * step - gapDp <= heightDp) return count
        val aboveMore = (heightDp + gapDp - moreDp) / step
        return if (aboveMore >= 1) aboveMore else ((heightDp + gapDp) / step).coerceIn(1, count)
    }

    /** Whether the "+3 more" line fits under [shown] rows ([rowsShown]). */
    fun showsMore(count: Int, shown: Int, heightDp: Int, rowDp: Int, moreDp: Int, gapDp: Int = 0): Boolean =
        shown < count && shown * (rowDp + gapDp) - gapDp + moreDp <= heightDp
}

/** The colour of a widget's badge, bar and chart: its role's colour in this theme, or the accent. */
internal fun roleColour(base: WidgetBase?): Color = colourOf(base?.color ?: StyledText.Colour.ACCENT)

/** Height of the "+3 more" line under a list, in dp. */
private const val MORE_DP = 20

/** The thickness of a bar's line ([LevelBar]). */
private val BAR_LINE = 6.dp

/** A wave of no height: a bar or ring that stands still. A wave would say "working". */
private val STILL: (Float) -> Float = { 0f }

/**
 * Draws the content of [spec] for the [box] it gets, inside a card ([WidgetCard]). It fills the
 * space it is given and draws no frame and no title.
 * - Each kind has a small, a wide and a large layout. A small one leaves parts out; the card's
 *   description for a screen reader still has them.
 * - [onTap] gets the action of a row, a button, a switch or an event. With null, nothing inside
 *   the content takes a tap (the Widgets screen).
 * - [stamp] is a number that changes when the content was filled again, also with the same
 *   description. A switch that waits for its script watches it. 0 for a card that has none.
 */
@Composable
internal fun KindBody(spec: WidgetSpec, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?, stamp: Long = 0) {
    when (spec) {
        is WidgetSpec.Stat -> StatBody(spec, box)
        is WidgetSpec.Text -> TextBody(spec, box)
        is WidgetSpec.Rows -> RowsBody(spec, onTap)
        is WidgetSpec.Progress -> ProgressBody(spec, box)
        is WidgetSpec.Chart -> ChartBody(spec, box)
        is WidgetSpec.Countdown -> CountdownBody(spec, box)
        is WidgetSpec.Buttons -> ButtonsBody(spec, onTap)
        is WidgetSpec.Toggles -> TogglesBody(spec, onTap, stamp)
        is WidgetSpec.Clock -> ClockBody(spec, box)
        is WidgetSpec.Agenda -> AgendaBody(spec, onTap)
        is WidgetSpec.Image -> ImageBody(spec, box)
        is WidgetSpec.Media -> MediaBody(spec, box, onTap)
    }
}

/**
 * [text] as a card draws it, with its styles ([widgetText]). Only a few fields may have styles
 * ([com.mtwabpin.kolahal.widgets.WidgetParser.STYLED]). It is made again only when the text or the
 * theme's colours change.
 */
@Composable
internal fun cardText(text: String): AnnotatedString = remember(text, Term.palette) { widgetText(text) }

/** One line in the middle of the body, for "Nothing to show" and the like. */
@Composable
internal fun QuietLine(text: String, small: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A bar from 0 to 1 in [colour]. It moves to a new value on the theme's spring. With [moving], and
 * while the widgets page is in view, it is a wave: something is in progress.
 * - A bar that stands still has a wave speed of 0. With any other speed the indicator asks for
 *   every frame, also while its wave has no height.
 * - It is not an item for a screen reader: the card's description says the value.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LevelBar(fraction: Float, moving: Boolean, colour: Color, modifier: Modifier = Modifier) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "level")
    val wave = moving && LocalWidgetsShown.current
    // A bar of 6 dp, not Material's 4: a card is seen from afar. The container stays 10 dp tall.
    val stroke = with(LocalDensity.current) { Stroke(width = BAR_LINE.toPx(), cap = StrokeCap.Round) }
    LinearWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
        color = colour,
        trackColor = colour.copy(alpha = 0.24f),
        stroke = stroke,
        trackStroke = stroke,
        // No dot at the track's end: the bar's end says where it stops.
        stopSize = 0.dp,
        amplitude = if (wave) WavyProgressIndicatorDefaults.indicatorAmplitude else STILL,
        wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = if (wave) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
    )
}

/** The big text of a kind, by the room it has. */
@Composable
private fun valueStyle(box: WidgetSizes.Box) = when (box) {
    WidgetSizes.Box.SMALL -> MaterialTheme.typography.titleLarge
    WidgetSizes.Box.WIDE -> MaterialTheme.typography.headlineSmall
    WidgetSizes.Box.LARGE -> MaterialTheme.typography.headlineLarge
}

/**
 * [style] for a value: extra bold and a little tighter, with digits of one width. A number that
 * ticks does not shake, and a value stands out from the words around it.
 */
internal fun TextStyle.asValue(): TextStyle = copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")

/**
 * A kind's big text, on one line, as a value ([asValue]). A text too long for its room gets
 * smaller, down to 12 sp, before it is cut with "…".
 */
@Composable
private fun BigText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val size = remember(style.fontSize) {
        val largest = style.fontSize.value.let { if (it.isNaN()) 22f else maxOf(it, 12f) }
        TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = largest.sp, stepSize = 1.sp)
    }
    val value = remember(style) { style.asValue() }
    Text(text, modifier = modifier, color = color, autoSize = size, style = value, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/**
 * `stat`: one big value with its label.
 * - Small: the value and the label, centred.
 * - Wide: the value with its change at the start, and a column at the end: the label over the
 *   first smaller value. The others go between them, as many as fit. A box under 150 dp has no
 *   room for that column: the label goes under the value, and a bar takes the label's place.
 * - Large: the value and its label at the top. The smaller values and the bar are at the bottom.
 */
@Composable
private fun StatBody(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    if (box == WidgetSizes.Box.SMALL) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(WidgetWords.joined(spec.value, spec.unit), valueStyle(box))
            spec.label?.let { Text(cardText(it), style = type.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        return
    }
    val colour = roleColour(spec.base)
    val large = box == WidgetSizes.Box.LARGE
    val change = listOfNotNull(WidgetWords.arrow(spec.trend), spec.delta).joinToString(" ")
    val value: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier) {
            BigText(spec.value, valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
            spec.unit?.let {
                Text(it, style = type.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 2.dp).alignByBaseline())
            }
            if (change.isNotEmpty()) {
                Text(change, style = type.labelLarge, color = colour, maxLines = 1, modifier = Modifier.padding(start = 8.dp).alignByBaseline())
            }
        }
    }
    if (large) {
        Column(Modifier.fillMaxSize()) {
            value(Modifier)
            spec.label?.let { Text(cardText(it), style = type.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.weight(1f))
            if (spec.more.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    spec.more.forEach { SmallValue(it, Modifier.weight(1f, fill = false)) }
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, colour, Modifier.padding(top = 8.dp)) }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth >= STAT_SIDE_FROM
        val first = spec.more.firstOrNull()
        // A smaller value between the big one and the end column needs about 64 dp, after 112 dp
        // for the big one and 96 dp for the column.
        val column = if (side && (spec.label != null || first != null)) 96.dp else 0.dp
        val between = if (side) spec.more.drop(1) else spec.more
        val half = maxWidth / 2
        val beside = ((maxWidth - 112.dp - column) / 64.dp).toInt().coerceIn(0, between.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    value(Modifier)
                    if (!side && spec.progress == null) spec.label?.let {
                        Text(cardText(it), style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                between.take(beside).forEach { SmallValue(it, Modifier.padding(start = 12.dp).widthIn(max = 72.dp)) }
                if (column > 0.dp) {
                    Column(Modifier.padding(start = 12.dp).widthIn(max = half), horizontalAlignment = Alignment.End) {
                        spec.label?.let {
                            // Over a smaller value the label is the column's heading. Alone it is a quiet note, on up to two lines.
                            if (first != null) Text(cardText(it), style = type.labelLarge.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else Text(cardText(it), style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                        }
                        first?.let {
                            Text(WidgetWords.joined(it.value, it.label), style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, colour, Modifier.padding(top = 4.dp)) }
        }
    }
}

/** From this width, a wide stat has a column at its end for its label and its first smaller value. */
private val STAT_SIDE_FROM = 150.dp

/** One of a stat's smaller values, over its label. */
@Composable
private fun SmallValue(small: WidgetSpec.Small, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(small.value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        small.label?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** `text`: a few lines, cut with "…" where the box ends, and a caption under them. A small box has no caption. */
@Composable
private fun TextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val style = when (box) {
        WidgetSizes.Box.SMALL -> type.bodySmall
        WidgetSizes.Box.WIDE -> type.bodyMedium
        WidgetSizes.Box.LARGE -> type.bodyLarge
    }
    Column(Modifier.fillMaxSize()) {
        // The text gets the height that is left, and its last line that fits ends in "…".
        Text(cardText(spec.text), style = style, color = MaterialTheme.colorScheme.onSurface, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).fillMaxWidth())
        if (box != WidgetSizes.Box.SMALL) spec.caption?.let {
            Text(cardText(it), style = type.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `list`: as many rows as fit, then "+3 more".
 * - Each row is in a lightly tinted container: large corners at the ends of the list, small ones
 *   between rows, and [ROW_GAP_DP] between two ([segmentShape]).
 * - A row that can be tapped is 48 dp tall. A row with a second line is 44 dp. Other rows are 32 dp.
 * - A narrow list (under 200 dp) leaves the second lines out.
 * - "+3 more" is at the end of the card's title line ([ReportMore]). A card without one has the
 *   line under the rows.
 */
@Composable
private fun RowsBody(spec: WidgetSpec.Rows, onTap: ((WidgetTap) -> Unit)?) {
    if (spec.items.isEmpty()) {
        QuietLine(spec.empty ?: WidgetWords.NOTHING)
        return
    }
    val inTitle = LocalMoreNote.current != null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val details = maxWidth >= 200.dp && spec.items.any { it.detail != null }
        val rowDp = when {
            spec.items.any { it.tap != null } -> 48
            details -> 44
            else -> 32
        }
        val heightDp = maxHeight.value.toInt()
        val moreDp = if (inTitle) 0 else MORE_DP
        val shown = WidgetWords.rowsShown(spec.items.size, heightDp, rowDp, moreDp, ROW_GAP_DP)
        val more = spec.items.size - shown
        ReportMore(if (more > 0) "+$more more" else null)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp)) {
            spec.items.take(shown).forEachIndexed { i, row ->
                key(i) { ListRow(row, rowDp.dp, details, segmentShape(i, shown), onTap) }
            }
            if (!inTitle && WidgetWords.showsMore(spec.items.size, shown, heightDp, rowDp, MORE_DP, ROW_GAP_DP)) {
                Text(
                    "+$more more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.height(MORE_DP.dp).wrapContentHeight(),
                )
            }
        }
    }
}

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
internal fun segmentColour(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

/**
 * One row of a list, in its container of [shape]. Its colour is on its icon, or else on a dot at
 * its start. The text at its end is dim: the colour says what the row is, not what its number is.
 * A row with a tap takes the press before the card does, so it hands a long-press on to the card
 * ([LocalCardLongPress]).
 *
 * A row with `done` has a checkbox at its start, in the row's colour.
 * - The box takes no tap of its own. A tap anywhere on the row is the row's tap, and the row is
 *   the checkbox for a screen reader.
 * - The box shows what the description says. Kolahal does not tick it on a tap: the script (or the
 *   built-in widget) shows the new state.
 */
@Composable
private fun ListRow(row: WidgetSpec.Row, height: Dp, details: Boolean, shape: Shape, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val tap = row.tap
    val done = row.done
    val colour = row.color?.let { colourOf(it) }
    val longPress = LocalCardLongPress.current
    val press = if (tap != null && onTap != null) {
        Modifier.combinedClickable(
            onClickLabel = tap.label,
            role = if (done != null) Role.Checkbox else Role.Button,
            onLongClickLabel = if (longPress != null) "arrange widgets" else null,
            onLongClick = longPress,
            onClick = { onTap(tap) },
        ).then(if (done != null) Modifier.semantics { toggleableState = ToggleableState(done) } else Modifier)
    } else {
        Modifier
    }
    Row(
        Modifier.fillMaxWidth().height(height).clip(shape).background(segmentColour()).then(press)
            .padding(start = if (done != null) 0.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = row.icon
        if (done != null) {
            Checkbox(
                checked = done,
                onCheckedChange = null,
                colors = if (colour != null) CheckboxDefaults.colors(checkedColor = colour) else CheckboxDefaults.colors(),
                modifier = Modifier.padding(start = 2.dp, end = 4.dp),
            )
        }
        if (icon != null) {
            Text(icon, style = type.titleMedium, color = colour ?: scheme.onSurface, maxLines = 1, modifier = Modifier.padding(end = 10.dp))
        } else if (done == null && colour != null) {
            Box(Modifier.padding(end = 10.dp).size(8.dp).clip(CircleShape).background(colour))
        }
        Column(Modifier.weight(1f)) {
            Text(cardText(row.title), style = type.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details) row.detail?.let {
                Text(cardText(it), style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        row.trailing?.let {
            Text(cardText(it), style = type.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * `progress`: how far along something is.
 * - A ring with its text in the middle, or a bar. With no `style`, a wide box gets the bar and the
 *   others get the ring.
 * - With `items`, it is several bars, each with its label and its text while there is room.
 * - With `moving`, the ring and the bars are waves while the page is in view.
 */
@Composable
private fun ProgressBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box) {
    val colour = roleColour(spec.base)
    when {
        spec.items.isNotEmpty() -> BarsBody(spec, colour)
        spec.style == "ring" || (spec.style != "bar" && box != WidgetSizes.Box.WIDE) -> RingBody(spec, box, colour)
        else -> BarBody(spec, box, colour)
    }
}

/**
 * A ring from 0 to 1 in [colour], [side] across. It moves to a new value on the theme's spring.
 * Like [LevelBar], it is a wave only with [moving] and in view, and it is no item for a screen reader.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Ring(fraction: Float, moving: Boolean, colour: Color, side: Dp) {
    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "ring")
    // A bigger ring gets a thicker line: an eighth of its side.
    val stroke = with(LocalDensity.current) { Stroke(width = (side / 8).coerceIn(4.dp, 14.dp).toPx(), cap = StrokeCap.Round) }
    val wave = moving && LocalWidgetsShown.current
    CircularWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = Modifier.size(side).clearAndSetSemantics {},
        color = colour,
        trackColor = colour.copy(alpha = 0.24f),
        stroke = stroke,
        trackStroke = stroke,
        amplitude = if (wave) WavyProgressIndicatorDefaults.indicatorAmplitude else STILL,
        wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
        waveSpeed = if (wave) WavyProgressIndicatorDefaults.CircularWavelength else 0.dp,
    )
}

/**
 * The ring.
 * - Small and large: as big as the box allows, with its text inside. Large has the label under it.
 * - Wide: the ring at the start, with its text and its label beside it.
 */
@Composable
private fun RingBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (box == WidgetSizes.Box.WIDE) {
            val side = min(maxHeight, maxWidth / 2).coerceAtLeast(24.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Ring(fraction, spec.moving, colour, side)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    BigText(text, type.titleLarge)
                    spec.label?.let { Text(it, style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        } else {
            val label = spec.label.takeIf { box == WidgetSizes.Box.LARGE }
            val labelRoom = if (label != null) 24.dp else 0.dp
            val side = min(maxWidth, maxHeight - labelRoom).coerceAtLeast(24.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    Ring(fraction, spec.moving, colour, side)
                    // The text stays clear of the ring's line, which is an eighth of the side.
                    BigText(text, if (side >= 96.dp) type.titleLarge else type.titleSmall, Modifier.padding(horizontal = side / 5))
                }
                if (label != null) {
                    Text(label, style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(labelRoom).wrapContentHeight())
                }
            }
        }
    }
}

/**
 * One bar under its text. The label is beside the text. A large box has the text at its top and
 * the bar at its bottom; the others have both in the middle.
 */
@Composable
private fun BarBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val large = box == WidgetSizes.Box.LARGE
    Column(Modifier.fillMaxSize(), verticalArrangement = if (large) Arrangement.SpaceBetween else Arrangement.Center) {
        Row {
            BigText(spec.text ?: WidgetWords.percent(spec.value, spec.max), valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
            if (box != WidgetSizes.Box.SMALL) spec.label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 96.dp).alignByBaseline(),
                )
            }
        }
        LevelBar(WidgetWords.fraction(spec.value, spec.max), spec.moving, colour, Modifier.padding(top = 4.dp))
    }
}

/** A bar with its label and its text over it needs this much height, in dp. */
private const val NAMED_BAR_DP = 30

/** A bar alone needs this much. */
private const val BAR_DP = 14

/**
 * Several bars. Each has its label and its text over it when the box is tall enough for all of
 * them. A lower box draws the bars alone, as many as fit. A bar's own colour wins over the card's.
 */
@Composable
private fun BarsBody(spec: WidgetSpec.Progress, colour: Color) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val named = maxHeight >= NAMED_BAR_DP.dp * spec.items.size
        val count = if (named) spec.items.size else (maxHeight / BAR_DP.dp).toInt().coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    Column {
                        if (named) Row {
                            Text(bar.label.orEmpty(), style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(bar.text ?: WidgetWords.percent(bar.value, bar.max), style = type.labelMedium, color = scheme.onSurface, maxLines = 1)
                        }
                        LevelBar(WidgetWords.fraction(bar.value, bar.max), spec.moving, bar.color?.let { colourOf(it) } ?: colour)
                    }
                }
            }
        }
    }
}

/**
 * `countdown`: the time to a moment, or since it. Kolahal does the ticking: the script gave the
 * moment once, and no run is needed as time passes.
 * - The time moves by the minute, with the header's clock ([rememberNow]).
 * - It moves by the second only while the text shows seconds and the page is in view.
 * - The number is big and its unit small: "36 days" ([WidgetWords.Count]).
 * - With a start (`from`), a bar runs from the start to the moment. In a wide box the bar takes the
 *   label's place. A small box has no bar. A large box has the time at its top and the bar at its
 *   bottom.
 * - The bar stands still. A countdown that waits is not at work, and a wave is drawn again on every
 *   frame, for days.
 * - The bar moves in steps ([WidgetWords.barStep]), so a clock that ticks does not draw it again
 *   every second.
 */
@Composable
private fun CountdownBody(spec: WidgetSpec.Countdown, box: WidgetSizes.Box) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    // The minute's tick. It also comes when home is back on screen, and when the clock is set.
    LaunchedEffect(minute) { now = System.currentTimeMillis() / 1000 }
    val count = WidgetWords.countdown(spec.to, now, spec.style, spec.reached, zone)
    if (count.ticks && LocalWidgetsShown.current) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            // Only while home is on screen.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val ms = System.currentTimeMillis()
                    now = ms / 1000
                    // Wake when the next second starts.
                    delay(1000 - ms % 1000)
                }
            }
        }
    }
    val small = box == WidgetSizes.Box.SMALL
    val large = box == WidgetSizes.Box.LARGE
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bar = if (small) null else WidgetWords.countdownFraction(spec.from, spec.to, now)?.let(WidgetWords::barStep)
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = if (large) Arrangement.Top else Arrangement.Center,
        horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        // The number big, its unit small: "36 days".
        Row {
            BigText(count.value, valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
            count.unit?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = muted, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp).alignByBaseline())
            }
        }
        if (bar == null || large) spec.label?.let {
            Text(it, style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (bar != null) {
            // A large box has the bar at its bottom.
            if (large) Spacer(Modifier.weight(1f))
            LevelBar(bar, moving = false, roleColour(spec.base), Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * `chart`: numbers over time as a line or as bars, with a headline.
 * - Small: the headline over a small plot.
 * - Wide: the headline with a small plot beside it.
 * - Large: the headline, the plot, and under it the labels of the first and the last point.
 * The plot has no words of its own. The card's description says the chart for a screen reader.
 */
@Composable
private fun ChartBody(spec: WidgetSpec.Chart, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val colour = roleColour(spec.base)
    val bars = spec.style == "bars"
    val value = spec.value
    val label = spec.label
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            if (value != null || label != null) Row {
                if (value != null) BigText(value, type.headlineSmall, Modifier.weight(1f, fill = false).alignByBaseline())
                if (label != null) {
                    Text(
                        label,
                        style = type.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = if (value != null) 8.dp else 0.dp).widthIn(max = 120.dp).alignByBaseline(),
                    )
                }
            }
            Plot(spec.points, bars, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp))
            if (spec.labels.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(spec.labels.first(), style = type.labelSmall, color = muted, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (spec.labels.size > 1) Text(spec.labels.last(), style = type.labelSmall, color = muted, maxLines = 1)
            }
        }
        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (value != null || label != null) Column(Modifier.padding(end = 12.dp).widthIn(max = 120.dp)) {
                if (value != null) BigText(value, type.titleLarge)
                if (label != null) Text(label, style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Plot(spec.points, bars, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxHeight())
        }
        WidgetSizes.Box.SMALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (value != null) BigText(value, type.titleMedium)
            Plot(spec.points, bars, spec.min, spec.max, colour, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/**
 * The plot of a chart, on a Canvas, in [colour]. There is no chart library.
 * - A line is 2 dp, with a wash under it. A dot marks the latest point. The dot has a ring in
 *   the card's colour, so it stays clear of the line.
 * - A bar is at most 24 dp thick, with 4 dp between bars (less when there are many). The latest
 *   bar is in full colour, the others lighter. A bar is round at its top and square at the
 *   baseline, which is a hairline.
 * - The scale is [min] to [max], or what the data gives ([WidgetWords.scale]). A value outside the
 *   scale is drawn at its edge.
 */
@Composable
private fun Plot(points: List<Float>, bars: Boolean, min: Float?, max: Float?, colour: Color, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val scale = remember(points, bars, min, max) { WidgetWords.scale(points, min, max, bars) }
    Canvas(modifier) {
        if (points.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val (low, high) = scale
        val line = 2.dp.toPx()
        // How high a value sits, from 0 at the bottom of the scale to 1 at its top.
        fun level(v: Float) = WidgetWords.fraction(v - low, high - low)
        if (bars) {
            // 4 dp between bars, less when there are many: a bar keeps two thirds of its slot.
            val slot = size.width / points.size
            val gap = minOf(4.dp.toPx(), slot / 3)
            val thick = (slot - gap).coerceIn(1f, 24.dp.toPx())
            val round = CornerRadius(minOf(5.dp.toPx(), thick / 2))
            // The latest bar is in full colour, the others lighter: the latest value is the news.
            val before = Path()
            val latest = Path()
            points.forEachIndexed { i, v ->
                val x = i * slot + (slot - thick) / 2
                // A value at the bottom of the scale still shows as a sliver.
                val top = (size.height * (1f - level(v))).coerceAtMost(size.height - line)
                (if (i == points.lastIndex) latest else before).addRoundRect(RoundRect(Rect(x, top, x + thick, size.height), topLeft = round, topRight = round))
            }
            drawPath(before, colour.copy(alpha = 0.4f))
            drawPath(latest, colour)
            drawLine(hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        } else {
            val dot = 4.dp.toPx()
            // Room for the dot and its ring at the end, the top and the bottom.
            val pad = dot + line
            val left = line / 2
            val right = (size.width - pad).coerceAtLeast(left)
            val tall = (size.height - 2 * pad).coerceAtLeast(0f)
            fun x(i: Int) = if (points.size == 1) right else left + (right - left) * i / (points.size - 1)
            fun y(v: Float) = pad + tall * (1f - level(v))
            if (points.size > 1) {
                val path = Path()
                val wash = Path()
                wash.moveTo(left, size.height)
                points.forEachIndexed { i, v ->
                    if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                    wash.lineTo(x(i), y(v))
                }
                wash.lineTo(right, size.height)
                wash.close()
                drawPath(wash, colour.copy(alpha = 0.2f))
                drawPath(path, colour, style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val end = Offset(x(points.lastIndex), y(points.last()))
            drawCircle(surface, radius = dot + line, center = end)
            drawCircle(colour, radius = dot, center = end)
        }
    }
}
