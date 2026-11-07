// @title Air quality
// @desc Air quality: aqi [city], without one its city option (US, Indian or European index; the nearest station's with a WAQI token)
// @author Kolahal
// @version 1.3
// @command
// @usage [<city>]
// @at 08:00
// @tags weather, air, health
// @requires 0.4.7
// @option city "" The city when you type none
// @option scale us|in|eu The index: us (US EPA), in (India's CPCB) or eu (European)
// @secret WAQI_TOKEN api.waqi.info Optional: your free token from aqicn.org/data-platform/token, for the nearest station's reading
//
// Talks to: geocoding-api.open-meteo.com and air-quality-api.open-meteo.com (free, no key), and
// api.waqi.info when you set a WAQI token. `aqi Delhi`, or `aqi` for its city option (`script set
// aqi city Pune`); `script set aqi scale in` for India's index. On a timer (no arguments) it pins
// the latest figure in the header.
//
// Where the numbers come from:
// - Without a token, or with scale in or eu: Open-Meteo's model (CAMS, on a grid tens of km wide),
//   not a measurement. In a smog it can read far below the stations: Delhi one November morning
//   was 157 µg/m³ of PM2.5 in the model and 380–440 at the monitors.
// - us and in: two figures worked out here from Open-Meteo's hourly concentrations. "Last hour"
//   puts this hour's readings through the scale's table, as aqi.in, aqicn.org and IQAir do;
//   "24 hours" uses the scale's own averages (24-hour means of PM, 8-hour of CO and O₃, and NO₂
//   and SO₂ over 24 hours on India's scale, this hour on the US one). Past the top of a table the
//   count goes on at the top band's slope, with no stop at 500, as those sites do.
// - eu: Open-Meteo's European index, as it gives it.
// - With a WAQI token and scale us: the nearest station's latest reading on aqicn.org's US scale,
//   with its name, time and the credit WAQI asks for. WAQI has no measured 24-hour figure, so
//   there is none. A station more than 50 km away or 6 hours old, or an error, goes back to
//   Open-Meteo and says why. WAQI gives only the US scale, so in and eu stay on Open-Meteo.

// A failure: what didn't work, in the theme's bad colour, and why under it.
const fail = (what, why) => log(fmt.color("bad", what), why);

const line = (argline || "").trim();

// The default city was saved with --default before it was an option.
const setDefault = line.match(/^--default\s+(.+)$/);
if (setDefault) {
  return log(fmt.color("muted", `The default city is an option now: script set aqi city ${setDefault[1].trim()}`));
}

// A city saved with --default still counts until the option is set.
const city = line || (options.city || "").trim() || store.get("city");
if (!city) return log(fmt.color("muted", "No city yet: aqi <city>, or script set aqi city <city> to keep one"));

async function getJson(url) {
  const r = await http.get(url);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return JSON.parse(r.body);
}

// The same air reads far apart on the three scales: Delhi one November morning was 202 Very
// unhealthy on the US one, 307 Very poor on India's and 107 Extremely poor on Europe's.
// Each in bands: the band's top, its name and its colour.
const BANDS = {
  us: [[50, "Good", "good"], [100, "Moderate", "warn"], [150, "Unhealthy for sensitive groups", "warn"],
    [200, "Unhealthy", "bad"], [300, "Very unhealthy", "bad"], [Infinity, "Hazardous", "bad"]],
  in: [[50, "Good", "good"], [100, "Satisfactory", "good"], [200, "Moderate", "warn"],
    [300, "Poor", "warn"], [400, "Very poor", "bad"], [Infinity, "Severe", "bad"]],
  eu: [[20, "Good", "good"], [40, "Fair", "good"], [60, "Moderate", "warn"],
    [80, "Poor", "warn"], [100, "Very poor", "bad"], [Infinity, "Extremely poor", "bad"]],
};
const scale = BANDS[options.scale] ? options.scale : "us";
const band = (aqi) => BANDS[scale].find(([top]) => aqi <= top).slice(1);

// The US (EPA, 2024) and Indian (CPCB) tables. Each pollutant: Open-Meteo's name, the hours its
// 24-hour figure averages, µg/m³ per the table's unit (ppm or ppb at 25 °C on the US one, mg/m³
// for CO on India's), the step readings are cut to, and the top of each band. India's scale ends
// with "and above"; its top band here is as wide as the one under it.
const TABLES = {
  us: {
    tops: [50, 100, 150, 200, 300, 500],
    rows: [
      ["pm2_5", 24, 1, 0.1, [9, 35.4, 55.4, 125.4, 225.4, 325.4]],
      ["pm10", 24, 1, 1, [54, 154, 254, 354, 424, 604]],
      ["ozone", 8, 1963, 0.001, [0.054, 0.07, 0.085, 0.105, 0.2]],
      ["carbon_monoxide", 8, 1145, 0.1, [4.4, 9.4, 12.4, 15.4, 30.4, 50.4]],
      ["nitrogen_dioxide", 1, 1.88, 1, [53, 100, 360, 649, 1249, 2049]],
      ["sulphur_dioxide", 1, 2.62, 1, [35, 75, 185, 304, 604, 1004]],
    ],
  },
  in: {
    tops: [50, 100, 200, 300, 400, 500],
    rows: [
      ["pm2_5", 24, 1, 1, [30, 60, 90, 120, 250, 380]],
      ["pm10", 24, 1, 1, [50, 100, 250, 350, 430, 510]],
      ["nitrogen_dioxide", 24, 1, 1, [40, 80, 180, 280, 400, 520]],
      ["sulphur_dioxide", 24, 1, 1, [40, 80, 380, 800, 1600, 2400]],
      ["carbon_monoxide", 8, 1000, 0.1, [1, 2, 10, 17, 34, 51]],
      ["ozone", 8, 1, 1, [50, 100, 168, 208, 748, 1288]],
    ],
  },
};

// One reading on a table: the band it falls in, by the scales' formula (a band runs from one step
// over the last band's top), and past the last band on at that band's slope.
function subIndex(c, step, highs, tops) {
  c = Math.floor(c / step + 1e-6) * step;
  let i = 0;
  while (i < highs.length - 1 && c > highs[i]) i++;
  const lo = i ? highs[i - 1] + step : 0;
  const from = i ? tops[i - 1] + 1 : 0;
  return from + ((c - lo) * (tops[i] - from)) / (highs[i] - lo);
}

// A figure from Open-Meteo's hourly readings (this hour last): each pollutant over its hours, or
// this hour alone, with at least three in four of them there; the highest counts.
function figure(hourly, thisHour) {
  const { tops, rows } = TABLES[scale];
  let top = null;
  for (const [name, hours, per, step, highs] of rows) {
    const n = thisHour ? 1 : hours;
    const values = (hourly[name] || []).slice(-n).filter((v) => v != null);
    if (values.length < Math.ceil(n * 0.75)) continue;
    const c = values.reduce((a, b) => a + b, 0) / values.length / per;
    top = Math.max(top ?? 0, subIndex(c, step, highs, tops));
  }
  return top == null ? null : Math.round(top);
}

// Kilometres between two points.
function km(lat1, lon1, lat2, lon2) {
  const r = Math.PI / 180;
  const a = Math.sin(((lat2 - lat1) * r) / 2) ** 2 +
    Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.sin(((lon2 - lon1) * r) / 2) ** 2;
  return 12742 * Math.asin(Math.sqrt(a));
}

const token = secrets.get("WAQI_TOKEN");
let place, station, away, why, current, hourly;
try {
  const geo = await getJson(
    `https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=${encodeURIComponent(city)}`
  );
  if (!geo.results || geo.results.length === 0) return fail("No air quality", `No place named '${city}'`);
  place = geo.results[0];
} catch (e) {
  return fail("No air quality", `${e.message || e}`);
}

// The nearest station, with a token on the US scale; anything amiss goes back to Open-Meteo.
if (token && scale === "us") {
  try {
    const w = await getJson(`https://api.waqi.info/feed/geo:${place.latitude};${place.longitude}/?token=${token}`);
    const d = w.data;
    if (w.status !== "ok") throw new Error(typeof d === "string" ? d : "no answer");
    const [lat, lon] = d.city?.geo || [];
    away = km(place.latitude, place.longitude, lat, lon);
    const age = (Date.now() - Date.parse(d.time?.iso)) / 3600000;
    if (typeof d.aqi !== "number") why = "the nearest station sent no reading";
    else if (!(away <= 50)) why = `the nearest station is ${Math.round(away)} km away`;
    else if (!(age < 6)) why = "the nearest station has nothing from the last 6 hours";
    else station = d;
  } catch (e) {
    why = `WAQI: ${e.message || e}`;
  }
}

if (!station) {
  try {
    const data = await getJson(
      "https://air-quality-api.open-meteo.com/v1/air-quality" +
        `?latitude=${place.latitude}&longitude=${place.longitude}&current=european_aqi&timezone=auto` +
        "&hourly=pm2_5,pm10,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,ozone&past_hours=23&forecast_hours=1"
    );
    current = data.current || {};
    hourly = data.hourly || {};
  } catch (e) {
    return fail("No air quality", `${e.message || e}`);
  }
}

// The index rows ([name, figure]), the readings under them ([name, value, unit]) and lines of notes.
const POLLUTANTS = { pm25: "PM2.5", pm10: "PM10", o3: "O₃", no2: "NO₂", so2: "SO₂", co: "CO" };
let figures, readings, notes, url;
if (station) {
  const credit = (station.attributions || []).map((a) => a.name);
  if (!credit.some((n) => /World Air Quality Index/.test(n))) credit.push("World Air Quality Index Project");
  const at = (station.time.s || "").slice(11, 16);
  figures = [["Latest", Math.round(station.aqi)]];
  readings = [];
  notes = [
    `${(station.city.name || "Station").split(",")[0]}, ${Math.round(away)} km away, at ${at}`,
    ...(POLLUTANTS[station.dominentpol] ? [`Main pollutant: ${POLLUTANTS[station.dominentpol]}`] : []),
    `Data: ${credit.join(", ")}`,
  ];
  url = /^https:\/\//.test(station.city.url || "") ? station.city.url : "https://aqicn.org/";
} else {
  figures = scale === "eu"
    ? [["Index", current.european_aqi == null ? null : Math.round(current.european_aqi)]]
    : [["Last hour", figure(hourly, true)], ["24 hours", figure(hourly, false)]];
  if (figures[0][1] == null) return fail("No air quality", "Open-Meteo sent no reading");
  figures = figures.filter(([, v]) => v != null);
  const now = (name) => Math.round((hourly[name] || []).slice(-1)[0] ?? 0) + "";
  readings = [
    ["PM2.5", now("pm2_5"), "µg/m³"],
    ["PM10", now("pm10"), "µg/m³"],
    ["NO₂", now("nitrogen_dioxide"), "µg/m³"],
    ["O₃", now("ozone"), "µg/m³"],
  ];
  notes = ["Open-Meteo's model, not a station" + (why ? ` (${why})` : "")];
  url = `https://open-meteo.com/en/docs/air-quality-api#latitude=${place.latitude}&longitude=${place.longitude}`;
}

// The place and scale are the title; the figures and readings under it, each figure in the colour
// of its band. From Kolahal 0.4.16 a table (fmt.table); before, a column padded so the numbers line
// up (mono keeps the terminal font for that).
const newer = typeof fmt.table === "function";
const name = `${scale.toUpperCase()} AQI`;
const rows = [...figures.map(([n, v]) => [n, String(v), band(v)[0]]), ...readings];
const tint = (i, text) => (i < figures.length ? fmt.color(band(figures[i][1])[1], text) : text);
const where = [place.name, place.country_code].filter(Boolean).join(", ");
if (newer) {
  const table = fmt.table(rows.map(([n, value, unit], i) => [n, tint(i, value), unit]), { align: "lrl" });
  log(`${where} · ${name}`, [table, ...notes].join("\n"), { url });
} else {
  const nameWidth = Math.max(...rows.map((r) => r[0].length));
  const valueWidth = Math.max(...rows.map((r) => r[1].length));
  const body = rows
    .map(([n, value, unit], i) => tint(i, `${n.padEnd(nameWidth)}  ${value.padStart(valueWidth)}  ${unit}`))
    .concat(notes)
    .join("\n");
  log(`${where} · ${name}`, body, { url, mono: "1" });
}

// Once, without a token: how to get the nearest station's reading instead of the model's.
if (!token && scale === "us" && !store.get("tokenHint")) {
  store.set("tokenHint", "1");
  log(fmt.color("muted", "For the nearest station's reading: a free token from aqicn.org, then script secret aqi WAQI_TOKEN"),
    { url: "https://aqicn.org/data-platform/token/" });
}

// The status line, to pin: from Kolahal 0.4.16 with a bar in its band's colour. While the air is
// good, the bar fills toward the top of the good band (50, or 20 on the European scale); past it the
// air isn't safe any more, so the bar is full and its colour (orange, then red) says how bad. An older
// Kolahal refuses a color, so it gets the line alone.
const GOOD_TOP = BANDS[scale][0][0];
const aqi = figures[0][1];
const text = `${name} ${aqi} · ${place.name}`;
if (!line) status(text, newer ? { progress: Math.min(1, aqi / GOOD_TOP), color: band(aqi)[1] } : undefined);
