-- night-work.lua — the work profile off for the night: paused at 11 PM, back at 7 AM. No network.
-- @desc Pauses the work profile at 11 PM and resumes it at 7 AM; if you paused it yourself, it does nothing
-- @title Night work profile
-- @author Kolahal
-- @version 1.0
-- @requires 0.5.0
-- @at 23:00, 07:00
-- @uses work
--
-- At ON it pauses the work profile, unless it's paused already: then you paused it, and it leaves
-- it be, at OFF too. At OFF it resumes only what it paused. Android lets only the default launcher
-- do this, and only in front: Kolahal runs it in the foreground at its times when it may set exact
-- alarms (Settings → Apps → Kolahal → Alarms & reminders). To change the times, change ON and OFF
-- here and its daily times (`script at night-work 22:30, 06:30`) to match. Run by hand, it says
-- what it will do.

local ON, OFF = "23:00", "07:00"

-- Set when it paused the work profile tonight.
local mine = store.get("paused")

if event and event.time == ON then
  if mine then return end
  -- No work profile, or paused already.
  if device.work() ~= "on" then return end
  if device.setWork(false) then
    store.set("paused", "1")
    log("Work profile paused until " .. OFF)
  else
    log(fmt.color("bad", "Couldn't pause the work profile"),
      "Kolahal must be the default launcher (type home), with Alarms & reminders allowed")
  end
elseif event and event.time == OFF then
  if not mine then return end
  store.set("paused", nil)
  -- Resumed since: nothing to do.
  if device.work() ~= "paused" then return end
  if device.setWork(true) then
    log("Work profile on")
  else
    log(fmt.color("bad", "Couldn't resume the work profile"), "Type work on, or open a work app")
  end
else
  local now = device.work()
  if now == nil then
    log("No work profile on this phone")
  elseif mine then
    log("Work profile " .. now, "This script paused it; it resumes it at " .. OFF)
  else
    log("Work profile " .. now, "Paused at " .. ON .. " unless it's paused already, back at " .. OFF .. " if this script paused it")
  end
end
