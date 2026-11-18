#!/usr/bin/env bash
# Draws pictures of the widgets to share (for example in the Discord chat), from the website's
# real card renders in site/src/assets/widgets (WidgetGalleryTest makes those):
#   widget-kinds.png      one card for each kind a script can pick, and media
#   library-widgets.png   the library scripts that have a widget, by the name they install by
#   builtin-widgets.png   the widgets Kolahal draws itself
# They go to share/widgets/ (git ignores it), or to the folder given as the first argument.
# Needs ImageMagick (magick) and the Adwaita Mono font.
# The counts in the titles are written here by hand: it warns when the library has another number.
set -euo pipefail

cd "$(dirname "$0")/.."
W=$PWD/site/src/assets/widgets
D=$(realpath -m "${1:-share/widgets}")
command -v magick >/dev/null || { echo "Needs ImageMagick (magick) on the PATH." >&2; exit 1; }
mkdir -p "$D"

# The library scripts with a widget, without the templates: the second picture shows 18.
library=$(grep -l '@widget' app/src/main/assets/library/* | grep -vc '/template-' || true)
[ "$library" = 18 ] || echo "Note: the library has $library scripts with a widget; library-widgets.png says 18." >&2

BG='#0b0d0b'; FG='#d7e3d4'; DIM='#8fa38b'; GAP=26
F=Adwaita-Mono; FB=Adwaita-Mono-Bold
t=$(mktemp -d); trap 'rm -rf "$t"' EXIT

# tile <png|blank:WxH> <label>: the card with its name above it.
tile() {
  local out; out=$(mktemp -p "$t" XXXXXX.png)
  if [[ $1 == blank:* ]]; then
    magick -size "${1#blank:}" xc:"$BG" -background "$BG" -gravity west -splice 0x52 "$out"
  else
    magick -background "$BG" -fill "$DIM" -font "$F" -pointsize 30 label:"$2" -gravity west -extent "$(magick identify -format %w "$W/$1")x52" \
      "$W/$1" -append "$out"
    # A wide card is two cells and a hairline: as wide as two narrow cards and the gap, so columns line up.
    local w h; w=$(magick identify -format %w "$out"); h=$(magick identify -format %h "$out")
    (( w > 1000 )) && magick "$out" -background "$BG" -gravity west -extent $((2 * 529 + GAP))x$h "$out"
  fi
  echo "$out"
}

# row <tiles...>: side by side, tops aligned.
row() { local out; out=$(mktemp -p "$t" XXXXXX.png); magick -background "$BG" "$@" -gravity north +smush ${GAP} "$out"; echo "$out"; }

# sheet <out> <title> <subtitle> <rows...>
sheet() {
  local out=$1 title=$2 sub=$3; shift 3
  magick -background "$BG" "$@" -gravity west -smush ${GAP} "$t/body.png"
  local w; w=$(magick identify -format %w "$t/body.png")
  magick -background "$BG" \
    \( -fill "$FG" -font "$FB" -pointsize 46 label:"$title" -gravity west -extent "${w}x70" \) \
    \( -fill "$DIM" -font "$F" -pointsize 28 label:"$sub" -gravity west -extent "${w}x60" \) \
    \( -size "${w}x24" xc:"$BG" \) "$t/body.png" -append -bordercolor "$BG" -border 48 -strip PNG24:"$out"
}

# 1. The kinds.
r1=$(row "$(tile example-steps-2x1.png stat)" "$(tile example-progress-2x1.png progress)" "$(tile example-countdown-2x1.png countdown)" "$(tile example-clock-2x1.png clock)")
r2=$(row "$(tile example-text-2x2.png text)" "$(tile example-chart-2x2.png chart)" "$(tile example-image-4x2.png image)")
r3=$(row "$(tile example-list-4x2.png list)" "$(tile example-agenda-4x2.png agenda)")
stack=$(mktemp -p "$t" XXXXXX.png)
magick -background "$BG" "$(tile example-buttons-4x1.png buttons)" "$(tile example-toggles-4x1.png toggles)" -gravity west -smush ${GAP} "$stack"
r4=$(row "$stack" "$(tile nowplaying-4x2.png 'media (Kolahal only)')")
sheet "$D/widget-kinds.png" "Widget kinds: 12" "A script picks one with @widget <kind> and fills it with widget.show { ... }" "$r1" "$r2" "$r3" "$r4"

# 2. The library scripts that make widgets.
r1=$(row "$(tile livescore-4x1.png 'cricket · football')" "$(tile weather-2x1.png weather)" "$(tile webnumber-2x1.png webnumber)")
r2=$(row "$(tile quicktoggles-4x1.png quick-toggles)" "$(tile countdown-date-2x1.png countdown)" "$(tile blank:529x264 '')")
r3=$(row "$(tile aqi-2x2.png aqi)" "$(tile comic-2x2.png xkcd)" "$(tile currency-2x2.png exchange)" "$(tile forecast-2x2.png forecast)")
r4=$(row "$(tile habit-2x2.png habit)" "$(tile quote-2x2.png quote)" "$(tile unlocks-2x2.png unlock-chart)" "$(tile worldclocks-2x2.png world-clocks)")
r5=$(row "$(tile crypto-4x2.png crypto)" "$(tile hn-4x2.png hn)")
r6=$(row "$(tile releases-4x2.png releases)" "$(tile feed-2x2.png headlines)" "$(tile blank:529x526 '')")
sheet "$D/library-widgets.png" "Library scripts with a widget: 18" "Each one is a script install <name> away, and its code is yours to change. Plus 4 templates to start your own." "$r1" "$r2" "$r3" "$r4" "$r5" "$r6"

# 3. The built-in widgets.
r1=$(row "$(tile battery-2x1.png battery)" "$(tile notes-2x1.png notes)" "$(tile shortcuts-4x1.png shortcuts)")
r2=$(row "$(tile todos-2x2.png todos)" "$(tile reminders-2x2.png reminders)" "$(tile agenda-4x2.png agenda)")
r3=$(row "$(tile nowplaying-4x2.png 'now playing')" "$(tile blank:1084x526 '')")
sheet "$D/builtin-widgets.png" "Built-in widgets: 7" "Kolahal draws these itself, from your todos, notes, reminders, calendar, battery and music." "$r1" "$r2" "$r3"

