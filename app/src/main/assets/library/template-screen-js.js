// @title Template: screen (JavaScript)
// @desc A starting point for a full-screen screen of your own, in JavaScript: a line of text and a block that moves, a tap to change its color
// @author Kolahal
// @version 1.0
// @command
// @usage
// @uses screen
// @tags template, screen, javascript
// @requires 0.5.17
//
// The JavaScript version of template-screen. Run `template-screen-js`: a line of text in the
// middle, and a block that goes back and forth under it. A tap or Space changes the block's color;
// Q, Back or Home closes the screen.
//
// The @uses screen line lets it open a screen. Such a script is always a command: it runs when you
// start it, never on a timer, an event or a widget.
//
// How a screen works: the code runs to its end, and its last call, tty.screen({…}), opens the
// screen. Kolahal then calls the functions it was given. Those that draw get g, the grid of
// character cells: g.cols × g.rows of them, counted from 0 at the top left. g.put(col, row, text,
// style) writes from a cell, and a cell keeps what was written until something else is written over
// it. So a frame needs to draw only the cells that change. The grid's calls need no await.
//
// The limits: each call of a function has 1 second, and a frame that takes over 50 ms, 30 times in
// a row, closes the screen with an error. The code before tty.screen has the 60 s of any run, so
// await what the screen needs there.
//
// To make it yours, copy it under a new name first, so a library update doesn't replace your
// changes: `script mv template-screen-js myscreen`. Then change TEXT, and what frame draws. The
// library's tty-… scripts, in Lua, are longer screens to read: tty-matrix, tty-pipes, tty-sl,
// tty-clock, tty-top, tty-decode and tty-boot.

const TEXT = "Hello from a screen of your own";
const HELP = "Tap or Space: color   Q: close";

// A style is a color by its role, or an object with the color and more: { color: "good", bold: true }.
// The theme picks the real colors, so the screen reads in a light theme too.
const COLORS = ["accent", "good", "warn", "info", "bad"];

// The block crosses the screen in this many ms.
const CROSS_MS = 1500;

// What the screen keeps between calls: the block's row, where it was drawn and in which color, the
// color it has now, and the time of the last frame. A function of the spec can also keep things on
// the spec itself: there, `this` is the spec.
let row = 0, drawnCol = null, drawnColor = null, color = 0, now = 0;

// Lays the screen out for the grid's size: the text in the middle, the help on the last row.
function lay(g) {
  g.clear();
  const middle = Math.floor(g.rows / 2);
  g.put(Math.max(0, Math.floor((g.cols - TEXT.length) / 2)), Math.max(0, middle - 1), TEXT, { color: "text", bold: true });
  g.put(0, g.rows - 1, HELP, "muted");
  row = Math.min(middle + 1, g.rows - 1);
  drawnCol = null;
}

const nextColor = () => { color = (color + 1) % COLORS.length; };

tty.screen({
  // What a screen reader says for the screen.
  title: "Template screen",
  // Frames a second, from 0 to 30 (10 without it). Kolahal gives fewer in battery saver.
  fps: 30,
  // Once, when the screen opens.
  start: lay,
  // When the grid changes size (the phone turned, the font size changed). The grid is blank by then.
  resize: lay,
  // For each frame. t is the time since the screen opened, in ms. The block's place comes from t,
  // not from a count of frames, so it moves at the same speed however many frames come.
  frame(g, t) {
    now = t;
    const width = Math.max(1, g.cols - 1);
    const step = Math.floor((t * width) / CROSS_MS) % (2 * width);
    const col = step <= width ? step : 2 * width - step;
    if (col === drawnCol && color === drawnColor) return;
    if (drawnCol !== null) g.put(drawnCol, row, " ");
    g.put(col, row, "█", COLORS[color]);
    drawnCol = col;
    drawnColor = color;
  },
  // A tap. It gets the cell it was on, tap(col, row), which this one doesn't need. Without a tap
  // function, a tap closes the screen.
  tap() {
    nextColor();
  },
  // A key of a hardware keyboard: a letter as typed ("q"), or "space", "enter", "up"… Returning
  // false, from any function, closes the screen. Without a key function, a key closes it.
  key(k) {
    if (k === "q" || k === "Q" || k === "escape") return false;
    if (k === "space") nextColor();
  },
  // When the screen closes, however it closes. What it draws doesn't show, but a line it logs does.
  stop() {
    log(`The screen was open for ${Math.floor(now / 1000)} s`);
  },
});
