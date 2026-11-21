/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * How Kolahal draws one of its own widgets. Each placed widget has its own, chosen on the Widgets
 * screen; the script and its description stay the same. An app's widget draws itself and is always
 * [PLAIN].
 * - [PLAIN]: the card as it was up to 0.5.14: the surface color, 28 dp corners, a title line.
 * - [TONAL]: the card in its color's container (from the script's `color`), chips and rows in a
 *   lighter tint of it.
 * - [BOLD]: the main value at display size, in the accent color. The title line goes.
 * - [SPLIT]: the card in connected tiles: 28 dp corners outside, 8 dp inside, 4 dp apart.
 * - [SHAPE]: the main thing takes a Material shape. In a square box (1×1, 2×2) that is the whole
 *   card; in a wide one the main value sits in a shape with the rest beside it. Buttons, switches
 *   and media controls each take a shape.
 * A look may leave out minor fields; a screen reader still reads them.
 */
enum class WidgetLook(val id: String, val label: String) {
    PLAIN("plain", "Plain"),
    TONAL("tonal", "Tonal"),
    BOLD("bold", "Bold"),
    SPLIT("split", "Split"),
    SHAPE("shape", "Shape");

    /**
     * The look a card of [kind] draws when this one is chosen: this one when the kind has it
     * ([offeredFor]), else [PLAIN]. The chosen look is kept either way, so it comes back if the
     * script shows a kind that has it.
     */
    fun drawnFor(kind: WidgetKind?): WidgetLook = if (this in offeredFor(kind)) this else PLAIN

    companion object {
        /** The look stored as [id], in any case. Null for anything else: a newer Kolahal's look reads as none. */
        fun named(id: String?): WidgetLook? {
            val wanted = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }

        private val withoutBold = entries - BOLD
        private val plainAndTonal = listOf(PLAIN, TONAL)

        /**
         * The looks that change how a card of [kind] looks, so the only ones offered for it. All of
         * them when the kind isn't known yet (a widget that has shown nothing).
         * - A picture covers its card, so only the card's color can change: Plain and Tonal.
         * - Buttons, switches and media controls have no main value to make large: no Bold.
         */
        fun offeredFor(kind: WidgetKind?): List<WidgetLook> = when (kind) {
            WidgetKind.IMAGE -> plainAndTonal
            WidgetKind.BUTTONS, WidgetKind.TOGGLES, WidgetKind.MEDIA -> withoutBold
            else -> entries
        }
    }
}
