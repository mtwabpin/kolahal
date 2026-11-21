/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink

/**
 * A cell's look in 32 bits: the text's ink (4 bits), the background's ink (4 bits), then the flags.
 * Only theme roles, never colors, so a theme change needs no new cells.
 * - [BOLD]: the font's bold face.
 * - [FAINT]: dim, drawn as the ink blended toward the background (as SGR 2 does), not with alpha.
 * - [ITALIC], [UNDERLINE], [STRIKE]: as in text.
 * - [INVERSE]: text and background swap.
 */
@JvmInline
value class CellStyle(val bits: Int) {
    val fg: Ink get() = INKS[bits and 0xF]
    val bg: Ink get() = INKS[(bits shr 4) and 0xF]

    fun has(flag: Int) = bits and flag != 0

    /** This style with [flags] added. */
    fun with(flags: Int) = CellStyle(bits or flags)

    /** This style with another text ink. */
    fun ink(fg: Ink) = CellStyle((bits and 0xF.inv()) or fg.ordinal)

    override fun toString(): String = buildString {
        append(fg.name)
        if (bg != Ink.BG) append(" on ").append(bg.name)
        for ((flag, name) in NAMES) if (has(flag)) append(' ').append(name)
    }

    companion object {
        const val BOLD = 1 shl 8
        const val FAINT = 1 shl 9
        const val ITALIC = 1 shl 10
        const val UNDERLINE = 1 shl 11
        const val STRIKE = 1 shl 12
        const val INVERSE = 1 shl 13

        private val INKS = Ink.entries.toTypedArray()
        private val NAMES = listOf(
            BOLD to "bold", FAINT to "faint", ITALIC to "italic",
            UNDERLINE to "underline", STRIKE to "strike", INVERSE to "inverse",
        )

        fun of(fg: Ink, bg: Ink = Ink.BG, flags: Int = 0) =
            CellStyle(fg.ordinal or (bg.ordinal shl 4) or flags)

        /** Plain text on the background. */
        val PLAIN = of(Ink.FG)
    }
}

/** A rectangle of whole cells: [col], [row] is its top left corner. */
data class CellRect(val col: Int, val row: Int, val cols: Int, val rows: Int)
