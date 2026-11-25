#!/usr/bin/env bash
# The website's captures of the app, taken on an emulator by hand and written down here as they were
# taken (site/CAPTURES.md names each picture's function). It is a record that runs: run it whole
# with another theme, look at every picture, and fix what drifts (a row that moved, a wait too short).
#
#   tools/site-captures.sh setup            # the state the pictures need (clears the app's data first)
#   tools/site-captures.sh <picture>...     # one or more pictures, by function name (list: `list`)
#   tools/site-captures.sh all              # setup, every picture, restore
#   tools/site-captures.sh restore          # the emulator as it was, and the app's data cleared
#   THEME=nord DARK=no tools/site-captures.sh all
#
# - Emulator only, never a phone: every command goes to $SERIAL (emulator-5554). The app is the
#   debug build (the full build), already installed and set up as the home app by `setup`.
# - Coordinates are for this emulator's screen: 1080 × 2400 pixels at 420 dpi, gesture navigation,
#   Gboard with its number row, animations off. Another screen needs them measured again.
# - Each picture is kept at full size in docs/release/graphics/raw/<name>.png and put on the site
#   540 pixels wide, stripped and quantized (`site_shot`); widget cards are cut at full size.
# - Invented, neutral content only: big cities, the invented name Maya, public feeds. A weather
#   script's city is set before its first run, or it reports the machine's own location.
# - Needs adb, ImageMagick (magick) and pngquant on the PATH (nix shell nixpkgs#imagemagick
#   nixpkgs#pngquant).
set -euo pipefail
cd "$(dirname "$0")/.."

SERIAL=${SERIAL:-emulator-5554}
THEME=${THEME:-rose-pine}   # the app's theme
DARK=${DARK:-yes}           # the phone's dark mode: yes (the theme's dark version) or no (its light one)
# A zone of whole hours where it is dusk (about 18:00 to 18:40) when you run it, so the scenes look
# as they do in the pictures, and no clock tells where the machine is: Asia/Dhaka is UTC+6,
# Asia/Bangkok +7, Asia/Tokyo +9. Never a half-hour zone (Asia/Yangon is +6:30): the world
# clocks show each city's difference from it, and Nairobi "−3:30" reads oddly. `zone` refuses one.
ZONE=${ZONE:-Asia/Dhaka}
ZONE_BEFORE=${ZONE_BEFORE:-}   # the zone to put back in `restore`; empty leaves it to the automatic time zone
APP=com.mtwabpin.kolahal.debug
RAW=docs/release/graphics/raw
ASSETS=site/src/assets
TMP=${TMP_CAPTURES:-$(mktemp -d)}
A=(adb -s "$SERIAL")

# ---- helpers ----------------------------------------------------------------------------------

sh_() { "${A[@]}" shell "$@" < /dev/null; }
tap() { sh_ input tap "$1" "$2"; }
key() { sh_ input keyevent "$1"; }
# t "<command>" [wait]: types a command at the prompt (the keyboard up) and presses Enter.
t() {
  local s=${1// /%s}
  s=${s//\'/\\\'}; s=${s//\"/\\\"}; s=${s//(/\\(}; s=${s//)/\\)}; s=${s//&/\\&}
  s=${s//</\\<}; s=${s//>/\\>}; s=${s//;/\\;}; s=${s//\*/\\*}
  sh_ input text "$s"; sleep 0.6; key 66; sleep "${2:-2}"
}
# ty "<text>": types without Enter.
ty() { local s=${1// /%s}; sh_ input text "${s//\*/\\*}"; }
# The keyboard's ˅ closes it (Back doesn't always); a tap on empty log space opens it.
kbdown() { tap 163 2337; sleep 1; }
kbup() { tap 300 2283; sleep 1.5; }   # the prompt (keyboard down); a tap on the log could reuse a line
# Home: the Home key, then a moment for the log to settle.
home() { key KEYCODE_HOME; sleep 2; }
# wait_cleared: the "Screen cleared · Undo" bar stays about 7 seconds.
wait_cleared() { sleep 7; }
# edtype <file>: types a file into the focused editor, line by line (it doesn't auto-indent or
# close brackets, so the file goes in as it is).
edtype() {
  local line s
  while IFS= read -r line; do
    if [ -n "$line" ]; then s=${line// /%s}; sh_ "input text '$s'"; fi
    key 66
  done < "$1"
}
# cap <name>: a full-size capture, kept in raw/.
cap() { "${A[@]}" exec-out screencap -p > "$RAW/$1.png"; echo "$RAW/$1.png"; }
# site_shot <name> <site path>: the capture <name>, 540 pixels wide, on the site.
site_shot() {
  local out=$ASSETS/$2
  mkdir -p "$(dirname "$out")"
  magick "$RAW/$1.png" -resize 540x -strip "$out"
  pngquant --quality 85-98 --force --output "$out" "$out"
}
# site_crop <name> <geometry> <site path>: a part of the capture <name> (WxH+X+Y at full size),
# 540 pixels wide, on the site.
site_crop() {
  local out=$ASSETS/$3
  mkdir -p "$(dirname "$out")"
  magick "$RAW/$1.png" -crop "$2" +repage -resize 540x -strip "$out"
  pngquant --quality 85-98 --force --output "$out" "$out"
}
# site_card <name> <WxH+X+Y> <site path>: one widget card cut at full size from the region given,
# trimmed to the card on the page's ground and given an even margin of 18 pixels.
site_card() {
  local out=$ASSETS/$3 bg
  bg=$(magick "$RAW/$1.png" -format '%[pixel:p{540,2100}]' info:)
  magick "$RAW/$1.png" -crop "$2" +repage -bordercolor "$bg" -border 2 -fuzz 3% -trim +repage \
    -bordercolor "$bg" -border 18 -strip "$out"
  pngquant --quality 85-98 --force --output "$out" "$out"
}

# ---- widgets: Add widget and Arrange widgets --------------------------------------------------

# tap_ui <words> [last]: a tap on the middle of what shows <words>, as its text or its description
# for TalkBack (uiautomator reads the screen): the first such on the screen, or with `last` the
# lowest. For buttons whose place changes with the card: the Look row offers only the looks that
# change the card, so their places differ from card to card.
tap_ui() {
  local what=$1 pick=head b
  [ "${2:-}" = last ] && pick=tail
  sh_ uiautomator dump /sdcard/ui.xml > /dev/null
  b=$(sh_ cat /sdcard/ui.xml | tr '>' '\n' | grep -E "(text|content-desc)=\"$what\"" | $pick -1 \
    | grep -o 'bounds="[^"]*"' | grep -o '[0-9][0-9]*' | tr '\n' ' ' || true)
  set -- $b
  [ $# -eq 4 ] || { echo "Nothing shows \"$what\" on the screen" >&2; return 1; }
  tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# w_search <name>: on Arrange widgets, + and a search for name (the picker keeps the last search:
# ✕ clears it first).
w_search() { tap_ui "Add widget"; sleep 2; tap 975 621; sleep 0.5; tap 540 621; sleep 0.8; ty "$1"; sleep 1.5; kbdown; }
# w_look <look>: the chosen card's look in the toolbar's Look row, then ✓ (Done).
w_look() { sleep 2.5; tap_ui "${1^}" last; sleep 1; tap_ui Done; sleep 1.5; }
# w_add <name> <look>: the first row the search finds, placed, in that look.
w_add() { w_search "$1"; tap 540 953; sleep 1.5; w_look "$2"; }

# gboard_chip <on|off>: Gboard's "Show recently copied text and images in suggestions bar". The
# emulator takes in the host's clipboard, so with it on a clipboard chip takes the number row's
# place however long you wait: setup turns it off, restore on again (Settings → On-screen keyboard
# → Gboard → Clipboard, its first switch).
gboard_chip() {
  local now
  sh_ am start --activity-clear-task -a android.settings.INPUT_METHOD_SETTINGS > /dev/null; sleep 3
  tap 270 705; sleep 3; tap 300 1790; sleep 3
  # 1 when the switch's track is lit (on), 0 when it is grey (off)
  now=$("${A[@]}" exec-out screencap -p | magick png:- -format '%[fx:p{915,640}.b-p{915,640}.r>0.1?1:0]' info:)
  if { [ "$1" = on ] && [ "$now" = 0 ]; } || { [ "$1" = off ] && [ "$now" = 1 ]; }; then tap 970 640; sleep 1.5; fi
  home
}

# zone: the emulator in $ZONE, automatic time zone off, and a stop if its offset isn't whole hours.
zone() {
  local z
  sh_ cmd time_zone_detector set_auto_detection_enabled false
  sh_ service call alarm 3 s16 "$ZONE" > /dev/null
  z=$(sh_ date +%z | tr -d '\r')
  [ "${z: -2}" = 00 ] || { echo "The zone $ZONE is UTC$z: take one of whole hours (ZONE=)." >&2; exit 1; }
}

# ---- the state --------------------------------------------------------------------------------

setup() {
  sh_ pm clear "$APP"
  sh_ cmd package set-home-activity "$APP/com.mtwabpin.kolahal.MainActivity"
  sh_ cmd uimode night "$DARK"
  zone
  sh_ appops set "$APP" GET_USAGE_STATS allow
  sh_ appops set "$APP" SCHEDULE_EXACT_ALARM allow
  sh_ cmd notification allow_listener "$APP/com.mtwabpin.kolahal.notifications.KolahalNotificationListener"
  sh_ pm grant "$APP" android.permission.POST_NOTIFICATIONS
  gboard_chip off
  home; sleep 2
  tap 980 147; sleep 2   # Skip, on the first start's tour
  local c
  # Pictures are taken with the header's line at the very top of the screen: with the status bar
  # hidden and header.below-bar on (its default), an empty band stays where the bar was. Demos are
  # taken with the built-in command maps off: `ma` then suggests the app Maps, not a command too.
  for c in "config set launch.instant off" "config set header.show on" \
    "config set screen.hide-bars status" "config set header.below-bar off" \
    "config set commands.off maps" "hide kolahal" "theme $THEME" \
    "config set selector.rail on" "config set suggestions.idle on" "config set log.tips off" \
    "config set log.fresh 0" \
    "todo Buy coffee beans" "todo Book the dentist" "todo Renew the passport" "todo Water the plants" \
    "script install weather" "script install world-clocks" "script install habit" \
    "script install forecast" "script install unlock-chart"; do t "$c" 1.5; done
  # The cities before the scripts' first runs, then the lines they wrote are cleared.
  for c in "script set weather city Tokyo" "script set forecast city Lisbon" \
    "script set world-clocks zones Asia/Tokyo Africa/Nairobi Europe/Lisbon" "status clear all" \
    "clear"; do t "$c" 1; done
  for c in "habit done" "feed add https://www.nasa.gov/feeds/iotd-feed/" \
    "feed add https://feeds.bbci.co.uk/news/science_and_environment/rss.xml" \
    "rename kolahal debug = Kolahal" "script install countdown" "script install charge-reminder" \
    "pin maps" "pin clock" "pin camera" "todo toggle #1"; do t "$c" 2; done
  sh_ "cmd notification post -S bigtext -t Maya maya 'Are we still on for Saturday? I can bring the tent.'"
  t "alarm 7:00" 4   # the Clock app sets it; `restore` deletes it
  home; kbup
  # The API reference's Water example (scripting/api.mdx → Taps) and the video's demo script focus
  # (docs/release/video.org → The demo script), typed into the editor; ✕ saves.
  sed -n '/^This widget counts glasses of water/,/^```$/p' site/src/content/docs/scripting/api.mdx \
    | sed '1,5d;$d' > "$TMP/water.lua"
  sed -n '/^\*\* The demo script/,/^#+end_src/p' docs/release/video.org \
    | sed -n '/^#+begin_src lua/,/^#+end_src/p' | sed '1d;$d' > "$TMP/focus.lua"
  for c in water focus; do
    t "script new $c" 3
    tap 540 900; sleep 0.5; sh_ input keycombination 113 29; sleep 0.5; key 67; sleep 0.5   # select all, delete
    edtype "$TMP/$c.lua"; kbdown
    tap 1017 191; sleep 2; kbup
  done
  # The widgets, each in an expressive look (Bold, Split, Shape; never Plain or Tonal).
  t "widget add" 3
  tap 540 621; sleep 1; ty Weather; sleep 1.5; kbdown; tap 540 953; w_look split
  w_add Battery bold
  w_add "World clocks" bold
  w_add 3-day split
  w_add Habit shape
  w_add Unlocks bold
  w_add Todos split
  w_add Water bold
  tap_ui Back; sleep 2   # back home
  # Five glasses on the Water card (its row is the last on the page).
  kbup; t "widget page" 3; sh_ input swipe 540 1800 540 600 300; sleep 1.5
  local i; for i in 1 2 3 4 5; do tap 300 2205; sleep 2; done
  home
}

restore() {
  home; kbup; t "notifs clear" 2; kbdown
  # The 7:00 alarm `setup` made, first in Clock's list: open it and Delete. Clock shows it open
  # already when it was set a moment ago: then a tap on its arrow would close it.
  sh_ am start -a android.intent.action.SHOW_ALARMS; sleep 3
  sh_ uiautomator dump /sdcard/ui.xml > /dev/null
  sh_ grep -q 'text=\"Delete\"' /sdcard/ui.xml || { tap 963 380; sleep 2; }
  sh_ rm /sdcard/ui.xml
  tap 250 1443; sleep 2; home
  # The wallpaper: Android goes back to its default one when the live wallpaper's app is removed
  # (there is no shell command for it), so the app is installed again from this folder's build.
  "${A[@]}" uninstall "$APP"; sleep 3
  "${A[@]}" install -r -t app/build/outputs/apk/debug/app-debug.apk
  sh_ cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity
  sh_ cmd uimode night no
  gboard_chip on
  [ -n "$ZONE_BEFORE" ] && sh_ service call alarm 3 s16 "$ZONE_BEFORE"
  sh_ cmd time_zone_detector set_auto_detection_enabled true
  sh_ appops set "$APP" GET_USAGE_STATS default
  sh_ appops set "$APP" SCHEDULE_EXACT_ALARM default
  sh_ cmd notification disallow_listener "$APP/com.mtwabpin.kolahal.notifications.KolahalNotificationListener"
  sh_ pm revoke "$APP" android.permission.POST_NOTIFICATIONS
  sh_ settings put global window_animation_scale 0
  sh_ settings put global transition_animation_scale 0
  sh_ settings put global animator_duration_scale 0
  "${A[@]}" emu power ac off; "${A[@]}" emu power capacity 100
  sh_ pm clear "$APP"   # the device tests fail on what the captures leave behind
}

# ---- the pictures (in the order they were taken) -----------------------------------------------

# shots/widgets/add.png: the picker of widgets to add, each with its kind and size.
widgets_add() {
  home; kbup; t "widget add" 3
  tap 975 621; sleep 1   # ✕: the picker keeps the last search
  key KEYCODE_BACK 2>/dev/null; sleep 1   # the search box takes the keyboard: Back closes it
  cap widgets-add-picker; site_shot widgets-add-picker shots/widgets/add.png
  key KEYCODE_BACK; sleep 1; home
}

# shots/home/water.png, world-clocks.png, unlock-chart.png, habit.png, forecast.png: five cards of
# the widgets page, cut at full size for the home page's "Scripts and widgets": Water Bold at 5 / 8,
# Unlocks Bold, Habit streak's week as a flower in Shape, 3-day forecast Split, and the World clocks
# card in Split that widget_looks placed (so after it: the page then ends with Water, that card and
# the one in Shape). The flower's leaves are near the page's ground, so it is cut as a square, not
# trimmed. The zone is set again first: the clocks show each city's difference from it.
home_cards() {
  zone
  home; kbup; t "widget page" 3
  sh_ input swipe 540 600 540 1800 300; sleep 2   # to the top: the forecast whole
  cap widgets-page-cards-top
  sh_ input swipe 540 1800 540 600 300; sleep 2   # to the end
  cap widgets-page-clocks-split
  site_card widgets-page-clocks-split 545x270+0+1545 shots/home/water.png
  site_card widgets-page-clocks-split 515x520+545+1550 shots/home/world-clocks.png
  site_card widgets-page-clocks-split 520x520+545+500 shots/home/unlock-chart.png
  site_card widgets-page-cards-top 510x520+545+780 shots/home/forecast.png
  local bg out=$ASSETS/shots/home/habit.png
  bg=$(magick "$RAW/widgets-page-clocks-split.png" -format '%[pixel:p{540,2100}]' info:)
  magick "$RAW/widgets-page-clocks-split.png" -crop 500x500+25+240 +repage -bordercolor "$bg" -border 18 -strip "$out"
  pngquant --quality 85-98 --force --output "$out" "$out"
  home
}

# hero <scene> <yes|no> <name>: home with the keyboard down over a scene, as the home page's phone
# shows it (shots/hero/<scene>-<dark|light>.png): the weather, a conversion and a reminder.
hero() {
  sh_ cmd uimode night "$2"; home; kbup
  t "shader $1" 3; t clear 1; t weather 5; t "= 5 km in mi" 2; t "remind 8pm pick up the bike" 2
  kbdown; wait_cleared; cap "$3"
}
hero_dunes_dark() { hero dunes yes home-dunes-dark; site_shot home-dunes-dark shots/hero/dunes-dark.png; }
hero_mountains_dark() { hero mountains yes home-mountains-dark; site_shot home-mountains-dark shots/hero/mountains-dark.png; }
hero_pond_light() { hero pond no home-pond-light; site_shot home-pond-light shots/hero/pond-light.png; sh_ cmd uimode night "$DARK"; }

# shots/typing/home.png: home with the keyboard down, no shader: the header, two answers, the prompt.
typing_home() {
  home; kbup; t "shader off" 2; t clear 1; t weather 5; t "= 5 km in mi" 2; kbdown; wait_cleared
  cap typing-home; site_shot typing-home shots/typing/home.png
}

# shots/suggestions/empty.png, flash.png, theme.png: an empty prompt (favorites nearest it, then
# the most-used apps), `flash` finding torch, and the theme names after `theme `. Opens a few apps
# first, so they count as used; the key bar off, as by default. No clipboard chip may show in the
# keyboard's strip: Gboard hides it about a minute after a copy.
suggestions() {
  local a
  for a in calendar files clock calendar files; do home; kbup; t "$a" 3; done
  home; kbup; t "config set keybar.show off" 2; t clear 1; t weather 5; t "= 5 km in mi" 2
  kbdown; wait_cleared
  tap 540 500; sleep 2.5; cap suggestions-empty; site_shot suggestions-empty shots/suggestions/empty.png
  ty flash; sleep 2; cap suggestions-flash; site_shot suggestions-flash shots/suggestions/flash.png
  tap 928 1464; sleep 1   # ✕ empties the prompt
  ty "theme "; sleep 2; cap suggestions-theme; site_shot suggestions-theme shots/suggestions/theme.png
  tap 928 1464; sleep 1; kbdown
}

# shots/scripts/forecast.png: `forecast lisbon`, three days with their lows and highs.
forecast() {
  home; kbup; t clear 1; t "forecast lisbon" 6; kbdown; wait_cleared
  cap scripts-forecast; site_shot scripts-forecast shots/scripts/forecast.png
}

# shots/log/fresh.png: a few commands, then `fresh`: a clean screen, "▴ 12 earlier lines".
fresh() {
  home; kbup; t "= 12 * 4.5 + 18" 2; t "date tokyo lisbon" 2; t todo 2; t fresh 3; kbdown; sleep 2
  cap log-fresh; site_shot log-fresh shots/log/fresh.png
}

# shots/pinning/pin.png, pinned.png, bar.png: weather's action sheet with "Pin weather to the
# top", the pinned line under the header, then the demo script focus pinned with its bar.
pinning() {
  home; kbup; t clear 1; t weather 5; kbdown; wait_cleared
  sh_ input swipe 300 2120 300 2120 900; sleep 2   # long-press weather's 17°C line
  cap pinning-pin; site_shot pinning-pin shots/pinning/pin.png
  tap 300 1290; sleep 3   # Pin weather to the top
  cap pinning-pinned; site_shot pinning-pinned shots/pinning/pinned.png
  kbup; t "focus 2" 3; kbdown
  sh_ input swipe 200 2187 200 2187 900; sleep 2   # long-press "Focus until …"
  tap 300 1290; sleep 4   # Pin focus to the top: the newest pinned line shows first
  cap pinning-bar; site_shot pinning-bar shots/pinning/bar.png
  kbup; t "focus stop" 2; t "script unpin focus" 2; t "script unpin weather" 2; kbdown
}

# shots/schedule/times.png: night-dnd's sheet at its end: Runs every, Daily at 07:00, 23:00.
schedule_times() {
  home; kbup; t "script install night-dnd" 3; t scripts 3
  tap 300 2217; sleep 2.5   # Night Do Not Disturb, the sixth card by name
  sh_ input swipe 540 2000 540 400 300; sleep 1; sh_ input swipe 540 2000 540 400 300; sleep 1.5
  cap schedule-times; site_shot schedule-times shots/schedule/times.png
  key KEYCODE_BACK; sleep 1; home
}

# shots/schedule/charger.png: charge-reminder's line on unplugging: 62%, +12% added, a bar.
# The emulator's battery goes 50% → plugged in → 62% → unplugged, home in front all along.
schedule_charger() {
  home; kbup; t clear 1; kbdown; wait_cleared
  "${A[@]}" emu power capacity 50; sleep 2; "${A[@]}" emu power ac on; sleep 30
  "${A[@]}" emu power capacity 62; sleep 10; "${A[@]}" emu power ac off; sleep 10
  cap schedule-charger; site_shot schedule-charger shots/schedule/charger.png
  "${A[@]}" emu power capacity 100
}

# shots/widgets/page.png, page-light.png: the widgets page (8 cards, `setup`'s), dark and light.
widgets_pages() {
  home; kbup; t "widget page" 3
  sh_ input swipe 540 600 540 1800 300; sleep 2   # to the top
  cap widgets-page; site_shot widgets-page shots/widgets/page.png
  sh_ cmd uimode night no; sleep 4
  cap widgets-page-light; site_shot widgets-page-light shots/widgets/page-light.png
  sh_ cmd uimode night "$DARK"; sleep 4
  arrange_widgets
}

# shots/widget-options/arrange-all.png: Arrange widgets with nothing chosen, opened fresh by the
# widgets page's pencil, its title bar open; arrange.png: Habit streak chosen by a long-press on
# the page at its top (its Look and Size rows). Choosing a card scrolls it into view above the
# toolbar, and that scroll folds the title bar by as much: from the page's top it folds whole (the
# small title, at full strength), but from a page scrolled part way it stops part way, with the
# big title half faded between its two states. ✓ leaves Arrange widgets with the bar as it is, so
# the picture with nothing chosen is taken first, on a fresh open.
arrange_widgets() {
  home; kbup; t "widget page" 3
  sh_ input swipe 540 600 540 1800 300; sleep 2   # the page at its top
  tap_ui "Edit widgets"; sleep 3   # the pencil: Arrange widgets, fresh
  cap arrange-widgets; site_shot arrange-widgets shots/widget-options/arrange-all.png
  key KEYCODE_BACK; sleep 2
  sh_ input swipe 540 600 540 1800 300; sleep 2   # the page at its top again
  sh_ input swipe 275 1280 275 1280 900; sleep 3   # long-press Habit streak
  cap arrange-widgets-look; site_shot arrange-widgets-look shots/widget-options/arrange.png
  tap_ui Done; sleep 1.5; key KEYCODE_BACK; sleep 1.5   # ✓, back to the page
  home
}

# shots/widget-options/looks.png: World clocks in Bold (setup's), Split and Shape, cut from the
# page and set in a grid on its ground (they aren't side by side on the page). After
# widget_options (Paris's card on the page). The look sheet can come up late: check each card's look.
widget_looks() {
  home; kbup; t "widget page" 3; tap_ui "Edit widgets"; sleep 2.5   # the pencil: Arrange widgets
  # A placed World clocks card opens its Options sheet: Back closes it. The new cards go at the
  # end of the page, Split beside Water and Shape under it.
  w_search "World clocks"; tap 540 953; sleep 4; key KEYCODE_BACK; sleep 1.5; w_look split
  w_search "World clocks"; tap 540 953; sleep 4; key KEYCODE_BACK; sleep 1.5; w_look shape
  tap_ui Back; sleep 2.5
  sh_ input swipe 540 600 540 1800 300; sleep 2; cap widget-looks-top
  sh_ input swipe 540 1800 540 400 300; sleep 2; cap widget-looks-bottom
  local bg='srgb(25,23,36)' f
  cut_() { magick "$RAW/$1.png" -crop "$2" +repage -bordercolor "$bg" -border 2 -fuzz 3% -trim +repage -bordercolor "$bg" -border 18 "$TMP/$3.png"; }
  cut_ widget-looks-top 540x535+0+515 bold
  cut_ widget-looks-bottom 518x520+540+1550 split   # short of the selector's letters at the edge
  cut_ widget-looks-bottom 540x540+0+1815 shape     # short of the gesture handle
  magick "$TMP/bold.png" "$TMP/split.png" -background "$bg" -gravity north +append +repage "$TMP/row1.png"
  magick "$TMP/shape.png" -background "$bg" -gravity center \
    -extent "$(magick identify -format %w "$TMP/row1.png")x$(magick identify -format %h "$TMP/shape.png")" +repage "$TMP/row2.png"
  magick "$TMP/row1.png" "$TMP/row2.png" -background "$bg" -append +repage "$RAW/widget-looks.png"
  site_shot widget-looks shots/widget-options/looks.png
}

# shots/widget-options/options.png: a second Weather card, its city option Paris, in Split,
# dragged up beside Tokyo's; shots/widget-options/settings-city.png: that card's own page, which
# opens when it is placed, with Paris under This card. Run it before widget_looks, on setup's page:
# a removed card leaves a hole on the page that the others don't close.
widget_options() {
  home; kbup; t "widget page" 3
  tap_ui "Add a widget"; sleep 2.5   # + on the widgets page
  tap 975 621; sleep 0.5; tap 540 621; sleep 0.8; ty Weather; sleep 1.5; kbdown
  tap 540 953; sleep 4   # placed; its own page opens
  tap_ui city; sleep 2
  tap 540 1232; sleep 1; sh_ input keycombination 113 29; sleep 0.5; ty Paris; sleep 1
  tap_ui Save; sleep 4   # the card at the top runs again for Paris
  cap widget-settings-city; site_shot widget-settings-city shots/widget-options/settings-city.png
  key KEYCODE_BACK; sleep 2   # Arrange widgets, the card chosen
  tap_ui Split last; sleep 1.5
  # Drag it up from beside Water: the page scrolls to the top and drops it beside Tokyo's card
  # (the Remove target is above, at y 160–260: don't let go there).
  sh_ input motionevent DOWN 800 1406; sleep 0.6
  local y; for y in 1350 1300 1200 1100 1000 900 800 700 600 500 420 380; do sh_ input motionevent MOVE 800 $y; sleep 0.15; done
  sleep 3; sh_ input motionevent MOVE 800 400; sleep 0.3; sh_ input motionevent UP 800 400; sleep 2
  tap_ui Done; sleep 1.5; tap_ui Back; sleep 2.5
  sh_ input swipe 540 600 540 1800 300; sleep 2
  cap widget-options-cities; site_shot widget-options-cities shots/widget-options/options.png
  home
}

# shots/widget-options/settings.png, settings-shape.png: the Weather card's own page (Settings in
# its toolbar), the card in Shape with Link and the 4-leaf clover chosen: the top, with the card live
# and Color; then scrolled to Shape and What shows. Reset puts its color and shape back, and it goes
# back to Split.
widget_settings() {
  home; kbup; t "widget page" 3
  sh_ input swipe 540 600 540 1800 300; sleep 1.5
  tap_ui "Edit widgets"; sleep 3; tap_ui Weather; sleep 2
  tap_ui Shape last; sleep 1.5; tap_ui Settings; sleep 3
  tap_ui Link; sleep 1.5; tap_ui "4-leaf clover"; sleep 2.5
  sh_ input swipe 540 900 540 2000 400; sleep 2   # to the top
  cap widget-settings; site_shot widget-settings shots/widget-options/settings.png
  sh_ input swipe 540 1900 540 900 600; sleep 2
  cap widget-settings-shape; site_shot widget-settings-shape shots/widget-options/settings-shape.png
  tap_ui "Reset to the card's own"; sleep 2; key KEYCODE_BACK; sleep 2
  tap_ui Split last; sleep 1.5; tap_ui Done; sleep 1; tap_ui Back; sleep 2; home
}

# shots/widgets/pages-1.png, pages-2.png, shots/widget-options/arrange-pages.png: three widgets
# pages. In Arrange widgets, + after the tabs adds a page; a long-press on its tab names it (Travel,
# Health); a card's Page row moves it: Paris's weather and the two World clocks cards that
# widget_looks placed to Travel, Habit streak and Water to Health. Close gaps, then Battery chosen,
# its toolbar with the Page row. Home on page 1, its title row "Page 1" with three dots, and on
# Travel (`widget page 2`). After widget_settings and home_cards: they want the cards on page 1.
widget_pages() {
  local c
  home; kbup; t "widget page" 3; tap_ui "Edit widgets"; sleep 3
  tap_ui "Add a page"; sleep 2.5
  tap_ui "Page 1"; sleep 2
  for c in "Weather 2" "World clocks 2" "World clocks 3"; do
    tap_ui "Page 1"; sleep 2; sh_ input swipe 540 1800 540 400 300; sleep 1.5
    tap_ui "$c"; sleep 2; tap_ui "Page 2" last; sleep 2.5; tap_ui Done; sleep 1
  done
  sh_ input swipe 690 369 690 369 900; sleep 2   # long-press the tab of page 2
  tap 480 1100; sleep 1; sh_ input keycombination 113 29; ty Travel; sleep 1; tap_ui Save; sleep 2
  tap_ui "Add a page"; sleep 2.5
  sh_ input swipe 763 370 763 370 900; sleep 2   # long-press the tab of page 3
  tap 480 1100; sleep 1; sh_ input keycombination 113 29; ty Health; sleep 1; tap_ui Save; sleep 2
  for c in "Habit streak" "Water"; do
    tap_ui "Page 1"; sleep 2; sh_ input swipe 540 1800 540 400 300; sleep 1.5
    tap_ui "$c"; sleep 2; tap_ui Health last; sleep 2.5; tap_ui Done; sleep 1
  done
  tap_ui "Page 1"; sleep 2; sh_ input swipe 540 600 540 1800 300; sleep 1.5
  tap_ui "Close gaps"; sleep 2.5; tap_ui "Battery and network"; sleep 2.5
  cap arrange-pages; site_shot arrange-pages shots/widget-options/arrange-pages.png
  tap_ui Done; sleep 1; tap_ui Back; sleep 2.5
  sh_ input swipe 540 600 540 1800 300; sleep 2
  cap widgets-pages-1; site_shot widgets-pages-1 shots/widgets/pages-1.png
  home; kbup; t "widget page 2" 3; sh_ input swipe 540 600 540 1800 300; sleep 2
  cap widgets-pages-2; site_shot widgets-pages-2 shots/widgets/pages-2.png
  home
}

# shots/usage/limits.png: a daily limit for unlocks under today's count: the header's unlocks part
# and `unlocks 7` in the warning color, with "3 over your limit of 10". The limit is set below
# today's count (13 here; check `unlocks` first and pick one so the count is past it but under one
# and a half times it). The header is redrawn when the screen comes on again. Settings back after.
limits() {
  home; kbup; t "config set header.right unlocks,todo,battery" 2; t "config set usage.unlock-limit 10" 2
  kbdown; key KEYCODE_POWER; sleep 2; key KEYCODE_WAKEUP; sleep 1; sh_ wm dismiss-keyguard; sleep 3; home
  kbup; t clear 1; t "unlocks 7" 3; kbdown; wait_cleared
  cap limits-unlocks; site_shot limits-unlocks shots/usage/limits.png
  kbup; t "config set header.right alarm,todo,battery" 2; t "config set usage.unlock-limit 0" 2; kbdown
}

# shots/wallpaper/preview.png, lock.png, log.png: `wallpaper shader pond` in Android's preview,
# set on both screens and seen on the lock screen, then home's log with the pond through it.
# The emulator's lock screen is off (None): it is turned on for the picture and off again; with it
# on, the screen sleeps after a few seconds. `restore` puts the emulator's wallpaper back.
wallpaper() {
  home; kbup; t clear 1; t "shader off" 2; t "wallpaper shader pond" 5
  cap wallpaper-preview; site_shot wallpaper-preview shots/wallpaper/preview.png
  tap 540 2245; sleep 2.5   # Set wallpaper
  tap 540 1415; sleep 4     # Home screen and lock screen
  # Maya's notification would show on the lock screen and shrink its clock: cleared first.
  home; kbup; t "notifs clear" 2; kbdown
  sh_ locksettings set-disabled false
  key KEYCODE_POWER; sleep 3; key KEYCODE_POWER; sleep 5
  cap wallpaper-lock; site_shot wallpaper-lock shots/wallpaper/lock.png
  sh_ input swipe 540 1900 540 700 300; sleep 2
  sh_ locksettings set-disabled true; key KEYCODE_WAKEUP; sleep 1; sh_ wm dismiss-keyguard; sleep 2
  home; kbup
  t "config set log.wallpaper 60" 2; t clear 1; t weather 5; t "= 5 km in mi" 2
  t "remind 8pm pick up the bike" 2; kbdown; wait_cleared
  cap wallpaper-log; site_shot wallpaper-log shots/wallpaper/log.png
  kbup; t "config set log.wallpaper 0" 2; kbdown
}

# parts/selector-slide.png, selector-pick.png, selector-open.png and shots/selector/pick.png: the app
# selector under a held touch. At G on the edge (with setup's three favorites, y 1370 is G); then a
# quick slide onto Google's row, captured on the device at once (the row fills from 300 ms and the
# app opens at 800 ms: the first capture shows it highlighted, the second filling); back onto the
# edge, which cancels, and a lift there, which leaves the list open.
selector() {
  home; kbup; t clear 1; kbdown; wait_cleared
  sh_ input motionevent DOWN 1068 1550
  local y; for y in 1530 1510 1490 1470 1450 1430 1412 1400 1390 1380 1370; do sh_ input motionevent MOVE 1068 $y; done
  sleep 1.2; cap selector-slide
  sh_ "input motionevent MOVE 900 1380; input motionevent MOVE 700 1385; input motionevent MOVE 500 1387; screencap -p /sdcard/pick1.png; screencap -p /sdcard/pick2.png"
  sh_ input motionevent MOVE 1068 1387; sleep 0.5; sh_ input motionevent UP 1068 1387; sleep 1.5
  # The two captures can outlast the 800 ms, and Google opens: the open list is taken on its own,
  # a slide to G and a lift on the edge.
  home; sh_ input motionevent DOWN 1068 1550
  for y in 1530 1510 1490 1470 1450 1430 1412 1400 1390 1380 1370; do sh_ input motionevent MOVE 1068 $y; done
  sleep 1.2; sh_ input motionevent UP 1068 1370; sleep 1.5
  cap selector-open
  "${A[@]}" pull /sdcard/pick1.png "$RAW/selector-pick.png" > /dev/null
  "${A[@]}" pull /sdcard/pick2.png "$RAW/selector-pick-filling.png" > /dev/null
  sh_ rm /sdcard/pick1.png /sdcard/pick2.png
  site_shot selector-slide parts/selector-slide.png
  site_shot selector-pick parts/selector-pick.png
  site_shot selector-open parts/selector-open.png
  site_shot selector-pick-filling shots/selector/pick.png
  key KEYCODE_BACK; sleep 1
}

# parts/home.png (marks in getting-started/parts.mdx): countdown pinned, then four groups:
# countdown, date tokyo london, a conversion, a todo added.
parts_home() {
  home; kbup; t clear 1
  t "countdown add 2026-12-25 Holidays" 3; t countdown 3; kbdown
  sh_ input swipe 200 1880 200 1880 900; sleep 2   # long-press "Countdowns"
  tap 300 1290; sleep 3   # Pin countdown to the top
  kbup; t clear 1; t countdown 3; t "date tokyo london" 2; t "= 5 km in mi" 2
  t "todo add call the bank" 2; kbdown; wait_cleared
  cap parts-home; site_shot parts-home parts/home.png
}

# parts/action-sheet.png: the todo line's action sheet, from the sheet's top (after parts_home).
parts_action_sheet() {
  sh_ input swipe 300 2128 300 2128 900; sleep 2   # long-press "4. [ ] call the bank"
  cap action-sheet-todo; site_crop action-sheet-todo 1080x1520+0+880 parts/action-sheet.png
  key KEYCODE_BACK; sleep 1.5
}

# parts/quick-actions.png: a tap on the $, from the sheet's top.
parts_quick_actions() {
  tap 42 2283; sleep 2
  cap quick-actions; site_crop quick-actions 1080x1800+0+600 parts/quick-actions.png
  key KEYCODE_BACK; sleep 1.5
}

# parts/typing.png: `ma` typed with the key bar on (todo and a conversion in the log), from the
# header to the key bar.
parts_typing() {
  home; kbup; t "config set keybar.show on" 2; t clear 1; t todo 2
  t "= 100 usd in eur" 3; kbdown; wait_cleared
  tap 540 600; sleep 2; ty ma; sleep 2.5
  cap parts-typing; site_crop parts-typing 1080x1530+0+0 parts/typing.png   # the header is at the top
  tap 928 1360; sleep 1; t "config set keybar.show off" 2; kbdown
}

# parts/settings.png: `settings`.
parts_settings() {
  home; kbup; t settings 3
  cap parts-settings; site_shot parts-settings parts/settings.png
}

# parts/scripts-tab.png: the Scripts tab (after parts_settings), Focus turned off, Countdowns'
# details open. Turns Focus on again after.
parts_scripts() {
  tap 540 527; sleep 2.5        # the Scripts tab
  tap 937 1757; sleep 1.5       # Focus's switch: off
  tap 803 1513; sleep 2         # Countdowns' details arrow
  cap parts-scripts; site_shot parts-scripts parts/scripts-tab.png
  tap 803 1513; sleep 1.5
  tap 937 1757; sleep 1.5       # Focus on again
  key KEYCODE_BACK; sleep 1; home
}

# parts/editor.png: unlock-chart.lua in the editor with the keyboard up, from above its top bar to
# the symbol bar. A letter typed and deleted hides the cursor's handle that a tap leaves.
parts_editor() {
  home; kbup; t "script edit unlock-chart" 3
  tap 700 1000; sleep 2; sh_ input text x; sleep 0.5; key 67; sleep 1.5
  cap parts-editor; site_crop parts-editor 1080x1440+0+80 parts/editor.png
  kbdown; tap 1017 191; sleep 2; home
}

# setup.png: the `setup` checklist.
setup_checklist() {
  home; kbup; t setup 3
  cap setup; site_shot setup setup.png
  key KEYCODE_BACK; sleep 1; home
}

# terminal-feeds.png: home with the countdown pinned (parts_home), then countdown, the feed list
# (`feed`, not `feed read`: news items carry real people's names) and a conversion.
terminal_feeds() {
  home; kbup; t clear 1; t countdown 3; t feed 3; t "= 5 km in mi" 2; kbdown; wait_cleared
  cap terminal-feeds; site_shot terminal-feeds terminal-feeds.png
}

# app-grid.png and shots/profiles/resume.png: a work profile made for the pictures (a managed
# profile with no admin app, which the shell can create), some apps added to it; `apps` typed on
# a home with a few lines in the log: three columns, a count in each heading, "+N more" on a long
# group. raw/app-grid-more.png: the same after a tap on the personal group's "+N more". Then
# `work off` and a tap on a faded work app: "Resume work apps?". The profile is removed after.
profiles() {
  local u p
  u=$(sh_ pm create-user --profileOf 0 --managed Work | sed -n 's/.*created user id \([0-9]*\).*/\1/p' | tr -d '\r')
  sh_ am start-user "$u"; sleep 5
  for p in com.google.android.calendar com.android.chrome com.google.android.deskclock \
    com.google.android.contacts com.google.android.apps.docs com.google.android.documentsui \
    com.google.android.gm com.google.android.apps.maps com.google.android.apps.messaging \
    com.google.android.apps.photos; do sh_ pm install-existing --user "$u" "$p"; done
  home; kbup; t clear 1; t weather 5; t "= 5 km in mi" 2; t apps 4; kbdown; wait_cleared
  cap app-grid; site_shot app-grid app-grid.png
  tap 825 1515; sleep 2.5   # "+8 more" at the end of the personal group
  cap app-grid-more
  kbup; t "work off" 4; t clear 1; t apps 4; kbdown; wait_cleared
  tap 225 1731; sleep 2.5   # Calendar, the first work app, faded
  cap profiles-resume; site_shot profiles-resume shots/profiles/resume.png
  tap 556 1470; sleep 1     # Cancel
  sh_ pm remove-user "$u"
}

# shots/tty/home.png, matrix.png, clock.png: the TTY style. Typing right after
# `config set home.style tty` gets lost while home is redrawn: give it a moment and open the
# keyboard again. The screens need the animator scale at 1 (with animations off they show a still
# frame and a note).
tty() {
  # parts_home's pinned countdown and its fourth todo go first: the TTY and blog pictures have neither.
  home; kbup; t "script unpin countdown" 2; t "todo rm 4" 2
  t "shader off" 2; t "config set home.style tty" 3; key KEYCODE_HOME; sleep 2; kbup
  t clear 1; t weather 5; t cal 2; t todo 2; kbdown; wait_cleared
  cap tty-home; site_shot tty-home shots/tty/home.png
  sh_ settings put global animator_duration_scale 1
  kbup; t matrix 5; cap tty-matrix; site_shot tty-matrix shots/tty/matrix.png; key KEYCODE_BACK; sleep 2
  kbup; t clock 4; cap tty-clock; site_shot tty-clock shots/tty/clock.png; key KEYCODE_BACK; sleep 2
  sh_ settings put global animator_duration_scale 0
  kbup; t "config set home.style gui" 3; kbdown
}

# ---- the store's captures of home (docs/release/google-play.org → Graphics) ---------------------
# First taken by hand; the site shows them too (site/CAPTURES.md, "Play captures").

# raw/home-pond-dark.png, home-mountains-light.png: hero's home over pond (dark) and mountains
# (light), for the store, the home page's phone and the scripts guide's `weather`.
play_home() {
  hero pond yes home-pond-dark
  site_shot home-pond-dark shots/hero/pond-dark.png; site_shot home-pond-dark shots/scripts/weather.png
  hero mountains no home-mountains-light
  site_shot home-mountains-light shots/hero/mountains-light.png
  site_shot home-mountains-light shots/scripts/weather-light.png
  sh_ cmd uimode night "$DARK"
}

# raw/answers-dunes-light.png, answers-dark.png: a sum, a conversion, the todos, a reminder and
# `date tokyo lisbon`, over dunes in light, then with no shader in dark.
play_answers() {
  local mode
  for mode in no yes; do
    sh_ cmd uimode night "$mode"; home; kbup
    if [ "$mode" = no ]; then t "shader dunes" 3; else t "shader off" 2; fi
    t clear 1; t "= 12 * 4.5 + 18" 2; t "= 72 f in c" 2; t todo 2
    t "remind fri 9am renew the passport" 2; t "date tokyo lisbon" 2; kbdown; wait_cleared
    if [ "$mode" = no ]; then
      cap answers-dunes-light; site_shot answers-dunes-light shots/hero/dunes-light.png
      site_shot answers-dunes-light shots/log/answers-light.png
    else
      cap answers-dark; site_shot answers-dark shots/log/answers.png
    fi
  done
  sh_ cmd uimode night "$DARK"
}

# raw/typing-suggestions.png: `ma` typed over pond (todo and a conversion in the log), keyboard up.
play_typing() {
  home; kbup; t "shader pond" 3; t clear 1; t todo 2; t "= 100 usd in eur" 3; kbdown; wait_cleared
  kbup; ty ma; sleep 2.5
  cap typing-suggestions; site_shot typing-suggestions shots/typing/ma.png
  key 67; key 67; sleep 1; kbdown   # the two letters deleted
}

# raw/selector-thumb-g.png, selector-thumb-p.png: the log of home-pond-dark, the selector under a
# held touch at G, then at P; let go at the bottom of the edge, where nothing opens.
play_selector() {
  home; kbup; t "shader pond" 3; t clear 1; t weather 5; t "= 5 km in mi" 2
  t "remind 8pm pick up the bike" 2; kbdown; wait_cleared
  sh_ input motionevent DOWN 1068 1550
  # With setup's three favorites (★ first on the edge) y 1370 is G and 1550 is P.
  local y; for y in 1530 1510 1490 1470 1450 1430 1412 1400 1390 1380 1370; do sh_ input motionevent MOVE 1068 $y; done
  sleep 1.2; cap selector-thumb-g
  for y in 1400 1450 1500 1550; do sh_ input motionevent MOVE 1068 $y; done
  sleep 1.2; cap selector-thumb-p
  sh_ input motionevent MOVE 1068 2350; sleep 0.3; sh_ input motionevent UP 1068 2350; sleep 1.5
  site_shot selector-thumb-g shots/home/selector.png; site_shot selector-thumb-g shots/selector/slide.png
  site_shot selector-thumb-p shots/selector/slide-p.png
}

# raw/scene-candle-screen-time.png, scene-lanterns-unlocks.png, scene-hourglass-screen-time.png:
# three scenes behind home, each with a line that its parameter follows.
play_scenes() {
  home; kbup; t "shader candle" 3; t clear 1; t "usage 1" 3; kbdown; wait_cleared
  cap scene-candle-screen-time; site_shot scene-candle-screen-time shots/shaders/candle.png
  kbup; t "shader lanterns" 3; t clear 1; t "unlocks week" 3; kbdown; wait_cleared
  cap scene-lanterns-unlocks; site_shot scene-lanterns-unlocks shots/shaders/lanterns.png
  kbup; t "shader hourglass" 3; t clear 1; t weather 5; t "remind 8pm pick up the bike" 2; kbdown; wait_cleared
  cap scene-hourglass-screen-time; site_shot scene-hourglass-screen-time shots/shaders/hourglass.png
}

# raw/log-notification-feed.png, action-sheet-line.png, quick-actions-full-build.png: over pond,
# Maya's notification (setup's) and the feeds in the log; a long-press on the NASA feed's row; a
# tap on the $.
play_log() {
  home; kbup; t "shader pond" 3; t clear 1; t notifs 2; t feed 3; t "remind 8pm pick up the bike" 2
  kbdown; wait_cleared
  cap log-notification-feed; site_shot log-notification-feed shots/log/feed.png
  sh_ input swipe 300 1690 300 1690 900; sleep 2   # long-press "#1 NASA Image of the Day"
  cap action-sheet-line; site_shot action-sheet-line shots/log/actions.png
  key KEYCODE_BACK; sleep 1.5
  tap 42 2283; sleep 2   # the $
  cap quick-actions-full-build; site_shot quick-actions-full-build shots/home/quick-actions.png
  key KEYCODE_BACK; sleep 1.5
}

# raw/scramble.png: `scramble on` over pond (weather, a conversion, `todo`); `scramble off` after.
play_scramble() {
  home; kbup; t "shader pond" 3; t clear 1; t weather 5; t "= 5 km in mi" 2; t todo 2
  t "scramble on" 3; kbdown; wait_cleared
  cap scramble; site_shot scramble shots/log/scramble.png
  kbup; t "scramble off" 2; t "shader off" 2; kbdown
}

# raw/script-sheet-switches.png, shader-details-follows.png, shader-details-slider.png: sheets in
# Settings (they once brought the status bar back): weather.lua's sheet (its switches and its
# city), then the Shaders tab (`shaders`) with "screen time" searched and Candle's ⓘ, then
# "mountains" and its ⓘ.
play_sheets() {
  home; kbup; t scripts 3
  tap 540 735; sleep 1; ty weather; sleep 1.5; key KEYCODE_BACK; sleep 1   # the search
  tap 300 1010; sleep 2.5   # Weather, the one row
  cap script-sheet-switches; site_shot script-sheet-switches shots/access/switches.png
  key KEYCODE_BACK; sleep 1; key KEYCODE_BACK; sleep 1
  home; kbup; t shaders 3
  tap 540 772; sleep 1; ty "screen time"; sleep 2; key KEYCODE_BACK; sleep 1.5
  tap 942 1514; sleep 3     # ⓘ on Candle, the second row
  cap shader-details-follows; site_shot shader-details-follows shots/shader-parameters/follow.png
  key KEYCODE_BACK; sleep 1.5
  tap 975 772; sleep 1; tap 540 772; sleep 1; ty mountains; sleep 2; key KEYCODE_BACK; sleep 1.5
  tap 942 1097; sleep 3     # ⓘ on Mountains
  cap shader-details-slider; site_shot shader-details-slider shots/shader-parameters/sliders.png
  key KEYCODE_BACK; sleep 1; key KEYCODE_BACK; sleep 1; home
}

# ---- for the blog post on 0.5.17 (kept in raw/ only) --------------------------------------------

# raw/tty-top.png: the TTY style's `top` screen: the time, bars for the battery, memory, Kolahal's
# own and today's screen time, and the scripts' table. It needs the animator scale at 1.
tty_top() {
  home; kbup; t "shader off" 2; t "config set home.style tty" 3; key KEYCODE_HOME; sleep 2; kbup
  sh_ settings put global animator_duration_scale 1
  t clear 1; t top 4   # a moment to settle
  cap tty-top; key KEYCODE_BACK; sleep 2
  sh_ settings put global animator_duration_scale 0
  kbup; t "config set home.style gui" 3; kbdown
}

# raw/scramble-before.png, scramble-after.png: the same home, then `scramble` typed (its own line
# is the only other change), then `scramble off`.
scramble_pair() {
  home; kbup; t "shader off" 2; t clear 1; t weather 5; t "= 5 km in mi" 2; t todo 2; kbdown; wait_cleared
  cap scramble-before
  kbup; t scramble 3; kbdown; sleep 2
  cap scramble-after
  kbup; t "scramble off" 2; kbdown
}

# raw/typing-tty-parts.png, typing-gui-parts.png: `ca` typed over the same log, keyboard up, with
# the suggestions and the key bar: in the TTY style with all its parts on the grid, then in the
# default style. The settings go back after.
typing_styles() {
  local p parts=(suggestions selector keybar quick-actions menu)
  home; kbup; t "shader off" 2; t "config set home.style tty" 3; key KEYCODE_HOME; sleep 2; kbup
  for p in "${parts[@]}"; do t "config set tty.$p tty" 1.5; done
  t "config set keybar.show on" 1.5
  t clear 1; t "= 5 km in mi" 2; kbdown; wait_cleared; kbup; ty ca; sleep 2.5
  cap typing-tty-parts
  tap 947 1358; sleep 0.5   # ✕ empties the prompt
  for p in "${parts[@]}"; do t "config set tty.$p gui" 1.5; done
  t "config set home.style gui" 3; key KEYCODE_HOME; sleep 2; kbup
  t clear 1; t "= 5 km in mi" 2; kbdown; wait_cleared; kbup; ty ca; sleep 2.5
  cap typing-gui-parts
  tap 928 1358; sleep 0.5
  t "config set keybar.show off" 1.5; kbdown
}

# raw/reference-table-tty.png: the editor's reference, Log lines, fmt.table's entry with its Home
# style set to TTY: the table drawn with box lines, as on the grid.
reference_tty() {
  home; kbup; t "script edit unlock-chart" 3; kbdown
  tap 639 191; sleep 2.5     # ?: the reference
  tap 848 527; sleep 2       # the Log lines tab
  tap 1017 1740; sleep 2.5   # fmt.table
  tap 789 848; sleep 2       # Home style: TTY
  cap reference-table-tty
  key KEYCODE_BACK; sleep 1.5; key KEYCODE_BACK; sleep 1.5
  tap 1017 191; sleep 2; home   # ✕ closes the editor
}

# ---- running it -------------------------------------------------------------------------------

# The pictures in an order that works from `setup`'s state: each leaves home in front.
PICTURES=(widgets_add hero_dunes_dark hero_mountains_dark hero_pond_light
  play_home play_answers play_typing play_selector play_scenes play_log play_scramble play_sheets
  typing_home suggestions forecast fresh pinning schedule_times schedule_charger widgets_pages
  widget_options widget_looks home_cards widget_settings widget_pages limits wallpaper selector parts_home parts_action_sheet parts_quick_actions
  parts_typing parts_settings parts_scripts parts_editor setup_checklist terminal_feeds profiles
  tty tty_top scramble_pair typing_styles reference_tty)

[ $# -gt 0 ] || { sed -n '2,24p' "$0"; exit 2; }
mkdir -p "$RAW"
for f in "$@"; do
  case $f in
    list) printf '%s\n' setup restore "${PICTURES[@]}" ;;
    all) setup; for p in "${PICTURES[@]}"; do "$p"; done; restore ;;
    *) declare -F "$f" > /dev/null || { echo "No picture or step named $f (list: $0 list)" >&2; exit 2; }; "$f" ;;
  esac
done
echo "Look at each picture before committing it; the notes at the top of site/CAPTURES.md say how."
