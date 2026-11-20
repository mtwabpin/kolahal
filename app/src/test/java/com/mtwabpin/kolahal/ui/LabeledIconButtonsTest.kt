package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every icon button shows its label on a long-press ([LabeledIconButton], [Labeled]). A Material
 * icon button called bare must be the first thing inside a `Labeled(…) {` (or, in EditorBars,
 * be the button that EditorIconButton labels itself), unless it is one of the few that have a
 * long-press of their own.
 */
class LabeledIconButtonsTest {
    private val root = sourceFile("src/main/java/com/mtwabpin/kolahal")

    private val iconButton = Regex(
        """(?<![A-Za-z.])(?:androidx\.compose\.material3\.)?(IconButton|OutlinedIconButton|FilledIconButton|FilledTonalIconButton|""" +
            """IconToggleButton|FilledIconToggleButton|FilledTonalIconToggleButton|OutlinedIconToggleButton)\(""",
    )

    /**
     * The bare icon buttons that show no label, by file, and why.
     * - EditorBars.kt: EditorIconButton, which puts its label on itself, except on a button with a
     *   long-press of its own (Run: Run with…).
     * - WidgetsScreen.kt: a step of a widget's size, which repeats while it is held.
     * - WidgetBodies.kt: a widget's buttons. A long-press on a card opens Arrange widgets.
     */
    private val unlabeled = mapOf("EditorBars.kt" to 1, "WidgetsScreen.kt" to 1, "WidgetBodies.kt" to 3)

    @Test fun everyIconButtonShowsItsLabel() {
        val bare = HashMap<String, Int>()
        val unwrapped = ArrayList<String>()
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val lines = f.readLines()
            lines.forEachIndexed { i, line ->
                if (!iconButton.containsMatchIn(line.substringBefore("//"))) return@forEachIndexed
                // The line before it opens a Labeled block: the button is labeled.
                val before = lines.subList(0, i).lastOrNull { it.isNotBlank() }?.trim().orEmpty()
                if (Regex("""Labeled\(.*\) \{$""").containsMatchIn(before)) return@forEachIndexed
                if (f.name in unlabeled) bare[f.name] = (bare[f.name] ?: 0) + 1
                else unwrapped += "${f.name}:${i + 1}: ${line.trim()}"
            }
        }
        assertEquals("icon buttons with no label on a long-press: use LabeledIconButton or Labeled", emptyList<String>(), unwrapped)
        assertEquals(unlabeled, bare)
    }
}
