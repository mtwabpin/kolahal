#!/usr/bin/env bash
# Puts Kolahal's preview video together from the screen recordings and tools/video/video.json
# (the chapters, their cuts, captions, grounds and lengths): docs/release/video.org → Making it.
#
# usage: tools/video/compose.sh [full|short] [theme] [--4k] [--only phone|framed] [--check]
#   full | short    the cut: all 16 chapters (281 s) or the short one (109 s); full without it
#   theme           the app theme the recordings show (default rose-pine): another theme reads
#                   build/video/recordings/<theme>/ and writes into build/video/<theme>/
#   --4k            3840 × 2160 instead of 1920 × 1080 (for the whole picture)
#   --only phone    the recordings alone, cut and joined, upright at 1080 × 2400, in an MP4
#   --only framed   the phone in its frame with round corners and its shadow, on a transparent
#                   ground, in a WebM (VP9 with alpha)
#   --chapter <id>  make one chapter's pieces only (to look at a recording, or to measure)
#   --check         check the data file and write the captions (SRT) only
#
# Memory: every ffmpeg and Chrome runs alone, one after the other, each on one chapter at most,
# and where systemd-run is there, in a scope with a memory cap, so a job that grows is stopped by
# itself and nothing else on the computer is: VIDEO_MEMORY_MAX (default 6G) is the cap,
# VIDEO_THREADS (default 4) the threads of each job. The run prints the largest a job used.
#
# Writes build/video/kolahal-<cut>[-2160p|-phone|-framed].<mp4|webm> and kolahal-<cut>.srt, and its
# pieces under build/video/work/ (kept, so a second run only redoes what changed).
# The recordings: build/video/recordings/<chapter id>.mp4 (or .webm); RECORDINGS=<folder> reads
# them from another folder (tools/video/stand-ins.sh makes stand-ins from the store's captures).
# A recording that is missing becomes a plain screen with the chapter's name, and the run says so.
# VIDEO_DIR=<folder> puts everything in another folder than build/video/.
#
# Needs ffmpeg with libx264 and libvpx (nix shell nixpkgs#ffmpeg), Node.js, Google Chrome or
# Chromium, and the website's fonts: run 'pnpm install --frozen-lockfile' in site/ first.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
command -v node >/dev/null || { echo "Needs Node.js on the PATH." >&2; exit 1; }
if [[ " $* " != *" --check "* ]]; then
  command -v ffmpeg >/dev/null || { echo "Needs ffmpeg on the PATH (nix shell nixpkgs#ffmpeg)." >&2; exit 1; }
  CHROME=${CHROME:-$(command -v google-chrome-stable || command -v google-chrome || command -v chromium || command -v chromium-browser || true)}
  [ -n "$CHROME" ] || { echo "Needs Google Chrome or Chromium on the PATH." >&2; exit 1; }
  export CHROME
  fonts=$root/site/node_modules
  [ -e "$fonts/@fontsource-variable/bricolage-grotesque" ] && [ -e "$fonts/@fontsource-variable/instrument-sans" ] &&
    [ -e "$fonts/@fontsource/jetbrains-mono" ] || {
    echo "Needs the website's fonts: run 'pnpm install --frozen-lockfile' in site/." >&2; exit 1; }
fi
exec node "$root/tools/video/compose.mjs" "$@"
