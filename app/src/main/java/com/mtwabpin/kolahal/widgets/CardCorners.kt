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
 * - Split's tiles and Shape's shape are an outline of their own: they keep it ([takenIn]).
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

        /** Whether a card in [look] takes the setting: every look but Split and Shape, whose outline is their own. */
        fun takenIn(look: WidgetLook): Boolean = look != WidgetLook.SPLIT && look != WidgetLook.SHAPE

        /** The setting's value [id] as corners: null for `own`, and for anything else. */
        fun named(id: Any?): CardCorners? {
            val wanted = (id as? String)?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }
    }
}
