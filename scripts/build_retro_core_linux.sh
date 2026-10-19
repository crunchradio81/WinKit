#!/usr/bin/env bash
# Builds third-party Android ARM64 engine in a separate worktree; not bundled in A1 ZIP.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ "$(uname -s)" = Linux ] && [ "$(uname -m)" = x86_64 ] || {
  echo 'Retro-DOS pinned build-core.sh hardcodes linux-x86_64 NDK toolchain; use Linux x86_64 or adapt upstream script on Mac.' >&2; exit 2;
}
[ -n "${ANDROID_SDK_ROOT:-}" ] || { echo 'ANDROID_SDK_ROOT required' >&2; exit 2; }
export ANDROID_ABI=arm64-v8a
export ANDROID_API=28
export ANDROID_NDK="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
[ -d "$ANDROID_NDK" ] || { echo "Missing Android NDK at $ANDROID_NDK" >&2; exit 2; }
bash "$ROOT/scripts/bootstrap_core.sh"
cd "$ROOT/_external/Retro-Dosbox"
./android/build-core.sh
[ -s android/app/src/main/jniLibs/arm64-v8a/libretrodos.so ] || { echo 'Core missing after build'; exit 2; }
file android/app/src/main/jniLibs/arm64-v8a/libretrodos.so
sha256sum android/app/src/main/jniLibs/arm64-v8a/libretrodos.so
printf '\nARM64 library exists (IF previous command succeeded). WinKit launcher integration and guest boot remain separate gates.\n'
