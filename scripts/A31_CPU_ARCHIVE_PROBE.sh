#!/usr/bin/env bash
# Build DOSBox-X's actual upstream CPU static library using configured Autotools.
# Evidence is AArch64 ELF objects in archive, NOT an Android app or guest boot.
set -euo pipefail
test -n "$A31_ROOT" && test -n "$ANDROID_HOME" && test -n "$ANDROID_NDK_REV"
SRC="$A31_ROOT/dosbox-source"
NDK="$ANDROID_HOME/ndk/$ANDROID_NDK_REV/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -f "$SRC/src/cpu/Makefile"
make -C "$SRC/src/cpu" -j 3 libcpu.a >"$A31_ROOT/cpu-archive-build.log" 2>&1 || {
  tail -n 100 "$A31_ROOT/cpu-archive-build.log"
  echo "::error::DOSBox-X CPU archive build failed under Android cross compiler"
  exit 1
}
LIB="$SRC/src/cpu/libcpu.a"
test -s "$LIB"
"$NDK/llvm-ar" t "$LIB" | tee "$A31_ROOT/cpu-archive-contents.txt"
OBJ_COUNT="$("$NDK/llvm-ar" t "$LIB" | wc -l | tr -d ' ')"
test "$OBJ_COUNT" -ge 10
# Extract one representative ELF to verify the archive is not an x86 host build.
mkdir -p "$A31_ROOT/cpu-verify"
cd "$A31_ROOT/cpu-verify"
"$NDK/llvm-ar" x "$LIB"
OBJ="$(find . -maxdepth 1 -name '*.o' -type f | head -1)"
test -n "$OBJ"
"$NDK/llvm-readelf" -h "$OBJ" | tee "$A31_ROOT/cpu-archive-elf.txt"
grep -F 'AArch64' "$A31_ROOT/cpu-archive-elf.txt"
echo "PASS DOSBox-X ARM64 CPU archive ($OBJ_COUNT members); not linked into an emulator" | tee "$A31_ROOT/cpu-archive-status.txt"
