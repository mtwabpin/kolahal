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
import java.time.temporal.ChronoUnit

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
        /**
         * For a variant ([variants]): the name of the guide's example it varies, and what it shows
         * differently ("4 switches"), for its chip in the reference. Null for the guide's examples.
         */
        val of: String? = null,
        val variant: String? = null,
        /** The card that the code makes at [now] (seconds) in [zone]. */
        val spec: (now: Long, zone: ZoneId) -> WidgetSpec,
    ) {
        /** What the reference calls it: the kind, as `kind` names it in the code. */
        val heading: String get() = kind.id

        /**
         * The line under its name in the reference: what the kind shows, or for the two examples that
         * show something more, what that is.
         */
        val line: String get() = when (name) {
            "styles" -> "Rows with styles: bold, a colour, struck-through text"
            "water" -> "A tap that adds a glass, kept in the store"
            else -> ReferenceSearch.kindLine(kind)
        }

        fun code(lang: ScriptLang): String = (if (lang == ScriptLang.LUA) lua else js).trimIndent()
    }

    val widgets: List<Widget> = listOf(
        Widget(
            "steps", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              value = "6,240",
              label = "Steps today",
              more = { { value = "4.1 km", label = "walked" } },
            }
            """,
            js = """
            widget.show({
              value: "6,240",
              label: "Steps today",
              more: [{ value: "4.1 km", label: "walked" }],
            });
            """,
        ) { _, _ -> WidgetSpec.Stat(WidgetBase(), value = "6,240", label = "Steps today", more = listOf(WidgetSpec.Small("4.1 km", "walked"))) },
        Widget(
            "text", WidgetKind.TEXT, WidgetSize.S2X2, "Text",
            lua = """
            widget.show{ kind = "text", title = "Word of the day",
              text = "serendipity\nFinding something good without looking for it.",
              caption = "From Wiktionary" }
            """,
            js = """
            widget.show({ kind: "text", title: "Word of the day",
              text: "serendipity\nFinding something good without looking for it.",
              caption: "From Wiktionary" });
            """,
        ) { _, _ -> WidgetSpec.Text(WidgetBase(title = "Word of the day"), "serendipity\nFinding something good without looking for it.", "From Wiktionary") },
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
              value = 8, label = "Unlocks today", labels = { "Sun", "Today" } }
            """,
            js = """
            widget.show({ kind: "chart", title: "Unlocks", points: [12, 30, 0, 45, 7, 1, 8], style: "bars",
              value: 8, label: "Unlocks today", labels: ["Sun", "Today"] });
            """,
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Unlocks"), listOf(12f, 30f, 0f, 45f, 7f, 1f, 8f), style = "bars",
                value = "8", label = "Unlocks today", labels = listOf("Sun", "Today"),
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
            -- (the script needs @uses dnd)
            if event and event.name == "action" and args[1] == "dnd" then
              device.setDnd(device.dnd() == "off" and "on" or "off")
            end
            widget.show{ kind = "toggles", items = {
              { label = "Do Not Disturb", on = device.dnd() ~= "off", tap = { args = "dnd" } },
            } }
            """,
            js = """
            // (the script needs @uses dnd)
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
                detail = "Due in " .. fmt.color("warn", "2 days"),
                trailing = fmt.strike("520") .. " 480",
              },
            } }
            """,
            js = """
            widget.show({ kind: "list", title: "Bills", items: [
              {
                title: fmt.bold("Rent") + " to Asha",
                detail: "Due in " + fmt.color("warn", "2 days"),
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
                        detail = "Due in " + StyledText.colour(Colour.WARN, "2 days"),
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
     * More versions of some of the guide's examples, for the reference only: another number of rows
     * or switches, another style. Each is real code in both languages, and ReferenceCardsTest runs it
     * as it runs the guide's. [Widget.of] names the example it varies; every name is unique among all
     * the examples. The guide doesn't have these, so ReferenceExamplesTest doesn't look for them there.
     */
    val variants: List<Widget> = listOf(
        Widget(
            "steps-change", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              value = "8,120",
              label = "Steps today",
              delta = "+12%",
              trend = "up",
              more = {
                { value = "5.6 km", label = "walked" },
                { value = "310", label = "kcal" },
                { value = "42 min", label = "active" },
              },
            }
            """,
            js = """
            widget.show({
              value: "8,120",
              label: "Steps today",
              delta: "+12%",
              trend: "up",
              more: [
                { value: "5.6 km", label: "walked" },
                { value: "310", label: "kcal" },
                { value: "42 min", label: "active" },
              ],
            });
            """,
            of = "steps", variant = "With a change",
        ) { _, _ ->
            WidgetSpec.Stat(
                WidgetBase(), value = "8,120", label = "Steps today", delta = "+12%", trend = "up",
                more = listOf(WidgetSpec.Small("5.6 km", "walked"), WidgetSpec.Small("310", "kcal"), WidgetSpec.Small("42 min", "active")),
            )
        },
        Widget(
            "steps-value", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{ value = "6,240" }
            """,
            js = """
            widget.show({ value: "6,240" });
            """,
            of = "steps", variant = "Value only",
        ) { _, _ -> WidgetSpec.Stat(WidgetBase(), value = "6,240") },
        Widget(
            "text-long", WidgetKind.TEXT, WidgetSize.S2X2, "Text",
            lua = """
            widget.show{ kind = "text", title = "Before you leave",
              text = "Water the plants on the balcony before nine, while the sun is still low. "
                .. "The basil needs a full glass; the cactus needs nothing until Sunday.\n"
                .. "Call the plumber about the kitchen tap, and ask for the price first. "
                .. "Bread and milk on the way home.",
              caption = "Left on the fridge" }
            """,
            js = """
            widget.show({ kind: "text", title: "Before you leave",
              text: "Water the plants on the balcony before nine, while the sun is still low. "
                + "The basil needs a full glass; the cactus needs nothing until Sunday.\n"
                + "Call the plumber about the kitchen tap, and ask for the price first. "
                + "Bread and milk on the way home.",
              caption: "Left on the fridge" });
            """,
            of = "text", variant = "Long text",
        ) { _, _ ->
            WidgetSpec.Text(
                WidgetBase(title = "Before you leave"),
                "Water the plants on the balcony before nine, while the sun is still low. " +
                    "The basil needs a full glass; the cactus needs nothing until Sunday.\n" +
                    "Call the plumber about the kitchen tap, and ask for the price first. " +
                    "Bread and milk on the way home.",
                "Left on the fridge",
            )
        },
        Widget(
            "list-10", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            local things = { "Rice", "Lentils", "Onions", "Tomatoes", "Milk", "Bread", "Eggs", "Bananas", "Coffee", "Soap" }
            local items = {}
            for i, name in ipairs(things) do items[i] = { title = name } end
            widget.show{ title = "Groceries", items = items }
            """,
            js = """
            const things = ["Rice", "Lentils", "Onions", "Tomatoes", "Milk", "Bread", "Eggs", "Bananas", "Coffee", "Soap"];
            widget.show({ title: "Groceries", items: things.map((name) => ({ title: name })) });
            """,
            of = "list", variant = "10 rows",
        ) { _, _ ->
            val things = listOf("Rice", "Lentils", "Onions", "Tomatoes", "Milk", "Bread", "Eggs", "Bananas", "Coffee", "Soap")
            WidgetSpec.Rows(WidgetBase(title = "Groceries"), things.map { WidgetSpec.Row(it) })
        },
        Widget(
            "list-lead", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            widget.show{
              title = "Spent this week",
              lead = { value = "3,420", unit = "₹", label = "In 4 payments" },
              items = {
                { title = "Groceries", trailing = "1,240" },
                { title = "Fuel", trailing = "1,500" },
                { title = "Lunch", trailing = "380" },
                { title = "Books", trailing = "300" },
              },
            }
            """,
            js = """
            widget.show({
              title: "Spent this week",
              lead: { value: "3,420", unit: "₹", label: "In 4 payments" },
              items: [
                { title: "Groceries", trailing: "1,240" },
                { title: "Fuel", trailing: "1,500" },
                { title: "Lunch", trailing: "380" },
                { title: "Books", trailing: "300" },
              ],
            });
            """,
            of = "list", variant = "With a lead",
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Spent this week"),
                listOf(
                    WidgetSpec.Row("Groceries", trailing = "1,240"),
                    WidgetSpec.Row("Fuel", trailing = "1,500"),
                    WidgetSpec.Row("Lunch", trailing = "380"),
                    WidgetSpec.Row("Books", trailing = "300"),
                ),
                lead = WidgetSpec.Lead("3,420", "₹", "In 4 payments"),
            )
        },
        Widget(
            "progress-bars", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{ kind = "progress", title = "Phone", items = {
              { label = "Battery", value = 72, max = 100, text = "72%", color = "good" },
              { label = "Storage", value = 96, max = 128, text = "96 / 128 GB", color = "warn" },
              { label = "Data", value = 1.4, max = 2, text = "1.4 / 2 GB" },
            } }
            """,
            js = """
            widget.show({ kind: "progress", title: "Phone", items: [
              { label: "Battery", value: 72, max: 100, text: "72%", color: "good" },
              { label: "Storage", value: 96, max: 128, text: "96 / 128 GB", color: "warn" },
              { label: "Data", value: 1.4, max: 2, text: "1.4 / 2 GB" },
            ] });
            """,
            of = "progress", variant = "Bars",
        ) { _, _ ->
            // With bars and no value, the ring's value is 0 of 1: the card draws the bars.
            WidgetSpec.Progress(
                WidgetBase(title = "Phone"), value = 0f, max = 1f,
                items = listOf(
                    WidgetSpec.Bar("Battery", 72f, 100f, "72%", Colour.GOOD),
                    WidgetSpec.Bar("Storage", 96f, 128f, "96 / 128 GB", Colour.WARN),
                    WidgetSpec.Bar("Data", 1.4f, 2f, "1.4 / 2 GB", null),
                ),
            )
        },
        Widget(
            "chart-line", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            lua = """
            widget.show{ kind = "chart", title = "Sleep", style = "line",
              points = { 7.2, 6.8, 7.5, 6.1, 8, 7.4, 6.9, 7.8, 7.1, 6.5, 7.9, 8.2, 7, 7.6 },
              value = "7.6 h", label = "Slept last night", labels = { "2 weeks ago", "Today" } }
            """,
            js = """
            widget.show({ kind: "chart", title: "Sleep", style: "line",
              points: [7.2, 6.8, 7.5, 6.1, 8, 7.4, 6.9, 7.8, 7.1, 6.5, 7.9, 8.2, 7, 7.6],
              value: "7.6 h", label: "Slept last night", labels: ["2 weeks ago", "Today"] });
            """,
            of = "chart", variant = "Line",
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Sleep"), listOf(7.2f, 6.8f, 7.5f, 6.1f, 8f, 7.4f, 6.9f, 7.8f, 7.1f, 6.5f, 7.9f, 8.2f, 7f, 7.6f), style = "line",
                value = "7.6 h", label = "Slept last night", labels = listOf("2 weeks ago", "Today"),
            )
        },
        Widget(
            "chart-dots", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            lua = """
            -- five weeks of a habit, the oldest day first: 1 for a day done, 0 for a day missed
            local days = "11011111011101110111111011011110111"
            local points = {}
            for i = 1, #days do points[i] = tonumber(days:sub(i, i)) end
            widget.show{ kind = "chart", title = "Exercise", style = "dots", points = points,
              value = 3, label = "Days in a row" }
            """,
            js = """
            // five weeks of a habit, the oldest day first: 1 for a day done, 0 for a day missed
            const days = "11011111011101110111111011011110111";
            widget.show({ kind: "chart", title: "Exercise", style: "dots", points: [...days].map(Number),
              value: 3, label: "Days in a row" });
            """,
            of = "chart", variant = "Dots",
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Exercise"), "11011111011101110111111011011110111".map { if (it == '1') 1f else 0f }, style = "dots",
                value = "3", label = "Days in a row",
            )
        },
        Widget(
            "countdown-clock", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            lua = """
            -- three hours from now, on the hour
            local t = os.date("*t")
            widget.show{ kind = "countdown", title = "Train to Mumbai", label = "From platform 4", style = "clock",
              to = os.time{ year = t.year, month = t.month, day = t.day, hour = t.hour + 3 } }
            """,
            js = """
            // three hours from now, on the hour
            const t = new Date();
            widget.show({ kind: "countdown", title: "Train to Mumbai", label: "From platform 4", style: "clock",
              to: new Date(t.getFullYear(), t.getMonth(), t.getDate(), t.getHours() + 3).getTime() / 1000 });
            """,
            of = "countdown", variant = "Near, as a clock",
        ) { now, zone ->
            val to = java.time.Instant.ofEpochSecond(now).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(3).toEpochSecond()
            WidgetSpec.Countdown(WidgetBase(title = "Train to Mumbai"), to = to, label = "From platform 4", style = "clock")
        },
        Widget(
            "buttons-1", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            lua = """
            widget.show{ kind = "buttons", items = {
              { label = "Log a coffee", icon = "coffee", tap = { args = "coffee" } },
            } }
            """,
            js = """
            widget.show({ kind: "buttons", items: [
              { label: "Log a coffee", icon: "coffee", tap: { args: "coffee" } },
            ] });
            """,
            of = "buttons", variant = "1 button",
        ) { _, _ -> WidgetSpec.Buttons(WidgetBase(), listOf(WidgetSpec.Button("Log a coffee", WidgetTap(args = "coffee"), icon = "coffee"))) },
        Widget(
            "buttons-8", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            lua = """
            -- a timer for each length: the most a buttons widget has
            local items = {}
            for i, m in ipairs{ 1, 2, 3, 5, 10, 15, 20, 30 } do
              items[i] = { label = m .. " min", tap = { args = "timer " .. m } }
            end
            widget.show{ kind = "buttons", title = "Timer", items = items }
            """,
            js = """
            // a timer for each length: the most a buttons widget has
            const items = [1, 2, 3, 5, 10, 15, 20, 30].map((m) => ({ label: m + " min", tap: { args: "timer " + m } }));
            widget.show({ kind: "buttons", title: "Timer", items: items });
            """,
            of = "buttons", variant = "8 buttons",
        ) { _, _ ->
            WidgetSpec.Buttons(WidgetBase(title = "Timer"), listOf(1, 2, 3, 5, 10, 15, 20, 30).map { WidgetSpec.Button("$it min", WidgetTap(args = "timer $it")) })
        },
        Widget(
            "toggles-2", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
            lua = """
            -- each switch is a setting in the script's store: a tap flips it
            if event and event.name == "action" then
              store.set(args[1], store.get(args[1]) == "on" and "off" or "on")
            end
            widget.show{ kind = "toggles", title = "Reminders", items = {
              { label = "Morning", on = store.get("Morning") == "on", tap = { args = "Morning" } },
              { label = "Evening", on = store.get("Evening") == "on", tap = { args = "Evening" } },
            } }
            """,
            js = """
            // each switch is a setting in the script's store: a tap flips it
            if (event && event.name === "action") {
              store.set(args[0], store.get(args[0]) === "on" ? "off" : "on");
            }
            widget.show({ kind: "toggles", title: "Reminders", items: [
              { label: "Morning", on: store.get("Morning") === "on", tap: { args: "Morning" } },
              { label: "Evening", on: store.get("Evening") === "on", tap: { args: "Evening" } },
            ] });
            """,
            of = "toggles", variant = "2 switches",
        ) { _, _ ->
            // Morning is on, Evening off. A switch counts as old after a minute when the code gives no staleAfter.
            WidgetSpec.Toggles(
                WidgetBase(title = "Reminders", staleAfter = 60),
                listOf(
                    WidgetSpec.Toggle("Morning", on = true, tap = WidgetTap(args = "Morning")),
                    WidgetSpec.Toggle("Evening", on = false, tap = WidgetTap(args = "Evening")),
                ),
            )
        },
        Widget(
            "toggles-4", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
            lua = """
            -- each switch is a setting in the script's store: a tap flips it
            if event and event.name == "action" then
              store.set(args[1], store.get(args[1]) == "on" and "off" or "on")
            end
            local items = {}
            for i, name in ipairs{ "Morning", "Noon", "Evening", "Night" } do
              items[i] = { label = name, on = store.get(name) == "on", tap = { args = name } }
            end
            widget.show{ kind = "toggles", title = "Reminders", items = items }
            """,
            js = """
            // each switch is a setting in the script's store: a tap flips it
            if (event && event.name === "action") {
              store.set(args[0], store.get(args[0]) === "on" ? "off" : "on");
            }
            const items = ["Morning", "Noon", "Evening", "Night"].map((name) => ({ label: name, on: store.get(name) === "on", tap: { args: name } }));
            widget.show({ kind: "toggles", title: "Reminders", items: items });
            """,
            of = "toggles", variant = "4 switches",
        ) { _, _ ->
            // Morning and Evening are on.
            val on = setOf("Morning", "Evening")
            WidgetSpec.Toggles(
                WidgetBase(title = "Reminders", staleAfter = 60),
                listOf("Morning", "Noon", "Evening", "Night").map { WidgetSpec.Toggle(it, on = it in on, tap = WidgetTap(args = it)) },
            )
        },
        Widget(
            "clock-4", WidgetKind.CLOCK, WidgetSize.S2X1, "Clock",
            lua = """
            widget.show{ kind = "clock", title = "Team", staleAfter = 604800, zones = {
              { zone = "Asia/Kolkata", label = "Pune" },
              { zone = "Europe/London" },
              { zone = "America/New_York" },
              { zone = "Asia/Tokyo" },
            } }
            """,
            js = """
            widget.show({ kind: "clock", title: "Team", staleAfter: 604800, zones: [
              { zone: "Asia/Kolkata", label: "Pune" },
              { zone: "Europe/London" },
              { zone: "America/New_York" },
              { zone: "Asia/Tokyo" },
            ] });
            """,
            of = "clock", variant = "4 places",
        ) { _, _ ->
            WidgetSpec.Clock(
                WidgetBase(title = "Team", staleAfter = 604800),
                listOf(WidgetSpec.Zone("Asia/Kolkata", "Pune"), WidgetSpec.Zone("Europe/London"), WidgetSpec.Zone("America/New_York"), WidgetSpec.Zone("Asia/Tokyo")),
            )
        },
        Widget(
            "agenda-empty", WidgetKind.AGENDA, WidgetSize.S4X2, "Agenda",
            lua = """
            widget.show{ kind = "agenda", title = "Plans", empty = "Nothing planned", events = {} }
            """,
            js = """
            widget.show({ kind: "agenda", title: "Plans", empty: "Nothing planned", events: [] });
            """,
            of = "agenda", variant = "Nothing planned",
        ) { _, _ -> WidgetSpec.Agenda(WidgetBase(title = "Plans"), emptyList(), empty = "Nothing planned") },
        Widget(
            "agenda-week", WidgetKind.AGENDA, WidgetSize.S4X2, "Agenda",
            lua = """
            local t = os.date("*t")
            -- a time on the day `day` days from today
            local function at(day, hour, min)
              return os.time{ year = t.year, month = t.month, day = t.day + day, hour = hour, min = min }
            end
            widget.show{ kind = "agenda", title = "This week", days = 4, events = {
              { title = "Dentist", starts = at(1, 9, 30), ends = at(1, 10), where = "Main Street" },
              { title = "Team lunch", starts = at(1, 13), ends = at(1, 14) },
              { title = "Gym", starts = at(2, 7), ends = at(2, 8), color = "good" },
              { title = "Call with Ravi", starts = at(2, 18) },
              { title = "Mira's birthday", starts = at(3, 0), allDay = true, color = "accent" },
              { title = "Flight to Delhi", starts = at(3, 21, 15), where = "Terminal 2", color = "warn" },
            } }
            """,
            js = """
            const t = new Date();
            // a time on the day `day` days from today
            const at = (day, hour, min = 0) => new Date(t.getFullYear(), t.getMonth(), t.getDate() + day, hour, min).getTime() / 1000;
            widget.show({ kind: "agenda", title: "This week", days: 4, events: [
              { title: "Dentist", starts: at(1, 9, 30), ends: at(1, 10), where: "Main Street" },
              { title: "Team lunch", starts: at(1, 13), ends: at(1, 14) },
              { title: "Gym", starts: at(2, 7), ends: at(2, 8), color: "good" },
              { title: "Call with Ravi", starts: at(2, 18) },
              { title: "Mira's birthday", starts: at(3, 0), allDay: true, color: "accent" },
              { title: "Flight to Delhi", starts: at(3, 21, 15), where: "Terminal 2", color: "warn" },
            ] });
            """,
            of = "agenda", variant = "A busy week",
        ) { now, zone ->
            val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
            fun at(day: Long, hour: Int, min: Int = 0) = today.plusDays(day).atTime(hour, min).atZone(zone).toEpochSecond()
            WidgetSpec.Agenda(
                WidgetBase(title = "This week"),
                listOf(
                    WidgetSpec.Event("Dentist", at(1, 9, 30), ends = at(1, 10), where = "Main Street"),
                    WidgetSpec.Event("Team lunch", at(1, 13), ends = at(1, 14)),
                    WidgetSpec.Event("Gym", at(2, 7), ends = at(2, 8), color = Colour.GOOD),
                    WidgetSpec.Event("Call with Ravi", at(2, 18)),
                    WidgetSpec.Event("Mira's birthday", at(3, 0), allDay = true, color = Colour.ACCENT),
                    WidgetSpec.Event("Flight to Delhi", at(3, 21, 15), where = "Terminal 2", color = Colour.WARN),
                ),
                days = 4,
            )
        },
    )

    /** The variants of the guide's example [name] ([variants]), in their order. */
    fun variantsOf(name: String): List<Widget> = variants.filter { it.of == name }

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

    /** The engines' `fmt` helpers, with numbers written as the scripts write them. */
    private val styles = Fmt { v -> if (v is Double) Json.numberText(v) else v?.toString().orEmpty() }

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
            local stories = {
              { title = "$STORY_1", points = 128, link = "https://example.com/1" },
              { title = "$STORY_2", points = 342, link = "https://example.com/2" },
            }
            for _, s in ipairs(stories) do log(s.title, s.points .. " points", { url = s.link, gap = true }) end
            log("Your code is 4821", { copy = "4821" })
            """,
            js = """
            log("neovim/neovim", "Nvim 0.11.4", { url: "https://github.com/neovim/neovim/releases" });
            log("Pune, IN", "Today: clear, 24–31°C\nTomorrow: rain, 23–29°C");
            log("Kolahal on GitHub", { url: "https://github.com/mtwabpin/kolahal" });
            // A list: each story a title with its points under it, and a small space between the stories.
            const stories = [
              { title: "$STORY_1", points: 128, link: "https://example.com/1" },
              { title: "$STORY_2", points: 342, link: "https://example.com/2" },
            ];
            for (const s of stories) log(s.title, `${'$'}{s.points} points`, { url: s.link, gap: true });
            log("Your code is 4821", { copy: "4821" });
            """,
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
            local item = { title = "Show HN: Kolahal, a launcher", link = "https://example.com/hn" }
            md.log("**3** new releases", "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119")
            md.log("**HN** " .. md.escape(item.title), nil, { url = item.link })
            """,
            js = """
            const item = { title: "Show HN: Kolahal, a launcher", link: "https://example.com/hn" };
            md.log("**3** new releases", "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119");
            md.log("**HN** " + md.escape(item.title), null, { url: item.link });
            """,
            lines = listOf(
                Line(Markdown.inline("**3** new releases"), Markdown.block("- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119")),
                Line(Markdown.inline("**HN** " + Markdown.escape("Show HN: Kolahal, a launcher")), url = "https://example.com/hn"),
            ),
        ),
        Log(
            "styles", "fmt: styles and colours",
            lua = """
            local item = { score = 128, title = "$STORY_1", link = "https://example.com/1" }
            log(fmt.bold(item.score) .. " · " .. item.title, nil, { url = item.link })
            log("Today", fmt.list({ "standup " .. fmt.code("10:00"), "review" }, true))
            log("BTC 67,120 USD " .. fmt.color("good", "▲ 1.2%"))
            """,
            js = """
            const item = { score: 128, title: "$STORY_1", link: "https://example.com/1" };
            log(`${'$'}{fmt.bold(item.score)} · ${'$'}{item.title}`, null, { url: item.link });
            log("Today", fmt.list([`standup ${'$'}{fmt.code("10:00")}`, "review"], true));
            log(`BTC 67,120 USD ${'$'}{fmt.color("good", "▲ 1.2%")}`);
            """,
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
            local item = { title = "$STORY_1", comments = "https://example.com/c", link = "https://example.com/1" }
            log(item.title .. " · " .. fmt.link("138 comments", item.comments), { url = item.link })
            md.log("New in [neovim](https://github.com/neovim/neovim/releases)")
            """,
            js = """
            const item = { title: "$STORY_1", comments: "https://example.com/c", link: "https://example.com/1" };
            log(`${'$'}{item.title} · ${'$'}{fmt.link("138 comments", item.comments)}`, { url: item.link });
            md.log("New in [neovim](https://github.com/neovim/neovim/releases)");
            """,
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
                Line("Pune · AQI 168", "Unhealthy\n" + styles.bar(listOf(168.0, mapOf("max" to 300.0, "color" to "bad")))),
                Line("Unlocks", styles.bars(listOf(listOf(listOf("Mon", 42.0), listOf("Tue", 21.0)), mapOf("color" to "good")))),
                Line(
                    "Pune · 3 days",
                    styles.ranges(listOf(listOf(listOf("Today", 23.0, 31.0), listOf("Tomorrow", 22.0, 30.0)), mapOf("unit" to "°", "color" to "warn"))),
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

    private val ROLES = listOf("accent", "link", "info", "muted", "text", "good", "warn", "bad")

    /**
     * A sample for each `fmt` function: one short `log` call, and the line it makes. The reference
     * draws it under the function's entry ([ApiDoc] of the same name), and the website shows it in
     * the guide's table of `fmt` functions. `fmt.plain` has none: its text has no styles to show.
     */
    val fmt: List<Log> = listOf(
        Log(
            "fmt.bold", "fmt.bold",
            lua = """log("Rent " .. fmt.bold("due today"))""",
            js = """log("Rent " + fmt.bold("due today"));""",
            lines = listOf(Line("Rent " + bold("due today"))),
        ),
        Log(
            "fmt.italic", "fmt.italic",
            lua = """log(fmt.italic("Simplicity first") .. " · Dijkstra")""",
            js = """log(fmt.italic("Simplicity first") + " · Dijkstra");""",
            lines = listOf(Line(StyledText.wrap(StyledText.Style.ITALIC, "Simplicity first") + " · Dijkstra")),
        ),
        Log(
            "fmt.underline", "fmt.underline",
            lua = """log("Read the " .. fmt.underline("terms") .. " first")""",
            js = """log("Read the " + fmt.underline("terms") + " first");""",
            lines = listOf(Line("Read the " + StyledText.wrap(StyledText.Style.UNDERLINE, "terms") + " first")),
        ),
        Log(
            "fmt.strike", "fmt.strike",
            lua = """log("Price " .. fmt.strike("520") .. " 480")""",
            js = """log("Price " + fmt.strike("520") + " 480");""",
            lines = listOf(Line("Price " + StyledText.wrap(StyledText.Style.STRIKE, "520") + " 480")),
        ),
        Log(
            "fmt.code", "fmt.code",
            lua = """log("Run " .. fmt.code("script lib"))""",
            js = """log("Run " + fmt.code("script lib"));""",
            lines = listOf(Line("Run " + code("script lib"))),
        ),
        Log(
            "fmt.color", "fmt.color",
            lua = """
            local roles = { "accent", "link", "info", "muted", "text", "good", "warn", "bad" }
            local shown = {}
            for i, role in ipairs(roles) do shown[i] = fmt.color(role, role) end
            log(table.concat(shown, " "))
            """,
            js = """
            const roles = ["accent", "link", "info", "muted", "text", "good", "warn", "bad"];
            log(roles.map((role) => fmt.color(role, role)).join(" "));
            """,
            lines = listOf(Line(ROLES.joinToString(" ") { colour(Colour.named(it)!!, it) })),
        ),
        Log(
            "fmt.tag", "fmt.tag",
            lua = """log(fmt.tag("bad", "LIVE") .. " " .. fmt.tag("NEW") .. " " .. fmt.tag("good", "v1.2"))""",
            js = """log(fmt.tag("bad", "LIVE") + " " + fmt.tag("NEW") + " " + fmt.tag("good", "v1.2"));""",
            lines = listOf(Line(StyledText.tag(Colour.BAD, "LIVE") + " " + StyledText.tag(Colour.ACCENT, "NEW") + " " + StyledText.tag(Colour.GOOD, "v1.2"))),
        ),
        Log(
            "fmt.link", "fmt.link",
            lua = """log("Read " .. fmt.link("the guide", "https://mtwabpin.github.io/kolahal/"))""",
            js = """log("Read " + fmt.link("the guide", "https://mtwabpin.github.io/kolahal/"));""",
            lines = listOf(Line("Read " + StyledText.link("https://mtwabpin.github.io/kolahal/", "the guide"))),
        ),
        Log(
            "fmt.list", "fmt.list",
            lua = """log("Today", fmt.list({ "standup", "review" }, true))""",
            js = """log("Today", fmt.list(["standup", "review"], true));""",
            lines = listOf(Line("Today", StyledText.list(listOf("standup", "review"), numbered = true))),
        ),
        Log(
            "fmt.table", "fmt.table",
            lua = """log("Rates", fmt.table({ { "USD", "84.40" }, { "EUR", "91.20" } }, { align = "lr" }))""",
            js = """log("Rates", fmt.table([["USD", "84.40"], ["EUR", "91.20"]], { align: "lr" }));""",
            lines = listOf(Line("Rates", StyledText.table(listOf(listOf("USD", "84.40"), listOf("EUR", "91.20")), header = false, align = "lr"))),
        ),
        Log(
            "fmt.bar", "fmt.bar",
            lua = """log("Battery", fmt.bar(0.68, { color = "good" }))""",
            js = """log("Battery", fmt.bar(0.68, { color: "good" }));""",
            lines = listOf(Line("Battery", styles.bar(listOf(0.68, mapOf("color" to "good"))))),
        ),
        Log(
            "fmt.bars", "fmt.bars",
            lua = """log("Unlocks", fmt.bars({ { "Mon", 42 }, { "Tue", 21 } }))""",
            js = """log("Unlocks", fmt.bars([["Mon", 42], ["Tue", 21]]));""",
            lines = listOf(Line("Unlocks", styles.bars(listOf(listOf(listOf("Mon", 42.0), listOf("Tue", 21.0)))))),
        ),
        Log(
            "fmt.ranges", "fmt.ranges",
            lua = """log("Pune", fmt.ranges({ { "Today", 23, 31 }, { "Tomorrow", 22, 30 } }, { unit = "°" }))""",
            js = """log("Pune", fmt.ranges([["Today", 23, 31], ["Tomorrow", 22, 30]], { unit: "°" }));""",
            lines = listOf(Line("Pune", styles.ranges(listOf(listOf(listOf("Today", 23.0, 31.0), listOf("Tomorrow", 22.0, 30.0)), mapOf("unit" to "°"))))),
        ),
        Log(
            "fmt.stats", "fmt.stats",
            lua = """log("Pune", fmt.stats({ { "25°C", "Sunny" }, { "36%", "humidity" } }))""",
            js = """log("Pune", fmt.stats([["25°C", "Sunny"], ["36%", "humidity"]]));""",
            lines = listOf(Line("Pune", StyledText.stats(listOf("25°C" to "Sunny", "36%" to "humidity")))),
        ),
    )

    private val fmtByName = fmt.associateBy { it.name }

    /** The sample of the `fmt` function [name] ("fmt.bold"), or null when it has none. */
    fun fmtSample(name: String): Log? = fmtByName[name]
}
