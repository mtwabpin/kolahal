package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs
import com.mtwabpin.kolahal.widgets.WidgetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the editor's reference page lists in its Functions tab, and what its search finds ([ReferenceSearch]). */
class ReferenceSearchTest {
    /**
     * Every entry is in exactly one group, in the API's order: the globals first (log is first), then
     * each namespace under its own entry.
     */
    @Test fun eachEntryIsInOneGroupUnderItsNamespace() {
        for (lang in ScriptLang.entries) {
            val groups = ReferenceSearch.groups(lang)
            assertEquals(ReferenceSearch.GLOBALS, groups.first().name)
            assertEquals(groups.map { it.name }.distinct(), groups.map { it.name })
            val listed = groups.flatMap { listOfNotNull(it.head) + it.entries }
            assertEquals(ApiDocs.forLang(lang).filter { it.kind != ApiDoc.Kind.KEYWORD }.toSet(), listed.toSet())
            assertEquals(listed.size, listed.toSet().size)
            for (g in groups) {
                if (g.name == ReferenceSearch.GLOBALS) {
                    assertNull(g.head)
                    assertTrue(g.entries.all { it.qualifier == null && it.kind != ApiDoc.Kind.NAMESPACE })
                } else {
                    assertEquals(g.name, g.head?.name)
                    assertTrue(g.name, g.entries.all { it.name.startsWith(g.name + ".") })
                }
            }
        }
        val http = ReferenceSearch.groups(ScriptLang.LUA).single { it.name == "http" }
        assertEquals(listOf("http.get", "http.cached", "http.request"), http.entries.map { it.name })
    }

    /** One word finds it in every tab: functions, widget examples and their variants, by their code. */
    @Test fun aWordIsFoundInEveryTab() {
        val found = ReferenceSearch.find("store", ScriptLang.LUA)
        assertTrue(found.functions.map { it.name }.containsAll(listOf("store", "store.get", "store.set")))
        // The glasses of water are counted in store, and so is each switch of a variant.
        assertTrue(found.widgets.map { it.name }.containsAll(listOf("water", "toggles-2")))
        assertTrue(found.widgets.all { it.code(ScriptLang.LUA).contains("store") })
        assertEquals(emptyList<String>(), found.icons)
        assertEquals(found.functions.size + found.directives.size + found.widgets.size + found.logs.size, found.count)
    }

    /** Every word must be found, in any case, and each can be in another field: the name, the line, the code. */
    @Test fun everyWordMustBeFound() {
        val found = ReferenceSearch.find("HTTP response", ScriptLang.LUA)
        assertTrue(found.functions.isNotEmpty())
        assertTrue(found.functions.all { it.name.startsWith("http") })
        assertEquals(0, ReferenceSearch.find("http zzzz", ScriptLang.LUA).count)
    }

    @Test fun directivesLogLinesAndIconsAreFound() {
        assertEquals(listOf("command"), ReferenceSearch.find("@command", ScriptLang.LUA).directives.map { it.name })
        assertEquals(listOf("sun"), ReferenceSearch.find("sun", ScriptLang.LUA).icons)
        // A log example by the words of its lines.
        assertTrue("log" in ReferenceSearch.find("neovim", ScriptLang.LUA).logs.map { it.name })
        // By its code in the script's language.
        assertTrue("log" in ReferenceSearch.find("const stories", ScriptLang.JS).logs.map { it.name })
        assertTrue("log" !in ReferenceSearch.find("const stories", ScriptLang.LUA).logs.map { it.name })
    }

    @Test fun aBlankSearchFindsNothing() {
        assertEquals(0, ReferenceSearch.find("", ScriptLang.LUA).count)
        assertEquals(0, ReferenceSearch.find("   ", ScriptLang.JS).count)
    }

    /** The guide's examples, each followed by its variants. */
    @Test fun theVariantsFollowTheirExample() {
        val names = ReferenceSearch.allWidgets.map { it.name }
        assertEquals((ReferenceExamples.widgets + ReferenceExamples.variants).map { it.name }.toSet(), names.toSet())
        assertEquals(names.indexOf("toggles") + 1, names.indexOf("toggles-2"))
        assertEquals(names.indexOf("toggles") + 2, names.indexOf("toggles-4"))
    }

    /** A widget's search result shows the line of its code that has the word. */
    @Test fun theLineWithTheWordIsTheFirstThatHasIt() {
        assertEquals("store.set(args[1],", ReferenceSearch.lineWith("local a = 1\n  store.set(args[1],\nstore.get(x)", listOf("STORE")))
        assertNull(ReferenceSearch.lineWith("local a = 1", listOf("store")))
        assertNull(ReferenceSearch.lineWith("local a = 1", listOf("")))
    }

    @Test fun eachKindHasALine() {
        for (kind in WidgetKind.entries) assertTrue(kind.id, ReferenceSearch.kindLine(kind).isNotBlank())
    }
}
