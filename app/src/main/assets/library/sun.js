// sun.js — today's sunrise, sunset and day length (Open-Meteo, no key).
// @command
// @desc Sunrise and sunset: sun | sun <city> | sun <lat> <lng> (city/coords are saved)
// @usage [<city>|<lat> <lng>]
// @title Sunrise and sunset
// @at 05:00
//
// Usage:
//   sun                  use the saved location
//   sun Lisbon           look up a city and save it
//   sun 12.97 77.59      save coordinates directly
//
// Times are local to the location (timezone=auto), so no device timezone
// handling is needed. On a timer (e.g. daily at 05:00) it sets its status line.

// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

const line = (argline || "").trim();

async function getJson(url) {
  const r = await http.get(url);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return JSON.parse(r.body);
}

try {
  // Update the saved location if arguments were given.
  const coords = line.match(/^(-?\d+(?:\.\d+)?)[\s,]+(-?\d+(?:\.\d+)?)$/);
  if (coords) {
    const lat = parseFloat(coords[1]);
    const lng = parseFloat(coords[2]);
    if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return fail("No sun times", "Latitude goes to ±90 and longitude to ±180");
    store.set("lat", String(lat));
    store.set("lng", String(lng));
    store.set("place", `${lat}, ${lng}`);
  } else if (line) {
    const geo = await getJson(
      `https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=${encodeURIComponent(line)}`
    );
    if (!geo.results || geo.results.length === 0) return fail("No sun times", `No place named '${line}'`);
    const p = geo.results[0];
    store.set("lat", String(p.latitude));
    store.set("lng", String(p.longitude));
    store.set("place", [p.name, p.country_code].filter(Boolean).join(", "));
  }

  const lat = store.get("lat");
  const lng = store.get("lng");
  if (!lat || !lng) return log(fmt.color("muted", "No location yet: sun <city> or sun <lat> <lng>"));

  const data = await getJson(
    `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}` +
      "&daily=sunrise,sunset,daylight_duration&timezone=auto&forecast_days=1"
  );
  const d = data.daily;
  if (!d || !d.sunrise || d.sunrise.length === 0) return fail("No sun times", "Open-Meteo sent no times");

  // "2026-10-16T06:12" -> "06:12"
  const hhmm = (iso) => (iso ? iso.slice(11, 16) : "--:--");
  const rise = hhmm(d.sunrise[0]);
  const set = hhmm(d.sunset[0]);
  const secs = d.daylight_duration ? d.daylight_duration[0] : null;
  // Rounded as a whole: the minutes alone could round up to "60m".
  const minutes = secs != null ? Math.round(secs / 60) : null;

  // Big numbers: what matters now, large (the time to sunrise, the daylight left, or after sunset
  // the day's length), then sunrise and sunset; under them a bar of how much daylight has gone. The
  // times are the place's own, so they're read as UTC and compared with now moved by its offset.
  const at = (iso) => Date.parse(iso.length === 16 ? `${iso}:00Z` : `${iso}Z`);
  const from = d.sunrise[0] ? at(d.sunrise[0]) : NaN;
  const to = d.sunset[0] ? at(d.sunset[0]) : NaN;
  const now = Date.now() + (data.utc_offset_seconds || 0) * 1000;
  const hm = (ms) => {
    const m = Math.max(0, Math.round(ms / 60000));
    return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m}m`;
  };
  const day = minutes != null ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : null;
  let first;
  if (!(to > from)) first = day ? [day, "of daylight"] : null;
  else if (now < from) first = [hm(from - now), "to sunrise"];
  else if (now < to) first = [hm(to - now), "daylight left"];
  else first = day ? [day, "of daylight today"] : null;
  const numbers = fmt.stats([first, [rise, "sunrise"], [set, "sunset"]].filter(Boolean));
  const share = to > from ? Math.min(1, Math.max(0, (now - from) / (to - from))) : null;
  const under = [
    share == null ? null : fmt.bar(share, { color: "warn" }),
    day && first && first[1] !== "of daylight today" && first[1] !== "of daylight" ? `${day} of daylight today` : null,
  ].filter(Boolean);
  log(store.get("place") || `${lat}, ${lng}`, [numbers, ...under].join("\n"));
  status(`Sun ${rise}–${set}`);
} catch (e) {
  return fail("No sun times", `${e.message || e}`);
}
