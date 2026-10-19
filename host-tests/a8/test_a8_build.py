#!/usr/bin/env python3
"""A8 host tests: generated Android ELF/APK fixtures are STRUCTURAL only, never bootable."""
import importlib.util
import io
import os
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'scripts'))
from apply_a2_overlay import patch_build
from verify_a8_apk import validate, aarch64_shared_elf, REQUIRED_LIBS


def fake_elf(name):
    b = bytearray(70)
    b[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<H', b, 16, 3)
    struct.pack_into('<H', b, 18, 183)
    if name == 'libretrodos.so':
        b += b'WinKit A6: launch profile mismatch\x00'
        b += b'WinKit 3.11 - YARDOMOB (OFFLINE)\x00libSDL3.so\x00'
    return bytes(b)


def make_fake_tree(root, *, replace=None, extra=(), missing=(), corrupt_jar=False):
    root.mkdir(parents=True, exist_ok=True)
    jni = root/'jni'
    jni.mkdir()
    for name in REQUIRED_LIBS:
        (jni/name).write_bytes(fake_elf(name))
    jar = root/'SDL3.jar'
    with zipfile.ZipFile(jar,'w') as z:
        z.writestr('org/libsdl/app/Missing.class' if corrupt_jar else
                   'org/libsdl/app/SDLActivity.class', b'java-placeholder')
    apk = root/'build.apk'
    with zipfile.ZipFile(apk,'w') as z:
        z.writestr('AndroidManifest.xml',b'compiled-placeholder')
        z.writestr('resources.arsc',b'resources-placeholder')
        z.writestr('classes.dex',b'dex\n035\x00placeholder')
        for name in REQUIRED_LIBS:
            z.writestr('lib/arm64-v8a/'+name,
                       replace.get(name,fake_elf(name)) if replace else fake_elf(name))
        for name in extra:
            z.writestr(name,b'bad-additional-file')
    for name in missing:
        (jni/name).unlink()
    return apk,jni,jar


class A8BuildTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.p=Path(self.tmp.name)

    def test_valid_structural_fixture(self):
        apk,jni,jar=make_fake_tree(self.p)
        self.assertEqual(len(validate(apk,jni,jar)),3)

    def test_reject_missing_sdl_activity(self):
        apk,jni,jar=make_fake_tree(self.p,corrupt_jar=True)
        with self.assertRaisesRegex(ValueError,'SDLActivity'): validate(apk,jni,jar)

    def test_reject_stale_packaged_core(self):
        apk,jni,jar=make_fake_tree(self.p)
        (jni/'libretrodos.so').write_bytes(fake_elf('libretrodos.so')+b'new')
        with self.assertRaisesRegex(ValueError,'differs'): validate(apk,jni,jar)

    def test_reject_x86_core(self):
        blob=bytearray(fake_elf('libretrodos.so'))
        struct.pack_into('<H',blob,18,62)
        apk,jni,jar=make_fake_tree(self.p,replace={'libretrodos.so':bytes(blob)})
        with self.assertRaisesRegex(ValueError,'not ELF64 AArch64'): validate(apk,jni,jar)

    def test_reject_missing_native_library(self):
        apk,jni,jar=make_fake_tree(self.p,missing=('libSDL3.so',))
        with self.assertRaisesRegex(ValueError,'Fresh build missing'): validate(apk,jni,jar)

    def test_reject_extra_abi(self):
        apk,jni,jar=make_fake_tree(self.p,extra=('lib/x86/libretrodos.so',))
        with self.assertRaisesRegex(ValueError,'non-ARM64'): validate(apk,jni,jar)

    def test_reject_proprietary_machine_disk(self):
        apk,jni,jar=make_fake_tree(self.p,extra=('assets/YARDO.raw',))
        with self.assertRaisesRegex(ValueError,'virtual HDD'): validate(apk,jni,jar)

    def test_reject_core_missing_winkit_native_gate(self):
        apk,jni,jar=make_fake_tree(self.p,replace={'libretrodos.so':fake_elf('libSDL3.so')})
        (jni/'libretrodos.so').write_bytes(fake_elf('libSDL3.so'))
        with self.assertRaisesRegex(ValueError,'lacks expected WinKit'): validate(apk,jni,jar)

    def test_patched_upstream_build_has_java_target_and_class_gate(self):
        original='''TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"
-DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF
    cmake --build "$OUT/sdl3-build" -j"$JOBS"
APP_LIBS="$HERE/app/libs"
'''
        patched=patch_build(original)
        self.assertIn('SDL3-jar',patched)
        self.assertIn('SDL_ANDROID_JAR=ON',patched)
        self.assertIn('org/libsdl/app/SDLActivity.class',patched)
        self.assertIn('\\\n        -DSDL_ANDROID_JAR=ON',patched)
        self.assertIn('NDK_HOST_TAG=linux-x86_64',patched)

    def test_patch_rejects_changed_upstream_template(self):
        with self.assertRaisesRegex(ValueError,'expected 1'): patch_build('not-upstream-source')

    def test_build_pipeline_contract(self):
        s=(ROOT/'scripts/BUILD_A8_ANDROID.sh').read_text()
        for term in ('RUN_A8_HOST_TESTS.sh','verify_a8_upstream.py','verify_a8_apk.py',
                     'BUILD_A3_ANDROID.sh','A8-BUILD-SHA256SUMS.txt'):
            self.assertIn(term,s)

    def test_ci_contract_and_no_executed_apk(self):
        s=(ROOT/'.github/workflows/a8-android-arm64.yml').read_text()
        for token in ('workflow_dispatch:', 'if: always()', 'if: success()', 'BUILD_A8_ANDROID.sh',
                      'WinKit311-A8-Android-ARM64-DEVICE-UNVERIFIED'):
            self.assertIn(token,s)
        source_files=[row.split('  ',1)[1] for row in (ROOT/'MANIFEST.sha256').read_text().splitlines()]
        self.assertFalse(any(Path(name).suffix.lower() in {'.apk','.raw'} for name in source_files))

if __name__=='__main__': unittest.main(verbosity=2)
