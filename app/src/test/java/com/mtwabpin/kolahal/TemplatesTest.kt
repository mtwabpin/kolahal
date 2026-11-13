package com.mtwabpin.kolahal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In a string, `"$Stores.SCRIPT_PREFIX%"` puts in the Stores object itself, then the text
 * ".SCRIPT_PREFIX%": only `"${Stores.SCRIPT_PREFIX}%"` puts in the constant. That once made a full
 * restore delete every script's store. A template that names a class or object (a capital, then a
 * small letter) followed by a dot is that mistake. Constants in capitals (`"$SCRIPT.lua"`) are fine.
 */
class TemplatesTest {
    @Test fun noTemplateNamesAnObjectThenADot() {
        val object_ = Regex("""\$[A-Z][a-z][A-Za-z0-9]*\.[A-Za-z_]""")
        val found = listOf("src/main", "src/test", "src/androidTest").flatMap { dir ->
            sourceFile(dir).walkTopDown().filter { it.extension == "kt" && it.name != "TemplatesTest.kt" }.flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) -> line.contains('"') && object_.containsMatchIn(line.substringBefore("//")) }
                    .map { (i, line) -> "${f.name}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertEquals(emptyList<String>(), found)
    }
}
