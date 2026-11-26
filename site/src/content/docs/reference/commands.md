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
| `apps [work\|personal\|private\|hidden] [-l\|-a]` | `ls` | show the app grid: each profile's pinned and most-used apps, then **+N more** to show the rest (`-l`: every app, one per line; `-a`: include hidden apps). A locked private space shows one line in place of its apps |
| `info <app> [-w]` | | open app info |
| `uninstall <app> [-w]` | | uninstall an app |
| `hide <app> [-w]` | | hide an app from lists and search |
| `unhide <app>` | | show a hidden app again (`apps hidden` lists hidden apps) |
| `pin <app>` | `fav` | make an app a favorite. Favorites come first in the suggestions on an empty prompt (`suggestions.idle`) and have a star in the app selector. `pin` alone lists them |
| `unpin <app>` | `unfav` | remove a favorite |
| `pins` | `favs` | show a grid of pinned apps |
| `rename <app> = <name>` | | give an app your own label; `rename <app> =` restores the original |
| `usage [<days> \| <app>]` | `screentime` | screen time for each app (1–30 days), or one app in detail: today by hour and its last 7 days (needs Usage Access) |
| `usage [summary \| week \| month \| year \| full] [<app>]` | `screentime` | screen time over Android's weeks, months and years: `summary`, a line for each with its time a day; `full` shows all; with an app after `week`, `month` or `year`, that app alone. A word is the word only on its own or before an app: `usage weekly` is the app Weekly (needs Usage Access) |
| `storage` | | free space, the largest apps with their cache, and apps not opened for 30 days (needs Usage Access) |
| `installs [<n>]` | `updates` | recent app installs, updates and uninstalls |

## People

| Usage | Aliases | Description |
| --- | --- | --- |
| `call <name\|number>` | `dial` | call a contact or number (in the Play build, without a name it opens the contact picker) |
| `sms <name\|number> [<text>]` | `text` | start a text message (in the Play build, without a name it opens the contact picker) |
| `contacts [<query>]` | `contact` | find contacts (without a query, shows favorites). In the Play build, pick a contact, or use `--forget <name>` |
| `calls [<n>]` | | recent incoming, outgoing and missed calls (20 by default; `allow calls` asks for call log access) |

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
| `notifs [all\|mute <app>\|unmute <app>\|muted\|log [on\|off]\|clear]` | `notifications` | your notifications in the terminal (needs notification access: `allow read-notifications`) |

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
| `dnd [on\|off\|alarms\|none\|status]` | | Do Not Disturb (with no argument, it toggles; needs Do Not Disturb access: `allow dnd`) |
| `brightness [0-100\|up\|down\|auto]` | | screen brightness (needs Modify system settings: `allow settings`) |
| `rotate [on\|off\|status]` | | auto-rotate (with no argument, it toggles; needs Modify system settings) |
| `screen-timeout [15s\|30s\|1m\|…]` | | screen timeout (needs Modify system settings) |
| `wifi` | | Wi-Fi panel |
| `bt` | `bluetooth` | Bluetooth settings |
| `nfc` | | NFC panel |
| `data [today \| month]` | | Internet / mobile data panel; with `today` or `month`, the data each app used |
| `toggles` | | the state of every toggle; tap a line to change it |

## Productivity

See [Productivity](/guides/productivity/), and [Text editor](/guides/text-editor/) for `edit`. `<n>` is the number shown in the list.

| Usage | Aliases | Description |
| --- | --- | --- |
| `todo [<text>\|add <text>]` | | list open todos, or add one (`todo buy milk`); tap an item to tick it |
| `todo done\|undo\|rm <n>` | | tick, untick or delete an item |
| `todo edit <n> <text>` | | change an item's text |
| `todo all` / `todo clear` | | show finished items too / delete finished items |
| `note <text>` | | save a quick note |
| `notes [grep <text>\|rm <n>]` | | show notes, newest first (tap one to copy it) |
| `edit` | | pick any file with Android's file picker, and open it in the text editor: a window of its own, which stays open when you go home |
| `edit new [<name>]` | | make a new file (the picker asks where), and open it |
| `edit recent` | | list the files opened lately, and those with unsaved text; tap one to open it |
| `edit <file>` | | open one of those by its name, or by the start of its name |
| `remind <when> <text>` | | set a reminder: `10m`, `2h`, `1h30m`, `18:30`, `6pm`, `tomorrow 9:00`, `fri 8am`, `dec 3 10:00`. Repeating: `every day 8:00`, `every weekday`, `every mon,thu 19:00`, `every month 1st`, `every year mar 14`. A time before 6 AM asks you to confirm; `remind --yes <when> <text>` doesn't ask |
| `reminders` / `remind rm <n>` | | list pending reminders / cancel one |
| `timer <5m\|1h30m\|25> [<label>]` | | start a timer in the clock app (with no arguments, show timers) |
| `alarm <HH:MM\|7am> [<label>]` | | set an alarm in the clock app (with no arguments, show alarms) |
| `alarms` / `stopwatch` | | open the clock app |
| `agenda [<days>]` | | upcoming calendar events (3 days by default, up to 31); tap one to open it. `allow calendar` asks for calendar access |

## Scripts

| Usage | Description |
| --- | --- |
| `scripts` | open the script manager (the Scripts tab in Settings); `scripts <sub>` works like `script <sub>` |
| `shaders` | open the Shaders tab; `shaders <sub>` works like `shader <sub>` |
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
| `script get [<name>\|<source>/<name>] [--refresh] [--replace]` | list the [shared scripts](/scripting/overview/#getting-scripts-from-others) of every source, or install or update one; a name goes to the first source that has it; one that asks for access arrives switched off |
| `script sources` | list the [sources](/scripting/overview/#your-own-scripts-repository) of Get more scripts, with their addresses, how many scripts each has and when it was read |
| `script source add <owner/name\|link>` | add a source: a GitHub repository as `owner/name`, or an `https://` link to an `index.json`. Kolahal doesn't check its scripts |
| `script source rm <name>` | remove a source you added; the scripts you installed from it stay |
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
| `script access <name>` | show each [access switch](/scripting/overview/#switching-access-off), on or off; tap one to put the command that changes it in the prompt. It also says when the script has [internet and private data](/scripting/overview/#internet-and-private-data) |
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
| `scramble [on\|off]` | | scramble home for a [screen recording](/reference/privacy/#screen-recordings): every line keeps its shape and colors but none of its text, and pictures show as plain blocks, until `scramble off`. Nothing stored changes |
| `restart [scripts]` | `reboot` | restart the launcher, or stop running scripts |
| `update [install]` | | full build only. Tells you if a newer release is available. `install` downloads it, checks it's signed with Kolahal's key and opens Android's installer |
| `alias [name = command]` | | list or define aliases |
| `unalias <name>` | | remove an alias |
| `config [ls\|set\|get\|reset] <key> <v>` | `set` | settings (with no arguments, opens the settings screen) |
| `echo <text>` | | print text |
| `date [<place>...]` | `time` | current date and time. With places, shows the time in each place and how far ahead or behind it is. A place can be a city (`tokyo`, `new york`), a zone (`Asia/Tokyo`), `utc`, or an offset like `+5:30` |
| `cal [<month>] [<year>]` | | show the month as a calendar, with today in bold: `cal`, `cal dec`, `cal dec 2027`, `cal 2027-12` |

## Look

How home looks: its colors, its font, what is drawn over it and what is behind it. See
[Themes and settings](/guides/themes-and-settings/) and [Shaders](/scripting/shaders/).

| Usage | Aliases | Description |
| --- | --- | --- |
| `theme <name>` | | `rose-pine` (the default) `green` `amber` `mono` `dracula` `solarized` `gruvbox` `gruvbox-material` `nord` `material-you`; each has a [dark and a light version](/guides/themes-and-settings/#light-and-dark). `material-you` takes its colors from [your wallpaper](/guides/themes-and-settings/#material-you), on Android 12 and newer |
| `font [<name>\|import\|sans [off\|prompt\|all]]` | | terminal font: `system` `jetbrains-mono` `fira-code` `ibm-plex-mono` `source-code-pro` `iosevka` `custom`. With no arguments, lists them (tap one to use it). `import` picks a .ttf/.otf file. `sans prompt` uses the system font for the prompt, and `sans all` for everything (`font.sans`) |
| `shader [<name>\|ls\|on\|off]` | | a [shader](/scripting/shaders/) over home and the screens, on Android 13 and newer: `crt`, `paper`, `aurora` and others, or one of yours. Alone, it lists them. `off` always works |
| `shader set <name> <parameter> <value>` · `shader reset <name>` | | change a shader's [parameters](/scripting/shaders/#parameters), such as the amount of snow, and reset them |
| `shader follow <name> <parameter> <value>` · `shader values` | | let a parameter [follow a value](/scripting/shaders/#values-to-follow), such as `battery` or one a script gives; `values` lists them |
| `shader play <name>` · `shader words <name> <words>` | | play a [one-off shader](/scripting/shaders/#one-off-shaders), such as `hearts`, once over home; `words` plays it when a notification has one of them, also [over the app in front](/scripting/shaders/#over-other-apps) |
| `shader get [<name>]` · `shader get --update` | | [shared shaders](/scripting/shaders/#getting-shaders-from-others): `get` lists them and marks those with a newer version, or installs or updates one; `--update` updates every one you installed |
| `shader check\|save\|rm <name>` · `shader reload` | | work with the shaders of [your own](/scripting/shaders/#adding-your-own) |
| `shader edit <name>` | | open a shader's code in the [editor](/scripting/shaders/#editing-one); one that comes with Kolahal becomes yours when you save it |
| `wallpaper` | | says what the home screen wallpaper is: yours, the theme's color or a shader, and the lock screen's when it differs. Then it lists what the wallpaper can become, each as a line to tap |
| `wallpaper theme` | | a plain wallpaper in your theme's color, so your wallpaper doesn't flash when you swipe home. It follows the theme until you set another wallpaper. Kolahal asks you to confirm first, because the old wallpaper can't be restored. The lock screen wallpaper doesn't change. **Settings → Look & feel → Wallpaper** has it too, with a shader and Android's list beside it. See the [FAQ](/reference/faq/#what-wallpaper-theme-does) |
| `wallpaper shader <name>` | | open Android's own preview with a shader as your [live wallpaper](/scripting/shaders/#as-a-live-wallpaper), to set it for the home screen, the lock screen or both |
| `wallpaper live` | | open Android's own list of live wallpapers, for a phone whose wallpaper settings don't link to it |

With `shader crt` (or [another, or one of yours](/scripting/shaders/)), the phone's
graphics chip draws an effect over home and the screens, on Android 13 and newer. `shader off` turns it off.
They are in the **Shaders** tab too.

## Widgets

See [Widgets](/guides/widgets/).

| Usage | Aliases | Description |
| --- | --- | --- |
| `widgets` | | open Settings → Widgets → Arrange widgets: your placed widgets; add, resize and arrange them. `widgets <sub>` works like `widget <sub>` |
| `widget add` | | open the widget picker; the widget goes on the widgets page shown last, page 1 at first |
| `widget ls` | | list placed widgets as text lines (tap one to open Arrange widgets); with more than one page, under each page's name (tap a name to turn home to that page); plain `widget` does the same |
| `widget page [<page>]` | | turn home to the widgets page: page 1, or the page in that place (`widget page 2`) or with that name, or the start of one (`widget page work`). If `widgets.page` is `off`, it says so; tap the line to turn the page on |

## Device

| Usage | Aliases | Description |
| --- | --- | --- |
| `settings` | `prefs` | Kolahal settings screen (with backup import and export) |
| `setup [text]` | | setup checklist page (`text`: print it in the log) |
| `tour` | | a page-by-page tour of how Kolahal works, with a few first settings |
| `source` | | Kolahal's source code on GitHub |
| `unlocks [<days> \| week \| month]` | | unlock counts (1–30 days); `week` and `month` as a chart; `month` also with the trend and a line per week |
| `fetch` | `neofetch`, `fastfetch` | a summary of this phone and Kolahal, in fastfetch style, under `name@device` (`user.name`). It shows Kolahal's icon next to the Android version, device, kernel, uptime, Kolahal's version, apps, scripts, feeds, theme, font, display, CPU, memory, storage, battery, network and today's unlocks, then the theme's colors |
| `allow [<what>]` | | the Android permissions Kolahal can use, each with whether it is allowed; tap one to allow it. `allow <what>` asks for one, or opens its page in Android's settings ([below](#allow)) |
| `crashes [share\|clear] [<n>]` | | crash reports kept on this phone, newest first (tap one to share it). `share` opens the share sheet with report `n` (by default, the newest). `clear` deletes all reports. See [Privacy](/reference/privacy/#crash-reports) |
| `home` | | choose the default home app |
| `lock` | | lock the screen (needs the accessibility service: `allow lock`) |
| `shade` | | pull down the notification shade |
| `system` | | Android system settings |

### allow

`allow` lists each permission with a word for it. `allow <word>` asks Android for that one when
Android can ask, and otherwise opens the page in Android's settings where you turn it on. Once you
say no to a permission Android asks for, `allow <word>` opens Kolahal's page in Android's settings
instead. The [setup checklist](/getting-started/setup/) has the same list.

| Usage | Android's name | What it is for |
| --- | --- | --- |
| `allow usage` | Usage access | screen time, data use and accurate unlock counts |
| `allow send-notifications` | Notifications | Kolahal sends you notifications: reminders, scripts, feeds |
| `allow read-notifications` | Notification access | Kolahal reads your notifications: `notifs`, `media`, scripts with `@uses notifications` |
| `allow lock` | Accessibility (Kolahal screen lock) | `lock` and double-tap to lock. Kolahal first says what the service does and asks |
| `allow dnd` | Do Not Disturb access | `dnd`, `ring silent` and scripts with `@uses dnd` |
| `allow settings` | Modify system settings | `brightness`, `rotate` and `screen-timeout` |
| `allow contacts` | Contacts | `call`, `sms` and `contacts` by name. Full build only: the Play build uses Android's contact picker |
| `allow calendar` | Calendar | `agenda`, the agenda widget and scripts with `@uses calendar` |
| `allow calls` | Call log | calls in the log, and `calls`. Full build only |
| `allow alarms` | Alarms & reminders | reminders and scripts' daily times on the minute (Android 12 and newer) |
| `allow battery` | Unrestricted battery | timer scripts and feeds run on time |
| `allow overlay` | Display over other apps | a [one-off shader](/scripting/shaders/#over-other-apps) plays over the app in front (Android 13 and newer). Not on Android Go and other low-memory phones, which can't allow it |

`allow` is about Kolahal's own permissions. What one script may use is its own switches:
`script allow|deny <name> <what>`.

## Screens

These take the whole screen, like a program in a terminal, in your theme's colors and font. A tap,
Back or Home closes them, and so does any key on a hardware keyboard: a character you type goes on
into the prompt (except `q`, which only quits).

The screens are commands of the [TTY home style](/reference/config/#tty-home-style)
(`home.style tty`). In the default style they aren't suggested or listed, and their names are plain
words: `clock` opens your clock app, as any other app's name does.

| Usage | Aliases | Description |
| --- | --- | --- |
| `matrix` | | characters fall down the screen, as in the film |
| `pipes` | | pipes grow across the screen and turn at random |
| `sl` | | a steam locomotive crosses the screen, then home comes back |
| `clock` | | the time in big digits, with the date |
| `top` | | this phone and Kolahal at a glance, updated every second |
| `decode` | | the log lines on home scramble and settle again |
| `boot` | | a short startup sequence with this phone's counts |
| `screen <name>` | | one of the screens by name, in either home style: `screen clock` |

What the screens show:

- `clock`: the time in big digits, with the date under it. The seconds show when they fit.
  `clock.24h` sets 12- or 24-hour time.
- `decode`: the log lines on home scramble into random characters and settle into their text again, in
  one wave after another for as long as it is open. They stay where home has them, in home's own
  look, search results and `log only` included, and a line that comes in while it runs decodes in too.
- `top`: this phone and Kolahal at a glance, as the Unix `top` shows a computer, updated every
  second. The first line has the time, how long Kolahal has been running and today's unlocks. Under
  it are bars for the battery, the phone's memory, Kolahal's own memory and today's screen time
  (with usage access: `allow usage`). Then comes a table of your scripts: when each one last ran,
  how it ended, and when its timer runs it next. The latest run is at the top. From a
  [daily limit](/guides/apps/#daily-limits), today's unlocks and screen time are in the warning or
  error color, with the limit after them.
- `boot`: a short startup sequence with this phone's counts: apps, scripts, theme, font and today's
  unlocks.

They pause while the screen is off or another app is open, and close if you're away for more than
30 seconds. In battery saver they run at a lower frame rate. With **Remove animations** on in
Android's accessibility settings, `matrix`, `pipes`, `sl` and `boot` show a single still frame, and
`decode` shows the plain text.

Instant launch never opens them, and scripts can't run them: type the whole name and press Enter.
In the TTY style a screen's name is Kolahal's command, so it runs the screen, not an alias, a script
or an app of that name. To open your clock app by typing `clock` there, turn the command off in
**Settings → Commands**. A script of yours named `clock` still runs with `script run clock`, and
`script mv clock <new-name>` renames it. In the default style the name is yours again: your alias,
your script or the app.

A script can draw a full-screen screen of its own with [`tty.screen`](/scripting/api/#ttyscreenspec). Such
a script is a command: type its name. In the default style it isn't suggested or listed, but its
whole name still runs it.

**Screensaver.** With `screen.saver` set to `matrix`, `clock`, `pipes` or `decode`, that screen opens over
home after `screen.saver-after` minutes (5 by default) without a touch, while the log page shows and
nothing is open or typed. In the default home style only `decode` runs, scrambling home's own lines
where they are; `matrix`, `clock` and `pipes` wait for `home.style tty`. With `keyboard.on-home` on, the keyboard being up doesn't count as typing:
the screensaver puts it away, and it comes back when the screensaver closes. The first touch or key
only closes it. It never starts while the screen is
off, in battery saver, or with **Remove animations** on. Your phone usually turns its screen off
sooner, so you'll mostly see it on a charger. Set it in **Settings → Look & feel → Screensaver**.

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

The list is long, so the page has a search field at the top. Type a few letters to find a command:

- by its name: `wiki`;
- by what it does: `screen time` finds `usage`;
- by another name for it: `youtube` finds `yt`.

A command that is off:

- isn't suggested, and `help` doesn't list it (`help` names the commands that are off at the end);
- doesn't run, by its name or its other names: with `yt` off, `youtube` opens the YouTube app;
- can't be run by scripts or gestures either, or by a tap on a command line you typed.

Kolahal's own buttons still work: a tap on the header's todo count still runs `todo`, and "tap to
allow" lines still run `script` and `allow`, even with those commands off. So does the setup
checklist.

`help`, `config` and `settings` always stay on, so you can turn the others back on. From the
prompt: `config set commands.off yt,wiki,maps`, and `config set commands.off none` turns them all
back on.
