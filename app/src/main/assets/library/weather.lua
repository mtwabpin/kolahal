-- weather.lua — current weather in one line from wttr.in (no API key).
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
local url = "https://wttr.in/" .. urlencode(city) .. "?format=3"

local ok, r = pcall(http.get, url, { ["Accept-Language"] = "en" })
if not ok then return fail("No weather", "Network error: " .. tostring(r)) end
if not r.ok then return fail("No weather", "wttr.in answered HTTP " .. tostring(r.status)) end

local text = trim(r.body or "")
if text == "" or text:find("Unknown location", 1, true) then
  return fail("No weather", "wttr.in doesn't know '" .. city .. "'")
end

-- Only background runs (no arguments) update the status line.
if line == "" then status(text) end
-- "Pune: ⛅️ +31°C": the place is the title, the weather under it.
local place, now = text:match("^(.-):%s*(.+)$")
log(place or text, now and (now:gsub("%s+", " ")))
