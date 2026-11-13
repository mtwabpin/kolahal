package com.mtwabpin.kolahal.control

import android.app.Activity
import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.eventually
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.ui.Palette
import com.mtwabpin.kolahal.ui.Palettes
import com.mtwabpin.kolahal.ui.Term
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** `wallpaper match`: the wallpaper follows the theme, but only while it's still the one Kolahal set. */
@RunWith(AndroidJUnit4::class)
class WallpaperMatchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val wallpapers = WallpaperManager.getInstance(context)
    private val config = Config.get(context)
    private val system = WallpaperManager.FLAG_SYSTEM
    private val lock = WallpaperManager.FLAG_LOCK
    private var night = "yes"

    private fun solid(colour: Int) = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }

    /**
     * The colour of the picture itself, not what getWallpaperColors says of it. Android drops a
     * wallpaper's colours as a set starts, and anyone asking before the new picture is in (the test, or
     * System UI still answering the last set) gets them worked out from the old one, and so does
     * everyone after: a wallpaper can report the colours of the one before for as long as it's up.
     */
    private fun picture(which: Int): Int? {
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.READ_WALLPAPER_INTERNAL")
        try {
            val file = wallpapers.getWallpaperFile(which) ?: return null
            val bitmap = file.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) } ?: return null
            return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    /** Within a shade: scaling the picture to the screen can round it. */
    private fun shows(which: Int, colour: Int): Boolean {
        val c = picture(which) ?: return false
        return abs(Color.red(c) - Color.red(colour)) <= 2 && abs(Color.green(c) - Color.green(colour)) <= 2 && abs(Color.blue(c) - Color.blue(colour)) <= 2
    }

    private fun waitFor(what: String, check: () -> Boolean) {
        if (!eventually(8_000, check)) fail("$what; matched ${WallpaperMatch.isMatched(context)}, set to ${Integer.toHexString(colourSet())}, picture ${picture(system)?.let(Integer::toHexString)}")
    }

    /** The colour of the wallpaper Kolahal last finished setting. */
    private fun colourSet() = context.getSharedPreferences("wallpaper_match", Context.MODE_PRIVATE).getInt("colour", 0)

    /** The wallpaper ends up in [colour]: following goes off the main thread, and it's done when the colour is stored. */
    private fun followsTo(what: String, colour: Int) = waitFor(what) { colourSet() == colour && shows(system, colour) }

    /** A palette's background as the wallpaper has it. */
    private fun wallpaperOf(palette: Palette) = palette.bg.copy(alpha = 1f).toArgb()

    @Before fun setUp() {
        night = shell("cmd uimode night").substringAfter(':').trim()
        WallpaperMatch.forget(context)
    }

    @After fun tearDown() {
        WallpaperMatch.forget(context)
        wallpapers.clear(system or lock)
        config.reset(Keys.THEME)
        shell("cmd uimode night $night")
    }

    /** A theme being tried leaves the wallpaper as it is: it changes when the theme is kept. */
    @Test fun aPreviewLeavesItAloneUntilEnter() {
        ActivityScenario.launch(com.mtwabpin.kolahal.MainActivity::class.java).use { scenario ->
            lateinit var vm: com.mtwabpin.kolahal.terminal.TerminalViewModel
            scenario.onActivity {
                vm = androidx.lifecycle.ViewModelProvider(it)[com.mtwabpin.kolahal.terminal.TerminalViewModel::class.java]
                vm.openScreen(com.mtwabpin.kolahal.terminal.Screen.TERMINAL)
            }
            val before = wallpaperOf(Term.palette)
            assertTrue(WallpaperMatch.set(context, before))
            followsTo("the theme in use", before)
            val id = wallpapers.getWallpaperId(system)

            instrumentation.runOnMainSync { vm.onInputChange(androidx.compose.ui.text.input.TextFieldValue("theme dracula")) }
            waitFor("the theme tried") { wallpaperOf(Term.palette) != before }
            Thread.sleep(1_500)
            assertEquals(id, wallpapers.getWallpaperId(system))
            assertEquals(before, colourSet())

            instrumentation.runOnMainSync { vm.submit() }
            waitFor("the theme kept") { config[Keys.THEME] == "dracula" }
            followsTo("the theme kept", wallpaperOf(Term.palette))
            assertNotEquals(before, colourSet())
        }
    }

    @Test fun itKnowsTheWallpaperItSet() {
        assertFalse(WallpaperMatch.isMatched(context))
        assertTrue(WallpaperMatch.set(context, Color.RED))
        assertTrue(WallpaperMatch.isMatched(context))
        waitFor("red") { shows(system, Color.RED) }
    }

    @Test fun itFollowsWhileTheWallpaperIsItsOwn() {
        WallpaperMatch.set(context, Color.RED)
        val first = wallpapers.getWallpaperId(system)
        WallpaperMatch.follow(context, Color.BLUE)
        followsTo("blue", Color.BLUE)
        assertNotEquals("a new wallpaper", first, wallpapers.getWallpaperId(system))
        assertTrue("and still its own", WallpaperMatch.isMatched(context))
    }

    /** The heart of it: someone sets their own, and from then on Kolahal keeps its hands off. */
    @Test fun itLeavesAWallpaperSetSinceAlone() {
        WallpaperMatch.set(context, Color.RED)
        wallpapers.setBitmap(solid(Color.GREEN), null, true, system)
        val theirs = wallpapers.getWallpaperId(system)
        assertFalse(WallpaperMatch.isMatched(context))

        WallpaperMatch.follow(context, Color.BLUE)
        // Forgotten, not just skipped: it lets go of the wallpaper it had set, for good.
        waitFor("forgotten") { !context.getSharedPreferences("wallpaper_match", android.content.Context.MODE_PRIVATE).contains("id") }
        assertEquals(theirs, wallpapers.getWallpaperId(system))
        waitFor("still green") { shows(system, Color.GREEN) }

        // So another theme later doesn't touch it either.
        WallpaperMatch.follow(context, Color.YELLOW)
        Thread.sleep(500)
        assertEquals(theirs, wallpapers.getWallpaperId(system))
    }

    @Test fun withoutMatchItNeverTouchesTheWallpaper() {
        wallpapers.setBitmap(solid(Color.GREEN), null, true, system)
        val theirs = wallpapers.getWallpaperId(system)
        WallpaperMatch.follow(context, Color.BLUE)
        Thread.sleep(1_000)
        assertEquals(theirs, wallpapers.getWallpaperId(system))
    }

    @Test fun theSameColourSetsNothing() {
        WallpaperMatch.set(context, Color.RED)
        val first = wallpapers.getWallpaperId(system)
        WallpaperMatch.follow(context, Color.RED)
        Thread.sleep(800)
        assertEquals(first, wallpapers.getWallpaperId(system))
    }

    /** Themes picked faster than wallpapers are set: it ends on the last one. */
    @Test fun itEndsOnTheLastTheme() {
        WallpaperMatch.set(context, Color.RED)
        WallpaperMatch.follow(context, Color.GREEN)
        WallpaperMatch.follow(context, Color.YELLOW)
        WallpaperMatch.follow(context, Color.BLUE)
        followsTo("blue, the last", Color.BLUE)
        Thread.sleep(800)
        assertEquals(Color.BLUE, colourSet())
        assertTrue(shows(system, Color.BLUE))
    }

    /** Home, for real: a theme picked, then dark mode turning off; the lock screen stays out of it; home doesn't restart. */
    @Test fun aThemeChangeOnHomeChangesTheWallpaper() {
        wallpapers.setBitmap(solid(Color.MAGENTA), null, true, system or lock)
        waitFor("magenta first") { shows(system, Color.MAGENTA) }
        shell("cmd uimode night yes")
        config.set(Keys.THEME, "gruvbox")
        var created = 0
        val count = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) { if (a is MainActivity) created++ }
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        }
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(count)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                waitFor("the palette") { Term.palette == Palettes.gruvbox }
                assertTrue(WallpaperMatch.set(context, WallpaperMatch.themeColour!!))
                val first = wallpapers.getWallpaperId(system)
                waitFor("gruvbox") { shows(system, wallpaperOf(Palettes.gruvbox)) }

                // Gruvbox and Dracula are close greys: the id says it changed, the colour that it's right.
                config.set(Keys.THEME, "dracula")
                followsTo("dracula", wallpaperOf(Palettes.dracula))
                assertNotEquals("a new wallpaper", first, wallpapers.getWallpaperId(system))

                shell("cmd uimode night no")
                followsTo("dracula's light version", wallpaperOf(Palettes.draculaLight))
                assertTrue("and still its own", WallpaperMatch.isMatched(context))

                assertTrue("the lock screen keeps its own", shows(lock, Color.MAGENTA))
                assertEquals("home doesn't restart for it", 1, created)
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(count)
        }
    }
}
