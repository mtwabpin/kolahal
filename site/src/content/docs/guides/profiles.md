---
title: Work profile & private space
description: Personal, work, private space and clone profiles in one launcher, and how to pause or resume them.
---

Kolahal lists apps from every profile that Android shows to the launcher:

- **personal**
- **work** (a managed profile)
- **private space** (Android 15+)
- **clone** profiles ("dual apps")

## Tags and colors

Each app has a tag for its profile, and each profile has its own color:

| Tag | Profile | Flag |
| --- | --- | --- |
| `[W]` | work | `-w`, `--work` |
| `[S]` | private space | `-s`, `--private` |
| `[C]` | clone | `-c`, `--clone` |
| `[P]` | personal | `-p`, `--personal` |

Kolahal shows `[P]` only when the same app is also in another profile. Normal personal apps have no tag.

Where the app's icon shows, in the app selector and in `apps`, the icon carries the system's badge
for its profile, and the tag is left out. Without icons, and in the TTY style, the tag
shows. A screen reader hears the profile either way ("Maps, work").

Use the flags to pick a profile in `open`, `info`, `uninstall`, `hide`, `unhide` and `rename`:

```sh
open gmail -w        # the work Gmail
info chrome -p       # app info for the personal Chrome
apps work            # only work apps (also: personal, private)
```

If an app is in two profiles, long-press it. The sheet offers **Launch in …** (the work profile, the private space) for the other copy.
When one copy can open and the other is in a paused profile or a locked private space, typing its name, Enter and the first
suggestion take the one that can open, whichever you used last.

## Pausing and resuming

```sh
work                 # work profile: on / paused
work off             # pause the work profile
work on              # resume it
private              # private space: unlocked / locked
private off          # lock the private space
private on           # unlock it
```

To turn profiles on or off, you need **Android 10+**, and Kolahal must be the **default home app** (`home`).

- You can add `profiles` as one of the header's parts (`header.left` or `header.right`). Then the header
  shows the work profile and the private space. Tap one to resume or unlock it. To pause or lock, you must tap twice,
  so you can't do it by accident.
- Apps in a paused work profile are faded. Tap one, in the app selector, a search or the log, and Kolahal
  asks **Resume work apps?** Tap **Resume**: Kolahal turns the profile on, waits for it (up to 20 s) and opens the app.
  **Cancel** leaves the profile as it is.
- Typed, `open <app>` prints a line that offers the same instead, under what you typed. Tap it.
- A paused profile's apps have no shortcuts until it is on again: `shortcuts <app> -w` says it is paused, with a tap that resumes it.

## Private space

On Android 15+, only the default home app can see the private space. Kolahal declares
`ACCESS_HIDDEN_PROFILES` for this. Private space apps have the tag `[S]`.
While Kolahal isn't the home app, `private` and `apps private` say so, with `home` to make it the home app.
The [setup checklist](/getting-started/setup/) lists the profiles it found.

### While it is locked

Kolahal shows what the phone's own launcher shows: none of the private space's apps.

- They are in no list, the app selector, the suggestions, the pinned and most-used strip or `pins`, and nothing you type finds them.
- `apps` shows one line in their place, **Private space is locked**. Tap it to unlock the space. `apps private` says the same.
- Its lines in the log (launches, notifications, its apps in `apps`) are hidden too.
- What points at one of its apps still works: an alias (`open maps -s`), a line in the log, a widget's line. It offers to unlock the
  space, and Android asks for your lock. Kolahal then opens the app. A dialog asks first after a tap: **Unlock the private space?**
- When you unlock it, everything comes back at once, pinned apps in their places.

If Android's **Hide private space when it's locked** is on (Android 16+), Kolahal shows no trace of the space while it is
locked: no line in `apps`, and no lock in the header or the quick actions. `private on`, typed in full, still unlocks it.
