package com.mtwabpin.kolahal.ui.wallpaper

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.terminal.PictureRequest
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.settings.ABOUT_PAGE
import com.mtwabpin.kolahal.ui.settings.SettingsOverHome
import com.mtwabpin.kolahal.ui.shaders.ShadersScreen
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lab ([com.mtwabpin.kolahal.data.Lab]) and its first feature, a picture from a shader: 8 taps on the version turn
 * it on, a scene's sheet then has Make a picture (a one-off has none), and saving writes a picture to the gallery
 * that Undo deletes.
 */
@RunWith(AndroidJUnit4::class)
class LabPictureTest {
    @get:Rule
    val vms = TestViewModels()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val configReset = ConfigReset()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)

    /** The pictures of Pond this test may have saved, by their file names. */
    private fun saved(): Int = app.contentResolver.query(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
        arrayOf("kolahal-pond-%"),
        null,
    )?.use { it.count } ?: 0

    private var before = 0

    @Before
    fun setUp() {
        ShaderSafety.get(app).turnedOn()
        before = saved()
    }

    @After
    fun tearDown() {
        // A picture a failed test left behind: only those of this test's names, newer than it.
        if (saved() > before) app.contentResolver.delete(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?",
            arrayOf("kolahal-pond-%", (System.currentTimeMillis() / 1000 - 600).toString()),
        )
    }

    private fun needsAndroid13() = assumeTrue("shaders need Android 13", Shaders.supported)

    private fun settings(page: String?): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.settingsCategory.value = page
        }
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp).consumeWindowInsets(WindowInsets.safeDrawing)) { SettingsOverHome(vm) }
            }
        }
        return vm
    }

    private fun shaders(): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent { ShadersScreen(vm.shaderActions, vm.notice, vm::openScreen, vm::closeScreen) }
        compose.waitForNode(hasTestTag("shader off"))
        return vm
    }

    private fun openSheet(name: String, title: String) {
        compose.onNodeWithTag("shader-search").performTextInput(name)
        compose.waitForNode(hasTestTag("shader $name"))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("shader $name"))
        compose.onNodeWithContentDescription("Details of $title").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForText("Code")
    }

    private fun pictureRows() = compose.onAllNodesWithTag("make-picture").fetchSemanticsNodes().size

    /**
     * 8 taps on Version turn Lab on; Settings then has a Lab page, last. Every tap reaches the row: the countdown is a
     * toast, where a snackbar over the bottom of the page took the 6th to 8th taps.
     */
    @Test
    fun eightTapsTurnLabOnAndTheCategoryAppears() {
        assertFalse(config[Keys.LAB_ON])
        val vm = settings(ABOUT_PAGE)
        // The last row of About: below the frame until the page is scrolled to it.
        val version = compose.onNodeWithText("Version").performScrollTo()
        // The countdown is a toast (LabTapsTest has its words): each tap lands on the row, though the message shows.
        repeat(7) { version.performClick() }
        compose.waitForIdle()
        assertFalse(config[Keys.LAB_ON])
        version.performClick()
        compose.waitFor("Lab isn't on") { config[Keys.LAB_ON] }
        version.performClick()
        assertTrue(config[Keys.LAB_ON])
        compose.runOnUiThread { vm.settingsCategory.value = null }
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("categories")
        compose.onNodeWithText("Lab").performScrollTo().performClick()
        compose.waitForText("Make a picture from a shader")
        // Off again: the page closes and Lab is hidden.
        compose.runOnUiThread { config.set(Keys.LAB_ON, "off") }
        compose.waitFor("the Lab page stays") { compose.onAllNodesWithText("Make a picture from a shader").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("categories")
        assertTrue(compose.onAllNodesWithText("Lab").fetchSemanticsNodes().isEmpty())
    }

    /** With Lab on, Make a picture in a scene's sheet opens the composer for it. */
    @Test
    fun theComposerOpensFromAScenesSheet() {
        needsAndroid13()
        config.set(Keys.LAB_ON, "on")
        val vm = shaders()
        openSheet("dunes", "Dunes")
        compose.onNodeWithTag("make-picture").performScrollTo().performClick()
        compose.waitFor("the composer isn't open") { vm.screen.value == Screen.PICTURE }
        assertEquals("dunes", PictureRequest.shader.value)
    }

    /** A one-off has no Make a picture, nor one that works on text; with Lab off a scene has none either. */
    @Test
    fun aOneOffHasNoRow() {
        needsAndroid13()
        config.set(Keys.LAB_ON, "on")
        shaders()
        openSheet("hearts", "Hearts")
        assertEquals(0, pictureRows())
    }

    @Test
    fun withLabOffASceneHasNoRow() {
        needsAndroid13()
        shaders()
        openSheet("dunes", "Dunes")
        assertEquals(0, pictureRows())
    }

    /** The composer of Pond shows its controls; Save to gallery writes a picture, and Undo deletes it. */
    @Test
    fun savingWritesAPictureAndUndoRemovesIt() {
        needsAndroid13()
        config.set(Keys.LAB_ON, "on")
        PictureRequest.ask("pond")
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent { KolahalTheme { PictureScreen(vm) } }
        compose.waitForNode(hasTestTag("picture-preview"), ms = 10_000)
        // Pond moves, follows the hour and declares seed.
        // The list is lazy: a row is there only once it is scrolled to, by its key.
        for ((key, tag) in listOf("frame" to "picture-frame", "hour" to "picture-hour", "seed" to "picture-shuffle", "size" to "picture-size screen")) {
            compose.onNode(hasScrollToKeyAction()).performScrollToKey(key)
            compose.onNodeWithTag(tag).assertExists()
        }
        compose.onNodeWithTag("picture-save").performClick()
        compose.waitForText("Saved to Pictures/Kolahal", ms = 20_000)
        assertEquals(before + 1, saved())
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Undo left the picture") { saved() == before }
        compose.waitForText("Picture deleted")
    }

    /** A frame drawn at full size off the screen: as large as asked, in sRGB; one larger than the GPU draws is refused. */
    @Test
    fun aPictureIsDrawnAtFullSize() {
        needsAndroid13()
        val code = Shaders.source(app, "pond")!!
        val meta = ShaderMeta.parse(code)
        val look = PictureLook(
            colors = WallpaperWriter.colors(com.mtwabpin.kolahal.ui.theme.Palettes.named("rose-pine", true)), dark = true,
            params = meta.params.map { it.default }, time = 41.5f, date = null, seed = 0f, screenW = 540, cellW = 10f, cellH = 21f,
        )
        val drawn = PictureRender.draw(code, look, 1080, 2400)
        assertTrue(drawn.toString(), drawn is PictureResult.Drawn)
        val bitmap = (drawn as PictureResult.Drawn).bitmap
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(2400, bitmap.height)
            assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
            assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), bitmap.colorSpace)
        } finally {
            bitmap.recycle()
        }
        val max = PictureRender.maxSide()
        if (max != null) assertEquals(PictureResult.TooLarge, PictureRender.draw(code, look, 100, max + 1))
        // Both scenes with seed compile here and draw at each size; how long each took is in the log.
        for (name in listOf("pond", "dunes")) for ((w, h) in listOf(1080 to 2400, 1440 to 3200, 2160 to 4800)) {
            val source = Shaders.source(app, name)!!
            val start = android.os.SystemClock.elapsedRealtime()
            val result = PictureRender.draw(source, look.copy(params = ShaderMeta.parse(source).params.map { it.default }, seed = 7f), w, h)
            android.util.Log.i("LabPictureTest", "$name ${w}x$h: $result in ${android.os.SystemClock.elapsedRealtime() - start} ms, GPU max side $max")
            assertTrue("$name $w x $h: $result", result is PictureResult.Drawn)
            (result as PictureResult.Drawn).bitmap.recycle()
        }
    }
}
