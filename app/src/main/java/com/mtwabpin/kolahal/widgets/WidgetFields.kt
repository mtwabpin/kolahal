/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * One field of a widget's description, as a script writes it.
 * - [about] is what the editor's reference says of it after its name, or nothing.
 * - [choices] are the words it takes, for a field that takes one of a few ([WidgetParser.choicesOf]).
 * - [inner] are the fields of the table it holds: of each item when [many] (`items`, `sides`), else
 *   of the one table (`lead`, `range`, `tap`).
 */
class WidgetField(
    val name: String,
    val about: String = "",
    val choices: List<String> = emptyList(),
    val inner: List<WidgetField> = emptyList(),
    val many: Boolean = true,
) {
    /** The field as the editor's reference names it: "trend (up, down or flat)", "items (each { label, tap })". */
    val described: String
        get() {
            val parts = listOfNotNull(
                choices.takeIf { it.isNotEmpty() }?.let(::wordsOf),
                inner.takeIf { it.isNotEmpty() }
                    ?.let { (if (many) "each " else "") + "{ " + it.joinToString { f -> f.described } + " }" },
                about.ifEmpty { null },
            )
            return if (parts.isEmpty()) name else "$name (${parts.joinToString("; ")})"
        }
}

/** "a, b or c". */
internal fun wordsOf(words: List<String>): String =
    if (words.size < 2) words.joinToString() else words.dropLast(1).joinToString() + " or " + words.last()

/**
 * The fields of each kind of widget, declared once. The parser takes these and no others: its
 * "has no field 'x'; use …" lists them ([WidgetParser]). The editor's reference writes them out
 * ([com.mtwabpin.kolahal.script.ApiDocs]). The tests compare the stored form ([WidgetJson]), the
 * reference's examples and the guides for an assistant with them. A field a kind gains is one entry
 * here, the line that reads it, and its drawing.
 */
object WidgetFields {
    private fun f(
        name: String,
        about: String = "",
        choices: List<String> = emptyList(),
        inner: List<WidgetField> = emptyList(),
        many: Boolean = true,
    ) = WidgetField(name, about, choices, inner, many)

    private fun names(vararg names: String) = names.map { WidgetField(it) }

    /** The fields of a `tap`, which every kind and some items take. */
    val TAP: List<WidgetField> = names("args", "url", "label", "page")

    /** The fields every kind takes. `kind` is first: the reference says it apart from the others. */
    val COMMON: List<WidgetField> = listOf(
        f("kind"),
        f("title"),
        f("icon"),
        f("color"),
        f("shape", "the Shape look's", choices = WidgetShape.ids),
        f(
            "tap",
            "{ args } runs this script again, { url } opens a link; label is what a screen reader says, " +
                    "page = \"log\" turns home to the log after the run",
            inner = TAP, many = false
        ),
        f("staleAfter", "seconds"),
        f("refresh", "seconds"),
        f("note", "a few words on a large card's last line"),
    )

    /** A stat's smaller values (`more`). */
    val SMALL: List<WidgetField> = names("value", "label")

    val STAT: List<WidgetField> = listOf(
        f("value"), f("label"), f("unit"), f("delta"),
        f("trend", choices = listOf("up", "down", "flat")),
        f("more", "up to ${WidgetParser.MAX_MORE}", inner = SMALL),
        f("progress"), f("moving"),
        f("points", "numbers, a line behind the value"),
        f("scene", "a shader for the ground, such as pond"),
    )

    val TEXT: List<WidgetField> = names("text", "caption")

    /** A row's range: a bar from its low to its high. */
    val RANGE: List<WidgetField> = names("low", "high", "value")

    /** A list's row. */
    val ROW: List<WidgetField> = listOf(
        f("title"), f("detail"), f("trailing"), f("icon"), f("color"), f("tap"), f("done"),
        f("range", "drawn as a bar", inner = RANGE, many = false),
    )

    /** The big line over a list's rows. */
    val LEAD: List<WidgetField> = names("value", "unit", "label")

    val LIST: List<WidgetField> = listOf(
        f("items", inner = ROW),
        f("empty"),
        f("lead", "a big line over the rows", inner = LEAD, many = false),
    )

    /** A progress widget's bar or ring. */
    val BAR: List<WidgetField> = names("label", "value", "max", "text", "color", "unit", "moving")

    /** A gauge's zone. */
    val GAUGE_ZONE: List<WidgetField> = names("from", "color")

    val PROGRESS: List<WidgetField> = listOf(
        f("value"), f("max"), f("text"), f("label"),
        f("style", choices = listOf("ring", "bar", "gauge", "rings")),
        f("items", "bars or rings, up to ${WidgetParser.MAX_BARS}; unit and moving only with rings", inner = BAR),
        f("moving"),
        f("min", "only a gauge's"), f("unit", "only a gauge's"),
        f("zones", "only a gauge's", inner = GAUGE_ZONE),
        f("stages", "2 to ${WidgetParser.MAX_STAGES} names, in place of items"),
        f("stage", "the one it is at, from 1"),
    )

    val CHART: List<WidgetField> = listOf(
        f("points"), f("style", choices = listOf("line", "bars", "dots")), f("min"), f("max"),
        f("value"), f("label"), f("labels"), f("summary"),
    )

    val COUNTDOWN: List<WidgetField> = listOf(
        f("to", "a time in seconds"), f("from", "a time in seconds"), f("label"),
        f("style", "dots: a dot a day, page: a calendar page", choices = listOf("days", "clock", "dots", "page")),
        f("reached"),
    )

    val BUTTON: List<WidgetField> = names("label", "icon", "color", "tap")

    val BUTTONS: List<WidgetField> = listOf(f("items", "1 to ${WidgetParser.MAX_BUTTONS}", inner = BUTTON))

    val TOGGLE: List<WidgetField> = names("label", "icon", "on", "tap")

    val TOGGLES: List<WidgetField> = listOf(
        f("items", "1 to ${WidgetParser.MAX_TOGGLES}; the tap is { args }, and the run shows the new state", inner = TOGGLE)
    )

    val ZONE: List<WidgetField> = listOf(f("zone", "a name such as Europe/Lisbon"), f("label"))

    val CLOCK: List<WidgetField> = listOf(f("zones", inner = ZONE), f("seconds"))

    /**
     * An agenda's event. It may carry `id`, which is taken and not kept: `calendar.events()` gives
     * its rows one, and those rows go straight into `events`.
     */
    val EVENT: List<WidgetField> = listOf(
        f("title"), f("starts", "a time in seconds"), f("ends", "a time in seconds"), f("allDay"), f("where"),
        f("color"), f("tap"), f("id", "ignored: calendar.events() rows have it"),
    )

    val AGENDA: List<WidgetField> = listOf(f("events", inner = EVENT), f("days"), f("empty"))

    val IMAGE: List<WidgetField> = listOf(
        f("image", "an https link to a picture"), f("alt", "what it shows, in words"), f("caption"),
        f("fit", choices = listOf("crop", "fit")),
    )

    /** A versus card's side. */
    val SIDE: List<WidgetField> = names("name", "mark", "color", "value", "text")

    val VERSUS: List<WidgetField> = listOf(
        f("sides", "two; the values make a bar between the sides", inner = SIDE),
        f("score"), f("turn", "1 or 2"), f("moving"), f("caption"), f("badge"),
    )

    /** One of the moon's next phases. */
    val PHASE: List<WidgetField> = names("phase", "waxing", "at")

    val SKY: List<WidgetField> = listOf(
        f("rises", "a time in seconds"), f("sets", "a time in seconds"),
        f("phase", "0 to 1: the moon, lit this much"), f("waxing"),
        f("phases", "up to ${WidgetParser.MAX_PHASES}", inner = PHASE),
    )

    /** The fields every [kind] widget takes: [COMMON]. */
    fun commonOf(@Suppress("UNUSED_PARAMETER") kind: WidgetKind): List<WidgetField> = COMMON

    /** The fields of a [kind] widget after the common ones. Only a kind a script can use has them. */
    fun ownOf(kind: WidgetKind): List<WidgetField> = when (kind) {
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
        WidgetKind.VERSUS -> VERSUS
        WidgetKind.SKY -> SKY
        WidgetKind.MEDIA -> throw IllegalArgumentException("widget: a script can't fill a ${kind.id} widget")
    }

    /** The fields of the tables inside [fields], at every level, in order: a list's rows, then a row's range. */
    fun innerOf(fields: List<WidgetField>): List<WidgetField> =
        fields.flatMap { it.inner + innerOf(it.inner) }

    /** The names of [fields]. */
    fun namesOf(fields: List<WidgetField>): List<String> = fields.map { it.name }
}
