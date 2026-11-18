-- screen-time.lua — your screen time today, over a bar for each of the last 7 days. No network.
-- @title Screen time
-- @desc Today's screen time, over a bar for each of the last 7 days: a widget
-- @author Kolahal
-- @version 1.0
-- @widget chart 2x2
-- @uses usage
-- @tags screen time, usage, chart
-- @requires 0.5.12
--
-- Its widget, once you placed it on the widgets page, shows today's screen time large, over a bar
-- for each day, the oldest first. The days are those Android has usage data for, so on a phone
-- that keeps less than a week there are fewer bars. A tap on it logs the apps you used most today.
-- Run by hand (script run screen-time), it logs them too.
-- It needs usage access for Kolahal (usage-access) and its own "Your app usage" switch.

-- What started this run: the widgets page ("widget"), a tap ("action"), or you (nil).
local started = event and event.name

-- Minutes as people say them: "4h 12m", or "38m".
local function duration(minutes)
  minutes = math.floor(minutes or 0)
  if minutes >= 60 then return string.format("%dh %dm", minutes // 60, minutes % 60) end
  return minutes .. "m"
end

local today = usage.today(8)
local days = usage.days(7)

-- The weekday of the day that was `ago` days before today ("Mon"). Noon keeps clear of the hour
-- that a clock change adds or removes.
local now = os.date("*t")
local function weekday(ago)
  return os.date("%a", os.time({ year = now.year, month = now.month, day = now.day - ago, hour = 12 }))
end

if widget then
  local said = {}
  for i, m in ipairs(days) do
    said[i] = (i == #days and "Today" or weekday(#days - i)) .. " " .. duration(m)
  end
  widget.show{
    title = "Screen time", icon = "timer",
    value = duration(today.total),
    label = "screen time today",
    points = days,
    style = "bars",
    min = 0,
    labels = { #days > 1 and weekday(#days - 1) or "Today", "Today" },
    summary = table.concat(said, ", "),
    -- Usage moves all the time: a new look when the card shows after 15 minutes.
    staleAfter = 15 * 60,
    tap = { args = "today", page = "log", label = "Show today's apps" },
  }
end

-- A tap on the widget, or a run by hand: today's apps, each with its bar on one scale.
if started == nil or (args or {})[1] == "today" then
  if #today.apps == 0 then
    log("Screen time today: " .. duration(today.total), "No app used for a minute or more yet")
    return
  end
  local rows = {}
  for i, a in ipairs(today.apps) do rows[i] = { a.app, a.minutes, duration(a.minutes) } end
  log("Screen time today: " .. duration(today.total), fmt.bars(rows))
end
