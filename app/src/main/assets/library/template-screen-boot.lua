-- @title Template: boot screen
-- @desc A starting point for a short full-screen screen: a startup sequence typed out with facts from your phone, as screen boot shows it
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage
-- @uses screen, apps
-- @tags template, screen, boot
-- @requires 0.5.17
--
-- Kolahal's `screen boot`, written as a script, to start a screen of your own from. Run
-- `template-screen-boot`: a startup sequence in the style of a Linux boot log is typed out line by
-- line, each character passing through a few others before it settles, with an OK before each line.
-- It takes 1.2 seconds, and the screen closes by itself. To make it yours, rename it first:
-- `script mv template-screen-boot myboot`.
--
-- The lines are made before tty.screen: the code before it has the 60 s of any run, and each call of
-- a screen's functions only 1 s, so what a screen needs is read first. Then each character gets the moment it appears and the moment it settles, and
-- each frame draws the lines as they are at its time t.
--
-- What differs from the built-in, and why: a script reads only what the script API gives it.
-- - The built-in names Kolahal's version and Android's, the theme, the font and how many scripts
--   you have. A script can't read those, so this one reads the battery and the network.
-- - Its app count (`@uses apps`) is of the apps a script may list: yours, and a work profile's, which
--   it also counts apart. A private space's apps are never listed to a script.

-- It takes this long to type, in ms, and stays this much longer.
local TYPE_MS, HOLD_MS = 900, 300

-- A character settles this long after it appears, and up to SETTLE_MORE more.
local SETTLE, SETTLE_MORE = 80, 120

-- What a character passes through: letters, digits, signs and half-width katakana, one cell each.
local SIGNS = {}
for _, cp in utf8.codes("abcdefghijklmnopqrstuvwxyz0123456789$=/|{}[]<>*#%+-ｱｳｶｸｻｼﾀﾂﾅﾆﾊﾐﾑﾓﾗﾘﾜ") do
  SIGNS[#SIGNS + 1] = utf8.char(cp)
end

local HEADER = { color = "text", bold = true }
local OK = { color = "good", bold = true }

-- The `[  OK  ]` before each line, and the column its text starts at.
local TAG, TEXT_COL = "[  OK  ]", 9

local NETWORK = {
  wifi = "Wi-Fi", cell = "mobile data", eth = "Ethernet", vpn = "a VPN", net = "a network", offline = "no network",
}

-- A number from Kolahal may come as 41.0: math.floor makes it a whole number, which .. writes as 41.
local function count(n, noun)
  n = math.floor(n)
  return n == 1 and "1 " .. noun or n .. " " .. noun .. "s"
end

-- The sequence: a first line, then a line for each fact that is known. A count of 0 is left out.
local lines = { "Starting Kolahal" }
local installed = apps.list()
if #installed > 0 then
  local other = 0
  for _, app in ipairs(installed) do
    if app.profile ~= "personal" then other = other + 1 end
  end
  lines[#lines + 1] = "Loaded " .. count(#installed, "app") .. (other > 0 and " (" .. other .. " in another profile)" or "")
end
local battery = device.battery()
if battery.level then
  lines[#lines + 1] = "Checked the battery: " .. math.floor(battery.level) .. "%" .. (battery.charging and ", charging" or "")
end
local network = device.network()
lines[#lines + 1] = "Found " .. (NETWORK[network] or network)
local unlocks = device.unlocks()
if unlocks > 0 then lines[#lines + 1] = "Counted " .. count(unlocks, "unlock") .. " today" end
lines[#lines + 1] = "Reached the prompt"

-- A line's characters, each with when it appears and when it settles, in ms from the start. By
-- characters, not bytes: utf8.codes goes through a text one character at a time.
local function characters(text, at)
  local list = {}
  for _, cp in utf8.codes(text) do
    local appears = at(#list + 1)
    list[#list + 1] = { char = utf8.char(cp), appears = appears, settles = appears + SETTLE + math.random(0, SETTLE_MORE - 1) }
  end
  return list
end

-- The schedule: the characters come one after another, all of them over TYPE_MS. A line's tag
-- comes once all of the line is there.
local total = 0
for _, text in ipairs(lines) do total = total + utf8.len(text) end
local typed = 0
local schedule = {}
for i, text in ipairs(lines) do
  local from = typed
  local line = { text = characters(text, function(n) return TYPE_MS * (from + n) // total end) }
  line.starts = line.text[1].appears
  typed = typed + utf8.len(text)
  if i > 1 then
    local done = TYPE_MS * typed // total
    line.tag = characters(TAG, function() return done end)
  end
  schedule[i] = line
end

-- The characters as they are at time t: nothing before one appears, then a sign that changes
-- each frame, then the real one. A space stays a space.
local function shown(list, t)
  local out = {}
  for i, c in ipairs(list) do
    if t < c.appears then
      out[i] = " "
    elseif t < c.settles and c.char ~= " " then
      out[i] = SIGNS[math.random(#SIGNS)]
    else
      out[i] = c.char
    end
  end
  return out
end

tty.screen{
  title = "Startup sequence",
  -- 30 frames a second, the most a script's screen gets (the built-in draws 60).
  fps = 30,
  -- A few short lines: each frame clears the grid and draws all of them, which is quick.
  frame = function(g, t)
    g.clear()
    -- A short screen shows the last lines, as a boot log scrolls.
    local started = 0
    for _, line in ipairs(schedule) do
      if line.starts <= t then started = started + 1 end
    end
    local first = math.max(0, started - g.rows)
    for i, line in ipairs(schedule) do
      local row = i - 1 - first
      if row >= 0 and row < g.rows then
        if line.tag then
          local tag = shown(line.tag, t)
          g.put(0, row, tag[1])
          g.put(1, row, table.concat(tag, "", 2, 7), OK)
          g.put(7, row, tag[8])
          g.put(TEXT_COL, row, table.concat(shown(line.text, t)))
        else
          g.put(0, row, table.concat(shown(line.text, t)), HEADER)
        end
      end
    end
    -- Returning false closes the screen: the sequence ends by itself.
    return t < TYPE_MS + HOLD_MS
  end,
}
