# A30: recover editable emulator source without losing A26/A29

A30 is a **separate exploratory branch** (`android-a30-upstream-core-source`).
The preserved A26 debug APK and A29 transfer importer remain unchanged.

## Confirmed history

GitHub Actions A26 run 39541955532 compiled original native SDL3 DOSBox-X
revision `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11` on October 21, 2026.
This was successful compilation, **not** a successful DOS 6.22 or Windows for
Workgroups 3.11 guest boot. The user observed keyboard toggling back to the
launcher; that failure still requires native investigation.

The custom core is a Git *submodule*, not part of the public parent
`CrownParkComputing/Retro-Dosbox`; the source submodule URL is currently 404.
An A26 APK or GitHub job log is **not** editable source.

## A30 public-source fallback

- Git remote: `https://github.com/joncampbell123/dosbox-x.git`
- Exact audit revision: `029bd65c5dcc7cdc7f2e14e35278620d9707d969`
- License: upstream COPYING (GPL; audit other bundled code separately)
- An upstream `--enable-sdl3` configure option is documented, **but this does
  not imply a functional Android embedded SDL3 DOSBox-X port**.
- The customized plain-C Retro-DOS host API `retrodos_host.h` is not part
  of stock DOSBox-X. The original Retro-DOS parent relies on that host API,
  so swapping its `core` submodule for stock upstream is an invalid shortcut.

A separate CI workflow performs a Git partial clone of the public pinned
revision and emits an auditable source-inventory JSON report. This is a
source-provenance milestone; no original proprietary disk content, compiled
emulator, or guest execution is uploaded. It does NOT publish the new source
tree into WinKit yet. To host editable source under WinKit, first review
inventory, licenses, object sizes and any third-party firmware/assets, then
import a vetted Git snapshot to `vendor/` on this dedicated branch. Never
copy binary ROMs of uncertain license automatically.

## Required next engineering milestones

1. Source ownership: import reviewed original core if recovered; otherwise
   vendor a pinned public upstream source snapshot into the A30 branch.
2. Native host interface: implement the host bridge expected by Retro-DOS
   (`retrodos_host_run`, framebuffer/window/input/lifecycle API) on our
   source-owned emulator.
3. Android NDK build: compile from source with selected SDL3 revision;
   verify executable ABI, package identity and native export symbols.
4. Debug Kbd: keep emulator thread alive while toggling on-screen keyboard;
   exercise window ownership and SDL event queue under lifecycle tests.
5. Boot a synthetic free boot sector image, then a private Mac YARDOMOB
   transfer with exact raw-disk geometry. LAN remains disabled until separate
   WFW computer identity and NIC MAC are verified.

Do not claim A30 is device-tested or the core is imported until these
checkpoints are demonstrated in CI and hardware.
