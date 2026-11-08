// @title Live football
// @desc Live football scores from one league: the team option's live match as its status line, to pin at the top of home; football live lists them all
// @author Kolahal
// @version 1.5
// @command
// @usage [live|<team>]
// @every 15m
// @tags sport, football, soccer, live
// @requires 0.4.7
// @option league eng.1 The league: eng.1 (Premier League), esp.1, ger.1, ita.1, fra.1, uefa.champions, ind.1, usa.1, or another of ESPN's codes
// @option team any The team whose live match it follows, such as Arsenal; any: the league's first live match
// @option refresh 30 Seconds between updates while home shows the match (10 at the fastest)
//
// Talks to: site.api.espn.com, ESPN's scores. It isn't a documented API, so it can change without
// notice.
//
// On its timer it looks in the league option for a live match of the team option and keeps its
// score as its status line, the minutes played as the bar. Pin it (`script pin football`) and the
// score shows at the top of home, fetched again every refresh seconds while home shows it (from
// Kolahal 0.4.14; older ones show the line in the header, updated on the timer). Once the match
// ends, its result stays two hours. `football Arsenal` shows Arsenal's match in the league and
// follows it if it's on; `football live` lists the league's live matches.
//
// From Kolahal 0.4.16, `football live` shows as a table (fmt.table), a match with a tag for how it
// stands (LIVE, FT, UPCOMING) and the minutes played as a bar; a typed command shows "Fetching
// scores…" at once, which turns into the answer. An older Kolahal shows lines, as before.

const league = (options.league || "eng.1").trim();
const BOARD = `https://site.api.espn.com/apis/site/v2/sports/soccer/${encodeURIComponent(league)}/scoreboard`;
const RESULT_HOURS = 2;

async function matches() {
  const r = await http.get(BOARD);
  if (!r.ok) throw new Error(r.status === 404 || r.status === 400 ? `no league ${league}` : `HTTP ${r.status}`);
  return JSON.parse(r.body).events || [];
}

const sides = (event) => (event.competitions && event.competitions[0] && event.competitions[0].competitors) || [];
const state = (event) => (event.status && event.status.type && event.status.type.state) || "";

// How well a team named [name] plays in the match, by its name, short name or abbreviation:
// 2 for the exact name, 1 for a longer name that starts with it ("Manchester" for "Manchester
// City"), 0 when no team matches.
function plays(event, name) {
  const want = name.toLowerCase();
  let best = 0;
  for (const c of sides(event)) {
    const t = c.team || {};
    for (const n of [t.displayName, t.shortDisplayName, t.name, t.abbreviation].filter(Boolean)) {
      if (n.toLowerCase() === want) return 2;
      if (n.toLowerCase().startsWith(want + " ")) best = 1;
    }
  }
  return best;
}

// The matches in [list] of a team named [name]: those with the exact name if there are any, else
// those with a longer name. So "Arsenal" finds Arsenal, not Arsenal Women.
function matchesOf(list, name) {
  const scored = list.map((e) => [e, plays(e, name)]);
  const best = Math.max(0, ...scored.map(([, s]) => s));
  return best ? scored.filter(([, s]) => s === best).map(([e]) => e) : [];
}

// "ARS 2–1 CHE · 67'", home first; "HT" or "FT" when it's that.
function line(event) {
  const [home, away] = ["home", "away"].map((h) => sides(event).find((c) => c.homeAway === h) || {});
  const abbr = (c) => (c.team && c.team.abbreviation) || "?";
  const type = (event.status && event.status.type) || {};
  const clock = state(event) === "in" && type.shortDetail !== "HT" ? event.status.displayClock : type.shortDetail;
  return `${abbr(home)} ${home.score ?? ""}–${away.score ?? ""} ${abbr(away)}` + (clock ? ` · ${clock}` : "");
}

// Minutes played out of 90, 0 to 1; extra time stays full.
function progress(event) {
  const m = ((event.status && event.status.displayClock) || "").match(/^(\d+)/);
  return m ? Math.min(1, Number(m[1]) / 90) : null;
}

function showMatch(event) {
  if (state(event) === "in") {
    const p = progress(event);
    const every = Math.max(10, Number(options.refresh) || 30);
    status(line(event), p == null ? { refresh: every } : { progress: p, refresh: every });
  } else {
    status(line(event));
  }
}

// The followed match ({ league, id, ended }).
const kept = () => {
  try {
    const m = JSON.parse(store.get("match") || "null");
    return m && m.league === league ? m : null;
  } catch (e) {
    return null;
  }
};

// Fetches the followed match again: its line while it's on, its result for a while after.
async function update(match, all) {
  const event = (all || (await matches())).find((e) => e.id === match.id);
  if (!event) {
    store.set("match", null);
    return status(null);
  }
  if (state(event) === "post") {
    if (!match.ended) store.set("match", JSON.stringify({ ...match, ended: Date.now() }));
    else if (Date.now() - match.ended > RESULT_HOURS * 3600000) {
      store.set("match", null);
      return status(null);
    }
  }
  showMatch(event);
}

function follow(event) {
  store.set("match", JSON.stringify({ league, id: event.id }));
  showMatch(event);
}

const words = (argline || "").trim();
const match = kept();

if (event && event.name === "refresh") {
  if (match) await update(match);
  else status(null);
  return;
}

if (!words) {
  // The timer: the followed match while it's on, then the team option's next live one. A failed
  // fetch leaves the pinned score as it was until the next run, without a line every 15 minutes.
  try {
    const all = await matches();
    if (match) await update(match, all);
    if (match && !match.ended && all.some((e) => e.id === match.id && state(e) !== "post")) return;
    const team = (options.team || "any").trim();
    const live = all.filter((e) => state(e) === "in");
    const found = team.toLowerCase() === "any" ? live[0] : matchesOf(live, team)[0];
    if (found && (!match || found.id !== match.id)) follow(found);
  } catch (e) {
    // Tried again at the next run.
  }
  return;
}

// From Kolahal 0.4.16: a tag for how the match stands, a bar for the minutes played, and "Fetching
// scores…" at once, which turns into the answer in place (log returns the line). An older Kolahal
// has no fmt.bar, and gets lines as before.
const rich = typeof fmt.bar === "function";
const pending = rich ? log(fmt.color("muted", "Fetching scores…")) : null;
const show = (...a) => (pending ? pending.set(...a) : log(...a));

let all;
try {
  all = await matches();
} catch (e) {
  return show(fmt.color("bad", "No football scores"), `${e.message || e}`);
}

if (words.toLowerCase() === "live") {
  const live = all.filter((e) => state(e) === "in");
  if (!live.length) return show(fmt.color("muted", `No live matches in ${league}`), "script set football league <code> picks another league");
  if (typeof fmt.table !== "function") return logs(live.map(line));
  // One row a match: home, the score, away and the clock. Tables need Kolahal 0.4.16; an older one
  // gets the lines above.
  const rows = live.map((e) => {
    const [home, away] = ["home", "away"].map((h) => sides(e).find((c) => c.homeAway === h) || {});
    const abbr = (c) => (c.team && c.team.abbreviation) || "?";
    const type = (e.status && e.status.type) || {};
    const clock = type.shortDetail !== "HT" ? (e.status && e.status.displayClock) || "" : "HT";
    return [abbr(home), `${home.score ?? ""}–${away.score ?? ""}`, abbr(away), clock];
  });
  return show(`${live.length} live in ${league}`, fmt.table(rows, { align: "rllr" }));
}

// A team: its live match first, then the latest or next one. A tag says how it stands, and while it's
// on, a bar shows the minutes played.
const theirs = matchesOf(all, words);
const found = theirs.find((e) => state(e) === "in") || theirs[0];
if (!found) return show(fmt.color("muted", `No match for ${words} in ${league}`), "football live lists the live ones");
const when = state(found) === "pre" ? `Starts ${new Date(found.date).toLocaleString()}` : "";
const tag = rich ? { in: fmt.tag("bad", "LIVE"), post: fmt.tag("muted", "FT"), pre: fmt.tag("info", "UPCOMING") }[state(found)] : "";
const p = rich && state(found) === "in" ? progress(found) : null;
const bar = p == null ? "" : fmt.bars([["Minute", p, (found.status && found.status.displayClock) || ""]], { max: 1, color: "good" });
// From Kolahal 0.5.0, once it has started: the match by its teams' names as the title, then big
// numbers: the score large, with the teams under it, and the minute (HT, or FT at the end) beside it.
const [home, away] = ["home", "away"].map((h) => sides(found).find((c) => c.homeAway === h) || {});
const shortOf = (c) => (c.team && c.team.abbreviation) || "?";
const nameOf = (c) => (c.team && c.team.displayName) || shortOf(c);
if (typeof fmt.stats === "function" && (state(found) === "in" || state(found) === "post")) {
  const type = (found.status && found.status.type) || {};
  const clock = state(found) === "post" ? ["FT", "full time"]
    : type.shortDetail === "HT" ? ["HT", "half time"]
    : [(found.status && found.status.displayClock) || "", "minute"];
  const numbers = fmt.stats([[`${home.score ?? ""}–${away.score ?? ""}`, `${shortOf(home)} v ${shortOf(away)}`], clock]);
  show([tag, `${nameOf(home)} v ${nameOf(away)}`].filter(Boolean).join(" "), [numbers, bar].filter(Boolean).join("\n"));
} else {
  show([tag, line(found)].filter(Boolean).join(" "), [[found.name, when].filter(Boolean).join(" · "), bar].filter(Boolean).join("\n"));
}
if (state(found) === "in") follow(found);
