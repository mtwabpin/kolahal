-- screen-time.lua — your screen time today, over a bar for each of the last 7 days. No network.
-- @title Screen time
-- @desc Today's screen time, over a bar for each of the last 7 days: a widget
-- @author Kolahal
-- @version 1.2
-- @widget chart 2x2
-- @uses usage
-- @tags screen time, usage, chart
-- @requires 0.5.12
--
-- Its widget, once you placed it on the widgets page, shows today's screen time large, over a bar
-- for each day, the oldest first. The days are those Android has usage data for, so on a phone
-- that keeps less than a week there are fewer bars. A tap on it logs the apps you used most today.
-- Run by hand (script run screen-time), it logs them too.
-- With a daily limit for screen time (config set usage.screen-time-limit 180, in minutes), the card
-- is in the warning color from the limit and in the error color from one and a half times it, and
-- its label says the time left ("1h 12m left"), or the time over it ("22m over").
-- On a 2x2 card in the Plain or Tonal look, its last line names the app you used most today
-- ("Maps 48m").
-- It needs usage access for Kolahal (allow usage) and its own "Your app usage" switch.

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

-- Nil without usage access or the switch: the card says so, and a tap says what to do.
if today == nil or days == nil then
  if widget then
    widget.show{ kind = "stat", title = "Screen time", icon = "timer", value = "–", label = "Needs usage access",
      tap = { args = "today", page = "log", label = "Show what to do" } }
  end
  if started == nil or (args or {})[1] == "today" then
    log("Screen time needs usage access", "Run allow usage, and turn on \"Your app usage\" on this script's sheet")
  end
  return
end

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
  -- Against your daily limit: the time left, or the time over it. Without one, what the value is.
  local label, against = "Screen time today", nil
  if today.limit then
    local limit = duration(today.limit)
    local left = math.floor(today.limit) - math.floor(today.total)
    if left > 0 then
      label, against = duration(left) .. " left", duration(left) .. " left of your " .. limit .. " limit"
    elseif left == 0 then
      label, against = "At your limit", "At your " .. limit .. " limit"
    else
      label, against = duration(-left) .. " over", duration(-left) .. " over your " .. limit .. " limit"
    end
  end
  -- A screen reader hears the limit and the top app first, then each day.
  local heard = { against }
  -- The app you used most today, with its time ("Maps 48m"): a large card's last line holds 16
  -- characters, so a long name is cut and the time stays. Nil until an app has a minute.
  local top, note = today.apps[1], nil
  if top then
    local time = duration(top.minutes)
    heard[#heard + 1] = "Most used: " .. top.app .. " " .. time
    local room = 16 - #time - 1
    local name = top.app
    if utf8.len(name) and utf8.len(name) > room then
      name = name:sub(1, utf8.offset(name, room) - 1):gsub("%s+$", "") .. "…"
    end
    note = name .. " " .. time
  end
  heard[#heard + 1] = table.concat(said, ", ")
  widget.show{
    title = "Screen time", icon = "timer",
    value = duration(today.total),
    label = label,
    note = note,
    points = days,
    style = "bars",
    min = 0,
    labels = { #days > 1 and weekday(#days - 1) or "Today", "Today" },
    summary = table.concat(heard, ". "),
    -- "warn" or "bad" against your daily limit, as Kolahal colors today's screen time; nil under it.
    color = today.color,
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
