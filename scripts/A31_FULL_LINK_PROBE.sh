#!/usr/bin/env bash
# Attempt native Android AArch64 DOSBox-X executable link via upstream Makefiles.
# This is NOT an APK or a guest boot test.
set -euo pipefail
: "${A31_ROOT:?}" "${ANDROID_HOME:?}" "${ANDROID_NDK_REV:?}"
SRC="$A31_ROOT/dosbox-source"
NDK="$ANDROID_HOME/ndk/$ANDROID_NDK_REV/toolchains/llvm/prebuilt/linux-x86_64/bin"
log="$A31_ROOT/full-emulator-link.log"
status="$A31_ROOT/full-emulator-link-status.txt"
test -s "$SRC/config.h"
echo 'A31: attempting complete upstream DOSBox-X executable Android link' | tee "$status"
if make -C "$SRC/src" -j 3 dosbox-x >"$log" 2>&1; then
  BIN="$SRC/src/dosbox-x"
  test -s "$BIN"
  "$NDK/llvm-readelf" -h "$BIN" | tee "$A31_ROOT/full-emulator-elf.txt"
  grep -Fq AArch64 "$A31_ROOT/full-emulator-elf.txt"
  printf '%s\n' 'A31_NATIVE_EMULATOR_LINK=PASS' 'AArch64 linked, no Android APK/device boot proved' | tee -a "$status"
else
  printf '%s\n' 'A31_NATIVE_EMULATOR_LINK=FAIL' 'Inspect full-emulator-link.log for actual linker errors' | tee -a "$status"
  grep -E "fatal error:|error:|undefined reference|ld.lld:" "$log" | tail -n 65 || true
  tail -n 40 "$log" || true
  exit 1
fi
