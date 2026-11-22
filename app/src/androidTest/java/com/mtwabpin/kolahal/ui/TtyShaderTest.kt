package com.mtwabpin.kolahal.ui

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderClock
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.tty.ActiveShaders
import com.mtwabpin.kolahal.ui.tty.GravitySource
import com.mtwabpin.kolahal.ui.tty.LiveTilt
import com.mtwabpin.kolahal.ui.tty.LocalShaderSources
import com.mtwabpin.kolahal.ui.tty.OnGravity
import com.mtwabpin.kolahal.ui.tty.ShaderSources
import com.mtwabpin.kolahal.ui.tty.watchTouches
import com.mtwabpin.kolahal.waitFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `screen.shader`: a shader over home on the grid and over the full-screen screens. It needs Android 13,
 * so on older versions these tests only check that nothing is drawn.
 */
@RunWith(AndroidJUnit4::class)
class TtyShaderTest {
    @get:Rule
    val vms = TestViewModels()

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val configReset = ConfigReset()

    @get:Rule
    val lines = LogLinesSince()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val logs = LogRepository.get(app)
    private val commands = CommandRunner(app)

    /** This class's own files: none is left behind. */
    private val mine = listOf("zz-fine", "zz-broken", "zz-wrong", "zz-lean", "zz-press")

    @Before
    fun setUp() {
        ShaderSafety.get(app).turnedOn()
        mine.forEach { Shaders.remove(app, it) }
    }

    @After
    fun tearDown() {
        mine.forEach { Shaders.remove(app, it) }
        ShaderSafety.get(app).turnedOn()
    }

    private val supported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun needsAndroid13() = assumeTrue("shaders need Android 13", supported)

    private fun home(
        style: String = "tty",
        sources: ShaderSources = ShaderSources(),
        shown: () -> Boolean = { true },
    ): TerminalViewModel {
        config.set(Keys.HOME_STYLE, style)
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalShaderSources provides sources, LocalTerminalShown provides shown()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(WindowInsets.safeDrawing)
                    ) {
                        Box(Modifier.size(360.dp, 700.dp)) { TerminalScreen(vm) }
                        HomeScreenOverlay(vm)
                    }
                }
            }
        }
        return vm
    }

    private fun ttyHome(sources: ShaderSources = ShaderSources(), shown: () -> Boolean = { true }) = home("tty", sources, shown).also {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tty-home").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The lines the test made, newest last. */
    private fun newLines(): List<LogEntry> = logs.recent(400, afterId = lines.start)

    private fun errors(text: String) = newLines().filter { it.kind == LogKind.ERROR && text in it.text }

    private fun drawn() = ActiveShaders.names.value

    /** The shaders that come with Kolahal compile on this phone's Android. */
    @Test
    fun theBundledShadersCompile() {
        needsAndroid13()
        for (name in ShaderCatalog.BUNDLED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertNull(name, Shaders.compileError(Shaders.bundled(app, name)))
            }
        }
    }

    @Test
    fun aShaderIsDrawnOverHomeAndOffTakesItAway() {
        needsAndroid13()
        ttyHome()
        assertTrue("drawn before it is set: ${drawn()}", drawn().isEmpty())
        config.set(Keys.SCREEN_SHADER, "bloom")
        compose.waitFor("bloom isn't drawn") { drawn() == listOf("bloom") }
        // Another one takes its place, live.
        config.set(Keys.SCREEN_SHADER, "crt")
        compose.waitFor("crt isn't drawn") { drawn() == listOf("crt") }
        commands.run("shader off")
        compose.waitFor("it is still drawn") { drawn().isEmpty() }
        assertEquals("off", config[Keys.SCREEN_SHADER])
    }

    /** The GUI home has it too, and loses it when it is off. */
    @Test
    fun theGuiHomeHasTheShader() {
        needsAndroid13()
        config.set(Keys.SCREEN_SHADER, "bloom")
        home(style = "gui")
        compose.waitFor("bloom isn't drawn over the gui home") { drawn() == listOf("bloom") }
        config.set(Keys.SCREEN_SHADER, "off")
        compose.waitFor("it is still drawn") { drawn().isEmpty() }
    }

    /** The full-screen screens have it in either home style. */
    @Test
    fun aScreenHasTheShaderInTheGuiStyleToo() {
        needsAndroid13()
        config.set(Keys.SCREEN_SHADER, "bloom")
        val vm = home(style = "gui")
        // matrix, as the clock screen never lets Compose's test clock come to rest, with or without a shader.
        compose.waitFor("bloom isn't drawn over the gui home") { drawn() == listOf("bloom") }
        compose.runOnIdle { vm.showTtyScreen("matrix") }
        // Home's and the screen's: two surfaces draw it.
        compose.waitFor("bloom isn't drawn over the screen") { drawn() == listOf("bloom", "bloom") }
        compose.runOnIdle { vm.closeTtyScreen() }
        compose.waitFor("the screen still draws it") { drawn() == listOf("bloom") }
    }

    /** A shader that doesn't compile is a line with the compiler's line and message, and home stays as it is. */
    @Test
    fun aBrokenShaderLogsItsErrorAndDrawsNothing() {
        needsAndroid13()
        Shaders.save(app, "zz-broken", "uniform shader content;\nhalf4 main(float2 p) {\n    return nope;\n}\n")
        ttyHome()
        config.set(Keys.SCREEN_SHADER, "zz-broken")
        compose.waitFor("no line says it is broken") { errors("Shader zz-broken:").isNotEmpty() }
        val error = errors("Shader zz-broken:").single().text
        assertTrue(error, "line 3" in error)
        assertTrue("${drawn()}", drawn().isEmpty())
        compose.onNodeWithTag("tty-home").assertExists()
    }

    /** A uniform of the wrong type is caught before the compiler, with the same one line. */
    @Test
    fun aWrongUniformIsCaughtFirst() {
        needsAndroid13()
        Shaders.save(app, "zz-wrong", "uniform shader content; uniform float2 time; half4 main(float2 p) { return content.eval(p); }")
        ttyHome()
        config.set(Keys.SCREEN_SHADER, "zz-wrong")
        compose.waitFor("no line says it is wrong") { errors("'time' must be a float").isNotEmpty() }
        assertTrue("${drawn()}", drawn().isEmpty())
    }

    /** `shader use` keeps the shader that is on when the new one is broken, and says why. */
    @Test
    fun useKeepsTheOldOneWhenTheNewOneIsBroken() {
        needsAndroid13()
        Shaders.save(app, "zz-broken", "uniform shader content;\nhalf4 main(float2 p) { return nope; }")
        commands.run("shader use bloom")
        assertEquals("bloom", config[Keys.SCREEN_SHADER])
        commands.run("shader use zz-broken")
        assertEquals("bloom", config[Keys.SCREEN_SHADER])
        assertTrue(errors("Shader zz-broken: line 2").isNotEmpty())
    }

    /** A shader that was turned off (too slow, or a crash) isn't drawn until it is turned on again. */
    @Test
    fun aTrippedShaderIsNotDrawnUntilItIsTurnedOnAgain() {
        needsAndroid13()
        ttyHome()
        val id = ShaderSafety.id("bloom", Shaders.bundled(app, "bloom"))
        ShaderSafety.get(app).trip(id)
        config.set(Keys.SCREEN_SHADER, "bloom")
        compose.waitForIdle()
        // Read in the background: give it time to have drawn it.
        Thread.sleep(700)
        assertTrue("${drawn()}", drawn().isEmpty())
        commands.run("shader on")
        compose.waitFor("it isn't drawn") { drawn() == listOf("bloom") }
    }

    /** Two fingers held on home turn the shader off, whatever it does to the screen. */
    @Test
    fun twoFingersHeldTurnItOff() {
        needsAndroid13()
        ttyHome()
        config.set(Keys.SCREEN_SHADER, "bloom")
        compose.waitFor("bloom isn't drawn") { drawn() == listOf("bloom") }
        compose.onNodeWithTag("tty-home").performTouchInput {
            down(0, Offset(width * 0.3f, height * 0.3f))
            down(1, Offset(width * 0.7f, height * 0.3f))
            advanceEventTime(2_500)
            up(0)
            up(1)
        }
        compose.waitFor("the shader is still on") { config[Keys.SCREEN_SHADER] == "off" }
        compose.waitFor("it is still drawn") { drawn().isEmpty() }
        assertTrue(newLines().any { "two fingers" in it.text })
    }

    /** The full-screen host is under it without `screen.shader` too: an old Android simply draws nothing. */
    @Test
    fun onOldAndroidNothingIsDrawnAndTheCommandSaysWhy() {
        assumeTrue("this phone has shaders", !supported)
        ttyHome()
        config.set(Keys.SCREEN_SHADER, "bloom")
        compose.waitForIdle()
        assertTrue(drawn().isEmpty())
        assertTrue(commands.run("shader use crt").any { "Android 13" in it })
        assertTrue(commands.run("shader").any { "needs Android 13" in it })
    }

    // ---- the lean of the phone -------------------------------------------------------------

    /** A gravity sensor that counts, and gives what the test says. */
    private class FakeGravity : GravitySource {
        @Volatile
        var starts = 0

        @Volatile
        var stops = 0

        @Volatile
        var to: OnGravity? = null

        override fun start(to: OnGravity): Boolean {
            starts++
            this.to = to
            return true
        }

        override fun stop() {
            stops++
            to = null
        }
    }

    private val gravity = FakeGravity()

    /** The phone's state as the shader is told it. The tests' own phone has animations off, so nothing would move. */
    @Volatile
    private var phone = ShaderClock.Phone(
        batterySaver = false, animationsOff = false, hot = false, charging = true, batteryPercent = 80,
    )
    private val sources = ShaderSources(phone = { phone }, gravity = { gravity })

    /** A still shader that reads the lean. A moving one would never let Compose's test clock come to rest. */
    private val leaning =
        "uniform shader content;\nuniform float2 tilt;\nhalf4 main(float2 p) { return content.eval(p) * (1.0 - 0.1 * abs(tilt.x)); }"

    private fun useLeaning() {
        Shaders.save(app, "zz-lean", leaning)
        config.set(Keys.SCREEN_SHADER, "zz-lean")
        compose.waitFor("zz-lean isn't drawn") { "zz-lean" in drawn() }
    }

    private fun touchHome() = compose.onNodeWithTag("tty-home").performTouchInput {
        down(Offset(width * 0.5f, height * 0.3f))
        up()
    }

    /** The phone lies flat, then its right side is lowered by 30 degrees. */
    private fun leanRight() = compose.runOnUiThread {
        val to = gravity.to!!
        var at = SystemClock.elapsedRealtimeNanos()
        fun read(x: Float, z: Float) = repeat(25) {
            to.on(x, 0f, z, false, 0, at)
            at += 20_000_000L
        }
        read(0f, 9.81f)
        read(-4.9f, 8.5f)
    }

    /** The motion sensor is read while a shader that declares `tilt` shows, and for no other. Then the lean goes back to 0. */
    @Test
    fun theMotionSensorIsReadOnlyForAShaderThatDeclaresTilt() {
        needsAndroid13()
        ttyHome(sources)
        config.set(Keys.SCREEN_SHADER, "bloom")
        compose.waitFor("bloom isn't drawn") { drawn() == listOf("bloom") }
        compose.waitForIdle()
        assertEquals(0, gravity.starts)
        useLeaning()
        compose.waitFor("the sensor isn't read") { gravity.starts == 1 }
        leanRight()
        assertTrue("${LiveTilt.x}", LiveTilt.x > 0.5f)
        config.set(Keys.SCREEN_SHADER, "bloom")
        compose.waitFor("the sensor is still read") { gravity.stops == 1 }
        compose.waitFor("it still leans") { LiveTilt.x == 0f && LiveTilt.y == 0f }
        assertEquals(1, gravity.starts)
    }

    /** Home and a screen over it share one listener, which goes with the last of them. */
    @Test
    fun twoSurfacesShareOneListener() {
        needsAndroid13()
        val vm = home(style = "gui", sources = sources)
        useLeaning()
        compose.waitFor("the sensor isn't read") { gravity.starts == 1 }
        compose.runOnIdle { vm.showTtyScreen("matrix") }
        compose.waitFor("zz-lean isn't drawn over the screen") { drawn() == listOf("zz-lean", "zz-lean") }
        compose.waitForIdle()
        compose.runOnIdle { vm.closeTtyScreen() }
        compose.waitFor("the screen still draws it") { drawn() == listOf("zz-lean") }
        compose.waitForIdle()
        assertEquals(1, gravity.starts)
        assertEquals(0, gravity.stops)
        config.set(Keys.SCREEN_SHADER, "off")
        compose.waitFor("the sensor is still read") { gravity.stops == 1 }
    }

    /** Behind the widgets page home doesn't read it. */
    @Test
    fun theSensorIsntReadBehindTheWidgetsPage() {
        needsAndroid13()
        var shown by mutableStateOf(true)
        ttyHome(sources) { shown }
        useLeaning()
        compose.waitFor("the sensor isn't read") { gravity.starts == 1 }
        compose.runOnIdle { shown = false }
        compose.waitFor("the sensor is still read") { gravity.stops == 1 }
        compose.runOnIdle { shown = true }
        compose.waitFor("the sensor isn't read again") { gravity.starts == 2 }
    }

    /** What stops a moving shader stops it, too: here battery saver. A touch after it has ended starts it again. */
    @Test
    fun theSensorIsntReadInBatterySaver() {
        needsAndroid13()
        phone = phone.copy(batterySaver = true)
        ttyHome(sources)
        useLeaning()
        compose.waitForIdle()
        Thread.sleep(700)
        assertEquals(0, gravity.starts)
        phone = phone.copy(batterySaver = false)
        touchHome()
        compose.waitFor("the sensor isn't read", 15_000) { gravity.starts == 1 }
        leanRight()
        phone = phone.copy(batterySaver = true)
        compose.waitFor("the sensor is still read", 15_000) { gravity.stops == 1 }
        compose.waitFor("it still leans") { LiveTilt.x == 0f && LiveTilt.y == 0f }
    }

    // ---- a finger --------------------------------------------------------------------------

    /** The press ends with the finger, and with a gesture that Android cancels; what is below still gets its taps. */
    @Test
    fun aPressEndsWithTheFingerAndWithACancel() {
        var pressed = false
        var taps = 0
        compose.setContent {
            Box(
                Modifier
                    .size(200.dp)
                    .testTag("surface")
                    .pointerInput(Unit) {
                        watchTouches(onTouch = {}, onPress = { pressed = it }, onHold = {})
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { taps++ })
            }
        }
        val surface = compose.onNodeWithTag("surface")
        fun isPressed() = compose.runOnIdle { pressed }
        assertFalse(isPressed())
        surface.performTouchInput { down(center) }
        assertTrue(isPressed())
        surface.performTouchInput { up() }
        assertFalse(isPressed())
        assertEquals(1, compose.runOnIdle { taps })

        surface.performTouchInput { down(center) }
        assertTrue(isPressed())
        surface.performTouchInput { cancel() }
        assertFalse("a canceled gesture still presses", isPressed())
        assertEquals(1, compose.runOnIdle { taps })

        // Two fingers: waiting whether they are held takes the events of both.
        surface.performTouchInput {
            down(0, Offset(width * 0.3f, height * 0.5f))
            down(1, Offset(width * 0.7f, height * 0.5f))
        }
        assertTrue(isPressed())
        surface.performTouchInput { up(0) }
        assertTrue(isPressed())
        surface.performTouchInput { up(1) }
        assertFalse(isPressed())
        surface.performTouchInput {
            down(0, Offset(width * 0.3f, height * 0.5f))
            down(1, Offset(width * 0.7f, height * 0.5f))
        }
        assertTrue(isPressed())
        surface.performTouchInput { cancel() }
        assertFalse("two canceled fingers still press", isPressed())
    }

    /** A still shader that declares `pressed` is drawn again when a finger comes down and when it goes. */
    @Test
    fun aStillShaderIsDrawnAgainOnPressAndRelease() {
        needsAndroid13()
        Shaders.save(
            app, "zz-press",
            "uniform shader content;\nuniform float pressed;\n" +
                    "half4 main(float2 p) { return half4(pressed, 0.0, 1.0 - pressed, 1.0) + content.eval(p) * 0.0; }",
        )
        ttyHome()
        config.set(Keys.SCREEN_SHADER, "zz-press")
        fun color(): Color =
            compose.onNodeWithTag("tty-home").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 3] }
        compose.waitFor("it isn't drawn") { color().let { it.blue > 0.8f && it.red < 0.2f } }
        compose.onNodeWithTag("tty-home").performTouchInput { down(Offset(width * 0.5f, height * 0.3f)) }
        compose.waitFor("a press isn't drawn") { color().let { it.red > 0.8f && it.blue < 0.2f } }
        compose.onNodeWithTag("tty-home").performTouchInput { cancel() }
        compose.waitFor("the end of the press isn't drawn") { color().let { it.blue > 0.8f && it.red < 0.2f } }
    }

    /** `shader` lists the bundled ones with their descriptions, and checks one. */
    @Test
    fun theCommandListsAndChecks() {
        val listed = commands.run("shader").joinToString("\n")
        for (name in ShaderCatalog.BUNDLED) assertTrue(listed, name in listed)
        if (supported) {
            assertTrue(commands.run("shader check crt").any { "compiles" in it })
            assertTrue(commands.run("shader check rain").any { "Animated" in it })
            assertTrue(commands.run("shader check foil").any { "Kolahal sets" in it && "tilt" in it })
            assertTrue(commands.run("shader check frost").any { "Kolahal sets" in it && "pressed" in it })
        }
        assertFalse(commands.lines("shader check zz-none").isEmpty())
        assertEquals("off", config[Keys.SCREEN_SHADER])
    }

    /** A shader from a file is the user's own: it shows in the list and can be used; `shader remove` deletes it. */
    @Test
    fun aFileOfYoursIsAShader() {
        Shaders.save(app, "zz-fine", "// @description Mine\nuniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }")
        assertTrue("zz-fine" in Shaders.userNames)
        assertTrue("zz-fine" in Keys.SCREEN_SHADER.choices)
        // The description is beside the name, in the line's own field, not in its text.
        val listed = commands.lines("shader").single { it.text.startsWith("zz-fine") }
        assertEquals("Mine", listed.ctx.values[LogContext.DESC])
        if (supported) {
            commands.run("shader use zz-fine")
            assertEquals("zz-fine", config[Keys.SCREEN_SHADER])
        }
        commands.run("shader remove zz-fine")
        assertFalse("zz-fine" in Shaders.userNames)
        // The setting falls back to none when its file is gone.
        assertEquals("off", config[Keys.SCREEN_SHADER])
    }

    /** Off always works, and says so when nothing is on. */
    @Test
    fun offWorksWithNothingOn() {
        assertTrue(commands.run("shader off").any { "No shader is on" in it })
    }
}
