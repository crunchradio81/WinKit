#!/usr/bin/env python3
"""Verify exact pinned and exact overlaid upstream sources before native build.

Prevents reusing a previously patched checkout with ambiguous native code.
Uses git show only against local pinned checkout; never modifies it.
SPDX-License-Identifier: GPL-2.0-or-later
"""
from pathlib import Path
import argparse
import subprocess
import sys
from apply_a2_overlay import APP_SHA, CORE_SHA, patch_frontend, patch_gradle, patch_manifest, patch_build, ROOT

PATCHES = {
    'frontend/retrodos_frontend.cpp': patch_frontend,
    'android/app/src/main/AndroidManifest.xml': patch_manifest,
    'android/app/build.gradle.kts': patch_gradle,
    'android/build-core.sh': patch_build,
}
KOTLIN_DIR = 'android/app/src/main/java/com/crownparkcomputing/retrodos/winkit'
NATIVE_DIR = 'frontend'


def git(repo, *args):
    return subprocess.check_output(['git','-C',str(repo), *args], text=True)


def verify(repo: Path):
    if git(repo, 'rev-parse', 'HEAD').strip() != APP_SHA or git(repo/'core', 'rev-parse','HEAD').strip() != CORE_SHA:
        raise ValueError('wrong upstream app/submodule revision')
    changed = set(git(repo, 'diff', '--name-only', '--ignore-submodules=all').splitlines())
    if changed != set(PATCHES):
        raise ValueError('unexpected/missing upstream tracked changes: '+repr(sorted(changed)))
    if git(repo/'core', 'diff', '--name-only').strip():
        raise ValueError('DOSBox-X core tracked files were modified')
    for relative, transform in PATCHES.items():
        original = git(repo, 'show', 'HEAD:' + relative)
        expected = transform(original)
        if (repo/relative).read_text() != expected:
            raise ValueError('upstream file not EXACT expected overlay: '+relative)
    for src in (ROOT/'overlay/kotlin').glob('*.kt'):
        dst = repo/KOTLIN_DIR/src.name
        if not dst.is_file() or dst.read_bytes() != src.read_bytes():
            raise ValueError('Kotlin overlay absent/stale: '+src.name)
    for src in (ROOT/'overlay/native').glob('*.h'):
        dst = repo/NATIVE_DIR/src.name
        if not dst.is_file() or dst.read_bytes() != src.read_bytes():
            raise ValueError('native overlay absent/stale: '+src.name)
    return len(PATCHES)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, default=ROOT/'_external/Retro-Dosbox')
    a=p.parse_args()
    try:
        n=verify(a.repo)
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(f'A8 EXACT-UPSTREAM GATE REJECTED: {exc}', file=sys.stderr)
        return 2
    print(f'A8 EXACT-UPSTREAM GATE PASS: {n} patched source files, Kotlin/native overlays, pinned revisions')
    return 0

if __name__ == '__main__':
    sys.exit(main())
