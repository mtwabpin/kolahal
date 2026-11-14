-- quick-toggles.lua — switches for Do Not Disturb and the work profile, on a widget. No network.
-- @title Quick toggles
-- @desc Switches for Do Not Disturb and the work profile: a widget
-- @author Kolahal
-- @version 1.0
-- @widget toggles 4x1
-- @uses dnd, work
-- @tags dnd, do not disturb, work profile, switches
-- @requires 0.5.8
--
-- Its widget, once you placed it on the widgets page, has a switch for Do Not Disturb, and one for
-- the work profile when the phone has one. A tap on a switch runs this script with "dnd" or "work".
-- The script changes the thing, waits until Android has done it, and shows the new state.
--
-- - Kolahal needs Do Not Disturb access for the first switch: type dnd access.
-- - Since Android 15 that switch turns Kolahal's own Do Not Disturb mode on and off. One you
--   turned on yourself stays on.
-- - Android lets only the default launcher pause and resume the work profile. If your work
--   profile needs its PIN to resume, Android asks you for it.
-- - Both can change elsewhere, so the widget counts as old after a minute: the widgets page then
--   reads them again.
-- Run by hand, it logs both states.

-- What started this run: the widgets page ("widget"), a tap on a switch ("action"), or you (nil).
local started = event and event.name
-- The switch that was tapped: "dnd" or "work". Nothing in any other run.
local word = (args or {})[1]
if word ~= nil and word ~= "dnd" and word ~= "work" then
  return log(fmt.color("muted", "Usage: script run quick-toggles [dnd|work]"))
end

local function dndOn() return (device.dnd() or "off") ~= "off" end

-- Android applies a change a moment after it is asked for. This waits until `done()` is true, for
-- `seconds` at most, and says whether it was.
local function settled(done, seconds)
  for _ = 1, seconds * 4 do
    if done() then return true end
    sleep(250)
  end
  return done()
end

-- What went wrong with a switch. It is said at the end, as an error, after the widget has the
-- state as it is: so the run counts as failed, and the switch still leaves "working".
local problem

if word == "dnd" then
  local was = dndOn()
  if not device.setDnd(was and "off" or "on") then
    problem = "Couldn't change Do Not Disturb. Kolahal needs Do Not Disturb access: type dnd access"
  elseif not settled(function() return dndOn() ~= was end, 3) then
    problem = was and "Do Not Disturb is still on. You turned it on outside Kolahal, so turn it off there"
      or "Do Not Disturb didn't turn on"
  end
elseif word == "work" then
  local was = device.work()
  -- No work profile: there is no such switch on the widget, and nothing to do.
  if was then
    local resume = was == "paused"
    local asked = device.setWork(resume)
    -- A refused pause is final. A refused resume can mean that Android asks for the work profile's
    -- PIN, so that waits longer.
    local wait = asked and 5 or (resume and 15 or 0)
    if not settled(function() return device.work() ~= was end, wait) then
      problem = resume and "Couldn't resume the work profile. Type work on, or open a work app"
        or "Couldn't pause the work profile. Kolahal must be the default launcher: type home"
    end
  end
end

local dnd = dndOn()
local work = device.work()

-- Its widget: a switch for each, with its state as it is now. Kolahal never guesses a new state:
-- this is where it comes from, also after a change that didn't work.
if widget then
  local items = { { label = "Do Not Disturb", icon = "🌙", on = dnd, tap = { args = "dnd" } } }
  if work then
    items[2] = { label = "Work profile", icon = "💼", on = work == "on", tap = { args = "work" } }
  end
  widget.show{ items = items, staleAfter = 60 }
end

if problem then error(problem, 0) end

-- A run by hand: both states in the log. A run for the widgets page or for a tap logs nothing.
if started == nil then
  log("Quick toggles", "Do Not Disturb " .. (dnd and "on" or "off") .. (work and ("\nWork profile " .. work) or "\nNo work profile on this phone"))
end
