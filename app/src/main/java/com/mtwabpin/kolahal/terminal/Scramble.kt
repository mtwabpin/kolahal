/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import org.json.JSONArray

/**
 * `scramble`: home's log as it shows while the mode is on, so a screen recording gives nothing away.
 *
 * A scrambled text keeps only its shape:
 * - its length in cells, its blanks and line breaks, its punctuation and symbols, and its styles,
 *   colors, links and tables ([StyledText] marks stay where they are);
 * - the kind of each character: a capital becomes a capital, a small letter a small letter, a
 *   digit a digit, a letter of a script a letter of that script, a wide character a wide one, and
 *   an emoji another emoji. A mark on a letter (an accent, a vowel sign, a point) becomes another
 *   mark of its kind from that script.
 *
 * Every letter, digit and mark is replaced, in every script. A script with no letters of its own
 * here takes Latin ones, and its marks go: a spacing one alone becomes a letter, so nothing real is
 * left on the line and it keeps its cells. What takes its place comes from [seed], the
 * place of the character, and [text]'s salt, never from the character itself. The same text with
 * the same salt is scrambled the same way, so the log doesn't flicker; another seed gives another text.
 * It is pure: nothing here is stored, and the log itself stays as it is.
 */
@Immutable
data class Scramble(val seed: Long) {

    /** [text] with every letter, digit, mark and emoji replaced. [salt] tells apart texts of one shape. */
    fun text(text: String, salt: Long = 0L): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var i = 0
        var lineStart = true
        // What a mark starts that the log never shows: a link's address, a bar's or a chart's numbers.
        var payload = NONE
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val size = Character.charCount(cp)
            if (cp == '\n'.code) {
                out.append('\n')
                payload = NONE
                lineStart = true
                i++
                continue
            }
            val first = lineStart
            lineStart = false
            when {
                payload == ADDRESS -> {
                    out.appendCodePoint(cp)
                    if (cp == StyledText.LINK_TEXT) payload = NONE
                }

                payload == NUMBERS -> {
                    out.appendCodePoint(cp)
                    if (cp == StyledText.CELL) payload = NONE
                }

                cp in StyledText.BASE..StyledText.LAST -> {
                    out.appendCodePoint(cp)
                    if (cp == StyledText.LINK) payload = ADDRESS
                    else if (first && (cp == StyledText.METER || cp == StyledText.COLUMNS)) payload = NUMBERS
                }

                else -> {
                    i = cluster(text, i, out, salt)
                    continue
                }
            }
            i += size
        }
        return out.toString()
    }

    /** [words] shown with line [id] (its time, an app's name in it), scrambled as the line's own words are. [field] tells them apart. */
    fun line(words: String, id: Long, field: Long): String = text(words, salt(id, field))

    /** A count, as `── 3 new since you left ──` shows it: another number with as many digits. */
    fun count(n: Int, salt: Long = COUNT): Int {
        if (n < 0) return n
        val digits = n.toString().length
        var low = 1L
        repeat(digits - 1) { low *= 10 }
        val from = if (digits == 1) 0L else low
        val span = low * 10 - from
        return (from + pick(salt, 0, span.toInt().coerceAtLeast(1))).toInt()
    }

    /**
     * [e] as the log shows it while scrambled: its text, its source and every value of its context
     * that can show, scrambled. Pictures are replaced ([PICTURE]): a contact's photo goes, so their
     * picture is the initials of the scrambled name. Values the log only reads to lay a line out
     * stay ([KEPT]). The copy says which seed made it ([KEY]); its id and time are the line's.
     */
    fun entry(e: LogEntry): LogEntry = e.copy(
        source = e.source?.let { text(it, salt(e.id, SOURCE)) },
        text = text(e.text, salt(e.id, TEXT)),
        ctx = LogContext(contextOf(e)),
    )

    private fun contextOf(e: LogEntry): Map<String, String> {
        val out = LinkedHashMap<String, String>(e.ctx.values.size + 1)
        for ((key, value) in e.ctx.values) {
            // The key's name tells the values of one line apart; it is never the line's text.
            val salt = salt(e.id, key.hashCode().toLong())
            when (key) {
                in KEPT -> out[key] = value
                LogContext.PHOTO -> Unit
                LogContext.ICON -> out[key] = PICTURE
                LogContext.IMAGE -> out[key] = PICTURE + text(value, salt)
                LogContext.ACTIONS -> out[key] = actions(value, salt)
                else -> out[key] = text(value, salt)
            }
        }
        out[KEY] = seed.toString()
        return out
    }

    /** A script's buttons ([LogContext.ACTIONS]) with each label and its arguments scrambled. */
    private fun actions(raw: String, salt: Long): String {
        val list = runCatching { JSONArray(raw) }.getOrNull() ?: return text(raw, salt)
        val out = JSONArray()
        for (i in 0 until list.length()) {
            val a = list.optJSONArray(i) ?: continue
            out.put(JSONArray().apply {
                put(text(a.optString(0), salt + 2 * i))
                put(text(a.optString(1), salt + 2 * i + 1))
            })
        }
        return out.toString()
    }

    /**
     * The grapheme cluster at [start] (what a reader sees as one character), scrambled into [out].
     * Returns where the next one starts. An emoji becomes one emoji; any other cluster is scrambled
     * code point by code point, so its marks and joiners stay with the letter before them.
     */
    private fun cluster(text: String, start: Int, out: StringBuilder, salt: Long): Int {
        var end = CellWidth.clusterEnd(text, start).coerceIn(start + 1, text.length)
        // A mark or a line break always starts a cluster of its own.
        var j = start + Character.charCount(text.codePointAt(start))
        while (j < end) {
            val c = text.codePointAt(j)
            if (c == '\n'.code || c in StyledText.BASE..StyledText.LAST) {
                end = j
                break
            }
            j += Character.charCount(c)
        }
        if (isEmoji(text, start, end)) {
            out.append(EMOJI[pick(salt, start, EMOJI.size)])
            return end
        }
        val base = text.codePointAt(start)
        var k = start
        while (k < end) {
            val cp = text.codePointAt(k)
            replace(cp, base, k == start, salt, k, out)
            k += Character.charCount(cp)
        }
        return end
    }

    /**
     * One code point of a cluster that starts with [base], scrambled into [out]: letters, digits and
     * marks replaced, anything else kept. [first]: it is the cluster's first.
     */
    private fun replace(cp: Int, base: Int, first: Boolean, salt: Long, at: Int, out: StringBuilder) {
        val type = Character.getType(cp).toByte()
        // A letter joined to the one before it (Thai ำ, a half-width sound mark) takes no cell of its own: it goes as a mark does.
        if (!first && Character.isLetter(cp) && CellWidth.ofCodePoint(cp) == 1) {
            mark(cp, spacing = false, base, first, salt, at, out)
            return
        }
        when (type) {
            Character.UPPERCASE_LETTER, Character.TITLECASE_LETTER -> letter(cp, upper = true, salt, at, out)
            Character.LOWERCASE_LETTER, Character.MODIFIER_LETTER, Character.OTHER_LETTER ->
                letter(cp, upper = false, salt, at, out)

            Character.NON_SPACING_MARK -> mark(cp, spacing = false, base, first, salt, at, out)
            Character.COMBINING_SPACING_MARK -> mark(cp, spacing = true, base, first, salt, at, out)

            Character.DECIMAL_DIGIT_NUMBER -> {
                // A digit of its own script: Arabic-Indic digits stay Arabic-Indic, full-width ones full-width.
                val value = Character.digit(cp, 10)
                val zero = if (value in 0..9) cp - value else '0'.code
                out.appendCodePoint(zero + pick(salt, at, 10))
            }

            Character.LETTER_NUMBER, Character.OTHER_NUMBER -> {
                val zero = if (CellWidth.ofCodePoint(cp) == 2) FULL_ZERO else '0'.code
                out.appendCodePoint(zero + pick(salt, at, 10))
            }

            else -> out.appendCodePoint(cp)
        }
    }

    private fun letter(cp: Int, upper: Boolean, salt: Long, at: Int, out: StringBuilder) {
        val width = CellWidth.ofCodePoint(cp)
        // A letter that takes no cell (a Hangul vowel or final consonant) goes with the one before it.
        if (width == 0) return
        val pool = poolOf(cp, upper, width == 2)
        var r = from(pool, pick(salt, at, size(pool)))
        if (CellWidth.ofCodePoint(r) != width) {
            val fallback = if (width == 2) HAN else if (upper) UPPER else LOWER
            r = from(fallback, pick(salt, at, size(fallback)))
        }
        out.appendCodePoint(r)
    }

    /**
     * A mark ([spacing] or not) in a cluster that starts with [base], scrambled into [out]: another
     * mark of its kind from its script, or from [base]'s when the mark belongs to any script (an
     * accent, an Arabic vowel). A script with no marks here loses it: a mark alone that takes a
     * cell becomes a letter, one alone that takes none an accent, and one on a letter goes.
     */
    private fun mark(cp: Int, spacing: Boolean, base: Int, first: Boolean, salt: Long, at: Int, out: StringBuilder) {
        // Variation selectors and the grapheme joiner say nothing of the text: they stay.
        if (cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF || cp in 0x180B..0x180D || cp == 0x34F) {
            out.appendCodePoint(cp)
            return
        }
        val pool = (if (spacing) SPACING else NON_SPACING)[scriptOf(cp, base)]
        when {
            pool != null -> out.appendCodePoint(from(pool, pick(salt, at, size(pool))))
            !first -> Unit
            spacing -> letter(cp, upper = false, salt, at, out)
            else -> out.appendCodePoint(from(ACCENTS, pick(salt, at, size(ACCENTS))))
        }
    }

    /** A number from [salt], the place [at] and [seed], from 0 to [n] - 1. */
    private fun pick(salt: Long, at: Int, n: Int): Int {
        var z = seed xor (salt * GOLDEN) xor ((at + 1L) * STEP)
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        z = z xor (z ushr 31)
        return ((z ushr 1) % n).toInt()
    }

    companion object {
        /** The context key of a scrambled line: the seed that made it. */
        const val KEY = "scramble"

        /**
         * What a picture's address becomes ([LogContext.IMAGE], [LogContext.ICON]): a plain block
         * shows in its place, and nothing is fetched. After it comes the scrambled address, whose
         * last part the grid shows as the picture's name.
         */
        const val PICTURE = "scramble:/"

        /** True for an address [PICTURE] made. */
        fun isPicture(address: String): Boolean = address.startsWith(PICTURE)

        /** The seed that scrambled [e], or null for a line as it is. */
        fun of(e: LogEntry): Scramble? = e.ctx.values[KEY]?.toLongOrNull()?.let(::Scramble)

        /** The salt of [field] of line [id]. */
        fun salt(id: Long, field: Long): Long = id * 1_000_003L + field

        /** The fields of a line, as salts. */
        const val TEXT = 1L
        const val SOURCE = 2L
        const val TIME = 3L
        const val APP = 4L
        const val FOUND = 5L
        const val COUNT = 7L
        const val DAY = 8L

        /** Context values the log reads to lay a line out, and never shows as text. */
        val KEPT: Set<String> = setOf(
            LogContext.APPS,
            LogContext.BAR,
            LogContext.ACTION_DONE,
            LogContext.MONO,
            LogContext.TITLE,
            LogContext.GAP,
            LogContext.APART,
            LogContext.FROM,
            LogContext.TODO_DONE,
            LogContext.USER,
            KEY,
        )

        private const val NONE = 0
        private const val ADDRESS = 1
        private const val NUMBERS = 2

        // SplitMix64's constants.
        private const val GOLDEN = -7046029254386353131L
        private const val STEP = -4417276706812531889L
        private const val MIX1 = -4658895280553007687L
        private const val MIX2 = -7723592293110705685L

        private const val FULL_ZERO = 0xFF10

        /** Emoji that every Android since 9 draws in color. */
        private val EMOJI = listOf(
            "🌀", "🌂", "🌟", "🌱", "🌻", "🍀", "🍎", "🍋", "🍩", "🎈", "🎁", "🎲", "🎵", "🎧", "👾",
            "💡", "📦", "📌", "📎", "🔔", "🔑", "🔷", "🔶", "😀", "😎", "🙂", "🚀", "🚲",
        )

        // Letters to pick from, as ranges of code points: first, last, first, last…
        private val UPPER = intArrayOf('A'.code, 'Z'.code)
        private val LOWER = intArrayOf('a'.code, 'z'.code)
        private val FULL_UPPER = intArrayOf(0xFF21, 0xFF3A)
        private val FULL_LOWER = intArrayOf(0xFF41, 0xFF5A)
        private val GREEK_UPPER = intArrayOf(0x391, 0x3A1, 0x3A3, 0x3A9)
        private val GREEK_LOWER = intArrayOf(0x3B1, 0x3C9)
        private val CYRILLIC_UPPER = intArrayOf(0x410, 0x42F)
        private val CYRILLIC_LOWER = intArrayOf(0x430, 0x44F)
        private val HEBREW = intArrayOf(0x5D0, 0x5EA)
        private val ARABIC = intArrayOf(0x628, 0x63A, 0x641, 0x64A)
        private val DEVANAGARI = intArrayOf(0x915, 0x939)
        private val BENGALI = intArrayOf(0x995, 0x9A8)
        private val THAI = intArrayOf(0xE01, 0xE2E)
        private val HANGUL = intArrayOf(0xAC00, 0xD7A3)
        private val HIRAGANA = intArrayOf(0x3042, 0x3093)
        private val KATAKANA = intArrayOf(0x30A2, 0x30F3)
        private val HALF_KATAKANA = intArrayOf(0xFF66, 0xFF9D)
        private val HAN = intArrayOf(0x4E00, 0x9FA5)
        private val GEORGIAN_UPPER = intArrayOf(0x10A0, 0x10C5)
        private val GEORGIAN_LOWER = intArrayOf(0x10D0, 0x10FA)
        private val ARMENIAN_UPPER = intArrayOf(0x531, 0x556)
        private val ARMENIAN_LOWER = intArrayOf(0x561, 0x586)

        /** The letters of more scripts: their consonants, as the platform types them. */
        private val LETTERS: Map<Character.UnicodeScript, IntArray> = mapOf(
            Character.UnicodeScript.GURMUKHI to intArrayOf(0xA15, 0xA39),
            Character.UnicodeScript.GUJARATI to intArrayOf(0xA95, 0xAB9),
            Character.UnicodeScript.ORIYA to intArrayOf(0xB15, 0xB39),
            Character.UnicodeScript.TAMIL to intArrayOf(0xB95, 0xBB9),
            Character.UnicodeScript.TELUGU to intArrayOf(0xC15, 0xC39),
            Character.UnicodeScript.KANNADA to intArrayOf(0xC95, 0xCB9),
            Character.UnicodeScript.MALAYALAM to intArrayOf(0xD15, 0xD39),
            Character.UnicodeScript.SINHALA to intArrayOf(0xD9A, 0xDC6),
            Character.UnicodeScript.LAO to intArrayOf(0xE81, 0xEAE),
            Character.UnicodeScript.MYANMAR to intArrayOf(0x1000, 0x1020),
            Character.UnicodeScript.KHMER to intArrayOf(0x1780, 0x17A2),
        ).mapValues { only(Character.OTHER_LETTER, it.value) }.filterValues { it.isNotEmpty() }

        /**
         * Where each script's marks are: vowel signs, points and the signs after a syllable. Each
         * pool below keeps those of one kind, as the platform types them.
         */
        private val SIGNS: Map<Character.UnicodeScript, IntArray> = mapOf(
            Character.UnicodeScript.HEBREW to intArrayOf(0x5B0, 0x5BC, 0x5C1, 0x5C2),
            Character.UnicodeScript.ARABIC to intArrayOf(0x64B, 0x652),
            Character.UnicodeScript.DEVANAGARI to intArrayOf(0x901, 0x903, 0x93E, 0x94D),
            Character.UnicodeScript.BENGALI to intArrayOf(0x981, 0x983, 0x9BE, 0x9CD),
            Character.UnicodeScript.GURMUKHI to intArrayOf(0xA01, 0xA03, 0xA3E, 0xA4D, 0xA70, 0xA71),
            Character.UnicodeScript.GUJARATI to intArrayOf(0xA81, 0xA83, 0xABE, 0xACD),
            Character.UnicodeScript.ORIYA to intArrayOf(0xB01, 0xB03, 0xB3E, 0xB4D),
            Character.UnicodeScript.TAMIL to intArrayOf(0xB82, 0xB82, 0xBBE, 0xBCD),
            Character.UnicodeScript.TELUGU to intArrayOf(0xC01, 0xC03, 0xC3E, 0xC4D),
            Character.UnicodeScript.KANNADA to intArrayOf(0xC82, 0xC83, 0xCBE, 0xCCD),
            Character.UnicodeScript.MALAYALAM to intArrayOf(0xD02, 0xD03, 0xD3E, 0xD4D),
            Character.UnicodeScript.SINHALA to intArrayOf(0xD82, 0xD83, 0xDCA, 0xDDF),
            Character.UnicodeScript.THAI to intArrayOf(0xE31, 0xE4E),
            Character.UnicodeScript.LAO to intArrayOf(0xEB1, 0xECD),
            Character.UnicodeScript.MYANMAR to intArrayOf(0x102B, 0x103E),
            Character.UnicodeScript.KHMER to intArrayOf(0x17B6, 0x17D2),
        )

        /** Accents that go on any letter: grave, acute, circumflex, tilde, … and those below. */
        private val ACCENTS = intArrayOf(0x300, 0x30C, 0x323, 0x328)

        /** Kana's sound marks. */
        private val KANA_MARKS = intArrayOf(0x3099, 0x309A)

        /** The marks that take no cell a mark of a script is replaced by. */
        private val NON_SPACING: Map<Character.UnicodeScript, IntArray> =
            SIGNS.mapValues { only(Character.NON_SPACING_MARK, it.value) }.filterValues { it.isNotEmpty() } + mapOf(
                Character.UnicodeScript.LATIN to ACCENTS,
                Character.UnicodeScript.GREEK to ACCENTS,
                Character.UnicodeScript.CYRILLIC to ACCENTS,
                Character.UnicodeScript.INHERITED to ACCENTS,
                Character.UnicodeScript.HIRAGANA to KANA_MARKS,
                Character.UnicodeScript.KATAKANA to KANA_MARKS,
            )

        /** The spacing marks (vowel signs beside the letter) a spacing mark of a script is replaced by. */
        private val SPACING: Map<Character.UnicodeScript, IntArray> =
            SIGNS.mapValues { only(Character.COMBINING_SPACING_MARK, it.value) }.filterValues { it.isNotEmpty() }

        /** The code points of [ranges] (first, last, first, last…) that the platform gives [type], as ranges. */
        private fun only(type: Byte, ranges: IntArray): IntArray {
            val out = ArrayList<Int>()
            for (r in ranges.indices step 2) for (cp in ranges[r]..ranges[r + 1]) {
                if (Character.getType(cp).toByte() != type) continue
                if (out.isNotEmpty() && out[out.size - 1] == cp - 1) out[out.size - 1] = cp
                else {
                    out += cp
                    out += cp
                }
            }
            return out.toIntArray()
        }

        /** The script whose marks replace mark [cp]: its own, or [base]'s for a mark of any script. */
        private fun scriptOf(cp: Int, base: Int): Character.UnicodeScript {
            val own = Character.UnicodeScript.of(cp)
            if (own != Character.UnicodeScript.COMMON && own != Character.UnicodeScript.INHERITED) return own
            val of = Character.UnicodeScript.of(base)
            return if (of != Character.UnicodeScript.COMMON && of != Character.UnicodeScript.INHERITED) of
            else Character.UnicodeScript.INHERITED
        }

        /** The letters a letter of [cp]'s script, case and width is replaced by. Other scripts take Latin letters, or Han ones when wide. */
        private fun poolOf(cp: Int, upper: Boolean, wide: Boolean): IntArray =
            when (val script = Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.LATIN -> when {
                    wide -> if (upper) FULL_UPPER else FULL_LOWER
                    else -> if (upper) UPPER else LOWER
                }

                Character.UnicodeScript.GREEK -> if (upper) GREEK_UPPER else GREEK_LOWER
                Character.UnicodeScript.CYRILLIC -> if (upper) CYRILLIC_UPPER else CYRILLIC_LOWER
                Character.UnicodeScript.HEBREW -> HEBREW
                Character.UnicodeScript.ARABIC -> ARABIC
                Character.UnicodeScript.DEVANAGARI -> DEVANAGARI
                Character.UnicodeScript.BENGALI -> BENGALI
                Character.UnicodeScript.THAI -> THAI
                Character.UnicodeScript.HANGUL -> HANGUL
                Character.UnicodeScript.HIRAGANA -> HIRAGANA
                Character.UnicodeScript.KATAKANA -> if (wide) KATAKANA else HALF_KATAKANA
                Character.UnicodeScript.HAN -> HAN
                Character.UnicodeScript.GEORGIAN -> if (upper) GEORGIAN_UPPER else GEORGIAN_LOWER
                Character.UnicodeScript.ARMENIAN -> if (upper) ARMENIAN_UPPER else ARMENIAN_LOWER
                else -> LETTERS[script]?.takeIf { !wide } ?: if (wide) HAN else if (upper) UPPER else LOWER
            }

        private fun size(pool: IntArray): Int {
            var n = 0
            for (r in pool.indices step 2) n += pool[r + 1] - pool[r] + 1
            return n
        }

        private fun from(pool: IntArray, index: Int): Int {
            var i = index
            for (r in pool.indices step 2) {
                val n = pool[r + 1] - pool[r] + 1
                if (i < n) return pool[r] + i
                i -= n
            }
            return pool[0]
        }

        /**
         * Whether the cluster from [start] to [end] shows as an emoji: two cells wide, and a
         * pictograph, a flag, or a character with the emoji variation selector (U+FE0F) after it.
         */
        private fun isEmoji(text: String, start: Int, end: Int): Boolean {
            if (CellWidth.width(text, start, end) != 2) return false
            val base = text.codePointAt(start)
            if (base in 0x1F000..0x1FAFF || base in 0x2000..0x2BFF || base == 0xA9 || base == 0xAE) return true
            return text.indexOf('️', start).let { it in start until end }
        }
    }
}

/**
 * Home's rows as `scramble` shows them: each line scrambled ([Scramble.entry]), a day's divider
 * told to scramble its date, and the unread count another number. A line is scrambled once and
 * kept while it stays among the rows, so a new line costs one line's work. It remembers each
 * shown line's real one ([real]), for what a tap or a swipe does with it. Main thread only.
 */
class ScrambledLog(val scramble: Scramble) {
    private class Kept(val real: LogEntry, val shown: LogEntry)

    /** The log's lines, and a search's, by id. */
    private var log = HashMap<Long, Kept>()
    private var found = HashMap<Long, Kept>()

    /** [rows] scrambled. [search]: they are a search's results, kept apart from the log's. */
    fun rows(rows: List<LogRow>, search: Boolean = false): List<LogRow> {
        val before = if (search) found else log
        val now = HashMap<Long, Kept>(rows.size * 2)
        val out = rows.map { row ->
            when (row) {
                is LogRow.Entry -> {
                    val e = row.entry
                    val had = before[e.id]?.takeIf { it.real === e || it.real == e }
                    val kept = had ?: Kept(e, scramble.entry(e))
                    now[e.id] = kept
                    row.copy(entry = kept.shown)
                }

                is LogRow.Day -> row.copy(scramble = scramble)
                is LogRow.Unread -> LogRow.Unread(scramble.count(row.count))
            }
        }
        if (search) found = now else log = now
        return out
    }

    /**
     * The real line of [shown], a line of these rows. Null when it isn't among them any more:
     * then nothing should be done with it, as a scrambled copy must never be stored or run.
     */
    fun real(shown: LogEntry): LogEntry? {
        if (Scramble.of(shown) == null) return shown
        return (log[shown.id] ?: found[shown.id])?.takeIf { it.shown === shown || it.shown == shown }?.real
    }
}
