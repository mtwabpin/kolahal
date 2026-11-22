package com.mtwabpin.kolahal.ui.theme

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.MaterialYou
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The `material-you` theme on a phone: the wallpaper's colors, in the phone's mode, changing with the wallpaper. */
@RunWith(AndroidJUnit4::class)
class MaterialYouTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val config = Config.get(context)
    private val wallpapers = WallpaperManager.getInstance(context)
    private var night = "yes"
    private var wallpaperSet = false

    @Before
    fun setUp() {
        // "Night mode: yes"
        night = shell("cmd uimode night").substringAfter(':').trim()
        config.set(Keys.THEME, MaterialYou.NAME)
    }

    @After
    fun tearDown() {
        config.reset(Keys.THEME)
        if (wallpaperSet) wallpapers.clear(WallpaperManager.FLAG_SYSTEM)
        shell("cmd uimode night $night")
    }

    /** The theme's palette as the phone's colors give it now: read again here, not taken from the app. */
    private fun fromPhone(dark: Boolean): Palette = when {
        Build.VERSION.SDK_INT < 31 -> paletteFrom(if (dark) darkColorScheme() else lightColorScheme())
        dark -> paletteFrom(dynamicDarkColorScheme(context))
        else -> paletteFrom(dynamicLightColorScheme(context))
    }

    @Test
    fun itShowsThePhonesColorsInThePhonesMode() {
        shell("cmd uimode night no")
        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntil("the light colors") { Term.palette == fromPhone(dark = false) }
            // Dark mode turning on while home shows: the dark colors, with no restart.
            shell("cmd uimode night yes")
            waitUntil("the dark colors") { Term.palette == fromPhone(dark = true) }
            assertEquals(Palettes.named(MaterialYou.NAME), Term.palette)
            assertNotEquals(fromPhone(dark = false), Term.palette)
        }
    }

    /** A new wallpaper: Android works out its colors, and home takes them without restarting. */
    @Test
    fun itFollowsANewWallpaper() {
        assumeTrue("Android 12 and newer have wallpaper colors", MaterialYou.supported)
        shell("cmd uimode night yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var home: MainActivity
            scenario.onActivity { home = it }
            waitUntil("the wallpaper's colors") { Term.palette == fromPhone(dark = true) }
            val before = Term.palette

            wallpaperSet = true
            wallpapers.setBitmap(
                Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(Color.RED) }, null, true, WallpaperManager.FLAG_SYSTEM
            )
            // Android holds back the colors of a wallpaper that a background app set (as this test
            // does) until the screen goes off: "keep deferring color events". One you pick yourself
            // applies at once. So the screen goes off and on, and Android works the colors out.
            shell("input keyevent KEYCODE_SLEEP")
            Thread.sleep(2_000)
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            waitUntil("new colors on home", 30_000) { Term.palette != before }
            waitUntil("the new wallpaper's colors") { Term.palette == fromPhone(dark = true) }
            assertEquals(
                "a wallpaper's color never changes what good means",
                before.good,
                Term.palette.good
            )
            scenario.onActivity { assertSame("home doesn't restart for it", home, it) }
        }
    }
}
