package com.mtwabpin.kolahal.ui.reference

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
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

    /**
     * The fields that change nothing a card draws: what a tap does, when the script runs, what a
     * screen reader says, and an event's id, which isn't kept. The other fields each change the card.
     */
    private val notDrawn = setOf("kind", "tap", "staleAfter", "refresh", "summary", "id")

    /**
     * Each kind a script can use has an example, but `image`: the reference doesn't draw a picture,
     * as drawing it would fetch it. And the examples and variants of a kind, in both languages, show
     * each word of its `style` and each of its fields that changes what the card draws
     * ([WidgetParser.fieldsOf]). The common ones that do (`title`, `icon`, `color`, `shape`, `note`)
     * are each in an example of some kind.
     */
    @Test
    fun eachKindStyleAndFieldHasAnExample() {
        val all = ReferenceExamples.widgets + ReferenceExamples.variants
        val missing = mutableListOf<String>()
        for (lang in ScriptLang.entries) {
            // A field as the code writes it: `style = ` in Lua, `style: ` in JavaScript.
            val set = if (lang == ScriptLang.LUA) " = " else ": "
            fun writes(code: String, field: String) = Regex("\\b$field$set").containsMatchIn(code)
            for (kind in WidgetKind.forScripts - WidgetKind.IMAGE) {
                val code = all.filter { it.kind == kind }.joinToString("\n") { it.code(lang) }
                if (code.isEmpty()) missing += "${kind.id}: no example"
                for (word in WidgetParser.choicesOf(kind)["style"].orEmpty()) {
                    if ("style$set\"$word\"" !in code) missing += "${kind.id} in $lang: style $word"
                }
                for (field in WidgetParser.fieldsOf(kind) - notDrawn) {
                    if (!writes(code, field)) missing += "${kind.id} in $lang: $field"
                }
            }
            val code = all.joinToString("\n") { it.code(lang) }
            for (field in WidgetParser.COMMON_FIELDS - notDrawn) {
                if (!writes(code, field)) missing += "any kind in $lang: $field"
            }
        }
        assertEquals(emptyList<String>(), missing.distinct())
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
