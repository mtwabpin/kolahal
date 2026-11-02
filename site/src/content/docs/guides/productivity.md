---
title: Productivity
description: Todos, quick notes, reminders, timers, alarms and your calendar agenda from the prompt.
---

Everything here is stored on the device, in Kolahal's own database. Nothing is synced or uploaded.
Commands that change something (`todo`, `note`, `notes`, `remind`, `timer`, `alarm`) always need
Enter, even with [instant launch](/guides/apps/) set to `all`.

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

- Tap an item in the log to tick or untick it. Long-press for **Mark done**, **Edit in prompt** and
  **Delete todo**.
- `<n>` is the number `todo` shows. After `todo done ` the suggestion strip lists the open items,
  so you rarely need to remember numbers.
- If your todo starts with a subcommand word (`all`, `done`, `clear`, …), use `todo add …`.
- The header shows `todo 3` while you have open items; tap it to list them. Turn it off with
  `config header.todo off`.

## Notes

```sh
note gate code 4821       # save a note
notes                     # newest first
notes grep gate           # search
notes rm 2                # delete
```

Tap a note to copy it. Long-press offers **Delete note**.

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

A date with no year that has already passed this year means next year. After a date, a number is read as an hour only after `at` (`dec 3 at 10`), so `remind dec 3 2 tickets` is a reminder about two tickets.

A time before 6 AM needs confirmation, in case it isn't what you meant. `remind tomorrow 2 tickets` shows it read 2 AM, doesn't set a reminder, and gives two lines to tap: 2 AM for `tickets`, or 9:00 for `2 tickets`. `remind --yes tomorrow 5:30 flight` skips the question, which is useful in an alias.
A duration such as `remind 2h pills` needs no confirmation.

### Repeating reminders

```sh
remind every day 8:00 pills
remind every weekday 9:30 standup     # Monday to Friday
remind every weekend 10:00 long run
remind every mon,thu 19:00 bins       # days with commas, no spaces
remind every month 1st rent           # the last day in shorter months for the 29th to 31st
remind every year mar 14 Alice's birthday
```

A repeating reminder fires at the same time on each of its days (9:00 if you give no time), and stays listed in `reminders` with its schedule. It keeps that time in another time zone, and after the clocks change: on the night a time is skipped (2:30 when clocks go from 2:00 to 3:00), it fires an hour later, that night only. `every year feb 29` is the 28th in other years. `remind rm` cancels it.

- A reminder posts a notification on the **Reminders** channel and adds a line to the log. The
  notification's **Snooze 10 min** and **Snooze 1 hour** remind you once more (a repeating one also keeps its schedule), and **Done** dismisses it. Tapping the log line puts a 10-minute snooze command in the prompt.
- Reminders survive restarts and reboots.
- Android 12 and later let you turn off exact alarms for an app. Kolahal then schedules reminders
  normally, and they may arrive late, up to an hour. `remind` prints a line you can tap to allow
  exact alarms again.
- If notifications are off, the reminder still shows up in the log. Run `notifications` to allow
  them.
- In a reminder list, tapping a line puts `remind rm …` in the prompt. Long-press offers
  **Cancel reminder**.

## Timers and alarms

These use your clock app, which handles the ringing:

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

- All-day events come first. Events that already ended today are left out.
- Tap an event to open it in your calendar app.
- The first time, `agenda` prints a line to tap that asks for the **Calendar** permission. You can
  also grant it from the [setup checklist](/getting-started/setup/) (**Calendar (agenda)**).
  Declined events and hidden calendars are skipped.
