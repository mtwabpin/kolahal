package com.mtwabpin.kolahal.ui.wallpaper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Home hands the live wallpaper its shader and what it is set to, when either changes. */
@RunWith(AndroidJUnit4::class)
class WallpaperWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val before = WallpaperChoice.name.value

    @After
    fun tearDown() {
        scope.cancel()
        WallpaperChoice.set(context, before)
        ShaderParams.reset(context, "dunes")
    }

    private fun waitFor(what: String, check: (WallpaperState) -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            if (WallpaperState.read(context)?.let(check) == true) return
            Thread.sleep(50)
        }
        throw AssertionError(what)
    }

    @Test
    fun itFollowsTheChoiceAndTheParameters() {
        scope.launch { WallpaperWriter.run(context) }
        WallpaperChoice.set(context, "dunes")
        waitFor("not dunes") { it.name == "dunes" && it.code == Shaders.source(context, "dunes") }
        val camels = Shaders.params(context, "dunes").first { it.name == "camels" }
        ShaderParams.setFollow(context, "dunes", camels, "battery", "battery / 20")
        waitFor("not followed") { it.follows["camels"] == "battery" && it.formulas["camels"] == "battery / 20" }
        val state = WallpaperState.read(context)!!
        assertTrue(state.dark.getValue("bg") != state.light.getValue("bg"))
        assertTrue(state.cellW > 1f && state.cellH > state.cellW)
        // One that is gone: the default.
        WallpaperChoice.set(context, "no-such-shader")
        waitFor("not the default") { it.name == WallpaperChoice.DEFAULT }
        assertEquals(WallpaperChoice.DEFAULT, WallpaperState.read(context)?.name)
        assertFalse(WallpaperChoice.isWallpaper(context))
    }
}
