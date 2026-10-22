#!/usr/bin/env python3
"""Small offline A30 source-import safety tests (does not need DOSBox-X checkout)."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("source_vendor", ROOT / "scripts" / "a30_vendor_upstream_source.py")
vendor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vendor)

class A30ImportPolicy(unittest.TestCase):
    def test_known_revision_and_separate_vendor_path(self):
        self.assertEqual(vendor.PIN, "029bd65c5dcc7cdc7f2e14e35278620d9707d969")
        self.assertEqual(str(vendor.DEST), "vendor/dosbox-x")

    def test_source_paths_accepted(self):
        for name in ["COPYING", "configure.ac", "src/dosbox.cpp",
                     "src/cpu/core_normal.cpp", "include/dosbox.h",
                     "tests/example.cpp", "src/libs/libchdr/lzma/LICENSE"]:
            with self.subTest(name=name):
                self.assertTrue(vendor.allowed(name))

    def test_proprietary_and_binary_paths_rejected(self):
        for name in ["ROMs/rom.bin", "docs/Windows 3.1/driver.zip",
                     "contrib/fonts/UnifontExMono.ttf", "vs/sdl2net/SDL2.dll",
                     "build-scripts/mingw/toolchain.7z", "src/dos/exepackv1.bin",
                     "src/libs/zmbv/unused-source-files.zip",
                     "src/readme.pdf", "../secret.txt",
                     ".git/config", "src/.git/config",
                     "src/example.raw", "include/toolkit.png"]:
            with self.subTest(name=name):
                self.assertFalse(vendor.allowed(name))

if __name__ == "__main__":
    unittest.main()
