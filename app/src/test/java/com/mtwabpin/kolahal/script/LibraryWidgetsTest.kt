package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.ui.widgets.WidgetWords
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the library's scripts hand to `widget.show` passes the real parser ([WidgetParser]).
 *
 * `widget.show` checks a description even when the widget isn't placed. So one field the parser
 * refuses would end that script's runs in an error, for everyone who has the script.
 *
 * Each description is written out here as its script builds it, in the form its engine passes on:
 * - Lua: a table arrives as a map, a list as a map with the keys 1.0, 2.0, …, and every number as
 *   a double. [Json.normalizeLua] reads that, as LuaEngine does.
 * - JavaScript: an object arrives as a map and an array as a list.
 * The kind comes from the script's own `@widget` line. The scripts themselves run in the device
 * tests (LibraryScriptsTest).
 */
class LibraryWidgetsTest {
    private fun kind(file: String): WidgetKind =
        ScriptMeta.widget(sourceFile("src/main/assets/library/$file").readText())!!.kind

    /** A link, as the host lets it pass: a web link only. */
    private fun link(url: String): String =
        url.also { require(it.startsWith("https://")) { "widget: not a web link" } }

    private fun parse(file: String, raw: Map<*, *>): WidgetSpec =
        WidgetParser.parse(raw, kind(file), { it }, ::link)

    private fun luaTable(vararg fields: Pair<String, Any?>): Map<*, *> = mapOf(*fields)

    private fun luaList(vararg items: Any?): Map<*, *> =
        items.withIndex().associate { (i, item) -> (i + 1).toDouble() to item }

    private fun fromLua(file: String, table: Map<*, *>): WidgetSpec =
        parse(file, Json.normalizeLua(table) as Map<*, *>)

    /** A tap that runs the script again with no arguments and turns home to the log. */
    private val toLog = WidgetTap(args = "", toLog = true)
    private val luaToLog = luaTable("args" to "", "page" to "log")
    private val jsToLog = mapOf("args" to "", "page" to "log")

    @Test
    fun everyScriptWithAWidgetIsHere() {
        val withWidget = sourceFile("src/main/assets/library").listFiles().orEmpty()
            .filter { ScriptMeta.widget(it.readText()) != null }.map { it.name }.sorted()
        assertEquals(
            listOf(
                "aqi.js",
                "countdown.lua",
                "counter.lua",
                "cricket.js",
                "crypto.js",
                "data-month.lua",
                "exchange.js",
                "football.js",
                "forecast.js",
                "habit.lua",
                "headlines.js",
                "hn.lua",
                "quick-toggles.lua",
                "quote.js",
                "releases.lua",
                "screen-time-months.lua",
                "screen-time.lua",
                "sun.js",
                "template-widget-js.js",
                "template-widget-list-js.js",
                "template-widget-list.lua",
                "template-widget.lua",
                "unlock-chart.lua",
                "unused-apps.lua",
                "weather.lua",
                "webnumber.js",
                "world-clocks.lua",
                "xkcd.js"
            ),
            withWidget,
        )
    }

    /**
     * A switch for Do Not Disturb, and one for the work profile on a phone that has one. Each has
     * its state, and a tap that runs the script again with one word.
     */
    @Test
    fun quickToggles() {
        val dnd = luaTable(
            "label" to "Do Not Disturb",
            "icon" to "🌙",
            "on" to true,
            "tap" to luaTable("args" to "dnd")
        )
        val work = luaTable(
            "label" to "Work profile",
            "icon" to "💼",
            "on" to false,
            "tap" to luaTable("args" to "work")
        )
        assertEquals(
            WidgetSpec.Toggles(
                WidgetBase(staleAfter = 60),
                items = listOf(
                    WidgetSpec.Toggle(
                        "Do Not Disturb",
                        on = true,
                        tap = WidgetTap(args = "dnd"),
                        icon = "🌙"
                    ),
                    WidgetSpec.Toggle(
                        "Work profile",
                        on = false,
                        tap = WidgetTap(args = "work"),
                        icon = "💼"
                    ),
                ),
            ),
            fromLua(
                "quick-toggles.lua",
                luaTable("items" to luaList(dnd, work), "staleAfter" to 60.0)
            ),
        )
        // A phone with no work profile has the one switch.
        val one = fromLua(
            "quick-toggles.lua",
            luaTable("items" to luaList(dnd), "staleAfter" to 60.0)
        ) as WidgetSpec.Toggles
        assertEquals(listOf("Do Not Disturb"), one.items.map { it.label })
    }

    /** quick-toggles asks for Do Not Disturb and the work profile, and for nothing else. */
    @Test
    fun quickTogglesAsksOnlyForWhatItSwitches() {
        val code = sourceFile("src/main/assets/library/quick-toggles.lua").readText()
        assertEquals(listOf("dnd", "work"), ScriptAccess.asksFor(code))
        assertEquals(listOf(ScriptAccess.WIDGET, "dnd", "work"), ScriptAccess.items(code))
        assertEquals(false, ScriptMeta.postsNotifications(code))
    }

    /**
     * A clock for each place of its zones option, each with a label: its own, or the last part of the
     * zone's name. The content never gets old, so it counts as current for a week.
     */
    @Test
    fun worldClocks() {
        val week = 604_800
        val spec = fromLua(
            "world-clocks.lua",
            luaTable(
                "zones" to luaList(
                    luaTable("zone" to "Asia/Kolkata", "label" to "Kolkata"),
                    luaTable("zone" to "America/New_York", "label" to "New York"),
                    luaTable("zone" to "Asia/Dubai", "label" to "Head office"),
                    luaTable("zone" to "UTC", "label" to "UTC"),
                ),
                "staleAfter" to week.toDouble(),
            ),
        )
        assertEquals(
            WidgetSpec.Clock(
                WidgetBase(staleAfter = week),
                zones = listOf(
                    WidgetSpec.Zone("Asia/Kolkata", "Kolkata"),
                    WidgetSpec.Zone("America/New_York", "New York"),
                    WidgetSpec.Zone("Asia/Dubai", "Head office"),
                    WidgetSpec.Zone("UTC", "UTC"),
                ),
            ),
            spec,
        )
        // It hands over every zone of the option. A clock keeps the first four.
        val six = luaList(*List(6) {
            luaTable(
                "zone" to "Etc/GMT+${it + 1}",
                "label" to "GMT+${it + 1}"
            )
        }.toTypedArray())
        assertEquals(
            4,
            (fromLua(
                "world-clocks.lua",
                luaTable("zones" to six, "staleAfter" to week.toDouble())
            ) as WidgetSpec.Clock).zones.size
        )
        // A zone that isn't one is an error that names it. The card then says that the run failed.
        val wrong = runCatching {
            fromLua(
                "world-clocks.lua",
                luaTable(
                    "zones" to luaList(luaTable("zone" to "Asia/Pune", "label" to "Pune")),
                    "staleAfter" to week.toDouble()
                )
            )
        }
        assertEquals(
            "widget: item 1 of zones: no time zone 'Asia/Pune'; use a name such as Asia/Kolkata or UTC",
            wrong.exceptionOrNull()?.message
        )
    }

    /** world-clocks asks for nothing: Kolahal knows the time zones. */
    @Test
    fun worldClocksAsksForNoAccess() {
        val code = sourceFile("src/main/assets/library/world-clocks.lua").readText()
        assertEquals(emptyList<String>(), ScriptAccess.asksFor(code))
        assertEquals(listOf(ScriptAccess.WIDGET), ScriptAccess.items(code))
        assertEquals(false, ScriptMeta.postsNotifications(code))
    }

    /**
     * The streak over five weeks of dots: a point for each of the last 35 days, the oldest first, 1
     * for a day that is done and 0 for any other. Once today is done the card is in the good color,
     * and a tap on it is Undo; before that it gives no color, and a tap marks today done. A screen
     * reader hears how many of the days were done, and the tap's label: what a tap does.
     */
    @Test
    fun habit() {
        fun days(
            streak: Double,
            days: List<Double>,
            color: String?,
            note: String? = null,
            tap: Map<*, *> = luaTable("args" to "done", "label" to "Done today")
        ) = fromLua(
            "habit.lua",
            luaTable(
                "title" to "Exercise",
                "icon" to "check",
                "value" to streak,
                "label" to "Days in a row",
                "points" to luaList(*days.toTypedArray()),
                "style" to "dots",
                "color" to color,
                "note" to note,
                "tap" to tap,
            ),
        ) as WidgetSpec.Chart

        val tap = WidgetTap(args = "done", label = "Done today")
        // Every seventh day missed, and today still open: 3 days in a row up to yesterday.
        val open = List(34) { if (it % 7 == 2) 0.0 else 1.0 } + 0.0
        val card = days(3.0, open, color = null)
        assertEquals(
            WidgetSpec.Chart(
                WidgetBase(title = "Exercise", icon = "check", tap = tap),
                points = open.map { it.toFloat() },
                style = "dots",
                value = "3",
                label = "Days in a row",
            ),
            card,
        )
        assertEquals(
            "Exercise: 3, Days in a row, Done on 29 of the last 35 days",
            WidgetWords.description("Exercise", card, 0, java.time.ZoneOffset.UTC)
        )
        // Done today: the good color, and "Done today" as the note at a large card's foot. A tap is Undo now.
        val done = days(
            4.0,
            open.dropLast(1) + 1.0,
            color = "good",
            note = "Done today",
            tap = luaTable("args" to "undo", "label" to "Undo")
        )
        assertEquals(
            listOf("4", StyledText.ColorRole.GOOD, 1f, "Done today"),
            listOf(done.value, done.base.color, done.points.last(), done.base.note)
        )
        assertEquals(WidgetTap(args = "undo", label = "Undo"), done.base.tap)
        assertEquals(
            "Exercise, Done today: 4, Days in a row, Done on 30 of the last 35 days",
            WidgetWords.description("Exercise", done, 0, java.time.ZoneOffset.UTC)
        )
        // A new habit: only today, and no dot for the days before its first.
        val new = days(0.0, listOf(0.0), color = null)
        assertEquals(listOf("0", listOf(0f), "dots"), listOf(new.value, new.points, new.style))
        assertEquals(
            "Exercise: 0, Days in a row, Not done today",
            WidgetWords.description("Exercise", new, 0, java.time.ZoneOffset.UTC)
        )
    }

    /**
     * habit asks for nothing: its days are in its own store. A word such as `url` or `status`
     * anywhere in its text, even in a comment, would give it a switch.
     */
    @Test
    fun habitAsksForNoAccess() {
        val code = sourceFile("src/main/assets/library/habit.lua").readText()
        assertEquals(emptyList<String>(), ScriptAccess.asksFor(code))
        assertEquals(listOf(ScriptAccess.WIDGET), ScriptAccess.items(code))
        assertEquals(false, ScriptMeta.postsNotifications(code))
    }

    /**
     * Daily counter: two buttons. The first shows today's count, "5 of 8" toward a target or "3 cups"
     * without one, and a tap on it adds one. Undo, in the muted color, takes one back. Once the
     * count reaches the target, the card is in the good color. It is old after an hour, or at
     * midnight, so a new day starts at 0.
     */
    @Test
    fun counter() {
        fun card(
            count: String,
            add: String,
            color: String? = null,
            title: String = "Water",
            icon: String? = "drop"
        ) = fromLua(
            "counter.lua",
            luaTable(
                "title" to title, "icon" to icon, "color" to color, "staleAfter" to 3_600.0,
                "items" to luaList(
                    luaTable(
                        "label" to count,
                        "icon" to icon,
                        "tap" to luaTable("args" to "add", "label" to add)
                    ),
                    luaTable(
                        "label" to "Undo",
                        "color" to "muted",
                        "tap" to luaTable("args" to "undo", "label" to "Undo")
                    ),
                ),
            ),
        ) as WidgetSpec.Buttons

        val undo = WidgetSpec.Button(
            "Undo",
            WidgetTap(args = "undo", label = "Undo"),
            color = StyledText.ColorRole.MUTED
        )
        // With a target: 5 of 8.
        val water = card("5 of 8", "Add 1 glass")
        assertEquals(
            WidgetSpec.Buttons(
                WidgetBase(title = "Water", icon = "drop", staleAfter = 3_600),
                listOf(
                    WidgetSpec.Button(
                        "5 of 8",
                        WidgetTap(args = "add", label = "Add 1 glass"),
                        icon = "drop"
                    ), undo
                ),
            ),
            water,
        )
        assertEquals(
            "Water: 5 of 8, Undo",
            WidgetWords.description("Water", water, 0, java.time.ZoneOffset.UTC)
        )
        // The target reached: the good color.
        assertEquals(
            StyledText.ColorRole.GOOD,
            card("8 of 8", "Add 1 glass", color = "good").base.color
        )
        // Without a target: the count with its unit, here a coffee counter with its own icon.
        val coffee = card("3 cups", "Add 1 cup", title = "Coffee", icon = "coffee")
        assertEquals(listOf("3 cups", "Undo"), coffee.items.map { it.label })
        assertEquals(listOf("coffee", null), coffee.items.map { it.icon })
        // No unit and no icon: the number alone.
        val bare = card("4", "Add 1", title = "Counter", icon = null)
        assertEquals(
            listOf(WidgetSpec.Button("4", WidgetTap(args = "add", label = "Add 1")), undo),
            bare.items
        )
        assertEquals(WidgetBase(title = "Counter", staleAfter = 3_600), bare.base)
    }

    /** counter asks for nothing: its counts are in its own store. Its options start as a water counter with a target of 8. */
    @Test
    fun counterAsksForNoAccess() {
        val code = sourceFile("src/main/assets/library/counter.lua").readText()
        assertEquals(emptyList<String>(), ScriptAccess.asksFor(code))
        assertEquals(listOf(ScriptAccess.WIDGET), ScriptAccess.items(code))
        assertEquals(false, ScriptMeta.postsNotifications(code))
        assertEquals(
            mapOf(
                "name" to "Water",
                "unit" to "glasses",
                "target" to "8",
                "icon" to "drop"
            ), ScriptMeta.options(code).associate { it.name to it.default })
    }

    /** Screen time: today large, a bar a day for the days Android has data for, oldest first. */
    @Test
    fun screenTime() {
        val tap = luaTable("args" to "today", "page" to "log", "label" to "Show today's apps")
        val spec = fromLua(
            "screen-time.lua",
            luaTable(
                "title" to "Screen time",
                "icon" to "timer",
                "value" to "4h 12m",
                "label" to "screen time today",
                "points" to luaList(252.0, 190.0, 300.0, 252.0),
                "style" to "bars",
                "min" to 0.0,
                "labels" to luaList("Fri", "Today"),
                "summary" to "Fri 4h 12m, Sat 3h 10m, Sun 5h 0m, Today 4h 12m",
                "staleAfter" to 900.0,
                "tap" to tap,
            ),
        ) as WidgetSpec.Chart
        assertEquals("4h 12m", spec.value)
        assertEquals(listOf(252f, 190f, 300f, 252f), spec.points)
        assertEquals(
            WidgetTap(args = "today", label = "Show today's apps", toLog = true),
            spec.base.tap
        )
        // Under the daily limit, or with none, usage.today's color is nil: the card keeps its own.
        assertEquals(null, spec.base.color)
        // Over it, the card takes the role usage.today gives.
        val over = fromLua(
            "screen-time.lua",
            luaTable(
                "title" to "Screen time",
                "icon" to "timer",
                "value" to "4h 12m",
                "label" to "screen time today",
                "points" to luaList(252.0),
                "style" to "bars",
                "min" to 0.0,
                "labels" to luaList("Today", "Today"),
                "summary" to "Today 4h 12m",
                "color" to "warn",
                "staleAfter" to 900.0,
                "tap" to tap,
            ),
        ) as WidgetSpec.Chart
        assertEquals(StyledText.ColorRole.WARN, over.base.color)
        assertTrue(sourceFile("src/main/assets/library/screen-time.lua").readText().contains("color = today.color,"))
    }

    /** Screen time, as the script describes it with today's total, the daily limit and the top app. */
    private fun screenTimeCard(label: String, note: String?, summary: String, color: String? = null) =
        fromLua(
            "screen-time.lua",
            luaTable(
                "title" to "Screen time",
                "icon" to "timer",
                "value" to "1h 48m",
                "label" to label,
                "note" to note,
                "points" to luaList(200.0, 150.0, 108.0),
                "style" to "bars",
                "min" to 0.0,
                "labels" to luaList("Tue", "Today"),
                "summary" to summary,
                "color" to color,
                "staleAfter" to 900.0,
                "tap" to luaTable("args" to "today", "page" to "log", "label" to "Show today's apps"),
            ),
        ) as WidgetSpec.Chart

    /**
     * Against the daily limit, the label says the time left, or the time over it; without a limit,
     * what the value is. A screen reader hears the limit first.
     */
    @Test
    fun screenTimeAgainstTheLimit() {
        val code = sourceFile("src/main/assets/library/screen-time.lua").readText()
        assertTrue(code.contains("label = label,"))
        assertTrue(code.contains("duration(left) .. \" left\""))
        assertTrue(code.contains("duration(-left) .. \" over\""))

        // 3h limit, 1h 48m used.
        val under = screenTimeCard(
            "1h 12m left", "Maps 48m",
            "1h 12m left of your 3h 0m limit. Most used: Maps 48m. Tue 3h 20m, Wed 2h 30m, Today 1h 48m"
        )
        assertEquals("1h 12m left", under.label)
        assertEquals(null, under.base.color)
        // 3h limit, 3h 22m used: the warning color too.
        val over = screenTimeCard(
            "22m over", "Maps 1h 15m",
            "22m over your 3h 0m limit. Most used: Maps 1h 15m. Tue 3h 20m, Wed 2h 30m, Today 3h 22m",
            color = "warn"
        )
        assertEquals("22m over", over.label)
        assertEquals(StyledText.ColorRole.WARN, over.base.color)
        assertTrue(
            WidgetWords.description("Screen time", over, 0, java.time.ZoneOffset.UTC)
                .contains("22m over your 3h 0m limit")
        )
        // No limit: the label it always had, and the days alone after the top app.
        val none = screenTimeCard(
            "Screen time today", "Maps 48m",
            "Most used: Maps 48m. Tue 3h 20m, Wed 2h 30m, Today 1h 48m"
        )
        assertEquals("Screen time today", none.label)
        // Each label fits a 2x1 card's 120 dp beside the chart, as "Screen time today" did.
        listOf(under, over, none).forEach { assertTrue(it.label!!.length <= "Screen time today".length) }
    }

    /**
     * The app used most today and its time, as the note on a large card's last line; none until an
     * app has a minute. A long name is cut in the script, so the time stays within the note's 16.
     */
    @Test
    fun screenTimeTopApp() {
        val code = sourceFile("src/main/assets/library/screen-time.lua").readText()
        assertTrue(code.contains("note = note,"))
        assertTrue(code.contains("local room = 16 - #time - 1"))

        val maps = screenTimeCard("1h 12m left", "Maps 48m", "Most used: Maps 48m. Today 1h 48m")
        assertEquals("Maps 48m", maps.base.note)
        assertEquals(
            "Screen time, Maps 48m: 1h 48m, 1h 12m left, Most used: Maps 48m. Today 1h 48m",
            WidgetWords.description("Screen time", maps, 0, java.time.ZoneOffset.UTC)
        )
        // "A very long application name", 10h 15m: cut to 16 with the time whole.
        val long = screenTimeCard("Screen time today", "A very… 10h 15m", "Today 10h 15m")
        assertEquals("A very… 10h 15m", long.base.note)
        assertTrue(long.base.note!!.length <= 16)
        // No app has a minute yet: no note.
        val first = screenTimeCard("2h 0m left", null, "2h 0m left of your 2h 0m limit. Today 0m")
        assertEquals(null, first.base.note)
    }

    /** Screen time by month: the time a day large, a bar a month, the oldest first, the newest called "now". */
    @Test
    fun screenTimeMonths() {
        val tap = luaTable("args" to "months", "page" to "log", "label" to "Show the months")
        val spec = fromLua(
            "screen-time-months.lua",
            luaTable(
                "title" to "Screen time by month",
                "icon" to "timer",
                "value" to "≈1h 41m",
                "label" to "a day · 3 months",
                "points" to luaList(58.0, 211.0, 124.0),
                "style" to "bars",
                "min" to 0.0,
                "labels" to luaList("12 Aug", "now"),
                "summary" to "12 Aug 58m, 11 Sep 3h 31m, 12 Oct 2h 4m",
                "staleAfter" to 3600.0,
                "tap" to tap,
            ),
        ) as WidgetSpec.Chart
        assertEquals("≈1h 41m", spec.value)
        assertEquals(listOf(58f, 211f, 124f), spec.points)
        assertEquals(
            WidgetTap(args = "months", label = "Show the months", toLog = true),
            spec.base.tap
        )
    }

    /** Data this month: a bar toward the plan, red when the pace is over it; with no plan, or on Android 9, a number. */
    @Test
    fun dataMonth() {
        val tap = luaTable("args" to "month", "page" to "log", "label" to "Show the month")
        val bar = fromLua(
            "data-month.lua",
            luaTable(
                "title" to "Data this month",
                "icon" to "wifi",
                "value" to 31.2,
                "max" to 75.0,
                "text" to "31.2",
                "label" to "of 75 GB",
                "color" to "bad",
                "staleAfter" to 1800.0,
                "tap" to tap
            ),
        ) as WidgetSpec.Progress
        assertEquals("31.2", bar.text)
        assertEquals(StyledText.ColorRole.BAD, bar.base.color)
        val number = fromLua(
            "data-month.lua",
            luaTable(
                "kind" to "stat",
                "title" to "Data this month",
                "icon" to "wifi",
                "value" to "31.2",
                "unit" to "GB",
                "label" to "mobile since 1 Nov",
                "staleAfter" to 1800.0,
                "tap" to tap
            ),
        ) as WidgetSpec.Stat
        assertEquals("31.2", number.value)
        assertEquals("GB", number.unit)
    }

    /** Unused apps: a row each, its size under its name and the days at its end; none says so. */
    @Test
    fun unusedApps() {
        val empty = "Every app you can uninstall was opened in the last 30 days"
        val tap = luaTable("args" to "list", "page" to "log", "label" to "Show the list")
        val spec = fromLua(
            "unused-apps.lua",
            luaTable(
                "icon" to "moon", "empty" to empty, "staleAfter" to 21_600.0, "tap" to tap,
                "items" to luaList(
                    luaTable(
                        "title" to "IRCTC Rail Connect",
                        "detail" to "96 MB",
                        "trailing" to "112 days"
                    ),
                    luaTable("title" to "Old Game", "trailing" to "no use recorded"),
                ),
            ),
        ) as WidgetSpec.Rows
        assertEquals(
            listOf(
                WidgetSpec.Row(
                    "IRCTC Rail Connect",
                    detail = "96 MB",
                    trailing = "112 days"
                ), WidgetSpec.Row("Old Game", trailing = "no use recorded")
            ), spec.items
        )
        assertEquals(
            empty,
            (fromLua(
                "unused-apps.lua",
                luaTable("items" to luaTable(), "empty" to empty)
            ) as WidgetSpec.Rows).empty
        )
    }

    /**
     * Each watched repo with its newest release: the tag at the row's end, and its age under the
     * repo's name. It gives no title, so the card has the script's `@title`.
     */
    @Test
    fun releases() {
        val empty = "No repos: releases add owner/repo"
        val spec = fromLua(
            "releases.lua",
            luaTable(
                "items" to luaList(
                    luaTable(
                        "title" to "neovim/neovim",
                        "trailing" to "v0.11.4",
                        "detail" to "3 days ago",
                        "tap" to luaTable("url" to "https://github.com/neovim/neovim/releases/tag/v0.11.4")
                    ),
                    // A release with no tag in its link has its name at the end. A long name is cut. With no date it has no second line.
                    luaTable(
                        "title" to "a/named",
                        "trailing" to "Kolahal 0.5.8: widgets from scripts",
                        "tap" to luaTable("url" to "https://github.com/a/named/releases/1")
                    ),
                    luaTable("title" to "a/new", "detail" to "No releases yet"),
                    luaTable(
                        "title" to "a/gone",
                        "detail" to "Couldn't read its releases",
                        "color" to "bad"
                    ),
                ),
                "empty" to empty, "staleAfter" to 21_600.0,
            ),
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(staleAfter = 21_600),
                items = listOf(
                    WidgetSpec.Row(
                        "neovim/neovim",
                        detail = "3 days ago",
                        trailing = "v0.11.4",
                        tap = WidgetTap(url = "https://github.com/neovim/neovim/releases/tag/v0.11.4")
                    ),
                    WidgetSpec.Row(
                        "a/named",
                        trailing = "Kolahal 0.5.8: widgets …",
                        tap = WidgetTap(url = "https://github.com/a/named/releases/1")
                    ),
                    WidgetSpec.Row("a/new", detail = "No releases yet"),
                    WidgetSpec.Row(
                        "a/gone",
                        detail = "Couldn't read its releases",
                        color = StyledText.ColorRole.BAD
                    ),
                ),
                empty = empty,
            ),
            spec,
        )
        // No repo is watched: Lua's empty table, which is an empty list. The card then says how to add one.
        val none = fromLua(
            "releases.lua",
            luaTable("items" to luaTable(), "empty" to empty, "staleAfter" to 21_600.0)
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(staleAfter = 21_600),
                items = emptyList(),
                empty = empty
            ), none
        )
    }

    /** One unit of the from currency in each of the others. The script writes each rate as text itself ("83.420"). */
    @Test
    fun exchange() {
        val spec = parse(
            "exchange.js",
            mapOf(
                "title" to "1 USD",
                "items" to listOf(
                    mapOf("title" to "EUR", "trailing" to "0.92000"),
                    mapOf("title" to "INR", "trailing" to "83.420"),
                    mapOf("title" to "JPY", "trailing" to "149.50"),
                ),
                "empty" to "No rates",
                "tap" to jsToLog,
                "staleAfter" to 43_200L,
            ),
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(title = "1 USD", tap = toLog, staleAfter = 43_200),
                items = listOf(
                    WidgetSpec.Row("EUR", trailing = "0.92000"),
                    WidgetSpec.Row("INR", trailing = "83.420"),
                    WidgetSpec.Row("JPY", trailing = "149.50")
                ),
                empty = "No rates",
            ),
            spec,
        )
        // None of its currencies is known: an empty array, and the card says "No rates".
        val none = parse(
            "exchange.js",
            mapOf(
                "title" to "1 USD",
                "items" to emptyList<Any?>(),
                "empty" to "No rates",
                "tap" to jsToLog,
                "staleAfter" to 43_200L
            )
        ) as WidgetSpec.Rows
        assertEquals(emptyList<WidgetSpec.Row>(), none.items)
        assertEquals("No rates", none.empty)
    }

    /** `device.unlocks(7)` gives whole numbers. Like every Lua number, they arrive as doubles. */
    @Test
    fun unlockChart() {
        fun week(vararg days: Double, max: Double, label: String, summary: String) = fromLua(
            "unlock-chart.lua",
            luaTable(
                "title" to "Unlocks",
                "value" to days.last(),
                "label" to label,
                "points" to luaList(*days.toTypedArray()),
                "style" to "bars",
                "min" to 0.0,
                "max" to max,
                "labels" to luaList("Sun", "Today"),
                "summary" to summary,
                "tap" to luaTable("args" to "week", "page" to "log", "label" to "Show the week"),
            ),
        )

        val tap = WidgetTap(args = "week", label = "Show the week", toLog = true)
        assertEquals(
            WidgetSpec.Chart(
                WidgetBase(title = "Unlocks", tap = tap),
                points = listOf(12f, 30f, 0f, 45f, 7f, 1f, 8f), style = "bars", min = 0f, max = 45f,
                value = "8", label = "Unlocks today", labels = listOf("Sun", "Today"),
                summary = "Sun 12, Mon 30, Tue 0, Wed 45, Thu 7, Fri 1, Today 8",
            ),
            week(
                12.0,
                30.0,
                0.0,
                45.0,
                7.0,
                1.0,
                8.0,
                max = 45.0,
                label = "Unlocks today",
                summary = "Sun 12, Mon 30, Tue 0, Wed 45, Thu 7, Fri 1, Today 8"
            ),
        )
        // A week with no unlocks: the script gives 1 as the top of the scale, so it stays above the bottom (0).
        val none = week(
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            max = 1.0,
            label = "Unlocks today",
            summary = "Sun 0, Mon 0, Tue 0, Wed 0, Thu 0, Fri 0, Today 0"
        ) as WidgetSpec.Chart
        assertEquals("0", none.value)
        assertEquals(listOf(0f, 1f), listOf(none.min, none.max))
    }

    @Test
    fun headlines() {
        val spec = parse(
            "headlines.js",
            mapOf(
                "title" to "Hacker News: Front Page",
                "items" to listOf(
                    mapOf("title" to "A story", "tap" to mapOf("url" to "https://a.example/x")),
                    // An item whose link isn't a web link has no tap, and one with no title says so.
                    mapOf("title" to "(untitled)"),
                ),
                "empty" to "No items", "staleAfter" to 7200L,
            ),
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(title = "Hacker News: Front Page", staleAfter = 7200),
                items = listOf(
                    WidgetSpec.Row(
                        "A story",
                        tap = WidgetTap(url = "https://a.example/x")
                    ), WidgetSpec.Row("(untitled)")
                ),
                empty = "No items",
            ),
            spec,
        )
        // It passes the feed's first 12 items: as many as a list keeps.
        val full = parse(
            "headlines.js",
            mapOf(
                "title" to "Headlines",
                "items" to List(12) { mapOf("title" to "Item $it") },
                "empty" to "No items"
            )
        ) as WidgetSpec.Rows
        assertEquals(12, full.items.size)
        // A feed with no items: an empty array.
        val none = parse(
            "headlines.js",
            mapOf("title" to "Headlines", "items" to emptyList<Any?>(), "empty" to "No items")
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(title = "Headlines"),
                items = emptyList(),
                empty = "No items"
            ), none
        )
    }

    /** The script writes the number as text itself ("67,123.46"), so the card shows it as it is. */
    @Test
    fun webnumber() {
        val spec = parse(
            "webnumber.js",
            mapOf(
                "title" to "Web number",
                "value" to "67,123.46",
                "label" to "BTC",
                "tap" to mapOf(
                    "url" to "https://api.example/price.json",
                    "label" to "Open the address"
                ),
                "staleAfter" to 7200L
            ),
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(
                    title = "Web number",
                    tap = WidgetTap(
                        url = "https://api.example/price.json",
                        label = "Open the address"
                    ),
                    staleAfter = 7200
                ),
                value = "67,123.46", label = "BTC",
            ),
            spec,
        )
        // The whole document is the number, and no label is set: an empty label is left out.
        val bare = parse(
            "webnumber.js",
            mapOf(
                "title" to "Web number",
                "value" to "2026",
                "label" to "",
                "tap" to mapOf("url" to "https://api.example/year", "label" to "Open the address")
            )
        ) as WidgetSpec.Stat
        assertEquals(null, bare.label)
        // Before its options are set, the card says which one to set. It has no tap of its own, so a tap runs the script again.
        for (option in listOf("url", "path")) {
            val label = "Set its $option option, then tap here"
            assertEquals(
                WidgetSpec.Stat(
                    WidgetBase(title = "Web number"),
                    value = "–",
                    label = label
                ),
                parse(
                    "webnumber.js",
                    mapOf("title" to "Web number", "value" to "–", "label" to label)
                )
            )
        }
    }

    @Test
    fun weather() {
        val spec = fromLua(
            "weather.lua",
            luaTable(
                "title" to "Pune", "icon" to "☀️", "value" to "25°C", "label" to "Sunny",
                "more" to luaList(
                    luaTable("value" to "36%", "label" to "humidity"),
                    luaTable("value" to "← 9 km/h", "label" to "wind")
                ),
                "tap" to luaToLog,
            ),
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(title = "Pune", icon = "☀️", tap = toLog),
                value = "25°C",
                label = "Sunny",
                more = listOf(
                    WidgetSpec.Small("36%", "humidity"),
                    WidgetSpec.Small("← 9 km/h", "wind")
                ),
            ),
            spec,
        )
    }

    /** wttr.in can leave a field empty. An empty icon or label is left out, and an empty value is still a value. */
    @Test
    fun weatherWithEmptyFields() {
        val spec = fromLua(
            "weather.lua",
            luaTable(
                "title" to "Pune", "icon" to "", "value" to "", "label" to "",
                "more" to luaList(
                    luaTable("value" to "", "label" to "humidity"),
                    luaTable("value" to "", "label" to "wind")
                ),
                "tap" to luaToLog,
            ),
        ) as WidgetSpec.Stat
        assertEquals("", spec.value)
        assertEquals(null, spec.base.icon)
        assertEquals(listOf("humidity", "wind"), spec.more.map { it.label })
    }

    @Test
    fun hn() {
        val spec = fromLua(
            "hn.lua",
            luaTable(
                "title" to "Hacker News", "empty" to "No stories",
                "items" to luaList(
                    luaTable(
                        "title" to "A story",
                        "trailing" to "120",
                        "tap" to luaTable("url" to "https://a.example/x")
                    ),
                    // A story whose link isn't a web link has no tap, and one without points has no trailing text.
                    luaTable("title" to "No link", "trailing" to "3"),
                    luaTable("title" to "(untitled)"),
                ),
            ),
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(title = "Hacker News"),
                items = listOf(
                    WidgetSpec.Row(
                        "A story",
                        trailing = "120",
                        tap = WidgetTap(url = "https://a.example/x")
                    ),
                    WidgetSpec.Row("No link", trailing = "3"),
                    WidgetSpec.Row("(untitled)"),
                ),
                empty = "No stories",
            ),
            spec,
        )
        // A feed with no stories: Lua's empty table, which is an empty list.
        val none = fromLua(
            "hn.lua",
            luaTable("title" to "Hacker News", "empty" to "No stories", "items" to luaTable())
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(title = "Hacker News"),
                items = emptyList(),
                empty = "No stories"
            ), none
        )
    }

    /** `os.time` gives whole seconds, which arrive as a double. */
    @Test
    fun countdown() {
        val spec = fromLua(
            "countdown.lua",
            luaTable(
                "title" to "Wedding anniversary · 26th",
                "label" to "To 15 June 2027",
                "style" to "days",
                "reached" to "Today",
                "to" to 1_812_997_800.0,
                "from" to 1_781_461_800.0,
                "tap" to luaToLog,
            ),
        )
        assertEquals(
            WidgetSpec.Countdown(
                WidgetBase(title = "Wedding anniversary · 26th", tap = toLog),
                to = 1_812_997_800,
                from = 1_781_461_800,
                label = "To 15 June 2027",
                style = "days",
                reached = "Today",
            ),
            spec,
        )
    }

    /**
     * Comic of the day: the latest comic whole, with its number and title under it. The comic's own
     * words are its alt text. A tap opens the comic on xkcd.com.
     */
    @Test
    fun xkcd() {
        val words = "Her daughter is named Help I'm trapped in a driver's license factory."
        fun comic(alt: String, title: String) = parse(
            "xkcd.js",
            mapOf(
                "image" to "https://imgs.xkcd.com/comics/exploits_of_a_mom.png",
                "alt" to alt,
                "caption" to "#327 $title",
                "fit" to "fit",
                "tap" to mapOf("url" to "https://xkcd.com/327/", "label" to "Open on xkcd.com"),
                "staleAfter" to 21_600L,
            ),
        ) as WidgetSpec.Image
        assertEquals(
            WidgetSpec.Image(
                WidgetBase(
                    tap = WidgetTap(
                        url = "https://xkcd.com/327/",
                        label = "Open on xkcd.com"
                    ), staleAfter = 21_600
                ),
                image = "https://imgs.xkcd.com/comics/exploits_of_a_mom.png",
                alt = words,
                caption = "#327 Exploits of a Mom",
                fit = "fit",
            ),
            comic(words, "Exploits of a Mom"),
        )
        // A comic's words can be long, and a title too: both are cut, not refused.
        val long = comic("w".repeat(900), "t".repeat(90))
        assertEquals(200, long.alt.length)
        assertEquals(60, long.caption!!.length)
        // Its @widget line: a picture, 4 cells wide and 2 rows tall.
        val code = sourceFile("src/main/assets/library/xkcd.js").readText()
        assertEquals(
            ScriptMeta.Widget(
                WidgetKind.IMAGE,
                com.mtwabpin.kolahal.widgets.WidgetSize.S4X2
            ), ScriptMeta.widget(code)
        )
        // It goes online, as before, and it has the widget's switch now.
        assertEquals(listOf(ScriptAccess.INTERNET), ScriptAccess.asksFor(code))
        assertEquals(true, ScriptAccess.WIDGET in ScriptAccess.items(code))
    }

    /** A tap on a score's card runs the script with "live" and turns home to the log. */
    private val liveTap = WidgetTap(args = "live", label = "Live matches", toLog = true)
    private val jsLiveTap = mapOf("args" to "live", "page" to "log", "label" to "Live matches")

    /** A side of a versus card, as the scores' scripts write one: its name and initials, and a part of the bar when there is one. */
    private fun side(name: String, mark: String, value: Any? = null, text: String? = null): Map<String, Any> =
        listOfNotNull("name" to name, "mark" to mark, value?.let { "value" to it }, text?.let { "text" to it }).toMap()

    /**
     * Live score, cricket: a row for each side with its initials, its own score and its overs, LIVE
     * or RESULT, and how the match stands as the last line. While the match is on, a dot by the side
     * batting, and the page asks again every `refresh` seconds. In a chase, the runs against the
     * target are the bar: the chasing side's runs, and the runs still to get.
     */
    @Test
    fun cricket() {
        // A side as cricket.js writes one: its score ("–" before it bats), its overs once it has batted.
        fun team(name: String, mark: String, score: String, overs: String? = null, value: Any? = null): Map<String, Any> =
            listOfNotNull("name" to name, "score" to score, "mark" to mark, overs?.let { "text" to it }, value?.let { "value" to it }).toMap()
        val chase = parse(
            "cricket.js",
            mapOf(
                "kind" to "versus",
                "icon" to "cricket",
                "sides" to listOf(team("India", "I", "237/4", "40 ov", 237L), team("New Zealand", "NZ", "299/8", "50 ov", 63L)),
                "badge" to "LIVE",
                "tap" to jsLiveTap,
                "staleAfter" to 60L,
                "caption" to "India require 63 runs with 60 balls remaining",
                "turn" to 1L,
                "moving" to true,
                "color" to "good",
                "refresh" to 30L,
            ),
        )
        assertEquals(
            WidgetSpec.Versus(
                WidgetBase(
                    icon = "cricket",
                    color = StyledText.ColorRole.GOOD,
                    tap = liveTap,
                    staleAfter = 60,
                    refresh = 30
                ),
                sides = listOf(
                    WidgetSpec.Side("India", "I", value = 237f, text = "40 ov", score = "237/4"),
                    WidgetSpec.Side("New Zealand", "NZ", value = 63f, text = "50 ov", score = "299/8"),
                ),
                turn = 1,
                moving = true,
                line = "India require 63 runs with 60 balls remaining",
                badge = "LIVE",
            ),
            chase,
        )
        // Before a chase there is no target: no bar. A side yet to bat has "–" and no overs. The fastest refresh is 10 seconds.
        val first = parse(
            "cricket.js",
            mapOf(
                "kind" to "versus",
                "icon" to "cricket",
                "sides" to listOf(team("India", "I", "–"), team("New Zealand", "NZ", "142/3", "28.4 ov")),
                "badge" to "LIVE",
                "tap" to jsLiveTap,
                "staleAfter" to 60L,
                "caption" to "New Zealand chose to bat",
                "turn" to 2L,
                "moving" to true,
                "color" to "good",
                "refresh" to 10L,
            ),
        ) as WidgetSpec.Versus
        assertEquals(listOf<Float?>(null, null), first.sides.map { it.value })
        assertEquals(listOf("–", "142/3"), first.sides.map { it.score })
        assertEquals(null, first.score)
        assertEquals(2, first.turn)
        assertEquals(10, first.base.refresh)
        // The result: it changes no more, so nothing moves and the page asks for nothing. A chase
        // that reached its target has the whole bar.
        val result = parse(
            "cricket.js",
            mapOf(
                "kind" to "versus",
                "icon" to "cricket",
                "sides" to listOf(team("India", "I", "301/6", "48.2 ov", 301L), team("New Zealand", "NZ", "299/8", "50 ov", 0L)),
                "badge" to "RESULT",
                "tap" to jsLiveTap,
                "staleAfter" to 900L,
                "caption" to "India won by 4 wickets",
            )
        )
        assertEquals(
            WidgetSpec.Versus(
                WidgetBase(icon = "cricket", tap = liveTap, staleAfter = 900),
                sides = listOf(
                    WidgetSpec.Side("India", "I", value = 301f, text = "48.2 ov", score = "301/6"),
                    WidgetSpec.Side("New Zealand", "NZ", value = 0f, text = "50 ov", score = "299/8"),
                ),
                line = "India won by 4 wickets",
                badge = "RESULT",
            ),
            result,
        )
        // Nothing to follow: one big value, as a stat.
        val none = parse(
            "cricket.js",
            mapOf(
                "kind" to "stat",
                "icon" to "cricket",
                "value" to "–",
                "label" to "No live match",
                "tap" to jsLiveTap,
                "staleAfter" to 900L
            )
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(icon = "cricket", tap = liveTap, staleAfter = 900),
                value = "–",
                label = "No live match"
            ), none
        )
    }

    /**
     * Live score, football: the two teams with their initials, home first, and the score between
     * them; the minute at the top and the last goal under it. When ESPN has possession, it is the bar.
     */
    @Test
    fun football() {
        val live = parse(
            "football.js",
            mapOf(
                "kind" to "versus",
                "icon" to "ball",
                "score" to "2–1",
                "sides" to listOf(side("Arsenal", "A", 58.3, "58%"), side("Chelsea", "C", 41.7, "42%")),
                "tap" to jsLiveTap,
                "staleAfter" to 60L,
                "badge" to "67'",
                "caption" to "64' Goal, B. Saka",
                "color" to "good",
                "refresh" to 30L,
            ),
        )
        assertEquals(
            WidgetSpec.Versus(
                WidgetBase(
                    icon = "ball",
                    color = StyledText.ColorRole.GOOD,
                    tap = liveTap,
                    staleAfter = 60,
                    refresh = 30
                ),
                sides = listOf(
                    WidgetSpec.Side("Arsenal", "A", value = 58.3f, text = "58%"),
                    WidgetSpec.Side("Chelsea", "C", value = 41.7f, text = "42%"),
                ),
                score = "2–1",
                line = "64' Goal, B. Saka",
                badge = "67'",
            ),
            live,
        )
        // No possession and no goal yet: no bar and no line.
        val start = parse(
            "football.js",
            mapOf(
                "kind" to "versus",
                "icon" to "ball",
                "score" to "0–0",
                "sides" to listOf(side("Arsenal", "A"), side("Chelsea", "C")),
                "tap" to jsLiveTap,
                "staleAfter" to 60L,
                "badge" to "1'",
                "color" to "good",
                "refresh" to 30L,
            ),
        ) as WidgetSpec.Versus
        assertEquals(listOf<Float?>(null, null), start.sides.map { it.value })
        assertEquals(null, start.line)
        val ended = parse(
            "football.js",
            mapOf(
                "kind" to "versus",
                "icon" to "ball",
                "score" to "2–1",
                "sides" to listOf(side("Arsenal", "A", 58.3, "58%"), side("Chelsea", "C", 41.7, "42%")),
                "tap" to jsLiveTap,
                "staleAfter" to 900L,
                "badge" to "FT",
                "caption" to "64' Goal, B. Saka",
            ),
        ) as WidgetSpec.Versus
        assertEquals(WidgetBase(icon = "ball", tap = liveTap, staleAfter = 900), ended.base)
        assertEquals("FT", ended.badge)
        val none = parse(
            "football.js",
            mapOf(
                "kind" to "stat",
                "icon" to "ball",
                "value" to "–",
                "label" to "No live match in eng.1",
                "tap" to jsLiveTap,
                "staleAfter" to 900L
            )
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(icon = "ball", tap = liveTap, staleAfter = 900),
                value = "–",
                label = "No live match in eng.1"
            ), none
        )
    }

    /** The scores' scripts start as a versus card 4 cells by 1 row, and have the widget's switch beside what they had. */
    @Test
    fun theScoresHaveAWideVersusWidget() {
        for (file in listOf("cricket.js", "football.js")) {
            val code = sourceFile("src/main/assets/library/$file").readText()
            assertEquals(
                file,
                ScriptMeta.Widget(WidgetKind.VERSUS, com.mtwabpin.kolahal.widgets.WidgetSize.S4X1),
                ScriptMeta.widget(code)
            )
            assertEquals(file, true, ScriptAccess.WIDGET in ScriptAccess.items(code))
            assertEquals(file, false, ScriptMeta.postsNotifications(code))
        }
    }

    @Test
    fun quote() {
        val spec = parse("quote.js", mapOf("text" to "Be kind", "caption" to "Someone"))
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "Be kind", caption = "Someone"), spec)
    }

    @Test
    fun crypto() {
        val spec = parse(
            "crypto.js",
            mapOf(
                "title" to "Prices in USD",
                "items" to listOf(
                    mapOf(
                        "title" to "BTC",
                        "trailing" to "67,120.40",
                        "detail" to "▼ 1.2%",
                        "color" to "bad"
                    ),
                    // A coin with no change for the day has no detail and no color.
                    mapOf("title" to "ETH", "trailing" to "3,100.50"),
                ),
                "empty" to "No prices",
                "tap" to jsToLog,
            ),
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(title = "Prices in USD", tap = toLog),
                items = listOf(
                    WidgetSpec.Row(
                        "BTC",
                        detail = "▼ 1.2%",
                        trailing = "67,120.40",
                        color = StyledText.ColorRole.BAD
                    ),
                    WidgetSpec.Row("ETH", trailing = "3,100.50"),
                ),
                empty = "No prices",
            ),
            spec,
        )
        // No coin had a price: an empty array.
        val none = parse(
            "crypto.js",
            mapOf(
                "title" to "Prices in USD",
                "items" to emptyList<Any?>(),
                "empty" to "No prices",
                "tap" to jsToLog
            )
        ) as WidgetSpec.Rows
        assertEquals(emptyList<WidgetSpec.Row>(), none.items)
    }

    /**
     * A gauge with a zone for each color of the scale's bands, the scale as its unit. The figure is a
     * whole number in JavaScript, which arrives as a long or as a double. Past the scale's top, the
     * arc is full.
     */
    @Test
    fun aqi() {
        fun spec(value: Any) = parse(
            "aqi.js",
            mapOf(
                "title" to "Pune", "icon" to "wind", "style" to "gauge", "value" to value, "max" to 300L,
                "unit" to "US AQI", "text" to "156", "label" to "Unhealthy", "color" to "bad",
                "zones" to listOf(
                    mapOf("from" to 0L, "color" to "good"),
                    mapOf("from" to 51L, "color" to "warn"),
                    mapOf("from" to 151L, "color" to "bad"),
                ),
                "tap" to jsToLog,
            ),
        )

        val expected = WidgetSpec.Progress(
            WidgetBase(title = "Pune", icon = "wind", color = StyledText.ColorRole.BAD, tap = toLog),
            value = 156f, max = 300f, text = "156", label = "Unhealthy", style = "gauge", unit = "US AQI",
            zones = listOf(
                WidgetSpec.GaugeZone(0f, StyledText.ColorRole.GOOD),
                WidgetSpec.GaugeZone(51f, StyledText.ColorRole.WARN),
                WidgetSpec.GaugeZone(151f, StyledText.ColorRole.BAD),
            ),
        )
        assertEquals(expected, spec(156L))
        assertEquals(expected, spec(156.0))
        assertEquals(300f, (spec(999L) as WidgetSpec.Progress).value)
    }

    /**
     * sun: the sun's path from today's sunrise to its sunset, in seconds, in the sun's color, once a
     * day. Where the sun doesn't rise or set that day, the day's length as a stat.
     */
    @Test
    fun sun() {
        val sky = parse(
            "sun.js",
            mapOf(
                "kind" to "sky", "title" to "Pune, IN", "icon" to "sun", "color" to "warn",
                "rises" to 1_795_483_200L, "sets" to 1_795_521_240L, "staleAfter" to 86_400L, "tap" to jsToLog,
            ),
        )
        assertEquals(
            WidgetSpec.Sky(
                WidgetBase(title = "Pune, IN", icon = "sun", color = StyledText.ColorRole.WARN, tap = toLog, staleAfter = 86_400),
                rises = 1_795_483_200L,
                sets = 1_795_521_240L,
            ),
            sky,
        )
        val polar = parse(
            "sun.js",
            mapOf(
                "kind" to "stat", "title" to "Tromsø, NO", "icon" to "sun", "value" to "0h 0m",
                "label" to "Of daylight", "staleAfter" to 86_400L, "tap" to jsToLog,
            ),
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(title = "Tromsø, NO", icon = "sun", tap = toLog, staleAfter = 86_400),
                value = "0h 0m",
                label = "Of daylight",
            ),
            polar,
        )
    }

    /** A row a day by its short name, with its sky, its low and its high, and its range for the bar. */
    @Test
    fun forecast() {
        fun day(title: String, icon: String, detail: String, low: Long, high: Long) =
            mapOf(
                "title" to title,
                "icon" to icon,
                "detail" to detail,
                "trailing" to "$low–$high°",
                "range" to mapOf("low" to low, "high" to high)
            )

        val spec = parse(
            "forecast.js",
            mapOf(
                "title" to "Pune, IN",
                "icon" to "sun",
                "items" to listOf(
                    day("Fri", "sun", "clear", 18, 30),
                    day("Sat", "rain", "rain", 17, 28),
                    day("Sun", "storm", "thunderstorm", 16, 26)
                ),
                "tap" to jsToLog,
            ),
        ) as WidgetSpec.Rows
        assertEquals(WidgetBase(title = "Pune, IN", icon = "sun", tap = toLog), spec.base)
        assertEquals(
            listOf(
                WidgetSpec.Row(
                    "Fri",
                    detail = "Clear",
                    trailing = "18–30°",
                    icon = "sun",
                    range = WidgetSpec.Range(18f, 30f)
                ),
                WidgetSpec.Row(
                    "Sat",
                    detail = "Rain",
                    trailing = "17–28°",
                    icon = "rain",
                    range = WidgetSpec.Range(17f, 28f)
                ),
                WidgetSpec.Row(
                    "Sun",
                    detail = "Thunderstorm",
                    trailing = "16–26°",
                    icon = "storm",
                    range = WidgetSpec.Range(16f, 26f)
                ),
            ),
            spec.items,
        )
        // A day's low and high as JavaScript's Math.round gives them, a double, read the same.
        val doubles = parse(
            "forecast.js",
            mapOf(
                "items" to listOf(
                    mapOf(
                        "title" to "Fri",
                        "range" to mapOf("low" to 18.0, "high" to 30.0)
                    )
                )
            )
        ) as WidgetSpec.Rows
        assertEquals(WidgetSpec.Range(18f, 30f), doubles.items.single().range)
    }

    /** Every symbol forecast.js has for the sky fits an icon whole: none is cut with "…". */
    @Test
    fun forecastsSymbolsFitAnIcon() {
        val code = sourceFile("src/main/assets/library/forecast.js").readText()
        val symbols = Regex("""return "([^"\w ]+)";""").findAll(
            code.substringAfter("function icon(code)").substringBefore("\n}")
        ).map { it.groupValues[1] }.toList()
        assertEquals(9, symbols.size)
        for (symbol in symbols) {
            val row = (parse(
                "forecast.js",
                mapOf("items" to listOf(mapOf("title" to "Today", "icon" to symbol)))
            ) as WidgetSpec.Rows).items.single()
            assertEquals(symbol, row.icon)
        }
    }

    /**
     * A library script that never calls `status(…)` doesn't say the word at all. Any mention counts
     * as setting a status line ([ScriptMeta.setsStatus]), so a comment such as "keeps no status line"
     * would let you pin Hacker News to the top of home, for a line it never sets.
     */
    @Test
    fun aScriptWithoutAStatusLineNeverNamesIt() {
        val call = Regex("""(?<![.:\w])status\s*\(""")
        val library = sourceFile("src/main/assets/library").listFiles()!!
            .filter { it.extension == "lua" || it.extension == "js" }
        for (f in library) {
            val code = f.readText()
            if (!call.containsMatchIn(code)) assertEquals(
                f.name,
                false,
                ScriptMeta.setsStatus(code)
            )
        }
        assertEquals(
            false,
            ScriptMeta.setsStatus(sourceFile("src/main/assets/library/hn.lua").readText())
        )
    }
}
