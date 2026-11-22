package com.mtwabpin.kolahal.ui.home

import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** `log.wallpaper`, `widgets.wallpaper`: home's window lets the phone's wallpaper through only when a page shows it. */
@RunWith(AndroidJUnit4::class)
class WallpaperTest {
    private val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun tearDown() {
        config.preview(Keys.LOG_WALLPAPER, null)
        config.reset(Keys.LOG_WALLPAPER)
        config.reset(Keys.WIDGETS_WALLPAPER)
        config.reset(Keys.WIDGETS_PAGE)
    }

    /**
     * Whether the window shows the wallpaper: asked for, with nothing painted behind home (waited
     * for, as the theme applies). The surface is translucent either way, from the start: its format
     * can't change once it exists, so switching the wallpaper on later would show nothing.
     */
    private fun ActivityScenario<MainActivity>.showsWallpaper(expected: Boolean) {
        fun now(): Boolean {
            var shows = false
            onActivity { a ->
                val p = a.window.attributes
                assertEquals(PixelFormat.TRANSLUCENT, p.format)
                val behind =
                    (a.window.decorView.background as? android.graphics.drawable.ColorDrawable)?.alpha
                shows =
                    p.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0 && behind == 0
            }
            return shows
        }

        val until = SystemClock.uptimeMillis() + 5_000
        while (now() != expected && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        assertEquals(expected, now())
    }

    @Test
    fun eachPageLetsTheWallpaperThroughOnlyWhenItShowsIt() {
        config.set(Keys.WIDGETS_PAGE, "left")
        ActivityScenario.launch(MainActivity::class.java).use {
            it.showsWallpaper(false)
            config.set(Keys.LOG_WALLPAPER, "50")
            it.showsWallpaper(true)
            config.reset(Keys.LOG_WALLPAPER)
            it.showsWallpaper(false)
            config.set(Keys.WIDGETS_WALLPAPER, "30")
            it.showsWallpaper(true)
            // With no widgets page, its wallpaper has nowhere to show.
            config.set(Keys.WIDGETS_PAGE, "off")
            it.showsWallpaper(false)
            // Dragged in Settings: home shows it as the slider moves, and goes back when it is dropped.
            config.preview(Keys.LOG_WALLPAPER, "40")
            it.showsWallpaper(true)
            config.preview(Keys.LOG_WALLPAPER, null)
            it.showsWallpaper(false)
        }
    }
}
