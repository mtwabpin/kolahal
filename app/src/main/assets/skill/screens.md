### Screens

`tty.screen(spec)` (with `@uses screen`) opens a full-screen screen over home after the code has run, only when the user starts the script. Fields: `fps` (0 to 30, 10; 0 draws only in `start`, `key` and `tap`), `title` (for a screen reader), `start(g)`, `frame(g, t, dt)` (ms), `key(k)` (a typed character, or `"up"`, `"down"`, `"left"`, `"right"`, `"enter"`, `"space"`, `"escape"`, `"tab"`, `"delete"`), `tap(col, row)`, `resize(g)`, `stop()`. A function that returns false closes it; without `key` a key closes it, without `tap` a tap does; Back always does.

The grid `g`: `g.cols`, `g.rows` (cells, counted from 0); `g.put(col, row, text, style?)`, `g.fill(col, row, width, height, char?, style?)`, `g.clear(style?)`, `g.box(col, row, width, height, style?)`, `g.bar(col, row, width, value, style?)`. A style is a role, or `{ color, bg, bold, faint, italic, underline, strike, inverse }`. Each call has 1 second; a frame over 50 ms 30 times in a row closes the screen. Fetch data before `tty.screen`. The library's `template-screen` and `tty-*` scripts (`script install tty-matrix`) are screens to read.

### Not possible

- Anything outside this API: no location, contacts, messages, calls, files, camera, sensors, Bluetooth, other libraries, or drawing of its own outside a widget's kinds and a screen's grid.
- Running more often than every 15 minutes in the background; reacting to events other than the eight `@on` events.
- Putting a terminal command behind a tap; placing its widget, pinning, or switching its notifications on.
- Reading a secret's value, or sending it to another site.
- Links other than http and https (`tel:`, an app's own links).
- A run over 60 seconds or 64 MB.
