-- @title Template: screen
-- @desc A starting point for a full-screen screen of your own: a line of text and a block that moves, a tap to change its color
-- @author Kolahal
-- @version 1.0
-- @command
-- @usage
-- @uses screen
-- @tags template, screen
-- @requires 0.5.17
--
-- A starting point for a script that draws a screen of its own over home. Run `template-screen`: a
-- line of text in the middle, and a block that goes back and forth under it. A tap or Space changes
-- the block's color; Q, Back or Home closes the screen. template-screen-js does the same in
-- JavaScript.
--
-- The @uses screen line lets it open a screen. Such a script is always a command: it runs when you
-- start it, never on a timer, an event or a widget.
--
-- How a screen works: the code runs to its end, and its last call, tty.screen{…}, opens the screen.
-- Kolahal then calls the functions it was given. Those that draw get g, the grid of character
-- cells: g.cols × g.rows of them, counted from 0 at the top left. g.put(col, row, text, style)
-- writes from a cell, and a cell keeps what was written until something else is written over it.
-- So a frame needs to draw only the cells that change.
--
-- The limits: each call of a function has 1 second, and a frame that takes over 50 ms, 30 times in
-- a row, closes the screen with an error. The code before tty.screen has the 60 s of any run, so
-- read what the screen needs there.
--
-- To make it yours, copy it under a new name first, so a library update doesn't replace your
-- changes: `script mv template-screen myscreen`. Then change TEXT, and what frame draws. The
-- library's tty-… scripts are longer screens to read: tty-matrix, tty-pipes, tty-sl, tty-clock,
-- tty-top, tty-decode and tty-boot.

local TEXT = "Hello from a screen of your own"
local HELP = "Tap or Space: color   Q: close"

-- A style is a color by its role, or a table with the color and more: { color = "good", bold = true }.
-- The theme picks the real colors, so the screen reads in a light theme too.
local COLORS = { "accent", "good", "warn", "info", "bad" }

-- The block crosses the screen in this many ms.
local CROSS_MS = 1500

-- What the screen keeps between calls: the block's row, where it was drawn and in which color, the
-- color it has now, and the time of the last frame.
local row, drawnCol, drawnColor, color, now = 0, nil, nil, 1, 0

-- Lays the screen out for the grid's size: the text in the middle, the help on the last row.
local function lay(g)
  g.clear()
  local middle = g.rows // 2
  g.put(math.max(0, (g.cols - #TEXT) // 2), math.max(0, middle - 1), TEXT, { color = "text", bold = true })
  g.put(0, g.rows - 1, HELP, "muted")
  row, drawnCol = math.min(middle + 1, g.rows - 1), nil
end

local function nextColor() color = color % #COLORS + 1 end

tty.screen{
  -- What a screen reader says for the screen.
  title = "Template screen",
  -- Frames a second, from 0 to 30 (10 without it). Kolahal gives fewer in battery saver.
  fps = 30,
  -- Once, when the screen opens.
  start = lay,
  -- When the grid changes size (the phone turned, the font size changed). The grid is blank by then.
  resize = lay,
  -- For each frame. t is the time since the screen opened, in ms. The block's place comes from t,
  -- not from a count of frames, so it moves at the same speed however many frames come.
  frame = function(g, t)
    now = t
    local width = math.max(1, g.cols - 1)
    local step = math.floor(t * width / CROSS_MS) % (2 * width)
    local col = step <= width and step or 2 * width - step
    if col == drawnCol and color == drawnColor then return end
    if drawnCol then g.put(drawnCol, row, " ") end
    g.put(col, row, "█", COLORS[color])
    drawnCol, drawnColor = col, color
  end,
  -- A tap. It gets the cell it was on, tap = function(col, row), which this one doesn't need.
  -- Without a tap function, a tap closes the screen.
  tap = function() nextColor() end,
  -- A key of a hardware keyboard: a letter as typed ("q"), or "space", "enter", "up"… Returning
  -- false, from any function, closes the screen. Without a key function, a key closes it.
  key = function(k)
    if k == "q" or k == "Q" or k == "escape" then return false end
    if k == "space" then nextColor() end
  end,
  -- When the screen closes, however it closes. What it draws doesn't show, but a line it logs does.
  stop = function() log("The screen was open for " .. math.floor(now / 1000) .. " s") end,
}
