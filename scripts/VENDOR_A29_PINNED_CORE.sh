#!/usr/bin/env bash
# Import the EXACT proven DOSBox-X SDL3 source commit into this WinKit Git history.
# This is source vendoring (git subtree), NOT extracting a compiled APK.
# Usage: bash scripts/VENDOR_A29_PINNED_CORE.sh /path/to/authorized/core-git-clone
#    or: bash scripts/VENDOR_A29_PINNED_CORE.sh https://github.com/OWNER/RESTORED-CORE.git
# Requires source authorization and Git history containing the pinned commit.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE_SHA=4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11
PREFIX=vendor/dosbox-x-sdl3
[[ $# = 1 && -n "$1" ]] || { echo "Usage: $0 <authorized Git repository URL or local clone>" >&2; exit 2; }
SOURCE="$1"
[[ "$(git -C "$ROOT" rev-parse --show-toplevel)" = "$ROOT" ]] || {
  echo 'Not a complete WinKit Git checkout; vendor import requires Git history.' >&2; exit 2;
}
[[ "$(git -C "$ROOT" branch --show-current)" = android-a29-mac-transfer-isolated ]] || {
  echo 'Import only on android-a29-mac-transfer-isolated, never on main or a detached HEAD.' >&2; exit 2;
}
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || {
  echo 'Working tree must be clean before importing third-party source.' >&2; exit 2;
}
[[ ! -e "$ROOT/$PREFIX" && ! -L "$ROOT/$PREFIX" ]] || {
  echo 'Vendored core already exists. Refusing overwrite.' >&2; exit 2;
}
# Fetch only the pinned object. A local clone retains the commit even if the
# original hosting repository is no longer accessible.
git -C "$ROOT" fetch --no-tags "$SOURCE" "$CORE_SHA"
git -C "$ROOT" cat-file -e "$CORE_SHA^{commit}" || {
  echo 'Pinned core commit could not be retrieved.' >&2; exit 2;
}
git -C "$ROOT" cat-file -e "$CORE_SHA:include/retrodos_host.h" || {
  echo 'Wrong core: missing Retro-DOS integration host API.' >&2; exit 2;
}
git -C "$ROOT" cat-file -e "$CORE_SHA:src/gui/retrodos_host.cpp" || {
  echo 'Wrong core: missing Retro-DOS host bridge.' >&2; exit 2;
}
[[ -z "$(git -C "$ROOT" ls-tree -r "$CORE_SHA" | grep '^160000 commit ' || true)" ]] || {
  echo 'Core has nested submodules requiring separate source review.' >&2; exit 2;
}
# The commit becomes part of WinKit ancestry; full-history GitHub checkouts
# retain the exact core Git object and all editable source files.
git -C "$ROOT" subtree add --prefix="$PREFIX" "$CORE_SHA" -m \
  "Vendor exact DOSBox-X SDL3 source 4167c3a for maintainable A29 builds"
VENDORED_TREE="$(git -C "$ROOT" rev-parse "HEAD:$PREFIX")"
ORIGINAL_TREE="$(git -C "$ROOT" rev-parse "$CORE_SHA^{tree}")"
[[ "$VENDORED_TREE" = "$ORIGINAL_TREE" ]] || {
  echo 'ERROR: Vendored source tree differs from the exact pinned commit.' >&2; exit 3;
}
# The checked-in source integrity manifest covers all WinKit files, including
# the vendored C/C++ tree. Deliberately reject symlinks for this gate.
python3 - "$ROOT" <<'PY'
import hashlib, pathlib, subprocess, sys
root = pathlib.Path(sys.argv[1])
tracked = subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z']).split(b'\0')
rows = []
for data in tracked:
    if not data: continue
    path = data.decode('utf-8', errors='surrogateescape')
    if path == 'MANIFEST.sha256' or path == 'reference/PoC1/transport/ethnet-poc-jvm.jar':
        continue
    parts = pathlib.PurePosixPath(path).parts
    if '_external' in parts or '_build_outputs' in parts or '__pycache__' in parts or path.endswith('.pyc'):
        continue
    source = root / path
    if source.is_symlink() or not source.is_file():
        raise SystemExit('Cannot verify vendored symlink or non-file: ' + path)
    rows.append((path, hashlib.sha256(source.read_bytes()).hexdigest()))
(root / 'MANIFEST.sha256').write_text(
    ''.join(f'{digest}  {name}\n' for name, digest in sorted(rows)) )
print(f'Locked {len(rows)} project and vendored-source files')
PY
git -C "$ROOT" add -- MANIFEST.sha256
git -C "$ROOT" commit -m "Record exact vendored core in WinKit source integrity manifest"
echo "Vendored pinned source committed into current branch: $PREFIX"
echo "Verified core revision: $CORE_SHA"
echo 'Source is editable and retained by WinKit, including the original Git commit.'
echo 'No emulator APK has been compiled. Review Git tree and licensing before pushing.'
