-- @title Template: widget
-- @desc A starting point for a widget: a number from the web, large, on a card on the widgets page
-- @author Kolahal
-- @version 1.0
-- @widget stat 2x1
-- @tags template, widget, github
-- @requires 0.5.9
-- @option repo mtwabpin/kolahal The GitHub project to show, as owner/name
--
-- A starting point for a widget that shows something from the web. This one shows a GitHub
-- project's stars, large, with its forks and open issues under them: Kolahal's, until you change
-- its repo option. A tap on the card opens the project. Talks to: api.github.com.
-- template-widget-js does the same in JavaScript.
--
-- To see the card: install the script, tap + on the widgets page, and pick "Template: widget" in
-- the Kolahal group.
--
-- The @widget line gives the kind of card and its size when placed. stat is one big value with up
-- to 3 smaller ones. 2x1 is half the page wide and one row high; you can resize it later. The
-- scripting guide lists every kind and its fields.
--
-- When it runs: the widgets page runs it when you place the widget, and when it shows the card
-- with content over an hour old. That is enough for most widgets. To fill the card in the
-- background too, add a timer, such as an @every 1h line.
--
-- Place the widget twice to show two projects: each widget has its own options. Long-press a card
-- to open Arrange widgets, and set its repo in its options.

-- A run that the widgets page asked for has event.name "widget". A run you typed has no event.
local forWidget = event ~= nil and event.name == "widget"

-- A failure. In a run for the widget it is an error: the card keeps its last content and says
-- "Failed 10:42" under it. Run by hand, it is a line in the log.
local function fail(why)
  if forWidget then error(why, 0) end
  return log(fmt.color("bad", "No numbers"), why)
end

local REPO = options.repo
if not REPO:match("^[%w%.%-_]+/[%w%.%-_]+$") then
  return fail("Set its repo option to owner/name: script set template-widget repo neovim/neovim")
end

-- http.get returns a table: r.ok, r.status (the HTTP code), r.body and r.headers. A network
-- error stops the run with an error line; an HTTP error doesn't, so check ok.
local r = http.get("https://api.github.com/repos/" .. REPO, { Accept = "application/vnd.github+json" })
if r.status == 404 then return fail(REPO .. " is no GitHub project") end
if not r.ok then return fail("GitHub answered HTTP " .. r.status) end
local repo = json.decode(r.body)

-- The card. Only value is needed. A typo in a field's name is an error that lists the fields.
-- - title: the card's name. Without it: the @title line.
-- - icon: a name from the scripting guide's icon list, drawn in the card's colour, or an emoji.
-- - color: a role (accent, good, warn, bad…), never a colour value: the theme picks it.
-- - tap: what a tap on the card does. { url = … } opens a link; { args = … } runs this script
--   again with those words. Without it, a tap runs the script again for the card.
-- A small card shows less: at 1x1, only the big value. Put what matters most in value.
-- widget.show returns false when the widget isn't placed. The card is checked either way.
local placed = widget.show{
  title = repo.name,
  icon = "star",
  value = repo.stargazers_count,
  label = repo.stargazers_count == 1 and "Star" or "Stars",
  more = {
    { value = repo.forks_count, label = "forks" },
    { value = repo.open_issues_count, label = "open issues" },
  },
  tap = { url = repo.html_url, label = "Open the project" },
}

-- A run for the widget logs nothing. Run by hand, it logs the numbers too.
if forWidget then return end
log(repo.full_name, fmt.stats({
  { tostring(repo.stargazers_count), "stars" },
  { tostring(repo.forks_count), "forks" },
  { tostring(repo.open_issues_count), "open issues" },
}), { url = repo.html_url })
if not placed then
  log(fmt.color("muted", "To see the card: tap + on the widgets page and pick Template: widget"))
end
