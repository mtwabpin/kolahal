// xkcd.js — the latest xkcd comic (or a given / random one).
// @command
// @desc xkcd comic: xkcd | xkcd <number> | xkcd random
// @usage [random|<number>]
// @title xkcd
// @widget image 4x2
// @every 6h
// @notify
//
// On a timer (no arguments) it notifies only when a new comic is out.
// The comic shows in the log under its title; tap the title to open it on xkcd.com.
// Its widget, once you placed it on the widgets page, shows the latest comic with its number and
// title under it; a tap on it opens the comic on xkcd.com.

// The widgets page asked for this run: it fills the widget and logs nothing.
const quiet = !!event && event.name === "widget";

// A failure: what didn't work, in the theme's bad colour, and why under it. In a run for the
// widget it is an error instead, so the run counts as failed.
const fail = (what, why) => {
  if (quiet) throw new Error(`${what}: ${why}`);
  return log(fmt.color("bad", what), why);
};

const arg = (argline || "").trim().toLowerCase();

async function comic(num) {
  const url = num ? `https://xkcd.com/${num}/info.0.json` : "https://xkcd.com/info.0.json";
  const r = await http.get(url);
  if (r.status === 404) throw new Error(`xkcd has no comic #${num}`);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return JSON.parse(r.body);
}

let c;
try {
  if (arg === "random") {
    const latest = await comic(null);
    let n = 1 + Math.floor(Math.random() * latest.num);
    if (n === 404) n = 405; // #404 famously does not exist
    c = await comic(n);
  } else if (/^\d+$/.test(arg)) {
    c = await comic(parseInt(arg, 10));
  } else if (arg === "") {
    c = await comic(null);
  } else {
    return log(fmt.color("muted", "Usage: xkcd | xkcd <number> | xkcd random"));
  }
} catch (e) {
  return fail("No comic", `${e.message || e}`);
}

const link = `https://xkcd.com/${c.num}/`;
const name = c.safe_title || c.title || "xkcd";

// Its widget, for the latest comic (no arguments): the whole comic, with its number and title under
// it. Home fetches the picture itself, and only over https. The comic's own words are what a screen
// reader says.
if (typeof widget !== "undefined" && arg === "" && c.img) {
  widget.show({
    image: String(c.img).replace(/^http:/, "https:"),
    alt: c.alt || name,
    caption: `#${c.num} ${name}`,
    fit: "fit",
    tap: { url: link, label: "Open on xkcd.com" },
    staleAfter: 6 * 3600,
  });
}
if (quiet) return;

// The comic's title, its alt text under it.
log(`#${c.num} ${name} (${c.year}-${String(c.month).padStart(2, "0")}-${String(c.day).padStart(2, "0")})`, c.alt, {
  url: link,
  copy: link,
  image: c.img,
});

// Background runs: notify once per new comic.
if (arg === "") {
  const last = store.get("last");
  if (last !== String(c.num)) {
    store.set("last", String(c.num));
    if (last != null) notify(`xkcd #${c.num}`, name, { url: link });
  }
}
