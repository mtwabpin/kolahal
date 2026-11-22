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

    /** What a shared shader says of itself, for its sheet and before it is installed. Empty lines say nothing. */
    @Test
    fun theHeaderOfASharedShaderIsRead() {
        val meta = ShaderMeta.parse("// @author ann\n// @version 1.2\n// @license MIT\n// @requires 0.5.17\nuniform shader content;")
        assertEquals(listOf("ann", "1.2", "MIT", "0.5.17"), listOf(meta.author, meta.version, meta.license, meta.requires))
        val bare = ShaderMeta.parse("// @author\nuniform shader content;")
        assertEquals(listOf(null, null, null, null), listOf(bare.author, bare.version, bare.license, bare.requires))
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

    /** What the phone gives a shader beside the screen: its lean, a finger and the battery, each with its own type. */
    @Test
    fun theHostKnowsTheLeanTheFingerAndTheBattery() {
        val good = "uniform shader content; uniform float2 tilt; uniform float pressed; uniform half battery, charging;"
        assertEquals(emptyList<String>(), ShaderMeta.problems(good))
        assertEquals(emptyList<String>(), ShaderMeta.parse(good).unset())
        assertEquals(
            listOf(
                "'tilt' must be a float2, not float",
                "'pressed' must be a float, not float2",
                "'battery' must be a float, not half4",
                "'charging' must be a float, not float[]",
            ),
            ShaderMeta.problems(
                "uniform shader content; uniform float tilt; uniform float2 pressed; uniform half4 battery; uniform float charging[2];"
            ),
        )
    }

    /** Only a shader that declares `tilt` uses it: a word in a comment or in a name doesn't. */
    @Test
    fun onlyADeclaredTiltCounts() {
        assertTrue(ShaderMeta.parse("uniform shader content; uniform float2 tilt;").tilt)
        assertTrue(ShaderMeta.parse("uniform float2 touch, tilt;").tilt)
        assertFalse(ShaderMeta.parse("uniform shader content; // uniform float2 tilt;").tilt)
        assertFalse(ShaderMeta.parse("uniform shader content; uniform float2 tilted;").tilt)
        val meta = ShaderMeta.parse("uniform shader content; uniform float battery;")
        assertTrue(meta.uses(ShaderMeta.BATTERY))
        assertFalse(meta.uses(ShaderMeta.CHARGING))
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
        // Only the ones that say they move are drawn over and over.
        assertEquals(
            listOf("rain", "snowfall", "aurora", "night", "fireflies", "contour", "ink"),
            ShaderCatalog.BUNDLED.filter { ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText()).animated },
        )
    }

    /** The motion sensor is read for these and no others, and only frost and rain ask whether a finger is down. No shader that comes with Kolahal reads the battery. */
    @Test
    fun theBundledShadersThatFollowThePhone() {
        val metas = ShaderCatalog.BUNDLED.associateWith {
            ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText())
        }
        assertEquals(listOf("foil", "rain", "snowfall", "night"), metas.filterValues { it.tilt }.keys.toList())
        assertEquals(setOf("frost", "rain"), metas.filterValues { it.uses(ShaderMeta.PRESSED) }.keys)
        assertEquals(
            emptySet<String>(),
            metas.filterValues { it.uses(ShaderMeta.BATTERY) || it.uses(ShaderMeta.CHARGING) }.keys,
        )
    }

    /** Every bundled shader has a file, and every file is a bundled shader. */
    @Test
    fun theBundledShadersAreTheFilesInAssets() {
        val files = sourceFile("src/main/assets/shaders").listFiles()!!.map { it.name }.sorted()
        assertEquals(ShaderCatalog.BUNDLED.map { "$it.${ShaderCatalog.EXT}" }.sorted(), files)
    }
}
