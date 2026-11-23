package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs
import com.mtwabpin.kolahal.script.ScriptCheck
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderSuits
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `skill.md`, the guide for an AI assistant (assets/skill.md, [SkillText]), says what the code has:
 * every function of the script API, every uniform a shader gets, every header line, and nothing
 * else; its examples pass the checks that run off a device. And the command that copies it is
 * offered to nobody, but runs when typed in full.
 */
class SkillTextTest {
    private val text = sourceFile("src/main/assets/${SkillText.NAME}").readText()

    /** The text under [heading] (`### Uniforms`), up to the next heading of its level or above. */
    private fun section(heading: String): String {
        val level = heading.takeWhile { it == '#' }.length
        val start = text.indexOf("\n$heading\n")
        assertTrue("no '$heading' in ${SkillText.NAME}", start >= 0)
        val body = text.substring(start + heading.length + 2)
        val end = Regex("(?m)^#{1,$level} ").find(body)?.range?.first ?: body.length
        return body.substring(0, end)
    }

    /** The code spans (`…`) in [s]. */
    private fun spans(s: String): List<String> =
        Regex("`([^`\n]+)`").findAll(s).map { it.groupValues[1] }.toList()

    /** The first cell of each row of the tables in [s], without the rows under a header. */
    private fun firstCells(s: String): List<String> =
        s.lines().filter { it.startsWith("| ") && !it.startsWith("| ---") }
            .map { it.removePrefix("|").substringBefore(" |").trim() }

    /** The fenced code blocks marked [lang] (`lua`, `js`, `glsl`). */
    private fun blocks(lang: String): List<String> =
        Regex("(?ms)^```$lang\n(.*?)^```").findAll(text).map { it.groupValues[1] }.toList()

    /** Every name of the script API: its functions and its values (`args`, `event`), not its tables. */
    private val api: Set<String> =
        ApiDocs.all.filter { it.kind != ApiDoc.Kind.NAMESPACE }.mapTo(HashSet()) { it.name }

    @Test
    fun anAssistantReadsItInOneGo() {
        assertTrue("${text.length} characters", text.length <= SkillText.MAX_CHARS)
    }

    /** Each function of [ApiDocs] has its row: a code span that starts with its name (`http.get(url, …)`, `args`). */
    @Test
    fun namesEveryFunctionOfTheScriptApi() {
        val named = spans(section("### Script API"))
        val missing = api.filter { name ->
            named.none { it == name || Regex("^(await )?${Regex.escape(name)}[ ({]").containsMatchIn(it) }
        }
        assertEquals(emptyList<String>(), missing.sorted())
    }

    /** No row names a function the API doesn't have, and nowhere is a call on an API table one it lacks (`http.gte`). */
    @Test
    fun namesNoFunctionTheApiLacks() {
        val lead = Regex("^(?:await )?([A-Za-z_][\\w.]*)")
        val rows = firstCells(section("### Script API")).flatMap(::spans)
            .mapNotNull { lead.find(it)?.groupValues?.get(1) }
        assertTrue(rows.toString(), rows.size >= api.size)
        assertEquals(emptyList<String>(), rows.filter { it !in api })
        val tables = ApiDocs.all.filter { it.kind == ApiDoc.Kind.NAMESPACE }.map { it.name }.distinct()
        val calls = tables.flatMap { table ->
            Regex("(?<![\\w.])${Regex.escape(table)}\\.([A-Za-z_]\\w*)").findAll(text)
                .map { "$table.${it.groupValues[1]}" }.toList()
        }
        assertTrue(calls.toString(), "http.get" in calls && "widget.show" in calls)
        assertEquals(emptyList<String>(), calls.filter { it !in api }.distinct())
    }

    /** Every `@` line the script editor knows ([ScriptCheck.DIRECTIVES]) is there, and the table has no other. */
    @Test
    fun namesEveryScriptHeaderLine() {
        val header = section("### Script header")
        val word = Regex("@(\\w+)")
        val said = word.findAll(header).map { it.groupValues[1] }.toSet()
        assertEquals(emptyList<String>(), ScriptCheck.DIRECTIVES.filter { it !in said }.sorted())
        val rows = firstCells(header).flatMap { cell -> word.findAll(cell).map { it.groupValues[1] }.toList() }
        assertEquals(ScriptCheck.DIRECTIVES.sorted(), rows.distinct().sorted())
    }

    /**
     * The words [ShaderMeta.parse] reads from a shader's header, from its source: those it asks for by
     * name (`said("once")`, `"animated" in tags`), and the ones it keeps in constants.
     */
    private val shaderWords: Set<String> by lazy {
        val source = sourceFile("src/main/java/com/mtwabpin/kolahal/shader/ShaderMeta.kt").readText()
        val quoted = Regex("\"(\\w+)\"")
        val said = Regex("said\\(([^)]*)\\)").findAll(source)
            .flatMap { call -> quoted.findAll(call.groupValues[1]).map { it.groupValues[1] } }
        val inTags = Regex("\"(\\w+)\" in tags").findAll(source).map { it.groupValues[1] }
        (said + inTags).toSet() + ShaderMeta.FPS + ShaderMeta.SETTLES + ShaderSuits.DIRECTIVE + ShaderParam.DIRECTIVE
    }

    /** Every header word a shader can have is there, and the table has no other. */
    @Test
    fun namesEveryShaderHeaderLine() {
        assertTrue(shaderWords.toString(), "once" in shaderWords && "animated" in shaderWords && "title" in shaderWords)
        val header = section("### Shader header")
        val word = Regex("@(\\w+)")
        val said = word.findAll(header).map { it.groupValues[1] }.toSet()
        assertEquals(emptyList<String>(), shaderWords.filter { it !in said }.sorted())
        val rows = firstCells(header).flatMap { cell -> word.findAll(cell).map { it.groupValues[1] }.toList() }
        assertEquals(emptyList<String>(), rows.filter { it !in shaderWords })
    }

    /** The uniforms' table has `content` and each uniform Kolahal sets ([ShaderMeta.KNOWN]), once, with a type it takes. */
    @Test
    fun namesEveryUniformKolahalSets() {
        val rows = section("### Uniforms").lines().filter { it.startsWith("| `") }
        val named = rows.flatMap { spans(firstCells(it).single()) }
        assertEquals((ShaderMeta.KNOWN.keys + ShaderMeta.INPUT).sorted(), named.sorted())
        for (row in rows) {
            val type = spans(row.split(" | ")[1]).single()
            for (name in spans(firstCells(row).single())) {
                if (name == ShaderMeta.INPUT) assertEquals("shader", type)
                else assertTrue(
                    "$name as $type",
                    ShaderMeta.Uniform(name, type, color = false, array = false).floats in ShaderMeta.KNOWN.getValue(name)
                )
            }
        }
    }

    /** Each shader in it would be tried: nothing in its way before the compiler, no header line left out, no uniform that stays 0. */
    @Test
    fun everyShaderPassesTheChecks() {
        val shaders = blocks("glsl")
        assertTrue(shaders.size.toString(), shaders.size >= 4)
        for (code in shaders) {
            val title = ShaderMeta.parse(code).title
            assertEquals(title, emptyList<String>(), ShaderMeta.problems(code))
            assertEquals(title, emptyList<String>(), ShaderMeta.warnings(code))
            assertEquals(title, emptyList<String>(), ShaderMeta.parse(code).unset())
            assertTrue(title, "half4 main(float2 xy)" in code)
        }
    }

    /** Each script in it is one the editor has nothing to point out in, and the options it reads are the ones it declares. */
    @Test
    fun everyScriptPassesTheChecks() {
        val scripts = blocks("lua").map { it to ScriptLang.LUA } + blocks("js").map { it to ScriptLang.JS }
        assertTrue(scripts.size.toString(), scripts.size >= 4)
        val read = Regex("""\boptions\.(\w+)""")
        for ((code, lang) in scripts) {
            assertEquals(code, emptyList<ScriptCheck.Issue>(), ScriptCheck.check(code, lang))
            assertEquals(
                code,
                ScriptMeta.options(code).map { it.name }.toSet(),
                read.findAll(code).map { it.groupValues[1] }.toSet()
            )
        }
    }

    /** The command is in the catalog, but nothing lists it: not `help`, not Settings → Commands. */
    @Test
    fun theCommandIsListedNowhere() {
        val name = SkillText.NAME
        assertTrue(name in CommandCatalog.COMMAND_NAMES)
        assertTrue(name in CommandCatalog.HIDDEN)
        assertFalse(name in CommandCatalog.OFFERED_NAMES)
        assertFalse(CommandCatalog.HELP_SECTIONS.values.any { name in it })
        assertFalse(name in CommandsOff.CHOICES)
        // Every hidden command is a command, so typed in full it runs (DispatchNamesTest: it is dispatched).
        assertTrue(CommandCatalog.HIDDEN.all { it in CommandCatalog.COMMAND_NAMES })
    }

    /**
     * Instant launch never runs it, at any prefix of its name or at the whole name: it waits for Enter.
     * An app whose name starts with "skill" opens as before. A script can't send it, as it sets the clipboard.
     */
    @Test
    fun onlyEnterRunsIt() {
        val name = SkillText.NAME
        fun target(typed: String, apps: List<String>) = InstantRun.target<String>(
            typed, InstantRun.ALL, 1,
            rankedApps = { q -> apps.filter { it.lowercase().startsWith(q) }.map { it to 1 } },
            appLabel = { it },
            commands = CommandCatalog.COMMAND_USAGES,
            aliases = emptyMap(),
            scriptCommands = emptyList(),
        )
        for (end in 1..name.length) {
            assertNotEquals(name.take(end), InstantRun.Target.Command(name), target(name.take(end), emptyList()))
        }
        assertEquals(InstantRun.Target.App("Skillshare"), target("skill", listOf("Skillshare")))
        assertTrue(InstantRun.isSensitive(name))
    }

    @Test
    fun theLineSaysWhatWasCopiedAndWhatToDo() {
        assertEquals(
            "Copied skill.md (12,400 characters) · paste it into an AI assistant and say what to build",
            SkillText.copied(12_400)
        )
        assertEquals(
            "Copied skill.md (812 characters) · paste it into an AI assistant and say what to build",
            SkillText.copied(812)
        )
    }
}
