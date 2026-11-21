package com.mtwabpin.kolahal.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.mtwabpin.kolahal.documents.ContentDocuments
import com.mtwabpin.kolahal.documents.Documents
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Files that Android gives the editor read-only, on real addresses:
 * - a file picked under Recent in Android's file picker (a media documents address);
 * - a file another app shares to read only ("Open with", as Samsung's My Files does).
 *
 * The files are made by the shell, as another app's file would be. Kolahal does not own them, so
 * it may write them only with the permission Android gives.
 */
@RunWith(AndroidJUnit4::class)
class ReadOnlyFilesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val device = UiDevice.getInstance(instrumentation)
    private val drafts = Documents.drafts(context)

    /** Files made in Download, by name, and every address the test opened. */
    private val made = mutableListOf<String>()
    private val addresses = mutableListOf<String>()

    @After
    fun tearDown() {
        addresses.forEach {
            drafts.delete(it)
            Documents.forget(context, it)
        }
        made.forEach { shell("rm /sdcard/Download/$it") }
    }

    /** A text file in Download, made by the shell. Returns its name and its media store ID. */
    private fun othersFile(text: String): Pair<String, Long> {
        // Android 11 has the picker's Documents and Recent for any text file, and MediaStore.getMediaUri.
        assumeTrue("media documents of text files from Android 11", Build.VERSION.SDK_INT >= 30)
        val name = "kolahal-ro-test-${System.nanoTime()}.txt"
        val (out, input, err) = instrumentation.uiAutomation.executeShellCommandRwe("tee /sdcard/Download/$name")
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write(text.toByteArray()) }
        ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
        err.close()
        made += name
        // The media store lists it once it is scanned.
        shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/$name")
        val id = waitForValue("the media store lists $name", 15_000) {
            val row =
                shell("content query --uri content://media/external/file --projection _id:is_pending --where _display_name='$name'")
            Regex("""_id=(\d+), is_pending=0""").find(row)?.groupValues?.get(1)?.toLong()
        }
        return name to id
    }

    private fun onDisk(name: String) = shell("cat /sdcard/Download/$name")

    private fun read(uri: Uri) =
        resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }

    /**
     * Taps what [selector] finds on Android's screens, once it shows; fails with [what] after 10
     * seconds. The picker redraws its lists as they load, so a found item can be gone at the tap:
     * then it is found again.
     */
    private fun tap(what: String, selector: BySelector) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            try {
                device.wait(Until.findObject(selector), 1_000)?.let {
                    it.click()
                    return
                }
            } catch (_: StaleObjectException) {
                // Redrawn between finding and tapping: look again.
            }
        }
        fail(what)
    }

    /** Picks the file [name] under Recent in Android's file picker, and returns the address the picker gives. */
    private fun pickInRecent(name: String): Uri {
        val picked = ArrayBlockingQueue<Uri>(1)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.activityResultRegistry.register(
                    "pick",
                    ActivityResultContracts.OpenDocument()
                ) { picked.put(it ?: Uri.EMPTY) }
                    .launch(arrayOf("text/plain"))
            }
            tap("the picker shows its roots", By.desc("Show roots"))
            tap("the picker has Recent", By.text("Recent"))
            // Recent redraws its list as it loads, and a tap during a redraw can be lost: then the
            // picker never answers. So the file is tapped again until it does.
            // Once the picker has answered, the file is gone from the screen: a later tap is skipped.
            tap("Recent lists $name", By.text(name))
            var answer: Uri? = picked.poll(4, TimeUnit.SECONDS)
            val until = SystemClock.uptimeMillis() + 16_000
            while (answer == null && SystemClock.uptimeMillis() < until) {
                try {
                    device.findObject(By.text(name))?.click()
                } catch (_: StaleObjectException) {
                    // Redrawn between finding and tapping: the next round finds it again.
                }
                answer = picked.poll(4, TimeUnit.SECONDS)
            }
            val uri = answer.also { assertNotNull("the picker gave an address", it) }!!
            addresses += uri.toString()
            // As `edit` does: the permission ends with this window unless it is kept.
            assertTrue(Documents.keepPermission(context, uri))
            return uri
        }
    }

    private fun text() = compose.onNodeWithTag("editor")
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun message() = runCatching {
        compose.onNodeWithTag("message")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
    }.getOrDefault("")

    private fun editors() = compose.onAllNodesWithTag("editor").fetchSemanticsNodes().size

    /**
     * A file picked under Recent: its app says it can't be written, but the editor saves it in place.
     * The file keeps its own address for the draft and the recent list. Only a write may go to its
     * media store address.
     */
    @Test
    fun aFileFromRecentSavesInPlace() {
        val (name, _) = othersFile("one\n")
        val doc = pickInRecent(name)
        assertEquals(ContentDocuments.MEDIA_DOCUMENTS, doc.authority)

        // What the Model says of it.
        val source = Documents.source(context)
        val info = source.info(doc.toString())
        assertEquals(name, info.name)
        assertTrue(info.writable)
        assertTrue(source.reopenHelps(doc))
        assertEquals(
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download"
            ), source.folderOf(doc)
        )

        // The way a write takes when the address itself refuses: the media store address, with the same permission.
        val media = MediaStore.getMediaUri(context, doc)!!
        assertEquals(MediaStore.AUTHORITY, media.authority)
        resolver.openOutputStream(media, "wt")!!
            .use { it.write("through the media store\n".toByteArray()) }
        assertEquals("through the media store\n", read(doc))

        ActivityScenario.launch<TextEditorActivity>(
            Intent(
                Intent.ACTION_EDIT,
                doc,
                context,
                TextEditorActivity::class.java
            )
        ).use {
            compose.waitFor("the editor shows the file", 10_000) { editors() == 1 }
            assertEquals("through the media store\n", text())
            assertFalse(message(), "read-only" in message() || "can't write" in message())
            compose.onNodeWithTag("editor").performTextReplacement("mine")
            compose.onNodeWithContentDescription("Save").performClick()
            compose.waitFor("saved") { message() == "Saved" }
            assertEquals("mine\n", onDisk(name))
        }
        // Everything is kept under the picker's address, not the media store's.
        assertFalse(drafts.has(doc.toString()))
        assertTrue(drafts.list().none { it.uri.startsWith("content://media/") })
        assertTrue(Documents.recent(context).list().any { it.uri == doc.toString() })
        assertTrue(Documents.recent(context).list().none { it.uri.startsWith("content://media/") })
    }

    /**
     * A file another app shared to read only, as a media store address (Samsung's My Files does
     * this). The editor says why it can't save, and opens the same file again from its folder,
     * with the unsaved text, where it saves.
     */
    @Test
    fun aFileSharedToReadOnlyOpensAgainFromItsFolder() {
        val (name, id) = othersFile("one\n")
        val shared = "content://media/external/file/$id"
        addresses += shared
        shell("am start -a android.intent.action.VIEW -t text/plain -d $shared --grant-read-uri-permission -n ${context.packageName}/${TextEditorActivity::class.java.name}")
        compose.waitFor("the editor shows the file", 10_000) { editors() == 1 }
        compose.waitFor("the editor says why it can't save") { message().startsWith("Read Only - Reopen again to edit") }

        compose.onNodeWithTag("editor").performTextReplacement("mine")
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitFor("the save failed") { message().startsWith("Not saved:") }
        assertEquals("one\n", onDisk(name))

        // A tap on the message opens the picker in the file's folder. The file is picked there.
        compose.onNodeWithTag("message").performClick()
        tap("the picker starts in Download, with $name", By.text(name))
        compose.waitFor("the file opened again, with the unsaved text", 10_000) {
            editors() == 1 && message() == "Your unsaved changes are back" && text() == "mine"
        }
        val reopened = Documents.recent(context).list().first().uri
        addresses += reopened
        assertTrue(
            reopened,
            reopened.startsWith("content://com.android.externalstorage.documents/")
        )
        // The text moved: nothing is left under the shared address.
        assertFalse(drafts.has(shared))

        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitFor("saved") { message() == "Saved" }
        assertEquals("mine\n", onDisk(name))
        compose.onNodeWithContentDescription("Close").performClick()
    }
}
