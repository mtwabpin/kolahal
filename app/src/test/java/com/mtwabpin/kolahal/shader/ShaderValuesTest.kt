package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.script.ScriptCheck
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptNames
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** The phone's use ([UsageValues]) is Kolahal's own, kept with what scripts give, so it reaches the live wallpaper the same way. */
    @Test
    fun thePhonesUse() {
        assertEquals("37", now("unlocks", given = mapOf("unlocks" to "37")))
        // Not worked out (nothing follows it, or no usage access): no value, so the slider counts.
        assertNull(now("screen"))
        assertTrue(ShaderValues.own("unlocks.usual"))
        assertTrue(ShaderValues.own("screen.h8"))
        assertTrue(ShaderValues.own("battery"))
        assertTrue(!ShaderValues.own("weather.rain"))
        assertEquals("Screen time against a usual day", ShaderValues.label("screen.usual"))
        assertEquals("Unlocks, day 3 of the last 7", ShaderValues.label("unlocks.d3"))
        try {
            ShaderValues.setUse(mapOf("unlocks" to "12"))
            assertEquals("12", ShaderValues.given.value["unlocks"])
            // The same again changes nothing, so the wallpaper's state isn't written again.
            assertTrue(!ShaderValues.setUse(mapOf("unlocks" to "12")))
            assertTrue(ShaderValues.setUse(mapOf("unlocks" to "13")))
        } finally {
            ShaderValues.setUse(emptyMap())
        }
        assertNull(ShaderValues.given.value["unlocks"])
    }

    /**
     * The phone's use, as it was last worked out, is kept with what scripts gave, so a parameter that follows it
     * starts there at the next start: under a name no script has, and it is no script's value.
     */
    @Test
    fun thePhonesUseIsKeptApartFromTheScripts() {
        assertFalse(ScriptNames.NAME.matches(ShaderValues.USE))
        val kept = mapOf(
            "weather" to """{"rain":"0.25"}""",
            ShaderValues.USE to """{"unlocks":"12","screen.usual":"1.25"}""",
            "unlocks" to """{"today":"3"}""",
        )
        assertEquals(mapOf("weather.rain" to "0.25"), ShaderValues.scripts(kept))
    }

    @Test
    fun thePhonesUseAsPeopleReadIt() {
        assertEquals("37", ShaderValues.shown("unlocks", "37"))
        assertEquals("95 min", ShaderValues.shown("screen", "94.6"))
        assertEquals("240 min", ShaderValues.shown("away", "240"))
        assertEquals("1.25", ShaderValues.shown("unlocks.usual", "1.25"))
        assertEquals("0.33", ShaderValues.shown("screen.h3", "0.333"))
    }

    /** Without a formula: unlocks over 100, screen time over 8 hours, time away over 4, a usual day at the middle. */
    @Test
    fun thePhonesUseSpreadOverARange() {
        assertEquals("0.37", ShaderValues.unit("unlocks", "37"))
        assertEquals("0.5", ShaderValues.unit("screen", "240"))
        assertEquals("0.25", ShaderValues.unit("away", "60"))
        assertEquals("0.5", ShaderValues.unit("unlocks.usual", "1"))
        assertEquals("1.0", ShaderValues.unit("screen.usual", "2"))
        assertEquals("0.4", ShaderValues.unit("screen.d3", "0.4"))
        val level = ShaderParam.read("level 0.0..2.0 = 0.5 Level")!!
        val given = mapOf("unlocks" to "150", "screen.usual" to "1")
        fun followed(id: String, formula: String? = null): Float? {
            try {
                ShaderValues.setUse(given)
                return ShaderParams.followed(level, id, formula, phone())
            } finally {
                ShaderValues.setUse(emptyMap())
            }
        }
        // Past the most, the parameter's most.
        assertEquals(2f, followed("unlocks"))
        assertEquals(1f, followed("screen.usual"))
        assertEquals(1.5f, followed("unlocks", "unlocks / 100"))
        assertNull(followed("away"))
    }

    /** A one-word id that is also the first word of others: each formula has its own. */
    @Test
    fun formulasWithThePhonesUse() {
        assertEquals(3.7f, ShaderValues.work("unlocks / 10", "unlocks", "37"))
        assertEquals(3f, ShaderValues.work("unlocks.usual * 2", "unlocks.usual", "1.5"))
        assertEquals(3f, ShaderValues.work("usual * 2", "unlocks.usual", "1.5"))
        assertEquals(0.5f, ShaderValues.work("h1 / 2", "screen.h1", "1"))
        assertEquals(2f, ShaderValues.work("min(2, screen / 60)", "screen", "300"))
        // `unlocks` is no part of `unlocks.usual`, and the other way round.
        assertNull(ShaderValues.work("unlocks.usual * 2", "unlocks", "37"))
        assertNull(ShaderValues.work("unlocks * 2", "unlocks.usual", "1.5"))
        // What follows `shader follow` or `@follow`, with and without the ids there are.
        val known = ShaderValues.BUILT_IN.keys + UsageValues.IDS
        for (k in listOf(known, null)) {
            assertEquals("unlocks" to null, ShaderValues.parse("unlocks", k))
            assertEquals("unlocks" to "unlocks / 10", ShaderValues.parse("unlocks / 10", k))
            assertEquals("unlocks.usual" to null, ShaderValues.parse("unlocks.usual", k))
            assertEquals("unlocks.usual" to "unlocks.usual / 2", ShaderValues.parse("unlocks.usual / 2", k))
            assertEquals("screen.h8" to "min(1, screen.h8 * 2)", ShaderValues.parse("min(1, screen.h8 * 2)", k))
            assertEquals("away" to "away / 60", ShaderValues.parse("away / 60", k))
        }
        val p = ShaderParam.read("level 0.0..2.0 = 0.5 Level @follow screen.usual")!!
        assertEquals("screen.usual", p.follow)
        assertEquals("unlocks", ShaderParam.read("count 0..9 = 3 Count @follow unlocks / 10")!!.follow)
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
