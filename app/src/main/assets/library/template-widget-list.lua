-- @title Template: checklist widget
-- @desc A starting point for a widget you tap: a checklist for each day, where a tap on a row ticks it
-- @author Kolahal
-- @version 1.0
-- @widget list 2x2
-- @at 00:01
-- @tags template, widget, checklist
-- @requires 0.5.9
-- @option items "Water the plants, Vitamins, A walk" The things to do each day, with commas between them
--
-- A starting point for a widget that you tap. This one is a checklist for each day: a row for
-- each thing in its items option, with a checkbox in front. A tap on a row ticks it, or takes the
-- tick away. Each new day starts with no ticks. Nothing leaves the phone.
-- template-widget-list-js does the same in JavaScript.
--
-- To see the card: install the script, run widget add, and pick "Template: checklist
-- widget" in the Kolahal group.
--
-- How a tap works:
-- 1. Each row has tap = { args = "tick 2" }. A tap on it runs this script again with those words.
-- 2. In that run, event.name is "action", and args is { "tick", "2" }.
-- 3. The script changes what it keeps, then calls widget.show with the new rows. Kolahal never
--    ticks a box by itself: the card shows what the script tells it.
--
-- The @at line runs it a minute after midnight, so the card starts the new day with no ticks.

-- What started this run: a tap ("action"), the widgets page ("widget"), its daily time ("at"), or you (nil).
local started = event and event.name

-- The things to do, from the items option: "Water the plants, Vitamins" gives two rows.
local things = {}
for thing in (options.items or ""):gmatch("[^,]+") do
  thing = thing:gsub("^%s+", ""):gsub("%s+$", "")
  if thing ~= "" then things[#things + 1] = thing end
end

-- store keeps text between runs, for this script only. Each widget of this script keeps its own
-- ticks: event.instance is "2", "3"… in a run for a second or third widget, and nil for the first.
local key = (event and event.instance) or ""
local today = os.date("%Y-%m-%d")

-- Today's ticks, kept as the ticked things, one on each line. The ticks of another day don't count.
local ticked = {}
if store.get("day" .. key) == today then
  for thing in (store.get("ticked" .. key) or ""):gmatch("[^\n]+") do ticked[thing] = true end
end

-- A tap on a row: "tick 2" ticks the second thing, or takes its tick away.
local word, n = (args or {})[1], tonumber((args or {})[2])
if word == "tick" and things[n] then
  ticked[things[n]] = not ticked[things[n]] or nil
  local kept = {}
  for _, thing in ipairs(things) do
    if ticked[thing] then kept[#kept + 1] = thing end
  end
  store.set("day" .. key, today)
  store.set("ticked" .. key, table.concat(kept, "\n"))
end

-- The rows: each thing, with done = true or false for its checkbox, and a tap that ticks it.
local rows, done = {}, 0
for i, thing in ipairs(things) do
  if ticked[thing] then done = done + 1 end
  rows[i] = { title = thing, done = ticked[thing] == true, tap = { args = "tick " .. i } }
end

-- The card: its title, over the rows of a large card, counts the ticks. Once every thing is
-- ticked, it is in the theme's good colour. A card too small for every row shows the first ones,
-- and "+2 more" under them.
-- Call widget.show in every run that a tap starts, even when nothing changed: the card shows
-- only what the script tells it.
local placed = widget.show{
  title = "Today · " .. done .. " of " .. #things,
  icon = "check",
  color = (#things > 0 and done == #things) and "good" or nil,
  items = rows,
  empty = "Set its items option",
}

-- Only a run by hand logs: the list, with a mark for each tick.
if started ~= nil then return end
local lines = {}
for i, thing in ipairs(things) do lines[i] = (ticked[thing] and "✓ " or "· ") .. thing end
log("Today · " .. done .. " of " .. #things, table.concat(lines, "\n"))
if not placed then
  log(fmt.color("muted", "To see the card: run widget add and pick Template: checklist widget"))
end
