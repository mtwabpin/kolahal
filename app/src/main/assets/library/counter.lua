-- counter.lua — a count for each day, such as glasses of water, on a widget with Undo. No network.
-- @command
-- @desc A count for each day, such as glasses of water, toward a target if you set one: a widget. counter [add|undo]
-- @usage [add|undo]
-- @title Daily counter
-- @author Kolahal
-- @version 1.0
-- @widget buttons 2x1
-- @option name Water What you count
-- @option unit glasses What you count it in, such as glasses or cups; empty for none
-- @option target 8 How many a day; 0 for no target
-- @option icon drop The widget's icon: a name such as drop or coffee, or an emoji
-- @tags counter, water, tally
-- @requires 0.5.9
--
-- Usage:
--   counter                     today's count and the last 7 days
--   counter add                 add one
--   counter undo                take one back
--
-- Its widget, once you placed it on the widgets page, has two buttons. The first shows today's
-- count, "5 of 8" with a target or "5 glasses" without one, and a tap on it adds one. Undo takes
-- one back, so a tap by mistake is easy to fix. Once the count reaches the target, the widget is
-- in the theme's good colour. Each day starts again at 0.
--
-- Its options say what it counts: name (Water), unit (glasses), target (8 a day; 0 for none) and
-- icon (drop, another icon name such as coffee, or an emoji). To count two things, place the
-- widget twice and set the second one's options: each widget keeps its own count.
--
-- The counts of the last 7 days are kept in its own store. It needs no access.

-- What started this run: a tap ("action"), the widgets page ("widget"), or you (nil).
local started = event and event.name

local word = (args or {})[1]
if word ~= nil and word ~= "add" and word ~= "undo" then
  return log(fmt.color("muted", "Usage: counter [add|undo]"))
end

-- The days it keeps: today and the 6 before it.
local DAYS = 7

-- The day that was `back` days before today, as "2026-11-21". Noon keeps clear of the hour that a
-- clock change adds or removes.
local now = os.date("*t")
local function noon(back)
  return os.time({ year = now.year, month = now.month, day = now.day - back, hour = 12 })
end
local function day(back) return os.date("%Y-%m-%d", noon(back)) end

-- Each widget of this script keeps its own count: event.instance is "2", "3"… in a run for a
-- second or third widget, and nil for the first one and for a run you start.
local key = "days" .. ((event and event.instance) or "")

-- The counts, kept as one text, the oldest first: "2026-11-20=8 2026-11-21=5". A day that isn't
-- there counted 0.
local counts = {}
for d, n in (store.get(key) or ""):gmatch("(%d+%-%d+%-%d+)=(%d+)") do counts[d] = tonumber(n) end
local count = counts[day(0)] or 0

-- add counts one more. undo takes one back, down to 0: with nothing to take back it changes nothing.
if word == "add" or (word == "undo" and count > 0) then
  count = count + (word == "add" and 1 or -1)
  counts[day(0)] = count
  -- Only the last 7 days are kept, and a day with 0 isn't: an older day shows nowhere.
  local keep = {}
  for back = DAYS - 1, 0, -1 do
    local n = counts[day(back)]
    if n and n > 0 then keep[#keep + 1] = day(back) .. "=" .. n end
  end
  store.set(key, #keep > 0 and table.concat(keep, " ") or nil)
end

-- What it counts, from its options: "Water", in "glasses", 8 a day, with the drop icon. A target
-- of 0, nothing or a word is no target.
local function trim(s) return ((s or ""):gsub("^%s+", ""):gsub("%s+$", "")) end
local name = trim(options.name)
if name == "" then name = "Counter" end
local unit = trim(options.unit)
local target = math.floor(tonumber(trim(options.target)) or 0)
if target <= 0 then target = nil end
local icon = trim(options.icon)
if icon == "" then icon = nil end

-- The unit for a count of one: "glass" for "glasses", "cup" for "cups". A unit with no plural
-- ending, such as ml, stays as it is.
local function one(u)
  if u:match("sses$") or u:match("[xz]es$") or u:match("[cs]hes$") then return u:sub(1, -3) end
  if u:match("[^s]s$") then return u:sub(1, -2) end
  return u
end

-- A count with its unit: "5 glasses", "1 glass", or "5" with no unit.
local function counted(n)
  if unit == "" then return tostring(n) end
  return n .. " " .. (n == 1 and one(unit) or unit)
end

-- Today's count as the widget shows it: "5 of 8" with a target, "5 glasses" without one.
local shown = target and (count .. " of " .. target) or counted(count)
local reached = target ~= nil and count >= target

-- Its widget: two buttons. The first shows today's count, and a tap on it runs this script again
-- with "add". Undo, in the muted colour, runs it with "undo": a tap by mistake is taken back. Once
-- the count reaches the target, the widget is in the theme's good colour.
-- The widget counts as old after an hour, as any widget does, and at midnight at the latest: the
-- widgets page then runs this script again when it shows it, so the new day starts at 0.
if widget then
  local midnight = os.time({ year = now.year, month = now.month, day = now.day + 1, hour = 0 })
  widget.show{
    title = name,
    icon = icon,
    color = reached and "good" or nil,
    staleAfter = math.max(60, math.min(3600, midnight - os.time())),
    items = {
      { label = shown, icon = icon, tap = { args = "add", label = "Add " .. counted(1) } },
      { label = "Undo", color = "muted", tap = { args = "undo", label = "Undo" } },
    },
  }
end

-- A run by hand, also counter add and counter undo typed: today's count large, and the last 7
-- days, each with its bar, on the target's scale when there is one. A run for the widgets page or
-- for a tap logs nothing.
if started == nil then
  local week = {}
  for back = DAYS - 1, 0, -1 do
    week[#week + 1] = { back == 0 and "Today" or os.date("%a", noon(back)), counts[day(back)] or 0 }
  end
  local today = target and (shown .. (unit ~= "" and " " .. unit or "")) or shown
  log(name, fmt.stats({ { today, "Today" } }) .. "\n" .. fmt.bars(week, { max = target }))
end
