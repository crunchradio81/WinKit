# A31 Android ARM64 SDL2 diagnostic build

A31 is an isolated Android branch, derived from A30. The macOS RC24.8 source and private guest images must not be modified or included in CI artifacts.

## Pinned provenance

- DOSBox-X upstream: joncampbell123/dosbox-x at `029bd65c5dcc7cdc7f2e14e35278620d9707d969`.
- The A30 vendor import omitted four text-only inputs listed in upstream `AC_CONFIG_FILES`. A31 restores the exact upstream blob data under `vendor/dosbox-x/contrib/` and `vendor/dosbox-x/make-rpm.sh.in`.
- SDL2 upstream: libsdl-org/SDL, release-2.32.10 commit `5d249570393f7a37e037abf22cd6012a4cc56a71`, fetched from its public repository and compiled by the Android ARM64 NDK. Git history is pinned by SHA, not mutable branch name.
- Upstream issue #6148 documents unresolved SDL3 porting problems; SDL2 is the initial boot-oriented integration target.

## Diagnostic gates

1. Verify the unchanged A30 upstream revision, restored Git blob IDs, and full WinKit source SHA256 manifest.
2. Build Android ARM64 SDL2 shared library via NDK CMake; check ELF machine.
3. Copy the vendored DOSBox-X source into a disposable CI build directory, run Autotools, and cross-configure using Android SDL2.
4. Compile a real DOSBox-X C++ unit to an AArch64 ELF object; capture errors and object architecture.
5. Upload **text logs only**. Never upload SDL2 binaries, guest disks, a misleading APK, or private installation media.

This is not a complete emulation core link, Android Activity integration, DOS/Windows boot, or verified audio/input/storage. A successful CI probe only authorizes moving to a genuine SDL2 Android emulator link and runtime lifecycle integration. Previously built A26 Android packages are not update-safe; do not uninstall to test A31.

## A31 multi-subsystem compile matrix

The dedicated `scripts/A31_COMPILE_MATRIX.sh` stage tests fourteen separate upstream translation units under Android NDK Clang: CPU instructions/flags/paging, machine memory, VGA and VGA-related display state, Sound Blaster, audio mixer, keyboard, BIOS, INT10, disk BIOS, FAT disk drives, DOS kernel, and FPU.

All compile checks must produce true AArch64 ELF objects and report per-file errors; the stage fails if any fail. Logs are text-only and record exact failures. A passing matrix remains a **partial core compilation**, not evidence that the full emulator links, SDL2 activity integration works, or guests boot. The next milestone is building complete component archives and a linked JNI library with runtime device verification.
