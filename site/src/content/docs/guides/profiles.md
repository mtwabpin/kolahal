---
title: Work profile & private space
description: Personal, work, private space and clone profiles in one launcher, and how to pause or resume them.
---

Kolahal lists apps from every profile that Android shows to the launcher:

- **personal**
- **work** (a managed profile)
- **private space** (Android 15+)
- **clone** profiles ("dual apps")

## Tags and colours

Each app has a tag for its profile, and each profile has its own colour:

| Tag | Profile | Flag |
| --- | --- | --- |
| `[W]` | work | `-w`, `--work` |
| `[S]` | private space | `-s`, `--private` |
| `[C]` | clone | `-c`, `--clone` |
| `[P]` | personal | `-p`, `--personal` |

Kolahal shows `[P]` only when the same app is also in another profile. Normal personal apps have no tag.

Use the flags to pick a profile in `open`, `info`, `uninstall`, `hide`, `unhide` and `rename`:

```sh
open gmail -w        # the work Gmail
info chrome -p       # app info for the personal Chrome
apps work            # only work apps (also: personal, private)
```

If an app is in two profiles, long-press it. The sheet offers **Launch in … profile** for the other copy.

## Pausing and resuming

```sh
work                 # work profile: on / paused
work off             # pause the work profile
work on              # resume it
private off          # lock private space
private on           # unlock it
```

To turn profiles on or off, you need **Android 10+**, and Kolahal must be the **default home app** (`home`).

- You can add `profiles` as one of the header's parts (`header.left` or `header.right`). Then the header
  shows the work profile's status. Tap it to resume a paused profile. To pause, you must tap twice,
  so you can't pause it by accident.
- Apps in a paused profile are faded. Tap one, in the app selector, a search or the log, and Kolahal
  asks **Resume work apps?** (for the private space: **Unlock the private space?**). Tap **Resume** (**Unlock**):
  Kolahal turns the profile on, waits for it (up to 20 s) and opens the app. For the private space,
  Android asks for your lock first. **Cancel** leaves the profile as it is.
- Typed, `open <app>` prints a line that offers the same instead, under what you typed. Tap it.
- While the private space is locked, its lines in the log (launches, notifications) are hidden too.
  They come back when you unlock it.

## Private space

On Android 15+, the default launcher can see the private space profile. Kolahal declares
`ACCESS_HIDDEN_PROFILES` for this. Private space apps have the tag `[S]`.
`private on|off|status` unlocks the private space, locks it or shows its status.
The [setup checklist](/getting-started/setup/) lists the profiles it found.
