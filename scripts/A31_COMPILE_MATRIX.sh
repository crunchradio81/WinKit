#!/usr/bin/env bash
# Diagnostic-only: compile real DOSBox-X units for Android AArch64.
set -euo pipefail
test -n "$A31_ROOT" && test -n "$ANDROID_HOME" && test -n "$ANDROID_NDK_REV"
SRC="$A31_ROOT/dosbox-source"
NDK="$ANDROID_HOME/ndk/$ANDROID_NDK_REV/toolchains/llvm/prebuilt/linux-x86_64/bin"
CXX="$NDK/aarch64-linux-android28-clang++"
READELF="$NDK/llvm-readelf"
test -x "$CXX" && test -s "$SRC/config.h"
mkdir -p "$A31_ROOT/matrix-objects"
RESULTS="$A31_ROOT/compile-matrix.txt"
: > "$RESULTS"
pass=0
fail=0
total=0
# CPU, paging, memory, video, sound, keyboard, BIOS, disk, DOS and FPU.
for unit in \
  cpu/flags.cpp cpu/paging.cpp cpu/cpu.cpp \
  hardware/memory.cpp hardware/vga.cpp hardware/mixer.cpp \
  hardware/sblaster.cpp hardware/keyboard.cpp \
  ints/bios.cpp ints/int10.cpp ints/bios_disk.cpp \
  dos/dos.cpp dos/drive_fat.cpp fpu/fpu.cpp
do
  total=$((total+1))
  srcfile="$SRC/src/$unit"
  test -f "$srcfile"
  tag="$(printf %s "$unit" | tr / _ | sed 's/\.cpp$//')"
  log="$A31_ROOT/matrix-$tag.log"
  obj="$A31_ROOT/matrix-objects/$tag.o"
  if "$CXX" -std=c++17 -fPIC -DRESDIR='"/data/local/tmp/winkit-a31-probe"' \
        -I"$SRC" -I"$SRC/include" -I"$SRC/src" \
        -I"$A31_ROOT/sdl2-prefix/include/SDL2" \
        -I"$A31_ROOT/libpng-prefix/include" \
        -c "$srcfile" -o "$obj" >"$log" 2>&1 &&
        "$READELF" -h "$obj" | grep -Fq 'AArch64'; then
    echo "PASS $unit" | tee -a "$RESULTS"
    pass=$((pass+1))
  else
    echo "FAIL $unit (see matrix-$tag.log)" | tee -a "$RESULTS"
    tail -n 14 "$log" || true
    fail=$((fail+1))
  fi
done
echo "A31_MATRIX_PASSED=$pass" | tee -a "$RESULTS"
echo "A31_MATRIX_FAILED=$fail" | tee -a "$RESULTS"
echo "A31_MATRIX_TOTAL=$total" | tee -a "$RESULTS"
echo "ARM64 object compilation only; no core link, APK or guest boot" | tee -a "$RESULTS"
if [ "$fail" -gt 0 ]; then
  echo "::error::A31 subsystem compilation failed ($pass passed, $fail failed)"
  exit 1
fi
echo "PASS: $total genuine DOSBox-X units compiled for Android AArch64"
