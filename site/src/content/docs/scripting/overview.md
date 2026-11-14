---
title: Scripting overview
description: Lua 5.4 and JavaScript scripts in Kolahal, timers, notifications, status lines, command scripts and the sandbox.
---

Scripts are small programs that run inside the launcher. A script can:

- fetch things from the web,
- print lines into the log,
- post notifications,
- set a status line that you can pin at the top,
- become a new terminal command.

You write scripts in **Lua 5.4** or **JavaScript** (QuickJS). Both languages get the same
[API](/scripting/api/).

## Your first script

```sh
script new hn              # hn.lua from an RSS template, opens the editor
script new news.js         # JavaScript instead (or: script new news js)
script new weather --cmd   # a command template that takes arguments
```

You can also open the script manager (`scripts`) and tap **Add script → New script**. Then:

```sh
script run hn              # run it now
script every hn 30m        # and every 30 minutes
```

The fastest way to start is the [script library](/scripting/library/). `script lib` lists 36
ready-made scripts. `script install weather` installs one, with useful timers already set.

## Lua or JavaScript?

|  | Lua 5.4 | JavaScript (QuickJS) |
| --- | --- | --- |
| Network calls | block (this is fine: scripts never run on the UI thread) | `async`: `await http.get(…)`; `Promise.all` works |
| JSON | `json.encode` / `json.decode` | built-in `JSON` |
| Print | `log(…)` or `print(…)` | `log(…)`, `console.log`, `console.error` |
| Arguments | `args` (table), `argline` (string) | `args` (array), `argline` (string) |
| Top level | `return value` prints it | `return value` prints it; top-level `await` works |

Use the language you like. The bundled library has scripts in both.

## Running scripts

A script runs in these cases:

- **On demand**: with `script run <name> [args…]`, the **Run** button, or `Ctrl+R` in the editor.
- **On an interval**: `script every <name> 30m`. You can use `15m`, `2h`, `1d`, a number of
  minutes, or `off`.
- **At daily times**: `script at <name> 08:00,18:30` (or `off`). You can also set them in the
  script's sheet: type them in **Daily at**, or tap its clock to pick a time.
- **As a command**, if it has an [`@command`](#command-scripts) line.
- **On an event**, if it has an [`@on`](#event-triggers) line.

Timer rules:

- The shortest interval is 15 minutes. This is Android's limit.
- A script can have an interval and daily times at the same time.
- Setting a timer turns a disabled script back on.
- `script off <name>` disables a script but keeps its timers.
- A run on an interval waits for a network only if the script goes online (`http`, `rss`).

Daily times are alarms:

- If you allow **Alarms & reminders** for Kolahal (an Android setting; `remind exact` asks for
  it), the script runs on the exact minute, even while the phone sleeps. It runs in the foreground
  for its few seconds.
- Without that permission, it runs within the hour.
- The script can tell which daily time started it ([`event.time`](/scripting/api/#event)).
- It waits for a network only if the script makes requests and there is no network.

`script ls` shows every script with its timers, whether it is a command, its notification setting
and the result of its last run.

The script manager (`scripts`, or the **Scripts** tab in Settings;
[its parts, numbered](/getting-started/parts/#scripts-tab)) shows one card per script. A card
shows:

- the script's title and file. The file of a script from Get more scripts says "shared"; one of
  yours says "yours".
- how it runs and how its last run went (red if it failed),
- **Run**, for a command,
- its on/off switch,
- a pill when something needs doing:
  - **New version · Update**: the library or GitHub has a newer version.
  - **Edited · Reset**: you edited a library or shared script, and nothing newer exists.
  - **Add key**: it needs a key ([`@secret`](#api-keys-and-other-secrets)) that has no value yet.
    The pill opens the box for the key.

A card is tinted in the theme's colour when its script is on, and faded when it is off.

The arrow on a card shows its details. The arrow at the top shows the details of all cards. The
details are, from the top:

- its status line and description.
- a line for each problem, with its button at the right end:
  - **Failed 10:42** and the error (the run's time and message are said only here).
  - **API_KEY is not set** · **Add key**.
  - **Edited by you** · **Compare**, **Reset to library**.
  - **Newer version in the library** · **Update** (and **Compare** when you edited it; then
    updating replaces your edits).
- lines with a small icon in front:
  - clock: how it runs, its next run and its last run. An event script has a pill for each event
    here.
  - terminal: how to call it (commands only).
  - shield: a pill for each thing it may do (internet, pinned at the top, notifications, what it
    declares).

A pill is filled in the theme's colour when it is on and faded when it is off. Tap a pill to switch
it. While the details are open, the pill next to the summary is hidden: the lines have the buttons.

Above the cards:

- **Search** finds a script by its name, title, description, author or tags.
- **Filters** (the sliders) opens the filter chips, in groups:
  - Status: On, Off, Failed, Needs a key.
  - How it runs: Command, Timer, Event, Pinned.
  - Source: Library, Shared (from Get more scripts), Yours.
  - Library version: Up to date, New version, Edited. For library and shared scripts.
  - Uses: what scripts declare with `@uses`.
  - Language: Lua, JavaScript.

  In a group, any chip may match: Library *or* Shared. Across groups, all must: Command *and*
  (Library or Shared). Each chip shows how many scripts it would show with the chips of the other
  groups. A chip with 0 is grey, but it still works. **Show N scripts** closes the sheet.
- The chips that are on show under the search, each with ×. **Clear** turns them all off. The
  badge on Filters counts them.
- **Sort**: by name, last run, or recently changed (the latest change to a script's code or name
  first).
- A line says how many scripts show, for example "4 of 11 scripts", and the sort.

When library or shared scripts differ from their source, a banner says how many: new versions and
edited ones. **Review** shows only those. **Update all…** lists them: those with a new version are
ticked, edited ones with nothing newer are not. **Update N** updates the ticked ones. × hides the
banner until another script differs. After an app update, one line says which library scripts
Kolahal updated by itself ([updates](/scripting/library/)). **Show** lists them; × closes the line.

Update and Reset change only the code. The timers, options, access and pinning stay. A copy you
edited asks first, and offers **Compare first** and **Export your copy first**. After an update or
a reset, **Undo** (for 10 seconds) puts your copy back.

- Tap a card for **Run**, **Edit**, **Export**, access, notifications, timers and **Delete**.
- **Add script** creates a new script. It can also add one from the library, from
  [the scripts people share](#getting-scripts-from-others), or from a file.

The script's **return value** is printed into the log. Tables and objects are printed as JSON.
The first line of a run ends with the script's name, unless the line is right under the command
that ran it. When a script fails, you can tap the error line. It opens the editor at the line that
failed.

### One-liners

```sh
lua return 6 * 7
js [1, 2, 3].map(x => x * 2)
= 2^10
```

`lua` and `js` run a short piece of code and print its value. The Lua REPL first tries
`return <code>`, so expressions work without `return`.

## Notifications

`notify(title, body, ctx)` posts a notification only when notifications are **on for that
script**:

```sh
script notify hn on
```

When notifications are off, `notify` shows nothing. The script's log lines still show the content.
If Android blocks Kolahal's notifications, a "tap to allow" line appears (once a day). See
[Notifications](/guides/notifications/#notifications-from-scripts-and-feeds).

## Status lines

`status(text)` sets one line for this script, for example `weather ▸ Pune: ☀️ +27°C`.
`status(nil)` (or `null` in JS) clears it. Kolahal uses only the first line of the text, up to 80
characters.

- The line shows on the script's card in Scripts and in `status`.
- If you pin the script, the line also shows at the top of home, in the
  [pinned header](/getting-started/basics/#the-pinned-header). It takes turns there with what's
  playing and with your other pinned scripts. There it shows the text alone, without the script's
  name, so it has room.
- A script can't pin itself.
- [`status(text, opts)`](/scripting/api/#statustext-opts) adds a bar there, and quicker updates
  while the line shows.

```sh
status                # list status lines, the pinned ones marked
script pin weather    # show its line at the top (script unpin weather takes it away)
status clear weather  # remove one (or: status clear all)
```

Tap a line at the top to run its script again. Long-press it for Run, Unpin and Edit. Every line
that a script logged also has **Pin to the top** (or **Unpin**) in its long-press menu.

## Widgets

A script can fill a card on the [widgets page](/guides/widgets/): a big number, a few lines of
text, a list, a ring, a chart, a countdown, buttons, switches, clocks, an agenda or a picture. The
script says what to show. Kolahal draws it, in the theme's colours.

```lua
-- @title Battery
-- @widget progress 1x1
-- @on charger, unplug

local b = device.battery()
widget.show{
  value = b.level or 0, max = 100,
  text = (b.level or "?") .. "%",
  label = b.charging and "charging" or "on battery",
}
```

- The `@widget` line says that the script has a widget, which kind of card it is, and how large it
  starts: `@widget <kind> [<size>]`.
  - The kinds are `stat`, `text`, `list`, `progress`, `chart`, `countdown`, `buttons`, `toggles`,
    `clock`, `agenda` and `image`.
  - The sizes are `1x1`, `2x1`, `2x2`, `4x1`, `4x2` and `4x4`, in cells of a 4-column grid.
- `widget.show{…}` fills the card. Each kind has its own fields:
  [the API reference](/scripting/api/#widgets) lists them.
- You place the widget yourself. Tap **+ Add** on the widgets page (or run `widget add`) and pick
  it in the **Kolahal** group. A script can't place itself.
- Until you place it, `widget.show` only checks what it gets, and stores nothing.
- A tap on the card can run the script again with arguments, or open a web link
  ([Taps](/scripting/api/#taps)). Without either, a tap refreshes the card.
- A row, a button, a switch and an event can have a tap of their own.
- A script can't put a terminal command behind a tap.

When the script runs:

- on its own timers and events, as any script does;
- when you place the widget;
- when the widgets page shows the widget with content older than one hour. The description's
  `staleAfter` changes the hour.

In the last two cases `event.name` is `"widget"`, and the run is quiet: what the script returns
isn't logged ([when a widget's script runs](/scripting/api/#when-a-widgets-script-runs)).

A script with no timer is a good widget. It runs only when you look at it or tap it, so it uses no
battery in the background.

Widgets need Kolahal 0.5.8 or newer. Fifteen [library](/scripting/library/#widgets) scripts have one,
for example weather, hn and countdown.

## Command scripts

Add `@command` in a comment, and the script becomes a terminal command with the script's name.

- `@desc` gives it a one-line description in `help` and in suggestions.
- `@usage` lists its arguments, in the same form as the usage lines in `help`: `[a|b]` is a
  choice, and `<city>` is a value you type. Kolahal suggests the words in it as you type (`xkcd`
  offers `xkcd random`), and `help` shows the line.

```lua
-- weather.lua
-- @command
-- @desc Current weather: weather [city]
-- @usage [<city>]
local city = argline ~= "" and argline or "London"
return (http.get("https://wttr.in/" .. city .. "?format=3").body:gsub("%s+$", ""))
```

```sh
weather paris
```

In JavaScript, use `// @command` and `// @desc …`. The arguments are in `args` and `argline`.

A command script that you run from the prompt can also call
[`launcher.run`](/scripting/api/#launcherruncommand) (with `@uses commands`). When the same script
runs on a timer, it gets no arguments. Most library scripts use this to tell the two cases apart.

When `launch.instant` is `all`, a command that takes no arguments runs as soon as you have typed
enough of its name, like the built-in commands. A script counts as taking no arguments if:

- it has an empty `@usage` line, or
- it never reads `args` or `argline`.

A script that reads them but has no `@usage` line always waits for Enter.

## Event triggers

A script can also run when something happens on the device. Add one or more `@on` lines:

```lua
-- @on unlock
-- @on charger
```

The supported events are `unlock`, `charger`, `unplug`, `wifi`, `offline`, `notification`,
`boot` and `headset`. In an event run, the script gets an `event` object. `event.name` is the
event. Some events add [more details](/scripting/api/#event).

```lua
-- @on charger
-- @on unplug
local b = device.battery()
status((b.charging and "charging " or "on battery ") .. tostring(b.level) .. "%")
```

## Describing a script

More comment lines describe a script, so the file is complete on its own. The script manager shows
them, and the [library](/scripting/library/) scripts have them too.

```lua
-- @title Hacker News
-- @desc New front-page stories since the last run
-- @author Your Name
-- @version 1.2
-- @every 1h
-- @notify
-- @tags news, tech
```

| Line | What it does |
| --- | --- |
| `@title <text>` | The display name, shown above the file name. |
| `@desc <text>` | A one-line description, shown in `help`, suggestions and the script manager. |
| `@author <name>`, `@version <x.y>` | Shown in the script's sheet in the script manager. |
| `@every <15m\|1h\|1d>`, `@at <HH:mm>[, …]` | The suggested timer. Kolahal sets it when you add the script from a file, a folder or the library. |
| `@notify` | Suggests notifications. Kolahal switches them on when you add the script. |
| `@widget <kind> [<size>]` | The script has a [widget](#widgets) for the widgets page, of this kind. The size is the one it starts with. |
| `@tags <word>[, …]` | Search keywords for Get more scripts. |
| `@requires <x.y.z>` | The oldest Kolahal version the script works with. Kolahal won't add it from a file, a folder, a backup or Get more scripts until you have that version or newer. |

## Sandbox and limits

Scripts run in a separate `:script` process. If an engine crashes, the home screen keeps working.

- A run can take at most **60 seconds** and use at most **64 MB** of memory.
  - In Lua, `string.rep` refuses results over 16 MB.
  - If memory grows past 128 MB between checks, the run is stopped and the script process restarts.
- A run can log at most **500 lines** and **256K characters**. Error lines count, and so do each
  line's link and other details. Past either limit, one line says that the rest wasn't logged.
- A run can send at most **10 commands** with `launcher.run`. A script started by another script's
  `launcher.run` can't send any. This stops scripts from starting each other without end.
- **Lua**:
  - Each run gets a fresh state.
  - These are removed: `java`, `io`, `package`, `require`, `debug`, `dofile`, `loadfile` and the
    process-level `os` functions. `os.time`, `os.date`, `os.clock` and `os.difftime` remain.
  - Only text chunks can be loaded.
  - An instruction-count hook enforces the timeout and the memory cap. The hook also covers
    coroutines, and `pcall`, `xpcall` and `coroutine.resume` can't catch these errors.
  - If a C function runs past the deadline, a watchdog kills the script process.
- **JavaScript**: QuickJS with only the bindings in the [API](/scripting/api/), a 64 MB memory limit
  and an evaluation timeout. There is no file, process or network access except through the API.
- `sleep(ms)` waits at most 5000 ms per call.
- If something is stuck, `restart scripts` stops the script process. Scheduled scripts start it
  again.

## What a script may use

Some things need permission. A script can use your notifications, the clipboard, the list of your
apps, running commands, Do Not Disturb, the work profile and your own lists (todos, notes,
reminders, the calendar, feeds) only if it asks for them with a `@uses` line at the top:

```lua
-- @uses notifications, clipboard
```

| `@uses` | allows |
| --- | --- |
| `notifications` | [`notifications.recent`](/scripting/api/#notificationsrecentn) and the `@on notification` trigger |
| `clipboard` | [`clipboard.get` and `clipboard.set`](/scripting/api/#clipboardget--clipboardsettext) |
| `apps` | [`apps.list`](/scripting/api/#appslist) |
| `commands` | [`launcher.run`](/scripting/api/#launcherruncommand): terminal commands, and other scripts by name, when you start the script from the prompt |
| `dnd` | [`device.dnd` and `device.setDnd`](/scripting/api/#devicednd--devicesetdndmode): reading and setting Do Not Disturb |
| `work` | [`device.work` and `device.setWork`](/scripting/api/#devicework--devicesetworkon): reading, pausing and resuming the work profile |
| `todos` | [`todos.list`](/scripting/api/#todoslist) and [`todos.setDone`](/scripting/api/#todossetdoneid-done): reading your todos, and marking one done or open again |
| `notes` | [`notes.list`](/scripting/api/#noteslistn): reading your notes |
| `reminders` | [`reminders.list`](/scripting/api/#reminderslist): reading the reminders that are set |
| `calendar` | [`calendar.events`](/scripting/api/#calendareventsdays): reading the events of the next days from the phone's calendar. Kolahal also needs Android's calendar permission |
| `feeds` | [`feeds.unread`](/scripting/api/#feedsunreadn): reading the unread items of the feeds you follow |

- If a script calls one of these without its `@uses` line, it stops with an error. The error says
  what to add.
- Each item is separate. A script with `@uses notes` can't read your todos or your calendar.
- `todos` is the only list a script can change, and only in one way: it can mark a todo done or
  open again. It can't add, change or delete one.
- Code you type at the prompt (`lua`, `js`, `=`) may use everything.
- Internet access has no `@uses` line, and it covers every address. A script with internet access
  can also reach this phone and your local network ([HTTP](/scripting/api/#http)).

### Switching access off

Open a script's sheet (Scripts → tap the script). It has two groups:

- **Access**: a switch for each thing the script uses.
- **Events**: a switch for each event in its `@on` lines.

The pills in the card's details switch the same things, in the same two groups.

Kolahal reads what a script uses from its code:

- **Internet**: when it uses `http` or `rss`, puts links or pictures on its lines (`url`, `image`),
  or runs commands (`launcher.run`, because a search goes to a site).
- **Pinned at the top**: when it calls `status`.
- **Post notifications**: when it calls `notify`.
- Everything its `@uses` line names.

A script gets only these. For example, a script that never mentions `http` can't go online.

When a switch is off, a call returns nothing if that is a valid result. Otherwise the script stops:

| Switch | When it is off, the script… |
| --- | --- |
| Internet | stops at its first `http` or `rss` call, with a line you can tap to allow it again. Its lines have no links or pictures. |
| Pinned at the top | is off until you pin it. Its `status()` line shows only on its card and in `status`, not at the top of home. |
| Post notifications | runs as before. `notify()` returns false. |
| An `@uses` item | gets nothing. `apps.list()`, `notifications.recent()` and the calls for [your lists](/scripting/api/#your-lists) return empty lists, `clipboard.get()` returns nil, `clipboard.set()` does nothing, and `launcher.run` and `todos.setDone` return false. |
| An `@on` event | doesn't run on that event. |

- A script's [widget](#widgets) has no switch. Placing the widget on the widgets page turns it on,
  and removing it turns it off. While it is off, `widget.show` stores nothing.
- To unpin a script, long-press its line at the top of home and tap **Unpin**.
- In the terminal, `script access <name>` lists the switches. `script allow <name> <what…>` and
  `script deny <name> <what…>` change them. `notify` is the name of the notifications switch.
- A script can check a switch with [`allowed(what)`](/scripting/api/#allowedwhat) and do something
  else when it is off.

Imported scripts and updates:

- Scripts you import (from a file, a folder or a backup) are **switched off**. So is a script whose
  code an import replaces. You have to allow its access again.
- When you switch a script on in the script manager, it lists everything the script declares with
  `@uses`, all on. You can switch off what you don't want first.
- `script on <name>` allows everything and lists it.
- If you turn a script on another way (you give it a timer, or you import one that was on), it
  doesn't get what it asks for. A line in the log lets you allow it.
- If an update from the library or Get more scripts declares something new, or runs on a new event,
  that new item starts switched off. A line in the log offers to allow it.
- Restoring a backup brings back only the switches you turned off, never the ones you turned on.

Links on a line (`url`, `image`, or a notification's link) must be web links. Kolahal drops any
other kind (`tel:`, `upi:`, an app's own link). A line's `copy` must be the text the line shows,
part of it, or its link.

## API keys and other secrets

A script that needs an API key or a token doesn't keep it in its code. Instead, it names the key
with an `@secret` line, and you set the value on the phone:

```lua
-- @secret OWM_KEY api.openweathermap.org Your OpenWeather API key
local key = secrets.get("OWM_KEY")
if not key then return "set OWM_KEY in the script's sheet" end
local r = http.get("https://api.openweathermap.org/data/2.5/weather?q=Pune&appid=" .. key)
```

The `@secret` line gives:

1. the key's name,
2. the site the key may be sent to (its subdomains are included; separate several sites with
   commas; `*` means any site),
3. a short description.

Set the value in the script's sheet (Scripts → tap the script → **Secrets**), or with
`script secret <name> <NAME>`. That command opens an input box that the keyboard doesn't learn
from. If you switch on a script whose keys aren't set, a line appears in the log. Tap it to set
them.

- **Storage**: values are encrypted with a key kept in Android's Keystore. They stay on this phone:
  they are not in the [backup file](/guides/backup/), Android's backups or exported scripts.
  Deleting a script deletes its values.
- **What the script gets**: `secrets.get` returns a placeholder, never the value. It returns nil
  until the value is set.
  - The script puts the placeholder where the request needs the key.
  - Kolahal puts the value in its place only in a request over https to a site that the `@secret`
    line names.
  - The placeholder can't be part of the site's name (`https://<key>.example.com`), because the
    name goes to the address lookup unencrypted.
  - A script can't work on the key itself (encode it, sign with it).
  - Reading a name that has no `@secret` line stops the script with an error.
  - Code typed at the prompt can't read secrets.
- **Display**: a value must be at least 4 characters. It is never shown: in log lines, the header,
  notifications, results and errors it appears as `••••`.
- **Where it can go**: a value goes only to the sites that its `@secret` line named when you set
  it.
  - A request that carries it anywhere else stops the script. This includes redirects.
  - The clipboard, the script's store and `launcher.run` reject a placeholder. Anywhere else (a
    line, a notification's link) it stays a placeholder, shown as `••••`.
  - If an update or another file names other sites for the key, Kolahal doesn't use the key until
    you set it again. A line in the log tells you.
  - If new code has no `@secret` line for the key, its value is deleted.
- **Trust**: a script never holds the value, so it can't send it anywhere else. But it can still use
  the key at its site in any way the site allows. Give keys only to scripts you trust.

## Options

Options are settings that a script reads when it runs, such as a city, a feed's address or units.
They are `@option` lines, so you can change them without editing the code. If you edit a library
script, it stops getting the library's updates. If you only change its options, it still gets
them.

```lua
-- @option city London The city to show
-- @option units metric|imperial Units for temperatures
local r = http.get("https://wttr.in/" .. options.city .. "?format=3&" .. (options.units == "metric" and "m" or "u"))
```

The `@option` line gives:

1. the option's name (letters, digits and `_`),
2. its default: one word, `"several words"` in quotes, or `""` for none. `metric|imperial` makes a
   choice, and the first choice is the default.
3. a short description.

The script reads `options.city`. The value is always text, so convert it with `tonumber` or
`Number()` if you need a number.

To change an option:

- in the script's sheet: Scripts → tap the script → **Options**, or
- with `script set <name> <option> <value>`. `script set <name>` lists the options, and `--reset`
  puts the default back.

Your values are stored apart from the code, by script name.

- They are kept through updates and renames.
- They are included in the [backup file](/guides/backup/).
- They are deleted with the script.
- If a value is no longer one of the choices (for example, an update removed it), the script reads
  the default.

## Getting scripts from others

**Scripts → Add script → Get more scripts** lists the scripts that people share in
[kolahal-scripts](https://github.com/mtwabpin/kolahal-scripts) on GitHub. Each script is reviewed
before it is added.

1. Tap a script to see what it does, what it uses and its code.
2. Tap **Install**.
3. Choose what it can use (everything starts on).
4. The script switches on, with the timer it suggests.

Updates:

- When Get more scripts (or `script get`) finds a newer version of a shared script, its card in the
  script manager shows **New version · Update**. The script's sheet has an **Update to …** button.
  Both use the list from the last time Get more scripts read it, so nothing is downloaded until
  you update.
- Anything new that the update asks for starts switched off.
- A copy you edited is replaced only when you ask: Update asks first. A copy you edited with nothing
  newer shows **Edited · Reset**, which brings the shared version back.
- Updates and resets change only the code, and **Undo** puts your copy back.

In the terminal:

- `script get` lists the scripts.
- `script get <name>` installs or updates one. A script that asks for access arrives switched
  off, with a line naming what it asks for. Tap the line (or type `script on <name>`) to switch it
  on and allow that.
- The list is kept for an hour. `--refresh` reads it again.
- `--replace` replaces a copy you edited.

To share one of your scripts, see the repository's
[contributing guide](https://github.com/mtwabpin/kolahal-scripts/blob/main/CONTRIBUTING.md).

The scripts named `template-…` are starting points. Their comments explain each part. There is a
template for a command, a timer, an API key, an event and fetching from the web, each in Lua and in
JavaScript (the JavaScript one ends in `-js`). `template-logs` shows every kind of line a script can
log, next to the code that makes it. Install one and [rename it](#renaming-a-script) to make it yours.
See [Templates](/scripting/library/#templates).

## Renaming a script

Tap the ✎ next to a script's name in its sheet (Scripts → tap the script), or run
`script mv <name> <new-name>`.

- A command script then runs by its new name.
- Its timers, access switches, stored data, keys and status line stay with it.
- A script from the library or Get more scripts becomes yours. It no longer gets updates, and you
  can install the original again.
- Lines already in the log keep the old name.

## Moving scripts

Scripts are part of the [backup file](/guides/backup/). You can also import and export them as
single `.lua` / `.js` files, or sync them with a folder (`script dir`, `script export`,
`script import`).
