package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.terminal.ShaderActions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** `shader.give`: what a script gives shaders, how long it stays, and a parameter that follows it. */
@RunWith(AndroidJUnit4::class)
class ShaderGiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val phone = ShaderValues.Phone(1f, false, LocalTime.NOON)

    @get:Rule
    val logLines = LogLinesSince()

    @After
    fun tearDown() {
        for (name in listOf("giver", "giver2")) scripts.byName(name)?.let { scripts.delete(it.id) }
        ShaderParams.reset(context, "snowfall")
        ShaderValues.load(context)
    }

    private fun script(code: String, lang: ScriptLang = ScriptLang.LUA) = scripts.create("giver", lang, code)

    private fun host(s: Script) = HostApi(
        context, s.name, scriptId = s.id, notifyEnabled = false, grants = scripts.byId(s.id)!!.grants,
        gives = ScriptMeta.gives(s.code),
    )

    private fun run(s: Script, lang: ScriptLang = s.lang) =
        runBlocking { ScriptEngine.forLang(lang).run(s.code, "giver", host(s), timeoutMs = 10_000, repl = false) }

    private fun given(): Map<String, String> {
        ShaderValues.load(context)
        return ShaderValues.given.value
    }

    @Test
    fun bothLanguagesGive() {
        run(script("-- @gives rain, kind\nshader.give('rain', 0.25)\nshader.give('kind', 'heavy')"))
        assertEquals("0.25", given()["giver.rain"])
        assertEquals("heavy", given()["giver.kind"])
        scripts.delete(scripts.byName("giver")!!.id)
        assertNull(given()["giver.rain"])
        run(script("// @gives rain\nshader.give('rain', 0.75)", ScriptLang.JS))
        assertEquals("0.75", given()["giver.rain"])
    }

    @Test
    fun nothingTakesItAwayAndOnlyItsNamesCount() {
        val s = script("-- @gives rain")
        val host = host(s)
        host.shaderGive("rain", 1)
        assertEquals("1.0", given()["giver.rain"])
        host.shaderGive("rain", null)
        assertNull(given()["giver.rain"])
        assertThrows(IllegalStateException::class.java) { host.shaderGive("snow", 1) }
        assertThrows(IllegalArgumentException::class.java) { host.shaderGive("rain", "two words") }
        // The same again changes nothing.
        assertTrue(ShaderValues.give(context, "giver", "rain", "0.5"))
        assertFalse(ShaderValues.give(context, "giver", "rain", "0.5"))
    }

    @Test
    fun itGoesWithTheScript() {
        val s = script("-- @gives rain, snow")
        host(s).shaderGive("rain", 0.5)
        host(s).shaderGive("snow", 0.5)
        // Code that no longer names one.
        scripts.saveCode(s.id, "-- @gives rain", byUser = true)
        assertEquals(setOf("giver.rain"), given().keys.filter { it.startsWith("giver.") }.toSet())
        // Another name: what follows it goes on.
        val param = Shaders.params(context, "snowfall").first { it.name == "amount" }
        ShaderParams.setFollow(context, "snowfall", param, "giver.rain")
        scripts.rename(s.id, "giver2")
        assertEquals("0.5", given()["giver2.rain"])
        assertEquals("giver2.rain", ShaderParams.follow("snowfall", param))
        ShaderParams.load(context)
        assertEquals("giver2.rain", ShaderParams.follow("snowfall", param))
        scripts.setEnabled(s.id, false)
        assertNull(given()["giver2.rain"])
    }

    /** A script asks for a one-off shader once, if it may, and home takes it. */
    @Test
    fun aScriptPlaysAOneOffShader() {
        val s = script("-- @uses effects\nshader.play('hearts', 3)\nshader.play('hearts', 3)")
        run(s)
        assertNull("not one that isn't a one-off", ShaderPlay.load(context))
        assertEquals("hearts" to 3f, ShaderPlay.current.value?.let { it.name to it.seconds })
        ShaderPlay.done(ShaderPlay.current.value!!)
        // Taken: it doesn't play again.
        ShaderPlay.load(context)
        assertNull(ShaderPlay.current.value)
        // One that stays on isn't played; home says so.
        ShaderPlay.request(context, "lcd", null)
        assertEquals("lcd", ShaderPlay.load(context))
        assertNull(ShaderPlay.current.value)
        // Not allowed: nothing. Not declared: an error.
        scripts.setAccess(s.id, "effects", false)
        run(s)
        assertNull(ShaderPlay.load(context))
        assertNull(ShaderPlay.current.value)
        scripts.saveCode(s.id, "shader.play('hearts')", byUser = true)
        assertThrows(IllegalStateException::class.java) { host(scripts.byName("giver")!!).shaderPlay("hearts", null) }
    }

    /** The parameter is the value's while there is one; then the slider's. */
    @Test
    fun aParameterFollowsIt() {
        val s = script("-- @gives snow")
        val param = Shaders.params(context, "snowfall").first { it.name == "amount" }
        ShaderParams.set(context, "snowfall", param, 0.4f)
        ShaderParams.setFollow(context, "snowfall", param, "giver.snow")
        ShaderValues.load(context)
        assertEquals(0.4f, ShaderParams.now("snowfall", param, phone))
        host(s).shaderGive("snow", 0.75)
        given()
        assertEquals(1.5f, ShaderParams.now("snowfall", param, phone))
        assertTrue(ShaderActions.sources(context).any { it.id == "giver.snow" && it.value == "0.75" })
        ShaderParams.setFollow(context, "snowfall", param, null)
        assertEquals(0.4f, ShaderParams.now("snowfall", param, phone))
    }
}
