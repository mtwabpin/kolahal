-- releases.lua — watch GitHub repositories for new releases (Atom feeds, no API key).
-- @command
-- @desc GitHub release watcher: releases [add|rm owner/repo | list]
-- @usage [add <owner/repo>|rm <owner/repo>|list]
-- @title GitHub releases
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

local DEFAULT_REPOS = { "mtwabpin/kolahal", "neovim/neovim", "termux/termux-app" }

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
        log(repo .. "  " .. fmt.color("muted", "latest") .. " " .. (items[1].title or "?"), { url = items[1].link })
      end
    else
      for i, item in ipairs(items) do
        if i > 3 then break end -- a repo rarely has more than a few releases between runs
        local title = item.title or "New release"
        log(repo .. "  " .. fmt.color("good", "new") .. " " .. title, { url = item.link })
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
