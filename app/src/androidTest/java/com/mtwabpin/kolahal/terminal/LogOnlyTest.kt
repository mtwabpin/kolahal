package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.LogFilter
import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogKept
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `log only …`, "Only …" on a line, and `/@` in a search. */
@RunWith(AndroidJUnit4::class)
class LogOnlyTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    // A clear here purges the phone's own lines too: the whole log comes back after each test.
    @get:Rule val logKept = LogKept()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)

    @Before fun setUp() {
        logs.add(LogKind.SCRIPT, "lo first comic", "loxkcd", LogContext.of(LogContext.SCRIPT to "loxkcd"))
        logs.add(LogKind.SCRIPT, "lo a release", "loreleases", LogContext.of(LogContext.SCRIPT to "loreleases"))
        logs.add(LogKind.SCRIPT, "lo second comic", "loxkcd", LogContext.of(LogContext.SCRIPT to "loxkcd"))
    }

    @After fun tearDown() {
        logs.filter = null
        logs.recent(200).filter { it.text.startsWith("lo ") || it.text.startsWith("log ") || it.text.startsWith("echo lo") }.forEach { logs.delete(it.id) }
    }

    private fun viewModel() = vms.terminal(app)

    /** Types [text] at the prompt; with no screen showing it, Compose is told of the change by hand. */
    private fun type(vm: TerminalViewModel, text: String) = instrumentation.runOnMainSync {
        vm.onInputChange(TextFieldValue(text, TextRange(text.length)))
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }

    /**
     * Each filter leaves out most lines in the database ([LogFilter.sql]) before they are read. That must
     * never leave out a line the filter takes: with it or without it, the same lines are found.
     */
    @Test fun theDatabaseNarrowsWithoutLosingALine() {
        logs.add(LogKind.SCRIPT, "lo a feed item", "Lo Feed", LogContext.of(com.mtwabpin.kolahal.feeds.FeedWorker.CTX_FEED to "1"))
        logs.add(LogKind.INFO, "lo a notification", "Lo Chat", LogContext.of(com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY to "k"))
        logs.add(LogKind.INFO, "lo an app", "apps")
        logs.add(LogKind.INFO, "lo a call", com.mtwabpin.kolahal.calls.CallEvents.SOURCE)
        logs.add(LogKind.INFO, "lo a reminder", "remind")
        logs.add(LogKind.COMMAND, "lo a command")
        logs.add(LogKind.OUTPUT, "lo its output", ctx = LogContext.of(LogContext.FROM to "x"))
        logs.add(LogKind.OUTPUT, "lo a script's output", ctx = LogContext.of(LogContext.SCRIPT to "Lo/Xkcd é"))
        val starred = logs.add(LogKind.OUTPUT, "lo starred")
        logs.setStarred(starred, true)
        val filters = LogFilter.Kind.entries.map { LogFilter.OfKind(it) } +
            listOf("loxkcd", "Lo XKCD", "lo-feed", "Lo Chat", "loreleases", "nothing here").map { LogFilter.Named(it) }
        for (f in filters) {
            val narrowed = logs.search(emptyList(), limit = 2_000, match = f::matches, narrowBy = f.sql)
            val whole = logs.search(emptyList(), limit = 2_000, match = f::matches)
            assertEquals("$f", whole.entries.map { it.id }, narrowed.entries.map { it.id })
            assertEquals("$f", whole.total, narrowed.total)
        }
        logs.setStarred(starred, false)
    }

    @Test fun logOnlyShowsOneSourceUntilACommandRuns() {
        val vm = viewModel()
        vm.runCommand("log only loxkcd")
        waitUntil("only its lines") { vm.entries.value.map { it.text } == listOf("lo first comic", "lo second comic") }
        assertEquals(2, vm.filterCount.value)
        // It stays: a new home (after a restart) still shows only those lines.
        assertEquals(LogFilter.Named("loxkcd"), viewModel().filter.value)
        // New lines from there come in as they are logged, and count; others don't.
        logs.add(LogKind.SCRIPT, "lo third comic", "loxkcd", LogContext.of(LogContext.SCRIPT to "loxkcd"))
        waitUntil("the new line") { vm.entries.value.lastOrNull()?.text == "lo third comic" }
        waitUntil("counted") { vm.filterCount.value == 3 }
        logs.add(LogKind.SCRIPT, "lo another release", "loreleases", LogContext.of(LogContext.SCRIPT to "loreleases"))
        // A line it shows after that one: once it's in, the other was read and left out.
        logs.add(LogKind.SCRIPT, "lo fourth comic", "loxkcd", LogContext.of(LogContext.SCRIPT to "loxkcd"))
        waitUntil("the line after it") { vm.entries.value.lastOrNull()?.text == "lo fourth comic" }
        assertEquals(listOf("lo first comic", "lo second comic", "lo third comic", "lo fourth comic"), vm.entries.value.map { it.text })
        assertEquals(4, vm.filterCount.value)
        // Another command ends it, so it and what it prints show.
        vm.runCommand("echo lo done")
        waitUntil("everything again") { vm.filter.value == null && vm.entries.value.any { it.text == "lo a release" } }
        assertNull(logs.filter)
    }

    /**
     * A line a script changes in place (line.set) shows changed under `log only` too, where it is,
     * and a changed line of another source stays out; the count holds.
     */
    @Test fun aLineChangedInPlaceChangesUnderTheFilter() {
        val vm = viewModel()
        vm.runCommand("log only loxkcd")
        waitUntil("only its lines") { vm.entries.value.map { it.text } == listOf("lo first comic", "lo second comic") }
        val first = vm.entries.value.first()
        assertTrue(logs.update(first.id, "lo first comic, changed", first.ctx))
        waitUntil("changed where it is") { vm.entries.value.map { it.text } == listOf("lo first comic, changed", "lo second comic") }
        val release = logs.recent(200).single { it.text == "lo a release" }
        assertTrue(logs.update(release.id, "lo a release, changed", release.ctx))
        logs.add(LogKind.SCRIPT, "lo third comic", "loxkcd", LogContext.of(LogContext.SCRIPT to "loxkcd"))
        waitUntil("the next line") { vm.entries.value.lastOrNull()?.text == "lo third comic" }
        assertEquals(listOf("lo first comic, changed", "lo second comic", "lo third comic"), vm.entries.value.map { it.text })
        assertEquals(3, vm.filterCount.value)
        vm.runCommand("log all")
    }

    @Test fun aLinesLongPressFiltersToItsScript() {
        val vm = viewModel()
        waitUntil("the lines load") { vm.entries.value.any { it.text == "lo a release" } }
        val only = vm.actionsFor(vm.entries.value.last { it.text == "lo a release" }).single { it.label == "Show only loreleases" }
        instrumentation.runOnMainSync { only.run() }
        waitUntil("only the release") { vm.entries.value.map { it.text } == listOf("lo a release") }
        vm.runCommand("log all")
        waitUntil("everything again") { vm.filter.value == null && vm.entries.value.count { it.text.startsWith("lo ") } == 3 }
    }

    /** A search result the filter would hide: showing it ends the filter, as running a command does. */
    @Test fun showingAHiddenResultEndsTheFilter() {
        val vm = viewModel()
        vm.runCommand("log only loxkcd")
        waitUntil("only its lines") { vm.entries.value.map { it.text } == listOf("lo first comic", "lo second comic") }
        val release = logs.recent(50).single { it.text == "lo a release" }
        instrumentation.runOnMainSync { vm.showInLog(release) }
        waitUntil("everything again") { vm.filter.value == null && vm.entries.value.any { it.text == "lo a release" } }
        // One the filter shows keeps it.
        vm.runCommand("log only loxkcd")
        waitUntil("only its lines again") { vm.filter.value != null }
        instrumentation.runOnMainSync { vm.showInLog(logs.recent(50).single { it.text == "lo first comic" }) }
        Thread.sleep(500)
        assertEquals(LogFilter.Named("loxkcd"), vm.filter.value)
    }

    /**
     * "Star line" on a line's long-press: `/@starred` finds it, after a clear too, and showing it from
     * there shows the starred lines, as the clear took it off the screen.
     */
    @Test fun aStarredLineIsFoundAfterAClear() {
        val vm = viewModel()
        waitUntil("the lines load") { vm.entries.value.any { it.text == "lo a release" } }
        val star = vm.actionsFor(vm.entries.value.last { it.text == "lo a release" }).single { it.label == "Star line" }
        instrumentation.runOnMainSync { star.run() }
        waitUntil("starred") { vm.entries.value.single { it.text == "lo a release" }.starred }
        val release = vm.entries.value.single { it.text == "lo a release" }
        try {
            assertTrue(vm.actionsFor(release).any { it.label == "Unstar line" })
            val cleared = logs.clearScreen()
            logs.purgeCleared(cleared.upTo)
            waitUntil("off the screen") { vm.entries.value.none { it.text.startsWith("lo ") } }
            type(vm, "/@starred release")
            waitUntil("found") { vm.search.value?.rows.orEmpty().any { it is LogRow.Entry && it.entry.id == release.id } }
            instrumentation.runOnMainSync { vm.showInLog(release) }
            waitUntil("among the starred lines") {
                vm.filter.value == LogFilter.OfKind(LogFilter.Kind.STARRED) && vm.entries.value.any { it.id == release.id }
            }
        } finally {
            logs.delete(release.id)
        }
    }

    @Test fun atNarrowsASearchToOneSource() {
        val vm = viewModel()
        waitUntil("the lines load") { vm.entries.value.any { it.text == "lo second comic" } }
        assertTrue(vm.suggestions("/@loxk").any { it is Suggestion.Completion && it.replacement == "/@loxkcd " })
        type(vm, "/@loxkcd second")
        waitUntil("one comic") { vm.search.value?.total == 1 }
        assertEquals(listOf(LogFilter.Named("loxkcd")), vm.search.value!!.sources)
        type(vm, "/@loxkcd @loreleases")
        waitUntil("both scripts' lines") { vm.search.value?.total == 3 }
    }

    /**
     * A search's results follow lines a script changes in place (line.set): a changed line that
     * still matches shows its new text, one that no longer matches leaves, and one that now
     * matches comes in.
     */
    @Test fun aSearchFollowsLinesChangedInPlace() {
        val vm = viewModel()
        waitUntil("the lines load") { vm.entries.value.any { it.text == "lo second comic" } }
        fun found() = vm.search.value?.rows.orEmpty().mapNotNull { (it as? LogRow.Entry)?.entry?.text }
        type(vm, "/lo comic")
        waitUntil("two comics") { found() == listOf("lo first comic", "lo second comic") }
        val byText = logs.recent(200).associateBy { it.text }
        val first = byText.getValue("lo first comic")
        assertTrue(logs.update(first.id, "lo first comic, bigger", first.ctx))
        waitUntil("changed where it is") { found() == listOf("lo first comic, bigger", "lo second comic") }
        assertEquals(2, vm.search.value!!.total)
        val second = byText.getValue("lo second comic")
        assertTrue(logs.update(second.id, "lo second strip", second.ctx))
        waitUntil("no longer a match") { found() == listOf("lo first comic, bigger") }
        assertEquals(1, vm.search.value!!.total)
        val release = byText.getValue("lo a release")
        assertTrue(logs.update(release.id, "lo a release comic", release.ctx))
        waitUntil("a new match") { found() == listOf("lo first comic, bigger", "lo a release comic") }
        assertEquals(2, vm.search.value!!.total)
        type(vm, "")
    }
}
