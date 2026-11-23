package com.mtwabpin.kolahal.ui.tty

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderClock
import com.mtwabpin.kolahal.shader.ShaderPlay
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.home.HomeScreenOverlay
import com.mtwabpin.kolahal.ui.home.LocalTerminalShown
import com.mtwabpin.kolahal.ui.home.TerminalScreen
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
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
    private val mine = listOf("zz-fine", "zz-broken", "zz-wrong", "zz-lean", "zz-press", "zz-fill", "zz-fill-text")

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
            view = LocalView.current
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

    /** The shaders that come with Kolahal, and the templates to start one from, compile on this phone's Android. */
    @Test
    fun theBundledShadersCompile() {
        needsAndroid13()
        for (name in ShaderCatalog.BUNDLED + ShaderCatalog.TEMPLATES) {
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
        config.set(Keys.SCREEN_SHADER, "lcd")
        compose.waitFor("lcd isn't drawn") { drawn() == listOf("lcd") }
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
        config.set(Keys.SCREEN_SHADER, "lcd")
        home(style = "gui")
        compose.waitFor("lcd isn't drawn over the gui home") { drawn() == listOf("lcd") }
        config.set(Keys.SCREEN_SHADER, "off")
        compose.waitFor("it is still drawn") { drawn().isEmpty() }
    }

    /** A one-off shader runs over the one that is on, in either home style, and goes by itself. */
    @Test
    fun aOneOffShaderPlaysOnTop() {
        needsAndroid13()
        config.set(Keys.SCREEN_SHADER, "lcd")
        home(style = "gui", sources = ShaderSources(phone = { ShaderClock.Phone(false, false, false, true, 100) }))
        compose.waitFor("lcd isn't drawn") { drawn() == listOf("lcd") }
        commands.run("shader play hearts 1")
        compose.waitFor("hearts isn't played") { drawn().toSet() == setOf("lcd", "hearts") }
        compose.waitFor("it doesn't end", 10_000) { drawn() == listOf("lcd") && ShaderPlay.current.value == null }
        // It isn't one that can be on.
        commands.run("shader hearts")
        assertEquals("lcd", config[Keys.SCREEN_SHADER])
        commands.run("shader play lcd")
        assertEquals(null, ShaderPlay.current.value)
    }

    /** In battery saver it isn't played, and doesn't wait for later. */
    @Test
    fun aOneOffShaderIsntPlayedInBatterySaver() {
        needsAndroid13()
        home(style = "tty", sources = ShaderSources(phone = { ShaderClock.Phone(true, false, false, true, 100) }))
        commands.run("shader play hearts 1")
        compose.waitFor("it waits") { ShaderPlay.current.value == null }
    }

    /**
     * A one-off is marked while it plays, as the shader on home is: one that was playing when Kolahal crashed is
     * turned off at the next start, and doesn't play again at the next word until a shader is turned on.
     */
    @Test
    fun aOneOffShaderThatCrashedDoesntPlayAgain() {
        needsAndroid13()
        config.set(Keys.SCREEN_SHADER, "off")
        home(style = "tty", sources = ShaderSources(phone = { ShaderClock.Phone(false, false, false, true, 100) }))
        val safety = ShaderSafety.get(app)
        val id = ShaderSafety.id("hearts", Shaders.bundled(app, "hearts"))
        commands.run("shader play hearts 5")
        compose.waitFor("hearts isn't played") { "hearts" in drawn() }
        // What the next start finds, if Kolahal crashes now.
        assertEquals(id, safety.recover(ShaderSafety.Exit.CRASH))
        compose.waitFor("it doesn't end", 10_000) { ShaderPlay.current.value == null && "hearts" !in drawn() }
        safety.trip(id)
        commands.run("shader play hearts 1")
        // Turned off, it is never drawn: not at once, and not a moment later.
        repeat(15) {
            compose.waitForIdle()
            assertTrue("${drawn()}", "hearts" !in drawn())
            Thread.sleep(100)
        }
        compose.waitFor("it waits") { ShaderPlay.current.value == null }
        safety.turnedOn()
        commands.run("shader play hearts 1")
        compose.waitFor("hearts isn't played") { "hearts" in drawn() }
    }

    /** The full-screen screens have it in either home style. */
    @Test
    fun aScreenHasTheShaderInTheGuiStyleToo() {
        needsAndroid13()
        config.set(Keys.SCREEN_SHADER, "lcd")
        val vm = home(style = "gui")
        // matrix, as the clock screen never lets Compose's test clock come to rest, with or without a shader.
        compose.waitFor("lcd isn't drawn over the gui home") { drawn() == listOf("lcd") }
        compose.runOnIdle { vm.showTtyScreen("matrix") }
        // Home's and the screen's: two surfaces draw it.
        compose.waitFor("lcd isn't drawn over the screen") { drawn() == listOf("lcd", "lcd") }
        compose.runOnIdle { vm.closeTtyScreen() }
        compose.waitFor("the screen still draws it") { drawn() == listOf("lcd") }
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
        commands.run("shader use lcd")
        assertEquals("lcd", config[Keys.SCREEN_SHADER])
        commands.run("shader use zz-broken")
        assertEquals("lcd", config[Keys.SCREEN_SHADER])
        assertTrue(errors("Shader zz-broken: line 2").isNotEmpty())
    }

    /** A shader that was turned off (a crash) isn't drawn until it is turned on again. */
    @Test
    fun aTrippedShaderIsNotDrawnUntilItIsTurnedOnAgain() {
        needsAndroid13()
        ttyHome()
        val id = ShaderSafety.id("lcd", Shaders.bundled(app, "lcd"))
        ShaderSafety.get(app).trip(id)
        config.set(Keys.SCREEN_SHADER, "lcd")
        compose.waitForIdle()
        // Read in the background: give it time to have drawn it.
        Thread.sleep(700)
        assertTrue("${drawn()}", drawn().isEmpty())
        commands.run("shader on")
        compose.waitFor("it isn't drawn") { drawn() == listOf("lcd") }
    }

    /** Two fingers held on home turn the shader off, whatever it does to the screen. */
    @Test
    fun twoFingersHeldTurnItOff() {
        needsAndroid13()
        ttyHome()
        config.set(Keys.SCREEN_SHADER, "lcd")
        compose.waitFor("lcd isn't drawn") { drawn() == listOf("lcd") }
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
        config.set(Keys.SCREEN_SHADER, "lcd")
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
        config.set(Keys.SCREEN_SHADER, "lcd")
        compose.waitFor("lcd isn't drawn") { drawn() == listOf("lcd") }
        compose.waitForIdle()
        assertEquals(0, gravity.starts)
        useLeaning()
        compose.waitFor("the sensor isn't read") { gravity.starts == 1 }
        leanRight()
        assertTrue("${LiveTilt.x}", LiveTilt.x > 0.5f)
        config.set(Keys.SCREEN_SHADER, "lcd")
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

    // ---- the app selector ------------------------------------------------------------------

    /** The view home is drawn in, for putting the keyboard away ([keyboardDown]). */
    private var view: android.view.View? = null

    /** Puts the keyboard away, as a person does before sliding on the edge: the selector's strip shows only then. */
    private fun keyboardDown() {
        val v = view ?: return
        val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
        compose.runOnUiThread {
            val window = (v.context as? android.app.Activity)?.window ?: return@runOnUiThread
            androidx.core.view.WindowCompat.getInsetsController(window, v).hide(ime)
        }
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(v)?.isVisible(ime) != true }
    }

    /**
     * A still shader that paints all of home magenta, a color no app icon has in its middle: one for text
     * ([text]), or one that isn't, as a scene.
     */
    private fun useFill(text: Boolean) {
        val name = if (text) "zz-fill-text" else "zz-fill"
        Shaders.save(
            app, name,
            (if (text) "// @text\n" else "") +
                    "uniform shader content;\nhalf4 main(float2 p) { return half4(1.0, 0.0, 1.0, 1.0) + content.eval(p) * 0.0; }",
        )
        config.set(Keys.SCREEN_SHADER, name)
        compose.waitFor("$name isn't drawn") { name in drawn() }
    }

    private fun isFill(c: Color) = c.red > 0.95f && c.green < 0.05f && c.blue > 0.95f

    private fun icons() = compose.onAllNodesWithTag("app-selector-icon", useUnmergedTree = true)

    /** Slides along the edge and lifts there, so the list stays open with its icons. */
    private fun openSelector() {
        keyboardDown()
        compose.waitFor("no selector") { compose.onAllNodesWithTag("app-selector").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-selector").performTouchInput {
            down(center)
            moveBy(Offset(0f, 20.dp.toPx()), delayMillis = 50)
        }
        compose.waitFor("the list doesn't open") { icons().fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-selector").performTouchInput { up() }
        compose.waitForIdle()
    }

    /**
     * The highest app icon the open list shows whole (a row cut by the list's edge is left out): where it
     * is, and the color in its middle, as the screen shows it. One picture; null while the list shows none.
     */
    private fun topIcon(): Pair<Rect, Color>? {
        val nodes = icons().fetchSemanticsNodes()
        val i = nodes.indices
            .filter { nodes[it].boundsInRoot.let { b -> b.width > 0f && b.height >= b.width - 1f } }
            .minByOrNull { nodes[it].boundsInRoot.top } ?: return null
        val picture = icons()[i].captureToImage().toPixelMap()
        return nodes[i].boundsInRoot to picture[picture.width / 2, picture.height / 2]
    }

    /**
     * A shader that isn't for text (a scene) takes the page and faint text apart by their color, and would
     * wash out an app icon high on the screen: the app selector is drawn over its picture, and an icon looks
     * as it does with no shader.
     */
    @Test
    fun theSelectorIsDrawnOverAShaderThatIsntForText() {
        needsAndroid13()
        config.set(Keys.SELECTOR_RAIL, "on")
        home(style = "gui")
        useFill(text = false)
        openSelector()
        // Its icon loads in the background: until then the shader's picture shows where it goes.
        var over: Pair<Rect, Color>? = null
        compose.waitFor("the icon is drawn through the shader") {
            topIcon()?.also { over = it }?.second?.let { !isFill(it) } == true
        }
        // The same icon with no shader. Without it, the selector is built again, closed.
        config.set(Keys.SCREEN_SHADER, "off")
        compose.waitFor("the shader is still drawn") { drawn().isEmpty() }
        if (compose.onAllNodesWithTag("app-selector-panel").fetchSemanticsNodes().isEmpty()) openSelector()
        val plain = checkNotNull(topIcon()) { "no icon shows whole" }
        assertEquals("another icon is highest", plain.first, over!!.first)
        val (a, b) = plain.second to over!!.second
        for ((x, y) in listOf(a.red to b.red, a.green to b.green, a.blue to b.blue)) {
            assertEquals("$b over the shader, $a without it", x, y, 0.02f)
        }
    }

    /** A shader for text works on all of home, the app selector too: a CRT's curve and lines cover the whole screen. */
    @Test
    fun aShaderForTextIsOverTheSelector() {
        needsAndroid13()
        config.set(Keys.SELECTOR_RAIL, "on")
        home(style = "gui")
        useFill(text = true)
        openSelector()
        assertTrue("the selector is drawn over a shader for text", isFill(checkNotNull(topIcon()) { "no icon shows whole" }.second))
    }

    /** On the grid's home, the GUI's selector (`tty.selector gui`) with its icons is drawn over a scene too. */
    @Test
    fun theGuisSelectorOnTheGridIsDrawnOverAShaderThatIsntForText() {
        needsAndroid13()
        config.set(Keys.SELECTOR_RAIL, "on")
        config.set(Keys.TTY_SELECTOR, "gui")
        ttyHome()
        useFill(text = false)
        openSelector()
        compose.waitFor("the icon is drawn through the shader") { topIcon()?.second?.let { !isFill(it) } == true }
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
