-- @title Clock
-- @desc The time in big digits over the whole screen, as clock shows it, written as a script
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage
-- @uses screen
-- @tags screen, clock, time
-- @requires 0.5.17
-- @option hours 12|24 12-hour time with AM and PM, or 24-hour time
--
-- Kolahal's `clock`, written as a script, to read as an example of a screen. Run `tty-clock`; a
-- tap, a key or Back closes it.
--
-- The digits are made of block cells, as big as the grid allows, with the date under them. The
-- seconds show when they fit; without them the clock changes once a minute.
--
-- A frame comes 4 times a second, but the clock draws only when the second (or the minute) it
-- shows has changed. A frame that draws nothing costs next to nothing.
--
-- What differs from the built-in:
-- - It reads 12- or 24-hour time from its hours option (`script set tty-clock hours 24`): a script
--   can't read Kolahal's clock.24h setting.
-- - The date is in English: os.date doesn't know the phone's language.

-- Each digit is 3 × 5 pixels, row by row: # is a pixel. FONT[0] is the digit 0.
local FONT = {
  [0] = "#### ## ## ####",
  "  #  #  #  #  #",
  "###  #####  ###",
  "###  ####  ####",
  "# ## ####  #  #",
  "####  ###  ####",
  "####  #### ####",
  "###  #  #  #  #",
  "#### ##### ####",
  "#### ####  ####",
}

local H24 = options.hours == "24"

-- The layout for the grid's size: whether the seconds show, and how many cells across (w) and
-- down (h) a pixel of a digit takes.
local seconds, w, h = false, 1, 1

-- The second (or minute) on the screen, so a frame in the same one draws nothing.
local shown

-- Cells across "12:34:56" or "12:34", with pixels w cells wide: each digit is 3 pixels, a colon 1,
-- and a pixel's space between them.
local function width(withSeconds, pw)
  return (withSeconds and 27 or 17) * pw
end

-- The biggest digits that fit, with the seconds if they fit too: 5 rows of pixels, and 2 rows for
-- the date. A pixel is twice as wide as it is tall, as a cell is about twice as tall as it is wide,
-- so a pixel comes out about square. On a tablet's 200 columns that is 6 cells by 3.
local function layout(g)
  for _, withSeconds in ipairs({ true, false }) do
    for ph = 3, 1, -1 do
      if width(withSeconds, 2 * ph) <= g.cols and 5 * ph + 2 <= g.rows then
        seconds, w, h = withSeconds, 2 * ph, ph
        return
      end
    end
  end
  -- A very small grid: pixels of one cell.
  seconds, w, h = width(true, 1) <= g.cols and 7 <= g.rows, 1, 1
end

-- Draws digit d (nothing for nil) with its left edge at column x; returns where the next part starts.
local function digit(g, d, x, top)
  if d then
    local f = FONT[d]
    for r = 0, 4 do
      for c = 0, 2 do
        local i = r * 3 + c + 1
        if f:sub(i, i) == "#" then g.fill(x + c * w, top + r * h, w, h, "█", "accent") end
      end
    end
  end
  return x + 4 * w
end

local function colon(g, x, top)
  g.fill(x, top + h, w, h, "█", "accent")
  g.fill(x, top + 3 * h, w, h, "█", "accent")
  return x + 2 * w
end

local function draw(g, now)
  local t = os.date("*t", now)
  local hour = t.hour
  local half = hour >= 12 and "PM" or "AM"
  if not H24 then
    hour = hour % 12
    if hour == 0 then hour = 12 end
  end
  g.clear()
  local big = width(seconds, w)
  local left = math.max(0, (g.cols - big) // 2)
  local top = math.max(0, (g.rows - 5 * h - 2) // 2)
  local x = left
  -- In 12-hour time, 9:05 keeps the place of a first digit, so the clock doesn't move at 10.
  x = digit(g, (H24 or hour >= 10) and hour // 10 or nil, x, top)
  x = digit(g, hour % 10, x, top)
  x = colon(g, x, top)
  x = digit(g, t.min // 10, x, top)
  x = digit(g, t.min % 10, x, top)
  if seconds then
    x = colon(g, x, top)
    x = digit(g, t.sec // 10, x, top)
    digit(g, t.sec % 10, x, top)
  end
  -- AM or PM beside the digits when there is room, else before the date.
  local beside = not H24 and left + big + 3 <= g.cols
  if beside then g.put(left + big + 1, top, half, "muted") end
  local date = os.date("%A ", now) .. t.day .. os.date(" %B %Y", now)
  if #date > g.cols then date = os.date("%a ", now) .. t.day .. os.date(" %b %Y", now) end
  if not H24 and not beside then date = half .. " · " .. date end
  -- The date is centered by its length in characters: utf8.len, as # counts bytes ("·" is 2).
  g.put(math.max(0, (g.cols - utf8.len(date)) // 2), top + 5 * h + 1, date, "muted")
end

-- Draws the time when it changed since the last call.
local function tick(g)
  local now = os.time()
  local step = now // (seconds and 1 or 60)
  if step == shown then return end
  shown = step
  draw(g, now)
end

-- At the start and after a change of size: a new layout, drawn at once.
local function lay(g)
  layout(g)
  shown = nil
  tick(g)
end

tty.screen{
  title = "Clock",
  fps = 4,
  start = lay,
  resize = lay,
  frame = tick,
}
