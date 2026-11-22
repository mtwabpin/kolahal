package com.mtwabpin.kolahal.ui

import android.app.Application
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.tty.ActiveShaders
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
 * `tty.shader`: a shader over home on the grid and over the full-screen screens. It needs Android 13,
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
    private val mine = listOf("zz-fine", "zz-broken", "zz-wrong")

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

    private fun home(style: String = "tty"): TerminalViewModel {
        config.set(Keys.HOME_STYLE, style)
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
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
        return vm
    }

    private fun ttyHome() = home().also {
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
        config.set(Keys.TTY_SHADER, "bloom")
        compose.waitFor("bloom isn't drawn") { drawn() == listOf("bloom") }
        // Another one takes its place, live.
        config.set(Keys.TTY_SHADER, "crt")
        compose.waitFor("crt isn't drawn") { drawn() == listOf("crt") }
        commands.run("shader off")
        compose.waitFor("it is still drawn") { drawn().isEmpty() }
        assertEquals("off", config[Keys.TTY_SHADER])
    }

    /** The GUI home has no shader: only the grid's surfaces do. */
    @Test
    fun theGuiHomeHasNoShader() {
        needsAndroid13()
        config.set(Keys.TTY_SHADER, "bloom")
        home(style = "gui")
        compose.waitForIdle()
        assertTrue("${drawn()}", drawn().isEmpty())
    }

    /** The full-screen screens have it in either home style. */
    @Test
    fun aScreenHasTheShaderInTheGuiStyleToo() {
        needsAndroid13()
        config.set(Keys.TTY_SHADER, "bloom")
        val vm = home(style = "gui")
        // matrix, as the clock screen never lets Compose's test clock come to rest, with or without a shader.
        compose.runOnIdle { vm.showTtyScreen("matrix") }
        compose.waitFor("bloom isn't drawn over the screen") { drawn() == listOf("bloom") }
        compose.runOnIdle { vm.closeTtyScreen() }
        compose.waitFor("it is still drawn") { drawn().isEmpty() }
    }

    /** A shader that doesn't compile is a line with the compiler's line and message, and home stays as it is. */
    @Test
    fun aBrokenShaderLogsItsErrorAndDrawsNothing() {
        needsAndroid13()
        Shaders.save(app, "zz-broken", "uniform shader content;\nhalf4 main(float2 p) {\n    return nope;\n}\n")
        ttyHome()
        config.set(Keys.TTY_SHADER, "zz-broken")
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
        config.set(Keys.TTY_SHADER, "zz-wrong")
        compose.waitFor("no line says it is wrong") { errors("'time' must be a float").isNotEmpty() }
        assertTrue("${drawn()}", drawn().isEmpty())
    }

    /** `shader use` keeps the shader that is on when the new one is broken, and says why. */
    @Test
    fun useKeepsTheOldOneWhenTheNewOneIsBroken() {
        needsAndroid13()
        Shaders.save(app, "zz-broken", "uniform shader content;\nhalf4 main(float2 p) { return nope; }")
        commands.run("shader use bloom")
        assertEquals("bloom", config[Keys.TTY_SHADER])
        commands.run("shader use zz-broken")
        assertEquals("bloom", config[Keys.TTY_SHADER])
        assertTrue(errors("Shader zz-broken: line 2").isNotEmpty())
    }

    /** A shader that was turned off (too slow, or a crash) isn't drawn until it is turned on again. */
    @Test
    fun aTrippedShaderIsNotDrawnUntilItIsTurnedOnAgain() {
        needsAndroid13()
        ttyHome()
        val id = ShaderSafety.id("bloom", Shaders.bundled(app, "bloom"))
        ShaderSafety.get(app).trip(id)
        config.set(Keys.TTY_SHADER, "bloom")
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
        config.set(Keys.TTY_SHADER, "bloom")
        compose.waitFor("bloom isn't drawn") { drawn() == listOf("bloom") }
        compose.onNodeWithTag("tty-home").performTouchInput {
            down(0, Offset(width * 0.3f, height * 0.3f))
            down(1, Offset(width * 0.7f, height * 0.3f))
            advanceEventTime(2_500)
            up(0)
            up(1)
        }
        compose.waitFor("the shader is still on") { config[Keys.TTY_SHADER] == "off" }
        compose.waitFor("it is still drawn") { drawn().isEmpty() }
        assertTrue(newLines().any { "two fingers" in it.text })
    }

    /** The full-screen host is under it without `tty.shader` too: an old Android simply draws nothing. */
    @Test
    fun onOldAndroidNothingIsDrawnAndTheCommandSaysWhy() {
        assumeTrue("this phone has shaders", !supported)
        ttyHome()
        config.set(Keys.TTY_SHADER, "bloom")
        compose.waitForIdle()
        assertTrue(drawn().isEmpty())
        assertTrue(commands.run("shader use crt").any { "Android 13" in it })
        assertTrue(commands.run("shader").any { "needs Android 13" in it })
    }

    /** `shader` lists the bundled ones with their descriptions, and checks one. */
    @Test
    fun theCommandListsAndChecks() {
        val listed = commands.run("shader").joinToString("\n")
        for (name in ShaderCatalog.BUNDLED) assertTrue(listed, name in listed)
        if (supported) {
            assertTrue(commands.run("shader check crt").any { "compiles" in it })
            assertTrue(commands.run("shader check rain").any { "Animated" in it })
        }
        assertFalse(commands.lines("shader check zz-none").isEmpty())
        assertEquals("off", config[Keys.TTY_SHADER])
    }

    /** A shader from a file is the user's own: it shows in the list and can be used; `shader remove` deletes it. */
    @Test
    fun aFileOfYoursIsAShader() {
        Shaders.save(app, "zz-fine", "// @description Mine\nuniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }")
        assertTrue("zz-fine" in Shaders.userNames)
        assertTrue("zz-fine" in Keys.TTY_SHADER.choices)
        // The description is beside the name, in the line's own field, not in its text.
        val listed = commands.lines("shader").single { it.text.startsWith("zz-fine") }
        assertEquals("Mine", listed.ctx.values[LogContext.DESC])
        if (supported) {
            commands.run("shader use zz-fine")
            assertEquals("zz-fine", config[Keys.TTY_SHADER])
        }
        commands.run("shader remove zz-fine")
        assertFalse("zz-fine" in Shaders.userNames)
        // The setting falls back to none when its file is gone.
        assertEquals("off", config[Keys.TTY_SHADER])
    }

    /** Off always works, and says so when nothing is on. */
    @Test
    fun offWorksWithNothingOn() {
        assertTrue(commands.run("shader off").any { "No shader is on" in it })
    }
}
