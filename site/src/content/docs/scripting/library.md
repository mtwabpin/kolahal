---
title: Script library
description: The 24 ready-made Lua and JavaScript scripts bundled with Kolahal.
---

Kolahal includes 24 ready-made scripts, five of them templates to start your own from. You can install one with a single tap in the script manager
(**Add script → From the library**), or from the prompt:

```sh
script lib                 # list them, with a tag on the installed ones
script install weather     # install (or update) one
```

People who use Kolahal share more scripts. Find them in **Scripts → Add script → Get more scripts**
or with `script get` ([getting scripts from others](/scripting/overview/#getting-scripts-from-others)).

When you install a script, Kolahal sets its suggested timers and turns its notifications on or off
as suggested. These are only suggestions. Change them with `script every`, `script at` and
`script notify`, or in the script manager.

Some scripts have [options](/scripting/overview/#options), for example weather's city, the coins
that crypto shows, or how many stories hn logs. Change them in the script's sheet (**Options**) or
with `script set weather city Pune`. `script set weather` lists them. Changing options doesn't count
as editing, so the script still updates with Kolahal.

**Updates**

- If you haven't edited your copy, it updates with Kolahal. When a Kolahal update has a newer
  version, it replaces yours automatically, at the next start.
  - Kolahal knows every version the library ever had. A copy that is one of them counts as not edited,
    even an old copy, or one restored from a backup.
  - Its switches are kept. So are its timers, unless they are still what the old version set: then
    they become what the new version sets.
  - Anything new that it asks for with `@uses` or `@on` stays off until you allow it. A line in the
    log lets you allow it.
  - The log lists the updated scripts. The script manager shows one line, for example "Kolahal 0.5.1
    updated 3 scripts". **Show** lists them, and their cards say "updated in 0.5.1" until you close
    the line with ×.
- If you edited your copy, it isn't updated. It is marked **(edited)** in `script lib`. In the
  script manager, its card shows a pill:
  - **Edited · Reset** when the library has nothing newer;
  - **New version · Update** when the library changed since you installed it.

  The **Library version** filters (Up to date, New version, Edited) list them. When any differ, a
  banner says how many; **Review** shows them, and **Update all…** updates the ones you tick. New
  versions start ticked; edited copies with nothing newer don't.
- **Compare** (in the card's details, the script's sheet, or the question before a reset) shows the
  differences. Lines only in the library version are red. Lines only in yours are in the theme's
  colour. `script diff weather` prints the same in the log.
- Your edited copy is replaced only when you ask: run `script install weather --replace`, or tap the
  pill or **Reset to library version** next to Compare (or at the bottom of Compare). The app asks
  first, and offers **Compare first** and **Export your copy first**.
- A reset or an update changes only the code. The timers, options, access and pinning stay. The
  message after it ("lobsters.lua is back to the library version", "hn.lua is updated") has
  **Undo** for 10 seconds: it puts your copy back.
- **Add script → From the library** lists your copies that differ first, under "Your copy differs",
  with Compare and Update or Reset.

Some scripts keep a status line (weather, countdown, cricket and others). To show it at the top of home,
pin the script: run `script pin weather`, or long-press the line.

None of the network scripts need an API key.

| Script | Lang | Command | Suggested timer | Notifies |
| --- | --- | --- | --- | --- |
| [hn](#hn) | Lua | | every 1h | yes |
| [lobsters](#lobsters) | Lua | | every 1h | yes |
| [releases](#releases) | Lua | yes | every 3h | yes |
| [reddit](#reddit) | JS | yes | | |
| [weather](#weather) | Lua | yes | every 1h | |
| [forecast](#forecast) | JS | yes | daily 07:00 | |
| [aqi](#aqi) | JS | yes | daily 08:00 | |
| [sun](#sun) | JS | yes | daily 05:00 | |
| [crypto](#crypto) | JS | yes | every 30m | |
| [exchange](#exchange) | JS | yes | | |
| [ip](#ip) | Lua | yes | every 30m | yes |
| [wikipedia](#wikipedia) | JS | yes | | |
| [xkcd](#xkcd) | JS | yes | every 6h | yes |
| [quote](#quote) | JS | yes | daily 08:00 | yes |
| [countdown](#countdown) | Lua | yes | daily 00:05 | |
| [cricket](#cricket) | JS | yes | every 15m | |
| [football](#football) | JS | yes | every 15m | |
| [charge-reminder](#charge-reminder) | Lua | | on charger and unplug | yes |
| [night-dnd](#night-dnd) | Lua | | daily 23:00 and 07:00 | |
| [night-work](#night-work) | Lua | | daily 23:00 and 07:00 | |
| [template-command](#templates) | Lua | yes | | |
| [template-timer](#templates) | Lua | | every 6h | yes |
| [template-event](#templates) | Lua | | on charger and unplug | |
| [template-api-key](#templates) | Lua | | every 6h | |
| [template-js](#templates) | JS | yes | | |

## hn

**Hacker News**: new front-page stories with 50 or more points since the last run (from
hnrss.org), up to 10 lines: each title, then its points and a link to its comments. A tap on the rest of
the line opens the story. It notifies you about the top new story. It keeps no status line, so it has
nothing to pin.
Options: `points` (`0` for every story) and `lines`.

## lobsters

**Lobsters**: new front-page stories from lobste.rs since the last run, up to 10 lines. Each title
has up to two of its tags, then its score and a link to its comments. A tap on the rest of the line
opens the story, or the discussion for a text post. It notifies you about the top new story. A story
shows once its score reaches the `score` option (5 to start with; `0` for every story). Options:
`score` and `lines`.

## releases

**GitHub releases**: watches repositories through their release Atom feeds and notifies you about
each new release. By default it watches Kolahal, Neovim and Termux. The first check of a repository
only records the releases that exist (tagged *latest*), so you don't get a flood of notifications. New
releases are tagged *new*.

```sh
releases                     # check now
releases add neovim/neovim
releases rm neovim/neovim
releases list                # the watched repos; tap one for its releases
```

## reddit

**Reddit top posts**: today's top 5 posts of a subreddit, with scores and comment counts. If Reddit
blocks anonymous JSON requests, it uses the subreddit's RSS feed instead. A tap on a post opens its
discussion; a post that links to another site ends with a link to it.

- `reddit` shows the subreddit in its `subreddit` option (r/android by default).
- `reddit linux` shows r/linux.
- The `posts` option shows up to 25 posts.

## weather

**Weather**: the current weather from wttr.in, as big numbers: the temperature large with the sky
under it, then humidity, wind and the UV index. On a timer, it also sets a one-line summary as
its status line.

```sh
weather                      # its city option (or your IP's location)
weather Paris
script set weather city Pune # the city when you type none
```

## forecast

**3-day forecast** from Open-Meteo: a bar for each day from its lowest to its highest temperature,
all on one scale, with the sky as a symbol and the chance of rain from 30%.
On a timer, it sets today's summary as its status line.

- `forecast` uses its `city` option (London if the option is empty).
- `forecast Tokyo` shows Tokyo.


## aqi

**Air quality**: the US, Indian or European air quality index for a city, from Open-Meteo, with
the last hour, the last 24 hours and the readings behind them. The last hour shows large, in its
band's colour with the band's name under it, and the 24-hour figure and PM2.5 beside it; then a bar of
it on the scale, and the other readings in a table. A typed `aqi` shows
"Fetching…" at once, which turns into the answer.

```sh
aqi Delhi
script set aqi city Delhi      # the city when you type none
script set aqi scale in        # us (default), in or eu
script pin aqi                 # at the top of home, with a coloured bar
```

- Pinned, its status line has a bar in its band's colour. While the air is good, the bar fills
  toward the top of the good band (50, or 20 on the European scale). Past it, the air isn't safe,
  so the bar is full and its colour, orange then red, says how bad.
- With a free token from aqicn.org (`script secret aqi WAQI_TOKEN`), it reads the nearest
  station instead of Open-Meteo's model.
## sun

**Sunrise and sunset** from Open-Meteo, in the location's own time zone, as big numbers: the daylight
left large (before sunrise, the time to it; after sunset, the day's length), then sunrise and sunset,
with a bar of how much of today's daylight has gone. `sun Lisbon` or `sun 12.97 77.59` saves the
location. After that, just type `sun`.

## crypto

**Crypto prices** with the 24-hour change, from CoinGecko: a table for several coins; for one, its
price large and the change beside it, in green or red. On a timer, its
status line is short, for example `BTC 67.1k ▲ 1.2% · ETH 3.1k ▼ 0.4%`.

```sh
crypto                       # bitcoin and ethereum in USD
crypto solana dogecoin       # CoinGecko ids
crypto bitcoin --vs eur
```

Its `coins` and `currency` options set what `crypto` shows with no arguments and on its timer.

## exchange

**Currency converter** with daily rates from open.er-api.com. On a timer, or when you run it with no
arguments, it converts its `from` option to its `to` options and pins the rates. By default this is
USD to EUR, GBP, INR and JPY, as a table; one currency shows its value large, with the rate beside
it. For simple cases, the built-in
[`= 100 usd in eur`](/guides/web-and-calculator/#currency) is enough.

```sh
exchange 100 usd inr eur
exchange usd inr             # 1 USD
exchange 50 eur              # in USD
```

## ip

**Public IP** from api.ipify.org. Long-press the line to copy it. On a timer, it notifies you when
the IP changes and sets the IP as its status line. This helps you notice when a VPN drops.

## wikipedia

**Wikipedia summary**, with the article's picture and a link to the full article:
`wikipedia Alan Turing`.

- It tries the exact title first, then the best search match.
- With no topic, it shows a random article.
- Its `lang` option picks another language edition: `script set wikipedia lang de`.

## xkcd

**xkcd**: the comic itself in the log, with its alt text below. Use `xkcd`, `xkcd 327` or
`xkcd random`. On a timer, it notifies you only when a new comic is out.

## quote

**Quote of the day** from ZenQuotes. Long-press to copy it. At its daily time, it also sends the
quote as a notification.

## countdown

**Countdowns**: the days until (or since) dates. A countdown shows its days in big numbers, with the
date and its weekday beside them. The list starts with the next one that way, then a table: each
countdown's number (for `rm`), its date and when it is. Its status line is the nearest date. It
doesn't use the network.

```sh
countdown 2026-12-25
countdown add 2026-12-25 Christmas
countdown                    # list, nearest first
countdown rm 2
```

## cricket

**Live cricket**: a team's live match as its status line, to pin at the top of home, with the
overs as a bar. It uses ESPN's scores, which aren't a documented API, so they can change without
notice.

```sh
cricket IND                  # India's match, followed while it's on
cricket live                 # every live match, as a table
script pin cricket           # the score at the top of home
```

- `cricket <team>` shows the match with a tag (LIVE, RESULT or UPCOMING), then a table of each
  side's score and, while it's on, the batting side's score large (with the runs to win and the
  balls left in a chase) and the overs as a bar. A typed command shows "Fetching scores…" at
  once, which turns into the answer. If the match is live, the
  script follows that match until it ends, and its result stays 2 hours.
- A team matches its name or its abbreviation. A name also matches teams whose name starts with it:
  `India` matches India A too. Use the abbreviation (`IND`, `NZ`) to pick the senior side.
- Its `team` option is the team its timer looks for (`any`: the first live international), and
  `refresh` is how often the pinned score updates while home shows it (30 seconds, 10 at the fastest).

## football

**Live football** from one league: a team's live match as its status line, to pin at the top of
home, with the minutes played as a bar. It uses ESPN's scores, like `cricket`. `football <team>` shows
the match with a tag (LIVE, FT or UPCOMING); once it has started, the score large with the minute
beside it, and while it's on, the minutes played as a bar.

```sh
football Arsenal             # Arsenal's match, followed while it's on
football live                # the league's live matches, as a table
```

- Its `league` option is ESPN's code for the league: `eng.1` (Premier League, the default),
  `esp.1`, `ger.1`, `ita.1`, `fra.1`, `uefa.champions`, `ind.1`, `usa.1` or another.
- Its `team` option is the team its timer looks for (`any`: the league's first live match), and
  `refresh` is how often the pinned score updates while home shows it.
## charge-reminder

**Charge reminder**: runs when you plug in the charger and when you unplug it. It has no timer and
doesn't use the network.

- It logs each session when you unplug: the battery large, then how much the charge added and how
  long it took, and a bar from the level it started at to the level
  when you unplugged.
- It notifies you when you unplug below 30% (its `low` option).
- When you run it yourself, it shows the last session.

## night-dnd

**Night Do Not Disturb**: turns Do Not Disturb on at 11 PM and off at 7 AM.

- If Do Not Disturb is already on at 11 PM, you turned it on. The script then changes nothing, at
  11 PM or at 7 AM. It only turns off what it turned on.
- Since Android 15, it turns on Kolahal's own Do Not Disturb mode. So if you turn on Do Not Disturb
  yourself, yours stays on in the morning.
- Kolahal needs Do Not Disturb access. Run `dnd access` once.
- Its `mode` option is `priority` (the default), `alarms` (alarms only) or `none` (total silence).
- To change the times, change `ON` and `OFF` at the top of the script, and change its daily times
  to match (`script at night-dnd 22:30, 06:30`).
- When you run it yourself, it shows what it will do.
- It has `@uses dnd`. It doesn't use the network.

## night-work

**Night work profile**: pauses the work profile at 11 PM and resumes it at 7 AM.

- It works like night-dnd: if the work profile is already paused at 11 PM, you paused it, and the
  script changes nothing at either time.
- Android lets only the default launcher pause and resume the work profile, and only while the
  launcher is in the foreground. So at its daily times, Kolahal runs the script in the foreground.
  This needs **Alarms & reminders** allowed for Kolahal.
- If your work profile needs its PIN to resume, Android asks you for it.
- Change the times in the same way as for night-dnd.
- It has `@uses work`. It doesn't use the network.

## Templates

Five short, commented scripts to start your own from. Each shows one way of working:

- **template-command**: a command you type. It reads its words and logs lines that do something
  when tapped.
- **template-timer**: runs on a timer, remembers what it saw and tells you when it changes (here,
  a GitHub project's latest release; its `repo` option picks the project).
- **template-event**: runs when something happens on the phone (here, plugging the charger in and
  out).
- **template-api-key**: uses an API key that you set on the phone, never in the code
  (`script secret template-api-key GITHUB_TOKEN`).
- **template-js**: the same in JavaScript: a command that fetches JSON and logs a line you can tap.

Install one, then copy it under a new name to make it yours, so library updates don't replace your
changes: `script mv template-command mycommand`.
