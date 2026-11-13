package com.mtwabpin.kolahal

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.os.SystemClock
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

    @Test fun memoryPressureLetsThemGoAtOnce() {
        @Suppress("DEPRECATION") trim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertNull(LogImages.cached(url, width))
    }

    /** Android 14 and later never send the home app the trim above: a while away does instead. */
    @Test fun aWhileAwayLetsThemGo() {
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        // Not at once: a short visit to another app keeps them.
        assertNotNull(LogImages.cached(url, width))
        assertTrue("still kept after the time away", eventually(5_000) { LogImages.cached(url, width) == null })
    }

    /**
     * App icons go too after a while away, except the most-used apps': you type those first, and
     * they'd pop in late. Under memory pressure, they all go.
     */
    @Test fun aWhileAwayKeepsTheMostUsedAppsIcons() {
        val repo = com.mtwabpin.kolahal.apps.AppRepository.get(context)
        repo.refresh()
        assertTrue(eventually(5_000) { repo.apps.value.isNotEmpty() })
        val icons = com.mtwabpin.kolahal.ui.AppIcons
        val prefs = com.mtwabpin.kolahal.apps.AppPrefs.get(context)
        // A pinned app counts among the most used, and a test phone has no launches to rank.
        val used = repo.apps.value.first { !it.hidden && it.profile.kind == com.mtwabpin.kolahal.apps.ProfileKind.PERSONAL }
        val wasPinned = prefs.isPinned(used.key)
        prefs.setPinned(used.key, true)
        try {
            val top = repo.top(LauncherApp.KEEP_ICONS)
            assertTrue(top.any { it.key == used.key })
            val other = repo.apps.value.firstOrNull { a -> !a.hidden && top.none { it.key == a.key } }
            assertNotNull(icons.load(context, used, 48))
            other?.let { assertNotNull(icons.load(context, it, 48)) }
            trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            assertTrue("the time away passed", eventually(5_000) { LogImages.cached(url, width) == null })
            // The same task on the main thread trims the icons just after the log pictures: let it end.
            instrumentation.runOnMainSync { }
            assertNotNull(icons.cached(used, 48))
            other?.let { assertNull(icons.cached(it, 48)) }
            @Suppress("DEPRECATION") trim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            assertNull(icons.cached(used, 48))
        } finally {
            prefs.setPinned(used.key, wasPinned)
        }
    }

    /**
     * Back on home before the time is up, they're kept. Home's return is told here as MainActivity's
     * start tells it (`homeShown`); the activity itself isn't started.
     */
    @Test fun backBeforeThenKeepsThem() {
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        instrumentation.runOnMainSync { app.homeShown() }
        Thread.sleep(1_000)
        assertNotNull(LogImages.cached(url, width))
    }

    /** Hidden again: the wait starts over rather than ending at the first hide's time. */
    @Test fun hiddenAgainStartsTheWaitOver() {
        app.awayTrimMs = 3_000
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        val first = SystemClock.uptimeMillis()
        Thread.sleep(2_500)
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        // Half a second past the first hide's time, and two seconds before the second's: room for a slow device.
        Thread.sleep(maxOf(0L, first + 3_500 - SystemClock.uptimeMillis()))
        assertNotNull("let go at the first hide's time", LogImages.cached(url, width))
        assertTrue("still kept after the second wait", eventually(10_000) { LogImages.cached(url, width) == null })
    }
}
