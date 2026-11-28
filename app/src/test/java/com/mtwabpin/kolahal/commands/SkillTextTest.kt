package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.commands.SkillText.Guide
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs
import com.mtwabpin.kolahal.script.ScriptCheck
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderSuits
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `skill.md` and its focused guides ([SkillText.Guide]), for an AI assistant, assembled from their
 * pieces in assets/skill as the command assembles them, say what the code has: every function of the
 * script API, every uniform a shader gets, every header line, every kind and shape of a widget, and
 * nothing else, wherever a guide has that part; their examples pass the checks that run off a device.
 * And the command that copies them is offered to nobody, but runs when typed in full.
 */
class SkillTextTest {
    /**
     * What only Lab's picture maker reads, which the guides leave out while Lab is hidden: the header's
     * `@still` and the `seed` uniform (0 everywhere else).
     */
    private val labOnlyWords = setOf(ShaderMeta.STILL)
    private val labOnlyUniforms = setOf(ShaderMeta.SEED)

    /** Each guide's text, as the command copies it. */
    private val guides: Map<Guide, String> =
        Guide.entries.associateWith { g -> g.assemble { sourceFile("src/main/assets/$it").readText() } }

    /** The text under [heading] (`### Uniforms`) in [text], up to the next heading of its level or above; null without it. */
    private fun sectionOrNull(text: String, heading: String): String? {
        val level = heading.takeWhile { it == '#' }.length
        val start = text.indexOf("\n$heading\n")
        if (start < 0) return null
        val body = text.substring(start + heading.length + 2)
        val end = Regex("(?m)^#{1,$level} ").find(body)?.range?.first ?: body.length
        return body.substring(0, end)
    }

    /** The text under [heading] in [guide], which must have it. */
    private fun section(guide: Guide, heading: String): String =
        sectionOrNull(guides.getValue(guide), heading) ?: throw AssertionError("no '$heading' in ${guide.command}")

    /** The guides that have [heading]. */
    private fun having(heading: String): List<Guide> = guides.filter { sectionOrNull(it.value, heading) != null }.keys.toList()

    /** The code spans (`…`) in [s]. */
    private fun spans(s: String): List<String> =
        Regex("`([^`\n]+)`").findAll(s).map { it.groupValues[1] }.toList()

    /** The first cell of each row of the tables in [s], without the rows under a header. */
    private fun firstCells(s: String): List<String> =
        s.lines().filter { it.startsWith("| ") && !it.startsWith("| ---") }
            .map { it.removePrefix("|").substringBefore(" |").trim() }

    /** The fenced code blocks marked [lang] (`lua`, `js`, `glsl`) in [guide]. */
    private fun blocks(guide: Guide, lang: String): List<String> =
        Regex("(?ms)^```$lang\n(.*?)^```").findAll(guides.getValue(guide)).map { it.groupValues[1] }.toList()

    /** The scripts in [guide], with their language. */
    private fun scripts(guide: Guide): List<Pair<String, ScriptLang>> =
        blocks(guide, "lua").map { it to ScriptLang.LUA } + blocks(guide, "js").map { it to ScriptLang.JS }

    /** Every name of the script API: its functions and its values (`args`, `event`), not its tables. */
    private val api: Set<String> =
        ApiDocs.all.filter { it.kind != ApiDoc.Kind.NAMESPACE }.mapTo(HashSet()) { it.name }

    @Test
    fun anAssistantReadsEachInOneGo() {
        for ((guide, text) in guides) assertTrue("${guide.command}: ${text.length} characters", text.length <= SkillText.MAX_CHARS)
    }

    /** Each piece in assets/skill is in a guide, each guide has a piece once, and the words after `skill.md` pick them. */
    @Test
    fun eachPieceIsInAGuide() {
        val files = sourceFile("src/main/assets/${SkillText.FOLDER}").listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(files, Guide.entries.flatMap { it.pieces }.distinct().map { "$it.md" }.sorted())
        for (guide in Guide.entries) assertEquals(guide.pieces.distinct(), guide.pieces)
        assertEquals(Guide.ALL, Guide.of(""))
        assertEquals(Guide.SHADERS, Guide.of("shaders"))
        assertEquals(Guide.WIDGETS, Guide.of(" Widgets"))
        assertNull(Guide.of("scripts"))
        assertNull(Guide.of("shaders widgets"))
    }

    /** The whole guide has every part; the shaders guide no script API or widgets; the widgets guide no shaders. */
    @Test
    fun eachGuideHasItsParts() {
        val scriptParts = listOf("### Script header", "### Script API", "### What a script may use", "### Widgets", "### Runs")
        val shaderParts = listOf("## Shaders", "### Shader header", "### Uniforms", "### Parameters")
        for (part in scriptParts) assertEquals(part, listOf(Guide.ALL, Guide.WIDGETS), having(part))
        for (part in shaderParts) assertEquals(part, listOf(Guide.ALL, Guide.SHADERS), having(part))
        assertTrue(scripts(Guide.SHADERS).isEmpty())
        assertTrue(blocks(Guide.WIDGETS, "glsl").isEmpty())
        for ((guide, text) in guides) assertTrue(guide.command, text.startsWith("# ${guide.title}\n\n"))
    }

    /**
     * Every kind a script can fill and every shape id is a code span under Widgets, as `widget.show`
     * takes it: `cookie4`, never "`cookie` + 4", which an assistant writes as `cookie` or `cookie-4`.
     * Each kind's row gives the size it has without one.
     */
    @Test
    fun theWidgetKindsAndShapeIdsAreWrittenOut() {
        for (guide in having("### Widgets")) {
            val section = section(guide, "### Widgets")
            val widgets = spans(section).toSet()
            for (kind in WidgetKind.forScripts) assertTrue("${guide.command}: kind ${kind.id}", kind.id in widgets)
            for (id in WidgetShape.ids) assertTrue("${guide.command}: shape $id", id in widgets)
            for (kind in WidgetKind.forScripts) {
                val row = section.lines().single { it.startsWith("| `${kind.id}` |") }
                assertTrue(row, row.endsWith("| `${kind.size.id}` |"))
            }
        }
    }

    /** Under Widgets, each kind's line of fields names every field the parser takes for it ([WidgetParser.fieldsOf]). */
    @Test
    fun eachKindsLineNamesItsFields() {
        for (guide in having("### Widgets")) {
            val section = section(guide, "### Widgets")
            for (kind in WidgetKind.forScripts) {
                val line = section.lines().single { it.startsWith("- `${kind.id}`: ") }
                // An event's `id` is taken and dropped, for the rows of calendar.events().
                val missing = WidgetParser.fieldsOf(kind).filter { it != "id" && !Regex("\\b$it\\b").containsMatchIn(line) }
                assertEquals("${guide.command}: ${kind.id}", emptyList<String>(), missing.distinct())
            }
        }
    }

    /** Each function of [ApiDocs] has its row: a code span that starts with its name (`http.get(url, …)`, `args`). */
    @Test
    fun namesEveryFunctionOfTheScriptApi() {
        for (guide in having("### Script API")) {
            val named = spans(section(guide, "### Script API"))
            val missing = api.filter { name ->
                named.none { it == name || Regex("^(await )?${Regex.escape(name)}[ ({]").containsMatchIn(it) }
            }
            assertEquals(guide.command, emptyList<String>(), missing.sorted())
        }
    }

    /** No row names a function the API doesn't have, and in no guide is a call on an API table one it lacks (`http.gte`). */
    @Test
    fun namesNoFunctionTheApiLacks() {
        val lead = Regex("^(?:await )?([A-Za-z_][\\w.]*)")
        val tables = ApiDocs.all.filter { it.kind == ApiDoc.Kind.NAMESPACE }.map { it.name }.distinct()
        for ((guide, text) in guides) {
            val calls = tables.flatMap { table ->
                Regex("(?<![\\w.])${Regex.escape(table)}\\.([A-Za-z_]\\w*)").findAll(text)
                    .map { "$table.${it.groupValues[1]}" }.toList()
            }
            assertEquals(guide.command, emptyList<String>(), calls.filter { it !in api }.distinct())
            val table = sectionOrNull(text, "### Script API") ?: continue
            val rows = firstCells(table).flatMap(::spans).mapNotNull { lead.find(it)?.groupValues?.get(1) }
            assertTrue(rows.toString(), rows.size >= api.size)
            assertEquals(guide.command, emptyList<String>(), rows.filter { it !in api })
            assertTrue(calls.toString(), "http.get" in calls && "widget.show" in calls)
        }
    }

    /** Every `@` line the script editor knows ([ScriptCheck.DIRECTIVES]) is there, and the table has no other. */
    @Test
    fun namesEveryScriptHeaderLine() {
        val word = Regex("@(\\w+)")
        for (guide in having("### Script header")) {
            val header = section(guide, "### Script header")
            val said = word.findAll(header).map { it.groupValues[1] }.toSet()
            assertEquals(guide.command, emptyList<String>(), ScriptCheck.DIRECTIVES.filter { it !in said }.sorted())
            val rows = firstCells(header).flatMap { cell -> word.findAll(cell).map { it.groupValues[1] }.toList() }
            assertEquals(guide.command, ScriptCheck.DIRECTIVES.sorted(), rows.distinct().sorted())
        }
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
        (said + inTags).toSet() + ShaderMeta.FPS + ShaderMeta.SETTLES + ShaderSuits.DIRECTIVE + ShaderParam.DIRECTIVE -
            labOnlyWords
    }

    /** Every header word a shader can have is there, and the table has no other. */
    @Test
    fun namesEveryShaderHeaderLine() {
        assertTrue(shaderWords.toString(), "once" in shaderWords && "animated" in shaderWords && "title" in shaderWords)
        val word = Regex("@(\\w+)")
        for (guide in having("### Shader header")) {
            val header = section(guide, "### Shader header")
            val said = word.findAll(header).map { it.groupValues[1] }.toSet()
            assertEquals(guide.command, emptyList<String>(), shaderWords.filter { it !in said }.sorted())
            val rows = firstCells(header).flatMap { cell -> word.findAll(cell).map { it.groupValues[1] }.toList() }
            assertEquals(guide.command, emptyList<String>(), rows.filter { it !in shaderWords })
        }
    }

    /** The uniforms' table has `content` and each uniform Kolahal sets ([ShaderMeta.KNOWN]), once, with a type it takes. */
    @Test
    fun namesEveryUniformKolahalSets() {
        for (guide in having("### Uniforms")) {
            val rows = section(guide, "### Uniforms").lines().filter { it.startsWith("| `") }
            val named = rows.flatMap { spans(firstCells(it).single()) }
            assertEquals(guide.command, (ShaderMeta.KNOWN.keys - labOnlyUniforms + ShaderMeta.INPUT).sorted(), named.sorted())
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
    }

    /** Each shader in a guide would be tried: nothing in its way before the compiler, no header line left out, no uniform that stays 0. */
    @Test
    fun everyShaderPassesTheChecks() {
        for (guide in Guide.entries) {
            val shaders = blocks(guide, "glsl")
            if (guide != Guide.WIDGETS) assertTrue("${guide.command}: ${shaders.size}", shaders.size >= 4)
            for (code in shaders) {
                val title = "${guide.command}: ${ShaderMeta.parse(code).title}"
                assertEquals(title, emptyList<String>(), ShaderMeta.problems(code))
                assertEquals(title, emptyList<String>(), ShaderMeta.warnings(code))
                assertEquals(title, emptyList<String>(), ShaderMeta.parse(code).unset())
                assertTrue(title, "half4 main(float2 xy)" in code)
            }
        }
    }

    /** Each script in a guide is one the editor has nothing to point out in, and the options it reads are the ones it declares. */
    @Test
    fun everyScriptPassesTheChecks() {
        val read = Regex("""\boptions\.(\w+)""")
        for (guide in Guide.entries) {
            val scripts = scripts(guide)
            if (guide != Guide.SHADERS) assertTrue("${guide.command}: ${scripts.size}", scripts.size >= 4)
            for ((code, lang) in scripts) {
                assertEquals(code, emptyList<ScriptCheck.Issue>(), ScriptCheck.check(code, lang))
                assertEquals(
                    code,
                    ScriptMeta.options(code).map { it.name }.toSet(),
                    read.findAll(code).map { it.groupValues[1] }.toSet()
                )
            }
        }
    }

    /** The text inside the first `widget.show{…}` or `widget.show({…})` of [code], from its brace to the matching one. */
    private fun shown(code: String): String? {
        val at = Regex("""widget\.show\(?\{""").find(code) ?: return null
        var depth = 0
        for (i in at.range.last until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return code.substring(at.range.last + 1, i)
            }
        }
        return null
    }

    /**
     * Each script that fills a widget uses only fields its kind has ([WidgetParser.fieldsOf], the
     * common ones and a tap's), and a word field (`style`, `shape`, …) only with a word it takes.
     */
    @Test
    fun everyWidgetExampleUsesFieldsItsKindHas() {
        val strings = Regex("\"[^\"\n]*\"|'[^'\n]*'|`[^`]*`")
        val word = Regex("""(\w+)\s*(?:=|:)\s*"(\w+)"""")
        var checked = 0
        for (guide in Guide.entries) for ((code, lang) in scripts(guide)) {
            val table = shown(code) ?: continue
            val named = Regex("""kind\s*[=:]\s*"(\w+)"""").find(table)?.groupValues?.get(1)
            val kind = named?.let { WidgetKind.named(it) } ?: ScriptMeta.widget(code)?.kind
            assertTrue(code, kind != null && kind.script)
            val allowed = WidgetParser.COMMON_FIELDS + WidgetParser.TAP_FIELDS + WidgetParser.fieldsOf(kind!!)
            val bare = table.replace(strings, "\"\"")
            val key = if (lang == ScriptLang.LUA) Regex("""(?<![\w.~=<>!])(\w+)\s*=(?!=)""") else Regex("""(\w+):(?!/)""")
            val keys = key.findAll(bare).map { it.groupValues[1] }.filter { it != "local" }.toSet()
            assertEquals("${guide.command}: ${kind.id}\n$code", emptyList<String>(), keys.filter { it !in allowed })
            val choices = WidgetParser.choicesOf(kind)
            for (m in word.findAll(table)) {
                val words = choices[m.groupValues[1]] ?: continue
                assertTrue("${guide.command}: ${m.value}", m.groupValues[2] in words)
            }
            checked++
        }
        assertTrue(checked.toString(), checked >= WidgetKind.forScripts.size)
    }

    /** The widgets guide has a whole script for each kind a script can fill, at a size the kind is made for. */
    @Test
    fun theWidgetsGuideHasAnExampleOfEachKind() {
        val widgets = scripts(Guide.WIDGETS).mapNotNull { (code, _) -> ScriptMeta.widget(code) }
        assertEquals(emptyList<String>(), WidgetKind.forScripts.filter { k -> widgets.none { it.kind == k } }.map { it.id })
        for (w in widgets) assertTrue("${w.kind.id} ${w.size.id}", w.size in w.kind.sizes)
    }

    /** Each kind's row under "Sizes and looks" gives the sizes it is made for ([WidgetKind.sizes]) and the looks it offers ([WidgetLook.offeredFor]). */
    @Test
    fun theSizesAndLooksAreTheCodes() {
        for (guide in having("### Sizes and looks")) {
            val rows = section(guide, "### Sizes and looks").lines().filter { it.startsWith("| `") }
            assertEquals(WidgetKind.forScripts.map { it.id }, rows.map { spans(firstCells(it).single()).single() })
            for (kind in WidgetKind.forScripts) {
                val cells = rows.single { it.startsWith("| `${kind.id}` |") }.split(" | ")
                assertEquals(kind.id, kind.sizes.joinToString(", ") { it.id }, cells[1])
                assertEquals(kind.id, WidgetLook.offeredFor(kind).joinToString(", ") { it.label }, cells[2].removeSuffix(" |"))
            }
        }
        assertEquals(listOf(Guide.WIDGETS), having("### Sizes and looks"))
    }

    /** What the shaders guide says Kolahal refuses a shader with is what [ShaderMeta.problems] says, word for word. */
    @Test
    fun theRefusalsAreQuotedAsKolahalSaysThem() {
        val said = spans(section(Guide.SHADERS, "### What Kolahal refuses, and what it says")).toSet()
        val head = "uniform shader content;\n"
        val main = "\nhalf4 main(float2 xy) { return content.eval(xy); }\n"
        fun params(n: Int) = (1..n).joinToString("") { "// @param p$it 0.0..1.0 = 0.5 P\n" } +
                head + (1..n).joinToString("") { "uniform float p$it;\n" }
        val shaders = listOf(
            head + "// " + "x".repeat(70_000) + main,
            "half4 main(float2 xy) { return half4(0.0); }\n",
            head + "uniform shader img;" + main,
            head + "uniform float resolution;" + main,
            params(9) + main,
            "// @param amount 0.0..2.0 = 1.0 Amount\n// @param amount 0.0..2.0 = 1.0 Amount\n" + head + "uniform float amount;" + main,
            "// @param amount 0.0..2.0 = 1.0 Amount\n" + head + main,
            "// @param time 0.0..2.0 = 1.0 Time\n" + head + "uniform float time;" + main,
        )
        for (code in shaders) {
            val problem = ShaderMeta.problems(code).first()
            assertTrue(problem, problem in said)
        }
        val unread = ShaderMeta.problems("// @param x\n$head$main").single()
        assertTrue(unread, unread.replace("'@param x'", "'@param …'") in said)
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

    /** Its help (`help skill.md`, typed in full) has a use for each guide, and its usage line the words that pick them. */
    @Test
    fun itsHelpHasEachGuide() {
        val spec = CommandCatalog.COMMANDS.single { it.name == SkillText.NAME }
        assertEquals(Guide.entries.map { it.command }, spec.uses.map { it.first })
        assertEquals("skill.md [shaders|widgets]", spec.usage)
        assertEquals(Guide.entries.mapNotNull { it.word }, UsageLine.split(UsageLine.tokens(spec.usage)[1].removeSurrounding("[", "]"), '|'))
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
        for (typed in (1..name.length).map { name.take(it) } + Guide.entries.map { it.command }) {
            assertNotEquals(typed, InstantRun.Target.Command(typed), target(typed, emptyList()))
        }
        assertEquals(InstantRun.Target.App("Skillshare"), target("skill", listOf("Skillshare")))
        assertTrue(InstantRun.isSensitive(name))
    }

    @Test
    fun theLineSaysWhichGuideWasCopiedAndWhatToDo() {
        assertEquals(
            "Copied skill.md (12,400 characters)\nPaste it into an AI assistant and say what to build",
            SkillText.copied(Guide.ALL, 12_400)
        )
        assertEquals(
            "Copied skill.md (812 characters)\nPaste it into an AI assistant and say what to build",
            SkillText.copied(Guide.ALL, 812)
        )
        assertEquals(
            "Copied skill.md shaders, the guide for shaders (21,000 characters)\nPaste it into an AI assistant and say what to build",
            SkillText.copied(Guide.SHADERS, 21_000)
        )
        assertEquals(
            "Copied skill.md widgets, the guide for widget scripts (39,000 characters)\nPaste it into an AI assistant and say what to build",
            SkillText.copied(Guide.WIDGETS, 39_000)
        )
    }

    /** A word that is no guide: what was wrong on the first line, then each guide on a line of its own. */
    @Test
    fun anotherWordGetsEachGuide() {
        assertEquals(
            "No guide called 'scripts'\n" +
                    "skill.md: the whole guide, scripts and shaders\n" +
                    "skill.md shaders: shaders only\n" +
                    "skill.md widgets: scripts with a widget, with the whole script API",
            SkillText.unknown(" scripts ")
        )
    }
}
