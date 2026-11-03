package com.mtwabpin.kolahal.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Material screens use Material 3 Expressive components only: docs/developing.org has what to use instead. */
class ExpressiveOnlyTest {
    private val root = File("src/main/java/com/mtwabpin/kolahal")

    /** Part of the terminal, which draws its own look in its own colours. */
    private val terminal = setOf("SuggestionList.kt")

    /** A Material name called, bare or written out (`androidx.compose.material3.TextField(`). */
    private fun calls(names: String) = Regex("""(?<![A-Za-z.])(?:androidx\.compose\.material3\.)?($names)\(""")

    private val older = calls(
        "OutlinedTextField|OutlinedSecureTextField|SegmentedButton|SingleChoiceSegmentedButtonRow|MultiChoiceSegmentedButtonRow|" +
            "ExposedDropdownMenuBox|DropdownMenu|AssistChip|ElevatedAssistChip|SuggestionChip|ElevatedSuggestionChip|" +
            "TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar|ExtendedFloatingActionButton|ListItem",
    )
    private val needShapes = calls(
        "Button|TextButton|FilledTonalButton|OutlinedButton|ElevatedButton|IconButton|OutlinedIconButton|FilledIconButton|" +
            "FilledTonalIconButton|IconToggleButton|FilledIconToggleButton|FilledTonalIconToggleButton|OutlinedIconToggleButton",
    )
    private val textField = calls("TextField")

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

    /** Every older component, button without its Expressive shapes, and text field not shaped and coloured as the others in [text]. */
    private fun problems(text: String): List<String> =
        older.findAll(text).map { it.groupValues[1] }.toList() +
            needShapes.findAll(text).mapNotNull { m ->
                val args = arguments(text, m.range.last + 1)
                val name = m.groupValues[1]
                when {
                    !args.contains(Regex("""\bshapes =""")) -> "$name without shapes"
                    name.endsWith("ToggleButton") && "toggleableShapes()" !in args -> "$name without toggleableShapes"
                    else -> null
                }
            }.toList() +
            textField.findAll(text).filter { m -> arguments(text, m.range.last + 1).let { "TextFieldDefaults.roundedShape" !in it || "fieldColors()" !in it } }
                .map { "TextField without roundedShape and fieldColors()" }.toList()

    @Test fun materialScreensUseExpressiveComponentsOnly() {
        // Every file that uses Material 3, in any folder.
        val files = root.walkTopDown().filter { it.extension == "kt" && "androidx.compose.material3" in it.readText() }.toList()
        assertTrue("no sources at ${root.absolutePath}", files.size > 20)
        val found = files.filter { it.name !in terminal }.associate { it.name to problems(it.readText()) }.filterValues { it.isNotEmpty() }
        assertEquals("Use the Expressive components: docs/developing.org, Common changes", emptyMap<String, List<String>>(), found)
    }

    @Test fun theCheckFindsWhatItShould() {
        assertEquals(listOf("OutlinedTextField"), problems("OutlinedTextField(value = a, onValueChange = {})"))
        assertEquals(listOf("TextButton without shapes"), problems("TextButton(onClick = { go(1) }) { Text(\"x\") }"))
        assertEquals(emptyList<String>(), problems("TextButton(shapes = ButtonDefaults.shapes(), onClick = { go(1) }) { Text(\"x\") }"))
        assertEquals(emptyList<String>(), problems("ToggleButton(checked = a, onCheckedChange = {}) {}\nfun BackButton(onClick: () -> Unit) {}"))
        assertEquals(emptyList<String>(), problems("LargeFlexibleTopAppBar(title = {})\nSmallExtendedFloatingActionButton(text = {}, icon = {}, onClick = {})"))
        // Written out in full, as some files do.
        assertEquals(listOf("OutlinedTextField"), problems("androidx.compose.material3.OutlinedTextField(value = a, onValueChange = {})"))
        assertEquals(listOf("MediumTopAppBar"), problems("MediumTopAppBar(title = {})"))
        assertEquals(
            listOf("TextField without roundedShape and fieldColors()"),
            problems("TextField(colors = fieldColors(), shape = MaterialTheme.shapes.extraLarge, value = a, onValueChange = {})"),
        )
        assertEquals(emptyList<String>(), problems("TextField(colors = fieldColors(), shape = TextFieldDefaults.roundedShape, value = a, onValueChange = {})"))
        assertEquals(listOf("IconToggleButton without toggleableShapes"), problems("IconToggleButton(shapes = IconButtonDefaults.shapes(), checked = a, onCheckedChange = {}) {}"))
        assertEquals(emptyList<String>(), problems("SegmentedListItem(onClick = {}, shapes = s) {}\nBasicTextField(value = a, onValueChange = {})"))
    }
}
