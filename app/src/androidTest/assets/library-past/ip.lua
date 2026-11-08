-- ip.lua — your public IP address (api.ipify.org, no API key).
-- @command
-- @desc public IP address; long-press the line to copy it
--
-- As a command it prints your IP. On a timer it notifies when the IP changes
-- (handy for spotting VPN drops) and pins it as a status line.

local ok, r = pcall(http.get, "https://api.ipify.org?format=json")
if not ok then
  log("ip: network error (" .. tostring(r) .. ")")
  return
end
if not r.ok then
  log("ip: HTTP " .. tostring(r.status))
  return
end

local okJson, data = pcall(json.decode, r.body or "")
if not okJson or type(data) ~= "table" or type(data.ip) ~= "string" then
  log("ip: unexpected response")
  return
end

local ip = data.ip
local previous = store.get("last")
store.set("last", ip)

if previous ~= nil and previous ~= ip then
  notify("Public IP changed", previous .. " → " .. ip, { copy = ip })
  log("IP changed: " .. previous .. " → " .. ip, { copy = ip })
else
  log(ip, { copy = ip })
end
status("IP " .. ip)
