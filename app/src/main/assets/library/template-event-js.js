// @title Template: event (JavaScript)
// @desc A starting point for a script that runs when something happens, in JavaScript: here, plugging the charger in and out
// @author Kolahal
// @version 1.0
// @on charger, unplug
// @tags template, javascript, battery
// @requires 0.4.1
//
// The JavaScript version of template-event. It tells you how much a charge added, and how long it
// took. Nothing leaves the phone.
//
// The @on line lists the events it runs on: unlock, charger, unplug, wifi, offline, boot, headset,
// and notification (which also needs `@uses notifications`). A run gets `event`; event.name says
// which one it was.

if (!event) {
  // Run by hand (`script run template-event-js`): no event, so say what it does.
  return log(fmt.color("muted", "Runs when you plug the charger in and when you unplug it"));
}

if (event.name === "charger") {
  // event.level: the battery in percent; event.plug: ac, usb or wireless.
  store.set("from", String(event.level));
  store.set("at", String(Date.now()));
  status(`Charging from ${event.level}%`);
} else if (event.name === "unplug") {
  const from = Number(store.get("from"));
  const at = Number(store.get("at"));
  status(null); // clears its status line
  if (store.get("from") != null && at && event.level != null) {
    const minutes = Math.round((Date.now() - at) / 60000);
    log(`Charged ${fmt.color("good", `+${event.level - from}%`)}`, `${from}% → ${event.level}% in ${minutes}m`);
  }
}
