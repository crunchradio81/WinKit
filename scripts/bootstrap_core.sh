#!/usr/bin/env bash
# Clone exactly pinned Retro-DOS and SDL3 DOSBox-X submodule into THIS project's private workdir.
# Never touches ~/Developer/WinKit311, Mac disks, or installed Mac app.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_SHA=65e8c67ed4d8e5aacabc4b96337e8d47ac92d720
CORE_SHA=4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11
# Optional public mirror/fork for recovery, NEVER a different source revision.
# No token-bearing URLs, shell injection or credentials are accepted.
CORE_URL="$(python3 "$ROOT/scripts/a29_core_source.py" "${WINKIT_CORE_REPO:-CrownParkComputing/dosbox-x-sdl3}")"
DEST="$ROOT/_external/Retro-Dosbox"
mkdir -p "$ROOT/_external"
if [ ! -d "$DEST/.git" ]; then
  test ! -e "$DEST" || { echo 'Destination exists but is not the expected git repository; refusing' >&2; exit 2; }
  git clone --no-checkout https://github.com/CrownParkComputing/Retro-Dosbox.git "$DEST"
  git -C "$DEST" checkout --detach "$APP_SHA"
  # Git local config overrides .gitmodules without modifying the pinned parent.
  # The submodule update still MUST fetch the exact parent gitlink revision.
  git -C "$DEST" config submodule.core.url "$CORE_URL"
  git -C "$DEST" submodule update --init --recursive
fi
ACTUAL_APP="$(git -C "$DEST" rev-parse HEAD)"
ACTUAL_CORE="$(git -C "$DEST/core" rev-parse HEAD)"
[ "$ACTUAL_APP" = "$APP_SHA" ] || { echo "FAIL: Parent revision mismatch $ACTUAL_APP" >&2; exit 2; }
[ "$ACTUAL_CORE" = "$CORE_SHA" ] || { echo "FAIL: Core revision mismatch $ACTUAL_CORE" >&2; exit 2; }
[ -f "$DEST/core/include/retrodos_host.h" ] || { echo 'Missing public host ABI'; exit 2; }
echo 'PINNED CORE PRESENT (source only):'
printf 'Retro-DOS app: %s\nDOSBox-X core: %s\n' "$ACTUAL_APP" "$ACTUAL_CORE"
echo 'NOTE: published Android script has --disable-sdlnet: ETHNET not enabled in this build.'
