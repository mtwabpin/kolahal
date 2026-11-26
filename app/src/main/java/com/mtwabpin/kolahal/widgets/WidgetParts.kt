/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * The parts of a card a person can hide or show on its settings page: What shows. Each placed card
 * keeps the ids of the ones it hides ([PlacedWidget.hidden]) and of the ones off by default that it
 * shows ([PlacedWidget.shown]); none of either is the card as its script and its look draw it.
 * - [NAME]: the card's name, wherever its look writes it small.
 * - [ICON]: the card's icon ([WidgetBase.icon]), where its look draws one.
 * - [UPDATED]: off by default ([OFF]). When the card's content was last set, always, where the card
 *   writes the age of old content: "Updated 3 min ago" ([com.mtwabpin.kolahal.ui.widgets.UpdatedLine]).
 * A part is an id, a short lowercase word, so parts can join later (one a script names) without
 * changing how they are kept: an id this version doesn't know is kept, and hides nothing.
 */
object WidgetParts {
    const val NAME = "name"
    const val ICON = "icon"
    const val UPDATED = "updated"

    /** The parts this version knows, in the page's order. */
    val KNOWN: List<String> = listOf(NAME, ICON, UPDATED)

    /** The parts that are off until turned on: a card keeps them in [PlacedWidget.shown], not [PlacedWidget.hidden]. */
    val OFF: Set<String> = setOf(UPDATED)

    /** Whether the card [w] shows [part]: one that is [OFF] once it is turned on, any other until it is hidden. */
    fun shows(w: PlacedWidget, part: String): Boolean =
        if (part in OFF) part in w.shown else part !in w.hidden

    /** What an id may be: a short lowercase word, so it fits the stored line and a backup. */
    private val ID = Regex("[a-z][a-z0-9-]{0,23}")

    /** The part's name on the settings page. */
    fun label(id: String): String = when (id) {
        NAME -> "Name"
        ICON -> "Icon"
        UPDATED -> "Last updated"
        else -> id
    }

    /** What the part's switch says under its name. */
    fun detail(id: String): String = when (id) {
        NAME -> "The card's name, where its look writes it"
        ICON -> "The card's icon"
        UPDATED -> "When the card's content was last refreshed"
        else -> ""
    }

    /** The kinds and looks that draw a card's icon. */
    private val ICON_LOOKS = mapOf(
        WidgetKind.STAT to setOf(WidgetLook.BOLD, WidgetLook.SPLIT, WidgetLook.SHAPE),
        WidgetKind.COUNTDOWN to setOf(WidgetLook.SPLIT),
        WidgetKind.TEXT to setOf(WidgetLook.SHAPE),
        WidgetKind.IMAGE to setOf(WidgetLook.SHAPE),
    )

    /**
     * The parts a card of [kind], showing [spec] (null when not known yet), drawn in [look], has to
     * hide, in the page's order.
     * - [NAME]: every look but one of a built-in card's own (Next, Stack, Now), which writes no name.
     * - [ICON]: only when the card has an icon and its look draws it: a stat in Bold, Split and
     *   Shape, a countdown in Split, a text's and a picture's mark in Shape.
     * - [UPDATED]: only on a [script]'s card, whose content comes from a run and so has a time.
     *   Kolahal's own cards read what they show live.
     */
    fun offered(kind: WidgetKind?, spec: WidgetSpec?, look: WidgetLook, script: Boolean = false): List<String> {
        val k = spec?.let(WidgetJson::kindOf) ?: kind
        val name = look !in WidgetLook.ownOnly && look != WidgetLook.NOW
        val icon = spec?.base?.icon != null && look in ICON_LOOKS[k].orEmpty()
        return listOfNotNull(NAME.takeIf { name }, ICON.takeIf { icon }, UPDATED.takeIf { script })
    }

    /** [spec] without the parts [hidden] that its description holds: the icon. Every other field stays. */
    fun applied(spec: WidgetSpec, hidden: Set<String>): WidgetSpec =
        if (ICON in hidden && spec.base.icon != null) WidgetSpecs.withBase(spec, spec.base.copy(icon = null))
        else spec

    /** The hidden or shown parts as they are stored: their ids, in order, between commas. Empty for none. */
    fun write(hidden: Set<String>): String = hidden.sorted().joinToString(",")

    /**
     * The hidden or shown parts read back from [raw]: a stored line's field ("icon,name") or a backup's list.
     * Anything that is no id is left out.
     */
    fun read(raw: Any?): Set<String> {
        val words = when (raw) {
            is String -> raw.split(',')
            is List<*> -> raw.mapNotNull { it as? String }
            else -> emptyList()
        }
        return words.map { it.trim() }.filter { ID.matches(it) }.toSet()
    }
}

/** Helpers on a description as a whole. */
object WidgetSpecs {
    /** [spec] with the fields every kind has given as [base]. Every other field stays. */
    fun withBase(spec: WidgetSpec, base: WidgetBase): WidgetSpec = when (spec) {
        is WidgetSpec.Stat -> spec.copy(base = base)
        is WidgetSpec.Text -> spec.copy(base = base)
        is WidgetSpec.Rows -> spec.copy(base = base)
        is WidgetSpec.Progress -> spec.copy(base = base)
        is WidgetSpec.Chart -> spec.copy(base = base)
        is WidgetSpec.Countdown -> spec.copy(base = base)
        is WidgetSpec.Buttons -> spec.copy(base = base)
        is WidgetSpec.Toggles -> spec.copy(base = base)
        is WidgetSpec.Clock -> spec.copy(base = base)
        is WidgetSpec.Agenda -> spec.copy(base = base)
        is WidgetSpec.Image -> spec.copy(base = base)
        is WidgetSpec.Versus -> spec.copy(base = base)
        is WidgetSpec.Sky -> spec.copy(base = base)
        is WidgetSpec.Media -> spec.copy(base = base)
    }
}
