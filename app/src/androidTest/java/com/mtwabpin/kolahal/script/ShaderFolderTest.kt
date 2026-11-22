package com.mtwabpin.kolahal.script

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Shaders arrive from the script folder and from picked files, as scripts do ([ScriptFolder], [ScriptFiles]). */
@RunWith(AndroidJUnit4::class)
class ShaderFolderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "shader-folder-test")
    private val mine = listOf("zz-top", "zz-inside", "zz-fold", "zz-picked")

    private val code = "uniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"

    @Before
    fun setUp() {
        dir.deleteRecursively()
        File(dir, "shaders").mkdirs()
        mine.forEach { Shaders.remove(context, it) }
    }

    @After
    fun tearDown() {
        mine.forEach { Shaders.remove(context, it) }
        dir.deleteRecursively()
    }

    /** Files at the top of the folder and in its `shaders` folder, by their names. Others, and names a shader can't have, stay out. */
    @Test
    fun theFolderGivesItsShaders() {
        File(dir, "zz-top.agsl").writeText(code)
        File(File(dir, "shaders"), "ZZ Inside.agsl").writeText(code + "\n// inside")
        File(dir, "notes.txt").writeText("x")
        File(dir, "off.agsl").writeText(code)
        val found = ScriptFolder(context).importShaders(DocumentFile.fromFile(dir))
        assertEquals(setOf("zz-top", "zz-inside"), found.names.toSet())
        assertEquals(listOf("off.agsl"), found.skipped)
        assertEquals(code, Shaders.source(context, "zz-top"))
        assertTrue(Shaders.source(context, "zz-inside")!!.endsWith("// inside"))
        assertTrue("zz-inside" in Shaders.userNames)
    }

    /** A folder with scripts and shaders: both come along in one import, and the shaders are named. */
    @Test
    fun anImportBringsTheShadersAlong() {
        File(dir, "zz-top.agsl").writeText(code)
        val r = ScriptFolder(context).import(DocumentFile.fromFile(dir))
        assertEquals(0, r.created)
        assertEquals(listOf("zz-top"), r.shaders)
    }

    /** A shader with more than 64 KB is refused and named, and the others arrive. */
    @Test
    fun aShaderTooLargeIsSkippedAndNamed() {
        File(dir, "zz-top.agsl").writeText(code + "\n" + "// x".repeat(ShaderMeta.MAX_BYTES))
        File(dir, "zz-fold.agsl").writeText(code)
        val found = ScriptFolder(context).importShaders(DocumentFile.fromFile(dir))
        assertEquals(listOf("zz-fold"), found.names)
        assertEquals(1, found.skipped.size)
        assertTrue(found.skipped.single(), found.skipped.single().startsWith("zz-top.agsl ("))
    }

    @Test
    fun yourShadersAreWrittenToTheFolder() {
        Shaders.save(context, "zz-fold", code)
        ScriptFolder(context).exportShaders(DocumentFile.fromFile(dir))
        val written = File(dir, "shaders").listFiles().orEmpty().filter { it.name.startsWith("zz-fold.agsl") }
        assertEquals(1, written.size)
        assertEquals(code, written.single().readText())
    }

    /** A picked `.agsl` file becomes a shader; a name that can't be one is said. */
    @Test
    fun aPickedFileIsAShader() {
        val file = File(dir, "zz picked.agsl").apply { writeText(code) }
        val other = File(dir, "on.agsl").apply { writeText(code) }
        val r = ScriptFiles(context).import(listOf(Uri.fromFile(file), Uri.fromFile(other)))
        assertEquals(listOf("zz-picked"), r.shaders)
        assertTrue("${r.skipped}", r.skipped.single().startsWith("on.agsl ("))
        assertEquals(code, Shaders.source(context, "zz-picked"))
    }

    /** Settings → Shaders → Import a file takes shaders only: a script stays out, and is said. */
    @Test
    fun theShadersScreenImportsShadersOnly() {
        val file = File(dir, "zz picked.agsl").apply { writeText(code) }
        val script = File(dir, "zz-script.lua").apply { writeText("return 1") }
        val r = ScriptFiles(context).importShaders(listOf(Uri.fromFile(file), Uri.fromFile(script)))
        assertEquals(listOf("zz-picked"), r.shaders)
        assertEquals(listOf("zz-script.lua (not .agsl)"), r.skipped)
    }
}
