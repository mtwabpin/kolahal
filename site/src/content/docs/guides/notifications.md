---
title: Notifications
description: Your notifications in the terminal log, muting apps, and notifications posted by scripts and feeds.
---

Kolahal works with notifications in two separate ways:

1. **Notification access** lets Kolahal *read* your notifications, so they appear in the log.
2. The **notifications permission** lets Kolahal *post* notifications for scripts and feeds.

## Notifications in the log

Grant access with `notifs enable` or **setup → Notification access**. After that, new
notifications appear in the log as `title — text`. The line ends with the app's status-bar icon, dimmed.

- If the icon isn't saved, the line ends with the app's name instead, for example `· Mail`.
  This happens for older lines, and for apps that draw their icon while running.
- Notifications that an app marks as secret are not shown.
- When you copy a notification line, the copy is marked as sensitive. This stops keyboards from
  showing it in their clipboard previews.

What you can do with a line:

- **Tap** it to open the notification. If the notification is gone, the app opens.
- **Long-press** it to dismiss it, mute the app, or copy the text.

To keep the log readable, Kolahal skips these notifications:

- its own notifications
- ongoing ones, such as music or navigation
- foreground-service and group-summary notifications
- notifications from low-importance (silent) channels
- notifications without a title or text

Some apps post the same notification again with new content. Kolahal handles this as follows:

- A chat app posts its notification again for every message. Each new message gets a line.
  If the new post has the same text as the last line, it gets no new line.
- Other apps sometimes post a notification again with new text, for example a count going up.
  Kolahal logs at most one line per 30 seconds for these.
- If a notification is dismissed and posted again later, it counts as new.
- Silent updates from other apps are not logged. Examples: a timer counting down, download progress, a player's current track.

```sh
notifs                 # active notifications
notifs all             # recent ones, including dismissed
notifs mute whatsapp   # stop logging an app (it still shows in the shade)
notifs unmute whatsapp
notifs muted           # list muted apps (tap one to get its unmute command)
notifs log off         # stop logging notifications at all; notifs log on to resume
notifs clear           # dismiss everything and clear the list
```

To open the notification shade, swipe down on the header (see [Gestures](/guides/gestures/)).

While Kolahal has access, scripts can read recent notifications with
[`notifications.recent(n)`](/scripting/api/#notificationsrecentn).

## Notifications from scripts and feeds

To allow Kolahal to post notifications, run `notifications`:

- On Android 13 and newer, it asks for the permission.
- On older versions, it opens Kolahal's notification settings.
- `notifications settings` always opens these settings.

Then:

- A script posts with [`notify(title, body, ctx)`](/scripting/api/#notifytitle-body-ctx).
  This only works if notifications are on for that script. Turn them on with `script notify <name> on`,
  or with the switch in the script's sheet in the script manager.
- A feed with notifications on (`feed notify <id> on`) posts one notification for each fetch that finds
  new items. See [Feeds](/guides/feeds/).
