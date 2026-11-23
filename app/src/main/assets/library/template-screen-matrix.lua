-- @title Template: matrix screen
-- @desc A starting point for a full-screen screen: streams of characters fall down it, as matrix draws them
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage
-- @uses screen
-- @tags template, screen, matrix
-- @requires 0.5.17
--
-- Kolahal's `matrix`, written as a script, to start a screen of your own from. Run
-- `template-screen-matrix`; a tap, a key or Back closes it. To make it yours, rename it first, so a
-- library update doesn't replace your changes: `script mv template-screen-matrix rain`.
--
-- How a screen works: the code runs to its end, and its last call, tty.screen{…}, opens the screen.
-- Kolahal then calls the functions it was given: start once, frame for each frame, resize when the
-- grid changes size (the phone turned, the font size changed). Each of them gets g, the grid of
-- character cells: g.cols × g.rows of them, counted from 0 at the top left. g.put(col, row, text,
-- style) writes from a cell, and a cell keeps what was written until something else is written over
-- it. So a frame needs to change only the cells that move.
--
-- Each stream falls down its own column, at its own speed: a bright head, the accent color behind
-- it, fainter toward its tail, which is wiped as it goes. Streams use every other column, so the
-- rain doesn't crowd. Now and then a character in a stream changes.

-- Half-width katakana, digits and a few signs: each takes one cell.
local GLYPHS = {}
for cp = 0xFF66, 0xFF9D do GLYPHS[#GLYPHS + 1] = utf8.char(cp) end
for c in ("0123456789Z:.=*+-<>|"):gmatch(".") do GLYPHS[#GLYPHS + 1] = c end

-- A style is a color by its role, or a table with the color and more. The theme picks the real
-- colors, so the rain reads in a light theme too.
local HEAD = { color = "text", bold = true }
local NEAR = { color = "accent", bold = true }
local BODY = "accent"
local FADED = { color = "accent", faint = true }

local streams, rows = {}, 0

local function glyph() return GLYPHS[math.random(#GLYPHS)] end

-- The streams for the grid's size: one for every other column. Each waits up to `most` ms before
-- it starts. A stream keeps the characters it shows, by row, so it can draw them again in another
-- style: a script can't read a cell back from the grid.
local function lay(g)
  rows, streams = g.rows, {}
  for col = 0, g.cols - 1, 2 do
    streams[#streams + 1] = { col = col, wait = math.random(0, 2000), chars = {} }
  end
end

-- A new stream: it enters at the top on its first step, and moves a row every `period` ms.
local function begin(s)
  s.head = -1
  s.length = math.random(4, math.max(5, rows * 3 // 4))
  s.fade = s.length * 2 // 3
  s.period = math.random(35, 119)
  s.elapsed = s.period
end

-- The look of a character `d` rows above its stream's head.
local function look(s, d)
  if d == 0 then return HEAD end
  if d == 1 then return NEAR end
  if d < s.fade then return BODY end
  return FADED
end

-- Draws the character `d` rows above the head again, in its look now. Rows off the grid have none.
local function restyle(g, s, d)
  local row = s.head - d
  local c = s.chars[row]
  if c then g.put(s.col, row, c, look(s, d)) end
end

-- Moves a stream down a row: a new head, the three cells whose look changes, and the tail wiped.
local function step(g, s)
  s.head = s.head + 1
  local h = s.head
  restyle(g, s, 1)
  restyle(g, s, 2)
  restyle(g, s, s.fade)
  if h < rows then
    s.chars[h] = glyph()
    g.put(s.col, h, s.chars[h], HEAD)
  end
  local tail = h - s.length
  if s.chars[tail] then
    s.chars[tail] = nil
    g.put(s.col, tail, " ")
  end
  -- Its tail has wiped the last row: the column waits for its next stream.
  if tail >= rows - 1 then
    s.head = nil
    s.wait = math.random(0, 1500)
  end
end

-- A few characters in the streams change, as in the film: about one a frame for every 150 cells.
local function glitch(g)
  for _ = 1, #streams * rows // 150 + 1 do
    local s = streams[math.random(#streams)]
    local row = math.random(0, rows - 1)
    if s.chars[row] then
      s.chars[row] = glyph()
      g.put(s.col, row, s.chars[row], look(s, s.head - row))
    end
  end
end

tty.screen{
  title = "Matrix rain",
  -- Frames a second: 30 is the most a script's screen gets. Kolahal gives fewer in battery saver,
  -- and to a script whose frames come back late.
  fps = 30,
  start = lay,
  -- The grid is blank by now: the streams start over.
  resize = lay,
  -- t is the time since the screen opened, dt the time since the last frame, both in ms. The
  -- streams move by dt, not by frames, so they fall at the same speed however many frames come.
  -- After a pause (Kolahal was behind another app) dt is long: it counts as 200 ms at most, so the
  -- rain goes on where it was.
  --
  -- A frame has 50 ms: one that takes longer, 30 times in a row, closes the screen with an error.
  -- This one draws only the cells that change, a few for each stream. That is a few hundred calls
  -- on a tablet's 200 columns, well inside the limit.
  frame = function(g, t, dt)
    dt = math.min(dt, 200)
    for _, s in ipairs(streams) do
      if not s.head then
        s.wait = s.wait - dt
        if s.wait <= 0 then begin(s) end
      end
      if s.head then
        s.elapsed = s.elapsed + dt
        while s.head and s.elapsed >= s.period do
          s.elapsed = s.elapsed - s.period
          step(g, s)
        end
      end
    end
    glitch(g)
  end,
  -- It has no key or tap function, so a key or a tap closes it, as it closes the built-in one. With
  -- key = function(k) … end it would get the keys of a hardware keyboard instead ("a", "up",
  -- "space"…), and with tap = function(col, row) … end the taps. Returning false from either
  -- closes the screen.
}
