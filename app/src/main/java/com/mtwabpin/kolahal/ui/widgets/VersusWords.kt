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
    /** From this width a wide versus card has its names beside the marks and the bar under them: a 4×1 card. */
    const val ROW_FROM_DP = 260

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

    /**
     * What a one-row card (4×1) holds, [heightDp] high: [score] is the place in [scoreDp] (the
     * heights of the score's sizes, the largest first) of the size it takes, [line] whether the
     * last line and the badge fit under it, [age] whether the card's "Updated 3 min ago" (and the
     * script's footer) does, and [detail] whether each side's detail fits under its name.
     */
    data class RowPlan(val score: Int, val line: Boolean, val age: Boolean = false, val detail: Boolean = false)

    /**
     * The plan of a one-row card [heightDp] high ([RowPlan]). The bar and its figures ([barDp], 0
     * without a bar) always have their room. Over it, the largest score that fits with the last line
     * ([lineDp], 0 without one); else the largest that fits alone, and the line gives way; else the
     * smallest.
     * - The card's age ([ageDp], 0 without it) joins the last line, so it shows with it. A card with no
     *   last line has the age alone under the score only when the score keeps the size it has
     *   without it: the age never makes the score smaller.
     * - A side's name with its detail under it ([sideDp], 0 without a detail) is beside the score,
     *   over the bar. The detail shows where that fits, and it gives way before the last line and
     *   the badge do: it never shows where they don't.
     */
    fun rowPlan(heightDp: Int, scoreDp: List<Int>, lineDp: Int, barDp: Int, ageDp: Int = 0, sideDp: Int = 0): RowPlan {
        if (scoreDp.isEmpty()) return RowPlan(0, false)
        val room = heightDp - barDp
        val plan = run {
            if (lineDp > 0) scoreDp.indexOfFirst { it + maxOf(lineDp, ageDp) <= room }.takeIf { it >= 0 }
                ?.let { return@run RowPlan(it, true, age = ageDp > 0) }
            val alone = scoreDp.indexOfFirst { it <= room }
            val score = if (alone >= 0) alone else scoreDp.lastIndex
            val age = ageDp > 0 && lineDp == 0 && alone >= 0 && scoreDp[alone] + ageDp <= room
            RowPlan(score, line = false, age = age)
        }
        val detail = sideDp > 0 && sideDp <= room && (plan.line || lineDp == 0)
        return plan.copy(detail = detail)
    }

    /**
     * Whether a side's detail ([WidgetSpec.Side.detail], [detailDp] high) fits under the rest of
     * its column ([usedDp]: its mark, its name, its figure) in [roomDp]: a large card's faces, a
     * Split tile. Where it doesn't, it gives way, and the name keeps its room.
     */
    fun detailFits(roomDp: Int, usedDp: Int, detailDp: Int): Boolean = usedDp + detailDp <= roomDp

    /**
     * Whether a side's detail [detailDp] wide shows whole beside a middle [middleDp] wide, on a
     * one-row card [widthDp] wide whose side has [fixedDp] for its mark and the room around it. A
     * detail cut to a few letters says nothing: it gives way. Pure.
     */
    fun detailWhole(widthDp: Float, middleDp: Float, fixedDp: Float, detailDp: Float): Boolean =
        (widthDp - middleDp) / 2 - fixedDp >= detailDp

    /**
     * The width of the middle of a one-row card [widthDp] wide, between two sides that each need
     * [sideDp] for a mark and a whole name. [wantDp] is what the middle wants: the score, and the
     * last line under it when that shows.
     * - All of it when everything fits, and the sides share what is left.
     * - Else what the sides leave, so the score gets smaller, but never less than [leastDp]: a name
     *   is cut only then. Never more than it wants.
     */
    fun middleDp(widthDp: Float, wantDp: Float, sideDp: Float, leastDp: Float): Float {
        if (wantDp + 2 * sideDp <= widthDp) return wantDp
        return maxOf(leastDp, widthDp - 2 * sideDp).coerceAtMost(wantDp).coerceIn(0f, widthDp)
    }

    /** The side whose turn it is (`turn` 1 or 2), by its place in the list: 0 or 1. Null for none. */
    fun turnIndex(spec: WidgetSpec.Versus): Int? = spec.turn?.let { it - 1 }?.takeIf { it in spec.sides.indices }

    /**
     * What a screen reader says for [spec], in parts: the badge ("LIVE"), the sides with the score
     * between them ("Lisbon 2–1 Paris"), each side's figure where there is a bar ("Lisbon 58"),
     * whose turn it is ("Paris's turn"), each side's detail ("Paris 7th · 33 pts"), and the last line.
     */
    fun said(spec: WidgetSpec.Versus): List<String> {
        val a = spec.sides.getOrNull(0)
        val b = spec.sides.getOrNull(1)
        return buildList {
            spec.badge?.let(::add)
            add(listOfNotNull(a?.name, spec.score, b?.name).joinToString(" "))
            if (shares(spec) != null) spec.sides.forEach { side -> figure(side)?.let { add("${side.name} $it") } }
            turnIndex(spec)?.let { add("${spec.sides[it].name}'s turn") }
            // Each side's small line after its name: "North 3rd · 41 pts".
            spec.sides.forEach { side -> side.detail?.let { add("${side.name} $it") } }
            spec.line?.let(::add)
        }
    }
}
