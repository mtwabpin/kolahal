-- screen-time-months.lua — your screen time a day, a bar for each of Android's months. No network.
-- @title Screen time by month
-- @desc Your screen time a day, a bar for each month Android keeps: a widget
-- @author Kolahal
-- @version 1.0
-- @widget chart 2x2
-- @uses usage
-- @tags screen time, usage, chart
-- @requires 0.5.15
--
-- Its widget, once you placed it on the widgets page, shows your screen time a day over the months
-- Android keeps (about 6), large, over a bar for each month, the oldest first: the same numbers as
-- the usage month command. Android starts each month where the one before ended, not on
-- the 1st, so the dates are its own. A tap on it logs a row for each month.
-- It needs usage access for Kolahal (allow usage) and its own "Your app usage" switch.

-- What started this run: the widgets page ("widget"), a tap ("action"), or you (nil).
local started = event and event.name

-- Minutes as people say them: "4h 12m", or "38m".
local function duration(minutes)
  minutes = math.floor(minutes or 0)
  if minutes >= 60 then return string.format("%dh %dm", minutes // 60, minutes % 60) end
  return minutes .. "m"
end

-- A day of a month's start, from seconds since 1970: "13 Jun".
local function day(seconds) return (os.date("%d %b", seconds):gsub("^0", "")) end

local tap = { args = "months", page = "log", label = "Show the months" }
local s = usage.summary("month")

-- Nil without usage access or the switch: the card says so, and a tap says what to do.
if s == nil then
  if widget then
    widget.show{ kind = "stat", title = "Screen time by month", icon = "timer", value = "–", label = "Needs usage access", tap = tap }
  end
  if started == nil or (args or {})[1] == "months" then
    log("Screen time by month needs usage access", "Run allow usage, and turn on \"Your app usage\" on this script's sheet")
  end
  return
end

local rows = s.rows

if widget then
  if #rows == 0 then
    widget.show{ kind = "stat", title = "Screen time by month", icon = "timer", value = "–", label = "No usage recorded yet", tap = tap }
  else
    local points, said = {}, {}
    for i, r in ipairs(rows) do
      points[i] = r.perDay
      said[i] = day(r.from) .. " " .. duration(r.perDay)
    end
    local last = rows[#rows]
    widget.show{
      title = "Screen time by month", icon = "timer",
      -- ≈: Android's own totals, about right, as usage month says.
      value = "≈" .. duration(s.perDay),
      label = "A day · " .. #rows .. (#rows == 1 and " month" or " months"),
      points = points,
      style = "bars",
      min = 0,
      labels = { day(rows[1].from), last.current and "Now" or day(last.from) },
      summary = table.concat(said, ", "),
      -- Months move slowly: a new look when the card shows after an hour.
      staleAfter = 60 * 60,
      tap = tap,
    }
  end
end

-- A tap on the widget, or a run by hand: a row for each month.
if started == nil or (args or {})[1] == "months" then
  if #rows == 0 then return log("Screen time by month", "No usage recorded yet") end
  local t = { { "Month", "Total", "A day" } }
  for _, r in ipairs(rows) do
    t[#t + 1] = { day(r.from) .. " – " .. (r.current and "now" or day(r.to)), duration(r.minutes), duration(r.perDay) }
  end
  log("Screen time by month", fmt.table(t, { header = true, align = "lrr" }))
end
