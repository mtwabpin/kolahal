---
title: FAQ
description: Answers to common questions, such as why the wallpaper flashes when you go home.
---

## Why does my wallpaper flash when I go home?

With gesture navigation, when you swipe home from an app, your phone's wallpaper can show for a moment
before Kolahal appears. This happens even when Kolahal's wallpaper settings are off.

Kolahal doesn't draw it. Android gives those gestures to your phone's own launcher, whatever home
app you use. While you swipe, that launcher shows the wallpaper behind the app. It opens
Kolahal only at the end. A home app can't take over those gestures.

There are two ways to stop the flash:

| | What to do | Downside |
| --- | --- | --- |
| **Three-button navigation** | In your phone's settings, search for "navigation" and choose the buttons | You lose the swipe gestures |
| **`wallpaper match`** | Type it in Kolahal and tap to confirm | Your home screen's wallpaper becomes a plain color |

### What `wallpaper match` does

It sets your home screen's wallpaper to the theme's color. Then no wallpaper shows when you go home.

- **It asks you to confirm first.** Android doesn't let an app read the wallpaper it replaces, so Kolahal can't bring
  the old one back. If you want the picture later, keep a copy of it.
- **It follows the theme.** If you pick another theme, or dark mode switches, the wallpaper changes too.
- **You can still set your own wallpaper.** If you do, Kolahal doesn't change it, and the flash comes back. Run `wallpaper match` again to go back to the plain color.
- **The lock screen keeps its own wallpaper.**
- **The wallpaper behind Kolahal also becomes that color.** `log.wallpaper` and `widgets.wallpaper` show only the plain color.
- **It doesn't work with the `material-you` theme** on Android 12 and newer. That theme takes its colors from the wallpaper, so the wallpaper can't follow it:
  each would keep changing the other. `wallpaper match` refuses while that theme is on. If the wallpaper was already Kolahal's, it stays as it is, and follows again when you pick another theme.

`wallpaper` with no arguments shows whether the wallpaper is Kolahal's or yours.
