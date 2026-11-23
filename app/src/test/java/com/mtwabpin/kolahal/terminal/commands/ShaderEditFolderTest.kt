package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.shader.WallpaperChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A shader saved in the editor goes into the script folder's file of it too ([ShaderCommands.saveInFolder]), and the
 * wallpaper named is the one drawn ([ShaderCommands.wallpaperDrawn]).
 */
class ShaderEditFolderTest {
    private val written = mutableListOf<Pair<String, String>>()

    /** The folder's file of the shader gets the saved code, so `shader reload` reads it and not the old one. */
    @Test
    fun aSaveGoesIntoTheFoldersFile() {
        assertNull(ShaderCommands.saveInFolder("my-tint", "new", names = { setOf("my-tint", "other") }) { n, c -> written += n to c })
        assertEquals(listOf("my-tint" to "new"), written)
    }

    /** A shader the folder has no file of stays out of it. */
    @Test
    fun aShaderNotInTheFolderStaysOut() {
        assertNull(ShaderCommands.saveInFolder("pond", "new", names = { setOf("my-tint") }) { n, c -> written += n to c })
        assertEquals(emptyList<Pair<String, String>>(), written)
    }

    /** A folder that can't be read or written says why; the save itself isn't undone. */
    @Test
    fun aFolderThatFailsSaysWhy() {
        assertEquals(
            "not saved to the script folder: script folder is not accessible",
            ShaderCommands.saveInFolder("my-tint", "new", names = { throw IllegalStateException("script folder is not accessible") }) { _, _ -> },
        )
        assertEquals(
            "not saved to the script folder: can't create my-tint.agsl",
            ShaderCommands.saveInFolder("my-tint", "new", names = { setOf("my-tint") }) { _, _ -> throw IllegalStateException("can't create my-tint.agsl") },
        )
    }

    /** The wallpaper is the one chosen while it exists; Kolahal's own when it is gone, or none was chosen, as the writer draws. */
    @Test
    fun theWallpaperIsTheOneDrawn() {
        val there = setOf("dunes")
        assertEquals("dunes", ShaderCommands.wallpaperDrawn("dunes") { it in there })
        assertEquals(WallpaperChoice.DEFAULT, ShaderCommands.wallpaperDrawn("removed") { it in there })
        assertEquals(WallpaperChoice.DEFAULT, ShaderCommands.wallpaperDrawn(null) { it in there })
    }
}
