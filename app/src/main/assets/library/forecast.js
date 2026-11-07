// forecast.js — 3-day forecast from Open-Meteo (free, no API key).
// @command
// @desc 3-day forecast: forecast [city] (default: its city option)
// @usage [<city>]
// @title 3-day forecast
// @at 07:00
// @option city "" Default city; empty: London
//
// Usage:
//   forecast                  forecast for its city option (London if it's empty)
//   forecast Tokyo            forecast for Tokyo
//
// On a timer (no arguments) it sets today's summary as its status line.

// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

const line = (argline || "").trim();

// The default city was saved with --default before it was an option.
const setDefault = line.match(/^--default\s+(.+)$/);
if (setDefault) {
  return log(fmt.color("muted", `The default city is an option now: script set forecast city ${setDefault[1].trim()}`));
}

// A city saved with --default still counts until the option is set.
const city = line || (options.city || "").trim() || store.get("city") || "London";

// WMO weather codes -> short text.
function describe(code) {
  if (code === 0) return "clear";
  if (code <= 2) return "partly cloudy";
  if (code === 3) return "overcast";
  if (code <= 48) return "fog";
  if (code <= 57) return "drizzle";
  if (code <= 67) return "rain";
  if (code <= 77) return "snow";
  if (code <= 82) return "showers";
  if (code <= 86) return "snow showers";
  return "thunderstorm";
}

async function getJson(url) {
  const r = await http.get(url);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return JSON.parse(r.body);
}

let place, data;
try {
  const geo = await getJson(
    `https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=${encodeURIComponent(city)}`
  );
  if (!geo.results || geo.results.length === 0) return fail("No forecast", `No place named '${city}'`);
  place = geo.results[0];
  data = await getJson(
    "https://api.open-meteo.com/v1/forecast" +
      `?latitude=${place.latitude}&longitude=${place.longitude}` +
      "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
      "&timezone=auto&forecast_days=3"
  );
} catch (e) {
  return fail("No forecast", `${e.message || e}`);
}

const d = data.daily;
if (!d || !d.time || d.time.length === 0) return fail("No forecast", "Open-Meteo sent no days");

const names = ["Today", "Tomorrow"];
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const where = [place.name, place.country_code].filter(Boolean).join(", ");

// The place is the title; one row per day under it.
const rows = [];
let todayLine = "";
for (let i = 0; i < d.time.length; i++) {
  const label = names[i] || WEEKDAYS[new Date(d.time[i] + "T12:00:00Z").getUTCDay()];
  const hi = Math.round(d.temperature_2m_max[i]);
  const lo = Math.round(d.temperature_2m_min[i]);
  const rain = d.precipitation_probability_max ? d.precipitation_probability_max[i] : null;
  rows.push({ label, code: d.weather_code[i], lo, hi, rain });
  if (i === 0) todayLine = `${place.name}: ${describe(d.weather_code[i])} ${lo}–${hi}°C`;
}

// WMO weather codes -> a symbol for the sky.
function icon(code) {
  if (code === 0) return "☀️";
  if (code <= 2) return "⛅";
  if (code === 3) return "☁️";
  if (code <= 48) return "🌫️";
  if (code <= 67) return "🌧️";
  if (code <= 77) return "❄️";
  if (code <= 82) return "🌦️";
  if (code <= 86) return "🌨️";
  return "⛈️";
}

// One bar a day, from its low to its high, all on one scale, so the days compare at a glance: the
// day and its sky, the low, the bar, the high, and a chance of rain from 30% (in the warning colour
// from 60%).
const days = rows.map((r) => {
  const rain = r.rain != null && r.rain >= 30 ? " " + fmt.color(r.rain >= 60 ? "warn" : "muted", `☂ ${r.rain}%`) : "";
  return [`${r.label} ${icon(r.code)}`, r.lo, r.hi, `${r.lo}°`, `${r.hi}°${rain}`];
});
log(where, fmt.ranges(days, { color: "warn" }), {
  url: `https://open-meteo.com/en/docs#latitude=${place.latitude}&longitude=${place.longitude}`,
});

if (!line) status(todayLine);
