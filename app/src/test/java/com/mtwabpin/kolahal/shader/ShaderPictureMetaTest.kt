package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two additions for Lab's pictures: `uniform float seed;` and `// @still <seconds>`. */
class ShaderPictureMetaTest {
    private val body = "uniform shader content;\nhalf4 main(float2 xy) { return content.eval(xy); }\n"

    private fun source(name: String) = sourceFile("src/main/assets/shaders/$name.agsl").readText()

    @Test
    fun seedIsAUniformKolahalSets() {
        assertEquals(listOf(1), ShaderMeta.KNOWN[ShaderMeta.SEED])
        val code = "uniform float seed;\n$body"
        assertEquals(emptyList<String>(), ShaderMeta.parse(code).unset())
        assertEquals(emptyList<String>(), ShaderMeta.problems(code))
        assertTrue(ShaderMeta.problems("uniform float2 seed;\n$body").single().startsWith("'seed' must be a float"))
        assertTrue(
            ShaderMeta.problems("// @param seed 0..9 = 1 Seed\nuniform float seed;\n$body")
                .contains("'seed' can't be a parameter: Kolahal sets it"),
        )
    }

    @Test
    fun stillIsWherePicturesStart() {
        assertEquals(41.5f, ShaderMeta.parse("// @still 41.5\n$body").still)
        assertEquals(0f, ShaderMeta.parse("// @still 0\n$body").still)
        assertEquals(120f, ShaderMeta.parse("// @still 120\n$body").still)
        assertNull(ShaderMeta.parse(body).still)
        assertEquals(emptyList<String>(), ShaderMeta.warnings("// @still 41.5\n$body"))
    }

    @Test
    fun aStillThatCantBeReadIsLeftOutWithAWarning() {
        for (said in listOf("200", "-1", "soon", "")) {
            val code = "// @still $said\n$body"
            assertNull(said, ShaderMeta.parse(code).still)
            val quoted = if (said.isEmpty()) "'@still'" else "'@still $said'"
            assertEquals(listOf("$quoted: write the seconds, from 0 to 120"), ShaderMeta.warnings(code))
            // Left out: nothing keeps the shader from being used.
            assertEquals(emptyList<String>(), ShaderMeta.problems(code))
        }
    }

    @Test
    fun pondAndDunesDeclareSeedAndPondStartsWhereItLooksBest() {
        for (name in listOf("pond", "dunes")) {
            val code = source(name)
            val meta = ShaderMeta.parse(code)
            assertTrue(name, meta.uses(ShaderMeta.SEED))
            assertEquals(name, emptyList<String>(), ShaderMeta.problems(code))
            assertEquals(name, emptyList<String>(), ShaderMeta.warnings(code))
            assertEquals(name, emptyList<String>(), meta.unset())
            // A local of the same name would hide the uniform.
            assertTrue(name, !Regex("""\bfloat\s+seed\b""").containsMatchIn(code.replace("uniform float seed;", "")))
        }
        assertEquals(41.5f, ShaderMeta.parse(source("pond")).still)
        assertNull(ShaderMeta.parse(source("dunes")).still)
    }

    /** Every other shipped shader is as it was: none declares seed or @still. */
    @Test
    fun noOtherShippedShaderHasThem() {
        val others = sourceFile("src/main/assets/shaders").listFiles().orEmpty()
            .filter { it.extension == "agsl" && it.nameWithoutExtension !in setOf("pond", "dunes") }
        assertTrue(others.size > 40)
        for (f in others) {
            val meta = ShaderMeta.parse(f.readText())
            assertTrue(f.name, !meta.uses(ShaderMeta.SEED) && meta.still == null)
        }
    }
}
