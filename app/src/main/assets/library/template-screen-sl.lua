-- @title Template: sl screen
-- @desc A starting point for a full-screen screen that ends by itself: a steam locomotive crosses it, as sl draws it
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage
-- @uses screen
-- @tags template, screen, sl, train
-- @requires 0.5.17
--
-- Kolahal's `sl`, written as a script, to start a screen of your own from. Run
-- `template-screen-sl`: a steam locomotive crosses the screen once, right to left, with smoke from
-- its funnel, and the screen closes by itself. A tap, a key or Back closes it sooner. To make it
-- yours, rename it first: `script mv template-screen-sl train`.
--
-- It draws the whole picture again at each step: it clears the grid, then writes the smoke and the
-- train. The train is one text with line breaks: g.put writes each line under the one before, from
-- the same column. Text past an edge of the grid is cut off, so the train can drive in at the right
-- and out at the left.
--
-- The built-in sl draws the same.

-- The train moves a cell to the left every 45 ms.
local STEP_MS = 45

-- The engine faces left: its funnel, boiler and cab, then the tender. A backslash is written \\
-- in a Lua string.
local TRAIN = table.concat({
  "    ___                                  ",
  "   |___|          _______                ",
  " ___| |__________|  [ ] |   ___________  ",
  "|  ________________     |-|           |  ",
  "|_|________________|____|-|___________|  ",
}, "\n")

-- The row of wheels, in turn, so the rods seem to move.
local WHEELS = {
  " \\(O)==(O)==(O)/   (O)      (O)     (O)  ",
  " \\(O)--(O)--(O)/   (O)      (O)     (O)  ",
  " \\(O)=-(O)=-(O)/   (O)      (O)     (O)  ",
}

local WIDTH, HEIGHT = #WHEELS[1], 6

-- The column of the funnel's middle, from the engine's left edge.
local FUNNEL = 5

-- A puff of smoke, from new to nearly gone. Each look lasts two steps, and a puff rises a row as
-- often. There are at most PUFFS at a time.
local SMOKE = { "(@@)", "(@)", "( )", "()", "o", "." }
local PUFFS = 12

local cols, rows = 0, 0

-- Where the train's left edge and top are, how many steps it has gone, and the time since the last.
local x, top, steps, elapsed = 0, 0, 0, 0

-- Each puff: its column, its row and its age in steps.
local puffs = {}

-- Before it starts, the train waits at the right edge. A little below the middle, so the smoke
-- has room above it.
local function lay(g)
  cols, rows = g.cols, g.rows
  if steps == 0 then x = cols - 1 end
  top = math.max(0, math.min((rows - HEIGHT) // 2 + 1, rows - HEIGHT))
end

local function step()
  steps = steps + 1
  x = x - 1
  -- Each puff gets older and rises, and goes after its last look. From the end of the list, so
  -- that removing one doesn't skip the next.
  for i = #puffs, 1, -1 do
    local p = puffs[i]
    p.age = p.age + 1
    if p.age % 2 == 0 then p.row = p.row - 1 end
    if p.age >= #SMOKE * 2 then table.remove(puffs, i) end
  end
  -- A new puff over the funnel every 4 steps, while the funnel is on the screen.
  local funnel = x + FUNNEL
  if steps % 4 == 0 and funnel >= 0 and funnel < cols and #puffs < PUFFS then
    puffs[#puffs + 1] = { col = funnel - 1, row = top - 1, age = 0 }
  end
end

local function draw(g)
  g.clear()
  for _, p in ipairs(puffs) do
    -- A puff that rose above the top is gone from sight. Writing it would be cut off anyway.
    if p.row >= 0 then g.put(p.col, p.row, SMOKE[p.age // 2 + 1], "muted") end
  end
  -- No style: the theme's text color.
  g.put(x, top, TRAIN)
  g.put(x, top + HEIGHT - 1, WHEELS[steps // 2 % #WHEELS + 1], "accent")
end

tty.screen{
  title = "Steam locomotive",
  fps = 30,
  start = lay,
  -- The train goes on from where it was, at its new height.
  resize = lay,
  frame = function(g, t, dt)
    elapsed = elapsed + math.min(dt, 200)
    local moved = steps == 0
    while elapsed >= STEP_MS do
      elapsed = elapsed - STEP_MS
      step()
      moved = true
    end
    if moved then draw(g) end
    -- Returning false closes the screen: once the train and its smoke are gone, it ends.
    return x + WIDTH > 0 or #puffs > 0
  end,
}
