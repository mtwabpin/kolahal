package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A log message says what happened on its first line, and puts each hint on a line of its own
 * under it (`\n`), not after a ` · ` on the same line. This test reads the sources of every area
 * that writes to the log, finds the texts handed to the log (the string arguments of `info(`,
 * `error(`, `fix(`, `out(`, `notice(`, `logInfo(`, and of `add(` and `log(` with a `LogKind`), the
 * `const val` texts beside them, and the files that hold only texts for the log, and fails on one
 * that joins two sentences with ` · `.
 *
 * The rule: a ` · ` fails when a side of it, up to the next dot, line break or end of the string,
 * reads as a sentence: four or more words with letters in them, or a hint that starts with tap,
 * long-press or a quoted command.
 *
 * So the dot stays between values: `Android 16 · API 36`, `▼ 12% · last 7 days`, a status row
 * whose parts are short, and a bare `" · "` that joins values worked out elsewhere
 * (`joinToString(" · ")`), which has no side in its string. A `${…}` counts as no word.
 */
class LogMessageFormTest {
    private val base = "src/main/java/com/mtwabpin/kolahal"

    /** Folders read without their subfolders: the terminal's own files, and the command groups. */
    private val flat = listOf("terminal", "terminal/commands")

    /** Folders read whole. */
    private val deep = listOf(
        "shader", "script",
        "feeds", "productivity", "calls", "usage", "apps", "contacts", "control", "notifications",
    )

    /** Single files, the texts of a backup and a crash among them. */
    private val single = listOf(
        "data/AutoBackup.kt", "data/Backup.kt", "data/FullBackup.kt", "data/HomeScramble.kt", "data/Themes.kt",
        "CrashReports.kt",
    )

    /** Files whose every string is a text for the log. */
    private val textsOnly = listOf("commands/SkillText.kt", "terminal/Tips.kt")

    private fun file(path: String) = sourceFile("$base/$path")

    private val sources: List<File> by lazy {
        val kotlin = { f: File -> f.isFile && f.extension == "kt" }
        (flat.flatMap { file(it).listFiles().orEmpty().filter(kotlin) } +
                deep.flatMap { file(it).walkTopDown().filter(kotlin).toList() } +
                single.map(::file)).distinct()
    }

    /** A text handed to the log, where it is: `File.kt:12`. */
    private data class Found(val where: String, val text: String)

    private val texts: List<Found> by lazy {
        sources.flatMap { f -> logTexts(f.name, f.readText()) } +
                textsOnly.map(::file).flatMap { f ->
                    val src = f.readText()
                    Lexer(src).run().literals.map { Found("${f.name}:${line(src, it.start)}", it.text) }
                }
    }

    @Test
    fun theSourcesAreRead() {
        assertTrue(sources.size.toString(), sources.size > 60)
        assertTrue(texts.size.toString(), texts.size > 400)
        val all = texts.map { it.text }
        // A call's text, with what a template fills in as §.
        assertTrue("Command not found: §\n'help' lists the commands" in all)
        // A constant's.
        assertTrue(com.mtwabpin.kolahal.terminal.commands.AllowCommands.NO_LOCK in all)
        // One from a file of texts.
        assertTrue(all.any { it.startsWith("Copied §") })
    }

    @Test
    fun theRuleTellsSentencesFromValues() {
        // Values, and a dot that joins values worked out elsewhere.
        for (ok in listOf(
            "Android § · API §", "▼ §% · last 7 days", "§% of § · on pace for § · § § left", " · ",
            "§ · in §", "Installed § from § · every 15 min", "Saved pond to shaders/pond.agsl\nEdit it there",
        )) assertFalse(ok, joinsSentences(ok))
        // Sentences, and hints.
        for (bad in listOf(
            "Scramble on: home shows no real text · 'scramble off' shows it again",
            "Nothing open · § done ('todo all' shows them, 'todo clear' removes them)",
            "Notification access is off · tap to turn it on",
            "§ isn't hidden · 'unhide §'",
            " · the one that comes with Kolahal is used again",
            "Tap to mute · volume <0-100>",
            " a line to use it · ",
        )) assertTrue(bad, joinsSentences(bad))
    }

    @Test
    fun noLogTextJoinsTwoSentencesWithADot() {
        val found = texts.filter { joinsSentences(it.text) }.map { "${it.where}: ${it.text.replace("\n", "⏎")}" }
        assertEquals(
            "Put each sentence on a line of its own (\\n): the first says what happened, each hint goes under it",
            emptyList<String>(),
            found,
        )
    }

    /** Whether [text] joins two sentences with a ` · `, by the rule in the class's description. */
    private fun joinsSentences(text: String): Boolean =
        text.lines().filter { " · " in it }.flatMap { it.split(" · ") }.any(::readsAsSentence)

    private fun readsAsSentence(side: String): Boolean {
        val words = side.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val first = words.firstOrNull()?.lowercase().orEmpty()
        return words.count { w -> w.any(Char::isLetter) } >= 4 ||
                first == "tap" || first.startsWith("long-press") || first.startsWith("'")
    }

    /** The texts in [src] handed to the log: the strings in a log call's arguments, and the `const val` texts. */
    private fun logTexts(name: String, src: String): List<Found> {
        val lexed = Lexer(src).run()
        val code = lexed.code
        val found = ArrayList<Found>()
        for (m in CALL.findAll(code)) {
            if (code.substring(maxOf(0, m.range.first - 4), m.range.first) == "fun ") continue
            val open = m.range.last
            val close = closing(code, open) ?: continue
            if (m.groupValues[1] in NEEDS_KIND && "LogKind." !in code.substring(open, close)) continue
            lexed.literals.filter { it.start in open until close }
                .mapTo(found) { Found("$name:${line(src, it.start)}", it.text) }
        }
        for (m in CONST.findAll(code)) {
            // The constant's strings, joined where they are added up (`"a " + "b"`).
            var at = m.range.last + 1
            val parts = ArrayList<String>()
            for (lit in lexed.literals.filter { it.start >= at }.sortedBy { it.start }) {
                // A string inside a template of the one before: already in its text, as §.
                if (lit.start < at) continue
                val gap = code.substring(at, lit.start)
                if (gap.isNotBlank() && !(parts.isNotEmpty() && gap.trim() == "+")) break
                parts += lit.text
                at = lit.end
            }
            if (parts.isNotEmpty()) found += Found("$name:${line(src, m.range.first)}", parts.joinToString(""))
        }
        return found
    }

    /** The `)` that closes the `(` at [open], in code whose strings and comments are blanked. */
    private fun closing(code: String, open: Int): Int? {
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return i
            }
        }
        return null
    }

    private fun line(src: String, at: Int) = src.substring(0, at).count { it == '\n' } + 1

    /** A string in the source: where it starts and ends, and its text with each `$x` or `${…}` as §. */
    private data class Literal(val start: Int, val end: Int, val text: String)

    private class Lexed(val code: String, val literals: List<Literal>)

    /**
     * Finds the strings of a Kotlin source, also those inside a template's `${…}`, and gives the
     * source back with its strings and comments blanked, so calls and brackets can be matched in it.
     */
    private class Lexer(private val src: String) {
        private val code = StringBuilder(src)
        private val literals = ArrayList<Literal>()

        fun run(): Lexed {
            readCode(0, inTemplate = false)
            return Lexed(code.toString(), literals)
        }

        private fun blank(from: Int, to: Int) {
            for (i in from until minOf(to, code.length)) if (code[i] != '\n') code.setCharAt(i, ' ')
        }

        /** Reads code from [from] to the end, or, in a template, to the `}` that closes it. Returns where it stopped. */
        private fun readCode(from: Int, inTemplate: Boolean): Int {
            var i = from
            var depth = 0
            while (i < src.length) {
                val c = src[i]
                when {
                    src.startsWith("//", i) -> {
                        val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                        blank(i, end)
                        i = end
                    }

                    src.startsWith("/*", i) -> {
                        val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                        blank(i, end)
                        i = end
                    }

                    c == '\'' -> i = charEnd(i)
                    c == '"' -> {
                        val end = string(i)
                        if (!inTemplate) blank(i, end)
                        i = end
                    }

                    c == '{' -> {
                        depth++
                        i++
                    }

                    c == '}' -> {
                        if (inTemplate && depth == 0) return i + 1
                        depth--
                        i++
                    }

                    else -> i++
                }
            }
            return i
        }

        /** The end of the character literal at [start]: `'a'`, `'\n'`, `' '`. */
        private fun charEnd(start: Int): Int {
            var i = start + 1
            i += if (i < src.length && src[i] == '\\') 2 else 1
            while (i < src.length && src[i] != '\'') i++
            return i + 1
        }

        /** Reads the string at [start] (plain or raw) and returns where it ends. */
        private fun string(start: Int): Int {
            val raw = src.startsWith("\"\"\"", start)
            var i = start + if (raw) 3 else 1
            val text = StringBuilder()
            while (i < src.length) {
                val c = src[i]
                if (raw && src.startsWith("\"\"\"", i)) {
                    i += 3
                    break
                }
                if (!raw && c == '"') {
                    i++
                    break
                }
                if (!raw && c == '\\' && i + 1 < src.length) {
                    when (val n = src[i + 1]) {
                        'n' -> text.append('\n')
                        't' -> text.append('\t')
                        'u' -> {
                            text.append(src.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }

                        else -> text.append(n)
                    }
                    i += 2
                    continue
                }
                if (c == '$' && i + 1 < src.length && src[i + 1] == '{') {
                    text.append('§')
                    i = readCode(i + 2, inTemplate = true)
                    continue
                }
                if (c == '$' && i + 1 < src.length && (src[i + 1].isLetter() || src[i + 1] == '_')) {
                    text.append('§')
                    i++
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    continue
                }
                text.append(c)
                i++
            }
            literals += Literal(start, i, text.toString())
            return i
        }
    }

    private companion object {
        /** A call that writes to the log. `add` and `log` count only with a `LogKind` in their arguments. */
        val CALL = Regex("""(?<![\w$])(info|error|fix|out|notice|logInfo|add|log)\s*\(""")
        val NEEDS_KIND = setOf("add", "log")
        val CONST = Regex("""\bconst\s+val\s+\w+\s*(?::\s*String\s*)?=""")
    }
}
