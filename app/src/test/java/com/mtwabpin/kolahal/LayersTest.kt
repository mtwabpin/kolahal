package com.mtwabpin.kolahal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The layers depend downwards only: ui/ (the screen) on terminal/ (the view model and the command
 * handlers), and both on the Model (storage, scripts, apps, Android services, the command facts).
 * - The Model never reaches up: not into terminal/, and not into ui/. The command facts (the catalog,
 *   InstantRun, LogFilter, Pipes, …) are in commands/, part of the Model, so the Model may read them.
 * - terminal/ never reaches into ui/. What both need (key bar keys, header taps, font names, setting
 *   titles) lives in terminal/ or the Model, and ui/ reads it from there.
 * - The app's root files (LauncherApp, CrashReports, Links) sit with the Model: the screen's caches
 *   add themselves to LauncherApp ([com.mtwabpin.kolahal.LauncherApp.keepsInMemory]). MainActivity
 *   is the screen's, so it isn't checked.
 * docs/codebase-guide.org explains the layers.
 */
class LayersTest {
    private val model = listOf(
        "commands", "data", "script", "apps", "feeds", "productivity", "notifications", "calls", "contacts", "unlock", "status", "control",
        "widgets", "notify", "gesture",
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

    /** The screen's two layers: anything in ui/ or terminal/, by import or by full name. */
    private val above = Regex("""com\.mtwabpin\.kolahal\.(ui|terminal)\b|\bTerminalViewModel\b""")

    @Test fun theModelNeverReachesTheScreen() {
        assertEquals(emptyList<String>(), reaching(model, above))
    }

    /** Every package is listed: a new one must say which layer it is in. */
    @Test fun everyPackageHasALayer() {
        val packages = sourceFile("src/main/java/com/mtwabpin/kolahal").listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet()
        assertEquals(emptySet<String>(), packages - model.toSet() - setOf("terminal", "ui"))
    }

    @Test fun theAppsRootFilesSitWithTheModel() {
        val root = sourceFile("src/main/java/com/mtwabpin/kolahal").listFiles()!!.filter { it.isFile && it.extension == "kt" && it.name != "MainActivity.kt" }
        val found = root.flatMap { f ->
            f.readLines().withIndex()
                .filter { (_, line) -> above.containsMatchIn(line.substringBefore("//")) && !line.trimStart().startsWith("*") }
                .map { (i, line) -> "${f.name}:${i + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList<String>(), found)
    }

    /** Imports and full names alike: `import com.mtwabpin.kolahal.ui.X` and `com.mtwabpin.kolahal.ui.X(…)`. */
    @Test fun theTerminalNeverReachesTheScreenCode() {
        assertEquals(emptyList<String>(), reaching(listOf("terminal"), Regex("""com\.mtwabpin\.kolahal\.ui\b""")))
    }
}
