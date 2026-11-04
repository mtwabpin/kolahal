---
title: Work profile & private space
description: Personal, work, private space and clone profiles in one launcher, and how to pause or resume them.
---

Kolahal lists apps from every profile Android gives the launcher: **personal**, **work** (a
managed profile), the Android 15+ **private space**, and **clone** profiles ("dual apps").

## Tags and colours

Apps are tagged by profile, each in its own colour:

| Tag | Profile | Flag |
| --- | --- | --- |
| `[W]` | work | `-w`, `--work` |
| `[S]` | private space | `-s`, `--private` |
| `[C]` | clone | `-c`, `--clone` |
| `[P]` | personal | `-p`, `--personal` |

`[P]` is only shown when the same app also exists in another profile, so ordinary personal apps stay
untagged. The flags pick a profile in `open`, `info`, `uninstall`, `hide`, `unhide` and `rename`:

```sh
open gmail -w        # the work Gmail
info chrome -p       # app info for the personal Chrome
apps work            # only work apps (also: personal, private)
```

Long-press an app that exists twice and the sheet offers **Launch in … profile** for the other copy.

## Pausing and resuming

```sh
work                 # work profile: on / paused
work off             # pause the work profile
work on              # resume it
private off          # lock private space
private on           # unlock it
```

Toggling profiles needs **Android 10+** and Kolahal set as the **default home app** (`home`).

- With `profiles` as one of the header's items (`header.item-1` or `header.item-2`), the header
  shows the work profile's status. Tapping it resumes a paused profile. Pausing asks for a second
  tap, so you can't pause it by accident.
- Apps in a paused profile are faded. Opening one prints a line offering to resume the profile and
  launch the app; tap it and Kolahal turns the profile on, waits for it (up to 20 s) and opens the app.
- While the private space is locked, its lines in the log (launches, notifications)
  are hidden too, and come back when you unlock it.

## Private space

On Android 15+, the default launcher can see the private space profile (Kolahal declares
`ACCESS_HIDDEN_PROFILES` for this). Its apps are tagged `[S]`, and `private on|off|status` unlocks it, locks it or shows its status. The [setup checklist](/getting-started/setup/) lists the profiles it found.
