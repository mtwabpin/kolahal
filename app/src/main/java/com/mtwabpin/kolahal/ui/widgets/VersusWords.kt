/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * The words and the numbers of a `versus` card ([WidgetSpec.Versus]): each side's mark and color,
 * the bar between the sides, and what a screen reader says. Pure, so it is unit tested.
 */
internal object VersusWords {
    /** A side's mark: its own, or the first letter or digit of its name, as a capital. "" for a name with none. */
    fun mark(side: WidgetSpec.Side): String = side.mark ?: WidgetWords.badgeLetter(side.name).orEmpty()

    /** A side's color: its own, or the accent for the first side and the link color for the second, as the mockup has them. */
    fun role(side: WidgetSpec.Side, index: Int): StyledText.ColorRole =
        side.color ?: if (index == 0) StyledText.ColorRole.ACCENT else StyledText.ColorRole.LINK

    /**
     * The two parts of the bar between the sides, each from 0 to 1, together 1: each side's value
     * over the two. Null without a value on both sides, or when both are 0: there is no bar then.
     */
    fun shares(spec: WidgetSpec.Versus): Pair<Float, Float>? {
        val a = spec.sides.getOrNull(0)?.value ?: return null
        val b = spec.sides.getOrNull(1)?.value ?: return null
        val sum = a.toDouble() + b.toDouble()
        if (!(sum > 0) || sum.isInfinite()) return null
        val first = (a / sum).toFloat().coerceIn(0f, 1f)
        return first to 1f - first
    }

    /** What the bar's end says for a side: its `text`, or its value as a number ("58"). Null without either. */
    fun figure(side: WidgetSpec.Side): String? = side.text ?: side.value?.let(WidgetWords::num)

    /** The side whose turn it is (`turn` 1 or 2), by its place in the list: 0 or 1. Null for none. */
    fun turnIndex(spec: WidgetSpec.Versus): Int? = spec.turn?.let { it - 1 }?.takeIf { it in spec.sides.indices }

    /**
     * What a screen reader says for [spec], in parts: the badge ("LIVE"), the sides with the score
     * between them ("Lisbon 2–1 Paris"), each side's figure where there is a bar ("Lisbon 58"),
     * whose turn it is ("Paris's turn"), and the last line.
     */
    fun said(spec: WidgetSpec.Versus): List<String> {
        val a = spec.sides.getOrNull(0)
        val b = spec.sides.getOrNull(1)
        return buildList {
            spec.badge?.let(::add)
            add(listOfNotNull(a?.name, spec.score, b?.name).joinToString(" "))
            if (shares(spec) != null) spec.sides.forEach { side -> figure(side)?.let { add("${side.name} $it") } }
            turnIndex(spec)?.let { add("${spec.sides[it].name}'s turn") }
            spec.line?.let(::add)
        }
    }
}
