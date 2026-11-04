---
title: Media and device controls
description: Control what's playing, the flashlight, volume, ringer, Do Not Disturb, brightness and rotation from the prompt.
---

The `control` group in `help` covers media playback and the quick toggles you would otherwise
find in the quick settings. Every one of them prints a status line, and the lines are tappable.

## Media

Media control reads the active media sessions, which Android only allows for apps with
**notification access**. Grant it with `notifs enable` (or **setup → Notification access**).
Without it, media commands print a tappable line that opens the permission page.

| Command | Action |
| --- | --- |
| `media` | now playing: `♪ Title — Artist`, with app, state and position / duration. Tap the line to play or pause |
| `media play` / `media pause` / `media toggle` | start, pause or toggle playback |
| `media next` / `media prev` / `media stop` | skip, go back, stop |
| `mp` | play / pause |
| `next`, `prev` | next / previous track |

Kolahal controls the session that is playing or, if nothing is playing, the most recently played one.

### In the pinned header

With `header.media` on (the default), the [pinned header](/getting-started/basics/#the-pinned-header)
under the header shows `♪ Title — Artist` while something plays, and `‖ Title — Artist` while it
is paused, before the scripts you pinned. A thin progress bar under the line fills as the track
plays, and updates when you seek or start another track; for a stream with no length it stays
empty.

- **Tap** the line to play or pause.
- **Long-press** it, or tap **⏭**, to skip to the next track.

The line disappears 30 minutes after playback was paused, and steps aside while you type. With
the header off (`header.show off`) it shows at the top of home. Turn it off with
`config header.media off`.

## Toggles

| Command | Action | Requires |
| --- | --- | --- |
| `torch [on\|off]` | flashlight; no argument toggles | nothing |
| `volume [0-100\|up\|down\|mute\|unmute]` | media volume; prints the new level | nothing |
| `ring [normal\|vibrate\|silent]` | ringer mode; no argument shows it | Do Not Disturb access for `silent` (and to leave silent) |
| `dnd [on\|off\|alarms\|none]` | Do Not Disturb: `on` is priority only, `alarms` alarms only, `none` total silence; no argument toggles; `dnd status` shows it. Since Android 15, `off` turns off only what Kolahal turned on, and says when one turned on elsewhere stays on | Do Not Disturb access |
| `brightness [0-100\|up\|down\|auto]` | screen brightness; a level switches adaptive brightness off, `auto` turns it on | Modify system settings |
| `rotate [on\|off]` | auto-rotate; no argument toggles | Modify system settings |
| `screen-timeout <15s\|30s\|1m\|5m\|…>` | screen timeout; no argument shows it | Modify system settings |
| `wifi`, `bt`, `nfc`, `data` | open the Wi-Fi, Bluetooth, NFC or Internet panel | nothing |
| `toggles` | one line per toggle with its current state | nothing |

Android doesn't let apps switch Wi-Fi, Bluetooth, NFC or mobile data on and off, so those commands
open the system panel (or the settings page) where you can do it in one tap.

Tapping a line from `toggles` toggles that item, or opens its panel. If a permission is missing,
the line opens the page to grant it instead.

### Granting access

Both kinds of access are optional, and the [setup checklist](/getting-started/setup/) has a
row for each:

- **Do Not Disturb access** (`dnd access`): used by `dnd`, `ring silent` and scripts with `@uses dnd`.
- **Modify system settings** (`brightness access`): used by `brightness`, `rotate` and
  `screen-timeout`.

When a command needs one of them and it isn't granted, the command prints a tappable line that
opens the right page.

These commands change device state, so [instant launch](/getting-started/basics/) never runs them from
part of a name: `torch` waits for Enter. A complete line such as `torch on` or `dnd off` runs without
Enter when `launch.instant` is `all`.
