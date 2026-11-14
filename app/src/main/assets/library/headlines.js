// headlines.js — the newest items of one feed you choose, in a widget.
// @title Feed headlines
// @desc The newest items of one feed, set by its url option: a widget
// @author Kolahal
// @version 1.0
// @widget list 4x2
// @every 1h
// @tags news, feed, rss, atom
// @requires 0.5.8
// @option url https://hnrss.org/frontpage The feed's address (RSS or Atom)
//
// Talks to: the feed in its url option.
//
// Its widget, once you placed it on the widgets page, lists the feed's newest items under the
// feed's own title. A tap on a row opens that item. Change the feed in the script's sheet
// (Options), or with:
//   script set headlines url https://example.com/feed.xml
//
// It logs no headlines: the widget is what it is for. To read a feed in the log, with unread
// counts, use the feed command.

// The widgets page asked for this run.
const quiet = !!event && event.name === "widget";

// A failure: what didn't work, in the theme's bad colour, and why under it. In a run for the
// widget it is an error instead, so the run counts as failed and the card says so.
const fail = (what, why) => {
  if (quiet) throw new Error(`${what}: ${why}`);
  log(fmt.color("bad", what), why);
};

// Only a placed widget is filled. Until then its timer has nothing to do, so it reads no feed.
const placed = typeof widget !== "undefined" && (quiet || allowed("widget"));
if (!placed) {
  // Say once where the widget is, for a run by hand. Later runs say nothing.
  if (!store.get("told")) {
    store.set("told", "1");
    log(fmt.color("muted", "headlines fills a widget: add it on the widgets page (+ Add)"));
  }
  return;
}

// A web address, the only kind of link a widget takes.
const web = /^https?:\/\/\S+$/i;

const url = (options.url || "").trim();
if (!web.test(url)) return fail("No headlines", "Set its url option to a feed's address (https://…)");

// After a failure the card keeps the headlines it had.
let feed;
try {
  feed = await rss.fetch(url);
} catch (e) {
  return fail("No headlines", `Couldn't read the feed: ${e.message || e}`);
}

// The newest 12 items: a list keeps that many, and shows as many as fit its size.
const items = (feed.items || []).slice(0, 12).map((it) => {
  const row = { title: (it.title || "").trim() || "(untitled)" };
  // A row with no web link still shows; a tap on it does nothing.
  const link = (it.link || "").trim();
  if (web.test(link)) row.tap = { url: link };
  return row;
});

// Its timer fills it every hour. It counts as old after two hours: after a run that was missed.
widget.show({ title: (feed.title || "").trim() || "Headlines", items, empty: "No items", staleAfter: 2 * 3600 });
