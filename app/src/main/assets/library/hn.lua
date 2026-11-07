-- hn.lua — Hacker News front page, only stories you have not seen yet.
-- @desc Hacker News front page: new stories since the last run
-- @title Hacker News
-- @every 1h
-- @notify
-- @option points 50 Only stories with at least this many points; 0: every story
-- @option lines 10 Maximum stories logged per run
--
-- Runs well on a timer (every 30-60 min). Each run logs the new front-page stories, with their
-- points and a link to their comments (tap the line to open the story), and notifies about the top
-- new one. It keeps no status line: a list of stories has nothing to pin at the top of home.

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

if count == 0 then return end

for i, item in ipairs(items) do
  if i > MAX_LINES then
    log(fmt.color("muted", "+" .. (count - MAX_LINES) .. " more"))
    break
  end
  -- hnrss puts the points, the comment count and the discussion's address in each item's summary.
  -- The points come first, in bold. The comments are a link of their own after the title; a tap
  -- anywhere else on the line opens the story.
  local about = item.summary or ""
  local points = about:match("Points:%s*(%d+)")
  local comments = about:match("# Comments:%s*(%d+)")
  local discussion = about:match("Comments URL:%s*(https://news%.ycombinator%.com/item%?id=%d+)")
  local title = item.title or "(untitled)"
  if points then title = fmt.bold(points) .. " " .. title end
  if discussion and discussion ~= item.link then
    local n = tonumber(comments)
    title = title .. fmt.color("muted", " · ") .. fmt.link(n and (n .. (n == 1 and " comment" or " comments")) or "comments", discussion)
  end
  log(title, { url = item.link })
end

notify("Hacker News", items[1].title or "New story", { url = items[1].link })
