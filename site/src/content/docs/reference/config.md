---
title: Configuration keys
description: Every Kolahal setting with its type, default and meaning.
---

You can change settings on the [settings screen](/guides/themes-and-settings/#settings-screen) or with
`config set <key> <value>`.

- `config ls` lists every key with its current value. `*` marks keys that you changed from the default.
- `config reset <key|all>` restores the default.

Types:

- **bool**: `on`/`off` (or `true`/`false`, `yes`/`no`, `1`/`0`)
- **number**: a whole number in the range shown
- **choice**: one of the listed values
- **text**: any text

:::note[Renamed in 0.3.0]
Some keys got clearer names in 0.3.0. Kolahal moved your settings to the new names automatically.
The old names still work in `config`, which tells you the new name. Older backups import as before.

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
| `gesture.swipeup.on` | removed: to turn swiping up off, set `gesture.swipe-up` to `none` |
| `log.lineheight`, `log.keepdays` | `log.line-height`, `log.keep-days` |
| `keyboard.onhome` | `keyboard.on-home` |
| `header.media-always` | removed in 0.4.14: what's playing always shows in the [pinned header](/getting-started/basics/#the-pinned-header) |
| `header.media` | removed in 0.4.16: what's playing always shows in the [pinned header](/getting-started/basics/#the-pinned-header), if Kolahal has notification access |
| `header.item-1`, `header.item-2` | removed in 0.4.16: the header's sides are now lists, `header.left` and `header.right`. Your two items moved to the right side, before the battery |
| `header.clock`, `header.date`, `header.battery` | removed in 0.4.14: the [header](/getting-started/basics/#the-header) always shows the time, the date and the battery |
| `header.unlocks`, `header.network`, `header.alarm`, `header.todo`, `header.settings`, `header.profiles` | removed in 0.4.14: choose the header's parts with `header.left` and `header.right` (the first two that were on moved over) |
| `header.device` | removed in 0.4.14: `fetch` shows `name@device` |
| `header.status` | removed in 0.4.14: a script's status line shows at the top after you [pin the script](/getting-started/basics/#the-pinned-header) |
| `header.autohide`, `header.icons`, `header.compact.*` | removed in 0.4.14: the header is one line with icons, and it looks the same all the time |

The `theme` and `font` commands didn't change.
:::

## Header

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `header.show` | bool | `off` | show the [header](/getting-started/basics/#the-header), the line at the top of home, with the parts you chose on each side. When off, only the [pinned header](/getting-started/basics/#the-pinned-header) is at the top, and you open settings with `settings` |
| `header.below-bar` | bool | `on` | in full screen, put the header below the status bar's area instead of next to the camera. When the status bar is shown, the header is always below it |
| `header.padding` | number 0–24 | `6` | space (dp) above and below the header's text. The header is one line of text with this much above and below it (31 dp at font size 14). Not in Settings |
| `header.left` | parts separated by commas, or `none` | `time,date` | the parts on the header's left side, from left to right. Any of `time` `date` `battery` `alarm` `todo` `network` `unlocks` `profiles` `settings`. Each part can be on one side only. Also in Settings → Header → Arrange the header |
| `header.right` | as `header.left` | `alarm,todo,battery` | the parts on the header's right side, from left to right. If they don't fit, parts in the middle are left out first |
| `header.pinned-seconds` | number 2–60 | `5` | how many seconds each item in the pinned header shows before the next one. Not in Settings |
| `header.pinned-dots` | number 0–10 | `3` | dots at the end of the pinned header that show which item is on the screen, for up to this many items. `0`: never. Not in Settings |
| `header.pinned-bar-above` | number 0–16 | `5` | space (dp) between the pinned header's text and its progress bar. Not in Settings |
| `header.pinned-bar-below` | number 0–16 | `5` | space (dp) under the pinned header's progress bar, before the log. Not in Settings |
| `clock.24h` | bool | `off` | use 24-hour time in the header and log |
| `user.name` | text | `kolahal` | your name in `name@device`, in `fetch`. Up to 32 characters, with no spaces or `@` |

## Appearance

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `theme.name` | choice: `green` `amber` `mono` `dracula` `solarized` `gruvbox` `gruvbox-material`, or [one you made](/guides/themes-and-settings/#your-own-themes) | `green` | colour theme (also `theme <name>`). The phone's dark mode chooses its dark or [light version](/guides/themes-and-settings/#light-and-dark) |
| `theme.always-dark` | bool | `off` | always use the theme's dark version, even in light mode |
| `screen.bars` | choice: `show` `hide` `immersive` | `show` | full screen: `hide` hides the status bar, and `immersive` hides both system bars. Swipe from the edge to show them |
| `font.family` | choice: `system` `jetbrains-mono` `fira-code` `ibm-plex-mono` `source-code-pro` `custom` | `jetbrains-mono` | terminal font (also `font <name>`). `custom` is a .ttf/.otf file you imported with `font import` |
| `font.sans` | choice: `off` `prompt` `all` | `off` | use the system font instead of the terminal font, for the prompt or for the whole terminal (also `font sans <mode>`). Lists with aligned columns and the script editor keep the terminal font |
| `font.size` | number 10–24 | `14` | terminal font size (sp). Pinching the log also changes it |

## Log

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `log.time` | choice: `off` `commands` `groups` | `groups` | times on the right of log lines. `groups`: on the first line of each group and when the minute changes. `commands`: on the lines you type |
| `log.dim` | bool | `on` | dim output older than the latest command |
| `log.spacing` | number 0–40 | `5` | gap (dp) above each output group. A group is a command and its output, or a background batch (feeds, scripts) |
| `log.gutter` | bool | `on` | thin colour bar at the left of each log line: green for commands, the script's colour for background output, red for errors |
| `log.line-height` | number 10–56 | `22` | minimum height of a log line, in sp. 22 is 22 dp at the default text size, and it scales with the phone's text size. With `log.taller-taps`, lines you can tap get +10, and two-column rows get +14. A line has up to 3 dp of padding above and below. The padding gets smaller as the height gets close to one line of text (19 at text size 14), and there is none at that height. A line is never shorter than its text. Script status lines in the header also use this height |
| `log.taller-taps` | bool | `off` | make lines you can tap a little taller, so they're easier to hit. When off, every line has the same height |
| `log.tables` | choice: `boxed` `plain` | `boxed` | how tables in script lines look: boxed (on a shaded box, with lines between rows) or plain (columns only); wider tables scroll sideways |
| `log.wallpaper` | number 0–100 | `0` | how much of the phone's wallpaper shows behind the log, in percent. `0` is none. A higher number shows less of the theme's colour |
| `chips.height` | number 20–64 | `44` | height (dp) of suggestion chips, the suggestion list's rows and app chips in the log |
| `log.keep-days` | number 1–365 | `60` | delete log lines older than this many days (checked at least twice a day). Starred lines are kept |
| `log.keep-lines` | number 500–50000 | `5000` | delete the oldest log lines beyond this number. Typed commands are counted separately, so ↑ history is kept. Starred lines are kept and not counted. Only with `config set`: the settings screen doesn't show it |
| `log.tips` | bool | `on` | for your first two weeks, one line a day about something you haven't used yet. Tap it to try it |
| `log.fresh` | number 0–240 | `10` | show a clean home screen after you've been away this many minutes (0: never). Earlier output is one tap away. `fresh` does this at once |
| `feeds.log` | bool | `on` | post new feed items in the log |
| `feeds.log-lines` | 1–50 | `10` | how many new items per feed and refresh appear in the log. The rest appear as one "+N more" line |
| `feeds.read-shown` | bool | `off` | items posted in the log count as read. When off, only items you opened or listed count as read |
| `feeds.backlog-read` | bool | `off` | when you subscribe, the posts already in the feed count as read |
| `feeds.interval` | 15–1440 | `60` | refresh interval (minutes) for feeds you add |
| `feeds.notify` | bool | `off` | feeds you add send notifications about new items |
| `feeds.wifi` | bool | `off` | scheduled refreshes run only on Wi-Fi (unmetered networks). `feed fetch` always runs |

## Launching and suggestions

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `launch.top` | bool | `on` | Enter opens the best match when it starts with what you typed |
| `launch.single` | bool | `on` | open the app automatically when only one app matches |
| `launch.instant` | choice: `off` `apps` `all` | `all` | run without Enter when only one match is left. `apps`: apps only. `all`: apps and commands, including whole lines like `torch on`. Sensitive commands always need Enter |
| `launch.instant-letters` | number 1–6 | `2` | how many letters to type before an instant launch |
| `suggestions.idle` | bool | `off` | show most-used apps when the prompt is empty |
| `suggestions.icons` | bool | `on` | show app icons next to app suggestions, in the list and on chips |
| `suggestions.wrap` | bool | `off` | show the whole description of a command or setting in the suggestion list, over several lines. When off, it is one line, cut short |
| `suggestions.auto` | bool | `on` | show suggestions only while you type (keyboard open or text in the prompt) |
| `suggestions.style` | choice: `list` `chips` | `list` | how suggestions look while you type. `list`: a list above the prompt, with the best match at the bottom, nearest your thumb, and command descriptions. `chips`: a strip of chips |
| `suggestions.rows` | number 1–2 | `2` | rows of suggestion chips while you type (with `suggestions.style chips`) |
| `selector.rail` | bool | `off` | show the app selector on an edge of the log while the keyboard is closed. Slide along it to a letter, then onto the list to pick an app. Lift your finger on an app to open it |
| `selector.browse` | choice: `letters` `apps` | `apps` | what sliding along the edge does. `letters`: moves one letter at a time. The letter's apps appear next to your thumb, with the letter large beside it. `apps`: moves through the whole list, one app at a time when you slide slowly |
| `selector.filter` | bool | `off` | with `letters`, the list shows only the letter's apps, not the whole list scrolled to that letter |
| `selector.side` | choice: `right` `left` | `right` | the edge you slide on. The list of apps opens on the other side |
| `selector.position` | choice: `bottom` `middle` `top` | `bottom` | where the slide area is on the edge. Keep it away from the back gesture |
| `selector.handle` | bool | `on` | show the slim A–Z handle on the edge. When off, the handle is hidden but the selector still works |
| `selector.letters` | bool | `on` | show the letters along the edge when you aren't sliding, so you can see where to touch. Only letters that have apps are shown |
| `selector.height` | number 200–900 | `400` | length (dp) of the part of the edge you slide on. Shorter means your thumb moves less. The list uses the whole height. With gesture navigation, a touch starts the selector only in the middle 200 dp, where the handle is |
| `selector.actions` | choice: `far-side` `off` | `off` | app actions while picking. `far-side`: the actions open when you lift your finger on the far side of the list. `off`: you get them only by long-pressing in the open list. Not in Settings ([what it does](/getting-started/basics/#app-actions-while-picking)) |
| `selector.row-height` | number 32–64 | `50` | height (dp) of each app in the app selector's list. Taller rows space the names out. It grows with a large text size |
| `selector.icons` | bool | `on` | show each app's icon in the app selector's list |
| `selector.system-font` | bool | `on` | use the system font in the app selector instead of the terminal font: in its list, the letters on the edge and the letter next to your thumb |
| `selector.aim` | number 0–40 | `10` | with `selector.browse apps`, a slow slide along the edge moves one app for every this many dp (`0`: a slide always scrolls the whole list). Not in Settings |
| `selector.aim-speed` | number 20–400 | `100` | slides slower than this (dp/s) move one app at a time. From 2.5 times this speed, they scroll the whole list again. Not in Settings |
| `suggestions.max` | number 0–30 | `30` | the most suggestions shown while you type |
| `search.engine` | choice: `duckduckgo` `google` `bing` `brave` `startpage` `kagi` `custom` | `duckduckgo` | web search engine for `search` / `g` |
| `search.url` | text | `https://duckduckgo.com/?q=%s` | custom search URL (`%s` is replaced by the search text). Used when `search.engine` is `custom` |

## Gestures

Each gesture takes a command, `lock`, `shade`, `quick` (quick actions) or `none`. See [Gestures](/guides/gestures/).

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `gesture.double-tap` | text | `lock` (`none` in the Play build, which has no screen lock) | double-tap empty log space |
| `gesture.swipe-down` | text | `shade` | swipe down on the header |
| `gesture.swipe-up` | text | `none` | swipe up on the prompt. `none`, the default, turns it off, because it's easy to do by accident. `apps` opens the app grid |

## Widgets

See [Widgets](/guides/widgets/).

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `widgets.page` | choice: `left` `right` `off` | `left` | a widgets page next to the terminal. Swipe sideways on the home screen to reach it |
| `widgets.start` | bool | `off` | start on the widgets page. Home opens on it, and Home and Back return to it. Swipe sideways to see the log |
| `widgets.wallpaper` | number 0–100 | `0` | how much of the phone's wallpaper shows behind the widgets page, in percent. `0` is none. A higher number shows less of the theme's colour |

## Keyboard and key bar

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `keybar.show` | bool | `off` | show the touch key bar |
| `keybar.auto` | bool | `on` | show the key bar only while the on-screen keyboard is open for the prompt (off: always) |
| `keybar.height` | number 20–64 | `40` | key bar row height (dp). Smaller leaves more room for the log |
| `prompt.height` | number 20–72 | `40` | prompt line height (dp), with the keyboard open or closed. It is never shorter than its text |
| `keybar.row1` | text | `/,@,\|,=,+,-,*,UP,DOWN` | touch keys, separated by commas: `ESC` `TAB` `UP` `DOWN` `LEFT` `RIGHT` `HOME` `END` `BKSP` `ENTER` `SPACE` `KBD` or any text |
| `keybar.row2` | text | empty | second key bar row (same format; empty by default) |
| `keyboard.on-home` | bool | `off` | open the on-screen keyboard every time the home screen appears |
| `keyboard.suggestions` | bool | `off` | keyboard suggestions in the prompt. When off, the prompt is a password-type field: there is no autocorrect, but some keyboards then type only Latin letters |

## Backups

See [Automatic backups](/guides/backup/#automatic-backups).

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `backup.auto` | choice: `off` `daily` `weekly` | `off` | write the backup file to the folder you picked (`backup dir`), every day or every week |
| `backup.keep` | number 1–60 | `7` | how many automatic backups the folder keeps. Older ones are deleted (only with `config`) |
