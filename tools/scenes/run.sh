#!/usr/bin/env bash
# Runs the scene renderer (agsl.py, beside this script) with its arguments:
#   tools/scenes/run.sh app/src/main/assets/shaders/pond.agsl pond.png --size 1080x2340 --dark --wallpaper
#
# It needs Python 3 with moderngl, pillow and numpy, and OpenGL 3.3 through EGL: a GPU's driver, or
# Mesa's software renderer (llvmpipe) where there is none, which is slower and draws the same.
# - With Nix (the documented way): nothing to install, nix shell brings Python and the three packages.
# - Without Nix: a Python 3 that has them, for example
#     python3 -m venv ~/.venvs/kolahal-scenes && ~/.venvs/kolahal-scenes/bin/pip install moderngl pillow numpy
#   and then KOLAHAL_PYTHON=~/.venvs/kolahal-scenes/bin/python3 tools/scenes/run.sh …
#   Linux also needs EGL and Mesa (Debian and Ubuntu: libegl1 libgl1-mesa-dri).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
check='import moderngl, PIL, numpy'

if [ -n "${KOLAHAL_PYTHON:-}" ]; then
  exec "$KOLAHAL_PYTHON" "$here/agsl.py" "$@"
elif command -v nix >/dev/null; then
  exec nix shell --impure --expr \
    'with import <nixpkgs> {}; python3.withPackages (ps: [ ps.moderngl ps.pillow ps.numpy ])' \
    -c python3 "$here/agsl.py" "$@"
elif command -v python3 >/dev/null && python3 -c "$check" 2>/dev/null; then
  exec python3 "$here/agsl.py" "$@"
else
  echo "Needs Python 3 with moderngl, pillow and numpy: Nix, or KOLAHAL_PYTHON set to a Python that has them (see this script's head)." >&2
  exit 1
fi
