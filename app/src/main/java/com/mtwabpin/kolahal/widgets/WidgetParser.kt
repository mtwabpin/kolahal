package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.takeWhole
import com.mtwabpin.kolahal.script.Json

/**
 * Reads the description a script hands to `widget.show` (a Lua table or a JavaScript object) into
 * a checked [WidgetSpec]. The rules are in docs/widgets-design.org:
 * - Typed. Each field has one type. A number where text is expected becomes text. Anything else
 *   is an error that names the field and says what to pass.
 * - Closed. An unknown field is an error that lists the known ones, so a typo (`colour`) doesn't
 *   pass unseen.
 * - Bounded. Text is cut with "…" at its limit. A list keeps its first items. Buttons and switches
 *   are the exception: more than their limit is an error, because one that is cut is a tap the
 *   user can't make.
 * - A field with no value (`nil`, `null`, `undefined`) counts as left out.
 *
 * Every error is an IllegalArgumentException that starts with "widget: ". The script gets it as
 * its own error, with the line number.
 *
 * This object is pure: it has no Android in it, so JVM tests cover it.
 */
object WidgetParser {
    const val MAX_TITLE = 40
    const val MAX_ICON = 4
    const val MAX_TEXT = 500
    const val MAX_CAPTION = 60
    const val MAX_TAP_LABEL = 24
    const val MAX_ARGS = 200

    /** A stat's big value, and a chart's headline. */
    const val MAX_VALUE = 40

    /** A label, a list's `empty` text and a countdown's `reached` text. */
    const val MAX_LABEL = 60
    const val MAX_UNIT = 12
    const val MAX_DELTA = 16

    /** A smaller value of a stat (`more`) and its label; a row's `trailing`; a progress widget's `text`. */
    const val MAX_SHORT = 24
    const val MAX_ROW_TITLE = 80
    const val MAX_DETAIL = 120

    const val MAX_BAR_LABEL = 40

    /** One of a chart's two `labels`, under its first and its last point. */
    const val MAX_POINT_LABEL = 16

    /** A chart's `summary`, the words a screen reader says for it. */
    const val MAX_SUMMARY = 200

    /** A picture's `alt`, the words a screen reader says for it. The card shows them when it has no picture. */
    const val MAX_ALT = 200

    /** A list keeps its first [MAX_ITEMS] items, and a stat its first [MAX_MORE] smaller values. */
    const val MAX_ITEMS = 12
    const val MAX_MORE = 3

    /** A progress widget keeps its first [MAX_BARS] bars. A chart keeps its last [MAX_POINTS] points: the newest. */
    const val MAX_BARS = 4
    const val MAX_POINTS = 60

    /** A buttons widget has 1 to [MAX_BUTTONS] buttons, and a toggles widget 1 to [MAX_TOGGLES] switches. More is an error: nothing is cut. */
    const val MAX_BUTTONS = 8
    const val MAX_TOGGLES = 6

    /** A clock keeps its first [MAX_ZONES] places, and an agenda its first [MAX_EVENTS] events. */
    const val MAX_ZONES = 4
    const val MAX_EVENTS = 20

    /** An agenda shows [DEFAULT_DAYS] days from today, or `days` of them: [MIN_DAYS] to [MAX_DAYS]. */
    const val MIN_DAYS = 1
    const val MAX_DAYS = 7
    const val DEFAULT_DAYS = 3

    /** A toggles widget without `staleAfter` is old after this many seconds: the thing it switches can change elsewhere. */
    const val TOGGLES_STALE = 60

    /**
     * The largest time a countdown takes, in seconds: the end of the year 9999, so any date a
     * script can write passes. A larger number is a time in milliseconds, as JavaScript's
     * `Date.now()` gives it: an error that says to divide it.
     */
    private const val MAX_TIME = 253_402_300_799.0

    const val MIN_STALE = 60
    const val MAX_STALE = 604_800
    const val MIN_REFRESH = 10
    const val MAX_REFRESH = 3_600

    private val COMMON = listOf("kind", "title", "icon", "color", "tap", "staleAfter", "refresh")
    private val TAP = listOf("args", "url", "label", "page")
    private val STAT = listOf("value", "label", "unit", "delta", "trend", "more", "progress", "moving")
    private val SMALL = listOf("value", "label")
    private val TEXT = listOf("text", "caption")
    private val LIST = listOf("items", "empty")
    private val ROW = listOf("title", "detail", "trailing", "icon", "color", "tap", "done")
    private val PROGRESS = listOf("value", "max", "text", "label", "style", "items", "moving")
    private val BAR = listOf("label", "value", "max", "text", "color")
    private val CHART = listOf("points", "style", "min", "max", "value", "label", "labels", "summary")
    private val COUNTDOWN = listOf("to", "from", "label", "style", "reached")
    private val BUTTONS = listOf("items")
    private val BUTTON = listOf("label", "icon", "color", "tap")
    private val TOGGLES = listOf("items")
    private val TOGGLE = listOf("label", "icon", "on", "tap")
    private val CLOCK = listOf("zones", "seconds")
    private val ZONE = listOf("zone", "label")
    private val AGENDA = listOf("events", "days", "empty")
    /**
     * An event may carry `id`. It is taken and not kept: `calendar.events()` gives its rows one, and
     * those rows go straight into `events`.
     */
    private val EVENT = listOf("title", "starts", "ends", "allDay", "where", "color", "tap", "id")
    private val IMAGE = listOf("image", "alt", "caption", "fit")

    private val TRENDS = listOf("up", "down", "flat")

    /**
     * Every field name a description can have, at any level. No name may be a Lua keyword (a script
     * writes `{ title = … }`), and none may be `status` or `notify`: those words in a script's code
     * switch on access items ([com.mtwabpin.kolahal.script.ScriptMeta.setsStatus]). A test checks this.
     */
    val FIELDS: Set<String> = (
        COMMON + TAP + STAT + SMALL + TEXT + LIST + ROW + PROGRESS + BAR + CHART + COUNTDOWN +
            BUTTONS + BUTTON + TOGGLES + TOGGLE + CLOCK + ZONE + AGENDA + EVENT + IMAGE
        ).toSet()

    /** The fields of a [kind] widget, after the common ones. */
    private fun own(kind: WidgetKind): List<String> = when (kind) {
        WidgetKind.STAT -> STAT
        WidgetKind.TEXT -> TEXT
        WidgetKind.LIST -> LIST
        WidgetKind.PROGRESS -> PROGRESS
        WidgetKind.CHART -> CHART
        WidgetKind.COUNTDOWN -> COUNTDOWN
        WidgetKind.BUTTONS -> BUTTONS
        WidgetKind.TOGGLES -> TOGGLES
        WidgetKind.CLOCK -> CLOCK
        WidgetKind.AGENDA -> AGENDA
        WidgetKind.IMAGE -> IMAGE
        WidgetKind.MEDIA -> notForScripts(kind)
    }

    /** A kind only Kolahal fills has no description a script can write. */
    private fun notForScripts(kind: WidgetKind): Nothing = throw IllegalArgumentException("widget: a script can't fill a ${kind.id} widget")

    /**
     * [raw] as a checked description.
     * - [default] is the kind of the script's `@widget` line. The description's own `kind` wins.
     * - [text] is called for every text before it is cut. It masks secrets. The styles are removed
     *   here, before it is called: a style mark in the middle of a secret would hide it from the mask.
     * - [link] is called for every link, also a picture's. It returns the link as it may be stored,
     *   or throws for a link that is refused.
     */
    fun parse(raw: Map<*, *>, default: WidgetKind, text: (String) -> String, link: (String) -> String): WidgetSpec {
        val kind = raw["kind"]?.let {
            WidgetKind.named(it as? String) ?: throw IllegalArgumentException("widget: no kind ${shown(it)}; use ${WidgetKind.forScripts.joinToString { k -> k.id }}")
        } ?: default
        if (!kind.script) notForScripts(kind)
        // "an image widget", "an agenda widget".
        val a = if (kind.id.first() in "aeiou") "an" else "a"
        val t = Table(raw, "", "$a ${kind.id} widget has no field", COMMON + own(kind), text, link)
        val base = WidgetBase(
            title = t.text("title", MAX_TITLE),
            icon = t.text("icon", MAX_ICON),
            color = t.colour(),
            tap = t.tap(),
            staleAfter = t.seconds("staleAfter", MIN_STALE, MAX_STALE),
            refresh = t.seconds("refresh", MIN_REFRESH, MAX_REFRESH),
        )
        return when (kind) {
            WidgetKind.STAT -> stat(t, base)
            WidgetKind.TEXT -> WidgetSpec.Text(
                base,
                text = t.need("text", MAX_TEXT, "the text to show", lines = true),
                caption = t.text("caption", MAX_CAPTION),
            )
            WidgetKind.LIST -> rows(t, base)
            WidgetKind.PROGRESS -> progress(t, base)
            WidgetKind.CHART -> chart(t, base)
            WidgetKind.COUNTDOWN -> countdown(t, base)
            WidgetKind.BUTTONS -> buttons(t, base)
            WidgetKind.TOGGLES -> toggles(t, base)
            WidgetKind.CLOCK -> clock(t, base)
            WidgetKind.AGENDA -> agenda(t, base)
            WidgetKind.IMAGE -> image(t, base)
            WidgetKind.MEDIA -> notForScripts(kind)
        }
    }

    private const val ALT_IS = "what the picture shows, in words, for a screen reader"

    /**
     * A picture from the web. The script gives its link, and home fetches it.
     * - `image` is needed. It must be an https link. It is never cut: one that is too long is an error.
     * - `alt` is needed, and it can't be empty. A screen reader says it, and the card shows it when it has no picture.
     */
    private fun image(t: Table, base: WidgetBase) = WidgetSpec.Image(
        base,
        image = t.picture("image"),
        alt = t.need("alt", MAX_ALT, ALT_IS).ifEmpty { t.fail("alt is needed: $ALT_IS") },
        caption = t.text("caption", MAX_CAPTION),
        fit = t.choice("fit", listOf("crop", "fit")),
    )

    private fun stat(t: Table, base: WidgetBase) = WidgetSpec.Stat(
        base,
        value = t.need("value", MAX_VALUE, "the big value, as text or a number"),
        label = t.text("label", MAX_LABEL),
        unit = t.text("unit", MAX_UNIT),
        delta = t.text("delta", MAX_DELTA),
        trend = t.choice("trend", TRENDS),
        more = t.items("more", MAX_MORE, "{ value, label }", SMALL).map { m ->
            WidgetSpec.Small(m.need("value", MAX_SHORT, "the smaller value, as text or a number"), m.text("label", MAX_SHORT))
        },
        progress = t.number("progress", "a number from 0 to 1")?.coerceIn(0.0, 1.0)?.toFloat(),
        moving = t.flag("moving"),
    )

    private fun rows(t: Table, base: WidgetBase): WidgetSpec.Rows {
        if (!t.has("items")) t.fail("items is needed: a list of rows, each { ${ROW.joinToString()} }")
        return WidgetSpec.Rows(
            base,
            items = t.items("items", MAX_ITEMS, "{ ${ROW.joinToString()} }", ROW).map { r ->
                WidgetSpec.Row(
                    title = r.need("title", MAX_ROW_TITLE, "the row's text"),
                    detail = r.text("detail", MAX_DETAIL),
                    trailing = r.text("trailing", MAX_SHORT),
                    icon = r.text("icon", MAX_ICON),
                    color = r.colour(),
                    tap = r.tap(),
                    done = r.flagOrNull("done"),
                )
            },
            empty = t.text("empty", MAX_LABEL),
        )
    }

    /**
     * The number of items in the list [key], which must have 1 to [max]. More is an error, not a cut:
     * a button that silently went missing would be a tap the user can't make. [things] names the
     * items ("buttons"), and [shape] shows one.
     */
    private fun count(t: Table, key: String, max: Int, things: String, shape: String) {
        val all = t.list(key, shape) ?: t.fail("$key is needed: a list of $things, each $shape")
        if (all.isEmpty()) t.fail("$key is empty: give 1 to $max $things")
        if (all.size > max) t.fail("$key has ${all.size} $things: give $max at most")
    }

    /** 1 to [MAX_BUTTONS] buttons. Each has a label and a tap. */
    private fun buttons(t: Table, base: WidgetBase): WidgetSpec.Buttons {
        val shape = "{ ${BUTTON.joinToString()} }"
        count(t, "items", MAX_BUTTONS, "buttons", shape)
        return WidgetSpec.Buttons(
            base,
            items = t.items("items", MAX_BUTTONS, shape, BUTTON).map { b ->
                WidgetSpec.Button(
                    label = b.need("label", MAX_SHORT, "the button's text"),
                    tap = b.tap() ?: b.fail("tap is needed: what the button does, { args } or { url }"),
                    icon = b.text("icon", MAX_ICON),
                    color = b.colour(),
                )
            },
        )
    }

    /**
     * 1 to [MAX_TOGGLES] switches. Each has a label, its state (`on`) and a tap that runs the script
     * again: Kolahal never guesses the new state, the script reports it. Without `staleAfter`, the
     * content is old after [TOGGLES_STALE] seconds.
     */
    private fun toggles(t: Table, base: WidgetBase): WidgetSpec.Toggles {
        val shape = "{ ${TOGGLE.joinToString()} }"
        count(t, "items", MAX_TOGGLES, "switches", shape)
        return WidgetSpec.Toggles(
            base.copy(staleAfter = base.staleAfter ?: TOGGLES_STALE),
            items = t.items("items", MAX_TOGGLES, shape, TOGGLE).map { s ->
                val label = s.need("label", MAX_SHORT, "the switch's text")
                val on = s.flagOrNull("on") ?: s.fail("on is needed: true or false, the state now")
                val tap = s.tap() ?: s.fail("tap is needed: { args }, the arguments that switch it")
                if (tap.args == null) s.fail("tap must be { args }, not { url }: a switch runs this script again")
                WidgetSpec.Toggle(label, on, tap, icon = s.text("icon", MAX_ICON))
            },
        )
    }

    /** The time in up to [MAX_ZONES] places. The script gives the places once, and Kolahal ticks. */
    private fun clock(t: Table, base: WidgetBase): WidgetSpec.Clock {
        val shape = "{ ${ZONE.joinToString()} }"
        val zones = t.items("zones", MAX_ZONES, shape, ZONE).map { z -> WidgetSpec.Zone(zone = z.zone("zone"), label = z.text("label", MAX_SHORT)) }
        if (zones.isEmpty()) t.fail("zones is needed: a list of places, each $shape")
        return WidgetSpec.Clock(base, zones, seconds = t.flag("seconds"))
    }

    /** Events by day. The first [MAX_EVENTS] are kept. Kolahal writes the times and drops events that have ended. */
    private fun agenda(t: Table, base: WidgetBase): WidgetSpec.Agenda {
        val shape = "{ ${EVENT.joinToString()} }"
        if (!t.has("events")) t.fail("events is needed: a list of events, each $shape")
        return WidgetSpec.Agenda(
            base,
            events = t.items("events", MAX_EVENTS, shape, EVENT).map { e ->
                val title = e.need("title", MAX_ROW_TITLE, "the event's name")
                val starts = e.time("starts") ?: e.fail("starts is needed: when it starts, as a time in seconds (os.time() in Lua, Date.now() / 1000 in JavaScript)")
                val ends = e.time("ends")
                if (ends != null && ends < starts) e.fail("ends must not be before starts")
                WidgetSpec.Event(title, starts, ends, allDay = e.flag("allDay"), where = e.text("where", MAX_LABEL), color = e.colour(), tap = e.tap())
            },
            days = t.number("days", "a number of days, from $MIN_DAYS to $MAX_DAYS")?.toInt()?.coerceIn(MIN_DAYS, MAX_DAYS) ?: DEFAULT_DAYS,
            empty = t.text("empty", MAX_LABEL),
        )
    }

    /**
     * A ring or one bar (`value` out of `max`), or up to [MAX_BARS] bars (`items`). One of the two
     * is needed. A value is held between 0 and its max.
     */
    private fun progress(t: Table, base: WidgetBase): WidgetSpec.Progress {
        val max = t.above0("max")
        val bars = t.items("items", MAX_BARS, "{ ${BAR.joinToString()} }", BAR).map { b ->
            Triple(b, b.number("value", "a number") ?: b.fail("value is needed: how far along this bar is"), b.above0("max"))
        }
        if (!t.has("value") && bars.isEmpty()) t.fail("value is needed: how far along it is, from 0 to max. Or give items, for bars")
        // Bars are on one scale: the widget's max, or else the largest value among them.
        val scale = max ?: bars.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
        val top = max ?: 1.0
        return WidgetSpec.Progress(
            base,
            value = float((t.number("value", "a number") ?: 0.0).coerceIn(0.0, top)),
            max = float(top),
            text = t.text("text", MAX_SHORT),
            label = t.text("label", MAX_LABEL),
            style = t.choice("style", listOf("ring", "bar")),
            items = bars.map { (b, value, own) ->
                val barTop = own ?: scale
                WidgetSpec.Bar(b.text("label", MAX_BAR_LABEL), float(value.coerceIn(0.0, barTop)), float(barTop), b.text("text", MAX_SHORT), b.colour())
            },
            moving = t.flag("moving"),
        )
    }

    /** Numbers over time. The last [MAX_POINTS] points are kept: the newest. */
    private fun chart(t: Table, base: WidgetBase): WidgetSpec.Chart {
        val all = t.list("points", "numbers") ?: t.fail("points is needed: a list of numbers, the oldest first")
        val points = all.takeLast(MAX_POINTS).map { p ->
            (p as? Number)?.toDouble()?.takeIf { it.isFinite() }?.let(::float) ?: t.fail("points must be a list of numbers; one of them is ${shown(p)}")
        }
        val min = t.number("min", "a number")?.let(::float)
        val max = t.number("max", "a number")?.let(::float)
        if (min != null && max != null && min >= max) t.fail("min must be below max")
        return WidgetSpec.Chart(
            base,
            points = points,
            style = t.choice("style", listOf("line", "bars")) ?: "line",
            min = min,
            max = max,
            value = t.text("value", MAX_VALUE),
            label = t.text("label", MAX_LABEL),
            labels = t.texts("labels", 2, MAX_POINT_LABEL),
            summary = t.text("summary", MAX_SUMMARY),
        )
    }

    /** The time to a moment. The script gives the moment once, and Kolahal counts. */
    private fun countdown(t: Table, base: WidgetBase): WidgetSpec.Countdown {
        val to = t.time("to") ?: t.fail("to is needed: the moment, as a time in seconds (os.time() in Lua, Date.now() / 1000 in JavaScript)")
        val from = t.time("from")
        if (from != null && from >= to) t.fail("from must be before to")
        return WidgetSpec.Countdown(
            base,
            to = to,
            from = from,
            label = t.text("label", MAX_LABEL),
            style = t.choice("style", listOf("days", "clock")),
            reached = t.text("reached", MAX_LABEL),
        )
    }

    /**
     * One table of a description, with its fields read by type.
     * - [at] says where it is, in front of an error: nothing for the description itself,
     *   "item 2 of items: " for a row.
     * - [known] are its field names. Any other name is an error.
     */
    private class Table(
        raw: Map<*, *>,
        private val at: String,
        noField: String,
        known: List<String>,
        private val clean: (String) -> String,
        private val link: (String) -> String,
    ) {
        private val values: Map<String, Any?> = raw.entries.associate { (k, v) -> k.toString() to v }

        init {
            // A name with no value is still checked: `colour = nil` in JavaScript's form is a typo too.
            val unknown = values.keys.filter { it !in known }
            if (unknown.isNotEmpty()) fail("$noField ${unknown.joinToString { "'${it.take(MAX_SHORT)}'" }}; use ${known.joinToString()}")
        }

        fun fail(message: String): Nothing = throw IllegalArgumentException("widget: $at$message")

        fun has(key: String) = values[key] != null

        /**
         * A text field, or null when it is left out or empty. A number becomes text. Styles go
         * first, then secrets ([clean]), and the cut comes last, so a cut never splits either.
         * With [lines] the text keeps its line breaks; without, they become spaces.
         */
        fun text(key: String, max: Int, lines: Boolean = false): String? = textOrEmpty(key, max, lines)?.takeIf { it.isNotEmpty() }

        /** A text field that must be there. [what] says what it is for, in the error. It may be empty. */
        fun need(key: String, max: Int, what: String, lines: Boolean = false): String =
            textOrEmpty(key, max, lines) ?: fail("$key is needed: $what")

        private fun textOrEmpty(key: String, max: Int, lines: Boolean = false): String? = values[key]?.let { textOf(key, it, max, lines) }

        /** [v] as the text of [key]. [key] is named in the error. */
        private fun textOf(key: String, v: Any?, max: Int, lines: Boolean = false): String {
            val given = when (v) {
                is String -> v
                is Number -> numberText(v)
                else -> fail("$key must be text, not ${shown(v)}")
            }
            val cleaned = clean(StyledText.plain(given)).let { if (lines) it.replace("\r\n", "\n").trim() else it.replace(BREAKS, " ").trim() }
            return cut(cleaned, max)
        }

        /** A list of plain values, whole. Null when it is left out. [what] names its items, in the error. */
        fun list(key: String, what: String): List<Any?>? {
            val v = values[key] ?: return null
            return Json.listOrNull(v) ?: fail("$key must be a list of $what, not ${shown(v)}")
        }

        /** A list of texts: its first [keep], each cut at [max]. An empty text stays, so the others keep their places. */
        fun texts(key: String, keep: Int, max: Int): List<String> = list(key, "texts").orEmpty().take(keep).map { textOf(key, it, max) }

        fun number(key: String, what: String): Double? {
            val v = values[key] ?: return null
            return (v as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: fail("$key must be $what, not ${shown(v)}")
        }

        /** A number that must be above 0: the top of a scale. */
        fun above0(key: String): Double? = number(key, "a number above 0")?.also { if (it <= 0) fail("$key must be a number above 0, not ${shown(it)}") }

        /**
         * A moment, in seconds since 1970. A fraction is dropped: JavaScript's `Date.now() / 1000` has one.
         * A number as large as milliseconds is an error that says to divide it.
         */
        fun time(key: String): Long? {
            val s = number(key, "a time in seconds") ?: return null
            if (s > MAX_TIME || s < -MAX_TIME) fail("$key must be a time in seconds, not in milliseconds: divide it by 1000")
            return kotlin.math.floor(s).toLong()
        }

        /** A number of seconds, held between [min] and [max]. Zero or less counts as left out. */
        fun seconds(key: String, min: Int, max: Int): Int? {
            val s = number(key, "a number of seconds") ?: return null
            return if (s <= 0) null else s.coerceIn(min.toDouble(), max.toDouble()).toInt()
        }

        fun flag(key: String): Boolean {
            val v = values[key] ?: return false
            return v as? Boolean ?: fail("$key must be true or false, not ${shown(v)}")
        }

        /** A true-or-false field that has no value when it is left out: a row's `done`, a switch's `on`. */
        fun flagOrNull(key: String): Boolean? {
            val v = values[key] ?: return null
            return v as? Boolean ?: fail("$key must be true or false, not ${shown(v)}")
        }

        /**
         * A time zone by its name, such as `Asia/Kolkata` or `UTC`. It is needed, and it is never
         * cut: a name `java.time.ZoneId.of` does not know is an error. Styles go first and then
         * secrets ([clean]), as for any text.
         */
        fun zone(key: String): String {
            val v = values[key] ?: fail("$key is needed: a time zone name, such as Asia/Kolkata")
            val name = (v as? String)?.let { clean(StyledText.plain(it)).trim() } ?: fail("$key must be a time zone name, such as Asia/Kolkata, not ${shown(v)}")
            runCatching { java.time.ZoneId.of(name) }.getOrNull() ?: fail("no time zone ${shown(name)}; use a name such as Asia/Kolkata or UTC")
            return name
        }

        /** A field that takes one of a few words. */
        fun choice(key: String, words: List<String>): String? {
            val v = values[key] ?: return null
            return (v as? String)?.trim()?.lowercase()?.takeIf { it in words }
                ?: fail("$key must be ${words.dropLast(1).joinToString()} or ${words.last()}, not ${shown(v)}")
        }

        /** `color`: a colour by its role. A script never gives a colour value. */
        fun colour(): StyledText.Colour? {
            val v = values["color"] ?: return null
            return (v as? String)?.let(StyledText.Colour::named)
                ?: fail("no colour ${shown(v)}; use ${StyledText.Colour.entries.joinToString { it.role }}")
        }

        /**
         * A list of tables: its first [keep] items, each with the fields [known]. [shape] shows one
         * item, in an error. An empty Lua table is an empty list: it has no keys to tell.
         */
        fun items(key: String, keep: Int, shape: String, known: List<String>): List<Table> {
            val v = values[key] ?: return emptyList()
            val all = Json.listOrNull(v) ?: fail("$key must be a list of $shape, not ${shown(v)}")
            // Only the items that are kept are checked.
            return all.take(keep).mapIndexed { i, item ->
                val where = "${at}item ${i + 1} of $key: "
                val map = item as? Map<*, *> ?: throw IllegalArgumentException("widget: ${where}must be $shape, not ${shown(item)}")
                Table(map, where, "no field", known, clean, link)
            }
        }

        /**
         * A picture's link. It is needed.
         * - It must pass [link], as a tap's link does.
         * - It must start with `https://`. Home fetches a picture without a tap, so never over plain http.
         * - It is never cut. One over [WidgetJson.MAX_PICTURE_LINK] characters is an error.
         * An error never quotes the link: it could hold a secret.
         */
        fun picture(key: String): String {
            val what = "a link to a picture that starts with https://"
            val v = values[key] ?: fail("$key is needed: $what")
            val given = (v as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: fail("$key must be $what, not ${if (v is String) "an empty text" else shown(v)}")
            val checked = link(given)
            if (!WidgetJson.isHttps(checked)) fail("$key must start with https://: a widget's picture is not fetched over plain http")
            if (checked.length > WidgetJson.MAX_PICTURE_LINK) fail("$key is over ${WidgetJson.MAX_PICTURE_LINK} characters")
            return checked
        }

        /**
         * `tap`: what a tap does. Exactly one of:
         * - `args`: the script runs again with these arguments. They may be empty. With `page = "log"`,
         *   home turns to the log after the tap.
         * - `url`: a web link opens. It must pass [link].
         * `label` is what a screen reader says for it.
         */
        fun tap(): WidgetTap? {
            val v = values["tap"] ?: return null
            val map = v as? Map<*, *> ?: fail("tap must be { args } or { url }, not ${shown(v)}")
            val t = Table(map, "${at}tap: ", "no field", TAP, clean, link)
            val label = t.text("label", MAX_TAP_LABEL)
            val toLog = t.values["page"]?.let { page -> if (page == "log") true else t.fail("page must be 'log', not ${shown(page)}") } ?: false
            return when {
                t.has("args") && t.has("url") -> t.fail("give args or url, not both")
                t.has("args") -> WidgetTap(args = t.need("args", MAX_ARGS, "the arguments"), label = label, toLog = toLog)
                t.has("url") -> {
                    if (toLog) t.fail("page goes with args, not with url")
                    val given = t.values["url"]
                    WidgetTap(url = link((given as? String ?: t.fail("url must be a web link, not ${shown(given)}")).trim()), label = label)
                }
                else -> t.fail("give args or url")
            }
        }
    }

    private val BREAKS = Regex("""\s*[\r\n]+\s*""")

    /** [s], or its start with "…" when it is longer than [max]. It never ends in half an emoji. */
    private fun cut(s: String, max: Int): String = if (s.length <= max) s else s.takeWhole(max - 1) + "…"

    /** [d] as the cards hold numbers. One too large for that is held at the largest there is. */
    private fun float(d: Double): Float = d.coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()

    /** A number as a script shows it: a whole one without ".0". */
    private fun numberText(n: Number): String = if (n is Double || n is Float) Json.numberText(n.toDouble()) else n.toString()

    /** [v] as an error names it. A text is quoted, and cut short. */
    private fun shown(v: Any?): String = when (v) {
        null -> "nothing"
        is String -> "'${v.take(MAX_SHORT)}'"
        is Number -> numberText(v)
        is Boolean -> v.toString()
        is List<*> -> "a list"
        is Map<*, *> -> "a table"
        else -> "that"
    }
}
