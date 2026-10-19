#!/usr/bin/env python3
"""Full manifest verification for cumulative A4 source. No network or SDK."""
from pathlib import Path
import hashlib
import sys
ROOT=Path(__file__).resolve().parent
LIST=ROOT/'MANIFEST.sha256'
items={}
for line in LIST.read_text(encoding='utf-8').splitlines():
    digest,path=line.split('  ',1)
    if path.startswith('/') or '..' in Path(path).parts or path in items:
        sys.exit('Invalid archive manifest path')
    items[path]=digest
observed={str(f.relative_to(ROOT)) for f in ROOT.rglob('*')
          if f.is_file() and f != LIST and not f.is_symlink()
          and '__pycache__' not in f.parts and f.suffix != '.pyc'}
if observed != set(items):
    sys.exit(f'Manifest mismatch: missing {sorted(set(items)-observed)} extra {sorted(observed-set(items))}')
for path,expected in sorted(items.items()):
    item=ROOT/path
    if item.is_symlink() or not item.is_file(): sys.exit(f'Unsafe path: {path}')
    h=hashlib.sha256()
    with item.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    if h.hexdigest()!=expected:sys.exit(f'Checksum mismatch: {path}')
print(f'A4 cumulative source integrity PASSED: {len(items)} files; NO APK or guest disk distributed')
