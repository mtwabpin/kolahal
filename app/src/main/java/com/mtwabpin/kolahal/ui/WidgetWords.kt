package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.widgets.WidgetSpec
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
     * The words under the two ends of a countdown's bar: its start [from] and its moment [to], at
     * [now], in the time zone [zone]. All three are in seconds.
     * - Each is its day: "1 Oct" and "20 Dec". A day in another year than now's has its year:
     *   "20 Dec 2027".
     * - When both are on one day, they are times of day in the form [clock24] says: "14:00" and "17:25".
     */
    fun countdownEnds(from: Long, to: Long, now: Long, zone: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): Pair<String, String> {
        val start = Instant.ofEpochSecond(from).atZone(zone)
        val end = Instant.ofEpochSecond(to).atZone(zone)
        if (start.toLocalDate() == end.toLocalDate()) {
            val clock = TimeFormats.clockFormat(clock24, locale)
            return start.format(clock) to end.format(clock)
        }
        val year = Instant.ofEpochSecond(now).atZone(zone).year
        fun day(t: java.time.ZonedDateTime) = t.format(DateTimeFormatter.ofPattern(if (t.year == year) "d MMM" else "d MMM yyyy", locale))
        return day(start) to day(end)
    }

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
                    spec.lead?.let { add(listOfNotNull(joined(it.value, it.unit), it.label).joinToString(", ")) }
                    if (spec.items.isEmpty() && spec.lead == null) add(spec.empty ?: NOTHING)
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
        // The note at the end of the title line ("Done today") is said after the title.
        val heading = listOfNotNull(title?.takeIf { it.isNotBlank() }, spec.base.note).joinToString(", ")
        return if (heading.isEmpty()) said else "$heading: $said"
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
     * What an image card says in the place of its picture, while it has no picture.
     * - [failed]: [PICTURE_UNAVAILABLE].
     * - Not there yet, and the widgets page is [inView]: [PICTURE_LOADING]. The fetch is on its way.
     * - Not there, and the page is not in view: the picture's [alt] text. Nothing is fetched then,
     *   so the card never says "Loading" for ever.
     */
    fun pictureNote(failed: Boolean, inView: Boolean, alt: String): String = when {
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

    /**
     * The day of a place whose day is the day here, where a large clock gives every place its day.
     * Elsewhere such a place says nothing about its day ([ZoneTime.day] is null).
     */
    const val SAME_DAY = "today"

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
    fun zoneTimes(zones: List<WidgetSpec.Zone>, now: Long, here: ZoneId, format: DateTimeFormatter): List<ZoneTime> =
        zoneTimesOf(places(zones), now, here, format)

    /** [zones] with their time zones, read once for a clock that ticks. A zone the phone does not know is left out. */
    fun places(zones: List<WidgetSpec.Zone>): List<Pair<WidgetSpec.Zone, ZoneId>> =
        zones.mapNotNull { zone -> runCatching { ZoneId.of(zone.zone) }.getOrNull()?.let { zone to it } }

    /** [zoneTimes] for [places] whose time zones are read already ([places]). */
    fun zoneTimesOf(places: List<Pair<WidgetSpec.Zone, ZoneId>>, now: Long, here: ZoneId, format: DateTimeFormatter): List<ZoneTime> {
        val moment = Instant.ofEpochMilli(now)
        val today = moment.atZone(here).toLocalDate()
        return places.map { (zone, id) ->
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
     * day. An event is [rowDp] tall, and a day's first event takes [headDp] more for the day's
     * heading (0 when the heading fits inside that row).
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
     * - A single ring (`style = "ring"`) leaves it out: it writes the title beside itself.
     * - A media card never has it. Its own first line is the track's title, and "Now playing" over
     *   that says nothing new.
     * - Any other kind has it.
     */
    fun wideTitle(spec: WidgetSpec?, heightDp: Int, footer: Boolean = false): Boolean = when (spec) {
        is WidgetSpec.Buttons, is WidgetSpec.Toggles, is WidgetSpec.Text -> footer || heightDp >= TITLED_DP
        is WidgetSpec.Media -> false
        // A single ring writes the title beside itself, as the mockup has it (RingBody).
        is WidgetSpec.Progress -> footer || if (spec.items.isEmpty() && spec.style == "ring") false else heightDp - WIDE_CHROME_DP >= NAMED_BAR_DP * spec.items.size
        else -> true
    }

    /** The small line of a card about its content. [said] is the same for a screen reader, with whole words. */
    data class Footer(val text: String, val said: String, val failed: Boolean)


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
    fun footer(hasContent: Boolean, old: Boolean, setAt: Long, triedAt: Long, failed: Boolean, now: Long, zone: ZoneId, clock24: Boolean): Footer? {
        if (failed) {
            val at = Instant.ofEpochMilli(triedAt).atZone(zone)
            // The time of day as the clock setting writes it, as everywhere else.
            val time = com.mtwabpin.kolahal.data.TimeFormats.clockFormat(clock24).format(at)
            val today = at.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return if (today) Footer("Failed $time", "Failed at $time", failed = true)
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

    /** A list's rows grow to share a box's height up to this height, in dp ([rowHeight]). */
    const val ROW_MAX_DP = 56

    /**
     * The height of each of a list's rows, in dp. [shown] of [count] rows are drawn ([rowsShown]),
     * each at least [rowDp] tall, with [gapDp] between two, in a box [heightDp] tall.
     * - When all of them are drawn, they grow to share the box's height evenly, up to [ROW_MAX_DP].
     *   A short list then fills its card, as the mockup has it.
     * - When some are left out, each is [rowDp]: the room is kept for "+3 more", and a row never
     *   gets smaller than [rowDp].
     */
    fun rowHeight(count: Int, shown: Int, heightDp: Float, rowDp: Int, gapDp: Int = 0): Float {
        if (shown <= 0 || shown < count) return rowDp.toFloat()
        val share = (heightDp + gapDp) / shown - gapDp
        return share.coerceIn(rowDp.toFloat(), maxOf(rowDp, ROW_MAX_DP).toFloat())
    }

    /** Whether the "+3 more" line fits under [shown] rows ([rowsShown]). */
    fun showsMore(count: Int, shown: Int, heightDp: Int, rowDp: Int, moreDp: Int, gapDp: Int = 0): Boolean =
        shown < count && shown * (rowDp + gapDp) - gapDp + moreDp <= heightDp
}
