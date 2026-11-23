package com.mtwabpin.kolahal.ui.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** How the live wallpaper leaves alone a shader that crashes its process ([WallpaperGuard]). */
class WallpaperGuardTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val code = "uniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"

    private fun tries(guard: java.io.File) = guard.readText().substringAfter(' ').toInt()

    /**
     * Android makes several engines within a moment (the preview, the picker's pictures, the wallpaper after Set):
     * each one counted a try, and the fourth found the shader crashed three times and drew the page alone.
     */
    @Test
    fun manyEnginesInOneProcessAreOneTry() {
        val file = folder.root.resolve("trying")
        val guard = WallpaperGuard(file)
        repeat(6) {
            assertTrue("engine ${it + 1}", guard.mayTry(code))
            guard.trying(code)
        }
        assertEquals(1, tries(file))
    }

    /** Each process that ends before the shader is proven is a try; after the last, it isn't tried until its code changes. */
    @Test
    fun aShaderThatCrashesTheProcessIsLeftAlone() {
        val file = folder.root.resolve("trying")
        repeat(WallpaperGuard.MAX_TRIES) {
            val process = WallpaperGuard(file)
            assertTrue("try ${it + 1}", process.mayTry(code))
            process.trying(code)
        }
        assertFalse(WallpaperGuard(file).mayTry(code))
        assertTrue("other code", WallpaperGuard(file).mayTry("$code\n"))
    }

    /** The process that makes the last try draws it in each of its engines. */
    @Test
    fun theLastTryIsDrawnByEachEngineOfItsProcess() {
        val file = folder.root.resolve("trying")
        repeat(WallpaperGuard.MAX_TRIES - 1) { WallpaperGuard(file).trying(code) }
        val last = WallpaperGuard(file)
        last.trying(code)
        assertTrue(last.mayTry(code))
        assertEquals(WallpaperGuard.MAX_TRIES, tries(file))
    }

    /** One that ran long enough has no count, and a later engine counts again from one. */
    @Test
    fun aProvenShaderStartsOver() {
        val file = folder.root.resolve("trying")
        repeat(WallpaperGuard.MAX_TRIES - 1) { WallpaperGuard(file).trying(code) }
        val guard = WallpaperGuard(file)
        guard.trying(code)
        guard.proven()
        assertFalse(file.exists())
        guard.trying(code)
        assertEquals(1, tries(file))
    }
}
