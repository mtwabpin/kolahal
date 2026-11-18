package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor reference's examples ([ReferenceExamples]) are the scripting guide's, word for word. */
class ReferenceExamplesTest {
    private val guide = sourceFile("../site/src/content/docs/scripting/api.mdx").readText()

    @Test fun theGuideHasEachExamplesCodeInBothLanguages() {
        val codes = ReferenceExamples.widgets.map { it.name to it::code } + ReferenceExamples.logs.map { it.name to it::code }
        for ((name, code) in codes) for (lang in ScriptLang.entries) {
            assertTrue("$name in $lang is not in api.mdx:\n${code(lang)}", code(lang) in guide)
        }
    }

    @Test fun eachExampleHasItsOwnName() {
        for (names in listOf(ReferenceExamples.widgets.map { it.name }, ReferenceExamples.logs.map { it.name })) assertEquals(names.distinct(), names)
    }
}
