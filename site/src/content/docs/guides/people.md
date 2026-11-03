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

- The first time you use one of these, Kolahal asks for the **Contacts** permission. Nothing is
  uploaded; contacts are only read to match names. The Play build never asks: it uses Android's
  contact picker ([below](#in-the-play-build)).
- After `call `, `sms ` or `contacts ` your contacts appear as suggestions right away, favourites (★)
  first, and narrow as you type a name or number. A contact with several numbers gets one row per
  number; choosing one fills in that number. `sms` always completes to the number, so you can type
  the message straight after it.
- If a name matches several contacts, they are listed instead: tap one to call, long-press to
  message, open the contact or copy the number.
- `call` opens the dialer with the number filled in. It never places the call by itself. `sms`
  opens your messaging app with the text pre-filled, ready for you to send.
- Contact and number lines in the log stay tappable later: tap dials, long-press offers call,
  message, open contact and copy number.

## In the Play build

The [Play build](/getting-started/install/#the-play-build) doesn't read your contacts: Google Play requires apps to use Android's contact picker when it's enough. So `call`, `sms` and `contacts` work
from the people you pick:

```sh
call                      # pick someone, then dial them
sms alice running late    # Alice picked before: straight to the message
contacts                  # pick someone and show them in the log
contacts --forget alice   # Kolahal forgets Alice
```

- With no name, or a name Kolahal doesn't know yet, Android's contact picker opens. Pick someone and Kolahal continues: it dials them, starts the message (with your text) or shows them in the log.
- Kolahal sees only the person you pick, and keeps only their name and numbers, on the phone, so next time you can use just the name: `call alice`. They appear as suggestions after `call `, `sms `
  and `contacts `, with **Pick a contact** at the end to add someone.
- `contacts --forget <name>` removes someone. Uninstalling Kolahal or clearing its data removes everyone.

## Calls in the log

With **call log** access, every call gets a line in the log as it ends, the way app installs do
(the full build only: the [Play build](/getting-started/install/#the-play-build) can't read the
call log):

```text
Call from Sarah ↙
  +91 98765 43210
  10:42 AM · 4m 12s
Call to Sarah ↗
Missed call from +91 98765 43210 ✕
```

The arrow at the end is the direction (↙ incoming, ↗ outgoing); missed and declined calls end in
✕, blocked ones in ⊘. The number, then the time and length (or "not answered"), are on dimmed lines
below. Tap a line to dial back; long-press to call, message, copy the number or open the contact.

- `calls` lists the last 20 calls from Android's call log, newest first, with the day; `calls 50` lists more.
- To turn this on, grant access: `calls --allow`, or **Call log** in the [setup checklist](/getting-started/setup/).
  Calls from before you allowed it aren't added to the log. To stop, remove the permission in
  Android's app settings (`calls --settings`).
- Calls made while the launcher wasn't running appear when it next starts (at most the newest 30).
