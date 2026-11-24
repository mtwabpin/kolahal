package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Material screens use Material 3 Expressive components only: docs/developing.org has what to use instead. */
class ExpressiveOnlyTest {
    private val root = sourceFile("src/main/java/com/mtwabpin/kolahal")

    /** Part of the terminal, which draws its own look in its own colors. A path under [root]. */
    private val terminal = setOf("ui/home/SuggestionList.kt")

    /** Where the app's themes are made: the only files that may call `MaterialTheme(`. */
    private val themes = "ui/theme/"

    /** A Material name called, bare or written out (`androidx.compose.material3.TextField(`). */
    private fun calls(names: String) =
        Regex("""(?<![A-Za-z.])(?:androidx\.compose\.material3\.)?($names)\(""")

    private val older = calls(
        "OutlinedTextField|OutlinedSecureTextField|SegmentedButton|SingleChoiceSegmentedButtonRow|MultiChoiceSegmentedButtonRow|" +
                "ExposedDropdownMenuBox|DropdownMenu|AssistChip|ElevatedAssistChip|SuggestionChip|ElevatedSuggestionChip|" +
                "TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar|ExtendedFloatingActionButton|" +
                "TimePickerDialog|LinearProgressIndicator|CircularProgressIndicator",
    )

    /** Material's list item. Its older form takes `headlineContent`; the Expressive ones take `content`. */
    private val listItem = calls("ListItem")
    private val needShapes = calls(
        "Button|TextButton|FilledTonalButton|OutlinedButton|ElevatedButton|IconButton|OutlinedIconButton|FilledIconButton|" +
                "FilledTonalIconButton|IconToggleButton|FilledIconToggleButton|FilledTonalIconToggleButton|OutlinedIconToggleButton|" +
                "FilterChip|ElevatedFilterChip|InputChip|TimePicker|TimeInput",
    )
    private val textField = calls("TextField|SecureTextField")
    private val theme = calls("MaterialTheme")

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

    /**
     * Every older component, button, chip or time picker without its Expressive shapes, and text field
     * not shaped and colored as the others in [text]. Outside the theme files ([inTheme] false), a
     * `MaterialTheme(` call too: a screen's theme comes from `GuiTheme`.
     */
    private fun problems(text: String, inTheme: Boolean = false): List<String> =
        older.findAll(text).map { it.groupValues[1] }.toList() +
                listItem.findAll(text)
                    .filter { Regex("""\bheadlineContent\b""") in arguments(text, it.range.last + 1) }
                    .map { "ListItem with headlineContent" }.toList() +
                needShapes.findAll(text).mapNotNull { m ->
                    val args = arguments(text, m.range.last + 1)
                    val name = m.groupValues[1]
                    when {
                        !args.contains(Regex("""\bshapes =""")) -> "$name without shapes"
                        name.endsWith("ToggleButton") && "toggleableShapes()" !in args -> "$name without toggleableShapes"
                        else -> null
                    }
                }.toList() +
                textField.findAll(text).filter { m ->
                    arguments(
                        text,
                        m.range.last + 1
                    ).let { "TextFieldDefaults.roundedShape" !in it || "fieldColors()" !in it }
                }
                    .map { "${it.groupValues[1]} without roundedShape and fieldColors()" }.toList() +
                (if (inTheme) emptyList<String>() else theme.findAll(text).map { "MaterialTheme outside $themes" }.toList())

    @Test
    fun materialScreensUseExpressiveComponentsOnly() {
        // Every file that uses Material 3, in any folder, by its path: two files can have one name.
        val files = root.walkTopDown()
            .filter { it.extension == "kt" && "androidx.compose.material3" in it.readText() }
            .associateBy { it.relativeTo(root).invariantSeparatorsPath }
        assertTrue("no sources at ${root.absolutePath}", files.size > 20)
        val found = files.filterKeys { it !in terminal }
            .mapValues { (path, file) -> problems(file.readText(), inTheme = path.startsWith(themes)) }
            .filterValues { it.isNotEmpty() }
        assertEquals(
            "Use the Expressive components: docs/developing.org, Common changes",
            emptyMap<String, List<String>>(),
            found
        )
    }

    @Test
    fun theCheckFindsWhatItShould() {
        assertEquals(
            listOf("OutlinedTextField"),
            problems("OutlinedTextField(value = a, onValueChange = {})")
        )
        assertEquals(
            listOf("TextButton without shapes"),
            problems("TextButton(onClick = { go(1) }) { Text(\"x\") }")
        )
        assertEquals(
            emptyList<String>(),
            problems("TextButton(shapes = ButtonDefaults.shapes(), onClick = { go(1) }) { Text(\"x\") }")
        )
        assertEquals(
            emptyList<String>(),
            problems("ToggleButton(checked = a, onCheckedChange = {}) {}\nfun BackButton(onClick: () -> Unit) {}")
        )
        assertEquals(
            emptyList<String>(),
            problems("LargeFlexibleTopAppBar(title = {})\nSmallExtendedFloatingActionButton(text = {}, icon = {}, onClick = {})")
        )
        // Written out in full, as some files do.
        assertEquals(
            listOf("OutlinedTextField"),
            problems("androidx.compose.material3.OutlinedTextField(value = a, onValueChange = {})")
        )
        assertEquals(listOf("MediumTopAppBar"), problems("MediumTopAppBar(title = {})"))
        assertEquals(
            listOf("TextField without roundedShape and fieldColors()"),
            problems("TextField(colors = fieldColors(), shape = MaterialTheme.shapes.extraLarge, value = a, onValueChange = {})"),
        )
        assertEquals(
            emptyList<String>(),
            problems("TextField(colors = fieldColors(), shape = TextFieldDefaults.roundedShape, value = a, onValueChange = {})")
        )
        assertEquals(
            listOf("IconToggleButton without toggleableShapes"),
            problems("IconToggleButton(shapes = IconButtonDefaults.shapes(), checked = a, onCheckedChange = {}) {}")
        )
        assertEquals(
            emptyList<String>(),
            problems("SegmentedListItem(onClick = {}, shapes = s) {}\nBasicTextField(value = a, onValueChange = {})")
        )
    }

    /** Chips and the time picker need their shapes; the older time picker dialog and progress bars are named. */
    @Test
    fun theCheckFindsChipsPickersAndProgress() {
        assertEquals(
            listOf("FilterChip without shapes", "InputChip without shapes", "ElevatedFilterChip without shapes"),
            problems(
                "FilterChip(selected = a, onClick = {}, label = {})\n" +
                        "InputChip(selected = a, onClick = {}, label = {})\n" +
                        "androidx.compose.material3.ElevatedFilterChip(selected = a, onClick = {}, label = {})"
            ),
        )
        assertEquals(
            emptyList<String>(),
            problems("FilterChip(selected = a, onClick = {}, label = {}, shapes = FilterChipDefaults.shapes())")
        )
        assertEquals(
            listOf("TimePickerDialog", "TimePicker without shapes", "TimeInput without shapes"),
            problems("TimePickerDialog(onDismissRequest = {}, confirmButton = {}) { TimePicker(clock); TimeInput(clock) }"),
        )
        assertEquals(
            emptyList<String>(),
            problems(
                "VibrantTimePickerDialog(onDismissRequest = {}, confirmButton = {}) {\n" +
                        "    TimePicker(state = clock, shapes = TimePickerDefaults.shapes())\n}"
            ),
        )
        assertEquals(
            listOf("LinearProgressIndicator", "CircularProgressIndicator"),
            problems("LinearProgressIndicator(Modifier.fillMaxWidth())\nCircularProgressIndicator()"),
        )
        assertEquals(
            emptyList<String>(),
            problems("LinearWavyProgressIndicator(Modifier.fillMaxWidth())\nCircularWavyProgressIndicator()\nLoadingIndicator()"),
        )
    }

    /**
     * Only the older list item is named, not the Expressive ones; a secure field is a text field; a
     * theme is made in the theme files only.
     */
    @Test
    fun theCheckFindsListItemsSecureFieldsAndThemes() {
        assertEquals(
            listOf("ListItem with headlineContent"),
            problems("ListItem(headlineContent = { Text(\"x\") }, supportingContent = { Text(\"y\") })"),
        )
        assertEquals(
            emptyList<String>(),
            problems(
                "ListItem(checked = a, onCheckedChange = {}, supportingContent = { Text(\"y\") }) { Text(\"x\") }\n" +
                        "ListItem(onClick = {}, shapes = ListItemDefaults.shapes()) { Text(\"x\") }"
            ),
        )
        assertEquals(
            listOf("SecureTextField without roundedShape and fieldColors()"),
            problems("SecureTextField(state = secret, colors = fieldColors())"),
        )
        assertEquals(
            emptyList<String>(),
            problems("SecureTextField(state = secret, shape = TextFieldDefaults.roundedShape, colors = fieldColors())")
        )
        assertEquals(
            listOf("MaterialTheme outside ui/theme/"),
            problems("MaterialTheme(colorScheme = scheme) { Text(\"x\", color = MaterialTheme.colorScheme.primary) }"),
        )
        assertEquals(
            emptyList<String>(),
            problems("MaterialTheme(colorScheme = scheme) {}", inTheme = true)
        )
        assertEquals(
            emptyList<String>(),
            problems("MaterialExpressiveTheme(colorScheme = scheme) { Text(\"x\", color = MaterialTheme.colorScheme.primary) }")
        )
    }
}
