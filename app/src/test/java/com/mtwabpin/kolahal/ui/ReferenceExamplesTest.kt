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
        for (ex in ReferenceExamples.widgets) for (lang in ScriptLang.entries) {
            assertTrue("${ex.name} in $lang is not in api.mdx:\n${ex.code(lang)}", ex.code(lang) in guide)
        }
    }

    @Test fun eachExampleHasItsOwnName() {
        val names = ReferenceExamples.widgets.map { it.name }
        assertEquals(names.distinct(), names)
    }
}
