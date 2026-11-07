---
title: People
description: Call, text and find contacts from the prompt, and see your calls in the log.
---

```sh
call mum                  # dial a contact (alias: dial)
call +44 20 7946 0000     # or a number
sms alex running late     # start a message with text (alias: text)
contacts                  # favourites, or the first 20 contacts if you have none
contacts sam              # search (alias: contact)
```

- The first time you use one of these commands, Kolahal asks for the **Contacts** permission.
  Kolahal uploads nothing. It reads contacts only to match names. The Play build never asks for this permission.
  It uses Android's contact picker instead ([see below](#in-the-play-build)).
- When you type `call `, `sms ` or `contacts `, your contacts appear as suggestions right away.
  Favourites (★) come first. The list gets shorter as you type a name or number.
- A contact with several numbers gets one row per number. Choose a row to fill in that number.
  `sms` always completes to the number, so you can type the message right after it.
- If a name matches several contacts, Kolahal lists them. Tap one to call. Long-press one to
  send a message, open the contact or copy the number.
- `call` opens the dialer with the number filled in. It never starts the call by itself.
- `sms` opens your messaging app with the text filled in. You send it yourself.
- You can still tap contact and number lines in the log later. A tap dials the number. A long-press lets you call,
  send a message, open the contact or copy the number.

## In the Play build

The [Play build](/getting-started/install/#the-play-build) doesn't read your contacts. Google Play requires apps
to use Android's contact picker when that is enough. So in this build, `call`, `sms` and `contacts` work
with the people you pick:

```sh
call                      # pick someone, then dial them
sms alice running late    # Alice picked before: straight to the message
contacts                  # pick someone and show them in the log
contacts --forget alice   # Kolahal forgets Alice
```

- If you give no name, or a name Kolahal doesn't know yet, Android's contact picker opens.
  Pick someone, and Kolahal continues: it dials them, starts the message with your text, or shows them in the log.
- Kolahal sees only the person you pick. It saves only their name and numbers, on the phone.
  Next time, you can use just the name: `call alice`.
- People you picked appear as suggestions after `call `, `sms ` and `contacts `.
  **Pick a contact** at the end of the list lets you add someone.
- `contacts --forget <name>` removes someone. If you uninstall Kolahal or clear its data, it forgets everyone.

## Calls in the log

If Kolahal has **call log** access, each call gets a line in the log when it ends, like app installs do.
This works only in the full build. The [Play build](/getting-started/install/#the-play-build) can't read the
call log.

```text
Call from Sarah ↙
  +91 98765 43210
  10:42 AM · 4m 12s
Call to Sarah ↗
Missed call from +91 98765 43210 ✕
```

- The arrow at the end shows the direction: ↙ incoming, ↗ outgoing.
- Missed and declined calls end with ✕. Blocked calls end with ⊘.
- The dimmed lines below show the number, then the time and length (or "not answered").
- Tap a line to call back. Long-press it to call, send a message, copy the number or open the contact.

More details:

- `calls` lists the last 20 calls from Android's call log, newest first, with the day. `calls 50` lists more.
- To turn this on, grant access with `calls --allow`, or with **Call log** in the [setup checklist](/getting-started/setup/).
  Calls from before you granted access are not added to the log.
- To turn it off, remove the permission in Android's app settings (`calls --settings`).
- Calls made while the launcher wasn't running appear the next time it starts (only the newest 30).
