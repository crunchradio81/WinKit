#!/usr/bin/env python3
"""Verify every file in cumulative A3 source against accompanying manifest."""
from pathlib import Path
import hashlib
import sys
root = Path(__file__).resolve().parent
manifest = root / 'MANIFEST.sha256'
expected = {}
for line in manifest.read_text(encoding='utf-8').splitlines():
    digest, rel = line.split('  ', 1)
    if not rel or rel.startswith('/') or '..' in Path(rel).parts or rel in expected:
        sys.exit('ERROR: invalid file name in manifest')
    expected[rel] = digest
actual = {p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() and p != manifest and '__pycache__' not in p.parts and p.suffix != '.pyc'}
if actual != set(expected):
    sys.exit(f'ERROR: missing {sorted(set(expected)-actual)} or extra {sorted(actual-set(expected))}')
for rel, digest in expected.items():
    path = root / rel
    if path.is_symlink(): sys.exit('ERROR: symlink in source package')
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(part)
    if h.hexdigest() != digest: sys.exit('ERROR: checksum mismatch for ' + rel)
print('PASS: WinKit A3 verified', len(expected), 'files; source only')
