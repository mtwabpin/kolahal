-- @title Top
-- @desc Live facts over the whole screen: the battery, the network and today's screen time, as top shows them, written as a script
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage
-- @uses screen, usage
-- @tags screen, top, battery, usage
-- @requires 0.5.17
--
-- Kolahal's `top`, written as a script, to read as an example of a screen. Run `tty-top`; a tap,
-- a key or Back closes it.
--
-- In the style of the Unix top, it shows facts about the phone and draws them again each second:
-- a first line with the time and today's unlocks, meters with bars, and a table under an inverse
-- header, the biggest first.
--
-- What differs from the built-in, and why: a script reads only what the script API gives it.
-- - The built-in shows Kolahal's memory, the phone's, and how long Kolahal has run. A script can't
--   read them, so this one has the network instead.
-- - The built-in's table lists your scripts and when each ran. A script can't read that either, so
--   this table lists the apps you used most today, with their time (`@uses usage`).
-- - Screen time needs usage access for Kolahal (type allow usage) and this script's own "Your app
--   usage" switch. Without them the screen says so, and shows the rest.

-- Cells for a meter's label ("Battery", "Network"…) and the space after it.
local LABEL = 9

-- A meter's bar is at most this wide, and is left out when narrower than the least: then the
-- numbers keep their room on a small phone.
local MOST_BAR, LEAST_BAR = 40, 6

-- Today's screen time is read again after this many seconds.
local USAGE_EVERY = 60

local NETWORK = {
  wifi = "Wi-Fi", cell = "mobile data", eth = "Ethernet", vpn = "VPN", net = "connected", offline = "offline",
}

local BOLD = { bold = true }
local HEAD = { bold = true, inverse = true }

-- Each call of a screen's functions has 1 second, and the code before tty.screen the 60 s of any
-- run. Reading today's screen time can take a while on a phone used a lot, so it is read here
-- first, and in a frame only once a minute. usage.today is nil without usage access.
local today, readAt = usage.today(30), os.time()
if not today then
  log("Screen time needs usage access", "Run allow usage, and turn on \"Your app usage\" on this script's sheet")
end

-- The second on the screen, so a frame in the same second draws nothing.
local shown

-- Minutes as people say them: "4h 12m", or "38m". A number from Kolahal may come as 38.0:
-- math.floor makes it a whole number, which .. writes as 38.
local function duration(minutes)
  minutes = math.floor(minutes)
  if minutes >= 60 then return string.format("%dh %dm", minutes // 60, minutes % 60) end
  return minutes .. "m"
end

local function count(n, noun)
  n = math.floor(n)
  return n == 1 and "1 " .. noun or n .. " " .. noun .. "s"
end

-- `top - 14:03:12, 41 unlocks`: Unix top's first line, with what a script knows.
local function header()
  return "top - " .. os.date("%H:%M:%S") .. ", " .. count(device.unlocks(), "unlock")
end

-- A label and a fact, on `row`.
local function fact(g, row, label, text)
  if row >= g.rows then return end
  g.put(0, row, label, BOLD)
  g.put(LABEL, row, text)
end

-- One meter on `row`: its label, a bar filled to `value` (0 to 1; nil leaves it empty) between two
-- thin ends, ▕████▌   ▏, then `text`. g.bar draws the fill in eighths of a cell.
local function meter(g, row, label, value, text, color)
  if row >= g.rows then return end
  g.put(0, row, label, BOLD)
  local col = LABEL
  local bar = math.min(MOST_BAR, g.cols - LABEL - 22)
  if bar >= LEAST_BAR then
    g.put(col, row, "▕", "muted")
    if value then g.bar(col + 1, row, bar - 2, value, color) end
    g.put(col + bar - 1, row, "▏", "muted")
    col = col + bar + 1
  end
  g.put(col, row, text)
end

local function battery(g, row)
  local b = device.battery()
  local level = b.level and math.floor(b.level)
  local text, color
  if not level then
    text = "not known"
  elseif b.charging then
    text, color = level >= 100 and "100% full" or level .. "% charging", "good"
  else
    text = level .. "% on battery"
    color = level <= 15 and "bad" or level <= 30 and "warn" or "accent"
  end
  meter(g, row, "Battery", level and level / 100, text, color)
end

-- The bar is the part of the day so far that the screen was on.
local function screenTime(g, row)
  if not today then
    fact(g, row, "Today", "needs usage access")
    return
  end
  local t = os.date("*t")
  local sinceMidnight = t.hour * 60 + t.min + 1
  meter(g, row, "Today", math.min(1, today.total / sinceMidnight), duration(today.total) .. " on screen", "info")
end

-- `text` cut to `n` characters, with "…" at the end when it was longer. By characters, not by bytes
-- (# counts bytes): utf8.offset finds where the character after the first n - 1 starts.
local function cut(text, n)
  if n <= 0 then return "" end
  if utf8.len(text) <= n then return text end
  return text:sub(1, utf8.offset(text, n) - 1) .. "…"
end

-- `text` with its last character at column `last`.
local function right(g, last, row, text, style)
  g.put(last - utf8.len(text) + 1, row, text, style)
end

-- The apps used most today under an inverse header, as top lists its processes: as many as fit.
-- A grid under 30 columns leaves the SHARE column out.
local function mostUsed(g, top)
  if top >= g.rows then return end
  local cols = g.cols
  local timeEnd = cols - 1
  local share = cols >= 30
  if share then timeEnd = cols - 8 end
  g.fill(0, top, cols, 1, nil, HEAD)
  g.put(1, top, "APP", HEAD)
  right(g, timeEnd, top, "TIME", HEAD)
  if share then right(g, cols - 1, top, "SHARE", HEAD) end
  if not today then
    if top + 1 < g.rows then g.put(1, top + 1, cut("Run allow usage, and turn on \"Your app usage\" for this script", cols - 1), "muted") end
    return
  end
  if #today.apps == 0 and top + 1 < g.rows then g.put(1, top + 1, "No app used a minute yet today", "muted") end
  for i, a in ipairs(today.apps) do
    local row = top + i
    if row >= g.rows then break end
    -- The name first, then the numbers over its end: a script can't tell how many cells a
    -- character takes (a Chinese one takes two), so a name of wide characters could run into TIME.
    g.put(1, row, cut(a.app, timeEnd - 8))
    g.fill(timeEnd - 7, row, cols - timeEnd + 7, 1)
    right(g, timeEnd, row, duration(a.minutes), "muted")
    if share then
      local percent = today.total > 0 and math.floor(a.minutes * 100 / today.total + 0.5) or 0
      right(g, cols - 1, row, percent .. "%", "muted")
    end
  end
end

local function draw(g)
  g.clear()
  g.put(0, 0, header())
  battery(g, 1)
  local network = device.network()
  fact(g, 2, "Network", NETWORK[network] or network)
  screenTime(g, 3)
  mostUsed(g, 5)
end

-- Draws again when the second changed. 2 frames a second, so the clock changes close to each second.
local function tick(g)
  local now = os.time()
  if now == shown then return end
  shown = now
  if now - readAt >= USAGE_EVERY then
    today, readAt = usage.today(30), now
  end
  draw(g)
end

tty.screen{
  title = "Top: battery, network and screen time",
  fps = 2,
  start = tick,
  resize = function(g)
    shown = nil
    tick(g)
  end,
  frame = tick,
}
