#!/usr/bin/env bash
# A31 Android-only completion matrix for remaining DOSBox-X archives.
# Uses upstream generated Makefiles; does NOT claim a complete emulator link or APK.
set -euo pipefail
: "${A31_ROOT:?}" "${ANDROID_HOME:?}" "${ANDROID_NDK_REV:?}"
SRC="$A31_ROOT/dosbox-source"
NDK="$ANDROID_HOME/ndk/$ANDROID_NDK_REV/toolchains/llvm/prebuilt/linux-x86_64/bin"
AR="$NDK/llvm-ar"
READELF="$NDK/llvm-readelf"
test -x "$AR"
test -s "$SRC/config.h"
report="$A31_ROOT/remaining-archives.txt"
: > "$report"
ok=0
bad=0
# Based on upstream src/Makefile.am LDADD/SUBDIRS, outside earlier CPU/DOS/FPU/hardware/ints probes.
for spec in \
  misc:libmisc.a \
  shell:libshell.a \
  builtin:libbuiltin.a \
  gui:libgui.a \
  output:liboutput.a \
  agent:libagent.a \
  aviwriter:libaviwriter.a \
  debug:libdebug.a \
  hardware/serialport:libserial.a \
  hardware/parport:libparallel.a \
  hardware/reSID:libresid.a \
  hardware/mame:libmame.a \
  libs/zmbv:libzmbv.a \
  libs/gui_tk:libgui_tk.a \
  libs/passthroughio:libpassthroughio.a \
  libs/mt32:libmt32.a \
  libs/xBRZ:libxbrz.a \
  libs/fluidsynth:libfluidsynth.a \
  libs/decoders/internal:libopusint.a
do
  part="${spec%%:*}"
  lib="${spec#*:}"
  tag="${part//\//_}"
  log="$A31_ROOT/remaining-$tag.log"
  if test ! -f "$SRC/src/$part/Makefile"; then
    echo "FAIL $spec missing generated Makefile" | tee -a "$report"
    bad=$((bad+1))
    continue
  fi
  if make -C "$SRC/src/$part" -j 3 "$lib" > "$log" 2>&1 && test -s "$SRC/src/$part/$lib"; then
    archive="$SRC/src/$part/$lib"
    members="$("$AR" t "$archive" | wc -l | tr -d ' ')"
    if test "$members" -gt 0; then
      echo "PASS $spec members=$members" | tee -a "$report"
      ok=$((ok+1))
    else
      echo "FAIL $spec empty archive" | tee -a "$report"
      bad=$((bad+1))
    fi
  else
    echo "FAIL $spec compile/link stage (remaining-$tag.log)" | tee -a "$report"
    tail -n 14 "$log" || true
    bad=$((bad+1))
  fi
done
printf 'A31_REMAINING_ARCHIVES_PASSED=%s\nA31_REMAINING_ARCHIVES_FAILED=%s\n' "$ok" "$bad" | tee -a "$report"
echo "Static archives only; no Android emulator link, APK or guest boot" | tee -a "$report"
test "$bad" -eq 0
