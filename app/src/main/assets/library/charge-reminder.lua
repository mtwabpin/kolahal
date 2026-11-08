-- charge-reminder.lua — reacts to the charger instead of a timer. No network.
-- @on charger, unplug
-- @desc Charging session summary; warns when unplugged below 30% (its low option)
-- @title Charge reminder
-- @notify
-- @option low 30 Warn when unplugged below this battery %
--
-- `event` is the trigger payload: event.name ("charger" / "unplug"), event.level (battery %),
-- event.plug ("ac", "usb", "wireless"). Run by hand (event is nil) it shows the last session.

local LOW = tonumber(options.low) or 30

local function pct(n) return n and (math.floor(n) .. "%") or "?%" end

local function minutes(since)
  local m = math.floor(os.difftime(os.time(), since) / 60 + 0.5)
  if m < 60 then return m .. "m" end
  return string.format("%dh %02dm", m // 60, m % 60)
end

if event == nil then
  local last = store.get("last")
  if last then log("Last charge", last) else log(fmt.color("muted", "No charging session yet: plug in the charger")) end
  return
end

local level = event.level or device.battery().level

if event.name == "charger" then
  store.set("since", tostring(os.time()))
  store.set("from", level and tostring(level) or nil)
  status("Charging since " .. os.date("%H:%M") .. " (" .. pct(level) .. ")")
  return
end

if event.name == "unplug" then
  status(nil)
  local since, from = tonumber(store.get("since")), tonumber(store.get("from"))
  store.set("since", nil)
  local line = "Unplugged at " .. pct(level)
  local detail = nil
  if since and level and from then
    detail = string.format("%+d%% in %s", math.floor(level - from), minutes(since))
  elseif since then
    detail = "after " .. minutes(since)
  end
  store.set("last", os.date("%H:%M") .. " · " .. line .. (detail and (" (" .. detail .. ")") or ""))
  -- Below the low mark in the warning colour, otherwise the good one; how much it added under it,
  -- and a bar in the same colour: on the whole 0–100% scale, from where this charge started to
  -- where it is now (from 0 when the start isn't known).
  local colour = level and level < LOW and "warn" or "good"
  local body = detail and (detail:sub(1, 1):upper() .. detail:sub(2))
  if level then
    local bar = from and fmt.ranges({ { "", from, level } }, { min = 0, max = 100, unit = "%", color = colour })
      or fmt.bar(level, { max = 100, color = colour })
    body = (body and (body .. "\n") or "") .. bar
  end
  log(fmt.color(colour, line), body)
  if level and level < LOW then
    notify("Battery at " .. pct(level), "Unplugged before it was charged — plug in again soon")
  end
end
