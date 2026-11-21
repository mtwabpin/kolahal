package com.mtwabpin.kolahal.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** A script's image under its log line, decoded for the log's width. */
@RunWith(AndroidJUnit4::class)
class LogImageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = "https://example.com/log-image-${System.nanoTime()}.png"

    @After
    fun tearDown() {
        download().delete()
    }

    /** Where [LogImages] keeps [url]'s download: put there, nothing is fetched. */
    private fun download(): File {
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "log-images").apply { mkdirs() }, name)
    }

    /**
     * Dark lines with nothing behind them, on the dark theme: the picture is on a light backdrop.
     * A picture with a background of its own (here, all grey) has none.
     */
    @Test
    fun aDarkDrawingOnADarkLogGetsABackdrop() {
        val before = Term.palette
        Term.palette = Palettes.green
        try {
            // Black strokes across a see-through picture, as Wikipedia's diagrams are.
            val drawing = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until 200 step 10) for (x in 0 until 400) setPixel(
                    x,
                    y,
                    android.graphics.Color.BLACK
                )
            }
            download().outputStream().use { drawing.compress(Bitmap.CompressFormat.PNG, 100, it) }
            var shown by mutableStateOf(url)
            compose.setContent { KolahalTheme { Box(Modifier.width(300.dp)) { LogImage(shown) } } }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("picture-backdrop").fetchSemanticsNodes().isNotEmpty()
            }

            // A picture with a background of its own: all grey.
            val photo = "https://example.com/log-photo-${System.nanoTime()}.png"
            val photoFile = File(
                download().parentFile,
                MessageDigest.getInstance("SHA-256").digest(photo.toByteArray())
                    .joinToString("") { "%02x".format(it) })
            photoFile.outputStream().use {
                Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(android.graphics.Color.GRAY) }
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            try {
                compose.runOnIdle { shown = photo }
                compose.waitUntil(5_000) { LogImages.cachedAny(photo) != null }
                compose.waitForIdle()
                compose.onAllNodesWithTag("picture-backdrop").assertCountEquals(0)
            } finally {
                photoFile.delete()
            }
        } finally {
            Term.palette = before
        }
    }

    /**
     * A link whose download is no picture (an error page) is not fetched again at once: the widgets
     * page asks at each visit, and the download can be 10 MB.
     */
    @Test
    fun aLinkThatIsNoPictureWaitsBeforeTheNextTry() {
        download().writeText("<html>Not found</html>")
        assertNull(LogImages.load(context, url, 300))
        assertFalse("the page that is no picture is deleted", download().exists())
        // The picture is there now, but the link is not tried again yet.
        download().outputStream().use {
            Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertNull(LogImages.load(context, url, 300))
    }

    /** The phone turned, so the log is wider: the image is decoded again for it, not stretched. */
    @Test
    fun aWiderLogDecodesItAgain() {
        download().outputStream().use {
            Bitmap.createBitmap(2000, 500, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        var width by mutableStateOf(200.dp)
        compose.setContent {
            KolahalTheme {
                Box(
                    Modifier
                        .width(width)
                        .testTag("log")
                ) { LogImage(url) }
            }
        }
        fun px() = compose.onNodeWithTag("log").fetchSemanticsNode().size.width
        val narrow = px()
        compose.waitUntil(5_000) { LogImages.cached(url, narrow) != null }
        compose.runOnIdle { width = 400.dp }
        compose.waitForIdle()
        val wide = px()
        assertTrue("$narrow, then $wide", wide > narrow)
        compose.waitUntil(5_000) { LogImages.cached(url, wide) != null }
    }
}
