// webnumber.js — one number from any JSON address, in a widget: a price, a count, a sensor at home.
// @title Web number
// @desc One number from a JSON address, set by its url and path options: a widget
// @author Kolahal
// @version 1.0
// @widget stat 2x1
// @every 1h
// @tags json, number, api, sensor
// @requires 0.5.8
// @option url "" The address of a JSON document (https://…)
// @option path "" Where the number is in it, such as data.price or items.0.count
// @option label "" What to call the number; empty: the last word of the path
//
// Talks to: the address in its url option.
//
// Its widget, once you placed it on the widgets page, shows the number large, with its label
// under it. A tap opens the address. Set it up in the script's sheet (Options), or with:
//   script set webnumber url https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd
//   script set webnumber path bitcoin.usd
//   script set webnumber label BTC
//
// The path names the keys from the top of the document, with dots between them. A number picks
// an item of a list, counted from 0. An empty path means the document itself is the number.
// It logs no number: the widget is what it is for.

// The widgets page asked for this run.
const quiet = !!event && event.name === "widget";

// A failure: what didn't work, in the theme's bad colour, and why under it. In a run for the
// widget it is an error instead, so the run counts as failed and the card says so.
const fail = (what, why) => {
  if (quiet) throw new Error(`${what}: ${why}`);
  log(fmt.color("bad", what), why);
};

// Only a placed widget is filled. Until then its timer has nothing to do, so it asks no site.
const placed = typeof widget !== "undefined" && (quiet || allowed("widget"));
if (!placed) {
  // Say once where the widget is, for a run by hand. Later runs say nothing.
  if (!store.get("told")) {
    store.set("told", "1");
    log(fmt.color("muted", "webnumber fills a widget: add it on the widgets page (+ Add)"));
  }
  return;
}

const url = (options.url || "").trim();
const path = (options.path || "").trim();
const named = (options.label || "").trim();

// The card's heading: the same words as its @title line.
const TITLE = "Web number";

// Not set up yet. This is no failure, so the card says what to set. It has no tap of its own, so
// a tap on it runs this again.
if (url === "") {
  widget.show({ title: TITLE, value: "–", label: "Set its url option, then tap here" });
  return;
}
if (!/^https?:\/\/\S+$/i.test(url)) return fail("No number", "Its url option must be a web address (https://…)");

// After a failure the card keeps the number it had.
let r;
try {
  // Kept for 10 minutes: changing the path or the label then asks the site nothing new.
  r = await http.cached(url, 600);
} catch (e) {
  return fail("No number", `Network error: ${e.message || e}`);
}
if (!r.ok) return fail("No number", `The address answered HTTP ${r.status}`);

let found;
try {
  found = JSON.parse(r.body);
} catch (e) {
  return fail("No number", "The address didn't send JSON");
}

// Walk the path from the top of the document: "data.price", or "items.0.count" for a list's item.
// Only the document's own keys count, so a name such as "constructor" finds nothing.
const keys = path.split(".").map((k) => k.trim()).filter(Boolean);
for (const key of keys) {
  const own = found != null && typeof found === "object" && Object.prototype.hasOwnProperty.call(found, key);
  found = own ? found[key] : undefined;
}

// Only a number or a text can be shown as one value: not a list, and not an object.
if (typeof found !== "number" && typeof found !== "string") {
  // The whole document, and it isn't a number: the path isn't set yet.
  if (keys.length === 0) {
    widget.show({ title: TITLE, value: "–", label: "Set its path option, then tap here" });
    return;
  }
  // What is there instead, for the failure's line.
  let what = String(found);
  if (found === undefined) what = "nothing";
  else if (Array.isArray(found)) what = "a list";
  else if (found !== null && typeof found === "object") what = "an object";
  return fail("No number", `At ${path} there is ${what}`);
}

// 67123.456 -> "67,123.46": at most 2 decimals from 1 up, and 4 digits under 1. Commas group the
// digits from 10,000 up, so a year stays "2026".
function shown(n) {
  if (!Number.isFinite(n)) return String(n);
  const rounded = String(Math.abs(n) < 1 ? Number(n.toPrecision(4)) : Number(n.toFixed(2)));
  // A very large or very small number is written with an exponent: it stays as it is.
  if (/e/i.test(rounded)) return rounded;
  const [int, frac] = rounded.split(".");
  const grouped = int.replace("-", "").length > 4 ? int.replace(/\B(?=(\d{3})+(?!\d))/g, ",") : int;
  return frac ? `${grouped}.${frac}` : grouped;
}

// Many sites send a number as text ("67123.4"). Other text shows as it is.
const text = typeof found === "string" ? found.trim() : "";
const value = typeof found === "number" ? shown(found) : text !== "" && Number.isFinite(Number(text)) ? shown(Number(text)) : text;

// Its label: the label option, or else the last word of the path ("price" for data.price).
const last = keys.slice().reverse().find((k) => !/^\d+$/.test(k)) || "";

// A tap opens the address. Its timer fills it every hour, and it counts as old after two hours:
// after a run that was missed.
widget.show({ title: TITLE, value, label: named || last, tap: { url, label: "Open the address" }, staleAfter: 2 * 3600 });
