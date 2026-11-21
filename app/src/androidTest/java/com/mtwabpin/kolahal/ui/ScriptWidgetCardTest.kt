package com.mtwabpin.kolahal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.terminal.WidgetFeed
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetRef
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * The card of a script's widget, drawn from what is kept for it: each kind's texts, what it says
 * with nothing to draw, its footer, its one description for a screen reader, and its taps.
 */
@RunWith(AndroidJUnit4::class)
class ScriptWidgetCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()

    @After
    fun tearDown() = names.forEach { WidgetStates.forget(context, it) }

    /** A name no script has. What a test keeps for its widget is forgotten after it. */
    private fun name(suffix: String): String = "wcard-$suffix".also {
        names += it
        WidgetStates.forget(context, it)
    }

    /** A name with [spec] kept as its widget's content, set just now. */
    private fun kept(suffix: String, spec: WidgetSpec): String =
        name(suffix).also { store(it, spec) }

    /** Stores [spec] as [name]'s widget and tells its card, as a run does when it ends. */
    private fun store(name: String, spec: WidgetSpec) {
        WidgetStates.putEncoded(context, name, WidgetJson.encode(spec))
        WidgetStates.announce(context, name)
    }

    /** [name]'s content as set [minutes] ago. */
    private fun setAgo(name: String, minutes: Long) {
        val at = System.currentTimeMillis() - minutes * 60_000 - 30_000
        KvStore(context, Stores.WIDGET_META).set(name, WidgetStates.encodeMeta(at, 0, null))
    }

    /**
     * The cards of these scripts' widgets, one under the other, each [width] wide and [height] tall.
     * - Without [actions] they are as the Widgets screen draws them.
     * - With [inView], the widgets page is the page in view.
     * - Without a [feed], each shown script is there, on, with a widget and no title ([scriptsOf]).
     */
    private fun show(
        vararg names: String,
        width: Dp = 320.dp,
        height: Dp = 200.dp,
        actions: WidgetCardActions? = null,
        inView: Boolean = false,
        feed: WidgetFeed? = null
    ) =
        showSources(
            *names.map(WidgetSource::script).toTypedArray(),
            width = width,
            height = height,
            actions = actions,
            inView = inView,
            feed = feed
        )

    private fun showSources(
        vararg sources: String,
        width: Dp = 320.dp,
        height: Dp = 200.dp,
        actions: WidgetCardActions? = null,
        inView: Boolean = false,
        feed: WidgetFeed? = null
    ) {
        val known = feed ?: scriptsOf(*sources)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalWidgetsShown provides inView) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        sources.forEachIndexed { i, source ->
                            Box(Modifier.width(width)) {
                                SourceWidget(
                                    PlacedWidget(
                                        null,
                                        source,
                                        0L,
                                        height.value.toInt(),
                                        i
                                    ), height, actions, known
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun inCard(name: String) = hasAnyAncestor(hasTestTag("widget:$name"))

    private val hasWords = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    private val hasDescription =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    private fun hasClickLabel(label: String?) =
        SemanticsMatcher("click label \"$label\"") {
            it.config.getOrNull(SemanticsActions.OnClick)
                ?.let { click -> click.label == label } == true
        }

    /** Every text [name]'s card draws, each as its own. */
    private fun texts(name: String): List<String> =
        compose.onAllNodes(inCard(name) and hasWords, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    /** Waits until [name]'s card draws each of [expected] as a text of its own. The state is read off the main thread. */
    private fun draws(name: String, vararg expected: String) {
        compose.waitFor("$name's card doesn't draw ${expected.toList()}, it draws ${texts(name)}") {
            texts(
                name
            ).containsAll(expected.toList())
        }
    }

    /**
     * Waits until [name]'s card says [expected] to a screen reader. That is the only description in
     * the card, and the card with all its texts is one item.
     */
    private fun says(name: String, expected: String) {
        fun said() = compose.onAllNodes(inCard(name) and hasDescription, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.ContentDescription] }
        compose.waitFor("$name's card doesn't say “$expected”, it says ${said()}") {
            said() == listOf(
                expected
            )
        }
        compose.onAllNodes(inCard(name) and (hasWords or hasDescription)).assertCountEquals(1)
        compose.onNode(inCard(name) and hasDescription).assertContentDescriptionEquals(expected)
    }

    /** A failure's time of day, as the card writes it: in the clock setting's form. */
    private fun hhmm(millis: Long) =
        com.mtwabpin.kolahal.data.TimeFormats.clockFormat(Config.get(context)[Keys.CLOCK_24H])
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** `stat`: the big value with its unit, the change, the label and the smaller values. */
    @Test
    fun aStatDrawsItsValueAndTheRest() {
        val name = kept(
            "stat",
            WidgetSpec.Stat(
                WidgetBase(title = "Weather", icon = "☀"),
                "24",
                label = "Sunny",
                unit = "°C",
                delta = "2",
                trend = "up",
                more = listOf(WidgetSpec.Small("68%", "humidity"), WidgetSpec.Small("12", "km/h")),
                progress = 0.4f,
            ),
        )
        show(name)
        // The arrow before the change is drawn, not written: the description says "up 2".
        draws(name, "24", "°C", "2", "Sunny", "68%", "Humidity", "12", "km/h")
        says(name, "Weather: 24°C, Sunny, up 2, 68% humidity, 12 km/h, 40%")
        // Current content has no footer.
        assertTrue(
            texts(name).toString(),
            texts(name).none { it.endsWith("ago") || it.startsWith("Failed") })
        // As the Widgets screen draws it, it takes no taps.
        compose.onAllNodes(inCard(name) and hasClickAction()).assertCountEquals(0)
    }

    /**
     * A large card has the script's note at its foot, and a screen reader hears it after the title.
     * No title line: the label says what the number is, so the title isn't drawn.
     */
    @Test
    fun aNoteIsAtTheFootOfALargeCard() {
        val name = kept(
            "note",
            WidgetSpec.Stat(
                WidgetBase(title = "Exercise", note = "Done today"),
                "5",
                label = "days in a row"
            )
        )
        show(name)
        draws(name, "Done today", "5", "Days in a row")
        assertFalse(texts(name).toString(), "Exercise" in texts(name))
        says(name, "Exercise, Done today: 5, Days in a row")
    }

    /** A card of one cell has room for the value and its label only. A screen reader still hears everything. */
    @Test
    fun aSmallCardLeavesPartsOutAndStillSaysThem() {
        val name = kept(
            "small",
            WidgetSpec.Stat(
                WidgetBase(title = "Weather", icon = "☀"),
                "24",
                label = "Sunny",
                unit = "°C",
                delta = "2",
                trend = "up",
                more = listOf(WidgetSpec.Small("68%", "humidity"))
            ),
        )
        setAgo(name, 125)
        show(name, width = 96.dp, height = 96.dp)
        draws(name, "24°C", "Sunny")
        assertEquals(listOf("24°C", "Sunny"), texts(name))
        says(name, "Weather: 24°C, Sunny, up 2, 68% humidity. Updated 2 hours ago")
    }

    /** `text`: its lines and its caption, under the title. Without a title the card has the script's name. */
    @Test
    fun aTextDrawsItsLinesAndCaption() {
        val quote = kept(
            "text",
            WidgetSpec.Text(
                WidgetBase(title = "Quote"),
                "Simple is better than complex.",
                "Tim Peters"
            )
        )
        val plain = kept("text-plain", WidgetSpec.Text(WidgetBase(), "No title here"))
        show(quote, plain)
        draws(quote, "Quote", "Simple is better than complex.", "Tim Peters")
        says(quote, "Quote: Simple is better than complex., Tim Peters")
        draws(plain, plain, "No title here")
        says(plain, "$plain: No title here")
    }

    /** `list`: each row's title, second line, icon and end text; as many rows as fit, then how many more; and its own words when empty. */
    @Test
    fun aListDrawsTheRowsThatFit() {
        val todo = kept(
            "list",
            WidgetSpec.Rows(
                WidgetBase(title = "Todo"),
                listOf(
                    WidgetSpec.Row("Buy milk", detail = "today", trailing = "2", icon = "🛒"),
                    WidgetSpec.Row("Call mum", color = StyledText.Colour.BAD)
                )
            ),
        )
        val long = kept(
            "list-long",
            WidgetSpec.Rows(
                WidgetBase(title = "Stories"),
                (1..12).map { WidgetSpec.Row("Story $it") })
        )
        val empty = kept(
            "list-empty",
            WidgetSpec.Rows(WidgetBase(title = "Inbox"), emptyList(), empty = "All read")
        )
        val bare = kept("list-bare", WidgetSpec.Rows(WidgetBase(title = "Inbox"), emptyList()))
        show(todo, long, empty, bare)
        draws(todo, "Todo", "🛒", "Buy milk", "Today", "2", "Call mum")
        says(todo, "Todo: Buy milk, Today, 2, Call mum")

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
    @Test
    fun aProgressDrawsHowFar() {
        val ring = kept(
            "ring",
            WidgetSpec.Progress(WidgetBase(title = "Battery"), 0.68f, label = "charging")
        )
        val bars = kept(
            "bars",
            WidgetSpec.Progress(
                WidgetBase(title = "Disk"), 0f,
                items = listOf(
                    WidgetSpec.Bar("photos", 30f, 100f, null, null),
                    WidgetSpec.Bar("music", 5f, 10f, "5 GB", StyledText.Colour.WARN)
                ),
            ),
        )
        val bar = kept(
            "bar",
            WidgetSpec.Progress(
                WidgetBase(),
                3f,
                5f,
                text = "3 of 5",
                label = "chapters",
                style = "bar"
            )
        )
        show(ring, bars, bar)
        draws(ring, "68%", "Charging")
        says(ring, "Battery: 68%, Charging")
        draws(bars, "Disk", "Photos", "30%", "Music", "5 GB")
        says(bars, "Disk: Photos 30%, Music 5 GB")
        draws(bar, "3 of 5", "Chapters")
        says(bar, "$bar: 3 of 5, Chapters")
    }

    /** `chart`: its headline and the labels of its first and last point. The plot is said in words. */
    @Test
    fun aChartDrawsItsHeadlineAndLabels() {
        val steps = kept(
            "chart",
            WidgetSpec.Chart(
                WidgetBase(title = "Steps"),
                listOf(1f, 3f, 2f, 5f),
                value = "5,204",
                label = "today",
                labels = listOf("Mon", "Thu")
            ),
        )
        val told = kept(
            "chart-told",
            WidgetSpec.Chart(
                WidgetBase(title = "Rain"),
                listOf(0f, 2.5f),
                style = "bars",
                value = "2.5 mm",
                summary = "More rain than yesterday"
            ),
        )
        show(steps, told)
        draws(steps, "5,204", "Today", "Mon", "Thu")
        says(steps, "Steps: 5,204, Today, 4 values, from 1 to 5, the latest 5")
        draws(told, "Rain", "2.5 mm")
        says(told, "Rain: 2.5 mm, More rain than yesterday")
    }

    /** How many pixels of [name]'s dots ([Dots]) are in the theme's accent: the card's colour. */
    private fun inAccent(name: String): Int {
        val accent = colourOf(StyledText.Colour.ACCENT)
        val pixels =
            compose.onNode(inCard(name) and hasTestTag("widget-dots"), useUnmergedTree = true)
                .captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val p = pixels[x, y]
            if (abs(p.red - accent.red) < 0.08f && abs(p.green - accent.green) < 0.08f && abs(p.blue - accent.blue) < 0.08f) count++
        }
        return count
    }

    /**
     * `chart` with `style = "dots"`: a dot for each day in the card's colour, and the days in words
     * for a screen reader. Once today is done, today's ring becomes a filled cookie: more of the
     * dots are in the card's colour.
     */
    @Test
    fun aChartOfDotsDrawsADotForEachDay() {
        // Five weeks of a habit with every fifth day missed, and today still open.
        val days = List(34) { if (it % 5 == 2) 0f else 1f } + 0f
        val open = WidgetSpec.Chart(
            WidgetBase(title = "Exercise"),
            days,
            style = "dots",
            value = "4",
            label = "days in a row"
        )
        val habit = kept("dots", open)
        show(habit)
        draws(habit, "4", "Days in a row")
        says(habit, "Exercise: 4, Days in a row, Done on 27 of the last 35 days")
        val before = inAccent(habit)
        assertTrue("no dots in the card's colour", before > 0)

        store(
            habit,
            open.copy(
                base = WidgetBase(title = "Exercise", note = "Done today"),
                points = days.dropLast(1) + 1f,
                value = "5"
            )
        )
        says(habit, "Exercise, Done today: 5, Days in a row, Done on 28 of the last 35 days")
        val after = inAccent(habit)
        assertTrue("today's dot didn't fill: $before pixels, then $after", after > before)
    }

    /** Right to left, the dots are the same drawing turned round: the week starts at the right. */
    @Test
    fun rightToLeftTheWeekStartsAtTheRight() {
        // Today still open, so every mark is round: the drawing turned round is the same.
        val name = kept(
            "dots-rtl",
            WidgetSpec.Chart(
                WidgetBase(title = "Exercise"),
                List(34) { if (it % 4 == 1) 0f else 1f } + 0f,
                style = "dots",
                value = "2"))
        val source = WidgetSource.script(name)
        val known = scriptsOf(source)
        compose.setContent {
            KolahalTheme {
                Column {
                    for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            Box(Modifier.width(320.dp)) {
                                SourceWidget(
                                    PlacedWidget(
                                        null,
                                        source,
                                        0L,
                                        200,
                                        direction.ordinal
                                    ), 200.dp, null, known
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitFor("two cards of dots") {
            compose.onAllNodes(
                hasTestTag("widget-dots"),
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 2
        }
        val (ltr, rtl) = compose.onAllNodes(hasTestTag("widget-dots"), useUnmergedTree = true)
            .let { listOf(it[0], it[1]) }.map { it.captureToImage().toPixelMap() }
        assertEquals(ltr.width, rtl.width)
        var differ = 0
        for (x in 0 until ltr.width) for (y in 0 until ltr.height) {
            val a = ltr[x, y]
            val b = rtl[ltr.width - 1 - x, y]
            if (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.3f) differ++
        }
        assertTrue(
            "$differ of ${ltr.width * ltr.height} pixels differ from the drawing turned round",
            differ < ltr.width * ltr.height / 200
        )
    }

    /** At twice the font size a 2×1 card still has room for its dots: the value goes beside them, not over them. */
    @Test
    fun largeTextLeavesRoomForTheDots() {
        val name = kept(
            "dots-large",
            WidgetSpec.Chart(
                WidgetBase(title = "Exercise"),
                List(35) { if (it % 3 == 0) 0f else 1f },
                style = "dots",
                value = "23",
                label = "days in a row"
            )
        )
        val source = WidgetSource.script(name)
        val known = scriptsOf(source)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density,
                        fontScale = 2f
                    )
                ) {
                    Box(Modifier.width(190.dp)) {
                        SourceWidget(
                            PlacedWidget(
                                null,
                                source,
                                0L,
                                92,
                                0
                            ), 92.dp, null, known
                        )
                    }
                }
            }
        }
        val least = with(compose.density) { 12.dp.toPx() }
        // Seven days of at least 12 dp each, and as tall as one: the words give way, not the dots.
        compose.waitFor("no room for the dots at twice the font size") {
            compose.onAllNodes(hasTestTag("widget-dots"), useUnmergedTree = true)
                .fetchSemanticsNodes().singleOrNull()
                ?.let { it.size.height >= least && it.size.width >= 7 * least } == true
        }
    }

    /** Each look draws the dots where its chart goes: in a large card, and in a 2×1 one. */
    @Test
    fun everyLookDrawsTheDots() {
        val name = kept(
            "dots-looks",
            WidgetSpec.Chart(
                WidgetBase(title = "Exercise"),
                List(35) { if (it % 3 == 0) 0f else 1f },
                style = "dots",
                value = "23",
                label = "days in a row"
            )
        )
        val source = WidgetSource.script(name)
        val known = scriptsOf(source)
        val sizes = listOf(360.dp to 190.dp, 190.dp to 92.dp)
        compose.setContent {
            KolahalTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    WidgetLook.entries.forEachIndexed { i, look ->
                        sizes.forEachIndexed { j, (width, height) ->
                            Box(Modifier.width(width)) {
                                SourceWidget(
                                    PlacedWidget(
                                        null,
                                        source,
                                        0L,
                                        height.value.toInt(),
                                        i * sizes.size + j,
                                        look = look
                                    ), height, null, known
                                )
                            }
                        }
                    }
                }
            }
        }
        val cards = WidgetLook.entries.size * sizes.size
        compose.waitFor("not every look drew the dots") {
            compose.onAllNodes(hasTestTag("widget-dots"), useUnmergedTree = true)
                .fetchSemanticsNodes().size == cards
        }
    }

    /** `countdown`: the days to the moment, its own words on the day, and the days since. */
    @Test
    fun aCountdownCountsTheDays() {
        val now = System.currentTimeMillis() / 1000
        val day = 86_400L
        val trip = kept(
            "countdown",
            WidgetSpec.Countdown(
                WidgetBase(title = "Trip"),
                to = now + 3 * day + 3_600,
                label = "to Goa",
                style = "days"
            )
        )
        val reached = kept(
            "countdown-reached",
            WidgetSpec.Countdown(WidgetBase(title = "Launch"), to = now - 1, reached = "Lift-off")
        )
        val past = kept(
            "countdown-past",
            WidgetSpec.Countdown(
                WidgetBase(title = "Since"),
                to = now - 5 * day - 3_600,
                label = "the move"
            )
        )
        show(trip, reached, past)
        // The number and its unit are two texts: the number big, the unit small.
        draws(trip, "3", "days", "To Goa")
        says(trip, "Trip: 3 days, To Goa")
        draws(reached, "Launch", "Lift-off")
        says(reached, "Launch: Lift-off")
        draws(past, "5", "days ago", "The move")
        says(past, "Since: 5 days ago, The move")
    }

    /** A widget that was cleared, or whose run showed nothing, says so. One that no run has filled yet says nothing: it loads. */
    @Test
    fun withNothingKeptItSaysSo() {
        val cleared = kept("cleared", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        WidgetStates.clear(context, cleared)
        WidgetStates.announce(context, cleared)
        val ran = name("ran")
        WidgetStates.ran(context, ran, null)
        WidgetStates.announce(context, ran)
        val new = name("new")
        show(cleared, ran, new)
        // Without content the card has the script's name: the title was part of what it showed.
        draws(cleared, cleared, "Nothing to show")
        says(cleared, "$cleared: Nothing to show")
        draws(ran, ran, "Nothing to show")
        says(ran, "$ran: Nothing to show")
        says(new, "$new: loading")
        // The card's name alone while it loads.
        assertEquals(listOf(new), texts(new))
    }

    /** Content that can't be read, and a built-in widget this version doesn't know, say "Widget unavailable". */
    @Test
    fun whatCantBeReadIsUnavailable() {
        // Set just now, as `widget.show` leaves it, and then not what Kolahal reads: another kind, another format.
        val broken = kept("broken", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        KvStore(context, Stores.WIDGET).set(broken, "{\"v\":1,\"kind\":\"gauge\",\"value\":\"24\"}")
        val newer = kept("newer", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        KvStore(context, Stores.WIDGET).set(newer, "{\"v\":2,\"kind\":\"stat\",\"value\":\"24\"}")
        showSources(
            WidgetSource.script(broken),
            WidgetSource.script(newer),
            WidgetSource.BUILTIN + "wcard-nosuch"
        )
        for (name in listOf(broken, newer)) {
            draws(name, name, "Widget unavailable")
            says(name, "$name: ${WidgetWords.UNAVAILABLE}")
        }
        for (what in listOf("wcard-nosuch")) {
            val card =
                hasContentDescription("$what: ${WidgetWords.UNAVAILABLE}") and hasAnyAncestor(
                    hasTestTag("widget-card")
                )
            compose.onNode(card).assert(hasText(what) and hasText("Widget unavailable"))
        }
    }

    /** Content older than an hour, or than its own `staleAfter`, has its age as the footer. */
    @Test
    fun oldContentSaysItsAge() {
        val old = kept("old", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        setAgo(old, 125)
        val quick =
            kept("quick", WidgetSpec.Stat(WidgetBase(title = "Score", staleAfter = 60), "136/8"))
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
        // The value, and the title as its label, as it has none: no age.
        assertEquals(listOf("24", "Weather"), texts(current))
    }

    /** After a run that failed, the footer says when. What the widget showed stays; with nothing kept, the card has the error's first line. */
    @Test
    fun aFailedRunSaysWhen() {
        val failed = kept("failed", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        WidgetStates.ran(context, failed, "wcard-failed: no network")
        WidgetStates.announce(context, failed)
        val empty = name("failed-empty")
        WidgetStates.ran(context, empty, "\n  wcard-failed-empty: no network  \nstack traceback: …")
        WidgetStates.announce(context, empty)
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
        override fun refresh(widget: String) {
            all += "refresh $widget"
        }

        override fun tap(widget: String, tap: WidgetTap) {
            all += "tap $widget ${tap.args ?: tap.url}"
        }

        override fun longPress() {
            all += "arrange"
        }

        override fun openBattery() {
            all += "battery"
        }

        override fun openScripts() {
            all += "scripts"
        }
    }

    /** On the widgets page a tap on the card is its `tap`, a row's tap is the row's own, and a long-press on either arranges. */
    @Test
    fun tapsGoToTheCardsActions() {
        val asked = Asked()
        val news = kept(
            "taps",
            WidgetSpec.Rows(
                WidgetBase(title = "News", tap = WidgetTap(args = "more", label = "more news")),
                listOf(
                    WidgetSpec.Row(
                        "First",
                        tap = WidgetTap(url = "https://example.com/1", label = "open")
                    ), WidgetSpec.Row("Second")
                ),
            ),
        )
        val plain = kept("taps-plain", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        show(news, plain, actions = asked)
        draws(news, "First", "Second")
        draws(plain, "24")

        val row = inCard(news) and hasText("First") and hasClickLabel("open")
        val card = inCard(news) and hasClickLabel("more news")
        // The row is an item of its own inside the card.
        compose.onNode(card)
            .assert(hasText("Second") and hasContentDescription("News: First, Second"))
        compose.onNode(row).performClick()
        // Near its top edge the card has no row.
        compose.onNode(card).performTouchInput { click(Offset(centerX, 6.dp.toPx())) }
        compose.onNode(row).performTouchInput { longClick() }
        compose.onNode(card).performTouchInput { longClick(Offset(centerX, 6.dp.toPx())) }
        // Without a tap of its own, a tap on the card refreshes it.
        compose.onNode(inCard(plain) and hasClickLabel("refresh")).performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    "tap $news https://example.com/1",
                    "tap $news more",
                    "arrange",
                    "arrange",
                    "tap $plain null"
                ), asked.all.toList()
            )
        }
    }

    /** While the widgets page is in view, a card whose content is old asks for one run. Current content asks for none. */
    @Test
    fun inViewOldContentAsksForOneRun() {
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

    /**
     * `refresh` runs only for a card on screen. The page scrolls: a card below the fold waits, and
     * runs as soon as it is scrolled into view, since its time has passed.
     */
    @Test
    fun aCardBelowTheFoldWaitsForItsRefresh() {
        val asked = Asked()
        val top = kept("top", WidgetSpec.Stat(WidgetBase(refresh = 10), "1"))
        val below = kept("below", WidgetSpec.Stat(WidgetBase(refresh = 10), "2"))
        // Set a minute ago: not old, but its refresh time has passed.
        setAgo(top, 1)
        setAgo(below, 1)
        val feed = scriptsOf(WidgetSource.script(top), WidgetSource.script(below))
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalWidgetsShown provides true) {
                    Column(Modifier
                        .verticalScroll(rememberScrollState())
                        .testTag("page")) {
                        listOf(top, below).forEachIndexed { i, name ->
                            OnScreenCard {
                                Box(Modifier.width(320.dp)) {
                                    SourceWidget(
                                        PlacedWidget(
                                            null,
                                            WidgetSource.script(name),
                                            0L,
                                            200,
                                            i
                                        ), 200.dp, asked, feed
                                    )
                                }
                            }
                            // Far more than a screen between them.
                            if (i == 0) Spacer(Modifier.height(4000.dp))
                        }
                    }
                }
            }
        }
        compose.waitFor("the card on screen asked for no run") { asked.all.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("refresh $top"), asked.all.toList())
        compose.onNode(hasTestTag("widget:$below")).performScrollTo()
        compose.waitFor("the card scrolled into view asked for no run") { "refresh $below" in asked.all }
    }

    /**
     * A script can have several widgets on the page. Each card is one widget: it draws what is kept
     * under its own key (`name`, `name#2`), and asks for its own runs and taps by that key. Only
     * this gives a further widget its runs.
     */
    @Test
    fun eachWidgetOfAScriptHasItsOwnCard() {
        val asked = Asked()
        val first = kept("two", WidgetSpec.Stat(WidgetBase(), "London", label = "first"))

        // A further widget's key has its id after the script's name.
        fun further(id: String) =
            WidgetRef(first, id).key.also { names += it; WidgetStates.forget(context, it) }

        val second = further("2")
        val third = further("3")
        assertEquals("$first#2", second)
        store(
            second,
            WidgetSpec.Stat(
                WidgetBase(tap = WidgetTap(args = "go", label = "go on")),
                "Tokyo",
                label = "second"
            )
        )
        // The third has nothing kept: its card is the one that asks for a run.
        showSources(
            "script:$first",
            "script:$second",
            "script:$third",
            actions = asked,
            inView = true
        )

        draws(first, "London", "First")
        draws(second, "Tokyo", "Second")
        // With no title of its own, a card has its script's name. A further widget's has its id too.
        compose.waitFor("$first's card says ${said(first)}") { said(first).startsWith("$first: ") }
        compose.waitFor("$second's card says ${said(second, "go on")}") {
            said(
                second,
                "go on"
            ).startsWith("$first 2: ")
        }
        compose.onNode(hasTestTag("widget:$third")).assertExists()
        // The first widget's card does not draw the second one's content, and the other way round.
        assertFalse(texts(first).toString(), texts(first).contains("Tokyo"))
        assertFalse(texts(second).toString(), texts(second).contains("London"))

        compose.waitFor("the empty card asked for no run") { asked.all.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("refresh $third"), asked.all.toList())
        // A tap is for the card's own widget: the second's own action, and a refresh of the first.
        compose.onNode(inCard(second) and hasClickLabel("go on")).performClick()
        compose.onNode(inCard(first) and hasClickLabel("refresh")).performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    "refresh $third",
                    "tap $second go",
                    "tap $first null"
                ), asked.all.toList()
            )
        }
    }

    /** What the view model knows of these scripts, as the cards get it. */
    private fun feed(vararg scripts: WidgetScript) =
        WidgetFeed(MutableStateFlow(scripts.associateBy { it.name }))

    /** A feed where the script of each of [sources] is there, on, with a widget and no title. Built-in sources have none. */
    private fun scriptsOf(vararg sources: String) = feed(
        *sources.mapNotNull { WidgetSource.scriptRef(it)?.script }.distinct()
            .map {
                WidgetScript(
                    it,
                    null,
                    enabled = true,
                    widget = ScriptMeta.Widget(WidgetKind.STAT, WidgetSize.S2X1)
                )
            }.toTypedArray(),
    )

    /**
     * A card that knows its script from the view model says when the script can't fill it: it is
     * gone, it is off, or its code has no `@widget` line any more. Such a card asks for no run, also
     * in view. A tap on it opens the Scripts screen, or the Widgets screen when the script is gone.
     */
    @Test
    fun aCardSaysWhenItsScriptCantFillIt() {
        val asked = Asked()
        val stat = ScriptMeta.Widget(WidgetKind.STAT, WidgetSize.S2X1)
        val gone = name("gone")
        val off = name("off")
        val bare = name("bare")
        val there = kept("there", WidgetSpec.Stat(WidgetBase(title = "Weather"), "24"))
        setAgo(there, 125)
        val known = feed(
            WidgetScript(off, "Off one", enabled = false, widget = stat),
            WidgetScript(bare, null, enabled = true, widget = null),
            WidgetScript(there, "There", enabled = true, widget = stat),
        )
        show(gone, off, bare, there, actions = asked, inView = true, feed = known)
        draws(gone, gone, "Script removed")
        // Without content the card has the script's title.
        draws(off, "Off one", "Script is off")
        draws(bare, bare, "Script has no widget now")
        val scripts = hasClickLabel("open scripts")
        val arrange = hasClickLabel("arrange widgets")
        compose.onNode(inCard(gone) and arrange)
            .assert(hasContentDescription("$gone: Script removed"))
        compose.onNode(inCard(off) and scripts)
            .assert(hasContentDescription("Off one: Script is off"))
        compose.onNode(inCard(bare) and scripts)
            .assert(hasContentDescription("$bare: Script has no widget now"))

        // Only the card whose script can run asks, for its old content.
        draws(there, "Weather", "24", "2 h ago")
        compose.waitFor("the card with a script asked for no run") { asked.all.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("refresh $there"), asked.all.toList())

        // Off: the Scripts screen switches it on. Gone: the Widgets screen removes the widget.
        compose.onNode(inCard(off) and scripts).performClick()
        compose.onNode(inCard(gone) and arrange).performClick()
        compose.runOnIdle {
            assertEquals(
                listOf("refresh $there", "scripts", "arrange"),
                asked.all.toList()
            )
        }
    }

    /** The card's own node on the widgets page: the one that takes the card's tap, with [label] for a screen reader. */
    private fun cardOf(name: String, label: String = "refresh") =
        inCard(name) and hasClickLabel(label)

    private fun isCheckbox(ticked: Boolean) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    if (ticked) ToggleableState.On else ToggleableState.Off
                )

    /**
     * A row with `done` has a checkbox, ticked or not, as the description says. A tap on the row is
     * the row's tap. Kolahal doesn't tick the box itself: the script shows the new state.
     */
    @Test
    fun aRowWithDoneHasACheckbox() {
        val asked = Asked()
        fun todos(milk: Boolean) = WidgetSpec.Rows(
            WidgetBase(title = "Todo"),
            listOf(
                WidgetSpec.Row(
                    "Buy milk",
                    done = milk,
                    tap = WidgetTap(args = "done 1", label = "mark as done")
                ),
                WidgetSpec.Row("Call mum", done = true, tap = WidgetTap(args = "undo 2")),
                WidgetSpec.Row("No box", tap = WidgetTap(args = "open 3")),
            ),
        )

        val todo = kept("done", todos(milk = false))
        // Tall enough for three rows: a row with a tap is 48 dp.
        show(todo, height = 280.dp, actions = asked)
        draws(todo, "Todo", "Buy milk", "Call mum", "No box")
        val milk = inCard(todo) and hasText("Buy milk")
        compose.onNode(milk and isCheckbox(ticked = false)).assertExists()
        compose.onNode(inCard(todo) and hasText("Call mum") and isCheckbox(ticked = true))
            .assertExists()
        // A row without `done` is no checkbox.
        compose.onAllNodes(inCard(todo) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .assertCountEquals(2)
        // The card says each box's state.
        compose.onNode(cardOf(todo))
            .assertContentDescriptionEquals("Todo: Buy milk, not done, Call mum, done, No box")

        compose.onNode(milk and hasClickLabel("mark as done")).performClick()
        compose.runOnIdle { assertEquals(listOf("tap $todo done 1"), asked.all.toList()) }
        // The box is as the description says, until the script shows a new one.
        compose.onNode(milk and isCheckbox(ticked = false)).assertExists()
        store(todo, todos(milk = true))
        compose.waitFor("the row isn't ticked after the script showed it done") {
            compose.onAllNodes(milk and isCheckbox(ticked = true)).fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** Three buttons: one whose tap has its own words for a screen reader, one in a colour, and one that opens a link. */
    private val timerButtons = WidgetSpec.Buttons(
        WidgetBase(title = "Timer"),
        listOf(
            WidgetSpec.Button("Start", WidgetTap(args = "start", label = "start the timer")),
            WidgetSpec.Button("Stop", WidgetTap(args = "stop 1"), color = StyledText.Colour.BAD),
            WidgetSpec.Button("Help", WidgetTap(url = "https://example.com/help")),
        ),
    )

    /**
     * `buttons`: each button with its label. A tap on a button is the button's own action. For a
     * screen reader each button is an item of its own, and the card names them.
     */
    @Test
    fun buttonsAreDrawnAndTapped() {
        val asked = Asked()
        val timer = kept("buttons", timerButtons)
        show(timer, actions = asked)
        draws(timer, "Start", "Stop", "Help")
        compose.onNode(cardOf(timer)).assertContentDescriptionEquals("Timer: Start, Stop, Help")
        compose.onNode(inCard(timer) and hasText("Start") and hasClickLabel("start the timer"))
            .performClick()
        compose.onNode(inCard(timer) and hasText("Stop") and hasClickAction()).performClick()
        compose.onNode(inCard(timer) and hasText("Help") and hasClickAction()).performClick()
        // A long-press on a button arranges, as one on the card does, and is no tap on the button.
        compose.onNode(inCard(timer) and hasText("Stop") and hasClickAction())
            .performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(
                listOf(
                    "tap $timer start",
                    "tap $timer stop 1",
                    "tap $timer https://example.com/help",
                    "arrange"
                ), asked.all.toList()
            )
        }
    }

    /** As the Widgets screen draws a `buttons` card, the buttons are there and take no taps. */
    @Test
    fun buttonsTakeNoTapsWhileArranging() {
        val timer = kept("buttons-still", timerButtons)
        show(timer)
        draws(timer, "Start", "Stop", "Help")
        compose.onAllNodes(inCard(timer) and hasClickAction() and isEnabled()).assertCountEquals(0)
    }

    /**
     * `toggles`: each switch with its label and the state the script reported. A tap hands the
     * switch's action on, and the switch says "working" until the script's run ends: the card's
     * stamp moves then, also when the run reports the same state as before. Kolahal never guesses
     * the new state.
     */
    @Test
    fun aSwitchWaitsForItsScript() {
        val asked = Asked()
        fun switches(dnd: Boolean, work: Boolean) = WidgetSpec.Toggles(
            WidgetBase(title = "Quick"),
            listOf(
                WidgetSpec.Toggle("Do Not Disturb", on = dnd, tap = WidgetTap(args = "dnd")),
                WidgetSpec.Toggle(
                    "Work",
                    on = work,
                    tap = WidgetTap(args = "work", label = "pause work")
                ),
                WidgetSpec.Toggle("Torch", on = false, tap = WidgetTap(args = "torch")),
            ),
        )

        val quick = kept("toggles", switches(dnd = false, work = true))
        show(quick, actions = asked)
        draws(quick, "Do Not Disturb", "Work", "Torch")
        fun switch(label: String) =
            inCard(quick) and hasText(label) and SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Switch
            )

        fun isOn(on: Boolean) = SemanticsMatcher.expectValue(
            SemanticsProperties.ToggleableState,
            if (on) ToggleableState.On else ToggleableState.Off
        )

        val working = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "working")
        fun waitsNoMore(label: String, on: Boolean) =
            compose.waitFor("$label isn't ${if (on) "on" else "off"} and done waiting") {
                compose.onAllNodes(switch(label) and isOn(on) and !working).fetchSemanticsNodes()
                    .isNotEmpty()
            }

        val dnd = switch("Do Not Disturb")
        val work = switch("Work")
        compose.onNode(dnd).assert(isOn(false) and !working)
        compose.onNode(work).assert(isOn(true) and !working and hasClickLabel("pause work"))
        // Each switch is an item of its own, and the card names them with their state.
        assertEquals("Quick: Do Not Disturb off, Work on, Torch off", said(quick))

        compose.onNode(dnd).performClick()
        compose.runOnIdle { assertEquals(listOf("tap $quick dnd"), asked.all.toList()) }
        // It waits, in the state it had. The other switch doesn't wait.
        compose.onNode(dnd).assert(working and isOn(false))
        compose.onNode(work).assert(!working)
        // A second tap while it waits starts nothing.
        compose.onNode(dnd).performClick()
        compose.runOnIdle { assertEquals(1, asked.all.size) }

        // The run ends with the state as it was: the change didn't work. The same description is
        // kept, only its time moves, and that is enough for the switch.
        store(quick, switches(dnd = false, work = true))
        waitsNoMore("Do Not Disturb", on = false)

        // The other switch, and a run that reports a new state.
        compose.onNode(work).performClick()
        compose.onNode(work).assert(working and isOn(true))
        store(quick, switches(dnd = false, work = false))
        waitsNoMore("Work", on = false)
        assertEquals("Quick: Do Not Disturb off, Work off, Torch off", said(quick))

        // A run whose end is noted for the card ends the wait too, also when it failed and showed
        // nothing new: the stamp is the later of the content's time and that run's. The card says
        // that it failed. (A tap's own run is noted so, and a run the widgets page asked for.)
        val torch = switch("Torch")
        compose.onNode(torch).performClick()
        compose.onNode(torch).assert(working and isOn(false))
        WidgetStates.ran(context, quick, "wcard-toggles: no torch")
        WidgetStates.announce(context, quick)
        waitsNoMore("Torch", on = false)
        draws(quick, "Failed ${hhmm(WidgetStates.read(context, quick).triedAt)}")
        compose.runOnIdle {
            assertEquals(
                listOf(
                    "tap $quick dnd",
                    "tap $quick work",
                    "tap $quick torch"
                ), asked.all.toList()
            )
        }
    }

    /** What the card's own node says to a screen reader, once it says anything. */
    private fun said(name: String, label: String = "refresh"): String {
        fun now() = compose.onAllNodes(cardOf(name, label)).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        compose.waitFor("$name's card says nothing") { now().isNotEmpty() }
        return now().single()
    }

    /** A time of day as Kolahal writes it: in 24-hour or 12-hour form, as its own setting says. */
    private val clock = DateTimeFormatter.ofPattern(
        TimeFormats.clock(Config.get(context)[Keys.CLOCK_24H]),
        Locale.getDefault()
    )

    /**
     * `clock`: each place with its name and its time there. Kolahal writes the time: no run of the
     * script is needed for it. A place without a label has the last part of its zone's name.
     */
    @Test
    fun aClockShowsTheTimeInEachPlace() {
        val asked = Asked()
        val world = kept(
            "clock",
            WidgetSpec.Clock(
                WidgetBase(title = "World"),
                listOf(
                    WidgetSpec.Zone("Asia/Kolkata", "Pune"),
                    WidgetSpec.Zone("America/New_York"),
                    WidgetSpec.Zone("UTC")
                )
            ),
        )

        // The time in a zone, at this minute, the one before and the one after: the minute may turn
        // between the card's drawing and the check, either way round.
        fun times(zone: String) = listOf(-60L, 0L, 60L).map {
            Instant.now().plusSeconds(it).atZone(ZoneId.of(zone)).format(clock)
        }
        // Tall enough for a row for each place.
        show(world, height = 240.dp, actions = asked)
        draws(world, "Pune", "New York", "UTC")
        for (zone in listOf("Asia/Kolkata", "America/New_York", "UTC")) {
            assertTrue(
                "${texts(world)} has none of ${times(zone)}",
                texts(world).any { it in times(zone) })
        }
        // A screen reader hears each place with its time, in one item with the card.
        val heard = said(world)
        assertTrue(
            heard,
            heard.startsWith("World: ") && times("Asia/Kolkata").any { "Pune $it" in heard } && times(
                "UTC"
            ).any { "UTC $it" in heard })
        // The content has no tap of its own: a tap on the card refreshes it.
        compose.onNode(cardOf(world)).performClick()
        compose.runOnIdle { assertEquals(listOf("tap $world null"), asked.all.toList()) }
    }

    /**
     * A 4×2 clock: under each place its difference from the phone's time, and a strip of the day
     * for each place. A tap on a strip moves every place's time to that hour, and the line under the
     * strips then has the hour and a "Now" button that brings back now. A second tap on the line does that too.
     * None of these taps is the card's own. A screen reader hears each difference in whole words.
     */
    @Test
    fun aWideClockShowsDifferencesAndDayStrips() {
        val asked = Asked()
        val here = ZoneId.systemDefault()
        val zones = listOf(
            WidgetSpec.Zone(here.id, "Home"),
            WidgetSpec.Zone("Asia/Kathmandu"),
            WidgetSpec.Zone("America/New_York")
        )
        val world = kept("clock-strips", WidgetSpec.Clock(WidgetBase(title = "World"), zones))
        // A 4×2 card: two rows on a page 380 dp wide.
        show(world, width = 380.dp, height = 192.dp, actions = asked, inView = true)
        draws(world, "Home", "Kathmandu", "New York", "Here")
        // Each place's words, in the forms the card picks from: it writes the longest that fits whole.
        // The minute may turn between the drawing and the check, either way round.
        fun places(at: Long) = WidgetWords.zoneTimes(zones, at, here, clock)
        fun around(): List<List<WidgetWords.ZoneTime>> =
            listOf(-60_000L, 0L, 60_000L).map { places(System.currentTimeMillis() + it) }
        for (i in 1..2) {
            val forms = around().flatMap { WidgetWords.placeLines(it[i]) }
            assertTrue("${texts(world)} has none of $forms", texts(world).any { it in forms })
        }
        val heard = said(world)
        for (i in 1..2) {
            val words = around().map { WidgetWords.differenceSaid(it[i].ahead, it[i].here) }
            assertTrue("“$heard” has none of $words", words.any { ", $it" in heard })
        }
        assertTrue(heard, around().any { "Home ${it[0].time}, here" in heard })

        // A tap on the strips at an hour far from now: every place's time is at that hour of today here.
        val rtl =
            compose.activity.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        val hour = ((java.time.LocalTime.now(here).hour + 12) % 24).coerceIn(1, 23)
        val strips =
            compose.onNode(inCard(world) and hasTestTag("clock-strips"), useUnmergedTree = true)

        fun tapHour() = strips.performTouchInput {
            click(
                Offset(
                    width * (if (rtl) 24 - hour else hour) / 24f,
                    height / 2f
                )
            )
        }

        fun timesAt(at: Long) = places(at).map { it.time }
        tapHour()
        val picked = WidgetWords.hourMoment(LocalDate.now(here), hour, here)
        draws(
            world,
            *timesAt(picked).toTypedArray(),
            "At ${Instant.ofEpochMilli(picked).atZone(here).format(clock)}",
            "Now"
        )
        // "Now" under the strips brings back now, and the button goes.
        fun backToNow() {
            compose.waitFor("$world's card still has its “Now” button") {
                compose.onAllNodes(
                    inCard(
                        world
                    ) and hasText("Now")
                ).fetchSemanticsNodes().isEmpty()
            }
            compose.waitFor("${texts(world)} isn't now") {
                around().any {
                    texts(world).containsAll(
                        it.map { p -> p.time })
                }
            }
        }
        compose.onNode(inCard(world) and hasText("Now") and hasClickAction()).performClick()
        backToNow()
        // A tap on the line where it stands brings back now too.
        tapHour()
        draws(world, *timesAt(picked).toTypedArray(), "Now")
        tapHour()
        backToNow()
        // The strips and the button took their taps: none of them ran the script.
        compose.runOnIdle {
            assertEquals(
                emptyList<String>(),
                asked.all.filter { it.startsWith("tap") })
        }
    }

    /**
     * `agenda`: the events under "Today" and "Tomorrow", each with its time. Kolahal leaves out an
     * event that has ended and one past the description's `days`, with no run of the script. A tap
     * on an event is the event's own.
     */
    @Test
    fun anAgendaShowsItsEventsByDay() {
        val asked = Asked()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(day: Long, hour: Int, minute: Int = 0) =
            today.plusDays(day).atTime(hour, minute).atZone(zone).toEpochSecond()

        val plans = kept(
            "agenda",
            WidgetSpec.Agenda(
                WidgetBase(title = "Plans"),
                listOf(
                    WidgetSpec.Event(
                        "Standup",
                        starts = at(1, 9, 30),
                        ends = at(1, 10),
                        tap = WidgetTap(args = "open 7", label = "open event")
                    ),
                    WidgetSpec.Event("Holiday", starts = at(0, 0), ends = at(1, 0), allDay = true),
                    WidgetSpec.Event("Ended", starts = at(-1, 10), ends = at(-1, 11)),
                    WidgetSpec.Event("Too far", starts = at(2, 12)),
                ),
                days = 2,
            ),
        )
        val quiet = kept(
            "agenda-empty",
            WidgetSpec.Agenda(
                WidgetBase(title = "Plans"),
                listOf(WidgetSpec.Event("Ended", starts = at(-1, 10), ends = at(-1, 11))),
                empty = "Nothing planned"
            )
        )
        // Tall enough for two days, each with its heading and an event.
        show(plans, quiet, height = 280.dp, actions = asked)
        val standup = today.plusDays(1).atTime(9, 30).format(clock)
        // The day headings are in capitals.
        draws(plans, "TODAY", "all day", "Holiday", "TOMORROW", standup, "Standup")
        assertEquals(emptyList<String>(), texts(plans).filter { it == "Ended" || it == "Too far" })
        assertEquals("Plans: Today, all day Holiday, Tomorrow, $standup Standup", said(plans))
        compose.onNode(inCard(plans) and hasText("Standup") and hasClickLabel("open event"))
            .performClick()
        compose.runOnIdle { assertEquals(listOf("tap $plans open 7"), asked.all.toList()) }
        // With every event over, it says its own words for that.
        draws(quiet, "Nothing planned")
        assertEquals("Plans: Nothing planned", said(quiet))
    }

    /**
     * `media`: the kind only Kolahal fills, so nothing is kept for it and the card is drawn here as
     * the Now playing widget draws it. The track and the artist with the app; three buttons hand
     * `prev`, `toggle` and `next` to the card's `onTap`; a tap beside them is the card's own.
     */
    @Test
    fun aMediaCardHasTheTrackAndThreeButtons() {
        val taps = CopyOnWriteArrayList<String>()
        val track = WidgetSpec.Media(
            WidgetBase(title = "Now playing"),
            title = "Blue in Green",
            artist = "Miles Davis",
            app = "Player",
            playing = true,
            position = 61_000,
            positionAt = System.currentTimeMillis(),
            speed = 0f,
            duration = 337_000,
        )
        compose.setContent {
            KolahalTheme {
                Box(Modifier.width(320.dp)) {
                    WidgetCard(
                        title = "Now playing",
                        height = 260.dp,
                        spec = track,
                        note = null,
                        description = "Now playing: Blue in Green",
                        modifier = Modifier.testTag("widget:media"),
                        onClick = { taps += "card" },
                        onClickLabel = "open Player",
                        onLongClick = { taps += "arrange" },
                        onTap = { taps += it.args.orEmpty() },
                    )
                }
            }
        }
        draws("media", "Blue in Green", "Miles Davis · Player", "1:01", "5:37")
        // It plays, so the middle button pauses.
        for (button in listOf(
            "Previous",
            "Pause",
            "Next"
        )) compose.onNode(inCard("media") and hasContentDescription(button) and hasClickAction())
            .performClick()
        compose.onAllNodes(inCard("media") and hasContentDescription("Play")).assertCountEquals(0)
        compose.onNode(cardOf("media", "open Player"))
            .performTouchInput { click(Offset(centerX, 6.dp.toPx())) }
        compose.runOnIdle { assertEquals(listOf("prev", "toggle", "next", "card"), taps.toList()) }
    }
}
