package com.mtwabpin.kolahal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.terminal.WidgetFeed
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetStates
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The card of a script's widget, drawn from what is kept for it: each kind's texts, what it says
 * with nothing to draw, its footer, its one description for a screen reader, and its taps.
 */
@RunWith(AndroidJUnit4::class)
class ScriptWidgetCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()

    @After fun tearDown() = names.forEach { WidgetStates.forget(context, it) }

    /** A name no script has. What a test keeps for its widget is forgotten after it. */
    private fun name(suffix: String): String = "wcard-$suffix".also {
        names += it
        WidgetStates.forget(context, it)
    }

    /** A name with [spec] kept as its widget's content, set just now. */
    private fun kept(suffix: String, spec: WidgetSpec): String = name(suffix).also { WidgetStates.put(context, it, spec) }

    /** [name]'s content as set [minutes] ago. */
    private fun setAgo(name: String, minutes: Long) {
        val at = System.currentTimeMillis() - minutes * 60_000 - 30_000
        KvStore(context, Stores.WIDGET_META).set(name, WidgetStates.encodeMeta(at, 0, null))
    }

    /**
     * The cards of these scripts' widgets, one under the other, each [width] wide and [height] tall.
     * - Without [actions] they are as the Widgets screen draws them.
     * - With [inView], the widgets page is the page in view.
     * - Without a [feed] the cards know nothing of their scripts, and draw what is kept under the name.
     */
    private fun show(vararg names: String, width: Dp = 320.dp, height: Dp = 200.dp, actions: WidgetCardActions? = null, inView: Boolean = false, feed: WidgetFeed? = null) =
        showSources(*names.map(WidgetSource::script).toTypedArray(), width = width, height = height, actions = actions, inView = inView, feed = feed)

    private fun showSources(vararg sources: String, width: Dp = 320.dp, height: Dp = 200.dp, actions: WidgetCardActions? = null, inView: Boolean = false, feed: WidgetFeed? = null) {
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalWidgetsShown provides inView) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        sources.forEachIndexed { i, source ->
                            Box(Modifier.width(width)) { SourceWidget(PlacedWidget(null, source, 0L, height.value.toInt(), i), height, actions, feed) }
                        }
                    }
                }
            }
        }
    }

    private fun inCard(name: String) = hasAnyAncestor(hasTestTag("widget:$name"))

    private val hasWords = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    private val hasDescription = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    private fun hasClickLabel(label: String?) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.let { click -> click.label == label } == true }

    /** Every text [name]'s card draws, each as its own. */
    private fun texts(name: String): List<String> = compose.onAllNodes(inCard(name) and hasWords, useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    /** Waits until [name]'s card draws each of [expected] as a text of its own. The state is read off the main thread. */
    private fun draws(name: String, vararg expected: String) {
        compose.waitFor("$name's card doesn't draw ${expected.toList()}, it draws ${texts(name)}") { texts(name).containsAll(expected.toList()) }
    }

    /**
     * Waits until [name]'s card says [expected] to a screen reader. That is the only description in
     * the card, and the card with all its texts is one item.
     */
    private fun says(name: String, expected: String) {
        fun said() = compose.onAllNodes(inCard(name) and hasDescription, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.ContentDescription] }
        compose.waitFor("$name's card doesn't say “$expected”, it says ${said()}") { said() == listOf(expected) }
        compose.onAllNodes(inCard(name) and (hasWords or hasDescription)).assertCountEquals(1)
        compose.onNode(inCard(name) and hasDescription).assertContentDescriptionEquals(expected)
    }

    private fun hhmm(millis: Long) = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** `stat`: the big value with its unit, the change, the label and the smaller values. */
    @Test fun aStatDrawsItsValueAndTheRest() {
        val name = kept(
            "stat",
            WidgetSpec.Stat(
                WidgetBase(title = "Weather", icon = "☀"), "24", label = "Sunny", unit = "°C", delta = "2", trend = "up",
                more = listOf(WidgetSpec.Small("68%", "humidity"), WidgetSpec.Small("12", "km/h")), progress = 0.4f,
            ),
        )
        show(name)
        draws(name, "Weather", "☀", "24", "°C", "↑ 2", "Sunny", "68%", "humidity", "12", "km/h")
        says(name, "Weather: 24°C, Sunny, up 2, 68% humidity, 12 km/h, 40%")
        // Current content has no footer.
        assertTrue(texts(name).toString(), texts(name).none { it.endsWith("ago") || it.startsWith("Failed") })
        // As the Widgets screen draws it, it takes no taps.
        compose.onAllNodes(inCard(name) and hasClickAction()).assertCountEquals(0)
    }

    /** A card of one cell has room for the value and its label only. A screen reader still hears everything. */
    @Test fun aSmallCardLeavesPartsOutAndStillSaysThem() {
        val name = kept(
            "small",
            WidgetSpec.Stat(WidgetBase(title = "Weather", icon = "☀"), "24", label = "Sunny", unit = "°C", delta = "2", trend = "up", more = listOf(WidgetSpec.Small("68%", "humidity"))),
        )
        setAgo(name, 125)
        show(name, width = 96.dp, height = 96.dp)
        draws(name, "24°C", "Sunny")
        assertEquals(listOf("24°C", "Sunny"), texts(name))
        says(name, "Weather: 24°C, Sunny, up 2, 68% humidity. Updated 2 hours ago")
    }

    /** `text`: its lines and its caption, under the title. Without a title the card has the script's name. */
    @Test fun aTextDrawsItsLinesAndCaption() {
        val quote = kept("text", WidgetSpec.Text(WidgetBase(title = "Quote"), "Simple is better than complex.", "Tim Peters"))
        val plain = kept("text-plain", WidgetSpec.Text(WidgetBase(), "No title here"))
        show(quote, plain)
        draws(quote, "Quote", "Simple is better than complex.", "Tim Peters")
        says(quote, "Quote: Simple is better than complex., Tim Peters")
        draws(plain, plain, "No title here")
        says(plain, "$plain: No title here")
    }

    /** `list`: each row's title, second line, icon and end text; as many rows as fit, then how many more; and its own words when empty. */
    @Test fun aListDrawsTheRowsThatFit() {
        val todo = kept(
            "list",
            WidgetSpec.Rows(WidgetBase(title = "Todo"), listOf(WidgetSpec.Row("Buy milk", detail = "today", trailing = "2", icon = "🛒"), WidgetSpec.Row("Call mum", color = StyledText.Colour.BAD))),
        )
        val long = kept("list-long", WidgetSpec.Rows(WidgetBase(title = "Stories"), (1..12).map { WidgetSpec.Row("Story $it") }))
        val empty = kept("list-empty", WidgetSpec.Rows(WidgetBase(title = "Inbox"), emptyList(), empty = "All read"))
        val bare = kept("list-bare", WidgetSpec.Rows(WidgetBase(title = "Inbox"), emptyList()))
        show(todo, long, empty, bare)
        draws(todo, "Todo", "🛒", "Buy milk", "today", "2", "Call mum")
        says(todo, "Todo: Buy milk, today, 2, Call mum")

        draws(long, "Stories", "Story 1")
        val rows = texts(long).filter { it.startsWith("Story ") }
        assertTrue("$rows", rows.size in 1..11)
        assertEquals((1..rows.size).map { "Story $it" }, rows)
        assertEquals(listOf("+${12 - rows.size} more"), texts(long).filter { it.endsWith(" more") })
        // A screen reader hears the rows that didn't fit too.
        says(long, "Stories: " + (1..12).joinToString(", ") { "Story $it" })

        draws(empty, "Inbox", "All read")
        says(empty, "Inbox: All read")
        draws(bare, "Inbox", "Nothing to show")
        says(bare, "Inbox: Nothing to show")
    }

    /** `progress`: a ring with its text in it, bars with their names, or its own text over one bar. */
    @Test fun aProgressDrawsHowFar() {
        val ring = kept("ring", WidgetSpec.Progress(WidgetBase(title = "Battery"), 0.68f, label = "charging"))
        val bars = kept(
            "bars",
            WidgetSpec.Progress(
                WidgetBase(title = "Disk"), 0f,
                items = listOf(WidgetSpec.Bar("photos", 30f, 100f, null, null), WidgetSpec.Bar("music", 5f, 10f, "5 GB", StyledText.Colour.WARN)),
            ),
        )
        val bar = kept("bar", WidgetSpec.Progress(WidgetBase(), 3f, 5f, text = "3 of 5", label = "chapters", style = "bar"))
        show(ring, bars, bar)
        draws(ring, "Battery", "68%", "charging")
        says(ring, "Battery: 68%, charging")
        draws(bars, "Disk", "photos", "30%", "music", "5 GB")
        says(bars, "Disk: photos 30%, music 5 GB")
        draws(bar, bar, "3 of 5", "chapters")
        says(bar, "$bar: 3 of 5, chapters")
    }

    /** `chart`: its headline and the labels of its first and last point. The plot is said in words. */
    @Test fun aChartDrawsItsHeadlineAndLabels() {
        val steps = kept(
            "chart",
            WidgetSpec.Chart(WidgetBase(title = "Steps"), listOf(1f, 3f, 2f, 5f), value = "5,204", label = "today", labels = listOf("Mon", "Thu")),
        )
        val told = kept(
            "chart-told",
            WidgetSpec.Chart(WidgetBase(title = "Rain"), listOf(0f, 2.5f), style = "bars", value = "2.5 mm", summary = "More rain than yesterday"),
        )
        show(steps, told)
        draws(steps, "Steps", "5,204", "today", "Mon", "Thu")
        says(steps, "Steps: 5,204, today, 4 values, from 1 to 5, the latest 5")
        draws(told, "Rain", "2.5 mm")
        says(told, "Rain: 2.5 mm, More rain than yesterday")
    }

    /** `countdown`: the days to the moment, its own words on the day, and the days since. */
    @Test fun aCountdownCountsTheDays() {
        val now = System.currentTimeMillis() / 1000
        val day = 86_400L
        val trip = kept("countdown", WidgetSpec.Countdown(WidgetBase(title = "Trip"), to = now + 3 * day + 3_600, label = "to Goa", style = "days"))
        val reached = kept("countdown-reached", WidgetSpec.Countdown(WidgetBase(title = "Launch"), to = now - 1, reached = "Lift-off"))
        val past = kept("countdown-past", WidgetSpec.Countdown(WidgetBase(title = "Since"), to = now - 5 * day - 3_600, label = "the move"))
        show(trip, reached, past)
        draws(trip, "Trip", "3 days", "to Goa")
        says(trip, "Trip: 3 days, to Goa")
        draws(reached, "Launch", "Lift-off")
        says(reached, "Launch: Lift-off")
        draws(past, "Since", "5 days ago", "the move")
        says(past, "Since: 5 days ago, the move")
    }

    /** A widget that was cleared, or whose run showed nothing, says so. One that no run has filled yet says nothing: it loads. */
    @Test fun withNothingKeptItSaysSo() {
        val cleared = kept("cleared", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        WidgetStates.clear(context, cleared)
        val ran = name("ran")
        WidgetStates.ran(context, ran, null)
        val new = name("new")
        show(cleared, ran, new)
        // Without content the card has the script's name: the title was part of what it showed.
        draws(cleared, cleared, "Nothing to show")
        says(cleared, "$cleared: Nothing to show")
        draws(ran, ran, "Nothing to show")
        says(ran, "$ran: Nothing to show")
        says(new, "$new: loading")
        assertEquals(listOf(new), texts(new))
    }

    /** Content that can't be read, and a built-in widget this version doesn't know, say "widget unavailable". */
    @Test fun whatCantBeReadIsUnavailable() {
        // Set just now, as `widget.show` leaves it, and then not what Kolahal reads: another kind, another format.
        val broken = kept("broken", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        KvStore(context, Stores.WIDGET).set(broken, "{\"v\":1,\"kind\":\"gauge\",\"value\":\"24\"}")
        val newer = kept("newer", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        KvStore(context, Stores.WIDGET).set(newer, "{\"v\":2,\"kind\":\"stat\",\"value\":\"24\"}")
        showSources(WidgetSource.script(broken), WidgetSource.script(newer), WidgetSource.BUILTIN + "wcard-nosuch")
        for (name in listOf(broken, newer)) {
            draws(name, name, "widget unavailable")
            says(name, "$name: widget unavailable")
        }
        for (what in listOf("wcard-nosuch")) {
            val card = hasContentDescription("$what: widget unavailable") and hasAnyAncestor(hasTestTag("widget-card"))
            compose.onNode(card).assert(hasText(what) and hasText("widget unavailable"))
        }
    }

    /** Content older than an hour, or than its own `staleAfter`, has its age as the footer. */
    @Test fun oldContentSaysItsAge() {
        val old = kept("old", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        setAgo(old, 125)
        val quick = kept("quick", WidgetSpec.Stat(WidgetBase(title = "Score", staleAfter = 60), "136/8"))
        setAgo(quick, 5)
        val current = kept("current", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        setAgo(current, 40)
        show(old, quick, current)
        draws(old, "Weather", "24", "2 h ago")
        says(old, "Weather: 24. Updated 2 hours ago")
        draws(quick, "Score", "136/8", "5 min ago")
        says(quick, "Score: 136/8. Updated 5 minutes ago")
        draws(current, "Weather", "24")
        says(current, "Weather: 24")
        assertEquals(listOf("Weather", "24"), texts(current))
    }

    /** After a run that failed, the footer says when. What the widget showed stays; with nothing kept, the card has the error's first line. */
    @Test fun aFailedRunSaysWhen() {
        val failed = kept("failed", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        WidgetStates.ran(context, failed, "wcard-failed: no network")
        val empty = name("failed-empty")
        WidgetStates.ran(context, empty, "\n  wcard-failed-empty: no network  \nstack traceback: …")
        show(failed, empty)
        val at = hhmm(WidgetStates.read(context, failed).triedAt)
        draws(failed, "Weather", "24", "Failed $at")
        says(failed, "Weather: 24. Failed at $at")
        val emptyAt = hhmm(WidgetStates.read(context, empty).triedAt)
        draws(empty, empty, "wcard-failed-empty: no network", "Failed $emptyAt")
        says(empty, "$empty: wcard-failed-empty: no network. Failed at $emptyAt")
    }

    /** What a card asks for, in the order asked. */
    private class Asked : WidgetCardActions {
        val all = CopyOnWriteArrayList<String>()
        override fun refresh(script: String) { all += "refresh $script" }
        override fun tap(script: String, tap: WidgetTap) { all += "tap $script ${tap.args ?: tap.url}" }
        override fun longPress() { all += "arrange" }
        override fun runStatus(script: String) { all += "run status $script" }
        override fun refreshStatus(script: String, every: Int): Long { all += "refresh status $script"; return 60_000L }
        override fun openBattery() { all += "battery" }
        override fun openScripts() { all += "scripts" }
    }

    /** On the widgets page a tap on the card is its `tap`, a row's tap is the row's own, and a long-press on either arranges. */
    @Test fun tapsGoToTheCardsActions() {
        val asked = Asked()
        val news = kept(
            "taps",
            WidgetSpec.Rows(
                WidgetBase(title = "News", tap = WidgetTap(args = "more", label = "more news")),
                listOf(WidgetSpec.Row("First", tap = WidgetTap(url = "https://example.com/1", label = "open")), WidgetSpec.Row("Second")),
            ),
        )
        val plain = kept("taps-plain", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        show(news, plain, actions = asked)
        draws(news, "First", "Second")
        draws(plain, "24")

        val row = inCard(news) and hasText("First") and hasClickLabel("open")
        val card = inCard(news) and hasClickLabel("more news")
        // The row is an item of its own inside the card.
        compose.onNode(card).assert(hasText("Second") and hasContentDescription("News: First, Second"))
        compose.onNode(row).performClick()
        // Near its top edge the card has no row.
        compose.onNode(card).performTouchInput { click(Offset(centerX, 6.dp.toPx())) }
        compose.onNode(row).performTouchInput { longClick() }
        compose.onNode(card).performTouchInput { longClick(Offset(centerX, 6.dp.toPx())) }
        // Without a tap of its own, a tap on the card refreshes it.
        compose.onNode(inCard(plain) and hasClickLabel("refresh")).performClick()
        compose.runOnIdle {
            assertEquals(listOf("tap $news https://example.com/1", "tap $news more", "arrange", "arrange", "tap $plain null"), asked.all.toList())
        }
    }

    /** While the widgets page is in view, a card whose content is old asks for one run. Current content asks for none. */
    @Test fun inViewOldContentAsksForOneRun() {
        val asked = Asked()
        val old = kept("due", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        setAgo(old, 125)
        val current = kept("not-due", WidgetSpec.Stat(WidgetBase(title = "Weather"), "25"))
        show(old, current, actions = asked, inView = true)
        draws(old, "24", "2 h ago")
        draws(current, "25")
        compose.waitFor("the old card asked for no run") { asked.all.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("refresh $old"), asked.all.toList())
        assertFalse(asked.all.any { it.endsWith(current) })
    }

    /** What the view model knows of these scripts, and their status lines, as the cards get them. */
    private fun feed(vararg scripts: WidgetScript, lines: MutableStateFlow<List<StatusLine>> = MutableStateFlow(emptyList())) =
        WidgetFeed(MutableStateFlow(scripts.associateBy { it.name }), lines)

    /**
     * A card that knows its script from the view model says when the script can't fill it: it is
     * gone, it is off, or its code has no `@widget` line any more. Such a card asks for no run, also
     * in view, and a tap on it opens the Scripts screen.
     */
    @Test fun aCardSaysWhenItsScriptCantFillIt() {
        val asked = Asked()
        val stat = ScriptMeta.Widget(WidgetKind.STAT, WidgetSize.S2X1)
        val gone = name("gone")
        val off = name("off")
        val bare = name("bare")
        val there = kept("there", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        setAgo(there, 125)
        val known = feed(
            WidgetScript(off, "Off one", enabled = false, widget = stat, setsStatus = false),
            WidgetScript(bare, null, enabled = true, widget = null, setsStatus = false),
            WidgetScript(there, "There", enabled = true, widget = stat, setsStatus = false),
        )
        show(gone, off, bare, there, actions = asked, inView = true, feed = known)
        draws(gone, gone, "Script removed")
        // Without content the card has the script's title.
        draws(off, "Off one", "Script is off")
        draws(bare, bare, "Script has no widget now")
        val scripts = hasClickLabel("open scripts")
        compose.onNode(inCard(gone) and scripts).assert(hasContentDescription("$gone: Script removed"))
        compose.onNode(inCard(off) and scripts).assert(hasContentDescription("Off one: Script is off"))
        compose.onNode(inCard(bare) and scripts).assert(hasContentDescription("$bare: Script has no widget now"))

        // Only the card whose script can run asks, for its old content.
        draws(there, "Weather", "24", "2 h ago")
        compose.waitFor("the card with a script asked for no run") { asked.all.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("refresh $there"), asked.all.toList())

        compose.onNode(inCard(off) and scripts).performClick()
        compose.runOnIdle { assertEquals(listOf("refresh $there", "scripts"), asked.all.toList()) }
    }

    /**
     * A Status card draws its script's line as the view model has it, under the script's title, and
     * follows a new line. A script with no line yet says so. The card reads nothing itself.
     */
    @Test fun aStatusCardDrawsTheLineTheViewModelHas() {
        val set = name("line")
        val none = name("no-line")
        val lines = MutableStateFlow(listOf(StatusLine(set, "NZ 136/8", progress = 0.6f)))
        val known = feed(
            WidgetScript(set, "Cricket", enabled = true, widget = null, setsStatus = true),
            WidgetScript(none, null, enabled = true, widget = null, setsStatus = true),
            lines = lines,
        )
        showSources(WidgetSource.STATUS + set, WidgetSource.STATUS + none, feed = known)
        draws("status:$set", "Cricket", "NZ 136/8")
        says("status:$set", "Cricket: NZ 136/8, 60%")
        draws("status:$none", none, "No status line yet")
        says("status:$none", "$none: No status line yet")

        lines.value = listOf(StatusLine(set, "NZ 140/8"))
        draws("status:$set", "Cricket", "NZ 140/8")
        says("status:$set", "Cricket: NZ 140/8")
    }
}
