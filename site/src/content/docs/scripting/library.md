---
title: Script library
description: The 39 ready-made Lua and JavaScript scripts bundled with Kolahal.
---

Kolahal includes 39 ready-made scripts, 12 of them templates to start your own from. You can install one with a single tap in the script manager
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
    updated 3 scripts". **Show** lists them, and their cards say "Updated in 0.5.1" until you close
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
  pill, **Reset to library** in the card's details, or **Reset to library version** in the sheet or at
  the bottom of Compare. The app asks first, and offers **Compare first** and **Export your copy first**.
- A reset or an update changes only the code. The timers, options, access and pinning stay. The
  message after it ("lobsters.lua is back to the library version", "hn.lua is updated") has
  **Undo** for 10 seconds: it puts your copy back.
- **Add script → From the library** lists your copies that differ first, under "Your copy differs",
  with Compare and Update or Reset.

Some scripts keep a status line (weather, countdown, cricket and others). To show it at the top of home,
pin the script: run `script pin weather`, or long-press the line.

Sixteen scripts have a [widget](#widgets) for the widgets page (weather, hn, countdown and others).

None of the network scripts need an API key.

| Script | Lang | Command | Suggested timer | Notifies |
| --- | --- | --- | --- | --- |
| [hn](#hn) | Lua | | every 1h | yes |
| [lobsters](#lobsters) | Lua | | every 1h | yes |
| [releases](#releases) | Lua | yes | every 3h | yes |
| [reddit](#reddit) | JS | yes | | |
| [headlines](#headlines) | JS | | every 1h | |
| [weather](#weather) | Lua | yes | every 1h | |
| [forecast](#forecast) | JS | yes | daily 07:00 | |
| [aqi](#aqi) | JS | yes | daily 08:00 | |
| [sun](#sun) | JS | yes | daily 05:00 | |
| [crypto](#crypto) | JS | yes | every 30m | |
| [exchange](#exchange) | JS | yes | every 6h | |
| [ip](#ip) | Lua | yes | every 30m | yes |
| [webnumber](#webnumber) | JS | | every 1h | |
| [wikipedia](#wikipedia) | JS | yes | | |
| [wiktionary](#wiktionary) | JS | yes | | |
| [xkcd](#xkcd) | JS | yes | every 6h | yes |
| [quote](#quote) | JS | yes | daily 08:00 | yes |
| [countdown](#countdown) | Lua | yes | daily 00:05 | |
| [cricket](#cricket) | JS | yes | every 15m | |
| [football](#football) | JS | yes | every 15m | |
| [charge-reminder](#charge-reminder) | Lua | | on charger and unplug | yes |
| [unlock-chart](#unlock-chart) | Lua | | on unlock | |
| [habit](#habit) | Lua | yes | daily 00:01 | |
| [world-clocks](#world-clocks) | Lua | | | |
| [quick-toggles](#quick-toggles) | Lua | | | |
| [night-dnd](#night-dnd) | Lua | | daily 23:00 and 07:00 | |
| [night-work](#night-work) | Lua | | daily 23:00 and 07:00 | |
| [template-command](#templates) | Lua | yes | | |
| [template-command-js](#templates) | JS | yes | | |
| [template-timer](#templates) | Lua | | every 6h | yes |
| [template-timer-js](#templates) | JS | | every 6h | yes |
| [template-event](#templates) | Lua | | on charger and unplug | |
| [template-event-js](#templates) | JS | | on charger and unplug | |
| [template-api-key](#templates) | Lua | | every 6h | |
| [template-api-key-js](#templates) | JS | | every 6h | |
| [template-fetch](#templates) | Lua | yes | | |
| [template-js](#templates) | JS | yes | | |
| [template-logs](#templates) | Lua | yes | | |
| [template-logs-js](#templates) | JS | yes | | |

## Widgets

Sixteen scripts have a [widget](/scripting/overview/#widgets): a card on the
[widgets page](/guides/widgets/) that the script fills. To use one:

1. Install the script.
2. Tap **+ Add** on the widgets page, or run `widget add`.
3. Pick the script in the **Kolahal** group.

| Script | Its widget shows | A tap |
| --- | --- | --- |
| [weather](#weather) | the temperature, large, with the sky, humidity and wind | runs `weather`, and home turns to the log |
| [forecast](#forecast) | three days, each with its sky, its low and its high | runs `forecast`, and home turns to the log |
| [aqi](#aqi) | the air quality figure in a ring, in its band's colour | runs `aqi`, and home turns to the log |
| [crypto](#crypto) | its coins, each with its price and the day's change | runs `crypto`, and home turns to the log |
| [exchange](#exchange) | one unit of your currency in each of the others you chose | runs `exchange`, and home turns to the log |
| [hn](#hn) | the first 8 stories of the front page, with their points | on a row: opens the story |
| [headlines](#headlines) | the newest items of a feed you choose | on a row: opens the item |
| [releases](#releases) | each watched repository, with its newest release and how old it is | on a row: opens the release |
| [quote](#quote) | the last quote, and who said it | fetches a new quote |
| [xkcd](#xkcd) | the latest comic, with its number and title under it | opens the comic on xkcd.com |
| [countdown](#countdown) | the days to your nearest saved date | runs `countdown`, and home turns to the log |
| [unlock-chart](#unlock-chart) | today's unlocks, over a bar for each of the last 7 days | logs the 7 days, and home turns to the log |
| [habit](#habit) | your streak in days, over a bar for each of the last 7 days | marks today done |
| [world-clocks](#world-clocks) | the time in up to 4 places you list | refreshes the card |
| [quick-toggles](#quick-toggles) | switches for Do Not Disturb and the work profile | on a switch: turns it on or off |
| [webnumber](#webnumber) | one number from a JSON address you choose | opens the address |

- A script fills its widget on its own timer. The widgets page also runs it when you place the
  widget, and when it shows the widget with old content.
- weather, forecast, aqi, crypto and exchange fill the widget only in runs with no arguments. So
  `weather Paris` at the prompt doesn't change the card.
- Old content stays on the card, with its age under it ("2 h ago"). After a run for the widget
  failed, the card says "Failed 10:42" and keeps its content.
- A long-press on a card opens **Arrange widgets**. There you resize, move or remove it.

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

Its [widget](#widgets) has a row for each watched repository, up to 12: the repository, its newest
release's tag, and how old that release is ("3 days ago").

- A tap on a row opens the release.
- While the widget is on the widgets page, each check reads every feed once more, to fill it.
- A release on the card doesn't count as seen. The next check still logs it and notifies you.
- A repository that can't be read says so in its row, and the other rows still show.

## reddit

**Reddit top posts**: today's top 5 posts of a subreddit, with scores and comment counts. If Reddit
blocks anonymous JSON requests, it uses the subreddit's RSS feed instead. A tap on a post opens its
discussion; a post that links to another site ends with a link to it.

- `reddit` shows the subreddit in its `subreddit` option (r/android by default).
- `reddit linux` shows r/linux.
- The `posts` option shows up to 25 posts.

## headlines

**Feed headlines**: a [widget](#widgets) that lists the newest items of one feed (RSS or Atom),
under the feed's own title. A tap on a row opens that item.

```sh
script set headlines url https://example.com/feed.xml
```

- Its `url` option is the feed's address. It starts as the front page of Hacker News.
- It reads the feed every hour, and only while its widget is on the widgets page.
- After you change the feed, the card shows it at the script's next run. To run it at once, tap the
  card's heading.
- It logs no headlines. To read feeds in the log, with unread counts, use
  [`feed`](/guides/feeds/).

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

Its [widget](#widgets) lists what one unit of its `from` option is in each of its `to` options. Its
timer fills it every 6 hours; the rates change once a day.

## ip

**Public IP** from api.ipify.org. Long-press the line to copy it. On a timer, it notifies you when
the IP changes and sets the IP as its status line. This helps you notice when a VPN drops.

## webnumber

**Web number**: a [widget](#widgets) that shows one number from any JSON address: a price, a count,
a sensor at home. The number is large, with its label under it. A tap opens the address.

```sh
script set webnumber url https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd
script set webnumber path bitcoin.usd
script set webnumber label BTC
```

- `url` is the address of a JSON document.
- `path` says where the number is in the document: the keys from the top, with dots between them
  (`bitcoin.usd`, `data.price`).
  - A number picks an item of a list, counted from 0: `items.0.count`.
  - An empty path means that the document itself is the number.
- `label` is the text under the number. Without it, the last word of the path is used.
- Until `url` is set, the card says which option to set. Tap the card after you set one.
- It asks the address every hour, and only while its widget is on the widgets page. The answer is
  kept for 10 minutes, so a change of `path` or `label` asks the site nothing new.
- A number has at most 2 decimals, and commas from 10,000 up: `67,123.46`. Text that isn't a number
  shows as it is.
- If the path leads to a list, to an object or to nothing, the run fails and says what is there.

## wikipedia

**Wikipedia summary**, with the article's picture and a link to the full article:
`wikipedia Alan Turing`.

- It tries the exact title first, then the best search match.
- With no topic, it shows a random article.
- Its `lang` option picks another language edition: `script set wikipedia lang de`.

## wiktionary

**A word's meanings** from Wiktionary, in any language: `wiktionary serendipity`.

- One line per meaning, its part of speech first, with an example under it when Wiktionary has one.
  Tap a line to open the entry.
- Its `lang` option picks the language, as Wiktionary names it: `script set wiktionary lang German`.
  `all` shows the first three languages that have the word.
- A word with no entry in your language shows the languages that have one: `wiktionary Hund`.

## xkcd

**xkcd**: the comic itself in the log, with its alt text below. Use `xkcd`, `xkcd 327` or
`xkcd random`. On a timer, it notifies you only when a new comic is out.

Its widget is **Comic of the day**: the latest comic, whole, with its number and title under it.

- Kolahal fetches the picture itself, over https only, and only while the widgets page is in view.
- A screen reader says the comic's own words (its alt text). The card shows them too where it
  has no picture to draw, such as on the **Arrange widgets** screen before the picture was loaded.
- `xkcd 327` and `xkcd random` at the prompt don't change the card.

## quote

**Quote of the day** from ZenQuotes. Long-press to copy it. At its daily time, it also sends the
quote as a notification.

## countdown

**Countdowns**: the days until (or since) dates, and to the next birthday or anniversary. A
countdown shows its days in big numbers, with the date and its weekday beside them. The list starts
with the next one that way, then a table: each countdown's number (for `rm`), its date and when it
is. Its status line is the nearest date. It doesn't use the network.

A countdown with `yearly` comes every year: it counts to the next one, and says which one it is
(`Wedding anniversary · 25th`). A date without a year (`06-15`) comes every year too.

```sh
countdown 2026-12-25
countdown add 2026-12-25 Christmas
countdown add yearly 2001-06-15 Wedding anniversary
countdown add 06-15 Mum's birthday
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

## unlock-chart

**Unlocks**: a [widget](#widgets) with today's unlock count, large, over a bar for each of the last
7 days. The oldest day comes first. It doesn't use the network, and it needs no access.

- It runs when you unlock the phone, at most once in 30 seconds. So the count is right when you
  reach the widgets page.
- A tap on the card logs the 7 days, each with its bar, and home turns to the log.
  `script run unlock-chart` logs them too.
- The counts are the ones the built-in [`unlocks`](/guides/apps/#screen-time-and-unlocks) command
  shows. They are most accurate with usage access (`usage-access`).
- While its widget isn't on the widgets page, a run for an unlock does nothing. To stop these runs
  too, switch its `unlock` event off in the script's sheet.
- It isn't named `unlocks`, because a script can't have a built-in command's name.

## habit

**Habit streak**: a [widget](#widgets) for one thing you do every day. It shows your streak large:
the days in a row that you did it. Under it is a bar for each of the last 7 days, the oldest first.
It doesn't use the network, and it needs no access.

```sh
habit                        # the streak and the last 7 days
habit done                   # mark today done
habit undo                   # take today's mark away
```

- A tap on the card marks today done. The card then turns to the theme's good colour.
- A tap by mistake: type `habit undo`.
- While today is still open, the streak counts up to yesterday. A missed day starts it again at 0.
- Its `name` option is the card's title: what you count (`script set habit name Read`).
- It runs at 00:01, so the card starts the new day with an open bar.
- It counts one habit. For a second one, rename your copy (`script mv habit read`) and install
  habit again. Each copy has its own days and its own widget.

## world-clocks

**World clocks**: a [widget](#widgets) with the time in up to 4 places. Kolahal moves the clocks
itself, so the script uses no battery while you look at them. It doesn't use the network, and it
needs no access.

```sh
script set world-clocks zones "Asia/Kolkata Europe/London America/New_York"
script set world-clocks zones "Asia/Kolkata=Pune Asia/Dubai=Head_office UTC"
```

- Its `zones` option lists the places: time zones by name, such as `Asia/Kolkata`,
  `Europe/London` or `UTC`. The first 4 show.
- A place's name on the card is the last part of its zone: "New York" for `America/New_York`.
- To pick the name yourself, write it after the zone: `Asia/Kolkata=Pune`. Write a space in a name
  as `_`.
- A place whose day isn't your day says so: "tomorrow" or "yesterday".
- The times are in 12-hour or 24-hour form, as the header's clock is (`clock.24h`).
- A small card shows the first place only, and a wide one as many as fit. So list the important
  place first.
- A zone that Kolahal doesn't know makes the run fail. The card then says "Failed", and the log
  names the zone.
- It has no timer. It runs when you place the widget and when you tap the card. So after you
  change the option, tap the card: it then shows the new places.

## quick-toggles

**Quick toggles**: a [widget](#widgets) with a switch for Do Not Disturb, and one for the work
profile when the phone has one. It doesn't use the network.

- A tap on a switch runs the script. It changes the thing, waits until Android has done it, and
  shows the new state. Until then the switch shows that it is working.
- Kolahal needs Do Not Disturb access for the first switch. Run `dnd access` once.
- Since Android 15, the switch turns Kolahal's own Do Not Disturb mode on and off. If you turned
  Do Not Disturb on yourself, it stays on, and the switch stays on too.
- Android lets only the default launcher pause and resume the work profile. If your work profile
  needs its PIN to resume, Android asks you for it.
- When a change doesn't work, the switch stays as it was, the card says "Failed", and the log
  says why.
- Both can change outside Kolahal. So the card counts as old after a minute: the widgets page then
  reads both again when you open it.
- It has `@uses dnd, work`. `script run quick-toggles` logs both states.
- Wi-Fi and Bluetooth have no switch here: Android lets an app only open their panels.

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

Short, commented scripts to start your own from. Each shows one way of working, and each comes in
Lua and in JavaScript: the JavaScript one has `-js` at the end of its name.

- **template-command** / **template-command-js**: a command you type. It reads its words and logs
  lines that do something when tapped.
- **template-timer** / **template-timer-js**: runs on a timer, remembers what it saw and tells you
  when it changes (here, a GitHub project's latest release; its `repo` option picks the project).
- **template-event** / **template-event-js**: runs when something happens on the phone (here,
  plugging the charger in and out).
- **template-api-key** / **template-api-key-js**: uses an API key that you set on the phone, never
  in the code (`script secret template-api-key GITHUB_TOKEN`).
- **template-fetch** / **template-js**: a command that fetches JSON from the web and logs a line you
  can tap (here, a Wikipedia article's summary).
- **template-logs** / **template-logs-js**: a reference for logging. Run it, and each of its lines
  shows one kind of line next to the code that makes it: plain lines and bodies, links and Copy
  text, styles and colours, tags, Markdown, lists, tables, bars, big numbers, a picture, a list with
  a space between its items, buttons, `line.set`, and the status line.

Install one, then copy it under a new name to make it yours, so library updates don't replace your
changes: `script mv template-command mycommand`.
