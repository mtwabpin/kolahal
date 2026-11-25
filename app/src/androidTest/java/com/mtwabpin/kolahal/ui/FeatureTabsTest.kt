package com.mtwabpin.kolahal.ui

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.feeds.FeedScheduler
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.feeds.FeedsScreen
import com.mtwabpin.kolahal.ui.scripts.ScriptsScreen
import com.mtwabpin.kolahal.ui.settings.SettingsOverHome
import com.mtwabpin.kolahal.ui.settings.ShadersOverHome
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Settings | Scripts | Shaders | Feeds tabs: their order, the open one, Back, and their words at large font sizes. */
@RunWith(AndroidJUnit4::class)
class FeatureTabsTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()

    // With its activity, so a test can press Back.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val feeds = FeedRepository.get(app)
    private lateinit var vm: TerminalViewModel

    /** Where home notes that its first run's tour was shown: set, so no tour takes a page's place. */
    private val launcher = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val toured = launcher.contains("intro_shown")

    @After
    fun tearDown() {
        if (!toured) launcher.edit().remove("intro_shown").commit()
        feeds.all().filter { MARK in it.url }.forEach { feeds.remove(it.id) }
        FeedScheduler.sync(app)
    }

    /** A button of the tab row. */
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            hasAnyAncestor(hasTestTag("feature-tabs"))

    private fun SemanticsNode.label() =
        config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    /**
     * The four screens as MainActivity shows them, on a phone-sized frame, open on [first]. As there, each
     * keeps what it remembers while another is open.
     */
    private fun show(first: Screen) {
        launcher.edit().putBoolean("intro_shown", true).commit()
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(first)
        }
        compose.setContent {
            val screen by vm.screen.collectAsStateWithLifecycle()
            val saved = rememberSaveableStateHolder()
            KolahalTheme {
                Box(
                    Modifier
                        .size(360.dp, 700.dp)
                        .consumeWindowInsets(WindowInsets.safeDrawing)
                ) {
                    saved.SaveableStateProvider(screen.name) {
                        when (screen) {
                            Screen.SETTINGS -> SettingsOverHome(vm)
                            Screen.SCRIPTS -> ScriptsScreen(vm)
                            Screen.SHADERS -> ShadersOverHome(vm)
                            Screen.FEEDS -> FeedsScreen(vm)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    /** Taps the tab that reads [label], and waits for its screen with its tab selected. */
    private fun open(label: String, screen: Screen) {
        compose.waitForNode(tab and hasText(label, substring = true))
        compose.onNode(tab and hasText(label, substring = true)).performClick()
        compose.waitFor("$screen isn't open") { vm.screen.value == screen }
        compose.waitForNode(tab and isSelected() and hasText(label, substring = true))
    }

    private fun back() =
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    /**
     * Each of the four screens shows the four tabs in order, with its own selected. A tap on the open
     * tab leaves it open.
     */
    @Test
    fun theFourTabsAreInOrderWithTheOpenOneSelected() {
        show(Screen.SETTINGS)
        val labels = listOf("Settings", "Scripts", "Shaders", "Feeds")
        for ((label, screen) in labels.zip(FEATURE_TABS)) {
            open(label, screen)
            val tabs = compose.onAllNodes(tab).fetchSemanticsNodes()
            // Feeds may have its unread count after it.
            assertEquals(labels, tabs.map { it.label().substringBefore(" ·") })
            assertEquals(
                listOf(label),
                tabs.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
                    .map { it.label().substringBefore(" ·") })
        }
    }

    /**
     * Shaders as a tab, as Scripts is: its search stays while another tab is open; Back clears the
     * search, then goes home.
     */
    @Test
    fun theShadersTabKeepsItsSearchAndBackGoesHome() {
        show(Screen.SETTINGS)
        open("Settings", Screen.SETTINGS)
        open("Shaders", Screen.SHADERS)
        compose.waitForNode(hasTestTag("shader-search"))
        compose.onNode(hasSetTextAction() and hasTestTag("shader-search")).performTextInput("crt")
        open("Scripts", Screen.SCRIPTS)
        open("Shaders", Screen.SHADERS)
        compose.waitForNode(hasSetTextAction() and hasTestTag("shader-search") and hasText("crt"))
        back()
        compose.waitFor("the search isn't cleared") {
            compose.onAllNodes(hasSetTextAction() and hasTestTag("shader-search") and hasText("crt"))
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals(Screen.SHADERS, vm.screen.value)
        back()
        compose.waitFor("Back doesn't go home") { vm.screen.value == Screen.TERMINAL }
    }

    /**
     * On a narrow phone (360 dp, less the page's margins), each tab's word is whole on one line at the usual font size and at
     * 1.3 and 2 times it, Feeds with 102 unread too: a word too long shrinks.
     */
    @Test
    fun theFourWordsFitAtLargeFontSizes() {
        val feed = feeds.add("http://127.0.0.1:9/$MARK/${System.nanoTime()}.xml", "Tabs")
        feeds.insertItems(feed.id, (1..102).map { FeedRepository.NewItem("t$it", "Item $it", null, null, null) })
        feeds.recordFetch(feed.id, "ok")
        compose.runOnUiThread { vm = vms.terminal(app) }
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            // Made again at each scale, as Android makes the activity again when the font size changes.
            key(scale) {
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                    KolahalTheme { Box(Modifier.width(328.dp)) { FeatureTabs(vm, Screen.SHADERS) } }
                }
            }
        }
        val words = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
                hasAnyAncestor(hasTestTag("feature-tabs"))
        for (s in listOf(1f, 1.3f, 2f)) {
            compose.runOnIdle { scale = s }
            compose.waitFor("Feeds has no count") {
                compose.onAllNodes(words and hasText("Feeds · ", substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val nodes = compose.onAllNodes(words, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(4, nodes.size)
            for (node in nodes) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.single()
                val text = layout.layoutInput.text.text
                val size = "${layout.layoutInput.style.fontSize}, ${layout.size.width} of ${layout.layoutInput.constraints.maxWidth} px"
                assertEquals("$text at $s", 1, layout.lineCount)
                assertFalse("$text at $s ends in … ($size)", layout.isLineEllipsized(0))
                assertEquals("$text at $s", text.length, layout.getLineEnd(0, visibleEnd = true))
                assertFalse("$text at $s", layout.hasVisualOverflow)
            }
        }
    }

    private companion object {
        const val MARK = "kolahal-feature-tabs-test"
    }
}
