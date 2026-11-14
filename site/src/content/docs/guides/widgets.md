---
title: Widgets
description: Host Android home-screen widgets on a widgets page beside the terminal, with Kolahal's own widgets and your scripts' widgets.
---

Kolahal can show the same home-screen widgets as any other launcher: clocks, weather, calendar,
music, notes. The widgets are on a separate **widgets page** beside the terminal, so they take no space from it.
The page also has [Kolahal's own widgets](#kolahals-own-widgets), such as your todos and what is
playing, and [widgets that scripts fill](#widgets-from-scripts).

## The widgets page

To open the widgets page, swipe sideways on the home screen. Start the swipe on the header, on empty space or on the prompt.
A swipe that starts on a log line copies or deletes that line instead.

- **+ Add** and **Edit** are at the top of the page.
- Back or Home takes you back to the terminal.
- `widgets.page` sets the side of the page: `left` (the default) or `right`. `off` turns the page off.

![The widgets page with a clock widget, + Add and Edit at the top](../../../assets/parts/widgets-page.png)

The page can show your phone's wallpaper (the one you set in Android) behind the widgets.
**Settings → Widgets → Transparency** (`widgets.wallpaper`) sets how much of it shows:

- 0 shows none of it. This is the default.
- 100 shows all of it, with none of the theme's colour over it.

The log has its own setting for this (`log.wallpaper`, under **Settings → Log**). So each page can show
more or less of the wallpaper.

When the [app selector](/getting-started/parts/#app-selector) is on (`selector.rail`), it is on the widgets page
too. Its strip is in the same place and uses the same settings as on the log. Its list uses the whole page,
and while it is open, the widgets fade out behind it.

You can make home open on the widgets page. It needs the [app selector](/getting-started/parts/#app-selector)
first: there it is your way to open apps. Turn it on in **Settings → App selector** (`selector.rail`), then
**Settings → Widgets → Start on the widgets page** (`widgets.start`). Then:

- Back and Home take you to the widgets page. Swipe sideways to reach the log.
- If you turn the app selector off, home opens on the log again. Turn the selector back on and home opens on the widgets page again.
- If you type on a hardware keyboard, Kolahal switches to the log and puts the text in the prompt.
- **Open the keyboard on Home** opens the keyboard when you reach the log.
- The page has no **+ Add** and **Edit** row. Long-press a widget to arrange widgets, or use `widget add`.

## Add a widget

You can add a widget in three ways:

- Tap **+ Add** on the widgets page.
- Open **Settings → Widgets → Arrange widgets** (or run `widgets`) and tap **Add widget**.
- Run `widget add` to go straight to the picker.

The picker lists every installed widget, grouped by app. It shows each widget's preview and default size
(`4×2` and so on). Tap a widget to add it:

1. The first time, Android asks if Kolahal may create widgets. Allow it. Tick "always" so Android doesn't ask
   again.
2. Some widgets have a setup screen, for example to pick a city, a calendar or a note. It opens next.
   If you leave it before you finish, the widget isn't added.

## Kolahal's own widgets

Kolahal has seven widgets of its own. They show what Kolahal knows itself: your todos, your
reminders, what is playing. Kolahal draws them as cards, in the theme's colours.

- The picker lists them first, in the **Kolahal** group.
- They are live. A card changes when its data changes. No script runs for them, and they have no
  setup screen.
- Each is placed once. After that its row in the picker says **Added**.
- A long-press on a card opens **Arrange widgets**. There you arrange and resize it like any other
  widget.

| Widget | It shows | A tap |
| --- | --- | --- |
| **Battery and network** | the battery's level in a ring, and the network | opens Android's battery screen |
| **Shortcuts** | up to 8 buttons, for commands you pick | on a button: runs its command |
| **Now playing** | the track, the artist and the app, with previous, play or pause, and next | on a button: does that. On the card: opens the player |
| **Todos** | your open todos, each with a checkbox | on a row: marks the todo done. On the card: runs `todo` |
| **Agenda** | your calendar's events for today and the next days | on an event: opens it in your calendar app. On the card: runs `agenda` |
| **Reminders** | the next reminder and the two after it, with the time to each | runs `reminders` |
| **Notes** | your newest note, or the newest three in a taller card | runs `notes` |

When a tap runs a command, it runs as if you had typed it. Home turns to the log, where the
answer is.

### Todos

The card lists your open [todos](/guides/productivity/#todos), the oldest first.

- Tap a row to mark its todo done. The row stays where it is, ticked, until you leave the widgets
  page. So a tap by mistake is easy to undo: tap the row again.
- A todo that you add with `todo buy milk` shows on the card at once.
- A card with more todos than fit says how many more there are. Tap the card for the whole list.

### Reminders and Notes

- **Reminders** shows your next three [reminders](/guides/productivity/#reminders). Each row has
  the text, when it is due, how it repeats, and the time to it ("in 25m"). The time counts down
  by the minute.
- **Notes** shows your newest [note](/guides/productivity/#notes). A taller card shows the newest
  three, each on one line.

### Shortcuts

**Shortcuts** is a card of buttons for commands that you choose: `torch`, `dnd`, `todo`, `weather`,
`open maps`. A tap on a button runs its command, as if you had typed it and pressed Enter.

A new Shortcuts card has no buttons. It says "No shortcuts yet. Tap to add commands."

To edit your shortcuts:

1. Open the **Shortcuts** sheet in one of two ways:
   - Tap the card beside its buttons. While the card has no buttons, tap it anywhere.
   - Or open **Settings → Widgets → Arrange widgets**, select the Shortcuts widget, and tap
     **Edit shortcuts** in the toolbar at the bottom.
2. Tap **Add shortcut**, and fill in the boxes:
   - **Command**: what you would type in the terminal, such as `torch`. While you type its first
     word, the commands that start so are offered under the box. Tap one to take it.
   - **Label**: what the button says. If you leave it empty, the button says the command.
   - **Icon**: an emoji or a letter, if you like.
3. Tap **Add**.

In the sheet, each shortcut has its own row:

- Tap a row to change that shortcut.
- The up and down arrows move it earlier or later. The buttons on the card are in this order.
- The bin removes it.

Each change is saved at once, and the card follows.

- A command can be up to 200 characters, a label up to 24, and an icon up to 4.
- The card has up to 8 buttons. A small card shows the first ones only, and never makes a button
  smaller. So put the important ones first.
- The command can be any command, also one such as `torch` or `dnd` that a
  [script](/scripting/overview/) may not run. You chose it yourself.
- A script can't change your shortcuts.
- Your shortcuts are in a [backup](/guides/backup/). After an import, the result lists each
  shortcut that the file brought.

### Now playing

The card shows what plays in your music or podcast app: the title, the artist and the app, and a
bar for the position.

- The three buttons are previous, play or pause, and next. A narrow card has fewer buttons: play
  or pause is always there.
- A tap elsewhere on the card opens the app that plays.
- While something is paused, the card keeps it, however long ago you paused it. So you can start
  it again from here. With nothing to show, the card says "Nothing playing".
- It needs [notification access](/guides/media-and-controls/#media), as the header's line for
  what is playing does. Without it, the card says "Allow notification access", and a tap asks
  for it.

### Agenda

The card shows the events of your phone's calendar for today and the next two days, under
"Today", "Tomorrow" and the date. The [`agenda`](/guides/productivity/#agenda) command reads the
same calendar.

- Each event has its time in front. An all-day event says "all day" and comes first in its day.
  One that goes on says "now".
- A tap on an event opens it in your calendar app. A tap elsewhere runs `agenda`.
- An event that has ended leaves the card by itself.
- With no event in these days, the card says "No events in the next 3 days".
- It needs the calendar permission. Without it, the card says "Allow calendar access", and a tap
  asks for it.
- Kolahal reads the calendar when the widgets page comes into view, and every 15 minutes while you
  stay there.

## Widgets from scripts

A [script](/scripting/overview/#widgets) can have a widget too: a card that Kolahal draws from what the
script tells it. For example the weather, a feed's headlines, or your unlocks of the last 7 days.

- The picker lists these in the **Kolahal** group, after Kolahal's own: a row for each of your scripts
  that has a widget, with its kind and the size it starts with. Eighteen
  [library scripts](/scripting/library/#widgets) have one.
- A script that keeps a [status line](/scripting/overview/#status-lines) has a second row, its
  **Status card**: the line as a card, with no change to the script.
- You can place a script's widget more than once ([below](#the-same-widget-more-than-once)). A
  status card is placed once.
- The card is drawn in the theme's colours.
- A tap does what the script chose: it runs the script again, opens a link, or refreshes the card.
- A long-press opens **Arrange widgets**. There you arrange and resize the card like any other widget.
  It can be as small as a quarter of the page wide.
- Under old content, the card says its age ("2 h ago"). After a run that failed, it says "Failed 10:42"
  and keeps what it had.

### The same widget more than once

A script's widget can be on the page several times, each with its own settings: the weather for
two cities, a countdown to two days.

1. Place the script's widget as usual. Its row in the picker then says
   **Added · tap to add another**.
2. Tap that row. One more widget is added. If the script has options, its **Options** sheet opens.
3. Set what this widget should show: its city, its day. These are the script's
   [options](/scripting/overview/#options).

To change a widget's options later:

1. Open **Settings → Widgets → Arrange widgets**.
2. Select the script's card: long-press it, or tap the edit button and then the card.
3. Tap **Options** in the toolbar at the bottom. It shows only for a script with options.

What to know:

- A script can have 6 widgets at most. Then its row in the picker says **Added · 6 of 6**.
- A further widget follows the script's own options until you set one for that widget. After
  that it keeps its own value for that option.
- The first widget has no options of its own. Its **Options** are the script's, the same as in the
  script's sheet and `script set`. A change in the first widget's **Options** sheet also runs the
  further widgets that follow it.
- The script's timer fills the first widget only. A further widget runs the script for itself:
  when you place it, when you look at it and its content is old, when you tap it, and when you
  change an option in its **Options** sheet. So more widgets don't run anything more in the
  background. A change made in the script's sheet on the Scripts screen, or with `script set`,
  runs no widget.
- If you removed the first widget and a further one is still placed, a tap on the row brings the
  first widget back. The timer fills only the first.
- Removing a widget deletes what it showed and its own options. The script and its other widgets
  stay.
- Kolahal's own widgets and a script's status card are placed once.

## Arrange and resize

Each widget has a width and a height. The page is twelve columns wide. So a widget can take a
sixth, a quarter, a third, half, two thirds or more of it, one twelfth at a time.

Narrower widgets sit side by side, in page order. If the next widget doesn't fit beside them, it starts a new row.

To change a widget, open **Settings → Widgets → Arrange widgets**. Then either long-press a widget, or tap the edit
button in the top bar and then tap a widget. A toolbar appears at the bottom:

| Button | Action |
| --- | --- |
| Width − / + | narrower or wider, a twelfth of the page at a time |
| Height − / + | shorter or taller, 8dp at a time (never below the size the widget needs) |
| Square | as tall as it is wide, or as close as the widget allows |
| ← / → | move it earlier or later on the page |
| delete | remove it (asks first) |

Hold − or + to repeat. The page in Settings shows the widgets the same way as the widgets page.
Kolahal gives each widget its real width and height, so it fits the space.

## Uninstalled apps and updates

When an app is updated, its widgets keep working. When an app is uninstalled, Kolahal removes its widgets
from the page.

## Backups

A [backup](/guides/backup/) records which widgets you placed, with their size and order. But other apps'
widgets themselves can't move between installs. So after an import:

1. Open **Settings → Widgets → Arrange widgets**. The widgets are listed under **Restored from backup**.
2. Tap **Add again** on each widget to add it back. Run its setup if it has one.

Each widget keeps its size and position.

Kolahal's own widgets and your scripts' widgets need no **Add again**. They are back on the page after
the import.

- A backup holds where a card is, not what it showed. Each card fills again by itself.
- A script's further widgets come back too, each with the options you set for it.
- An imported script arrives switched off. Its card says "Script is off" until you turn the script on.
- Your shortcuts come back with the Shortcuts widget. The import result lists each shortcut that
  the file brought, so you can check what its button runs.

The same happens when Android restores Kolahal on a new phone from Android's own backup. A line in the log says
how many widgets you need to add again. Tap it to open that page.

## From the terminal

```sh
widgets        # open Settings → Widgets → Arrange widgets
widgets ls     # list placed widgets (tap a line to open them)
widget add     # open the picker
```

See also the [command reference](/reference/commands/#widgets) and
[configuration keys](/reference/config/#widgets).

## Work profile

Work-profile widgets work like any other widget. They have a **work** badge in the picker.

While the work profile is paused, or the private space is locked, these widgets show a dim **paused** placeholder.
They come back when you turn work apps on.
