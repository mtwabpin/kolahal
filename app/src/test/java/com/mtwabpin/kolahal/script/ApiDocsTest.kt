package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps [ApiDocs] in sync with the functions the engines actually bind (parsed from their sources). */
class ApiDocsTest {
    /** The editor's reference lists every `@` line the editor accepts. */
    @Test fun everyDirectiveIsListed() {
        assertEquals(ScriptCheck.DIRECTIVES, ApiDocs.directives.map { it.name }.toSet())
    }


    private fun source(name: String): List<String> = sourceFile("src/main/java/com/mtwabpin/kolahal/script/$name").readLines()

    private fun luaBindings(): Set<String> {
        val out = mutableSetOf<String>()
        var table: String? = null
        var awaitingName = false
        val member = Regex("\"(\\w+)\" to \\{ l: Lua")
        for (line in source("LuaEngine.kt")) {
            Regex("lua\\.global\\(\"(\\w+)\"\\)").find(line)?.let { out += it.groupValues[1] }
            if (awaitingName) {
                Regex("^\\s*\"(\\w+)\",").find(line)?.let { table = it.groupValues[1] }
                awaitingName = false
            }
            if ("lua.table(" in line) {
                val same = Regex("lua\\.table\\(\"(\\w+)\"").find(line)
                if (same != null) table = same.groupValues[1] else awaitingName = true
            }
            member.findAll(line).forEach { out += "$table.${it.groupValues[1]}" }
        }
        return out
    }

    private fun jsBindings(): Set<String> {
        val out = mutableSetOf<String>()
        var ns: String? = null
        for (line in source("JsEngine.kt")) {
            Regex("js\\.define\\(\"(\\w+)\"\\)").find(line)?.let { ns = it.groupValues[1] }
            Regex("js\\.(?:asyncFunction|function)\\(\"(\\w+)\"\\)").findAll(line).forEach { out += it.groupValues[1] }
            Regex("(?<!js\\.)\\b(?:asyncFunction|function)\\(\"(\\w+)\"\\)").findAll(line).forEach { out += "$ns.${it.groupValues[1]}" }
        }
        return out.filterNot { it.startsWith("__") }.toSet()
    }

    private fun documented(lang: ScriptLang, extra: Set<String> = emptySet()) =
        ApiDocs.all.filter { lang in it.langs && it.kind == ApiDoc.Kind.FUNCTION }.map { it.name }.toSet() - extra

    @Test fun luaApiIsDocumented() {
        val bound = luaBindings()
        assertTrue("parser found too few bindings: $bound", bound.size > 20)
        // print is defined by the Lua prelude, not a Kotlin binding.
        assertEquals(bound, documented(ScriptLang.LUA, setOf("print")))
    }

    @Test fun jsApiIsDocumented() {
        val bound = jsBindings()
        assertTrue("parser found too few bindings: $bound", bound.size > 20)
        assertEquals(bound, documented(ScriptLang.JS))
    }

    @Test fun everyMemberHasItsNamespace() {
        for (lang in ScriptLang.entries) {
            for (d in ApiDocs.forLang(lang)) {
                val q = d.qualifier ?: continue
                assertTrue("$q missing as a namespace for $lang", ApiDocs.isNamespace(q, lang))
            }
        }
    }
}
