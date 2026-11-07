---
title: Productivity
description: Todos, quick notes, reminders, timers, alarms and your calendar agenda from the prompt.
---

Kolahal stores everything on this page on the device, in its own database. Nothing is synced or uploaded.

Commands that change something (`todo`, `note`, `notes`, `remind`, `timer`, `alarm`) always need
Enter. This is true even when [instant launch](/guides/apps/) is set to `all`.

## Todos

```sh
todo buy milk             # add (same as: todo add buy milk)
todo                      # open items, numbered
todo done 2               # tick item 2 (todo undo 2 unticks it)
todo edit 1 buy oat milk  # change the text
todo rm 3                 # delete
todo all                  # include finished items
todo clear                # delete all finished items
```

- Tap an item in the log to tick or untick it. Long-press it for **Mark done**, **Edit in prompt** and
  **Delete todo**.
- `<n>` is the number that `todo` shows. After you type `todo done `, the suggestion strip lists the open items.
  So you rarely need to remember numbers.
- If your todo starts with a subcommand word (`all`, `done`, `clear`, …), use `todo add …`.
- The header can have a `todo` part. By default it is on the header's right side (`header.right`).
  It shows how many todos are open. Tap it to list them.

## Notes

```sh
note gate code 4821       # save a note
notes                     # newest first
notes grep gate           # search
notes rm 2                # delete
```

Tap a note to copy it. Long-press it for **Delete note**.

## Reminders

```sh
remind 10m tea            # in ten minutes
remind 1h30m stretch
remind 18:30 gym          # today, or tomorrow if 18:30 has passed
remind 6pm call home
remind tomorrow 9:00 call the bank
remind fri 8am standup notes  # today, if it's Friday and 8am is still ahead
remind dec 3 10:30 dentist    # or 3 dec, december 3rd, 2027-12-03; 9:00 without a time
remind on dec 3 2027 at 10 renew passport
reminders                 # pending, soonest first
remind rm 1               # cancel
```

How Kolahal reads dates and times:

- If a date has no year and has already passed this year, it means next year.
- After a date, a number is an hour only if it comes after `at`, as in `dec 3 at 10`.
  So `remind dec 3 2 tickets` is a reminder about two tickets.
- A time before 6 AM needs confirmation, in case you didn't mean it. For example, `remind tomorrow 2 tickets` reads
  the time as 2 AM. It doesn't set a reminder. Instead it shows two lines to tap: 2 AM for `tickets`, or 9:00 for `2 tickets`.
- `remind --yes tomorrow 5:30 flight` skips the question. This is useful in an alias.
- A duration, such as `remind 2h pills`, needs no confirmation.

### Repeating reminders

```sh
remind every day 8:00 pills
remind every weekday 9:30 standup     # Monday to Friday
remind every weekend 10:00 long run
remind every mon,thu 19:00 bins       # days with commas, no spaces
remind every month 1st rent           # the last day in shorter months for the 29th to 31st
remind every year mar 14 Alice's birthday
```

- A repeating reminder goes off at the same time on each of its days. If you give no time, it uses 9:00.
- It stays in the `reminders` list with its schedule.
- It keeps the same local time in another time zone and after the clocks change.
- Sometimes a time is skipped when the clocks change, for example 2:30 when clocks go from 2:00 to 3:00.
  On that night only, the reminder goes off an hour later.
- `every year feb 29` uses the 28th in other years.
- `remind rm` cancels a repeating reminder.

More about reminders:

- A reminder posts a notification on the **Reminders** channel and adds a line to the log.
- The notification has three buttons:
  - **Snooze 10 min** and **Snooze 1 hour** remind you one more time. A repeating reminder also keeps its schedule.
  - **Done** dismisses it.
- Tap the log line to put a 10-minute snooze command in the prompt.
- Reminders stay set after restarts and reboots.
- On Android 12 and later, you can turn off exact alarms for an app. If you do, Kolahal schedules reminders
  the normal way, and they may come late, by up to an hour. `remind` prints a line you can tap to allow
  exact alarms again.
- If notifications are off, the reminder still shows up in the log. Run `notifications` to allow
  them.
- In a reminder list, tap a line to put `remind rm …` in the prompt. Long-press it for
  **Cancel reminder**.

## Timers and alarms

Timers and alarms use your clock app. The clock app makes the sound:

```sh
timer 25m focus           # starts at once (timer 25 also means 25 minutes)
timer                     # show running timers
alarm 7:30 gym            # opens the clock app with the alarm filled in
alarm 6am
alarms                    # show your alarms
stopwatch                 # open the clock app (Android has no stopwatch shortcut)
```

## Agenda

```sh
agenda                    # today and the next two days
agenda 7                  # a week (up to 31 days)
```

```
Today · Sat 17 Oct
All day      Diwali
09:30–10:00  Standup
Tomorrow · Sun 18 Oct
19:00–21:00  Dinner with Sam
```

- All-day events come first. Events that already ended today are not shown.
- Tap an event to open it in your calendar app.
- The first time, `agenda` prints a line. Tap it to grant the **Calendar** permission. You can
  also grant it in the [setup checklist](/getting-started/setup/) (**Calendar (agenda)**).
- Declined events and hidden calendars are skipped.
