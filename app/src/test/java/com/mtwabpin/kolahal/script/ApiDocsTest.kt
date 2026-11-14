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
        // Names that start with __ are the preludes' own, not the API.
        return out.filterNot { it.startsWith("__") }.toSet()
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

    /**
     * guard.js wraps the functions of the namespaces it lists (`spaces`), so each takes a checked
     * copy of its arguments. A namespace that JsEngine defines and the list leaves out would not
     * be guarded. A name in the list that is not defined would stop every JavaScript run.
     */
    @Test fun everyJsNamespaceIsGuarded() {
        val lines = source("JsEngine.kt")
        val defined = lines.flatMap { line -> Regex("js\\.define\\(\"(\\w+)\"\\)").findAll(line).map { it.groupValues[1] } }
        val list = lines.single { "const spaces = [" in it }.substringAfter('[').substringBefore(']')
        val guarded = Regex("\"(\\w+)\"").findAll(list).map { it.groupValues[1] }.toList()
        assertTrue("parser found too few namespaces: $defined", defined.size > 10)
        assertEquals(defined.toSet(), guarded.toSet())
        assertEquals("a namespace is listed twice", guarded.size, guarded.toSet().size)
    }

    /** Your own lists are bound in both languages, with the same names. */
    @Test fun theListsAreBoundInBothLanguages() {
        val lists = setOf("todos.list", "todos.setDone", "notes.list", "reminders.list", "calendar.events", "feeds.unread")
        assertEquals(lists, luaBindings().filter { it in lists }.toSet())
        assertEquals(lists, jsBindings().filter { it in lists }.toSet())
    }

    /**
     * A call that reads private data says which `@uses` line it needs, in the editor's reference.
     * The line has the name of the call's namespace, and the editor's check knows the call too.
     */
    @Test fun aListCallSaysWhichUsesLineItNeeds() {
        for (ns in listOf("todos", "notes", "reminders", "calendar", "feeds")) {
            assertTrue(ns, ns in ScriptMeta.USES)
            val calls = ApiDocs.all.filter { it.qualifier == ns }
            assertTrue("$ns has no calls", calls.isNotEmpty())
            for (call in calls) {
                assertTrue("${call.name}: ${call.doc}", call.doc.endsWith("(needs @uses $ns)"))
                assertTrue("the editor doesn't know ${call.name}", ScriptCheck.NEEDS.getValue(ns).containsMatchIn("${call.name}()"))
                assertEquals(setOf(ScriptLang.LUA, ScriptLang.JS), call.langs)
            }
        }
    }

    /** `allowed(what)` takes every `@uses` item, and its text names them all. */
    @Test fun allowedNamesEveryUsesItem() {
        val doc = ApiDocs.find("allowed", ScriptLang.LUA)!!.doc
        for (item in ScriptMeta.USES) assertTrue("$item is not in: $doc", Regex("\\b$item\\b").containsMatchIn(doc))
    }
}
