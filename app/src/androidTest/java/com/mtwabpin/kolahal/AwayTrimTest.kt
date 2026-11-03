package com.mtwabpin.kolahal

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ui.LogImages
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** The log pictures kept for scrolling back: let go under memory pressure, or a while after home went away. */
@RunWith(AndroidJUnit4::class)
class AwayTrimTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as LauncherApp
    private val url = "https://example.com/away-trim-${System.nanoTime()}.png"
    private val width = 300

    /** Where [LogImages] keeps [url]'s download: put there, nothing is fetched. */
    private fun download(): File {
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "log-images").apply { mkdirs() }, name)
    }

    @Before fun setUp() {
        download().outputStream().use { Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.awayTrimMs = 300
        assertNotNull(LogImages.load(context, url, width))
        assertNotNull(LogImages.cached(url, width))
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { app.homeShown() }
        app.awayTrimMs = LauncherApp.AWAY_TRIM_MS
        download().delete()
    }

    private fun trim(level: Int) = instrumentation.runOnMainSync { app.onTrimMemory(level) }

    /** Waits up to [ms] for [done]. */
    private fun within(ms: Long, done: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            if (done()) return true
            Thread.sleep(50)
        }
        return done()
    }

    @Test fun memoryPressureLetsThemGoAtOnce() {
        @Suppress("DEPRECATION") trim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertNull(LogImages.cached(url, width))
    }

    /** Android 14 and later never send the home app the trim above: a while away does instead. */
    @Test fun aWhileAwayLetsThemGo() {
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        // Not at once: a short visit to another app keeps them.
        assertNotNull(LogImages.cached(url, width))
        assertTrue("still kept after the time away", within(5_000) { LogImages.cached(url, width) == null })
    }

    @Test fun backBeforeThenKeepsThem() {
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        instrumentation.runOnMainSync { app.homeShown() }
        Thread.sleep(1_000)
        assertNotNull(LogImages.cached(url, width))
    }

    /** Hidden again: the wait starts over rather than ending at the first hide's time. */
    @Test fun hiddenAgainStartsTheWaitOver() {
        app.awayTrimMs = 1_500
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        Thread.sleep(1_000)
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        Thread.sleep(1_000)
        assertNotNull("let go at the first hide's time", LogImages.cached(url, width))
        assertTrue("still kept after the second wait", within(5_000) { LogImages.cached(url, width) == null })
    }
}
