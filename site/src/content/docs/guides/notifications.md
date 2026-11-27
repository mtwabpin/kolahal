---
title: Notifications
description: Your notifications in the terminal log, muting apps, and notifications posted by scripts and feeds.
---

Kolahal works with notifications in two separate ways:

1. **Notification access** lets Kolahal *read* your notifications, so they appear in the log.
2. The **notifications permission** lets Kolahal *post* notifications for scripts and feeds.

## Notifications in the log

Grant access with `allow read-notifications` or **setup → Notification access**. After that, new
notifications appear in the log as `title — text`. The line ends with the app's status-bar icon, dimmed.

- If the icon isn't saved, the line ends with the app's name instead, for example `· Mail`.
  This happens for older lines, and for apps that draw their icon while running.
- Notifications that an app marks as secret are not shown.
- When you copy a notification line, the copy is marked as sensitive. This stops keyboards from
  showing it in their clipboard previews.

What you can do with a line:

- **Tap** it to open the notification. If the notification is gone, the app opens.
- **Long-press** it to dismiss it, mute the app, or copy the text.
- **Delete** it, with the [swipe](/guides/gestures/) that deletes a line or **Delete** in the long-press
  sheet. The notification is dismissed too, so it also goes from the notification shade. See
  [Deleting a line dismisses its notification](#deleting-a-line-dismisses-its-notification).

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
[`notifications.recent(n)`](/scripting/api/#notificationsrecentn). A script can also run when a
notification arrives, with `@on notification`: for every app, or
[only for the apps it names](/scripting/overview/#notifications-from-some-apps).

### Deleting a line dismisses its notification

A swipe that deletes a line offers **Undo** for a few seconds. A dismissed notification can't be brought
back, so Kolahal dismisses it only once **Undo** is gone:

- Tap **Undo** in time, and the line is back and the notification stays where it is.
- **Delete** in the long-press sheet has no **Undo**, so the notification is dismissed at once.
- A notification that can't be dismissed, such as music or a call, stays. Only its line goes.
- A notification that is already gone is fine: nothing happens, and nothing is said.
- Clearing the log (`clear`) and lines the log deletes by itself don't dismiss anything, and neither does
  **Delete with output** on a command's line. `notifs clear` dismisses all your notifications.

For example, a message from Alex shows as a line. Swipe it away, and a few seconds later it is gone from the
shade and the lock screen too.

To only remove lines from the log, turn off **Settings → Log → Deleting a line dismisses its notification**,
or run `config set notifs.dismiss-on-delete off`. The setting shows once Kolahal has notification access.

## Notifications from scripts and feeds

To allow Kolahal to post notifications, run `allow send-notifications`:

- On Android 13 and newer, it asks for the permission.
- On older versions, after you said no, or with Kolahal's notifications turned off, it opens
  Kolahal's notification settings.

Then:

- A script posts with [`notify(title, body, ctx)`](/scripting/api/#notifytitle-body-ctx).
  This only works if notifications are on for that script. Turn them on with `script notify <name> on`,
  or with the switch in the script's sheet in the script manager.
- A feed with notifications on (`feed notify <id> on`) posts one notification for each fetch that finds
  new items. See [Feeds](/guides/feeds/).
