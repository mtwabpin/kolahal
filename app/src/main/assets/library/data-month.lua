-- data-month.lua — your mobile data this month, against your plan. No network.
-- @title Data this month
-- @desc Mobile data since your plan's day, as a bar toward your plan: a widget
-- @author Kolahal
-- @version 1.0
-- @widget progress 2x1
-- @uses usage
-- @option plan 0 Your monthly plan in GB (0: no plan)
-- @option start 1 The day of the month your plan starts again (1 to 28)
-- @tags data, usage, progress
-- @requires 0.5.12
--
-- Its widget, once you placed it on the widgets page, shows the mobile data used since your plan
-- started again this month, with a bar toward your plan. Set the plan in GB, and the day it starts,
-- on the script's sheet (script set data-month plan 75). Without a plan it shows the data alone.
-- A tap on it logs the month: mobile and Wi-Fi, the pace for the month and the days left.
-- It needs usage access for Kolahal (usage-access) and its own "Your app usage" switch.
-- On Android 9, which doesn't tell the mobile data, it shows the Wi-Fi data.

local started = event and event.name
local GB = 1e9

local plan = tonumber(options.plan) or 0
local startDay = math.max(1, math.min(28, math.floor(tonumber(options.start) or 1)))

-- When the plan started again: this month's start day, or last month's if that is still to come.
local now = os.date("*t")
local month = now.day >= startDay and now.month or now.month - 1
local since = os.time({ year = now.year, month = month, day = startDay, hour = 0 })
local nextStart = os.time({ year = now.year, month = month + 1, day = startDay, hour = 0 })
local daysIn = math.max(1, math.floor((nextStart - since) / 86400 + 0.5))
local gone = math.max(1, math.floor((os.time() - since) / 86400) + 1)
local left = math.max(0, daysIn - gone + 1)

local used = usage.data(since)
local mobile = used.mobile
-- In GB with one decimal: "31.2".
local function gb(bytes) return string.format("%.1f", (bytes or 0) / GB) end
local sinceText = os.date("%d %b", since):gsub("^0", "")

if widget then
  local tap = { args = "month", page = "log", label = "Show the month" }
  if mobile == nil then
    -- Android 9: no mobile data. The Wi-Fi data as a number: there is no plan to show a bar toward.
    widget.show{ kind = "stat", title = "Wi-Fi this month", icon = "wifi", value = gb(used.wifi), unit = "GB",
      label = "since " .. sinceText, staleAfter = 30 * 60, tap = tap }
  elseif plan > 0 then
    widget.show{
      title = "Data this month", icon = "wifi",
      value = mobile / GB, max = plan,
      text = gb(mobile), label = "of " .. math.floor(plan) .. " GB",
      -- Red when the pace for the month goes over the plan.
      color = (mobile / gone * daysIn > plan * GB) and "bad" or nil,
      staleAfter = 30 * 60,
      tap = tap,
    }
  else
    -- No plan: the data as a number, with no bar that would stand for nothing.
    widget.show{ kind = "stat", title = "Data this month", icon = "wifi", value = gb(mobile), unit = "GB",
      label = "mobile since " .. sinceText, staleAfter = 30 * 60, tap = tap }
  end
end

-- A tap on the widget, or a run by hand: the month in the log.
if started == nil or (args or {})[1] == "month" then
  local lines = {}
  if mobile ~= nil then
    lines[#lines + 1] = "Mobile: " .. gb(mobile) .. " GB"
    if plan > 0 then
      lines[#lines + 1] = fmt.bar(mobile / GB, { max = plan })
      lines[#lines + 1] = string.format("%d%% of %d GB · on pace for %s GB · %d %s left",
        math.floor(mobile / GB / plan * 100), math.floor(plan), gb(mobile / gone * daysIn), left, left == 1 and "day" or "days")
    end
  end
  lines[#lines + 1] = "Wi-Fi: " .. gb(used.wifi) .. " GB"
  log("Data since " .. sinceText, table.concat(lines, "\n"))
end
