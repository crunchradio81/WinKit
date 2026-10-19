#!/usr/bin/env bash
# A8 actual Android build recipe, requires Linux SDK/NDK/Gradle/network.
# This script is NOT a substitute for on-device BIOS, DOS or Windows tests.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(uname -s)" = Linux && "$(uname -m)" = x86_64 ]] || {
  echo 'A8 Android cross-build requires Linux x86_64 host' >&2; exit 2;
}
: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT must point at an installed SDK}"
NDK="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
[[ -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || {
  echo 'A8: NDK 28.2.13676358 not installed' >&2; exit 2;
}
for name in git gradle java javac jar kotlinc cmake ninja autoconf automake libtoolize make zipinfo unzip python3 sha256sum strings; do
  command -v "$name" >/dev/null || { echo "A8: missing $name" >&2; exit 2; }
done
bash "$ROOT/scripts/RUN_A8_HOST_TESTS.sh"
# Stage/verify the exact pinned source *before* spending time compiling it.
# BUILD_A3_ANDROID recognizes this already-patched checkout and does not
# reapply the overlay. This fails closed on any A7/stale/mutated source.
bash "$ROOT/scripts/bootstrap_core.sh"
UP="$ROOT/_external/Retro-Dosbox"
if ! grep -Fq 'winkit_image_lease.h' "$UP/frontend/retrodos_frontend.cpp"; then
    python3 "$ROOT/scripts/apply_a2_overlay.py" --repo "$UP"
fi
python3 "$ROOT/scripts/verify_a8_upstream.py" --repo "$UP"
bash "$ROOT/scripts/BUILD_A3_ANDROID.sh"
python3 "$ROOT/scripts/verify_a8_upstream.py" --repo "$UP"
PREV="$ROOT/_build_outputs/WinKit311-Android-A3-debug-DEVICE-UNVERIFIED.apk"
JNI="$UP/android/app/src/main/jniLibs/arm64-v8a"
JAR="$UP/android/app/libs/SDL3.jar"
python3 "$ROOT/scripts/verify_a8_apk.py" --apk "$PREV" --jni-dir "$JNI" --sdl3-jar "$JAR"
OUT="$ROOT/_build_outputs/WinKit311-Android-A8-debug-DEVICE-UNVERIFIED.apk"
[[ ! -e "$OUT" ]] || { echo 'A8 destination already exists; never overwrite prior APK' >&2; exit 2; }
cp -- "$PREV" "$OUT"
sha256sum "$JNI/libretrodos.so" "$JNI/libSDL3.so" "$JNI/libc++_shared.so" "$OUT" > "$ROOT/_build_outputs/A8-BUILD-SHA256SUMS.txt"
printf 'A8 ARM64 BUILD+PACKAGE PASS (NOT DEVICE BOOT): %s\n' "$OUT"
