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
 * - Bounded. Text is cut with "…" at its limit. A list keeps its first items.
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

    /** A list keeps its first [MAX_ITEMS] items, and a stat its first [MAX_MORE] smaller values. */
    const val MAX_ITEMS = 12
    const val MAX_MORE = 3

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
    private val ROW = listOf("title", "detail", "trailing", "icon", "color", "tap")

    private val TRENDS = listOf("up", "down", "flat")

    /**
     * Every field name a description can have, at any level. No name may be a Lua keyword (a script
     * writes `{ title = … }`), and none may be `status` or `notify`: those words in a script's code
     * switch on access items ([com.mtwabpin.kolahal.script.ScriptMeta.setsStatus]). A test checks this.
     */
    val FIELDS: Set<String> = (COMMON + TAP + STAT + SMALL + TEXT + LIST + ROW).toSet()

    /** The fields of a [kind] widget, after the common ones. Null for a kind this build can't read yet. */
    private fun own(kind: WidgetKind): List<String>? = when (kind) {
        WidgetKind.STAT -> STAT
        WidgetKind.TEXT -> TEXT
        WidgetKind.LIST -> LIST
        else -> null
    }

    /**
     * [raw] as a checked description.
     * - [default] is the kind of the script's `@widget` line. The description's own `kind` wins.
     * - [text] is called for every text before it is cut. It masks secrets and removes styles.
     * - [link] is called for every link. It returns the link as it may be stored, or throws for a
     *   link that is refused.
     */
    fun parse(raw: Map<*, *>, default: WidgetKind, text: (String) -> String, link: (String) -> String): WidgetSpec {
        val kind = raw["kind"]?.let {
            WidgetKind.named(it as? String) ?: throw IllegalArgumentException("widget: no kind ${shown(it)}; use ${WidgetKind.entries.joinToString { k -> k.id }}")
        } ?: default
        val fields = own(kind) ?: throw IllegalArgumentException("widget: the ${kind.id} kind can't be shown yet")
        val t = Table(raw, "", "a ${kind.id} widget has no field", COMMON + fields, text, link)
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
            else -> rows(t, base)
        }
    }

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
                )
            },
            empty = t.text("empty", MAX_LABEL),
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
         * A text field, or null when it is left out or empty. A number becomes text. Secrets and
         * styles go first ([clean]) and the cut comes after, so a cut never splits either.
         * With [lines] the text keeps its line breaks; without, they become spaces.
         */
        fun text(key: String, max: Int, lines: Boolean = false): String? = textOrEmpty(key, max, lines)?.takeIf { it.isNotEmpty() }

        /** A text field that must be there. [what] says what it is for, in the error. It may be empty. */
        fun need(key: String, max: Int, what: String, lines: Boolean = false): String =
            textOrEmpty(key, max, lines) ?: fail("$key is needed: $what")

        private fun textOrEmpty(key: String, max: Int, lines: Boolean = false): String? {
            val v = values[key] ?: return null
            val given = when (v) {
                is String -> v
                is Number -> numberText(v)
                else -> fail("$key must be text, not ${shown(v)}")
            }
            val cleaned = clean(given).let { if (lines) it.replace("\r\n", "\n").trim() else it.replace(BREAKS, " ").trim() }
            return cut(cleaned, max)
        }

        fun number(key: String, what: String): Double? {
            val v = values[key] ?: return null
            return (v as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: fail("$key must be $what, not ${shown(v)}")
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
