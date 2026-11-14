-- world-clocks.lua — the time in the places you list, on a widget. No network.
-- @title World clocks
-- @desc The time in up to 4 places you list: a widget
-- @author Kolahal
-- @version 1.0
-- @widget clock 2x2
-- @tags clock, time, zones
-- @requires 0.5.8
-- @option zones "Asia/Kolkata Europe/London America/New_York" Up to 4 time zones, by name; Zone=Label names one yourself
--
-- Its widget, once you placed it on the widgets page, shows the time in each place. Kolahal moves
-- the clocks itself. So this script runs only when you place the widget, when you tap it, and once
-- in a week. After you change its zones option, tap the widget: it then shows the new places.
--
-- A zone is a name such as Asia/Kolkata, Europe/London or UTC. The place's name on the widget is
-- the last part of the zone's name ("New York" for America/New_York). To pick the name yourself,
-- write it after the zone: Asia/Kolkata=Pune. Write a space in a name as "_": Asia/Dubai=Head_office.
--
-- It needs no access.

-- What started this run: the widgets page ("widget", also for a tap on the widget), or you (nil).
local started = event and event.name

-- The zones option, read into places: each has a zone and a label. With nothing in the option, the
-- widget shows the zones it starts with.
local function places(text)
  local list = {}
  for word in (text or ""):gmatch("[^%s,]+") do
    local zone, label = word:match("^([^=]+)=(.+)$")
    zone = zone or word
    -- Without a label of its own: the zone's last part, with spaces ("America/New_York" reads "New York").
    label = label or zone:match("([^/]+)$")
    list[#list + 1] = { zone = zone, label = (label:gsub("_", " ")) }
  end
  return list
end

local zones = places(options.zones)
if #zones == 0 then zones = places("Asia/Kolkata Europe/London America/New_York") end

-- Its widget: a clock for each place. A zone that Kolahal doesn't know is an error that names it.
-- The clocks never get old, so the content counts as current for a week: the longest there is.
if widget then
  widget.show{ zones = zones, staleAfter = 7 * 86400 }
end

-- A run by hand: the places, and how to see their time. A run for the widgets page logs nothing.
if started == nil then
  local names = {}
  for i, place in ipairs(zones) do
    if i > 4 then break end
    names[i] = place.label .. " (" .. place.zone .. ")"
  end
  log("World clocks", table.concat(names, "\n") .. "\nTheir time is on its widget: tap + Add on the widgets page")
end
