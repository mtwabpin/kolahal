/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.reference

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.reference.ReferenceExamples.Widget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import java.time.LocalDate

/** The guide's widget examples ([ReferenceExamples.widgets]). */
internal object ReferenceExamplesWidgets {
    val widgets: List<Widget> = listOf(
        Widget(
            "steps", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              kind = "stat",
              value = "6,240",
              label = "Steps today",
              more = {
                {
                  value = "4.1 km",
                  label = "walked",
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "stat",
              value: "6,240",
              label: "Steps today",
              more: [
                {
                  value: "4.1 km",
                  label: "walked",
                },
              ],
            });
            """,
        ) { _, _ ->
            WidgetSpec.Stat(
                WidgetBase(),
                value = "6,240",
                label = "Steps today",
                more = listOf(WidgetSpec.Small("4.1 km", "walked"))
            )
        },
        Widget(
            "text", WidgetKind.TEXT, WidgetSize.S2X2, "Text",
            lua = """
            widget.show{
              kind = "text",
              title = "Word of the day",
              text = "serendipity\nFinding something good without looking for it.",
              caption = "From Wiktionary",
            }
            """,
            js = """
            widget.show({
              kind: "text",
              title: "Word of the day",
              text: "serendipity\nFinding something good without looking for it.",
              caption: "From Wiktionary",
            });
            """,
        ) { _, _ ->
            WidgetSpec.Text(
                WidgetBase(title = "Word of the day"),
                "serendipity\nFinding something good without looking for it.",
                "From Wiktionary"
            )
        },
        Widget(
            "list", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            widget.show{
              kind = "list",
              title = "Reading list",
              items = {
                {
                  title = "Kolahal's guide",
                  trailing = "docs",
                  tap = {
                    url = "https://mtwabpin.github.io/kolahal/",
                  },
                },
                {
                  title = "No link here",
                  detail = "A row needs only its title",
                },
              },
              empty = "Nothing to read",
            }
            """,
            js = """
            widget.show({
              kind: "list",
              title: "Reading list",
              items: [
                {
                  title: "Kolahal's guide",
                  trailing: "docs",
                  tap: {
                    url: "https://mtwabpin.github.io/kolahal/",
                  },
                },
                {
                  title: "No link here",
                  detail: "A row needs only its title",
                },
              ],
              empty: "Nothing to read",
            });
            """,
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Reading list"),
                listOf(
                    WidgetSpec.Row(
                        "Kolahal's guide",
                        trailing = "docs",
                        tap = WidgetTap(url = "https://mtwabpin.github.io/kolahal/")
                    ),
                    WidgetSpec.Row("No link here", detail = "A row needs only its title"),
                ),
                empty = "Nothing to read",
            )
        },
        Widget(
            "progress", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{
              kind = "progress",
              title = "Pune air",
              value = 168,
              max = 300,
              text = "168",
              label = "Unhealthy",
              color = "warn",
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Pune air",
              value: 168,
              max: 300,
              text: "168",
              label: "Unhealthy",
              color: "warn",
            });
            """,
        ) { _, _ ->
            WidgetSpec.Progress(
                WidgetBase(title = "Pune air", color = ColorRole.WARN),
                value = 168f,
                max = 300f,
                text = "168",
                label = "Unhealthy"
            )
        },
        Widget(
            "chart", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            lua = """
            widget.show{
              kind = "chart",
              title = "Unlocks",
              points = { 12, 30, 0, 45, 7, 1, 8 },
              style = "bars",
              value = 8,
              label = "Unlocks today",
              labels = { "Sun", "Today" },
            }
            """,
            js = """
            widget.show({
              kind: "chart",
              title: "Unlocks",
              points: [12, 30, 0, 45, 7, 1, 8],
              style: "bars",
              value: 8,
              label: "Unlocks today",
              labels: ["Sun", "Today"],
            });
            """,
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Unlocks"),
                listOf(12f, 30f, 0f, 45f, 7f, 1f, 8f),
                style = "bars",
                value = "8",
                label = "Unlocks today",
                labels = listOf("Sun", "Today"),
            )
        },
        Widget(
            "countdown", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            lua = """
            widget.show{
              kind = "countdown",
              title = "Goa trip",
              to = os.time({
                year = 2026,
                month = 12,
                day = 20,
                hour = 0,
              }),
            }
            """,
            js = """
            widget.show({
              kind: "countdown",
              title: "Goa trip",
              to: new Date(
                2026,
                11,
                20,
              ).getTime() / 1000,
            });
            """,
        ) { _, zone ->
            WidgetSpec.Countdown(
                WidgetBase(title = "Goa trip"),
                to = LocalDate.of(2026, 12, 20).atStartOfDay(zone).toEpochSecond()
            )
        },
        Widget(
            "buttons", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            lua = """
            widget.show{
              kind = "buttons",
              items = {
                {
                  label = "Start",
                  icon = "play",
                  tap = { args = "start" },
                },
                {
                  label = "Stop",
                  icon = "pause",
                  color = "bad",
                  tap = { args = "stop" },
                },
                {
                  label = "Help",
                  tap = {
                    url = "https://example.com/help",
                  },
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "buttons",
              items: [
                {
                  label: "Start",
                  icon: "play",
                  tap: { args: "start" },
                },
                {
                  label: "Stop",
                  icon: "pause",
                  color: "bad",
                  tap: { args: "stop" },
                },
                {
                  label: "Help",
                  tap: {
                    url: "https://example.com/help",
                  },
                },
              ],
            });
            """,
        ) { _, _ ->
            WidgetSpec.Buttons(
                WidgetBase(),
                listOf(
                    WidgetSpec.Button("Start", WidgetTap(args = "start"), icon = "play"),
                    WidgetSpec.Button(
                        "Stop",
                        WidgetTap(args = "stop"),
                        icon = "pause",
                        color = ColorRole.BAD
                    ),
                    WidgetSpec.Button("Help", WidgetTap(url = "https://example.com/help")),
                ),
            )
        },
        Widget(
            "toggles", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
            lua = """
            -- The script needs @uses dnd.
            if event and event.name == "action"
                and args[1] == "dnd" then
              device.setDnd(
                device.dnd() == "off" and "on"
                  or "off"
              )
            end

            widget.show{
              kind = "toggles",
              items = {
                {
                  label = "Do Not Disturb",
                  on = device.dnd() ~= "off",
                  tap = { args = "dnd" },
                },
              },
            }
            """,
            js = """
            // The script needs @uses dnd.
            if (event && event.name === "action"
                && args[0] === "dnd") {
              device.setDnd(
                device.dnd() === "off"
                  ? "on"
                  : "off",
              );
            }

            widget.show({
              kind: "toggles",
              items: [
                {
                  label: "Do Not Disturb",
                  on: device.dnd() !== "off",
                  tap: { args: "dnd" },
                },
              ],
            });
            """,
        ) { _, _ ->
            // A switch counts as old after a minute when the code gives no staleAfter.
            WidgetSpec.Toggles(
                WidgetBase(staleAfter = 60),
                listOf(
                    WidgetSpec.Toggle(
                        "Do Not Disturb",
                        on = true,
                        tap = WidgetTap(args = "dnd")
                    )
                )
            )
        },
        Widget(
            "clock", WidgetKind.CLOCK, WidgetSize.S2X1, "Clock",
            lua = """
            widget.show{
              kind = "clock",
              title = "Clocks",
              staleAfter = 604800,
              zones = {
                {
                  zone = "Asia/Kolkata",
                  label = "Pune",
                },
                { zone = "America/New_York" },
              },
            }
            """,
            js = """
            widget.show({
              kind: "clock",
              title: "Clocks",
              staleAfter: 604800,
              zones: [
                {
                  zone: "Asia/Kolkata",
                  label: "Pune",
                },
                { zone: "America/New_York" },
              ],
            });
            """,
        ) { _, _ ->
            WidgetSpec.Clock(
                WidgetBase(title = "Clocks", staleAfter = 604800),
                listOf(WidgetSpec.Zone("Asia/Kolkata", "Pune"), WidgetSpec.Zone("America/New_York"))
            )
        },
        Widget(
            "agenda", WidgetKind.AGENDA, WidgetSize.S4X2, "Agenda",
            lua = """
            local t = os.date("*t")
            local lunch = os.time({
              year = t.year,
              month = t.month,
              day = t.day + 1,
              hour = 13,
            })
            local trip = os.time({
              year = t.year,
              month = t.month,
              day = t.day + 2,
              hour = 0,
            })

            widget.show{
              kind = "agenda",
              title = "Plans",
              days = 3,
              empty = "Nothing planned",
              events = {
                {
                  title = "Lunch with Asha",
                  starts = lunch,
                  ends = lunch + 3600,
                  where = "Cafe Goodluck",
                },
                {
                  title = "Goa trip",
                  starts = trip,
                  allDay = true,
                  color = "good",
                },
              },
            }
            """,
            js = """
            const t = new Date();
            const lunch = new Date(
              t.getFullYear(),
              t.getMonth(),
              t.getDate() + 1,
              13,
            ).getTime() / 1000;
            const trip = new Date(
              t.getFullYear(),
              t.getMonth(),
              t.getDate() + 2,
            ).getTime() / 1000;

            widget.show({
              kind: "agenda",
              title: "Plans",
              days: 3,
              empty: "Nothing planned",
              events: [
                {
                  title: "Lunch with Asha",
                  starts: lunch,
                  ends: lunch + 3600,
                  where: "Cafe Goodluck",
                },
                {
                  title: "Goa trip",
                  starts: trip,
                  allDay: true,
                  color: "good",
                },
              ],
            });
            """,
        ) { now, zone ->
            val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
            val lunch = today.plusDays(1).atTime(13, 0).atZone(zone).toEpochSecond()
            val trip = today.plusDays(2).atStartOfDay(zone).toEpochSecond()
            WidgetSpec.Agenda(
                WidgetBase(title = "Plans"),
                listOf(
                    WidgetSpec.Event(
                        "Lunch with Asha",
                        lunch,
                        ends = lunch + 3600,
                        where = "Cafe Goodluck"
                    ),
                    WidgetSpec.Event("Goa trip", trip, allDay = true, color = ColorRole.GOOD),
                ),
                days = 3,
                empty = "Nothing planned",
            )
        },
        Widget(
            "styles", WidgetKind.LIST, WidgetSize.S4X1, "Styles",
            lua = """
            widget.show{
              kind = "list",
              title = "Bills",
              items = {
                {
                  title = fmt.bold("Rent")
                    .. " to Asha",
                  detail = "Due in "
                    .. fmt.color("warn", "2 days"),
                  trailing = fmt.strike("520")
                    .. " 480",
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "list",
              title: "Bills",
              items: [
                {
                  title: fmt.bold("Rent")
                    + " to Asha",
                  detail: "Due in "
                    + fmt.color("warn", "2 days"),
                  trailing: fmt.strike("520")
                    + " 480",
                },
              ],
            });
            """,
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Bills"),
                listOf(
                    WidgetSpec.Row(
                        StyledText.wrap(StyledText.Style.BOLD, "Rent") + " to Asha",
                        detail = "Due in " + StyledText.color(ColorRole.WARN, "2 days"),
                        trailing = StyledText.wrap(StyledText.Style.STRIKE, "520") + " 480",
                    ),
                ),
            )
        },
        Widget(
            "water", WidgetKind.PROGRESS, WidgetSize.S2X1, "Water",
            lua = """
            local today = os.date("%Y-%m-%d")
            local n = tonumber(
              store.get(today) or "0"
            )
            if event and event.name == "action"
                and args[1] == "add" then
              n = n + 1
              store.set(today, tostring(n))
            end
            local goal = tonumber(options.goal) or 8

            widget.show{
              kind = "progress",
              value = n,
              max = goal,
              text = n .. " / " .. goal,
              color = n >= goal and "good"
                or "accent",
              tap = {
                args = "add",
                label = "add a glass",
              },
            }
            """,
            js = """
            const d = new Date();
            const today = `${'$'}{d.getFullYear()}-${'$'}{d.getMonth() + 1}-${'$'}{d.getDate()}`;
            let n = Number(store.get(today) || "0");
            if (event && event.name === "action"
                && args[0] === "add") {
              n += 1;
              store.set(today, String(n));
            }
            const goal = Number(options.goal) || 8;

            widget.show({
              kind: "progress",
              value: n,
              max: goal,
              text: `${'$'}{n} / ${'$'}{goal}`,
              color: n >= goal ? "good" : "accent",
              tap: {
                args: "add",
                label: "add a glass",
              },
            });
            """,
        ) { _, _ ->
            // Five glasses of eight so far today.
            WidgetSpec.Progress(
                WidgetBase(
                    color = ColorRole.ACCENT,
                    tap = WidgetTap(args = "add", label = "add a glass")
                ), value = 5f, max = 8f, text = "5 / 8"
            )
        },
    )
}
