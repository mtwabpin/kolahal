/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

/**
 * How many grid cells a piece of text takes: 0, 1 or 2 per grapheme cluster (what a reader sees
 * as one character: a letter with its accents, an emoji with its skin tone, a flag).
 *
 * Layout must give the same answer on every phone and in JVM tests, so this never asks a font.
 * The painter then makes each glyph fit its cells.
 * - 2: East Asian wide and fullwidth characters (CJK, Hangul, kana), emoji shown as emoji (one
 *   that is an emoji by default, or any with U+FE0F after it), and flags.
 * - 0: a combining mark or a zero-width character on its own, and control characters (layout
 *   drops them or draws them as pictures).
 * - 1: everything else, including box drawing, blocks and the "ambiguous" characters (as in
 *   Western terminals).
 *
 * Clusters follow Unicode's extended grapheme clusters (UAX #29) in the parts that matter on a
 * grid: marks, ZWJ sequences, emoji modifiers, variation selectors, flags and Hangul. The marks
 * come from the platform's character types ([Character.getType]); the wide ranges are a table here.
 */
object CellWidth {
    /** Cells for the cluster [cluster] (one cluster; see [clusters]). */
    fun of(cluster: CharSequence): Int = if (cluster.isEmpty()) 0 else width(cluster, 0, cluster.length)

    /** Cells for all of [text], cluster by cluster. */
    fun total(text: CharSequence): Int {
        var cells = 0
        var i = 0
        while (i < text.length) {
            val end = clusterEnd(text, i)
            cells += width(text, i, end)
            i = end
        }
        return cells
    }

    /** [text] split into grapheme clusters. Allocates; layout walks with [clusterEnd] instead. */
    fun clusters(text: CharSequence): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val end = clusterEnd(text, i)
            out += text.substring(i, end)
            i = end
        }
        return out
    }

    /** Where the cluster that starts at [start] ends (exclusive). Allocates nothing. */
    fun clusterEnd(text: CharSequence, start: Int): Int {
        val n = text.length
        var i = start
        var cp = Character.codePointAt(text, i)
        var prev = property(cp)
        i += Character.charCount(cp)
        when (prev) {
            CR -> return if (i < n && text[i] == '\n') i + 1 else i
            LF, CONTROL -> return i
        }
        // GB12–13: flags are pairs of regional indicators.
        var regional = if (prev == RI) 1 else 0
        // GB11: a pictograph, any marks, then ZWJ joins the next pictograph.
        var pictograph = prev == PICT
        var joinsPictograph = false
        while (i < n) {
            cp = Character.codePointAt(text, i)
            val p = property(cp)
            val joins = when {
                p == CR || p == LF || p == CONTROL -> false
                p == EXTEND || p == ZWJ || p == SPACING -> true
                prev == L && (p == L || p == V || p == LV || p == LVT) -> true
                (prev == LV || prev == V) && (p == V || p == T) -> true
                (prev == LVT || prev == T) && p == T -> true
                p == PICT && prev == ZWJ && joinsPictograph -> true
                p == RI && prev == RI && regional % 2 == 1 -> true
                else -> false
            }
            if (!joins) break
            when (p) {
                RI -> regional++
                PICT -> pictograph = true
                ZWJ -> {
                    joinsPictograph = pictograph; pictograph = false
                }

                EXTEND -> Unit
                else -> pictograph = false
            }
            prev = p
            i += Character.charCount(cp)
        }
        return i
    }

    /** Cells for the one cluster in [text] from [start] to [end]. Allocates nothing. */
    fun width(text: CharSequence, start: Int, end: Int): Int {
        val base = Character.codePointAt(text, start)
        val own = ofCodePoint(base)
        var i = start + Character.charCount(base)
        if (i >= end) return own
        if (own == 0) return 0
        var emoji = false
        var text15 = false
        var flag = false
        while (i < end) {
            val cp = Character.codePointAt(text, i)
            when {
                cp == 0xFE0F -> emoji = true
                cp == 0xFE0E -> text15 = true
                cp in 0x1F3FB..0x1F3FF -> emoji = true
                cp == 0x200D -> emoji = emoji || isPictographic(base)
                cp in RI_FIRST..RI_LAST -> flag = true
            }
            i += Character.charCount(cp)
        }
        return when {
            flag && base in RI_FIRST..RI_LAST -> 2
            emoji -> 2
            text15 && isPictographic(base) -> 1
            else -> own
        }
    }

    /** Cells for a single code point, alone. */
    fun ofCodePoint(cp: Int): Int = when {
        cp < 0x20 || cp in 0x7F..0x9F -> 0
        cp < 0x300 -> 1
        isZeroWidth(cp) -> 0
        isWide(cp) -> 2
        else -> 1
    }

    /** True for a code point that takes no cell: a mark, a format character, a Hangul vowel or final consonant. */
    fun isZeroWidth(cp: Int): Boolean {
        if (cp in 0x1160..0x11FF || cp in 0xD7B0..0xD7FF) return true
        if (cp == 0x00AD) return false
        return when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT -> true
            else -> false
        }
    }

    /** True for an East Asian wide or fullwidth code point, or an emoji shown as emoji by default. */
    fun isWide(cp: Int): Boolean {
        if (cp < WIDE[0]) return false
        var lo = 0
        var hi = WIDE.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                cp < WIDE[mid * 2] -> hi = mid - 1
                cp > WIDE[mid * 2 + 1] -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    // Grapheme break properties, as far as the rules above need them.
    private const val OTHER = 0
    private const val CR = 1
    private const val LF = 2
    private const val CONTROL = 3
    private const val EXTEND = 4
    private const val ZWJ = 5
    private const val RI = 6
    private const val SPACING = 7
    private const val L = 8
    private const val V = 9
    private const val T = 10
    private const val LV = 11
    private const val LVT = 12
    private const val PICT = 13

    private const val RI_FIRST = 0x1F1E6
    private const val RI_LAST = 0x1F1FF

    private fun property(cp: Int): Int = when {
        cp == 0x0D -> CR
        cp == 0x0A -> LF
        cp < 0x20 || cp in 0x7F..0x9F -> CONTROL
        cp < 0x300 -> if (cp == 0xA9 || cp == 0xAE) PICT else OTHER
        cp == 0x200D -> ZWJ
        cp == 0x200C || cp in 0xE0020..0xE007F || cp in 0xFF9E..0xFF9F -> EXTEND
        cp in RI_FIRST..RI_LAST -> RI
        cp in 0x1F3FB..0x1F3FF -> EXTEND
        cp in 0x1100..0x115F || cp in 0xA960..0xA97C -> L
        cp in 0x1160..0x11A7 || cp in 0xD7B0..0xD7C6 -> V
        cp in 0x11A8..0x11FF || cp in 0xD7CB..0xD7FB -> T
        cp in 0xAC00..0xD7A3 -> if ((cp - 0xAC00) % 28 == 0) LV else LVT
        cp == 0x0E33 || cp == 0x0EB3 -> SPACING
        isPictographic(cp) -> PICT
        else -> when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK -> EXTEND
            Character.COMBINING_SPACING_MARK -> SPACING
            Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> CONTROL
            else -> OTHER
        }
    }

    /** Close to Unicode's Extended_Pictographic: the code points emoji are made from. */
    private fun isPictographic(cp: Int): Boolean = when {
        cp == 0xA9 || cp == 0xAE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 -> true
        cp in 0x2194..0x2199 || cp in 0x21A9..0x21AA || cp == 0x24C2 -> true
        cp in 0x2300..0x23FF || cp in 0x25AA..0x25AB || cp == 0x25B6 || cp == 0x25C0 || cp in 0x25FB..0x25FE -> true
        cp in 0x2600..0x27BF || cp in 0x2934..0x2935 || cp in 0x2B05..0x2B07 || cp in 0x2B1B..0x2B1C -> true
        cp == 0x2B50 || cp == 0x2B55 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 -> true
        cp in 0x1F000..0x1FAFF && cp !in RI_FIRST..RI_LAST && cp !in 0x1F3FB..0x1F3FF -> true
        else -> false
    }

    /**
     * East Asian Width W and F, and Emoji_Presentation, as ranges (first, last), sorted.
     * From Unicode 15.1's EastAsianWidth.txt, with neighbouring ranges merged.
     */
    private val WIDE = intArrayOf(
        0x1100, 0x115F, 0x231A, 0x231B, 0x2329, 0x232A, 0x23E9, 0x23EC, 0x23F0, 0x23F0,
        0x23F3, 0x23F3, 0x25FD, 0x25FE, 0x2614, 0x2615, 0x2648, 0x2653, 0x267F, 0x267F,
        0x2693, 0x2693, 0x26A1, 0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE, 0x26C4, 0x26C5,
        0x26CE, 0x26CE, 0x26D4, 0x26D4, 0x26EA, 0x26EA, 0x26F2, 0x26F3, 0x26F5, 0x26F5,
        0x26FA, 0x26FA, 0x26FD, 0x26FD, 0x2705, 0x2705, 0x270A, 0x270B, 0x2728, 0x2728,
        0x274C, 0x274C, 0x274E, 0x274E, 0x2753, 0x2755, 0x2757, 0x2757, 0x2795, 0x2797,
        0x27B0, 0x27B0, 0x27BF, 0x27BF, 0x2B1B, 0x2B1C, 0x2B50, 0x2B50, 0x2B55, 0x2B55,
        0x2E80, 0x2E99, 0x2E9B, 0x2EF3, 0x2F00, 0x2FD5, 0x2FF0, 0x2FFF, 0x3000, 0x303E,
        0x3041, 0x3096, 0x3099, 0x30FF, 0x3105, 0x312F, 0x3131, 0x318E, 0x3190, 0x31E3,
        0x31EF, 0x321E, 0x3220, 0x3247, 0x3250, 0x4DBF, 0x4E00, 0xA48C, 0xA490, 0xA4C6,
        0xA960, 0xA97C, 0xAC00, 0xD7A3, 0xF900, 0xFAFF, 0xFE10, 0xFE19, 0xFE30, 0xFE52,
        0xFE54, 0xFE66, 0xFE68, 0xFE6B, 0xFF01, 0xFF60, 0xFFE0, 0xFFE6,
        0x16FE0, 0x16FE4, 0x16FF0, 0x16FF1, 0x17000, 0x187F7, 0x18800, 0x18CD5, 0x18D00, 0x18D08,
        0x1AFF0, 0x1AFF3, 0x1AFF5, 0x1AFFB, 0x1AFFD, 0x1AFFE, 0x1B000, 0x1B122, 0x1B132, 0x1B132,
        0x1B150, 0x1B152, 0x1B155, 0x1B155, 0x1B164, 0x1B167, 0x1B170, 0x1B2FB,
        0x1F004, 0x1F004, 0x1F0CF, 0x1F0CF, 0x1F18E, 0x1F18E, 0x1F191, 0x1F19A,
        0x1F200, 0x1F202, 0x1F210, 0x1F23B, 0x1F240, 0x1F248, 0x1F250, 0x1F251, 0x1F260, 0x1F265,
        0x1F300, 0x1F320, 0x1F32D, 0x1F335, 0x1F337, 0x1F37C, 0x1F37E, 0x1F393, 0x1F3A0, 0x1F3CA,
        0x1F3CF, 0x1F3D3, 0x1F3E0, 0x1F3F0, 0x1F3F4, 0x1F3F4, 0x1F3F8, 0x1F43E, 0x1F440, 0x1F440,
        0x1F442, 0x1F4FC, 0x1F4FF, 0x1F53D, 0x1F54B, 0x1F54E, 0x1F550, 0x1F567, 0x1F57A, 0x1F57A,
        0x1F595, 0x1F596, 0x1F5A4, 0x1F5A4, 0x1F5FB, 0x1F64F, 0x1F680, 0x1F6C5, 0x1F6CC, 0x1F6CC,
        0x1F6D0, 0x1F6D2, 0x1F6D5, 0x1F6D7, 0x1F6DC, 0x1F6DF, 0x1F6EB, 0x1F6EC, 0x1F6F4, 0x1F6FC,
        0x1F7E0, 0x1F7EB, 0x1F7F0, 0x1F7F0, 0x1F90C, 0x1F93A, 0x1F93C, 0x1F945, 0x1F947, 0x1F9FF,
        0x1FA70, 0x1FA7C, 0x1FA80, 0x1FA89, 0x1FA8F, 0x1FAC6, 0x1FACE, 0x1FADC, 0x1FADF, 0x1FAE9,
        0x1FAF0, 0x1FAF8, 0x20000, 0x2FFFD, 0x30000, 0x3FFFD,
    )
}
