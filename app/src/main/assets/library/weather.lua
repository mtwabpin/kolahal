-- weather.lua — the current weather from wttr.in (no API key): temperature, sky, humidity, wind, UV.
-- @command
-- @desc Current weather: weather [city] (default: its city option, or your location)
-- @usage [<city>]
-- @title Weather
-- @every 1h
-- @option city "" Default city; empty: your IP address's location
--
-- Usage:
--   weather                 weather for its city option (or your IP location)
--   weather Paris           weather for Paris
--
-- On a timer (no arguments) it sets the result as its status line; pin it
-- (script pin weather) to see it at the top of home.

local function urlencode(s)
  return (s:gsub("[^%w%-_%.~]", function(c)
    return string.format("%%%02X", string.byte(c))
  end))
end

local function trim(s)
  return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

-- A failure: what didn't work, in the theme's bad colour, and why under it.
local function fail(what, why) log(fmt.color("bad", what), why) end

local line = trim(argline or "")

-- The default city was saved with --default before it was an option.
local saved = line:match("^%-%-default%s+(.+)$")
if saved then
  return log(fmt.color("muted", "The default city is an option now: script set weather city " .. trim(saved)))
end

local city = line
if city == "" then city = trim(options.city or "") end
-- A city saved with --default still counts until the option is set.
if city == "" then city = store.get("city") or "" end
-- An empty path makes wttr.in guess the location from your IP address.
-- One line of fields: the place, the sky's symbol and words, the temperature, humidity, wind and UV
-- index ("Pune|☀️ |Sunny|+25°C|36%|←9km/h|3").
local url = "https://wttr.in/" .. urlencode(city) .. "?format=%l|%c|%C|%t|%h|%w|%u"

local ok, r = pcall(http.get, url, { ["Accept-Language"] = "en" })
if not ok then return fail("No weather", "Network error: " .. tostring(r)) end
if not r.ok then return fail("No weather", "wttr.in answered HTTP " .. tostring(r.status)) end

local text = trim(r.body or "")
if text == "" or text:find("Unknown location", 1, true) then
  return fail("No weather", "wttr.in doesn't know '" .. city .. "'")
end
local f = {}
for field in (text .. "|"):gmatch("(.-)|") do f[#f + 1] = trim(field) end
if #f < 7 then return fail("No weather", "wttr.in sent something unexpected") end
local place, icon, sky, temp, humidity, wind, uv = f[1], f[2], f[3], f[4], f[5], f[6], f[7]
-- "+25°C" reads as "25°C"; below zero keeps its minus. "←9km/h" reads as "← 9 km/h".
temp = temp:gsub("^%+", "")
wind = wind:gsub("^(%D*)(%d+)%s*(.*)$", function(dir, n, unit)
  return (dir ~= "" and (dir .. " ") or "") .. n .. (unit ~= "" and (" " .. unit) or "")
end)

-- Only background runs (no arguments) update the status line: "Pune: ☀️ 25°C".
if line == "" then status(place .. ": " .. icon .. " " .. temp) end
-- The place is the title; under it, the temperature big, with the sky under it, then humidity,
-- wind and the UV index, smaller.
log(place, fmt.stats({
  { temp, icon .. " " .. sky },
  { humidity, "humidity" },
  { wind, "wind" },
  { uv, "UV" },
}))
