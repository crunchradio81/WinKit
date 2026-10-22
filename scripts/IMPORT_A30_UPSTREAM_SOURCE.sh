#!/usr/bin/env bash
# A30: preserve a complete, editable, pinned upstream DOSBox-X Git tree in WinKit.
# This intentionally does not patch the original A29 core or publish an APK.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIN=029bd65c5dcc7cdc7f2e14e35278620d9707d969
REMOTE=https://github.com/joncampbell123/dosbox-x.git
PREFIX=vendor/dosbox-x-upstream
cd "$ROOT"
[[ "$(git branch --show-current)" = android-a30-upstream-core-source ]] || {
  echo 'A30 source import must not modify main or the A26/A29 branches' >&2; exit 2;
}
[[ -z "$(git status --porcelain)" ]] || { echo 'Working tree not clean' >&2; exit 2; }
[[ ! -e "$PREFIX" && ! -L "$PREFIX" ]] || { echo 'Source already imported; refusing replacement' >&2; exit 2; }
git fetch --no-tags "$REMOTE" "$PIN"
[[ "$(git rev-parse FETCH_HEAD)" = "$PIN" ]] || { echo 'Upstream revision changed' >&2; exit 2; }
for f in COPYING configure.ac src/dosbox.cpp include/dosbox.h; do
  git cat-file -e "$PIN:$f" || { echo "Missing source $f" >&2; exit 2; }
done
git show "$PIN:COPYING" | grep -Fq 'GNU GENERAL PUBLIC LICENSE' || {
  echo 'Expected GPL COPYING absent' >&2; exit 2;
}
# Keep complete source and history, rather than shipping the frozen A26 .so.
git subtree add --prefix="$PREFIX" "$PIN" -m "A30: vendor exact upstream DOSBox-X 029bd65 source and ancestry"
[[ "$(git rev-parse "HEAD:$PREFIX")" = "$(git rev-parse "$PIN^{tree}")" ]] || {
  echo 'Vendored source tree does not match pinned upstream' >&2; exit 2;
}
python3 - "$ROOT" <<'PY'
from pathlib import Path
import hashlib, sys
root = Path(sys.argv[1])
excluded = {'reference/PoC1/transport/ethnet-poc-jvm.jar'}
entries = []
for f in root.rglob('*'):
    if not f.is_file() or f.name == 'MANIFEST.sha256':
        continue
    rel = f.relative_to(root)
    if set(rel.parts) & {'.git', '_external', '_build_outputs', '__pycache__'}:
        continue
    if str(rel) in excluded or f.suffix == '.pyc':
        continue
    if f.is_symlink():
        raise SystemExit('Unexpected symlink: ' + str(rel))
    entries.append((rel.as_posix(), hashlib.sha256(f.read_bytes()).hexdigest()))
(root / 'MANIFEST.sha256').write_text(''.join(
    f'{digest}  {name}\n' for name, digest in sorted(entries)
))
print('Verified manifest entries:',len(entries))
PY
python3 VERIFY_A10.py
git add MANIFEST.sha256
git commit -m "A30: checksum-lock full pinned upstream emulator source"
echo 'A30 pinned DOSBox-X source is now tracked and editable in the A30 branch.'
echo 'This is NOT a compiled Android host bridge or guest boot.'
