-- data-month.lua — your mobile data this month, against your plan. No network.
-- @title Data this month
-- @desc Mobile data since your plan's day, as a bar toward your plan: a widget
-- @author Kolahal
-- @version 1.1
-- @widget progress 2x1
-- @uses usage
-- @tags data, usage, progress
-- @requires 0.5.12
--
-- Its widget, once you placed it on the widgets page, shows the mobile data used since your plan
-- started again this month, with a bar toward your plan: the same month as the data month command.
-- Set your plan in GB with config set data.plan 75, and the day it starts again with
-- config set data.start-day 5. The bar turns red when the pace for the month goes over the plan.
-- Without a plan it shows the data alone. A tap on it logs the month.
-- It needs usage access for Kolahal (allow usage) and its own "Your app usage" switch.
-- On Android 9, which doesn't tell the mobile data, it shows the Wi-Fi data.

local started = event and event.name
local GB = 1e9
local tap = { args = "month", page = "log", label = "Show the month" }

-- The plan's month, as Kolahal counts it: nil without usage access or the switch.
local month = usage.data("month")

-- In GB with one decimal: "31.2".
local function gb(bytes) return string.format("%.1f", (bytes or 0) / GB) end

if month == nil then
  if widget then
    widget.show{ kind = "stat", title = "Data this month", icon = "wifi", value = "–",
      label = "Needs usage access", tap = tap }
  end
  if started == nil or (args or {})[1] == "month" then
    log("Data this month needs usage access", "Run allow usage, and turn on \"Your app usage\" on this script's sheet")
  end
  return
end

local sinceText = os.date("%d %b", month.since):gsub("^0", "")
local mobile, plan = month.mobile, month.plan

if widget then
  if mobile == nil then
    -- Android 9: no mobile data. The Wi-Fi data as a number: there is no plan to show a bar toward.
    widget.show{ kind = "stat", title = "Wi-Fi this month", icon = "wifi", value = gb(month.wifi), unit = "GB",
      label = "Since " .. sinceText, staleAfter = 30 * 60, tap = tap }
  elseif plan then
    widget.show{
      title = "Data this month", icon = "wifi",
      value = mobile / GB, max = plan / GB,
      text = gb(mobile), label = "Of " .. math.floor(plan / GB) .. " GB",
      -- Red when the pace for the month goes over the plan.
      color = month.pace > plan and "bad" or nil,
      staleAfter = 30 * 60,
      tap = tap,
    }
  else
    -- No plan: the data as a number, with no bar that would stand for nothing.
    widget.show{ kind = "stat", title = "Data this month", icon = "wifi", value = gb(mobile), unit = "GB",
      label = "Mobile since " .. sinceText, staleAfter = 30 * 60, tap = tap }
  end
end

-- A tap on the widget, or a run by hand: the month in the log.
if started == nil or (args or {})[1] == "month" then
  local lines = {}
  if mobile ~= nil then
    lines[#lines + 1] = "Mobile: " .. gb(mobile) .. " GB"
    if plan then
      lines[#lines + 1] = fmt.bar(mobile / GB, { max = plan / GB })
      lines[#lines + 1] = string.format("%d%% of %d GB · on pace for %s GB · %d %s left",
        math.floor(mobile / plan * 100), math.floor(plan / GB), gb(month.pace), month.daysLeft, month.daysLeft == 1 and "day" or "days")
    else
      lines[#lines + 1] = "No plan · config set data.plan 75"
    end
  end
  lines[#lines + 1] = "Wi-Fi: " .. gb(month.wifi) .. " GB"
  log("Data since " .. sinceText, table.concat(lines, "\n"))
end
