package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import org.junit.Assert.assertEquals
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
    private fun kind(file: String): WidgetKind = ScriptMeta.widget(sourceFile("src/main/assets/library/$file").readText())!!.kind

    /** A link, as the host lets it pass: a web link only. */
    private fun link(url: String): String = url.also { require(it.startsWith("https://")) { "widget: not a web link" } }

    private fun parse(file: String, raw: Map<*, *>): WidgetSpec = WidgetParser.parse(raw, kind(file), { it }, ::link)

    private fun luaTable(vararg fields: Pair<String, Any?>): Map<*, *> = mapOf(*fields)

    private fun luaList(vararg items: Any?): Map<*, *> = items.withIndex().associate { (i, item) -> (i + 1).toDouble() to item }

    private fun fromLua(file: String, table: Map<*, *>): WidgetSpec = parse(file, Json.normalizeLua(table) as Map<*, *>)

    /** A tap that runs the script again with no arguments and turns home to the log. */
    private val toLog = WidgetTap(args = "", toLog = true)
    private val luaToLog = luaTable("args" to "", "page" to "log")
    private val jsToLog = mapOf("args" to "", "page" to "log")

    @Test fun everyScriptWithAWidgetIsHere() {
        val withWidget = sourceFile("src/main/assets/library").listFiles().orEmpty()
            .filter { ScriptMeta.widget(it.readText()) != null }.map { it.name }.sorted()
        assertEquals(
            listOf("aqi.js", "countdown.lua", "crypto.js", "forecast.js", "headlines.js", "hn.lua", "quote.js", "unlock-chart.lua", "weather.lua", "webnumber.js"),
            withWidget,
        )
    }

    /** `device.unlocks(7)` gives whole numbers. Like every Lua number, they arrive as doubles. */
    @Test fun unlockChart() {
        fun week(vararg days: Double, max: Double, label: String, summary: String) = fromLua(
            "unlock-chart.lua",
            luaTable(
                "title" to "Unlocks", "value" to days.last(), "label" to label, "points" to luaList(*days.toTypedArray()), "style" to "bars",
                "min" to 0.0, "max" to max, "labels" to luaList("Sun", "Today"), "summary" to summary,
                "tap" to luaTable("args" to "week", "page" to "log", "label" to "Show the week"),
            ),
        )
        val tap = WidgetTap(args = "week", label = "Show the week", toLog = true)
        assertEquals(
            WidgetSpec.Chart(
                WidgetBase(title = "Unlocks", tap = tap),
                points = listOf(12f, 30f, 0f, 45f, 7f, 1f, 8f), style = "bars", min = 0f, max = 45f,
                value = "8", label = "unlocks today", labels = listOf("Sun", "Today"),
                summary = "Sun 12, Mon 30, Tue 0, Wed 45, Thu 7, Fri 1, Today 8",
            ),
            week(12.0, 30.0, 0.0, 45.0, 7.0, 1.0, 8.0, max = 45.0, label = "unlocks today", summary = "Sun 12, Mon 30, Tue 0, Wed 45, Thu 7, Fri 1, Today 8"),
        )
        // A week with no unlocks: the script gives 1 as the top of the scale, so it stays above the bottom (0).
        val none = week(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, max = 1.0, label = "unlocks today", summary = "Sun 0, Mon 0, Tue 0, Wed 0, Thu 0, Fri 0, Today 0") as WidgetSpec.Chart
        assertEquals("0", none.value)
        assertEquals(listOf(0f, 1f), listOf(none.min, none.max))
    }

    @Test fun headlines() {
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
                items = listOf(WidgetSpec.Row("A story", tap = WidgetTap(url = "https://a.example/x")), WidgetSpec.Row("(untitled)")),
                empty = "No items",
            ),
            spec,
        )
        // It passes the feed's first 12 items: as many as a list keeps.
        val full = parse("headlines.js", mapOf("title" to "Headlines", "items" to List(12) { mapOf("title" to "Item $it") }, "empty" to "No items")) as WidgetSpec.Rows
        assertEquals(12, full.items.size)
        // A feed with no items: an empty array.
        val none = parse("headlines.js", mapOf("title" to "Headlines", "items" to emptyList<Any?>(), "empty" to "No items"))
        assertEquals(WidgetSpec.Rows(WidgetBase(title = "Headlines"), items = emptyList(), empty = "No items"), none)
    }

    /** The script writes the number as text itself ("67,123.46"), so the card shows it as it is. */
    @Test fun webnumber() {
        val spec = parse(
            "webnumber.js",
            mapOf("title" to "Web number", "value" to "67,123.46", "label" to "BTC", "tap" to mapOf("url" to "https://api.example/price.json", "label" to "Open the address"), "staleAfter" to 7200L),
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(title = "Web number", tap = WidgetTap(url = "https://api.example/price.json", label = "Open the address"), staleAfter = 7200),
                value = "67,123.46", label = "BTC",
            ),
            spec,
        )
        // The whole document is the number, and no label is set: an empty label is left out.
        val bare = parse("webnumber.js", mapOf("title" to "Web number", "value" to "2026", "label" to "", "tap" to mapOf("url" to "https://api.example/year", "label" to "Open the address"))) as WidgetSpec.Stat
        assertEquals(null, bare.label)
        // Before its options are set, the card says which one to set. It has no tap of its own, so a tap runs the script again.
        for (option in listOf("url", "path")) {
            val label = "Set its $option option, then tap here"
            assertEquals(WidgetSpec.Stat(WidgetBase(title = "Web number"), value = "–", label = label), parse("webnumber.js", mapOf("title" to "Web number", "value" to "–", "label" to label)))
        }
    }

    @Test fun weather() {
        val spec = fromLua(
            "weather.lua",
            luaTable(
                "title" to "Pune", "icon" to "☀️", "value" to "25°C", "label" to "Sunny",
                "more" to luaList(luaTable("value" to "36%", "label" to "humidity"), luaTable("value" to "← 9 km/h", "label" to "wind")),
                "tap" to luaToLog,
            ),
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(title = "Pune", icon = "☀️", tap = toLog), value = "25°C", label = "Sunny",
                more = listOf(WidgetSpec.Small("36%", "humidity"), WidgetSpec.Small("← 9 km/h", "wind")),
            ),
            spec,
        )
    }

    /** wttr.in can leave a field empty. An empty icon or label is left out, and an empty value is still a value. */
    @Test fun weatherWithEmptyFields() {
        val spec = fromLua(
            "weather.lua",
            luaTable(
                "title" to "Pune", "icon" to "", "value" to "", "label" to "",
                "more" to luaList(luaTable("value" to "", "label" to "humidity"), luaTable("value" to "", "label" to "wind")),
                "tap" to luaToLog,
            ),
        ) as WidgetSpec.Stat
        assertEquals("", spec.value)
        assertEquals(null, spec.base.icon)
        assertEquals(listOf("humidity", "wind"), spec.more.map { it.label })
    }

    @Test fun hn() {
        val spec = fromLua(
            "hn.lua",
            luaTable(
                "title" to "Hacker News", "empty" to "No stories",
                "items" to luaList(
                    luaTable("title" to "A story", "trailing" to "120", "tap" to luaTable("url" to "https://a.example/x")),
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
                    WidgetSpec.Row("A story", trailing = "120", tap = WidgetTap(url = "https://a.example/x")),
                    WidgetSpec.Row("No link", trailing = "3"),
                    WidgetSpec.Row("(untitled)"),
                ),
                empty = "No stories",
            ),
            spec,
        )
        // A feed with no stories: Lua's empty table, which is an empty list.
        val none = fromLua("hn.lua", luaTable("title" to "Hacker News", "empty" to "No stories", "items" to luaTable()))
        assertEquals(WidgetSpec.Rows(WidgetBase(title = "Hacker News"), items = emptyList(), empty = "No stories"), none)
    }

    /** `os.time` gives whole seconds, which arrive as a double. */
    @Test fun countdown() {
        val spec = fromLua(
            "countdown.lua",
            luaTable(
                "title" to "Wedding anniversary · 26th", "label" to "15 Jun 2027", "style" to "days", "reached" to "Today",
                "to" to 1_812_997_800.0, "tap" to luaToLog,
            ),
        )
        assertEquals(
            WidgetSpec.Countdown(
                WidgetBase(title = "Wedding anniversary · 26th", tap = toLog),
                to = 1_812_997_800, label = "15 Jun 2027", style = "days", reached = "Today",
            ),
            spec,
        )
    }

    @Test fun quote() {
        val spec = parse("quote.js", mapOf("text" to "“Be kind”", "caption" to "Someone"))
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "“Be kind”", caption = "Someone"), spec)
    }

    @Test fun crypto() {
        val spec = parse(
            "crypto.js",
            mapOf(
                "title" to "Prices in USD",
                "items" to listOf(
                    mapOf("title" to "BTC", "trailing" to "67,120.40", "detail" to "▼ 1.2%", "color" to "bad"),
                    // A coin with no change for the day has no detail and no colour.
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
                    WidgetSpec.Row("BTC", detail = "▼ 1.2%", trailing = "67,120.40", color = StyledText.Colour.BAD),
                    WidgetSpec.Row("ETH", trailing = "3,100.50"),
                ),
                empty = "No prices",
            ),
            spec,
        )
        // No coin had a price: an empty array.
        val none = parse("crypto.js", mapOf("title" to "Prices in USD", "items" to emptyList<Any?>(), "empty" to "No prices", "tap" to jsToLog)) as WidgetSpec.Rows
        assertEquals(emptyList<WidgetSpec.Row>(), none.items)
    }

    /** The figure is a whole number in JavaScript, which arrives as a long or as a double. Past the scale's top, the ring is full. */
    @Test fun aqi() {
        fun spec(value: Any) = parse(
            "aqi.js",
            mapOf(
                "title" to "US AQI · Pune", "value" to value, "max" to 300L, "text" to "156",
                "label" to "Unhealthy", "color" to "bad", "tap" to jsToLog,
            ),
        )
        val expected = WidgetSpec.Progress(
            WidgetBase(title = "US AQI · Pune", color = StyledText.Colour.BAD, tap = toLog),
            value = 156f, max = 300f, text = "156", label = "Unhealthy",
        )
        assertEquals(expected, spec(156L))
        assertEquals(expected, spec(156.0))
        assertEquals(300f, (spec(999L) as WidgetSpec.Progress).value)
    }

    @Test fun forecast() {
        val spec = parse(
            "forecast.js",
            mapOf(
                "title" to "Pune, IN",
                "items" to listOf(
                    mapOf("title" to "Today", "icon" to "☀️", "detail" to "clear", "trailing" to "18–30°"),
                    mapOf("title" to "Tomorrow", "icon" to "🌧️", "detail" to "rain", "trailing" to "17–28°"),
                    mapOf("title" to "Mon", "icon" to "⛈️", "detail" to "thunderstorm", "trailing" to "16–26°"),
                ),
                "tap" to jsToLog,
            ),
        ) as WidgetSpec.Rows
        assertEquals(WidgetBase(title = "Pune, IN", tap = toLog), spec.base)
        assertEquals(
            listOf(
                WidgetSpec.Row("Today", detail = "clear", trailing = "18–30°", icon = "☀️"),
                WidgetSpec.Row("Tomorrow", detail = "rain", trailing = "17–28°", icon = "🌧️"),
                WidgetSpec.Row("Mon", detail = "thunderstorm", trailing = "16–26°", icon = "⛈️"),
            ),
            spec.items,
        )
    }

    /** Every symbol forecast.js has for the sky fits an icon whole: none is cut with "…". */
    @Test fun forecastsSymbolsFitAnIcon() {
        val code = sourceFile("src/main/assets/library/forecast.js").readText()
        val symbols = Regex("""return "([^"\w ]+)";""").findAll(code.substringAfter("function icon(code)").substringBefore("\n}")).map { it.groupValues[1] }.toList()
        assertEquals(9, symbols.size)
        for (symbol in symbols) {
            val row = (parse("forecast.js", mapOf("items" to listOf(mapOf("title" to "Today", "icon" to symbol)))) as WidgetSpec.Rows).items.single()
            assertEquals(symbol, row.icon)
        }
    }
}
