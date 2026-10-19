#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo 'Checking offline modules first (requires kotlinc + C++17/CMake/Ninja)...'
bash "$ROOT/scripts/RUN_OFFLINE_TESTS.sh"
command -v gradle >/dev/null || { echo 'Missing Gradle 8.9 (or use Android Studio). No APK built.' >&2; exit 2; }
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK" ] && [ -d "$SDK/platforms/android-35" ] && [ -d "$SDK/ndk/28.2.13676358" ] || {
  echo 'Missing Android SDK platform 35 / NDK 28.2.13676358. No APK built.' >&2; exit 2;
}
cd "$ROOT/android"
gradle --no-daemon assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk
[ -s "$APK" ] || { echo 'Gradle returned but debug APK missing; failure' >&2; exit 2; }
sha256sum "$APK"
echo 'Built only the A1 import/probe scaffold APK. It is NOT an emulator/guest boot; test installation and JNI on real Android.'
