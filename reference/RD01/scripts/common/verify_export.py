#!/usr/bin/env python3
"""Verify the integrity of a private Mac-exported YARDOMOB disk folder."""
import argparse
import hashlib
import json
import pathlib
import sys


def sha256(p):
    h = hashlib.sha256()
    with p.open('rb') as f:
        while True:
            b = f.read(8 * 1024 * 1024)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def verify(folder):
    folder = pathlib.Path(folder).expanduser()
    manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
    assert manifest['schema'] == 'winkit311-android-export-v1', 'Unexpected manifest schema'
    basename = manifest['image_file']
    assert basename == 'YARDOMOB.raw', 'Unsafe or unexpected disk image name'
    disk = folder / basename
    assert disk.is_file() and not disk.is_symlink(), 'Disk absent or symlink'
    actual_size = disk.stat().st_size
    actual_hash = sha256(disk)
    assert actual_size == manifest['image_bytes'], 'Image size mismatch'
    assert actual_hash == manifest['sha256'], 'Image SHA mismatch'
    print('PASS: image byte length:', actual_size)
    print('PASS: SHA-256:', actual_hash)
    if not manifest.get('network_safe_to_connect', False):
        print('IMPORTANT: image is NOT cleared to join the legacy virtual LAN.')
    return True


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('export_directory')
    args = ap.parse_args()
    try:
        verify(args.export_directory)
        return 0
    except (OSError, ValueError, KeyError, AssertionError, json.JSONDecodeError) as e:
        print('FAIL:', e, file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
