package com.mtwabpin.kolahal.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId

/**
 * The card of an `image` widget: its picture, its caption, and what it says without a picture.
 *
 * No test here asks the web for anything. A picture's file is put where [LogImages] keeps its
 * downloads, as LogImageTest does, so the card finds it there.
 */
@RunWith(AndroidJUnit4::class)
class ImageWidgetCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = "https://example.com/widget-picture-${System.nanoTime()}.png"
    private val picture = WidgetSpec.Image(WidgetBase(title = "Comic"), image = url, alt = "A graph of sleep", caption = "Monday")

    @After fun tearDown() {
        download().delete()
        LogImages.trim()
    }

    /** Where [LogImages] keeps [url]'s download: put there, nothing is fetched. */
    private fun download(url: String = this.url): File {
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "log-images").apply { mkdirs() }, name)
    }

    /** A picture of [width] by [height] pixels as [url]'s download. */
    private fun downloaded(width: Int, height: Int) {
        download().outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Whether the widgets page is in view. A test changes it while the card is drawn. */
    private var inView by mutableStateOf(false)

    /** [spec]'s card, [width] by [height]. */
    private fun show(spec: WidgetSpec.Image, shown: Boolean, width: Dp = 320.dp, height: Dp = 200.dp) {
        inView = shown
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetsShown provides inView) {
                        Box(Modifier.width(width)) {
                            WidgetCard(
                                title = spec.base.title ?: "image",
                                height = height,
                                spec = spec,
                                note = null,
                                description = WidgetWords.description(spec.base.title, spec, 0L, ZoneId.systemDefault()),
                            )
                        }
                    }
                }
            }
        }
    }

    private val hasWords = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

    /** Every text the card draws, each as its own. */
    private fun texts(): List<String> = compose.onAllNodes(hasWords, useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private fun hasPicture() = compose.onAllNodes(hasTestTag("widget-picture"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun draws(vararg expected: String) =
        compose.waitFor("the card doesn't draw ${expected.toList()}, it draws ${texts()}") { texts().containsAll(expected.toList()) }

    private val hasDescription = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    /** Waits until the card says [expected] to a screen reader. That is the only description in it: the picture has none. */
    private fun says(expected: String) {
        fun said() = compose.onAllNodes(hasDescription, useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.ContentDescription] }
        compose.waitFor("the card doesn't say “$expected”, it says ${said()}") { said() == listOf(expected) }
    }

    /** In view, the card fetches its picture and draws it with its caption. A screen reader hears the words for it. */
    @Test fun inViewItDrawsItsPictureAndCaption() {
        downloaded(800, 400)
        show(picture, shown = true)
        compose.waitFor("the picture isn't drawn; the card draws ${texts()}") { hasPicture() }
        draws("Comic", "Monday")
        assertFalse(texts().toString(), texts().any { it == "A graph of sleep" || it == WidgetWords.PICTURE_LOADING || it == WidgetWords.PICTURE_UNAVAILABLE })
        says("Comic: A graph of sleep, Monday")
        // It is in memory now, decoded no larger than twice its place.
        val kept = assertNotNullAnd(LogImages.cachedAny(url))
        val place = compose.onNodeWithTag("widget-picture", useUnmergedTree = true).fetchSemanticsNode().size
        assertTrue("${kept.width}×${kept.height} for a place of $place", kept.width <= place.width * 2 + 32 && kept.width * kept.height <= 2 * (place.width + 16) * (place.height + 16))
    }

    private fun <T : Any> assertNotNullAnd(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    /**
     * Out of view, the card fetches nothing and decodes nothing, even with the file at hand. It shows
     * the words for the picture, and never "Loading". Once the page is in view, the picture comes.
     */
    @Test fun outOfViewItShowsItsWordsUntilThePageIsInView() {
        downloaded(400, 400)
        show(picture, shown = false)
        draws("Comic", "A graph of sleep", "Monday")
        compose.waitForIdle()
        Thread.sleep(500)
        assertFalse("a picture was decoded out of view", hasPicture())
        assertNull(LogImages.cachedAny(url))
        assertFalse(texts().toString(), texts().contains(WidgetWords.PICTURE_LOADING))
        says("Comic: A graph of sleep, Monday")

        compose.runOnIdle { inView = true }
        compose.waitFor("the picture isn't drawn once the page is in view; the card draws ${texts()}") { hasPicture() }
        // Out of view again, the picture stays.
        compose.runOnIdle { inView = false }
        compose.waitForIdle()
        assertTrue(hasPicture())
    }

    /** A link with no file at hand is not asked for out of view: no file appears for it. */
    @Test fun outOfViewNothingIsFetched() {
        show(picture, shown = false)
        draws("A graph of sleep")
        compose.waitForIdle()
        Thread.sleep(500)
        assertFalse(download().exists())
        assertFalse(hasPicture())
    }

    /** Out of view, a picture that is already in memory shows, at whatever size it has there: the Widgets screen draws it so. */
    @Test fun outOfViewAPictureInMemoryShows() {
        downloaded(600, 300)
        assertNotNull(LogImages.load(context, url, widthPx = 300, maxPixels = 90_000f, httpsOnly = true))
        show(picture, shown = false, width = 240.dp, height = 240.dp)
        compose.waitFor("the picture in memory isn't drawn; the card draws ${texts()}") { hasPicture() }
        assertFalse(texts().toString(), texts().contains("A graph of sleep"))
    }

    /** A file that is no picture: the card says so, and the file goes, so the next look tries again. */
    @Test fun aFileThatIsNoPictureIsUnavailable() {
        download().writeText("<html>not found</html>")
        show(picture, shown = true)
        draws("Comic", WidgetWords.PICTURE_UNAVAILABLE)
        assertFalse(hasPicture())
        assertFalse(download().exists())
        says("Comic: A graph of sleep, Monday")
    }

    /** A small card has no room for the caption. A screen reader still hears it. */
    @Test fun aSmallCardHasThePictureAlone() {
        downloaded(300, 300)
        show(picture, shown = true, width = 120.dp, height = 96.dp)
        compose.waitFor("the picture isn't drawn; the card draws ${texts()}") { hasPicture() }
        assertEquals(emptyList<String>(), texts())
        says("Comic: A graph of sleep, Monday")
    }

    /** A low, wide card has its title line and the picture. Its caption has no room either. */
    @Test fun aLowCardHasNoCaption() {
        downloaded(900, 300)
        show(picture.copy(fit = "fit"), shown = true, width = 320.dp, height = 120.dp)
        compose.waitFor("the picture isn't drawn; the card draws ${texts()}") { hasPicture() }
        // The badge's letter and the title: no caption.
        assertEquals(listOf("C", "Comic"), texts())
        says("Comic: A graph of sleep, Monday")
    }

    /** A widget's picture is https only: a plain http link is never loaded, even with its file at hand. */
    @Test fun aPlainHttpLinkIsNeverLoaded() {
        val plain = "http://example.com/widget-picture-${System.nanoTime()}.png"
        val file = download(plain)
        try {
            file.outputStream().use { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertNull(LogImages.load(context, plain, widthPx = 64, maxPixels = 4096f, httpsOnly = true))
            // A log line still takes it.
            assertNotNull(LogImages.load(context, plain, widthPx = 64))
        } finally {
            file.delete()
        }
    }
}
