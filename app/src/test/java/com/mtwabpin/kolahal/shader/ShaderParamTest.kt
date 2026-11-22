package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A shader's parameters ([ShaderParam]): what a header can say, and which values they take. */
class ShaderParamTest {
    private fun read(line: String) = ShaderParam.read(line)

    @Test
    fun aNumberInARange() {
        val p = read("amount 0.0..2.0 = 1.0 Amount of snow")!!
        assertEquals("amount", p.name)
        assertEquals("Amount of snow", p.title)
        assertEquals("0..2", p.range)
        assertEquals(1f, p.default)
        assertFalse(p.whole)
        assertEquals(1.25f, p.parse("1.25"))
        assertEquals(2f, p.parse("9"))
        assertEquals(0f, p.parse("-3"))
        for (bad in listOf("", "much", "NaN", "Infinity")) assertNull(bad, p.parse(bad))
        assertEquals("1.35", p.text(1.3512f))
        assertEquals("1", p.text(0.999f))
        assertEquals("0", p.text(0f))
        // What it says is what it reads.
        assertEquals(p.text(0.777f), p.text(p.parse(p.text(0.777f))!!))
        assertEquals("-1.5..-0.5", read("a -1.5..-0.5 = -1.0")!!.range)
    }

    @Test
    fun wholeNumbersHaveNoPoint() {
        val p = read("petals 3..8 = 5")!!
        assertTrue(p.whole)
        assertEquals("Petals", p.title)
        assertEquals(4f, p.parse("4.4"))
        assertEquals("7", p.text(6.6f))
        assertFalse(read("petals 3..8 = 5.0")!!.whole)
        assertFalse(read("petals 3.0..8 = 5")!!.whole)
    }

    @Test
    fun oneOfSomeWords() {
        val p = read("shape round|star|ring = star Shape of the flakes")!!
        assertEquals(listOf("round", "star", "ring"), p.choices)
        assertEquals(1f, p.default)
        assertEquals(2f, p.parse("RING"))
        assertNull(p.parse("square"))
        assertNull(p.parse("1"))
        assertEquals("round", p.text(0f))
        assertEquals("ring", p.text(7f))
    }

    @Test
    fun whatCantBeReadIsNone() {
        for (bad in listOf(
            "", "amount", "amount 0..2", "amount 2..0 = 1", "amount 1..1 = 1", "amount 0..2 = 3", "amount 0..2 = some",
            "9lives 0..2 = 1", "shape one = one", "shape a|b = c", "shape a|A = a", "shape a||b = a", "shape a|b|c|d|e = a",
        )) assertNull(bad, read(bad))
    }

    private fun shader(params: String, uniforms: String = "uniform float amount;") =
        "// @name A\n$params\nuniform shader content;\n$uniforms\nhalf4 main(float2 p) { return content.eval(p); }"

    @Test
    fun theHeadersParametersAreRead() {
        val meta = ShaderMeta.parse(shader("// @param amount 0.0..2.0 = 1.0 Amount\n// @param kind a|b = b Kind", "uniform float amount, kind, other;"))
        assertEquals(listOf("amount", "kind"), meta.params.map { it.name })
        // A parameter is set, so it isn't one that stays 0.
        assertEquals(listOf("other"), meta.unset())
    }

    @Test
    fun whatIsWrongWithThemIsSaid() {
        fun problems(params: String, uniforms: String = "uniform float amount;") = ShaderMeta.problems(shader(params, uniforms))
        assertEquals(emptyList<String>(), problems("// @param amount 0.0..2.0 = 1.0"))
        assertEquals(emptyList<String>(), problems("// @param amount 0.0..2.0 = 1.0", "uniform half amount;"))
        assertTrue("can't be read" in problems("// @param amount lots").single())
        assertTrue("uniform float amount" in problems("// @param amount 0.0..2.0 = 1.0", "").single())
        assertTrue("uniform float amount" in problems("// @param amount 0.0..2.0 = 1.0", "uniform float2 amount;").single())
        assertTrue("Kolahal sets it" in problems("// @param time 0.0..2.0 = 1.0", "uniform float time;").single())
        assertTrue("twice" in problems("// @param amount 0.0..2.0 = 1.0\n// @param amount 0.0..3.0 = 1.0").single())
        val many = (1..9).joinToString("\n") { "// @param p$it 0.0..1.0 = 0.5" }
        assertTrue("9 parameters" in problems(many, (1..9).joinToString("\n") { "uniform float p$it;" }).single())
    }

    /** What is set counts as long as the parameter can have it; else its default. */
    @Test
    fun aValueThatNoLongerFitsIsTheDefault() {
        val p = read("shape round|star = star")!!
        assertEquals(0f, ShaderParams.of(mapOf("shape" to "round"), p))
        assertEquals(1f, ShaderParams.of(mapOf("shape" to "gone"), p))
        assertEquals(1f, ShaderParams.of(emptyMap(), p))
        val n = read("amount 0.0..1.0 = 0.5")!!
        assertEquals(1f, ShaderParams.of(mapOf("amount" to "1.8"), n))
    }

    /** The bundled shaders' parameters have nothing wrong, and each is used. */
    @Test
    fun theBundledOnesAreFine() {
        val with = ShaderCatalog.BUNDLED.filter { name ->
            val source = sourceFile("src/main/assets/shaders/$name.agsl").readText()
            assertEquals(name, emptyList<String>(), ShaderMeta.problems(source))
            val params = ShaderMeta.parse(source).params
            for (p in params) {
                val uses = Regex("\\b${p.name}\\b").findAll(source.substringAfter("half4 main(")).count()
                assertTrue("$name doesn't use ${p.name}", uses > 0)
                assertNotNull(p.title)
            }
            params.isNotEmpty()
        }
        assertTrue(with.toString(), with.containsAll(listOf("snowfall", "grove-snow", "grove-autumn", "rain", "fireflies")))
    }
}
