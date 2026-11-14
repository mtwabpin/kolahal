-- unlock-chart.lua — how often you unlocked the phone: today's count, over a bar for each of the last 7 days. No network.
-- @title Unlocks
-- @desc Today's unlocks, over a bar for each of the last 7 days: a widget
-- @author Kolahal
-- @version 1.0
-- @widget chart 2x2
-- @on unlock
-- @tags unlocks, screen time, chart
-- @requires 0.5.8
--
-- Its widget, once you placed it on the widgets page, shows today's count large, over seven bars:
-- one for each day, the oldest first. It runs when you unlock the phone, so the count is right
-- when you arrive. A tap on it logs the seven days and turns home to the log.
-- Run by hand (script run unlock-chart), it logs the seven days too.
--
-- The counts are the ones the unlocks command shows. It needs no access.

-- What started this run: an unlock, the widgets page ("widget"), a tap ("action"), or you (nil).
local started = event and event.name
-- Every unlock starts a run. With no widget placed, there is nothing to fill.
if started == "unlock" and not (widget and allowed("widget")) then return end

-- The last 7 days, the oldest first: the last number is today's.
local days = device.unlocks(7)
-- An older Kolahal knows only today's count, and gives it as a number.
if type(days) ~= "table" then days = { days } end
local today = math.floor(days[#days] or 0)

-- The weekday of the day that was `ago` days before today ("Mon"). Noon keeps clear of the hour
-- that a clock change adds or removes.
local now = os.date("*t")
local function weekday(ago)
  return os.date("%a", os.time({ year = now.year, month = now.month, day = now.day - ago, hour = 12 }))
end

-- A row for each day, the day's name and its count, and the busiest day's count: the top of the bars.
local rows, most = {}, 1
for i, n in ipairs(days) do
  rows[i] = { i == #days and "Today" or weekday(#days - i), math.floor(n) }
  if n > most then most = math.floor(n) end
end

-- Its widget: today's count large, over the bars. The bars start at 0, so a quiet day is a short
-- bar, not a missing one. The summary is what a screen reader says for the bars.
-- A tap runs this script again with "week" and turns home to the log. The heading is the same
-- words as its @title line.
if widget then
  local said = {}
  for i, row in ipairs(rows) do said[i] = row[1] .. " " .. row[2] end
  widget.show{
    title = "Unlocks",
    value = today,
    label = today == 1 and "unlock today" or "unlocks today",
    points = days,
    style = "bars",
    min = 0,
    max = most,
    labels = { rows[1][1], "Today" },
    summary = table.concat(said, ", "),
    tap = { args = "week", page = "log", label = "Show the week" },
  }
end

-- A tap on the widget, or a run by hand: the days in the log, each with its bar on one scale.
-- A run for an unlock or for the widgets page logs nothing.
if started == nil or (args or {})[1] == "week" then
  log("Unlocks in the last " .. #days .. " days", fmt.bars(rows))
end
