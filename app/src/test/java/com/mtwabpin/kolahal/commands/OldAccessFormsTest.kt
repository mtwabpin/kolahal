package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `allow <word>` took the place of the commands and sub-forms that asked for an access
 * (`usage-access`, `notifs enable`, `calls --allow`, …). No text may tell a person to type one of
 * them again: not the app's code, library scripts and skill.md, nor the guide and the docs.
 * The release notes keep them, as the history they are.
 */
class OldAccessFormsTest {
    /** Each old form, as code builds it or a text names it. */
    private val forms = listOf(
        Regex("""usage-access"""),
        Regex("""notifs enable"""),
        Regex("""\b(calls|agenda|contacts) --(allow|settings)\b"""),
        Regex("""\bdnd access\b"""),
        Regex("""\bbrightness access\b"""),
        Regex("""\bremind exact\b"""),
        Regex("""notifications-feed"""),
        Regex("""\bnotifications settings\b"""),
        // The three commands whose names are everyday words, where a text tells a person to run them.
        Regex("""\b(RUN to|[Tt]ype|[Rr]un) "?`?(notifications|battery|accessibility)\b(?! (access|optimization|service|permission))"""),
        Regex("""fix\((?:[^()]|\([^()]*\))*?"(notifications|battery|accessibility)"\s*\)"""),
    )

    /** The app's own sources (every source set but the tests), and the guide and docs when they are here. */
    private val roots: List<File> by lazy {
        val src = sourceFile("src")
        val app = src.listFiles().orEmpty().filter { it.isDirectory && it.name != "test" && it.name != "androidTest" }
        val project = src.absoluteFile.parentFile.parentFile
        app + listOf(File(project, "site/src/content/docs"), File(project, "docs"), File(project, "README.org"))
            .filter { it.exists() }
    }

    /** History and plans, which may name what is gone. */
    private fun skipped(file: File) =
        file.path.replace('\\', '/').let { "/docs/release/notes/" in it || it.endsWith("/docs/tasks.org") }

    private val texts = setOf("kt", "kts", "xml", "lua", "js", "md", "mdx", "org", "txt")

    @Test
    fun theSourcesAreRead() {
        val files = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in texts }.toList() }
        assertTrue(files.size.toString(), files.size > 100)
        assertTrue(files.any { it.name == "CommandCatalog.kt" })
    }

    @Test
    fun noTextTellsAPersonToTypeAnOldForm() {
        val found = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension in texts && !skipped(it) }.flatMap { file ->
                // The whole text, so a call that spans lines (`fix(\n "…",\n "battery"\n)`) is seen too.
                val text = file.readText()
                forms.asSequence().flatMap { it.findAll(text) }.map { m ->
                    val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                    "${file.name}:$line: ${m.value.replace(Regex("\\s+"), " ")}"
                }
            }.toList()
        }
        assertEquals("Say allow <word> instead (CommandCatalog, Accesses)", emptyList<String>(), found)
    }
}
