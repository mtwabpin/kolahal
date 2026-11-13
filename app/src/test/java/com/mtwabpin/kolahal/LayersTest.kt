package com.mtwabpin.kolahal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Model (storage, scripts, apps, Android services) never reaches up into the screen: not the
 * Compose code in ui/, and not TerminalViewModel. It may read the command facts in terminal/
 * (CommandCatalog, InstantRun, LogFilter, CommandsOff): those are plain objects, not the ViewModel.
 * docs/codebase-guide.org explains the layers.
 */
class LayersTest {
    private val model = listOf(
        "data", "script", "apps", "feeds", "productivity", "notifications", "calls", "contacts", "unlock", "status", "control", "widgets",
    )

    @Test fun theModelNeverReachesTheScreen() {
        val above = Regex("""com\.mtwabpin\.kolahal\.ui\.|\bTerminalViewModel\b""")
        val found = model.flatMap { pkg ->
            sourceFile("src/main/java/com/mtwabpin/kolahal/$pkg").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
                f.readLines().withIndex()
                    // KDoc links ([com.mtwabpin.kolahal.ui.TermFonts]) only point the reader there.
                    .filter { (_, line) -> above.containsMatchIn(line.substringBefore("//")) && !line.trimStart().startsWith("*") }
                    .map { (i, line) -> "$pkg/${f.name}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertEquals(emptyList<String>(), found)
    }
}
