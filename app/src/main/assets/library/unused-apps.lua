-- unused-apps.lua — the apps you haven't opened for 30 days or more, the longest unused first. No network.
-- @title Unused apps
-- @desc The apps you haven't opened for a month, to tidy up: a widget
-- @author Kolahal
-- @version 1.0
-- @widget list 4x2
-- @uses usage
-- @option days 30 How long an app is not opened to count, in days (7 to 365)
-- @tags apps, usage, storage, list
-- @requires 0.5.12
--
-- Its widget, once you placed it on the widgets page, lists the apps you haven't opened for 30 days
-- or more, each with the days since and its size. An app Android has no use recorded for says so.
-- A tap on it logs the whole list. The `storage` command lists them too: there a long-press on one
-- offers its info and uninstall.
-- It needs usage access for Kolahal (usage-access) and its own "Your app usage" switch.

local started = event and event.name
local days = math.max(7, math.min(365, math.floor(tonumber(options.days) or 30)))
local apps = usage.unused(days)

-- Nil without usage access or the switch: the card says so, and a tap says what to do.
if apps == nil then
  if widget then
    widget.show{ icon = "moon", items = {}, empty = "Needs usage access", tap = { args = "list", page = "log", label = "Show what to do" } }
  end
  if started == nil or (args or {})[1] == "list" then
    log("Unused apps needs usage access", "Run usage-access, and allow \"Your app usage\" on this script's sheet")
  end
  return
end

-- A size as Android's Settings write it, and as the storage command does: "<1 MB", "410 MB", "1.2 GB".
local function size(bytes)
  if not bytes then return nil end
  if bytes >= 1e9 then return string.format("%.1f GB", bytes / 1e9) end
  if bytes < 1e6 then return "<1 MB" end
  return string.format("%d MB", math.floor(bytes / 1e6))
end

local function since(a) return a.days and (math.floor(a.days) .. " days") or "no use recorded" end

if widget then
  local rows = {}
  for i, a in ipairs(apps) do
    rows[i] = { title = a.app, detail = size(a.bytes), trailing = since(a) }
  end
  widget.show{
    icon = "moon",
    items = rows,
    empty = "Every app you can uninstall was opened in the last " .. days .. " days",
    -- The list changes slowly: a new look when the card shows after 6 hours.
    staleAfter = 6 * 3600,
    tap = { args = "list", page = "log", label = "Show the list" },
  }
end

-- A tap on the widget, or a run by hand: the whole list in the log.
if started == nil or (args or {})[1] == "list" then
  if #apps == 0 then return log("Every app you can uninstall was opened in the last " .. days .. " days") end
  local rows = { { "App", "Not opened", "Size" } }
  for _, a in ipairs(apps) do rows[#rows + 1] = { a.app, since(a), size(a.bytes) or "" } end
  log(#apps .. (#apps == 1 and " app" or " apps") .. " not opened for " .. days .. " days or more", fmt.table(rows, { header = true, align = "lrr" }))
end
