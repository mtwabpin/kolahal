package com.mtwabpin.kolahal.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.notifications.NotifIcons
import com.mtwabpin.kolahal.terminal.Gutter
import com.mtwabpin.kolahal.terminal.LogRow
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val AT = LocalDate.of(2026, 3, 2).atTime(9, 41).toInstant(ZoneOffset.UTC).toEpochMilli()
private const val TIME = "09:41"
private val TIMES = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private val INDENTS = LineIndents(16.sp, 16.sp, 16.sp)

/** One log line: its time, its swipes and taps, an apps line's chips, a picture, a notification's icon, its bar; and the divider above new lines. */
@RunWith(AndroidJUnit4::class)
class LogLineTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tapped = CopyOnWriteArrayList<LogEntry>()
    private val longPressed = CopyOnWriteArrayList<LogEntry>()
    private val copied = CopyOnWriteArrayList<LogEntry>()
    private val deleted = CopyOnWriteArrayList<LogEntry>()
    private val appTaps = CopyOnWriteArrayList<AppEntry>()
    private val appLongPresses = CopyOnWriteArrayList<AppEntry>()
    private val files = ArrayList<File>()
    private var ids = 0L

    @After fun tearDown() = files.forEach { it.delete() }

    private fun entry(text: String, kind: LogKind = LogKind.OUTPUT, source: String? = null, ctx: Map<String, String> = emptyMap()) =
        LogEntry(++ids, AT, kind, source, text, LogContext(ctx))

    private fun row(e: LogEntry, dimmed: Boolean = false, gutter: Gutter = Gutter.COMMAND, showTime: Boolean = true, showSource: Boolean = true) =
        LogRow.Entry(e, dimmed, gutter, showSource = showSource, showTime = showTime)

    @Composable
    private fun Line(row: LogRow.Entry, timeMode: TimeMode = TimeMode.OFF, dim: Boolean = false, gutter: Boolean = true, apps: List<AppEntry> = emptyList()) =
        LogLine(
            row, timeMode, TIMES, dim, topGap = 0.dp, gutter = gutter, indents = INDENTS, highlighted = false, apps = { apps },
            onTap = { tapped += it }, onLongPress = { longPressed += it },
            onAppTap = { appTaps += it }, onAppLongPress = { appLongPresses += it },
            onSwipeCopy = { copied += it }, onSwipeDelete = { deleted += it },
        )

    private fun show(content: @Composable () -> Unit) = compose.setContent { KolahalTheme { Column(Modifier.width(360.dp)) { content() } } }

    private fun line(e: LogEntry) = compose.onNodeWithTag("line-${e.id}")

    private fun texts(e: LogEntry) = line(e).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }

    private fun bounds(e: LogEntry) = line(e).getUnclippedBoundsInRoot()

    /** Nothing copied or deleted for a second. */
    private fun staysPut() {
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue("copied $copied", copied.isEmpty())
            assertTrue("deleted $deleted", deleted.isEmpty())
            Thread.sleep(100)
        }
    }

    /** Above the first line that came while home was left: how many came. */
    @Test fun theNewLinesDividerSaysHowMany() {
        show { UnreadDivider(LogRow.Unread(3)) }
        compose.onNodeWithText("── 3 new since you left ──").assertExists()
    }

    /** log.time: on every line, on commands, on a group's first line, or on none. */
    @Test fun theTimeShowsOnTheLinesItsModeSays() {
        val command = entry("date", LogKind.COMMAND)
        val grouped = entry("same minute")
        val first = entry("a new group")
        var mode by mutableStateOf(TimeMode.ALL)
        show { listOf(row(command), row(grouped, showTime = false), row(first)).forEach { Line(it, timeMode = mode) } }
        fun timed() = listOf(command, grouped, first).filter { TIME in texts(it) }
        assertEquals(listOf(command, grouped, first), timed())
        compose.runOnIdle { mode = TimeMode.COMMANDS }
        assertEquals(listOf(command), timed())
        compose.runOnIdle { mode = TimeMode.GROUPS }
        assertEquals(listOf(command, first), timed())
        compose.runOnIdle { mode = TimeMode.OFF }
        assertEquals(emptyList<LogEntry>(), timed())
    }

    private fun part(e: LogEntry, text: String, substring: Boolean = false) =
        compose.onNode(hasText(text, substring) and hasAnyAncestor(hasTestTag("line-${e.id}")), useUnmergedTree = true)

    /** Where [line] of a part of a log line is, from the top. */
    private fun SemanticsNodeInteraction.baseline(line: AlignmentLine) = getUnclippedBoundsInRoot().top + getAlignmentLinePosition(line)

    /** The time sits on the first line's baseline, not halfway down a line that wraps. */
    @Test fun theTimeSitsOnTheFirstLine() {
        val long = entry("zq " + "a line long enough to wrap ".repeat(5))
        show { Line(row(long), TimeMode.ALL) }
        val text = part(long, "zq ", substring = true)
        assertTrue("one line only", text.baseline(LastBaseline) - text.baseline(FirstBaseline) > 30.dp)
        assertEquals(text.baseline(FirstBaseline).value, part(long, TIME).baseline(FirstBaseline).value, 0.5f)
    }

    /** The time makes no line taller, at the usual line height or one shorter than the text; a taller line keeps its text and time halfway down. */
    @Test fun theTimeAddsNoHeight() {
        val timed = entry("zq timed", LogKind.COMMAND)
        val plain = entry("zq plain", LogKind.COMMAND)
        show { Line(row(timed), TimeMode.ALL); Line(row(plain), TimeMode.OFF) }
        fun height(e: LogEntry) = bounds(e).let { it.bottom - it.top }
        try {
            for (lineHeight in listOf(Keys.LOG_LINE_HEIGHT.default, 10)) {
                compose.runOnIdle { Term.lineHeight = lineHeight }
                assertEquals("at $lineHeight", height(plain).value, height(timed).value, 0.5f)
            }
            compose.runOnIdle { Term.lineHeight = Keys.LOG_LINE_HEIGHT.default; Term.tallerTaps = true }
            val line = bounds(timed)
            val text = part(timed, "zq timed", substring = true).getUnclippedBoundsInRoot()
            assertEquals(((line.top + line.bottom) / 2).value, ((text.top + text.bottom) / 2).value, 1f)
            assertEquals(part(timed, "zq timed", substring = true).baseline(FirstBaseline).value, part(timed, TIME).baseline(FirstBaseline).value, 0.5f)
        } finally {
            Term.lineHeight = Keys.LOG_LINE_HEIGHT.default
            Term.tallerTaps = Keys.LOG_TALLER_TAPS.default
        }
    }

    /** Swiped right past a third of its width, a line is copied, and goes back in place. */
    @Test fun aSwipeRightCopies() {
        val e = entry("swipe me")
        show { Line(row(e)) }
        line(e).performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        compose.waitUntil(5_000) { copied.isNotEmpty() }
        assertEquals(listOf(e), copied.toList())
        assertTrue(deleted.isEmpty())
        compose.waitUntil(5_000) { bounds(e).left == 0.dp }
    }

    /** Swiped left past a third, a line is deleted, and slides out of sight. */
    @Test fun aSwipeLeftDeletes() {
        val e = entry("swipe me")
        show { Line(row(e)) }
        line(e).performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f) }
        compose.waitUntil(5_000) { deleted.isNotEmpty() }
        assertEquals(listOf(e), deleted.toList())
        assertTrue(copied.isEmpty())
        compose.waitUntil(5_000) { bounds(e).right <= 1.dp }
    }

    /** A dimmed line swipes as a bright one does: the press it watches gives way to the swipe. */
    @Test fun aDimmedLineSwipesToo() {
        val e = entry("an old line")
        show { Line(row(e, dimmed = true), dim = true) }
        line(e).performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
        compose.waitUntil(5_000) { copied.isNotEmpty() }
        assertEquals(listOf(e), copied.toList())
        assertTrue(tapped.isEmpty())
    }

    /** A swipe short of a third moves the line, does nothing, and the line goes back. */
    @Test fun aShortSwipeDoesNothing() {
        val e = entry("swipe me")
        show { Line(row(e)) }
        line(e).performTouchInput { down(center); moveBy(Offset(width / 5f, 0f)) }
        compose.waitUntil(5_000) { bounds(e).left > 0.dp }
        line(e).performTouchInput { up() }
        staysPut()
        assertTrue("tapped $tapped", tapped.isEmpty())
        compose.waitUntil(5_000) { bounds(e).left == 0.dp }
    }

    /** While a line is dragged, what letting go would do shows where it moved from; brought back, it does nothing. */
    @Test fun aDragSaysWhatItWillDo() {
        val e = entry("drag me")
        show { Line(row(e)) }
        line(e).performTouchInput { down(center); moveBy(Offset(width * 0.4f, 0f)) }
        compose.onNodeWithText("⧉ Copy").assertExists()
        compose.onAllNodesWithText("Delete ✕").assertCountEquals(0)
        line(e).performTouchInput { moveBy(Offset(-width * 0.8f, 0f)) }
        compose.onNodeWithText("Delete ✕").assertExists()
        compose.onAllNodesWithText("⧉ Copy").assertCountEquals(0)
        line(e).performTouchInput { moveBy(Offset(width * 0.4f, 0f)); up() }
        compose.waitUntil(5_000) { bounds(e).left == 0.dp && compose.onAllNodesWithText("Delete ✕").fetchSemanticsNodes().isEmpty() }
        staysPut()
    }

    /**
     * A table in a script line's body shows under the text above it, with the text after it below,
     * in either look (log.tables). A screen reader reads it once, each cell after its column's name.
     * A wide one scrolls sideways: a swipe on it moves the table and leaves the line where it is.
     */
    @Test fun aTableShowsUnderItsLineAndScrolls() {
        val wide = List(6) { "a long column header $it" }
        val table = com.mtwabpin.kolahal.data.StyledText.table(listOf(listOf("Coin", "Price") + wide, listOf("BTC", "64,210")), header = true, align = "lr")
        val e = entry("Prices", LogKind.SCRIPT, source = "crypto", ctx = mapOf(LogContext.TITLE to "1", LogContext.BODY to "above\n$table\nbelow"))
        compose.runOnIdle { Term.boxedTables = true }
        show { Line(row(e)) }
        for (boxed in listOf(true, false)) {
            compose.runOnIdle { Term.boxedTables = boxed }
            compose.onNodeWithTag("log-table", useUnmergedTree = true).assert(hasContentDescription("Coin BTC, Price 64,210", substring = true))
            // What a screen reader gets (the merged tree): the cells only in the table's description.
            compose.onAllNodes(hasText("64,210", substring = true)).assertCountEquals(0)
            compose.onNode(hasText("below"), useUnmergedTree = true).assertExists()
            assertTrue(texts(e).joinToString().contains("above"))
        }
        compose.onNodeWithTag("log-table", useUnmergedTree = true).performTouchInput { swipeLeft() }
        staysPut()
        compose.runOnIdle { Term.boxedTables = true }
    }

    /** A tap and a long-press reach the line, and a dimmed line's too, which watches its own presses. */
    @Test fun aTapAndALongPressReachTheLine() {
        val bright = entry("a bright line")
        val dimmed = entry("a dimmed line")
        show {
            Line(row(bright))
            Line(row(dimmed, dimmed = true), dim = true)
        }
        for (e in listOf(bright, dimmed)) {
            line(e).performClick()
            line(e).performTouchInput { longClick() }
        }
        compose.runOnIdle {
            assertEquals(listOf(bright, dimmed), tapped.toList())
            assertEquals(listOf(bright, dimmed), longPressed.toList())
        }
    }

    private fun app(label: String, kind: ProfileKind = ProfileKind.PERSONAL, quiet: Boolean = false) =
        AppEntry(label, ComponentName("com.example.$label", "$label.Main"), Profile(Process.myUserHandle(), kind.ordinal.toLong(), kind, quiet))

    private fun chip(label: String) = compose.onNode(hasText(label))

    /** An apps line is a chip per app, another profile's tagged and a paused one's marked; a tap opens one, a long-press asks for its actions. */
    @Test fun anAppsLineIsAChipPerApp() {
        val clock = app("Clock")
        val maps = app("Maps", ProfileKind.WORK)
        val mail = app("Mail", ProfileKind.WORK, quiet = true)
        val e = entry("apps", ctx = mapOf(LogContext.APPS to "[]"))
        show { Line(row(e), apps = listOf(clock, maps, mail)) }
        fun shown(label: String) = chip(label).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Clock"), shown("Clock"))
        assertEquals(listOf("W", "Maps"), shown("Maps"))
        assertEquals(listOf("W⏸", "Mail"), shown("Mail"))

        chip("Clock").performClick()
        chip("Maps").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(clock), appTaps.toList())
            assertEquals(listOf(maps), appLongPresses.toList())
            assertTrue(tapped.isEmpty() && longPressed.isEmpty())
        }
    }

    /** An apps line whose apps are all gone says so. */
    @Test fun anAppsLineWithItsAppsGoneSaysSo() {
        show { Line(row(entry("apps", ctx = mapOf(LogContext.APPS to "[]")))) }
        compose.onNodeWithText("(apps no longer installed)").assertExists()
    }

    /** Where [LogImages] keeps [url]'s download: put there, nothing is fetched. */
    private fun download(url: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "log-images").apply { mkdirs() }, name).also { files += it }
    }

    /** A picture shows under its line's text, with the time beside the text, not by the picture; a picture with no text has no empty line above it. */
    @Test fun aPictureShowsUnderItsText() {
        val url = "https://example.com/log-line-${System.nanoTime()}.png"
        download(url).outputStream().use { Bitmap.createBitmap(2000, 500, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val captioned = entry("zq caption", ctx = mapOf(LogContext.IMAGE to url))
        val bare = entry("", ctx = mapOf(LogContext.IMAGE to url))
        show {
            Line(row(captioned), TimeMode.ALL)
            Line(row(bare), TimeMode.ALL)
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Loading image…").fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithText("Image unavailable").assertCountEquals(0)

        val caption = compose.onNodeWithText("zq caption", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val time = compose.onNode(hasText(TIME) and hasAnyAncestor(hasTestTag("line-${captioned.id}")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("time at ${time.top}, text ${caption.top} to ${caption.bottom}", time.top < caption.bottom)
        val taller = bounds(captioned).let { it.bottom - it.top } - bounds(bare).let { it.bottom - it.top }
        assertEquals((caption.bottom - caption.top).value, taller.value, 1f)
    }

    /** A notification's first line ends in its app's saved icon instead of its name; with no icon saved, the name; after the first, neither. */
    @Test fun aNotificationLineEndsInItsAppsIcon() {
        val key = "ll-icon-${System.nanoTime()}"
        NotifIcons.save(context, key, Icon.createWithResource(context, R.drawable.ic_stat_terminal))
        files += File(File(context.filesDir, "notif-icons"), "$key.png")
        val iconed = entry("zq alarm", LogKind.SCRIPT, "Clock", mapOf(LogContext.ICON to key))
        val named = entry("zq alarm two", LogKind.SCRIPT, "Clock", mapOf(LogContext.ICON to "ll-none-${System.nanoTime()}"))
        val later = entry("zq alarm three", LogKind.SCRIPT, "Clock", mapOf(LogContext.ICON to key))
        show {
            Line(row(iconed))
            Line(row(named))
            Line(row(later, showSource = false))
        }
        assertEquals(listOf("zq alarm Clock"), texts(iconed))
        assertEquals(listOf("zq alarm two · Clock"), texts(named))
        assertEquals(listOf("zq alarm three"), texts(later))
    }

    /** The bar at a line's left is its kind's colour, command, script or error; with log.gutter off there is none. */
    @Test fun theBarIsTheColourOfItsKind() {
        val lines = Gutter.entries.map { it to entry("bar ${it.name.lowercase()}") }
        var gutter by mutableStateOf(true)
        compose.setContent {
            KolahalTheme {
                Column(Modifier.width(360.dp).background(Color.Black)) { lines.forEach { (g, e) -> Line(row(e, gutter = g), gutter = gutter) } }
            }
        }
        // The top edge, clear of the letters.
        fun edge(e: LogEntry) = line(e).captureToImage().toPixelMap()[0, 1]
        val bars = lines.map { edge(it.second) }
        assertEquals(bars.toString(), 3, bars.toSet().size)
        assertTrue(bars.toString(), Color.Black !in bars)
        compose.runOnIdle { gutter = false }
        assertEquals(listOf(Color.Black, Color.Black, Color.Black), lines.map { edge(it.second) })
    }

    /**
     * A body taller than log.fold lines folds under "▾ N more lines"; a tap shows it all, and
     * "▴ Show less" folds it again. A line just over the limit doesn't fold, nor does a search result.
     */
    @Test fun aLongLineFolds() {
        val body = (1..30).joinToString("\n") { "line $it" }
        val long = entry("Long", LogKind.SCRIPT, ctx = mapOf(LogContext.TITLE to "1", LogContext.BODY to body))
        val near = entry("Near", LogKind.SCRIPT, ctx = mapOf(LogContext.TITLE to "1", LogContext.BODY to (1..9).joinToString("\n") { "near $it" }))
        val found = entry("Found", LogKind.SCRIPT, ctx = mapOf(LogContext.TITLE to "1", LogContext.BODY to body))
        compose.runOnIdle { Term.foldLines = 8 }
        show {
            Line(row(long))
            Line(row(near))
            LogLine(
                row(found), TimeMode.OFF, TIMES, false, topGap = 0.dp, indents = INDENTS, highlighted = false, apps = { emptyList() },
                onTap = {}, onLongPress = {}, onAppTap = {}, onAppLongPress = {}, marks = listOf("line 30"),
            )
        }
        fun height(e: LogEntry) = bounds(e).let { it.bottom - it.top }
        val folded = height(long)
        // Title and 7 lines, then the link: well short of 31 lines.
        assertTrue("folded to $folded", folded < 260.dp)
        compose.onNode(hasText("▾ 23 more lines") and hasAnyAncestor(hasTestTag("line-${long.id}")), useUnmergedTree = true).assertExists().performClick()
        compose.waitForIdle()
        assertTrue("opened to ${height(long)}", height(long) > folded + 300.dp)
        compose.onNode(hasText("▴ Show less") and hasAnyAncestor(hasTestTag("line-${long.id}")), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(folded.value, height(long).value, 1f)
        // 10 lines in all: two over the limit, so it shows whole.
        compose.onAllNodes(hasTestTag("fold-link") and hasAnyAncestor(hasTestTag("line-${near.id}")), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("fold-link") and hasAnyAncestor(hasTestTag("line-${found.id}")), useUnmergedTree = true).assertCountEquals(0)
        // log.fold 0: nothing folds.
        compose.runOnIdle { Term.foldLines = 0 }
        compose.onAllNodes(hasTestTag("fold-link"), useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { Term.foldLines = Keys.LOG_FOLD.default }
    }

    /** Bars in a body line up in columns; a screen reader hears each row's texts, or a lone bar as a percentage. */
    @Test fun barsShowUnderTheirLine() {
        val st = com.mtwabpin.kolahal.data.StyledText
        val ranges = st.ranges(listOf(com.mtwabpin.kolahal.data.StyledText.RangeRow("Sat", 23.0, 31.0), com.mtwabpin.kolahal.data.StyledText.RangeRow("Sun", 22.0, 30.0)), unit = "°")
        val e = entry("Pune", LogKind.SCRIPT, ctx = mapOf(LogContext.TITLE to "1", LogContext.BODY to "above\n$ranges\n${st.bar(0.25)}\nbelow"))
        show { Line(row(e)) }
        val bars = compose.onAllNodes(hasTestTag("log-bars"), useUnmergedTree = true)
        bars.assertCountEquals(1)
        bars[0].assert(hasContentDescription("Sat 23° 31°. Sun 22° 30°. 25 percent"))
        compose.onNode(hasText("below"), useUnmergedTree = true).assertExists()
    }

    /** A built-in command's bar (unlocks) sits after the line's text, and lines up with the next line's. */
    @Test fun aCommandsBarFollowsItsText() {
        val a = entry("Today      12 unlocks", ctx = LogContext.ALIGNED + LogContext.bar(0.25))
        val b = entry("Yesterday  48 unlocks", ctx = LogContext.ALIGNED + LogContext.bar(1.0))
        show { Line(row(a)); Line(row(b)) }
        val text = part(a, "Today      12 unlocks").getUnclippedBoundsInRoot()
        assertTrue("the text keeps its width", text.right - text.left > 150.dp)
        assertEquals(bounds(a).bottom - bounds(a).top, bounds(b).bottom - bounds(b).top)
    }

    /** A tag shows its text; a link in the text opens through LocalUriHandler, not as a tap on the line. */
    @Test fun aLinkInTheTextOpensItself() {
        val st = com.mtwabpin.kolahal.data.StyledText
        val e = entry("412 " + st.link("https://example.org/s", "A story") + " " + st.tag(com.mtwabpin.kolahal.data.StyledText.Colour.BAD, "LIVE"), LogKind.SCRIPT, ctx = mapOf(LogContext.TITLE to "1"))
        val opened = CopyOnWriteArrayList<String>()
        show {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalUriHandler provides object : androidx.compose.ui.platform.UriHandler { override fun openUri(uri: String) { opened += uri } },
            ) { Line(row(e)) }
        }
        assertTrue(texts(e).joinToString(), texts(e).joinToString().contains("412 A story LIVE"))
        // "412 " is four characters of the terminal font; the link starts after them, past the gutter's 10 dp.
        line(e).performTouchInput { click(Offset(70.dp.toPx(), 10.dp.toPx())) }
        compose.waitForIdle()
        assertEquals(listOf("https://example.org/s"), opened.toList())
        assertTrue("the line's own tap didn't run: $tapped", tapped.isEmpty())
    }
}
