/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.Json

/**
 * The size a script widget starts with, in cells of a 4-column grid, as home screens count them.
 * One cell is 3 of the page's 12 columns ([WidgetLayout.COLUMNS]). After it is placed, a widget is
 * resized like any other.
 */
enum class WidgetSize(val id: String, val cells: Int, val rows: Int) {
    S1X1("1x1", 1, 1), S2X1("2x1", 2, 1), S2X2("2x2", 2, 2), S4X1("4x1", 4, 1), S4X2(
        "4x2",
        4,
        2
    ),
    S4X4("4x4", 4, 4);

    /** Its width in the page's columns. */
    val span get() = cells * 3

    /** How the screens write it: "2×1". */
    val label: String get() = id.replace('x', '×')

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

    /** Two sides and the score between them: a match, a game of chess. */
    VERSUS("versus", WidgetSize.S4X1),

    /** The sun's path across the sky from its rise to its set, or the moon's. Kolahal moves it as time passes. */
    SKY("sky", WidgetSize.S2X2),

    /** What is playing. A script can't see that and may not send media commands, so only Kolahal fills it. */
    MEDIA("media", WidgetSize.S4X1, script = false);

    /**
     * The sizes this kind is drawn well at (docs/widgets-design.org, the catalog). A card draws for any
     * box it gets; these are the ones each kind is made for, and the ones the editor's reference offers.
     */
    val sizes: List<WidgetSize>
        get() = when (this) {
            STAT -> listOf(WidgetSize.S1X1, WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X1)
            TEXT -> listOf(WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X1, WidgetSize.S4X2)
            LIST -> listOf(WidgetSize.S2X2, WidgetSize.S4X2, WidgetSize.S4X4)
            // A 4×1 is for stages with their names, a 4×2 for rings with theirs.
            PROGRESS -> listOf(WidgetSize.S1X1, WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X1, WidgetSize.S4X2)
            COUNTDOWN -> listOf(WidgetSize.S1X1, WidgetSize.S2X1, WidgetSize.S2X2)
            CHART -> listOf(WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X2)
            BUTTONS -> listOf(WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X1, WidgetSize.S4X2)
            TOGGLES, CLOCK -> listOf(WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X1)
            AGENDA -> listOf(WidgetSize.S2X2, WidgetSize.S4X2, WidgetSize.S4X4)
            IMAGE -> listOf(WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X2, WidgetSize.S4X4)
            VERSUS -> listOf(WidgetSize.S2X1, WidgetSize.S2X2, WidgetSize.S4X1, WidgetSize.S4X2)
            SKY -> listOf(WidgetSize.S2X1, WidgetSize.S2X2)
            MEDIA -> listOf(WidgetSize.S2X1, WidgetSize.S4X1, WidgetSize.S4X2)
        }

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
data class WidgetTap(
    val args: String? = null,
    val url: String? = null,
    val label: String? = null,
    val toLog: Boolean = false
)

/**
 * The fields every kind has. All are optional. [staleAfter] and [refresh] are in seconds.
 * [note] is a short text on a large card's last line, in the card's color: "Done today".
 * [shape] is the card's own shape in the Shape look, where it has one shape ([WidgetShape]), until
 * the user chooses another.
 */
data class WidgetBase(
    val title: String? = null,
    val icon: String? = null,
    val color: StyledText.ColorRole? = null,
    val shape: WidgetShape? = null,
    val tap: WidgetTap? = null,
    val staleAfter: Int? = null,
    val refresh: Int? = null,
    val note: String? = null,
)

/**
 * A script's widget description after it was checked: one class for each kind. Texts are already
 * cut to their limits, and colors are roles of the theme.
 * - WidgetParser makes one from what a script passes to `widget.show`.
 * - [WidgetJson] stores it and reads it back.
 * - A text is plain. Only the fields of [WidgetParser.STYLED] may hold styles: bold, italic,
 *   underline, strike and color roles, in the form of [StyledText.inline]. A card draws them, and
 *   a screen reader hears the plain words.
 */
@Immutable
sealed interface WidgetSpec {
    val base: WidgetBase

    /** One of a [Stat]'s smaller values. */
    data class Small(val value: String, val label: String?)

    /**
     * Kind `stat`: one big value. [trend] is `up`, `down` or `flat`. [progress] is 0 to 1.
     * [points] are the value's recent numbers, the oldest first, drawn as a soft line behind it.
     * [scene] names a shader whose still picture is the card's ground ([StatScenes]).
     */
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
        val points: List<Float> = emptyList(),
        val scene: String? = null,
    ) : WidgetSpec

    /** Kind `text`: a few lines and a caption. */
    data class Text(override val base: WidgetBase, val text: String, val caption: String? = null) :
        WidgetSpec

    /**
     * One row of [Rows]. With [done] (true or false) the row has a checkbox, ticked or not, for a todo
     * or a habit. With null it has none. With a [range], the row has a bar from its low to its high,
     * on one scale with the other rows' bars.
     */
    data class Row(
        val title: String,
        val detail: String? = null,
        val trailing: String? = null,
        val icon: String? = null,
        val color: StyledText.ColorRole? = null,
        val tap: WidgetTap? = null,
        val done: Boolean? = null,
        val range: Range? = null,
    )

    /**
     * A row's range: from [low] to [high], such as a day's lowest and highest temperature. [value]
     * is a number between them that the bar marks with a dot, such as a price now. The parser keeps
     * [low] at most [high], and [value] between them.
     */
    data class Range(val low: Float, val high: Float, val value: Float? = null)

    /**
     * The big line over a list's rows: a [value] with a small [unit] and a [label] under it, such as
     * the next reminder ("42 min", "Call the bank · 22:22").
     */
    data class Lead(val value: String, val unit: String? = null, val label: String? = null)

    /** Kind `list`: rows, and perhaps a [lead] over them. [empty] is what to say with no rows. */
    data class Rows(
        override val base: WidgetBase,
        val items: List<Row>,
        val empty: String? = null,
        val lead: Lead? = null
    ) : WidgetSpec

    /**
     * One bar of a [Progress] with several, or one of its rings. [unit] follows a ring's scale
     * ("of 8 glasses"). A [moving] ring is a wave on its own, for the one still in progress. Only
     * rings take the two: `widget.show` refuses them on bars.
     */
    data class Bar(
        val label: String?,
        val value: Float,
        val max: Float,
        val text: String?,
        val color: StyledText.ColorRole?,
        val unit: String? = null,
        val moving: Boolean = false,
    )

    /** One zone of a gauge: it starts at [from] and runs to the next zone, or to the gauge's max, in [color]. */
    data class GaugeZone(val from: Float, val color: StyledText.ColorRole)

    /**
     * Kind `progress`: a ring, bars, a gauge, rings or stages.
     * - [style] is `ring`, `bar`, `gauge` or `rings`; without it the box decides between a ring
     *   and a bar. With [items], `rings` draws them as rings inside each other, and no style or
     *   `bar` as bars. `widget.show` refuses `ring` and `gauge` with items.
     * - A gauge is an arc from [min] to [max], in its [zones]' colors, filled to [value]. Its
     *   [unit] is written with the value. Only a gauge has a [min] other than 0, and [zones].
     * - With [stages], it is a path of named steps: [stage] (1 to their number) is the one it is
     *   at, and [value] of [max] is how far along the way to the next one.
     * [labelIcon] is a drawable before the ring's label: the Battery card's network. Only Kolahal's
     * own cards set it. A script can't, and it is never stored ([WidgetJson]).
     */
    data class Progress(
        override val base: WidgetBase,
        val value: Float,
        val max: Float = 1f,
        val text: String? = null,
        val label: String? = null,
        val style: String? = null,
        val items: List<Bar> = emptyList(),
        val moving: Boolean = false,
        val labelIcon: Int? = null,
        val min: Float = 0f,
        val unit: String? = null,
        val zones: List<GaugeZone> = emptyList(),
        val stages: List<String> = emptyList(),
        val stage: Int = 0,
    ) : WidgetSpec

    /**
     * Kind `chart`: numbers over time. [style] is `line`, `bars` or `dots`: a dot for each day, the
     * last point today, laid out as a calendar (DotCalendar). [labels] name the first and the last
     * point; dots have the days of the week instead.
     */
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
     * [style] is `days`, `clock`, `dots` (a dot a day) or `page` (a calendar page). [reached] is what
     * to say on the day.
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
    data class Button(
        val label: String,
        val tap: WidgetTap,
        val icon: String? = null,
        val color: StyledText.ColorRole? = null
    )

    /** Kind `buttons`: a grid of 1 to 8 buttons. A small card draws fewer buttons, never smaller ones. */
    data class Buttons(override val base: WidgetBase, val items: List<Button>) : WidgetSpec

    /** One switch of [Toggles]. [on] is its state as the script last reported it. Its [tap] always has `args`. */
    data class Toggle(
        val label: String,
        val on: Boolean,
        val tap: WidgetTap,
        val icon: String? = null
    )

    /** Kind `toggles`: 1 to 6 switches. A tap runs the script, and the script reports the new state. */
    data class Toggles(override val base: WidgetBase, val items: List<Toggle>) : WidgetSpec

    /** One place of a [Clock]. [zone] is a time zone name that `java.time.ZoneId.of` knows, such as `Asia/Kolkata`. */
    data class Zone(val zone: String, val label: String? = null)

    /** Kind `clock`: the time in up to 4 places. Kolahal ticks. With [seconds], it shows seconds. */
    data class Clock(
        override val base: WidgetBase,
        val zones: List<Zone>,
        val seconds: Boolean = false
    ) : WidgetSpec

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
        val color: StyledText.ColorRole? = null,
        val tap: WidgetTap? = null,
    )

    /** Kind `agenda`: up to 20 events by day, for [days] days from today (1 to 14). [empty] is what to say with no events. */
    data class Agenda(
        override val base: WidgetBase,
        val events: List<Event>,
        val days: Int = 3,
        val empty: String? = null
    ) : WidgetSpec

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
     * One side of a [Versus]: a player or a team.
     * - [mark] is a letter or two that the card draws on a Material shape, in the side's [color].
     *   Without it, the first letter of [name].
     * - [value] is the side's part of the bar between the two sides: its clock's time left, its
     *   share of the ball. [text] is what the bar's end says for it ("58:10"); without it, the value.
     */
    data class Side(
        val name: String,
        val mark: String? = null,
        val color: StyledText.ColorRole? = null,
        val value: Float? = null,
        val text: String? = null,
    )

    /**
     * Kind `versus`: two [sides] and the [score] between them, for a match or a game.
     * - [turn] is 1 or 2: the side to move, or the side with the ball. A dot marks it.
     * - With [moving], the turn's part of the bar is a wave that moves: its clock runs.
     * - [line] is a last line, such as the last move ("17. b3 · Berlin"): the script's `caption`.
     *   [badge] is a small text at the top ("67'", "LIVE").
     * - The bar shows only when both sides have a [Side.value], and they are not both 0.
     */
    data class Versus(
        override val base: WidgetBase,
        val sides: List<Side>,
        val score: String,
        val turn: Int? = null,
        val moving: Boolean = false,
        val line: String? = null,
        val badge: String? = null,
    ) : WidgetSpec

    /**
     * One of the moon's next phases on a [Sky]: its [phase] (the lit part, 0 to 1), whether it is
     * [waxing], and when it comes, [at], in seconds since 1970.
     */
    data class Phase(val phase: Float, val waxing: Boolean, val at: Long)

    /**
     * Kind `sky`: the sun on its path from [rises] to [sets], in seconds since 1970. Kolahal moves it
     * as time passes, with no run: the script gives the times, never a place on the path.
     * - With a [phase] (the lit part, 0 to 1) the card is the moon's, and [rises] and [sets] are
     *   the moon's. [waxing] says whether the lit part grows. [phases] are its next phases, up to 4.
     * - [sets] is after [rises], less than a day later.
     */
    data class Sky(
        override val base: WidgetBase,
        val rises: Long,
        val sets: Long,
        val phase: Float? = null,
        val waxing: Boolean = false,
        val phases: List<Phase> = emptyList(),
    ) : WidgetSpec

    /**
     * Kind `media`: what is playing, with its controls. Made by Kolahal only; never parsed, never stored.
     * Times are in milliseconds. [position] was right at the moment [positionAt] (on the phone's clock,
     * `System.currentTimeMillis`), and moves at [speed] while [playing]. A [duration] of 0 or less
     * means it is not known.
     * - [canSeek]: the player can move to a place in the track, so the bar takes a drag.
     * - [cover]: the track's album art, small ([com.mtwabpin.kolahal.control.Covers]). It is never
     *   changed once made, and the same picture is the same object.
     * - [ground]: the same art larger, for album art as background, while some placed card has it on.
     *   Null until it is made, and then the card's ground is [cover].
     * - [lastPlayed]: nothing plays. This is the player that played last, with its last track when
     *   known, and Play starts it again.
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
        val canSeek: Boolean = false,
        val cover: Bitmap? = null,
        val lastPlayed: Boolean = false,
        val ground: Bitmap? = null,
    ) : WidgetSpec
}

/**
 * The stored form of a [WidgetSpec]: JSON whose keys are the script's field names, plus `v`
 * ([FORMAT]) and `kind`. A tap is `{ args | url, label, page }`, where `page` is `"log"` or absent.
 *
 * [decode] never trusts the stored text. The main process reads what the :script process wrote, so
 * it checks every field again:
 * - Null for anything odd: text that isn't JSON, another format number, an unknown kind, a field of
 *   the wrong type, a needed field that is missing, a number that isn't finite, a color that isn't
 *   a role, a style that isn't one of its words, a tap without exactly one of `args` and `url`, a
 *   link that isn't a web link, a text or a description far longer than `widget.show` allows.
 * - A picture's link must start with `https://` and have at most [MAX_LINK] characters, and
 *   its `alt` text must not be blank. `widget.show` refuses the same ([isHttps]). A tap's link has
 *   the same limit.
 * - A text with a style marker in it is refused, except in the few fields that may have styles
 *   ([WidgetParser.STYLED]). Such a field passes through [StyledText.inline] again, so no stored
 *   text can make a card draw a link, a tag or a table.
 * - A list longer than its limit is cut as `widget.show` cuts it, and a number outside its range
 *   is brought into it. The parser has done both already, so this changes nothing it wrote.
 * - Where `widget.show` refuses and does not cut, this refuses too: no buttons or more than
 *   [MAX_BUTTONS], no switches or more than [MAX_TOGGLES], a switch whose tap is not `{ args }`,
 *   no zones, a zone that `java.time.ZoneId.of` does not know, a row's range whose low is above
 *   its high.
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

    // The limits the parser keeps are its own ([WidgetParser]): a card it shows reads back the same.
    const val MAX_ROWS = WidgetParser.MAX_ITEMS
    const val MAX_MORE = WidgetParser.MAX_MORE
    const val MAX_BARS = WidgetParser.MAX_BARS
    const val MAX_GAUGE_ZONES = WidgetParser.MAX_GAUGE_ZONES
    const val MAX_STAGES = WidgetParser.MAX_STAGES
    const val MAX_POINTS = WidgetParser.MAX_POINTS
    const val MAX_LABELS = 2
    const val MAX_BUTTONS = WidgetParser.MAX_BUTTONS
    const val MAX_TOGGLES = WidgetParser.MAX_TOGGLES
    const val MAX_ZONES = WidgetParser.MAX_ZONES
    const val MAX_EVENTS = WidgetParser.MAX_EVENTS

    /**
     * The longest link of a picture or a tap, in characters. `widget.show` refuses a longer one, and
     * never cuts it: a cut link opens another page.
     */
    const val MAX_LINK = 2_000
    const val MIN_DAYS = WidgetParser.MIN_DAYS
    const val MAX_DAYS = WidgetParser.MAX_DAYS
    const val DEFAULT_DAYS = WidgetParser.DEFAULT_DAYS
    const val MIN_STALE = WidgetParser.MIN_STALE
    const val MAX_STALE = WidgetParser.MAX_STALE
    const val MIN_REFRESH = WidgetParser.MIN_REFRESH
    const val MAX_REFRESH = WidgetParser.MAX_REFRESH

    /** The furthest moment a countdown may name, in seconds each way from 1970: the end of the year 9999. */
    const val MAX_TIME = WidgetParser.MAX_TIME

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

    /** The kind of [spec]: what `kind` says in its description. */
    fun kindOf(spec: WidgetSpec): WidgetKind = when (spec) {
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
        is WidgetSpec.Versus -> WidgetKind.VERSUS
        is WidgetSpec.Sky -> WidgetKind.SKY
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
        fields(
            "args" to tap.args,
            "url" to tap.url,
            "label" to tap.label,
            "page" to LOG.takeIf { tap.toLog })

    private fun toMap(spec: WidgetSpec): Map<String, Any?> {
        val base = spec.base
        val common = fields(
            "v" to FORMAT,
            "kind" to kindOf(spec).id,
            "title" to base.title,
            "icon" to base.icon,
            "color" to base.color?.role,
            "shape" to base.shape?.id,
            "tap" to base.tap?.let(::tapMap),
            "staleAfter" to base.staleAfter,
            "refresh" to base.refresh,
            "note" to base.note,
        )
        val own = when (spec) {
            is WidgetSpec.Stat -> fields(
                "value" to spec.value,
                "label" to spec.label,
                "unit" to spec.unit,
                "delta" to spec.delta,
                "trend" to spec.trend,
                "more" to spec.more.takeIf { it.isNotEmpty() }
                    ?.map { fields("value" to it.value, "label" to it.label) },
                "progress" to number(spec.progress),
                "moving" to flag(spec.moving),
                "points" to spec.points.takeIf { it.isNotEmpty() }?.map { number(it) },
                "scene" to spec.scene,
            )

            is WidgetSpec.Text -> fields("text" to spec.text, "caption" to spec.caption)
            is WidgetSpec.Rows -> fields(
                "items" to spec.items.map {
                    fields(
                        "title" to it.title,
                        "detail" to it.detail,
                        "trailing" to it.trailing,
                        "icon" to it.icon,
                        "color" to it.color?.role,
                        "tap" to it.tap?.let(::tapMap),
                        "done" to it.done,
                        "range" to it.range?.let { r ->
                            fields(
                                "low" to number(r.low),
                                "high" to number(r.high),
                                "value" to number(r.value)
                            )
                        },
                    )
                },
                "empty" to spec.empty,
                "lead" to spec.lead?.let {
                    fields(
                        "value" to it.value,
                        "unit" to it.unit,
                        "label" to it.label
                    )
                },
            )

            is WidgetSpec.Progress -> fields(
                "value" to number(spec.value),
                "max" to number(spec.max),
                "text" to spec.text,
                "label" to spec.label,
                "style" to spec.style,
                "items" to spec.items.takeIf { it.isNotEmpty() }?.map {
                    fields(
                        "label" to it.label,
                        "value" to number(it.value),
                        "max" to number(it.max),
                        "text" to it.text,
                        "color" to it.color?.role,
                        "unit" to it.unit,
                        "moving" to flag(it.moving),
                    )
                },
                "moving" to flag(spec.moving),
                "min" to number(spec.min.takeIf { it != 0f }),
                "unit" to spec.unit,
                "zones" to spec.zones.takeIf { it.isNotEmpty() }
                    ?.map { fields("from" to number(it.from), "color" to it.color.role) },
                "stages" to spec.stages.takeIf { it.isNotEmpty() },
                "stage" to spec.stage.takeIf { spec.stages.isNotEmpty() },
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

            is WidgetSpec.Countdown -> fields(
                "to" to spec.to,
                "from" to spec.from,
                "label" to spec.label,
                "style" to spec.style,
                "reached" to spec.reached
            )

            is WidgetSpec.Buttons -> fields(
                "items" to spec.items.map {
                    fields(
                        "label" to it.label,
                        "icon" to it.icon,
                        "color" to it.color?.role,
                        "tap" to tapMap(it.tap)
                    )
                },
            )

            is WidgetSpec.Toggles -> fields(
                "items" to spec.items.map {
                    fields(
                        "label" to it.label,
                        "icon" to it.icon,
                        "on" to it.on,
                        "tap" to tapMap(it.tap)
                    )
                },
            )

            is WidgetSpec.Clock -> fields(
                "zones" to spec.zones.map { fields("zone" to it.zone, "label" to it.label) },
                "seconds" to flag(spec.seconds),
            )

            is WidgetSpec.Agenda -> fields(
                "events" to spec.events.map {
                    fields(
                        "title" to it.title,
                        "starts" to it.starts,
                        "ends" to it.ends,
                        "allDay" to flag(it.allDay),
                        "where" to it.where,
                        "color" to it.color?.role,
                        "tap" to it.tap?.let(::tapMap),
                    )
                },
                "days" to spec.days,
                "empty" to spec.empty,
            )

            is WidgetSpec.Image -> fields(
                "image" to spec.image,
                "alt" to spec.alt,
                "caption" to spec.caption,
                "fit" to spec.fit
            )

            is WidgetSpec.Versus -> fields(
                "sides" to spec.sides.map {
                    fields(
                        "name" to it.name,
                        "mark" to it.mark,
                        "color" to it.color?.role,
                        "value" to number(it.value),
                        "text" to it.text,
                    )
                },
                "score" to spec.score,
                "turn" to spec.turn,
                "moving" to flag(spec.moving),
                "caption" to spec.line,
                "badge" to spec.badge,
            )

            is WidgetSpec.Sky -> fields(
                "rises" to spec.rises,
                "sets" to spec.sets,
                "phase" to number(spec.phase),
                "waxing" to flag(spec.waxing),
                "phases" to spec.phases.takeIf { it.isNotEmpty() }?.map {
                    fields("phase" to number(it.phase), "waxing" to flag(it.waxing), "at" to it.at)
                },
            )
            // Not reached: [encode] stops before it.
            is WidgetSpec.Media -> emptyMap()
        }
        return common + own
    }

    // ---- reading ----------------------------------------------------------------------------

    /** Thrown inside [decode] for a field that isn't what it must be. It carries nothing, so it is cheap. */
    private class Odd : RuntimeException(null, null, false, false)

    private fun odd(): Nothing = throw Odd()

    /**
     * A plain text field. One with a style marker in it is refused: `widget.show` takes the styles
     * out of every text but those of [WidgetParser.STYLED].
     */
    private fun Map<*, *>.text(key: String): String? = when (val v = this[key]) {
        null -> null
        is String -> if (v.length <= MAX_TEXT && !StyledText.hasMarker(v)) v else odd()
        else -> odd()
    }

    private fun Map<*, *>.needText(key: String): String = text(key) ?: odd()

    /**
     * A text field that may have styles ([WidgetParser.STYLED]). It goes through [StyledText.inline]
     * again, so only the styles a card draws come out: never a link, a tag or a table. What
     * `widget.show` wrote is in that form already, so this changes nothing of it.
     */
    private fun Map<*, *>.styled(key: String): String? = when (val v = this[key]) {
        null -> null
        is String -> if (v.length <= MAX_TEXT) StyledText.inline(v) else odd()
        else -> odd()
    }

    private fun Map<*, *>.needStyled(key: String): String = styled(key) ?: odd()

    private fun finite(v: Any?): Float =
        (v as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: odd()

    private fun Map<*, *>.number(key: String): Float? = this[key]?.let(::finite)

    private fun Map<*, *>.seconds(key: String): Long? = this[key]?.let { v ->
        (v as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= -MAX_TIME && it <= MAX_TIME }
            ?.toLong() ?: odd()
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
    private fun Map<*, *>.zone(key: String): String = needText(key).also { name ->
        runCatching { java.time.ZoneId.of(name) }.getOrNull() ?: odd()
    }

    /** A list that must have 1 to [max] items. `widget.show` refuses any other, so nothing is cut here. */
    private fun Map<*, *>.counted(key: String, max: Int): List<*> =
        list(key)?.takeIf { it.size in 1..max } ?: odd()

    private fun Map<*, *>.color(key: String): StyledText.ColorRole? = text(key)?.let { role ->
        StyledText.ColorRole.entries.firstOrNull { it.role == role } ?: odd()
    }

    private fun Map<*, *>.oneOf(key: String, vararg words: String): String? =
        text(key)?.also { if (it !in words) odd() }

    private fun Map<*, *>.list(key: String): List<*>? = when (val v = this[key]) {
        null -> null
        is List<*> -> v
        else -> odd()
    }

    private fun obj(v: Any?): Map<*, *> = v as? Map<*, *> ?: odd()

    private fun isWebLink(url: String) =
        isHttps(url) || url.startsWith("http://", ignoreCase = true)

    /**
     * True for a link that starts with `https://`. A picture's link must: home fetches it without
     * a tap, so it never goes over plain http. The parser and [decode] both ask here, so they agree.
     */
    fun isHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

    /** A picture's link: needed, https, and not longer than [MAX_LINK]. */
    private fun Map<*, *>.picture(key: String): String =
        needText(key).also { if (!isHttps(it) || it.length > MAX_LINK) odd() }

    private fun Map<*, *>.tap(key: String): WidgetTap? {
        val m = obj(this[key] ?: return null)
        val args = m.text("args")
        val url = m.text("url")
        if ((args == null) == (url == null)) odd()
        if (url != null && (!isWebLink(url) || url.length > MAX_LINK)) odd()
        return WidgetTap(args, url, m.text("label"), toLog = m.oneOf("page", LOG) != null)
    }

    private fun fromMap(m: Map<*, *>): WidgetSpec? {
        if ((m["v"] as? Number)?.toDouble() != FORMAT.toDouble()) return null
        // Only a kind a script can fill is ever stored.
        val kind = WidgetKind.forScripts.firstOrNull { it.id == m["kind"] } ?: return null
        val base = WidgetBase(
            title = m.text("title"),
            icon = m.text("icon"),
            color = m.color("color"),
            shape = m.text("shape")?.let { id -> WidgetShape.entries.firstOrNull { it.id == id } ?: odd() },
            tap = m.tap("tap"),
            staleAfter = m.number("staleAfter")?.toInt()?.coerceIn(MIN_STALE, MAX_STALE),
            refresh = m.number("refresh")?.toInt()?.coerceIn(MIN_REFRESH, MAX_REFRESH),
            note = m.text("note"),
        )
        return when (kind) {
            WidgetKind.STAT -> WidgetSpec.Stat(
                base,
                value = m.needText("value"),
                label = m.styled("label")?.let(StyledText::capitalized),
                unit = m.text("unit"),
                delta = m.text("delta"),
                trend = m.oneOf("trend", "up", "down", "flat"),
                more = m.list("more").orEmpty().take(MAX_MORE).map {
                    obj(it).let { s ->
                        WidgetSpec.Small(
                            s.needText("value"),
                            s.text("label")
                        )
                    }
                },
                progress = m.number("progress")?.coerceIn(0f, 1f),
                moving = m.flag("moving"),
                points = m.list("points").orEmpty().takeLast(MAX_POINTS).map(::finite),
                scene = m.text("scene")?.also { if (StatScenes.problem(it) != null) odd() },
            )

            WidgetKind.TEXT -> WidgetSpec.Text(
                base,
                m.needStyled("text"),
                m.styled("caption")?.let(StyledText::capitalized)
            )

            WidgetKind.LIST -> WidgetSpec.Rows(
                base,
                items = (m.list("items") ?: odd()).take(MAX_ROWS).map {
                    val r = obj(it)
                    WidgetSpec.Row(
                        r.needStyled("title"),
                        r.styled("detail")?.let(StyledText::capitalized),
                        r.styled("trailing"),
                        r.text("icon"),
                        r.color("color"),
                        r.tap("tap"),
                        r.flagOrNull("done"),
                        r["range"]?.let { raw -> range(obj(raw)) },
                    )
                },
                empty = m.text("empty")?.let(StyledText::capitalized),
                lead = m["lead"]?.let(::obj)?.let {
                    WidgetSpec.Lead(
                        it.needText("value"),
                        it.text("unit"),
                        it.text("label")?.let(StyledText::capitalized)
                    )
                },
            )

            WidgetKind.PROGRESS -> {
                val max = scale(m.number("max") ?: 1f)
                val min = (m.number("min") ?: 0f).also { if (it >= max) odd() }
                val stages = m.list("stages").orEmpty().take(MAX_STAGES).map {
                    (it as? String)?.takeIf { s -> s.length <= MAX_TEXT && !StyledText.hasMarker(s) }
                        ?: odd()
                }.also { if (it.size == 1) odd() }
                WidgetSpec.Progress(
                    base,
                    value = m.number("value") ?: odd(),
                    max = max,
                    text = m.text("text"),
                    label = m.text("label")?.let(StyledText::capitalized),
                    style = m.oneOf("style", "ring", "bar", "gauge", "rings"),
                    items = m.list("items").orEmpty().take(MAX_BARS).map {
                        val b = obj(it)
                        WidgetSpec.Bar(
                            b.text("label")?.let(StyledText::capitalized),
                            b.number("value") ?: odd(),
                            scale(b.number("max") ?: 1f),
                            b.text("text"),
                            b.color("color"),
                            b.text("unit"),
                            b.flag("moving"),
                        )
                    },
                    moving = m.flag("moving"),
                    min = min,
                    unit = m.text("unit"),
                    zones = m.list("zones").orEmpty().take(MAX_GAUGE_ZONES).map {
                        val z = obj(it)
                        WidgetSpec.GaugeZone(z.number("from") ?: odd(), z.color("color") ?: odd())
                    },
                    stages = stages.map(StyledText::capitalized),
                    // The stage it is at must be one of them.
                    stage = if (stages.isEmpty()) 0 else
                        m.number("stage")?.toInt()?.takeIf { it in 1..stages.size } ?: odd(),
                )
            }

            WidgetKind.CHART -> WidgetSpec.Chart(
                base,
                points = (m.list("points") ?: odd()).takeLast(MAX_POINTS).map(::finite),
                style = m.oneOf("style", "line", "bars", "dots") ?: "line",
                min = m.number("min"),
                max = m.number("max"),
                value = m.text("value"),
                label = m.text("label")?.let(StyledText::capitalized),
                labels = m.list("labels").orEmpty().take(MAX_LABELS).map {
                    (it as? String)?.takeIf { s ->
                        s.length <= MAX_TEXT && !StyledText.hasMarker(s)
                    } ?: odd()
                }.map(StyledText::capitalized),
                summary = m.text("summary"),
            )

            WidgetKind.COUNTDOWN -> WidgetSpec.Countdown(
                base,
                to = m.seconds("to") ?: odd(),
                from = m.seconds("from"),
                label = m.text("label")?.let(StyledText::capitalized),
                style = m.oneOf("style", "days", "clock", "dots", "page"),
                reached = m.text("reached")?.let(StyledText::capitalized),
            )

            WidgetKind.BUTTONS -> WidgetSpec.Buttons(
                base,
                items = m.counted("items", MAX_BUTTONS).map {
                    val b = obj(it)
                    WidgetSpec.Button(
                        StyledText.capitalized(b.needText("label")),
                        b.tap("tap") ?: odd(),
                        b.text("icon"),
                        b.color("color")
                    )
                },
            )

            WidgetKind.TOGGLES -> WidgetSpec.Toggles(
                base,
                items = m.counted("items", MAX_TOGGLES).map {
                    val t = obj(it)
                    // A switch runs its script. It never opens a link.
                    val tap = t.tap("tap")?.takeIf { tap -> tap.args != null } ?: odd()
                    WidgetSpec.Toggle(
                        StyledText.capitalized(t.needText("label")),
                        t.flagOrNull("on") ?: odd(),
                        tap,
                        t.text("icon")
                    )
                },
            )

            WidgetKind.CLOCK -> WidgetSpec.Clock(
                base,
                zones = (m.list("zones") ?: odd()).take(MAX_ZONES)
                    .map { obj(it).let { z -> WidgetSpec.Zone(z.zone("zone"), z.text("label")) } }
                    .ifEmpty { odd() },
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
                        color = e.color("color"),
                        tap = e.tap("tap"),
                    )
                },
                days = m.number("days")?.toInt()?.coerceIn(MIN_DAYS, MAX_DAYS) ?: DEFAULT_DAYS,
                empty = m.text("empty")?.let(StyledText::capitalized),
            )

            WidgetKind.IMAGE -> WidgetSpec.Image(
                base,
                image = m.picture("image"),
                // The card shows this text when it has no picture, so a blank one is no text at all.
                alt = m.needText("alt").also { if (it.isBlank()) odd() },
                caption = m.styled("caption")?.let(StyledText::capitalized),
                fit = m.oneOf("fit", "crop", "fit"),
            )

            WidgetKind.VERSUS -> WidgetSpec.Versus(
                base,
                // `widget.show` refuses any other number of sides, so none is cut here.
                sides = (m.list("sides") ?: odd()).takeIf { it.size == WidgetParser.SIDES }?.map {
                    val s = obj(it)
                    WidgetSpec.Side(
                        name = s.needText("name"),
                        mark = s.text("mark"),
                        color = s.color("color"),
                        value = s.number("value")?.coerceAtLeast(0f),
                        text = s.text("text"),
                    )
                } ?: odd(),
                score = m.needText("score"),
                turn = m.number("turn")?.toInt()?.also { if (it !in 1..WidgetParser.SIDES) odd() },
                moving = m.flag("moving"),
                line = m.styled("caption")?.let(StyledText::capitalized),
                badge = m.text("badge"),
            )

            WidgetKind.SKY -> {
                val rises = m.seconds("rises") ?: odd()
                val sets = m.seconds("sets") ?: odd()
                val phase = m.number("phase")?.coerceIn(0f, 1f)
                if (sets <= rises || sets - rises >= WidgetParser.dayOf(moon = phase != null)) odd()
                WidgetSpec.Sky(
                    base,
                    rises = rises,
                    sets = sets,
                    phase = phase,
                    waxing = m.flag("waxing"),
                    phases = m.list("phases").orEmpty().take(WidgetParser.MAX_PHASES).map {
                        val p = obj(it)
                        WidgetSpec.Phase(
                            (p.number("phase") ?: odd()).coerceIn(0f, 1f),
                            p.flag("waxing"),
                            p.seconds("at") ?: odd()
                        )
                    },
                )
            }
            // Not reached: `media` is not among the kinds looked up above.
            WidgetKind.MEDIA -> null
        }
    }

    /** A bar's or a ring's full scale. It must be more than nothing, or the card would divide by zero. */
    private fun scale(max: Float): Float = if (max > 0f) max else odd()

    /** A row's range. A low above the high is refused, as `widget.show` refuses it. A value outside the range is brought into it. */
    private fun range(m: Map<*, *>): WidgetSpec.Range {
        val low = m.number("low") ?: odd()
        val high = m.number("high") ?: odd()
        if (low > high) odd()
        return WidgetSpec.Range(low, high, m.number("value")?.coerceIn(low, high))
    }
}
