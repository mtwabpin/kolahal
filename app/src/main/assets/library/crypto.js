// crypto.js — cryptocurrency prices from CoinGecko (free public API, no key).
// @command
// @desc Coin prices: crypto [ids…] [--vs eur] (default: its coins and currency options)
// @usage [<ids…>] [--vs <currency>]
// @title Crypto prices
// @widget list 2x2
// @every 30m
// @option coins "bitcoin ethereum" Default CoinGecko ids
// @option currency usd Price currency, unless set with --vs
//
// Usage:
//   crypto                       its coins in its currency: bitcoin and ethereum in USD to start with
//   crypto solana dogecoin       other coins (CoinGecko ids)
//   crypto bitcoin --vs eur      in another currency
//
// On a timer (no arguments) its status line is a compact price line, e.g. "BTC 67.1k ▲ 1.2% · ETH 3.1k ▼ 0.4%".
// Its widget, once you placed it on the widgets page, lists the same coins with their prices.

const list = (s) => (s || "").trim().split(/[\s,]+/).filter(Boolean).map((w) => w.toLowerCase());
const DEFAULT_IDS = list(options.coins).length > 0 ? list(options.coins) : ["bitcoin", "ethereum"];
const SYMBOLS = { bitcoin: "BTC", ethereum: "ETH", solana: "SOL", dogecoin: "DOGE", litecoin: "LTC", monero: "XMR", cardano: "ADA", ripple: "XRP" };

// The widgets page asked for this run: it fills the widget and logs nothing.
const quiet = !!event && event.name === "widget";

// A failure: what didn't work, in the theme's bad colour, and why under it. In a run for the
// widget it is an error instead, so the run counts as failed.
const fail = (what, why) => {
  if (quiet) throw new Error(`${what}: ${why}`);
  return log(fmt.color("bad", what), why);
};

const words = (argline || "").trim().split(/[\s,]+/).filter(Boolean);
let vs = list(options.currency)[0] || "usd";
const ids = [];
for (let i = 0; i < words.length; i++) {
  if (words[i] === "--vs" && words[i + 1]) vs = words[++i].toLowerCase();
  else ids.push(words[i].toLowerCase());
}
if (ids.length === 0) ids.push(...DEFAULT_IDS);

const url =
  "https://api.coingecko.com/api/v3/simple/price" +
  `?ids=${encodeURIComponent(ids.join(","))}&vs_currencies=${encodeURIComponent(vs)}&include_24hr_change=true`;

let r;
try {
  r = await http.get(url, { Accept: "application/json" });
} catch (e) {
  return fail("No prices", `Network error: ${e.message || e}`);
}
if (r.status === 429 && quiet) return fail("CoinGecko says wait", "too many requests");
if (r.status === 429) return log(fmt.color("warn", "CoinGecko says wait"), "Too many requests: try again in a minute");
if (!r.ok) return fail("No prices", `CoinGecko answered HTTP ${r.status}`);

let prices;
try {
  prices = JSON.parse(r.body);
} catch (e) {
  return fail("No prices", "CoinGecko sent something unexpected");
}

// 67123.4 -> "67,123.40"; tiny prices keep more decimals.
function num(n) {
  if (n >= 1) {
    const [int, frac] = n.toFixed(2).split(".");
    return int.replace(/\B(?=(\d{3})+(?!\d))/g, ",") + "." + frac;
  }
  return n.toPrecision(4);
}
const compact = (n) => (n >= 1000 ? (n / 1000).toFixed(1) + "k" : num(n));
const arrow = (c) => (c >= 0 ? "▲ " : "▼ ") + Math.abs(c).toFixed(1) + "%";

const rows = [];
const parts = [];
for (const id of ids) {
  const p = prices[id];
  if (!p || p[vs] == null) {
    // One coin without a price doesn't stop a run for the widget: the other coins still show.
    if (!quiet) fail(`${id}: no such coin`, `or no ${vs.toUpperCase()} price for it`);
    continue;
  }
  const change = p[`${vs}_24h_change`];
  const sym = SYMBOLS[id] || id.toUpperCase();
  rows.push({ id, sym, price: num(p[vs]), change });
  parts.push(`${sym} ${compact(p[vs])}` + (change != null ? ` ${arrow(change)}` : ""));
}
// Several coins: one table, the price and the day's change on the right, the change in the theme's
// good or bad colour (the arrow says it too). One coin: one line, and a tap opens its page.
const moves = (r) => (r.change != null ? fmt.color(r.change >= 0 ? "good" : "bad", arrow(r.change)) : "");
// Its widget, for its own coins (no arguments): each coin with its price, and the day's change in
// the theme's good or bad colour. A tap runs crypto again and turns home to the log.
if (typeof widget !== "undefined" && words.length === 0) {
  widget.show({
    title: `Prices in ${vs.toUpperCase()}`,
    items: rows.map((r) => {
      const item = { title: r.sym, trailing: r.price };
      if (r.change != null) {
        item.detail = arrow(r.change);
        item.color = r.change >= 0 ? "good" : "bad";
      }
      return item;
    }),
    empty: "No prices",
    tap: { args: "", page: "log" },
  });
}
if (quiet) {
  // A run for the widget logs nothing.
} else if (rows.length > 1) {
  log(`Prices in ${vs.toUpperCase()}`, fmt.table([["Coin", "Price", "24h"], ...rows.map((r) => [r.sym, r.price, moves(r)])], { header: true, align: "lrr" }), {
    url: "https://www.coingecko.com/",
  });
} else if (rows.length === 1) {
  // One coin: its name, then big numbers: the price large, with the coin and the currency under it,
  // and the day's change in the good or bad colour. A tap opens its page.
  const r = rows[0];
  const name = r.id.split("-").map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
  const numbers = [[r.price, `${r.sym} in ${vs.toUpperCase()}`]];
  if (r.change != null) numbers.push([moves(r), "24 hours"]);
  log(name, fmt.stats(numbers), { url: `https://www.coingecko.com/en/coins/${r.id}` });
}

if (words.length === 0 && parts.length > 0) status(parts.join(" · "));
