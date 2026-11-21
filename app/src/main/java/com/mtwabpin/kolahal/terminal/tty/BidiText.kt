/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

/**
 * Right-to-left text and scripts whose letters join, for the grid. Pure: [java.text.Bidi] is in the
 * JVM and in Android alike.
 * - [needs]: whether a text has anything that plain left-to-right cell-by-cell layout gets wrong.
 * - [levels]: the Unicode bidi algorithm's embedding level of each character. Each line of the
 *   text (up to a line break) is a paragraph; its direction is that of its first strong letter
 *   (as the GUI's text has it), else left to right.
 * - [joins]: a letter of a script that must be shaped as a whole word (Arabic, Hebrew, the Indic
 *   scripts, Thai…). The painter draws such words in one piece, fitted to their cells.
 * - [mirror]: a bracket's mirror image, for brackets inside right-to-left text.
 */
internal object BidiText {
    /** True when [text] has a right-to-left letter, a direction mark or a letter that joins. */
    fun needs(text: CharSequence): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.code >= 0x0590) {
                val cp = Character.codePointAt(text, i)
                if (isRtl(cp) || joins(cp)) return true
                i += Character.charCount(cp)
            } else i++
        }
        return false
    }

    /**
     * The embedding level of each character of [text] (even: left to right, odd: right to left),
     * and the level of the paragraph each one is in (0 or 1). A line break ends a paragraph.
     */
    fun levels(text: String): Pair<ByteArray, ByteArray> {
        val levels = ByteArray(text.length)
        val base = ByteArray(text.length)
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            if (end > start) {
                val bidi = java.text.Bidi(text.substring(start, end), java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
                val b = bidi.baseLevel.toByte()
                for (i in start until end) {
                    levels[i] = bidi.getLevelAt(i - start).toByte()
                    base[i] = b
                }
            }
            if (end < text.length) {
                // The line break itself is on the paragraph's level.
                levels[end] = if (end > start) base[end - 1] else 0
                base[end] = levels[end]
            }
            start = end + 1
        }
        return levels to base
    }

    /** True for a letter written right to left (Hebrew, Arabic, Syriac, Thaana, N'Ko…) or an explicit direction mark. */
    fun isRtl(cp: Int): Boolean = when (Character.getDirectionality(cp)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE,
        Character.DIRECTIONALITY_FIRST_STRONG_ISOLATE -> true

        else -> false
    }

    /**
     * True for a letter of a script that is shaped: its letters join or change with their
     * neighbours, so a word can't be drawn one cell at a time.
     */
    fun joins(cp: Int): Boolean {
        if (cp < 0x0590) return false
        return Character.isValidCodePoint(cp) && Character.UnicodeScript.of(cp) in SHAPED
    }

    private val SHAPED = java.util.EnumSet.of(
        Character.UnicodeScript.HEBREW,
        Character.UnicodeScript.ARABIC,
        Character.UnicodeScript.SYRIAC,
        Character.UnicodeScript.THAANA,
        Character.UnicodeScript.NKO,
        Character.UnicodeScript.MANDAIC,
        Character.UnicodeScript.MONGOLIAN,
        Character.UnicodeScript.DEVANAGARI,
        Character.UnicodeScript.BENGALI,
        Character.UnicodeScript.GURMUKHI,
        Character.UnicodeScript.GUJARATI,
        Character.UnicodeScript.ORIYA,
        Character.UnicodeScript.TAMIL,
        Character.UnicodeScript.TELUGU,
        Character.UnicodeScript.KANNADA,
        Character.UnicodeScript.MALAYALAM,
        Character.UnicodeScript.SINHALA,
        Character.UnicodeScript.THAI,
        Character.UnicodeScript.LAO,
        Character.UnicodeScript.TIBETAN,
        Character.UnicodeScript.MYANMAR,
        Character.UnicodeScript.KHMER,
    )

    /** [cp]'s mirror image in right-to-left text: `(` for `)` and the like; [cp] itself when it has none. */
    fun mirror(cp: Int): Int {
        val i = MIRRORS.indexOf(cp.toChar())
        if (cp > 0xFFFF || i < 0) return cp
        return MIRRORS[i xor 1].code
    }

    /** Pairs of characters that are each other's mirror image, as Unicode's BidiMirroring lists the common ones. */
    private const val MIRRORS = "()<>[]{}«»‹›⁅⁆⁽⁾₍₎≤≥⟨⟩⟪⟫⟦⟧⦃⦄〈〉《》「」『』【】〔〕〖〗〘〙〚〛"
}
