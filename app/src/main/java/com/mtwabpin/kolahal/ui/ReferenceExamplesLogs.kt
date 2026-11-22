/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.script.Fmt
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.Markdown
import com.mtwabpin.kolahal.ui.ReferenceExamples.Line
import com.mtwabpin.kolahal.ui.ReferenceExamples.Log

/** The guide's log examples and the `fmt` samples ([ReferenceExamples.logs], [ReferenceExamples.fmt]). */
internal object ReferenceExamplesLogs {
    private fun bold(text: String) = StyledText.wrap(StyledText.Style.BOLD, text)
    private fun code(text: String) = StyledText.wrap(StyledText.Style.CODE, text)
    private fun color(color: ColorRole, text: String) = StyledText.color(color, text)

    /** The engines' `fmt` helpers, with numbers written as the scripts write them. */
    private val styles =
        Fmt { v -> if (v is Double) Json.numberText(v) else v?.toString().orEmpty() }

    private const val STORY_1 = "A launcher that is a terminal"
    private const val STORY_2 = "Lua 5.5 is out"

    val logs: List<Log> = listOf(
        Log(
            "log", "log(title, body?, ctx?)",
            lua = """
            log(
              "neovim/neovim",
              "Nvim 0.11.4",
              {
                url = "https://github.com/neovim/neovim/releases",
              }
            )

            log(
              "Pune, IN",
              "Today: clear, 24–31°C\nTomorrow: rain, 23–29°C"
            )

            log(
              "Kolahal on GitHub",
              {
                url = "https://github.com/mtwabpin/kolahal",
              }
            )

            -- A list: each story a title with its
            -- points under it, and a small space
            -- between the stories.
            local stories = {
              {
                title = "$STORY_1",
                points = 128,
                link = "https://example.com/1",
              },
              {
                title = "$STORY_2",
                points = 342,
                link = "https://example.com/2",
              },
            }

            for _, s in ipairs(stories) do
              log(
                s.title,
                s.points .. " points",
                { url = s.link, gap = true }
              )
            end

            log(
              "Your code is 4821",
              { copy = "4821" }
            )
            """,
            js = """
            log(
              "neovim/neovim",
              "Nvim 0.11.4",
              {
                url: "https://github.com/neovim/neovim/releases",
              },
            );

            log(
              "Pune, IN",
              "Today: clear, 24–31°C\nTomorrow: rain, 23–29°C",
            );

            log(
              "Kolahal on GitHub",
              {
                url: "https://github.com/mtwabpin/kolahal",
              },
            );

            // A list: each story a title with its
            // points under it, and a small space
            // between the stories.
            const stories = [
              {
                title: "$STORY_1",
                points: 128,
                link: "https://example.com/1",
              },
              {
                title: "$STORY_2",
                points: 342,
                link: "https://example.com/2",
              },
            ];

            for (const s of stories) {
              log(
                s.title,
                `${'$'}{s.points} points`,
                { url: s.link, gap: true },
              );
            }

            log(
              "Your code is 4821",
              { copy: "4821" },
            );
            """,
            lines = listOf(
                Line(
                    "neovim/neovim",
                    "Nvim 0.11.4",
                    url = "https://github.com/neovim/neovim/releases"
                ),
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
            local item = {
              title = "Show HN: Kolahal, a launcher",
              link = "https://example.com/hn",
            }

            md.log(
              "**3** new releases",
              "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119"
            )

            md.log(
              "**HN** " .. md.escape(item.title),
              nil,
              { url = item.link }
            )
            """,
            js = """
            const item = {
              title: "Show HN: Kolahal, a launcher",
              link: "https://example.com/hn",
            };

            md.log(
              "**3** new releases",
              "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119",
            );

            md.log(
              "**HN** " + md.escape(item.title),
              null,
              { url: item.link },
            );
            """,
            lines = listOf(
                Line(
                    Markdown.inline("**3** new releases"),
                    Markdown.block("- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119")
                ),
                Line(
                    Markdown.inline("**HN** " + Markdown.escape("Show HN: Kolahal, a launcher")),
                    url = "https://example.com/hn"
                ),
            ),
        ),
        Log(
            "styles", "fmt: styles and colors",
            lua = """
            local item = {
              score = 128,
              title = "$STORY_1",
              link = "https://example.com/1",
            }

            log(
              fmt.bold(item.score) .. " · "
                .. item.title,
              nil,
              { url = item.link }
            )

            log(
              "Today",
              fmt.list(
                {
                  "standup " .. fmt.code("10:00"),
                  "review",
                },
                true
              )
            )

            log(
              "BTC 67,120 USD "
                .. fmt.color("good", "▲ 1.2%")
            )
            """,
            js = """
            const item = {
              score: 128,
              title: "$STORY_1",
              link: "https://example.com/1",
            };

            log(
              `${'$'}{fmt.bold(item.score)} · ${'$'}{item.title}`,
              null,
              { url: item.link },
            );

            log(
              "Today",
              fmt.list(
                [
                  `standup ${'$'}{fmt.code("10:00")}`,
                  "review",
                ],
                true,
              ),
            );

            log(
              `BTC 67,120 USD ${'$'}{fmt.color("good", "▲ 1.2%")}`,
            );
            """,
            lines = listOf(
                Line(bold("128") + " · " + STORY_1, url = "https://example.com/1"),
                Line(
                    "Today",
                    StyledText.list(listOf("standup " + code("10:00"), "review"), numbered = true)
                ),
                Line("BTC 67,120 USD " + color(ColorRole.GOOD, "▲ 1.2%")),
            ),
        ),
        Log(
            "table", "fmt.table(rows, opts?)",
            lua = """
            log(
              "Prices in USD",
              fmt.table(
                {
                  { "Coin", "Price", "24h" },
                  {
                    "BTC",
                    "64,210",
                    fmt.color("good", "▲ 2.1%"),
                  },
                  {
                    "ETH",
                    "3,118",
                    fmt.color("bad", "▼ 0.8%"),
                  },
                },
                { header = true, align = "lrr" }
              )
            )
            """,
            js = """
            log(
              "Prices in USD",
              fmt.table(
                [
                  ["Coin", "Price", "24h"],
                  [
                    "BTC",
                    "64,210",
                    fmt.color("good", "▲ 2.1%"),
                  ],
                  [
                    "ETH",
                    "3,118",
                    fmt.color("bad", "▼ 0.8%"),
                  ],
                ],
                { header: true, align: "lrr" },
              ),
            );
            """,
            lines = listOf(
                Line(
                    "Prices in USD",
                    StyledText.table(
                        listOf(
                            listOf("Coin", "Price", "24h"),
                            listOf("BTC", "64,210", color(ColorRole.GOOD, "▲ 2.1%")),
                            listOf("ETH", "3,118", color(ColorRole.BAD, "▼ 0.8%")),
                        ),
                        header = true, align = "lrr",
                    ),
                ),
            ),
        ),
        Log(
            "links", "fmt.link(text, url)",
            lua = """
            local item = {
              title = "$STORY_1",
              comments = "https://example.com/c",
              link = "https://example.com/1",
            }

            log(
              item.title .. " · "
                .. fmt.link(
                  "138 comments",
                  item.comments
                ),
              { url = item.link }
            )

            md.log(
              "New in [neovim](https://github.com/neovim/neovim/releases)"
            )
            """,
            js = """
            const item = {
              title: "$STORY_1",
              comments: "https://example.com/c",
              link: "https://example.com/1",
            };

            log(
              `${'$'}{item.title} · ${'$'}{fmt.link("138 comments", item.comments)}`,
              { url: item.link },
            );

            md.log(
              "New in [neovim](https://github.com/neovim/neovim/releases)",
            );
            """,
            lines = listOf(
                Line(
                    "$STORY_1 · " + StyledText.link("https://example.com/c", "138 comments"),
                    url = "https://example.com/1"
                ),
                Line(Markdown.inline("New in [neovim](https://github.com/neovim/neovim/releases)")),
            ),
        ),
        Log(
            "tags", "fmt.tag(role?, text)",
            lua = """
            log(
              fmt.tag("bad", "LIVE") .. " IND v NZ"
            )

            log(
              "neovim/neovim " .. fmt.tag("v0.12.0")
                .. " "
                .. fmt.tag("warn", "pre-release")
            )
            """,
            js = """
            log(
              `${'$'}{fmt.tag("bad", "LIVE")} IND v NZ`,
            );

            log(
              `neovim/neovim ${'$'}{fmt.tag("v0.12.0")} ${'$'}{fmt.tag("warn", "pre-release")}`,
            );
            """,
            lines = listOf(
                Line(StyledText.tag(ColorRole.BAD, "LIVE") + " IND v NZ"),
                Line(
                    "neovim/neovim " + StyledText.tag(
                        ColorRole.ACCENT,
                        "v0.12.0"
                    ) + " " + StyledText.tag(ColorRole.WARN, "pre-release")
                ),
            ),
        ),
        Log(
            "bars", "fmt.bar, fmt.bars, fmt.ranges",
            lua = """
            log(
              "Pune · AQI 168",
              "Unhealthy\n"
                .. fmt.bar(
                  168,
                  { max = 300, color = "bad" }
                )
            )

            log(
              "Unlocks",
              fmt.bars(
                { { "Mon", 42 }, { "Tue", 21 } },
                { color = "good" }
              )
            )

            log(
              "Pune · 3 days",
              fmt.ranges(
                {
                  { "Today", 23, 31 },
                  { "Tomorrow", 22, 30 },
                },
                { unit = "°", color = "warn" }
              )
            )
            """,
            js = """
            log(
              "Pune · AQI 168",
              "Unhealthy\n"
                + fmt.bar(
                  168,
                  { max: 300, color: "bad" },
                ),
            );

            log(
              "Unlocks",
              fmt.bars(
                [["Mon", 42], ["Tue", 21]],
                { color: "good" },
              ),
            );

            log(
              "Pune · 3 days",
              fmt.ranges(
                [
                  ["Today", 23, 31],
                  ["Tomorrow", 22, 30],
                ],
                { unit: "°", color: "warn" },
              ),
            );
            """,
            lines = listOf(
                Line(
                    "Pune · AQI 168",
                    "Unhealthy\n" + styles.bar(
                        listOf(
                            168.0,
                            mapOf("max" to 300.0, "color" to "bad")
                        )
                    )
                ),
                Line(
                    "Unlocks",
                    styles.bars(
                        listOf(
                            listOf(listOf("Mon", 42.0), listOf("Tue", 21.0)),
                            mapOf("color" to "good")
                        )
                    )
                ),
                Line(
                    "Pune · 3 days",
                    styles.ranges(
                        listOf(
                            listOf(
                                listOf("Today", 23.0, 31.0),
                                listOf("Tomorrow", 22.0, 30.0)
                            ), mapOf("unit" to "°", "color" to "warn")
                        )
                    ),
                ),
            ),
        ),
        Log(
            "stats", "fmt.stats(items)",
            lua = """
            log(
              "Pune",
              fmt.stats({
                { "25°C", "☀️ Sunny" },
                { "36%", "humidity" },
                { "9 km/h", "wind" },
              })
            )
            """,
            js = """
            log(
              "Pune",
              fmt.stats([
                ["25°C", "☀️ Sunny"],
                ["36%", "humidity"],
                ["9 km/h", "wind"],
              ]),
            );
            """,
            lines = listOf(
                Line(
                    "Pune",
                    StyledText.stats(
                        listOf(
                            "25°C" to "☀️ Sunny",
                            "36%" to "humidity",
                            "9 km/h" to "wind"
                        )
                    )
                )
            ),
        ),
        Log(
            "buttons", "ctx.actions: buttons",
            lua = """
            log(
              "Battery at 92%",
              "Unplug the charger.",
              {
                actions = {
                  { "Snooze 30 min", "snooze 30" },
                  { "Not today", "snooze 720" },
                },
              }
            )
            """,
            js = """
            log(
              "Battery at 92%",
              "Unplug the charger.",
              {
                actions: [
                  ["Snooze 30 min", "snooze 30"],
                  ["Not today", "snooze 720"],
                ],
              },
            );
            """,
            lines = listOf(
                Line(
                    "Battery at 92%",
                    "Unplug the charger.",
                    actions = Json.stringify(
                        listOf(
                            listOf("Snooze 30 min", "snooze 30"),
                            listOf("Not today", "snooze 720")
                        )
                    )
                ),
            ),
        ),
    )

    private val ROLES = listOf("accent", "link", "info", "muted", "text", "good", "warn", "bad")

    val fmt: List<Log> = listOf(
        Log(
            "fmt.bold", "fmt.bold",
            lua = """log("Rent " .. fmt.bold("due today"))""",
            js = """log("Rent " + fmt.bold("due today"));""",
            lines = listOf(Line("Rent " + bold("due today"))),
        ),
        Log(
            "fmt.italic", "fmt.italic",
            lua = """
            log(
              fmt.italic("Simplicity first")
                .. " · Dijkstra"
            )
            """,
            js = """
            log(
              fmt.italic("Simplicity first")
                + " · Dijkstra",
            );
            """,
            lines = listOf(
                Line(
                    StyledText.wrap(
                        StyledText.Style.ITALIC,
                        "Simplicity first"
                    ) + " · Dijkstra"
                )
            ),
        ),
        Log(
            "fmt.underline", "fmt.underline",
            lua = """
            log(
              "Read the " .. fmt.underline("terms")
                .. " first"
            )
            """,
            js = """
            log(
              "Read the " + fmt.underline("terms")
                + " first",
            );
            """,
            lines = listOf(
                Line(
                    "Read the " + StyledText.wrap(
                        StyledText.Style.UNDERLINE,
                        "terms"
                    ) + " first"
                )
            ),
        ),
        Log(
            "fmt.strike", "fmt.strike",
            lua = """
            log(
              "Price " .. fmt.strike("520")
                .. " 480"
            )
            """,
            js = """
            log(
              "Price " + fmt.strike("520") + " 480",
            );
            """,
            lines = listOf(
                Line(
                    "Price " + StyledText.wrap(
                        StyledText.Style.STRIKE,
                        "520"
                    ) + " 480"
                )
            ),
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
            local roles = {
              "accent",
              "link",
              "info",
              "muted",
              "text",
              "good",
              "warn",
              "bad",
            }

            local shown = {}

            for i, role in ipairs(roles) do
              shown[i] = fmt.color(role, role)
            end

            log(table.concat(shown, " "))
            """,
            js = """
            const roles = [
              "accent",
              "link",
              "info",
              "muted",
              "text",
              "good",
              "warn",
              "bad",
            ];

            log(
              roles.map(
                (role) => fmt.color(role, role),
              ).join(" "),
            );
            """,
            lines = listOf(Line(ROLES.joinToString(" ") { color(ColorRole.named(it)!!, it) })),
        ),
        Log(
            "fmt.tag", "fmt.tag",
            lua = """
            log(
              fmt.tag("bad", "LIVE") .. " "
                .. fmt.tag("NEW") .. " "
                .. fmt.tag("good", "v1.2")
            )
            """,
            js = """
            log(
              fmt.tag("bad", "LIVE") + " "
                + fmt.tag("NEW") + " "
                + fmt.tag("good", "v1.2"),
            );
            """,
            lines = listOf(
                Line(
                    StyledText.tag(
                        ColorRole.BAD,
                        "LIVE"
                    ) + " " + StyledText.tag(ColorRole.ACCENT, "NEW") + " " + StyledText.tag(
                        ColorRole.GOOD,
                        "v1.2"
                    )
                )
            ),
        ),
        Log(
            "fmt.link", "fmt.link",
            lua = """
            log(
              "Read "
                .. fmt.link(
                  "the guide",
                  "https://mtwabpin.github.io/kolahal/"
                )
            )
            """,
            js = """
            log(
              "Read "
                + fmt.link(
                  "the guide",
                  "https://mtwabpin.github.io/kolahal/",
                ),
            );
            """,
            lines = listOf(
                Line(
                    "Read " + StyledText.link(
                        "https://mtwabpin.github.io/kolahal/",
                        "the guide"
                    )
                )
            ),
        ),
        Log(
            "fmt.list", "fmt.list",
            lua = """
            log(
              "Today",
              fmt.list(
                { "standup", "review" },
                true
              )
            )
            """,
            js = """
            log(
              "Today",
              fmt.list(["standup", "review"], true),
            );
            """,
            lines = listOf(
                Line(
                    "Today",
                    StyledText.list(listOf("standup", "review"), numbered = true)
                )
            ),
        ),
        Log(
            "fmt.table", "fmt.table",
            lua = """
            log(
              "Rates",
              fmt.table(
                {
                  { "USD", "84.40" },
                  { "EUR", "91.20" },
                },
                { align = "lr" }
              )
            )
            """,
            js = """
            log(
              "Rates",
              fmt.table(
                [
                  ["USD", "84.40"],
                  ["EUR", "91.20"],
                ],
                { align: "lr" },
              ),
            );
            """,
            lines = listOf(
                Line(
                    "Rates",
                    StyledText.table(
                        listOf(listOf("USD", "84.40"), listOf("EUR", "91.20")),
                        header = false,
                        align = "lr"
                    )
                )
            ),
        ),
        Log(
            "fmt.bar", "fmt.bar",
            lua = """
            log(
              "Battery",
              fmt.bar(0.68, { color = "good" })
            )
            """,
            js = """
            log(
              "Battery",
              fmt.bar(0.68, { color: "good" }),
            );
            """,
            lines = listOf(Line("Battery", styles.bar(listOf(0.68, mapOf("color" to "good"))))),
        ),
        Log(
            "fmt.bars", "fmt.bars",
            lua = """
            log(
              "Unlocks",
              fmt.bars({
                { "Mon", 42 },
                { "Tue", 21 },
              })
            )
            """,
            js = """
            log(
              "Unlocks",
              fmt.bars([["Mon", 42], ["Tue", 21]]),
            );
            """,
            lines = listOf(
                Line(
                    "Unlocks",
                    styles.bars(listOf(listOf(listOf("Mon", 42.0), listOf("Tue", 21.0))))
                )
            ),
        ),
        Log(
            "fmt.ranges", "fmt.ranges",
            lua = """
            log(
              "Pune",
              fmt.ranges(
                {
                  { "Today", 23, 31 },
                  { "Tomorrow", 22, 30 },
                },
                { unit = "°" }
              )
            )
            """,
            js = """
            log(
              "Pune",
              fmt.ranges(
                [
                  ["Today", 23, 31],
                  ["Tomorrow", 22, 30],
                ],
                { unit: "°" },
              ),
            );
            """,
            lines = listOf(
                Line(
                    "Pune",
                    styles.ranges(
                        listOf(
                            listOf(
                                listOf("Today", 23.0, 31.0),
                                listOf("Tomorrow", 22.0, 30.0)
                            ), mapOf("unit" to "°")
                        )
                    )
                )
            ),
        ),
        Log(
            "fmt.stats", "fmt.stats",
            lua = """
            log(
              "Pune",
              fmt.stats({
                { "25°C", "Sunny" },
                { "36%", "humidity" },
              })
            )
            """,
            js = """
            log(
              "Pune",
              fmt.stats([
                ["25°C", "Sunny"],
                ["36%", "humidity"],
              ]),
            );
            """,
            lines = listOf(
                Line(
                    "Pune",
                    StyledText.stats(listOf("25°C" to "Sunny", "36%" to "humidity"))
                )
            ),
        ),
    )
}
