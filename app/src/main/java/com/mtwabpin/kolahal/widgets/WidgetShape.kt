/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * One of Material's shapes, for a card in the Shape look ([WidgetLook.SHAPE]) that has one shape:
 * the whole card when it is square, or the shape its main value sits in when it is wide. Each card
 * has a shape of its own (a 12-sided cookie for a number, a clover for a level); the user can give a
 * placed card another one ([PlacedWidget.shape]), and a script can name the one its card starts with
 * ([WidgetBase.shape]).
 * - [id] is how a script names it and how it is stored.
 * - [label] is its plain name, for a screen reader and the guide.
 */
enum class WidgetShape(val id: String, val label: String) {
    COOKIE9("cookie9", "9-sided cookie"),
    COOKIE12("cookie12", "12-sided cookie"),
    CLOVER4("clover4", "4-leaf clover"),
    CLOVER8("clover8", "8-leaf clover"),
    SUN("sun", "Sun"),
    BURST("burst", "Burst");

    companion object {
        /** The shape stored or written as [id], in any case. Null for anything else: a newer Kolahal's shape reads as none. */
        fun named(id: String?): WidgetShape? {
            val wanted = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }

        /** Every shape's [id], as a script may write them. */
        val ids: List<String> get() = entries.map { it.id }

        /**
         * The shapes offered for a card whose own shape is [own]: that one first, so one tap gives the
         * card its own shape back, then the others in their order.
         */
        fun choices(own: WidgetShape): List<WidgetShape> = listOf(own) + (entries - own)
    }
}
