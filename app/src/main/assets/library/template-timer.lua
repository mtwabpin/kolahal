-- @title Template: timer
-- @desc A starting point for a script on a timer: checks something, remembers it, tells you when it changes
-- @author Kolahal
-- @version 1.3
-- @every 6h
-- @notify
-- @tags template, github
-- @requires 0.4.7
-- @option repo mtwabpin/kolahal The GitHub project to watch, as owner/name
--
-- A starting point for a script that runs by itself. This one watches a GitHub project for new
-- releases: Kolahal's, until you change its repo option. Talks to: api.github.com.
--
-- The @every line suggests a timer (15m, 1h, 1d…), set when the script is added; an @at line
-- (`@at 07:00, 18:30`) runs it at set times instead. Change either in the script's sheet. The
-- @notify line suggests letting it post notifications.
--
-- An @option line is a setting changed without editing the code: in the script's sheet
-- (Options), or `script set template-timer repo neovim/neovim`. The code reads it as
-- options.repo, always as text; the value after the name is its default.

local REPO = options.repo
if not REPO:match("^[%w%.%-_]+/[%w%.%-_]+$") then
  return log(fmt.color("muted", "Set its repo option to owner/name: script set template-timer repo neovim/neovim"))
end

-- http.get returns { status, ok, body, headers }. A network error stops the run with an error
-- line; an HTTP error doesn't, so check ok.
local r = http.get("https://api.github.com/repos/" .. REPO .. "/releases/latest", { Accept = "application/vnd.github+json" })
if r.status == 404 then return log(fmt.color("muted", REPO .. " has no releases yet")) end
if not r.ok then error("GitHub answered HTTP " .. r.status) end
local release = json.decode(r.body)

-- status() pins one line in the header; tapping it runs the script again.
status(REPO:match("[^/]+$") .. " " .. release.tag_name)

-- store keeps strings between runs, for this script only: here, the newest release it has seen.
local seen = store.get("seen")
if seen == release.tag_name then return end -- nothing new: no line, no notification
store.set("seen", release.tag_name)

if seen == nil then
  -- The first run only remembers what is there.
  log(REPO, "Watching: " .. fmt.code(release.tag_name) .. " is the newest release", { url = release.html_url })
else
  -- fmt.color takes a role, and the theme picks the colour: good for news like this.
  log(REPO, fmt.color("good", "New: ") .. fmt.code(release.tag_name), { url = release.html_url })
  -- notify() posts a notification if you let this script; tapping it opens the url.
  notify(REPO .. " " .. release.tag_name, release.name or "New release", { url = release.html_url })
end
