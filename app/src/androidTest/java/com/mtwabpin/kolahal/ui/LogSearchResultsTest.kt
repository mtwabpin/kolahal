package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.Gutter
import com.mtwabpin.kolahal.terminal.LogFilter
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val DAY = LocalDate.of(2026, 3, 2)
private val AT = DAY.atTime(9, 41).toInstant(ZoneOffset.UTC).toEpochMilli()
private val TIMES = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private val INDENTS = LineIndents(16.sp, 16.sp, 16.sp)

/** A log search (`/words`): the lines it finds in place of the log, and what the line under them says. */
@RunWith(AndroidJUnit4::class)
class LogSearchResultsTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val config = Config.get(app)
    @get:Rule val logLines = LogLinesSince()
    @get:Rule val configReset = ConfigReset()
    private val opened = ArrayList<LogEntry>()
    private val pressed = ArrayList<LogEntry>()
    private var ids = 0L

    private fun entry(text: String, kind: LogKind = LogKind.OUTPUT, source: String? = null, ctx: Map<String, String> = emptyMap()) =
        LogEntry(++ids, AT, kind, source, text, LogContext(ctx))

    private fun row(e: LogEntry, showTime: Boolean = true) = LogRow.Entry(e, dimmed = false, gutter = Gutter.COMMAND, showTime = showTime)

    private fun found(words: List<String>, vararg rows: LogRow, total: Int = rows.count { it is LogRow.Entry }, searched: Int = 100, sources: List<LogFilter> = emptyList()) =
        TerminalViewModel.LogSearch(words, rows.toList(), total, searched, sources)

    /** The results and the line under them, as home shows them while searching. */
    private fun show(search: () -> TerminalViewModel.LogSearch) = compose.setContent {
        KolahalTheme {
            Column(Modifier.size(360.dp, 600.dp)) {
                LogSearchResults(
                    search(), TIMES, gutter = true, indents = INDENTS, menuEntryId = null, apps = { emptyList() },
                    onOpen = { opened += it }, onLongPress = { pressed += it }, onAppTap = {}, onAppLongPress = {},
                    onSwipeCopy = {}, onSwipeDelete = {}, modifier = Modifier.weight(1f),
                )
                LogSearchSummary(search())
            }
        }
    }

    private fun texts(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }

    private fun summary() = texts("log-search-summary").joinToString("")

    private fun displayed(e: LogEntry) = runCatching { compose.onNodeWithTag("line-${e.id}").assertIsDisplayed() }.isSuccess

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Nothing typed after / yet: how to search. */
    @Test fun withNothingTypedItSaysHowToSearch() {
        show { found(emptyList()) }
        assertEquals(
            "Search the log: type words after / to find lines, commands, sources and links · @ and a name searches one source's lines",
            summary(),
        )
    }

    /** Nothing found: what was looked for, and where, and how to close the search. */
    @Test fun withNoMatchItSaysWhatWasLookedFor() {
        var search by mutableStateOf(found(listOf("zq", "late"), total = 0))
        show { search }
        assertEquals("No line has “zq late” · Esc or back to close", summary())
        compose.runOnIdle { search = found(emptyList(), total = 0, sources = listOf(LogFilter.Named("xkcd"), LogFilter.OfKind(LogFilter.Kind.FEEDS))) }
        assertEquals("No line from xkcd or feeds · Esc or back to close", summary())
        compose.runOnIdle { search = found(listOf("zq"), total = 0, sources = listOf(LogFilter.Named("xkcd"))) }
        assertEquals("No line from xkcd has “zq” · Esc or back to close", summary())
    }

    /** How many lines match of how many were searched, and when more match than show, that the newest show. A day's divider is no line. */
    @Test fun itCountsTheMatches() {
        val rows = arrayOf(LogRow.Day(DAY), row(entry("zq one")), row(entry("zq two")))
        var search by mutableStateOf(found(listOf("zq"), *rows, searched = 120))
        show { search }
        assertEquals("2 of 120 lines · tap one to see it in the log", summary())
        compose.runOnIdle { search = found(listOf("zq"), *rows, total = 250, searched = 9000) }
        assertEquals("250 of 9000 lines · showing the newest 2 · tap one to see it in the log", summary())
        compose.runOnIdle { search = found(listOf("zq"), *rows, searched = 120, sources = listOf(LogFilter.Named("xkcd"))) }
        assertEquals("2 of 120 lines from xkcd · tap one to see it in the log", summary())
    }

    /** The newest match is at the bottom, as in the log, under its day; each has its time, even one the log shows without. */
    @Test fun theNewestIsAtTheBottomAndEachHasItsTime() {
        val older = entry("zq older")
        val newer = entry("zq newer")
        show { found(listOf("zq"), LogRow.Day(DAY), row(older, showTime = false), LogRow.Unread(1), row(newer, showTime = false)) }
        val label = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(DAY)
        val day = compose.onNodeWithText("── $label ──").getUnclippedBoundsInRoot()
        val top = compose.onNodeWithTag("line-${older.id}").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithTag("line-${newer.id}").getUnclippedBoundsInRoot()
        assertTrue("day ${day.bottom}, older ${top.top}", day.bottom <= top.top)
        assertTrue("older ${top.bottom}, newer ${bottom.top}", top.bottom <= bottom.top)
        assertEquals(listOf("zq older", "09:41"), texts("line-${older.id}"))
        assertEquals(listOf("zq newer", "09:41"), texts("line-${newer.id}"))
        compose.onAllNodesWithText("new since you left", substring = true).assertCountEquals(0)
    }

    /** The words found are marked where the line shows them, whatever their case. */
    @Test fun theWordsAreMarkedInTheLine() {
        val e = entry("Battery ZQ low")
        show { found(listOf("zq"), row(e)) }
        val text = compose.onNodeWithTag("line-${e.id}").fetchSemanticsNode().config[SemanticsProperties.Text].first()
        val marked = text.spanStyles.filter { it.item.background.isSpecified }.map { text.text.substring(it.start, it.end) }
        assertEquals(listOf("ZQ"), marked)
    }

    /**
     * A line the words were found in by its link or where it came from says so under it, each place
     * once; a line that shows every word, or that names its source itself, says nothing more.
     */
    @Test fun aLineFoundElsewhereSaysWhere() {
        val link = entry("Release notes late", ctx = mapOf(LogContext.URL to "https://example.com/zq-notes"))
        val fed = entry("late train", source = "zq-feed")
        val run = entry("late run", source = "zq-run", ctx = mapOf(LogContext.SCRIPT to "zq-run"))
        val both = entry("late both", source = "zq-both-feed", ctx = mapOf(LogContext.URL to "https://example.com/zq-both"))
        val seen = entry("zq late seen", ctx = mapOf(LogContext.URL to "https://example.com/zq-seen"))
        val named = entry("late title", kind = LogKind.SCRIPT, source = "zq-script")
        val described = entry("late name", ctx = mapOf(LogContext.DESC to "zq column", LogContext.URL to "https://example.com/zq-desc"))
        show { found(listOf("zq", "late"), *listOf(link, fed, run, both, seen, named, described).map { row(it) }.toTypedArray()) }

        compose.onAllNodesWithText("https://example.com/zq-notes").assertCountEquals(1)
        compose.onAllNodesWithText("from zq-feed").assertCountEquals(1)
        compose.onAllNodesWithText("from zq-run").assertCountEquals(1)
        compose.onAllNodesWithText("https://example.com/zq-both · from zq-both-feed").assertCountEquals(1)
        compose.onAllNodesWithText("https://example.com/zq-seen").assertCountEquals(0)
        compose.onAllNodesWithText("from zq-script").assertCountEquals(0)
        compose.onAllNodesWithText("https://example.com/zq-desc").assertCountEquals(0)
    }

    /** Tapping a match shows it in the log; a long-press asks for its actions. */
    @Test fun aTapOpensAMatchAndALongPressAsksForItsActions() {
        val e = entry("zq tap me")
        show { found(listOf("zq"), row(entry("zq other")), row(e)) }
        compose.onNodeWithTag("line-${e.id}").performClick()
        compose.runOnIdle { assertEquals(listOf(e), opened) }
        compose.onNodeWithTag("line-${e.id}").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(e), pressed)
            assertEquals(listOf(e), opened)
        }
    }

    /** Other words start again at the newest match; the same words with a new match in keep the place. */
    @Test fun otherWordsStartAgainAtTheNewest() {
        val entries = (1..60).map { entry("zq line $it") }
        val rows = entries.map { row(it) }.toTypedArray()
        var search by mutableStateOf(found(listOf("zq"), *rows))
        show { search }
        val oldest = entries.first()
        compose.onNodeWithTag("log-search").performScrollToNode(hasTestTag("line-${oldest.id}"))
        compose.waitUntil(5_000) { displayed(oldest) }
        assertFalse(displayed(entries.last()))

        val newer = entry("zq line 61")
        compose.runOnIdle { search = found(listOf("zq"), *rows, row(newer)) }
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue("the place was lost", displayed(oldest))
            Thread.sleep(100)
        }

        compose.runOnIdle { search = found(listOf("zq", "line"), *rows, row(newer)) }
        compose.waitUntil(5_000) { displayed(newer) }
        assertFalse(displayed(oldest))
    }

    /** `/` and words at the prompt show the lines that match in place of the log; a tap ends the search and shows the line in the log. */
    @Test fun aSearchAtThePromptShowsTheMatchesInPlaceOfTheLog() {
        config.set(Keys.LOG_TIPS, "off")
        val token = "lsr${System.nanoTime()}"
        // Older lines above it, as in a log of any length.
        repeat(30) { logs.add(LogKind.OUTPUT, "lsr older $it") }
        val first = logs.add(LogKind.OUTPUT, "$token first")
        val other = logs.add(LogKind.OUTPUT, "lsr other line")
        val second = logs.add(LogKind.OUTPUT, "$token second")
        // More than a screen after them: the tap has to bring the first back into view.
        val last = (1..60).map { logs.add(LogKind.OUTPUT, "lsr filler $it") }.last()
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp).consumeWindowInsets(WindowInsets.safeDrawing)) { TerminalScreen(vm, onPickFolder = {}) }
            }
        }
        fun type(text: String) = compose.runOnIdle { vm.onInputChange(TextFieldValue(text, TextRange(text.length))) }
        compose.waitUntil(5_000) { exists("line-$last") }
        assertFalse("the first is off screen before", exists("line-$first"))

        type("/")
        compose.waitUntil(5_000) { exists("log-search-summary") }
        assertTrue(summary(), summary().startsWith("Search the log: type words after /"))
        assertTrue(exists("log"))
        assertFalse(exists("log-search"))

        type("/$token")
        val counted = Regex("2 of \\d+ lines · tap one to see it in the log")
        compose.waitUntil(5_000) { exists("log-search") && counted.matches(summary()) }
        assertTrue(exists("line-$first") && exists("line-$second"))
        assertFalse(exists("line-$other") || exists("line-$last"))
        assertFalse(exists("log"))

        compose.onNodeWithTag("line-$first").performClick()
        compose.waitUntil(5_000) { !exists("log-search") && exists("log") && exists("line-$first") }
        compose.onNodeWithTag("line-$first").assertIsDisplayed()
        assertEquals("", vm.input.text)
        assertFalse(exists("log-search-summary"))
    }
}
