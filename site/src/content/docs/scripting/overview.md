---
title: Scripting overview
description: Lua 5.4 and JavaScript scripts in Kolahal, timers, notifications, status lines, command scripts and the sandbox.
---

Scripts are small programs that run inside the launcher. They can fetch things from the web,
print lines into the log, post notifications, set a status line you can pin at the top, or become new
terminal commands. Write them in **Lua 5.4** or **JavaScript** (QuickJS). Both languages get the
same [API](/scripting/api/).

## Your first script

```sh
script new hn              # hn.lua from an RSS template, opens the editor
script new news.js         # JavaScript instead (or: script new news js)
script new weather --cmd   # a command template that takes arguments
```

Or tap **Add script → New script** in the script manager (`scripts`). Then:

```sh
script run hn              # run it now
script every hn 30m        # and every 30 minutes
```

The fastest way to start is the [script library](/scripting/library/): `script lib` lists 16 ready-made
scripts and `script install weather` installs one with sensible timers.

## Lua or JavaScript?

|  | Lua 5.4 | JavaScript (QuickJS) |
| --- | --- | --- |
| Network calls | block (fine, scripts never run on the UI thread) | `async`: `await http.get(…)`; `Promise.all` works |
| JSON | `json.encode` / `json.decode` | built-in `JSON` |
| Print | `log(…)` or `print(…)` | `log(…)`, `console.log`, `console.error` |
| Arguments | `args` (table), `argline` (string) | `args` (array), `argline` (string) |
| Top level | `return value` prints it | `return value` prints it; top-level `await` works |

Pick whichever you like; the bundled library has both.

## Running scripts

A script runs:

- **on demand**: `script run <name> [args…]`, the **Run** button, or `Ctrl+R` in the editor,
- **on an interval**: `script every <name> 30m` (`15m`, `2h`, `1d`, a number of minutes, or `off`),
- **at daily times**: `script at <name> 08:00,18:30` (or `off`), or in the script's sheet: type them in
  **Daily at**, or tap its clock to pick one,
- **as a command**, if it is marked [`@command`](#command-scripts),
- **on an event**, if it declares [`@on`](#event-triggers).

Intervals have Android's 15-minute minimum. An interval and daily times can be combined. Setting a
timer turns a disabled script back on; `script off <name>` disables it without losing the timers.
A run on an interval waits for a network only if the script goes online (`http`, `rss`).

Daily times are alarms. With **Alarms & reminders** allowed for Kolahal (Android's setting; `remind
exact` asks for it), a script runs on the minute, even while the phone sleeps, and in the
foreground for its few seconds; otherwise it runs within the hour. A script run at a daily time can tell which time it was ([`event.time`](/scripting/api/#event)), and it waits for a network only if the
script makes requests and there is none.

`script ls` shows every script with its timers, whether it is a command, notification state and the
last run's result. The script manager (`scripts`, or the **Scripts** tab in Settings;
[its parts, numbered](/getting-started/parts/#scripts-tab)) shows a card per
script: its title, how it runs and how its last run went (red if it failed), **Run** for a command,
and its on/off switch; a card is tinted in the theme's colour when its script is on, and faded when it's off. The arrow on a card, or the one at the top for all of them, shows its details: its status line, next run, usage, and a pill for everything it may do (internet, pinned at the top, notifications, what it declares, its events). A pill is filled in the theme's colour when on and faded when off. Tap it to switch it.
A library script you edited (its summary says "edited") gets **Compare** and **Reset to library
version** there too.
Chips filter the cards (commands, timers, events, pinned, failed, off, needs a key, differs
from the library, and what they use), and the search icon finds one by name.
Tap a card for **Run**, **Edit**, **Export**, access, notifications, timers and **Delete**.
**Add script** creates a new one, or adds one from the library, from
[the scripts people share](#getting-scripts-from-others) or from a file.

The script's **return value** is printed into the log (tables and objects as JSON). The first line
of a run ends with the script's name, unless it's right under the command that ran it. When a script fails, the error line is
tappable and opens the editor at the failing line.

### One-liners

```sh
lua return 6 * 7
js [1, 2, 3].map(x => x * 2)
= 2^10
```

`lua` and `js` evaluate a snippet and print its value. The Lua REPL tries `return <code>` first, so
expressions work without `return`.

## Notifications

`notify(title, body, ctx)` only posts when notifications are **on for that script**:

```sh
script notify hn on
```

With notifications off, `notify` does nothing visible, but the script's log lines still show the
content. If Android blocks Kolahal's notifications, a "tap to allow" line appears (once a day). See
[Notifications](/guides/notifications/#notifications-from-scripts-and-feeds).

## Status lines

`status(text)` sets one line for this script, like `weather ▸ Pune: ☀️ +27°C`, and `status(nil)`
(or `null` in JS) clears it. Only the first line is used, up to 80 characters. The line shows on the
script's card in Scripts and in `status`. Pin the script and it shows at the top of home too, in
the [pinned header](/getting-started/basics/#the-pinned-header), taking turns with what's playing
and the other scripts you pinned; a script can't pin itself. [`status(text, opts)`](/scripting/api/#statustext-opts)
adds a bar there, and quicker updates while it shows.

```sh
status                # list status lines, the pinned ones marked
script pin weather    # show its line at the top (script unpin weather takes it away)
status clear weather  # remove one (or: status clear all)
```

Tapping a line at the top runs its script again; long-pressing it offers Run, Unpin and Edit. Any
line a script logged has **Pin to the top** (or **Unpin**) in its long-press menu too.

## Command scripts

Add `@command` in a comment and the script becomes a terminal command named after the script.
`@desc` gives it a one-line description in `help` and in suggestions.
`@usage` lists its arguments, written like the usage lines in `help`: `[a|b]` is a
choice, `<city>` a value you type. The words in it are suggested as you type (`xkcd` offers
`xkcd random`), and `help` shows the line.

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

In JavaScript use `// @command` and `// @desc …`. Arguments arrive in `args` and `argline`. A
command script run from the prompt may also call [`launcher.run`](/scripting/api/#launcherruncommand) (with `@uses commands`). When
the same script runs on a timer, it gets no arguments, which is how most library scripts tell the
two cases apart.

A command that takes no arguments runs as soon as you have typed enough of its name, like the built-in
ones, when `launch.instant` is `all`. That's a script with an empty `@usage` line, or one that never
reads `args` or `argline`. A script that reads them but has no `@usage` line always waits for Enter.

## Event triggers

A script can also run when something happens on the device, by declaring one or more `@on`
directives:

```lua
-- @on unlock
-- @on charger
```

The supported events are `unlock`, `charger`, `unplug`, `wifi`, `offline`, `notification`,
`boot` and `headset`. During an event run, the script gets an `event` object: `event.name` is the
event, and some events add [details](/scripting/api/#event).

```lua
-- @on charger
-- @on unplug
local b = device.battery()
status((b.charging and "charging " or "on battery ") .. tostring(b.level) .. "%")
```

## Describing a script

A few more comment lines describe a script, so the file is complete on its own: the script manager shows them, and the [library](/scripting/library/) scripts carry them too.

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
| `@title <text>` | its display name, shown above its file name |
| `@desc <text>` | a one-line description, in `help`, suggestions and the script manager |
| `@author <name>`, `@version <x.y>` | shown in its sheet in the script manager |
| `@every <15m\|1h\|1d>`, `@at <HH:mm>[, …]` | the timer it suggests, set when it is added from a file, a folder or the library |
| `@notify` | it suggests posting notifications, switched on when it is added |
| `@tags <word>[, …]` | search keywords for Get more scripts |
| `@requires <x.y.z>` | the minimum Kolahal version it needs: Kolahal won't add it from a file, a folder, a backup or Get more scripts until it has that version or newer |

## Sandbox and limits

Scripts run in a separate `:script` process, so a crashing engine can't take down the home screen.

- **60 seconds** per run, and a **64 MB** memory cap. In Lua, `string.rep`
  refuses results over 16 MB, and a run whose memory grows past 128 MB between checks is stopped
  (the script process is restarted).
- At most **500 log lines** and **256K characters** per run, error lines and each line's link and
  other details included; past either, one line says the rest weren't logged.
- At most **10 commands** per run with `launcher.run`. A script started by another script's
  `launcher.run` can't send any, so scripts can't start each other endlessly.
- **Lua**: a fresh state per run. `java`, `io`, `package`, `require`, `debug`, `dofile`, `loadfile`
  and process-level `os` functions are removed (`os.time`, `os.date`, `os.clock`, `os.difftime`
  remain), and only text chunks can be loaded. The timeout and memory cap are enforced by an
  instruction-count hook that also covers coroutines, and `pcall`, `xpcall` and `coroutine.resume`
  can't swallow them. A watchdog kills the script process if a C function outlives the deadline.
- **JavaScript**: QuickJS with only the bindings in the [API](/scripting/api/), a 64 MB memory limit and an
  evaluation timeout. There is no file, process or network access besides the API.
- `sleep(ms)` is capped at 5000 ms per call.
- `restart scripts` stops the script process if something is stuck. Scheduled scripts start it again.

## What a script may use

Scripts can use your notifications, the clipboard, the list of your apps, running commands, Do Not Disturb and the work profile only if they ask, with a `@uses` line at the top:

```lua
-- @uses notifications, clipboard
```

| `@uses` | allows |
| --- | --- |
| `notifications` | [`notifications.recent`](/scripting/api/#notificationsrecentn) and the `@on notification` trigger |
| `clipboard` | [`clipboard.get` and `clipboard.set`](/scripting/api/#clipboardget--clipboardsettext) |
| `apps` | [`apps.list`](/scripting/api/#appslist) |
| `commands` | [`launcher.run`](/scripting/api/#launcherruncommand): terminal commands, and other scripts by their names, when you start it from the prompt |
| `dnd` | [`device.dnd` and `device.setDnd`](/scripting/api/#devicednd--devicesetdndmode): seeing and setting Do Not Disturb |
| `work` | [`device.work` and `device.setWork`](/scripting/api/#devicework--devicesetworkon): seeing, pausing and resuming the work profile |

Calling one of these without its `@uses` line stops the script with an error saying what to add.
Code you type at the prompt (`lua`, `js`, `=`) may use everything. Internet access has no `@uses` line and covers every address: a script that has it can also reach this phone and your local network
([HTTP](/scripting/api/#http)).

### Switching access off

Each script's sheet (Scripts → tap the script) has an **Access** group, with a switch for
everything it uses, and an **Events** group, with one for each event of its `@on` lines; the pills
in its card's details switch the same things, in the same two groups. What it uses is read from its
code: **Internet** when it uses `http` or `rss`, puts links or pictures on its lines (`url`,
`image`), or runs commands (`launcher.run`, as a search reaches a site); **Pinned at the top** when it calls `status`; **Post notifications** when it calls `notify`;
and what its `@uses` line names. A script gets only those: one that never mentions `http` can't go
online. With a switch off, a call returns nothing where that's a valid result, and the script stops where it isn't:

| Switch | Switched off, the script… |
| --- | --- |
| Internet | stops at its first `http` or `rss` call, with a line to tap to allow it again; its lines carry no links or pictures |
| Pinned at the top | off until you pin it: its `status()` line shows only on its card and in `status`, not at the top of home |
| Post notifications | runs as before; `notify()` returns false |
| An `@uses` item | gets nothing: `apps.list()` and `notifications.recent()` return empty lists, `clipboard.get()` returns nil and `clipboard.set()` does nothing, and `launcher.run` returns false |
| An `@on` event | doesn't run on that event |

Long-press a pinned script's line at the top of home for **Unpin**. In the terminal,
`script access <name>` lists the switches, and `script allow <name> <what…>` and
`script deny <name> <what…>` change them (`notify` is the notifications switch). A script can
check with [`allowed(what)`](/scripting/api/#allowedwhat) and do something else.

Imported scripts (from a file, a folder or a backup) are **switched off**, and so is a script whose code one of them replaces. You have to allow its access again. Switching one on in the script manager lists what it declares with `@uses`, all on, so you can switch off what you don't want first. `script on <name>` allows everything and lists it. Turning a script on another way (giving it a timer, or importing one that was on) doesn't allow anything it asks for: a line in the log lets you allow it. If an update from the library or Get more scripts declares something more,
or runs on a new event, that starts switched off and a line in the log offers to allow it.
Restoring a backup brings back only the switches you turned off, never the ones you turned on.

Links a script puts on a line (`url`, `image`, or a notification's link) must be web links:
anything else (`tel:`, `upi:`, an app's own link) is dropped. A line's `copy` must be what the line
shows, part of it, or its link.

## API keys and other secrets

A script that needs an API key or a token doesn't keep it in its code. It names the key with an
`@secret` line, and you set the value on the phone:

```lua
-- @secret OWM_KEY api.openweathermap.org Your OpenWeather API key
local key = secrets.get("OWM_KEY")
if not key then return "set OWM_KEY in the script's sheet" end
local r = http.get("https://api.openweathermap.org/data/2.5/weather?q=Pune&appid=" .. key)
```

The line gives the key's name, the site it may be sent to (and its subdomains; several with commas,
`*` for any), and a short description. Set the value in the script's sheet (Scripts → tap the script → **Secrets**),
or with `script secret <name> <NAME>`, which opens a box the keyboard doesn't learn from. Switching on a script whose keys aren't set adds a line to the log. Tap it to set them.

- Values are encrypted with a key kept in Android's Keystore, and stay on this phone: they are left
  out of the [backup file](/guides/backup/), Android's backups and exported scripts. Deleting a script deletes them.
- `secrets.get` returns a placeholder for the value, never the value, or nil until the value is set.
  The script puts it where the request needs the key, and the value goes in its place only in a
  request to a site the `@secret` line names, over https. A script that needs to work on the key
  itself (encode it, sign with it) can't. Reading a name without its `@secret` line stops the script
  with an error, and code typed at the prompt can't read secrets.
- A value must be at least 4 characters and is never displayed: in log lines, the header, notifications, results and errors it appears as `••••`.
- A value goes only to the sites its `@secret` line named when you set it. A request carrying it
  anywhere else stops the script, redirects included. The clipboard, the script's store and
  `launcher.run` reject a placeholder; anywhere else (a line, a notification's link) it remains a placeholder, shown as `••••`. If an update or another file names
  other sites for the key, it isn't used until you set it again; a line in the log says so. New code without the key's `@secret` line deletes its value.
- A script never holds the value, so it can't send it anywhere else. It can still use the key at its
  site the way the site allows, so give keys to scripts you trust.

## Options

Settings a script reads as it runs (a city, a feed's address, units) are `@option` lines, so you
change them without editing its code. An edited library script stops getting the library's updates; one whose options you changed still gets them.

```lua
-- @option city London The city to show
-- @option units metric|imperial Units for temperatures
local r = http.get("https://wttr.in/" .. options.city .. "?format=3&" .. (options.units == "metric" and "m" or "u"))
```

The line gives the option's name (letters, digits and `_`), its default, and a short description. A
default is one word, `"several words"` in quotes, or `""` for none; `metric|imperial` makes a
choice, the first being the default. The script reads `options.city`, always as text (convert it with `tonumber` or `Number()`). Change one in the script's sheet (Scripts → tap the script → **Options**), or with
`script set <name> <option> <value>`; `script set <name>` lists them, and `--reset` puts the
default back.

- Your values are stored separately from the code, by script name. They are kept through updates and renames, included in the [backup file](/guides/backup/), and deleted with the script.
- A value that isn't one of the choices (an update dropped it) reads as the default.

## Getting scripts from others

**Scripts → Add script → Get more scripts** lists the scripts people share in
[kolahal-scripts](https://github.com/mtwabpin/kolahal-scripts) on GitHub. Each one is reviewed before it is added. Tap one to see what it does, what it uses and its code. When you tap **Install**, you choose what it can use (everything starts on), then it switches on with the timer it suggests.

When Get more scripts (or `script get`) finds a newer version of a shared script you haven't
edited, the script manager shows "update available" and its sheet has an **Update to …** button.
Anything new it asks for starts switched off. A copy you edited is only replaced when you say so.

In the terminal, `script get` lists them and `script get <name>` installs or updates one. The list
is kept for an hour; `--refresh` reads it again, and `--replace` replaces a copy you edited. To
share one of yours, see the repository's [contributing guide](https://github.com/mtwabpin/kolahal-scripts/blob/main/CONTRIBUTING.md).

The scripts named `template-…` are starting points, with comments that explain each part: a
command, a timer, an API key, an event, and one in JavaScript. Install one and
[rename it](#renaming-a-script) to make it yours.

## Renaming a script

Tap the ✎ beside a script's name in its sheet (Scripts → tap the script), or run
`script mv <name> <new-name>`. A command script then runs by its new name. Its timers, access
switches, stored data, keys and status line stay with it. A script from the library or Get more
scripts becomes yours: it no longer gets their updates, and the original can be installed again.
Lines already in the log keep the old name.

## Moving scripts

Scripts are part of the [backup file](/guides/backup/), and can be imported and exported as
single `.lua` / `.js` files or synced with a folder (`script dir`, `script export`, `script import`).
