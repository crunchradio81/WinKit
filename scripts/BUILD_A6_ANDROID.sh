#!/usr/bin/env bash
# A6 Android ARM64 build: actual build attempt only on Linux host with SDK/NDK.
# Does NOT touch Mac disks; has no network permission at runtime; does NOT
# claim Android installation, BIOS guest boot, DOS/WFW or NetBEUI operation.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(uname -s)" = Linux && "$(uname -m)" = x86_64 ]] || {
  echo 'A6 compilation recipe requires Linux x86_64 build host' >&2; exit 2;
}
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to an installed Android SDK}"
[[ -x "$ANDROID_SDK_ROOT/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || {
  echo 'Missing Android NDK r28 toolchain (28.2.13676358)' >&2; exit 2;
}
for cmd in git gradle java cmake ninja autoconf automake libtoolize make python3 c++ kotlinc as ld strings sha256sum unzip; do
  command -v "$cmd" >/dev/null || { echo "Missing build requirement: $cmd" >&2; exit 2; }
done
# An old patched checkout must never be reused just because the A3 builder
# detected a header. Use a NEW clean checkout and retain the prior one.
UP="$ROOT/_external/Retro-Dosbox"
if [[ -f "$UP/frontend/retrodos_frontend.cpp" ]] &&
   grep -Fq 'winkit_image_lease.h' "$UP/frontend/retrodos_frontend.cpp" &&
   ! grep -Fq 'winkit_launch_snapshot.h' "$UP/frontend/retrodos_frontend.cpp"; then
  echo 'Old A2-A5 patched checkout. Move it aside manually and run against a clean pinned checkout.' >&2
  exit 2
fi
bash "$ROOT/scripts/RUN_A6_TESTS.sh"
# A3 script provides pinned checkout, overlay, SDL3+DOSBox-X NDK build, Gradle
# debug build, native symbols, APK Android manifest and native library checks.
# The source overlay imported here is A6, not A3.
bash "$ROOT/scripts/BUILD_A3_ANDROID.sh"
SO="$UP/android/app/src/main/jniLibs/arm64-v8a/libretrodos.so"
PREV="$ROOT/_build_outputs/WinKit311-Android-A3-debug-DEVICE-UNVERIFIED.apk"
[[ -s "$SO" && -s "$PREV" ]] || { echo 'Missing Android ARM64 core or APK' >&2; exit 2; }
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
strings "$SO" > "$TMP"
grep -Fq 'WinKit A6: launch profile mismatch' "$TMP" || {
  echo 'A6 actual launch snapshot gate is absent from the linked core' >&2; exit 2;
}
python3 - "$PREV" <<'PY'
from pathlib import Path
import sys, zipfile
p=Path(sys.argv[1]); assert p.stat().st_size > 0
with zipfile.ZipFile(p) as z:
    bad=z.testzip(); assert bad is None, f'Corrupt APK entry: {bad}'
    names=set(z.namelist())
    assert 'lib/arm64-v8a/libretrodos.so' in names
    assert 'lib/arm64-v8a/libSDL3.so' in names
    assert any(n.startswith('classes') and n.endswith('.dex') for n in names)
    core=z.read('lib/arm64-v8a/libretrodos.so')
    assert b'WinKit A6: launch profile mismatch' in core, 'APK contains stale core'
    assert b'WinKit 3.11 - YARDOMOB (OFFLINE)' in core
    assert b'libSDL3.so' in z.read('lib/arm64-v8a/libretrodos.so')
print('A6 APK integrity and embedded A6 native code: pass (NOT device verified)')
PY
OUT="$ROOT/_build_outputs/WinKit311-Android-A6-debug-DEVICE-UNVERIFIED.apk"
[[ ! -e "$OUT" ]] || { echo 'Will not overwrite previous A6 APK' >&2; exit 2; }
cp "$PREV" "$OUT"
sha256sum "$SO" "$OUT" > "$ROOT/_build_outputs/A6-BUILD-SHA256SUMS.txt"
echo "ARM64 APK PACKAGING PASSED: $OUT"
echo 'Guest boot, Android installation and NetBEUI remain untested.'
