-- lobsters.lua — Lobsters' front page, only stories you have not seen yet.
-- @desc Lobsters front page: new stories since the last run, each with a link to its comments
-- @title Lobsters
-- @every 1h
-- @notify
-- @option score 5 Only stories with at least this score; 0: every story
-- @option lines 10 Maximum stories logged per run
--
-- Talks to: lobste.rs (its front page as JSON, hottest.json).
--
-- Runs well on a timer (every 30-60 min). Each run logs the front-page stories it hasn't shown
-- before: the title (tap the line to open the story, or the discussion for a text post), its tags,
-- its score and a link to its comments. It notifies about the top new one, and keeps no status
-- line: a list of stories has nothing to pin at the top of home. A story under the score option
-- shows once it gets there.

local MIN_SCORE = math.floor(tonumber(options.score) or 5)
local MAX_LINES = math.max(1, math.floor(tonumber(options.lines) or 10))
-- How many shown stories it remembers: a few front pages' worth.
local KEEP = 200

local function failed(why) log(fmt.color("bad", "Couldn't fetch Lobsters"), why) end

local ok, r = pcall(http.get, "https://lobste.rs/hottest.json")
if not ok then return failed(tostring(r)) end
if not r.ok then return failed("lobste.rs answered HTTP " .. tostring(r.status)) end
local parsed, stories = pcall(json.decode, r.body)
if not parsed or type(stories) ~= "table" then return failed("lobste.rs sent something unexpected") end

-- The stories shown before, newest first, and as a set.
local seenList = {}
for id in (store.get("seen") or ""):gmatch("%S+") do seenList[#seenList + 1] = id end
local seen = {}
for _, id in ipairs(seenList) do seen[id] = true end

local new = {}
for _, s in ipairs(stories) do
  if type(s) == "table" and type(s.short_id) == "string" and not seen[s.short_id] and (tonumber(s.score) or 0) >= MIN_SCORE then
    new[#new + 1] = s
  end
end
if #new == 0 then return end

local function plural(n, word) return n .. " " .. word .. (n == 1 and "" or "s") end

-- A story's own address; a text post has none, so its discussion stands in.
local function linkOf(s)
  if type(s.url) == "string" and s.url ~= "" then return s.url end
  return s.comments_url or s.short_id_url
end

for i, s in ipairs(new) do
  if i > MAX_LINES then
    log(fmt.color("muted", "+" .. (#new - MAX_LINES) .. " more"))
    break
  end
  local link = linkOf(s)
  local discussion = s.comments_url or s.short_id_url
  -- The title, then up to two of its tags; after them, muted, the score and the comments. The
  -- comments are a link of their own, unless the line itself opens the discussion (a text post).
  -- A small space above each story (gap) keeps the stories apart when their titles wrap.
  local title = s.title or "(untitled)"
  local tags = {}
  for t = 1, math.min(2, #(s.tags or {})) do tags[#tags + 1] = fmt.tag("muted", tostring(s.tags[t])) end
  if #tags > 0 then title = title .. " " .. table.concat(tags, " ") end
  title = title .. fmt.color("muted", " · " .. plural(math.floor(tonumber(s.score) or 0), "point") .. " · ")
  local comments = plural(math.floor(tonumber(s.comment_count) or 0), "comment")
  if discussion and discussion ~= link then
    title = title .. fmt.link(comments, discussion)
  else
    title = title .. fmt.color("muted", comments)
  end
  log(title, { url = link, gap = true })
end

-- Remember what was shown (the ones past the line limit too), newest first.
local keep = {}
for _, s in ipairs(new) do keep[#keep + 1] = s.short_id end
for _, id in ipairs(seenList) do
  if #keep >= KEEP then break end
  keep[#keep + 1] = id
end
store.set("seen", table.concat(keep, " "))

notify("Lobsters", new[1].title or "New story", { url = linkOf(new[1]) })
