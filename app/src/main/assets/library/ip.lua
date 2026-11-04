-- ip.lua — your public IP address (api.ipify.org, no API key).
-- @command
-- @desc Public IP address; long-press the line to copy it
-- @usage
-- @title Public IP
-- @every 30m
-- @notify
--
-- As a command it prints your IP. On a timer it notifies when the IP changes
-- (handy for spotting VPN drops) and sets it as its status line.

-- A failure: what didn't work, in the theme's bad colour, and why under it.
local function fail(what, why) log(fmt.color("bad", what), why) end

local ok, r = pcall(http.get, "https://api.ipify.org?format=json")
if not ok then return fail("No IP address", "Network error: " .. tostring(r)) end
if not r.ok then return fail("No IP address", "api.ipify.org answered HTTP " .. tostring(r.status)) end

local okJson, data = pcall(json.decode, r.body or "")
if not okJson or type(data) ~= "table" or type(data.ip) ~= "string" then
  return fail("No IP address", "api.ipify.org sent something unexpected")
end

local ip = data.ip
local previous = store.get("last")
store.set("last", ip)

if previous ~= nil and previous ~= ip then
  notify("Public IP changed", previous .. " → " .. ip, { copy = ip })
  log(fmt.color("warn", "IP changed"), previous .. " → " .. fmt.bold(ip), { copy = ip })
else
  log(ip, { copy = ip })
end
status("IP " .. ip)
