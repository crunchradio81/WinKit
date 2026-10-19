#!/usr/bin/env bash
# Deterministic, Microsoft-free BIOS HDD fixture using GNU x86 assembler.
# Writes *only* to new files inside caller-specified OUT directory.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "${1:-}" != "--out" || -z "${2:-}" || $# -ne 2 ]]; then
  echo 'Usage: bash scripts/BUILD_A4_BOOT_FIXTURE.sh --out /NEW/private/testing/folder' >&2
  exit 2
fi
OUT="$2"
[[ "$OUT" = /* && "$OUT" != / ]] || { echo 'Output must be an absolute directory path' >&2; exit 2; }
[[ ! -L "$OUT" ]] || { echo 'Output directory may not be a symlink' >&2; exit 2; }
for cmd in as ld python3; do command -v "$cmd" >/dev/null || { echo "Missing: $cmd" >&2; exit 2; }; done
mkdir -p "$OUT"
[[ -d "$OUT" && ! -L "$OUT" ]] || exit 2
# Deliberately refuse overwrites (including source guest disk copies).
MACHINES="$OUT/machines"
[[ ! -e "$MACHINES" && ! -L "$MACHINES" ]] || {
  echo "Refusing existing machine fixture folder: $MACHINES" >&2; exit 2;
}
mkdir "$MACHINES"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
as --32 -o "$TMP/boot.o" "$ROOT/fixtures/winkit_bios_boot.S"
ld -m elf_i386 -Ttext 0x7c00 --oformat binary -o "$TMP/boot.bin" "$TMP/boot.o"
python3 - "$MACHINES" "$TMP/boot.bin" <<'PY'
from pathlib import Path
import hashlib, json, os, sys
out, boot = Path(sys.argv[1]), Path(sys.argv[2]).read_bytes()
if len(boot) != 512 or boot[-2:] != b'\x55\xaa' or b'WINKIT A4 BIOS HDD BOOT OK' not in boot:
    raise SystemExit('Invalid MBR source bytes; not writing image')
# A4 exact test geometry: 512 bytes/sector, 63 sectors/track, 16 heads, 8 cylinders.
size = 512*63*16*8
image = out/'YARDOMOB.raw'
fd = os.open(image, os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW, 0o600)
with os.fdopen(fd,'wb') as f:
    f.truncate(size)
    f.seek(0)
    f.write(boot)
    f.flush()
    os.fsync(f.fileno())
raw = image.resolve(strict=True)
profile = ('; WINKIT A4 GENERATED FREE BOOT SECTOR TEST ONLY; NOT DOS/Windows\n'
           '[dosbox]\nmemsize=16\nworking directory option=noprompt\n'
           '[ne2000]\nne2000=false\n[ipx]\nipx=false\n'
           '[autoexec]\n@echo off\n'
           f'IMGMOUNT 2 "{raw}" -t hdd -fs none -size 512,63,16,8\n'
           'BOOT C:\n')
for name, data in [('YARDOMOB.offline.conf',profile.encode()), ('OFFLINE_READY',b'a2-offline\n')]:
    path = out/name
    fd = os.open(path, os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd,'wb') as f:
        f.write(data)
        f.flush(); os.fsync(f.fileno())
info = {'kind':'synthetic BIOS fixture, not DOS or Microsoft Windows',
        'image_file':'YARDOMOB.raw','image_bytes':size,
        'sha256':hashlib.sha256(image.read_bytes()).hexdigest(),
        'exact_geometry':'512,63,16,8','first_sector_marker':'WINKIT A4 BIOS HDD BOOT OK',
        'boot_verified':False,'guest_os_installed':False,'network_safe':False}
path=out/'A4-FIXTURE-INFO.json'
fd=os.open(path, os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW, 0o600)
with os.fdopen(fd,'w') as f: json.dump(info,f,indent=2,sort_keys=True);f.write('\n')
print('GENERATED BOOT-FIXTURE, NOT BOOTED:', raw)
print('SHA256:', info['sha256'])
print('Expected if a real emulator boots this disk: WINKIT A4 BIOS HDD BOOT OK')
PY
python3 "$ROOT/scripts/inspect_a4_disk.py" "$MACHINES/YARDOMOB.raw" --geometry 512,63,16,8
