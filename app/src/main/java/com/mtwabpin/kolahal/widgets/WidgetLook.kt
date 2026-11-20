package com.mtwabpin.kolahal.widgets

/**
 * How Kolahal draws one of its own widgets. Each placed widget has its own, chosen on the Widgets
 * screen; the script and its description stay the same. An app's widget draws itself and is always
 * [PLAIN].
 * - [PLAIN]: the card as it was up to 0.5.14: the surface colour, 28 dp corners, a title line.
 * - [TONAL]: the card in its colour's container (from the script's `color`), chips and rows in a
 *   lighter tint of it.
 * - [BOLD]: the main value at display size, in the accent colour. The title line goes.
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

    companion object {
        /** The look stored as [id], in any case. Null for anything else: a newer Kolahal's look reads as none. */
        fun named(id: String?): WidgetLook? {
            val wanted = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }
    }
}
