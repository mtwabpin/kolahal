-- @title Decode
-- @desc Text that scrambles and settles in waves over the whole screen, as decode does, written as a script
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage [<text>]
-- @uses screen
-- @tags screen, decode, text
-- @requires 0.5.17
--
-- Kolahal's `decode`, written as a script, to read as an example of a screen. Run `tty-decode`, or
-- `tty-decode` and some words of your own; a tap, a key or Back closes it.
--
-- Its lines scramble and settle again: each character passes through random ones and settles on
-- the real one, from the left. First all the lines, from the top down; then, after a short rest, a
-- wave of another kind, never the same kind twice in a row, for as long as it is open:
-- - a band of a few lines that sweeps down,
-- - from the bottom up,
-- - a few lines here and there,
-- - column by column, from the left or from the right.
--
-- Each character has the time it starts to scramble and the time it settles. A frame draws again
-- only the lines that are scrambling, each as one text: one call for a whole line is far quicker
-- than one for each cell.
--
-- What differs from the built-in, and why:
-- - The built-in decodes home's own log lines, in their places. A script can't read the log, so
--   this one decodes its own lines, or the words you give it, at the bottom of the screen where
--   the log would be.
-- - Only the letters, digits and signs of plain ASCII scramble. A script can't tell how many cells
--   another character takes (a Chinese one takes two), so the others stay as they are, and nothing
--   on a line moves. A text in a right-to-left script shows its letters from the left, unjoined.

-- The lines it decodes without words of yours.
local TEXT = {
  "Each character scrambles, then settles on the real one.",
  "A wave takes some of the lines at a time, so the rest can be read:",
  "all of them from the top down, a band that sweeps down,",
  "from the bottom up, a few lines here and there,",
  "or column by column, from the left or from the right.",
  "Type tty-decode and some words to decode your own.",
}

-- What a character passes through: letters, digits, signs and half-width katakana, one cell each.
local SIGNS = {}
for _, cp in utf8.codes("abcdefghijklmnopqrstuvwxyz0123456789$=/|{}[]<>*#%+-ｱｳｶｸｻｼﾀﾂﾅﾆﾊﾐﾑﾓﾗﾘﾜ") do
  SIGNS[#SIGNS + 1] = utf8.char(cp)
end

-- The text stays as it is this long before the first wave, and rests this long and up to
-- REST_MORE longer between waves, in ms.
local FIRST, REST, REST_MORE = 400, 200, 500

-- After the first wave, which takes all the lines, one of these, never the last one again.
local WAVES = { "band", "up", "scattered", "columns" }

-- The lines on the screen: each has its characters, its row, and while it scrambles, when each
-- character starts and settles, when the last one settles (ends) and its tint.
local lines = {}

-- The time of the last frame, whether a wave runs, when the next one starts, and the last kind.
local now, running, nextWave, last = 0, false, FIRST, nil

-- `text` in lines of at most `width` characters, broken between words. A word longer than that
-- stays whole, and the grid cuts it at the edge.
local function wrap(text, width, out)
  local line = ""
  for word in text:gmatch("%S+") do
    if line == "" then
      line = word
    elseif utf8.len(line) + 1 + utf8.len(word) <= width then
      line = line .. " " .. word
    else
      out[#out + 1] = line
      line = word
    end
  end
  if line ~= "" then out[#out + 1] = line end
end

-- Whether a character scrambles: a printable character of plain ASCII, not a space.
local function scrambles(c)
  local b = c:byte()
  return #c == 1 and b > 32 and b < 127
end

-- The lines for the grid's size, at the bottom, the last on the row above the last one. If there are
-- more than fit, the last ones. Then the screen shows them as they are.
local function lay(g)
  local texts = {}
  for _, text in ipairs(argline ~= "" and { argline } or TEXT) do wrap(text, g.cols, texts) end
  local from = math.max(1, #texts - (g.rows - 1) + 1)
  local top = math.max(0, g.rows - 1 - (#texts - from + 1))
  lines = {}
  for i = from, #texts do
    local chars = {}
    for _, cp in utf8.codes(texts[i]) do chars[#chars + 1] = utf8.char(cp) end
    local line = { chars = chars, row = top + #lines }
    lines[#lines + 1] = line
    g.put(0, line.row, texts[i])
  end
  running, nextWave = false, now + FIRST
end

-- Line `line` decodes from `at` ms: its characters all scramble at once, then settle from its first
-- to its last, each a little early or late. A longer line takes longer, 660 ms at most.
local function decodeLine(line, at)
  local n = #line.chars
  local sweep = math.min(660, 280 + n * 9)
  line.starts, line.settles, line.ends = {}, {}, at
  for i = 1, n do
    line.starts[i] = at + math.random(0, 59)
    line.settles[i] = at + 120 + (i - 1) * sweep // n + math.random(0, 119)
    line.ends = math.max(line.ends, line.settles[i])
  end
  -- One line in five flickers in the warning or the accent color while it scrambles.
  line.tint = math.random(5) == 1 and (math.random(2) == 1 and "warn" or "accent") or nil
end

-- The time between one line (or column) and the next: `most` ms, less when all `n` would take
-- more than `inAll`.
local function gap(most, inAll, n)
  return math.min(most, inAll // math.max(1, n))
end

-- Starts the next wave at time t.
local function wave(cols, t)
  local kind = "all"
  if last then
    local others = {}
    for _, w in ipairs(WAVES) do if w ~= last then others[#others + 1] = w end end
    kind = others[math.random(#others)]
  end
  last = kind
  local n = #lines
  for _, line in ipairs(lines) do line.ends = nil end
  if kind == "all" then
    for r, line in ipairs(lines) do decodeLine(line, t + (r - 1) * gap(40, 1600, n) + math.random(0, 79)) end
  elseif kind == "band" then
    for r, line in ipairs(lines) do decodeLine(line, t + (r - 1) * gap(110, 4200, n)) end
  elseif kind == "up" then
    for r, line in ipairs(lines) do decodeLine(line, t + (n - r) * gap(80, 3000, n)) end
  elseif kind == "scattered" then
    -- About a third of the lines, and at least one.
    local one = math.random(n)
    for r, line in ipairs(lines) do
      if r == one or math.random(3) == 1 then decodeLine(line, t + math.random(0, 2999)) end
    end
  else
    -- Each character on its own, by its column: they settle at once, not from the left.
    local step = gap(45, 2600, cols)
    local fromRight = math.random(2) == 1
    for _, line in ipairs(lines) do
      line.starts, line.settles, line.ends, line.tint = {}, {}, t, nil
      for i = 1, #line.chars do
        local col = fromRight and cols - i or i - 1
        line.starts[i] = t + col * step + math.random(0, 199)
        line.settles[i] = line.starts[i] + 220 + math.random(0, 259)
        line.ends = math.max(line.ends, line.settles[i])
      end
    end
  end
  running = true
end

-- Draws a line as it is at time t, in one call. A line whose wave is over is drawn once more, as
-- it is, and then left alone.
local function draw(g, line, t)
  local out, busy = {}, false
  for i, c in ipairs(line.chars) do
    if t >= line.starts[i] and t < line.settles[i] and scrambles(c) then
      out[i] = SIGNS[math.random(#SIGNS)]
      busy = true
    else
      out[i] = c
    end
  end
  g.put(0, line.row, table.concat(out), busy and line.tint or nil)
  if t >= line.ends then line.ends = nil end
end

tty.screen{
  title = "Decoding text",
  -- 24 frames a second, as the built-in. Between waves a frame draws nothing.
  fps = 24,
  start = lay,
  -- A new size: the lines are laid out again for it, and the next wave waits FIRST ms.
  resize = lay,
  frame = function(g, t)
    now = t
    if not running then
      if t < nextWave or #lines == 0 then return end
      wave(g.cols, t)
    end
    running = false
    for _, line in ipairs(lines) do
      if line.ends then
        draw(g, line, t)
        running = running or line.ends ~= nil
      end
    end
    if not running then nextWave = t + REST + math.random(0, REST_MORE - 1) end
  end,
}
