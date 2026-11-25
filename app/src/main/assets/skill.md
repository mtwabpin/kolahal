# Writing scripts and shaders for Kolahal

Kolahal is an Android launcher that works like a terminal: home is a log of lines with a prompt under it, and a page of widgets beside it. People extend it with **scripts** (Lua 5.4 or JavaScript, run by Kolahal in a sandbox) and **shaders** (AGSL, drawn over home or as the live wallpaper).

This text is for an AI assistant that writes one of these for a user. It describes the Kolahal that copied it. Anything it doesn't name (a function, a field, an event, a uniform) doesn't exist: never guess one.

## How to answer

1. Pick what fits. A **script** fetches, reacts to events, logs lines, notifies, sets a status line, fills a widget card or becomes a command. A **shader** changes how the screen looks; it can't fetch or store anything.
2. Ask only for what you can't choose well yourself: a city, a site and whether it needs an API key, a size. Put choices the user may change later in `@option` lines instead of asking.
3. If the wish needs something Kolahal can't do (see "Not possible"), say so and offer the nearest thing it can do.
4. Give **one complete file** in one code block, with its header lines. Lua is the default; write JavaScript when the user asks for it.
5. Say how to add it (next section), with the exact commands, and the name to give it.
6. Say what it will ask for: internet, each `@uses` item, notifications, and any Android permission (table under "What a script may use").
7. Use only web APIs you are sure of, keyless where you can; a key goes in `@secret`.

## Getting it into Kolahal

Commands are typed at Kolahal's prompt.

**A script**, on the phone:

1. `script new <name>` for Lua, `script new <name>.js` for JavaScript. The editor opens with a template. A name is lowercase letters, digits, `-` and `_`, at most 40, and not a built-in command's name.
2. Replace the template with the code, then tap **Save**. Saving points out unknown API names, a call that lacks its `@uses` line, and header lines Kolahal can't read. A script you made and saved may use what its `@uses` line names. `@every` and `@at` lines set its timers on saving.
3. Run it: a command by its name (`<name> args…`), any script with `script run <name> [args…]`.
4. As needed: `script notify <name> on` lets `notify` post; `script pin <name>` shows its status line at the top of home; for a widget, tap **+** on the widgets page (or type `widget add`) and pick it in the **Kolahal** group. A script can't do any of these itself.

**A script as a file**: save it as `<name>.lua` or `<name>.js` (the file name is its name, with the rules above), then **Scripts → Add script → Import a file**, or put it in the folder picked with `script dir` and type `script import`. A script from a file arrives switched off: `script on <name>` switches it on and allows what it declares.

**A shader**, on the phone:

1. `shader save template-tint` makes a shader of yours from a template, called `my-tint` (the log line says the name; `my-tint-2` when taken).
2. `shader edit my-tint`, replace all the code, tap **Save**: it says **Saved · compiles**, or the compiler's error with its line.
3. `shader my-tint` turns it on over home; `shader play my-tint` plays a one-off; `wallpaper shader my-tint` makes it the live wallpaper; `shader off` turns shaders off.

**A shader as a file**: save it as `<name>.agsl` (its name is the file name in lowercase; letters, digits, `-` and `_`; not a `shader` subcommand such as `ls` or `edit`), then **Shaders → Add shader → Import a file**, or put it in the script folder's `shaders` folder and type `shader reload`. Then `shader <name>`. Shaders need Android 13 or newer.

## Scripts

### The smallest script

```lua
-- @command
-- @desc Says hello: hello [name]
-- @usage [<name>]
return "hello, " .. (argline ~= "" and argline or "world")
```

In JavaScript the last line is ``return `hello, ${argline || "world"}`;`` with `//` comments. Saved as `hello`, `hello Asha` prints the line. What a run returns is printed (tables and objects as JSON). An error stops the run with a red line; a tap on it opens the editor at the failing line.

Lua and JavaScript have the same API. In Lua every call blocks (scripts never run on the UI thread). In JavaScript the code runs inside an async function, so top-level `await` and `return` work, and `Promise.all` runs requests side by side. There is no `require`, and no file, process or network access but the API below.

### Script header

Header lines are comments at the top: `-- @name value` in Lua, `// @name value` in JavaScript.

| Line | What it does |
| --- | --- |
| `@command` | Runs by its name at the prompt: `<name> args…`. |
| `@desc <text>` | One line, shown in `help`, suggestions and Scripts. |
| `@usage [<args>]` | Its arguments, as a usage line: `[<city>]`; its words are suggested as you type. Empty: it takes none. |
| `@uses <what>, …` | What it may use: see "What a script may use". A call without its item stops with an error. |
| `@on <event>, …` | Runs on events: `unlock`, `charger`, `unplug`, `wifi`, `offline`, `notification`, `boot`, `headset`. |
| `@every 1h` | Its timer: `15m` (the shortest, Android's limit), `2h`, `1d`, a number of minutes, or `off`; at most a week. Runs that go online wait for a network. |
| `@at 07:30, 18:00` | Daily times, HH:mm. On the minute when Kolahal may set exact alarms (`allow alarms`), else within the hour. |
| `@widget <kind> [<size>]` | It fills a card on the widgets page: see "Widgets". |
| `@option <name> <default> <description>` | A setting the user changes without editing code, read as `options.<name>` (always text). |
| `@secret <NAME> <site> <description>` | An API key the user sets on the phone, read with `secrets.get`, sent only to that site. |
| `@gives <name>, …` | Values it gives shaders with `shader.give` (up to 8): small letters, digits, `-` and `_`, at most 20. |
| `@notify` | Suggests turning its notifications on. |
| `@title <text>` | Its name for people, in Scripts. |
| `@author <name>` | Who wrote it. |
| `@version <x.y>` | Its version. |
| `@tags <word>, …` | Search words. |
| `@requires <x.y.z>` | The oldest Kolahal it works with. |

- `@option`: the name (a letter, then letters, digits, `_`; at most 32); the default is one word, `"several words"` or `""`; a default such as `metric|imperial` offers a choice, and its first word is the default. Users set it in the script's sheet or with `script set <name> <option> <value>`. Convert numbers with `tonumber` / `Number()`.
- `@secret OWM_KEY api.openweathermap.org Your OpenWeather key`: the name (a letter, then letters, digits, `_`), the site (subdomains included; several with commas; `*` is any site), a description. The user sets it in the script's sheet or with `script secret <name> <NAME>`.
- A script with `@uses screen` is a command only: `@every`, `@at`, `@on` and `@widget` don't work in it.

### Runs

A script runs when it is typed as a command, by `script run`, on its timer or daily times, on an `@on` event, when its widget asks, on a tap on its widget or a button under its line, and when a pinned status line refreshes. Each run starts fresh: keep what must last in `store`.

`event` says what started the run; `nil` / `null` for a run typed at the prompt or by `script run`.

| `event.name` | Other fields |
| --- | --- |
| `charger`, `unplug` | `level` (battery %), `plug` (`ac`, `usb`, `wireless`, `other`) while plugged in |
| `notification` | `app`, `pkg`, `title`, `text`, `time` (ms) |
| `headset` | `type` (`wired`), `device`, `mic` |
| `wifi` | `network` (`wifi`) |
| `boot` | `uptime` (seconds) |
| `unlock`, `offline` | none |
| `at` | `time`, the daily time that started it (`"07:30"`) |
| `action` | a tap on a button under a line or on its widget: `label`; `widget` is true for a widget tap; the tap's words are in `args` |
| `widget` | the widgets page wants new content: on placing, when it shows content older than `staleAfter`, on `refresh`, on a tap on a card with no `tap` |
| `refresh` | a pinned status line's `refresh` |

In a run for the second or later widget of the script, `event.instance` is that widget's number as text (`"2"`). Text in `event` is cut at 2,000 characters.

### Script API

`?` marks an optional argument. In JavaScript, write `await` before `http.*`, `rss.fetch`, `rss.fresh` and `sleep`.

#### Output

| Call | What it does |
| --- | --- |
| `log(title, body?, ctx?)` | Adds a line: the title, and the body muted under it. `log(title, ctx)` works too. Returns the line; `line.set(title, body?, ctx?)` changes it in place while the run lasts. |
| `logs(titles)` | One title line per item of a list, or per argument: `logs("a", "b")`. |
| `print(...)` | Lua only: logs the values joined by tabs. |
| `console.log(...values)` | JavaScript only: logs the values joined by spaces. |
| `console.error(...values)` | JavaScript only: logs them as an error line. |
| `md.log(title, body?, ctx?)` | Like `log`, reading Markdown: `**bold**`, `*italic*`, `~~strike~~`, `++underline++`, `` `code` ``, `[text](https://…)`; `- ` and `1. ` lists in the body. `log` never reads Markdown. |
| `md.escape(text)` | → string: text with Markdown marks escaped, to put web text in `md.log`. |
| `notify(title, body, ctx?)` | → bool. Posts a notification if they are on for this script; `ctx.url` opens on a tap. At most 10 a run. |
| `status(text, opts?)` | Sets its status line (first line, 80 characters): on its card, in `status`, and at the top of home once pinned. `opts.progress` (0 to 1) adds a bar, `opts.color` is its role, `opts.refresh` (10 to 3600 s) runs it again while shown. `status(nil)` clears it. 20 changes a run. |
| `allowed(what)` | → bool: may it use `what`: `"internet"`, `"header"` (pinned), `"widget"` (placed), an `@uses` item or an `@on` event. |

`ctx` (a table / object): `url` (a tap opens this web link), `copy` (what Copy text copies: part of what the line shows, or its url), `image` (a picture's http(s) link, shown under the line), `mono = "1"` (terminal font, for columns), `gap = true` (a small space above: a new item of a list), `actions` (up to 3 buttons, each a two-item list `{ "Snooze", "snooze 30" }`: label, then the words this script runs with on a tap). Other keys are dropped. Links must be http or https.

A run logs at most 500 lines and 256K characters. `line.set` works 300 times a run.

#### Styles

Each returns text with styles, to join with other text in a title or body. Roles: `accent`, `link`, `info`, `muted`, `text`, `good`, `warn`, `bad`; the theme picks the colors, so never give a color value.

| Call | What it makes |
| --- | --- |
| `fmt.bold(text)` | bold |
| `fmt.italic(text)` | italic |
| `fmt.underline(text)` | underlined |
| `fmt.strike(text)` | struck through |
| `fmt.code(text)` | a shaded chip in the terminal font |
| `fmt.color(role, text)` | text in a role's color |
| `fmt.tag(role?, text)` | a short label on a shaded chip (`LIVE`, `v1.2`); accent without a role |
| `fmt.link(text, url)` | text that opens a web link |
| `fmt.list(items, numbered?)` | a body list: bullets, or 1., 2. … |
| `fmt.table(rows, {header?, align?})` | a body table: each row a list of cells; `header = true` makes the first row the header; `align = "lrr"`, one letter a column. Up to 8 columns and 50 rows |
| `fmt.bar(value, {max?, color?})` | a bar as wide as the line, `value` of `max` (1) |
| `fmt.bars(rows, {max?, color?, unit?})` | a bar a row, one scale; each row a list `{ label, value, text? }`: `{ "Mon", 42 }` |
| `fmt.ranges(rows, {min?, max?, color?, unit?})` | a bar from low to high a row; each row a list `{ label, low, high, lowText?, highText? }` |
| `fmt.stats(items)` | a line of big numbers; each item a list `{ value, label }`: `{ "25°C", "sunny" }`. Up to 5 |
| `fmt.plain(text)` | the text without styles, before storing or sending it |

#### Web and data

| Call | What it does |
| --- | --- |
| `http.get(url, headers?)` | → `{ status, ok, body, headers }`. `ok` is true for 2xx; header names are lowercase; up to 4 MB of body is read. |
| `http.cached(url, seconds?)` | A GET that reuses this script's stored response while younger than `seconds` (600); adds `cached`. Only successful bodies are kept. |
| `http.request{ url, method, body, contentType, headers }` | Any method (JS: `http.request({ … })`). A string body is sent as text, a table / object as JSON. |
| `rss.fetch(url)` | → `{ title, link, items }`, each item `{ title, link, id, date, summary }`. |
| `rss.fresh(url)` | Like `rss.fetch`, only items this script hasn't had; marked seen when the run succeeds. |
| `rss.parse(xml)` | Parses feed XML you have (no `await`). |
| `store.get(key)` | → string or nil/null. This script's own store, kept between runs. |
| `store.set(key, value)` | Stores a string; nil/null deletes it. Up to 1 MB a value, 1,000 keys and 16 MB a script. |
| `secrets.get(name)` | → a placeholder for the key named on an `@secret` line, or nil/null until set. Put it in a URL, header or body; Kolahal fills in the value only in https requests to that line's site. |
| `json.encode(value)` | Lua only (JavaScript has its own `JSON`): a table to JSON text. |
| `json.decode(text)` | Lua only: JSON to tables (arrays start at 1); an error on bad JSON, so use `pcall` for web text. |
| `sleep(ms)` | Waits, up to 5000 ms a call. |

A network failure raises an error (JS: rejects); an HTTP error status doesn't, so check `ok`. A request can go to any address, `http://` too. A JavaScript run makes 4 requests at a time.

#### The phone

| Call | What it does |
| --- | --- |
| `device.battery()` | → `{ level, charging }`; `level` 0 to 100, or nil when unknown. |
| `device.network()` | → `"wifi"`, `"cell"`, `"eth"`, `"vpn"`, `"net"` or `"offline"`. |
| `device.unlocks(days?)` | → today's unlock count; with `days` (1 to 30), a list of daily counts, oldest first, today last. |
| `device.dnd()` | → `"off"`, `"priority"`, `"alarms"` or `"none"`. `@uses dnd`. |
| `device.setDnd(mode)` | `"off"`, `"on"` (priority), `"alarms"` or `"none"`; → false without Do Not Disturb access. `@uses dnd`. |
| `clipboard.get()` | → text or nil; usually nil in timer runs (only the app in front may read it). `@uses clipboard`. |
| `clipboard.set(text)` | Copies text, up to 100,000 characters. `@uses clipboard`. |
| `apps.list()` | → `[{ label, pkg, profile }]`, the launchable apps; `profile` is `"personal"` or `"other"`. `@uses apps`. |
| `notifications.recent(n?)` | → `[{ pkg, app, title, text, time }]`, newest first, 20 (up to 50); `time` in ms. `@uses notifications`. |
| `launcher.run(command)` | → bool. Runs a built-in or another script's command, as typed. Only in a run started from the prompt (a command, `script run`), at most 10 a run; commands that change things (`clear`, `uninstall`, `work`, `script`…) are refused. `@uses commands`. |
| `device.work()` | → `"on"`, `"paused"`, or nil without a work profile. `@uses work`. |
| `device.setWork(on)` | Resumes (true) or pauses (false) the work profile; → false when Android refuses (only in the foreground). `@uses work`. |

#### Your lists and app usage

Times are seconds since 1970 (`os.time()` in Lua, `Date.now() / 1000` in JS). No `await` needed.

| Call | What it does |
| --- | --- |
| `todos.list()` | → `[{ id, text, done }]`, open ones first, up to 200. `@uses todos`. |
| `todos.setDone(id, done)` | Marks a todo done (true) or open (false); `id` may be text; → true when it changed. 20 a run. `@uses todos`. |
| `notes.list(n?)` | → `[{ id, text, created }]`, newest first, 10 (up to 50). `@uses notes`. |
| `reminders.list()` | → `[{ id, text, at, repeats }]`, soonest first; `repeats` is words, or nil. `@uses reminders`. |
| `calendar.events(days?)` | → `[{ id, title, starts, ends, allDay }]` for 3 days (1 to 30), up to 100; fits an agenda widget's `events`. `@uses calendar`. |
| `feeds.unread(n?)` | → `[{ id, feed, title, link, date, summary }]`, the user's unread feed items, 10 (up to 50); no request is made. `@uses feeds`. |
| `usage.today(n?)` | → `{ total, limit, color, apps = [{ app, pkg, minutes }] }`: today's screen time, its daily limit and role (`warn`, `bad`) or nil, and the `n` (10, up to 30) apps used most. `@uses usage`. |
| `usage.days(n?)` | → `[minutes]` for the last `n` days (7, up to 30), oldest first; only days Android has. `@uses usage`. |
| `usage.data(since?)` | → `{ mobile, wifi }` bytes since a time (midnight); `"month"` gives the data plan's month, adding `since`, `plan`, `pace`, `daysLeft`. `@uses usage`. |
| `usage.unused(days?)` | → `[{ app, pkg, days, bytes }]`, apps not opened for `days` (30; 7 to 365) or more, up to 50. `@uses usage`. |
| `usage.summary(period, app?)` | `"week"`, `"month"` or `"year"` → `{ perDay, rows = [{ from, to, minutes, perDay, unlocks, current }], top }`. `@uses usage`. |

The usage calls return nil without usage access. With an `@uses` switch off, the list calls return empty lists.

#### Shaders, widgets, screens and run values

| Call | What it does |
| --- | --- |
| `shader.give(name, value)` | Gives a value named on the `@gives` line: a number (0 to 1 spreads over a shader parameter's range) or a word; nil/null takes it away. Shaders see it as `<script>.<name>`. 40 changes a run. |
| `shader.play(name, seconds?)` | Plays a one-off shader (such as `hearts`) over home once, 1 to 10 s. `@uses effects`. |
| `widget.show{ kind, title, … }` | Fills this script's widget (JS: `widget.show({ … })`); → true when placed, false when only checked. See "Widgets". |
| `widget.clear()` | Empties its widget. |
| `tty.screen{ fps, title, start, frame, key, tap, resize, stop }` | A full-screen screen of its own (JS: `tty.screen({ … })`). `@uses screen`. See "Screens". |
| `args` | The words after the command's name: a table in Lua, an array in JS. Empty in timer runs. |
| `argline` | The same words joined by spaces. |
| `event` | What started the run: see "Runs". |
| `options` | Its `@option` values by name, as text. |

### What a script may use

Internet has no `@uses` line: a script whose code mentions `http`, `rss`, `url`, `image` or `launcher.run` has it, unless the user switches it off. Each `@uses` item is a switch of its own.

| `@uses` | Allows | Also needs |
| --- | --- | --- |
| `notifications` | `notifications.recent`, `@on notification` | notification access: `allow read-notifications` |
| `clipboard` | `clipboard.get`, `clipboard.set` | |
| `apps` | `apps.list` | |
| `commands` | `launcher.run` | |
| `dnd` | `device.dnd`, `device.setDnd` | Do Not Disturb access: `allow dnd` |
| `todos` | `todos.list`, `todos.setDone` | |
| `notes` | `notes.list` | |
| `reminders` | `reminders.list` | |
| `calendar` | `calendar.events` | the calendar permission: `allow calendar` |
| `feeds` | `feeds.unread` | |
| `usage` | `usage.today`, `usage.days`, `usage.data`, `usage.unused`, `usage.summary` | usage access: `allow usage` |
| `screen` | `tty.screen` | |
| `effects` | `shader.play` | |
| `work` | `device.work`, `device.setWork` | a work profile |

Write them on one line: `-- @uses notifications, effects`.

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

Fields of every kind, all optional (lengths in characters; longer text is cut): `kind` (else the `@widget` line's), `title` (40), `icon` (a name below, or up to 4 characters such as an emoji), `color` (a role), `shape` (Shape look: `cookie4`, `cookie6`, `cookie9`, `cookie12`, `clover4`, `clover8`, `sun`, `burst`, `circle`, `pixel`, `gem`, `slanted`, `arch`, `ghost`, `pill`; without it, the card's own for its kind and size), `tap` (an action), `staleAfter` (60 to 604800 s; default 1 h, 1 min for toggles), `refresh` (10 to 3600 s, while on screen), `note` (16: a large card's last line).

Fields of each kind:

- `stat`: `value` (needed, 40), `label` (60), `unit` (12), `delta` (16), `trend` (`up`, `down`, `flat`), `more` (up to 3 `{ value, label }`, 24 each), `progress` (0 to 1), `moving` (bool: a wave), `points` (numbers: a line behind), `scene` (a shader as ground, such as `pond`, `rain`, `snowfall`, `dunes`, `mountains`).
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

Runs for a widget: its own timers and events, and `"widget"` runs (see "Runs"). A `"widget"` run's return value isn't logged: skip `log` in it. A failed run leaves the content, with "Failed 10:42". With no timer it runs only when seen or tapped. A widget can be placed up to 6 times, each with its own options.

### Screens

`tty.screen(spec)` (with `@uses screen`) opens a full-screen screen over home after the code has run, only when the user starts the script. Fields: `fps` (0 to 30, 10; 0 draws only in `start`, `key` and `tap`), `title` (for a screen reader), `start(g)`, `frame(g, t, dt)` (ms), `key(k)` (a typed character, or `"up"`, `"down"`, `"left"`, `"right"`, `"enter"`, `"space"`, `"escape"`, `"tab"`, `"delete"`), `tap(col, row)`, `resize(g)`, `stop()`. A function that returns false closes it; without `key` a key closes it, without `tap` a tap does; Back always does.

The grid `g`: `g.cols`, `g.rows` (cells, counted from 0); `g.put(col, row, text, style?)`, `g.fill(col, row, width, height, char?, style?)`, `g.clear(style?)`, `g.box(col, row, width, height, style?)`, `g.bar(col, row, width, value, style?)`. A style is a role, or `{ color, bg, bold, faint, italic, underline, strike, inverse }`. Each call has 1 second; a frame over 50 ms 30 times in a row closes the screen. Fetch data before `tty.screen`. The library's `template-screen` (and `template-screen-js`) is a short screen to start from, and its `tty-*` scripts draw Kolahal's own screens (matrix, pipes, sl, clock, top, decode, boot) this way: `script install tty-matrix` to read one.

### Not possible

- Anything outside this API: no location, contacts, messages, calls, files, camera, sensors, Bluetooth, other libraries, or drawing of its own outside a widget's kinds and a screen's grid.
- Running more often than every 15 minutes in the background; reacting to events other than the eight `@on` events.
- Putting a terminal command behind a tap; placing its widget, pinning, or switching its notifications on.
- Reading a secret's value, or sending it to another site.
- Links other than http and https (`tel:`, an app's own links).
- A run over 60 seconds or 64 MB.

## Shaders

A shader is an AGSL program run for every pixel of home (or of the live wallpaper). It gets what is on the screen as `content` and returns each pixel's color. It has no other input: no images, files or network, and it can't store anything.

### The smallest shader

```glsl
// @name Tint
// @description A little of the accent color over everything

uniform shader content;
layout(color) uniform half4 accent;

half4 main(float2 xy) {
    half4 c = content.eval(xy);
    return half4(mix(c.rgb, accent.rgb * c.a, 0.1), c.a);
}
```

`xy` is the pixel, in pixels from the top left corner. `content.eval(xy)` gives the color there. Colors are **premultiplied**: red, green and blue are already multiplied by alpha, so mix a color in as `color * alpha`. Over a wallpaper the page can be transparent: check `a`.

### Shader header

Comments at the top, all optional:

| Line | What it does |
| --- | --- |
| `// @name Tint` | Its name for people (`@title` also works). |
| `// @description …` | What it looks like (`@desc` also works). |
| `// @param amount 0.0..2.0 = 1.0 Amount` | Something people change: see "Parameters". |
| `// @suits dark good, light fair, wallpaper poor` | How it looks over a dark theme, a light theme and a wallpaper behind the log: `good`, `fair` or `poor`. A one-off adds `apps good` to play over other apps (GitHub build), on a clear `content`. |
| `// @text` | It works on text, so it can't be a live wallpaper. |
| `// @animated` | It changes by itself: drawn up to 30 times a second, and `time` moves. Without it, it is drawn only when the screen changes, and costs nothing while it is still. |
| `// @fps 10` | An animated shader's frames a second, 1 to 30. Use it for slow scenes. |
| `// @settles 4` | Seconds a touch changes the picture, 0.5 to 60 (60). A shader whose only clock is `touchAge` stops after that until the next touch. |
| `// @once 3` | A one-off: plays for this many seconds (1 to 10) over the shader that is on, then goes. Start and end with nothing. |
| `// @author …` | Who made it. |
| `// @version 1.0` | Its version. |
| `// @license MIT` | Its license. |
| `// @tags glow, rain` | Search words. |
| `// @requires 0.5.17` | The oldest Kolahal it works with. |

### Uniforms

Declare only those you use; Kolahal sets them. A uniform of another name stays 0. Types must match (`half` for `float` and `half2` for `float2` are fine); no arrays.

| Uniform | Type | What it is |
| --- | --- | --- |
| `content` | `shader` | what is on the screen. Required, and the only shader input |
| `resolution` | `float2` | the surface's size in pixels |
| `cell` | `float2` | one character cell's size in pixels, to size things like the text |
| `grid` | `float2` | columns and rows of cells that fit |
| `origin` | `float2` | where the first column starts, in pixels from the left, and 0 |
| `time` | `float` | seconds since the shader was turned on; moves only with `@animated`. Wrap it: `mod(time, 3141.59265)` |
| `progress` | `float` | how far a one-off has come, 0 to 1 |
| `touch` | `float2` | the last touch, in pixels; `-1, -1` before the first |
| `touchAge` | `float` | seconds since that touch, only with `@animated`; 1000 before the first |
| `pressed` | `float` | 1 while a finger is down, else 0 |
| `tilt` | `float2` | how far the phone leans, about -1 to 1 (x: right side lower, y: bottom edge lower); `0, 0` when level. An offset for what is behind text, never for text |
| `battery` | `float` | how full the battery is, 0 to 1 |
| `charging` | `float` | 1 while plugged in, else 0 |
| `corner` | `float` | the radius of the screen's rounded corners in pixels; 0 when square or in a preview |
| `cutout` | `float4` | the camera cutout at the top: left, top, right, bottom in pixels; all 0 without one |
| `wallpaper` | `float` | 1 as the live wallpaper (then `content` is the theme's background alone), else 0 |
| `dark` | `float` | 1 in a dark theme, 0 in a light one |
| `date` | `float4` | year, month (1 to 12), day of the month, seconds since midnight, local time. A still shader that declares it is drawn again each minute |
| `bg` `surface` `fg` `dim` `accent` `error` `warn` `good` `info` `link` | `half4` | the theme's colors; declare each as `layout(color) uniform half4 accent;` |

A still shader (no `@animated`) is drawn again when `pressed`, `battery`, `charging`, `tilt` or a parameter changes.

### Parameters

```text
// @param amount 0.0..2.0 = 1.0 Amount of snow
// @param shape round|star = round Shape of the flakes
uniform float amount;
uniform float shape;
```

- `@param <name> <min>..<max> = <default> <title>`: a slider; with no decimal point anywhere in it (`1..8 = 3`), whole numbers. The default must be in the range.
- `@param <name> a|b|c = a <title>`: 2 to 8 words (letters, digits, `-`), shown as buttons; the uniform gets the word's place, 0 for the first.
- Each needs `uniform float <name>;`. Up to 8 parameters; a name can't be a uniform Kolahal sets.
- People change them in the shader's details or with `shader set <name> <param> <value>`.
- **Following a value**: end the line with `@follow <value>` (or a formula such as `@follow battery / 20`), and the parameter follows it until the user picks another. A number from Kolahal is spread over the range (a full battery is `max`); a formula's result is the value itself.

| Value | What it is |
| --- | --- |
| `battery` | 0 to 100 |
| `charging` | 0, or 1 while plugged in |
| `day` | the time of day in hours, 0 to 24 |
| `night` | 0 by day, 1 at night, with a dusk and a dawn |
| `unlocks` | today's unlocks (100 is a parameter's most) |
| `unlocks.usual` | today's unlocks against a usual day: 0 none, 1 as many, 2 at most |
| `screen` | today's screen time in minutes (480 is the most) |
| `screen.usual` | today's screen time against a usual day |
| `away` | minutes the phone was locked before the last unlock (240 is the most) |
| `unlocks.d1` … `unlocks.d7` | each of the last 7 days' unlocks, `d7` today, 0 to 1 (1: the busiest day) |
| `screen.d1` … `screen.d7` | the same for screen time |
| `screen.h1` … `screen.h8` | each three hours of today from midnight, 0 to 1: how much the screen was on |
| `<script>.<name>` | a value a script gives with `shader.give` (`weather.rain`) |

Screen time and `away` need usage access (`allow usage`).

### Writing AGSL that compiles

Write it the way Kolahal's own shaders are written; they use only these forms:

- The entry point is exactly `half4 main(float2 xy)`. Helper functions and `const` values come before it.
- Every number in float math has a decimal point: `2.0`, `0.5`, never `2`.
- Convert types explicitly: `float4 here = float4(content.eval(xy));`, `half4(here)`, `float3(accent.rgb)`, `float(i)`.
- Loops are `for (int i = 0; i < 5; i++)` with a fixed number as the bound; `break` may leave early. No `while`, `switch`, arrays or `%`; use `mod()` for remainders.
- Built-ins used: `mix`, `smoothstep`, `clamp`, `min`, `max`, `abs`, `fract`, `floor`, `mod`, `length`, `dot`, `normalize`, `sin`, `cos`, `atan`, `exp`, `exp2`, `pow`, `sqrt`, `step`. For noise, Kolahal's shaders use this hash: `float hash(float2 p) { float3 q = fract(float3(p.xyx) * 0.1031); q += dot(q, q.yzx + 33.33); return fract((q.x + q.y) * q.z); }`.
- Before the compiler, Kolahal refuses a file without `uniform shader content;`, another `uniform shader`, a known uniform of the wrong type or as an array, a `@param` without its `uniform float`, more than 8 parameters, and a file over 64 KB.
- A compile error reads `line 12: unknown identifier 'x'`: fix that line.

### Behind the text, and cost

Most shaders draw only where the page shows, so text stays readable:

```text
float d = min(length(here.rgb - float3(bg.rgb) * here.a), length(here.rgb - float3(surface.rgb) * here.a));
float open = 1.0 - clamp(d * 5.0, 0.0, 1.0);   // 1 on the page, 0 on text
```

- Use the theme's color uniforms, never fixed colors, so it fits every theme; take less on a light theme (`mix(0.75, 1.0, dark)`), and check `wallpaper`.
- Return early for pixels you don't change. Look at a few pixels around each one at most, never hundreds.
- An `@animated` shader works out every pixel every frame: use `@fps 10` for slow motion, and `@settles` with `touchAge` for touch effects instead of `time`.
- Don't move pixels far from where they are (under half a cell): a tap would miss what it shows.
- Kolahal doesn't stop a slow shader: home just gets slow.

## Examples

### Script: a widget you tap (Lua)

Saved as `water`; place it with `widget add`.

```lua
-- @title Water
-- @desc Glasses of water today; a tap on the card adds one
-- @widget progress 2x1
-- @at 00:01
-- @option goal 8 Glasses a day

local today = os.date("%Y-%m-%d")
local n = 0
if store.get("day") == today then n = tonumber(store.get("count")) or 0 end
if event and event.name == "action" and args[1] == "add" then
  n = n + 1
  store.set("day", today)
  store.set("count", tostring(n))
end
local goal = tonumber(options.goal) or 8

widget.show{
  kind = "progress",
  value = n,
  max = goal,
  text = n .. " / " .. goal,
  label = "Glasses today",
  color = n >= goal and "good" or "accent",
  tap = { args = "add", label = "add a glass" },
}
```

### Script: a command with arguments (Lua)

Saved as `stars`; `stars neovim/neovim` logs the numbers. Asks for internet.

```lua
-- @command
-- @desc GitHub stars: stars [owner/repo]
-- @usage [<owner/repo>]

local repo = args[1] or "neovim/neovim"
if not repo:match("^[%w%.%-_]+/[%w%.%-_]+$") then return "usage: stars owner/repo" end
local r = http.cached("https://api.github.com/repos/" .. repo, 600)
if not r.ok then error("GitHub answered HTTP " .. r.status) end
local d = json.decode(r.body)
log(d.full_name, fmt.stats({
  { tostring(d.stargazers_count), "stars" },
  { tostring(d.forks_count), "forks" },
  { tostring(d.open_issues_count), "open issues" },
}), { url = d.html_url })
```

### Script: a timer that notifies (JavaScript)

Saved as `releases.js`; then `script notify releases on`, and `script pin releases` for its status line. Asks for internet.

```js
// @title Releases
// @desc Tells you when a GitHub project has a new release
// @every 6h
// @notify
// @option repo neovim/neovim The project, as owner/name

const repo = options.repo;
if (!/^[\w.-]+\/[\w.-]+$/.test(repo)) return log(fmt.color("bad", "Set the repo option to owner/name"));
const r = await http.get(`https://api.github.com/repos/${repo}/releases/latest`, { Accept: "application/vnd.github+json" });
if (r.status === 404) return log(`${repo} has no releases yet`);
if (!r.ok) throw new Error(`GitHub answered HTTP ${r.status}`);
const release = JSON.parse(r.body);
status(`${repo.split("/")[1]} ${release.tag_name}`);
const seen = store.get("seen");
if (seen === release.tag_name) return;
store.set("seen", release.tag_name);
if (seen != null) {
  log(repo, fmt.color("good", "New: ") + fmt.code(release.tag_name), { url: release.html_url });
  notify(`${repo} ${release.tag_name}`, release.name || "New release", { url: release.html_url });
}
```

### Shader: still, with a parameter

```glsl
// @name Vignette
// @description Darker corners, the middle as it is
// @suits dark good, light fair, wallpaper good
// @param strength 0.0..1.0 = 0.4 Strength

uniform shader content;
uniform float2 resolution;
uniform float strength;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    float far = length(xy / resolution - 0.5) * 1.4;
    float shade = 1.0 - strength * smoothstep(0.3, 1.0, far);
    return half4(here.rgb * shade, here.a);
}
```

### Shader: moving, behind the text

```glsl
// @name Mist
// @description Slow bands of mist drift across the lower half, behind the text
// @suits dark good, light good, wallpaper good
// @animated
// @fps 10

uniform shader content;
uniform float2 resolution;
uniform float time;
uniform float dark;
layout(color) uniform half4 bg;
layout(color) uniform half4 surface;
layout(color) uniform half4 info;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    float2 p = xy / resolution;
    if (p.y < 0.5) return half4(here);
    float d = min(length(here.rgb - float3(bg.rgb) * here.a), length(here.rgb - float3(surface.rgb) * here.a));
    float open = 1.0 - clamp(d * 5.0, 0.0, 1.0);
    if (open <= 0.0) return half4(here);
    float t = mod(time, 3141.59265);
    float band = 0.5 + 0.5 * sin(p.x * 6.0 + t * 0.3 + 2.0 * sin(p.y * 9.0 - t * 0.2));
    float amount = band * smoothstep(0.5, 1.0, p.y) * 0.18 * mix(0.75, 1.0, dark) * open;
    return half4(here * (1.0 - amount) + float4(float3(info.rgb) * amount, amount));
}
```

### Shader: a one-off

```glsl
// @name Flash
// @description A one-off: the accent color washes over the page once and fades
// @suits dark good, light good, wallpaper good
// @once 2

uniform shader content;
uniform float progress;
layout(color) uniform half4 accent;

half4 main(float2 xy) {
    float4 here = float4(content.eval(xy));
    if (progress <= 0.0 || progress >= 1.0) return half4(here);
    float amount = sin(3.14159265 * progress) * 0.25;
    return half4(here * (1.0 - amount) + float4(float3(accent.rgb) * amount, amount));
}
```
