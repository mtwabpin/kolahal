package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Test

/** How much of the phone's wallpaper shows behind home with a shader on ([WallpaperChoice.homeShare]). */
class WallpaperShareTest {
    private fun meta(name: String) = ShaderMeta.parse(sourceFile("src/main/assets/shaders/$name.agsl").readText())

    @Test
    fun noShaderKeepsTheSetting() {
        assertEquals(50, WallpaperChoice.homeShare(50, null))
    }

    /** A scene brings its own ground, so none of the wallpaper shows; a person's own scene too. */
    @Test
    fun aSceneHidesTheWallpaper() {
        assertEquals(0, WallpaperChoice.homeShare(50, meta("pond")))
        assertEquals(0, WallpaperChoice.homeShare(100, meta("rain")))
        val own = ShaderMeta.parse("// @name Mine\nuniform shader content;\nhalf4 main(float2 xy) { return content.eval(xy); }\n")
        assertEquals(0, WallpaperChoice.homeShare(50, own))
    }

    @Test
    fun aShaderOnTextKeepsTheSetting() {
        assertEquals(50, WallpaperChoice.homeShare(50, meta("crt")))
        assertEquals(50, WallpaperChoice.homeShare(50, meta("eink")))
    }

    @Test
    fun aOneOffKeepsTheSetting() {
        assertEquals(50, WallpaperChoice.homeShare(50, meta("hearts")))
        assertEquals(50, WallpaperChoice.homeShare(50, meta("pulse")))
    }

    @Test
    fun aShareOfNoneStaysNone() {
        assertEquals(0, WallpaperChoice.homeShare(0, null))
        assertEquals(0, WallpaperChoice.homeShare(0, meta("pond")))
        assertEquals(0, WallpaperChoice.homeShare(0, meta("crt")))
    }
}
