#!/usr/bin/env python3
"""Patch only the disposable, already checksum-audited Android build copy.
Never alter the pinned vendor/dosbox-x checkout or A31 baseline.
The A32 imageDisk adapter must have been compiled and linked into libmain.so.
"""
from pathlib import Path
import os

root = Path(os.environ["A31_ROOT"]) / "dosbox-source"
path = root / "src/ints/bios_disk.cpp"
data = path.read_text()
name = "void BIOS_SetupDisks(void) {"
needle = "    swapping_requested = false;\n}"
assert data.count(name) == 1, "unexpected DOSBox-X BIOS function revision"
start = data.index(name)
pos = data.find(needle, start)
assert pos >= 0 and pos - start < 5000, "BIOS initialization anchor not found"
assert data.count("winkit_a32_attach_pending_disk") == 0
# Declare C ABI entry point at global scope; invoke only after disk tables exist.
data = ('extern "C" void winkit_a32_attach_pending_disk(void);\n' + data)
start = data.index(name)
pos = data.find(needle, start)
data = data[:pos] + data[pos:].replace(
    needle,
    "    swapping_requested = false;\n"
    "    /* A32: stage read-only SAF HDD only after BIOS INT 13h setup. */\n"
    "    winkit_a32_attach_pending_disk();\n}",
    1
)
path.write_text(data)
assert data.count("winkit_a32_attach_pending_disk") == 2
print("A32: disposable BIOS_SetupDisks hook installed; source checkout untouched")
