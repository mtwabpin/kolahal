-- hn.lua — Hacker News front page, only stories you have not seen yet.
-- @desc Hacker News front page: new stories since the last run
-- @title Hacker News
-- @every 1h
-- @notify
-- @option points 50 Only stories with at least this many points; 0: every story
-- @option lines 10 Maximum stories logged per run
--
-- Runs well on a timer (every 30-60 min). Each run logs the new front-page stories, each followed
-- by its points and a link to its comments (tap the line to open the story). It notifies about the
-- top new one, and keeps no status line: a list of stories has nothing to pin at the top of home.

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
  -- The title comes first; after it, muted, the points, then the comments as a link of their own.
  -- A tap anywhere else on the line opens the story.
  local about = item.summary or ""
  local points = tonumber(about:match("Points:%s*(%d+)"))
  local comments = tonumber(about:match("# Comments:%s*(%d+)"))
  local discussion = about:match("Comments URL:%s*(https://news%.ycombinator%.com/item%?id=%d+)")
  local title = item.title or "(untitled)"
  if points then title = title .. fmt.color("muted", " · " .. points .. (points == 1 and " point" or " points")) end
  if discussion and discussion ~= item.link then
    local said = comments and (comments .. (comments == 1 and " comment" or " comments")) or "comments"
    title = title .. fmt.color("muted", " · ") .. fmt.link(said, discussion)
  end
  log(title, { url = item.link })
end

notify("Hacker News", items[1].title or "New story", { url = items[1].link })
