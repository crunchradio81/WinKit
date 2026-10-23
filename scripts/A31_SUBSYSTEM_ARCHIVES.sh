#!/usr/bin/env bash
# Compile upstream boot-critical DOSBox-X static archives using the Android NDK.
# Archive success is not an emulator link, APK or runtime boot.
set -euo pipefail
test -n "$A31_ROOT" && test -n "$ANDROID_HOME" && test -n "$ANDROID_NDK_REV"
ROOT="$A31_ROOT/dosbox-source"
NDK="$ANDROID_HOME/ndk/$ANDROID_NDK_REV/toolchains/llvm/prebuilt/linux-x86_64/bin"
report="$A31_ROOT/subsystem-archives.txt"
: > "$report"
passed=0
failed=0
for part in ints dos fpu hardware; do
  lib="lib$part.a"
  if [ "$part" = "ints" ]; then lib="libints.a"; fi
  dir="$ROOT/src/$part"
  log="$A31_ROOT/archive-$part.log"
  if make -C "$dir" -j 3 "$lib" >"$log" 2>&1; then
    archive="$dir/$lib"
    if test -s "$archive"; then
      members="$("$NDK/llvm-ar" t "$archive" | wc -l | tr -d ' ')"
      echo "PASS $part $lib members=$members" | tee -a "$report"
      passed=$((passed+1))
    else
      echo "FAIL $part archive not produced" | tee -a "$report"
      failed=$((failed+1))
    fi
  else
    echo "FAIL $part compilation" | tee -a "$report"
    tail -n 35 "$log" || true
    failed=$((failed+1))
  fi
done
echo "A31_SUBSYSTEM_ARCHIVES_PASSED=$passed" | tee -a "$report"
echo "A31_SUBSYSTEM_ARCHIVES_FAILED=$failed" | tee -a "$report"
echo "Partial native archive builds only; executable link and guest boot not yet tested" | tee -a "$report"
test "$failed" -eq 0
