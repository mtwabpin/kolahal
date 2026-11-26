/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * Album art as background: the Now playing card's choice, on its settings page, to draw the track's
 * cover behind the whole card ([PlacedWidget.artBackground]). Off: the cover is the face of play or
 * pause, as it has always been.
 * - It is the card's ground only while the track has a cover ([ground]). With none (nothing
 *   playing, "Last played" without a picture, an app that gives none), the card is as it is with
 *   the choice off.
 * - In every look the card has: Plain, Tonal and Shape draw the cover behind the whole card,
 *   in its own corners; Split behind all its tiles. A small Split card is play or pause alone,
 *   whose face is the cover already: it stays so.
 * - The cover behind the card takes the place of the one on play or pause: the same picture twice
 *   reads badly.
 */
object MediaArt {
    /** The switch's name on the settings page. */
    const val LABEL = "Album art as background"

    /** The line under it. */
    const val DETAIL = "The track's cover fills the card, behind the words and buttons"

    /** How the choice is kept in the stored line's field: this word when on, nothing when off. */
    const val STORED = "art"

    /** Its key in a backup's entry, there only when on. */
    const val BACKUP = "art-background"

    /** Whether the settings page offers the switch: for the built-in Now playing card ([builtin]) only. */
    fun offered(builtin: String?): Boolean = builtin == WidgetSource.MEDIA

    /**
     * Whether the cover is the card's ground: the choice is [on], the track has a cover ([cover]),
     * and the card is not a small Split card, which is play or pause alone with the cover as its
     * face already. [look] is the look the card is drawn in.
     */
    fun ground(on: Boolean, cover: Boolean, look: WidgetLook, box: WidgetSizes.Box): Boolean =
        on && cover && !(look == WidgetLook.SPLIT && box == WidgetSizes.Box.SMALL)

    /** The choice as the stored line's field holds it ([STORED]). */
    fun read(raw: Any?): Boolean = when (raw) {
        is Boolean -> raw
        is String -> raw.trim().let { it == STORED || it == "true" }
        else -> false
    }
}
