-- releases.lua — watch GitHub repositories for new releases (Atom feeds, no API key).
-- @command
-- @desc GitHub release watcher: releases [add|rm owner/repo | list]
-- @usage [add <owner/repo>|rm <owner/repo>|list]
-- @title GitHub releases
-- @widget list 4x2
-- @every 3h
-- @notify
--
-- Usage:
--   releases                    check every watched repo now
--   releases add neovim/neovim  start watching a repo
--   releases rm neovim/neovim   stop watching a repo
--   releases list               show watched repos
--
-- On a timer it checks every repo and notifies about each new release.
-- The first check of a repo only records what exists, so you are not
-- flooded with old releases.
-- Its widget, once you placed it on the widgets page, lists the newest release of each repo.

local DEFAULT_REPOS = { "mtwabpin/kolahal", "neovim/neovim", "termux/termux-app" }

-- The widgets page asked for this run: it fills the widget and logs nothing.
local quiet = event and event.name == "widget"

-- ---------------------------------------------------------------- helpers

local function loadRepos()
  local raw = store.get("repos")
  if raw == nil then return DEFAULT_REPOS end
  local list = {}
  for repo in raw:gmatch("%S+") do list[#list + 1] = repo end
  return list
end

local function saveRepos(list)
  store.set("repos", table.concat(list, "\n"))
end

local function hint(text) log(fmt.color("muted", text)) end

local function indexOf(list, value)
  for i, v in ipairs(list) do
    if v:lower() == value:lower() then return i end
  end
  return nil
end

-- How long ago a feed's date was ("2026-11-01T12:34:56Z"): "today", "3 days ago", "2 months ago".
-- Nil when it isn't such a date. It counts whole days, and noon keeps clear of a clock change.
local function age(date)
  local y, m, d = (date or ""):match("^(%d%d%d%d)-(%d%d)-(%d%d)")
  if not y then return nil end
  local now = os.date("*t")
  local today = os.time({ year = now.year, month = now.month, day = now.day, hour = 12 })
  local that = os.time({ year = tonumber(y), month = tonumber(m), day = tonumber(d), hour = 12 })
  local days = math.floor(os.difftime(today, that) / 86400 + 0.5)
  if days <= 0 then return "today" end
  if days == 1 then return "yesterday" end
  if days < 60 then return days .. " days ago" end
  if days < 730 then return math.floor(days / 30) .. " months ago" end
  return math.floor(days / 365) .. " years ago"
end

-- ---------------------------------------------------------------- commands

local cmd, arg = (argline or ""):match("^%s*(%S*)%s*(.-)%s*$")
local repos = loadRepos()

if cmd == "list" then
  if #repos == 0 then return hint("No repos watched: releases add owner/repo") end
  -- A line per repo, each alike; tapping one opens its releases.
  for _, repo in ipairs(repos) do
    log(repo, nil, { url = "https://github.com/" .. repo .. "/releases" })
  end
  return
end

if cmd == "add" or cmd == "rm" then
  if not arg:match("^[%w%.%-_]+/[%w%.%-_]+$") then
    return hint("Usage: releases " .. cmd .. " owner/repo")
  end
  local i = indexOf(repos, arg)
  if cmd == "add" then
    if i then return hint(arg .. " is watched already") end
    repos[#repos + 1] = arg
    saveRepos(repos)
    return log("Watching " .. arg, #repos .. " repos", { url = "https://github.com/" .. arg .. "/releases" })
  else
    if not i then return hint(arg .. " isn't watched") end
    table.remove(repos, i)
    saveRepos(repos)
    return log("Stopped watching " .. arg, #repos .. " repos left")
  end
end

if cmd ~= "" then
  return hint("Usage: releases [add|rm owner/repo | list]")
end

-- ---------------------------------------------------------------- widget

-- Its widget: a row for each watched repo, with its newest release and how old that is. A tap on
-- a row opens the release. Only a placed widget is filled, because this reads each feed once more.
-- It reads with rss.fetch: the releases it shows must not count as seen for the check below.
if widget and (quiet or allowed("widget")) then
  local rows, failed, why = {}, 0, nil
  for i, repo in ipairs(repos) do
    if i > 12 then break end -- a list keeps 12 rows
    local read, feed = pcall(rss.fetch, "https://github.com/" .. repo .. "/releases.atom")
    local newest = read and (feed.items or {})[1]
    if newest then
      local link = (newest.link or ""):match("^https?://%S+$")
      rows[i] = {
        title = repo,
        -- The tag is the end of the release's link (…/releases/tag/v1.2.0). It is shorter than the title.
        trailing = link and link:match("/releases/tag/([^/?#]+)$") or newest.title,
        detail = age(newest.date),
        tap = link and { url = link } or nil,
      }
    elseif read then
      rows[i] = { title = repo, detail = "no releases yet" }
    else
      -- One repo that can't be read (renamed, removed) doesn't hide the others.
      rows[i] = { title = repo, detail = "couldn't read its releases", color = "bad" }
      failed, why = failed + 1, why or tostring(feed)
    end
  end
  if #rows > 0 and failed == #rows then
    -- Nothing could be read, so the card keeps what it has. In a run for the widget this is an
    -- error, so the run counts as failed.
    if quiet then error("Couldn't read the releases: " .. why, 0) end
  else
    -- It runs every 3 hours: content counts as old only after 6, so the card shows no age between runs.
    widget.show{ items = rows, empty = "No repos: releases add owner/repo", staleAfter = 6 * 3600 }
  end
end
-- A run for the widget stops here. It must not check for new releases: they would count as seen
-- without ever being logged.
if quiet then return end

-- ---------------------------------------------------------------- check

if #repos == 0 then
  return hint("No repos watched: releases add owner/repo")
end

local total = 0
-- Repos checked for the first time, noted once the run has got through them all: rss.fresh keeps
-- what it has seen only when the run ends well, so one cut short checks them for the first time again.
local firstChecked = {}
for _, repo in ipairs(repos) do
  local url = "https://github.com/" .. repo .. "/releases.atom"
  local ok, feed = pcall(rss.fresh, url)
  if not ok then
    log(repo, fmt.color("bad", "Couldn't fetch its releases") .. ": " .. tostring(feed))
  else
    local items = feed.items or {}
    local firstKey = "init:" .. repo
    if store.get(firstKey) == nil then
      -- First check: show the latest release, but don't notify.
      firstChecked[#firstChecked + 1] = firstKey
      if #items > 0 then
        log(repo .. " " .. fmt.tag("muted", "latest") .. " " .. (items[1].title or "?"), { url = items[1].link, gap = true })
      end
    else
      for i, item in ipairs(items) do
        if i > 3 then break end -- a repo rarely has more than a few releases between runs
        local title = item.title or "New release"
        -- A small space above each release (gap) keeps them apart when a title wraps.
        log(repo .. " " .. fmt.tag("good", "new") .. " " .. title, { url = item.link, gap = true })
        notify(repo, title, { url = item.link })
        total = total + 1
      end
    end
  end
end

for _, key in ipairs(firstChecked) do store.set(key, "1") end

if total > 0 then
  status("Releases: " .. total .. " new")
else
  status(nil)
end
