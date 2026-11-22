/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText

/**
 * What a script drew on its screen (`tty.screen`) in one of its calls: the drawing calls in order,
 * as plain numbers and texts, so it crosses from the `:script` process to home cheaply. Home plays it
 * on its character grid, where it knows the width of each character.
 *
 * - [cols] × [rows] is the grid's size when it was drawn. Home drops a frame of another size (one
 *   drawn just before a rotation).
 * - [ops] holds each call's code, then its numbers ([CLEAR], [PUT], [FILL], [BOX], [BAR]). A text is
 *   its index in [texts].
 * - [tick] is true when it answers a tick: home sends the next tick only after it.
 */
class ScreenFrame(
    val cols: Int,
    val rows: Int,
    val ops: IntArray,
    val texts: List<String>,
    val tick: Boolean,
) {
    companion object {
        /** Blanks the grid: style. */
        const val CLEAR = 1

        /** Writes a text: column, row, the text's index, style. A line break goes to the next row. */
        const val PUT = 2

        /** Fills a rectangle: column, row, width, height, the code point (a space blanks), style. */
        const val FILL = 3

        /** A frame of lines: column, row, width, height, style. */
        const val BOX = 4

        /** A bar: column, row, width, how many eighths of a cell are filled, style. */
        const val BAR = 5

        /** How many numbers follow each code; 0 for a code that isn't one. */
        fun operands(op: Int): Int = when (op) {
            CLEAR -> 1
            PUT -> 4
            FILL -> 6
            BOX -> 5
            BAR -> 5
            else -> 0
        }

        /*
         * A style in one number: the text's color role (bits 0-3), the background's (4-7), and the
         * flags. A role is 1 + its place in [StyledText.ColorRole], or 0 for none: the text color on
         * the theme's background. The flags have the same bits as a cell's
         * ([com.mtwabpin.kolahal.terminal.tty.CellStyle]), so home takes them as they are.
         */
        const val BOLD = 1 shl 8
        const val FAINT = 1 shl 9
        const val ITALIC = 1 shl 10
        const val UNDERLINE = 1 shl 11
        const val STRIKE = 1 shl 12
        const val INVERSE = 1 shl 13

        /** Every flag, by the name a script gives it. */
        val FLAGS: Map<String, Int> = linkedMapOf(
            "bold" to BOLD,
            "faint" to FAINT,
            "italic" to ITALIC,
            "underline" to UNDERLINE,
            "strike" to STRIKE,
            "inverse" to INVERSE,
        )

        val FLAG_MASK: Int = FLAGS.values.fold(0) { all, flag -> all or flag }

        /** A style with these color roles (null: none) and [flags]. */
        fun style(fg: StyledText.ColorRole?, bg: StyledText.ColorRole?, flags: Int): Int =
            index(fg) or (index(bg) shl 4) or flags

        private fun index(role: StyledText.ColorRole?) = if (role == null) 0 else role.ordinal + 1

        /** The text's color role in [style], or null. */
        fun fg(style: Int): StyledText.ColorRole? = StyledText.ColorRole.entries.getOrNull((style and 0xF) - 1)

        /** The background's color role in [style], or null. */
        fun bg(style: Int): StyledText.ColorRole? =
            StyledText.ColorRole.entries.getOrNull(((style shr 4) and 0xF) - 1)
    }
}
