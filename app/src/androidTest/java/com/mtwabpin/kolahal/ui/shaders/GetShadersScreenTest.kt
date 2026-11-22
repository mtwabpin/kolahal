package com.mtwabpin.kolahal.ui.shaders

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
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
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.ScriptActions
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.scripts.ScriptSourcesScreen
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings → Shaders → Get more shaders, from a list kept from the last download and code handed to the
 * screen as if downloaded ([com.mtwabpin.kolahal.ui.scripts.GetScriptsScreenTest] does the same for scripts). Nothing goes online.
 */
@RunWith(AndroidJUnit4::class)
class GetShadersScreenTest {
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

    private val code = "uniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"
    private val glow = ListedShader(
        "gz-glow", "Soft glow", "A glow around text", "ann", "1.0", code, listOf("soft", "light")
    )
    private val glowNext = glow.copy(version = "1.1", code = "$code\n// brighter")
    private val storm = ListedShader(
        "gz-storm", "Night storm", "Drops and thunder", "bo", "2.0", "$code\n// storm",
        listOf("weather"), animated = true
    )
    private val newer = ListedShader(
        "gz-newer", "From the future", "Needs a newer Kolahal", "di", "1.0", "$code\n// later",
        requires = "99.0"
    )
    private val own = ListedShader("gz-own", "Own", "A name of yours", "ed", "1.0", "$code\n// theirs")

    @After
    fun tearDown() {
        Shaders.remove(app, own.name)
    }

    /** The screen, opened as Add shader opens it; [downloaded] code is there as if fetched for the sheet. */
    private fun show(vararg downloaded: ListedShader): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            downloaded.forEach { vm.scriptActions.sharedCode[it.sha] = it.code }
            vm.openScreen(Screen.GET_SHADERS)
        }
        compose.setContent {
            GetShadersScreen(vm.shaderActions, vm.notice, vm::openScreen, vm::closeScreen)
        }
        compose.waitUntil(5_000) { vm.scriptActions.sharedScripts.value.let { !it.loading && it.sources.isNotEmpty() } }
        return vm
    }

    private fun row(title: String, vararg texts: String): SemanticsMatcher =
        texts.fold(hasText(title) and hasClickAction()) { m, t -> m and hasText(t) }

    private fun open(title: String) {
        compose.waitForText(title)
        compose.onNode(hasText(title) and hasClickAction()).performClick()
    }

    /** Taps the button [label] once it can be tapped: a sheet's buttons wait for the code. */
    private fun tapWhenEnabled(label: String) {
        val button = hasText(label) and hasClickAction() and isEnabled()
        compose.waitForNode(button, 15_000)
        compose.onNode(button).performScrollTo().performClick()
    }

    private fun search(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    /** Each shared shader by its title, with its file, what it looks like and whether it can be installed. */
    @Test
    fun theListShowsEachShader() {
        Shaders.save(app, own.name, "$code\n// mine")
        lists.listed(glow, storm, newer, own)
        show()
        compose.waitForNode(row("Soft glow", "gz-glow.agsl", "A glow around text"))
        compose.waitForNode(row("Night storm", "gz-storm.agsl · animated", "Drops and thunder"))
        compose.waitForNode(row("From the future", "Needs a newer Kolahal · Needs Kolahal 99.0"))
        compose.waitForNode(row("Own", "A name of yours · Name taken"))
        compose.onNodeWithText("Shared in ${ScriptCatalog.SOURCE} on GitHub").assertExists()
    }

    /** One that follows the phone's lean says so in its row and on its sheet. */
    @Test
    fun oneThatUsesTiltSaysSo() {
        val leaning = glow.copy(code = "uniform float2 tilt;\n$code", tilt = true)
        lists.listed(leaning)
        show(leaning)
        compose.waitForNode(row("Soft glow", "gz-glow.agsl · uses tilt"))
        open("Soft glow")
        compose.waitForText("Uses tilt: yes")
    }

    /** A list that doesn't say so: the sheet does, once the code is here. */
    @Test
    fun theCodeSaysItUsesTiltToo() {
        val leaning = glow.copy(code = "uniform float2 tilt;\n$code")
        lists.listed(leaning)
        show(leaning)
        compose.waitForNode(row("Soft glow", "gz-glow.agsl"))
        open("Soft glow")
        compose.waitForText("Uses tilt: yes")
    }

    /** A list without shaders says so: every list was one before. */
    @Test
    fun aListWithoutShadersSaysSo() {
        lists.listed()
        show()
        compose.waitForText("No shaders shared yet.")
    }

    @Test
    fun theSearchMatchesNamesAuthorsAndTags() {
        lists.listed(glow, storm)
        show()
        compose.waitForText("Soft glow")
        search("weather bo")
        compose.onNodeWithText("Night storm").assertExists()
        compose.onNodeWithText("Soft glow").assertDoesNotExist()
        search("soft weather")
        compose.onNodeWithText("No shader matches “soft weather”.").assertExists()
    }

    /** The sheet shows the code before Install; installing doesn't turn it on, and then offers Use. */
    @Test
    fun installingOneThenUsingIt() {
        lists.listed(glow)
        show(glow)
        open("Soft glow")
        compose.waitForText("gz-glow.agsl · v1.0 · by ann")
        compose.onNodeWithText("License: MIT").assertExists()
        compose.onNodeWithText("Tags: soft, light").assertExists()
        compose.waitForText(code)
        assertNull(Shaders.own(app, glow.name))
        tapWhenEnabled("Install")
        compose.waitFor("it isn't installed") { Shaders.own(app, glow.name) == code }
        assertEquals(ScriptCatalog.SOURCE, lists.store.installedFrom(glow.name))
        assertEquals(ShaderCatalog.OFF, config[Keys.SCREEN_SHADER])
        compose.waitForNode(hasText("Use") and hasClickAction())
        if (Shaders.supported) {
            tapWhenEnabled("Use")
            compose.waitFor("it isn't on") { config[Keys.SCREEN_SHADER] == glow.name }
            compose.waitForNode(hasText("Turn off") and hasClickAction())
        }
        compose.waitForNode(row("Soft glow", "A glow around text · Installed"))
    }

    /** A newer version of one installed from here shows as an update, and Update brings its code. */
    @Test
    fun anUpdateBringsTheNewCode() {
        lists.installed(glow)
        lists.listed(glowNext)
        show(glowNext)
        compose.waitForNode(row("Soft glow", "A glow around text · Update to 1.1"))
        open("Soft glow")
        tapWhenEnabled("Update to 1.1")
        compose.waitFor("it isn't updated") { Shaders.own(app, glow.name) == glowNext.code }
        compose.waitForNode(row("Soft glow", "A glow around text · Installed"))
    }

    /** A copy the user edited is replaced only after asking. */
    @Test
    fun anEditedCopyIsReplacedOnlyAfterAsking() {
        lists.installed(glow)
        val mine = "$code\n// mine"
        Shaders.save(app, glow.name, mine)
        show(glow)
        compose.waitForNode(row("Soft glow", "A glow around text · Installed, edited"))
        open("Soft glow")
        tapWhenEnabled("Replace your edited copy")
        compose.waitForText("Replace gz-glow.agsl?")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(mine, Shaders.own(app, glow.name))
        tapWhenEnabled("Replace your edited copy")
        compose.onNodeWithText("Replace").performClick()
        compose.waitFor("it isn't replaced") { Shaders.own(app, glow.name) == code }
    }

    /** A source the user added: its shaders name it, and a name installed from another source says so. */
    @Test
    fun aSourceYouAddedIsNamed() {
        lists.installed(glow)
        lists.listed(glow.copy(title = "Other glow", code = "$code\n// other"), source = lists.other)
        show()
        compose.waitForNode(row("Soft glow", "A glow around text · Installed"))
        compose.waitForNode(
            row(
                "Other glow",
                "A glow around text · From ${lists.other.name} · Installed from ${ScriptCatalog.SOURCE}"
            )
        )
        open("Other glow")
        compose.waitForText("You installed gz-glow from ${ScriptCatalog.SOURCE}.")
        compose.onNodeWithText("From: ${lists.other.name}, a source you added").assertExists()
    }

    /** A source that can't be read says why, and the shaders it had still show. */
    @Test
    fun aSourceThatCantBeReadSaysWhy() {
        lists.listed(glow)
        val vm = show()
        compose.waitForText("Soft glow")
        compose.runOnUiThread {
            val shared = vm.scriptActions._shared
            shared.value =
                ScriptActions.SharedScripts(shared.value.sources.map { it.copy(error = "HTTP 503 from mtwabpin.github.io") })
        }
        compose.waitForText("Can't read ${ScriptCatalog.SOURCE}: HTTP 503 from mtwabpin.github.io")
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Soft glow").assertExists()
    }

    /** Sources, in the top bar, opens the list of sources, which counts the shaders too. */
    @Test
    fun sourcesOpensFromTheTopBar() {
        lists.listed(glow, storm)
        val vm = show()
        compose.onNode(hasText("Sources") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { vm.screen.value == Screen.SCRIPT_SOURCES }
    }

    @Test
    fun theSourcesPageCountsTheShaders() {
        lists.listed(glow, storm)
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.GET_SHADERS)
            vm.openScreen(Screen.SCRIPT_SOURCES)
        }
        compose.setContent { ScriptSourcesScreen(vm) }
        compose.waitForNode(
            hasText(ScriptCatalog.SOURCE) and hasText("Reviewed · 0 scripts · 2 shaders · Read just now")
        )
    }
}
