#!/usr/bin/env python3
"""Safe private guest-disk exporter. Requires a STOPPED *-WORKING.raw image.

This intentionally DOES NOT patch guest machine identity, install drivers, or alter
source/backup images. Standard-library only; Windows/DOS files are not distributed.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import uuid

ALLOWED = {'YARDO-WORKING.raw', 'YARDO-B-WORKING.raw'}
BLOCKED_NAMES = {'DOS622-PeerA-NetBEUI-v10.raw', 'DOS622-PeerB-NetBEUI-v10.raw'}
BLOCK = 8 * 1024 * 1024


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(BLOCK), b''):
            h.update(chunk)
    return h.hexdigest()


def valid_working_source(src):
    if src.is_symlink() or not src.is_file():
        raise ValueError('Source must be a regular, non-symlink working image')
    if src.name in BLOCKED_NAMES or src.name not in ALLOWED:
        raise ValueError('Refusing source: expected YARDO-WORKING.raw or YARDO-B-WORKING.raw')
    resolved = src.resolve(strict=True)
    if not any(x.startswith('WorkingGuests-') for x in resolved.parts):
        raise ValueError('Refusing source outside a named WorkingGuests-* clone folder')
    if 'LegacyLANLab' in resolved.parts or 'ColdBackup' in str(resolved):
        raise ValueError('Refusing original images or cold-backup material')
    if resolved.stat().st_size < 512 or resolved.stat().st_size % 512:
        raise ValueError('Image is too small or not sector-aligned (512-byte sectors)')
    return resolved


def ensure_emulators_stopped():
    if shutil.which('pgrep'):
        p = subprocess.run(['pgrep', '-ix', 'dosbox-x'], text=True,
                           stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        if p.returncode == 0:
            raise RuntimeError('DOSBox-X process detected. Shut down ALL DOSBox-X windows before cloning.')
    # lsof is an additional guard even if a process's name is obscured.


def ensure_not_open(src):
    if shutil.which('lsof'):
        p = subprocess.run(['lsof', '-Fn', str(src)], text=True,
                           stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        if p.returncode == 0 and p.stdout.strip():
            raise RuntimeError('Source image is open in another process. Stop it before export.')


def export_guest(source, output_base):
    src = valid_working_source(pathlib.Path(source).expanduser())
    ensure_emulators_stopped()
    ensure_not_open(src)
    outbase = pathlib.Path(output_base).expanduser().resolve()
    if outbase == src.parent or outbase in src.parents:
        raise ValueError('Output must be outside source working image directory')
    outbase.mkdir(parents=True, exist_ok=True, mode=0o700)
    usage = shutil.disk_usage(outbase)
    if usage.free < src.stat().st_size + 16 * 1024 * 1024:
        raise RuntimeError('Insufficient free space for a full independent disk copy')
    stamp = dt.datetime.now().strftime('%Y%m%d-%H%M%S')
    target = outbase / f'YARDOMOB-{stamp}-{uuid.uuid4().hex[:8]}'
    target.mkdir(mode=0o700)
    dest = target / 'YARDOMOB.raw'
    try:
        print(f'Checking source: {src}', flush=True)
        source_before = sha256_file(src)
        # Exclusive destination and no symlink redirection.
        fd = os.open(dest, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        out_hash = hashlib.sha256()
        with open(src, 'rb') as inp, os.fdopen(fd, 'wb') as out:
            while True:
                b = inp.read(BLOCK)
                if not b:
                    break
                out.write(b)
                out_hash.update(b)
            out.flush()
            os.fsync(out.fileno())
        source_after = sha256_file(src)
        dest_hash = sha256_file(dest)
        if len({source_before, source_after, dest_hash, out_hash.hexdigest()}) != 1:
            raise RuntimeError('Image hash mismatch (source changed or copy corrupt); export rejected')
        if dest.stat().st_size != src.stat().st_size:
            raise RuntimeError('Image length mismatch; export rejected')
        manifest = {
            'schema': 'winkit311-android-export-v1',
            'created_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
            'image_file': dest.name,
            'image_bytes': dest.stat().st_size,
            'sha256': dest_hash,
            'source_working_clone_filename': src.name,
            'target_guest_name': 'YARDOMOB',
            'guest_name_already_changed': False,
            'network_safe_to_connect': False,
            'ethernet_mac_change_required': True,
            'requires_offline_first_boot': True,
            'notes': 'Never network this clone until guest Windows name and emulated NIC MAC are unique.',
        }
        with open(target / 'manifest.json', 'x', encoding='utf-8') as out:
            json.dump(manifest, out, indent=2)
            out.write('\n')
        with open(target / 'SHA256SUMS.txt', 'x', encoding='utf-8') as out:
            out.write(f'{dest_hash}  {dest.name}\n')
        print('\nPRIVATE EXPORT CREATED:', target)
        print('Image SHA-256:', dest_hash)
        print('SOURCE UNCHANGED; NETWORK NOT SAFE UNTIL GUEST IDENTITY CHANGED.')
        return target
    except BaseException:
        # Do not leave a half-built disk labeled good.
        shutil.rmtree(target)
        raise


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--source', required=True, help='Stopped *-WORKING.raw image')
    ap.add_argument('--output-base', required=True, help='Private output directory outside source')
    args = ap.parse_args()
    try:
        export_guest(args.source, args.output_base)
    except (OSError, RuntimeError, ValueError) as e:
        print('ERROR:', e, file=sys.stderr)
        return 2
    return 0

if __name__ == '__main__':
    sys.exit(main())
