package com.mtwabpin.kolahal.ui

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.sheetAction
import com.mtwabpin.kolahal.terminal.LogAction
import com.mtwabpin.kolahal.terminal.LogLineActions
import com.mtwabpin.kolahal.terminal.UiEvent
import com.mtwabpin.kolahal.terminal.commands.CommandProcessor
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The long-press sheet of a line and of an app: the row of icons in fixed places, a group under a
 * heading for each thing the line names, chips that take touches over 48 dp.
 */
@RunWith(AndroidJUnit4::class)
class ActionSheetTest {
    @get:Rule
    val logLines = LogLinesSince()

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val apps = AppRepository.get(context)
    private val scripts = ScriptRepository.get(context)

    /** Home's side, doing nothing: the test only looks at the sheet. */
    private class Home : LogLineActions.Host {
        var lines: List<LogEntry> = emptyList()
        var list: List<Script> = emptyList()
        var ran = 0
        override val entries get() = lines
        override val scripts get() = list
        override fun io(block: suspend () -> Unit) = runBlocking { block() }
        override fun setText(text: String) {}
        override fun submit() {}
        override fun emit(event: UiEvent) {}
        override fun runsOnTap(line: String) = false
        override fun runCommand(cmd: String) {}
        override fun openEditorAt(script: Script, line: Int?) {}
        override fun startActivity(intent: Intent) {}
        override fun filterLog(filter: LogFilter?) {
            ran++
        }
    }

    private val home = Home()
    private val actions = LogLineActions(context, CommandProcessor(context, TerminalUiStub()), home)
    private var menu by mutableStateOf<ActionMenu?>(null)
    private var dismissed = 0

    @Before
    fun setUp() {
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
        compose.setContent { KolahalTheme { menu?.let { ActionSheet(it, onDismiss = { dismissed++ }) } } }
    }

    @After
    fun tearDown() {
        scripts.byName(SCRIPT)?.let { scripts.delete(it.id) }
    }

    private fun line(kind: LogKind, text: String, vararg ctx: Pair<String, String?>, source: String? = null): LogEntry {
        val id = logs.add(kind, text, source, LogContext.of(*ctx))
        return logs.recent(400, afterId = id - 1).single { it.id == id }
    }

    private fun show(title: String, list: List<LogAction>) {
        compose.runOnIdle { menu = ActionMenu(title, list, null) }
        compose.waitForIdle()
    }

    private fun bounds(label: String) = compose.sheetAction(label).fetchSemanticsNode().boundsInRoot

    private fun heading(text: String) =
        compose.onNode(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNode().boundsInRoot

    private fun settings(): AppEntry {
        val s = apps.apps.value.firstOrNull { it.pkg == SETTINGS && it.profile.kind == ProfileKind.PERSONAL }
        assumeTrue("Settings is installed", s != null)
        return s!!
    }

    /** Copy, Star and Delete are in the same places on a plain line and on a command's line. */
    @Test
    fun theRowKeepsItsPlacesFromLineToLine() {
        val plain = line(LogKind.INFO, "Llsheet plain")
        val command = line(LogKind.COMMAND, "echo llsheet", LogContext.COMMAND to "echo llsheet")
        val output = line(LogKind.OUTPUT, "llsheet", LogContext.FROM to command.id.toString())
        home.lines = listOf(plain, command, output)
        val row = listOf("Copy text", "Star line", "Delete line")
        show(plain.text, actions.actionsFor(plain))
        val onPlain = row.map { bounds(it) }
        show(command.text, actions.actionsFor(command))
        val onCommand = row.map { bounds(it) }
        assertEquals(onPlain.map { it.left }, onCommand.map { it.left })
        assertTrue(onPlain.zipWithNext().all { (a, b) -> a.right <= b.left && a.top == b.top })
        // The command's own actions are chips under its heading, below the row.
        assertTrue(heading("Command").top >= onCommand.first().bottom)
        assertTrue(bounds("Copy output").top > heading("Command").top)
        // A tap closes the sheet and runs the action.
        compose.sheetAction("Show only commands").performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(1, home.ran) }
    }

    /** A script's line that names an app has the app's group and the script's, each under its name. */
    @Test
    fun aScriptsLineThatNamesAnAppHasTwoGroups() {
        val s = settings()
        val script = scripts.create(SCRIPT, ScriptLang.LUA, "log('llsheet')")
        home.list = listOf(script)
        val e = line(
            LogKind.SCRIPT, "Llsheet line", LogContext.SCRIPT to SCRIPT, LogContext.PKG to s.pkg,
            LogContext.CLS to s.component.className, LogContext.USER to s.profile.serial.toString(), source = SCRIPT,
        )
        home.lines = listOf(e)
        show(e.text, actions.actionsFor(e))
        val app = heading(s.label)
        val named = heading(SCRIPT)
        assertTrue(app.bottom <= bounds("Launch ${s.label} [P]").top)
        assertTrue(bounds("Uninstall").bottom <= named.top)
        assertTrue(named.bottom <= bounds("Run $SCRIPT now").top)
        assertTrue(named.bottom <= bounds("Turn off $SCRIPT").top)
    }

    /**
     * A chip looks 40 dp tall and takes touches over 48 dp, and no two chips' touch areas overlap;
     * the row's buttons are at least 48 dp each way.
     */
    @Test
    fun chipsAndTheRowTakeTouchesOver48dp() {
        val s = settings()
        val e = line(
            LogKind.INFO, "Llsheet app", LogContext.PKG to s.pkg, LogContext.CLS to s.component.className,
            LogContext.USER to s.profile.serial.toString(), LogContext.URL to "https://example.com/a",
        )
        home.lines = listOf(e)
        val list = actions.actionsFor(e)
        show(e.text, list)
        val min = with(compose.density) { 48.dp.toPx() }
        val touch = list.map { compose.sheetAction(it.label).fetchSemanticsNode().touchBoundsInRoot }
        touch.forEachIndexed { i, r ->
            assertTrue("${list[i].label}: ${r.height}", r.height >= min - 1 && r.width >= min - 1)
        }
        for (i in touch.indices) for (j in i + 1 until touch.size) {
            val overlap: Rect = touch[i].intersect(touch[j])
            assertTrue(
                "${list[i].label} and ${list[j].label} overlap",
                overlap.width <= 0f || overlap.height <= 0f,
            )
        }
    }

    /** An app held in a list has its five in the row, in order: Open, App info, Favorite, Hide, Uninstall. */
    @Test
    fun anAppInAListHasItsFiveInTheRow() {
        val s = settings()
        show(s.label, actions.appActions(s))
        val five = listOf(
            "Launch ${s.label} [P]",
            "App info",
            if (com.mtwabpin.kolahal.apps.AppPrefs.get(context).isPinned(s.key)) "Unpin from favorites" else "Pin to favorites",
            "Hide from lists",
            "Uninstall",
        ).map { bounds(it) }
        assertTrue(five.zipWithNext().all { (a, b) -> a.right <= b.left && a.top == b.top })
        assertEquals(1, compose.onAllNodesWithContentDescription("Copy package name").fetchSemanticsNodes().size)
        assertTrue(heading("More").bottom <= bounds("Copy package name").top)
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
        const val SCRIPT = "llsheet"
    }
}
