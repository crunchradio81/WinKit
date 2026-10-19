#!/usr/bin/env python3
"""Deterministic A6 checksum manifest verifier (sources only, no APK)."""
from pathlib import Path
import hashlib, sys
ROOT=Path(__file__).resolve().parent
manifest=ROOT/'MANIFEST.sha256'
expected={}
for line in manifest.read_text().splitlines():
    digest, rel=line.split('  ',1)
    if Path(rel).is_absolute() or '..' in Path(rel).parts or rel in expected:
        sys.exit('Malformed manifest entry '+rel)
    expected[rel]=digest
observed={str(p.relative_to(ROOT)) for p in ROOT.rglob('*')
          if p.is_file() and p!=manifest and not p.is_symlink()
          and '__pycache__' not in p.parts and p.suffix!='.pyc'}
if set(expected)!=observed:
    sys.exit('Manifest mismatch: missing '+str(sorted(set(expected)-observed))+
             '; extra '+str(sorted(observed-set(expected))))
for rel,digest in sorted(expected.items()):
    path=ROOT/rel
    if path.is_symlink() or not path.is_file(): sys.exit('Unsafe file '+rel)
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1<<20),b''):h.update(b)
    if h.hexdigest()!=digest: sys.exit('Checksum mismatch '+rel)
print(f'A6 manifest PASS: {len(expected)} source files; Android APK NOT built')
