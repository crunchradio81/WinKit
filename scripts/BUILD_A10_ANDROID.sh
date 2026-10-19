#!/usr/bin/env bash
# Full Linux x86_64 + Android SDK/NDK build attempt, never a device-boot claim.
# Real building is NOT performed by host source tests.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
A10_SKIP_INHERITED=1 bash "$ROOT/scripts/RUN_A10_HOST_TESTS.sh"
bash "$ROOT/scripts/BUILD_A9_ANDROID.sh"
PREV="$ROOT/_build_outputs/WinKit311-Android-A9-debug-DEVICE-UNVERIFIED.apk"
DEST="$ROOT/_build_outputs/WinKit311-Android-A10-debug-DEVICE-UNVERIFIED.apk"
[[ -s "$PREV" ]] || { echo 'A9 checked APK missing; refusing A10 promotion' >&2; exit 2; }
[[ ! -e "$DEST" ]] || { echo 'Refusing to overwrite an existing A10 artifact' >&2; exit 2; }
cp -n -- "$PREV" "$DEST"
sha256sum "$DEST" > "$ROOT/_build_outputs/A10-BUILD-SHA256SUMS.txt"
printf 'A10 Android ARM64 compiled/package-checked: %s\n' "$DEST"
echo 'Android hardware boot, BIOS display, MS-DOS/WFW and NetBEUI remain UNVERIFIED.'
