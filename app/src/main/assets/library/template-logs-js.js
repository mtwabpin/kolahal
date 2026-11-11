// @title Template: logs (JavaScript)
// @desc Every kind of line a script can log, each with the code that makes it, in JavaScript: copy what you need
// @author Kolahal
// @version 1.0
// @command
// @tags template, javascript
// @requires 0.5.0
//
// A reference for logging. Run `template-logs-js` and read its lines next to this code: each part
// below makes one kind of line. template-logs does the same in Lua.
// The scripting guide's API page explains each call: mtwabpin.github.io/kolahal/scripting/api/
// Talks to: imgs.xkcd.com (the picture only).

// A tap on a button below runs this script again with the button's words: event.name is "action".
if (event && event.name === "action") {
  return log(fmt.color("good", `You tapped ${event.label}`), `args: ${argline}`);
}

// 1. log(title): a plain line.
log("1 · A plain line: log(title)");

// 2. log(title, body): the title bold, as a heading, and the body muted under it, on as many lines as it needs.
log("2 · A title and a body", "The body is muted.\nIt can have several lines.");

// 3. ctx decides what a tap and a long-press do: url opens a link; copy is what "Copy text" copies.
log("3 · A line that opens a link", "Tap it", { url: "https://mtwabpin.github.io/kolahal/scripting/api/" });
log("3 · Your code is 4821", "Long-press → Copy text copies 4821 only", { copy: "4821" });

// 4. logs(): one line for each title, with no bodies.
logs(["4 · logs() makes one line", "4 · for each title"]);

// 5. fmt: styles for any part of a line. Safe for text from the web: nothing in it is read as a style.
log("5 · Styles", `${fmt.bold("bold")} ${fmt.italic("italic")} ${fmt.underline("underline")} ${fmt.strike("strike")} ${fmt.code("code")}`);

// 6. Colours by role: the theme picks each one, in a dark and a light version.
const roles = ["accent", "link", "info", "muted", "text", "good", "warn", "bad"];
log("6 · Colours", roles.map((role) => fmt.color(role, role)).join(" "));

// 7. Tags: a short label on a shaded chip, for a state or a version.
log(`7 · Tags ${fmt.tag("v1.0")} ${fmt.tag("good", "done")} ${fmt.tag("warn", "beta")} ${fmt.tag("bad", "LIVE")}`);

// 8. Links inside a line: each opens its own address. The line's own url is the rest of the line.
log(`8 · A story · ${fmt.link("138 comments", "https://news.ycombinator.com/")}`, { url: "https://mtwabpin.github.io/kolahal/" });

// 9. md.log reads Markdown in the title and the body: lists, **bold**, `code`, [links](…).
md.log("9 · **Markdown** with md.log", "- *italic*, ~~strike~~, ++underline++\n- `code` and [a link](https://mtwabpin.github.io/kolahal/)\n  - a second level\n1. a numbered item");

// 10. fmt.list: one item per line, with bullets, or numbered with true.
log("10 · A list", fmt.list([`standup ${fmt.code("10:00")}`, "review", "lunch"], true));

// 11. fmt.table: rows of cells. header: true mutes the first row; align is l or r per column.
log("11 · A table", fmt.table([
  ["Coin", "Price", "24h"],
  ["BTC", "64,210", fmt.color("good", "▲ 2.1%")],
  ["ETH", "3,118", fmt.color("bad", "▼ 0.8%")],
], { header: true, align: "lrr" }));

// 12. Bars: one bar, bars to compare, and ranges from low to high. Say the number in the text too.
log("12 · A bar: AQI 168 of 300", fmt.bar(168, { max: 300, color: "bad" }));
log("12 · Bars", fmt.bars([["Mon", 42], ["Tue", 21], ["Wed", 35]], { color: "good" }));
log("12 · Ranges", fmt.ranges([["Today", 23, 31], ["Tomorrow", 22, 30]], { unit: "°", color: "warn" }));

// 13. Big numbers: the first value large, the others smaller, each with its label under it.
log("13 · Big numbers", fmt.stats([["25°C", "☀️ Sunny"], ["36%", "humidity"], ["9 km/h", "wind"]]));

// 14. A picture under the line, as wide as the log (an http or https address).
log("14 · A picture", { image: "https://imgs.xkcd.com/comics/exploits_of_a_mom.png" });

// 15. A list of items: gap: true puts a small space above each item, so they stand apart.
["First story", "Second story", "Third story"].forEach((story, i) => {
  log(`15 · ${story}`, `${(i + 1) * 40} points`, { gap: true });
});

// 16. Buttons: up to 3 under a line. A tap runs this script again with the button's words.
log("16 · Buttons", "Tap one", { actions: [["Snooze 30 min", "snooze 30"], ["Done", "done"]] });

// 17. mono: "1": the terminal's font even when the log uses the phone's, so spaces line up columns.
log("17 · Monospace", "name    size\nkolahal  4 MB", { mono: "1" });

// 18. console.log logs too (its arguments joined with spaces); console.error logs an error line.
console.log("18 · console.log()", 42);
console.error("18 · console.error() logs an error line");

// 19. line.set changes a line where it is, while the script runs: "Working…" turns into the answer.
const line = log(fmt.color("muted", "19 · Working…"));
for (let step = 1; step <= 5; step++) {
  line.set(`19 · Step ${step} of 5`, fmt.bar(step, { max: 5 }));
  await sleep(300);
}
line.set("19 · Done: line.set changed this line in place");

// 20. The status line: a short line about this script. Pin the script (`script pin template-logs-js`)
// to see it at the top of home. status(null) clears it.
const now = new Date();
status(`template-logs-js ran at ${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`);

// Also: throw new Error("…") stops the script and logs the message as an error line.
// And whatever the script returns is logged as its result line (an object as JSON):
return "21 · The returned value";
