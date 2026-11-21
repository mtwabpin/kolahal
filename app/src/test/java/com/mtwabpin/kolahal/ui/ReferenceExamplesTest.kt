package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor reference's examples ([ReferenceExamples]) are the scripting guide's, word for word. */
class ReferenceExamplesTest {
    private val guide = sourceFile("../site/src/content/docs/scripting/api.mdx").readText()

    @Test
    fun theGuideHasEachExamplesCodeInBothLanguages() {
        val codes =
            ReferenceExamples.widgets.map { it.name to it::code } + ReferenceExamples.logs.map { it.name to it::code }
        for ((name, code) in codes) for (lang in ScriptLang.entries) {
            assertTrue("$name in $lang is not in api.mdx:\n${code(lang)}", code(lang) in guide)
        }
    }

    /** Each fmt function of the reference has a sample, but fmt.plain, whose text has no styles; and each sample is of a function there. */
    @Test
    fun eachFmtFunctionHasASample() {
        val functions = com.mtwabpin.kolahal.script.ApiDocs.forLang(ScriptLang.LUA).map { it.name }
            .filter { it.startsWith("fmt.") }.toSet()
        assertEquals(functions - "fmt.plain", ReferenceExamples.fmt.map { it.name }.toSet())
        assertEquals(
            ReferenceExamples.fmt.size,
            ReferenceExamples.fmt.map { it.name }.distinct().size
        )
    }

    /**
     * Each widget example and variant names its kind in `widget.show`, in both languages, and it is
     * the kind the reference files it under. The reference shows no `@widget` line, so the code must
     * draw its card under any script's line.
     */
    @Test
    fun eachWidgetExampleNamesItsKind() {
        for (ex in ReferenceExamples.widgets + ReferenceExamples.variants) {
            assertTrue("${ex.name} in Lua", "kind = \"${ex.kind.id}\"" in ex.code(ScriptLang.LUA))
            assertTrue(
                "${ex.name} in JavaScript",
                "kind: \"${ex.kind.id}\"" in ex.code(ScriptLang.JS)
            )
        }
    }

    @Test
    fun eachExampleHasItsOwnName() {
        val widgets = (ReferenceExamples.widgets + ReferenceExamples.variants).map { it.name }
        for (names in listOf(
            widgets,
            ReferenceExamples.logs.map { it.name })) assertEquals(names.distinct(), names)
    }

    /** A variant varies one of the guide's examples: same kind, same size, and a chip label of its own. */
    @Test
    fun eachVariantVariesAGuideExample() {
        val guide = ReferenceExamples.widgets.associateBy { it.name }
        for (v in ReferenceExamples.variants) {
            val of = guide[v.of]
            assertTrue("${v.name} varies no guide example: ${v.of}", of != null && of.of == null)
            assertEquals(v.name, of!!.kind, v.kind)
            assertEquals(v.name, of.size, v.size)
            assertTrue("${v.name} has no chip label", !v.variant.isNullOrBlank())
        }
        for ((of, variants) in ReferenceExamples.variants.groupBy { it.of }) {
            assertEquals(
                "labels of $of",
                variants.map { it.variant }.distinct(),
                variants.map { it.variant })
        }
    }
}
