package com.mtwabpin.kolahal.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.ScreenSaver
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.screens.DecodeScreen
import com.mtwabpin.kolahal.terminal.tty.screens.ScreenEnv
import com.mtwabpin.kolahal.terminal.tty.screens.ShownLog
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.tty.GridMetrics
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * `screen decode` and `screen.saver decode` over home: the lines home shows, in both home styles,
 * until a touch. Device tests run with animations off, so the screen shows the plain text; what
 * it shows is read from the same place it reads ([TerminalViewModel.shownLog]).
 */
@RunWith(AndroidJUnit4::class)
class HomeDecodeTest {
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

    private val fast = ScreenSaverTiming(
        minuteMs = 300L,
        phone = { ScreenSaver.Phone(screenOn = true, batterySaver = false, animationsOff = false) },
    )

    private val decoding = hasContentDescription("Decoding log")

    /** The view home is drawn in, for putting the keyboard away ([keyboardDown]), and the grid's cells. */
    private var view: android.view.View? = null
    private var metrics: GridMetrics? = null

    /** Home as MainActivity shows it: the terminal, with the screens over it, the keyboard down. */
    private fun show(style: String): TerminalViewModel {
        config.set(Keys.HOME_STYLE, style)
        val vm = vms.terminal(app)
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            KolahalTheme {
                metrics = rememberTtyGrid().metrics
                CompositionLocalProvider(LocalScreenSaverTiming provides fast) {
                    Box(Modifier.fillMaxSize()) {
                        TerminalScreen(vm)
                        HomeScreenOverlay(vm)
                    }
                }
            }
        }
        keyboardDown()
        return vm
    }

    /** Puts the keyboard away: the test's home focuses the prompt. */
    private fun keyboardDown() {
        compose.waitForIdle()
        val v = view ?: return
        val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
        compose.runOnUiThread {
            val window = (v.context as? android.app.Activity)?.window ?: return@runOnUiThread
            androidx.core.view.WindowCompat.getInsetsController(window, v).hide(ime)
        }
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(v)?.isVisible(ime) != true }
    }

    private fun waitForLine(id: Long) = compose.waitUntil(5_000) {
        compose.onAllNodesWithTag("line-$id").fetchSemanticsNodes().isNotEmpty()
    }

    /** The lines home shows now, as the screen gets them. */
    private fun TerminalViewModel.shown(): ShownLog? {
        val m = metrics ?: return null
        return compose.runOnUiThread { shownLog.read(m.cellW, m.cellH) }
    }

    private fun ShownLog.text() = rows.map { it.block.grid.rowText(it.row) }

    private fun waitForText(vm: TerminalViewModel, text: String, there: Boolean = true) =
        compose.waitUntil(5_000) { vm.shown()?.text()?.any { text in it } == there }

    private fun decodeShowsHome(style: String) {
        val mark = System.nanoTime().toString()
        logs.add(LogKind.COMMAND, "echo $mark")
        val last = logs.add(LogKind.OUTPUT, "out $mark")
        val vm = show(style)
        waitForLine(last)
        waitForText(vm, "out $mark")
        compose.runOnIdle { vm.showTtyScreen("decode") }
        compose.waitUntil(5_000) { compose.onAllNodes(decoding).fetchSemanticsNodes().isNotEmpty() }
        val shown = vm.shown()!!
        // The newest line is the lowest one, as on home.
        val text = shown.text().filter { mark in it }
        assertEquals(2, text.size)
        assertTrue(text[0], "$ echo $mark" in text[0])
        assertTrue(text[1], "out $mark" in text[1])
        assertEquals(shown.rows.map { it.top }.sorted(), shown.rows.map { it.top })
        // The screen's own still frame is those rows.
        val screen = DecodeScreen()
        val grid = CellBuffer(200, 200)
        screen.start(ScreenEnv(200, 200, Random(1), { 0L }, true, log = { shown }))
        compose.runOnIdle { screen.still(grid) }
        assertEquals(shown.text().map { it.trimEnd() }, grid.dump().lines().map { it.trimEnd() }.take(shown.rows.size))
        compose.onNode(decoding).performTouchInput { click(center) }
        compose.waitUntil(5_000) { vm.ttyScreen.value == null }
        assertEquals("", vm.input.text)
    }

    @Test
    fun itShowsHomesLinesInTheTtyStyle() = decodeShowsHome("tty")

    /** In the gui style it is home's own log, where it is, in its own look: only the rest of home goes. */
    @Test
    fun itScramblesTheGuiLogInPlace() {
        val mark = System.nanoTime().toString()
        logs.add(LogKind.COMMAND, "echo $mark")
        val last = logs.add(LogKind.OUTPUT, "out $mark")
        val vm = show("gui")
        waitForLine(last)
        val line = compose.onNodeWithTag("line-$last")
        val place = line.fetchSemanticsNode().boundsInWindow
        fun picture() = compose.onNodeWithTag("line-$last").captureToImage().asAndroidBitmap()
        val before = picture()
        compose.runOnIdle { vm.showTtyScreen("decode") }
        compose.waitUntil(5_000) { vm.logDecode.active && compose.onAllNodes(decoding).fetchSemanticsNodes().isNotEmpty() }
        // The grid of the tty style has no part in it.
        assertEquals(null, vm.shown())
        // Animations are off on the test's phone, so the waves get their time from here.
        var scrambled = false
        for (t in 1_000L..4_000L step 100) {
            compose.runOnIdle { vm.logDecode.frame(t) }
            if (!picture().sameAs(before)) scrambled = true
        }
        assertTrue(scrambled)
        assertEquals(place, line.fetchSemanticsNode().boundsInWindow)
        // A screen reader still has the real text.
        compose.onAllNodes(hasText("out $mark", substring = true)).onFirst().assertExists()
        compose.onNode(decoding).performTouchInput { click(center) }
        compose.waitUntil(5_000) { vm.ttyScreen.value == null && !vm.logDecode.active }
        compose.waitUntil(5_000) { picture().sameAs(before) }
        assertEquals("", vm.input.text)
    }

    /** In the tty style each row is just where the log draws it. */
    @Test
    fun theRowsAreWhereTheTtyLogHasThem() {
        val mark = System.nanoTime().toString()
        val ids = (1..4).map { logs.add(if (it % 2 == 1) LogKind.COMMAND else LogKind.OUTPUT, "place $it $mark") }
        val vm = show("tty")
        ids.forEach(::waitForLine)
        waitForText(vm, "place 4 $mark")
        val shown = vm.shown()!!
        for ((i, id) in ids.withIndex()) {
            val row = shown.rows.first { "place ${i + 1} $mark" in it.block.grid.rowText(it.row) }
            val node = compose.onNodeWithTag("line-$id").fetchSemanticsNode().boundsInWindow
            assertEquals(node.top, (shown.top + row.top).toFloat(), 1f)
        }
    }

    /** While a search's results are in the log's place, they are what shows; and a deleted line goes. */
    @Test
    fun itFollowsWhatHomeShows() {
        val mark = System.nanoTime().toString()
        val found = logs.add(LogKind.OUTPUT, "found $mark")
        val away = "away ${System.nanoTime()}"
        val other = logs.add(LogKind.OUTPUT, away)
        val vm = show("tty")
        waitForLine(found)
        waitForText(vm, "found $mark")
        waitForText(vm, away)
        compose.runOnIdle { vm.onInputChange(TextFieldValue("/$mark", TextRange(1 + mark.length))) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("log-search").fetchSemanticsNodes().isNotEmpty() }
        waitForText(vm, away, there = false)
        waitForText(vm, "found $mark")
        compose.runOnIdle { vm.onInputChange(TextFieldValue("")) }
        waitForText(vm, away)
        logs.delete(other)
        waitForText(vm, away, there = false)
    }

    /** As the screensaver it says so, and the first touch only closes it. */
    @Test
    fun itIsAScreensaver() = screensaver("tty")

    @Test
    fun itIsAScreensaverInTheGuiStyle() = screensaver("gui")

    private fun screensaver(style: String) {
        config.set(Keys.SCREEN_SAVER, "decode")
        config.set(Keys.SCREEN_SAVER_AFTER, "1")
        val vm = show(style)
        compose.waitUntil(10_000) { vm.ttyScreen.value == "decode" }
        assertTrue(vm.screenSaverOn.value)
        val saver = hasContentDescription("Screensaver: Decoding log")
        compose.waitUntil(5_000) { compose.onAllNodes(saver).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(saver).performTouchInput { click(center) }
        compose.waitUntil(5_000) { vm.ttyScreen.value == null }
        assertFalse(vm.screenSaverOn.value)
        assertEquals("", vm.input.text)
    }
}
