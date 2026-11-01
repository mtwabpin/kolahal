package com.mtwabpin.kolahal.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Material screens use Material 3 Expressive components only: docs/developing.org has what to use instead. */
class ExpressiveOnlyTest {
    private val folder = File("src/main/java/com/mtwabpin/kolahal/ui")

    /** Part of the terminal, which draws its own look in its own colours. */
    private val terminal = setOf("SuggestionList.kt")

    private val older = Regex("""(?<![A-Za-z.])(OutlinedTextField|SegmentedButton|SingleChoiceSegmentedButtonRow|ExposedDropdownMenuBox|DropdownMenu|AssistChip|SuggestionChip|TopAppBar|ExtendedFloatingActionButton)\(""")
    private val needShapes = Regex("""(?<![A-Za-z.])(Button|TextButton|FilledTonalButton|OutlinedButton|ElevatedButton|IconButton|OutlinedIconButton|FilledIconButton|FilledTonalIconButton|IconToggleButton)\(""")

    /** What's inside the brackets opening just before [start]. */
    private fun arguments(text: String, start: Int): String {
        var depth = 1
        var i = start
        while (i < text.length && depth > 0) {
            if (text[i] == '(') depth++ else if (text[i] == ')') depth--
            i++
        }
        return text.substring(start, i)
    }

    /** Every older component or button without its Expressive shapes in [text]. */
    private fun problems(text: String): List<String> =
        older.findAll(text).map { it.groupValues[1] }.toList() +
            needShapes.findAll(text).filter { !arguments(text, it.range.last + 1).contains(Regex("""\bshapes =""")) }.map { "${it.groupValues[1]} without shapes" }

    @Test fun materialScreensUseExpressiveComponentsOnly() {
        val files = folder.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue("no sources at ${folder.absolutePath}", files.size > 20)
        val found = files.filter { it.name !in terminal }.associate { it.name to problems(it.readText()) }.filterValues { it.isNotEmpty() }
        assertEquals("Use the Expressive components: docs/developing.org, Common changes", emptyMap<String, List<String>>(), found)
    }

    @Test fun theCheckFindsWhatItShould() {
        assertEquals(listOf("OutlinedTextField"), problems("OutlinedTextField(value = a, onValueChange = {})"))
        assertEquals(listOf("TextButton without shapes"), problems("TextButton(onClick = { go(1) }) { Text(\"x\") }"))
        assertEquals(emptyList<String>(), problems("TextButton(shapes = ButtonDefaults.shapes(), onClick = { go(1) }) { Text(\"x\") }"))
        assertEquals(emptyList<String>(), problems("ToggleButton(checked = a, onCheckedChange = {}) {}\nfun BackButton(onClick: () -> Unit) {}"))
        assertEquals(emptyList<String>(), problems("LargeFlexibleTopAppBar(title = {})\nSmallExtendedFloatingActionButton(text = {}, icon = {}, onClick = {})"))
    }
}
