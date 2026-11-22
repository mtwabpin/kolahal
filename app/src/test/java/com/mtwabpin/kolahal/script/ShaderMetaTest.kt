package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a shader file says about itself ([ShaderMeta]): its header, its uniforms and what is wrong with it. */
class ShaderMetaTest {
    private val valid = """
        // @name Glow
        // @description A soft glow
        // @animated
        uniform shader content;
        uniform float2 resolution;
        layout(color) uniform half4 bg;
        half4 main(float2 p) { return content.eval(p); }
    """.trimIndent()

    @Test
    fun theHeaderIsRead() {
        val meta = ShaderMeta.parse(valid)
        assertEquals("Glow", meta.title)
        assertEquals("A soft glow", meta.description)
        assertTrue(meta.animated)
    }

    /** The scripts' words for the same are read too, and a shader without a header is fine. */
    @Test
    fun theScriptsWordsAndNoHeaderAreFine() {
        val script = ShaderMeta.parse("// @title Rain\n// @desc Drops\nuniform shader content;")
        assertEquals("Rain", script.title)
        assertEquals("Drops", script.description)
        val bare = ShaderMeta.parse("uniform shader content;")
        assertNull(bare.title)
        assertNull(bare.description)
        assertFalse(bare.animated)
    }

    /** `@animated` in the middle of a sentence isn't the directive, and the first of two wins. */
    @Test
    fun onlyDirectivesCount() {
        val meta = ShaderMeta.parse("// not @animated here\n// @name One\n// @name Two")
        assertFalse(meta.animated)
        assertEquals("One", meta.title)
    }

    @Test
    fun uniformsAreFoundInEachWayOfDeclaringThem() {
        val meta = ShaderMeta.parse(
            """
            uniform shader content;
            uniform float time;
            uniform  half2  touch ;
            layout(color) uniform half4 bg, fg, accent;
            layout( color ) uniform float4 dim;
            uniform float3 tint;
            uniform float levels[4];
            uniform int steps;
            """.trimIndent()
        )
        val byName = meta.uniforms.associateBy { it.name }
        assertEquals(
            listOf("content", "time", "touch", "bg", "fg", "accent", "dim", "tint", "levels", "steps"),
            meta.uniforms.map { it.name },
        )
        assertEquals(1, byName.getValue("time").floats)
        assertEquals(2, byName.getValue("touch").floats)
        assertEquals(4, byName.getValue("bg").floats)
        assertTrue(byName.getValue("accent").color)
        assertTrue(byName.getValue("dim").color)
        assertFalse(byName.getValue("tint").color)
        assertEquals(3, byName.getValue("tint").floats)
        // An array, an integer and a shader hold no numbers a setter knows.
        assertEquals(0, byName.getValue("levels").floats)
        assertEquals(0, byName.getValue("steps").floats)
        assertEquals(0, byName.getValue("content").floats)
        assertTrue(byName.getValue("levels").array)
    }

    @Test
    fun commentsDeclareNothing() {
        val meta = ShaderMeta.parse(
            """
            // uniform float one;
            /* uniform float two;
               uniform float three; */
            uniform shader content; // uniform float four;
            """.trimIndent()
        )
        assertEquals(listOf("content"), meta.uniforms.map { it.name })
    }

    @Test
    fun aShaderThatOnlyReadsWhatKolahalSetsHasNoProblems() {
        assertEquals(emptyList<String>(), ShaderMeta.problems(valid))
        // Its own uniforms are fine too: they stay 0, and that is said by `shader check`.
        assertEquals(listOf("mine"), ShaderMeta.parse("uniform shader content; uniform float mine; uniform float time;").unset())
        assertEquals(emptyList<String>(), ShaderMeta.problems("uniform shader content; uniform float mine;"))
    }

    @Test
    fun aKnownUniformOfAnotherTypeIsAProblem() {
        val problems = ShaderMeta.problems(
            "uniform shader content; uniform float time; uniform float4 resolution; uniform float bg; uniform float2 touchAge; uniform float cell[2]; uniform half4 fg;"
        )
        assertEquals(
            listOf(
                "'resolution' must be a float2, not float4",
                "'bg' must be a float4, not float",
                "'touchAge' must be a float, not float2",
                "'cell' must be a float2, not float[]",
            ),
            problems,
        )
    }

    @Test
    fun theSurfaceIsNeededAndNoOtherInput() {
        val noContent = ShaderMeta.problems("uniform float time;")
        assertEquals(1, noContent.size)
        assertTrue(noContent.single(), "uniform shader content" in noContent.single())
        assertEquals(
            listOf("'noise': the only shader input is content"),
            ShaderMeta.problems("uniform shader content; uniform shader noise;"),
        )
        // The input is a shader, so a name like it isn't enough.
        assertEquals(1, ShaderMeta.problems("uniform float2 content;").size)
    }

    @Test
    fun aFileTooLargeIsRefused() {
        val code = "uniform shader content;\n" + "// x".repeat(ShaderMeta.MAX_BYTES / 4)
        assertEquals(listOf("the file is larger than 64 KB"), ShaderMeta.problems(code))
    }

    @Test
    fun theCompilersLineIsFound() {
        val error = ShaderMeta.compileError("\nerror: 12: unknown identifier 'foo'\n    foo\n    ^^^\n1 error\n")
        assertEquals(12, error.line)
        assertEquals("unknown identifier 'foo'", error.text)
        assertEquals("line 12: unknown identifier 'foo'", error.toString())
        // Another form of error, and none: something is still said.
        val plain = ShaderMeta.compileError("Something else went wrong")
        assertNull(plain.line)
        assertEquals("Something else went wrong", plain.toString())
        assertEquals("it doesn't compile", ShaderMeta.compileError("").toString())
    }

    /**
     * The shaders that come with Kolahal, as far as a test without Android can check them: their
     * header, the uniforms they declare, and that nothing is refused. TtyShaderTest compiles them.
     */
    @Test
    fun theShadersThatComeWithKolahalAreInOrder() {
        for (name in ShaderCatalog.BUNDLED) {
            val source = sourceFile("src/main/assets/shaders/$name.agsl").readText()
            assertEquals(name, emptyList<String>(), ShaderMeta.problems(source))
            val meta = ShaderMeta.parse(source)
            assertNotNull(name, meta.title)
            assertNotNull(name, meta.description)
            assertEquals("$name reads uniforms nothing sets", emptyList<String>(), meta.unset())
            assertTrue(name, "half4 main(float2" in source)
            // Balanced, as a typo would leave it: the compiler is on the phone.
            val code = source.replace(Regex("//[^\n]*"), "")
            for ((open, close) in listOf("{" to "}", "(" to ")")) {
                assertEquals("$name has unbalanced $open$close", code.count { it == open[0] }, code.count { it == close[0] })
            }
        }
        // Only the one that says it moves is drawn over and over.
        assertEquals(
            listOf("rain"),
            ShaderCatalog.BUNDLED.filter { ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText()).animated },
        )
    }

    /** Every bundled shader has a file, and every file is a bundled shader. */
    @Test
    fun theBundledShadersAreTheFilesInAssets() {
        val files = sourceFile("src/main/assets/shaders").listFiles()!!.map { it.name }.sorted()
        assertEquals(ShaderCatalog.BUNDLED.map { "$it.${ShaderCatalog.EXT}" }.sorted(), files)
    }
}
