-- @title Template: event
-- @desc A starting point for a script that runs when something happens: here, plugging the charger in and out
-- @author Kolahal
-- @version 1.2
-- @on charger, unplug
-- @tags template, battery
-- @requires 0.4.1
--
-- A starting point for a script that runs when something happens on the phone. This one tells you
-- how much a charge added, and how long it took. Nothing leaves the phone.
--
-- The @on line lists the events it runs on: unlock, charger, unplug, wifi, offline, boot, headset,
-- and notification (which also needs `@uses notifications`). A run gets `event`; event.name says
-- which one it was.

if not event then
  -- Run by hand (`script run template-event`): no event, so say what it does.
  return log(fmt.color("muted", "Runs when you plug the charger in and when you unplug it"))
end

if event.name == "charger" then
  -- event.level: the battery in percent; event.plug: ac, usb or wireless.
  store.set("from", tostring(event.level))
  store.set("at", tostring(os.time()))
  status(string.format("Charging from %d%%", event.level))
elseif event.name == "unplug" then
  local from, at = tonumber(store.get("from")), tonumber(store.get("at"))
  status(nil) -- clears its header line
  if from and at and event.level then
    local minutes = math.floor((os.time() - at) / 60 + 0.5)
    log("Charged " .. fmt.color("good", string.format("+%d%%", event.level - from)), string.format("%d%% → %d%% in %dm", from, event.level, minutes))
  end
end
