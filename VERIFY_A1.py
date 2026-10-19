#!/usr/bin/env python3
import hashlib
from pathlib import Path
import sys
root = Path(__file__).resolve().parent
expected = (root/'MANIFEST.sha256').read_text().splitlines()
missing = []
for entry in expected:
    digest, rel = entry.split('  ', 1)
    f = root/rel
    if not f.is_file() or hashlib.sha256(f.read_bytes()).hexdigest() != digest:
        missing.append(rel)
if missing:
    for x in missing: print('FAIL:',x)
    sys.exit(2)
print(f'PASS: {len(expected)} A1 source files match MANIFEST.sha256')
