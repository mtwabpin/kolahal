package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText

/**
 * One log entry for each kind of row the log draws: each kind of line, each context key that
 * changes how a row looks or what it does, and each `fmt` function a script's body can hold. The
 * tests of [lineModel] go through all of them, and so will the tests of each way of drawing the log.
 */
object LogCorpus {
    /** A line of the corpus: what it shows, for failure messages, and the entry. */
    class Line(val name: String, val entry: LogEntry) {
        override fun toString() = name
    }

    /** When the first line was logged: Friday 21 November 2025, 14:02 UTC. Each line is a second later. */
    const val AT = 1_763_733_720_000L

    private var ids = 0L

    private fun line(
        name: String,
        kind: LogKind,
        text: String,
        source: String? = null,
        ctx: Map<String, String> = emptyMap(),
    ): Line {
        ids++
        return Line(name, LogEntry(ids, AT + ids * 1_000, kind, source, text, LogContext(ctx)))
    }

    private fun script(name: String, title: String, source: String, body: String, more: Map<String, String> = emptyMap()) =
        line(
            name,
            LogKind.SCRIPT,
            title,
            source,
            mapOf(LogContext.TITLE to "1", LogContext.BODY to body) + more
        )

    private val bold = StyledText.Style.BOLD

    // ---- built-in commands ----------------------------------------------------------------------

    val command = line("a typed command", LogKind.COMMAND, "torch on")
    val output = line("plain output", LogKind.OUTPUT, "Torch is on")
    val details = line("output with details", LogKind.OUTPUT, "Battery 82%\ncharging · 40 min to full")
    val styledOutput = line(
        "styled output",
        LogKind.OUTPUT,
        StyledText.tag(StyledText.ColorRole.GOOD, "done") + " " + StyledText.wrap(bold, "3 tasks") +
                "\n" + StyledText.list(listOf("one", "two"), numbered = false),
    )
    val aligned = line(
        "aligned columns",
        LogKind.OUTPUT,
        "name   size\nfoo    12",
        ctx = LogContext.ALIGNED
    )
    val linked = line(
        "a line that opens a link",
        LogKind.OUTPUT,
        "kolahal.example",
        ctx = mapOf(LogContext.URL to "https://kolahal.example/")
    )
    val info = line("info with details", LogKind.INFO, "Installed Clock\ncom.example.clock")
    val profileTag = line(
        "a profile tag",
        LogKind.INFO,
        "Launched Maps [W]",
        ctx = mapOf(LogContext.PKG to "com.example.maps", LogContext.APART to "1")
    )
    val error = line(
        "an error",
        LogKind.ERROR,
        "Unknown command " + StyledText.wrap(bold, "torh")
    )
    val builtInBar = line(
        "a command's bar",
        LogKind.OUTPUT,
        "Volume 60%",
        ctx = LogContext.bar(0.6, "good")
    )
    val statsAlone = line(
        "a command's big numbers alone",
        LogKind.OUTPUT,
        StyledText.stats(listOf("41" to "unlocks today", "3h 2m" to "screen time"))
    )
    val outputTable = line(
        "a command's table",
        LogKind.OUTPUT,
        "Usage today\n" + StyledText.table(
            listOf(listOf("App", "Time"), listOf("Maps", "1h 20m"), listOf("Clock", "4m")),
            header = true,
            align = "lr"
        )
    )
    val twoColumns = line(
        "two columns",
        LogKind.OUTPUT,
        "log.dim",
        ctx = mapOf(LogContext.DESC to "on · dims older output")
    )
    val twoColumnsBar = line(
        "two columns with a bar",
        LogKind.OUTPUT,
        "Maps",
        ctx = mapOf(LogContext.DESC to "1h 20m", LogContext.PKG to "com.example.maps") +
                LogContext.bar(0.6)
    )
    val nameColumns = line(
        "help's name column",
        LogKind.OUTPUT,
        "torch",
        ctx = mapOf(
            LogContext.DESC to "Turns the torch on or off, " + StyledText.link(
                "https://kolahal.example/torch",
                "see more"
            ),
            LogContext.NAME_COL to "notifications",
            LogContext.ARGS to "on · off",
            LogContext.COMMAND to "torch",
        )
    )
    val apps = line(
        "an apps grid",
        LogKind.OUTPUT,
        "Maps, Clock",
        ctx = mapOf(
            LogContext.APPS to "[{\"pkg\":\"com.example.maps\",\"cls\":\"com.example.maps.Main\",\"user\":0}," +
                    "{\"pkg\":\"com.example.clock\"}]"
        )
    )
    val call = line(
        "a call with its person",
        LogKind.INFO,
        "Ada Lovelace\nmissed · 2 min ago",
        ctx = mapOf(
            LogContext.PERSON to "Ada Lovelace",
            LogContext.PHOTO to "content://contacts/1/photo",
            LogContext.PHONE to "+1 555 0100",
        ) + LogContext.ITEM
    )
    val contact = line(
        "a contact",
        LogKind.OUTPUT,
        "Ada Lovelace",
        ctx = mapOf(
            LogContext.PERSON to "Ada Lovelace",
            LogContext.PHONE to "+1 555 0100",
            LogContext.DESC to "+1 555 0100 · mobile"
        )
    )
    val runs = line(
        "a hint that runs a command",
        LogKind.INFO,
        "Tap to set up notifications",
        ctx = mapOf(LogContext.RUN to "setup")
    )

    // ---- scripts, feeds and notifications -------------------------------------------------------

    val scriptLine = line("a script's line", LogKind.SCRIPT, "12°C, clear", "weather")
    val heading = script("a title with a body", "Top stories", "hn", "Three new stories today")
    val listBody = script(
        "a body with lists",
        "News",
        "news",
        "Read these\n" + StyledText.list(listOf("one", "two"), numbered = false) + "\n" +
                StyledText.list(listOf("first"), numbered = true) + "\n" +
                StyledText.list(listOf("nested"), numbered = false, level = 2)
    )
    val styles = line(
        "styles, colors, a tag and a link",
        LogKind.SCRIPT,
        StyledText.wrap(bold, "big") + " " + StyledText.wrap(StyledText.Style.ITALIC, "aside") + " " +
                StyledText.wrap(StyledText.Style.UNDERLINE, "under") + " " +
                StyledText.wrap(StyledText.Style.STRIKE, "gone") + " " +
                StyledText.wrap(StyledText.Style.CODE, "v1.2") + " " +
                StyledText.tag(StyledText.ColorRole.BAD, "LIVE") + " " +
                StyledText.color(StyledText.ColorRole.WARN, "warm") + " " +
                StyledText.link("https://kolahal.example/a", StyledText.wrap(bold, "the") + " page"),
        "styles"
    )
    val table = script(
        "a table between words",
        "Prices",
        "coins",
        "Today\n" + StyledText.table(
            listOf(listOf("Coin", "Price"), listOf("BTC", "64,210"), listOf("ETH", "3,100")),
            header = true,
            align = "lr"
        ) + "\nfrom an " + StyledText.link("https://kolahal.example/feed", "example feed")
    )
    val bars = script(
        "bars",
        "Steps",
        "health",
        StyledText.bars(
            listOf(StyledText.BarRow("Mon", 4_000.0), StyledText.BarRow("Tue", 8_000.0)),
            unit = " steps"
        )
    )
    val ranges = script(
        "ranges",
        "Forecast",
        "weather",
        StyledText.ranges(
            listOf(StyledText.RangeRow("Mon", 3.0, 9.0), StyledText.RangeRow("Tue", 5.0, 12.0)),
            unit = "°"
        )
    )
    val bar = script("one bar with no text", "Battery", "battery", "82%\n" + StyledText.bar(0.82))
    val stats = script(
        "big numbers",
        "Today",
        "health",
        StyledText.stats(listOf("8,204" to "steps", "6.1 km" to "walked"))
    )
    val chart = script(
        "a column chart",
        "Unlocks",
        "usage",
        StyledText.columns(listOf(1.0, 3.0, 2.0), listOf("00", "12"), "3 unlocks this morning", slots = 5)
    )
    val untitled = line(
        "blocks with no title",
        LogKind.SCRIPT,
        "",
        "coins",
        mapOf(LogContext.BODY to StyledText.table(listOf(listOf("a", "b")), header = false))
    )
    val picture = line(
        "a picture",
        LogKind.SCRIPT,
        "A comic",
        "xkcd",
        mapOf(
            LogContext.IMAGE to "https://kolahal.example/comic.png",
            LogContext.URL to "https://kolahal.example/comic"
        )
    )
    val buttons = line(
        "buttons",
        LogKind.SCRIPT,
        "Charge the phone",
        "charge",
        mapOf(
            LogContext.SCRIPT to "charge",
            LogContext.ACTIONS to "[[\"Snooze 30 min\",\"snooze 30\"],[\"Done\",\"done\"]]"
        )
    )
    val buttonDone = line(
        "a button that was tapped",
        LogKind.SCRIPT,
        "Charge the phone",
        "charge",
        mapOf(
            LogContext.SCRIPT to "charge",
            LogContext.ACTIONS to "[[\"Snooze 30 min\",\"snooze 30\"],[\"Done\",\"done\"]]",
            LogContext.ACTION_DONE to "1"
        )
    )
    val notification = line(
        "a notification",
        LogKind.SCRIPT,
        "New message from Ada",
        "Chat",
        mapOf("notif" to "0|com.example.chat|1", LogContext.ICON to "chat-icon", LogContext.PKG to "com.example.chat")
    )
    val item = line(
        "an item of a list",
        LogKind.SCRIPT,
        "Second story",
        "hn",
        mapOf(LogContext.URL to "https://kolahal.example/2") + LogContext.ITEM
    )
    val long = script(
        "a long line",
        "Notes",
        "notes",
        (1..40).joinToString("\n") { "line $it" })

    /** Every line, in the order they were logged. */
    val all: List<Line> = listOf(
        command, output, details, styledOutput, aligned, linked, info, profileTag, error, builtInBar,
        statsAlone, outputTable, twoColumns, twoColumnsBar, nameColumns, apps, call, contact, runs,
        scriptLine, heading, listBody, styles, table, bars, ranges, bar, stats, chart, untitled,
        picture, buttons, buttonDone, notification, item, long,
    )

    /** [line]'s row, as the log builds it on its own, or with the flags given. */
    fun row(
        line: Line,
        unread: Boolean = false,
        showSource: Boolean = true,
        showTime: Boolean = true,
        blockStart: Boolean = false,
        itemGap: Boolean = false,
        dimmed: Boolean = false,
    ) = LogRow.Entry(
        line.entry,
        dimmed,
        when (line.entry.kind) {
            LogKind.ERROR -> Gutter.ERROR
            LogKind.SCRIPT -> Gutter.SCRIPT
            else -> Gutter.COMMAND
        },
        unread = unread,
        blockStart = blockStart,
        showSource = showSource,
        showTime = showTime,
        itemGap = itemGap,
    )
}
