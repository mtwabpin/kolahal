---
title: Widgets
description: Host Android home-screen widgets on a widgets page beside the terminal.
---

Kolahal can show the same home-screen widgets as any other launcher: clocks, weather, calendar,
music, notes. The widgets are on a separate **widgets page** beside the terminal, so they take no space from it.

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

You can make home open on the widgets page. It needs the [app selector](/getting-started/parts/#app-selector)
first: there it is your way to open apps. Turn it on in **Settings → App selector** (`selector.rail`), then
**Settings → Widgets → Start on the widgets page** (`widgets.start`). Then:

- Back and Home take you to the widgets page. Swipe sideways to reach the log.
- The [app selector](/getting-started/parts/#app-selector) is on the widgets page too. It is in the same place and uses the same
  settings as on the log. While its list is open, the widgets fade out behind it.
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

A [backup](/guides/backup/) records which widgets you placed, with their size and order. But the widgets
themselves can't move between installs. So after an import:

1. Open **Settings → Widgets → Arrange widgets**. The widgets are listed under **Restored from backup**.
2. Tap **Add again** on each widget to add it back. Run its setup if it has one.

Each widget keeps its size and position.

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
