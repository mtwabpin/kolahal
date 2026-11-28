/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.reference

import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.reference.ReferenceExamples.Widget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetShape
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
            "steps-bar", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              kind = "stat",
              title = "Battery",
              icon = "battery",
              shape = "sun",
              value = 68,
              unit = "%",
              label = "Charging",
              progress = 0.68,
              moving = true,
            }
            """,
            js = """
            widget.show({
              kind: "stat",
              title: "Battery",
              icon: "battery",
              shape: "sun",
              value: 68,
              unit: "%",
              label: "Charging",
              progress: 0.68,
              moving: true,
            });
            """,
            of = "steps", variant = "With a bar",
        ) { _, _ ->
            WidgetSpec.Stat(
                WidgetBase(title = "Battery", icon = "battery", shape = WidgetShape.SUN),
                value = "68", unit = "%", label = "Charging", progress = 0.68f, moving = true,
            )
        },
        Widget(
            "steps-week", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              kind = "stat",
              value = "6,240",
              label = "Steps today",
              points = { 5120, 7400, 4310, 8020, 6650, 3980, 6240 },
            }
            """,
            js = """
            widget.show({
              kind: "stat",
              value: "6,240",
              label: "Steps today",
              points: [5120, 7400, 4310, 8020, 6650, 3980, 6240],
            });
            """,
            of = "steps", variant = "With the week",
        ) { _, _ ->
            WidgetSpec.Stat(
                WidgetBase(), value = "6,240", label = "Steps today",
                points = listOf(5120f, 7400f, 4310f, 8020f, 6650f, 3980f, 6240f),
            )
        },
        // The guide's example of a stat's scene, without its comment.
        Widget(
            "steps-scene", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              kind = "stat",
              title = "Lisbon",
              value = "14°",
              label = "Clear night",
              scene = "pond",
              points = { 17, 16, 16, 15, 14 },
            }
            """,
            js = """
            widget.show({
              kind: "stat",
              title: "Lisbon",
              value: "14°",
              label: "Clear night",
              scene: "pond",
              points: [17, 16, 16, 15, 14],
            });
            """,
            of = "steps", variant = "Scene",
        ) { _, _ ->
            WidgetSpec.Stat(
                WidgetBase(title = "Lisbon"), value = "14°", label = "Clear night",
                points = listOf(17f, 16f, 16f, 15f, 14f), scene = "pond",
            )
        },
        Widget(
            "steps-target", WidgetKind.STAT, WidgetSize.S2X1, "Steps",
            lua = """
            widget.show{
              kind = "stat",
              value = "6,240",
              label = "Steps today",
              target = "8,000",
              progress = 6240 / 8000,
            }
            """,
            js = """
            widget.show({
              kind: "stat",
              value: "6,240",
              label: "Steps today",
              target: "8,000",
              progress: 6240 / 8000,
            });
            """,
            of = "steps", variant = "With a target",
        ) { _, _ ->
            WidgetSpec.Stat(
                WidgetBase(), value = "6,240", label = "Steps today", target = "8,000", progress = 0.78f,
            )
        },
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
            "text-footer", WidgetKind.TEXT, WidgetSize.S2X2, "Text",
            lua = """
            widget.show{
              kind = "text",
              title = "Quote of the day",
              text = "Begin anywhere. The middle will find you on the way.",
              caption = "Proverb",
              footer = "A new quote each morning",
            }
            """,
            js = """
            widget.show({
              kind: "text",
              title: "Quote of the day",
              text: "Begin anywhere. The middle will find you on the way.",
              caption: "Proverb",
              footer: "A new quote each morning",
            });
            """,
            of = "text", variant = "With a footer",
        ) { _, _ ->
            WidgetSpec.Text(
                WidgetBase(title = "Quote of the day", footer = "A new quote each morning"),
                "Begin anywhere. The middle will find you on the way.",
                "Proverb",
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
            "list-checks", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            widget.show{
              kind = "list",
              title = "Habits",
              items = {
                {
                  title = "Stretch",
                  icon = "walk",
                  done = true,
                },
                {
                  title = "Read 20 pages",
                  icon = "book",
                  done = true,
                },
                {
                  title = "Water the plants",
                  icon = "drop",
                  color = "warn",
                  done = false,
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "list",
              title: "Habits",
              items: [
                {
                  title: "Stretch",
                  icon: "walk",
                  done: true,
                },
                {
                  title: "Read 20 pages",
                  icon: "book",
                  done: true,
                },
                {
                  title: "Water the plants",
                  icon: "drop",
                  color: "warn",
                  done: false,
                },
              ],
            });
            """,
            of = "list", variant = "With checkboxes",
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Habits"),
                listOf(
                    WidgetSpec.Row("Stretch", icon = "walk", done = true),
                    WidgetSpec.Row("Read 20 pages", icon = "book", done = true),
                    WidgetSpec.Row(
                        "Water the plants",
                        icon = "drop",
                        color = ColorRole.WARN,
                        done = false
                    ),
                ),
            )
        },
        Widget(
            "list-badge", WidgetKind.LIST, WidgetSize.S4X2, "List",
            lua = """
            widget.show{
              kind = "list",
              title = "Inbox",
              badge = "3",
              items = {
                {
                  title = "Plans for Saturday",
                  detail = "Sam · see you at ten?",
                  trailing = "09:12",
                },
                {
                  title = "Your order has shipped",
                  detail = "Shop · arrives Thursday",
                  trailing = "08:40",
                },
                {
                  title = "Book club notes",
                  detail = "Ana · chapter six",
                  trailing = "Mon",
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "list",
              title: "Inbox",
              badge: "3",
              items: [
                {
                  title: "Plans for Saturday",
                  detail: "Sam · see you at ten?",
                  trailing: "09:12",
                },
                {
                  title: "Your order has shipped",
                  detail: "Shop · arrives Thursday",
                  trailing: "08:40",
                },
                {
                  title: "Book club notes",
                  detail: "Ana · chapter six",
                  trailing: "Mon",
                },
              ],
            });
            """,
            of = "list", variant = "With a badge",
        ) { _, _ ->
            WidgetSpec.Rows(
                WidgetBase(title = "Inbox", badge = "3"),
                listOf(
                    WidgetSpec.Row("Plans for Saturday", detail = "Sam · see you at ten?", trailing = "09:12"),
                    WidgetSpec.Row("Your order has shipped", detail = "Shop · arrives Thursday", trailing = "08:40"),
                    WidgetSpec.Row("Book club notes", detail = "Ana · chapter six", trailing = "Mon"),
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
            "progress-gauge", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{
              kind = "progress",
              title = "Outside",
              style = "gauge",
              value = 4,
              min = -10,
              max = 40,
              unit = "°C",
              label = "Cold",
              zones = {
                { from = -10, color = "info" },
                { from = 10, color = "good" },
                { from = 30, color = "bad" },
              },
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Outside",
              style: "gauge",
              value: 4,
              min: -10,
              max: 40,
              unit: "°C",
              label: "Cold",
              zones: [
                { from: -10, color: "info" },
                { from: 10, color: "good" },
                { from: 30, color: "bad" },
              ],
            });
            """,
            of = "progress", variant = "Gauge",
        ) { _, _ ->
            WidgetSpec.Progress(
                WidgetBase(title = "Outside"), value = 4f, max = 40f, label = "Cold",
                style = "gauge", min = -10f, unit = "°C",
                zones = listOf(
                    WidgetSpec.GaugeZone(-10f, ColorRole.INFO),
                    WidgetSpec.GaugeZone(10f, ColorRole.GOOD),
                    WidgetSpec.GaugeZone(30f, ColorRole.BAD),
                ),
            )
        },
        Widget(
            "progress-rings", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{
              kind = "progress",
              title = "Today",
              style = "rings",
              items = {
                {
                  label = "Steps",
                  value = 6240,
                  max = 8000,
                  text = "6,240",
                  color = "accent",
                  moving = true,
                },
                {
                  label = "Water",
                  value = 5,
                  max = 8,
                  text = "5/8",
                  unit = "glasses",
                  color = "link",
                },
                {
                  label = "Sleep",
                  value = 7.2,
                  max = 8,
                  text = "7:10",
                  unit = "h",
                  color = "warn",
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Today",
              style: "rings",
              items: [
                {
                  label: "Steps",
                  value: 6240,
                  max: 8000,
                  text: "6,240",
                  color: "accent",
                  moving: true,
                },
                {
                  label: "Water",
                  value: 5,
                  max: 8,
                  text: "5/8",
                  unit: "glasses",
                  color: "link",
                },
                {
                  label: "Sleep",
                  value: 7.2,
                  max: 8,
                  text: "7:10",
                  unit: "h",
                  color: "warn",
                },
              ],
            });
            """,
            of = "progress", variant = "Rings",
        ) { _, _ ->
            // With rings and no value, the value is 0 of 1: the card draws the rings.
            WidgetSpec.Progress(
                WidgetBase(title = "Today"), value = 0f, max = 1f, style = "rings",
                items = listOf(
                    WidgetSpec.Bar("Steps", 6240f, 8000f, "6,240", ColorRole.ACCENT, moving = true),
                    WidgetSpec.Bar("Water", 5f, 8f, "5/8", ColorRole.LINK, unit = "glasses"),
                    WidgetSpec.Bar("Sleep", 7.2f, 8f, "7:10", ColorRole.WARN, unit = "h"),
                ),
            )
        },
        Widget(
            "progress-stages", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{
              kind = "progress",
              title = "Flight to Paris",
              stages = {
                "Check-in",
                "Security",
                "Boarding",
                "Departed",
              },
              stage = 3,
              label = "Gate 23",
              color = "link",
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Flight to Paris",
              stages: [
                "Check-in",
                "Security",
                "Boarding",
                "Departed",
              ],
              stage: 3,
              label: "Gate 23",
              color: "link",
            });
            """,
            of = "progress", variant = "Stages",
        ) { _, _ ->
            WidgetSpec.Progress(
                WidgetBase(title = "Flight to Paris", color = ColorRole.LINK), value = 0f,
                label = "Gate 23",
                stages = listOf("Check-in", "Security", "Boarding", "Departed"),
                stage = 3,
            )
        },
        Widget(
            "progress-details", WidgetKind.PROGRESS, WidgetSize.S2X1, "Progress",
            lua = """
            widget.show{
              kind = "progress",
              title = "Parcel",
              stages = {
                "Ordered",
                "Shipped",
                "Out for delivery",
                "Delivered",
              },
              stage = 3,
              value = 0.5,
              label = "By 6 PM",
              details = {
                "Mon 09:12",
                "Tue 18:40",
                "Today 07:05",
              },
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Parcel",
              stages: [
                "Ordered",
                "Shipped",
                "Out for delivery",
                "Delivered",
              ],
              stage: 3,
              value: 0.5,
              label: "By 6 PM",
              details: [
                "Mon 09:12",
                "Tue 18:40",
                "Today 07:05",
              ],
            });
            """,
            of = "progress", variant = "Stages with details",
        ) { _, _ ->
            // The details show where the stages' names do: 4x2 and larger.
            WidgetSpec.Progress(
                WidgetBase(title = "Parcel"), value = 0.5f,
                label = "By 6 PM",
                stages = listOf("Ordered", "Shipped", "Out for delivery", "Delivered"),
                stage = 3,
                details = listOf("Mon 09:12", "Tue 18:40", "Today 07:05"),
            )
        },
        Widget(
            "water-bar", WidgetKind.PROGRESS, WidgetSize.S2X1, "Water",
            lua = """
            widget.show{
              kind = "progress",
              title = "Water",
              style = "bar",
              value = 5,
              max = 8,
              text = "5 / 8",
              label = "Glasses today",
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Water",
              style: "bar",
              value: 5,
              max: 8,
              text: "5 / 8",
              label: "Glasses today",
            });
            """,
            of = "water", variant = "A bar",
        ) { _, _ ->
            WidgetSpec.Progress(
                WidgetBase(title = "Water"), value = 5f, max = 8f, text = "5 / 8",
                label = "Glasses today", style = "bar",
            )
        },
        Widget(
            "water-ring", WidgetKind.PROGRESS, WidgetSize.S2X1, "Water",
            lua = """
            widget.show{
              kind = "progress",
              title = "Water",
              style = "ring",
              value = 5,
              max = 8,
              text = "5 / 8",
              color = "link",
              moving = true,
            }
            """,
            js = """
            widget.show({
              kind: "progress",
              title: "Water",
              style: "ring",
              value: 5,
              max: 8,
              text: "5 / 8",
              color: "link",
              moving: true,
            });
            """,
            of = "water", variant = "A moving ring",
        ) { _, _ ->
            WidgetSpec.Progress(
                WidgetBase(title = "Water", color = ColorRole.LINK), value = 5f, max = 8f,
                text = "5 / 8", style = "ring", moving = true,
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
            "chart-scale", WidgetKind.CHART, WidgetSize.S2X2, "Chart",
            lua = """
            widget.show{
              kind = "chart",
              title = "Bedroom",
              note = "Window open",
              points = {
                21, 21.5, 22, 22.5, 23,
                22.5, 22, 21.5, 21,
              },
              min = 15,
              max = 30,
              value = "21°",
              label = "Over the day",
            }
            """,
            js = """
            widget.show({
              kind: "chart",
              title: "Bedroom",
              note: "Window open",
              points: [
                21, 21.5, 22, 22.5, 23,
                22.5, 22, 21.5, 21,
              ],
              min: 15,
              max: 30,
              value: "21°",
              label: "Over the day",
            });
            """,
            of = "chart", variant = "On a scale",
        ) { _, _ ->
            WidgetSpec.Chart(
                WidgetBase(title = "Bedroom", note = "Window open"),
                listOf(21f, 21.5f, 22f, 22.5f, 23f, 22.5f, 22f, 21.5f, 21f),
                min = 15f,
                max = 30f,
                value = "21°",
                label = "Over the day",
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
            "countdown-dots", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            lua = """
            -- 26 days to go, from a trip booked
            -- 14 days ago: a dot a day.
            local t = os.date("*t")

            widget.show{
              kind = "countdown",
              title = "Paris",
              label = "A dot a day",
              style = "dots",
              from = os.time({
                year = t.year,
                month = t.month,
                day = t.day - 14,
                hour = 0,
              }),
              to = os.time({
                year = t.year,
                month = t.month,
                day = t.day + 26,
                hour = 0,
              }),
            }
            """,
            js = """
            // 26 days to go, from a trip booked
            // 14 days ago: a dot a day.
            const t = new Date();

            widget.show({
              kind: "countdown",
              title: "Paris",
              label: "A dot a day",
              style: "dots",
              from: new Date(
                t.getFullYear(),
                t.getMonth(),
                t.getDate() - 14,
              ).getTime() / 1000,
              to: new Date(
                t.getFullYear(),
                t.getMonth(),
                t.getDate() + 26,
              ).getTime() / 1000,
            });
            """,
            of = "countdown", variant = "A dot a day",
        ) { now, zone ->
            val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
            WidgetSpec.Countdown(
                WidgetBase(title = "Paris"),
                to = today.plusDays(26).atStartOfDay(zone).toEpochSecond(),
                from = today.minusDays(14).atStartOfDay(zone).toEpochSecond(),
                label = "A dot a day",
                style = "dots"
            )
        },
        Widget(
            "countdown-page", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            lua = """
            widget.show{
              kind = "countdown",
              title = "Goa trip",
              style = "page",
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
              style: "page",
              to: new Date(
                2026,
                11,
                20,
              ).getTime() / 1000,
            });
            """,
            of = "countdown", variant = "A calendar page",
        ) { _, zone ->
            WidgetSpec.Countdown(
                WidgetBase(title = "Goa trip"),
                to = java.time.LocalDate.of(2026, 12, 20).atStartOfDay(zone).toEpochSecond(),
                style = "page"
            )
        },
        Widget(
            "countdown-reached", WidgetKind.COUNTDOWN, WidgetSize.S2X1, "Countdown",
            lua = """
            -- Midnight today: the moment has
            -- passed, so the card shows reached.
            local t = os.date("*t")

            widget.show{
              kind = "countdown",
              title = "Results",
              style = "days",
              reached = "Out today",
              to = os.time({
                year = t.year,
                month = t.month,
                day = t.day,
                hour = 0,
              }),
            }
            """,
            js = """
            // Midnight today: the moment has
            // passed, so the card shows reached.
            const t = new Date();

            widget.show({
              kind: "countdown",
              title: "Results",
              style: "days",
              reached: "Out today",
              to: new Date(
                t.getFullYear(),
                t.getMonth(),
                t.getDate(),
              ).getTime() / 1000,
            });
            """,
            of = "countdown", variant = "On the day",
        ) { now, zone ->
            val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
            WidgetSpec.Countdown(
                WidgetBase(title = "Results"),
                to = today.atStartOfDay(zone).toEpochSecond(),
                style = "days",
                reached = "Out today"
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
            "buttons-value", WidgetKind.BUTTONS, WidgetSize.S4X1, "Buttons",
            lua = """
            widget.show{
              kind = "buttons",
              title = "Heating",
              items = {
                {
                  label = "Colder",
                  icon = "down",
                  tap = { args = "colder" },
                },
                {
                  label = "Living room",
                  icon = "temp",
                  value = "21°",
                  tap = { args = "room" },
                },
                {
                  label = "Warmer",
                  icon = "up",
                  tap = { args = "warmer" },
                },
              },
            }
            """,
            js = """
            widget.show({
              kind: "buttons",
              title: "Heating",
              items: [
                {
                  label: "Colder",
                  icon: "down",
                  tap: { args: "colder" },
                },
                {
                  label: "Living room",
                  icon: "temp",
                  value: "21°",
                  tap: { args: "room" },
                },
                {
                  label: "Warmer",
                  icon: "up",
                  tap: { args: "warmer" },
                },
              ],
            });
            """,
            of = "buttons", variant = "With a value",
        ) { _, _ ->
            WidgetSpec.Buttons(
                WidgetBase(title = "Heating"),
                listOf(
                    WidgetSpec.Button("Colder", WidgetTap(args = "colder"), icon = "down"),
                    WidgetSpec.Button("Living room", WidgetTap(args = "room"), icon = "temp", value = "21°"),
                    WidgetSpec.Button("Warmer", WidgetTap(args = "warmer"), icon = "up"),
                ),
            )
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
            "toggles-icons", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
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
              title = "Lights",
              items = {
                {
                  label = "Porch",
                  icon = "torch",
                  on = store.get("Porch") == "on",
                  tap = { args = "Porch" },
                },
                {
                  label = "Garden",
                  icon = "leaf",
                  on = store.get("Garden") == "on",
                  tap = { args = "Garden" },
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
              title: "Lights",
              items: [
                {
                  label: "Porch",
                  icon: "torch",
                  on: store.get("Porch") === "on",
                  tap: { args: "Porch" },
                },
                {
                  label: "Garden",
                  icon: "leaf",
                  on: store.get("Garden") === "on",
                  tap: { args: "Garden" },
                },
              ],
            });
            """,
            of = "toggles", variant = "With icons",
        ) { _, _ ->
            // The porch light is on.
            WidgetSpec.Toggles(
                WidgetBase(title = "Lights", staleAfter = 60),
                listOf(
                    WidgetSpec.Toggle("Porch", on = true, tap = WidgetTap(args = "Porch"), icon = "torch"),
                    WidgetSpec.Toggle("Garden", on = false, tap = WidgetTap(args = "Garden"), icon = "leaf"),
                ),
            )
        },
        Widget(
            "toggles-values", WidgetKind.TOGGLES, WidgetSize.S4X1, "Toggles",
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
              title = "Home",
              items = {
                {
                  label = "Heating",
                  icon = "temp",
                  value = "21°",
                  on = store.get("Heating") == "on",
                  tap = { args = "Heating" },
                },
                {
                  label = "Fan",
                  icon = "wind",
                  on = store.get("Fan") == "on",
                  tap = { args = "Fan" },
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
              title: "Home",
              items: [
                {
                  label: "Heating",
                  icon: "temp",
                  value: "21°",
                  on: store.get("Heating") === "on",
                  tap: { args: "Heating" },
                },
                {
                  label: "Fan",
                  icon: "wind",
                  on: store.get("Fan") === "on",
                  tap: { args: "Fan" },
                },
              ],
            });
            """,
            of = "toggles", variant = "With a value",
        ) { _, _ ->
            // The heating is on: its switch says 21° in place of On. The fan is off and says Off.
            WidgetSpec.Toggles(
                WidgetBase(title = "Home", staleAfter = 60),
                listOf(
                    WidgetSpec.Toggle(
                        "Heating", on = true, tap = WidgetTap(args = "Heating"), icon = "temp", value = "21°"
                    ),
                    WidgetSpec.Toggle("Fan", on = false, tap = WidgetTap(args = "Fan"), icon = "wind"),
                ),
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
            "clock-seconds", WidgetKind.CLOCK, WidgetSize.S2X1, "Clock",
            lua = """
            widget.show{
              kind = "clock",
              title = "Tokyo",
              staleAfter = 604800,
              seconds = true,
              zones = {
                { zone = "Asia/Tokyo" },
              },
            }
            """,
            js = """
            widget.show({
              kind: "clock",
              title: "Tokyo",
              staleAfter: 604800,
              seconds: true,
              zones: [
                { zone: "Asia/Tokyo" },
              ],
            });
            """,
            of = "clock", variant = "With seconds",
        ) { _, _ ->
            WidgetSpec.Clock(
                WidgetBase(title = "Tokyo", staleAfter = 604800),
                listOf(WidgetSpec.Zone("Asia/Tokyo")),
                seconds = true,
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
        Widget(
            "versus-chess", WidgetKind.VERSUS, WidgetSize.S2X2, "Match",
            lua = """
            widget.show{
              kind = "versus",
              title = "Blitz",
              badge = "3+2",
              turn = 2,
              moving = true,
              sides = {
                {
                  name = "Iris Novak",
                  mark = "IN",
                  clock = 252,
                  detail = "2214 · White",
                },
                {
                  name = "Tomas Brandt",
                  mark = "TB",
                  clock = 238,
                  detail = "2187 · Black",
                },
              },
              caption = "31. Nf3",
            }
            """,
            js = """
            widget.show({
              kind: "versus",
              title: "Blitz",
              badge: "3+2",
              turn: 2,
              moving: true,
              sides: [
                {
                  name: "Iris Novak",
                  mark: "IN",
                  clock: 252,
                  detail: "2214 · White",
                },
                {
                  name: "Tomas Brandt",
                  mark: "TB",
                  clock: 238,
                  detail: "2187 · Black",
                },
              ],
              caption: "31. Nf3",
            });
            """,
            of = "versus", variant = "A game of chess",
        ) { _, _ ->
            // Each side's clock in seconds left: the side to move's counts down on the phone.
            WidgetSpec.Versus(
                WidgetBase(title = "Blitz"),
                listOf(
                    WidgetSpec.Side("Iris Novak", mark = "IN", detail = "2214 · White", clock = 252),
                    WidgetSpec.Side("Tomas Brandt", mark = "TB", detail = "2187 · Black", clock = 238),
                ),
                turn = 2,
                moving = true,
                line = "31. Nf3",
                badge = "3+2",
            )
        },
        Widget(
            "versus-scores", WidgetKind.VERSUS, WidgetSize.S2X2, "Match",
            lua = """
            widget.show{
              kind = "versus",
              title = "Cup final",
              badge = "LIVE",
              turn = 1,
              moving = true,
              sides = {
                {
                  name = "Harbour",
                  score = "187/4",
                  text = "18.2 ov",
                  lines = { "Ames 64* (38)", "Kahn 22* (11)" },
                },
                {
                  name = "Valley",
                  score = "201/6",
                  text = "20 ov",
                  lines = { "Ruiz 3/28 (4)" },
                },
              },
              caption = "Need 15 off 10",
            }
            """,
            js = """
            widget.show({
              kind: "versus",
              title: "Cup final",
              badge: "LIVE",
              turn: 1,
              moving: true,
              sides: [
                {
                  name: "Harbour",
                  score: "187/4",
                  text: "18.2 ov",
                  lines: ["Ames 64* (38)", "Kahn 22* (11)"],
                },
                {
                  name: "Valley",
                  score: "201/6",
                  text: "20 ov",
                  lines: ["Ruiz 3/28 (4)"],
                },
              ],
              caption: "Need 15 off 10",
            });
            """,
            of = "versus", variant = "Each side's own score",
        ) { _, _ ->
            WidgetSpec.Versus(
                WidgetBase(title = "Cup final"),
                listOf(
                    WidgetSpec.Side("Harbour", text = "18.2 ov", score = "187/4", lines = listOf("Ames 64* (38)", "Kahn 22* (11)")),
                    WidgetSpec.Side("Valley", text = "20 ov", score = "201/6", lines = listOf("Ruiz 3/28 (4)")),
                ),
                turn = 1,
                moving = true,
                line = "Need 15 off 10",
                badge = "LIVE",
            )
        },
        Widget(
            "versus-colors", WidgetKind.VERSUS, WidgetSize.S2X2, "Match",
            lua = """
            widget.show{
              kind = "versus",
              title = "Cup final",
              badge = "FT",
              score = "1–1",
              sides = {
                {
                  name = "Rivertown",
                  mark = "R",
                  color = "bad",
                  value = 55,
                  text = "55%",
                },
                {
                  name = "Hillside",
                  mark = "H",
                  color = "good",
                  value = 45,
                  text = "45%",
                },
              },
              caption = "Penalties next",
            }
            """,
            js = """
            widget.show({
              kind: "versus",
              title: "Cup final",
              badge: "FT",
              score: "1–1",
              sides: [
                {
                  name: "Rivertown",
                  mark: "R",
                  color: "bad",
                  value: 55,
                  text: "55%",
                },
                {
                  name: "Hillside",
                  mark: "H",
                  color: "good",
                  value: 45,
                  text: "45%",
                },
              ],
              caption: "Penalties next",
            });
            """,
            of = "versus", variant = "In team colors",
        ) { _, _ ->
            WidgetSpec.Versus(
                WidgetBase(title = "Cup final"),
                listOf(
                    WidgetSpec.Side("Rivertown", mark = "R", color = ColorRole.BAD, value = 55f, text = "55%"),
                    WidgetSpec.Side("Hillside", mark = "H", color = ColorRole.GOOD, value = 45f, text = "45%"),
                ),
                score = "1–1",
                line = "Penalties next",
                badge = "FT",
            )
        },
        Widget(
            "versus-details", WidgetKind.VERSUS, WidgetSize.S2X2, "Match",
            lua = """
            widget.show{
              kind = "versus",
              title = "League",
              badge = "67'",
              score = "2–1",
              sides = {
                {
                  name = "North",
                  color = "info",
                  detail = "3rd · 41 pts",
                },
                {
                  name = "South",
                  color = "warn",
                  detail = "7th · 33 pts",
                },
              },
              caption = "Second half",
            }
            """,
            js = """
            widget.show({
              kind: "versus",
              title: "League",
              badge: "67'",
              score: "2–1",
              sides: [
                {
                  name: "North",
                  color: "info",
                  detail: "3rd · 41 pts",
                },
                {
                  name: "South",
                  color: "warn",
                  detail: "7th · 33 pts",
                },
              ],
              caption: "Second half",
            });
            """,
            of = "versus", variant = "With each side's place",
        ) { _, _ ->
            WidgetSpec.Versus(
                WidgetBase(title = "League"),
                listOf(
                    WidgetSpec.Side("North", color = ColorRole.INFO, detail = "3rd · 41 pts"),
                    WidgetSpec.Side("South", color = ColorRole.WARN, detail = "7th · 33 pts"),
                ),
                score = "2–1",
                line = "Second half",
                badge = "67'",
            )
        },
        Widget(
            "sky-moon", WidgetKind.SKY, WidgetSize.S2X2, "Sky",
            lua = """
            local t = os.date("*t")
            local function on(day, hour, min)
              return os.time({
                year = t.year,
                month = t.month,
                day = t.day + day,
                hour = hour,
                min = min,
              })
            end

            widget.show{
              kind = "sky",
              title = "Lisbon",
              color = "link",
              rises = on(0, 13, 50),
              sets = on(1, 3, 12),
              phase = 0.78,
              waxing = true,
              phases = {
                {
                  phase = 1,
                  waxing = true,
                  at = on(3, 4, 0),
                },
                {
                  phase = 0.5,
                  at = on(10, 9, 0),
                },
                {
                  phase = 0,
                  at = on(17, 22, 0),
                },
              },
            }
            """,
            js = """
            const t = new Date();
            const on = (day, hour, min) => new Date(
              t.getFullYear(),
              t.getMonth(),
              t.getDate() + day,
              hour,
              min,
            ).getTime() / 1000;

            widget.show({
              kind: "sky",
              title: "Lisbon",
              color: "link",
              rises: on(0, 13, 50),
              sets: on(1, 3, 12),
              phase: 0.78,
              waxing: true,
              phases: [
                {
                  phase: 1,
                  waxing: true,
                  at: on(3, 4, 0),
                },
                {
                  phase: 0.5,
                  at: on(10, 9, 0),
                },
                {
                  phase: 0,
                  at: on(17, 22, 0),
                },
              ],
            });
            """,
            of = "sky", variant = "The moon",
        ) { now, zone ->
            val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
            fun on(day: Long, hour: Int, min: Int) =
                today.plusDays(day).atTime(hour, min).atZone(zone).toEpochSecond()
            WidgetSpec.Sky(
                WidgetBase(title = "Lisbon", color = ColorRole.LINK),
                rises = on(0, 13, 50),
                sets = on(1, 3, 12),
                phase = 0.78f,
                waxing = true,
                phases = listOf(
                    WidgetSpec.Phase(1f, waxing = true, at = on(3, 4, 0)),
                    WidgetSpec.Phase(0.5f, waxing = false, at = on(10, 9, 0)),
                    WidgetSpec.Phase(0f, waxing = false, at = on(17, 22, 0)),
                ),
            )
        },
    )
}
