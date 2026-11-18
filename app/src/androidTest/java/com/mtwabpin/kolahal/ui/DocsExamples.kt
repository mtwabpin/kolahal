package com.mtwabpin.kolahal.ui

import android.content.Context
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptEngine
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSpec
import kotlinx.coroutines.runBlocking

/**
 * The examples of the scripting guide (site/src/content/docs/scripting/api.mdx) that the website
 * shows as pictures. Each [code] is the guide's Lua code, as it is there: change both together.
 * [before] is code run first that the guide leaves out, such as a stand-in for the network.
 */
object DocsExamples {
    class Widget(
        val name: String,
        val kind: WidgetKind,
        val size: WidgetSize,
        /** The script's @title: the card's title when the code gives none. */
        val title: String,
        val code: String,
        val before: String = "",
        val options: Map<String, String> = emptyMap(),
    )

    class Log(val name: String, val code: String, val before: String = "")

    /** The picture link of the image example. The gallery puts a picture there, so nothing is fetched. */
    const val PICTURE = "https://example.com/pictures/1432.jpg"

    /** The xkcd link of the log example's picture line. The gallery puts a drawing there. */
    const val COMIC = "https://imgs.xkcd.com/comics/exploits_of_a_mom.png"

    val widgets = listOf(
        Widget(
            "steps", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            """
            widget.show{
              value = "6,240",
              label = "steps today",
              more = { { value = "4.1 km", label = "walked" } },
            }
            """,
        ),
        Widget(
            "text", WidgetKind.TEXT, WidgetSize.S2X2, "Text",
            """
            widget.show{ kind = "text", title = "Word of the day",
              text = "serendipity\nFinding something good without looking for it.",
              caption = "from Wiktionary" }
            """,
        ),
        Widget(
            "list", WidgetKind.LIST, WidgetSize.S4X2, "List",
            """
            widget.show{
              title = "Reading list",
              items = {
                { title = "Lua 5.4 manual", trailing = "docs", tap = { url = "https://www.lua.org/manual/5.4/" } },
                { title = "No link here", detail = "A row needs only its title" },
              },
              empty = "Nothing to read",
            }
            """,
        ),
        Widget(
            "progress", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            """
            widget.show{ kind = "progress", title = "Pune air", value = 168, max = 300, text = "168", label = "Unhealthy", color = "warn" }
            """,
        ),
        Widget(
            "chart", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            """
            widget.show{ kind = "chart", title = "Unlocks", points = { 12, 30, 0, 45, 7, 1, 8 }, style = "bars",
              value = 8, label = "unlocks today", labels = { "Sun", "Today" } }
            """,
        ),
        Widget(
            "countdown", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            """
            widget.show{ kind = "countdown", title = "Goa trip", to = os.time{ year = 2026, month = 12, day = 20, hour = 0 } }
            """,
        ),
        Widget(
            "buttons", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            """
            widget.show{ kind = "buttons", items = {
              { label = "Start", icon = "play", tap = { args = "start" } },
              { label = "Stop", icon = "pause", color = "bad", tap = { args = "stop" } },
              { label = "Help", tap = { url = "https://example.com/help" } },
            } }
            """,
        ),
        Widget(
            "toggles", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
            """
            if event and event.name == "action" and args[1] == "dnd" then
              device.setDnd(device.dnd() == "off" and "on" or "off")
            end
            widget.show{ kind = "toggles", items = {
              { label = "Do Not Disturb", on = device.dnd() ~= "off", tap = { args = "dnd" } },
            } }
            """,
            // Do Not Disturb is on: the picture shows a switch that is on.
            before = "device = { dnd = function() return \"priority\" end }\n",
        ),
        Widget(
            "clock", WidgetKind.CLOCK, WidgetSize.S2X1, "Clock",
            """
            widget.show{ kind = "clock", title = "Clocks", staleAfter = 604800, zones = {
              { zone = "Asia/Kolkata", label = "Pune" },
              { zone = "America/New_York" },
            } }
            """,
        ),
        Widget(
            "agenda", WidgetKind.AGENDA, WidgetSize.S4X2, "Agenda",
            """
            local t = os.date("*t")
            local lunch = os.time{ year = t.year, month = t.month, day = t.day + 1, hour = 13 }
            local trip = os.time{ year = t.year, month = t.month, day = t.day + 2, hour = 0 }
            widget.show{ kind = "agenda", title = "Plans", days = 3, empty = "Nothing planned", events = {
              { title = "Lunch with Asha", starts = lunch, ends = lunch + 3600, where = "Cafe Goodluck" },
              { title = "Goa trip", starts = trip, allDay = true, color = "good" },
            } }
            """,
        ),
        Widget(
            "image", WidgetKind.IMAGE, WidgetSize.S4X2, "Picture of the day",
            """
            local res = http.cached("https://example.com/today.json", 3600)
            local today = json.decode(res.body)
            widget.show{
              image = today.url,                 -- "https://example.com/pictures/1432.jpg"
              alt = today.description,           -- "Hills at sunrise"
              caption = today.title,
              tap = { url = today.page, label = "open the page" },
            }
            """,
            before = "http = { cached = function() return { ok = true, status = 200, body = [[" +
                """{"url":"$PICTURE","description":"Hills at sunrise","title":"Sunrise over the ghats","page":"https://example.com/pictures/1432"}""" +
                "]] } end }\n",
        ),
        Widget(
            "styles", WidgetKind.LIST, WidgetSize.S4X1, "Styles",
            """
            widget.show{ kind = "list", title = "Bills", items = {
              {
                title = fmt.bold("Rent") .. " to Asha",
                detail = "due in " .. fmt.color("warn", "2 days"),
                trailing = fmt.strike("520") .. " 480",
              },
            } }
            """,
        ),
        Widget(
            "water", WidgetKind.PROGRESS, WidgetSize.S2X1, "Water",
            """
            local today = os.date("%Y-%m-%d")
            local n = tonumber(store.get(today) or "0")
            if event and event.name == "action" and args[1] == "add" then
              n = n + 1
              store.set(today, tostring(n))
            end
            local goal = tonumber(options.goal) or 8
            widget.show{
              value = n, max = goal,
              text = n .. " / " .. goal,
              color = n >= goal and "good" or "accent",
              tap = { args = "add", label = "Add a glass" },
            }
            """,
            // Five glasses so far today.
            before = "store.set(os.date(\"%Y-%m-%d\"), \"5\")\n",
            options = mapOf("goal" to "8"),
        ),
    )

    val logs = listOf(
        Log(
            "log",
            """
            log("neovim/neovim", "Nvim 0.11.4", { url = "https://github.com/neovim/neovim/releases" })
            log("Pune, IN", "Today: clear, 24–31°C\nTomorrow: rain, 23–29°C")
            log("Kolahal on GitHub", { url = "https://github.com/mtwabpin/kolahal" })
            -- A list: each story a title with its points under it, and a small space between the stories.
            for _, s in ipairs(stories) do log(s.title, s.points .. " points", { url = s.link, gap = true }) end
            log("Your code is 4821", { copy = "4821" })
            log("xkcd #327", { image = "https://imgs.xkcd.com/comics/exploits_of_a_mom.png" })
            print("print() also logs", 42)   -- arguments joined with tabs
            """,
            before = "local stories = { { title = \"A launcher that is a terminal\", points = 128, link = \"https://example.com/1\" }," +
                " { title = \"Lua 5.5 is out\", points = 342, link = \"https://example.com/2\" } }\n",
        ),
        Log(
            "logs",
            """
            logs({ "milk", "bread", "eggs" })
            """,
        ),
        Log(
            "md",
            """
            md.log("**3** new releases", "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119")
            md.log("**HN** " .. md.escape(item.title), nil, { url = item.link })
            """,
            before = "local item = { title = \"Show HN: Kolahal, a launcher\", link = \"https://example.com/hn\" }\n",
        ),
        Log(
            "styles",
            """
            log(fmt.bold(item.score) .. " · " .. item.title, nil, { url = item.link })
            log("Today", fmt.list({ "standup " .. fmt.code("10:00"), "review" }, true))
            log("BTC 67,120 USD " .. fmt.color("good", "▲ 1.2%"))
            """,
            before = "local item = { score = 128, title = \"A launcher that is a terminal\", link = \"https://example.com/1\" }\n",
        ),
        Log(
            "table",
            """
            log("Prices in USD", fmt.table({
              { "Coin", "Price", "24h" },
              { "BTC", "64,210", fmt.color("good", "▲ 2.1%") },
              { "ETH", "3,118", fmt.color("bad", "▼ 0.8%") },
            }, { header = true, align = "lrr" }))
            """,
        ),
        Log(
            "links",
            """
            log(item.title .. " · " .. fmt.link("138 comments", item.comments), { url = item.link })
            md.log("New in [neovim](https://github.com/neovim/neovim/releases)")
            """,
            before = "local item = { title = \"A launcher that is a terminal\", comments = \"https://example.com/c\", link = \"https://example.com/1\" }\n",
        ),
        Log(
            "tags",
            """
            log(fmt.tag("bad", "LIVE") .. " IND v NZ")
            log("neovim/neovim " .. fmt.tag("v0.12.0") .. " " .. fmt.tag("warn", "pre-release"))
            """,
        ),
        Log(
            "bars",
            """
            log("Pune · AQI 168", "Unhealthy\n" .. fmt.bar(168, { max = 300, color = "bad" }))
            log("Unlocks", fmt.bars({ { "Mon", 42 }, { "Tue", 21 } }, { color = "good" }))
            log("Pune · 3 days", fmt.ranges({ { "Today", 23, 31 }, { "Tomorrow", 22, 30 } }, { unit = "°", color = "warn" }))
            """,
        ),
        Log(
            "stats",
            """
            log("Pune", fmt.stats({ { "25°C", "☀️ Sunny" }, { "36%", "humidity" }, { "9 km/h", "wind" } }))
            """,
        ),
        Log(
            "buttons",
            """
            if event and event.name == "action" then
              if args[1] == "snooze" then store.set("until", tostring(os.time() + tonumber(args[2]) * 60)) end
              return log("Snoozed for " .. args[2] .. " min")
            end
            log("Battery at 92%", "Unplug the charger.", { actions = { { "Snooze 30 min", "snooze 30" }, { "Not today", "snooze 720" } } })
            """,
        ),
    )

    /** Keeps what the code hands to `widget.show` in the script's store, as JSON, and passes it on. */
    private const val KEEPS_SHOWN = "do local show = widget.show; widget = { show = function(d) store.set('_shown', json.encode(d)); return show(d) end } end\n"

    /**
     * Runs [ex]'s code as a script with its @widget line, and returns its card as `widget.show`
     * checks it. The widget isn't placed: nothing is stored but the script's own store, which is
     * emptied after.
     */
    fun spec(context: Context, ex: Widget): WidgetSpec {
        val id = 910_000_000L + System.nanoTime() % 1_000_000
        val store = KvStore(context, ScriptRepository.scopeOf(id))
        try {
            val host = HostApi(context, "docs-${ex.name}", scriptId = id, notifyEnabled = false, options = ex.options, widget = ScriptMeta.Widget(ex.kind, ex.size))
            runBlocking { ScriptEngine.forLang(ScriptLang.LUA).run(KEEPS_SHOWN + ex.before + ex.code.trimIndent(), "docs-${ex.name}", host, 10_000, false, emptyList(), null) }
            host.flush()
            val json = checkNotNull(store.get("_shown")) { "${ex.name}: the code called no widget.show" }
            return WidgetParser.parse(Json.parse(json) as Map<*, *>, ex.kind, text = { it }, link = { it })
        } finally {
            store.all().keys.forEach { store.set(it, null) }
        }
    }
}
