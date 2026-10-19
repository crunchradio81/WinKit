#!/usr/bin/env python3
"""Verify cumulative source integrity excluding the manifest itself."""
from pathlib import Path
import hashlib
ROOT=Path(__file__).resolve().parent
entries=(ROOT/'MANIFEST.sha256').read_text().splitlines()
seen=set()
for row in entries:
    sha,sep,name=row.partition('  ')
    assert sep and len(sha)==64 and name not in seen, f'bad manifest entry: {row}'
    seen.add(name)
    p=ROOT/name
    assert p.is_file() and not p.is_symlink(), f'missing or linked {name}'
    assert hashlib.sha256(p.read_bytes()).hexdigest()==sha, f'checksum differs: {name}'
assert seen=={str(x.relative_to(ROOT)) for x in ROOT.rglob('*') if x.is_file() and x.name!='MANIFEST.sha256' and '_external' not in x.parts and '_build_outputs' not in x.parts and str(x.relative_to(ROOT))!='reference/PoC1/transport/ethnet-poc-jvm.jar' and '__pycache__' not in x.parts and x.suffix!='.pyc'}, 'missing manifest entries'
print(f'PASS A10 source integrity: {len(seen)} files')
