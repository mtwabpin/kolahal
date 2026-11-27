/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * How round the corners of Kolahal's cards are: one setting for all of them, `widgets.corners`
 * ([com.mtwabpin.kolahal.data.Keys.WIDGET_CORNERS]). `own` (null here) is the cards' own: 24 dp on a
 * small card, 28 dp on a larger one, as cards have always had.
 * - It rounds a card's outline and what is clipped to it: a scene, album art, the shade of a press.
 *   Tiles, rows and pills inside keep their own corners.
 * - Split's tiles are the card's outline: they take the chosen corner where they meet the card's
 *   corners, and one that fits across the gaps between them ([splitOuter], [splitInner]).
 * - A card in Shape that is one Material shape keeps its shape ([takenIn]). Shape's other cards are
 *   rounded cards, and follow it.
 * - An app's widget draws itself, and keeps its own corners.
 * Pure: the card, Arrange widgets and the setting ask here.
 */
enum class CardCorners(val id: String, val dp: Int) {
    SQUARE("square", 0),
    SLIGHT("slight", 8),
    ROUNDED("rounded", 16),
    VERY("very", 40);

    companion object {
        /** The cards' own corners, in dp: 24 on a small card ([small]), else 28. */
        fun own(small: Boolean): Int = if (small) 24 else 28

        /** The corners a card has, in dp: the [chosen] ones, else its own ([own]). */
        fun dp(chosen: CardCorners?, small: Boolean): Int = chosen?.dp ?: own(small)

        /**
         * The corner, in dp, of a surface inside a card that fills it [inset] dp in from its edge (a
         * look's sheet): [own] with the cards' own corners, else the chosen corner less [inset], so it
         * fits the card's: square in a square card, never a small one in a very round card.
         */
        fun inner(chosen: CardCorners?, inset: Int, own: Int): Int = chosen?.let { (it.dp - inset).coerceAtLeast(0) } ?: own

        /**
         * Whether a card in [look] takes the setting: every card but one in Shape that is one Material
         * shape ([oneShape]: a scallop, a clover, a circle), whose shape is the one chosen with the look.
         */
        fun takenIn(look: WidgetLook, oneShape: Boolean): Boolean = !(look == WidgetLook.SHAPE && oneShape)

        /**
         * The kinds whose card can be one Material shape: in Shape, in a square box (a 1×1, a 2×2), and
         * for a progress only with one level. Every other card in Shape is a rounded card with a shape in it.
         */
        val ONE_SHAPE: Set<WidgetKind> =
            setOf(WidgetKind.STAT, WidgetKind.COUNTDOWN, WidgetKind.CLOCK, WidgetKind.CHART, WidgetKind.SKY, WidgetKind.PROGRESS)

        /** Split's corners with the cards' own: 28 dp where a tile meets the card's corner, 8 dp where tiles meet. */
        const val SPLIT_OUTER = 28
        const val SPLIT_INNER = 8

        /** The room between two of Split's tiles, in dp. */
        const val SPLIT_GAP = 4

        /** The corner, in dp, of a Split tile where it meets the card's corner: the [chosen] one, else 28. */
        fun splitOuter(chosen: CardCorners?): Int = chosen?.dp ?: SPLIT_OUTER

        /**
         * The corner, in dp, of a Split tile where it meets another across the gap: the [chosen] one
         * less the gap ([inner]), so Square is square there too, and never more than Split's own 8.
         * The tiles stay one group with the card's corners outside: a larger one would open a wedge
         * between round tiles, where Now playing's cover shows through.
         */
        fun splitInner(chosen: CardCorners?): Int = minOf(inner(chosen, SPLIT_GAP, SPLIT_INNER), SPLIT_INNER)

        /** A chosen corner of [corner] on a tile whose shorter side is [side]: half of [side] at most, so it can't swallow a small tile. */
        fun capped(corner: Float, side: Float): Float = minOf(corner, side / 2)

        /** The setting's value [id] as corners: null for `own`, and for anything else. */
        fun named(id: Any?): CardCorners? {
            val wanted = (id as? String)?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }
    }
}
