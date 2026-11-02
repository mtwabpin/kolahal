---
title: Configuration keys
description: Every Kolahal setting with its type, default and meaning.
---

Change settings on the [settings screen](/guides/themes-and-settings/#settings-screen) or with
`config set <key> <value>`. `config ls` lists every key with its current value; `*` marks keys
changed from the default, and `config reset <key|all>` restores defaults.

Types: **bool** takes `on`/`off` (or `true`/`false`, `yes`/`no`, `1`/`0`); **number** takes an
integer in the range shown; **choice** takes one of the listed values; **text** takes free text.

:::note[Renamed in 0.3.0]
Some keys got clearer names in 0.3.0. Your settings were migrated automatically, the old names still
work in `config` (which tells you the new name), and older backups import as before.

| Old name | New name |
| --- | --- |
| `theme` | `theme.name` |
| `gui.colors` | removed in 0.3.1: Settings always use the theme's colours |
| `widgets.home`, `widgets.height` | removed: widgets are on the widgets page (`widgets.page`) |
| `font` | `font.family` |
| `statusbar` | `screen.bars` |
| `suggestions` | `suggestions.max` |
| `keybar`, `keybar2` | `keybar.row1`, `keybar.row2` |
| `launch.instant.min` | `launch.instant-letters` |
| `feeds.log.lines`, `feeds.backlog.read`, `feeds.read.shown` | `feeds.log-lines`, `feeds.backlog-read`, `feeds.read-shown` |
| `gesture.doubletap`, `gesture.swipedown`, `gesture.swipeup` | `gesture.double-tap`, `gesture.swipe-down`, `gesture.swipe-up` |
| `gesture.swipeup.on` | removed: set `gesture.swipe-up` to `none` to turn swiping up off |
| `log.lineheight`, `log.keepdays` | `log.line-height`, `log.keep-days` |
| `keyboard.onhome` | `keyboard.on-home` |

The `theme` and `font` commands are unchanged.
:::

## Header

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `header.show` | bool | `on` | show the header; off: the log fills the screen (open settings with `settings`) |
| `header.media-always` | bool | `off` | with the header off, still show what's playing at the top of home, with a progress bar: tap plays / pauses, long-press skips (needs notification access) |
| `header.clock` | bool | `on` | show the clock |
| `header.settings` | bool | `on` | show the settings button (`settings` opens Settings too) |
| `header.device` | bool | `off` | show `name@device` in the header (`kolahal@device` until you set `user.name`) |
| `user.name` | text | `kolahal` | your name in `name@device`, in the header and in `fetch`: up to 32 characters, no spaces or `@` |
| `header.date` | bool | `on` | show the date next to the clock |
| `header.unlocks` | bool | `on` | show today's unlock count |
| `header.battery` | bool | `on` | show battery level |
| `header.network` | bool | `off` | show network type |
| `header.alarm` | bool | `on` | show the next alarm |
| `header.status` | bool | `on` | show status lines pinned by scripts |
| `header.media` | bool | `on` | show what's playing under the clock, with a progress bar: tap plays / pauses, long-press skips (needs notification access; see [Media](/guides/media-and-controls/#in-the-header)) |
| `header.todo` | bool | `on` | show the open todo count (tap to run `todo`) |
| `header.autohide` | bool | `on` | scrolling back through older output hides the header; scrolling toward the newest shows it as one line (also after a command); Home or a tap brings the full header back |
| `header.icons` | bool | `on` | icons for battery, network, unlocks, alarm and profiles in the header (off: text labels) |
| `clock.24h` | bool | `off` | use 24-hour time in the header and log |
| `header.profiles` | bool | `on` | show work/private profile state |
| `header.compact.clock` | bool | `on` | show the clock in the one-line header |
| `header.compact.date` | bool | `off` | show the date in the one-line header |
| `header.compact.device` | bool | `off` | show `name@device` in the one-line header |
| `header.compact.unlocks` | bool | `off` | show today's unlock count in the one-line header |
| `header.compact.battery` | bool | `on` | show battery level in the one-line header |
| `header.compact.network` | bool | `off` | show network type in the one-line header |
| `header.compact.alarm` | bool | `off` | show the next alarm in the one-line header |
| `header.compact.media` | bool | `on` | show a music note while something plays in the one-line header |
| `header.compact.todo` | bool | `off` | show the open todo count in the one-line header |
| `header.compact.status` | bool | `on` | show how many status lines scripts have pinned in the one-line header |
| `header.compact.settings` | bool | `off` | show the settings button in the one-line header |
| `header.compact.profiles` | bool | `off` | show work/private profile state in the one-line header |

## Appearance

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `theme.name` | choice: `green` `amber` `mono` `dracula` `solarized` `gruvbox` `gruvbox-material`, or [one you made](/guides/themes-and-settings/#your-own-themes) | `green` | colour theme (also `theme <name>`); its dark or [light version](/guides/themes-and-settings/#light-and-dark) follows the phone's dark mode |
| `theme.always-dark` | bool | `off` | always use the theme's dark version, even in light mode |
| `screen.bars` | choice: `show` `hide` `immersive` | `immersive` | full screen: hide the status bar, or both system bars (immersive); swipe from the edge to show them |
| `font.family` | choice: `system` `jetbrains-mono` `fira-code` `ibm-plex-mono` `source-code-pro` `custom` | `jetbrains-mono` | terminal font (also `font <name>`); `custom` is a .ttf/.otf imported with `font import` |
| `font.sans` | choice: `off` `prompt` `all` | `off` | use the system font instead of the terminal font: for the prompt, or for all of the terminal (also `font sans <mode>`); lists with aligned columns and the script editor stay in the terminal font |
| `font.size` | number 10–24 | `14` | terminal font size (sp); pinching the log changes it too |

## Log

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `log.time` | choice: `off` `commands` `groups` | `groups` | right-aligned times on log lines: `groups` on each group's first line and when the minute changes, `commands` on the lines you type |
| `log.dim` | bool | `on` | dim output older than the latest command |
| `log.spacing` | number 0–40 | `5` | gap (dp) above each output group: a command and its output, or a background batch (feeds, scripts) |
| `log.gutter` | bool | `on` | thin colour bar at the left of each log line: green for commands, script colour for background output, red for errors |
| `log.line-height` | number 10–56 | `22` | minimum height of a log line, in sp: 22 is 22 dp at the default text size, and it scales with the phone's text size. With `log.taller-taps`, tappable lines get +10 and two-column rows +14. A line has up to 3 dp of padding above and below, less near one line of text (19 at text size 14) and none at that height. A line is never shorter than its text. Script status lines in the header use it too |
| `log.taller-taps` | bool | `off` | make tappable lines slightly taller, so they're easier to hit; off: every line is the same height |
| `log.wallpaper` | number 0–100 | `0` | how much of the phone's wallpaper shows behind the log, in percent: `0` is none; higher shows less of the theme's colour |
| `chips.height` | number 20–64 | `48` | height (dp) of suggestion and app-grid chips |
| `log.keep-days` | number 1–365 | `60` | delete log lines older than this many days (checked at least twice a day) |
| `log.keep-lines` | number 500–50000 | `5000` | delete the oldest log lines past this many. Typed commands are counted separately, so ↑ history is kept. Only with `config set`: the settings screen doesn't show it |
| `log.tips` | bool | `on` | for your first two weeks, a line a day about something you haven't used yet; tap it to try it |
| `log.fresh` | number 0–240 | `10` | show a clean home screen after this many minutes away (0: never); earlier output is one tap away. `fresh` does this right away |
| `feeds.log` | bool | `on` | post new feed items in the log |
| `feeds.log-lines` | 1–50 | `10` | new items per feed and refresh shown in the log; the rest are shown as one "+N more" line |
| `feeds.read-shown` | bool | `off` | items posted in the log count as read (otherwise only opened or listed ones do) |
| `feeds.backlog-read` | bool | `off` | when you subscribe, the posts already in the feed count as read |
| `feeds.interval` | 15–1440 | `60` | refresh interval (minutes) for newly added feeds |
| `feeds.notify` | bool | `off` | newly added feeds notify about new items |
| `feeds.wifi` | bool | `off` | scheduled refreshes only on Wi-Fi (unmetered); `feed fetch` always runs |

## Launching and suggestions

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `launch.top` | bool | `on` | Enter launches the best match when it starts with what you typed |
| `launch.single` | bool | `on` | launch automatically when only one app matches |
| `launch.instant` | choice: `off` `apps` `all` | `all` | run without Enter once only one match is left: apps, or apps and commands, including whole lines like `torch on` (sensitive commands always need Enter) |
| `launch.instant-letters` | number 1–6 | `2` | letters to type before an instant launch |
| `suggestions.idle` | bool | `off` | show most-used apps when the prompt is empty |
| `suggestions.icons` | bool | `on` | show app icons next to app suggestions, in the list and on chips |
| `suggestions.wrap` | bool | `off` | show a command's or setting's whole description in the suggestion list, over more lines (off: one line, cut short) |
| `suggestions.auto` | bool | `on` | show suggestions only while typing (keyboard open or text in the prompt) |
| `suggestions.style` | choice: `list` `chips` | `list` | while typing: a list above the prompt (best match at the bottom, nearest the thumb, with command descriptions) or a strip of chips |
| `suggestions.rows` | number 1–2 | `2` | rows of suggestion chips while typing (`suggestions.style chips`) |
| `selector.rail` | bool | `off` | the app selector on an edge of the log while the keyboard is closed: slide along it to a letter, then onto the list to pick an app, let go on one to launch |
| `selector.browse` | choice: `letters` `apps` | `letters` | sliding along the edge: `letters` moves a letter at a time, with its apps beside your thumb and the letter large beside it; `apps` moves through the whole list, an app at a time when you slide slowly |
| `selector.filter` | bool | `off` | with `letters`: the list shows only the letter's apps, not the whole list scrolled to it |
| `selector.side` | choice: `right` `left` | `right` | the edge you slide on; the list of apps opens on the other side |
| `selector.position` | choice: `bottom` `middle` `top` | `bottom` | position of the slide area on the edge: keep it away from the back gesture |
| `selector.handle` | bool | `on` | show the slim A–Z handle on the edge; off hides it while the selector keeps working |
| `selector.letters` | bool | `off` | show the letters along the edge when you aren't sliding, so you can see where to touch; only letters that have apps |
| `selector.height` | number 200–900 | `350` | length (dp) of the part of the edge you slide on: shorter means less thumb travel; the list uses the whole height. With gesture navigation a touch starts it only in the middle 200 dp, where the handle is |
| `selector.actions` | choice: `far-side` `off` | `off` | app actions while picking: `far-side` opens them when you lift on the far side of the list; `off`: long-press in the open list only; not in Settings ([what it does](/getting-started/basics/#app-actions-while-picking)) |
| `selector.row-height` | number 32–64 | `38` | height (dp) of each app in the app selector's list: taller spaces the names out; it grows with a large text size |
| `selector.icons` | bool | `off` | show each app's icon in the app selector's list |
| `selector.system-font` | bool | `off` | use the system font in the app selector instead of the terminal font: its list, the letters on the edge and the one beside your thumb |
| `selector.aim` | number 0–40 | `10` | with `selector.browse apps`, a slow slide along the edge moves one app per this many dp (`0`: a slide always scrolls the whole list); not in Settings |
| `selector.aim-speed` | number 20–400 | `100` | slides slower than this (dp/s) move one app at a time; from 2.5 times this speed they scroll the whole list again; not in Settings |
| `suggestions.max` | number 0–30 | `30` | max suggestions shown while typing |
| `search.engine` | choice: `duckduckgo` `google` `bing` `brave` `startpage` `kagi` `custom` | `duckduckgo` | web search engine for `search` / `g` |
| `search.url` | text | `https://duckduckgo.com/?q=%s` | custom search URL (`%s` = query), used when `search.engine` = `custom` |

## Gestures

Each takes a command, `lock`, `shade`, `quick` (quick actions) or `none`. See [Gestures](/guides/gestures/).

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `gesture.double-tap` | text | `lock` (`none` in the Play build, which has no screen lock) | double-tap empty log space |
| `gesture.swipe-down` | text | `shade` | swipe down on the header |
| `gesture.swipe-up` | text | `none` | swipe up on the prompt (`none`, the default, turns it off: it's easy to trigger by accident; `apps` opens the app grid) |

## Widgets

See [Widgets](/guides/widgets/).

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `widgets.page` | choice: `left` `right` `off` | `left` | a widgets page beside the terminal: swipe sideways on the home screen to reach it |
| `widgets.start` | bool | `off` | start on the widgets page: home opens on it, and Home and Back return to it; swipe sideways for the log |
| `widgets.wallpaper` | number 0–100 | `0` | how much of the phone's wallpaper shows behind the widgets page, in percent: `0` is none; higher shows less of the theme's colour |

## Keyboard and key bar

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `keybar.show` | bool | `off` | show the touch key bar |
| `keybar.auto` | bool | `on` | show the key bar only while the on-screen keyboard is open for the prompt (off: always) |
| `keybar.height` | number 20–64 | `40` | key bar row height (dp); smaller leaves more room for the log |
| `prompt.height` | number 20–72 | `40` | prompt line height (dp), with the keyboard open or closed; it never gets shorter than its text |
| `keybar.row1` | text | `/,\|,=,LEFT,UP,DOWN,RIGHT` | touch keys, comma separated: `ESC` `TAB` `UP` `DOWN` `LEFT` `RIGHT` `HOME` `END` `BKSP` `ENTER` `SPACE` `KBD` or literal text |
| `keybar.row2` | text | empty | second key bar row (same syntax; empty by default) |
| `keyboard.on-home` | bool | `off` | open the on-screen keyboard whenever the home screen is shown |
| `keyboard.suggestions` | bool | `off` | prompt keyboard suggestions. Off uses a password-type field: no autocorrect, but Latin-only on some keyboards |

## Backups

See [Automatic backups](/guides/backup/#automatic-backups).

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `backup.auto` | choice: `off` `daily` `weekly` | `off` | write the backup file to the folder you picked (`backup dir`), every day or every week |
| `backup.keep` | number 1–60 | `7` | how many automatic backups the folder keeps; older ones are deleted (config only) |
