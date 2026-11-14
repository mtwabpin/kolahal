-- habit.lua — one thing you do every day: your streak, over a bar for each of the last 7 days. No network.
-- @command
-- @desc A daily habit's streak, over a bar for each of the last 7 days: a widget. habit [done|undo]
-- @usage [done|undo]
-- @title Habit streak
-- @author Kolahal
-- @version 1.0
-- @widget chart 2x2
-- @at 00:01
-- @option name Exercise What you count
-- @tags habit, streak, chart
-- @requires 0.5.8
--
-- Usage:
--   habit                       the streak and the last 7 days
--   habit done                  mark today done
--   habit undo                  take today's mark away
--
-- Its widget, once you placed it on the widgets page, shows the streak large: the days in a row
-- that you did it. Under it are seven bars, one for each day, the oldest first. A tap on the
-- widget marks today done. A tap by mistake: type habit undo.
-- It runs a minute after midnight, so the widget starts the new day with an open bar.
--
-- The days are kept in its own store. It needs no access.

-- What started this run: its daily time ("at"), the widgets page ("widget"), a tap ("action"), or you (nil).
local started = event and event.name
-- Its daily time only starts the widget's new day. With no widget placed, there is nothing to do.
if started == "at" and not (widget and allowed("widget")) then return end

local word = (args or {})[1]
if word ~= nil and word ~= "done" and word ~= "undo" then
  return log(fmt.color("muted", "Usage: habit [done|undo]"))
end

-- The day that was `back` days before today, as "2026-11-14". Noon keeps clear of the hour that a
-- clock change adds or removes.
local now = os.date("*t")
local function noon(back)
  return os.time({ year = now.year, month = now.month, day = now.day - back, hour = 12 })
end
local function day(back) return os.date("%Y-%m-%d", noon(back)) end

-- The days you did it, kept as one text: "2026-11-12 2026-11-13".
local done = {}
for d in (store.get("days") or ""):gmatch("%S+") do done[d] = true end

local changed = false
if word == "done" and not done[day(0)] then
  done[day(0)], changed = true, true
elseif word == "undo" and done[day(0)] then
  done[day(0)], changed = nil, true
end

-- The streak: the days in a row, up to today. While today is still open, it counts up to yesterday.
local first = done[day(0)] and 0 or 1
local streak = 0
while done[day(first + streak)] do streak = streak + 1 end

-- Only the streak's days and the last 7 days are kept: an older day changes nothing shown here.
if changed then
  local keep = {}
  for back = math.max(first + streak, 7) - 1, 0, -1 do
    if done[day(back)] then keep[#keep + 1] = day(back) end
  end
  store.set("days", table.concat(keep, " "))
end

-- The last 7 days, the oldest first: the day's name, and whether you did it.
local week = {}
for back = 6, 0, -1 do
  week[#week + 1] = { back == 0 and "Today" or os.date("%a", noon(back)), done[day(back)] or false }
end

local name = (options.name or ""):gsub("^%s+", ""):gsub("%s+$", "")
if name == "" then name = "Habit" end
local label = streak == 1 and "day in a row" or "days in a row"

-- Its widget: the streak large, over the bars. A day you did it is a full bar, and any other day
-- is an empty one. Once today is done, the widget is in the theme's good colour. The summary is
-- what a screen reader says for the bars. A tap runs this script again with "done".
if widget then
  local points, said = {}, {}
  for i, d in ipairs(week) do
    points[i] = d[2] and 1 or 0
    said[i] = d[1] .. (d[2] and " done" or (i == #week and " not yet" or " missed"))
  end
  widget.show{
    title = name,
    value = streak,
    label = label,
    points = points,
    style = "bars",
    min = 0,
    max = 1,
    labels = { week[1][1], "Today" },
    summary = table.concat(said, ", "),
    color = done[day(0)] and "good" or nil,
    tap = { args = "done", label = "Done today" },
  }
end

-- A run by hand: the streak and the week in the log. A run for the widgets page, for a tap or at
-- its daily time logs nothing.
if started == nil then
  local marks = {}
  for i, d in ipairs(week) do marks[i] = d[1] .. " " .. (d[2] and "✓" or "·") end
  log(name, fmt.stats({ { tostring(streak), label } }) .. "\n" .. table.concat(marks, "  "))
end
