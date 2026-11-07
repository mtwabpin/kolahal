---
title: Backup & restore
description: Back up everything to one JSON file — settings, apps, scripts, todos, notes, reminders, feeds and the log — or sync scripts with a folder.
---

You can keep your setup safe, or move it to another phone, in four ways.

## Backup file

**Settings → Backup → Export everything** saves one file, `kolahal-backup.json`. You choose where with
the system file picker. The file contains:

- settings you changed from the default
- [themes you made](/guides/themes-and-settings/#your-own-themes)
- the font you imported with `font import`
- aliases
- hidden apps, custom labels and pinned favourites
- every script with its code, timers, notification flag and on/off state
- **everything else** under `data`:
  - todos, notes and reminders
  - feed subscriptions, with their items and read state
  - each script's `store` values
  - the whole log and ↑ history
  - app launch history and unlock history
  - notification and trigger settings

The file is plain JSON, so you can edit it on a computer. Use **Import a backup** to load it on any phone.

```json
{ "kolahal": 2,
  "config":  { "theme.name": "gruvbox-material", "log.spacing": "16" },
  "aliases": { "m": "open maps" },
  "apps":    { "hidden": [], "labels": {}, "pinned": [] },
  "scripts": [ { "name": "hn", "lang": "lua", "code": "...", "every": 30,
                 "at": ["08:00"], "notify": true, "enabled": true } ],
  "data":    { "prefs": { … }, "tables": { "todos": [ … ], "feeds": [ … ], "logs": [ … ] },
               "scriptStores": { "hn": { "seen": "…" } } } }
```

When you import a file:

- The `data` section **replaces** what is on the phone: todos, notes, reminders, feeds, the log and so on.
  After that, Kolahal applies the readable sections above. So if you edited those sections by hand, your edits win.
- Script stores are matched to their script by name.
- Kolahal schedules reminders, feeds and timers again, and then restarts to load everything.
- Older backups without a `data` section still import as before.
- Unknown or invalid settings are skipped and listed.
- Aliases with the same name as a built-in command are skipped.
- **New scripts are imported switched off.** This lets you review them before they run on a timer.
- A script that already exists (same name and language) keeps its `enabled` flag only if its code
  hasn't changed. If its code changed, it is also imported switched off.
- Scripts with the same name as a built-in command are skipped.
- A script with the same name as an existing script but a different language is skipped.
- Restored log lines keep their text. When you tap them, they can't run commands or app shortcuts.
  They keep only web, map and store links.
- The import result lists settings you may want to check: a custom search address and gesture commands.
- Timer intervals under 15 minutes are raised to 15, because that is Android's minimum.

Hidden apps and labels are saved per component and profile. They only carry over to a new phone if those match.

Some things can't move between phones, so they are not in the file:

- the folder you picked with `script dir` (you must grant this storage permission again)
- the live ids of home-screen widgets (the widgets are listed, so you can add them again)

Some things always stay on the phone:

- notification and call lines
- lines with someone's number
- the `call` and `sms` lines you type
- a private space's lines, and its apps' pins, labels, hidden flags and launch history
- responses that scripts have cached
- the copy of recent notifications kept for scripts
- this phone's record of its calls and apps (a restore keeps the new phone's record, so it doesn't log every app as installed)

The file can still contain private things, such as notes and the rest of the log. Keep it somewhere safe.

## Automatic backups

Kolahal can save the backup file for you every day or every week, in a folder you pick.
Use **Settings → Backup → Automatic backups**, or:

```sh
backup every daily     # or weekly, or off
backup                 # one now
backup dir             # pick the folder
```

How it works:

- Each file name has the date it was saved, for example `kolahal-backup-2026-10-27.json`.
  A second backup on the same day replaces that day's file.
- Kolahal keeps the newest 7 files and deletes older ones. To keep more, use `config set backup.keep 30`.
  Other files in the folder are not changed.
- If you haven't set a folder, turning backups on asks you to pick one. `backup` also asks you to pick one, then saves the backup.
- **Backup folder** in Settings shows where backups go and when the last one was saved.
- If a backup fails (for example, because the folder was deleted), a line in the log lets you pick the folder again.

If you lose your phone, you still want your backup. So pick a folder that is copied off the phone, such as a
folder your cloud drive app syncs, or a memory card.

## Moving to a new phone

On Android 12 and newer, Android can transfer your data from your old phone to the new one, over a cable or Wi-Fi.
This carries your settings and everything else: scripts, todos, notes, reminders, feeds and the log.

Script secrets don't work on the new phone, because only the old phone's Keystore can read them. Set them again.

Android's cloud backup carries only your settings. For everything else, use the backup file.

## Single script files

To import scripts from files:

1. Open the script manager (`scripts`).
2. Tap **Add script → Import a file**.
3. Pick one or more files in the file manager.

The file name becomes the script name. If you import a file with the same name again, it updates the script.
Imported scripts are switched off. Review the script, then turn it on with its switch or with `script on <name>`.

To save a script as a file, tap the script and use **Export** in its sheet.

## Folder sync

```sh
script dir       # pick a folder (any Storage Access Framework location)
script export    # write every script plus kolahal.json (timers and flags) there
script import    # read them back: new scripts are created, changed ones updated
```

On import, `kolahal.json` sets the timers and flags it lists. This replaces any changes you made to them since the export.
If a script changed but `kolahal.json` doesn't list it, the script keeps your timers and flags. The exception is when its `@every` or `@at` line changed.

Folder sync is useful if you keep scripts in a synced folder or in a git repository on your computer.

- An export deletes only the files of scripts you deleted on this phone since its last export.
  It never deletes files that another phone put there.
- Scripts from a backup or from the folder are imported switched off, like scripts from any file.
