#!/usr/bin/env python3
"""Offline APK/ELF/SDL3.jar integrity gate (NOT proof of Android boot).

Only use AFTER Gradle and NDK successfully compile. Uses Python stdlib.
Requires byte-for-byte identity with the linked libraries in the current build.
SPDX-License-Identifier: GPL-2.0-or-later
"""
from __future__ import annotations
import argparse
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

CORE_SIGNATURE = b'WinKit A6: launch profile mismatch'
MACHINE_SIGNATURE = b'WinKit 3.11 - YARDOMOB (OFFLINE)'
JAR_CLASS = 'org/libsdl/app/SDLActivity.class'
REQUIRED_LIBS = ('libretrodos.so', 'libSDL3.so', 'libc++_shared.so')


def aarch64_shared_elf(blob: bytes) -> bool:
    # ELF64, little endian, ET_DYN, EM_AARCH64. Doesn't claim valid relocation.
    return (len(blob) >= 64 and blob[:4] == b'\x7fELF' and
            blob[4] == 2 and blob[5] == 1 and
            struct.unpack_from('<H', blob, 16)[0] == 3 and
            struct.unpack_from('<H', blob, 18)[0] == 183)


def validate(apk: Path, jni_dir: Path, sdljar: Path) -> dict:
    if not apk.is_file() or not sdljar.is_file():
        raise ValueError('APK and SDL3.jar must both exist')
    with zipfile.ZipFile(sdljar) as z:
        if z.testzip() is not None or JAR_CLASS not in z.namelist():
            raise ValueError('SDL3.jar lacks org.libsdl.app.SDLActivity')
    result = {}
    with zipfile.ZipFile(apk) as z:
        bad = z.testzip()
        if bad:
            raise ValueError(f'Corrupt APK entry: {bad}')
        names = set(z.namelist())
        if 'AndroidManifest.xml' not in names or 'resources.arsc' not in names:
            raise ValueError('APK lacks compiled Android manifest/resources')
        if not any(n.startswith('classes') and n.endswith('.dex') and z.getinfo(n).file_size > 8 for n in names):
            raise ValueError('APK lacks non-empty DEX')
        if any(n.startswith('lib/') and not n.startswith('lib/arm64-v8a/') for n in names):
            raise ValueError('Unexpected non-ARM64 native library in APK')
        # Source package must never distribute a private installed guest disk.
        if any(n.lower().endswith(('.raw', '.qcow2', '.vhd', '.vhdx', '.vmdk')) for n in names):
            raise ValueError('APK contains a virtual HDD: proprietary disk exclusion')
        for lib in REQUIRED_LIBS:
            key = 'lib/arm64-v8a/' + lib
            if key not in names:
                raise ValueError(f'APK missing ARM64 {lib}')
            built = jni_dir / lib
            if not built.is_file():
                raise ValueError(f'Fresh build missing {built}')
            payload = z.read(key)
            if not aarch64_shared_elf(payload):
                raise ValueError(f'APK {lib} is not ELF64 AArch64 shared library')
            if hashlib.sha256(payload).digest() != hashlib.sha256(built.read_bytes()).digest():
                raise ValueError(f'APK {lib} differs from freshly built native library')
            result[lib] = hashlib.sha256(payload).hexdigest()
        core = z.read('lib/arm64-v8a/libretrodos.so')
        if CORE_SIGNATURE not in core or MACHINE_SIGNATURE not in core:
            raise ValueError('APK core lacks expected WinKit machine or A6 launch safety gate')
        if b'libSDL3.so' not in core:
            raise ValueError('APK core lacks expected SDL3 dynamic link name')
    return result


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--jni-dir', type=Path, required=True)
    p.add_argument('--sdl3-jar', type=Path, required=True)
    a = p.parse_args()
    try:
        hashes = validate(a.apk, a.jni_dir, a.sdl3_jar)
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        print(f'A8 APK GATE REJECTED: {exc}', file=sys.stderr)
        return 2
    for lib, sha in hashes.items():
        print(f'A8 APK MATCHED: {lib} sha256={sha}')
    print('A8 APK FILE GATES PASS; device/emulator boot remain unverified.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
