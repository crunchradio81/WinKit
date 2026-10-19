# A6 — launch-path correction and Android ARM64 CI build recipe

## Source audit

The pinned Retro-DOS frontend's `engine_thread(std::string conf, std::string workdir)` receives
`conf_path`, specifically the generated `cfg_dir/dosbox-x.conf`. It does **not** receive
the configuration contents. A5 called
`winkit_raw::image_matches_profile(conf, disk, fd, bytes)` and would reject a valid
configuration because that validator expects DOSBox-X text with `[autoexec]`.
A5 did not boot any Android emulator and did not uncover this dynamic mismatch.

A second launch incompatibility came from the upstream `if (!g.conf_override.empty())`
branch: it automatically inserts a blank floppy `IMGMOUNT A` before `BOOT` in a
machine config. WinKit's strict offline profile (A3/A5) forbids any second IMGMOUNT.

## A6 implementation

1. Native `winkit_launch_snapshot.h` reads both the frontend-generated active
   profile and prepared `machines/YARDOMOB.offline.conf` as **text**, refusing
   symlinks, hardlinks, nonregular files, NUL, empties and files above 256 KiB.
2. Requires byte-for-byte equality, the existing strict offline parser, matching
   CHS/raw image size and raw-container checks, while the existing exclusive
   guest disk lease is active.
3. Writes a unique 0400-mode per-run launch snapshot under the Android app's
   private `machines` directory. DOSBox-X receives its **filename** via `-conf`.
   After emulator return the snapshot is unlinked. This narrows, but cannot
   eliminate, races with same-UID processes that deliberately rewrite files.
4. Excludes only the named WinKit machine from upstream's automatic blank
   floppy injection. Other Retro-DOS machines keep their original behavior.
5. Keeps the synthesized, GPL-only x86 BIOS fixture and source-only Microsoft-free
   import system from A1–A5. No network permissions, no Tailscale and no live
   ETHNET are enabled in A6.
6. Provides manual GitHub Actions `workflow_dispatch` to attempt an actual SDK
   + NDK build, without touching the Mac. It is a recipe, **not a CI result**.

## Offline proof

`bash scripts/RUN_A6_TESTS.sh` executes actual host C++17 compilation + 38
native assertions against a disposable A4 HDD, Python source-patch tests,
previous source gate tests, and the A6 archive file-integrity verifier.

`A6_INCLUDE_A5=1 bash scripts/RUN_A6_TESTS.sh` runs the full A5 suite too.

## Unverified gates

- Android NDK cross-build of the integrated native SDL3/DOSBox-X core.
- Android Gradle build and signed debug APK packaging.
- A real guest BIOS boot (even for the GPL-only fixture).
- DOS 6.22 / WFW 3.11 boot and application compatibility.
- NE2000, live ETHNET peer connection, WinPopup/file sharing over Tailscale.

The Mac RC24.3/YARDO/YARDO-B originals and their disks/backups were not accessed
or changed. No proprietary Microsoft bytes are part of this package.
