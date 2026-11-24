/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * How Kolahal draws one of its own widgets. Each placed widget has its own, chosen on the Widgets
 * screen; the script and its description stay the same. An app's widget draws itself and is always
 * [PLAIN].
 * - [PLAIN]: the card on the surface color, with 28 dp corners.
 * - [TONAL]: the card in its color's container (from the script's `color`), chips and rows in a
 *   lighter tint of it.
 * - [BOLD]: the main value at display size, in the accent color.
 * - [SPLIT]: the card in connected tiles: 28 dp corners outside, 8 dp inside, 4 dp apart.
 * - [SHAPE]: the main thing takes a Material shape. In a square box (1×1, 2×2) that is the whole
 *   card; in a wide one the main value sits in a shape with the rest beside it. Buttons, switches
 *   and media controls each take a shape.
 * - [POSTER]: the main value so large that the card's edge crops it, with the label small in a
 *   corner. Only for a kind with one value to make large: stat, countdown and clock.
 * - [NEXT]: one of Kolahal's own cards only. Todos: a ring of the ticked ones, and the next todo
 *   large with a check to tap. Reminders: the next one as a pill that empties as its time comes,
 *   the later ones on a line.
 * - [STACK]: Notes only. The newest note on a stack of sheets, and + for a new note.
 * - [NOW]: an agenda's now and next: what is on now and how far along it is, the free time until
 *   the next event, and that event; a large card has the day with the free time between its
 *   events. Only for an agenda.
 * A look may leave out minor fields; a screen reader still reads them.
 */
enum class WidgetLook(val id: String, val label: String) {
    PLAIN("plain", "Plain"),
    TONAL("tonal", "Tonal"),
    BOLD("bold", "Bold"),
    SPLIT("split", "Split"),
    SHAPE("shape", "Shape"),
    POSTER("poster", "Poster"),
    NEXT("next", "Next"),
    STACK("stack", "Stack"),
    NOW("now", "Now");

    /**
     * The look a card of [kind] draws when this one is chosen: this one when the kind has it
     * ([offeredFor]), else [PLAIN]. The chosen look is kept either way, so it comes back if the
     * script shows a kind that has it. [builtin] is the id of one of Kolahal's own widgets, for the
     * looks only it has.
     */
    fun drawnFor(kind: WidgetKind?, builtin: String? = null): WidgetLook =
        if (this in offeredFor(kind, builtin)) this else PLAIN

    companion object {
        /** The look stored as [id], in any case. Null for anything else: a newer Kolahal's look reads as none. */
        fun named(id: String?): WidgetLook? {
            val wanted = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }

        private val withoutBold = entries - BOLD
        private val forPictures = listOf(PLAIN, TONAL, SHAPE)
        private val withoutShape = entries - SHAPE

        /**
         * The looks that change how a card of [kind] looks, so the only ones offered for it. All of
         * them but [NOW] when the kind isn't known yet (a widget that has shown nothing).
         * - A picture covers its card, so only the card's color can change it, and Shape, which
         *   cuts the picture to a shape: Plain, Tonal and Shape.
         * - Buttons, switches and media controls have no main value to make large: no Bold.
         * - A versus card draws each side's mark on a Material shape in every look: no Shape.
         * - Only a stat, a countdown and a clock have Poster ([withPoster]).
         * - Next and Stack are only for one of Kolahal's own widgets, by its id [builtin] ([ownLooks]).
         * - Only an agenda has Now.
         */
        fun offeredFor(kind: WidgetKind?, builtin: String? = null): List<WidgetLook> = when (kind) {
            WidgetKind.IMAGE -> forPictures
            WidgetKind.BUTTONS, WidgetKind.TOGGLES, WidgetKind.MEDIA -> withoutBold
            WidgetKind.VERSUS -> withoutShape
            else -> entries
        }.let { if (kind == null || kind in withPoster) it else it - POSTER }
            .let { ownLooks(it - ownOnly, builtin) }
            .let { if (kind == WidgetKind.AGENDA) it else it - NOW }

        /** The looks of one of Kolahal's own cards only: never offered for a script's widget. */
        val ownOnly: List<WidgetLook> = listOf(NEXT, STACK)

        /**
         * [looks] with those that only the built-in widget [builtin] has, last in the row.
         * - Todos: Next takes Shape's place. Each of its rows has a checkbox, which Shape draws where
         *   other lists have a shape, so on Todos it was Plain without the rows' tints.
         * - Reminders: Next. Notes: Stack.
         */
        private fun ownLooks(looks: List<WidgetLook>, builtin: String?): List<WidgetLook> =
            when (builtin) {
                WidgetSource.TODOS -> looks - SHAPE + NEXT
                WidgetSource.REMINDERS -> looks + NEXT
                WidgetSource.NOTES -> looks + STACK
                else -> looks
            }

        /** The kinds with one value to make as large as a poster's. */
        private val withPoster = listOf(WidgetKind.STAT, WidgetKind.COUNTDOWN, WidgetKind.CLOCK)
    }
}
