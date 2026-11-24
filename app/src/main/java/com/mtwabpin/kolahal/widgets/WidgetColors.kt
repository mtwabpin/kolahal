/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText.ColorRole

/**
 * A card's color role, the one it uses card-wide: its Bold fill and big value, its ring, bar or
 * gauge without zones, its chart's line, the Shape look's shape, its icon, its Tonal card. The
 * script gives it with `color` ([WidgetBase.color]); without one it is [ColorRole.ACCENT]. The user
 * can give a placed card another one ([PlacedWidget.color]), in the Color row of Arrange widgets.
 * The colors a script gives a part of a card stay as they are: a gauge's zones, a versus card's
 * sides, a bar's or a ring's own, a row's, an event's, a styled text's.
 */
object WidgetColors {
    /**
     * The roles the Color row offers, in its order. `info` is left out: in Rosé Pine (the default)
     * and Dracula it is the link's color, and in the other themes it is close to the accent, the
     * link or muted. A card whose script gives `info` still has it as its own.
     */
    val OFFERED: List<ColorRole> = listOf(
        ColorRole.ACCENT,
        ColorRole.LINK,
        ColorRole.GOOD,
        ColorRole.WARN,
        ColorRole.BAD,
        ColorRole.MUTED,
        ColorRole.TEXT,
    )

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

    /** Whether the placed widget with [source] has a Color row: every card Kolahal draws. An app's widget draws itself. */
    fun offeredFor(source: String?): Boolean = source != null && WidgetSource.isSource(source)

    /**
     * The role a card draws with, from its [own] (the script's `color`, null for the accent) and the
     * one [chosen] for it in Arrange widgets.
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
    fun recolored(spec: WidgetSpec, color: ColorRole?): WidgetSpec {
        if (spec.base.color == color) return spec
        val base = spec.base.copy(color = color)
        return when (spec) {
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

    /**
     * The roles offered for a card whose own role is [own] (null: the accent): its own first, so one
     * tap gives it back, then the others of [OFFERED] in their order.
     */
    fun choices(own: ColorRole?): List<ColorRole> {
        val first = own ?: ColorRole.ACCENT
        return listOf(first) + (OFFERED - first)
    }
}
