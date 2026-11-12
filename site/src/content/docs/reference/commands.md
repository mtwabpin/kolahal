---
title: Commands
description: Every built-in Kolahal command with its usage, aliases and subcommands.
---

This page lists every built-in command, in about the same sections as `help`.

If what you type isn't a command, an [alias](/guides/apps/#aliases) or a
[script command](/scripting/overview/#command-scripts), Kolahal matches it against app names. You can
[turn off](#turning-commands-off) the commands you don't use.

How to read the usage:

- `<angle brackets>`: required
- `[square brackets]`: optional
- `a|b`: one of the choices

## Apps

| Usage | Aliases | Description |
| --- | --- | --- |
| `open <app>[:shortcut] [-w\|-p\|-s]` | `launch` | open an app, or one of its shortcuts (`open maps:home`) |
| `shortcuts <app>` | | list an app's launcher shortcuts; tap one to start it |
| `activity <app> [<name>]` | | list the screens of an app that other apps may open; tap one to open it. With a name, open the one that matches it, or list them if several do (`activity settings wifi`). Personal profile apps only |
| `apps [work\|personal\|private\|hidden] [-l\|-a]` | `ls` | show the app grid (`-l`: one app per line; `-a`: include hidden apps) |
| `info <app> [-w]` | | open app info |
| `uninstall <app> [-w]` | | uninstall an app |
| `hide <app> [-w]` | | hide an app from lists and search |
| `unhide <app>` | | show a hidden app again (`apps hidden` lists hidden apps) |
| `pin <app>` | `fav` | make an app a favourite. Favourites come first in the suggestions on an empty prompt (`suggestions.idle`) and have a star in the app selector. `pin` alone lists them |
| `unpin <app>` | `unfav` | remove a favourite |
| `pins` | `favs` | show a grid of pinned apps |
| `rename <app> = <name>` | | give an app your own label; `rename <app> =` restores the original |
| `usage [<days>]` | `screentime` | screen time for each app (1–30 days; needs Usage Access) |
| `installs [<n>]` | `updates` | recent app installs, updates and uninstalls |

## People

| Usage | Aliases | Description |
| --- | --- | --- |
| `call <name\|number>` | `dial` | call a contact or number (in the Play build, without a name it opens the contact picker) |
| `sms <name\|number> [<text>]` | `text` | start a text message (in the Play build, without a name it opens the contact picker) |
| `contacts [<query>]` | `contact` | find contacts (without a query, shows favourites). In the Play build, pick a contact, or use `--forget <name>` |
| `calls [<n>]` | | recent incoming, outgoing and missed calls (20 by default; `calls --allow` asks for call log access) |

## Web

| Usage | Aliases | Description |
| --- | --- | --- |
| `search <query>` | `g` | search the web (set the search engine with `config search.engine`) |
| `yt <query>` | `youtube` | search YouTube |
| `wiki <query>` | | search Wikipedia |
| `maps <place>` | `map` | find a place in your maps app |
| `play <query>` | `store` | search the Play Store |
| `url <address>` | `web` | open a link |

## Feeds

| Usage | Aliases | Description |
| --- | --- | --- |
| `feed [add <url>\|ls\|rm\|fetch\|read\|all\|open\|notify\|every\|on\|off] [<id>]` | `feeds` | RSS/Atom subscriptions with unread counts |
| `rss <url>` | | fetch a feed into the log once |
| `notifs [all\|enable\|mute <app>\|unmute <app>\|muted\|log [on\|off]\|clear]` | `notifications-feed` | your notifications in the terminal |

`feed` subcommands:

| Subcommand | Description |
| --- | --- |
| `feed`, `feed ls` | list subscriptions with unread counts, check interval and status |
| `feed add <url>` | subscribe to a feed and fetch it now |
| `feed url <id> <url>` | change a feed's address (for a typo, or a feed that moved); its items and settings stay |
| `feed rm <id>` | unsubscribe and delete the feed's items |
| `feed fetch [id]` | refresh now (alias `refresh`) |
| `feed read [id]` | show unread items, then mark them as read |
| `feed all [id]` | show the 40 newest stored items, read or unread |
| `feed open [id]` | open up to 5 unread items in the browser and mark them as read |
| `feed notify <id> on\|off` | turn notifications for new items on or off |
| `feed every <id> <30m\|2h\|…>` | set how often to check (at least 15m) |
| `feed on\|off <id>` | turn a feed on, or pause it |

`feeds` with no arguments opens the [Feeds screen](/guides/feeds/#feeds-screen).

`notifs` subcommands:

- `notifs`: active notifications
- `notifs all`: all notifications, including dismissed ones
- `notifs enable`: open the notification access settings
- `notifs mute <app>`, `notifs unmute <app>`, `notifs muted`
- `notifs log on|off`
- `notifs clear`: dismiss all

## Control

See [Media and device controls](/guides/media-and-controls/).

| Usage | Aliases | Description |
| --- | --- | --- |
| `media [play\|pause\|toggle\|next\|prev\|stop]` | | show what's playing, or control it (needs notification access) |
| `mp` | | play / pause |
| `next` | | next track |
| `prev` | | previous track |
| `torch [on\|off]` | | flashlight (with no argument, it toggles) |
| `volume [0-100\|up\|down\|mute\|unmute]` | `vol` | media volume |
| `ring [normal\|vibrate\|silent]` | | ringer mode (`silent` needs Do Not Disturb access) |
| `dnd [on\|off\|alarms\|none\|status\|access]` | | Do Not Disturb (with no argument, it toggles; `access` opens the permission page) |
| `brightness [0-100\|up\|down\|auto\|access]` | | screen brightness (needs Modify system settings; `access` opens the permission page) |
| `rotate [on\|off\|status]` | | auto-rotate (with no argument, it toggles; needs Modify system settings) |
| `screen-timeout [15s\|30s\|1m\|…]` | | screen timeout (needs Modify system settings) |
| `wifi` | | Wi-Fi panel |
| `bt` | `bluetooth` | Bluetooth settings |
| `nfc` | | NFC panel |
| `data` | | Internet / mobile data panel |
| `toggles` | | the state of every toggle; tap a line to change it |

## Productivity

See [Productivity](/guides/productivity/). `<n>` is the number shown in the list.

| Usage | Aliases | Description |
| --- | --- | --- |
| `todo [<text>\|add <text>]` | | list open todos, or add one (`todo buy milk`); tap an item to tick it |
| `todo done\|undo\|rm <n>` | | tick, untick or delete an item |
| `todo edit <n> <text>` | | change an item's text |
| `todo all` / `todo clear` | | show finished items too / delete finished items |
| `note <text>` | | save a quick note |
| `notes [grep <text>\|rm <n>]` | | show notes, newest first (tap one to copy it) |
| `remind <when> <text>` | | set a reminder: `10m`, `2h`, `1h30m`, `18:30`, `6pm`, `tomorrow 9:00`, `fri 8am`, `dec 3 10:00`. Repeating: `every day 8:00`, `every weekday`, `every mon,thu 19:00`, `every month 1st`, `every year mar 14`. A time before 6 AM asks you to confirm; `remind --yes <when> <text>` doesn't ask |
| `reminders` / `remind rm <n>` | | list pending reminders / cancel one |
| `remind exact` | | allow exact alarms, so reminders go off on time |
| `timer <5m\|1h30m\|25> [<label>]` | | start a timer in the clock app (with no arguments, show timers) |
| `alarm <HH:MM\|7am> [<label>]` | | set an alarm in the clock app (with no arguments, show alarms) |
| `alarms` / `stopwatch` | | open the clock app |
| `agenda [<days>]` | | upcoming calendar events (3 days by default, up to 31); tap one to open it |
| `agenda --allow` / `agenda --settings` | | ask for calendar access / open Kolahal's app settings |

## Scripts

| Usage | Description |
| --- | --- |
| `scripts` | open the script manager (the Scripts tab in Settings); `scripts <sub>` works like `script <sub>` |
| `script <sub> <name>` | manage scripts; see the subcommands below |
| `lua <code>` | run Lua 5.4 code |
| `js <code>` | run JavaScript code (QuickJS) |
| `= <expr>` | calculator: `= 5 km in mi`, `= 72 f to c`, `= 100 usd in eur` |
| `status [clear <name\|all>]` | show scripts' status lines, with the pinned ones marked; `clear` removes them |

`script` subcommands:

| Subcommand | Description |
| --- | --- |
| `script ls` | list scripts with their timers, flags and last result (plain `script` does the same) |
| `script lib` | show the bundled [library](/scripting/library/) |
| `script install <name> [--replace]` | install or update a library script; `--replace` overwrites a copy you edited |
| `script get [<name>] [--refresh] [--replace]` | list the [shared scripts](/scripting/overview/#getting-scripts-from-others), or install or update one |
| `script new <name>[.lua\|.js] [lua\|js] [--cmd]` | create a script from a template and open the editor |
| `script edit <name>` | open the editor |
| `script run <name> [<words>]` | run now; the words become its `args`, as when you run it by name |
| `script cat <name>` | print the code with line numbers (alias `show`) |
| `script diff <name>` | compare your copy with the library version. Lines only in the library show as `-` (red), your lines as `+`, with three unchanged lines around each change. Copy text on the first line copies a plain diff |
| `script every <name> <15m\|30m\|2h\|1d\|off>` | set an interval timer |
| `script at <name> <HH:mm[,HH:mm…]\|off>` | set daily times |
| `script notify <name> on\|off` | allow `notify()` for this script, or not |
| `script on\|off <name>` | turn a script on or off (aliases `enable`, `disable`) |
| `script triggers` | list scripts with `@on` lines and the events they run on (alias `events`) |
| `script access <name>` | show each [access switch](/scripting/overview/#switching-access-off), on or off; tap one to put the command that changes it in the prompt |
| `script allow\|deny <name> <what…>` | turn access on or off: `internet`, `header` (pinned at the top), an `@uses` item, an `@on` event, or `notify` |
| `script pin\|unpin <name>` | show the script's status line in the [pinned header](/getting-started/basics/#the-pinned-header), or stop showing it |
| `script secret <name> [<NAME> [--clear]]` | list the script's [secrets](/scripting/overview/#api-keys-and-other-secrets) and whether each is set. With a secret's name, open a box to set it (`--clear` deletes it) |
| `script set <name> [<option> [<value>\|--reset]]` | list the script's [options](/scripting/overview/#options) and their values. With an option and a value, set it (`--reset` restores the default) |
| `script mv <name> <new-name>` | [rename the script](/scripting/overview/#renaming-a-script) (alias `rename`) |
| `script rm <name>` | delete the script with its timers, data and status line (alias `delete`) |
| `script dir` | pick a sync folder |
| `script export` | write every script and `kolahal.json` to the folder |
| `script import` | read scripts back from the folder |

Script names can use lowercase letters, digits, `-` and `_` (up to 40 characters). A script name can't be the name of a built-in command.

## Terminal

| Usage | Aliases | Description |
| --- | --- | --- |
| `help [<command>]` | `?` | show every command in its section: its name, what it does, and its arguments in a muted line under that. Each section heading says how many commands it has. With a command, show that command in full: what it does, each way to use it, its other names, and a link here |
| `history` | | recent commands (tap one to use it again) |
| `clear [history\|all]` | `cls` | clear the screen, the ↑ history, or both |
| `backup [now\|every daily\|weekly\|off\|dir]` | | write the [backup file](/guides/backup/#automatic-backups) to your backup folder now, set automatic backups, or pick the folder |
| `log only <name\|kind>` / `log all` | | show only the lines of one script, feed or app, or of one kind (`commands`, `scripts`, `feeds`, `notifications`, `apps`, `calls`, `reminders`, `starred`). This lasts until `log all`, ✕, Back or another command |
| `fresh` | | show a clean screen now, like `log.fresh`. Nothing is deleted: "▴ N earlier lines" brings it back. Not added to the log |
| `restart [scripts]` | `reboot` | restart the launcher, or stop running scripts |
| `update [install]` | | full build only. Tells you if a newer release is available. `install` downloads it, checks it's signed with Kolahal's key and opens Android's installer |
| `alias [name = command]` | | list or define aliases |
| `unalias <name>` | | remove an alias |
| `config [ls\|set\|get\|reset] <key> <v>` | `set` | settings (with no arguments, opens the settings screen) |
| `theme <name>` | | `green` `amber` `mono` `dracula` `solarized` `gruvbox` `gruvbox-material`; each has a [dark and a light version](/guides/themes-and-settings/#light-and-dark) |
| `font [<name>\|import\|sans [off\|prompt\|all]]` | | terminal font: `system` `jetbrains-mono` `fira-code` `ibm-plex-mono` `source-code-pro` `custom`. With no arguments, lists them (tap one to use it). `import` picks a .ttf/.otf file. `sans prompt` uses the system font for the prompt, and `sans all` for everything (`font.sans`) |
| `echo <text>` | | print text |
| `date [<place>...]` | `time` | current date and time. With places, shows the time in each place and how far ahead or behind it is. A place can be a city (`tokyo`, `new york`), a zone (`Asia/Tokyo`), `utc`, or an offset like `+5:30` |
| `cal [<month>] [<year>]` | | show the month as a calendar, with today in bold: `cal`, `cal dec`, `cal dec 2027`, `cal 2027-12` |

## Widgets

See [Widgets](/guides/widgets/).

| Usage | Aliases | Description |
| --- | --- | --- |
| `widgets` | | open Settings → Widgets → Arrange widgets: your placed widgets; add, resize and arrange them |
| `widgets ls` | `widget ls` | list placed widgets as text lines (tap one to open the page) |
| `widget add` | | open the widget picker; the widget goes on the widgets page |

## Device

| Usage | Aliases | Description |
| --- | --- | --- |
| `settings` | `prefs` | Kolahal settings screen (with backup import and export) |
| `setup [text]` | | setup checklist page (`text`: print it in the log) |
| `tour` | | a page-by-page tour of how Kolahal works, with a few first settings |
| `source` | | Kolahal's source code on GitHub |
| `wallpaper [match]` | | shows whether the home screen wallpaper is Kolahal's or yours. `match` sets it to this theme's colour, so the screen doesn't flash when you swipe home. The wallpaper then follows the theme until you set another one. Kolahal asks you to confirm first, because the old wallpaper can't be restored. The lock screen wallpaper doesn't change |
| `unlocks [<days>]` | | unlock counts (1–30 days) |
| `fetch` | `neofetch`, `fastfetch` | a summary of this phone and Kolahal, in fastfetch style, under `name@device` (`user.name`). It shows Kolahal's icon next to the Android version, device, kernel, uptime, Kolahal's version, apps, scripts, feeds, theme, font, display, CPU, memory, storage, battery, network and today's unlocks, then the theme's colours |
| `notifications [settings]` | | allow script notifications |
| `usage-access` | | grant usage access (for the unlock count) |
| `crashes [share\|clear] [<n>]` | | crash reports kept on this phone, newest first (tap one to share it). `share` opens the share sheet with report `n` (by default, the newest). `clear` deletes all reports. See [Privacy](/reference/privacy/#crash-reports) |
| `battery` | | battery optimisation settings |
| `home` | | choose the default home app |
| `lock` | | lock the screen (needs the accessibility service) |
| `shade` | | pull down the notification shade |
| `accessibility` | | turn on the screen-lock service for double-tap |
| `system` | | Android system settings |

## Profiles

| Usage | Description |
| --- | --- |
| `work [on\|off\|status]` | pause / resume the work profile |
| `private [on\|off\|status]` | lock / unlock the private space |

## Profile flags

If you have a work profile, a private space or cloned apps, app commands take a flag to choose one:

- `-w`: work
- `-p`: personal
- `-s`: private space
- `-c`: clone

See [Work profile & private space](/guides/profiles/).

## Turning commands off

You can turn off the commands you don't use: **Settings → Commands** has a switch for each one.
This helps when a command has the name of an app you open often. With `maps` off, typing `maps`
opens Maps.

A command that is off:

- isn't suggested, and `help` doesn't list it (`help` names the commands that are off at the end);
- doesn't run, by its name or its other names: with `yt` off, `youtube` opens the YouTube app;
- can't be run by scripts or gestures either, or by a tap on a command line you typed.

Kolahal's own buttons still work: a tap on the header's todo count still runs `todo`, and "tap to
allow" lines still run `script`, even with those commands off.

`help`, `config` and `settings` always stay on, so you can turn the others back on. From the
prompt: `config set commands.off yt,wiki,maps`, and `config set commands.off none` turns them all
back on.
