package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the manifest must say for a file to stay open in the editor when you go home. Home's task
 * is cleared on Home and kept out of Recents, so the editor must not be part of it.
 */
class TextEditorManifestTest {
    private val manifest = sourceFile("src/main/AndroidManifest.xml").readText()

    /** The `<activity>` element named [name], up to its end tag. */
    private fun activity(name: String): String {
        val start = manifest.indexOf("android:name=\"$name\"")
            .also { assertTrue("no activity $name", it >= 0) }
        return manifest.substring(
            manifest.lastIndexOf("<activity", start),
            manifest.indexOf("</activity>", start)
        )
    }

    private val editor = activity(".ui.TextEditorActivity")
    private val home = activity(".MainActivity")

    @Test
    fun theEditorHasATaskOfItsOwnInRecents() {
        // Home's task is the one that loses its screens: the test would mean nothing if that changed.
        assertTrue(
            home,
            "android:clearTaskOnLaunch=\"true\"" in home && "android:excludeFromRecents=\"true\"" in home
        )
        assertTrue(editor, "android:taskAffinity=\"\${applicationId}.editor\"" in editor)
        assertFalse(
            editor,
            "excludeFromRecents" in editor || "clearTaskOnLaunch" in editor || "noHistory" in editor
        )
        // Each file is its own task, and a file that is open comes back instead of opening twice.
        assertTrue(editor, "android:documentLaunchMode=\"intoExisting\"" in editor)
        // A document can only be launched that way from the standard launch mode.
        assertFalse(editor, "android:launchMode" in editor)
        assertTrue(editor, "android:label=\"@string/editor_name\"" in editor)
    }

    /** A rotation or a dark mode change doesn't build the window again, as for home. */
    @Test
    fun theEditorHandlesTheSameChangesAsHome() {
        val changes = Regex("""android:configChanges="([^"]+)"""")
        assertEquals(changes.find(home)!!.groupValues[1], changes.find(editor)!!.groupValues[1])
    }

    /** Other apps open text files with it, by `content:` addresses only. */
    @Test
    fun otherAppsOpenTextFilesWithIt() {
        assertTrue(editor, "android:exported=\"true\"" in editor)
        for (action in listOf("VIEW", "EDIT")) assertTrue(
            action,
            "<action android:name=\"android.intent.action.$action\" />" in editor
        )
        assertEquals(
            listOf("content"),
            Regex("""android:scheme="([^"]+)"""").findAll(editor).map { it.groupValues[1] }.toList()
        )
        val types =
            Regex("""android:mimeType="([^"]+)"""").findAll(editor).map { it.groupValues[1] }
                .toList()
        assertTrue(
            types.toString(),
            types.containsAll(listOf("text/*", "application/json", "application/xml"))
        )
        // Not every file on the phone: the editor would be offered for pictures and archives.
        assertFalse(types.toString(), "*/*" in types || "application/octet-stream" in types)
    }

    /** Files come through the file picker or the app that shares them: Kolahal asks for no storage permission. */
    @Test
    fun noStoragePermission() {
        assertEquals(
            emptyList<String>(),
            Regex("""android\.permission\.\w*(STORAGE|MEDIA)\w*""").findAll(manifest)
                .map { it.value }.toList()
        )
    }
}
