#!/usr/bin/env bash
# The screen recordings of the preview video (docs/release/video.org → The shot list), one file per
# part, taken on the emulator. A record that runs: run it again with another theme or after the app
# changes, look at every take's sheet, and fix what drifts (a row that moved, a wait too short).
#
#   tools/video/record.sh setup              # the state the takes need (clears the app's data first)
#   tools/video/record.sh <id>...            # one or more parts again, by id (01-typing …; `list`)
#   tools/video/record.sh keep <id> [take]   # a take (the newest by default) as the part's recording
#   tools/video/record.sh all                # setup, every part kept as taken, restore
#   tools/video/record.sh restore            # the emulator as it was, and the app's data cleared
#   tools/video/record.sh do '<steps>'       # helpers by hand, to measure a place or a wait
#   THEME=nord tools/video/record.sh all     # another theme: recordings/<theme>/, which
#                                            # `compose.sh full nord` reads
#
# - Emulator only, never a phone: every command goes to $SERIAL (emulator-5554). The app is the
#   debug build, already installed; `setup` makes it the home app.
# - Coordinates are for this emulator: 1080 × 2400 pixels at 420 dpi, gesture navigation, Gboard
#   with its number row. Another screen needs them measured again.
# - Touches go in as the touchscreen's own events, through the emulator's console (`adb emu event
#   send`), not `input tap`: Android's "Show taps" draws a dot only for those, and a slide can bend
#   and move at a finger's speed. Keys and typed text go in with `input`.
# - Each take is recorded on the device (`screenrecord`, 1080 × 2400, 20 Mbit/s: the text stays
#   sharp through two more encodes and the shaders' motion doesn't smear; at most 3 minutes),
#   pulled to $OUT/takes/<id>-<n>.raw.mp4, made a constant 30 frames a second with the last frame
#   held 2 seconds (<id>-<n>.mp4), and cut into a sheet of a frame a second (<id>-<n>.jpg). The
#   seconds of each step from the recording's start are in <id>-<n>.marks, to find the cuts.
#   Each take starts with 2 seconds of still screen. `keep` copies a take to $OUT/<id>.mp4.
# - Invented, neutral content only, as tools/site-captures.sh has it: big cities, the invented name
#   Maya, public feeds. A weather script's city is set before its first run.
# - The clock: a zone of whole hours (ZONE, or one where it is about 17:00 when `setup` runs); the
#   shader parts (11 to 13) pick one again, so their scenes are at dusk.
# - Android's animations are on (1×) while recording: the app's own motion, and moving shaders
#   move. `restore` sets them to 0, as the device tests and the captures want them.
# - Needs adb, ffmpeg and ImageMagick (magick) on the PATH. ffmpeg runs under a memory cap.
set -euo pipefail
cd "$(dirname "$0")/../.."

SERIAL=${SERIAL:-emulator-5554}
THEME=${THEME:-rose-pine}   # the app's theme, its dark version (the phone's dark mode is on)
APP=com.mtwabpin.kolahal.debug
MAIN=$(git rev-parse --path-format=absolute --git-common-dir)/..
OUT=${OUT:-$MAIN/build/video/recordings}
[ "$THEME" = rose-pine ] || OUT=$OUT/$THEME
TAKES=$OUT/takes
A=(adb -s "$SERIAL")
CAP=(systemd-run --user --scope -q -p MemoryMax=4G -p MemorySwapMax=1G)

# ---- helpers ----------------------------------------------------------------------------------

sh_() { "${A[@]}" shell "$@" < /dev/null; }
key() { sh_ input keyevent "$1"; }
log() { printf '%s\n' "$*" >&2; }

# Touches, as the touchscreen's own events, through the emulator's console (`adb emu event send`,
# about 8 ms a call). Its axes run 0 to 32767 over the screen.
TID=1
dx() { echo $(($1 * 32767 / 1079)); }
dy() { echo $(($1 * 32767 / 2399)); }
ev() { "${A[@]}" emu event send "$@" EV_SYN:0:0 > /dev/null; }
down() { ev EV_ABS:ABS_MT_SLOT:0 EV_ABS:ABS_MT_TRACKING_ID:$TID EV_ABS:ABS_MT_POSITION_X:"$(dx "$1")" EV_ABS:ABS_MT_POSITION_Y:"$(dy "$2")" EV_ABS:ABS_MT_PRESSURE:512; }
move() { ev EV_ABS:ABS_MT_POSITION_X:"$(dx "$1")" EV_ABS:ABS_MT_POSITION_Y:"$(dy "$2")"; }
up() { ev EV_ABS:ABS_MT_TRACKING_ID:-1; TID=$((TID % 60000 + 1)); }
# tap x y: a touch of 80 ms.
tap() { down "$1" "$2"; sleep 0.08; up; }
# hold x y [s]: a long-press, a touch that stays put (0.9 s).
hold() { down "$1" "$2"; sleep "${3:-0.9}"; up; }
# glide x1 y1 x2 y2 [s]: a touch already down moves in a straight line over s seconds (0.3), a step
# about every 16 ms.
glide() {
  local x1=$1 y1=$2 x2=$3 y2=$4 n i
  n=$(awk -v s="${5:-0.3}" 'BEGIN { n = int(s / 0.016); print n < 1 ? 1 : n }')
  for ((i = 1; i <= n; i++)); do move $((x1 + (x2 - x1) * i / n)) $((y1 + (y2 - y1) * i / n)); sleep 0.008; done
}
# circle x y r s: a touch down at x y that moves round a small circle of radius r for s seconds,
# then lifts: held, but neither a long-press (it moves) nor a swipe (it doesn't get anywhere).
circle() {
  local x=$1 y=$2 r=$3 n i pts
  n=$(awk -v s="$4" 'BEGIN { print int(s / 0.016) }')
  pts=$(awk -v x="$x" -v y="$y" -v r="$r" -v n="$n" 'BEGIN { for (i = 1; i <= n; i++) {
    a = 6.2832 * i / n * 2; printf "%d %d\n", x + r * sin(a), y - r + r * cos(a) } }')
  down "$x" "$y"
  while read -r i n; do move "$i" "$n"; sleep 0.008; done <<< "$pts"
  up
}
# drag x1 y1 x2 y2 [s] [hold]: down, a straight slide over s seconds (0.3), up; hold: seconds to
# stay put first (a long-press that then moves).
drag() { down "$1" "$2"; sleep "${6:-0}"; glide "$1" "$2" "$3" "$4" "${5:-0.3}"; sleep 0.05; up; }
# swipe_line x1 x2 y: a quick sideways swipe of a log line.
swipe_line() { drag "$1" "$3" "$2" "$3" 0.25; }

# esc <text>: text for `input text` (a space is %s; the shell's own characters escaped).
esc() {
  local s=${1// /%s}
  s=${s//\'/\\\'}; s=${s//\"/\\\"}; s=${s//(/\\(}; s=${s//)/\\)}; s=${s//&/\\&}
  s=${s//</\\<}; s=${s//>/\\>}; s=${s//;/\\;}; s=${s//\*/\\*}; s=${s//|/\\|}
  printf '%s' "$s"
}
# t "<command>" [wait]: types a command at once (off camera) and presses Enter.
t() { sh_ input text "$(esc "$1")"; sleep 0.6; key 66; sleep "${2:-2}"; }
# ty "<text>": types at once, without Enter.
ty() { sh_ input text "$(esc "$1")"; }
# type_ "<text>": types at a person's pace, a letter at a time, without Enter. Each adb call takes
# about 0.15 s itself, so a letter comes about every 0.35 s.
PACE=${PACE:-0.2}
type_() {
  local s=$1 i c
  for ((i = 0; i < ${#s}; i++)); do
    c=${s:i:1}
    if [ "$c" = " " ]; then sh_ input keyevent 62; else sh_ input text "$(esc "$c")"; fi
    sleep "$PACE"
  done
}
# say "<command>" [wait]: typed at a person's pace, a beat, then Enter, then a wait (2 s).
say() { type_ "$1"; sleep 0.6; key 66; sleep "${2:-2}"; }
# The keyboard's ˅ closes it (Back doesn't always); a tap on the prompt opens it.
kbdown() { sh_ input tap 163 2337; sleep 1; }
kbup() { sh_ input tap 300 2283; sleep 1.5; }
# kb_shown: whether the keyboard is up; kbdown_if: closed when it is.
kb_shown() { sh_ dumpsys input_method | grep -q 'mInputShown=true'; }
kbdown_if() { if kb_shown; then kbdown; fi; }
home() { key KEYCODE_HOME; sleep 2; }
# wait_cleared: the "Screen cleared · Undo" bar stays about 7 seconds.
wait_cleared() { sleep 7; }
# fresh_log: home with an empty log, an empty ↑ history (or the suggestions show commands typed
# while setting up or in an earlier take), and the keyboard down.
fresh_log() { home; kbup; t "clear history" 1; t clear 1; kbdown; wait_cleared; }
# edtype <file>: types a file into the focused editor, line by line (it doesn't auto-indent or
# close brackets, so the file goes in as it is).
edtype() {
  local line s
  while IFS= read -r line; do
    if [ -n "$line" ]; then s=${line// /%s}; sh_ "input text '$s'"; fi
    key 66
  done < "$1"
}
# at "<text>": the middle of the first element on screen whose text or description starts with <text>
# (from uiautomator's dump), as "x y"; empty when none. Off camera only: a dump takes a second.
at() {
  sh_ uiautomator dump /data/local/tmp/ui.xml > /dev/null 2>&1 || true
  sh_ cat /data/local/tmp/ui.xml | tr '>' '\n' | grep -F -e "text=\"$1" -e "content-desc=\"$1" | head -1 |
    sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' |
    awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }' || true
}
# shows "<text>": whether such an element is on screen.
shows() { [ -n "$(at "$1")" ]; }
# tap_at "<text>": a touch on it (where the dump's second of pause doesn't matter).
tap_at() { local p; p=$(at "$1"); [ -n "$p" ] || { log "Not on screen: $1"; return 1; }; tap $p; }

# ---- widgets: Add widget and Arrange widgets --------------------------------------------------

LOOKX_plain=140; LOOKX_tonal=340; LOOKX_bold=540; LOOKX_split=740; LOOKX_shape=940
SIZEX_1x1=122; SIZEX_2x1=289; SIZEX_2x2=456; SIZEX_4x1=621; SIZEX_4x2=791; SIZEX_4x4=959
# w_search <name>: on Arrange widgets, + and a search for name (the picker keeps the last search:
# ✕ clears it first).
w_search() { sh_ input tap 1006 212; sleep 2; sh_ input tap 975 621; sleep 0.5; sh_ input tap 540 621; sleep 0.8; ty "$1"; sleep 1.5; kbdown; }
# w_look <look>: the chosen card's look, then ✓.
w_look() { local v=LOOKX_$1; sleep 2.5; tap_at "${1^}" || sh_ input tap "${!v}" 1968; sleep 1; sh_ input tap 975 1764; sleep 1.5; }
# w_add <name> <look>: the first row the search finds, placed, in that look.
w_add() { w_search "$1"; sh_ input tap 540 953; sleep 1.5; w_look "$2"; }

# Off camera, in Arrange widgets opened fresh by `widgets` (each card is labelled with its name):
# w_choose <label>: the card chosen, when it is on the page (false when not).
w_choose() { shows "$1" || return 1; tap_at "$1"; sleep 2; }
# w_remove <label>: that card removed (its trash, then the confirmation), when it is there.
w_remove() {
  home; kbup; t widgets 3
  if w_choose "$1"; then tap_at Remove; sleep 1.5; sh_ input tap 791 1431; sleep 1.5; fi
  key KEYCODE_BACK; sleep 1; home
}
# widgets_reset: the page as setup leaves it plus part 9's Countdowns: no second weather card,
# World clocks in Bold at 2×2, and the gaps closed.
widgets_reset() {
  w_remove "Weather 2"
  kbup; t widgets 3
  if w_choose "World clocks"; then
    sh_ input tap $LOOKX_bold 1968; sleep 1; sh_ input tap $SIZEX_2x2 2173; sleep 1.5
    sh_ input tap 975 1763; sleep 1.5
  fi
  tap_at "Close gaps"; sleep 1.5
  key KEYCODE_BACK; sleep 1; home
}
# gboard_chip <on|off>: Gboard's "Show recently copied text and images in suggestions bar" (the
# emulator takes in the computer's clipboard; with it on, a chip takes the number row's place).
gboard_chip() {
  local now
  sh_ am start --activity-clear-task -a android.settings.INPUT_METHOD_SETTINGS > /dev/null; sleep 3
  sh_ input tap 270 705; sleep 3; sh_ input tap 300 1790; sleep 3
  now=$("${A[@]}" exec-out screencap -p | magick png:- -format '%[fx:p{915,640}.b-p{915,640}.r>0.1?1:0]' info:)
  if { [ "$1" = on ] && [ "$now" = 0 ]; } || { [ "$1" = off ] && [ "$now" = 1 ]; }; then sh_ input tap 970 640; sleep 1.5; fi
  home
}

# ---- the clock --------------------------------------------------------------------------------

# zone <name>: the emulator in that zone, automatic time zone off; a stop if its offset isn't whole
# hours (the world clocks show each city's difference from it).
zone() {
  local z
  sh_ cmd time_zone_detector set_auto_detection_enabled false
  sh_ service call alarm 3 s16 "$1" > /dev/null
  z=$(sh_ date +%z | tr -d '\r')
  [ "${z: -2}" = 00 ] || { log "The zone $1 is UTC$z: take one of whole hours."; exit 1; }
}
# dusk: $ZONE when set, or a zone of whole hours (none with summer time) where it is 17:00 to
# 17:59 now.
# (An associative array: an indexed one reads a negative index from its end.)
declare -A DUSK_ZONES=([0]=Atlantic/Reykjavik [1]=Africa/Lagos [2]=Africa/Johannesburg
  [3]=Africa/Nairobi [4]=Asia/Dubai [5]=Asia/Karachi [6]=Asia/Dhaka [7]=Asia/Bangkok
  [8]=Asia/Singapore [9]=Asia/Tokyo [10]=Australia/Brisbane [-3]=America/Sao_Paulo
  [-5]=America/Bogota [-6]=America/Costa_Rica [-7]=America/Phoenix [-10]=Pacific/Honolulu)
dusk() {
  if [ -n "${ZONE:-}" ]; then zone "$ZONE"; return; fi
  local h o
  h=$(date -u +%H); h=$((10#$h))
  o=$(((17 - h + 36) % 24 - 12))
  while [ -z "${DUSK_ZONES[$o]:-}" ]; do o=$((o + 1)); done
  zone "${DUSK_ZONES[$o]}"
}

# emu <console command>: the emulator's console (the charger, the battery).
emu() { "${A[@]}" emu "$@" > /dev/null; }
# animations <0|1>: Android's three animation scales.
animations() {
  sh_ settings put global window_animation_scale "$1"
  sh_ settings put global transition_animation_scale "$1"
  sh_ settings put global animator_duration_scale "$1"
}

# ---- recording --------------------------------------------------------------------------------

REC_PID=
REC=
# rec <id>: show taps on, the recorder started, 2 s of still screen. Steps are marked with `m`.
rec() {
  REC=$1
  mkdir -p "$TAKES"
  local n=1; while [ -e "$TAKES/$REC-$n.raw.mp4" ]; do n=$((n + 1)); done
  TAKE=$REC-$n
  animations 1   # the app's own motion, and moving shaders (setup works with them off)
  sh_ settings put system show_touches 1
  sh_ rm -f /sdcard/rec.mp4
  "${A[@]}" shell screenrecord --bit-rate 20000000 --time-limit 180 /sdcard/rec.mp4 < /dev/null &
  REC_PID=$!
  sleep 0.4
  REC_T0=$(date +%s.%N)
  : > "$TAKES/$TAKE.marks"
  sleep 2
}
# m "<what>": the step about to happen, with its second in the recording.
m() { awk -v a="$(date +%s.%N)" -v b="$REC_T0" -v w="$*" 'BEGIN { printf "%.2f\t%s\n", a - b, w }' >> "$TAKES/$TAKE.marks"; }
# stop: 2 s of still screen, the recorder stopped, the take pulled, made constant-rate, and a
# sheet of its frames.
stop() {
  m end; sleep 2
  sh_ pkill -INT screenrecord || true
  wait "$REC_PID" || true
  sleep 1
  "${A[@]}" pull /sdcard/rec.mp4 "$TAKES/$TAKE.raw.mp4" > /dev/null
  sh_ rm -f /sdcard/rec.mp4
  "${CAP[@]}" ffmpeg -v error -y -i "$TAKES/$TAKE.raw.mp4" \
    -vf "fps=30,tpad=stop_mode=clone:stop_duration=2" -c:v libx264 -preset medium -crf 14 \
    -pix_fmt yuv420p -movflags +faststart -an -threads 4 "$TAKES/$TAKE.mp4"
  sheet "$TAKES/$TAKE.mp4"
  log "$TAKES/$TAKE.mp4"
}
# sheet <video> [from] [to] [every]: its frames, one a second (or every), with their seconds, in a
# JPEG beside it (<video>.jpg, or -<from>.jpg for a part).
sheet() {
  local v=$1 from=${2:-0} to=${3:-999} every=${4:-1} d out
  d=$(mktemp -d -p "$TAKES")
  out=${v%.mp4}.jpg; [ "$from" = 0 ] || out=${v%.mp4}-$from.jpg
  "${CAP[@]}" ffmpeg -v error -ss "$from" -to "$to" -i "$v" -vf "fps=1/$every,scale=180:-1" -start_number 0 "$d/%04d.png"
  local f i=0 args=()
  for f in "$d"/*.png; do
    args+=(-label "$(awk -v a="$from" -v i="$i" -v e="$every" 'BEGIN { printf "%.1f", a + i * e }')" "$f"); i=$((i + 1))
  done
  "${CAP[@]}" magick montage -font "$(fc-match -f "%{file}" "DejaVu Sans")" "${args[@]}" -tile 10x -geometry +3+3 -pointsize 14 -background '#222' -fill white -quality 80 "$out"
  rm -rf "$d"
  log "$out"
}
# keep <id> [take]: the take (the newest by default) as the part's recording.
keep() {
  local n=${2:-}
  if [ -z "$n" ]; then n=1; while [ -e "$TAKES/$1-$((n + 1)).mp4" ]; do n=$((n + 1)); done; fi
  cp "$TAKES/$1-$n.mp4" "$OUT/$1.mp4"
  log "$OUT/$1.mp4 (take $n)"
}

# ---- the state --------------------------------------------------------------------------------

# The state of tools/site-captures.sh's setup, with what the video needs besides: instant launch
# off while setting up (typed commands go in whole), a wallpaper left as the emulator has it, the
# widgets of the video's page, the TTY style's own parts for part 14.
setup() {
  animations 0
  sh_ pm clear "$APP"
  sh_ cmd package set-home-activity "$APP/com.mtwabpin.kolahal.MainActivity"
  sh_ cmd uimode night yes
  dusk
  "${A[@]}" emu power ac off > /dev/null; "${A[@]}" emu power capacity 80 > /dev/null
  sh_ appops set "$APP" GET_USAGE_STATS allow
  sh_ appops set "$APP" SCHEDULE_EXACT_ALARM allow
  sh_ pm grant "$APP" android.permission.POST_NOTIFICATIONS
  gboard_chip off
  home; sleep 2
  sh_ input tap 980 147; sleep 2   # Skip, on the first start's tour
  local c
  # The header at the very top (no empty band where the status bar was), and the built-in command
  # maps off, so `ma` suggests the app Maps only.
  for c in "config set launch.instant off" "config set header.show on" \
    "config set screen.hide-bars status" "config set header.below-bar off" \
    "config set commands.off maps" "hide kolahal" "theme $THEME" "config set theme.always-dark on" \
    "config set selector.rail on" "config set suggestions.idle on" "config set log.tips off" \
    "config set log.fresh 0" \
    "config set tty.suggestions tty" "config set tty.selector tty" "config set tty.keybar tty" \
    "config set tty.quick-actions tty" "config set tty.menu tty" \
    "todo Buy coffee beans" "todo Book the dentist" "todo Renew the passport" "todo Water the plants" \
    "script install weather" "script install world-clocks" "script install forecast"; do t "$c" 1.5; done
  # The cities before the scripts' first runs, then the lines they wrote are cleared.
  for c in "script set weather city Tokyo" "script set forecast city Lisbon" \
    "script set world-clocks zones Asia/Tokyo Africa/Nairobi Europe/Lisbon" "status clear all" \
    "clear"; do t "$c" 1; done
  for c in "feed add https://www.nasa.gov/feeds/iotd-feed/" "rename kolahal debug = Kolahal" \
    "script install countdown" "countdown add 01-01 New Year" "script install charge-reminder" \
    "pin maps" "pin clock" "pin camera" "todo toggle #1" "status clear all"; do t "$c" 2; done
  t "alarm 7:00" 4   # the Clock app sets it; `restore` deletes it
  home; kbup
  # The API reference's Water example (scripting/api.mdx → Taps) and the demo script focus
  # (docs/release/video.org → The demo script), typed into the editor; ✕ saves.
  local tmp; tmp=$(mktemp -d)
  sed -n '/^This widget counts glasses of water/,/^```$/p' site/src/content/docs/scripting/api.mdx \
    | sed '1,5d;$d' > "$tmp/water.lua"
  sed -n '/^\*\* The demo script/,/^#+end_src/p' docs/release/video.org \
    | sed -n '/^#+begin_src lua/,/^#+end_src/p' | sed '1d;$d' > "$tmp/focus.lua"
  for c in water focus; do
    t "script new $c" 3
    sh_ input tap 540 900; sleep 0.5; sh_ input keycombination 113 29; sleep 0.5; key 67; sleep 0.5   # select all, delete
    edtype "$tmp/$c.lua"; kbdown
    sh_ input tap 1017 191; sleep 2; kbup
  done
  rm -rf "$tmp"
  t "script run charge-reminder" 2
  # Calendar, Files and Clock opened a few times, so the empty prompt offers them.
  for c in calendar files clock calendar files; do home; kbup; t "$c" 3; done
  # The widgets page: World clocks and the forecast side by side, then Todos with Water and
  # weather stacked beside it, so the two cards placed in parts 9 and 10 land under them, the
  # second weather card right under Tokyo's. Each in an expressive look (never Plain or Tonal).
  home; kbup; t "widget add" 3
  sh_ input tap 540 621; sleep 1; ty "World clocks"; sleep 1.5; kbdown; sh_ input tap 540 953; w_look bold
  w_add 3-day split
  w_add Todos split
  w_add Water shape
  w_add Weather bold
  sh_ input tap 75 212; sleep 2   # back home
  home; kbup; t clear 1; kbdown
}

# restore: the emulator as tools/site-captures.sh's restore leaves it: show taps off, the 7:00
# alarm deleted, the lock screen off again, the battery as the emulator starts (not charging, 100%),
# the stock launcher as home, light mode, Gboard's clipboard chip on, the automatic time zone,
# the permissions as they were, animations off (the device tests want them so), the app's data
# cleared. Part 13 leaves Kolahal's live wallpaper on; clearing the app's data puts Android's own
# back (checked on this emulator, Android 16). Should a wallpaper of Kolahal's still be on after it,
# APK=<the installed build's apk> installs the app again, which does; without, the run says so.
restore() {
  sh_ settings put system show_touches 0
  home
  sh_ am start -a android.intent.action.SHOW_ALARMS > /dev/null; sleep 3
  shows Delete || { sh_ input tap 963 380; sleep 2; }
  tap_at Delete || true; sleep 2; home
  sh_ locksettings set-disabled true > /dev/null
  emu power ac off; emu power capacity 100
  sh_ cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity
  sh_ cmd uimode night no
  gboard_chip on
  sh_ cmd time_zone_detector set_auto_detection_enabled true
  sh_ appops set "$APP" GET_USAGE_STATS default
  sh_ appops set "$APP" SCHEDULE_EXACT_ALARM default
  sh_ pm revoke "$APP" android.permission.POST_NOTIFICATIONS || true
  animations 0
  sh_ pm clear "$APP"   # the device tests fail on what the takes leave behind
  if sh_ dumpsys wallpaper | grep -q "mWallpaperComponent=.*kolahal"; then
    if [ -n "${APK:-}" ]; then
      "${A[@]}" uninstall "$APP"; sleep 3; "${A[@]}" install -r -t "$APK"
    else
      log "The wallpaper is still Kolahal's pond: install the app again (APK=<apk> $0 restore) to put Android's back."
    fi
  fi
}

# ---- the parts --------------------------------------------------------------------------------

# Each part: what it needs, made off camera (so it can be recorded again alone, after any other);
# then the take, its steps marked (`m`) for the cuts. The places measured once with `do` and
# `at` are written out, as a dump on camera is a second's pause.

# unpinned: weather's line not pinned yet, for the parts before 8 (which pins it).
unpinned() { home; kbup; t "script unpin weather" 1; kbdown; }
# home_lines: home with the keyboard down and two answers in the log (the weather, a conversion),
# the state the opening shows and part 1 starts from, so the cut between them doesn't show.
home_lines() { home; kbup; t "clear history" 1; t clear 1; t weather 5; t "= 5 km in mi" 2; kbdown; wait_cleared; }

# 00-opening: home, still: the header, two answers, the selector's letters on the edge.
p_00-opening() {
  unpinned
  home_lines
  rec 00-opening; m still; sleep 6; stop
}

# 01-typing: `ma` and Enter opens Maps; `clo` opens Clock without Enter. Instant launch is for
# apps and from the third letter in this part only (typed commands would otherwise run before their
# last letter, and `ma` would open Maps by itself). Maps' data is cleared first: it opens on its
# welcome screen, not on "Latest in the area", which shows strangers' names and photos.
p_01-typing() {
  unpinned
  sh_ pm clear com.google.android.apps.maps > /dev/null
  home; kbup; t "config set launch.instant apps" 1.5; t "config set launch.instant-letters 3" 1.5; kbdown
  home_lines
  rec 01-typing
  m "tap the log"; tap 540 700; sleep 2.2
  m "type ma"; type_ ma; sleep 4
  m enter; key 66; sleep 2.2
  m home; key KEYCODE_HOME; sleep 1.5
  m "tap the log"; tap 540 700; sleep 1.6
  m "type clo"; type_ clo; sleep 3
  m home; key KEYCODE_HOME; sleep 1.5
  stop
  kbup; t "config set launch.instant off" 1.5; t "config set launch.instant-letters 2" 1.5; kbdown
}

# 02-suggestions: the empty prompt (favorites and the most-used apps), `flash` finding torch, ✕,
# then `theme ` and the theme names. torch isn't run: the emulator has no flashlight.
p_02-suggestions() {
  unpinned
  home_lines
  rec 02-suggestions
  m "tap the log"; tap 540 700; sleep 4.5
  m "type flash"; type_ flash; sleep 4
  m "x"; tap 928 1470; sleep 1
  m "type theme"; type_ "theme "; sleep 4
  m "x"; tap 928 1470; sleep 0.8
  m "keyboard down"; kbdown; sleep 1
  stop
}

# 03-selector: the app selector under a thumb (its strip of letters at x 1068; a touch starts in
# its middle). Up and down the edge, off it onto the list, up to Calendar and lift; then again to F,
# onto Files and hold still until its row fills and Files opens.
p_03-selector() {
  unpinned
  home_lines
  rec 03-selector; sleep 1
  m "touch the edge"; down 1068 1550; sleep 0.3
  m "up"; glide 1068 1550 1068 1250 1.6; sleep 0.3
  m "down"; glide 1068 1250 1068 1750 1.8; sleep 0.3
  m "up to C"; glide 1068 1750 1068 1100 1.6; sleep 0.4
  m "onto the list"; glide 1068 1100 540 1100 0.5; sleep 0.3
  m "to Calendar"; glide 540 1100 540 834 0.6; sleep 0.4
  m "lift"; up; sleep 2.2
  m home; key KEYCODE_HOME; sleep 1.5
  m "touch the edge"; down 1068 1550; sleep 0.3
  m "up to F"; glide 1068 1550 1068 1300 1.4; sleep 0.4
  m "onto Files"; glide 1068 1300 540 1300 0.5
  m "hold"; sleep 2.2; up; sleep 1
  m home; key KEYCODE_HOME; sleep 1.5
  stop
}

# 04-log: a conversion and `date tokyo london`; then the lines: a tap on a command puts it back in
# the prompt, a swipe right copies, a swipe left deletes (and Undo), a long-press opens the line's
# actions; `fresh`, and the line that brings the earlier ones back. Places are with the keyboard
# down, from an empty log.
p_04-log() {
  unpinned
  fresh_log
  rec 04-log
  m "tap the log"; tap 540 700; sleep 1
  m "type = 5 km in mi"; say "= 5 km in mi" 2
  m "type date"; say "date tokyo london" 2
  m "keyboard down"; kbdown; sleep 0.6
  m "tap the command"; tap 300 1799; sleep 2.2
  m "x"; tap 923 2284; sleep 0.8
  m "swipe London right"; swipe_line 300 900 2190; sleep 2.4
  m "swipe Tokyo left"; swipe_line 800 150 2110; sleep 1.3
  m "undo"; tap 994 2173; sleep 1.6
  m "long-press the answer"; hold 300 1900; sleep 2.2
  m back; key KEYCODE_BACK; sleep 1
  m "tap the log"; tap 540 700; sleep 1
  m "type fresh"; say fresh 2
  m "earlier lines"; tap_at "▴" || true; sleep 2.5
  stop
}

# 05-scripts: `weather tokyo` and `forecast paris` (library scripts; the answers wait on the
# network), then weather.lua in the editor, which opens at its end: the keyboard closed (a drag on
# it would type), four flings to the top, a little down, ✕ (which saves: nothing was changed).
p_05-scripts() {
  unpinned
  fresh_log
  rec 05-scripts
  m "tap the log"; tap 540 700; sleep 1
  m "type weather"; say "weather tokyo" 3.4
  m "type forecast"; say "forecast paris" 3.6
  m "type script edit"; say "script edit weather" 2.2
  m "keyboard down"; kbdown; sleep 0.3
  m "to the top"; local i; for i in 1 2 3 4; do drag 540 700 540 2000 0.12; sleep 0.35; done; sleep 2.4
  m "scroll"; drag 540 1500 540 1150 0.9; sleep 2
  m close; tap 1017 191; sleep 2
  stop
}

# 06-permissions: the script manager, Add script → From the library, a search for night, Night Do
# Not Disturb's page (it runs daily, uses Do Not Disturb, no internet), Install, back to the
# manager, its sheet and its Do Not Disturb switch off and on: its Access group has that one switch
# and no Internet. night-dnd is removed first, so it can be installed again.
p_06-permissions() {
  unpinned
  home; kbup; t "script rm night-dnd" 1.5; kbdown
  fresh_log
  rec 06-permissions
  m "tap the log"; tap 540 700; sleep 0.8
  m "type scripts"; say scripts 2
  m "add script"; tap 853 2220; sleep 1.4
  m "from the library"; tap 540 1728; sleep 1.8
  m "search"; tap 540 640; sleep 0.8
  m "type night"; type_ night; sleep 0.6; key KEYCODE_BACK; sleep 1
  m "its page"; tap 759 945; sleep 3.2
  m install; tap 154 791; sleep 1.6
  m back; key KEYCODE_BACK; sleep 0.8; tap 73 211; sleep 1.6
  m "its sheet"; tap 540 1994; sleep 2.2
  m "switch off"; tap 945 1345; sleep 1.4
  m "switch on"; tap 945 1345; sleep 3.6
  stop
  key KEYCODE_BACK; sleep 1; home
}

# 07-scheduling: from where part 6 ends, Night Do Not Disturb's sheet: the clock beside Daily at
# (07:00, 23:00) and its time picker, Cancel; Back twice to home, `script every weather 30m`; the charger in
# (the header's battery charging), a wait that is cut, 62%, the charger out: charge-reminder's
# line. The battery starts at 50%, so it says +12%.
p_07-scheduling() {
  unpinned
  emu power ac off; emu power capacity 50
  home; kbup; t "script every weather off" 1; t "script install night-dnd" 1.5; t "script on night-dnd" 1.5
  kbdown
  fresh_log
  kbup; t scripts 3; tap_at "Night Do Not Disturb"; sleep 2.5
  rec 07-scheduling
  m "the clock"; tap 127 2074; sleep 2.6
  m cancel; tap_at Cancel; sleep 1.2
  m back; key KEYCODE_BACK; sleep 0.8; key KEYCODE_BACK; sleep 1.6   # Home doesn't close the sheet
  m "tap the log"; tap 540 700; sleep 1.2
  m "type script every"; say "script every weather 30m" 2.4
  m "keyboard down"; kbdown; sleep 1
  m "charger in"; emu power ac on; sleep 3.5
  m "wait"; sleep 25
  m "62%"; emu power capacity 62; sleep 3
  m "charger out"; emu power ac off; sleep 5
  stop
  emu power capacity 80
}

# 08-pinning: weather's line, long-pressed, Pin weather to the top: its status line under the
# header; then the demo script focus (`focus 2`) pinned the same way, with its bar; the pinned
# lines take turns, and a tap on the dots shows the next. After it, focus is stopped and unpinned;
# weather stays pinned for the parts after.
p_08-pinning() {
  home; kbup; t "focus stop" 1; t "script unpin focus" 1; t "script unpin weather" 1; kbdown
  fresh_log
  rec 08-pinning
  m "tap the log"; tap 540 700; sleep 0.8
  m "type weather"; say weather 3.6
  m "keyboard down"; kbdown; sleep 0.6
  m "long-press"; hold 300 2120; sleep 1.2
  m "pin weather"; tap 300 1293; sleep 3.6
  m "tap the log"; tap 540 700; sleep 0.8
  m "type focus"; say "focus 2" 1.6
  m "keyboard down"; kbdown; sleep 0.6
  m "long-press"; hold 200 2187; sleep 0.4
  m "pin focus"; tap_at "Pin focus"; sleep 4
  m "dots"; tap 1017 174; sleep 2.6
  m "dots"; tap 1017 174; sleep 2.4
  stop
  kbup; t "focus stop" 1; t "script unpin focus" 1; kbdown
}

# 09-widgets: a swipe sideways to the widgets page; two taps on the Water card (one more glass
# each); + and the picker, scrolled a little, Countdowns: it lands under Water in Plain, set to
# Bold and ✓ (the look is cut out), and back to the page. A Countdowns card from a take before
# is removed first.
p_09-widgets() {
  w_remove Countdowns
  fresh_log
  rec 09-widgets
  m "swipe"; drag 200 1000 900 1000 0.3; sleep 2.8
  m "water"; tap 277 1435; sleep 1.6
  m "water"; tap 277 1435; sleep 2.4
  m "plus"; tap 858 190; sleep 2
  m "scroll"; drag 540 1900 540 1300 0.6; sleep 1
  m "countdowns"; tap 400 1804; sleep 3.5
  m "bold"; tap 540 1968; sleep 1.2; tap 975 1763; sleep 1.4
  m "back"; tap 75 212; sleep 3.5
  stop
  home
}

# 10-widget-options: on the widgets page, a long-press on World clocks: Arrange widgets with it
# chosen; its five looks in turn, ending on Shape; size 4×2; ✓; + and a second Weather card, its
# Options sheet, city Paris, Save; then (cut out) Bold, ✓, back to the page with both cards.
p_10-widget-options() {
  widgets_reset
  fresh_log
  kbup; t "widget page" 3
  rec 10-widget-options
  m "long-press"; hold 277 515; sleep 1.8
  local l; for l in plain tonal bold split shape; do
    local v=LOOKX_$l; m "$l"; tap "${!v}" 1968; sleep 1.1
  done
  sleep 0.6
  m "4x2"; tap $SIZEX_4x2 2173; sleep 2.4
  m done; tap 975 1763; sleep 1.2
  m plus; tap 1006 212; sleep 2
  m scroll; drag 540 1900 540 1300 0.6; sleep 0.8
  m weather; tap_at "Weather"; sleep 3.5
  m city; tap 960 2210; sleep 1.5
  m "field"; tap 540 1230; sleep 0.5; sh_ input keycombination 113 29; sleep 0.3
  m "type Paris"; type_ Paris; sleep 1
  m save; tap 812 1083; sleep 2.4
  m back; key KEYCODE_BACK; sleep 1.5
  m bold; tap 540 1968; sleep 1.2; tap 975 1763; sleep 1.2
  m page; tap 75 212; sleep 3.5
  stop
  home
}

# 11-shaders: `settings`, Shaders, the list scrolled slowly down to Mountains, chosen; Back to home
# over the ridges; `shader aurora`; `shader rain` and a touch circling slowly, which the rain keeps
# away from (held still it is a long-press, which puts the last command back in the prompt;
# sideways it turns to the widgets page);
# `shader crt`. The zone is picked again (dusk), as the scenes follow the hour.
p_11-shaders() {
  dusk
  home; kbup; t "shader off" 1.5; kbdown
  fresh_log
  rec 11-shaders
  m "tap the log"; tap 540 700; sleep 0.8
  m "type settings"; say settings 2
  m shaders; tap 540 1162; sleep 2
  m scroll; local i; for i in 1 2 3; do drag 540 1900 540 900 1; sleep 0.3; done
  drag 540 1900 540 1400 0.7; sleep 0.8
  m mountains; tap_at Mountains; sleep 1.6
  m back; key KEYCODE_BACK; sleep 0.8; key KEYCODE_BACK; sleep 1
  m "keyboard down"; kbdown_if; sleep 2.5   # coming back from Settings opens it
  m "tap the log"; tap 540 700; sleep 0.8
  m "type aurora"; say "shader aurora" 1
  m "keyboard down"; kbdown; sleep 3.5
  m "tap the log"; tap 540 700; sleep 0.8
  m "type rain"; say "shader rain" 1
  m "keyboard down"; kbdown; sleep 1.5
  m "touch"; circle 540 1300 40 2.8; sleep 1.2
  m "tap the log"; tap 540 700; sleep 0.8
  m "type crt"; say "shader crt" 1
  m "keyboard down"; kbdown; sleep 3.5
  stop
}

# shaders_scroll: Settings → Shaders scrolled slowly down to Snowfall's row (Mountains is above it).
shaders_scroll() {
  local i; for i in 1 2 3; do drag 540 1900 540 900 1; sleep 0.3; done
  drag 540 1900 540 1400 0.7; sleep 0.3; drag 540 1900 540 1300 0.7; sleep 0.8
}
# info <name>: a touch on the ⓘ of a row of the Shaders list (found off camera: a second's pause).
info() { local p y; p=$(at "$1"); y=${p#* }; tap 942 $((y - 20)); }

# 12-parameters: Settings → Shaders, ⓘ on Snowfall: its preview, and its two sliders dragged
# (Amount of snow 1 → 2, Size of the flakes 1 → 1.5) as the preview follows; ⓘ on Dunes: Follow →
# Battery (at 100%: 7 camels), Use, home over the dunes; then the battery set to 30% from the
# emulator's console: 2 camels.
p_12-parameters() {
  emu power ac off; emu power capacity 100
  home; kbup; t "shader off" 1; t "shader reset snowfall" 1; t "shader reset dunes" 1; kbdown
  fresh_log
  rec 12-parameters
  m "tap the log"; tap 540 700; sleep 0.8
  m "type settings"; say settings 2
  m shaders; tap 540 1162; sleep 2
  m scroll; shaders_scroll
  m snowfall; info Snowfall; sleep 2.6
  m "amount"; drag 510 1447 944 1447 1.6; sleep 1.2
  m "size"; drag 510 1819 727 1819 1.2; sleep 1.8
  m back; key KEYCODE_BACK; sleep 1
  m dunes; info Dunes; sleep 2.6
  m follow; tap 254 1762; sleep 1.4
  m battery; tap 256 371; sleep 2
  m use; tap 540 2231; sleep 1.4
  m home; key KEYCODE_BACK; sleep 0.8; key KEYCODE_BACK; sleep 0.8; key KEYCODE_BACK; sleep 1
  kbdown_if; sleep 3.5
  m "30%"; emu power capacity 30; sleep 6
  stop
  emu power capacity 80
}

# 13-wallpaper: `wallpaper shader pond`: Android's preview; Set wallpaper → Home and lock screens;
# the power key twice: the lock screen over the moving pond (the emulator's lock screen is None,
# so it is turned on for this part only); a swipe up; `config set log.wallpaper 60`: the pond
# through the log. `restore` puts the emulator's own wallpaper back.
p_13-wallpaper() {
  dusk
  home; kbup; t "shader off" 1.5; t "config set log.wallpaper 0" 1.5; kbdown
  fresh_log
  sh_ locksettings set-disabled false
  rec 13-wallpaper
  m "tap the log"; tap 540 700; sleep 0.8
  m "type wallpaper"; say "wallpaper shader pond" 3.6
  m "set wallpaper"; tap 540 2245; sleep 1.6
  m "both screens"; tap 540 1415; sleep 3
  m power; key KEYCODE_POWER; sleep 1.6; key KEYCODE_POWER; sleep 4
  m unlock; drag 540 1900 540 700 0.3; sleep 2
  m "tap the log"; tap 540 700; sleep 0.8
  m "type config"; say "config set log.wallpaper 60" 1.4
  m "keyboard down"; kbdown; sleep 3.5
  stop
  sh_ locksettings set-disabled true
}

# 14-tty: `config set home.style tty` (the TTY parts' own keys are set by setup): home on the
# grid; `top`, a tap; `matrix`, a tap; `sl`, which ends by itself. Typing right after the style
# changes gets lost while home is redrawn: a moment, and the keyboard opened again. The log is
# opaque again first (part 13 leaves the pond showing through it).
p_14-tty() {
  home; kbup; t "config set log.wallpaper 0" 1.5; t "config set home.style gui" 2; kbdown
  fresh_log
  rec 14-tty
  m "tap the log"; tap 540 700; sleep 0.8
  m "type config"; say "config set home.style tty" 2.6
  m "tap the log"; tap 540 700; sleep 1
  m "type top"; say top 3.6
  m close; tap 540 1200; sleep 1.2
  m "tap the log"; tap 540 700; sleep 0.8
  m "type matrix"; say matrix 3.6
  m close; tap 540 1200; sleep 1.2
  m "tap the log"; tap 540 700; sleep 0.8
  m "type sl"; say sl 9
  stop
  home; kbup; t "config set home.style gui" 3; kbdown
}

# 15-closing: home, still, in the GUI style, as the opening shows it.
p_15-closing() {
  home_lines
  rec 15-closing; m still; sleep 7; stop
}

PARTS=(00-opening 01-typing 02-suggestions 03-selector 04-log 05-scripts 06-permissions 07-scheduling 08-pinning 09-widgets 10-widget-options 11-shaders 12-parameters 13-wallpaper 14-tty 15-closing)

# ---- running it -------------------------------------------------------------------------------

[ $# -gt 0 ] || { sed -n '2,32p' "$0"; exit 2; }
while [ $# -gt 0 ]; do
  f=$1; shift
  case $f in
    list) printf '%s\n' setup restore "${PARTS[@]}" ;;
    keep) keep "$1" "${2:-}"; exit ;;
    do) eval "$*"; exit ;;   # do '<helpers>': steps by hand, to measure a place or a wait
    all) setup; for p in "${PARTS[@]}"; do "p_$p"; keep "$p"; done; restore ;;
    setup | restore) "$f" ;;
    *) declare -F "p_$f" > /dev/null || { log "No part or step named $f (list: $0 list)"; exit 2; }; "p_$f" ;;
  esac
done
