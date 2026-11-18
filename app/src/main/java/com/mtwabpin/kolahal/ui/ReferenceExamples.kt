package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.StyledText.Colour
import com.mtwabpin.kolahal.script.Fmt
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.Markdown
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import java.time.LocalDate
import java.time.ZoneId

/**
 * The widget and log examples of the scripting guide (site/src/content/docs/scripting/api.mdx), for
 * the script editor's API reference: each one's code in Lua and in JavaScript, and what it draws.
 * - The code is the guide's, as it is there. ReferenceExamplesTest checks that the guide has it.
 * - [Widget.spec] is the card and [Log.lines] the lines, made here: the reference runs no script to
 *   draw them. Device tests (ReferenceCardsTest, ReferenceLinesTest) run both codes and check that
 *   they make this card and these lines.
 * - The website's pictures come from the Lua code (DocsExamples, WidgetGalleryTest, LogGalleryTest).
 * - The picture example and the picture line aren't here: drawing them would fetch the picture.
 */
internal object ReferenceExamples {
    class Widget(
        /** Its name in the website's pictures: example-<name>-<size>.png. */
        val name: String,
        val kind: WidgetKind,
        val size: WidgetSize,
        /** The script's @title: the card's title when the code gives none. */
        val title: String,
        val lua: String,
        val js: String,
        /** The card that the code makes at [now] (seconds) in [zone]. */
        val spec: (now: Long, zone: ZoneId) -> WidgetSpec,
    ) {
        /** What the reference calls it: the kind, and what else it shows. */
        val heading: String get() = when (name) {
            "styles" -> "list, with styles"
            "water" -> "progress, with a tap"
            else -> kind.id
        }

        fun code(lang: ScriptLang): String = (if (lang == ScriptLang.LUA) lua else js).trimIndent()
    }

    val widgets: List<Widget> = listOf(
        Widget(
            "steps", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              value = "6,240",
              label = "steps today",
              more = { { value = "4.1 km", label = "walked" } },
            }
            """,
            js = """
            widget.show({
              value: "6,240",
              label: "steps today",
              more: [{ value: "4.1 km", label: "walked" }],
            });
            """,
        ) { _, _ -> WidgetSpec.Stat(WidgetBase(), value = "6,240", label = "steps today", more = listOf(WidgetSpec.Small("4.1 km", "walked"))) },
        Widget(
            "text", WidgetKind.TEXT, WidgetSize.S2X2, "Text",
            lua = """
            widget.show{ kind = "text", title = "Word of the day",
              text = "serendipity\nFinding something good without looking for it.",
              caption = "from Wiktionary" }
            """,
            js = """
            widget.show({ kind: "text", title: "Word of the day",
              text: "serendipity\nFinding something good without looking for it.",
              caption: "from Wiktionary" });
            """,
        ) { _, _ -> WidgetSpec.Text(WidgetBase(title = "Word of the day"), "serendipity\nFinding something good without looking for it.", "from Wiktionary") },
        Widget(
            "list", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            widget.show{
              title = "Reading list",
              items = {
                { title = "Kolahal's guide", trailing = "docs", tap = { url = "https://mtwabpin.github.io/kolahal/" } },
                { title = "No link here", detail = "A row needs only its title" },
              },
              empty = "Nothing to read",
            }
            """,
            js = """
            widget.show({
              title: "Reading list",
              items: [
                { title: "Kolahal's guide", trailing: "docs", tap: { url: "https://mtwabpin.github.io/kolahal/" } },
                { title: "No link here", detail: "A row needs only its title" },
              ],
              empty: "Nothing to read",
            });
            """,
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Reading list"),
                listOf(
                    WidgetSpec.Row("Kolahal's guide", trailing = "docs", tap = WidgetTap(url = "https://mtwabpin.github.io/kolahal/")),
                    WidgetSpec.Row("No link here", detail = "A row needs only its title"),
                ),
                empty = "Nothing to read",
            )
        },
        Widget(
            "progress", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{ kind = "progress", title = "Pune air", value = 168, max = 300, text = "168", label = "Unhealthy", color = "warn" }
            """,
            js = """
            widget.show({ kind: "progress", title: "Pune air", value: 168, max: 300, text: "168", label: "Unhealthy", color: "warn" });
            """,
        ) { _, _ -> WidgetSpec.Progress(WidgetBase(title = "Pune air", color = Colour.WARN), value = 168f, max = 300f, text = "168", label = "Unhealthy") },
        Widget(
            "chart", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            lua = """
            widget.show{ kind = "chart", title = "Unlocks", points = { 12, 30, 0, 45, 7, 1, 8 }, style = "bars",
              value = 8, label = "unlocks today", labels = { "Sun", "Today" } }
            """,
            js = """
            widget.show({ kind: "chart", title: "Unlocks", points: [12, 30, 0, 45, 7, 1, 8], style: "bars",
              value: 8, label: "unlocks today", labels: ["Sun", "Today"] });
            """,
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Unlocks"), listOf(12f, 30f, 0f, 45f, 7f, 1f, 8f), style = "bars",
                value = "8", label = "unlocks today", labels = listOf("Sun", "Today"),
            )
        },
        Widget(
            "countdown", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            lua = """
            widget.show{ kind = "countdown", title = "Goa trip", to = os.time{ year = 2026, month = 12, day = 20, hour = 0 } }
            """,
            js = """
            widget.show({ kind: "countdown", title: "Goa trip", to: new Date(2026, 11, 20).getTime() / 1000 });
            """,
        ) { _, zone -> WidgetSpec.Countdown(WidgetBase(title = "Goa trip"), to = LocalDate.of(2026, 12, 20).atStartOfDay(zone).toEpochSecond()) },
        Widget(
            "buttons", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            lua = """
            widget.show{ kind = "buttons", items = {
              { label = "Start", icon = "play", tap = { args = "start" } },
              { label = "Stop", icon = "pause", color = "bad", tap = { args = "stop" } },
              { label = "Help", tap = { url = "https://example.com/help" } },
            } }
            """,
            js = """
            widget.show({ kind: "buttons", items: [
              { label: "Start", icon: "play", tap: { args: "start" } },
              { label: "Stop", icon: "pause", color: "bad", tap: { args: "stop" } },
              { label: "Help", tap: { url: "https://example.com/help" } },
            ] });
            """,
        ) { _, _ ->
            WidgetSpec.Buttons(
                WidgetBase(),
                listOf(
                    WidgetSpec.Button("Start", WidgetTap(args = "start"), icon = "play"),
                    WidgetSpec.Button("Stop", WidgetTap(args = "stop"), icon = "pause", color = Colour.BAD),
                    WidgetSpec.Button("Help", WidgetTap(url = "https://example.com/help")),
                ),
            )
        },
        Widget(
            "toggles", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
            lua = """
            if event and event.name == "action" and args[1] == "dnd" then
              device.setDnd(device.dnd() == "off" and "on" or "off")
            end
            widget.show{ kind = "toggles", items = {
              { label = "Do Not Disturb", on = device.dnd() ~= "off", tap = { args = "dnd" } },
            } }
            """,
            js = """
            if (event && event.name === "action" && args[0] === "dnd") {
              device.setDnd(device.dnd() === "off" ? "on" : "off");
            }
            widget.show({ kind: "toggles", items: [
              { label: "Do Not Disturb", on: device.dnd() !== "off", tap: { args: "dnd" } },
            ] });
            """,
        ) { _, _ ->
            // A switch counts as old after a minute when the code gives no staleAfter.
            WidgetSpec.Toggles(WidgetBase(staleAfter = 60), listOf(WidgetSpec.Toggle("Do Not Disturb", on = true, tap = WidgetTap(args = "dnd"))))
        },
        Widget(
            "clock", WidgetKind.CLOCK, WidgetSize.S2X1, "Clock",
            lua = """
            widget.show{ kind = "clock", title = "Clocks", staleAfter = 604800, zones = {
              { zone = "Asia/Kolkata", label = "Pune" },
              { zone = "America/New_York" },
            } }
            """,
            js = """
            widget.show({ kind: "clock", title: "Clocks", staleAfter: 604800, zones: [
              { zone: "Asia/Kolkata", label: "Pune" },
              { zone: "America/New_York" },
            ] });
            """,
        ) { _, _ ->
            WidgetSpec.Clock(WidgetBase(title = "Clocks", staleAfter = 604800), listOf(WidgetSpec.Zone("Asia/Kolkata", "Pune"), WidgetSpec.Zone("America/New_York")))
        },
        Widget(
            "agenda", WidgetKind.AGENDA, WidgetSize.S4X2, "Agenda",
            lua = """
            local t = os.date("*t")
            local lunch = os.time{ year = t.year, month = t.month, day = t.day + 1, hour = 13 }
            local trip = os.time{ year = t.year, month = t.month, day = t.day + 2, hour = 0 }
            widget.show{ kind = "agenda", title = "Plans", days = 3, empty = "Nothing planned", events = {
              { title = "Lunch with Asha", starts = lunch, ends = lunch + 3600, where = "Cafe Goodluck" },
              { title = "Goa trip", starts = trip, allDay = true, color = "good" },
            } }
            """,
            js = """
            const t = new Date();
            const lunch = new Date(t.getFullYear(), t.getMonth(), t.getDate() + 1, 13).getTime() / 1000;
            const trip = new Date(t.getFullYear(), t.getMonth(), t.getDate() + 2).getTime() / 1000;
            widget.show({ kind: "agenda", title: "Plans", days: 3, empty: "Nothing planned", events: [
              { title: "Lunch with Asha", starts: lunch, ends: lunch + 3600, where: "Cafe Goodluck" },
              { title: "Goa trip", starts: trip, allDay: true, color: "good" },
            ] });
            """,
        ) { now, zone ->
            val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
            val lunch = today.plusDays(1).atTime(13, 0).atZone(zone).toEpochSecond()
            val trip = today.plusDays(2).atStartOfDay(zone).toEpochSecond()
            WidgetSpec.Agenda(
                WidgetBase(title = "Plans"),
                listOf(
                    WidgetSpec.Event("Lunch with Asha", lunch, ends = lunch + 3600, where = "Cafe Goodluck"),
                    WidgetSpec.Event("Goa trip", trip, allDay = true, color = Colour.GOOD),
                ),
                days = 3,
                empty = "Nothing planned",
            )
        },
        Widget(
            "styles", WidgetKind.LIST, WidgetSize.S4X1, "Styles",
            lua = """
            widget.show{ kind = "list", title = "Bills", items = {
              {
                title = fmt.bold("Rent") .. " to Asha",
                detail = "due in " .. fmt.color("warn", "2 days"),
                trailing = fmt.strike("520") .. " 480",
              },
            } }
            """,
            js = """
            widget.show({ kind: "list", title: "Bills", items: [
              {
                title: fmt.bold("Rent") + " to Asha",
                detail: "due in " + fmt.color("warn", "2 days"),
                trailing: fmt.strike("520") + " 480",
              },
            ] });
            """,
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Bills"),
                listOf(
                    WidgetSpec.Row(
                        StyledText.wrap(StyledText.Style.BOLD, "Rent") + " to Asha",
                        detail = "due in " + StyledText.colour(Colour.WARN, "2 days"),
                        trailing = StyledText.wrap(StyledText.Style.STRIKE, "520") + " 480",
                    ),
                ),
            )
        },
        Widget(
            "water", WidgetKind.PROGRESS, WidgetSize.S2X1, "Water",
            lua = """
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
            js = """
            const d = new Date();
            const today = `${'$'}{d.getFullYear()}-${'$'}{d.getMonth() + 1}-${'$'}{d.getDate()}`;
            let n = Number(store.get(today) || "0");
            if (event && event.name === "action" && args[0] === "add") {
              n += 1;
              store.set(today, String(n));
            }
            const goal = Number(options.goal) || 8;
            widget.show({
              value: n, max: goal,
              text: `${'$'}{n} / ${'$'}{goal}`,
              color: n >= goal ? "good" : "accent",
              tap: { args: "add", label: "Add a glass" },
            });
            """,
        ) { _, _ ->
            // Five glasses of eight so far today.
            WidgetSpec.Progress(WidgetBase(color = Colour.ACCENT, tap = WidgetTap(args = "add", label = "Add a glass")), value = 5f, max = 8f, text = "5 / 8")
        },
    )

    /**
     * One line a log example makes: its title and body, as `log` stores them, and what its context
     * holds (`url`, `gap`, `copy`, `actions`).
     */
    data class Line(
        val text: String,
        val body: String? = null,
        val url: String? = null,
        val gap: Boolean = false,
        val copy: String? = null,
        /** The buttons, as `log` keeps them: `[[label, args], …]` as JSON. */
        val actions: String? = null,
    ) {
        /** Its context, as a line's has it. */
        val ctx: Map<String, String>
            get() = listOfNotNull(
                LogContext.TITLE to "1",
                body?.let { LogContext.BODY to it },
                url?.let { LogContext.URL to it },
                (LogContext.GAP to "1").takeIf { gap },
                copy?.let { LogContext.COPY to it },
                actions?.let { LogContext.ACTIONS to it },
            ).toMap()
    }

    class Log(
        val name: String,
        /** What the reference calls it. */
        val heading: String,
        val lua: String,
        val js: String,
        /**
         * Code that sets what the example's code uses but doesn't set, such as `item`, in each
         * language. The [lines] show these values. Tests run it before the code.
         */
        val luaGiven: String = "",
        val jsGiven: String = "",
        val lines: List<Line>,
    ) {
        fun code(lang: ScriptLang): String = (if (lang == ScriptLang.LUA) lua else js).trimIndent()

        /** The lines as log entries of a script called "example", for the log's own line to draw. */
        fun entries(now: Long): List<LogEntry> = lines.mapIndexed { i, l ->
            LogEntry(-1L - i, now, LogKind.SCRIPT, "example", l.text, LogContext(l.ctx))
        }
    }

    private fun bold(text: String) = StyledText.wrap(StyledText.Style.BOLD, text)
    private fun code(text: String) = StyledText.wrap(StyledText.Style.CODE, text)
    private fun colour(colour: Colour, text: String) = StyledText.colour(colour, text)

    private val fmt = Fmt { v -> if (v is Double) Json.numberText(v) else v?.toString().orEmpty() }

    private const val STORY_1 = "A launcher that is a terminal"
    private const val STORY_2 = "Lua 5.5 is out"

    val logs: List<Log> = listOf(
        Log(
            "log", "log(title, body?, ctx?)",
            lua = """
            log("neovim/neovim", "Nvim 0.11.4", { url = "https://github.com/neovim/neovim/releases" })
            log("Pune, IN", "Today: clear, 24–31°C\nTomorrow: rain, 23–29°C")
            log("Kolahal on GitHub", { url = "https://github.com/mtwabpin/kolahal" })
            -- A list: each story a title with its points under it, and a small space between the stories.
            for _, s in ipairs(stories) do log(s.title, s.points .. " points", { url = s.link, gap = true }) end
            log("Your code is 4821", { copy = "4821" })
            """,
            js = """
            log("neovim/neovim", "Nvim 0.11.4", { url: "https://github.com/neovim/neovim/releases" });
            log("Pune, IN", "Today: clear, 24–31°C\nTomorrow: rain, 23–29°C");
            log("Kolahal on GitHub", { url: "https://github.com/mtwabpin/kolahal" });
            // A list: each story a title with its points under it, and a small space between the stories.
            for (const s of stories) log(s.title, `${'$'}{s.points} points`, { url: s.link, gap: true });
            log("Your code is 4821", { copy: "4821" });
            """,
            luaGiven = "local stories = { { title = \"$STORY_1\", points = 128, link = \"https://example.com/1\" }," +
                " { title = \"$STORY_2\", points = 342, link = \"https://example.com/2\" } }\n",
            jsGiven = "const stories = [{ title: \"$STORY_1\", points: 128, link: \"https://example.com/1\" }," +
                " { title: \"$STORY_2\", points: 342, link: \"https://example.com/2\" }];\n",
            lines = listOf(
                Line("neovim/neovim", "Nvim 0.11.4", url = "https://github.com/neovim/neovim/releases"),
                Line("Pune, IN", "Today: clear, 24–31°C\nTomorrow: rain, 23–29°C"),
                Line("Kolahal on GitHub", url = "https://github.com/mtwabpin/kolahal"),
                Line(STORY_1, "128 points", url = "https://example.com/1", gap = true),
                Line(STORY_2, "342 points", url = "https://example.com/2", gap = true),
                Line("Your code is 4821", copy = "4821"),
            ),
        ),
        Log(
            "logs", "logs(titles)",
            lua = """
            logs({ "milk", "bread", "eggs" })
            logs("milk", "bread")
            """,
            js = """
            logs(["milk", "bread", "eggs"]);
            logs("milk", "bread");
            """,
            lines = listOf("milk", "bread", "eggs", "milk", "bread").map { Line(it) },
        ),
        Log(
            "md", "md.log(title, body?, ctx?)",
            lua = """
            md.log("**3** new releases", "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119")
            md.log("**HN** " .. md.escape(item.title), nil, { url = item.link })
            """,
            js = """
            md.log("**3** new releases", "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119");
            md.log("**HN** " + md.escape(item.title), null, { url: item.link });
            """,
            luaGiven = "local item = { title = \"Show HN: Kolahal, a launcher\", link = \"https://example.com/hn\" }\n",
            jsGiven = "const item = { title: \"Show HN: Kolahal, a launcher\", link: \"https://example.com/hn\" };\n",
            lines = listOf(
                Line(Markdown.inline("**3** new releases"), Markdown.block("- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119")),
                Line(Markdown.inline("**HN** " + Markdown.escape("Show HN: Kolahal, a launcher")), url = "https://example.com/hn"),
            ),
        ),
        Log(
            "styles", "fmt: styles and colours",
            lua = """
            log(fmt.bold(item.score) .. " · " .. item.title, nil, { url = item.link })
            log("Today", fmt.list({ "standup " .. fmt.code("10:00"), "review" }, true))
            log("BTC 67,120 USD " .. fmt.color("good", "▲ 1.2%"))
            """,
            js = """
            log(`${'$'}{fmt.bold(item.score)} · ${'$'}{item.title}`, null, { url: item.link });
            log("Today", fmt.list([`standup ${'$'}{fmt.code("10:00")}`, "review"], true));
            log(`BTC 67,120 USD ${'$'}{fmt.color("good", "▲ 1.2%")}`);
            """,
            luaGiven = "local item = { score = 128, title = \"$STORY_1\", link = \"https://example.com/1\" }\n",
            jsGiven = "const item = { score: 128, title: \"$STORY_1\", link: \"https://example.com/1\" };\n",
            lines = listOf(
                Line(bold("128") + " · " + STORY_1, url = "https://example.com/1"),
                Line("Today", StyledText.list(listOf("standup " + code("10:00"), "review"), numbered = true)),
                Line("BTC 67,120 USD " + colour(Colour.GOOD, "▲ 1.2%")),
            ),
        ),
        Log(
            "table", "fmt.table(rows, opts?)",
            lua = """
            log("Prices in USD", fmt.table({
              { "Coin", "Price", "24h" },
              { "BTC", "64,210", fmt.color("good", "▲ 2.1%") },
              { "ETH", "3,118", fmt.color("bad", "▼ 0.8%") },
            }, { header = true, align = "lrr" }))
            """,
            js = """
            log("Prices in USD", fmt.table([
              ["Coin", "Price", "24h"],
              ["BTC", "64,210", fmt.color("good", "▲ 2.1%")],
              ["ETH", "3,118", fmt.color("bad", "▼ 0.8%")],
            ], { header: true, align: "lrr" }));
            """,
            lines = listOf(
                Line(
                    "Prices in USD",
                    StyledText.table(
                        listOf(
                            listOf("Coin", "Price", "24h"),
                            listOf("BTC", "64,210", colour(Colour.GOOD, "▲ 2.1%")),
                            listOf("ETH", "3,118", colour(Colour.BAD, "▼ 0.8%")),
                        ),
                        header = true, align = "lrr",
                    ),
                ),
            ),
        ),
        Log(
            "links", "fmt.link(text, url)",
            lua = """
            log(item.title .. " · " .. fmt.link("138 comments", item.comments), { url = item.link })
            md.log("New in [neovim](https://github.com/neovim/neovim/releases)")
            """,
            js = """
            log(`${'$'}{item.title} · ${'$'}{fmt.link("138 comments", item.comments)}`, { url: item.link });
            md.log("New in [neovim](https://github.com/neovim/neovim/releases)");
            """,
            luaGiven = "local item = { title = \"$STORY_1\", comments = \"https://example.com/c\", link = \"https://example.com/1\" }\n",
            jsGiven = "const item = { title: \"$STORY_1\", comments: \"https://example.com/c\", link: \"https://example.com/1\" };\n",
            lines = listOf(
                Line("$STORY_1 · " + StyledText.link("https://example.com/c", "138 comments"), url = "https://example.com/1"),
                Line(Markdown.inline("New in [neovim](https://github.com/neovim/neovim/releases)")),
            ),
        ),
        Log(
            "tags", "fmt.tag(role?, text)",
            lua = """
            log(fmt.tag("bad", "LIVE") .. " IND v NZ")
            log("neovim/neovim " .. fmt.tag("v0.12.0") .. " " .. fmt.tag("warn", "pre-release"))
            """,
            js = """
            log(`${'$'}{fmt.tag("bad", "LIVE")} IND v NZ`);
            log(`neovim/neovim ${'$'}{fmt.tag("v0.12.0")} ${'$'}{fmt.tag("warn", "pre-release")}`);
            """,
            lines = listOf(
                Line(StyledText.tag(Colour.BAD, "LIVE") + " IND v NZ"),
                Line("neovim/neovim " + StyledText.tag(Colour.ACCENT, "v0.12.0") + " " + StyledText.tag(Colour.WARN, "pre-release")),
            ),
        ),
        Log(
            "bars", "fmt.bar, fmt.bars, fmt.ranges",
            lua = """
            log("Pune · AQI 168", "Unhealthy\n" .. fmt.bar(168, { max = 300, color = "bad" }))
            log("Unlocks", fmt.bars({ { "Mon", 42 }, { "Tue", 21 } }, { color = "good" }))
            log("Pune · 3 days", fmt.ranges({ { "Today", 23, 31 }, { "Tomorrow", 22, 30 } }, { unit = "°", color = "warn" }))
            """,
            js = """
            log("Pune · AQI 168", "Unhealthy\n" + fmt.bar(168, { max: 300, color: "bad" }));
            log("Unlocks", fmt.bars([["Mon", 42], ["Tue", 21]], { color: "good" }));
            log("Pune · 3 days", fmt.ranges([["Today", 23, 31], ["Tomorrow", 22, 30]], { unit: "°", color: "warn" }));
            """,
            lines = listOf(
                Line("Pune · AQI 168", "Unhealthy\n" + fmt.bar(listOf(168.0, mapOf("max" to 300.0, "color" to "bad")))),
                Line("Unlocks", fmt.bars(listOf(listOf(listOf("Mon", 42.0), listOf("Tue", 21.0)), mapOf("color" to "good")))),
                Line(
                    "Pune · 3 days",
                    fmt.ranges(listOf(listOf(listOf("Today", 23.0, 31.0), listOf("Tomorrow", 22.0, 30.0)), mapOf("unit" to "°", "color" to "warn"))),
                ),
            ),
        ),
        Log(
            "stats", "fmt.stats(items)",
            lua = """
            log("Pune", fmt.stats({ { "25°C", "☀️ Sunny" }, { "36%", "humidity" }, { "9 km/h", "wind" } }))
            """,
            js = """
            log("Pune", fmt.stats([["25°C", "☀️ Sunny"], ["36%", "humidity"], ["9 km/h", "wind"]]));
            """,
            lines = listOf(Line("Pune", StyledText.stats(listOf("25°C" to "☀️ Sunny", "36%" to "humidity", "9 km/h" to "wind")))),
        ),
        Log(
            "buttons", "ctx.actions: buttons",
            lua = """
            log("Battery at 92%", "Unplug the charger.", { actions = { { "Snooze 30 min", "snooze 30" }, { "Not today", "snooze 720" } } })
            """,
            js = """
            log("Battery at 92%", "Unplug the charger.", { actions: [["Snooze 30 min", "snooze 30"], ["Not today", "snooze 720"]] });
            """,
            lines = listOf(
                Line("Battery at 92%", "Unplug the charger.", actions = Json.stringify(listOf(listOf("Snooze 30 min", "snooze 30"), listOf("Not today", "snooze 720")))),
            ),
        ),
    )
}
