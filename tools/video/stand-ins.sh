#!/usr/bin/env bash
# Makes a stand-in recording for every chapter of tools/video/video.json, from the still captures of
# the app in docs/release/graphics/raw/ that each chapter lists under "stills": each still with a
# slow zoom, one crossfading into the next. They have the size, frame rate and length of a real
# recording (1080 × 2400, 30 frames a second, the chapter's seconds plus the still lead-in at both
# ends), so the whole video can be put together and looked at before anything is recorded:
#
#   tools/video/stand-ins.sh
#   RECORDINGS=build/video/stand-ins tools/video/compose.sh full
#
# usage: tools/video/stand-ins.sh [folder]   (default: build/video/stand-ins/)
# Needs ffmpeg and Node.js. compose.sh does without them: a chapter with no recording gets a plain
# screen with its name. One ffmpeg at a time, each with VIDEO_THREADS threads (default 4) and, where
# systemd-run is there, a memory cap of VIDEO_MEMORY_MAX (default 6G), as compose.sh runs them.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
out=$(realpath -m "${1:-${VIDEO_DIR:-$root/build/video}/stand-ins}")
raw=$root/docs/release/graphics/raw
command -v ffmpeg >/dev/null || { echo "Needs ffmpeg on the PATH (nix shell nixpkgs#ffmpeg)." >&2; exit 1; }
command -v node >/dev/null || { echo "Needs Node.js on the PATH." >&2; exit 1; }
mkdir -p "$out"
threads=${VIDEO_THREADS:-4}
cap=()
command -v systemd-run >/dev/null &&
  cap=(systemd-run --user --scope -q -p "MemoryMax=${VIDEO_MEMORY_MAX:-6G}" -p MemorySwapMax=1G --)

# One line per chapter: the file name, its length in seconds, and its stills.
node -e '
  const d = require(process.argv[1]);
  for (const c of d.chapters)
    console.log([c.recording, 2 * d.recording.lead + c.full.seconds, ...c.stills].join(" "));
' "$root/tools/video/video.json" |
while read -r file seconds stills; do
  read -ra list <<<"$stills"
  n=${#list[@]}
  # Each still's share, so that with a 1-second crossfade between them they add up to $seconds.
  each=$(node -p "($seconds + ($n - 1)) / $n")
  frames=$(node -p "Math.ceil($each * 30)")
  args=()
  graph=""
  for i in "${!list[@]}"; do
    args+=(-i "$raw/${list[i]}.png")
    # A slow zoom of 4%, in on one still and out on the next, about the middle.
    if (( i % 2 )); then z="1.04-0.04*on/$frames"; else z="1+0.04*on/$frames"; fi
    graph+="[$i:v]scale=2160:4800,zoompan=z='$z':d=$frames:x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':s=1080x2400:fps=30,setsar=1,format=yuv420p,settb=AVTB[s$i];"
  done
  acc="[s0]"
  for ((i = 1; i < n; i++)); do
    offset=$(node -p "($each - 1) * $i")
    graph+="${acc}[s$i]xfade=transition=fade:duration=1:offset=$offset[x$i];"
    acc="[x$i]"
  done
  graph+="${acc}trim=duration=$seconds[v]"
  "${cap[@]}" ffmpeg -nostdin -hide_banner -loglevel error -y "${args[@]}" -filter_complex_threads "$threads" \
    -filter_complex "$graph" -map '[v]' -r 30 -c:v libx264 -preset veryfast -crf 16 -pix_fmt yuv420p -threads "$threads" "$out/$file"
  echo "$out/$file  ${seconds} s  ${list[*]}"
done
