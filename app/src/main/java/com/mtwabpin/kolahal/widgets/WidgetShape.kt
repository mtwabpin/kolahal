/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * One of Material's shapes, for a card in the Shape look ([WidgetLook.SHAPE]) that has one shape:
 * the whole card when it is square, or the shape its main value sits in when it is wide. Each card
 * has a shape of its own (a 12-sided cookie for a number, a clover for a level); the user can give a
 * placed card another one on its settings page ([PlacedWidget.shape]), and a script can name the one
 * its card starts with ([WidgetBase.shape]).
 * - [id] is how a script names it and how it is stored.
 * - [label] is its plain name, for a screen reader and the guide; [short] is the name under it on
 *   the settings page, where there is little room.
 * - [symmetric]: the same turned a half turn about its middle. A round thing drawn inside (a
 *   clock's or a gauge's dial, rings) sits right only in such a shape. Slanted, arch, ghost and pill
 *   are not.
 * Every shape here keeps a three-digit number clear of its edge at 1×1 and 2×2.
 */
enum class WidgetShape(
    val id: String,
    val label: String,
    val short: String,
    val symmetric: Boolean = true
) {
    COOKIE9("cookie9", "9-sided cookie", "Cookie 9"),
    COOKIE12("cookie12", "12-sided cookie", "Cookie 12"),
    CLOVER4("clover4", "4-leaf clover", "Clover"),
    CLOVER8("clover8", "8-leaf clover", "Clover 8"),
    SUN("sun", "Sun", "Sun"),
    BURST("burst", "Burst", "Burst"),
    CIRCLE("circle", "Circle", "Circle"),
    COOKIE4("cookie4", "4-sided cookie", "Cookie 4"),
    COOKIE6("cookie6", "6-sided cookie", "Cookie 6"),
    GEM("gem", "Gem", "Gem"),
    PIXEL("pixel", "Pixel circle", "Pixel"),
    SLANTED("slanted", "Slanted square", "Slanted", symmetric = false),
    ARCH("arch", "Arch", "Arch", symmetric = false),
    GHOST("ghost", "Ghost", "Ghost", symmetric = false),
    PILL("pill", "Pill", "Pill", symmetric = false);

    companion object {
        /** The shape stored or written as [id], in any case. Null for anything else: a newer Kolahal's shape reads as none. */
        fun named(id: String?): WidgetShape? {
            val wanted = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == wanted }
        }

        /** Every shape's [id], as a script may write them. */
        val ids: List<String> get() = entries.map { it.id }

        /**
         * The shapes offered for a card whose own shape is [own], of those it may take ([offered]):
         * its own first, so one tap gives the card its own shape back, then the others in their order.
         */
        fun choices(own: WidgetShape, offered: List<WidgetShape> = entries): List<WidgetShape> =
            listOf(own) + (offered - own)
    }
}
