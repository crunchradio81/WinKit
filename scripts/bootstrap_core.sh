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
VENDOR_DIR="$ROOT/vendor/dosbox-x-sdl3"
CORE_HISTORY="${WINKIT_CORE_HISTORY_GIT:-}"
if [[ -e "$VENDOR_DIR" || -L "$VENDOR_DIR" ]]; then
  [[ -d "$VENDOR_DIR" && ! -L "$VENDOR_DIR" &&
     -f "$VENDOR_DIR/include/retrodos_host.h" ]] || {
    echo 'Unusable vendored DOSBox-X source folder; refusing remote fallback.' >&2; exit 2;
  }
  [[ -n "$CORE_HISTORY" && -d "$CORE_HISTORY/.git" ]] || {
    echo 'Vendored source requires a full WinKit Git checkout in WINKIT_CORE_HISTORY_GIT.' >&2; exit 2;
  }
  VENDORED_TREE="$(git -C "$CORE_HISTORY" rev-parse "HEAD:vendor/dosbox-x-sdl3")"
  PINNED_TREE="$(git -C "$CORE_HISTORY" rev-parse "$CORE_SHA^{tree}")"
  [[ "$VENDORED_TREE" = "$PINNED_TREE" ]] || {
    echo 'The vendor snapshot differs from the pinned SDL3 source tree.' >&2; exit 2;
  }
  echo 'A29: pinned SDL3 source is owned by this WinKit checkout; no missing core remote needed.'
fi
mkdir -p "$ROOT/_external"
if [ ! -d "$DEST/.git" ]; then
  test ! -e "$DEST" || { echo 'Destination exists but is not the expected git repository; refusing' >&2; exit 2; }
  git clone --no-checkout https://github.com/CrownParkComputing/Retro-Dosbox.git "$DEST"
  git -C "$DEST" checkout --detach "$APP_SHA"
  # Git local config overrides .gitmodules without modifying the pinned parent.
  # The submodule update still MUST fetch the exact parent gitlink revision.
  if [[ -e "$VENDOR_DIR" ]]; then
    # A non-squashed Git subtree retains the exact original commit as WinKit
    # ancestry. Fetch that commit from our own full Git checkout, not the 404.
    git -C "$DEST" config submodule.core.url "$CORE_HISTORY"
    git -C "$DEST" -c protocol.file.allow=always submodule update --init --recursive
  else
    git -C "$DEST" config submodule.core.url "$CORE_URL"
    git -C "$DEST" submodule update --init --recursive
  fi
fi
ACTUAL_APP="$(git -C "$DEST" rev-parse HEAD)"
ACTUAL_CORE="$(git -C "$DEST/core" rev-parse HEAD)"
[ "$ACTUAL_APP" = "$APP_SHA" ] || { echo "FAIL: Parent revision mismatch $ACTUAL_APP" >&2; exit 2; }
[ "$ACTUAL_CORE" = "$CORE_SHA" ] || { echo "FAIL: Core revision mismatch $ACTUAL_CORE" >&2; exit 2; }
[ -f "$DEST/core/include/retrodos_host.h" ] || { echo 'Missing public host ABI'; exit 2; }
echo 'PINNED CORE PRESENT (source only):'
printf 'Retro-DOS app: %s\nDOSBox-X core: %s\n' "$ACTUAL_APP" "$ACTUAL_CORE"
echo 'NOTE: published Android script has --disable-sdlnet: ETHNET not enabled in this build.'
