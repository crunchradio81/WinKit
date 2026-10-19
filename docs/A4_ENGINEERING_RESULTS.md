# WinKit Android A4 — Offline BIOS/HDD Boot-fixture research and source changes

**Scope:** Current dated research and offline source engineering; no Mac, Android phone, Tailscale, guest DOS/Windows media, or running guest accessed.

## What was built

- `fixtures/winkit_bios_boot.S`: real 16-bit x86 BIOS boot sector **source**.
  When executed by a compatible x86 PC BIOS, it attempts to display the banner
  `WINKIT A4 BIOS HDD BOOT OK - NO MICROSOFT FILES` using `INT 10h`, then halts.
  The x86 code has been assembled and disassembled; **not yet executed in DOSBox-X**.
- `scripts/BUILD_A4_BOOT_FIXTURE.sh`: generates a *new*, never-overwritten,
  4,128,768-byte synthetic HDD with boot code at LBA 0 and a `55 AA`
  boot signature, corresponding 512,63,16,8 CHS, complete offline
  `YARDOMOB.offline.conf` plus existing A3 marker. Generated files are never
  shipped in this source ZIP; the script creates them on demand in a private
  temporary/test folder. It does not need proprietary operating systems.
- `scripts/inspect_a4_disk.py`: strictly read-only first-sector MBR inspection,
  including partition-table plausibility/overlap checks and byte-length vs
  optional geometry comparison. Its results are *evidence*, never proof of
  a bootable OS, a healthy partition filesystem or unique network identity.
- `overlay/kotlin/BootSectorEvidence.kt`: independent JVM-portable Android-side
  first-sector probe. The Android setup import flow now surfaces the result
  after the image's SHA-256 has been verified. Test fixture identification
  is advisory; it is not the user-supplied DOS/Windows install.
- `host-tests/a4/*` + `scripts/RUN_A4_TESTS.sh`: offline regression checks for
  assembler output, fixture layout, non-overwrite policy, malformed partition
  fields, native+Kotlin offline config-policy agreement, injected Ethernet
  rejection, and inherited A3 controls.

## Reproducible commands (host only)

```
bash scripts/RUN_A4_TESTS.sh
```

Generate a **disposable synthetic diagnostic image**, not into any Mac guest folder:

```
TMP="$(mktemp -d)"
bash scripts/BUILD_A4_BOOT_FIXTURE.sh --out "$TMP"
python3 scripts/inspect_a4_disk.py "$TMP/machines/YARDOMOB.raw" --geometry 512,63,16,8
```

The generated image has no DOS, WFW, FAT partition, TCP/IP, NetBEUI or emulated NIC.
Never mistake the fixture for a valid user-supplied Windows installation.

## Build and emulator acceptance remains explicitly unverified

1. Android NDK/SDK, Gradle and SDL3/Retro-DOS dependencies are **not installed**
   in the source-development container. Toolchain direct downloads were
   unavailable there. No ARM64 `libretrodos.so` or Android APK was produced.
2. The **same pinned upstream** SDL3 DOSBox-X app/core from A3 remains the
   selected target. Build recipe: `scripts/BUILD_A3_ANDROID.sh` (not run).
3. A real emulator/BIOS needs to load the fixture from LBA 0, execute its
   machine code, and visibly display the diagnostic banner. This has not
   happened yet. Byte-level inspection is not equivalent to executing it.
4. Only then does a **separate, independently exported** user-owned DOS 6.22 /
   WFW 3.11 guest boot. Preserve exact disk geometry and never overwrite
   original Mac guest disks. Windows networking stays completely disabled
   until copied guest name `YARDOMOB` and unique emulated MAC are verified.
5. The experimental ETHNET/Kotlin transport remains unconnected. Tailscale
   is later a *network transport*, not a Windows remote-desktop display.

## Safety and distribution

This cumulative ZIP distributes only sources and scripts, **no** generated
`YARDOMOB.raw`, Microsoft installation media or original/private guest images.
All A4 test artifacts are generated in temporary directories and discarded.
The synthetic sector is freshly authored and GPL-2.0-or-later, not borrowed
from MS-DOS or another operating system.

## A4 measured host test result

The completed default `bash scripts/RUN_A4_TESTS.sh` run passed:

- 6 Python synthetic-image/layout and non-overwrite tests.
- 1 Kotlin + 1 C++ acceptance of the generated offline config.
- 18 Kotlin first-sector probe assertions.
- 13 A3 + 6 A2 native boot-policy assertions, and the native image-lease test.
- 5 upstream-source patch fixtures + 5 package/source contract checks.
- 4 earlier Python boot-config regression tests and the 112-file A4 manifest.

The earlier *combined* A4 runner was interrupted during the inherited Kotlin
regressions, after printing some A3 successes; it is **not** reported as a
completed full inherited test. `A4_FULL_REGRESSION=1 bash scripts/RUN_A4_TESTS.sh`
opts in to repeating the slower comprehensive A3 suite on an unconstrained host.

No core build, Android virtual machine launch or live network acceptance is
within these tests.
