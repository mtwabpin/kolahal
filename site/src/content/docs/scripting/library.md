---
title: Script library
description: The 16 ready-made Lua and JavaScript scripts bundled with Kolahal.
---

Kolahal includes 16 scripts you can install with one tap in the script manager (**Add script → From the
library**), or from the prompt:

```sh
script lib                 # list them, with ✓ for installed ones
script install weather     # install (or update) one
```

More scripts, shared by people who use Kolahal, are in **Scripts → Add script → Get more scripts** and
`script get` ([getting scripts from others](/scripting/overview/#getting-scripts-from-others)).

Installing sets the script's suggested timers and notification flag. They are only suggestions:
change them with `script every`, `script at` and `script notify`, or in the script manager.

Some have [options](/scripting/overview/#options): weather's city, the coins crypto shows, how
many stories hn logs. Change them in the script's sheet (**Options**) or with
`script set weather city Pune`; `script set weather` lists them. Changing a script's options doesn't count as editing it, so it still updates with Kolahal.

A copy you haven't edited updates with Kolahal: when an update includes a newer version, it replaces yours automatically. Its timers and switches are kept, anything new it requests with `@uses` or `@on` stays off until you allow it (a line in the log lets you), and the log lists the updated scripts. A copy you edited isn't updated, and is marked **(edited)** in `script lib` and "edited" in the script manager, whose
**Differs from the library** chip lists them all. **Compare**, in its card's details or its sheet,
shows how it differs: lines only in the library version in red, lines only in yours in the theme's
colour; `script diff weather` prints the same in the log. It is only replaced when you ask: `script install weather --replace`, or **Reset to library
version** beside Compare (or at the bottom of Compare). Export it first to keep your version.

Scripts that keep a status line (weather, hn, countdown and others) show it at the top of home once
you pin them: `script pin weather`, or long-press the line.

None of the network scripts need an API key.

| Script | Lang | Command | Suggested timer | Notifies |
| --- | --- | --- | --- | --- |
| [hn](#hn) | Lua | | every 1h | yes |
| [releases](#releases) | Lua | yes | every 3h | yes |
| [reddit](#reddit) | JS | yes | | |
| [weather](#weather) | Lua | yes | every 1h | |
| [forecast](#forecast) | JS | yes | daily 07:00 | |
| [sun](#sun) | JS | yes | daily 05:00 | |
| [crypto](#crypto) | JS | yes | every 30m | |
| [exchange](#exchange) | JS | yes | | |
| [ip](#ip) | Lua | yes | every 30m | yes |
| [wikipedia](#wikipedia) | JS | yes | | |
| [xkcd](#xkcd) | JS | yes | every 6h | yes |
| [quote](#quote) | JS | yes | daily 08:00 | yes |
| [countdown](#countdown) | Lua | yes | daily 00:05 | |
| [charge-reminder](#charge-reminder) | Lua | | on charger and unplug | yes |
| [night-dnd](#night-dnd) | Lua | | daily 23:00 and 07:00 | |
| [night-work](#night-work) | Lua | | daily 23:00 and 07:00 | |

## hn

**Hacker News**: new front-page stories with 50+ points since the last run (via hnrss.org), up
to 10 lines. Notifies about the top new story and keeps `HN: N new` as its status line. Options:
`points` (`0` for every story) and `lines`.

## releases

**GitHub releases**: watches repositories through their release Atom feeds and notifies about each
new release: Kolahal, Neovim and Termux by default. The first check of a repo only records what
exists, so you aren't flooded.

```sh
releases                     # check now
releases add neovim/neovim
releases rm neovim/neovim
releases list                # the watched repos; tap one for its releases
```

## reddit

**Reddit top posts**: today's top 5 posts of a subreddit with scores and comment counts, falling
back to the subreddit's RSS feed when Reddit blocks anonymous JSON requests. `reddit` (its `subreddit` option, r/android by default) or `reddit linux`. The `posts` option shows up to 25.

## weather

**Weather**: current weather in one line from wttr.in. On a timer it keeps the result as its status line.

```sh
weather                      # its city option (or your IP's location)
weather Paris
script set weather city Pune # the city when you type none
```

## forecast

**3-day forecast** from Open-Meteo: conditions, min/max temperature and rain chance. On a timer it
keeps today's summary as its status line. `forecast` (its `city` option, London if it's empty) or `forecast Tokyo`.

## sun

**Sunrise and sunset**, plus day length, from Open-Meteo, in the location's own time zone.
`sun Lisbon` or `sun 12.97 77.59` saves the location; then just `sun`.

## crypto

**Crypto prices** with 24h change from CoinGecko, lined up in columns. On a timer its status line is a
compact one such as `BTC 67.1k ▲ 1.2% · ETH 3.1k ▼ 0.4%`.

```sh
crypto                       # bitcoin and ethereum in USD
crypto solana dogecoin       # CoinGecko ids
crypto bitcoin --vs eur
```

Its `coins` and `currency` options set what `crypto` shows with no arguments and on its timer.

## exchange

**Currency converter** with daily rates from open.er-api.com. On a timer, or run with no arguments, it converts its `from` option to its `to` options (USD to EUR, GBP, INR and JPY by default) and pins
the rates. The built-in [`= 100 usd in eur`](/guides/web-and-calculator/#currency) covers simple
cases.

```sh
exchange 100 usd inr eur
exchange usd inr             # 1 USD
exchange 50 eur              # in USD
```

## ip

**Public IP** from api.ipify.org; long-press the line to copy it. On a timer it notifies when the
IP changes (handy for noticing a dropped VPN) and sets it as its status line.

## wikipedia

**Wikipedia summary** with the article's picture and a link to the full article: `wikipedia Alan Turing`. It tries the exact
title, then the best search match. With no topic it shows a random article. Its `lang` option picks
another language edition: `script set wikipedia lang de`.

## xkcd

**xkcd**, the comic itself in the log with its alt text below: `xkcd`, `xkcd 327`, `xkcd random`. On a timer it notifies only when a
new comic is out.

## quote

**Quote of the day** from ZenQuotes; long-press to copy. At its daily time it is also sent as a notification.

## countdown

**Countdowns**: days until (or since) dates, lined up in columns, with the nearest one as its status
line. No network.

```sh
countdown 2026-12-25
countdown add 2026-12-25 Christmas
countdown                    # list, nearest first
countdown rm 2
```

## charge-reminder

**Charge reminder**: runs when you plug the charger in and when you unplug it, with no timer and
no network: logs how much a session added, and notifies when you unplug below 30% (its `low`
option). Run manually, it shows the last session.

## night-dnd

**Night Do Not Disturb**: turns Do Not Disturb on at 11 PM and off at 7 AM. If it's already on at 11 PM, you turned it on, so the script doesn't change it at 11 PM or 7 AM. It only turns off what it turned on. Since Android 15, what it turns on is Kolahal's own Do Not Disturb mode, so one you turn on
yourself stays on in the morning. Kolahal needs Do Not Disturb access: run `dnd access` once. Its
`mode` option is `alarms` for alarms only or `none` for total silence. To change the times, change
`ON` and `OFF` at its top and
its daily times to match (`script at night-dnd 22:30, 06:30`). Run manually, it shows what it will do.
Uses `@uses dnd`; no network.

## night-work

**Night work profile**: pauses the work profile at 11 PM and resumes it at 7 AM, the same way: if it's already paused at 11 PM, you paused it, and the script doesn't change it at either time. Android
lets only the default launcher pause and resume the work profile, and only while it's in the foreground, so at its daily times Kolahal runs it in the foreground, which needs **Alarms & reminders** allowed for
Kolahal. If your work profile asks for its PIN to resume, Android asks you for it. Change the times
as for night-dnd. Uses `@uses work`; no network.
