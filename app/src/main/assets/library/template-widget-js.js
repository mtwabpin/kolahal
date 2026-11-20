// @title Template: widget (JavaScript)
// @desc A starting point for a widget, in JavaScript: a number from the web, large, on a card on the widgets page
// @author Kolahal
// @version 1.0
// @widget stat 2x1
// @tags template, widget, javascript, github
// @requires 0.5.9
// @option repo mtwabpin/kolahal The GitHub project to show, as owner/name
//
// The JavaScript version of template-widget. It shows a GitHub project's stars, large, with its
// forks and open issues under them: Kolahal's, until you change its repo option. A tap on the
// card opens the project. Talks to: api.github.com.
//
// To see the card: install the script, tap + on the widgets page, and pick "Template: widget
// (JavaScript)" in the Kolahal group.
//
// The @widget line gives the kind of card and its size when placed. stat is one big value with up
// to 3 smaller ones. 2x1 is half the page wide and one row high; you can resize it later. The
// scripting guide lists every kind and its fields.
//
// When it runs: the widgets page runs it when you place the widget, and when it shows the card
// with content over an hour old. That is enough for most widgets. To fill the card in the
// background too, add a timer, such as an @every 1h line.
//
// Place the widget twice to show two projects: each widget has its own options. Long-press a card
// to open Arrange widgets, and set its repo in its options.

// A run that the widgets page asked for has event.name "widget". A run you typed has no event.
const forWidget = !!event && event.name === "widget";

// A failure. In a run for the widget it is an error: the card keeps its last content and says
// "Failed 10:42" under it. Run by hand, it is a line in the log.
const fail = (why) => {
  if (forWidget) throw new Error(why);
  log(fmt.color("bad", "No numbers"), why);
};

const REPO = options.repo;
if (!/^[\w.-]+\/[\w.-]+$/.test(REPO)) {
  return fail("Set its repo option to owner/name: script set template-widget-js repo neovim/neovim");
}

// http.get resolves to an object: r.ok, r.status (the HTTP code), r.body and r.headers. A
// network error throws; an HTTP error doesn't, so check ok.
const r = await http.get(`https://api.github.com/repos/${REPO}`, { Accept: "application/vnd.github+json" });
if (r.status === 404) return fail(`${REPO} is no GitHub project`);
if (!r.ok) return fail(`GitHub answered HTTP ${r.status}`);
const repo = JSON.parse(r.body);

// The card. Only value is needed. A typo in a field's name is an error that lists the fields.
// - title: the card's heading. Without it: the @title line.
// - icon: a name from the scripting guide's icon list, drawn in the card's colour, or an emoji.
// - color: a role (accent, good, warn, bad…), never a colour value: the theme picks it.
// - tap: what a tap on the card does. { url: … } opens a link; { args: … } runs this script
//   again with those words. Without it, a tap runs the script again for the card.
// A small card shows less: at 1x1, only the big value. Put what matters most in value.
// widget.show returns false when the widget isn't placed. The card is checked either way.
const placed = widget.show({
  title: repo.name,
  icon: "star",
  value: repo.stargazers_count,
  label: repo.stargazers_count === 1 ? "Star" : "Stars",
  more: [
    { value: repo.forks_count, label: "forks" },
    { value: repo.open_issues_count, label: "open issues" },
  ],
  tap: { url: repo.html_url, label: "Open the project" },
});

// A run for the widget logs nothing. Run by hand, it logs the numbers too.
if (forWidget) return;
log(repo.full_name, fmt.stats([
  [String(repo.stargazers_count), "stars"],
  [String(repo.forks_count), "forks"],
  [String(repo.open_issues_count), "open issues"],
]), { url: repo.html_url });
if (!placed) log(fmt.color("muted", "To see the card: tap + on the widgets page and pick Template: widget (JavaScript)"));
