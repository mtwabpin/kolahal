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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
