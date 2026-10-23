#!/usr/bin/env bash
# Re-link verified upstream DOSBox-X ARM64 archives as an SDL2 Android
# shared library. No guest disks or proprietary Windows media are packaged.
set -euo pipefail
: "${A31_ROOT:?}" "${ANDROID_HOME:?}" "${ANDROID_NDK_REV:?}"
SRC="$A31_ROOT/dosbox-source"
NDK="$ANDROID_HOME/ndk/$ANDROID_NDK_REV/toolchains/llvm/prebuilt/linux-x86_64/bin"
CXX="$NDK/aarch64-linux-android28-clang++"
LIB="$A31_ROOT/a31-runtime-libs/libmain.so"
test -s "$SRC/src/dosbox-x"
test -s "$A31_ROOT/sdl2-prefix/lib/libSDL2.so"
mkdir -p "$A31_ROOT/a31-runtime-libs"
# A32: force the final link with one additional compiled FD imageDisk adapter.
# This is built into libmain.so; merely repackaging libbridge.so is insufficient.
A32_OBJ="$A31_ROOT/a32-image-disk.o"
test -s "$A32_OBJ"
# Force only the final link (no extra main forwarding stub).
# -u SDL_main pulls SDL2's entry point out of libgui.a.
rm "$SRC/src/dosbox-x"
if make -C "$SRC/src" -j 3 dosbox-x \
    dosbox_x_LINK="$CXX -shared -Wl,-soname,libmain.so -Wl,--no-undefined -Wl,-u,SDL_main $A32_OBJ -L$A31_ROOT/sdl2-prefix/lib -L$A31_ROOT/libpng-prefix/lib -o dosbox-x" \
    LDFLAGS="-L$A31_ROOT/sdl2-prefix/lib -L$A31_ROOT/libpng-prefix/lib" \
    > "$A31_ROOT/android-libmain-link.log" 2>&1; then
  mv "$SRC/src/dosbox-x" "$LIB"
else
  echo 'A32_RUNTIME_LIBMAIN_LINK=FAIL' | tee "$A31_ROOT/android-libmain-status.txt"
  grep -E 'error:|ld.lld:|undefined symbol:|relocation' "$A31_ROOT/android-libmain-link.log" | tail -n 75 || true
  tail -n 35 "$A31_ROOT/android-libmain-link.log" || true
  exit 1
fi
"$NDK/llvm-readelf" -h "$LIB" | tee "$A31_ROOT/android-libmain-elf.txt"
"$NDK/llvm-readelf" -d "$LIB" | tee "$A31_ROOT/android-libmain-needed.txt"
"$NDK/llvm-nm" -D "$LIB" | tee "$A31_ROOT/android-libmain-symbols.txt"
grep -Fq 'AArch64' "$A31_ROOT/android-libmain-elf.txt"
grep -Eq '[[:space:]]T[[:space:]]SDL_main$' "$A31_ROOT/android-libmain-symbols.txt"
grep -Fq 'libSDL2.so' "$A31_ROOT/android-libmain-needed.txt"
"$NDK/llvm-nm" -D -C "$LIB" | grep -F 'WinKitA32ReadOnlyImage::Create' | tee "$A31_ROOT/a32-disk-symbol.txt"
cp "$A31_ROOT/sdl2-prefix/lib/libSDL2.so" "$A31_ROOT/a31-runtime-libs/libSDL2.so"
echo 'A32_RUNTIME_LIBMAIN_LINK=PASS' | tee "$A31_ROOT/android-libmain-status.txt"
echo 'Android Activity startup, DOS and WFW guest boot NOT VERIFIED' | tee -a "$A31_ROOT/android-libmain-status.txt"

echo 'A32 FD adapter linked; guest disk NOT attached; guest boot NOT VERIFIED'
