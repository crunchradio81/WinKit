#!/usr/bin/env bash
# WinKit Android A2: source checkout + strict overlay + Android ARM64 compilation.
# Requires Linux x86_64 SDK 36, NDK r28.2, CMake/Ninja, Gradle 8.9, Java 17,
# autotools, SDL sources from GitHub. NOT run by ChatGPT in the source handoff.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
  echo 'This reproducibility path is validated for Linux x86_64 HOST builders (target Android ARM64).' >&2
  echo 'macOS NDK toolchain selection is patched, but Autotools/sed/nproc build is not validated there.' >&2
  exit 2
fi
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to installed Android SDK}"
export ANDROID_NDK="${ANDROID_NDK:-$ANDROID_SDK_ROOT/ndk/28.2.13676358}"
export ANDROID_ABI=arm64-v8a
export ANDROID_API=28
[[ -x "$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || {
  echo "Android NDK missing/invalid: $ANDROID_NDK" >&2; exit 2;
}
command -v gradle >/dev/null || { echo 'Install Gradle 8.9 and Java 17' >&2; exit 2; }
command -v ninja >/dev/null || { echo 'Ninja missing' >&2; exit 2; }
command -v git >/dev/null || { echo 'Git missing' >&2; exit 2; }
command -v cmake >/dev/null || { echo 'CMake missing' >&2; exit 2; }

bash "$ROOT/scripts/bootstrap_core.sh"
UP="$ROOT/_external/Retro-Dosbox"
if ! grep -q 'winkit_offline_gate.h' "$UP/frontend/retrodos_frontend.cpp"; then
  python3 "$ROOT/scripts/apply_a2_overlay.py" --repo "$UP"
fi
# Fail closed if machine injection, safety labels, permissions or application id missing.
grep -Fq 'WinKit 3.11 - YARDOMOB (OFFLINE)' "$UP/frontend/retrodos_frontend.cpp"
grep -Fq 'android:allowBackup="false"' "$UP/android/app/src/main/AndroidManifest.xml"
! grep -Fq 'android.permission.INTERNET' "$UP/android/app/src/main/AndroidManifest.xml"
grep -Fq 'applicationId = "org.winkit.android.a2"' "$UP/android/app/build.gradle.kts"

(cd "$UP" && ./android/build-core.sh)
SO="$UP/android/app/src/main/jniLibs/arm64-v8a/libretrodos.so"
[[ -s "$SO" ]] || { echo 'Build failed to produce ARM64 libretrodos.so' >&2; exit 2; }
"$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -h "$SO" | grep -q 'AArch64' || {
  echo 'libretrodos.so is not ARM64'; exit 2;
}
(cd "$UP/android" && gradle --no-daemon assembleDebug)
APK="$UP/android/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo 'No debug APK after Gradle completed'; exit 2; }
OUT="$ROOT/_build_outputs"
mkdir -p "$OUT"
cp -n "$APK" "$OUT/WinKit311-Android-A2-debug-unverified.apk" || {
  echo 'APK exists. Refusing to overwrite previous build in _build_outputs'; exit 2;
}
sha256sum "$SO" "$OUT/WinKit311-Android-A2-debug-unverified.apk" \
  > "$OUT/A2-BUILD-SHA256SUMS.txt"
printf 'ARM64 BUILD PRODUCED (NOT DEVICE VERIFIED): %s\n' "$OUT/WinKit311-Android-A2-debug-unverified.apk"
printf 'Next gate: inspect manifest and native library; install on Android; import private image/config; boot DOS then WFW OFFLINE.\n'
