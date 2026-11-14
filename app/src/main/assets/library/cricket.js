// @title Live cricket
// @desc Live cricket scores: the team option's live match as its status line, to pin at the top of home; cricket live lists them all
// @author Kolahal
// @version 1.6
// @command
// @usage [live|<team>]
// @widget stat 4x1
// @every 15m
// @tags sport, cricket, live
// @requires 0.4.7
// @option team any The team whose live match it follows, such as India or Mumbai; any: the first live international
// @option refresh 30 Seconds between updates while home shows the match (10 at the fastest)
//
// Talks to: site.web.api.espn.com, ESPN's scores. It isn't a documented API, so it can change
// without notice.
//
// On its timer it looks for a live match of the team option and keeps its score as its status
// line, the overs as the bar. Pin it (`script pin cricket`) and the score shows at the top of home,
// fetched again every refresh seconds while home shows it (from Kolahal 0.4.14; older ones show the
// line in the header, updated on the timer). Once the match ends, its result stays two hours.
// `cricket India` shows India's match and follows it if it's on; `cricket live` lists every live
// match.
//
// From Kolahal 0.4.16, `cricket live` and a team's match show as tables (fmt.table), a match with a
// tag for how it stands (LIVE, RESULT, UPCOMING) and the overs as a bar; a typed command shows
// "Fetching scores…" at once, which turns into the answer. An older Kolahal shows them as lines, as
// before.
//
// Its widget, once you placed it on the widgets page, is the live score: both sides' scores large,
// how the match stands, and the overs as a bar that moves while the match is on. In a chase it has
// the runs to win and the balls left beside the score. While the page shows it, it is fetched again
// every refresh seconds. A tap lists the live matches in the log.

const PANEL = "https://site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel";
const BOARD = (league) => `https://site.web.api.espn.com/apis/site/v2/sports/cricket/${league}/scoreboard`;
const RESULT_HOURS = 2;

async function getJson(url) {
  const r = await http.get(url);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return JSON.parse(r.body);
}

// Every match the panel has, each with its league: [{ league, event }].
async function allMatches() {
  const panel = await getJson(PANEL);
  return (panel.scores || []).flatMap((s) => {
    const league = s.leagues && s.leagues[0] ? s.leagues[0].id : null;
    return (s.events || []).map((event) => ({ league, event }));
  });
}

const teams = (event) => (event.competitions && event.competitions[0] && event.competitions[0].competitors) || [];
const state = (event) => (event.status && event.status.type && event.status.type.state) || "";
const international = (event) => {
  const c = event.competitions && event.competitions[0] && event.competitions[0].class;
  return !!c && c.internationalClassId && c.internationalClassId !== "0";
};

// How well a team named [name] plays in the match, by its name, short name or abbreviation:
// 2 for the exact name, 1 for a longer name that starts with it ("Mumbai" for "Mumbai Indians"),
// 0 when no team matches.
function plays(event, name) {
  const want = name.toLowerCase();
  let best = 0;
  for (const c of teams(event)) {
    const t = c.team || {};
    for (const n of [t.displayName, t.shortDisplayName, t.name, t.abbreviation].filter(Boolean)) {
      if (n.toLowerCase() === want) return 2;
      if (n.toLowerCase().startsWith(want + " ")) best = 1;
    }
  }
  return best;
}

// The matches in [list] of a team named [name]: those with the exact name if there are any, else
// those with a longer name. So "India" finds India, not India A or India Women.
function matchesOf(list, name) {
  const scored = list.map((m) => [m, plays(m.event, name)]);
  const best = Math.max(0, ...scored.map(([, s]) => s));
  return best ? scored.filter(([, s]) => s === best).map(([m]) => m) : [];
}

// A score without what the line can do without: "42/1 (8.5/50 ov, target 186)" is "42/1 (8.5 ov)".
const shortScore = (s) => (s || "").replace(/\((\d+(?:\.\d)?)\/\d+ ov(?:, target \d+)?\)/, "($1 ov)").replace(/, target \d+/, "");

// "NZ 185 v IND 42/1 (8.5 ov) · India require 144 runs"
function line(event) {
  const sides = teams(event).map((c) => `${(c.team && c.team.abbreviation) || "?"} ${shortScore(c.score)}`.trim());
  const summary = (event.status && event.status.summary) || "";
  return sides.join(" v ") + (summary ? ` · ${summary}` : "");
}

// The batting side's overs: how far its innings is, 0 to 1, and as written ("40.2/50"); null without
// a limit (a Test).
// - The batting side: the one chasing a target, if there is one; else the first with overs.
// - All out (10 wickets: runs alone, "185 (42.3/47 ov)", or "185/10"): its innings is over, so it is
//   full, however many overs were left.
function overs(event) {
  const re = /\((\d+)(?:\.(\d))?\/(\d+) ov/;
  const scores = teams(event).map((c) => (c.score || "").trim());
  const batting = scores.find((s) => s.includes("target") && re.test(s)) || scores.find((s) => re.test(s));
  if (!batting) return null;
  const m = batting.match(re);
  const text = `${m[1]}${m[2] ? "." + m[2] : ""}/${m[3]}`;
  if (/^\d+\s*\(/.test(batting) || /\/10\b/.test(batting)) return { share: 1, text: `all out, ${text}` };
  return { share: Math.min(1, (Number(m[1]) * 6 + Number(m[2] || 0)) / (Number(m[3]) * 6)), text };
}

// Big numbers for a live match (fmt.stats): the batting side's score large ("237/4"), with the side
// and its overs under it; then, in a chase, the runs it needs and the balls left, as the summary
// gives them ("India require 64 runs with 60 balls remaining"). Null when no side is batting.
function scoreNumbers(event) {
  const re = /\((\d+(?:\.\d)?)\/\d+ ov/;
  const sides = teams(event);
  const batting = sides.find((c) => (c.score || "").includes("target") && re.test(c.score)) || sides.find((c) => re.test(c.score || ""));
  if (!batting) return null;
  const who = (batting.team && (batting.team.shortDisplayName || batting.team.displayName)) || (batting.team && batting.team.abbreviation) || "?";
  const numbers = [[batting.score.split(" (")[0].trim(), `${who} · ${batting.score.match(re)[1]} overs`]];
  const s = (event.status && event.status.summary) || "";
  const runs = s.match(/(\d+)\s+runs?\b/);
  const balls = s.match(/(\d+)\s+balls?\b/);
  if (runs) numbers.push([runs[1], "to win"]);
  if (balls) numbers.push([balls[1], "balls left"]);
  return { text: fmt.stats(numbers), chase: !!runs };
}

// How far the batting side's innings is, 0 to 1, for the status line's bar; null without a limit.
const progress = (event) => {
  const o = overs(event);
  return o ? o.share : null;
};

// An older Kolahal has no widgets: there the script only keeps its status line.
const hasWidget = typeof widget !== "undefined";

// A tap on its widget lists the live matches, and home turns to the log.
const TAP = { args: "live", page: "log", label: "Live matches" };

// Its widget for a match: both sides' scores large ("IND 237/4 · NZ 299/8"), and how the match
// stands under them.
// - While it's on: the overs as a bar that moves, and the widgets page asks again every [every]
//   seconds while it shows the card. A card one row tall has the bar in the place of the words.
// - In a chase: the runs to win and the balls left beside the score, as the summary gives them
//   ("India require 64 runs with 60 balls remaining").
function card(event, live, p, every) {
  const abbr = (c) => (c.team && c.team.abbreviation) || "?";
  const runs = (c) => (c.score || "").split(" (")[0].trim();
  const said = (event.status && event.status.summary) || "";
  const d = {
    icon: "🏏",
    value: teams(event).map((c) => `${abbr(c)} ${runs(c)}`.trim()).join(" · ") || "–",
    label: said,
    tap: TAP,
    // A result changes no more. A live score is old after a minute out of view.
    staleAfter: live ? 60 : 900,
  };
  if (!live) return d;
  d.color = "good";
  d.moving = true;
  d.refresh = every;
  if (p != null) d.progress = p;
  if (/\b(?:require|need)s?\b/i.test(said)) {
    const toWin = said.match(/(\d+)\s+(?:more\s+)?runs?\b/);
    const balls = said.match(/(\d+)\s+balls?\b/);
    d.more = [];
    if (toWin) d.more.push({ value: toWin[1], label: "to win" });
    if (balls) d.more.push({ value: balls[1], label: "balls left" });
  }
  return d;
}

// Its status line: the match's line, its bar when there's a limit, and runs again while it's live.
// Its widget shows the same match.
function showMatch(event) {
  const live = state(event) === "in";
  const p = live ? progress(event) : null;
  const every = Math.max(10, Number(options.refresh) || 30);
  if (live) status(line(event), p == null ? { refresh: every } : { progress: p, refresh: every });
  else status(line(event));
  if (hasWidget) widget.show(card(event, live, p, every));
}

// No match to follow: no status line, and its widget says so.
function showNone() {
  status(null);
  if (hasWidget) widget.show({ icon: "🏏", value: "–", label: "No live match", tap: TAP, staleAfter: 900 });
}

// The followed match ({ league, id, ended }) and what it shows now.
const kept = () => {
  try {
    return JSON.parse(store.get("match") || "null");
  } catch (e) {
    return null;
  }
};

async function followed(match) {
  const board = await getJson(BOARD(match.league));
  return (board.events || []).find((e) => e.id === match.id) || null;
}

// Fetches the followed match again: its line while it's on, its result for a while after.
async function update(match) {
  const event = await followed(match);
  if (!event) {
    store.set("match", null);
    return showNone();
  }
  if (state(event) === "post") {
    if (!match.ended) store.set("match", JSON.stringify({ ...match, ended: Date.now() }));
    else if (Date.now() - match.ended > RESULT_HOURS * 3600000) {
      store.set("match", null);
      return showNone();
    }
  }
  showMatch(event);
}

function follow(league, event) {
  store.set("match", JSON.stringify({ league, id: event.id }));
  showMatch(event);
}

const words = (argline || "").trim();
const match = kept();

if (event && event.name === "refresh") {
  // While home shows the score: only the followed match's league, a few KB.
  if (match) await update(match);
  else showNone();
  return;
}

// The widgets page asked for this run. It is as quiet as a refresh: it logs nothing, and while a
// match is on it fetches that match's league only.
const forWidget = !!event && event.name === "widget";

if (!words) {
  // The timer: the followed match while it's on, then the team option's next live one. A failed
  // fetch leaves the pinned score as it was until the next run, without a line every 15 minutes.
  try {
    if (match && !match.ended) return await update(match);
    if (match) await update(match);
    const team = (options.team || "any").trim();
    const live = (await allMatches()).filter((m) => state(m.event) === "in");
    const found = team.toLowerCase() === "any" ? live.find((m) => international(m.event)) : matchesOf(live, team)[0];
    if (found && (!match || found.event.id !== match.id)) follow(found.league, found.event);
    else if (!match) showNone();
  } catch (e) {
    // Tried again at the next run. A run for the widget fails instead, so its card says so.
    if (forWidget) throw e;
  }
  return;
}

// From Kolahal 0.4.16: a tag for how the match stands, a bar for the overs, and "Fetching scores…"
// at once, which turns into the answer in place (log returns the line). An older Kolahal has no
// fmt.bar, and gets lines as before.
const rich = typeof fmt.bar === "function";
const pending = rich ? log(fmt.color("muted", "Fetching scores…")) : null;
const show = (...a) => (pending ? pending.set(...a) : log(...a));

let matches;
try {
  matches = await allMatches();
} catch (e) {
  return show(fmt.color("bad", "No cricket scores"), `${e.message || e}`);
}

// Tables need Kolahal 0.4.16. An older one has no fmt.table, and gets lines as before.
const tables = typeof fmt.table === "function";
const abbr = (c) => (c.team && c.team.abbreviation) || "?";
const summary = (event) => (event.status && event.status.summary) || "";

if (words.toLowerCase() === "live") {
  const live = matches.filter((m) => state(m.event) === "in");
  if (!live.length) return show(fmt.color("muted", "No live matches"));
  if (!tables) return logs(live.map((m) => line(m.event)));
  // One row a match: who plays, the scores, and how it stands.
  const rows = live.map((m) => {
    const sides = teams(m.event);
    // Each side's score in its place, "–" for one that hasn't batted, so a score is never the wrong side's.
    const each = sides.map((c) => shortScore(c.score));
    const scores = each.some(Boolean) ? each.map((s) => s || "–").join(" · ") : "";
    return [sides.map(abbr).join(" v "), scores, summary(m.event)];
  });
  return show(`${live.length} live`, fmt.table([["Match", "Score", "Status"], ...rows], { header: true }));
}

// A team: its live match first, then the latest or next one.
const theirs = matchesOf(matches, words);
const found = theirs.find((m) => state(m.event) === "in") || theirs[0];
if (!found) return show(fmt.color("muted", `No match for ${words}`), "cricket live lists the live ones");
const e = found.event;
const when = state(e) === "pre" ? `Starts ${new Date(e.date).toLocaleString()}` : (e.status && e.status.type && e.status.type.detail) || "";
const about = [e.competitions && e.competitions[0] && e.competitions[0].description, when].filter(Boolean).join(" · ");
if (tables) {
  // The match as its title, after a tag for how it stands; then the summary, each side's score and,
  // while it's on, the batting side's overs as a bar.
  const tag = rich ? { in: fmt.tag("bad", "LIVE"), post: fmt.tag("muted", "RESULT"), pre: fmt.tag("info", "UPCOMING") }[state(e)] : "";
  const card = fmt.table([["Team", "Score"], ...teams(e).map((c) => [(c.team && c.team.displayName) || abbr(c), c.score || "—"])], { header: true });
  const o = rich && state(e) === "in" ? overs(e) : null;
  const bar = o ? fmt.bars([["Overs", o.share, o.text]], { max: 1, color: "good" }) : "";
  // From Kolahal 0.5.0, while it's on: big numbers above the card. When they show what the summary
  // says (the runs to win), the summary goes.
  const big = typeof fmt.stats === "function" && state(e) === "in" ? scoreNumbers(e) : null;
  const said = big && big.chase ? "" : summary(e);
  show([tag, e.name || line(e)].filter(Boolean).join(" "), [said, big && big.text, card, bar, about].filter(Boolean).join("\n"));
} else {
  log(line(e), [e.name, about].filter(Boolean).join(" · "));
}
if (state(e) === "in") follow(found.league, e);
