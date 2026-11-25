package com.mtwabpin.kolahal.ui.shaders

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.ListedShader
import com.mtwabpin.kolahal.NotificationAccess
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.SharedShaderLists
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderPlay
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.ShaderWords
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.editor.BUNDLED_NOTE
import com.mtwabpin.kolahal.ui.editor.ShaderEditor
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.shell
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

/** The Shaders tab: the list, turning one on with one tap, a shader's sheet, and removing one. */
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

    @get:Rule
    val access = NotificationAccess()

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

    /** A button of the sheet, by its word: where four don't fit with their words, each is an icon that says it. */
    private fun tapOnSheet(label: String) =
        compose.onNode((hasText(label) or hasContentDescription(label)) and hasClickAction()).performScrollTo().performClick()

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
        access.set(true)
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

    /** Without notification access, the words' field is a line and a button that asks for it. The words are kept. */
    @Test
    fun itsWordsNeedNotificationAccess() {
        needsAndroid13()
        ShaderWords.set(app, "hearts", "Alex")
        access.set(false)
        try {
            show()
            compose.onNodeWithTag("shader-search").performTextInput("hearts")
            compose.waitForNode(hasTestTag("shader hearts"))
            openSheet("hearts", "Hearts")
            // On the sheet: the page under it asks for the same access, where Play over other apps needs it.
            val onSheet = androidx.compose.ui.test.hasAnyAncestor(hasTestTag("shader-pager"))
            compose.onNode(hasTestTag("needs-access ${Accesses.READ_NOTIFICATIONS}") and onSheet).performScrollTo()
            compose.onNode(hasText("Needs notification access") and onSheet).assertExists()
            compose.onNode(hasText("Allow notification access") and onSheet).assertExists()
            compose.onNodeWithTag("shader-words").assertDoesNotExist()
            assertEquals("Alex", ShaderWords.words.value["hearts"])
        } finally {
            ShaderWords.set(app, "hearts", "")
        }
    }

    /** Runs [block] with usage access [mode] (allow, ignore), and puts it back as it was after. */
    private fun withUsageAccess(mode: String, block: () -> Unit) {
        val before = Regex("GET_USAGE_STATS: (\\w+)")
            .find(shell("appops get ${app.packageName} GET_USAGE_STATS"))?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set ${app.packageName} GET_USAGE_STATS $mode")
            block()
        } finally {
            shell("appops set ${app.packageName} GET_USAGE_STATS $before")
        }
    }

    /** Runs [block] with Android's Display over other apps access [mode] (allow, ignore), and puts it back as it was after. */
    private fun withOverlayAccess(mode: String, block: () -> Unit) {
        val before = Regex("SYSTEM_ALERT_WINDOW: (\\w+)")
            .find(shell("appops get ${app.packageName} SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW $mode")
            block()
        } finally {
            shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW $before")
        }
    }

    private fun overApps() = hasTestTag("shader-over-apps")

    /** Scrolls the list to Play over other apps, under the one-off shaders. */
    private fun showOverApps() {
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(overApps())
    }

    /**
     * Play over other apps needs Display over other apps. Without it there is no switch: its name stands over one
     * line and one button that asks for the access, as an option that needs an access has them everywhere.
     */
    @Test
    fun playOverOtherAppsNeedsDisplayOverOtherApps() {
        needsAndroid13()
        assumeTrue("this build plays nothing over other apps", BuildConfig.SHADER_OVER_APPS)
        access.set(true)
        withOverlayAccess("ignore") {
            showOverApps()
            compose.onNode(overApps() and isToggleable()).assertDoesNotExist()
            compose.onNodeWithText("Play over other apps").assertExists()
            compose.onNodeWithTag("needs-access ${Accesses.OVERLAY}").assertExists()
            compose.onNodeWithText("Needs display over other apps").assertExists()
            compose.onNode(hasText("Allow display over other apps") and hasClickAction()).assertExists()
            // Notification access is there: nothing asks for it.
            compose.onNodeWithTag("needs-access ${Accesses.READ_NOTIFICATIONS}").assertDoesNotExist()
        }
    }

    /** Without notification access too, no word plays a shader at all: a line and a button for each, that one first. */
    @Test
    fun playOverOtherAppsNeedsNotificationAccessToo() {
        needsAndroid13()
        assumeTrue("this build plays nothing over other apps", BuildConfig.SHADER_OVER_APPS)
        access.set(false)
        withOverlayAccess("ignore") {
            showOverApps()
            compose.onNode(overApps() and isToggleable()).assertDoesNotExist()
            val asked = compose.onAllNodes(SemanticsMatcher("needs an access") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("needs-access ") == true })
                .fetchSemanticsNodes().sortedBy { it.positionInRoot.y }.map { it.config[SemanticsProperties.TestTag] }
            assertEquals(listOf("needs-access ${Accesses.READ_NOTIFICATIONS}", "needs-access ${Accesses.OVERLAY}"), asked)
            compose.onNode(hasText("Allow notification access") and hasClickAction()).assertExists()
            compose.onNode(hasText("Allow display over other apps") and hasClickAction()).assertExists()
        }
    }

    /** With both accesses the switch is there, off at first, and a tap keeps the setting; nothing asks for an access. */
    @Test
    fun playOverOtherAppsIsASwitchWithItsAccesses() {
        needsAndroid13()
        assumeTrue("this build plays nothing over other apps", BuildConfig.SHADER_OVER_APPS)
        access.set(true)
        withOverlayAccess("allow") {
            showOverApps()
            compose.onNodeWithTag("needs-access ${Accesses.OVERLAY}").assertDoesNotExist()
            compose.onNodeWithTag("needs-access ${Accesses.READ_NOTIFICATIONS}").assertDoesNotExist()
            compose.onNode(overApps() and isToggleable()).assertIsOff().performClick()
            compose.waitFor("the setting isn't on") { config[Keys.SHADER_OVER_APPS] }
            compose.onNode(overApps() and isToggleable()).assertIsOn().performClick()
            compose.waitFor("the setting isn't off") { !config[Keys.SHADER_OVER_APPS] }
        }
    }

    /**
     * Without usage access, a scene of the phone's use stays in the list and says that it needs it, in its row and
     * on its sheet, with the button that asks for it. With it, neither says so.
     */
    @Test
    fun aSceneOfThePhonesUseNeedsUsageAccess() {
        needsAndroid13()
        withUsageAccess("ignore") {
            show()
            compose.onNodeWithTag("shader-search").performTextInput("candle")
            compose.waitForNode(hasTestTag("shader candle") and hasText("Needs usage access", substring = true))
            openSheet("candle", "Candle")
            compose.onNodeWithTag("needs-access ${Accesses.USAGE}").performScrollTo()
            compose.onNodeWithText("Allow usage access").assertExists()
        }
    }

    /** With usage access, the scene's row and sheet are as they were. */
    @Test
    fun aSceneOfThePhonesUseWithUsageAccess() {
        needsAndroid13()
        withUsageAccess("allow") {
            show()
            compose.onNodeWithTag("shader-search").performTextInput("candle")
            compose.waitForNode(hasTestTag("shader candle"))
            compose.onNode(hasTestTag("shader candle") and hasText("Needs usage access", substring = true)).assertDoesNotExist()
            openSheet("candle", "Candle")
            compose.onNodeWithTag("needs-access ${Accesses.USAGE}").assertDoesNotExist()
        }
    }

    /**
     * A formula for what a parameter follows counts once it can be worked out. Without one, its field is in the
     * Follow menu, and the row is one line.
     */
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
            assertTrue(compose.onAllNodesWithTag("formula camels", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("follow camels", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitForNode(hasTestTag("add-formula camels"))
            compose.onNodeWithTag("add-formula camels").performClick()
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

    /**
     * Its details say what it works well over, and warn while too much of a wallpaper shows. CRT works on
     * the text and is poor over a wallpaper. A scene such as Pond hides the wallpaper while it is on, so it
     * gets no warning (ShaderPageTest.theWallpaperWarning).
     */
    @Test
    fun theDetailsRateItAndWarn() {
        needsAndroid13()
        config.set(Keys.LOG_WALLPAPER, "40")
        show()
        compose.onNodeWithTag("shader-search").performTextInput("crt")
        compose.waitForNode(hasTestTag("shader crt"))
        openSheet("crt", "CRT")
        for (surface in listOf("dark", "light", "wallpaper")) compose.onNodeWithTag("suits $surface").assertExists()
        compose.waitForText("Yours shows at 40%", substring = true)
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

    /** A one-off that was turned off says why, and the tap that plays it turns it on again. */
    @Test
    fun aOneOffThatWasTurnedOffTurnsOnAgain() {
        needsAndroid13()
        val id = ShaderSafety.id("hearts", Shaders.bundled(app, "hearts"))
        ShaderSafety.get(app).trip(id, "Kolahal stopped while it played over an app")
        show()
        compose.onNodeWithTag("shader-search").performTextInput("hearts")
        compose.waitForNode(
            hasTestTag("shader hearts") and hasText("Turned off: Kolahal stopped while it played over an app", substring = true)
        )
        row("hearts").performClick()
        compose.waitFor("hearts is still turned off") { ShaderSafety.get(app).tripped() == null }
        compose.waitFor("its row still says so") {
            compose.onAllNodes(hasTestTag("shader hearts") and hasText("Turned off", substring = true)).fetchSemanticsNodes().isEmpty()
        }
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

    /**
     * The hearts' eight styles are two rows of four buttons, each row as wide as the other; a tap on one picks it, and
     * Reset puts it back.
     */
    @Test
    fun aChoiceOfEightWordsIsTwoRows() {
        needsAndroid13()
        val param = Shaders.params(app, "hearts").first { it.name == "style" }
        fun bounds(word: String) = compose.onNodeWithTag("param style $word", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        try {
            show()
            compose.onNodeWithTag("shader-search").performTextInput("hearts")
            compose.waitForNode(hasTestTag("shader hearts"))
            openSheet("hearts", "Hearts")
            compose.onNodeWithTag("param style halftone", useUnmergedTree = true).performScrollTo()
            val (upper, lower) = param.choices.map(::bounds).chunked(4)
            assertEquals(listOf(4, 4), listOf(upper.size, lower.size))
            for (row in listOf(upper, lower)) assertEquals(1, row.map { it.top }.distinct().size)
            assertTrue(lower.first().top >= upper.first().bottom)
            assertEquals(upper.first().left, lower.first().left, 1f)
            assertEquals(upper.last().right, lower.last().right, 1f)
            compose.onNodeWithTag("param style neon", useUnmergedTree = true).performClick()
            compose.waitFor("not set") { ShaderParams.of("hearts", param) == 5f }
            tapOnSheet("Reset")
            compose.waitFor("not reset") { ShaderParams.of("hearts", param) == param.default }
        } finally {
            ShaderParams.reset(app, "hearts")
        }
    }

    /** While a parameter follows a value it has no slider; picking Nothing in its Follow menu brings the slider back. */
    @Test
    fun aFollowingParameterHasNoSlider() {
        needsAndroid13()
        val param = Shaders.params(app, "snowfall").first { it.name == "amount" }
        ShaderParams.setFollow(app, "snowfall", param, "battery")
        fun sliders() = compose.onAllNodesWithTag("param amount", useUnmergedTree = true).fetchSemanticsNodes().size
        try {
            show()
            openSheet("snowfall", "Snowfall")
            compose.onNodeWithTag("follow amount", useUnmergedTree = true).performScrollTo()
            assertEquals(0, sliders())
            compose.onNodeWithTag("follow amount", useUnmergedTree = true).performClick()
            compose.waitForText("Nothing")
            compose.onNodeWithText("Nothing").performClick()
            compose.waitFor("no slider") { sliders() == 1 }
            assertNull(ShaderParams.follow("snowfall", param))
        } finally {
            ShaderParams.reset(app, "snowfall")
        }
    }

    /**
     * Seismograph's eight blocks of today are one row, closed at first; a tap shows their own rows and a second one
     * hides them. A screen reader hears whether it is open.
     */
    @Test
    fun aSeriesIsOneRowThatOpens() {
        needsAndroid13()
        show()
        compose.onNodeWithTag("shader-search").performTextInput("seismograph")
        compose.waitForNode(hasTestTag("shader seismograph"))
        openSheet("seismograph", "Seismograph")
        val series = compose.onNodeWithTag("series h1")
        fun rows() = (1..8).count { compose.onAllNodesWithTag("follow h$it", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fun state() = series.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        series.performScrollTo()
        assertEquals(0, rows())
        assertEquals("Collapsed", state())
        series.performClick()
        compose.waitFor("the series stays closed") { rows() == 8 }
        assertEquals("Expanded", state())
        series.performScrollTo().performClick()
        compose.waitFor("the series stays open") { rows() == 0 }
    }

    /**
     * In Seismograph's opened series each parameter is one line inside its row: its name, its Follow button and its
     * value side by side. The button is the link icon, which says what it follows to a screen reader.
     */
    @Test
    fun aSeriesParameterIsOneLine() {
        needsAndroid13()
        show()
        compose.onNodeWithTag("shader-search").performTextInput("seismograph")
        compose.waitForNode(hasTestTag("shader seismograph"))
        openSheet("seismograph", "Seismograph")
        compose.onNodeWithTag("series h1").performScrollTo().performClick()
        compose.waitFor("the series stays closed") {
            compose.onAllNodesWithTag("follow h8", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("follow h3") and hasContentDescription("Follows Screen time from 06:00 to 09:00", substring = true))
            .assertExists()
        for (param in Shaders.params(app, "seismograph")) {
            // The eight rows are longer than the sheet: each is brought into view first. A row under the sheet's
            // edge has no bounds on the screen, and one cut by the edge has only a part of them.
            compose.onNodeWithTag("follow ${param.name}").performScrollTo()
            compose.waitForIdle()
            val button = compose.onNodeWithTag("follow ${param.name}", useUnmergedTree = true).fetchSemanticsNode()
            val merged = compose.onNodeWithTag("follow ${param.name}").fetchSemanticsNode()
            assertNull("${param.name}: its button has words", merged.config.getOrNull(SemanticsProperties.Text))
            val name = compose.onNode(hasText(param.title), useUnmergedTree = true).fetchSemanticsNode()
            // The row holds the name and the value, its only other text.
            val row = name.parent!!
            val value = row.children.single { it.id != name.id && it.config.getOrNull(SemanticsProperties.Text) != null }
            val line = row.boundsInRoot
            val parts = listOf(name.boundsInRoot, button.boundsInRoot, value.boundsInRoot)
            for (part in parts) {
                assertTrue("${param.name}: $part isn't on the screen", part.width > 0f && part.height > 0f)
                assertEquals("${param.name}: not on one line", parts[0].center.y, part.center.y, 2f)
                assertTrue("${param.name}: $part is outside its row $line", part.left >= line.left - 1f && part.right <= line.right + 1f)
            }
            assertTrue("${param.name}: its name runs into its button", parts[0].right <= parts[1].left + 1f)
            assertTrue("${param.name}: its button runs into its value", parts[1].right <= parts[2].left + 1f)
        }
    }

    /** The preview stays where it is while the details scroll to their end. */
    @Test
    fun thePreviewStaysInView() {
        needsAndroid13()
        show()
        compose.onNodeWithTag("shader-search").performTextInput("seismograph")
        compose.waitForNode(hasTestTag("shader seismograph"))
        openSheet("seismograph", "Seismograph")
        fun top() = compose.onAllNodesWithTag("shader-preview").fetchSemanticsNodes().first { it.layoutInfo.isPlaced }.boundsInRoot.top
        val before = top()
        compose.onNodeWithText("Code").performScrollTo()
        compose.waitForIdle()
        assertEquals(before, top())
    }

    /**
     * A parameter's value can be typed, with a comma for the decimal point: the row's screen reader action opens
     * the same box as a hold on its title. A value outside its range is not kept.
     */
    @Test
    fun aParameterCanBeTyped() {
        needsAndroid13()
        val param = Shaders.params(app, "snowfall").first { it.name == "amount" }
        try {
            show()
            openSheet("snowfall", "Snowfall")
            compose.onNodeWithTag("param amount", useUnmergedTree = true).performScrollTo()
            val row = compose.onNode(hasText(param.title) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            val type = row.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Type a value" }
            compose.runOnUiThread { type.action() }
            val box = compose.onNodeWithTag("type-value")
            compose.onNodeWithText("0.0 to 2.0").assertExists()

            box.performTextReplacement("2,5")
            box.performImeAction()
            box.assertExists()
            assertEquals(param.default, ShaderParams.of("snowfall", param))

            box.performTextReplacement("1,5")
            box.performImeAction()
            compose.waitFor("not set") { ShaderParams.of("snowfall", param) == 1.5f }
            box.assertDoesNotExist()
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
        val title = com.mtwabpin.kolahal.shader.ShaderMeta.parse(Shaders.bundled(app, "lcd")).title ?: "lcd"
        openSheet("lcd", title)
        compose.onNodeWithText("From: Kolahal").assertExists()
        compose.onNodeWithText("Remove").assertDoesNotExist()
        compose.onNode(hasContentDescription("Remove") and hasClickAction()).assertDoesNotExist()
        // Its four buttons: Edit, Check, Share and Save to folder, as words or as icons that say them.
        for (word in listOf("Edit", "Check", "Share", "Save to folder")) {
            compose.onNode((hasText(word) or hasContentDescription(word)) and hasClickAction()).performScrollTo().assertExists()
        }
    }

    /**
     * The words of five buttons don't fit on a phone's line: each is then its icon, which says its word to
     * a screen reader and on a long-press.
     */
    @Test
    fun fiveButtonsAreIconsWhereTheirWordsDontFit() {
        Shaders.save(app, "zz-fine", fine)
        show()
        openSheet("zz-fine", "Mine")
        for (word in listOf("Edit", "Check", "Share", "Save to folder", "Remove")) {
            compose.onNode(hasContentDescription(word) and hasClickAction()).performScrollTo().assertExists()
        }
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Check").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
        // The five share the line's width, as the buttons with words do: the first starts where the last ends, mirrored.
        fun button(word: String) = compose.onNode(hasContentDescription(word) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val first = button("Edit")
        val last = button("Remove")
        val screen = app.resources.displayMetrics.widthPixels.toFloat()
        assertEquals(first.left, screen - last.right, 2f)
        assertTrue("an icon button is ${first.width} px of $screen", first.width > screen * 0.12f)
        assertEquals(first.width, last.width, 2f)
    }

    /** Shows the Shaders screen, or the shader editor while one is open, as home's window does. */
    private fun showWithEditor(): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent {
            val editing by vm.shaderActions.editing.collectAsState()
            val open = editing
            if (open != null) KolahalTheme {
                ShaderEditor(
                    name = open.name,
                    code = open.code,
                    bundled = open.bundled,
                    onSave = { code -> vm.shaderActions.save(open.name, code) },
                    onClose = vm.shaderActions::closeEditor,
                )
            } else ShadersScreen(vm.shaderActions, vm.notice, vm::openScreen, vm::closeScreen, onEdit = vm::openShaderEditor)
        }
        compose.waitForNode(hasTestTag("shader off"))
        return vm
    }

    /** The message line of the editor. */
    private fun editorMessage() = runCatching {
        compose.onNodeWithTag("message").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
    }.getOrDefault("")

    /**
     * Edit in a shader's sheet opens its code in the editor. Save writes the user's file, which the list reads again,
     * and the message says what the check found; Close comes back to the list.
     */
    @Test
    fun editSavesYourShader() {
        Shaders.save(app, "zz-fine", fine)
        val vm = showWithEditor()
        openSheet("zz-fine", "Mine")
        tapOnSheet("Edit")
        compose.waitForNode(hasTestTag("editor"))
        compose.onNodeWithText("zz-fine.agsl", substring = true).assertExists()
        val changed = fine.replace("As it was", "Changed in the editor")
        compose.onNodeWithTag("editor").performTextReplacement(changed)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitFor("it isn't saved") { Shaders.own(app, "zz-fine") == changed }
        compose.waitFor("no check") { editorMessage().startsWith("Saved · ") }
        if (Shaders.supported) assertEquals("Saved · compiles", editorMessage())
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitFor("the editor stays") { vm.shaderActions.editing.value == null }
        compose.waitForNode(hasTestTag("shader zz-fine") and hasText("Changed in the editor"))
    }

    /** A shader that doesn't compile is saved all the same, and the editor says why, at its line. */
    @Test
    fun aBrokenEditIsSavedAndSaysWhy() {
        needsAndroid13()
        Shaders.save(app, "zz-fine", fine)
        showWithEditor()
        openSheet("zz-fine", "Mine")
        tapOnSheet("Edit")
        compose.waitForNode(hasTestTag("editor"))
        compose.onNodeWithTag("editor").performTextReplacement(broken)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitFor("it isn't saved") { Shaders.own(app, "zz-fine") == broken }
        compose.waitFor("no check") { editorMessage().startsWith("Saved · ") }
        assertTrue(editorMessage(), editorMessage().startsWith("Saved · can't be used: line 2: "))
    }

    /** `shader edit <name>` opens it in the editor; a name that is no shader says so, and opens nothing. */
    @Test
    fun shaderEditOpensTheEditor() {
        Shaders.save(app, "zz-fine", fine)
        val vm = showWithEditor()
        val commands = CommandRunner(app, vm)
        assertTrue(commands.run("shader edit zz-none").any { "No shader 'zz-none'" in it })
        assertNull(vm.shaderActions.editing.value)
        commands.run("shader edit zz-fine")
        compose.waitForNode(hasTestTag("editor"))
        assertEquals(fine, vm.shaderActions.editing.value?.code)
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitFor("the editor stays") { vm.shaderActions.editing.value == null }
        assertEquals(fine, Shaders.own(app, "zz-fine"))
    }

    /**
     * Edit on one that comes with Kolahal opens its code and says that saving makes your copy. Closing with a change
     * saves it as your file of the same name, used instead of Kolahal's; Remove brings Kolahal's back.
     */
    @Test
    fun editingABundledOneMakesYourCopy() {
        Shaders.remove(app, "lcd")
        try {
            showWithEditor()
            val bundled = Shaders.bundled(app, "lcd")
            val title = ShaderMeta.parse(bundled).title ?: "lcd"
            openSheet("lcd", title)
            tapOnSheet("Edit")
            compose.waitForText(BUNDLED_NOTE)
            // Nothing is saved until there is a change.
            assertFalse("lcd" in Shaders.userNames)
            compose.onNodeWithTag("editor").performTextReplacement("$bundled\n// mine")
            compose.onNodeWithContentDescription("Close").performClick()
            compose.waitFor("it isn't saved") { Shaders.own(app, "lcd") == "$bundled\n// mine" }
            assertTrue("lcd" in Shaders.userNames)
            assertEquals("$bundled\n// mine", Shaders.source(app, "lcd"))
            compose.waitForText("Saved lcd.agsl", substring = true)
        } finally {
            Shaders.remove(app, "lcd")
        }
        assertEquals(Shaders.bundled(app, "lcd"), Shaders.source(app, "lcd"))
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

    /**
     * Start from a template lists the templates by their names and descriptions, each row with a small
     * picture of its template where shaders can be drawn (a box taller than wide, inside the row's one touch
     * target); one becomes a shader of the user's under a free name, with the template's code, and the
     * templates themselves aren't listed.
     */
    @Test
    fun aTemplateBecomesOneOfYours() {
        val before = Shaders.userNames.toSet()
        try {
            show()
            compose.onNodeWithText("Add shader", useUnmergedTree = true).performClick()
            compose.onNode(hasText("Start from a template") and hasClickAction()).performClick()
            // Two sheets open and six templates are read first: under the coverage run that takes longer than most waits.
            compose.waitForText("Burst", ms = 20_000)
            for (title in listOf("Tint", "Hills", "Sea", "Ripple", "Gauge")) compose.onNodeWithText(title).assertExists()
            for (template in ShaderCatalog.TEMPLATES) {
                val meta = ShaderMeta.parse(Shaders.bundled(app, template))
                // The row says what it said before the pictures: its name and its description, and nothing more.
                val title = meta.title ?: template
                val said = compose.onNode(hasText(title) and hasClickAction()).fetchSemanticsNode().config
                assertEquals(setOf(title, meta.description.orEmpty()), said[SemanticsProperties.Text].map { it.text }.toSet())
                assertEquals(2, said[SemanticsProperties.Text].size)
                assertNull(said.getOrNull(SemanticsProperties.ContentDescription))
                val previews = compose.onAllNodesWithTag("template-preview $template", useUnmergedTree = true).fetchSemanticsNodes()
                if (!Shaders.supported) assertTrue(previews.isEmpty())
                else {
                    assertEquals(template, 1, previews.size)
                    val size = previews.single().size
                    assertTrue("$template: $size", size.width > 0 && size.height > size.width)
                }
            }
            compose.onNode(hasText("Tint") and hasClickAction()).performClick()
            compose.waitFor("no shader was made from it") { (Shaders.userNames - before).isNotEmpty() }
            val made = (Shaders.userNames - before).single()
            assertTrue(made, made == "my-tint" || made.startsWith("my-tint-"))
            assertEquals(Shaders.bundled(app, "template-tint"), Shaders.own(app, made))
            // The list reads the files again off the main thread: its row comes a moment after the file.
            compose.waitFor("$made isn't listed") { runCatching { scrollTo(made) }.isSuccess }
            row(made).assertExists()
            for (template in ShaderCatalog.TEMPLATES) row(template).assertDoesNotExist()
        } finally {
            (Shaders.userNames - before).forEach { Shaders.remove(app, it) }
        }
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
