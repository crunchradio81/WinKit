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
# A20: reject native APKs that do not contain the core-entry probes.
# Provenance gate only; presence does not prove runtime execution.
python3 - "$PREV" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    core = apk.read("lib/arm64-v8a/libretrodos.so")
for marker in (
    b"WinKit A20 stage 20A: DOSBox-X wrapper entered",
    b"WinKit A18 stage 21: engine main entered",
):
    if marker not in core:
        raise SystemExit("A20 APK REJECTED: required native probe absent: " + marker.decode())
print("A20 ARM64 native core-entry probes present; device boot remains unverified")
PY
cp -n -- "$PREV" "$DEST"
sha256sum "$DEST" > "$ROOT/_build_outputs/A10-BUILD-SHA256SUMS.txt"
printf 'A10 Android ARM64 compiled/package-checked: %s\n' "$DEST"
echo 'Android hardware boot, BIOS display, MS-DOS/WFW and NetBEUI remain UNVERIFIED.'
