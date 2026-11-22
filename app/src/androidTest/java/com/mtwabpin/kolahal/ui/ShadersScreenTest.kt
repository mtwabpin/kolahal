package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.ListedShader
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.SharedShaderLists
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
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

/** Settings → Shaders: the list, turning one on with one tap, a shader's sheet, and removing one. */
@RunWith(AndroidJUnit4::class)
class ShadersScreenTest {
    @get:Rule
    val vms = TestViewModels()

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val configReset = ConfigReset()

    @get:Rule
    val lines = LogLinesSince()

    @get:Rule
    val lists = SharedShaderLists()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)

    // Not @animated: a moving preview never lets Compose's test clock come to rest.
    private val fine =
        "// @name Mine\n// @description As it was\nuniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"
    private val broken = "uniform shader content;\nhalf4 main(float2 p) { return nope; }"
    private val mine = listOf("zz-fine", "zz-broken")

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

    private fun needsAndroid13() = assumeTrue("shaders need Android 13", Shaders.supported)

    private fun show(): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent {
            ShadersScreen(vm.shaderActions, vm.notice, vm::openScreen, vm::closeScreen)
        }
        compose.waitForNode(hasTestTag("shader off"))
        return vm
    }

    private fun row(name: String) = compose.onNodeWithTag("shader $name")

    private fun scrollTo(name: String) =
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("shader $name"))

    /** Opens the sheet of the shader titled [title], whose row is [name]. */
    private fun openSheet(name: String, title: String) {
        scrollTo(name)
        compose.onNodeWithContentDescription("Details of $title").performClick()
        compose.waitForText("Code")
    }

    private fun tapOnSheet(label: String) =
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()

    /** None, the ones that come with Kolahal and the user's own, each with what it says of itself. None is selected at first. */
    @Test
    fun itListsThemAll() {
        Shaders.save(app, "zz-fine", fine)
        show()
        row(ShaderCatalog.OFF).assertIsSelected()
        for (name in ShaderCatalog.BUNDLED) row(name).assertIsNotSelected()
        compose.onNode(hasTestTag("shader rain") and hasText("Animated", substring = true)).assertExists()
        scrollTo("zz-fine")
        compose.onNode(
            hasTestTag("shader zz-fine") and hasText("Mine") and hasText("As it was") and hasText("Yours") and
                    hasText("zz-fine.agsl")
        ).assertExists()
    }

    /** One tap turns one on and selects it, and None turns it off. */
    @Test
    fun oneTapTurnsItOn() {
        needsAndroid13()
        show()
        row("bloom").performClick()
        compose.waitFor("bloom isn't on") { config[Keys.SCREEN_SHADER] == "bloom" }
        compose.waitForNode(hasTestTag("shader bloom") and hasText("In use"))
        row("bloom").assertIsSelected()
        row(ShaderCatalog.OFF).assertIsNotSelected().performClick()
        compose.waitFor("it is still on") { config[Keys.SCREEN_SHADER] == ShaderCatalog.OFF }
        compose.waitForIdle()
        row(ShaderCatalog.OFF).assertIsSelected()
    }

    /** One that doesn't compile isn't turned on: the one that is on stays, and its row says why. */
    @Test
    fun oneThatDoesntCompileSaysWhy() {
        needsAndroid13()
        Shaders.save(app, "zz-broken", broken)
        config.set(Keys.SCREEN_SHADER, "bloom")
        show()
        scrollTo("zz-broken")
        row("zz-broken").performClick()
        compose.waitForNode(
            hasTestTag("shader zz-broken") and hasText("Can't be used: line 2", substring = true)
        )
        assertEquals("bloom", config[Keys.SCREEN_SHADER])
    }

    /** One that was turned off by itself says why, and a tap turns it on again. */
    @Test
    fun oneThatWasTurnedOffSaysWhyAndTurnsOnAgain() {
        needsAndroid13()
        config.set(Keys.SCREEN_SHADER, "bloom")
        ShaderSafety.get(app)
            .trip(ShaderSafety.id("bloom", Shaders.bundled(app, "bloom")), "the GPU takes 60 ms")
        show()
        compose.waitForNode(
            hasTestTag("shader bloom") and hasText("Turned off: the GPU takes 60 ms", substring = true)
        )
        row("bloom").performClick()
        compose.waitForNode(hasTestTag("shader bloom") and hasText("In use"))
        assertNull(ShaderSafety.get(app).tripped())
    }

    /** The sheet shows the code and checks it; Use turns it on. */
    @Test
    fun theSheetShowsChecksAndUsesIt() {
        needsAndroid13()
        Shaders.save(app, "zz-fine", fine)
        show()
        openSheet("zz-fine", "Mine")
        compose.onNodeWithText("From: you").assertExists()
        compose.onNodeWithText("Moves: no").assertExists()
        compose.onNodeWithText(fine).performScrollTo().assertExists()
        compose.waitFor("no preview") {
            compose.onAllNodesWithTag("shader-preview").fetchSemanticsNodes().isNotEmpty()
        }
        tapOnSheet("Check")
        compose.waitForText("Compiles")
        tapOnSheet("Use")
        compose.waitFor("it isn't on") { config[Keys.SCREEN_SHADER] == "zz-fine" }
        compose.waitForNode(hasText("Turn off") and hasClickAction())
    }

    /** Looking at a preview marks nothing: the shader in use can't be blamed for it, and none is trusted for it. */
    @Test
    fun aPreviewLeavesTheWatchdogAlone() {
        needsAndroid13()
        Shaders.save(app, "zz-fine", fine)
        val prefs = app.getSharedPreferences("crash", android.content.Context.MODE_PRIVATE)
        fun marks() = listOf("shader.armed", "shader.tripped", "shader.proven").map { prefs.getString(it, null) }
        val before = marks()
        show()
        openSheet("zz-fine", "Mine")
        compose.waitFor("no preview") {
            compose.onAllNodesWithTag("shader-preview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        assertEquals(before, marks())
    }

    /** One that comes with Kolahal can't be removed; the user's own can, and Undo brings it back, on as it was. */
    @Test
    fun removeHasUndo() {
        needsAndroid13()
        Shaders.save(app, "zz-fine", fine)
        config.set(Keys.SCREEN_SHADER, "zz-fine")
        show()
        openSheet("zz-fine", "Mine")
        tapOnSheet("Remove")
        compose.waitForText("Removed zz-fine")
        assertFalse("zz-fine" in Shaders.userNames)
        assertEquals(ShaderCatalog.OFF, config[Keys.SCREEN_SHADER])
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("it isn't back") { "zz-fine" in Shaders.userNames && config[Keys.SCREEN_SHADER] == "zz-fine" }
        assertEquals(fine, Shaders.own(app, "zz-fine"))
    }

    @Test
    fun aBundledOneHasNoRemove() {
        show()
        // The title of bloom is its file's own.
        val title = com.mtwabpin.kolahal.script.ShaderMeta.parse(Shaders.bundled(app, "bloom")).title ?: "bloom"
        openSheet("bloom", title)
        compose.onNodeWithText("From: Kolahal").assertExists()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }

    /** One from a list says which, and a newer version there can be installed from its sheet. */
    @Test
    fun oneFromAListHasItsUpdate() {
        val glow = ListedShader("zz-glow", "Glow", "A glow", "ann", "1.0", fine)
        val next = glow.copy(version = "1.1", code = "$fine\n// next")
        lists.installed(glow)
        lists.listed(next)
        val vm = vms.terminal(app)
        compose.runOnUiThread { vm.scriptActions.sharedCode[next.sha] = next.code }
        compose.setContent {
            ShadersScreen(vm.shaderActions, vm.notice, vm::openScreen, vm::closeScreen)
        }
        compose.waitForNode(hasTestTag("shader off"))
        scrollTo("zz-glow")
        compose.waitForNode(
            hasTestTag("shader zz-glow") and hasText("From ${ScriptCatalog.SOURCE} · Update to 1.1")
        )
        openSheet("zz-glow", "Mine")
        tapOnSheet("Update to 1.1")
        compose.waitFor("it isn't updated") { Shaders.own(app, "zz-glow") == next.code }
    }

    /** Add shader offers the list of shared ones, a file and the folder; the first opens its screen. */
    @Test
    fun addShaderOpensGetMoreShaders() {
        val vm = show()
        compose.onNodeWithText("Add shader", useUnmergedTree = true).performClick()
        compose.waitForText("Read the script folder again")
        compose.onNodeWithText("Import a file").assertExists()
        // Its list is kept, so opening it reads nothing online.
        lists.listed()
        compose.onNode(hasText("Get more shaders") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { vm.screen.value == Screen.GET_SHADERS }
    }

    /** On Android 12 and older the shaders are listed, and the screen says why none can be used. */
    @Test
    fun onOldAndroidTheyAreListedOnly() {
        assumeTrue("this phone has shaders", !Shaders.supported)
        show()
        compose.onNodeWithText(Shaders.NEEDS, substring = true).assertExists()
        for (name in ShaderCatalog.BUNDLED) row(name).assertExists()
        row("crt").performClick()
        compose.waitForIdle()
        assertEquals(ShaderCatalog.OFF, config[Keys.SCREEN_SHADER])
        assertTrue(compose.onAllNodesWithTag("shader-preview").fetchSemanticsNodes().isEmpty())
    }
}
