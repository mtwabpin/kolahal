---
title: Privacy & permissions
description: What each permission is for, and what leaves the device.
---

Kolahal has no account, no analytics, no ads and no crash reporting service. A
[crash report](#crash-reports) stays on your phone until you share it.

Kolahal uses these libraries: AndroidX, Jetpack Compose, WorkManager, OkHttp and the two script engines (LuaJava and quickjs-kt).
Your log, settings, scripts, feeds and notifications are stored in the app's private storage on the phone.

## What leaves the device

Kolahal itself makes network requests in exactly five cases:

1. **Scripts** that you write or install. They make requests through `http.*` and `rss.*`. If a log line links to an image (`image`),
   the home screen downloads the image when the line appears.
   - Scripts connect only to the URLs in their code.
   - The [library scripts](/scripting/library/) use wttr.in, Open-Meteo, CoinGecko, open.er-api.com, api.ipify.org, Wikipedia,
     xkcd.com, ZenQuotes, Reddit, GitHub and hnrss.org.
   - Requests send the `User-Agent` `Kolahal-Launcher/0.1 (+https://github.com/mtwabpin)`, unless the script sets its own.
   - A script has internet access only if its code makes requests, puts links on its lines or runs commands. You can switch
     it off ([switching access off](/scripting/overview/#switching-access-off)).
2. **Feeds** you subscribe to: one request for each feed when it is due.
3. **Currency conversion** with `= 100 usd in eur`: a request to open.er-api.com for the base
   currency. The result is cached for six hours.
4. **Getting scripts**: when you open Get more scripts, refresh it or run `script get`, Kolahal downloads the list of
   shared scripts from mtwabpin.github.io (and keeps it for an hour). It also downloads the file of a script you look at
   or install.
5. **Updates**, in the full build only, and only when you run them. `update` asks GitHub for the
   latest release, and `update install` downloads its APK from GitHub. Nothing checks in the background.

Commands like `search`, `yt`, `wiki`, `maps`, `play` and `url` don't make requests themselves. They
pass a link to your browser or the matching app.

Plain `http://` is allowed, because many feeds are still served without TLS.

## Crash reports

If Kolahal stops unexpectedly, it keeps a report on the phone. The report has:

- the app version and build
- the Android version and the phone model
- which part of Kolahal stopped
- the error with its stack trace (the code that was running)

On Android 11 and newer, the report also notes when Kolahal froze ("not responding") or crashed inside native code, such as the
script engines. This comes from what Android recorded.

The next time you open the home screen, a red line says that Kolahal stopped unexpectedly. Tap it to share the
report with the share sheet (by email, in a chat, or by pasting it into a GitHub issue).

- `crashes` lists the reports, and `crashes clear` deletes them.
- **Settings → About → Crash reports** also lists them. Tap a report to:
  - email it to the developer (your mail app opens a draft with the report, and you send it yourself)
  - share it with the share sheet
  - delete it

  You can also delete all reports at once.

Nothing is sent automatically. Kolahal keeps the newest 10 reports, and they are not included in Android's backup.
An error message can contain a bit of what you were doing (a command, a file name), so read the report before you send it.

:::note[Android's own backup]
The app allows Android's system backup. If device backup is on in your Android settings, Android
can include Kolahal's data in it, as it does for other apps. Kolahal's own
[backup file](/guides/backup/) is separate. Kolahal writes it only where you choose.
:::

## Permissions

The Play build (the Google Play version, also available as an APK on GitHub) doesn't have four of these:
`READ_CALL_LOG`, `READ_CONTACTS`, `REQUEST_INSTALL_PACKAGES` and the accessibility service. So calls
in the log, `update` and the screen lock are only in the full build.

Instead of reading your contacts, the Play build opens Android's contact picker. The picker shows Kolahal only the person you pick.
Kolahal keeps that person's name and numbers on the phone, not in Android's cloud backup, until you run `contacts --forget`
([People](/guides/people/#in-the-play-build)).

Permissions declared in the manifest:

| Permission | Why |
| --- | --- |
| `INTERNET` | scripts, feeds and currency rates (see above) |
| `ACCESS_NETWORK_STATE` | the network item in the header and `device.network()` |
| `READ_CONTACTS` | `call`, `sms` and `contacts` find people by name. Kolahal asks the first time you use them. Optional. Not in the Play build |
| `READ_CALL_LOG` | calls in the log, and `calls`. Kolahal asks the first time you use them. Optional. Not in the Play build |
| `REQUEST_INSTALL_PACKAGES` | `update install` passes the new version to Android's installer. Android asks you to allow it the first time, and the installer asks before it installs. Optional. Not in the Play build |
| `READ_CALENDAR` | `agenda` lists upcoming events, and the Agenda widget shows them. Kolahal asks the first time you use `agenda`, or when you tap the widget. Optional. Nothing leaves the device |
| `SET_ALARM` | `timer` and `alarm` pass the timer or alarm to your clock app |
| `SCHEDULE_EXACT_ALARM` | `remind` and scripts' daily times run on time. Android 12 and newer lets you turn it off. Then they can be up to an hour late |
| `RECEIVE_BOOT_COMPLETED` | pending reminders and scripts' daily times are set again after a restart |
| `FOREGROUND_SERVICE` | a script runs at its daily time in the foreground, for a few seconds. Android lets a launcher pause or resume the work profile only in the foreground |
| `POST_NOTIFICATIONS` | notifications from scripts and feeds. Android 13 and newer asks for this when the app runs |
| `REQUEST_DELETE_PACKAGES` | `uninstall` and the Uninstall action open Android's uninstall dialog |
| `EXPAND_STATUS_BAR` | swiping down on the header (or `shade`) pulls down the notification shade |
| `SET_WALLPAPER` | `wallpaper match` sets the home screen's wallpaper when you ask for it. It sets it again when the theme changes, if the wallpaper is still Kolahal's |
| `PACKAGE_USAGE_STATS` | Usage Access, which you grant in Settings: accurate unlock counts and `usage` screen time |
| `ACCESS_NOTIFICATION_POLICY` | lists Kolahal on the Do Not Disturb access page, for `dnd`, `ring silent` and scripts with `@uses dnd`. You still have to grant it |
| `WRITE_SETTINGS` | "Modify system settings", which you grant in Settings: `brightness`, `rotate`, `screen-timeout` |
| `ACCESS_WIFI_STATE` | whether Wi-Fi is on, for `toggles` |
| `WAKE_LOCK` | keeps the phone awake while a script runs at its daily time. Android's work scheduler also uses it |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | added by AndroidX, private to Kolahal. It stops other apps from receiving Kolahal's own broadcasts |
| `ACCESS_HIDDEN_PROFILES` | lets the default launcher see the private space (Android 15 and newer) |

`torch` needs no camera permission. It only switches the flash on or off.

Kolahal also asks Android for two lists:

- the list of apps you can open (a `<queries>` entry for launcher activities). Kolahal needs it to show your apps at all.
- the list of apps with widgets, for the widget picker.

## Special access you can grant

These are off until you turn them on, usually from the [setup checklist](/getting-started/setup/):

| Access | Why | Without it |
| --- | --- | --- |
| Default home app | opens when you press Home; app shortcuts, pausing and resuming profiles, private space | works as a normal app |
| Usage access | accurate unlock counts, `usage` | unlocks are counted only while the launcher runs; no `usage` |
| Notification access | notifications in the log, `notifs`, `notifications.recent()`, `media` and the header's now-playing line (reading media sessions) | none of these |
| Do Not Disturb access | `dnd`, `ring silent`, scripts with `@uses dnd` | those commands print a line you can tap to grant it; scripts can't change Do Not Disturb |
| Modify system settings | `brightness`, `rotate`, `screen-timeout` | those commands print a line you can tap to grant it |
| Accessibility service **Kolahal screen lock** | double-tap and `lock` lock the screen. It can't read window content and receives no events | no lock gesture |
| Alarms & reminders (Android 12+) | `remind` and scripts' daily times run on the minute | up to an hour late; a script can't pause the work profile at night |
| Unrestricted battery | timers and feeds run on time | Android may delay them |

**Notification access.** Before Android's Notification access screen opens (from `notifs enable` or Setup), Kolahal explains
what it reads and why. It opens the screen only if you tap **Allow**.

Notification text that Kolahal reads stays on the device:

- It goes into the log and into a list in memory.
- Kolahal keeps a copy of the 50 most recent notifications, so scripts can call `notifications.recent()`.
- Only scripts that ask for it with `@uses notifications`, and that you allow, can read them. They never get the private space's notifications.

A script could send what it reads somewhere else. So install only scripts you trust, and read the code of scripts you get from
other people. For this reason, scripts imported from a file or a backup start switched off. Each script's access switches can
take away what it may read.

**Secrets.** API keys you set for scripts ([secrets](/scripting/overview/#api-keys-and-other-secrets)) are
encrypted with a key kept in Android's Keystore. They leave the phone only in requests to the sites that their `@secret` line
names, over https. The script itself holds only a placeholder. Secrets are not in backups, exported scripts or the log.
