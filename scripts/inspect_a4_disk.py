#!/usr/bin/env python3
"""Read-only MBR/VBR layout report; no partition modifications. Python stdlib.

An inspection result cannot prove a disk has bootable DOS, working Windows,
clean shutdown, unique network identity, or any particular CHS geometry.
"""
import argparse
import json
import os
from pathlib import Path

SIGNATURE = b'\x55\xaa'
FIXTURE_MARKER = b'WINKIT A4 BIOS HDD BOOT OK'


def inspect_image(image: Path, expected_geometry=None):
    # Absolute file path only; symbolic links, pipes, active directories refused.
    image = Path(image)
    if image.is_symlink() or not image.is_file():
        raise ValueError('Must inspect an ordinary non-symlink file')
    size = image.stat().st_size
    if size < 512 or size % 512:
        raise ValueError('Image must contain at least one whole 512-byte sector')
    with image.open('rb') as f:
        sector = f.read(512)
    if len(sector) != 512:
        raise ValueError('Could not read first sector')
    has_signature = sector[510:512] == SIGNATURE
    partition_records = []
    partition_errors = []
    bounds = []
    if has_signature:
        for i in range(4):
            raw = sector[446 + 16 * i:446 + 16 * (i+1)]
            status, typ = raw[0], raw[4]
            start = int.from_bytes(raw[8:12], 'little')
            length = int.from_bytes(raw[12:16], 'little')
            if not any(raw):
                continue
            record = {'slot': i+1, 'boot_flag': status, 'type': typ,
                      'lba_start': start, 'sectors': length}
            partition_records.append(record)
            if status not in (0, 0x80):
                partition_errors.append(f'partition {i+1}: boot flag must be 0 or 128')
            if typ == 0 or start < 1 or length == 0 or start + length > size // 512:
                partition_errors.append(f'partition {i+1}: invalid type or LBA range')
            else:
                bounds.append((start, start + length, i+1))
        bounds.sort()
        for a, b in zip(bounds, bounds[1:]):
            if a[1] > b[0]:
                partition_errors.append(f'partition {a[2]} overlaps partition {b[2]}')
    fixture_marker = FIXTURE_MARKER in sector
    classification = ('A4_BIOS_ONLY_FIXTURE' if fixture_marker else
                      'MBR_PARTITIONED' if partition_records else
                      'SIGNATURE_WITHOUT_PARTITIONS' if has_signature else
                      'NO_55AA_SIGNATURE')
    expected_size = None
    geometry_match = None
    if expected_geometry is not None:
        if len(expected_geometry) != 4:
            raise ValueError('Geometry requires bytes-per-sector,sectors,heads,cylinders')
        b, s, h, c = expected_geometry
        if b != 512 or not (1 <= s <= 63 and 1 <= h <= 255 and 1 <= c <= 65535):
            raise ValueError('Geometry is outside supported A4 fixture geometry bounds')
        expected_size = b*s*h*c
        geometry_match = (size == expected_size)
    return {'image_bytes': size, 'sectors': size//512,
            'first_sector_signature_55aa': has_signature,
            'classification': classification, 'partitions': partition_records,
            'partition_errors': partition_errors,
            'geometry_expected_bytes': expected_size,
            'geometry_exactly_matches_file': geometry_match,
            'boot_verified': False,
            'guest_name_verified': False, 'network_safe_to_enable': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--geometry', help='Optional expected b,s,h,c for length comparison')
    args = parser.parse_args()
    try:
        geom = None
        if args.geometry is not None:
            geom = tuple(int(x) for x in args.geometry.split(','))
        print(json.dumps(inspect_image(args.image, geom), sort_keys=True, indent=2))
    except (OSError, ValueError) as e:
        parser.error(str(e))

if __name__ == '__main__':
    main()
