package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The website's Script check (site/src/content/docs/tools/script-check.mdx) says what the editor says, from:
 * - site/src/data/api-names.json, the API's names in each language, which it checks calls against as
 *   [ScriptCheck] does. When it is stale, this writes it as it should be to app/build/api-names.json: copy that
 *   over the site's file.
 * - site/src/lib/rules.mjs, kolahal-scripts' rules, copied by site/scripts/copy-rules.mjs. Its lists must be the
 *   app's. When they aren't, change tools/rules.mjs in kolahal-scripts (on the branch of this version), then
 *   copy it again.
 */
class SiteScriptCheckTest {
    /** A file of the site, from where the tests run: `app/` under Gradle, the repository's root in some IDEs. */
    private fun site(path: String): File =
        listOf(File("../site/$path"), File("site/$path")).firstOrNull { it.exists() }
            ?: error("site/$path isn't here or one folder up")

    /** The names [ScriptCheck] checks calls against, in each language: every entry, and the namespaces. */
    private fun apiNames(): JSONObject = JSONObject().apply {
        for (lang in ScriptLang.entries) {
            val docs = ApiDocs.all.filter { lang in it.langs }
            put(
                lang.ext,
                JSONObject()
                    .put("namespaces", JSONArray(docs.filter { it.kind == ApiDoc.Kind.NAMESPACE }.map { it.name }.distinct().sorted()))
                    .put("names", JSONArray(docs.map { it.name }.distinct().sorted())),
            )
        }
    }

    /** Each list of [json] by `<lang>.<list>`, to compare whatever the order of the keys. */
    private fun lists(json: JSONObject): Map<String, List<String>> = buildMap {
        for (lang in json.keys()) {
            val inner = json.getJSONObject(lang)
            for (key in inner.keys()) {
                val array = inner.getJSONArray(key)
                put("$lang.$key", List(array.length()) { array.getString(it) })
            }
        }
    }

    @Test
    fun theSiteHasTheApisNames() {
        val want = apiNames()
        val have = JSONObject(site("src/data/api-names.json").readText())
        if (lists(want) != lists(have)) {
            File("build/api-names.json").apply { parentFile?.mkdirs() }.writeText(want.toString(2) + "\n")
        }
        assertEquals(
            "site/src/data/api-names.json is stale: copy app/build/api-names.json over it",
            lists(want), lists(have),
        )
    }

    private val rules by lazy { site("src/lib/rules.mjs").readText() }

    /** The quoted words of `export const <name> = [ … ];` in rules.mjs. */
    private fun list(name: String): List<String> {
        val m = Regex("""export const $name = \[(.*?)\];""", RegexOption.DOT_MATCHES_ALL).find(rules)
            ?: error("rules.mjs has no list $name")
        return Regex("""'([^']*)'""").findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    /** The block of `export const <name> = { … };` in rules.mjs. */
    private fun block(name: String): String =
        Regex("""export const $name = \{(.*?)\n\};""", RegexOption.DOT_MATCHES_ALL).find(rules)?.groupValues?.get(1)
            ?: error("rules.mjs has no object $name")

    private fun number(name: String): Int =
        Regex("""export const $name = ([\d *]+);""").find(rules)?.groupValues?.get(1)
            ?.split('*')?.map { it.trim().toInt() }?.reduce(Int::times) ?: error("rules.mjs has no number $name")

    @Test
    fun theRulesHaveTheScriptsLists() {
        assertEquals("USES", ScriptMeta.USES, list("USES"))
        assertEquals("EVENTS", ScriptMeta.EVENTS, list("EVENTS"))
        assertEquals("DIRECTIVES", ScriptCheck.DIRECTIVES.toList(), list("DIRECTIVES"))
        assertEquals("SCREEN_REFUSES", ScriptCheck.SCREEN_REFUSES, list("SCREEN_REFUSES").toSet())
        val needs = Regex("""\['(\w+)', /(.*)/]""").findAll(
            Regex("""export const NEEDS = \[(.*?)\n];""", RegexOption.DOT_MATCHES_ALL).find(rules)!!.groupValues[1]
        ).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals("NEEDS", ScriptCheck.NEEDS.mapValues { it.value.pattern }, needs)
        assertEquals("NEEDS order", ScriptCheck.NEEDS.keys.toList(), needs.keys.toList())
    }

    @Test
    fun theRulesHaveTheWidgets() {
        assertEquals("WIDGET_KINDS", WidgetKind.forScripts.map { it.id }, list("WIDGET_KINDS"))
        assertEquals("WIDGET_SIZES", WidgetSize.entries.map { it.id }, list("WIDGET_SIZES"))
        val sizeOf = Regex("""(\w+): '(\w+)'""").findAll(block("WIDGET_SIZE_OF")).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals("WIDGET_SIZE_OF", WidgetKind.forScripts.associate { it.id to it.size.id }, sizeOf)
        val sizesOf = Regex("""(\w+): \[([^\]]*)]""").findAll(block("WIDGET_SIZES_OF")).associate { m ->
            m.groupValues[1] to Regex("""'([^']*)'""").findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
        }
        assertEquals("WIDGET_SIZES_OF", WidgetKind.forScripts.associate { k -> k.id to k.sizes.map { it.id } }, sizesOf)
    }

    @Test
    fun theRulesHaveTheShadersLists() {
        assertEquals("SHADER_WORDS", ShaderCatalog.WORDS, list("SHADER_WORDS"))
        assertEquals("SHADER_BUNDLED", ShaderCatalog.BUNDLED, list("SHADER_BUNDLED"))
        assertEquals("SHADER_COLORS", ShaderMeta.COLORS, list("SHADER_COLORS"))
        // The uniforms Kolahal sets, by how many numbers each holds: the theme's colors four each.
        val uniforms = Regex("""(\w+): (\d+)""").findAll(block("UNIFORMS")).associate { it.groupValues[1] to listOf(it.groupValues[2].toInt()) } +
                ShaderMeta.COLORS.associateWith { listOf(4) }
        assertEquals("UNIFORMS", ShaderMeta.KNOWN, uniforms)
        assertEquals("PARAMS_MAX", ShaderParam.MAX, number("PARAMS_MAX"))
        assertEquals("MAX_BYTES", ShaderMeta.MAX_BYTES, number("MAX_BYTES"))
    }
}
