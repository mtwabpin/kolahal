package com.mtwabpin.kolahal.ui.shaders

import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Make a picture in a shader's sheet ([ShaderPage.makesPicture]): for a scene, only with Lab's pictures on. */
class ShaderPictureRowTest {
    private fun meta(name: String) = ShaderMeta.parse(sourceFile("src/main/assets/shaders/$name.agsl").readText())

    @Test
    fun aSceneHasItWithLabOn() {
        for (name in listOf("pond", "dunes", "snowfall", "paper")) assertTrue(name, ShaderPage.makesPicture(meta(name), drawable = true, picturesOn = true))
    }

    @Test
    fun nothingWithLabOff() {
        for (name in listOf("pond", "dunes", "hearts", "crt")) assertFalse(name, ShaderPage.makesPicture(meta(name), drawable = true, picturesOn = false))
    }

    @Test
    fun aOneOffOrAShaderOnTextHasNone() {
        for (name in listOf("hearts", "comet", "crt", "eink")) assertFalse(name, ShaderPage.makesPicture(meta(name), drawable = true, picturesOn = true))
    }

    @Test
    fun oneThatIsntDrawnHasNone() {
        assertFalse(ShaderPage.makesPicture(meta("pond"), drawable = false, picturesOn = true))
    }
}
