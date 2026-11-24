package com.mtwabpin.kolahal.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The form of a setting's description ([ConfigKey.help]): short sentences, one part to a line. */
class SettingHelpTest {
    /** Sentences, not clauses chained with semicolons, and no sentence inside brackets. */
    @Test
    fun noSemicolonChains() {
        for (key in Keys.ALL) {
            assertFalse("${key.key}: '${key.help}' has a semicolon", ';' in key.help)
            assertFalse("${key.key}: '${key.help}' has a sentence in brackets", SENTENCE_IN_BRACKETS.containsMatchIn(key.help))
        }
    }

    /**
     * Every line but the last ends with a full stop, so the description reads well where a line
     * break shows as a space. The last one ends without one, as a one-line description does.
     */
    @Test
    fun linesEndAsSentences() {
        for (key in Keys.ALL) {
            val lines = key.help.split('\n')
            for (line in lines) {
                assertTrue("${key.key}: an empty line in '${key.help}'", line.isNotBlank())
                assertTrue("${key.key}: '$line' has spaces at an end", line == line.trim())
            }
            for (line in lines.dropLast(1)) assertTrue("${key.key}: '$line' ends without a full stop", line.endsWith("."))
            assertFalse("${key.key}: '${lines.last()}' ends with a full stop", lines.last().endsWith("."))
        }
    }

    private companion object {
        val SENTENCE_IN_BRACKETS = Regex("""\([^)]*\. [^)]*\)""")
    }
}
