/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * The words and the numbers of a `versus` card ([WidgetSpec.Versus]): each side's mark, color and
 * figures, the score split between the sides, a clock's text, what gives way where room runs out,
 * and what a screen reader says. Pure, so it is unit tested.
 */
internal object VersusWords {
    /** From this width a versus card has the room of a 4×1 or a 4×2: a status column, a figure column. */
    const val ROW_FROM_DP = 260

    /** A side's mark: its own, or the first letter or digit of its name, as a capital. "" for a name with none. */
    fun mark(side: WidgetSpec.Side): String = side.mark ?: WidgetWords.badgeLetter(side.name).orEmpty()

    /** A side's color: its own, or the accent for the first side and the link color for the second, as the mockup has them. */
    fun role(side: WidgetSpec.Side, index: Int): StyledText.ColorRole =
        side.color ?: if (index == 0) StyledText.ColorRole.ACCENT else StyledText.ColorRole.LINK

    /**
     * The two parts of the thin bar under the sides, each from 0 to 1, together 1: each side's value
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

    /** The side whose turn it is (`turn` 1 or 2), by its place in the list: 0 or 1. Null for none. */
    fun turnIndex(spec: WidgetSpec.Versus): Int? = spec.turn?.let { it - 1 }?.takeIf { it in spec.sides.indices }

    // ---- the score and the clocks ---------------------------------------------------------------

    /**
     * Where a score splits: an en or em dash or " · " with or without spaces around it, a hyphen
     * with spaces or between digits ("2-1", not "Rain-delayed"), a colon between numbers ("2:1",
     * not "Abandoned: rain").
     */
    private val SPLIT = Regex("""\s*[–—·]\s*|\s+-\s+|(?<=\d)-(?=\d)|(?<=[\d½¼¾])\s*:\s*(?=[\d½¼¾])""")

    /**
     * The card's [score] split between the two sides: "2–1" is 2 and 1, "187/4 · 201/6" is 187/4
     * and 201/6. Null for a score that is not exactly two parts ("W/O", "Abandoned", "2–1 (4–3)").
     */
    fun split(score: String?): Pair<String, String>? {
        val parts = score?.trim()?.split(SPLIT) ?: return null
        if (parts.size != 2) return null
        val (a, b) = parts.map { it.trim() }
        return if (a.isEmpty() || b.isEmpty()) null else a to b
    }

    /**
     * The card's score drawn whole, apart from the rows: one that doesn't split ([split]), where a
     * side has no score of its own to show in its place. Null when the sides show their scores.
     */
    fun whole(spec: WidgetSpec.Versus): String? =
        spec.score?.takeIf { split(it) == null && spec.sides.any { s -> s.score == null } }

    /** A clock of [seconds] left: "0:05", "4:12", "1:04:12". */
    fun clockText(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = (s % 60).toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
    }

    /**
     * What is left of a clock of [clock] seconds set at [setAtMs], at [nowMs], when it [runs]: it
     * counts down from the moment the card's content was set, and stops at 0. A clock that doesn't
     * run, or whose content has no time, stays as it was given.
     */
    fun clockLeft(clock: Long, runs: Boolean, setAtMs: Long, nowMs: Long): Long =
        if (!runs || setAtMs <= 0L || nowMs <= setAtMs) clock
        else (clock - (nowMs - setAtMs) / 1000).coerceAtLeast(0)

    /** Whether side [index]'s clock counts down on the phone: the side to move's, while the card is [WidgetSpec.Versus.moving]. */
    fun clockRuns(spec: WidgetSpec.Versus, index: Int): Boolean =
        spec.moving && turnIndex(spec) == index && spec.sides.getOrNull(index)?.clock != null

    /**
     * A side's figures in its row: [big], the score at the row's end (its own score, else its part
     * of the card's score, else its clock), and [small], the figure before it (its clock where the
     * clock is not the big one, else its text). [bigClock] and [smallClock] say which is the clock,
     * so it can count down.
     */
    data class Figures(val big: String?, val small: String?, val bigClock: Boolean = false, val smallClock: Boolean = false)

    /** Side [index]'s figures ([Figures]). */
    fun figures(spec: WidgetSpec.Versus, index: Int): Figures {
        val side = spec.sides.getOrNull(index) ?: return Figures(null, null)
        val part = split(spec.score)?.let { if (index == 0) it.first else it.second }
        val score = side.score ?: part
        val clock = side.clock?.let(::clockText)
        return when {
            score != null -> Figures(score, clock ?: side.text, smallClock = clock != null)
            clock != null -> Figures(clock, side.text, bigClock = true)
            else -> Figures(null, side.text)
        }
    }

    // ---- what gives way -------------------------------------------------------------------------

    /**
     * The parts of a versus card that give way where room runs out, in the order they go: each
     * side's lines first, then a 2×2 name's second line ([NAMES]: a long name wraps only into spare
     * height, never in place of anything else), the details, the caption, each side's small figure,
     * and the badge. Names are cut with "…" last; marks and scores never go. A screen reader still
     * says every part.
     */
    enum class Part { LINES, NAMES, DETAIL, CAPTION, TEXT, BADGE }

    /**
     * The face of a versus card, by its box and width: [SMALL] (1×1, the marks and the scores),
     * [NARROW] (2×1, the rows alone), [ROW] (4×1, the rows and a status column), [SQUARE] (2×2,
     * a header, the rows with a line under each name, the foot) and [LARGE] (4×2, as a 2×2 with a
     * figure column and each side's lines).
     */
    enum class Face { SMALL, NARROW, ROW, SQUARE, LARGE }

    /** The face of a card in [box], [widthDp] wide. */
    fun face(box: WidgetSizes.Box, widthDp: Float): Face = when (box) {
        WidgetSizes.Box.SMALL -> Face.SMALL
        WidgetSizes.Box.WIDE -> if (widthDp >= ROW_FROM_DP) Face.ROW else Face.NARROW
        WidgetSizes.Box.LARGE -> if (widthDp >= ROW_FROM_DP) Face.LARGE else Face.SQUARE
    }

    /** The parts a [face] has a place for at all. */
    fun places(face: Face): Set<Part> = when (face) {
        Face.SMALL, Face.NARROW -> emptySet()
        Face.ROW -> setOf(Part.CAPTION, Part.TEXT, Part.BADGE)
        Face.SQUARE -> setOf(Part.DETAIL, Part.CAPTION, Part.TEXT, Part.BADGE)
        Face.LARGE -> Part.entries.toSet() - Part.NAMES
    }

    /**
     * The parts [spec] has that [face] has a place for. [Part.NAMES] is never here: a 2×2 adds it
     * where a name would be cut on one line.
     */
    fun present(spec: WidgetSpec.Versus, face: Face): Set<Part> = places(face).filterTo(mutableSetOf()) { part ->
        when (part) {
            Part.LINES -> spec.sides.any { it.lines.isNotEmpty() }
            Part.DETAIL -> spec.sides.any { it.detail != null }
            Part.CAPTION -> spec.line != null
            Part.TEXT -> spec.sides.indices.any { figures(spec, it).small != null }
            Part.BADGE -> spec.badge != null
            Part.NAMES -> false
        }
    }

    /**
     * Of the [present] parts, those kept where [fits] says what fits: they give way one by one, in
     * [Part]'s order, until the rest fits. With none left that fits, none is kept.
     */
    fun keep(present: Set<Part>, fits: (Set<Part>) -> Boolean): Set<Part> {
        var kept = present
        for (part in Part.entries) {
            if (fits(kept)) return kept
            kept = kept - part
        }
        return kept
    }

    /**
     * The heights of a large card's parts, in dp, as the phone's font size draws them: the header
     * line ([header]), a row's line ([line]: its mark, its name or its score), a line under a name
     * ([sub]), the room between the rows ([rowGap]), the caption's line ([caption]), the card's age
     * ([age]), the bar with the room over it ([bar]), and a whole score's line ([whole]). Each zone
     * (the header, the rows, the foot) has [zonePad] inside it, and [zoneGap] between it and the
     * next: Split's tiles.
     */
    data class Heights(
        val header: Int,
        val line: Int,
        val sub: Int,
        val rowGap: Int,
        val caption: Int,
        val age: Int = 0,
        val bar: Int = 0,
        val whole: Int = 0,
        val zonePad: Int = 0,
        val zoneGap: Int = 0,
        /** How much taller each side's row is with its name on two lines, while [Part.NAMES] is kept (0 for one line). */
        val nameExtra: List<Int> = emptyList(),
    )

    /**
     * The height a large card's body needs with the [kept] parts, in dp ([Heights]).
     * - A header shows with a title, a badge, or (4×2) a whole score.
     * - Under each name: a 2×2 has one line, the small figure and the detail; a 4×2 the detail and
     *   the lines, each on a line of its own.
     * - A 2×2 name on two lines ([Part.NAMES]) makes its row [Heights.nameExtra] taller.
     * - A whole score is in a 2×2's middle, over the rows.
     * - [age] (0 without it) is the card's own age, which a Bold or Split card writes: on the
     *   caption's line at 4×2, under it at 2×2.
     */
    fun largeHeight(spec: WidgetSpec.Versus, face: Face, kept: Set<Part>, h: Heights, title: Boolean): Int {
        val whole = whole(spec) != null
        val header = title || (Part.BADGE in kept && spec.badge != null) || (face == Face.LARGE && whole)
        val rows = spec.sides.indices.sumOf { i ->
            val side = spec.sides[i]
            val subs = if (face == Face.LARGE) {
                listOf(Part.DETAIL in kept && side.detail != null, Part.LINES in kept && side.lines.isNotEmpty()).count { it }
            } else {
                if ((Part.TEXT in kept && figures(spec, i).small != null) || (Part.DETAIL in kept && side.detail != null)) 1 else 0
            }
            h.line + (if (Part.NAMES in kept) h.nameExtra.getOrElse(i) { 0 } else 0) + subs * h.sub
        } + h.rowGap * (spec.sides.size - 1).coerceAtLeast(0)
        val caption = Part.CAPTION in kept && spec.line != null
        val foot = when {
            caption && h.age > 0 && face == Face.SQUARE -> h.caption + h.age
            caption -> maxOf(h.caption, h.age)
            else -> h.age
        }
        val body = rows + h.bar + (if (face == Face.SQUARE && whole) h.whole else 0) + h.zonePad
        return body + (if (header) h.header + h.zonePad + h.zoneGap else 0) + (if (foot > 0) foot + h.zonePad + h.zoneGap else 0)
    }

    /**
     * How many lines a 4×1's status column gives the caption, [roomDp] high, with the rest it holds:
     * a whole score ([wholeDp]), the badge ([badgeDp]), the age and the footer ([ageDp]), each 0
     * when absent, with [gapDp] between them. 2, 1, or 0: the caption gives way.
     */
    fun captionLines(roomDp: Int, lineDp: Int, gapDp: Int, wholeDp: Int, badgeDp: Int, ageDp: Int): Int {
        val others = listOf(wholeDp, badgeDp, ageDp).filter { it > 0 }
        val used = others.sum() + gapDp * others.size
        return (2 downTo 1).firstOrNull { used + it * lineDp <= roomDp } ?: 0
    }

    /** A 4×1's status column is as wide as its badge, its age or a caption's line, between these, in dp. */
    const val STATUS_LEAST = 84
    const val STATUS_MOST = 124

    /** The rule and the room between the rows and the status column, in dp. */
    const val STATUS_EDGE = 13

    /**
     * The width of a 4×1's status column, in dp, from the widths of what it holds (0 when absent):
     * the widest, a caption's up to [captionMost] (it wraps), with the rule and room at its start,
     * between [STATUS_LEAST] and [STATUS_MOST].
     */
    fun statusDp(badgeDp: Float, ageDp: Float, captionDp: Float, wholeDp: Float, captionMost: Float = 110f): Float =
        (maxOf(badgeDp, ageDp, minOf(captionDp, captionMost), wholeDp) + STATUS_EDGE)
            .coerceIn(STATUS_LEAST.toFloat(), STATUS_MOST.toFloat())

    /**
     * Whether the small figures' column keeps its place in a row [rowDp] wide: a name keeps the width
     * it needs ([nameDp]), up to [nameLeast]. [fixedDp] is the mark with its gap, [figureDp] the
     * column with its gap, [scoreDp] the score with its gap. Else the column gives way.
     */
    fun figureFits(rowDp: Float, fixedDp: Float, nameDp: Float, figureDp: Float, scoreDp: Float, nameLeast: Float = 72f): Boolean =
        rowDp - fixedDp - figureDp - scoreDp >= minOf(nameDp, nameLeast)

    /**
     * The scores' size: the first of [sizes] (the largest first) at which [fits] says both scores fit,
     * then 2 sp smaller each step down to 12 sp. Both scores take this one size.
     */
    fun scoreSize(sizes: List<Int>, fits: (Int) -> Boolean): Int {
        val all = sizes + (sizes.lastOrNull() ?: 14).let { last -> (last - 2 downTo 12 step 2).toList() }
        return all.firstOrNull(fits) ?: all.last()
    }

    // ---- what a screen reader says --------------------------------------------------------------

    /**
     * What a screen reader says for [spec], in parts: the badge ("67'"); a score drawn whole with
     * the sides around it ("North W/O South"); each side as "name score, figure, detail", with
     * "name's turn" after the side to move, and then its lines; the caption.
     */
    fun said(spec: WidgetSpec.Versus): List<String> {
        val whole = whole(spec)
        val turn = turnIndex(spec)
        return buildList {
            spec.badge?.let(::add)
            if (whole != null) add(listOfNotNull(spec.sides.getOrNull(0)?.name, whole, spec.sides.getOrNull(1)?.name).joinToString(" "))
            spec.sides.forEachIndexed { i, side ->
                val f = figures(spec, i)
                val extras = listOfNotNull(f.small, side.detail, "${side.name}'s turn".takeIf { turn == i })
                val head = listOfNotNull(side.name, f.big).joinToString(" ")
                // A side already said with a whole score, with nothing more, is not said again.
                if (whole == null || f.big != null || extras.isNotEmpty()) add((listOf(head) + extras).joinToString(", "))
                addAll(side.lines)
            }
            spec.line?.let(::add)
        }
    }
}
