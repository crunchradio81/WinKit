#!/usr/bin/env python3
"""Check native dependency closure inside the isolated A31 ARM64 APK.

Native dependencies must either be packaged in the APK or be part of Android's
API-28 system library set. A compiler/linker success does not establish boot.
"""
import pathlib
import re
import subprocess
import sys
import tempfile
import zipfile

EXPECTED = {"libmain.so", "libSDL2.so", "libc++_shared.so"}
MEDIA_EXTENSIONS = (".raw", ".img", ".vhd", ".vhdx", ".vdi", ".iso", ".qcow2", ".vmdk")

def elf_needed(readelf: pathlib.Path, binary: pathlib.Path) -> set[str]:
    txt = subprocess.check_output([str(readelf), "-d", str(binary)], text=True)
    return set(re.findall(r"Shared library: \[([^\]]+)\]", txt))

def verify(apk: pathlib.Path, readelf: pathlib.Path, sysroot: pathlib.Path) -> None:
    if not apk.is_file() or not readelf.is_file():
        raise RuntimeError("APK or NDK readelf missing")
    system_dirs = (sysroot / "usr/lib/aarch64-linux-android/28",
                   sysroot / "usr/lib/aarch64-linux-android")
    with zipfile.ZipFile(apk) as zipped:
        files = set(zipped.namelist())
        libs = {x.rsplit("/", 1)[-1] for x in files
                if x.startswith("lib/arm64-v8a/") and x.endswith(".so")}
        if not EXPECTED.issubset(libs):
            raise RuntimeError("Missing packaged ARM64 runtime: " +
                               repr(sorted(EXPECTED - libs)))
        if any(x.startswith("lib/") and not x.startswith("lib/arm64-v8a/")
               for x in files):
            raise RuntimeError("Unexpected other ABI inside A31 APK")
        if any(x.lower().endswith(MEDIA_EXTENSIONS) for x in files):
            raise RuntimeError("Refusing bundled user guest disk media")
        with tempfile.TemporaryDirectory() as tmp:
            for name in sorted(libs):
                binary = pathlib.Path(tmp) / name
                binary.write_bytes(zipped.read("lib/arm64-v8a/" + name))
                header = subprocess.check_output(
                    [str(readelf), "-h", str(binary)], text=True)
                if "AArch64" not in header:
                    raise RuntimeError(name + " is not AArch64")
                needed = sorted(elf_needed(readelf, binary))
                for dependency in needed:
                    if dependency in libs:
                        continue
                    if dependency == "libc++_shared.so":
                        raise RuntimeError("Android C++ runtime must be packaged")
                    if not any((d / dependency).is_file() for d in system_dirs):
                        raise RuntimeError(name + " needs unavailable " + dependency)
                print("ELF NEEDED PASS:", name, needed)
    print("A31_APK_ELF_CLOSURE=PASS; startup and DOS/WFW boot unverified")

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: A31_VERIFY_APK_RUNTIME.py APK LLVM_READELF NDK_SYSROOT")
    try:
        verify(*map(pathlib.Path, sys.argv[1:]))
    except (RuntimeError, subprocess.CalledProcessError) as e:
        raise SystemExit(str(e))
