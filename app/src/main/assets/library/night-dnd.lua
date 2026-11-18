-- night-dnd.lua — Do Not Disturb for the night: on at 11 PM, off at 7 AM. No network.
-- @desc Do Not Disturb on at 11 PM and off at 7 AM; if you turned it on yourself, it does nothing
-- @title Night Do Not Disturb
-- @author Kolahal
-- @version 1.0
-- @requires 0.5.0
-- @at 23:00, 07:00
-- @uses dnd
-- @option mode priority|alarms|none Priority only, alarms only, or total silence
--
-- At ON it turns Do Not Disturb on, unless it's on already: then it's yours, and it leaves it be,
-- at OFF too. At OFF it turns off only what it turned on. (Since Android 15 that's Kolahal's own
-- Do Not Disturb mode: one you turned on yourself stays on.)
-- Kolahal needs Do Not Disturb access (type `dnd access`). To change the times, change ON and OFF
-- here and its daily times (`script at night-dnd 22:30, 06:30`) to match. Run by hand, it says
-- what it will do.

local ON, OFF = "23:00", "07:00"
local MODE = options.mode or "priority"

-- The mode it set tonight, if it set one.
local mine = store.get("on")

if event and event.time == ON then
  if mine then return end
  if device.dnd() ~= "off" then return end
  if device.setDnd(MODE) then
    store.set("on", MODE)
    log("Do Not Disturb on until " .. OFF)
  else
    log(fmt.color("bad", "Couldn't turn Do Not Disturb on"), "Kolahal needs Do Not Disturb access: type dnd access")
  end
elseif event and event.time == OFF then
  if not mine then return end
  store.set("on", nil)
  if device.dnd() == "off" then return end
  device.setDnd("off")
  if device.dnd() == "off" then log("Do Not Disturb off") end
else
  local now = device.dnd()
  if mine then
    log("Do Not Disturb " .. tostring(now), "This script turned it on; it turns it off at " .. OFF)
  else
    log("Do Not Disturb " .. tostring(now), "On at " .. ON .. " unless it's on already, off at " .. OFF .. " if this script turned it on")
  end
end
