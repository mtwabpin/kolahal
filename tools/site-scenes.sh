#!/usr/bin/env bash
# Draws every still of the app's scenes that the project uses, from the shaders in
# app/src/main/assets/shaders/, with the renderer in tools/scenes/ and the theme's colors from
# site/src/data/themes.json:
#   site/src/assets/scenes/          the website's cover, cards, gallery and drawn phone
#   docs/release/graphics/scenes/    the store's screenshot frames (the others take the website's)
#   docs/discord/src/scenes/         the Discord server's banners and stickers
#
# usage: tools/site-scenes.sh [theme] [folder]
#   theme   a theme of site/src/data/themes.json (rose-pine, nord, …); without it, the export's default
#   folder  where the pictures go, each at its path in the repository. Without it: the repository
#           itself for the default theme, build/pictures/<theme>/ for another, so the committed
#           pictures stay as they are.
# ONLY=<path prefix> draws only the rows whose path starts with it (ONLY=docs/discord).
#
# Needs what tools/scenes/run.sh needs (Python with moderngl, pillow and numpy; Nix brings them), and
# ImageMagick. One render at a time: set GPU_LOCK to a file to hold it with flock around each one,
# where other work uses the GPU too. How every picture of the project is made: docs/pictures.org.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

command -v node >/dev/null || { echo "Needs Node.js on the PATH." >&2; exit 1; }
default=$(node -p 'require("./site/src/data/themes.json").default')
theme=${1:-$default}
node -e 'process.exit(require("./site/src/data/themes.json").themes[process.argv[1]] ? 0 : 1)' "$theme" ||
  { echo "No theme $theme in site/src/data/themes.json." >&2; exit 1; }
if [ -n "${2:-}" ]; then out=$(realpath -m "$2")
elif [ "$theme" = "$default" ]; then out=$root
else out=$root/build/pictures/$theme
fi
command -v magick >/dev/null || { echo "Needs ImageMagick (magick) on the PATH." >&2; exit 1; }

# One row for each picture:
#   shader     the file in app/src/main/assets/shaders/, without .agsl
#   size       width x height in pixels
#   hour       the hour of the day the scene shows (18 is dusk)
#   mode       dark or light: the theme's version, and the shader's own
#   params     the shader's @param values as name=value, comma-separated, or - for its defaults
#   format     jpg:<quality> or webp:<quality>
#   path       where it goes
# Every scene is drawn as a wallpaper (no page of text over it), on 23 November 2026 at 12 s.
SCENES=(
  # The website: wide for the cover (Hero.astro, Scenes.astro), tall for the drawn phone and the gallery.
  "mountains  2560x1240  18  light  birds=0  jpg:88   site/src/assets/scenes/mountains-wide.jpg"
  "mountains  2560x1240  18  dark   birds=0  jpg:88   site/src/assets/scenes/mountains-dark-wide.jpg"
  "pond       2560x1240  19  dark   -        jpg:88   site/src/assets/scenes/pond-wide.jpg"
  "pond       2560x1240  18  light  birds=0  jpg:88   site/src/assets/scenes/pond-light-wide.jpg"
  "dunes      2560x1240  17  light  -        jpg:88   site/src/assets/scenes/dunes-wide.jpg"
  "dunes      2560x1240  18  dark   -        jpg:88   site/src/assets/scenes/dunes-dark-wide.jpg"
  "mountains  1080x2340  18  light  -        webp:88  site/src/assets/scenes/mountains.webp"
  "mountains  1080x2340  18  dark   -        webp:88  site/src/assets/scenes/mountains-dark.webp"
  "pond       1080x2340  19  dark   -        webp:88  site/src/assets/scenes/pond.webp"
  "pond       1080x2340  18  light  -        webp:88  site/src/assets/scenes/pond-light.webp"
  "dunes      1080x2340  17  light  -        webp:88  site/src/assets/scenes/dunes.webp"
  "dunes      1080x2340  18  dark   -        webp:88  site/src/assets/scenes/dunes-dark.webp"
  "lanterns   1080x2340  18  dark   -        webp:88  site/src/assets/scenes/lanterns.webp"
  "candle     1080x2340  18  dark   -        webp:88  site/src/assets/scenes/candle.webp"
  # The store: the light pond behind screenshot.html's frames.
  "pond       2560x1240  18  light  -        jpg:86   docs/release/graphics/scenes/pond-light-wide.jpg"
  # Discord (tools/discord-graphics.sh): banner.html, stickers.html and the invite and profile backgrounds.
  "mountains  1920x1080  18  dark   -        jpg:90   docs/discord/src/scenes/mountains-dark.jpg"
  "mountains  1600x640   18  light  -        jpg:90   docs/discord/src/scenes/mountains-light.jpg"
  "pond       1920x1080  18  dark   -        jpg:90   docs/discord/src/scenes/pond-dark.jpg"
  "pond       1360x480   18  light  -        jpg:90   docs/discord/src/scenes/pond-light.jpg"
)

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

drawn=0
for row in "${SCENES[@]}"; do
  read -r shader size hour mode params format path <<<"$row"
  [[ $path == "${ONLY:-}"* ]] || continue
  args=(--size "$size" --hour "$hour" --wallpaper --theme "$theme")
  [ "$mode" = dark ] && args+=(--dark)
  if [ "$params" != - ]; then
    IFS=, read -ra sets <<<"$params"
    for kv in "${sets[@]}"; do args+=(--set "$kv"); done
  fi
  png=$tmp/scene.png
  lock=()
  [ -n "${GPU_LOCK:-}" ] && lock=(flock "$GPU_LOCK")
  "${lock[@]}" tools/scenes/run.sh "app/src/main/assets/shaders/$shader.agsl" "$png" "${args[@]}" >"$tmp/log" 2>&1 ||
    { cat "$tmp/log" >&2; echo "Could not draw: $row" >&2; exit 1; }
  mkdir -p "$(dirname "$out/$path")"
  case $format in
    jpg:*) magick "$png" -strip -quality "${format#jpg:}" -sampling-factor 4:4:4 "$out/$path" ;;
    webp:*) magick "$png" -strip -quality "${format#webp:}" -define webp:method=6 "$out/$path" ;;
    *) echo "Unknown format $format in: $row" >&2; exit 1 ;;
  esac
  echo "$out/$path  $size  $(( ($(stat -c %s "$out/$path") + 1023) / 1024 )) KB"
  drawn=$((drawn + 1))
done
[ "$drawn" -gt 0 ] || { echo "No scene's path starts with ${ONLY:-}." >&2; exit 1; }
