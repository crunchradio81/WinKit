#!/usr/bin/env bash
# Linux x86_64 cross-build for Android ARM64. Builds neither a Mac app nor any guest OS.
# ALL failures stop before labeling a build artifact as produced.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(uname -s)" = Linux && "$(uname -m)" = x86_64 ]] || {
  echo 'A3 recipe supports Linux x86_64 host only; target is Android ARM64' >&2; exit 2;
}
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to your installed Android SDK}"
export ANDROID_NDK="${ANDROID_NDK:-$ANDROID_SDK_ROOT/ndk/28.2.13676358}"
export ANDROID_ABI=arm64-v8a ANDROID_API=28
TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -x "$TOOLCHAIN/clang" && -x "$TOOLCHAIN/llvm-readelf" ]] || {
  echo "Missing Android NDK toolchain at $TOOLCHAIN" >&2; exit 2;
}
for cmd in git python3 gradle java cmake ninja autoconf automake libtoolize make unzip zipinfo sha256sum strings; do
  command -v "$cmd" >/dev/null || { echo "Missing build dependency: $cmd" >&2; exit 2; }
done
AAPT2="$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 2 -type f -name aapt2 2>/dev/null | sort -V | tail -1)"
[[ -x "$AAPT2" ]] || { echo 'Missing Android SDK build-tools aapt2' >&2; exit 2; }
[[ -f "$ANDROID_SDK_ROOT/platforms/android-36/android.jar" ]] || {
  echo 'Missing Android platform API 36'; exit 2;
}
# Test host-side source contracts first; never touch guest HDDs.
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
bash "$ROOT/scripts/RUN_A3_TARGETED_TESTS.sh"
bash "$ROOT/scripts/bootstrap_core.sh"
UP="$ROOT/_external/Retro-Dosbox"
if ! grep -Fq 'winkit_image_lease.h' "$UP/frontend/retrodos_frontend.cpp"; then
  python3 "$ROOT/scripts/apply_a2_overlay.py" --repo "$UP"
fi
# Mandatory source checks after overlay application.
grep -Fq 'winkit_lease::ImageLease lease' "$UP/frontend/retrodos_frontend.cpp"
grep -Fq 'WinKit 3.11 - YARDOMOB (OFFLINE)' "$UP/frontend/retrodos_frontend.cpp"
grep -Fq 'OfflineBootPolicy.allows' "$UP/android/app/src/main/java/com/crownparkcomputing/retrodos/winkit/OfflineBootConfig.kt"
grep -Fq 'applicationId = "org.winkit.android.a2"' "$UP/android/app/build.gradle.kts"
grep -Fq 'android:allowBackup="false"' "$UP/android/app/src/main/AndroidManifest.xml"
! grep -Fq 'android.permission.INTERNET' "$UP/android/app/src/main/AndroidManifest.xml"
# Upstream script compiles SDL3, DOSBox-X and the integrated native frontend.
(cd "$UP" && bash ./android/build-core.sh)
SO="$UP/android/app/src/main/jniLibs/arm64-v8a/libretrodos.so"
[[ -s "$SO" ]] || { echo 'Missing libretrodos.so' >&2; exit 2; }
"$TOOLCHAIN/llvm-readelf" -h "$SO" > "$TEMP/elf-header.txt"
grep -q 'AArch64' "$TEMP/elf-header.txt" || {
  echo 'Native binary does not contain AArch64 ELF' >&2; exit 2;
}
strings "$SO" > "$TEMP/native-strings.txt"
grep -Fq 'WinKit 3.11 - YARDOMOB (OFFLINE)' "$TEMP/native-strings.txt" || {
  echo 'A3 overlay not present in freshly linked libretrodos.so' >&2; exit 2;
}
(cd "$UP/android" && gradle --no-daemon assembleDebug)
APK="$UP/android/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo 'APK not generated' >&2; exit 2; }
zipinfo -1 "$APK" > "$TEMP/apk-contents.txt"
grep -Fxq 'lib/arm64-v8a/libretrodos.so' "$TEMP/apk-contents.txt" || {
  echo 'APK has no ARM64 DOSBox-X core' >&2; exit 2;
}
grep -Fxq 'lib/arm64-v8a/libSDL3.so' "$TEMP/apk-contents.txt" || {
  echo 'APK has no ARM64 SDL3' >&2; exit 2;
}
"$AAPT2" dump badging "$APK" > "$TEMP/apk-badging.txt"
grep -Fq "package: name='org.winkit.android.a2'" "$TEMP/apk-badging.txt" || {
  echo 'APK has wrong app ID' >&2; exit 2;
}
"$AAPT2" dump permissions "$APK" > "$TEMP/apk-permissions.txt"
if grep -Eq 'android.permission.(INTERNET|ACCESS_NETWORK_STATE)' "$TEMP/apk-permissions.txt"; then
  echo 'Unexpected Android network permission found in offline APK' >&2; exit 2;
fi
OUT="$ROOT/_build_outputs"
mkdir -p "$OUT"
DEST="$OUT/WinKit311-Android-A3-debug-DEVICE-UNVERIFIED.apk"
[[ ! -e "$DEST" ]] || { echo 'Refusing to overwrite prior A3 APK' >&2; exit 2; }
cp "$APK" "$DEST"
sha256sum "$SO" "$DEST" > "$OUT/A3-BUILD-SHA256SUMS.txt"
printf '\nBUILD + PACKAGE CHECKS PASS: %s\n' "$DEST"
echo 'This does NOT prove an Android installation, emulator runtime, DOS/WFW boot or network functionality.'
