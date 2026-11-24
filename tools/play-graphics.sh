#!/usr/bin/env bash
# Renders the Google Play store graphics from their sources in docs/release/graphics/:
#   icon.svg                                  -> icon.png                    512 × 512, 32-bit PNG
#   feature-graphic.html?v=<variant>          -> drafts/feature-<variant>.png 1024 × 500, no alpha
#   screenshot.html?shot=<draft> + raw/*.png  -> drafts/screenshots/<draft>.png 1080 × 1920, no alpha
# then copies the chosen ones to what Play gets: FEATURE below to feature-graphic.png, and the
# drafts in PLAY, in that order, to screenshots/<n>-<name>.png (Play takes 2 to 8).
# Git keeps raw/, drafts/, screenshots/ and feature-graphic.png: each run renders the drafts again.
#
# The variants and drafts are the keys of VARIANTS in feature-graphic.html and of SHOTS in
# screenshot.html. The captures in raw/ come from the emulator; how to take them again:
# docs/release/google-play.org → Graphics.
#
# The pages use the website's fonts from the site's packages, inside this repository
# (site/node_modules/@fontsource…): run `pnpm install --frozen-lockfile` in site/ first.
# Needs Google Chrome or Chromium, and ImageMagick.
set -euo pipefail

# The choice: the feature graphic's variant, and the screenshots for Play, in order.
FEATURE=a-mountains
PLAY=(01-home-pond 03-typing 04-selector 05-answers 09-widgets 12-script-switches 17-candle 21-tty-home)

cd "$(dirname "$0")/../docs/release/graphics"

chrome=$(command -v google-chrome-stable || command -v google-chrome || command -v chromium || command -v chromium-browser || true)
[ -n "$chrome" ] || { echo "Needs Google Chrome or Chromium on the PATH." >&2; exit 1; }
command -v magick >/dev/null || { echo "Needs ImageMagick (magick) on the PATH." >&2; exit 1; }
fonts=../../../site/node_modules/@fontsource-variable
[ -e "$fonts/bricolage-grotesque" ] && [ -e "$fonts/instrument-sans" ] || {
  echo "Needs the website's fonts: run 'pnpm install --frozen-lockfile' in site/." >&2; exit 1; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# render <page[?query]> <width> <height> <png>: a screenshot of the page in headless Chrome.
render() {
  "$chrome" --headless=new --disable-gpu --hide-scrollbars --no-first-run --no-default-browser-check \
    --user-data-dir="$tmp/profile" --force-device-scale-factor=1 --window-size="$2,$3" \
    --virtual-time-budget=3000 --allow-file-access-from-files \
    --screenshot="$4" "file://$PWD/$1" >/dev/null 2>&1
}

# check <png> <width>x<height> <max bytes>: Play refuses other sizes and bigger files.
check() {
  local size bytes
  size=$(magick identify -format '%wx%h' "$1")
  bytes=$(stat -c %s "$1")
  [ "$size" = "$2" ] || { echo "$1 is $size, not $2" >&2; exit 1; }
  [ "$bytes" -le "$3" ] || { echo "$1 is $bytes bytes, over $3" >&2; exit 1; }
  echo "$1  $size  $((bytes / 1024)) KB"
}

# keys <page> <object>: the keys of a JavaScript object in the page, one per line.
keys() {
  sed -n "/const $2 = {/,/^\t\t};/p" "$1" | grep -oP "^\t\t\t'\K[a-z0-9-]+(?=':)"
}

render icon.svg 512 512 "$tmp/icon.png"
magick "$tmp/icon.png" -strip PNG32:icon.png
check icon.png 512x512 1048576

mkdir -p drafts/screenshots
for v in $(keys feature-graphic.html VARIANTS); do
  render "feature-graphic.html?v=$v" 1024 500 "$tmp/feature.png"
  magick "$tmp/feature.png" -strip -alpha off PNG24:"drafts/feature-$v.png"
  check "drafts/feature-$v.png" 1024x500 15728640
done

for name in $(keys screenshot.html SHOTS); do
  render "screenshot.html?shot=$name" 1080 1920 "$tmp/$name.png"
  magick "$tmp/$name.png" -strip -alpha off PNG24:"drafts/screenshots/$name.png"
  check "drafts/screenshots/$name.png" 1080x1920 8388608
done

# What Play gets.
cp "drafts/feature-$FEATURE.png" feature-graphic.png
rm -rf screenshots
mkdir screenshots
n=0
for name in "${PLAY[@]}"; do
  n=$((n + 1))
  cp "drafts/screenshots/$name.png" "screenshots/$n-${name#[0-9][0-9]-}.png"
done
echo "feature-graphic.png: $FEATURE; screenshots/: ${PLAY[*]}"
