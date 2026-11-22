package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
import com.mtwabpin.kolahal.script.ShaderParams
import com.mtwabpin.kolahal.script.ShaderPlay
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.ShaderWords
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
        // Not a tap: the row can be under the Add button.
        compose.onNodeWithContentDescription("Details of $title").performSemanticsAction(SemanticsActions.OnClick)
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
        // The user's own come first, so they are in sight at once.
        row("zz-fine").assertExists()
        for (name in ShaderCatalog.BUNDLED) {
            scrollTo(name)
            row(name).assertIsNotSelected()
        }
        scrollTo("rain")
        compose.onNode(hasTestTag("shader rain") and hasText("Animated", substring = true)).assertExists()
        scrollTo("foil")
        compose.onNode(hasTestTag("shader foil") and hasText("Uses tilt", substring = true)).assertExists()
        scrollTo("zz-fine")
        compose.onNode(
            hasTestTag("shader zz-fine") and hasText("Mine") and hasText("As it was") and hasText("Yours") and
                    hasText("zz-fine.agsl")
        ).assertExists()
    }

    /** The search and the chips keep what they say; None is away while something is looked for. */
    @Test
    fun theSearchAndTheChipsFindThem() {
        show()
        compose.onNodeWithTag("shader-search").performTextInput("pond")
        compose.waitFor("others still show") { compose.onAllNodesWithTag("shader crt").fetchSemanticsNodes().isEmpty() }
        row("pond").assertExists()
        compose.onNodeWithTag("shader off").assertDoesNotExist()
        // The pond hides a wallpaper.
        compose.onNodeWithTag("shader-filter WALLPAPER").performScrollTo().performClick()
        compose.waitForText("No shader matches")
        compose.onNodeWithText("Clear").performClick()
        compose.waitForNode(hasTestTag("shader off"))
    }

    /** A one-off shader is played from its row, on home; it isn't turned on. */
    @Test
    fun aOneOffShaderIsPlayed() {
        needsAndroid13()
        val vm = show()
        compose.onNodeWithTag("shader-search").performTextInput("hearts")
        compose.waitForNode(hasTestTag("shader hearts"))
        try {
            row("hearts").performClick()
            compose.waitFor("not played") { ShaderPlay.current.value?.name == "hearts" }
            assertEquals(Screen.TERMINAL, vm.screen.value)
            assertEquals(ShaderCatalog.OFF, config[Keys.SCREEN_SHADER])
        } finally {
            ShaderPlay.current.value?.let(ShaderPlay::done)
        }
    }

    /** A one-off shader's words are kept as they are typed, and its row says them. */
    @Test
    fun itsWordsAreKept() {
        needsAndroid13()
        try {
            show()
            compose.onNodeWithTag("shader-search").performTextInput("hearts")
            compose.waitForNode(hasTestTag("shader hearts"))
            openSheet("hearts", "Hearts")
            compose.onNodeWithTag("shader-words").performScrollTo().performTextInput("Alex, picnic")
            compose.waitFor("not kept") { ShaderWords.words.value["hearts"] == "Alex, picnic" }
            compose.runOnIdle { ShaderWords.load(app) }
            assertEquals("Alex, picnic", ShaderWords.words.value["hearts"])
            ShaderWords.onNotification("alex", "Running late")
            assertEquals("hearts", ShaderPlay.current.value?.name)
        } finally {
            ShaderPlay.current.value?.let(ShaderPlay::done)
            ShaderWords.set(app, "hearts", "")
        }
    }

    /** A formula for what a parameter follows counts once it can be worked out. */
    @Test
    fun aFormulaForAParameter() {
        needsAndroid13()
        val camels = Shaders.params(app, "dunes").first { it.name == "camels" }
        ShaderParams.setFollow(app, "dunes", camels, "battery")
        try {
            show()
            compose.onNodeWithTag("shader-search").performTextInput("dunes")
            compose.waitForNode(hasTestTag("shader dunes"))
            openSheet("dunes", "Dunes")
            val field = compose.onNodeWithTag("formula camels", useUnmergedTree = true)
            field.performScrollTo().performTextInput("battery /")
            compose.waitForText("Can't be worked out", substring = true)
            assertNull(ShaderParams.formula("dunes", camels))
            field.performTextInput(" 20")
            compose.waitFor("not kept") { ShaderParams.formula("dunes", camels) == "battery / 20" }
            compose.runOnIdle { ShaderParams.load(app) }
            assertEquals("battery / 20", ShaderParams.formula("dunes", camels))
        } finally {
            ShaderParams.reset(app, "dunes")
        }
        assertNull(ShaderParams.formula("dunes", camels))
    }

    /** A scene can be the wallpaper; one that works on text, or plays once, can't. */
    @Test
    fun aSceneCanBeTheWallpaper() {
        needsAndroid13()
        show()
        for ((name, title, can) in listOf(Triple("dunes", "Dunes", true), Triple("crt", "CRT", false), Triple("hearts", "Hearts", false))) {
            compose.onNodeWithTag("shader-search").performTextClearance()
            compose.onNodeWithTag("shader-search").performTextInput(name)
            compose.waitForNode(hasTestTag("shader $name"))
            openSheet(name, title)
            val buttons = compose.onAllNodesWithTag("set-wallpaper").fetchSemanticsNodes().size
            assertEquals(name, if (can) 1 else 0, buttons)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitFor("the sheet stays") { compose.onAllNodesWithTag("shader-pager").fetchSemanticsNodes().isEmpty() }
        }
    }

    /** Its details say what it works well over, and warn while too much of a wallpaper shows. */
    @Test
    fun theDetailsRateItAndWarn() {
        needsAndroid13()
        config.set(Keys.LOG_WALLPAPER, "40")
        show()
        compose.onNodeWithTag("shader-search").performTextInput("pond")
        compose.waitForNode(hasTestTag("shader pond"))
        openSheet("pond", "Pond")
        for (surface in listOf("dark", "light", "wallpaper")) compose.onNodeWithTag("suits $surface").assertExists()
        compose.waitForText("yours shows at 40%", substring = true)
    }

    /** One tap turns one on and selects it, and None turns it off. */
    @Test
    fun oneTapTurnsItOn() {
        needsAndroid13()
        show()
        row("lcd").performClick()
        compose.waitFor("lcd isn't on") { config[Keys.SCREEN_SHADER] == "lcd" }
        compose.waitForNode(hasTestTag("shader lcd") and hasText("In use"))
        row("lcd").assertIsSelected()
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
        config.set(Keys.SCREEN_SHADER, "lcd")
        show()
        scrollTo("zz-broken")
        row("zz-broken").performClick()
        compose.waitForNode(
            hasTestTag("shader zz-broken") and hasText("Can't be used: line 2", substring = true)
        )
        assertEquals("lcd", config[Keys.SCREEN_SHADER])
    }

    /** One that was turned off by itself says why, and a tap turns it on again. */
    @Test
    fun oneThatWasTurnedOffSaysWhyAndTurnsOnAgain() {
        needsAndroid13()
        config.set(Keys.SCREEN_SHADER, "lcd")
        ShaderSafety.get(app)
            .trip(ShaderSafety.id("lcd", Shaders.bundled(app, "lcd")), "the GPU takes 60 ms")
        show()
        compose.waitForNode(
            hasTestTag("shader lcd") and hasText("Turned off: the GPU takes 60 ms", substring = true)
        )
        row("lcd").performClick()
        compose.waitForNode(hasTestTag("shader lcd") and hasText("In use"))
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

    /** A swipe on the sheet goes to the next shader, and back, in the list's order. */
    @Test
    fun aSwipeShowsTheNextOne() {
        show()
        openSheet("crt", "CRT")
        compose.onNodeWithText("1 of ${ShaderCatalog.BUNDLED.size}").assertExists()
        compose.onNodeWithTag("shader-pager").performTouchInput { swipeLeft() }
        compose.waitForText("2 of ${ShaderCatalog.BUNDLED.size}")
        compose.onNodeWithTag("shader-pager").performTouchInput { swipeRight() }
        compose.waitForText("1 of ${ShaderCatalog.BUNDLED.size}")
    }

    /** A slider changes what is set at once and keeps it, also through a new read; Reset puts it back. */
    @Test
    fun aParameterIsKeptAndReset() {
        needsAndroid13()
        val param = Shaders.params(app, "snowfall").first { it.name == "amount" }
        try {
            show()
            openSheet("snowfall", "Snowfall")
            compose.onNodeWithTag("param amount", useUnmergedTree = true).performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.waitFor("not set") { ShaderParams.of("snowfall", param) == 2f }
            compose.runOnIdle { ShaderParams.load(app) }
            assertEquals(2f, ShaderParams.of("snowfall", param))
            tapOnSheet("Reset")
            compose.waitFor("not reset") { ShaderParams.of("snowfall", param) == param.default }
            compose.onNodeWithText("Reset").assertDoesNotExist()
            compose.runOnIdle { ShaderParams.load(app) }
            assertEquals(param.default, ShaderParams.of("snowfall", param))
        } finally {
            ShaderParams.reset(app, "snowfall")
        }
    }

    /** Holding the sheet's eye shows home with that shader, which isn't turned on for it; letting go ends it. */
    @Test
    fun theEyeShowsHomeWithTheShader() {
        needsAndroid13()
        val vm = vms.terminal(app)
        val peek = kotlinx.coroutines.flow.MutableStateFlow<TerminalViewModel.Peek?>(null)
        compose.setContent {
            ShadersScreen(vm.shaderActions, vm.notice, vm::openScreen, vm::closeScreen, peek, { peek.value = it })
        }
        compose.waitForNode(hasTestTag("shader off"))
        openSheet("crt", "CRT")
        val eye = compose.onNode(
            androidx.compose.ui.test.hasContentDescription("Hold to see the home screen") and
                    androidx.compose.ui.test.hasAnyAncestor(hasTestTag("shader-pager"))
        )
        eye.performTouchInput { down(center); advanceEventTime(1_000) }
        compose.waitFor("home isn't shown") { peek.value?.shader == "crt" }
        eye.performTouchInput { up() }
        compose.waitFor("home is still shown") { peek.value == null }
        assertEquals(ShaderCatalog.OFF, config[Keys.SCREEN_SHADER])
    }

    @Test
    fun aBundledOneHasNoRemove() {
        show()
        // The title of lcd is its file's own.
        val title = com.mtwabpin.kolahal.script.ShaderMeta.parse(Shaders.bundled(app, "lcd")).title ?: "lcd"
        openSheet("lcd", title)
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
