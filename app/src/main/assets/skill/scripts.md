## Scripts

### The smallest script

```lua
-- @command
-- @desc Says hello: hello [name]
-- @usage [<name>]
return "hello, " .. (argline ~= "" and argline or "world")
```

Saved as `hello`, `hello Asha` prints the line. What a run returns is printed (tables and objects as JSON). An error stops the run with a red line; a tap on it opens the editor at the failing line.

Lua and JavaScript have the same API. In Lua every call blocks. In JavaScript the code runs inside an async function, so top-level `await` and `return` work, and `Promise.all` runs requests side by side. There is no `require`, and no file, process or network access but the API below.

### Script header

Header lines are comments at the top: `-- @name value` in Lua, `// @name value` in JavaScript.

| Line | What it does |
| --- | --- |
| `@command` | Runs by its name at the prompt: `<name> args…`. |
| `@desc <text>` | One line, shown in `help`, suggestions and Scripts. |
| `@usage [<args>]` | Its arguments, as a usage line: `[<city>]`; its words are suggested as you type. Empty: it takes none. |
| `@uses <what>, …` | What it may use: see "What a script may use". A call without its item stops with an error. |
| `@on <event>, …` | Events: `unlock`, `charger`, `unplug`, `wifi`, `offline`, `notification`, `boot`, `headset`. `notification <package> …` runs for those apps only. |
| `@every 1h` | Its timer: `15m` (the shortest), `2h`, `1d`, a number of minutes, or `off`; at most a week. Runs that go online wait for a network. |
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

In a run for the second or later widget of the script, `event.instance` is that widget's number as text (`"2"`).

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

`ctx` (a table / object): `url` (a tap opens this web link), `copy` (what Copy text copies: part of what the line shows, or its url), `image` (a picture's http(s) link, shown under the line), `mono = "1"` (terminal font, for columns), `gap = true` (a small space above: a new item of a list), `actions` (up to 3 buttons, each a two-item list `{ "Snooze", "snooze 30" }`: label, then the words this script runs with on a tap). Other keys are dropped.

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
| `web.open(url)` | → bool. Opens an http(s) link in a user-started run; 3 a run, no secret |
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

Internet has no `@uses` line: a script whose code mentions `http`, `rss`, `url`, `image`, `web` or `launcher.run` has it, unless the user switches it off. Each `@uses` item is a switch of its own.

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
