### Widgets

A script fills a card on the widgets page with a kind and its fields; Kolahal draws it in the theme.

`@widget <kind> [<size>]`. Sizes: `1x1`, `2x1`, `2x2`, `4x1`, `4x2`, `4x4` (columns × rows of a 4-column grid). A card draws for the space it has: put what matters first.

| Kind | Shows | Size without one |
| --- | --- | --- |
| `stat` | one big value, up to 3 smaller ones | `2x1` |
| `text` | a few lines and a caption | `2x2` |
| `list` | rows | `4x2` |
| `progress` | a ring, bars, rings, a gauge or stages | `2x1` |
| `chart` | numbers over time | `2x2` |
| `countdown` | the time to a moment; Kolahal counts | `2x1` |
| `buttons` | 1 to 8 buttons | `4x1` |
| `toggles` | 1 to 6 switches | `4x1` |
| `clock` | the time in up to 4 places; Kolahal moves it | `2x1` |
| `agenda` | events by day | `4x2` |
| `image` | a picture from the web | `2x2` |
| `versus` | two sides and a score | `4x1` |
| `sky` | the sun's or moon's path | `2x2` |

Fields of every kind, all optional (lengths in characters; longer text is cut): `kind` (else the `@widget` line's), `title` (40), `icon` (a name below, or up to 4 characters such as an emoji), `color` (a role), `shape` (only in the Shape look, which the user picks: `cookie4`, `cookie6`, `cookie9`, `cookie12`, `clover4`, `clover8`, `sun`, `burst`, `circle`, `pixel`, `gem`, `slanted`, `arch`, `ghost`, `pill`; without it, the card's own), `tap` (an action), `staleAfter` (60 to 604800 s; default 1 h, 1 min for toggles), `refresh` (10 to 3600 s, while on screen), `note` (16: a large card's last line, in Plain and Tonal).

Fields of each kind:

- `stat`: `value` (needed, 40), `label` (60), `unit` (12), `delta` (16), `trend` (`up`, `down`, `flat`), `more` (up to 3 `{ value, label }`, 24 each), `progress` (0 to 1), `moving` (bool: a wave), `points` (numbers: a line behind), `scene` (one of Kolahal's own shaders as ground, such as `pond`, `rain`, `snowfall`, `dunes`, `mountains`).
- `text`: `text` (needed, 500, keeps line breaks), `caption` (60).
- `list`: `items` (needed, may be empty, 12 kept), `empty` (60), `lead` (`{ value, unit, label }`: a big line over the rows). A row: `title` (needed, 80), `detail` (120), `trailing` (24), `icon`, `color`, `tap`, `done` (bool: a checkbox), `range` (`{ low, high, value }`: a bar; give the numbers in `trailing` too).
- `progress`: `value` and `max` (1), `text` (24, the ring's middle), `label` (60), `style` (`ring`, `bar`, `gauge`: with `value`; `rings`: draws `items`), `moving`; or `items`, up to 4 bars `{ label, value, max, text, color, unit, moving }` (`unit`, `moving`: rings only); or `stages` (2 to 6 names) with `stage` (from 1; `value`: the way to the next). Only a gauge: `min`, `unit`, `zones` (up to 5 `{ from, color }`, `from` rising).
- `chart`: `points` (needed, the oldest first, last 60 kept), `style` (`line`, `bars`, `dots`: a dot a day, the last point today), `min`, `max`, `value` (40), `label` (60), `labels` (2 texts of 16, under the first and last point), `summary` (200, for a screen reader).
- `countdown`: `to` (needed, seconds), `from` (seconds, adds a bar), `label` (60), `style` (`days`, `clock`, `dots`: a dot a day, `page`: calendar page), `reached` (60). Milliseconds are an error.
- `buttons`: `items`, 1 to 8 `{ label, icon, color, tap }`; `label` (24) and `tap` needed.
- `toggles`: `items`, 1 to 6 `{ label, icon, on, tap }`; `on`: the state now; `tap` is `{ args }`. Its run must change the thing and call `widget.show` with the new state, also on a failure.
- `clock`: `zones` (needed, 1 to 4 `{ zone, label }`, zone like `Asia/Kolkata`, `UTC`), `seconds` (bool). Give `staleAfter = 604800`.
- `agenda`: `events` (needed, 20 kept, may be empty) `{ title, starts, ends, allDay, where, color, tap }` with `title` and `starts` needed; `days` (1 to 14, 3), `empty` (60). An all-day event starts at local midnight.
- `image`: `image` (needed, an `https://` link), `alt` (needed, 200: what it shows), `caption` (60), `fit` (`crop`, `fit`).
- `versus`: `sides` (needed, 2 `{ name, mark, color, value, text }`, `name` needed, `value` ≥ 0), `score` (needed), `turn` (1, 2), `moving`, `caption` (60), `badge` (8).
- `sky`: `rises`, `sets` (needed, seconds; `sets` after `rises`, under a day later: the moon's next set after its rise), `phase` (0 to 1: the moon), `waxing`, `phases` (with `phase`, up to 4 `{ phase, waxing, at }`).

A tap is a table / object with one of: `{ args = "add 1" }` runs this script again with those words (`event.name` is `"action"`); `{ url = "https://…" }` opens a link. It may add `label` (24, for a screen reader) and, with `args`, `page = "log"` to show the log. A card without `tap` refreshes on a tap.

Rules: an unknown field, another style's field, a wrong type, or too many buttons or stages is an error naming it; Kolahal capitalizes labels; a text keeps only `fmt.bold`, `.italic`, `.underline`, `.strike` and `.color` (in `text`, `caption`, a row's `title`, `detail` and `trailing`, a stat's `label`); Markdown isn't read. A description is at most 32 KB; a run calls `widget.show` and `widget.clear` up to 20 times. In Lua, a list must not have `nil` in the middle.

Icon names: sun, moon, cloud, rain, snow, storm, wind, fog, temp, drop, alarm, timer, clock, calendar, bell, home, work, lock, unlock, wifi, battery, bolt, torch, phone, dnd, person, people, mail, chat, quote, music, play, pause, news, book, feed, heart, star, check, flag, fire, leaf, trophy, ball, cricket, money, cart, chart, up, down, parcel, gift, car, bus, train, plane, walk, pin, globe, coffee, code, bug, link. Another lowercase word over 4 letters is an error.

Runs for a widget: its own timers and events, and `"widget"` runs (see "Runs"). A `"widget"` run's return value isn't logged: skip `log` in it. A failed `"widget"` run or tap leaves the content, with "Failed 10:42" on a large card's last line. With no timer it runs only when seen or tapped. A widget can be placed up to 6 times, each with its own options; `@every`, `@at` and `@on` runs fill the first only.
