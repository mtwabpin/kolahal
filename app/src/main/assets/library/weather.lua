-- weather.lua — the current weather from wttr.in (no API key): temperature, sky, humidity, wind, UV.
-- @command
-- @desc Current weather: weather [city] (default: its city option, or your location)
-- @usage [<city>]
-- @title Weather
-- @author Kolahal
-- @version 1.2
-- @requires 0.5.17
-- @widget stat 2x1
-- @gives rain, snow
-- @every 1h
-- @option city "" Default city; empty: your IP address's location
--
-- Usage:
--   weather                 weather for its city option (or your IP location)
--   weather Paris           weather for Paris
--
-- On a timer (no arguments) it sets the result as its status line; pin it
-- (script pin weather) to see it at the top of home. It also fills its widget, once you
-- placed it on the widgets page: the temperature, the sky, humidity and wind. And it gives
-- shaders how hard it rains and snows: a shader's parameter can follow them. A second or later
-- widget can have a city of its own: its runs fill that widget only, and leave the status line and
-- the values for shaders to the script's own city.

local function urlencode(s)
  return (s:gsub("[^%w%-_%.~]", function(c)
    return string.format("%%%02X", string.byte(c))
  end))
end

local function trim(s)
  return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

-- The widgets page asked for this run: it fills the widget and logs nothing.
local quiet = event and event.name == "widget"

-- A failure: what didn't work, in the theme's bad colour, and why under it. In a run for the
-- widget it is an error instead, so the run counts as failed.
local function fail(what, why)
  if quiet then error(what .. ": " .. why, 0) end
  log(fmt.color("bad", what), why)
end

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

-- The widget's icon for wttr.in's sky symbol: a name the card draws in its colour. Nil for a
-- symbol it doesn't know: the card then has the title's first letter.
local function skyIcon(symbol)
  local function has(s) return symbol:find(s, 1, true) ~= nil end
  if has("⛈") or has("🌩") then return "storm" end
  if has("🌨") or has("❄") then return "snow" end
  if has("🌧") or has("🌦") then return "rain" end
  if has("🌫") then return "fog" end
  if has("☁") or has("⛅") then return "cloud" end
  if has("☀") then return "sun" end
  return nil
end

-- How hard it rains or snows by wttr.in's words ("Light rain shower", "Heavy snow"), 0 to 1.
local function force(words, kinds)
  words = words:lower()
  local falls = false
  for _, kind in ipairs(kinds) do falls = falls or words:find(kind, 1, true) ~= nil end
  if not falls then return 0 end
  if words:find("heavy", 1, true) or words:find("torrential", 1, true) or words:find("blizzard", 1, true) then return 1 end
  if words:find("moderate", 1, true) or words:find("thunder", 1, true) then return 0.6 end
  if words:find("light", 1, true) or words:find("drizzle", 1, true) or words:find("patchy", 1, true) then return 0.25 end
  return 0.5
end

-- A run for a second or later widget (event.instance is "2", "3"…) is for that widget's own city.
local further = event and event.instance

if line == "" and not further then
  shader.give("rain", force(sky, { "rain", "drizzle", "shower", "thunder" }))
  shader.give("snow", force(sky, { "snow", "sleet", "blizzard", "ice pellets" }))
end

-- Only background runs (no arguments) of the script's own city update the status line: "Pune: ☀️ 25°C".
if line == "" and not further then status(place .. ": " .. icon .. " " .. temp) end
-- Its widget, in the same runs: the temperature big, the sky under it, humidity and wind smaller.
-- A tap runs weather again and turns home to the log, for the full report.
if widget and line == "" then
  widget.show{
    title = place, icon = skyIcon(icon), value = temp, label = sky,
    more = { { value = humidity, label = "humidity" }, { value = wind, label = "wind" } },
    tap = { args = "", page = "log" },
    -- It runs every hour: content counts as old only after two, so the card shows no age between runs.
    staleAfter = 2 * 3600,
  }
end
if quiet then return end
-- The place is the title; under it, the temperature big, with the sky under it, then humidity,
-- wind and the UV index, smaller.
log(place, fmt.stats({
  { temp, icon .. " " .. sky },
  { humidity, "humidity" },
  { wind, "wind" },
  { uv, "UV" },
}))
