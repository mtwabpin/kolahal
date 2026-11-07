---
title: Media and device controls
description: Control what's playing, the flashlight, volume, ringer, Do Not Disturb, brightness and rotation from the prompt.
---

The `control` group in `help` has commands for media playback. It also has the quick toggles that you
usually find in quick settings. Each command prints a status line, and you can tap the lines.

## Media

To control media, Kolahal reads the active media sessions. Android only allows this for apps with
**notification access**. Grant it with `notifs enable` or **setup → Notification access**.
Without it, media commands print a line you can tap to open the permission page.

| Command | Action |
| --- | --- |
| `media` | shows what's playing: `♪ Title — Artist`, with the app, state and position / duration. Tap the line to play or pause |
| `media play` / `media pause` / `media toggle` | start, pause or toggle playback |
| `media next` / `media prev` / `media stop` | skip, go back, stop |
| `mp` | play / pause |
| `next`, `prev` | next / previous track |

Kolahal controls the session that is playing. If nothing is playing, it controls the one that played most recently.

### In the pinned header

If Kolahal has notification access, the [pinned header](/getting-started/basics/#the-pinned-header)
shows what's playing. It appears under the header, before the scripts you pinned:

- `♪ Title — Artist` while something plays
- `‖ Title — Artist` while it is paused

A thin progress bar under the line fills as the track plays. It updates when you seek or start another track.
For a stream with no length, the bar stays empty.

- **Tap** the line to play or pause.
- **Long-press** it, or tap **⏭**, to skip to the next track.

The line disappears 30 minutes after you pause playback. It also hides while you type.
If the header is off (`header.show off`, the default), the line shows at the top of home.

## Toggles

| Command | Action | Requires |
| --- | --- | --- |
| `torch [on\|off]` | flashlight; with no argument, it toggles | nothing |
| `volume [0-100\|up\|down\|mute\|unmute]` | media volume; prints the new level | nothing |
| `ring [normal\|vibrate\|silent]` | ringer mode; with no argument, it shows the mode | Do Not Disturb access for `silent` (and to leave silent) |
| `dnd [on\|off\|alarms\|none]` | Do Not Disturb. `on` is priority only, `alarms` is alarms only, `none` is total silence. With no argument, it toggles. `dnd status` shows the state. Since Android 15, `off` turns off only what Kolahal turned on. If Do Not Disturb was turned on somewhere else, it stays on, and Kolahal tells you | Do Not Disturb access |
| `brightness [0-100\|up\|down\|auto]` | screen brightness. Setting a level turns adaptive brightness off; `auto` turns it on | Modify system settings |
| `rotate [on\|off]` | auto-rotate; with no argument, it toggles | Modify system settings |
| `screen-timeout <15s\|30s\|1m\|5m\|…>` | screen timeout; with no argument, it shows the timeout | Modify system settings |
| `wifi`, `bt`, `nfc`, `data` | open the Wi-Fi, Bluetooth, NFC or Internet panel | nothing |
| `toggles` | one line per toggle, with its current state | nothing |

Android doesn't let apps turn Wi-Fi, Bluetooth, NFC or mobile data on or off. So these commands
open the system panel or settings page, where you can change it with one tap.

Tap a line from `toggles` to toggle that item or open its panel. If a permission is missing,
the line opens the page where you can grant it.

### Granting access

Both kinds of access are optional. The [setup checklist](/getting-started/setup/) has a
row for each:

- **Do Not Disturb access** (`dnd access`): used by `dnd`, `ring silent` and scripts with `@uses dnd`.
- **Modify system settings** (`brightness access`): used by `brightness`, `rotate` and
  `screen-timeout`.

If a command needs one of these and you haven't granted it, the command prints a line you can tap to
open the right page.

These commands change the device's state. So [instant launch](/getting-started/basics/) never runs them from
part of a name. For example, `torch` waits for Enter. When `launch.instant` is `all`, a complete line such as
`torch on` or `dnd off` runs without Enter.
