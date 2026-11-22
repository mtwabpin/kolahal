/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.ReferenceExamples.Widget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import java.time.temporal.ChronoUnit

/** The reference's variants of the guide's widget examples ([ReferenceExamples.variants]). */
internal object ReferenceExamplesVariants {
    val variants: List<Widget> = listOf(
        Widget(
            "steps-change", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              kind = "stat",
              value = "8,120",
              label = "Steps today",
              delta = "+12%",
              trend = "up",
              more = {
                {
                  value = "5.6 km",
                  label = "walked",
                },
                { value = "310", label = "kcal" },
                {
                  value = "42 min",
                  label = "active",
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "stat",
              value: "8,120",
              label: "Steps today",
              delta: "+12%",
              trend: "up",
              more: [
                {
                  value: "5.6 km",
                  label: "walked",
                },
                { value: "310", label: "kcal" },
                {
                  value: "42 min",
                  label: "active",
                },
              ],
            });
            """,
            of = "steps", variant = "With a change",
        ) { _, _ ->
            WidgetSpec.Stat(
                WidgetBase(), value = "8,120", label = "Steps today", delta = "+12%", trend = "up",
                more = listOf(
                    WidgetSpec.Small("5.6 km", "walked"),
                    WidgetSpec.Small("310", "kcal"),
                    WidgetSpec.Small("42 min", "active")
                ),
            )
        },
        Widget(
            "steps-value", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              kind = "stat",
              value = "6,240",
            }
            """,
            js = """
            widget.show({
              kind: "stat",
              value: "6,240",
            });
            """,
            of = "steps", variant = "Value only",
        ) { _, _ -> WidgetSpec.Stat(WidgetBase(), value = "6,240") },
        Widget(
            "text-long", WidgetKind.TEXT, WidgetSize.S2X2, "Text",
            lua = """
            widget.show{
              kind = "text",
              title = "Before you leave",
              text = "Water the plants on the balcony before nine, while the sun is still low. "
                .. "The basil needs a full glass; the cactus needs nothing until Sunday.\n"
                .. "Call the plumber about the kitchen tap, and ask for the price first. "
                .. "Bread and milk on the way home.",
              caption = "Left on the fridge",
            }
            """,
            js = """
            widget.show({
              kind: "text",
              title: "Before you leave",
              text: "Water the plants on the balcony before nine, while the sun is still low. "
                + "The basil needs a full glass; the cactus needs nothing until Sunday.\n"
                + "Call the plumber about the kitchen tap, and ask for the price first. "
                + "Bread and milk on the way home.",
              caption: "Left on the fridge",
            });
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
            local things = {
              "Rice",
              "Lentils",
              "Onions",
              "Tomatoes",
              "Milk",
              "Bread",
              "Eggs",
              "Bananas",
              "Coffee",
              "Soap",
            }
            local items = {}
            for i, name in ipairs(things) do
              items[i] = { title = name }
            end

            widget.show{
              kind = "list",
              title = "Groceries",
              items = items,
            }
            """,
            js = """
            const things = [
              "Rice",
              "Lentils",
              "Onions",
              "Tomatoes",
              "Milk",
              "Bread",
              "Eggs",
              "Bananas",
              "Coffee",
              "Soap",
            ];

            widget.show({
              kind: "list",
              title: "Groceries",
              items: things.map((name) => ({
                title: name,
              })),
            });
            """,
            of = "list", variant = "10 rows",
        ) { _, _ ->
            val things = listOf(
                "Rice",
                "Lentils",
                "Onions",
                "Tomatoes",
                "Milk",
                "Bread",
                "Eggs",
                "Bananas",
                "Coffee",
                "Soap"
            )
            WidgetSpec.Rows(WidgetBase(title = "Groceries"), things.map { WidgetSpec.Row(it) })
        },
        Widget(
            "list-lead", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            widget.show{
              kind = "list",
              title = "Spent this week",
              lead = {
                value = "3,420",
                unit = "₹",
                label = "In 4 payments",
              },
              items = {
                {
                  title = "Groceries",
                  trailing = "1,240",
                },
                {
                  title = "Fuel",
                  trailing = "1,500",
                },
                {
                  title = "Lunch",
                  trailing = "380",
                },
                {
                  title = "Books",
                  trailing = "300",
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "list",
              title: "Spent this week",
              lead: {
                value: "3,420",
                unit: "₹",
                label: "In 4 payments",
              },
              items: [
                {
                  title: "Groceries",
                  trailing: "1,240",
                },
                {
                  title: "Fuel",
                  trailing: "1,500",
                },
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
        // The guide's example of a row's range, without its comment.
        Widget(
            "list-ranges", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            widget.show{
              kind = "list",
              title = "Pune",
              items = {
                {
                  title = "Today",
                  trailing = "22–31°",
                  range = {
                    low = 22,
                    high = 31,
                    value = 27,
                  },
                },
                {
                  title = "Sun",
                  trailing = "21–30°",
                  range = { low = 21, high = 30 },
                },
                {
                  title = "Mon",
                  trailing = "20–27°",
                  range = { low = 20, high = 27 },
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "list",
              title: "Pune",
              items: [
                {
                  title: "Today",
                  trailing: "22–31°",
                  range: {
                    low: 22,
                    high: 31,
                    value: 27,
                  },
                },
                {
                  title: "Sun",
                  trailing: "21–30°",
                  range: { low: 21, high: 30 },
                },
                {
                  title: "Mon",
                  trailing: "20–27°",
                  range: { low: 20, high: 27 },
                },
              ],
            });
            """,
            of = "list", variant = "With ranges",
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Pune"),
                listOf(
                    WidgetSpec.Row(
                        "Today",
                        trailing = "22–31°",
                        range = WidgetSpec.Range(22f, 31f, 27f)
                    ),
                    WidgetSpec.Row("Sun", trailing = "21–30°", range = WidgetSpec.Range(21f, 30f)),
                    WidgetSpec.Row("Mon", trailing = "20–27°", range = WidgetSpec.Range(20f, 27f)),
                ),
            )
        },
        Widget(
            "progress-bars", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{
              kind = "progress",
              title = "Phone",
              items = {
                {
                  label = "Battery",
                  value = 72,
                  max = 100,
                  text = "72%",
                  color = "good",
                },
                {
                  label = "Storage",
                  value = 96,
                  max = 128,
                  text = "96 / 128 GB",
                  color = "warn",
                },
                {
                  label = "Data",
                  value = 1.4,
                  max = 2,
                  text = "1.4 / 2 GB",
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Phone",
              items: [
                {
                  label: "Battery",
                  value: 72,
                  max: 100,
                  text: "72%",
                  color: "good",
                },
                {
                  label: "Storage",
                  value: 96,
                  max: 128,
                  text: "96 / 128 GB",
                  color: "warn",
                },
                {
                  label: "Data",
                  value: 1.4,
                  max: 2,
                  text: "1.4 / 2 GB",
                },
              ],
            });
            """,
            of = "progress", variant = "Bars",
        ) { _, _ ->
            // With bars and no value, the ring's value is 0 of 1: the card draws the bars.
            WidgetSpec.Progress(
                WidgetBase(title = "Phone"), value = 0f, max = 1f,
                items = listOf(
                    WidgetSpec.Bar("Battery", 72f, 100f, "72%", ColorRole.GOOD),
                    WidgetSpec.Bar("Storage", 96f, 128f, "96 / 128 GB", ColorRole.WARN),
                    WidgetSpec.Bar("Data", 1.4f, 2f, "1.4 / 2 GB", null),
                ),
            )
        },
        Widget(
            "chart-line", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            lua = """
            widget.show{
              kind = "chart",
              title = "Sleep",
              style = "line",
              points = {
                7.2, 6.8, 7.5, 6.1, 8, 7.4, 6.9,
                7.8, 7.1, 6.5, 7.9, 8.2, 7, 7.6,
              },
              value = "7.6 h",
              label = "Slept last night",
              labels = { "2 weeks ago", "Today" },
            }
            """,
            js = """
            widget.show({
              kind: "chart",
              title: "Sleep",
              style: "line",
              points: [
                7.2, 6.8, 7.5, 6.1, 8, 7.4, 6.9,
                7.8, 7.1, 6.5, 7.9, 8.2, 7, 7.6,
              ],
              value: "7.6 h",
              label: "Slept last night",
              labels: ["2 weeks ago", "Today"],
            });
            """,
            of = "chart", variant = "Line",
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Sleep"),
                listOf(
                    7.2f,
                    6.8f,
                    7.5f,
                    6.1f,
                    8f,
                    7.4f,
                    6.9f,
                    7.8f,
                    7.1f,
                    6.5f,
                    7.9f,
                    8.2f,
                    7f,
                    7.6f
                ),
                style = "line",
                value = "7.6 h",
                label = "Slept last night",
                labels = listOf("2 weeks ago", "Today"),
            )
        },
        Widget(
            "chart-dots", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            lua = """
            -- Five weeks of a habit, the oldest day
            -- first: 1 for a day done, 0 for a day
            -- missed.
            local days = "11011111011101110111111011011110111"
            local points = {}
            for i = 1, #days do
              points[i] = tonumber(days:sub(i, i))
            end

            widget.show{
              kind = "chart",
              title = "Exercise",
              style = "dots",
              points = points,
              value = 3,
              label = "Days in a row",
            }
            """,
            js = """
            // Five weeks of a habit, the oldest day
            // first: 1 for a day done, 0 for a day
            // missed.
            const days = "11011111011101110111111011011110111";

            widget.show({
              kind: "chart",
              title: "Exercise",
              style: "dots",
              points: [...days].map(Number),
              value: 3,
              label: "Days in a row",
            });
            """,
            of = "chart", variant = "Dots",
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Exercise"),
                "11011111011101110111111011011110111".map { if (it == '1') 1f else 0f },
                style = "dots",
                value = "3",
                label = "Days in a row",
            )
        },
        Widget(
            "countdown-clock", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            lua = """
            -- Three hours from now, on the hour.
            local t = os.date("*t")

            widget.show{
              kind = "countdown",
              title = "Train to Mumbai",
              label = "From platform 4",
              style = "clock",
              to = os.time({
                year = t.year,
                month = t.month,
                day = t.day,
                hour = t.hour + 3,
              }),
            }
            """,
            js = """
            // Three hours from now, on the hour.
            const t = new Date();

            widget.show({
              kind: "countdown",
              title: "Train to Mumbai",
              label: "From platform 4",
              style: "clock",
              to: new Date(
                t.getFullYear(),
                t.getMonth(),
                t.getDate(),
                t.getHours() + 3,
              ).getTime() / 1000,
            });
            """,
            of = "countdown", variant = "Near, as a clock",
        ) { now, zone ->
            val to = java.time.Instant.ofEpochSecond(now).atZone(zone).truncatedTo(ChronoUnit.HOURS)
                .plusHours(3).toEpochSecond()
            WidgetSpec.Countdown(
                WidgetBase(title = "Train to Mumbai"),
                to = to,
                label = "From platform 4",
                style = "clock"
            )
        },
        Widget(
            "buttons-1", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            lua = """
            widget.show{
              kind = "buttons",
              items = {
                {
                  label = "Log a coffee",
                  icon = "coffee",
                  tap = { args = "coffee" },
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "buttons",
              items: [
                {
                  label: "Log a coffee",
                  icon: "coffee",
                  tap: { args: "coffee" },
                },
              ],
            });
            """,
            of = "buttons", variant = "1 button",
        ) { _, _ ->
            WidgetSpec.Buttons(
                WidgetBase(),
                listOf(
                    WidgetSpec.Button(
                        "Log a coffee",
                        WidgetTap(args = "coffee"),
                        icon = "coffee"
                    )
                )
            )
        },
        Widget(
            "buttons-8", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            lua = """
            -- A timer for each length: the most a
            -- buttons widget has.
            local items = {}
            for i, m in ipairs({
              1, 2, 3, 5, 10, 15, 20, 30,
            }) do
              items[i] = {
                label = m .. " min",
                tap = { args = "timer " .. m },
              }
            end

            widget.show{
              kind = "buttons",
              title = "Timer",
              items = items,
            }
            """,
            js = """
            // A timer for each length: the most a
            // buttons widget has.
            const items = [
              1, 2, 3, 5, 10, 15, 20, 30,
            ].map((m) => ({
              label: m + " min",
              tap: { args: "timer " + m },
            }));

            widget.show({
              kind: "buttons",
              title: "Timer",
              items: items,
            });
            """,
            of = "buttons", variant = "8 buttons",
        ) { _, _ ->
            WidgetSpec.Buttons(
                WidgetBase(title = "Timer"),
                listOf(1, 2, 3, 5, 10, 15, 20, 30).map {
                    WidgetSpec.Button(
                        "$it min",
                        WidgetTap(args = "timer $it")
                    )
                })
        },
        Widget(
            "toggles-2", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
            lua = """
            -- Each switch is a setting in the
            -- script's store: a tap flips it.
            if event and event.name == "action" then
              store.set(
                args[1],
                store.get(args[1]) == "on" and "off"
                  or "on"
              )
            end

            widget.show{
              kind = "toggles",
              title = "Reminders",
              items = {
                {
                  label = "Morning",
                  on = store.get("Morning") == "on",
                  tap = { args = "Morning" },
                },
                {
                  label = "Evening",
                  on = store.get("Evening") == "on",
                  tap = { args = "Evening" },
                },
              },
            }
            """,
            js = """
            // Each switch is a setting in the
            // script's store: a tap flips it.
            if (event && event.name === "action") {
              store.set(
                args[0],
                store.get(args[0]) === "on"
                  ? "off"
                  : "on",
              );
            }

            widget.show({
              kind: "toggles",
              title: "Reminders",
              items: [
                {
                  label: "Morning",
                  on: store.get("Morning") === "on",
                  tap: { args: "Morning" },
                },
                {
                  label: "Evening",
                  on: store.get("Evening") === "on",
                  tap: { args: "Evening" },
                },
              ],
            });
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
            -- Each switch is a setting in the
            -- script's store: a tap flips it.
            if event and event.name == "action" then
              store.set(
                args[1],
                store.get(args[1]) == "on" and "off"
                  or "on"
              )
            end
            local items = {}
            for i, name in ipairs({
              "Morning",
              "Noon",
              "Evening",
              "Night",
            }) do
              items[i] = {
                label = name,
                on = store.get(name) == "on",
                tap = { args = name },
              }
            end

            widget.show{
              kind = "toggles",
              title = "Reminders",
              items = items,
            }
            """,
            js = """
            // Each switch is a setting in the
            // script's store: a tap flips it.
            if (event && event.name === "action") {
              store.set(
                args[0],
                store.get(args[0]) === "on"
                  ? "off"
                  : "on",
              );
            }
            const items = [
              "Morning",
              "Noon",
              "Evening",
              "Night",
            ].map((name) => ({
              label: name,
              on: store.get(name) === "on",
              tap: { args: name },
            }));

            widget.show({
              kind: "toggles",
              title: "Reminders",
              items: items,
            });
            """,
            of = "toggles", variant = "4 switches",
        ) { _, _ ->
            // Morning and Evening are on.
            val on = setOf("Morning", "Evening")
            WidgetSpec.Toggles(
                WidgetBase(title = "Reminders", staleAfter = 60),
                listOf("Morning", "Noon", "Evening", "Night").map {
                    WidgetSpec.Toggle(
                        it,
                        on = it in on,
                        tap = WidgetTap(args = it)
                    )
                },
            )
        },
        Widget(
            "clock-4", WidgetKind.CLOCK, WidgetSize.S2X1, "Clock",
            lua = """
            widget.show{
              kind = "clock",
              title = "Team",
              staleAfter = 604800,
              zones = {
                {
                  zone = "Asia/Kolkata",
                  label = "Pune",
                },
                { zone = "Europe/London" },
                { zone = "America/New_York" },
                { zone = "Asia/Tokyo" },
              },
            }
            """,
            js = """
            widget.show({
              kind: "clock",
              title: "Team",
              staleAfter: 604800,
              zones: [
                {
                  zone: "Asia/Kolkata",
                  label: "Pune",
                },
                { zone: "Europe/London" },
                { zone: "America/New_York" },
                { zone: "Asia/Tokyo" },
              ],
            });
            """,
            of = "clock", variant = "4 places",
        ) { _, _ ->
            WidgetSpec.Clock(
                WidgetBase(title = "Team", staleAfter = 604800),
                listOf(
                    WidgetSpec.Zone("Asia/Kolkata", "Pune"),
                    WidgetSpec.Zone("Europe/London"),
                    WidgetSpec.Zone("America/New_York"),
                    WidgetSpec.Zone("Asia/Tokyo")
                ),
            )
        },
        Widget(
            "agenda-empty", WidgetKind.AGENDA, WidgetSize.S4X2, "Agenda",
            lua = """
            widget.show{
              kind = "agenda",
              title = "Plans",
              empty = "Nothing planned",
              events = {},
            }
            """,
            js = """
            widget.show({
              kind: "agenda",
              title: "Plans",
              empty: "Nothing planned",
              events: [],
            });
            """,
            of = "agenda", variant = "Nothing planned",
        ) { _, _ ->
            WidgetSpec.Agenda(
                WidgetBase(title = "Plans"),
                emptyList(),
                empty = "Nothing planned"
            )
        },
        Widget(
            "agenda-week", WidgetKind.AGENDA, WidgetSize.S4X2, "Agenda",
            lua = """
            local t = os.date("*t")

            -- A time on the day `day` days from
            -- today.
            local function at(day, hour, min)
              return os.time({
                year = t.year,
                month = t.month,
                day = t.day + day,
                hour = hour,
                min = min,
              })
            end

            widget.show{
              kind = "agenda",
              title = "This week",
              days = 4,
              events = {
                {
                  title = "Dentist",
                  starts = at(1, 9, 30),
                  ends = at(1, 10),
                  where = "Main Street",
                },
                {
                  title = "Team lunch",
                  starts = at(1, 13),
                  ends = at(1, 14),
                },
                {
                  title = "Gym",
                  starts = at(2, 7),
                  ends = at(2, 8),
                  color = "good",
                },
                {
                  title = "Call with Ravi",
                  starts = at(2, 18),
                },
                {
                  title = "Mira's birthday",
                  starts = at(3, 0),
                  allDay = true,
                  color = "accent",
                },
                {
                  title = "Flight to Delhi",
                  starts = at(3, 21, 15),
                  where = "Terminal 2",
                  color = "warn",
                },
              },
            }
            """,
            js = """
            const t = new Date();

            // A time on the day `day` days from
            // today.
            const at = (day, hour, min = 0) =>
              new Date(
                t.getFullYear(),
                t.getMonth(),
                t.getDate() + day,
                hour,
                min,
              ).getTime() / 1000;

            widget.show({
              kind: "agenda",
              title: "This week",
              days: 4,
              events: [
                {
                  title: "Dentist",
                  starts: at(1, 9, 30),
                  ends: at(1, 10),
                  where: "Main Street",
                },
                {
                  title: "Team lunch",
                  starts: at(1, 13),
                  ends: at(1, 14),
                },
                {
                  title: "Gym",
                  starts: at(2, 7),
                  ends: at(2, 8),
                  color: "good",
                },
                {
                  title: "Call with Ravi",
                  starts: at(2, 18),
                },
                {
                  title: "Mira's birthday",
                  starts: at(3, 0),
                  allDay: true,
                  color: "accent",
                },
                {
                  title: "Flight to Delhi",
                  starts: at(3, 21, 15),
                  where: "Terminal 2",
                  color: "warn",
                },
              ],
            });
            """,
            of = "agenda", variant = "A busy week",
        ) { now, zone ->
            val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
            fun at(day: Long, hour: Int, min: Int = 0) =
                today.plusDays(day).atTime(hour, min).atZone(zone).toEpochSecond()
            WidgetSpec.Agenda(
                WidgetBase(title = "This week"),
                listOf(
                    WidgetSpec.Event(
                        "Dentist",
                        at(1, 9, 30),
                        ends = at(1, 10),
                        where = "Main Street"
                    ),
                    WidgetSpec.Event("Team lunch", at(1, 13), ends = at(1, 14)),
                    WidgetSpec.Event("Gym", at(2, 7), ends = at(2, 8), color = ColorRole.GOOD),
                    WidgetSpec.Event("Call with Ravi", at(2, 18)),
                    WidgetSpec.Event(
                        "Mira's birthday",
                        at(3, 0),
                        allDay = true,
                        color = ColorRole.ACCENT
                    ),
                    WidgetSpec.Event(
                        "Flight to Delhi",
                        at(3, 21, 15),
                        where = "Terminal 2",
                        color = ColorRole.WARN
                    ),
                ),
                days = 4,
            )
        },
    )
}
