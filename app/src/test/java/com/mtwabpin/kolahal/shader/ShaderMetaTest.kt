package com.mtwabpin.kolahal.shader

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

    /** The screen's rounded corners: one float that Kolahal sets, so no parameter can take its name. */
    @Test
    fun theHostKnowsTheCorner() {
        assertEquals(listOf(1), ShaderMeta.KNOWN[ShaderMeta.CORNER])
        val good = "uniform shader content; uniform half corner;"
        assertEquals(emptyList<String>(), ShaderMeta.problems(good))
        assertEquals(emptyList<String>(), ShaderMeta.parse(good).unset())
        assertTrue(ShaderMeta.parse(good).uses(ShaderMeta.CORNER))
        assertEquals(listOf("'corner' must be a float, not float2"), ShaderMeta.problems("uniform shader content; uniform float2 corner;"))
        assertEquals(
            listOf("'corner' can't be a parameter: Kolahal sets it"),
            ShaderMeta.problems("// @param corner 0.0..1.0 = 0.5 Corner\nuniform shader content; uniform float corner;"),
        )
    }

    /** The camera's cutout: four floats that Kolahal sets (left, top, right, bottom), so no parameter can take its name. */
    @Test
    fun theHostKnowsTheCutout() {
        assertEquals(listOf(4), ShaderMeta.KNOWN[ShaderMeta.CUTOUT])
        val good = "uniform shader content; uniform float4 cutout;"
        assertEquals(emptyList<String>(), ShaderMeta.problems(good))
        assertEquals(emptyList<String>(), ShaderMeta.parse(good).unset())
        assertTrue(ShaderMeta.parse(good).uses(ShaderMeta.CUTOUT))
        assertEquals(listOf("'cutout' must be a float4, not float"), ShaderMeta.problems("uniform shader content; uniform float cutout;"))
        assertEquals(
            listOf("'cutout' can't be a parameter: Kolahal sets it"),
            ShaderMeta.problems("// @param cutout 0.0..1.0 = 0.5 Cutout\nuniform shader content; uniform float4 cutout;"),
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
            listOf("mountains", "pond", "rain", "snowfall", "grove-snow", "grove-autumn", "aurora", "night", "fireflies", "contour", "ink", "aura", "lanterns", "record", "candle", "hearts", "pulse"),
            ShaderCatalog.BUNDLED.filter { ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText()).animated },
        )
    }

    /** The motion sensor is read for these and no others, and only frost and rain ask whether a finger is down. No shader that comes with Kolahal reads the battery. */
    @Test
    fun theBundledShadersThatFollowThePhone() {
        val metas = ShaderCatalog.BUNDLED.associateWith {
            ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText())
        }
        assertEquals(listOf("foil", "mountains", "rain", "snowfall", "night"), metas.filterValues { it.tilt }.keys.toList())
        assertEquals(setOf("frost", "rain"), metas.filterValues { it.uses(ShaderMeta.PRESSED) }.keys)
        assertEquals(
            emptySet<String>(),
            metas.filterValues { it.uses(ShaderMeta.BATTERY) || it.uses(ShaderMeta.CHARGING) }.keys,
        )
    }

    /**
     * The app selector on home is under a shader that works on the text itself, as the rest of home; over
     * any other, a scene that would wash out its icons. Of those that come with Kolahal, these five.
     */
    @Test
    fun theShadersForTextCoverTheSelector() {
        assertTrue(ShaderMeta.parse("// @text\nuniform shader content;").coversSelector)
        assertFalse(ShaderMeta.parse("uniform shader content;").coversSelector)
        assertEquals(
            setOf("crt", "lcd", "eink", "foil", "riso"),
            ShaderCatalog.BUNDLED.filter { ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText()).coversSelector }.toSet(),
        )
    }

    /** A one-off shader says for how long it runs, within bounds, and moves. */
    @Test
    fun aOneOffShader() {
        fun once(line: String) = ShaderMeta.parse("$line\nuniform shader content;")
        assertEquals(6f, once("// @once 6").once)
        assertTrue(once("// @once 6").animated)
        assertEquals(ShaderPlay.MAX_SECONDS, once("// @once 900").once)
        assertEquals(ShaderPlay.MIN_SECONDS, once("// @once 0").once)
        for (none in listOf("", "// @once", "// @once soon")) assertNull(none, once(none).once)
        // It can be told how far it has come.
        assertTrue(ShaderMeta.PROGRESS in ShaderMeta.KNOWN)
        assertTrue("must be a float" in ShaderMeta.problems("uniform shader content; uniform float2 progress;").single())
        assertEquals(listOf("hearts", "pulse"), ShaderCatalog.BUNDLED.filter { ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText()).once != null })
    }

    /** How often it needs to be drawn: a whole number of frames a second, and the seconds a touch lasts, each within its range. */
    @Test
    fun howOftenItIsDrawn() {
        fun meta(vararg lines: String) = ShaderMeta.parse(lines.joinToString("\n") + "\nuniform shader content;")
        fun warnings(vararg lines: String) = ShaderMeta.warnings(lines.joinToString("\n") + "\nuniform shader content;")
        val both = meta("// @animated", "// @fps 10", "// @settles 4")
        assertEquals(10, both.fps)
        assertEquals(4f, both.settles)
        // The ends of the ranges.
        assertEquals(1, meta("// @fps 1").fps)
        assertEquals(ShaderClock.FPS, meta("// @fps 30").fps)
        assertEquals(0.5f, meta("// @settles 0.5").settles)
        assertEquals(60f, meta("// @settles 60").settles)
        assertEquals(emptyList<String>(), warnings("// @fps 1", "// @settles 0.5"))
        assertEquals(emptyList<String>(), warnings("// @FPS 30", "// @settles 60"))
        // Without the lines: null, and nothing to say.
        val bare = meta("// @animated")
        assertNull(bare.fps)
        assertNull(bare.settles)
        assertEquals(emptyList<String>(), warnings("// @animated"))
        assertEquals(emptyList<String>(), ShaderMeta.warnings(valid))
        // A line that can't be read is left out, with what to write.
        for (bad in listOf("0", "31", "10.5", "ten", "-3")) {
            assertNull(bad, meta("// @fps $bad").fps)
            assertEquals(listOf("'@fps $bad': write a whole number from 1 to 30"), warnings("// @fps $bad"))
        }
        assertEquals(listOf("'@fps': write a whole number from 1 to 30"), warnings("// @fps"))
        for (bad in listOf("0.4", "0", "61", "soon", "NaN", "-1")) {
            assertNull(bad, meta("// @settles $bad").settles)
            assertEquals(listOf("'@settles $bad': write the seconds, from 0.5 to 60"), warnings("// @settles $bad"))
        }
        assertEquals(listOf("'@settles': write the seconds, from 0.5 to 60"), warnings("// @settles"))
        assertEquals(2, warnings("// @fps 0", "// @settles 90").size)
        // They keep nothing from being used.
        assertEquals(emptyList<String>(), ShaderMeta.problems("// @fps 0\n// @settles\nuniform shader content;"))
        // The first of two wins, as for the other lines.
        assertEquals(12, meta("// @fps 12", "// @fps 5").fps)
    }

    /** What it works well over. What can't be read isn't rated; every bundled shader is, for all three. */
    @Test
    fun whatItSuits() {
        assertEquals(
            mapOf(ShaderSurface.DARK to ShaderFit.GOOD, ShaderSurface.WALLPAPER to ShaderFit.POOR),
            ShaderSuits.read("Dark good, light great, wallpaper  poor, paper good, light"),
        )
        assertEquals(emptyMap<ShaderSurface, ShaderFit>(), ShaderMeta.parse("uniform shader content;").suits)
        for (name in ShaderCatalog.BUNDLED) {
            val suits = ShaderMeta.parse(sourceFile("src/main/assets/shaders/$name.agsl").readText()).suits
            assertEquals(name, ShaderSurface.entries.toSet(), suits.keys)
            // One that is poor on both themes wouldn't be worth having.
            assertTrue(name, suits[ShaderSurface.DARK] != ShaderFit.POOR || suits[ShaderSurface.LIGHT] != ShaderFit.POOR)
        }
    }

    /** Every bundled shader and template has a file, and every file is a bundled shader or a template. */
    @Test
    fun theBundledShadersAreTheFilesInAssets() {
        val files = sourceFile("src/main/assets/shaders").listFiles()!!.map { it.name }.sorted()
        assertEquals((ShaderCatalog.BUNDLED + ShaderCatalog.TEMPLATES).map { "$it.${ShaderCatalog.EXT}" }.sorted(), files)
    }

    /**
     * The templates can be used as they come, are short, and each shows its one idea: the header lines and
     * the uniforms it is about. None is a bundled shader.
     */
    @Test
    fun theTemplatesAreInOrder() {
        val metas = ShaderCatalog.TEMPLATES.associateWith { name ->
            val source = sourceFile("src/main/assets/shaders/$name.agsl").readText()
            assertTrue(name, name.startsWith("template-") && name !in ShaderCatalog.BUNDLED && ShaderCatalog.valid(name))
            assertEquals(name, emptyList<String>(), ShaderMeta.problems(source))
            assertEquals(name, emptyList<String>(), ShaderMeta.warnings(source))
            assertTrue(name, source.trimEnd().lines().size in 25..50)
            assertTrue(name, "half4 main(float2" in source)
            val code = source.replace(Regex("//[^\n]*"), "")
            for ((open, close) in listOf("{" to "}", "(" to ")")) {
                assertEquals("$name has unbalanced $open$close", code.count { it == open[0] }, code.count { it == close[0] })
            }
            val meta = ShaderMeta.parse(source)
            assertNotNull(name, meta.title)
            assertNotNull(name, meta.description)
            assertEquals("$name reads uniforms nothing sets", emptyList<String>(), meta.unset())
            assertEquals(name, ShaderSurface.entries.toSet(), meta.suits.keys)
            meta
        }
        val tint = metas.getValue("template-tint")
        assertFalse(tint.animated)
        assertEquals(listOf("amount"), tint.params.map { it.name })
        val scene = metas.getValue("template-scene")
        assertTrue(scene.uses("dark") && scene.uses(ShaderMeta.WALLPAPER) && scene.uses("bg") && scene.uses("surface"))
        assertFalse(scene.animated)
        val moving = metas.getValue("template-moving")
        assertTrue(moving.animated && moving.uses("time"))
        assertEquals(10, moving.fps)
        // Its only clock is the touch's age: between touches it isn't drawn.
        val touch = metas.getValue("template-touch")
        assertTrue(touch.animated && touch.uses("touch") && touch.uses("touchAge") && touch.uses(ShaderMeta.PRESSED))
        assertFalse(touch.uses("time"))
        assertEquals(2f, touch.settles)
        val value = metas.getValue("template-value")
        assertEquals("battery", value.params.first { it.name == "level" }.follow)
        assertEquals(listOf("round", "square"), value.params.first { it.name == "ends" }.choices)
        assertFalse(value.animated)
        val once = metas.getValue("template-once")
        assertEquals(3f, once.once)
        assertTrue(once.uses(ShaderMeta.PROGRESS))
        // Only the one-off is one.
        assertEquals(listOf("template-once"), metas.filterValues { it.once != null }.keys.toList())
    }
}
