---
title: Privacy & permissions
description: What each permission is for, and what leaves the device.
---

Kolahal has no account, no analytics, no ads and no crash reporting service: a
[crash report](#crash-reports) stays on your phone until you share it. Its dependencies are AndroidX,
Jetpack Compose, WorkManager, OkHttp and the two script engines (LuaJava and quickjs-kt). Your log,
settings, scripts, feeds and notifications are stored in the app's private storage on the phone.

## What leaves the device

Kolahal itself makes network requests in exactly five cases:

1. **Scripts** you write or install, through `http.*` and `rss.*`, and any image a log line links to (`image`), which the home screen downloads when the line appears. They connect only to the URLs in their code. The [library scripts](/scripting/library/) use wttr.in, Open-Meteo,
   CoinGecko, open.er-api.com, api.ipify.org, Wikipedia, xkcd.com, ZenQuotes, Reddit, GitHub
   and hnrss.org. Requests carry a `User-Agent` of `Kolahal-Launcher/0.1 (+https://github.com/mtwabpin)` unless the
   script sets its own. A script has internet access only if its code makes requests, puts links
   on its lines or runs commands, and you can switch it off
   ([switching access off](/scripting/overview/#switching-access-off)).
2. **Feeds** you subscribe to: one request per feed when it is due.
3. **Currency conversion** with `= 100 usd in eur`: a request to open.er-api.com for the base
   currency, cached for six hours.
4. **Getting scripts**: when you open Get more scripts, refresh it or run `script get`, the list of
   shared scripts from mtwabpin.github.io (kept for an hour), and the file of a script you look at
   or install.
5. **Updates**, in the full build only, and only when you run them: `update` asks GitHub for the
   latest release, and `update install` downloads its APK from GitHub. Nothing checks in the background.

Commands like `search`, `yt`, `wiki`, `maps`, `play` and `url` don't make requests themselves. They
hand a link to your browser or the matching app.

Plain `http://` is allowed, because many feeds are still served without TLS.

## Crash reports

If Kolahal stops unexpectedly, it keeps a report on the phone: the app version and
build, the Android version, the phone model, which part of Kolahal stopped, and the error with
its stack trace (the code that was running). On Android 11 and newer it also notes when Kolahal
froze ("not responding") or crashed inside native code such as the script engines, from what
Android recorded.

The next time you open the home screen, a red line says Kolahal stopped unexpectedly; tap it to share the
report with the share sheet (email, a chat, or paste it into a GitHub issue). `crashes` lists the
reports, and `crashes clear` deletes them. **Settings → About → Crash reports** lists them too: tap
one to email it to the developer (your mail app opens a draft with the report, which you send
yourself), to share it with the share sheet, or to delete it, or delete them all at once. Nothing is sent automatically, the newest 10 are kept, and
they are left out of Android's backup. An error message can contain a bit of what you were doing
(a command, a file name), so read the report before you send it.

:::note[Android's own backup]
The app allows Android's system backup. If device backup is on in your Android settings, Android
may include Kolahal's data in it, as it does for other apps. Kolahal's own
[backup file](/guides/backup/) is separate and is only written where you choose.
:::

## Permissions

The Play build (the Google Play version, also an APK on GitHub) leaves four of these out:
`READ_CALL_LOG`, `READ_CONTACTS`, `REQUEST_INSTALL_PACKAGES` and the accessibility service. The calls
in the log, `update` and the screen lock are only in the full build. Instead of reading your contacts,
the Play build opens Android's contact picker, which shows Kolahal only the person you pick; it keeps
their name and numbers on the phone, out of Android's cloud backup, until `contacts --forget`
([People](/guides/people/#in-the-play-build)).

Declared in the manifest:

| Permission | Why |
| --- | --- |
| `INTERNET` | scripts, feeds and currency rates (see above) |
| `ACCESS_NETWORK_STATE` | the `net` item in the header and `device.network()` |
| `READ_CONTACTS` | `call`, `sms` and `contacts` name matching. Asked for on first use; optional. Not in the Play build |
| `READ_CALL_LOG` | calls in the log, and `calls`. Asked for on first use; optional. Not in the Play build |
| `REQUEST_INSTALL_PACKAGES` | `update install` hands the new version to Android's installer. Android asks you to allow it the first time, and the installer asks before it installs; optional. Not in the Play build |
| `READ_CALENDAR` | `agenda` lists upcoming events. Asked for on first use; optional; nothing leaves the device |
| `SET_ALARM` | `timer` and `alarm` hand the timer or alarm to your clock app |
| `SCHEDULE_EXACT_ALARM` | `remind` and scripts' daily times fire on time (Android 12+ lets you turn it off; they may then be up to an hour late) |
| `RECEIVE_BOOT_COMPLETED` | pending reminders and scripts' daily times are set again after a reboot |
| `FOREGROUND_SERVICE` | a script runs at its daily time in the foreground, for a few seconds: Android lets a launcher pause or resume the work profile only in the foreground |
| `POST_NOTIFICATIONS` | notifications from scripts and feeds (Android 13+ asks at runtime) |
| `REQUEST_DELETE_PACKAGES` | `uninstall` and the Uninstall action open Android's uninstall dialog |
| `EXPAND_STATUS_BAR` | swipe down on the header (or `shade`) pulls down the notification shade |
| `SET_WALLPAPER` | `wallpaper match`, once you ask for it, sets the home screen's wallpaper, and again when the theme changes while it's still that one |
| `PACKAGE_USAGE_STATS` | Usage Access, which you grant in Settings: accurate unlock counts and `usage` screen time |
| `ACCESS_NOTIFICATION_POLICY` | lists Kolahal on the Do Not Disturb access page, for `dnd`, `ring silent` and scripts with `@uses dnd` (you still grant it) |
| `WRITE_SETTINGS` | "Modify system settings", which you grant in Settings: `brightness`, `rotate`, `screen-timeout` |
| `ACCESS_WIFI_STATE` | whether Wi-Fi is on, for `toggles` |
| `WAKE_LOCK` | keeps the phone awake for a script's daily time while it runs (Android's work scheduler uses it too) |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | added by AndroidX, private to Kolahal: keeps its own broadcasts from other apps |
| `ACCESS_HIDDEN_PROFILES` | lets the default launcher see the Android 15+ private space |

`torch` needs no camera permission: it only switches the flash.

Kolahal also asks Android for the list of launchable apps (a `<queries>` entry for launcher
activities), which it needs to show your apps at all, and for the apps with widgets, for the widget
picker.

## Special access you can grant

These are off until you enable them, usually from the [setup checklist](/getting-started/setup/):

| Access | Why | Without it |
| --- | --- | --- |
| Default home app | opens on Home; app shortcuts, pausing/resuming profiles, private space | works as a normal app |
| Usage access | accurate unlocks, `usage` | unlocks counted while the launcher runs; no `usage` |
| Notification access | notifications in the log, `notifs`, `notifications.recent()`, `media` and the header's now-playing line (reading media sessions) | none of these |
| Do Not Disturb access | `dnd`, `ring silent`, scripts with `@uses dnd` | those commands print a tappable line to grant it; scripts can't change Do Not Disturb |
| Modify system settings | `brightness`, `rotate`, `screen-timeout` | those print a tappable line to grant it |
| Accessibility service **Kolahal screen lock** | double-tap / `lock` locks the screen. It can't read window content and receives no events | no lock gesture |
| Alarms & reminders (Android 12+) | `remind` and scripts' daily times on the minute | up to an hour late; a script can't pause the work profile at night |
| Unrestricted battery | timers and feeds run on time | Android may defer them |

Before Android's Notification access screen opens (`notifs enable`, or Setup), Kolahal explains what it reads and why, and opens it only if you tap **Allow**. Notification text read through
notification access stays on the device: it goes into the log and
an in-memory list, and a copy of the 50 most recent is kept so scripts can call
`notifications.recent()`: only scripts that ask for it with `@uses notifications` and that you
allow, and never the private space's notifications. A script could send what it reads somewhere,
so only install scripts you trust and read the code of ones you get from others.
Scripts imported from a file or a backup are switched off for that reason, and each script's access switches can revoke what it may read.

API keys you set for scripts ([secrets](/scripting/overview/#api-keys-and-other-secrets)) are
encrypted with a key kept in Android's Keystore and never leave the phone except in requests to the
sites their `@secret` line names, over https: the script itself only holds a placeholder. They aren't in
backups, exported scripts or the log.
