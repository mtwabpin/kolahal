package com.mtwabpin.kolahal.ui

import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.documents.Documents
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForText
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The text editor in its own window, on real files: files in the phone's Downloads, reached through
 * the media store. Their addresses are `content:` ones that are not Kolahal's own, as a file manager
 * shares them, and Kolahal needs no permission for the files it made there itself.
 */
@RunWith(AndroidJUnit4::class)
class TextEditorActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val drafts = Documents.drafts(context)
    private val made = mutableListOf<Uri>()

    @After fun tearDown() {
        made.forEach {
            drafts.delete(it.toString())
            runCatching { resolver.delete(it, null, null) }
        }
    }

    /** A file in Downloads holding [bytes]. Its name is new each time, so the media store doesn't change it. */
    private fun file(ending: String, bytes: ByteArray, type: String = "text/plain"): Uri {
        assumeTrue("the media store takes any file in Downloads from Android 10", Build.VERSION.SDK_INT >= 29)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "kolahal-editor-test-${System.nanoTime()}$ending")
            put(MediaStore.MediaColumns.MIME_TYPE, type)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        made += uri
        resolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
        return uri
    }

    private fun file(text: String) = file(".txt", text.toByteArray())

    private fun name(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
        it.moveToFirst()
        it.getString(0)
    }

    private fun onDisk(uri: Uri) = resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }

    /** Another app writes the file. */
    private fun change(uri: Uri, text: String) = resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }

    private fun launch(uri: Uri): ActivityScenario<TextEditorActivity> =
        ActivityScenario.launch(Intent(Intent.ACTION_VIEW, uri, context, TextEditorActivity::class.java))

    /** Opens [uri] and waits for its text to show. */
    private fun edit(uri: Uri): ActivityScenario<TextEditorActivity> = launch(uri).also {
        compose.waitFor("the editor shows the file", 10_000) { compose.onAllNodesWithTag("editor").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun text() = compose.onNodeWithTag("editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun type(text: String) = compose.onNodeWithTag("editor").performTextReplacement(text)

    private fun message() = runCatching {
        compose.onNodeWithTag("message").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
    }.getOrDefault("")

    @Test fun aFileOpensWithItsNameAndText() {
        val uri = file("one\ntwo\n")
        edit(uri).use {
            assertEquals("one\ntwo\n", text())
            compose.onNodeWithText(name(uri)).assertExists()
        }
    }

    /**
     * Home, and back: the window is stopped and shown again. It is the same window with the same
     * text, the draft was written when it was hidden, and the file was not touched.
     */
    @Test fun typedTextIsThereAfterHomeAndBack() {
        val uri = file("one\n")
        edit(uri).use { scenario ->
            type("one\nand more")
            compose.onNodeWithText("${name(uri)} *").assertExists()
            var window: TextEditorActivity? = null
            scenario.onActivity { window = it }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertEquals("one\nand more", drafts.read(uri.toString())?.text)
            assertEquals("one\n", onDisk(uri))
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertSame(window, it) }
            assertEquals("one\nand more", text())
            compose.onNodeWithText("${name(uri)} *").assertExists()
        }
    }

    /** Save writes the file with its own line endings and its line break at the end, and the draft goes. */
    @Test fun savingWritesTheFileInItsOwnFormat() {
        val uri = file("one\r\ntwo\r\n")
        edit(uri).use {
            assertEquals("one\ntwo\n", text())
            type("one\ntwo\nthree")
            compose.onNodeWithText("[save]").performClick()
            compose.waitFor("saved") { message() == "Saved" }
            assertEquals("one\r\ntwo\r\nthree\r\n", onDisk(uri))
            // The editor shows what the file holds now, and nothing is unsaved.
            assertEquals("one\ntwo\nthree\n", text())
            compose.onNodeWithText(name(uri)).assertExists()
            assertFalse(drafts.has(uri.toString()))
        }
    }

    /** The editor is closed by Android with unsaved text: a new window for the file brings the text back. */
    @Test fun unsavedTextComesBackInANewWindow() {
        val uri = file("one\n")
        edit(uri).use { scenario ->
            type("never saved")
            scenario.moveToState(Lifecycle.State.CREATED)
        }
        edit(uri).use {
            assertEquals("never saved", text())
            compose.onNodeWithText("${name(uri)} *").assertExists()
            assertEquals("Your unsaved changes are back", message())
            assertEquals("one\n", onDisk(uri))
        }
    }

    /** Android builds the window again (a new language, a font size): the text and the undo history stay. */
    @Test fun theTextAndUndoLiveThroughARebuiltWindow() {
        val uri = file("one\n")
        edit(uri).use { scenario ->
            type("one\ntwo")
            scenario.recreate()
            compose.waitFor("the editor is back") { compose.onAllNodesWithTag("editor").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("one\ntwo", text())
            compose.onNodeWithText("↶").performClick()
            assertEquals("one\n", text())
        }
    }

    /** Another app changed the file while it was open: save asks, and writes only after Overwrite. */
    @Test fun aChangedFileIsNotOverwrittenUnasked() {
        val uri = file("one\n")
        edit(uri).use {
            type("mine")
            change(uri, "theirs, and longer\n")
            compose.onNodeWithText("[save]").performClick()
            compose.waitForText("The file has changed")
            assertEquals("theirs, and longer\n", onDisk(uri))
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("theirs, and longer\n", onDisk(uri))
            compose.onNodeWithText("[save]").performClick()
            compose.waitForText("The file has changed")
            compose.onNodeWithText("Overwrite").performClick()
            compose.waitFor("saved") { message() == "Saved" }
            assertEquals("mine\n", onDisk(uri))
        }
    }

    /** Close with unsaved text asks. Cancel stays; Discard closes the window, and neither the file nor a draft has the text. */
    @Test fun closeAsksAboutUnsavedText() {
        val uri = file("one\n")
        edit(uri).use { scenario ->
            type("thrown away")
            compose.onNodeWithText("[close]").performClick()
            compose.waitForText("Save changes?")
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("thrown away", text())
            compose.onNodeWithText("[close]").performClick()
            compose.waitForText("Save changes?")
            compose.onNodeWithText("Discard").performClick()
            waitUntil("the window closed") { scenario.state == Lifecycle.State.DESTROYED }
            waitUntil("the draft is gone") { !drafts.has(uri.toString()) }
            assertEquals("one\n", onDisk(uri))
        }
    }

    /** A file that is not text doesn't open in the editor, and the page says why. */
    @Test fun aBinaryFileIsRefused() {
        val uri = file(".bin", byteArrayOf(0x50, 0x4B, 3, 4, 0, 0, 0, 8), "application/octet-stream")
        launch(uri).use {
            compose.waitForText("It is not a text file: it holds binary data.", ms = 10_000)
            compose.onNodeWithText("Can't edit ${name(uri)}").assertExists()
            compose.onNodeWithTag("editor").assertDoesNotExist()
        }
    }

    /** A file whose app is gone: the page says it can't be opened. */
    @Test fun aFileOutOfReachSaysSo() {
        launch(Uri.parse("content://com.mtwabpin.kolahal.test.no.such.app/notes.txt")).use {
            compose.waitForText("Can't open notes.txt", ms = 10_000)
            compose.onNodeWithTag("editor").assertDoesNotExist()
        }
    }

    /** Another app can't make the editor open Kolahal's own files, or a path on disk. */
    @Test fun kolahalsOwnFilesAreRefused() {
        launch(Uri.parse("file://${context.filesDir}/crash/report.txt")).use {
            compose.waitForText("Kolahal opens files from a file picker or another app (content: addresses) only.", ms = 10_000)
        }
        // The full build shares its downloaded updates through a provider of its own.
        val own = context.packageManager.resolveContentProvider("${context.packageName}.fileprovider", 0)
        assumeTrue("this build has no provider of its own", own != null)
        launch(Uri.parse("content://${context.packageName}.fileprovider/updates/kolahal.apk")).use {
            compose.waitForText("That is one of Kolahal's own files.", ms = 10_000)
        }
    }

    /** What the installed app says of the editor's window: a task of its own in Recents, one per file, and "Open with" for text. */
    @Test fun theEditorHasItsOwnTaskAndOpensTextFromOtherApps() {
        val pm = context.packageManager
        val editor = pm.getActivityInfo(android.content.ComponentName(context, TextEditorActivity::class.java), 0)
        val home = pm.getActivityInfo(android.content.ComponentName(context, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.DOCUMENT_LAUNCH_INTO_EXISTING, editor.documentLaunchMode)
        assertNotEquals(home.taskAffinity, editor.taskAffinity)
        assertEquals(0, editor.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS)
        assertTrue(editor.exported)

        fun opens(type: String) = pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://some.app/file"), type).setPackage(context.packageName),
            PackageManager.MATCH_DEFAULT_ONLY,
        ).any { it.activityInfo.name == TextEditorActivity::class.java.name }
        for (type in listOf("text/plain", "text/markdown", "application/json", "application/xml")) assertTrue(type, opens(type))
        for (type in listOf("image/png", "application/zip", "application/octet-stream")) assertFalse(type, opens(type))
        // It reaches files through the picker or the sharing app only.
        assertEquals(PackageManager.PERMISSION_DENIED, pm.checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, context.packageName))
    }
}
