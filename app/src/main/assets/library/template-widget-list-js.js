// @title Template: checklist widget (JavaScript)
// @desc A starting point for a widget you tap, in JavaScript: a checklist for each day, where a tap on a row ticks it
// @author Kolahal
// @version 1.0
// @widget list 2x2
// @at 00:01
// @tags template, widget, javascript, checklist
// @requires 0.5.9
// @option items "Water the plants, Vitamins, A walk" The things to do each day, with commas between them
//
// The JavaScript version of template-widget-list. It is a checklist for each day: a row for each
// thing in its items option, with a checkbox in front. A tap on a row ticks it, or takes the tick
// away. Each new day starts with no ticks. Nothing leaves the phone.
//
// To see the card: install the script, tap + on the widgets page, and pick "Template: checklist
// widget (JavaScript)" in the Kolahal group.
//
// How a tap works:
// 1. Each row has tap: { args: "tick 2" }. A tap on it runs this script again with those words.
// 2. In that run, event.name is "action", and args is ["tick", "2"].
// 3. The script changes what it keeps, then calls widget.show with the new rows. Kolahal never
//    ticks a box by itself: the card shows what the script tells it.
//
// The @at line runs it a minute after midnight, so the card starts the new day with no ticks.

// What started this run: a tap ("action"), the widgets page ("widget"), its daily time ("at"), or you (null).
const started = event ? event.name : null;

// The things to do, from the items option: "Water the plants, Vitamins" gives two rows.
const things = (options.items || "").split(",").map((t) => t.trim()).filter(Boolean);

// store keeps text between runs, for this script only. Each widget of this script keeps its own
// ticks: event.instance is "2", "3"… in a run for a second or third widget, and undefined for the first.
const key = (event && event.instance) || "";
const now = new Date();
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;

// Today's ticks, kept as the ticked things, one on each line. The ticks of another day don't count.
const ticked = new Set(store.get(`day${key}`) === today ? (store.get(`ticked${key}`) || "").split("\n").filter(Boolean) : []);

// A tap on a row: "tick 2" ticks the second thing, or takes its tick away.
const thing = args[0] === "tick" ? things[Number(args[1]) - 1] : undefined;
if (thing !== undefined) {
  if (ticked.has(thing)) ticked.delete(thing);
  else ticked.add(thing);
  store.set(`day${key}`, today);
  store.set(`ticked${key}`, things.filter((t) => ticked.has(t)).join("\n"));
}

// The rows: each thing, with done: true or false for its checkbox, and a tap that ticks it.
const rows = things.map((t, i) => ({ title: t, done: ticked.has(t), tap: { args: `tick ${i + 1}` } }));
const done = things.filter((t) => ticked.has(t)).length;

// The card: its title counts the ticks. Once every thing is ticked, it is in the theme's good
// colour. A card too small for every row shows the first ones, and "+2 more" on its title line.
// Call widget.show in every run that a tap starts, even when nothing changed: the card shows
// only what the script tells it.
const placed = widget.show({
  title: `Today · ${done} of ${things.length}`,
  icon: "check",
  color: things.length > 0 && done === things.length ? "good" : undefined,
  items: rows,
  empty: "Set its items option",
});

// Only a run by hand logs: the list, with a mark for each tick.
if (started === null) {
  log(`Today · ${done} of ${things.length}`, things.map((t) => (ticked.has(t) ? "✓ " : "· ") + t).join("\n"));
  if (!placed) log(fmt.color("muted", "To see the card: tap + on the widgets page and pick Template: checklist widget (JavaScript)"));
}
