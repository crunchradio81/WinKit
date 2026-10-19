#!/usr/bin/env bash
# Report tool availability only; DO NOT change Android SDK or any Mac file.
set -euo pipefail
for p in java kotlinc python3 cmake ninja git gradle adb sdkmanager; do
  if command -v "$p" >/dev/null 2>&1; then printf '%-12s %s\n' "$p" "$(command -v "$p")";
  else printf '%-12s %s\n' "$p" 'UNAVAILABLE'; fi
done
printf 'ANDROID_SDK_ROOT: %s\n' "${ANDROID_SDK_ROOT:-not set}"
printf 'ANDROID_HOME: %s\n' "${ANDROID_HOME:-not set}"
for root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
  if [ -n "$root" ] && [ -d "$root" ]; then
    for item in 'platforms/android-35' 'ndk/28.2.13676358' 'cmake/3.22.1'; do
      if [ -e "$root/$item" ]; then echo "  PRESENT $root/$item"; else echo "  MISSING $root/$item"; fi
    done
  fi
done
