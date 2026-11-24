#!/usr/bin/env bash
# Renders the Discord server's pictures from their sources in docs/discord/src/:
#   server-icon.svg                          -> server-icon.png          512 × 512
#   scenes/mountains-dark.jpg + banner.html  -> server-banner.png        960 × 540
#   scenes/pond-dark.jpg                     -> invite-background.png    1920 × 1080
#   scenes/pond-light.jpg                    -> profile-banner.png       680 × 240
#   banner.html?kind=event-cover             -> event-cover.png          800 × 320, with the app's version
#   emoji/<name>.svg                         -> emoji/<name>.png         128 × 128, clear background
#   roles/<name>.svg                         -> roles/<name>.png         64 × 64, clear background
#   stickers.html?sticker=<name>             -> stickers/<name>.png      320 × 320, clear background
# What each is for and where it is uploaded: docs/discord/README.org.
#
# usage: tools/discord-graphics.sh [theme] [folder]
#   theme   a theme of site/src/data/themes.json (rose-pine, nord, …); without it, the export's default.
#           The sources have {{…}} where a color goes (tools/fill-theme.mjs fills them in).
#   folder  where the pictures go. Without it: docs/discord/ for the default theme, whose scenes are
#           the committed ones in src/scenes/; build/pictures/<theme>/discord/ for another, whose
#           scenes tools/site-scenes.sh draws first (it needs what that script needs).
#
# Needs Node.js, Google Chrome or Chromium, ImageMagick, and the website's packages (pnpm install in
# site/) for its type.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

command -v node >/dev/null || { echo "Needs Node.js on the PATH." >&2; exit 1; }
chrome=$(command -v google-chrome-stable || command -v google-chrome || command -v chromium || command -v chromium-browser || true)
[ -n "$chrome" ] || { echo "Needs Google Chrome or Chromium on the PATH." >&2; exit 1; }
command -v magick >/dev/null || { echo "Needs ImageMagick (magick) on the PATH." >&2; exit 1; }
[ -d "$root/site/node_modules/@fontsource-variable/bricolage-grotesque" ] ||
  { echo "Needs the website's packages: run pnpm install --frozen-lockfile in site/." >&2; exit 1; }

default=$(node -p 'require("./site/src/data/themes.json").default')
theme=${1:-$default}
if [ -n "${2:-}" ]; then out=$(realpath -m "$2")
elif [ "$theme" = "$default" ]; then out=$root/docs/discord
else out=$root/build/pictures/$theme/discord
fi

version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
[ -n "$version" ] || { echo "No versionName in app/build.gradle.kts." >&2; exit 1; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# The sources with the theme's colors, in a folder of their own; the pages' type from the repository.
src=$tmp/src
node tools/fill-theme.mjs "$theme" docs/discord/src "$src"
sed -i "s|'\.\./\.\./\.\./|'file://$root/|g" "$src"/*.html
if [ "$theme" != "$default" ]; then
  ONLY=docs/discord/src/scenes tools/site-scenes.sh "$theme" "$tmp/scenes" >/dev/null
  cp "$tmp/scenes/docs/discord/src/scenes/"*.jpg "$src/scenes/"
fi
mkdir -p "$out/emoji" "$out/roles" "$out/stickers"
cd "$out"

# render <page[?query]> <width> <height> <png>: a screenshot of the page in headless Chrome, with a
# clear background where the page draws nothing.
render() {
  "$chrome" --headless=new --disable-gpu --hide-scrollbars --no-first-run --no-default-browser-check \
    --user-data-dir="$tmp/profile" --force-device-scale-factor=1 --window-size="$2,$3" \
    --default-background-color=00000000 --virtual-time-budget=3000 --allow-file-access-from-files \
    --screenshot="$4" "file://$src/$1" >/dev/null 2>&1
}

# check <png> <width>x<height> <max bytes>: Discord refuses bigger files.
check() {
  local size bytes
  size=$(magick identify -format '%wx%h' "$1")
  bytes=$(stat -c %s "$1")
  [ "$size" = "$2" ] || { echo "$1 is $size, not $2" >&2; exit 1; }
  [ "$bytes" -le "$3" ] || { echo "$1 is $bytes bytes, over $3" >&2; exit 1; }
  echo "$1  $size  $(( (bytes + 1023) / 1024 )) KB"
}

render server-icon.svg 512 512 "$tmp/icon.png"
magick "$tmp/icon.png" -strip -alpha off PNG24:server-icon.png
check server-icon.png 512x512 10485760

render "banner.html?kind=server-banner" 960 540 "$tmp/banner.png"
magick "$tmp/banner.png" -strip -alpha off PNG24:server-banner.png
check server-banner.png 960x540 10485760

magick "$src/scenes/pond-dark.jpg" -strip -resize '1920x1080!' -alpha off PNG24:invite-background.png
check invite-background.png 1920x1080 10485760

magick "$src/scenes/pond-light.jpg" -strip -resize '680x240!' -alpha off PNG24:profile-banner.png
check profile-banner.png 680x240 10485760

render "banner.html?kind=event-cover&version=$version" 800 320 "$tmp/event.png"
magick "$tmp/event.png" -strip -alpha off PNG24:event-cover.png
check event-cover.png 800x320 10485760

for svg in "$src"/emoji/*.svg; do
  name=$(basename "$svg" .svg)
  render "emoji/$name.svg" 128 128 "$tmp/$name.png"
  magick "$tmp/$name.png" -strip PNG32:"emoji/$name.png"
  check "emoji/$name.png" 128x128 262144
done

for svg in "$src"/roles/*.svg; do
  name=$(basename "$svg" .svg)
  render "roles/$name.svg" 64 64 "$tmp/role-$name.png"
  magick "$tmp/role-$name.png" -strip PNG32:"roles/$name.png"
  check "roles/$name.png" 64x64 262144
done

for name in $(sed -n 's/.*<div id="\([a-z-]*\)" class="sticker".*/\1/p' "$src/stickers.html"); do
  render "stickers.html?sticker=$name" 320 320 "$tmp/sticker-$name.png"
  magick "$tmp/sticker-$name.png" -strip PNG32:"stickers/$name.png"
  check "stickers/$name.png" 320x320 524288
done
