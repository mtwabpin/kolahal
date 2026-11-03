package com.mtwabpin.kolahal.ui

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.SetupCheck
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The setup checklist: what each check shows, what Fix does, and the ways back. */
@RunWith(AndroidJUnit4::class)
class SetupScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val logs = LogRepository.get(instrumentation.targetContext)
    private val before = logs.maxId()
    private lateinit var vm: TerminalViewModel

    /** System pages a fix opened: caught, so none really opens over the test. */
    private val opened = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.packageName == instrumentation.targetContext.packageName) return null
            opened += intent
            return Instrumentation.ActivityResult(0, null)
        }
    }

    /** The system page each of these fixes opens. */
    private val pages = mapOf(
        "home" to Settings.ACTION_HOME_SETTINGS,
        "battery" to Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
        "usage-access" to Settings.ACTION_USAGE_ACCESS_SETTINGS,
    )

    /** Where home notes that its first run's tour was shown. */
    private val launcher = instrumentation.targetContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val toured = launcher.contains("intro_shown")

    @Before fun setUp() {
        // Not a first run: the tour the first home opens then would take the checklist's place.
        launcher.edit().putBoolean("intro_shown", true).commit()
        instrumentation.addMonitor(monitor)
    }

    @After fun tearDown() {
        instrumentation.removeMonitor(monitor)
        logs.recent(200, afterId = before).forEach { logs.delete(it.id) }
        if (!toured) launcher.edit().remove("intro_shown").commit()
    }

    /** The checklist on a phone-sized frame, opened from [from] as `setup` or Settings opens it. */
    private fun show(from: Screen = Screen.TERMINAL) {
        compose.runOnUiThread {
            vm = vms.terminal(compose.activity.application)
            if (from != Screen.TERMINAL) vm.openScreen(from)
            vm.openScreen(Screen.SETUP)
        }
        compose.setContent { KolahalTheme { Box(Modifier.size(360.dp, 720.dp)) { SetupScreen(vm) } } }
    }

    /** The checks, once they are in and showing. */
    private fun checks(): List<SetupCheck> {
        compose.waitUntil(5_000) { vm.setupChecks.value.isNotEmpty() }
        val checks = vm.setupChecks.value
        compose.onNodeWithText(checks.first().title).assertIsDisplayed()
        return checks
    }

    /** The title bar draws its lines twice, once for each of its heights: [text] showing at all. */
    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrollTo(check: SetupCheck) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(check.title))
    }

    private fun row(check: SetupCheck) = compose.onNode(hasText(check.title) and hasText(check.detail))

    private fun fixButton(check: SetupCheck) = compose.onNode(hasText("Fix") and hasAnyAncestor(hasText(check.title)))

    private fun commands() = logs.recent(200, afterId = before).filter { it.kind == LogKind.COMMAND }.map { it.text }

    /** The first check that can be fixed by opening a page this test knows, if any is still to do here. */
    private fun fixable(checks: List<SetupCheck>): SetupCheck {
        val check = checks.firstOrNull { !it.ok && it.fix in pages }
        assumeTrue("every check with a known page is done on this device", check != null)
        return check!!
    }

    /** Under the title: how many checks are done, a check with nothing to fix counting as done. */
    @Test fun itSaysHowManyAreDone() {
        show()
        val checks = checks()
        val done = checks.count { it.ok || it.fix == null }
        waitForText("$done of ${checks.size} done")
        compose.onAllNodesWithText("Checking…").assertCountEquals(0)
    }

    /** Each check says what it is for, with a tick when done, Fix when it can be, and a dash when there's nothing to do. */
    @Test fun eachCheckShowsWhereItStands() {
        show()
        for (check in checks()) {
            scrollTo(check)
            val row = row(check).assertIsDisplayed()
            when {
                check.ok -> {
                    row.assert(hasContentDescription("done"))
                    fixButton(check).assertDoesNotExist()
                }
                check.fix == null -> {
                    row.assert(hasText("–"))
                    fixButton(check).assertDoesNotExist()
                }
                else -> {
                    row.assert(hasText("!"))
                    fixButton(check).assertIsDisplayed()
                }
            }
        }
    }

    /** Fix runs the check's command, which opens its system page; tapping the row does the same. */
    @Test fun fixOpensTheChecksSystemPage() {
        show()
        val check = fixable(checks())
        scrollTo(check)
        fixButton(check).performClick()
        compose.waitUntil(5_000) { opened.size == 1 }
        assertEquals(pages[check.fix], opened.single().action)
        assertEquals(listOf(check.fix), commands())

        row(check).performClick()
        compose.waitUntil(5_000) { opened.size == 2 }
        assertEquals(pages[check.fix], opened.last().action)
        assertEquals(Screen.SETUP, vm.screen.value)
    }

    /** A check with nothing to fix, or one that's done, runs nothing when tapped. */
    @Test fun aCheckWithNothingToFixDoesNothing() {
        show()
        val checks = checks()
        // One that's done but has a fix (notifications allowed, say) is the one a missing guard would open.
        val tapped = listOfNotNull(checks.first { it.fix == null }, checks.firstOrNull { it.ok && it.fix != null })
        for (check in tapped) {
            scrollTo(check)
            row(check).performClick()
        }
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue("opened ${opened.map { it.action }}", opened.isEmpty())
            assertTrue("ran ${commands()}", commands().isEmpty())
            Thread.sleep(100)
        }
    }

    /** While the checks load, it says so, and the button isn't Done: none are done yet. */
    @Test fun beforeTheChecksComeItIsntDone() {
        compose.runOnUiThread { vm = vms.terminal(compose.activity.application) }
        // Not opened as `setup` opens it, nothing reads the checks: they stay loading.
        compose.setContent { KolahalTheme { Box(Modifier.size(360.dp, 720.dp)) { SetupScreen(vm) } } }
        waitForText("Checking…")
        compose.onNodeWithText("Continue to terminal").assertExists()
        compose.onAllNodesWithText("Done").assertCountEquals(0)
    }

    /** With checks still to do, the button says the terminal is next, and goes there. */
    @Test fun continueGoesToTheTerminal() {
        show()
        val checks = checks()
        assumeTrue("every check is done on this device", checks.any { !it.ok && it.fix != null })
        compose.onNodeWithText("Continue to terminal").performClick()
        compose.waitUntil(5_000) { vm.screen.value == Screen.TERMINAL }
    }

    /** The back arrow goes back to the page the checklist was opened from. */
    @Test fun theBackArrowGoesBackToSettings() {
        show(from = Screen.SETTINGS)
        checks()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) { vm.screen.value == Screen.SETTINGS }
    }

    /** Back leaves the checklist for the terminal it was opened from. */
    @Test fun backLeavesForTheTerminal() {
        show()
        checks()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { vm.screen.value == Screen.TERMINAL }
    }
}
