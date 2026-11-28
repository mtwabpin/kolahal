/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * Where a card writes the line about its content ([WidgetWords.Footer]): "Updated 3 min ago", or
 * "Failed 10:42". Pure, so it is unit tested.
 * - A large card in Plain or Tonal has always had it under its content, once the content is old.
 *   So has a picture that covers its card, in its chip.
 * - With Last updated on ([com.mtwabpin.kolahal.widgets.WidgetParts.UPDATED], [WidgetWords.Footer.asked])
 *   it shows always, there and wherever else a card has room for it: a wide card (2×1, 4×1) in
 *   Plain or Tonal whose content leaves a line free ([wideRoom]), and a versus card in each of its
 *   looks from 4×1, where the card places it itself ([Place.BODY]).
 * - Never at 1×1, nor in a look that is the content (Bold, Split, Shape, Poster) but a versus
 *   card's: those leave it out. A screen reader still hears it.
 * Content keeps its size and its lines: the line takes room the card had free, or the card leaves it out.
 * The script's footer goes on the same line, before the age, wherever the line may show ([footer]).
 */
internal object UpdatedLine {
    /** Where the line goes: nowhere, under the content ([CARD]), or where the body places it ([BODY]). */
    enum class Place { NONE, CARD, BODY }

    /**
     * Whether a wide card (one row) in Plain or Tonal showing [spec] (of [kind] when not known yet)
     * has a line free under its content. The content sits in the middle of the card with room to
     * spare: a number without its recent numbers, a text, a bar, a countdown in words, a picture
     * that covers the card (in its chip). The others fill the card's height and would shrink or lose
     * a line: a chart, a clock, buttons, an agenda's or a list's rows, a gauge, rings, stages, a
     * countdown's dots or page, a sparkline.
     */
    private fun wideRoom(spec: WidgetSpec?, kind: WidgetKind?): Boolean = when (spec) {
        null -> kind in setOf(WidgetKind.STAT, WidgetKind.TEXT, WidgetKind.PROGRESS, WidgetKind.COUNTDOWN)
        is WidgetSpec.Stat -> spec.points.size < 2
        is WidgetSpec.Text -> true
        is WidgetSpec.Progress ->
            (spec.style == null || spec.style == "bar") && spec.items.isEmpty() && spec.stages.isEmpty()
        is WidgetSpec.Countdown -> spec.style != CountdownDays.DOTS && spec.style != CountdownDays.PAGE
        is WidgetSpec.Image -> covers(spec)
        else -> false
    }

    /** A picture that covers its card ([BleedImageCard]): its chip has the line, as it always has. */
    private fun covers(spec: WidgetSpec.Image): Boolean = spec.fit != "fit"

    /** The looks that draw a card around its content (Plain, Tonal, an agenda's Now): the others are the content ([untitled]). */
    private fun framed(look: WidgetLook): Boolean = !look.untitled()

    /**
     * Where a card showing [spec] in [look] (as drawn), [widthDp] by [heightDp], writes its line.
     * [asked]: its Last updated is on, else only a large card writes it, as old content's age.
     */
    fun place(spec: WidgetSpec?, look: WidgetLook, widthDp: Int, heightDp: Int, asked: Boolean): Place {
        val box = WidgetSizes.box(widthDp, heightDp)
        // A large card under its content, in the looks that frame it, as it has always been.
        if (box == WidgetSizes.Box.LARGE && framed(look)) return Place.CARD
        val kind = spec?.let(WidgetJson::kindOf)
        if (!asked || why(spec, kind, look, widthDp, heightDp) != null) return Place.NONE
        return if (kind == WidgetKind.VERSUS) Place.BODY else Place.CARD
    }

    /**
     * Where a card showing [spec] in [look] (as drawn), [widthDp] by [heightDp], writes the script's
     * footer ([com.mtwabpin.kolahal.widgets.WidgetBase.footer]), on the one foot line with the note
     * and the age ([FootRoom]). Last updated on or off doesn't matter: a footer alone takes the line.
     * - A large card in the looks that frame it: always ([Place.CARD]); a picture that covers its card,
     *   in its chip.
     * - A wide card in those looks: wherever the age may show ([why] is null), but not a picture that
     *   covers its card, whose chip has no room for it. A versus card writes it in its body ([Place.BODY]).
     * - Never at 1×1, nor in Bold, Split, Shape or Poster, as the age. A screen reader still hears it.
     */
    fun footer(spec: WidgetSpec?, look: WidgetLook, widthDp: Int, heightDp: Int): Place {
        if (spec?.base?.footer == null || !framed(look)) return Place.NONE
        val kind = WidgetJson.kindOf(spec)
        return when (WidgetSizes.box(widthDp, heightDp)) {
            WidgetSizes.Box.LARGE -> Place.CARD
            WidgetSizes.Box.SMALL -> Place.NONE
            WidgetSizes.Box.WIDE -> when {
                why(spec, kind, look, widthDp, heightDp) != null -> Place.NONE
                kind == WidgetKind.VERSUS -> Place.BODY
                spec is WidgetSpec.Image && covers(spec) -> Place.NONE
                else -> Place.CARD
            }
        }
    }

    /**
     * Why a card showing [spec] (of [kind] when not known yet) in [look], [widthDp] by [heightDp],
     * leaves the line out with Last updated on, as its switch says it under its own words: "Not at
     * 1×1" ([size] is the card's size, as the page writes it, or "this size" for one of its own),
     * "Not in Bold". Null when it shows the line.
     */
    fun why(
        spec: WidgetSpec?,
        kind: WidgetKind?,
        look: WidgetLook,
        widthDp: Int,
        heightDp: Int,
        size: String = ""
    ): String? {
        val box = WidgetSizes.box(widthDp, heightDp)
        val k = spec?.let(WidgetJson::kindOf) ?: kind
        val at = "Not at " + size.ifBlank { "this size" }
        return when {
            box == WidgetSizes.Box.SMALL -> at
            k == WidgetKind.VERSUS ->
                // A 2×1 versus card is its two rows: no line free.
                if (box == WidgetSizes.Box.WIDE && widthDp < VersusWords.ROW_FROM_DP) at else null
            !framed(look) -> "Not in ${look.label}"
            box == WidgetSizes.Box.WIDE && !wideRoom(spec, k) -> at
            else -> null
        }
    }
}
