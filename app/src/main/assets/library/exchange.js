// exchange.js — currency conversion with daily rates from open.er-api.com (no key).
// @command
// @desc Convert currency: exchange [amount] <from> [to…] (e.g. exchange 100 usd inr eur)
// @usage [<amount>] <from> [<to…>]
// @title Currency converter
// @option from USD Default currency to convert from
// @option to "EUR GBP INR JPY" Default currencies to convert to
//
// Usage:
//   exchange 100 usd inr       100 US dollars in Indian rupees
//   exchange usd inr eur       1 USD in INR and EUR
//   exchange 50 eur            50 EUR in USD
//   exchange                   its from and to options: USD -> EUR, GBP, INR, JPY to start with
//                              (good on a timer as a status line)
//
// Rates update once a day, so a timer more often than every few hours is wasted.

// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

const words = (argline || "").trim().split(/\s+/).filter(Boolean);

let amount = 1;
if (words.length > 0 && /^[\d.,]+$/.test(words[0])) amount = parseFloat(words.shift().replace(/,/g, ""));
if (!isFinite(amount)) return fail("No conversion", "That amount isn't a number");

const codes = words.map((w) => w.toUpperCase());
const optionCodes = (s) => (s || "").trim().split(/[\s,]+/).filter(Boolean).map((w) => w.toUpperCase());
if (codes.some((c) => !/^[A-Z]{3}$/.test(c))) {
  return log(fmt.color("muted", "Usage: exchange [amount] <from> [to…], e.g. exchange 100 usd inr"));
}
const background = codes.length === 0;
const from = codes[0] || optionCodes(options.from)[0] || "USD";
let targets = codes.slice(1);
if (targets.length === 0 && background) targets = optionCodes(options.to).filter((c) => c !== from);
if (targets.length === 0) targets = [from === "USD" ? "EUR" : "USD"];

let r;
try {
  r = await http.get(`https://open.er-api.com/v6/latest/${from}`);
} catch (e) {
  return fail("No rates", `Network error: ${e.message || e}`);
}
if (!r.ok) return fail("No rates", `open.er-api.com answered HTTP ${r.status}`);

let data;
try {
  data = JSON.parse(r.body);
} catch (e) {
  return fail("No rates", "open.er-api.com sent something unexpected");
}
if (data.result !== "success") return fail("No rates", `No currency ${from}`);

// 1234567.891 -> "1,234,567.89"
function num(n) {
  const digits = n >= 100 ? 2 : n >= 1 ? 3 : 5;
  const [int, frac] = n.toFixed(digits).split(".");
  return int.replace(/\B(?=(\d{3})+(?!\d))/g, ",") + (frac ? "." + frac : "");
}
// Whole amounts print without decimals: "100 USD", not "100.00 USD".
const amountText = Number.isInteger(amount) ? num(amount).split(".")[0] : num(amount);

const rows = [];
const parts = [];
for (const to of targets) {
  const rate = data.rates[to];
  if (rate == null) {
    fail(`${to}: no such currency`);
    continue;
  }
  rows.push({ to, value: num(amount * rate) });
  parts.push(`${to} ${num(rate)}`);
}
// Several targets: one table, the values on the right. One target: one line, which copies its value.
if (rows.length > 1) {
  log(`${amountText} ${from}`, fmt.table(rows.map((r) => [r.to, r.value]), { align: "lr" }));
} else {
  for (const r of rows) log(`${amountText} ${from} = ${fmt.bold(r.value)} ${r.to}`, { copy: r.value.replace(/,/g, "") });
}

if (background && parts.length > 0) status(`${from}: ` + parts.join(" · "));
