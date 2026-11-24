# Captures of the app on the site

Every picture of the app the website shows, and what makes it. All are in the app's default theme,
`rose-pine`, dark unless a row says light, and every widget is in one of the expressive looks
**Bold**, **Split** or **Shape** (never Plain, never Tonal), chosen per card so that cards side by
side differ. A picture card is the one exception: a picture covers its card, so its only looks are
Plain and Tonal, and it is Tonal (`widgets/comic-2x2.png`, `widgets/example-image-4x2.png`).

A page takes each picture by its file name (`src/shots.ts`), so a new file at the same path is all
a page needs. Paths below are under `site/src/assets/`.

**Made again in another theme** by two scripts, which keep what was done by hand:

- `tools/site-pictures.sh [theme] [light]`: the guide's widget and log pictures and the widgets page
  of "Parts of the app", drawn by the device tests `WidgetGalleryTest` and `LogGalleryTest`, which
  take the theme (and a look for every card, `LOOK=`) as instrumentation arguments. It builds,
  installs, runs them on the emulator and copies each picture over the old one.
- `tools/site-captures.sh`: everything taken on the emulator, one shell function per picture with
  the exact taps, typing, held touches and waits, after `setup` (the state: `THEME`, `DARK`, the
  time zone, todos, scripts, widgets in their looks) and before `restore` (the emulator as it was,
  the app's data cleared). The zone is one of whole hours (`ZONE`, Asia/Dhaka): the world clocks
  show each city's difference from it, and a half-hour zone makes them "−3:30"; the script stops
  on one. Coordinates are for the emulator's 1080 × 2400 screen. `THEME=nord
  tools/site-captures.sh all` takes them all; run it whole, look at every picture, fix what drifts.

Each hand-made capture is kept at full size in `docs/release/graphics/raw/` under the name in the
last column, and put on the site 540 pixels wide, stripped and quantized (`magick … -resize 540x
-strip`, `pngquant --quality 85-98`); a widget card for the home page is cut at full size from the
widgets page. Every capture is taken with the status bar hidden and `config set header.below-bar
off`: the header's line at the very top of the screen, with no empty band above it where the status
bar was. The built-in command `maps` is off (`config set commands.off maps`), so that typing `ma`
suggests the app Maps and no command of that name. *Dark* is the phone's dark mode on (`cmd uimode night yes`), *light* off (Rosé Pine
Dawn). A `<name>-light.png` beside a picture in `shots/` is shown in the site's light theme instead.

Taken on 24 November 2026 with the debug build of 0.5.17 (the full build) on the emulator
`sdk_gphone64_x86_64`, Android 16. The TTY pictures are from that build with the fix to the TTY
header's signs and the `clock` screen's centering. Taken again that evening with
`header.below-bar` off and `maps` off: the scenes at dusk in `ZONE=Asia/Riyadh` (UTC+3), the widget
pictures in `Asia/Dhaka` (the world clocks' −3:00 and −6:00). The widgets' screens (the picker,
the widgets page, Arrange widgets, a card's own page, the pages, the home page's cards) and Settings
were taken again on 25 November 2026, after the widgets' settings pages, pages and daily limits
came in, with `ZONE=America/Argentina/Buenos_Aires` and the clocks in `Asia/Dhaka` (Lisbon then on
the day before: "−1"). In Arrange widgets the script finds a button by what it says (`tap_ui`,
through uiautomator): the Look row offers only the looks that change the card, so its buttons move.

## The hero's phone (home page)

| File | What it shows | Function | Raw |
| --- | --- | --- | --- |
| `shots/hero/pond-dark.png` | home over `pond`: weather, a conversion, a reminder | `play_home` | `home-pond-dark` |
| `shots/hero/mountains-light.png` | the same over `mountains`, light | `play_home` | `home-mountains-light` |
| `shots/hero/dunes-light.png` | answers over `dunes`, light | `play_answers` | `answers-dunes-light` |
| `shots/hero/pond-light.png` | home over `pond`, light | `hero_pond_light` | `home-pond-light` |
| `shots/hero/mountains-dark.png` | home over `mountains` | `hero_mountains_dark` | `home-mountains-dark` |
| `shots/hero/dunes-dark.png` | home over `dunes` | `hero_dunes_dark` | `home-dunes-dark` |

## The home page's cards ("Scripts and widgets")

| File | What it shows | Function | Raw |
| --- | --- | --- | --- |
| `shots/home/water.png` | the API reference's Water card, 5 / 8, **Bold** | `home_cards` | `widgets-page-clocks-split` |
| `shots/home/habit.png`, `forecast.png` | Habit streak's week as a flower, **Shape**; 3-day forecast for Lisbon **Split** | `home_cards` | `widgets-page-clocks-split`, `widgets-page-cards-top` |
| `shots/home/world-clocks.png` | World clocks in **Split**, the card that `widget_looks` placed (setup's is Bold) | `home_cards` | `widgets-page-clocks-split` |
| `shots/home/unlock-chart.png` | Unlocks, **Bold** | `home_cards` | `widgets-page-clocks-split` |
| `shots/home/selector.png`, `quick-actions.png` | the selector at G; quick actions | `play_selector`, `play_log` | `selector-thumb-g`, `quick-actions-full-build` |

## The tour (`/tour/`) and the gallery

| File | What it shows | Function | Raw |
| --- | --- | --- | --- |
| `shots/typing/home.png` | home with the keyboard down, no shader | `typing_home` | `typing-home` |
| `shots/typing/ma.png` | `ma` typed over `pond` | `play_typing` | `typing-suggestions` |
| `shots/suggestions/empty.png` | an empty prompt: favorites nearest it, then the most-used apps | `suggestions` | `suggestions-empty` |
| `shots/suggestions/flash.png` | `flash` finds `torch` | `suggestions` | `suggestions-flash` |
| `shots/suggestions/theme.png` | after `theme `, the theme names | `suggestions` | `suggestions-theme` |
| `shots/selector/slide.png`, `slide-p.png` | the selector at G and at P | `play_selector` | `selector-thumb-g`, `-p` |
| `shots/selector/pick.png` | Google's row highlighted and filling under a held thumb | `selector` | `selector-pick-filling` |
| `shots/log/answers.png`, `answers-light.png` | a sum, a conversion, the todos, a reminder, two cities' times | `play_answers` | `answers-dark`, `answers-dunes-light` |
| `shots/log/feed.png`, `actions.png` | Maya's notification and the feeds; a line's actions | `play_log` | `log-notification-feed`, `action-sheet-line` |
| `shots/log/scramble.png` | `scramble on` over the log | `play_scramble` | `scramble` |
| `shots/log/fresh.png` | after `fresh`: "▴ 12 earlier lines · tap to show" | `fresh` | `log-fresh` |
| `shots/scripts/weather.png`, `weather-light.png` | `weather` over `pond`, and over `mountains` light | `play_home` | `home-pond-dark`, `home-mountains-light` |
| `shots/scripts/editor.png` | the editor | (Play captures) | `editor-script` |
| `shots/scripts/forecast.png` | `forecast lisbon` | `forecast` | `scripts-forecast` |
| `shots/access/switches.png` | weather.lua's sheet: its switches and its city | `play_sheets` | `script-sheet-switches` |
| `shots/access/library.png`, `cards.png` | the library, the script manager | (Play captures) | `library`, `scripts-list` |
| `shots/schedule/times.png` | night-dnd's sheet: Runs every, Daily at 07:00, 23:00 | `schedule_times` | `schedule-times` |
| `shots/schedule/charger.png` | charge-reminder on unplugging: 62%, +12%, a bar | `schedule_charger` | `schedule-charger` |
| `shots/pinning/pin.png` | weather's action sheet with *Pin weather to the top* | `pinning` | `pinning-pin` |
| `shots/pinning/pinned.png` | weather's line pinned under the header | `pinning` | `pinning-pinned` |
| `shots/pinning/bar.png` | "Focus: 2 min left" with its bar (the demo script `focus`) | `pinning` | `pinning-bar` |
| `shots/widgets/page.png`, `page-light.png` | the widgets page, Bold, Split and Shape cards | `widgets_pages` | `widgets-page`, `widgets-page-light` |
| `shots/widgets/add.png` | the picker of widgets to add | `widgets_add` | `widgets-add-picker` |
| `shots/widget-options/arrange.png` | Arrange widgets, Habit streak chosen, the title bar folded | `arrange_widgets` | `arrange-widgets-look` |
| `shots/widget-options/arrange-all.png` | Arrange widgets, nothing chosen, opened fresh | `arrange_widgets` | `arrange-widgets` |
| `shots/widget-options/looks.png` | World clocks in Bold, Split and Shape, set in a grid | `widget_looks` | `widget-looks` |
| `shots/widget-options/options.png` | two weather cards side by side, Tokyo and Paris, Split | `widget_options` | `widget-options-cities` |
| `shots/widget-options/settings-city.png` | the second weather card's own page as it opens when placed, its city Paris | `widget_options` | `widget-settings-city` |
| `shots/widget-options/settings.png`, `settings-shape.png` | the Weather card's own page in Shape, Link and the 4-leaf clover chosen: the top with the card live and Color; Shape and What shows | `widget_settings` | `widget-settings`, `widget-settings-shape` |
| `shots/widgets/pages-1.png`, `pages-2.png` | three widgets pages: page 1 with its name and dots, and Travel | `widget_pages` | `widgets-pages-1`, `widgets-pages-2` |
| `shots/widget-options/arrange-pages.png` | Arrange widgets with three tabs (Page 1, Travel, Health), Battery chosen with its Page row | `widget_pages` | `arrange-pages` |
| `shots/usage/limits.png` | a daily limit of 10 unlocks: the header's unlocks and `unlocks 7` in the warning color | `limits` | `limits-unlocks` |
| `shots/shaders/candle.png`, `lanterns.png`, `hourglass.png` | three scenes behind home | `play_scenes` | `scene-*` |
| `shots/shader-parameters/follow.png`, `sliders.png` | Candle's and Mountains' details | `play_sheets` | `shader-details-follows`, `-slider` |
| `shots/shaders/list.png`, `shots/themes/*` | the list of shaders, Settings | (Play captures) | `shaders-list`, `settings`, `theme-picker` |
| `shots/wallpaper/preview.png` | Android's wallpaper preview with `pond` | `wallpaper` | `wallpaper-preview` |
| `shots/wallpaper/lock.png` | the lock screen with `pond` behind the clock | `wallpaper` | `wallpaper-lock` |
| `shots/wallpaper/log.png` | the log with the pond through it (`log.wallpaper 60`) | `wallpaper` | `wallpaper-log` |
| `shots/tty/home.png`, `clock.png`, `matrix.png` | the TTY style: home, `clock`, `matrix` | `tty` | `tty-home`, `tty-clock`, `tty-matrix` |
| `shots/profiles/resume.png` | a paused work app tapped: *Resume work apps?* (gallery only) | `profiles` | `profiles-resume` |

## The guide's screenshots

The numbered marks of *Parts of the app* (`getting-started/parts.mdx`) are placed by percentage on
the `parts/` pictures: after taking one again, check each mark still sits on what it names.

| File | What it shows | Function | Raw |
| --- | --- | --- | --- |
| `parts/home.png` | header, countdown pinned, four groups, the prompt | `parts_home` | `parts-home` |
| `parts/typing.png` | `ma` typed, the key bar on, header to key bar | `parts_typing` | `parts-typing` |
| `parts/selector-slide.png` | sliding along the edge at G | `selector` | `selector-slide` |
| `parts/selector-pick.png` | Google's row highlighted | `selector` | `selector-pick` |
| `parts/selector-open.png` | the list left open | `selector` | `selector-open` |
| `parts/action-sheet.png` | the todo line's action sheet | `parts_action_sheet` | `action-sheet-todo` |
| `parts/quick-actions.png` | quick actions | `parts_quick_actions` | `quick-actions` |
| `parts/widgets-page.png` | the widgets page | `tools/site-pictures.sh` (`WidgetGalleryTest#thePageIsDrawnAndSaved`) | |
| `parts/settings.png` | Settings | `parts_settings` | `parts-settings` |
| `parts/scripts-tab.png` | the Scripts tab: Countdowns' details open, Focus off | `parts_scripts` | `parts-scripts` |
| `parts/editor.png` | the editor with the symbol bar | `parts_editor` | `parts-editor` |
| `setup.png` | the setup checklist | `setup_checklist` | `setup` |
| `app-grid.png` | `apps` over a short log: personal and work apps in three columns, a count in each heading, "+8 more" | `profiles` | `app-grid` |
| `terminal-feeds.png` | countdown pinned, the feed list and a conversion | `terminal_feeds` | `terminal-feeds` |

## The guide's widget and log pictures

Drawn by `tools/site-pictures.sh`: the device tests draw them from the guide's examples and the
ready-made cards, each card in its look (`WidgetGalleryTest.siteLook`: by kind, Bold for numbers,
clocks, countdowns, charts and scores, Split for lists, text, agendas, switches and media, Shape for
progress, buttons and the sky, with a few cards set by name), and the script copies them to the same
names. The kinds and styles that the guide's code has no example for (a gauge, rings, stages, a
number's line and scene, the dots and page countdowns, the looks Poster, Shape and Now) are the
test's own cards in one look: a site file named `widgets/<look>-<card>-<size>.png`
(`bold-kind-gauge-2x2.png`) makes the script draw that card in that look
(`WidgetGalleryTest#everyLookIsDrawnAndSaved`, `looks` and `names`) and copy it over. Drawn with
`ZONE=Pacific/Pitcairn` on 25 November 2026, in the afternoon there, so the sky and the clocks read
as by day; the places on the cards keep their own time.

| Files | What they show | Test |
| --- | --- | --- |
| `widgets/example-*.png` (15) | the API reference's widget examples | `WidgetGalleryTest#theGuidesExamplesAreDrawnAndSaved` (`docs-*`) |
| `widgets/*.png` (27 others) | the library's cards and Kolahal's own, with Next and Stack | the same test (`real-*`) |
| `widgets/<look>-*.png` (14) | the test's own cards in one look: new kinds, styles and looks | `WidgetGalleryTest#everyLookIsDrawnAndSaved` |
| `log/*.png` (10) | the scripting guide's log examples | `LogGalleryTest#theGuidesLogExamplesAreDrawnAndSaved` |
| `fmt/*.png` (11) | the fmt samples, one line each | `LogGalleryTest#theFmtSamplesAreDrawnAndSaved` |

## For the blog post on 0.5.17

Kept in `docs/release/graphics/raw/` only: the post on this release uses them, and no page shows
them. The emulator takes in the host's clipboard, and Gboard would show it as a chip in place of the
number row however long you wait: `setup` turns Gboard's "Show recently copied text and images in
suggestions bar" off (`gboard_chip`), `restore` on again.

| Raw | What it shows | Function |
| --- | --- | --- |
| `tty-top` | the TTY style's `top` screen: the time, bars for the battery, memory, Kolahal and today's screen time, the scripts' table | `tty_top` |
| `scramble-before`, `scramble-after` | the same home (weather, a conversion, `todo`), then after `scramble`: the header says *scrambled* | `scramble_pair` |
| `typing-tty-parts`, `typing-gui-parts` | `ca` typed over the same log with the suggestions and the key bar: the TTY style with every part on the grid (`tty.suggestions`, `tty.selector`, `tty.keybar`, `tty.quick-actions`, `tty.menu` set to `tty`), then the default style | `typing_styles` |
| `reference-table-tty` | the editor's reference, Log lines → `fmt.table`, its Home style set to TTY | `reference_tty` |
| `app-grid-more` | `apps` as in `app-grid.png`, after a tap on the personal group's "+8 more": the whole group and "Show less" | `profiles` |
