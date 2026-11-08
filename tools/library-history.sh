#!/usr/bin/env bash
# Lists every version of each library script, as a short fingerprint, in
# app/src/main/assets/library-history.txt:
#   <file> <the first 16 hex digits of the SHA-256 of one version>
# With it, Kolahal knows a copy you never edited even when the text it installed wasn't recorded:
# such a copy follows the library when the app updates (ScriptLibrary.updateUnedited).
#
# Run it after changing a script in assets/library. LibraryHistoryTest fails until you do.
# It keeps the versions already listed and adds every committed version and the current one,
# so it also works in a shallow clone.
set -euo pipefail
cd "$(dirname "$0")/.."

lib=app/src/main/assets/library
out=app/src/main/assets/library-history.txt
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

fingerprint() { sha256sum | cut -c1-16; }

{
  [ -f "$out" ] && grep -v '^#' "$out" || true
  for f in "$lib"/*.lua "$lib"/*.js; do
    name=$(basename "$f")
    git log --format=%H -- "$f" | while read -r commit; do
      git show "$commit:$f" 2>/dev/null | fingerprint | sed "s/^/$name /"
    done
    fingerprint < "$f" | sed "s/^/$name /"
  done
} | grep -v '^$' | sort -u > "$tmp"

{
  echo "# Every version of each library script: <file> <first 16 hex digits of its SHA-256>."
  echo "# Made by tools/library-history.sh; a copy matching one of these is a copy you never edited."
  cat "$tmp"
} > "$out"
echo "$(grep -vc '^#' "$out") versions of $(ls "$lib"/*.lua "$lib"/*.js | wc -l) scripts in $out"
