package com.mtwabpin.kolahal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The layers depend downwards only: ui/ (the screen) on terminal/ (the view model and commands), and
 * both on the Model (storage, scripts, apps, Android services).
 * - The Model never reaches up: not the Compose code in ui/, and not TerminalViewModel. It may read
 *   the command facts in terminal/ (CommandCatalog, InstantRun, LogFilter, CommandsOff): those are
 *   plain objects, not the ViewModel.
 * - terminal/ never reaches into ui/. What both need (key bar keys, header taps, font names, setting
 *   titles) lives in terminal/ or the Model, and ui/ reads it from there.
 * docs/codebase-guide.org explains the layers.
 */
class LayersTest {
    private val model = listOf(
        "data", "script", "apps", "feeds", "productivity", "notifications", "calls", "contacts", "unlock", "status", "control", "widgets",
    )

    /** Lines in [packages] that name something matching [above], outside comments. */
    private fun reaching(packages: List<String>, above: Regex) = packages.flatMap { pkg ->
        sourceFile("src/main/java/com/mtwabpin/kolahal/$pkg").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex()
                // KDoc links ([com.mtwabpin.kolahal.ui.TermFonts]) only point the reader there.
                .filter { (_, line) -> above.containsMatchIn(line.substringBefore("//")) && !line.trimStart().startsWith("*") }
                .map { (i, line) -> "$pkg/${f.name}:${i + 1}: ${line.trim()}" }
        }.toList()
    }

    @Test fun theModelNeverReachesTheScreen() {
        assertEquals(emptyList<String>(), reaching(model, Regex("""com\.mtwabpin\.kolahal\.ui\.|\bTerminalViewModel\b""")))
    }

    /** Imports and full names alike: `import com.mtwabpin.kolahal.ui.X` and `com.mtwabpin.kolahal.ui.X(…)`. */
    @Test fun theTerminalNeverReachesTheScreenCode() {
        assertEquals(emptyList<String>(), reaching(listOf("terminal"), Regex("""com\.mtwabpin\.kolahal\.ui\b""")))
    }
}
