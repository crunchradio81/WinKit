#!/usr/bin/env bash
# Read-only environment report. Does not attempt network downloads/builds.
set -euo pipefail
printf 'A5 host: %s / %s\n' "$(uname -s)" "$(uname -m)"
for c in java javac gradle kotlin kotlinc python3 git cmake ninja autoconf automake libtoolize make c++ as ld sha256sum zipinfo; do
  if command -v "$c" >/dev/null; then printf 'AVAILABLE  %s\n' "$c";
  else printf 'MISSING    %s\n' "$c"; fi
done
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" ]]; then echo 'MISSING    ANDROID_SDK_ROOT/ANDROID_HOME'; exit 2; fi
for p in "$SDK/platforms/android-36/android.jar" "$SDK/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"; do
  if [[ -s "$p" ]]; then echo "AVAILABLE  $p"; else echo "MISSING    $p"; fi
done
if command -v gradle >/dev/null; then gradle --version | head -13; fi
printf 'This reports prerequisites ONLY; it cannot establish a verified Android build.\n'
