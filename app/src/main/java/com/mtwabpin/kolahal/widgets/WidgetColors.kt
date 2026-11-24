/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText.ColorRole

/**
 * A card's color role, the one it uses card-wide: its Bold fill and big value, its ring, bar or
 * gauge without zones, its chart's line, the Shape look's shape, its icon, its Tonal card. The
 * script gives it with `color` ([WidgetBase.color]); without one it is [ColorRole.ACCENT]. The user
 * can give a placed card another one ([PlacedWidget.color]), on its settings page.
 * The colors a script gives a part of a card stay as they are: a gauge's zones, a versus card's
 * sides, a bar's or a ring's own, a row's, an event's, a styled text's.
 */
object WidgetColors {
    /** The roles in the order the settings page offers them, after the card's own. */
    val ORDER: List<ColorRole> = listOf(
        ColorRole.ACCENT,
        ColorRole.LINK,
        ColorRole.INFO,
        ColorRole.GOOD,
        ColorRole.WARN,
        ColorRole.BAD,
        ColorRole.MUTED,
        ColorRole.TEXT,
    )

    /**
     * The roles the settings page offers in the theme in use, whose color for a role is [colorOf]:
     * each of [ORDER] whose color differs from every one before it, so no dot is the twin of
     * another. Info is left out where it is the link's color (Rosé Pine, the default, and Dracula).
     * [chosen] is kept even when it is such a twin, so the page still shows what the card has.
     */
    fun <C> offered(colorOf: (ColorRole) -> C, chosen: ColorRole? = null): List<ColorRole> {
        val seen = HashSet<C>()
        return ORDER.filter { seen.add(colorOf(it)) || it == chosen }
    }

    /** The role's plain name, for a screen reader and the guide. */
    fun label(role: ColorRole): String = when (role) {
        ColorRole.ACCENT -> "Accent"
        ColorRole.LINK -> "Link"
        ColorRole.INFO -> "Info"
        ColorRole.MUTED -> "Muted"
        ColorRole.TEXT -> "Text"
        ColorRole.GOOD -> "Good"
        ColorRole.WARN -> "Warning"
        ColorRole.BAD -> "Bad"
    }

    /** Whether the placed widget with [source] has a settings page with a color: every card Kolahal draws. An app's widget draws itself. */
    fun offeredFor(source: String?): Boolean = source != null && WidgetSource.isSource(source)

    /**
     * The role a card draws with, from its [own] (the script's `color`, null for the accent) and the
     * one [chosen] for it on its settings page.
     * - Nothing chosen: its own. A script that sets its color by what it shows (air turning bad)
     *   keeps doing so.
     * - Something chosen: that one wins, also over a script's color by state, because the user
     *   chose it.
     * - [keepsState]: a card whose own color is a warning Kolahal gives (the Battery and network
     *   card: bad when low, good on power, muted when the level is unknown) keeps that color while
     *   it has one, and takes the chosen one otherwise.
     */
    fun drawn(own: ColorRole?, chosen: ColorRole?, keepsState: Boolean = false): ColorRole? =
        if (chosen == null || (keepsState && own != null)) own else chosen

    /** [spec] with its card-wide color [color]. Every other part stays as it is. */
    fun recolored(spec: WidgetSpec, color: ColorRole?): WidgetSpec =
        if (spec.base.color == color) spec else WidgetSpecs.withBase(spec, spec.base.copy(color = color))
}
