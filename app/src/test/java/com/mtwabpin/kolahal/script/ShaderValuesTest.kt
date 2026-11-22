package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** The values a shader's parameters can follow ([ShaderValues]), and what following one makes a parameter. */
class ShaderValuesTest {
    private fun phone(battery: Float = 0.5f, charging: Boolean = false, time: String = "12:00") =
        ShaderValues.Phone(battery, charging, LocalTime.parse(time))

    private fun now(id: String, phone: ShaderValues.Phone = phone(), given: Map<String, String> = emptyMap()) =
        ShaderValues.of(id, given, phone)

    @Test
    fun kolahalsOwn() {
        assertEquals("50.0", now("battery"))
        assertEquals("1.0", now("charging", phone(charging = true)))
        assertEquals("0.0", now("charging"))
        assertEquals("6.0", now("day", phone(time = "06:00")))
        fun night(time: String) = now("night", phone(time = time))!!.toFloat()
        assertEquals(1f, night("02:00"))
        assertEquals(0f, night("12:00"))
        assertEquals(0.5f, night("06:00"), 0.01f)
        assertEquals(0.5f, night("19:00"), 0.01f)
        assertEquals(1f, night("23:59"))
        // No script can take these names: a script's have its own name first.
        assertTrue(ShaderValues.BUILT_IN.keys.none { '.' in it })
        assertTrue(ShaderValues.BUILT_IN.keys.all { now(it) != null })
    }

    @Test
    fun aScriptsValueIsUnderItsName() {
        assertEquals("0.2", now("weather.rain", given = mapOf("weather.rain" to "0.2")))
        assertNull(now("weather.snow", given = mapOf("weather.rain" to "0.2")))
        assertEquals("weather · rain", ShaderValues.label("weather.rain"))
        assertEquals("Battery", ShaderValues.label("battery"))
    }

    @Test
    fun whatAScriptGivesIsANumberOrAWord() {
        assertEquals("0.5", ShaderValues.clean(0.5))
        // Its own units are for a formula.
        assertEquals("27.5", ShaderValues.clean(27.5))
        assertEquals("-2.0", ShaderValues.clean(-2))
        assertEquals("1", ShaderValues.clean(true))
        assertEquals("heavy", ShaderValues.clean(" heavy "))
        assertNull(ShaderValues.clean(null))
        for (bad in listOf("two words", "", "0.5", "<b>", "x".repeat(21), Double.NaN, listOf(1))) {
            assertThrows(bad.toString(), IllegalArgumentException::class.java) { ShaderValues.clean(bad) }
        }
    }

    @Test
    fun asPeopleReadThem() {
        assertEquals("64%", ShaderValues.shown("battery", "64.0"))
        assertEquals("yes", ShaderValues.shown("charging", "1.0"))
        assertEquals("0.25", ShaderValues.shown("weather.rain", "0.25"))
        assertEquals("0", ShaderValues.shown("weather.rain", "0.0"))
        assertEquals("heavy", ShaderValues.shown("weather.rain", "heavy"))
    }

    @Test
    fun aParameterSpreadsANumberOverItsRange() {
        val p = ShaderParam.read("amount 0.5..2.5 = 1.0 Amount")!!
        assertEquals(0.5f, p.followed("0"))
        assertEquals(1.5f, p.followed("0.5"))
        assertEquals(2.5f, p.followed("1.0"))
        assertNull(p.followed("heavy"))
        val whole = ShaderParam.read("camels 0..7 = 5")!!
        assertEquals(4f, whole.followed("0.5"))
    }

    /** Without a formula, a value with its own units goes over the whole range all the same. */
    @Test
    fun valuesInTheirOwnUnits() {
        assertEquals("0.5", ShaderValues.unit("battery", "50.0"))
        assertEquals("0.25", ShaderValues.unit("day", "6.0"))
        assertEquals("0.4", ShaderValues.unit("weather.rain", "0.4"))
        assertEquals("heavy", ShaderValues.unit("weather.rain", "heavy"))
        val camels = ShaderParam.read("camels 0..7 = 5")!!
        assertEquals(7f, ShaderParams.followed(camels, "battery", null, phone(battery = 1f)))
        assertEquals(5f, ShaderParams.followed(camels, "battery", "battery / 20", phone(battery = 1f)))
        assertEquals(1f, ShaderParams.followed(camels, "battery", "battery / 20", phone(battery = 0.2f)))
        assertEquals(0f, ShaderParams.followed(camels, "battery", "battery / 20", phone(battery = 0f)))
        // Taken into the range; nothing when it can't be worked out.
        assertEquals(7f, ShaderParams.followed(camels, "battery", "battery * 3", phone(battery = 1f)))
        assertNull(ShaderParams.followed(camels, "battery", "battery /", phone()))
        assertNull(ShaderParams.followed(camels, "weather.rain", "rain * 7", phone()))
    }

    @Test
    fun formulas() {
        fun work(formula: String, id: String = "battery", value: String = "80.0") = ShaderValues.work(formula, id, value)
        assertEquals(4f, work("battery / 20"))
        assertEquals(4f, work("battery/20"))
        assertEquals(4f, work("Battery ÷ 20"))
        assertEquals(4f, work("value / 20"))
        assertEquals(5f, work("min(5, battery / 10)"))
        assertEquals(2f, work("(100 - battery) / 10"))
        assertEquals(-1.5f, work("rain - 1", "weather.rain", "-0.5"))
        assertEquals(0f, work("weather.rain * 2 - 1", "weather.rain", "0.5"))
        // A longer name isn't the value, and a word is no number.
        for (bad in listOf("", "battery /", "batteryx / 2", "battery ; 2", "rain / 2")) assertNull(bad, work(bad))
        assertNull(work("rain * 2", "weather.rain", "heavy"))
        assertNull(work("1 / (battery - 80)"))
    }

    /** What is typed after `shader follow` or `@follow`: a value, alone or in a formula. */
    @Test
    fun aValueOrAFormula() {
        val known = listOf("battery", "day", "weather.rain")
        assertEquals("battery" to null, ShaderValues.parse("battery", known))
        assertEquals("battery" to "battery / 20", ShaderValues.parse("battery / 20", known))
        assertEquals("weather.rain" to "min(1, weather.rain * 2)", ShaderValues.parse("min(1, weather.rain * 2)", known))
        assertNull(ShaderValues.parse("tide / 2", known))
        val p = ShaderParam.read("camels 0..7 = 5 Camels @follow battery / 20")!!
        assertEquals(listOf("Camels", "battery", "battery / 20"), listOf(p.title, p.follow, p.formula))
        assertNull(ShaderParam.read("camels 0..7 = 5 Camels @follow battery")!!.formula)
    }

    @Test
    fun aParameterOfWordsTakesItsWord() {
        val p = ShaderParam.read("kind none|light|heavy = none Rain")!!
        assertEquals(1f, p.followed("Light"))
        assertNull(p.followed("hail"))
        assertEquals(2f, p.followed("1"))
        assertEquals(1f, p.followed("0.5"))
    }

    /** What was picked counts, or nothing; else what the shader suggests. */
    @Test
    fun whatItFollows() {
        val p = ShaderParam.read("amount 0.0..2.0 = 1.0 Amount of rain @follow weather.rain")!!
        assertEquals("Amount of rain", p.title)
        assertEquals("weather.rain", p.follow)
        assertEquals("weather.rain", ShaderParams.follow(emptyMap(), p))
        assertEquals("battery", ShaderParams.follow(mapOf("amount" to "battery"), p))
        assertNull(ShaderParams.follow(mapOf("amount" to ""), p))
        assertEquals("Amount", ShaderParam.read("amount 0.0..2.0 = 1.0 @follow battery")!!.title)
        assertNull(ShaderParam.read("amount 0.0..2.0 = 1.0 Amount")!!.follow)
    }

    @Test
    fun theGivesLine() {
        assertEquals(listOf("rain", "snow"), ScriptMeta.gives("-- @gives rain, snow\n-- @gives rain"))
        assertEquals(listOf("a"), ScriptMeta.gives("// @gives a 9b"))
        assertEquals(emptyList<String>(), ScriptMeta.gives("-- gives rain"))
        assertEquals(ShaderValues.MAX, ScriptMeta.gives("-- @gives " + ('a'..'z').joinToString()).size)
        assertTrue(ScriptCheck.check("-- @gives 9lives", com.mtwabpin.kolahal.data.ScriptLang.LUA).any { "9lives" in it.text })
        assertTrue(ScriptCheck.check("-- @gives rain", com.mtwabpin.kolahal.data.ScriptLang.LUA).isEmpty())
        // The library's weather gives what it says.
        assertEquals(listOf("rain", "snow"), ScriptMeta.gives(sourceFile("src/main/assets/library/weather.lua").readText()))
    }
}
