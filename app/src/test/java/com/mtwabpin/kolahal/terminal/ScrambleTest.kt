package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.LogCorpus.row
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import com.mtwabpin.kolahal.terminal.tty.LineLayout
import com.mtwabpin.kolahal.terminal.tty.LogLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** `scramble`'s text ([Scramble]): the shape of what home shows stays, and nothing of what it says. */
class ScrambleTest {
    private val scramble = Scramble(42L)

    /** Texts whose letters carry marks: vowel signs, points, harakat and accents written apart. */
    private val marked = listOf(
        "नमस्ते दुनिया, कैसे हैं?",
        "আমার সোনার বাংলা",
        "வணக்கம் தமிழ் நாடு",
        "สวัสดีครับ น้ำ ไม่",
        "שָׁלוֹם עוֹלָם",
        "مَرْحَبًا بِالْعَالَمِ",
        "Cafe\u0301 nai\u0308ve Ångstro\u0308m",
        "నమస్కారం ಕನ್ನಡ മലയാളം ਪੰਜਾਬੀ ગુજરાતી ଓଡ଼ିଆ සිංහල សួស្តី",
        "Բարեւ გამარჯობა",
    )

    /** Texts in many scripts, with digits, punctuation, styles and emoji. */
    private val samples = listOf(
        "Ada Lovelace called +1 555 0100 at 14:02.",
        "Missed call · 2 min ago\nOpen WhatsApp (3 new)",
        "Привет, мир! Встреча в 18:30",
        "Καλημέρα Ελλάδα",
        "مرحبا بالعالم ١٢٣",
        "שלום עולם",
        "नमस्ते दुनिया",
        "こんにちは 世界 カタカナ",
        "안녕하세요 세계",
        "ｆｕｌｌ ＷＩＤＴＨ １２３",
        "Party 🎉 tonight 👨‍👩‍👧 🇩🇪 1️⃣ ❤️",
        "Café naïve résumé",
    ) + marked

    private enum class Kind { UPPER, LOWER, LETTER, DIGIT, EMOJI, OTHER }

    /** What kind a cluster is, as the scrambler sees it. */
    private fun kindOf(cluster: String): Kind {
        val cp = cluster.codePointAt(0)
        if (CellWidth.of(cluster) == 2 && (cp >= 0x1F000 || cluster.contains('️') || cp in 0x2000..0x2BFF)) return Kind.EMOJI
        return when (Character.getType(cp).toByte()) {
            Character.UPPERCASE_LETTER, Character.TITLECASE_LETTER -> Kind.UPPER
            Character.LOWERCASE_LETTER -> Kind.LOWER
            Character.OTHER_LETTER, Character.MODIFIER_LETTER -> Kind.LETTER
            Character.DECIMAL_DIGIT_NUMBER, Character.OTHER_NUMBER, Character.LETTER_NUMBER -> Kind.DIGIT
            else -> Kind.OTHER
        }
    }

    /** Each line keeps its width, each character its kind and width, and anything not a letter, digit or emoji stays. */
    @Test
    fun theShapeStays() {
        for (sample in samples) for (seed in 1L..50L) {
            val out = Scramble(seed).text(sample, seed * 7)
            val inLines = sample.split('\n')
            val outLines = out.split('\n')
            assertEquals(sample, inLines.size, outLines.size)
            for ((a, b) in inLines.zip(outLines)) {
                assertEquals("$a → $b", CellWidth.total(a), CellWidth.total(b))
                val ca = CellWidth.clusters(a)
                val cb = CellWidth.clusters(b)
                assertEquals("$a → $b", ca.size, cb.size)
                for ((x, y) in ca.zip(cb)) {
                    assertEquals("$x → $y in $a", kindOf(x), kindOf(y))
                    assertEquals("$x → $y in $a", CellWidth.of(x), CellWidth.of(y))
                    // A mark is replaced, also after a sign; the sign itself stays.
                    if (kindOf(x) == Kind.OTHER && !isMark(x.codePointAt(0))) {
                        assertEquals("$a → $b", x.codePointAt(0), y.codePointAt(0))
                    }
                }
            }
        }
    }

    private fun isMark(cp: Int) = Character.getType(cp).toByte().let {
        it == Character.NON_SPACING_MARK || it == Character.COMBINING_SPACING_MARK || it == Character.ENCLOSING_MARK
    }

    /** The letters each character starts with (a letter joined to one before it, as Thai ำ, is a mark). */
    private fun letters(text: String) =
        CellWidth.clusters(text).map { it.codePointAt(0) }.filter { Character.isLetter(it) && CellWidth.ofCodePoint(it) > 0 }

    /** A letter of a script stays a letter of that script, so the line reads in the same direction and joins as it did. */
    @Test
    fun scriptsStay() {
        for (sample in samples) {
            val out = scramble.text(sample)
            val a = letters(sample)
            val b = letters(out)
            assertEquals(sample, a.size, b.size)
            for ((x, y) in a.zip(b)) assertEquals(
                "${String(Character.toChars(x))} → ${String(Character.toChars(y))}",
                Character.UnicodeScript.of(x),
                Character.UnicodeScript.of(y),
            )
        }
    }

    /**
     * Over many seeds, a letter or a digit is the same as before at its place about as often as
     * chance gives (1 in 26 for a Latin letter, 1 in 10 for a digit), never more.
     */
    @Test
    fun noLetterOrDigitSurvivesMoreThanChance() {
        val text = "Ada Lovelace +1 555 0100 Mon 14:02 THE QUICK BROWN FOX jumps over 9876543210"
        val seeds = 3_000
        var letters = 0
        var lettersSame = 0
        var digits = 0
        var digitsSame = 0
        for (seed in 1..seeds) {
            val out = Scramble(seed * 7_919L).text(text, salt = 5)
            assertEquals(text.length, out.length)
            for (i in text.indices) {
                val c = text[i]
                if (c.isLetter()) {
                    letters++
                    if (out[i] == c) lettersSame++
                } else if (c.isDigit()) {
                    digits++
                    if (out[i] == c) digitsSame++
                }
            }
        }
        val letterRate = lettersSame.toDouble() / letters
        val digitRate = digitsSame.toDouble() / digits
        assertTrue("letters kept $letterRate of the time", letterRate < 1.25 / 26)
        assertTrue("digits kept $digitRate of the time", digitRate < 1.25 / 10)
        // And it is chance, not a rule that leaves out the real one (which would tell what it isn't).
        assertTrue("letters kept $letterRate of the time", letterRate > 0.75 / 26)
        assertTrue("digits kept $digitRate of the time", digitRate > 0.75 / 10)
    }

    /**
     * In scripts whose letters carry marks, no letter and no mark is the same as before at its place
     * more often than chance (one in the number of characters that turn up there), and each changes.
     */
    @Test
    fun noLetterOrMarkSurvivesMoreThanChance() {
        val seeds = 2_000
        for (text in marked) {
            val shown = HashMap<Int, HashSet<Char>>()
            val starts = HashSet<Int>()
            var at = 0
            while (at < text.length) {
                starts += at
                at = CellWidth.clusterEnd(text, at)
            }
            val same = IntArray(text.length)
            for (seed in 1..seeds) {
                val out = Scramble(seed * 7_919L).text(text, salt = 5)
                // Every script here has its letters and marks: nothing is dropped, so places line up.
                assertEquals(text, text.length, out.length)
                for (i in text.indices) {
                    val c = text[i]
                    if (!c.isLetter() && !isMark(c.code)) continue
                    shown.getOrPut(i) { HashSet() }.add(out[i])
                    if (out[i] == c) same[i]++
                }
            }
            for ((i, seen) in shown) {
                val c = text[i]
                assertTrue("'$c' at $i in $text is always '${seen.first()}'", seen.size >= 2)
                assertTrue("'$c' at $i in $text kept ${same[i]} times of $seeds, ${seen.size} kinds", same[i] <= 2.0 * seeds / seen.size)
                // A mark stays a mark of its kind, and a letter joined to the one before it (Thai ำ) becomes one.
                for (o in seen) when {
                    isMark(c.code) -> assertEquals("'$c' → '$o' in $text", Character.getType(c), Character.getType(o))
                    i !in starts -> assertTrue("'$c' → '$o' in $text", isMark(o.code))
                    else -> assertTrue("'$c' → '$o' in $text", o.isLetter())
                }
            }
        }
    }

    /** A script with no letters here (Tibetan, Ethiopic): its letters become Latin ones and its marks go, so none of it is left, and the cells stay. */
    @Test
    fun aScriptWithoutLettersHereKeepsNothing() {
        for (text in listOf("བཀྲ་ཤིས་བདེ་ལེགས", "ሰላም ልዑል")) for (seed in 1L..50L) {
            val out = Scramble(seed).text(text, seed)
            assertEquals("$text → $out", CellWidth.total(text), CellWidth.total(out))
            assertEquals("$text → $out", CellWidth.clusters(text).size, CellWidth.clusters(out).size)
            val script = Character.UnicodeScript.of(text.codePointAt(0))
            for (cp in out.codePoints().toArray()) {
                if (Character.isLetter(cp) || isMark(cp)) assertNotEquals("$text → $out", script, Character.UnicodeScript.of(cp))
            }
        }
    }

    /** The text that takes a character's place doesn't come from it: two texts of one shape scramble the same way. */
    @Test
    fun theReplacementComesFromTheShapeNotTheText() {
        assertEquals(scramble.text("Alice Smith, 0612 345 678", 9), scramble.text("Bobby Jones, 0798 111 222", 9))
        assertEquals(scramble.text("Встреча в 18:30", 9), scramble.text("Привета я 07:15", 9))
    }

    /** The same text with the same salt gives the same text each time, so a line doesn't flicker. Another seed gives another. */
    @Test
    fun aSessionIsStableAndAnotherSeedIsNot() {
        for (sample in samples) {
            assertEquals(scramble.text(sample, 3), scramble.text(sample, 3))
            assertNotEquals(sample, scramble.text(sample, 3), Scramble(43L).text(sample, 3))
        }
        val e = LogCorpus.heading.entry
        assertEquals(scramble.entry(e), scramble.entry(e))
        assertNotEquals(scramble.entry(e).text, Scramble(43L).entry(e).text)
    }

    /** Styles, colors, links, tables, bars and charts keep their marks, places and addresses; only their words change. */
    @Test
    fun marksStay() {
        for (line in LogCorpus.all) {
            for (text in listOfNotNull(line.entry.text, line.entry.ctx.body, line.entry.ctx.values[LogContext.DESC])) {
                if (!StyledText.has(text)) continue
                val a = StyledText.lines(text)
                val b = StyledText.lines(scramble.text(text))
                assertEquals(line.name, a.size, b.size)
                for ((x, y) in a.zip(b)) {
                    assertEquals(line.name, x.level, y.level)
                    assertEquals(line.name, x.spans.map { Triple(it.styles, it.color, it.url) }, y.spans.map { Triple(it.styles, it.color, it.url) })
                    assertEquals(line.name, x.spans.map { CellWidth.total(it.text) }, y.spans.map { CellWidth.total(it.text) })
                    assertEquals(line.name, x.row?.cells?.size, y.row?.cells?.size)
                    assertEquals(line.name, x.meter?.let { it.from to it.to }, y.meter?.let { it.from to it.to })
                    assertEquals(line.name, x.columns?.heights, y.columns?.heights)
                }
            }
        }
    }

    /** Words of 5 letters or more that a line shows: its text, source, columns, body and buttons. */
    private fun wordsOf(e: LogEntry): Set<String> {
        val shown = listOfNotNull(e.text, e.source, e.ctx.body, e.ctx.values[LogContext.DESC], e.ctx.values[LogContext.ARGS]) +
            LogContext.actionsOf(e.ctx).map { it.first }
        return shown.flatMap { Regex("\\p{L}{5,}").findAll(StyledText.plain(it)).map { m -> m.value.lowercase() }.toList() }.toSet()
    }

    /** Every kind of line keeps its layout on the grid, row for row and cell for cell, and shows none of its words. */
    @Test
    fun everyLineKeepsItsLayoutAndLosesItsWords() {
        val options = LineOptions(TimeMode.OFF, foldLines = 8)
        for (line in LogCorpus.all) {
            val real = lineModel(row(line), options)
            val shown = lineModel(row(LogCorpus.Line(line.name, scramble.entry(line.entry))), options)
            assertEquals(line.name, real.body::class, shown.body::class)
            assertEquals(line.name, real.blocks.map { it::class }, shown.blocks.map { it::class })
            assertEquals(line.name, real.buttons.size, shown.buttons.size)
            assertEquals(line.name, real.image != null, shown.image != null)
            assertEquals(line.name, real.tappable, shown.tappable)
            for (open in listOf(false, true)) {
                val a = LineLayout.layout(real, LogLook(40), "14:02", foldOpen = open)
                val b = LineLayout.layout(shown, LogLook(40), "14:02", foldOpen = open)
                assertEquals(line.name, a.rows, b.rows)
                for (r in 0 until a.rows) {
                    assertEquals("${line.name}: ${a.grid.rowText(r)} → ${b.grid.rowText(r)}", CellWidth.total(a.grid.rowText(r)), CellWidth.total(b.grid.rowText(r)))
                }
                val text = b.dump().lowercase()
                for (word in wordsOf(line.entry)) assertFalse("${line.name}: '$word' in\n$text", word in text)
            }
        }
    }

    /** Pictures don't show: an image's address and a notification's icon become a plain block's, and a contact's photo goes. */
    @Test
    fun picturesAreReplaced() {
        val picture = scramble.entry(LogCorpus.picture.entry)
        val image = picture.ctx.image!!
        assertTrue(image, Scramble.isPicture(image))
        assertFalse(image, "comic" in image)
        assertEquals(Scramble.PICTURE, scramble.entry(LogCorpus.notification.entry).ctx.values[LogContext.ICON])
        val call = scramble.entry(LogCorpus.call.entry)
        assertNull(call.ctx.values[LogContext.PHOTO])
        assertNotEquals("Ada Lovelace", call.ctx.values[LogContext.PERSON])
        assertFalse(Scramble.isPicture(LogCorpus.picture.entry.ctx.image!!))
    }

    /** A scrambled line is the line in all that isn't shown: its id, time, kind and star, and the values the log lays it out by. */
    @Test
    fun aScrambledLineIsTheSameLine() {
        for (line in LogCorpus.all) {
            val e = line.entry
            val s = scramble.entry(e)
            assertEquals(line.name, listOf(e.id, e.ts, e.kind, e.starred), listOf(s.id, s.ts, s.kind, s.starred))
            assertEquals(line.name, e.ctx.values.keys - LogContext.PHOTO + Scramble.KEY, s.ctx.values.keys)
            for (key in Scramble.KEPT - Scramble.KEY) assertEquals(line.name, e.ctx.values[key], s.ctx.values[key])
            assertEquals(line.name, scramble, Scramble.of(s))
            assertNull(Scramble.of(e))
        }
    }

    /** The rows: lines scrambled and kept while they stay, days told to scramble, the unread count another number of as many digits. */
    @Test
    fun theRowsAreScrambled() {
        val log = ScrambledLog(scramble)
        val real = LogCorpus.row(LogCorpus.output)
        val rows = listOf(LogRow.Day(LocalDate.of(2025, 11, 20)), LogRow.Unread(12), real)
        val shown = log.rows(rows)
        assertEquals(rows.map { it.key }, shown.map { it.key })
        assertEquals(scramble, (shown[0] as LogRow.Day).scramble)
        assertEquals(2, (shown[1] as LogRow.Unread).count.toString().length)
        val line = (shown[2] as LogRow.Entry).entry
        assertNotEquals(real.entry.text, line.text)
        // The same line is scrambled once and kept.
        assertSame(line, (log.rows(rows)[2] as LogRow.Entry).entry)
        // A tap on it reaches the real line; a line shown as it is stays itself.
        assertSame(real.entry, log.real(line))
        assertSame(real.entry, log.real(real.entry))
        // A copy that is no longer among the rows reaches nothing.
        log.rows(emptyList())
        assertNull(log.real(line))
        // A search's rows are kept apart from the log's.
        log.rows(rows)
        val found = log.rows(listOf(LogCorpus.row(LogCorpus.error)), search = true)
        assertSame(real.entry, log.real(line))
        assertSame(LogCorpus.error.entry, log.real((found[0] as LogRow.Entry).entry))
    }

    /** A script's buttons keep their number and their place, with their labels scrambled. */
    @Test
    fun buttonsAreScrambled() {
        val s = scramble.entry(LogCorpus.buttons.entry)
        val labels = LogContext.actionsOf(s.ctx).map { it.first }
        assertEquals(2, labels.size)
        assertEquals(listOf(13, 4), labels.map { it.length })
        assertFalse(labels.toString(), "Snooze 30 min" in labels || "Done" in labels)
    }

    /** A plain line of the log scrambles as it is: kind and id stay, the words go. */
    @Test
    fun aNewLineIsScrambled() {
        val e = LogEntry(7, 0L, LogKind.SCRIPT, "WhatsApp", "Ada: see you at 8", LogContext.EMPTY)
        val s = scramble.entry(e)
        for ((a, b) in e.text.split(' ').zip(s.text.split(' '))) if (a.length >= 3) assertNotEquals(s.text, a, b)
        assertFalse(s.source!!, "WhatsApp" == s.source)
        assertEquals(e.text.length, s.text.length)
    }
}
