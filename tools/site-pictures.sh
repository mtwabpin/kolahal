#!/usr/bin/env bash
# Draws the website's pictures of widgets and log lines again, on an emulator, and puts each at its
# path under site/src/assets/ (the same file names, so the pages find them):
#   widgets/example-<name>.png  the scripting guide's widget examples (WidgetGalleryTest, docs-*)
#   widgets/<name>.png          the ready-made cards: the library's and Kolahal's own (real-*)
#   widgets/<look>-<name>.png   a card of WidgetGalleryTest's own in one look (gallery-looks), for
#                               the kinds and styles that have no example in the guide's code: the
#                               file's name says the look (bold, split, shape, poster, now) and the
#                               card (kind-gauge-2x2), and the run draws just those
#   parts/widgets-page.png      the widgets page of "Parts of the app" (gallery-page)
#   log/<name>.png, fmt/<name>.png  the scripting guide's log lines (LogGalleryTest)
# Only files the site already has are replaced; a new picture is added to the site by hand.
#
#   tools/site-pictures.sh                 # Kolahal's default theme (rose-pine), dark
#   tools/site-pictures.sh nord            # another theme, by its name in `theme`
#   tools/site-pictures.sh nord light      # its light version
#   LOOK=split tools/site-pictures.sh      # every card in one look (else each card's own:
#                                          # WidgetGalleryTest.siteLook, Bold, Split or Shape)
#   ZONE=Asia/Tokyo tools/site-pictures.sh  # the emulator's time zone for the run (Asia/Dhaka)
#
# The world clocks show each city's difference from the emulator's zone, so the run sets a zone of
# whole hours first, as tools/site-captures.sh does: automatic time zone off, ZONE set, and a stop
# if its offset isn't whole hours. When the run ends, also when it fails, automatic time zone is on
# again.
#
# Needs:
# - A running emulator, emulator-5554 unless SERIAL names another (never a phone: the run installs
#   the debug build and its tests). Its zone is set before the build, so start it first.
# - Android Studio's JDK: JAVA_HOME (for example ~/.apps/Android/Studio/jbr).
# - flock, when GPU_LOCK or GRADLE_LOCK name lock files that other jobs on this machine hold.
# The run builds the debug app (Gradle), installs it over the one there (its data stays) and runs
# the two test classes. Look at a few of the pictures before committing them.
set -euo pipefail
cd "$(dirname "$0")/.."

THEME=${1:-}
LIGHT=${2:-}
SERIAL=${SERIAL:-emulator-5554}
ZONE=${ZONE:-Asia/Dhaka}
APP=com.mtwabpin.kolahal.debug
ASSETS=site/src/assets
: "${JAVA_HOME:?Set JAVA_HOME to the JDK of Android Studio}"
sh_() { adb -s "$SERIAL" shell "$@" < /dev/null; }

out=$(mktemp -d)
trap 'sh_ cmd time_zone_detector set_auto_detection_enabled true; rm -rf "$out"' EXIT
sh_ cmd time_zone_detector set_auto_detection_enabled false
sh_ service call alarm 3 s16 "$ZONE" > /dev/null
z=$(sh_ date +%z | tr -d '\r')
[ "${z: -2}" = 00 ] || { echo "The zone $ZONE is UTC$z: take one of whole hours (ZONE=)." >&2; exit 1; }

args=(--arg "theme=${THEME:-rose-pine}")
[ "$LIGHT" = light ] && args+=(--arg light=true)
[ -n "${LOOK:-}" ] && args+=(--arg "look=$LOOK")

# The cards in a look that the site has (widgets/<look>-<name>-<size>.png): the looks and names
# for everyLookIsDrawnAndSaved, which draws each named card in each look.
classes=com.mtwabpin.kolahal.ui.widgets.WidgetGalleryTest#theGuidesExamplesAreDrawnAndSaved,com.mtwabpin.kolahal.ui.widgets.WidgetGalleryTest#thePageIsDrawnAndSaved,com.mtwabpin.kolahal.ui.home.LogGalleryTest
looks=() names=()
for f in "$ASSETS"/widgets/{bold,split,shape,poster,now}-*.png; do
  [ -f "$f" ] || continue
  b=$(basename "$f" .png); looks+=("${b%%-*}"); b=${b#*-}; names+=("${b%-*}")
done
if [ ${#names[@]} -gt 0 ]; then
  classes+=,com.mtwabpin.kolahal.ui.widgets.WidgetGalleryTest#everyLookIsDrawnAndSaved
  join() { printf '%s\n' "$@" | sort -u | paste -sd, -; }
  args+=(--arg "looks=$(join "${looks[@]}")" --arg "names=$(join "${names[@]}")")
fi

run=(env ANDROID_SERIAL="$SERIAL" tools/device-tests.sh --emulators 1 --keep --class "$classes" "${args[@]}")
[ -n "${GRADLE_LOCK:-}" ] && run=(flock "$GRADLE_LOCK" "${run[@]}")
[ -n "${GPU_LOCK:-}" ] && run=(flock "$GPU_LOCK" "${run[@]}")
"${run[@]}"

for d in gallery-docs gallery-page gallery-log gallery-fmt gallery-looks; do
  adb -s "$SERIAL" pull "/sdcard/Android/data/$APP/files/$d" "$out/" > /dev/null || [ "$d" = gallery-looks ]
done

n=0
put() { # put <drawn file> <site path>: only over a picture the site has.
  if [ -f "$1" ] && [ -f "$ASSETS/$2" ]; then cp "$1" "$ASSETS/$2"; n=$((n + 1)); fi
}
for f in "$ASSETS"/widgets/*.png; do
  b=$(basename "$f")
  case $b in
    example-*) put "$out/gallery-docs/docs-${b#example-}" "widgets/$b" ;;
    bold-*|split-*|shape-*|poster-*|now-*) put "$out/gallery-looks/$b" "widgets/$b" ;;
    *) put "$out/gallery-docs/real-$b" "widgets/$b" ;;
  esac
done
put "$out/gallery-page/widgets-page.png" parts/widgets-page.png
for f in "$ASSETS"/log/*.png; do b=$(basename "$f"); put "$out/gallery-log/log-$b" "log/$b"; done
for f in "$ASSETS"/fmt/*.png; do b=$(basename "$f"); put "$out/gallery-fmt/fmt-$b" "fmt/$b"; done
echo "Replaced $n pictures under $ASSETS. Check the captions that name a color or a look."
