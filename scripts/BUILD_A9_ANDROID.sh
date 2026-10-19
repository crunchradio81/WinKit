#!/usr/bin/env bash
# Linux Android ARM64 attempt. Host tests run first; no Mac/device is touched.
# A successful APK build does NOT prove BIOS, DOS/WFW or NetBEUI boots.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$ROOT/scripts/RUN_A9_HOST_TESTS.sh"
bash "$ROOT/scripts/BUILD_A8_ANDROID.sh"
PREV="$ROOT/_build_outputs/WinKit311-Android-A8-debug-DEVICE-UNVERIFIED.apk"
OUT="$ROOT/_build_outputs/WinKit311-Android-A9-debug-DEVICE-UNVERIFIED.apk"
[[ -s "$PREV" ]] || { echo 'A8 verified APK not present; A9 package blocked' >&2; exit 2; }
[[ ! -e "$OUT" ]] || { echo 'Refusing to overwrite prior A9 output' >&2; exit 2; }
cp -- "$PREV" "$OUT"
sha256sum "$OUT" > "$ROOT/_build_outputs/A9-BUILD-SHA256SUMS.txt"
echo "A9 APK package checks passed: $OUT (Android DEVICE BOOT STILL UNVERIFIED)"
