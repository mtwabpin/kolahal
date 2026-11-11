// @title Template: timer (JavaScript)
// @desc A starting point for a script on a timer, in JavaScript: checks something, remembers it, tells you when it changes
// @author Kolahal
// @version 1.0
// @every 6h
// @notify
// @tags template, javascript, github
// @requires 0.4.7
// @option repo mtwabpin/kolahal The GitHub project to watch, as owner/name
//
// The JavaScript version of template-timer. It watches a GitHub project for new releases:
// Kolahal's, until you change its repo option. Talks to: api.github.com.
//
// The @every line suggests a timer (15m, 1h, 1d…), set when the script is added; an @at line
// (`@at 07:00, 18:30`) runs it at set times instead. Change either in the script's sheet. The
// @notify line suggests letting it post notifications.
//
// An @option line is a setting changed without editing the code: in the script's sheet
// (Options), or `script set template-timer-js repo neovim/neovim`. The code reads it as
// options.repo, always as text; the value after the name is its default.

const REPO = options.repo;
if (!/^[\w.-]+\/[\w.-]+$/.test(REPO)) {
  return log(fmt.color("muted", "Set its repo option to owner/name: script set template-timer-js repo neovim/neovim"));
}

// http.get resolves to { status, ok, body, headers }. A network error throws; an HTTP error
// doesn't, so check ok.
const r = await http.get(`https://api.github.com/repos/${REPO}/releases/latest`, { Accept: "application/vnd.github+json" });
if (r.status === 404) return log(fmt.color("muted", `${REPO} has no releases yet`));
if (!r.ok) throw new Error(`GitHub answered HTTP ${r.status}`);
const release = JSON.parse(r.body);

// status() sets its status line. Pin the script (`script pin template-timer-js`) to see it at the
// top of home; tapping it there runs the script again.
status(`${REPO.split("/")[1]} ${release.tag_name}`);

// store keeps strings between runs, for this script only: here, the newest release it has seen.
const seen = store.get("seen");
if (seen === release.tag_name) return; // nothing new: no line, no notification
store.set("seen", release.tag_name);

if (seen == null) {
  // The first run only remembers what is there.
  log(REPO, `Watching: ${fmt.code(release.tag_name)} is the newest release`, { url: release.html_url });
} else {
  // fmt.color takes a role, and the theme picks the colour: good for news like this.
  log(REPO, fmt.color("good", "New: ") + fmt.code(release.tag_name), { url: release.html_url });
  // notify() posts a notification if you let this script; tapping it opens the url.
  notify(`${REPO} ${release.tag_name}`, release.name || "New release", { url: release.html_url });
}
