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
  the app's data cleared). Coordinates are for the emulator's 1080 × 2400 screen. `THEME=nord
  tools/site-captures.sh all` takes them all; run it whole, look at every picture, fix what drifts.

Each hand-made capture is kept at full size in `docs/release/graphics/raw/` under the name in the
last column, and put on the site 540 pixels wide, stripped and quantized (`magick … -resize 540x
-strip`, `pngquant --quality 85-98`); a widget card for the home page is cut at full size from the
widgets page. *Dark* is the phone's dark mode on (`cmd uimode night yes`), *light* off (Rosé Pine
Dawn). A `<name>-light.png` beside a picture in `shots/` is shown in the site's light theme instead.

Taken on 24 November 2026 with the debug build of 0.5.17 (the full build) on the emulator
`sdk_gphone64_x86_64`, Android 16. The TTY pictures are from that build with the fix to the TTY
header's signs and the `clock` screen's centering.

## The hero's phone (home page)

| File | What it shows | Function | Raw |
| --- | --- | --- | --- |
| `shots/hero/pond-dark.png` | home over `pond`: weather, a conversion, a reminder | (Play captures, `docs/release/google-play.org`) | `home-pond-dark` |
| `shots/hero/mountains-light.png` | the same over `mountains`, light | (Play captures) | `home-mountains-light` |
| `shots/hero/dunes-light.png` | answers over `dunes`, light | (Play captures) | `answers-dunes-light` |
| `shots/hero/pond-light.png` | home over `pond`, light | `hero_pond_light` | `home-pond-light` |
| `shots/hero/mountains-dark.png` | home over `mountains` | `hero_mountains_dark` | `home-mountains-dark` |
| `shots/hero/dunes-dark.png` | home over `dunes` | `hero_dunes_dark` | `home-dunes-dark` |

## The home page's cards ("Scripts and widgets")

| File | What it shows | Function | Raw |
| --- | --- | --- | --- |
| `shots/home/water.png` | the API reference's Water card, 5 / 8, **Bold** | `home_cards` | `widgets-page-water` |
| `shots/home/world-clocks.png` | World clocks, **Bold** | `home_cards` | `widgets-page-water` |
| `shots/home/unlock-chart.png` | Unlocks, **Bold** | `home_cards` | `widgets-page-water` |
| `shots/home/habit.png`, `forecast.png` | Habit streak **Shape**, 3-day forecast **Split** | (Play captures) | `widgets-page-looks` |
| `shots/home/selector.png`, `quick-actions.png` | the selector at G; quick actions | (Play captures) | `selector-thumb-g`, `quick-actions-full-build` |

## The tour (`/tour/`) and the gallery

| File | What it shows | Function | Raw |
| --- | --- | --- | --- |
| `shots/typing/home.png` | home with the keyboard down, no shader | `typing_home` | `typing-home` |
| `shots/typing/ma.png` | `ma` typed over `pond` | (Play captures) | `typing-suggestions` |
| `shots/suggestions/empty.png` | an empty prompt: favorites nearest it, then the most-used apps | `suggestions` | `suggestions-empty` |
| `shots/suggestions/flash.png` | `flash` finds `torch` | `suggestions` | `suggestions-flash` |
| `shots/suggestions/theme.png` | after `theme `, the theme names | `suggestions` | `suggestions-theme` |
| `shots/selector/slide.png`, `slide-p.png` | the selector at G and at P | (Play captures) | `selector-thumb-g`, `-p` |
| `shots/selector/pick.png` | Google's row highlighted and filling under a held thumb | `selector` | `selector-pick-filling` |
| `shots/log/answers.png`, `answers-light.png`, `actions.png`, `feed.png`, `scramble.png` | the log | (Play captures) | |
| `shots/log/fresh.png` | after `fresh`: "▴ 12 earlier lines · tap to show" | `fresh` | `log-fresh` |
| `shots/scripts/weather.png`, `weather-light.png`, `editor.png` | `weather`; the editor | (Play captures) | |
| `shots/scripts/forecast.png` | `forecast lisbon` | `forecast` | `scripts-forecast` |
| `shots/access/library.png`, `switches.png`, `cards.png` | the library, a script's sheet, the script manager | (Play captures) | |
| `shots/schedule/times.png` | night-dnd's sheet: Runs every, Daily at 07:00, 23:00 | `schedule_times` | `schedule-times` |
| `shots/schedule/charger.png` | charge-reminder on unplugging: 62%, +12%, a bar | `schedule_charger` | `schedule-charger` |
| `shots/pinning/pin.png` | weather's action sheet with *Pin weather to the top* | `pinning` | `pinning-pin` |
| `shots/pinning/pinned.png` | weather's line pinned under the header | `pinning` | `pinning-pinned` |
| `shots/pinning/bar.png` | "Focus: 2 min left" with its bar (the demo script `focus`) | `pinning` | `pinning-bar` |
| `shots/widgets/page.png`, `page-light.png` | the widgets page, Bold, Split and Shape cards | `widgets_pages` | `widgets-page`, `widgets-page-light` |
| `shots/widgets/add.png` | the picker of widgets to add | `widgets_add` | `widgets-add-picker` |
| `shots/widget-options/arrange.png` | Arrange widgets, Habit streak chosen | `widgets_pages` | `arrange-widgets-look` |
| `shots/widget-options/arrange-all.png` | Arrange widgets, nothing chosen | `widgets_pages` | `arrange-widgets` |
| `shots/widget-options/looks.png` | World clocks in Bold, Split and Shape, set in a grid | `widget_looks` | `widget-looks` |
| `shots/widget-options/options.png` | two weather cards side by side, Tokyo and Paris, Split | `widget_options` | `widget-options-cities` |
| `shots/shaders/*`, `shots/shader-parameters/*`, `shots/themes/*` | shaders, their details, Settings | (Play captures) | |
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
| `app-grid.png` | `apps` with personal and work apps (W) | `profiles` | `app-grid` |
| `terminal-feeds.png` | countdown pinned, the feed list and a conversion | `terminal_feeds` | `terminal-feeds` |

## The guide's widget and log pictures

Drawn by `tools/site-pictures.sh`: the device tests draw them from the guide's examples and the
ready-made cards, each card in its look (`WidgetGalleryTest.siteLook`: by kind, Bold for numbers,
clocks, countdowns and charts, Split for lists, text, agendas, switches and media, Shape for progress
and buttons, with a few cards set by name), and the script copies them to the same names.

| Files | What they show | Test |
| --- | --- | --- |
| `widgets/example-*.png` (13) | the API reference's widget examples | `WidgetGalleryTest#theGuidesExamplesAreDrawnAndSaved` (`docs-*`) |
| `widgets/*.png` (23 others) | the library's cards and Kolahal's own | the same test (`real-*`) |
| `log/*.png` (10) | the scripting guide's log examples | `LogGalleryTest#theGuidesLogExamplesAreDrawnAndSaved` |
| `fmt/*.png` (11) | the fmt samples, one line each | `LogGalleryTest#theFmtSamplesAreDrawnAndSaved` |
