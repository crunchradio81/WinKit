#!/usr/bin/env python3
"""Produce a separate offline-first DOSBox-X config from a REAL working Mac config.

No guessed geometry, no running ETHNET, no original image modifications.
This is a draft for core integration; core needs an ARM64 build before usable.
"""
import argparse
import pathlib
import re
import sys


def prepare(text: str, old_path: str, android_path: str) -> str:
    if not old_path or '\n' in old_path or '\r' in old_path:
        raise ValueError('Original Mac image path must be explicit and nonempty')
    if not android_path.startswith('/') or any(x in android_path for x in '"\r\n'):
        raise ValueError('Android destination must be an absolute plain app-private path')
    if not any('/' in p for p in [old_path]) or android_path == old_path:
        raise ValueError('Mac source image path and Android private target must differ')
    if text.count(old_path) != 1:
        raise ValueError('Exactly one explicit original disk path must be present; inspect Mac config')
    lines = text.replace(old_path, android_path).splitlines(keepends=True)
    have_autoexec = False
    have_ne2000 = False
    has_ne2k_line = False
    has_working = False
    section = ''
    output = []
    for line in lines:
        match = re.match(r'^\s*\[([^]]+)\]\s*(?:[;#].*)?$', line)
        if match:
            if section == 'ne2000' and not has_ne2k_line:
                output.append('ne2000=false\n')
            if section == 'dosbox' and not has_working:
                output.append('working directory option=noprompt\n')
            section = match.group(1).strip().lower()
            have_autoexec |= (section == 'autoexec')
            have_ne2000 |= (section == 'ne2000')
        if section == 'autoexec' and re.match(r'^\s*(?:@?ETHNET\b)', line, re.I):
            # Drop explicit tunnel setup. This does NOT disable an IN-GUEST adapter.
            continue
        if section == 'ne2000' and re.match(r'^\s*ne2000\s*=', line, re.I):
            line = 'ne2000=false\n'
            has_ne2k_line = True
        if section == 'dosbox' and re.match(r'^\s*working directory option\s*=', line, re.I):
            line = 'working directory option=noprompt\n'
            has_working = True
        output.append(line)
    if section == 'ne2000' and not has_ne2k_line:
        output.append('ne2000=false\n')
    if section == 'dosbox' and not has_working:
        output.append('working directory option=noprompt\n')
    if not have_autoexec:
        raise ValueError('Cannot validate Mac boot chain: missing [autoexec]')
    if not have_ne2000:
        output.extend(['\n[ne2000]\n', 'ne2000=false\n'])
    if not re.search(r'(?im)^\s*\[dosbox\]\s*$', text):
        output.extend(['\n[dosbox]\n', 'working directory option=noprompt\n'])
    assembled = ''.join(output)
    if re.search(r'(?im)^\s*@?ETHNET\s+(?:CONNECT|STARTSERVER)\b', assembled):
        raise ValueError('ETHNET commands remain; review supplied boot chain before running')
    if not re.search(r'(?im)^\s*ne2000=false\s*$',assembled):
        raise ValueError('Could not disable network in emulator')
    return '; WINKIT A1 OFFLINE BOOT; DOES NOT CONFIRM GUEST IDENTITY.\n' + assembled


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mac-conf', required=True)
    p.add_argument('--original-image-path', required=True)
    p.add_argument('--android-image-path', required=True)
    p.add_argument('--out', required=True)
    a = p.parse_args()
    inp = pathlib.Path(a.mac_conf).expanduser().resolve(strict=True)
    out = pathlib.Path(a.out).expanduser()
    if inp == out.resolve() or out.exists():
        p.error('Output must be a new file and not overwrite Mac config')
    try:
        result = prepare(inp.read_text(), a.original_image_path, a.android_image_path)
        out.write_text(result, encoding='utf-8')
        print(f'PRIVATE OFFLINE CONFIG: {out}')
        print('VERIFY all other mounts, CHS, and DOSBox-X options manually before boot.')
    except (OSError, ValueError) as exc:
        p.error(str(exc))

if __name__ == '__main__':
    main()
