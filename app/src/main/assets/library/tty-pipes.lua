-- @title Pipes
-- @desc Pipes grow across the whole screen and turn at random, as pipes draws them, written as a script
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage
-- @uses screen
-- @tags screen, pipes
-- @requires 0.5.17
--
-- Kolahal's `pipes`, written as a script, to read as an example of a screen. Run `tty-pipes`; a
-- tap, a key or Back closes it.
--
-- Two pipes grow a cell at a time. After a few cells straight on, a pipe may turn left or right,
-- never back. One that leaves the screen at an edge comes back at the other, in another color.
-- When the screen is about full, it clears and the pipes go on.
--
-- The grid counts from 0: columns 0 to g.cols - 1 from the left, rows 0 to g.rows - 1 from the top.
-- A cell keeps what was written to it, so each step draws one cell for each pipe and nothing else.
--
-- What differs from the built-in: its colors are the 7 that a script can name. The built-in has
-- other ones too: the color of scripts and those of the profiles.

local PIPES = 2

-- Each pipe grows a cell every 25 ms: 40 cells a second.
local STEP_MS = 25

-- A pipe goes at least this many cells straight on, then turns at each cell with this chance.
local MIN_STRAIGHT = 2
local TURN_PERCENT = 13

local COLORS = { "accent", "info", "link", "good", "warn", "bad", "text" }

-- The directions, 1 to 4: up, right, down, left. A step in each moves by DX across and DY down.
local DX = { 0, 1, 0, -1 }
local DY = { -1, 0, 1, 0 }

-- The piece for a pipe that went `from` and now goes `to`: PIECES[from][to]. Straight on, or the
-- corner that joins the side it came in by to the side it goes out by. Heavy box-drawing lines:
-- Kolahal draws them as lines, so they join at every font size.
local PIECES = {
  { "┃", "┏", nil, "┓" }, -- going up
  { "┛", "━", "┓", nil }, -- going right
  { nil, "┗", "┃", "┛" }, -- going down
  { "┗", nil, "┏", "━" }, -- going left
}

local pipes, cols, rows = {}, 0, 0

-- How long since the last step, and how many cells were drawn since the screen was cleared.
local elapsed, drawn = 0, 0

local function color() return COLORS[math.random(#COLORS)] end

-- The pipes start anywhere, going any way.
local function lay(g)
  cols, rows, elapsed, drawn = g.cols, g.rows, 0, 0
  for i = 1, PIPES do
    pipes[i] = { x = math.random(0, cols - 1), y = math.random(0, rows - 1), dir = math.random(4), color = color(), straight = 0 }
  end
end

-- Draws a pipe's next piece where it is, then moves it on.
local function grow(g, p)
  local from, to = p.dir, p.dir
  if p.straight >= MIN_STRAIGHT and math.random(100) <= TURN_PERCENT then
    -- A quarter turn either way: the next direction, or three on, which is the one before.
    to = (from - 1 + (math.random(2) == 1 and 1 or 3)) % 4 + 1
  end
  p.straight = to == from and p.straight + 1 or 0
  g.put(p.x, p.y, PIECES[from][to], p.color)
  p.dir = to
  p.x, p.y = p.x + DX[to], p.y + DY[to]
  -- Out at one edge, in at the other, in a new color. In Lua, -1 % cols is cols - 1.
  if p.x < 0 or p.x >= cols or p.y < 0 or p.y >= rows then
    p.x, p.y = p.x % cols, p.y % rows
    p.color = color()
  end
end

tty.screen{
  title = "Pipes",
  -- 30 frames a second, the most a script's screen gets. Each frame takes the steps whose time has
  -- come: about one, so the pipes grow at the same speed at any frame rate.
  fps = 30,
  start = lay,
  resize = lay,
  frame = function(g, t, dt)
    -- After a pause dt is long: it counts as 200 ms at most, 8 steps.
    elapsed = elapsed + math.min(dt, 200)
    while elapsed >= STEP_MS do
      elapsed = elapsed - STEP_MS
      for _, p in ipairs(pipes) do grow(g, p) end
      drawn = drawn + PIPES
      if drawn >= cols * rows then
        g.clear()
        drawn = 0
      end
    end
  end,
  -- No key or tap function: a key or a tap closes it, as it closes the built-in one.
}
