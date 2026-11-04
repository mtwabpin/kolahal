-- hn.lua — Hacker News front page, only stories you have not seen yet.
-- @desc Hacker News front page: new stories since the last run
-- @title Hacker News
-- @every 1h
-- @notify
-- @option points 50 Only stories with at least this many points; 0: every story
-- @option lines 10 Maximum stories logged per run
--
-- Runs well on a timer (every 30-60 min). Each run logs the new front-page
-- stories (tap a line to open it), notifies about the top new one and sets
-- a short "HN: N new" status line.

local POINTS = math.floor(tonumber(options.points) or 50)
local FEED = "https://hnrss.org/frontpage" .. (POINTS > 0 and ("?points=" .. POINTS) or "")
local MAX_LINES = math.max(1, math.floor(tonumber(options.lines) or 10))

-- rss.fresh raises an error on HTTP failure; catch it so a flaky network
-- produces one short line instead of a stack trace.
local ok, feed = pcall(rss.fresh, FEED)
if not ok then
  log(fmt.color("bad", "Couldn't fetch Hacker News"), tostring(feed))
  return
end

local items = feed.items or {}
local count = #items

if count == 0 then
  status("HN: nothing new")
  return
end

for i, item in ipairs(items) do
  if i > MAX_LINES then
    log(fmt.color("muted", "+" .. (count - MAX_LINES) .. " more"))
    break
  end
  log(item.title or "(untitled)", { url = item.link })
end

notify("Hacker News", items[1].title or "New story", { url = items[1].link })
status("HN: " .. count .. " new")
