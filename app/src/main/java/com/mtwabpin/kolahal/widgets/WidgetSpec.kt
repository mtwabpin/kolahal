package com.mtwabpin.kolahal.widgets

import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.Json

/**
 * The size a script widget starts with, in cells of a 4-column grid, as home screens count them.
 * One cell is 3 of the page's 12 columns ([WidgetLayout.COLUMNS]). After it is placed, a widget is
 * resized like any other.
 */
enum class WidgetSize(val id: String, val cells: Int, val rows: Int) {
    S1X1("1x1", 1, 1), S2X1("2x1", 2, 1), S2X2("2x2", 2, 2), S4X1("4x1", 4, 1), S4X2("4x2", 4, 2), S4X4("4x4", 4, 4);

    /** Its width in the page's columns. */
    val span get() = cells * 3

    companion object {
        /** The size written as "2x1", in any case; "2×1" works too. Null for anything else. */
        fun named(id: String?): WidgetSize? {
            val wanted = id?.trim()?.lowercase()?.replace('×', 'x') ?: return null
            return entries.firstOrNull { it.id == wanted }
        }
    }
}

/**
 * The kinds of card Kolahal draws. [size] is the size a kind gets when its `@widget` line names none.
 * - A kind with [script] can be a script's widget: its `@widget` line names it and `widget.show` fills it.
 * - A kind without it is filled by Kolahal only. A script can't name it, and it is never stored.
 */
enum class WidgetKind(val id: String, val size: WidgetSize, val script: Boolean = true) {
    STAT("stat", WidgetSize.S2X1),
    TEXT("text", WidgetSize.S2X2),
    LIST("list", WidgetSize.S4X2),
    PROGRESS("progress", WidgetSize.S2X1),
    CHART("chart", WidgetSize.S2X2),
    COUNTDOWN("countdown", WidgetSize.S2X1),
    BUTTONS("buttons", WidgetSize.S4X1),
    TOGGLES("toggles", WidgetSize.S4X1),
    CLOCK("clock", WidgetSize.S2X1),
    AGENDA("agenda", WidgetSize.S4X2),

    /** A picture from the web. Home fetches it, not the script. */
    IMAGE("image", WidgetSize.S2X2),

    /** What is playing. A script can't see that and may not send media commands, so only Kolahal fills it. */
    MEDIA("media", WidgetSize.S4X1, script = false);

    companion object {
        /** The kinds a script's widget can have, in the order the docs list them. */
        val forScripts: List<WidgetKind> = entries.filter { it.script }

        /** The kind a script can use with this name, in any case. Null for anything else, and for a kind only Kolahal fills. */
        fun named(id: String?): WidgetKind? {
            val wanted = id?.trim()?.lowercase() ?: return null
            return forScripts.firstOrNull { it.id == wanted }
        }
    }
}

/**
 * What a tap does. It has exactly one of:
 * - [args]: the script runs again with these arguments.
 * - [url]: the web link opens.
 * [label] is what a screen reader says. With [toLog], home turns to the log after a tap that runs the script.
 */
data class WidgetTap(val args: String? = null, val url: String? = null, val label: String? = null, val toLog: Boolean = false)

/** The fields every kind has. All are optional. [staleAfter] and [refresh] are in seconds. */
data class WidgetBase(
    val title: String? = null,
    val icon: String? = null,
    val color: StyledText.Colour? = null,
    val tap: WidgetTap? = null,
    val staleAfter: Int? = null,
    val refresh: Int? = null,
)

/**
 * A script's widget description after it was checked: one class for each kind. Texts are plain and
 * already cut to their limits, and colours are roles of the theme.
 * - WidgetParser makes one from what a script passes to `widget.show`.
 * - [WidgetJson] stores it and reads it back.
 */
@Immutable
sealed interface WidgetSpec {
    val base: WidgetBase

    /** One of a [Stat]'s smaller values. */
    data class Small(val value: String, val label: String?)

    /** Kind `stat`: one big value. [trend] is `up`, `down` or `flat`. [progress] is 0 to 1. */
    data class Stat(
        override val base: WidgetBase,
        val value: String,
        val label: String? = null,
        val unit: String? = null,
        val delta: String? = null,
        val trend: String? = null,
        val more: List<Small> = emptyList(),
        val progress: Float? = null,
        val moving: Boolean = false,
    ) : WidgetSpec

    /** Kind `text`: a few lines and a caption. */
    data class Text(override val base: WidgetBase, val text: String, val caption: String? = null) : WidgetSpec

    /**
     * One row of [Rows]. With [done] (true or false) the row has a checkbox, ticked or not, for a todo
     * or a habit. With null it has none.
     */
    data class Row(
        val title: String,
        val detail: String? = null,
        val trailing: String? = null,
        val icon: String? = null,
        val color: StyledText.Colour? = null,
        val tap: WidgetTap? = null,
        val done: Boolean? = null,
    )

    /** Kind `list`: rows. [empty] is what to say with no rows. */
    data class Rows(override val base: WidgetBase, val items: List<Row>, val empty: String? = null) : WidgetSpec

    /** One bar of a [Progress] with several. */
    data class Bar(val label: String?, val value: Float, val max: Float, val text: String?, val color: StyledText.Colour?)

    /** Kind `progress`: a ring or bars. [style] is `ring` or `bar`; without it the box decides. */
    data class Progress(
        override val base: WidgetBase,
        val value: Float,
        val max: Float = 1f,
        val text: String? = null,
        val label: String? = null,
        val style: String? = null,
        val items: List<Bar> = emptyList(),
        val moving: Boolean = false,
    ) : WidgetSpec

    /** Kind `chart`: numbers over time. [style] is `line` or `bars`. [labels] name the first and the last point. */
    data class Chart(
        override val base: WidgetBase,
        val points: List<Float>,
        val style: String = "line",
        val min: Float? = null,
        val max: Float? = null,
        val value: String? = null,
        val label: String? = null,
        val labels: List<String> = emptyList(),
        val summary: String? = null,
    ) : WidgetSpec

    /**
     * Kind `countdown`: the time to or since a moment. [to] and [from] are in seconds since 1970.
     * [style] is `days` or `clock`. [reached] is what to say on the day.
     */
    data class Countdown(
        override val base: WidgetBase,
        val to: Long,
        val from: Long? = null,
        val label: String? = null,
        val style: String? = null,
        val reached: String? = null,
    ) : WidgetSpec

    /** One button of [Buttons]. */
    data class Button(val label: String, val tap: WidgetTap, val icon: String? = null, val color: StyledText.Colour? = null)

    /** Kind `buttons`: a grid of 1 to 8 buttons. A small card draws fewer buttons, never smaller ones. */
    data class Buttons(override val base: WidgetBase, val items: List<Button>) : WidgetSpec

    /** One switch of [Toggles]. [on] is its state as the script last reported it. Its [tap] always has `args`. */
    data class Toggle(val label: String, val on: Boolean, val tap: WidgetTap, val icon: String? = null)

    /** Kind `toggles`: 1 to 6 switches. A tap runs the script, and the script reports the new state. */
    data class Toggles(override val base: WidgetBase, val items: List<Toggle>) : WidgetSpec

    /** One place of a [Clock]. [zone] is a time zone name that `java.time.ZoneId.of` knows, such as `Asia/Kolkata`. */
    data class Zone(val zone: String, val label: String? = null)

    /** Kind `clock`: the time in up to 4 places. Kolahal ticks. With [seconds], it shows seconds. */
    data class Clock(override val base: WidgetBase, val zones: List<Zone>, val seconds: Boolean = false) : WidgetSpec

    /**
     * One event of an [Agenda]. [starts] and [ends] are in seconds since 1970.
     * - The card puts an event on the phone's own day of [starts], and drops it once [ends] has
     *   passed. With no [ends], it drops the event when that day is over.
     * - An [allDay] event shows no time. Its [starts] and [ends] must be midnights of the phone's
     *   time zone. A calendar that keeps all-day events at midnight UTC must be shifted first, or
     *   the event shows on the wrong day.
     */
    data class Event(
        val title: String,
        val starts: Long,
        val ends: Long? = null,
        val allDay: Boolean = false,
        val where: String? = null,
        val color: StyledText.Colour? = null,
        val tap: WidgetTap? = null,
    )

    /** Kind `agenda`: up to 20 events by day, for [days] days from today (1 to 7). [empty] is what to say with no events. */
    data class Agenda(override val base: WidgetBase, val events: List<Event>, val days: Int = 3, val empty: String? = null) : WidgetSpec

    /**
     * Kind `image`: a picture from the web, with a caption.
     * - [image] is an https link. Home fetches the picture when the widgets page is in view.
     * - [alt] says what the picture shows. A screen reader says it, and the card shows it when it has no picture.
     * - [fit] is `crop` or `fit`. Null means `crop`: the picture fills the card and loses its edges.
     *   With `fit`, the whole picture shows.
     */
    data class Image(
        override val base: WidgetBase,
        val image: String,
        val alt: String,
        val caption: String? = null,
        val fit: String? = null,
    ) : WidgetSpec

    /**
     * Kind `media`: what is playing, with its controls. Made by Kolahal only; never parsed, never stored.
     * Times are in milliseconds. [position] was right at the moment [positionAt] (on the phone's clock,
     * `System.currentTimeMillis`), and moves at [speed] while [playing]. A [duration] of 0 or less
     * means it is not known.
     */
    data class Media(
        override val base: WidgetBase,
        val title: String?,
        val artist: String?,
        val app: String,
        val playing: Boolean,
        val position: Long,
        val positionAt: Long,
        val speed: Float,
        val duration: Long,
    ) : WidgetSpec
}

/**
 * The stored form of a [WidgetSpec]: JSON whose keys are the script's field names, plus `v`
 * ([FORMAT]) and `kind`. A tap is `{ args | url, label, page }`, where `page` is `"log"` or absent.
 *
 * [decode] never trusts the stored text. The main process reads what the :script process wrote, so
 * it checks every field again:
 * - Null for anything odd: text that isn't JSON, another format number, an unknown kind, a field of
 *   the wrong type, a needed field that is missing, a number that isn't finite, a colour that isn't
 *   a role, a style that isn't one of its words, a tap without exactly one of `args` and `url`, a
 *   link that isn't a web link, a text or a description far longer than `widget.show` allows.
 * - A picture's link must start with `https://` and have at most [MAX_PICTURE_LINK] characters, and
 *   its `alt` text must not be blank. `widget.show` refuses the same ([isHttps]).
 * - A list longer than its limit is cut as `widget.show` cuts it, and a number outside its range
 *   is brought into it. The parser has done both already, so this changes nothing it wrote.
 * - Where `widget.show` refuses and does not cut, this refuses too: no buttons or more than
 *   [MAX_BUTTONS], no switches or more than [MAX_TOGGLES], a switch whose tap is not `{ args }`,
 *   no zones, a zone that `java.time.ZoneId.of` does not know.
 * - A kind only Kolahal fills (`media`) is never written and never read: [encode] gives "" for it,
 *   and [decode] gives null. So the :script process can't hand one to the main process.
 * - Unknown keys are skipped.
 */
object WidgetJson {
    const val FORMAT = 1

    /** The longest stored description that is read back, in characters. `widget.show` allows 32 KB. */
    const val MAX_JSON = 131_072
    /**
     * The longest text in one field, in UTF-16 units. The longest limit a field has is 500
     * characters, and one emoji can take several units. So this never refuses what the parser cut.
     */
    const val MAX_TEXT = 4_000
    const val MAX_ROWS = 12
    const val MAX_MORE = 3
    const val MAX_BARS = 4
    const val MAX_POINTS = 60
    const val MAX_LABELS = 2
    const val MAX_BUTTONS = 8
    const val MAX_TOGGLES = 6
    const val MAX_ZONES = 4
    const val MAX_EVENTS = 20
    /** The longest link of a picture, in characters. A longer one is refused, never cut. */
    const val MAX_PICTURE_LINK = 2_000
    const val MIN_DAYS = 1
    const val MAX_DAYS = 7
    const val DEFAULT_DAYS = 3
    const val MIN_STALE = 60
    const val MAX_STALE = 604_800
    const val MIN_REFRESH = 10
    const val MAX_REFRESH = 3_600
    /** The furthest moment a countdown may name, in seconds each way from 1970 (about the year 5100). */
    const val MAX_TIME = 99_999_999_999L

    private const val LOG = "log"

    /**
     * The JSON for [spec]. Fields that are null, false or empty are left out. A true-or-false field
     * that says something when it is false (a switch's `on`, a row's `done`) is written then too.
     * "" for a kind that is never stored ([WidgetKind.script] is false).
     */
    fun encode(spec: WidgetSpec): String {
        if (!kindOf(spec).script) return ""
        return runCatching { Json.stringify(toMap(spec)) }.getOrDefault("")
    }

    /** The description stored as [text]. Null for anything that isn't one: it never throws. */
    fun decode(text: String?): WidgetSpec? {
        if (text.isNullOrEmpty() || text.length > MAX_JSON) return null
        // Throwable, not Exception: deeply nested rubbish ends the reader with a StackOverflowError.
        return try {
            (Json.decode(text) as? Map<*, *>)?.let(::fromMap)
        } catch (_: Throwable) {
            null
        }
    }

    // ---- writing ----------------------------------------------------------------------------

    private fun kindOf(spec: WidgetSpec): WidgetKind = when (spec) {
        is WidgetSpec.Stat -> WidgetKind.STAT
        is WidgetSpec.Text -> WidgetKind.TEXT
        is WidgetSpec.Rows -> WidgetKind.LIST
        is WidgetSpec.Progress -> WidgetKind.PROGRESS
        is WidgetSpec.Chart -> WidgetKind.CHART
        is WidgetSpec.Countdown -> WidgetKind.COUNTDOWN
        is WidgetSpec.Buttons -> WidgetKind.BUTTONS
        is WidgetSpec.Toggles -> WidgetKind.TOGGLES
        is WidgetSpec.Clock -> WidgetKind.CLOCK
        is WidgetSpec.Agenda -> WidgetKind.AGENDA
        is WidgetSpec.Image -> WidgetKind.IMAGE
        is WidgetSpec.Media -> WidgetKind.MEDIA
    }

    /** A map of the pairs whose value is not null, in the order given. */
    private fun fields(vararg pairs: Pair<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((key, value) in pairs) if (value != null) out[key] = value
        return out
    }

    /** A number as JSON holds it: 0.68, not the float's long form 0.6800000071525574. Null when it isn't finite. */
    private fun number(f: Float?): Double? = f?.takeIf { it.isFinite() }?.toString()?.toDouble()

    private fun flag(on: Boolean): Boolean? = if (on) true else null

    private fun tapMap(tap: WidgetTap): Map<String, Any?> =
        fields("args" to tap.args, "url" to tap.url, "label" to tap.label, "page" to LOG.takeIf { tap.toLog })

    private fun toMap(spec: WidgetSpec): Map<String, Any?> {
        val base = spec.base
        val common = fields(
            "v" to FORMAT,
            "kind" to kindOf(spec).id,
            "title" to base.title,
            "icon" to base.icon,
            "color" to base.color?.role,
            "tap" to base.tap?.let(::tapMap),
            "staleAfter" to base.staleAfter,
            "refresh" to base.refresh,
        )
        val own = when (spec) {
            is WidgetSpec.Stat -> fields(
                "value" to spec.value,
                "label" to spec.label,
                "unit" to spec.unit,
                "delta" to spec.delta,
                "trend" to spec.trend,
                "more" to spec.more.takeIf { it.isNotEmpty() }?.map { fields("value" to it.value, "label" to it.label) },
                "progress" to number(spec.progress),
                "moving" to flag(spec.moving),
            )
            is WidgetSpec.Text -> fields("text" to spec.text, "caption" to spec.caption)
            is WidgetSpec.Rows -> fields(
                "items" to spec.items.map {
                    fields(
                        "title" to it.title, "detail" to it.detail, "trailing" to it.trailing, "icon" to it.icon,
                        "color" to it.color?.role, "tap" to it.tap?.let(::tapMap), "done" to it.done,
                    )
                },
                "empty" to spec.empty,
            )
            is WidgetSpec.Progress -> fields(
                "value" to number(spec.value),
                "max" to number(spec.max),
                "text" to spec.text,
                "label" to spec.label,
                "style" to spec.style,
                "items" to spec.items.takeIf { it.isNotEmpty() }?.map {
                    fields("label" to it.label, "value" to number(it.value), "max" to number(it.max), "text" to it.text, "color" to it.color?.role)
                },
                "moving" to flag(spec.moving),
            )
            is WidgetSpec.Chart -> fields(
                // A point that isn't finite has no JSON form: it goes as null, and the whole description reads back as nothing.
                "points" to spec.points.map { number(it) },
                "style" to spec.style,
                "min" to number(spec.min),
                "max" to number(spec.max),
                "value" to spec.value,
                "label" to spec.label,
                "labels" to spec.labels.takeIf { it.isNotEmpty() },
                "summary" to spec.summary,
            )
            is WidgetSpec.Countdown -> fields("to" to spec.to, "from" to spec.from, "label" to spec.label, "style" to spec.style, "reached" to spec.reached)
            is WidgetSpec.Buttons -> fields(
                "items" to spec.items.map { fields("label" to it.label, "icon" to it.icon, "color" to it.color?.role, "tap" to tapMap(it.tap)) },
            )
            is WidgetSpec.Toggles -> fields(
                "items" to spec.items.map { fields("label" to it.label, "icon" to it.icon, "on" to it.on, "tap" to tapMap(it.tap)) },
            )
            is WidgetSpec.Clock -> fields(
                "zones" to spec.zones.map { fields("zone" to it.zone, "label" to it.label) },
                "seconds" to flag(spec.seconds),
            )
            is WidgetSpec.Agenda -> fields(
                "events" to spec.events.map {
                    fields(
                        "title" to it.title, "starts" to it.starts, "ends" to it.ends, "allDay" to flag(it.allDay), "where" to it.where,
                        "color" to it.color?.role, "tap" to it.tap?.let(::tapMap),
                    )
                },
                "days" to spec.days,
                "empty" to spec.empty,
            )
            is WidgetSpec.Image -> fields("image" to spec.image, "alt" to spec.alt, "caption" to spec.caption, "fit" to spec.fit)
            // Not reached: [encode] stops before it.
            is WidgetSpec.Media -> emptyMap()
        }
        return common + own
    }

    // ---- reading ----------------------------------------------------------------------------

    /** Thrown inside [decode] for a field that isn't what it must be. It carries nothing, so it is cheap. */
    private class Odd : RuntimeException(null, null, false, false)

    private fun odd(): Nothing = throw Odd()

    private fun Map<*, *>.text(key: String): String? = when (val v = this[key]) {
        null -> null
        is String -> if (v.length <= MAX_TEXT) v else odd()
        else -> odd()
    }

    private fun Map<*, *>.needText(key: String): String = text(key) ?: odd()

    private fun finite(v: Any?): Float = (v as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: odd()

    private fun Map<*, *>.number(key: String): Float? = this[key]?.let(::finite)

    private fun Map<*, *>.seconds(key: String): Long? = this[key]?.let { v ->
        (v as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= -MAX_TIME && it <= MAX_TIME }?.toLong() ?: odd()
    }

    private fun Map<*, *>.flag(key: String): Boolean = when (val v = this[key]) {
        null -> false
        is Boolean -> v
        else -> odd()
    }

    /** A true-or-false field that has no value when it is left out: a row's `done`. */
    private fun Map<*, *>.flagOrNull(key: String): Boolean? = when (val v = this[key]) {
        null -> null
        is Boolean -> v
        else -> odd()
    }

    /** A time zone name that `java.time.ZoneId.of` knows. */
    private fun Map<*, *>.zone(key: String): String = needText(key).also { name -> runCatching { java.time.ZoneId.of(name) }.getOrNull() ?: odd() }

    /** A list that must have 1 to [max] items. `widget.show` refuses any other, so nothing is cut here. */
    private fun Map<*, *>.counted(key: String, max: Int): List<*> = list(key)?.takeIf { it.size in 1..max } ?: odd()

    private fun Map<*, *>.colour(key: String): StyledText.Colour? = text(key)?.let { role -> StyledText.Colour.entries.firstOrNull { it.role == role } ?: odd() }

    private fun Map<*, *>.oneOf(key: String, vararg words: String): String? = text(key)?.also { if (it !in words) odd() }

    private fun Map<*, *>.list(key: String): List<*>? = when (val v = this[key]) {
        null -> null
        is List<*> -> v
        else -> odd()
    }

    private fun obj(v: Any?): Map<*, *> = v as? Map<*, *> ?: odd()

    private fun isWebLink(url: String) = isHttps(url) || url.startsWith("http://", ignoreCase = true)

    /**
     * True for a link that starts with `https://`. A picture's link must: home fetches it without
     * a tap, so it never goes over plain http. The parser and [decode] both ask here, so they agree.
     */
    fun isHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

    /** A picture's link: needed, https, and not longer than [MAX_PICTURE_LINK]. */
    private fun Map<*, *>.picture(key: String): String = needText(key).also { if (!isHttps(it) || it.length > MAX_PICTURE_LINK) odd() }

    private fun Map<*, *>.tap(key: String): WidgetTap? {
        val m = obj(this[key] ?: return null)
        val args = m.text("args")
        val url = m.text("url")
        if ((args == null) == (url == null)) odd()
        if (url != null && !isWebLink(url)) odd()
        return WidgetTap(args, url, m.text("label"), toLog = m.oneOf("page", LOG) != null)
    }

    private fun fromMap(m: Map<*, *>): WidgetSpec? {
        if ((m["v"] as? Number)?.toDouble() != FORMAT.toDouble()) return null
        // Only a kind a script can fill is ever stored.
        val kind = WidgetKind.forScripts.firstOrNull { it.id == m["kind"] } ?: return null
        val base = WidgetBase(
            title = m.text("title"),
            icon = m.text("icon"),
            color = m.colour("color"),
            tap = m.tap("tap"),
            staleAfter = m.number("staleAfter")?.toInt()?.coerceIn(MIN_STALE, MAX_STALE),
            refresh = m.number("refresh")?.toInt()?.coerceIn(MIN_REFRESH, MAX_REFRESH),
        )
        return when (kind) {
            WidgetKind.STAT -> WidgetSpec.Stat(
                base,
                value = m.needText("value"),
                label = m.text("label"),
                unit = m.text("unit"),
                delta = m.text("delta"),
                trend = m.oneOf("trend", "up", "down", "flat"),
                more = m.list("more").orEmpty().take(MAX_MORE).map { obj(it).let { s -> WidgetSpec.Small(s.needText("value"), s.text("label")) } },
                progress = m.number("progress")?.coerceIn(0f, 1f),
                moving = m.flag("moving"),
            )
            WidgetKind.TEXT -> WidgetSpec.Text(base, m.needText("text"), m.text("caption"))
            WidgetKind.LIST -> WidgetSpec.Rows(
                base,
                items = (m.list("items") ?: odd()).take(MAX_ROWS).map {
                    val r = obj(it)
                    WidgetSpec.Row(r.needText("title"), r.text("detail"), r.text("trailing"), r.text("icon"), r.colour("color"), r.tap("tap"), r.flagOrNull("done"))
                },
                empty = m.text("empty"),
            )
            WidgetKind.PROGRESS -> WidgetSpec.Progress(
                base,
                value = m.number("value") ?: odd(),
                max = scale(m.number("max") ?: 1f),
                text = m.text("text"),
                label = m.text("label"),
                style = m.oneOf("style", "ring", "bar"),
                items = m.list("items").orEmpty().take(MAX_BARS).map {
                    val b = obj(it)
                    WidgetSpec.Bar(b.text("label"), b.number("value") ?: odd(), scale(b.number("max") ?: 1f), b.text("text"), b.colour("color"))
                },
                moving = m.flag("moving"),
            )
            WidgetKind.CHART -> WidgetSpec.Chart(
                base,
                points = (m.list("points") ?: odd()).takeLast(MAX_POINTS).map(::finite),
                style = m.oneOf("style", "line", "bars") ?: "line",
                min = m.number("min"),
                max = m.number("max"),
                value = m.text("value"),
                label = m.text("label"),
                labels = m.list("labels").orEmpty().take(MAX_LABELS).map { (it as? String)?.takeIf { s -> s.length <= MAX_TEXT } ?: odd() },
                summary = m.text("summary"),
            )
            WidgetKind.COUNTDOWN -> WidgetSpec.Countdown(
                base,
                to = m.seconds("to") ?: odd(),
                from = m.seconds("from"),
                label = m.text("label"),
                style = m.oneOf("style", "days", "clock"),
                reached = m.text("reached"),
            )
            WidgetKind.BUTTONS -> WidgetSpec.Buttons(
                base,
                items = m.counted("items", MAX_BUTTONS).map {
                    val b = obj(it)
                    WidgetSpec.Button(b.needText("label"), b.tap("tap") ?: odd(), b.text("icon"), b.colour("color"))
                },
            )
            WidgetKind.TOGGLES -> WidgetSpec.Toggles(
                base,
                items = m.counted("items", MAX_TOGGLES).map {
                    val t = obj(it)
                    // A switch runs its script. It never opens a link.
                    val tap = t.tap("tap")?.takeIf { tap -> tap.args != null } ?: odd()
                    WidgetSpec.Toggle(t.needText("label"), t.flagOrNull("on") ?: odd(), tap, t.text("icon"))
                },
            )
            WidgetKind.CLOCK -> WidgetSpec.Clock(
                base,
                zones = (m.list("zones") ?: odd()).take(MAX_ZONES).map { obj(it).let { z -> WidgetSpec.Zone(z.zone("zone"), z.text("label")) } }.ifEmpty { odd() },
                seconds = m.flag("seconds"),
            )
            WidgetKind.AGENDA -> WidgetSpec.Agenda(
                base,
                events = (m.list("events") ?: odd()).take(MAX_EVENTS).map {
                    val e = obj(it)
                    WidgetSpec.Event(
                        title = e.needText("title"),
                        starts = e.seconds("starts") ?: odd(),
                        ends = e.seconds("ends"),
                        allDay = e.flag("allDay"),
                        where = e.text("where"),
                        color = e.colour("color"),
                        tap = e.tap("tap"),
                    )
                },
                days = m.number("days")?.toInt()?.coerceIn(MIN_DAYS, MAX_DAYS) ?: DEFAULT_DAYS,
                empty = m.text("empty"),
            )
            WidgetKind.IMAGE -> WidgetSpec.Image(
                base,
                image = m.picture("image"),
                // The card shows this text when it has no picture, so a blank one is no text at all.
                alt = m.needText("alt").also { if (it.isBlank()) odd() },
                caption = m.text("caption"),
                fit = m.oneOf("fit", "crop", "fit"),
            )
            // Not reached: `media` is not among the kinds looked up above.
            WidgetKind.MEDIA -> null
        }
    }

    /** A bar's or a ring's full scale. It must be more than nothing, or the card would divide by zero. */
    private fun scale(max: Float): Float = if (max > 0f) max else odd()
}
