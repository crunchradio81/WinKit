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

## CD-ROM source-import correction

The first subsystem matrix compiled 10/14 Android AArch64 source objects. Four BIOS/DOS/disk files failed because the original A30 import omitted `vs/sdl/src/cdrom/compat_SDL_cdrom.h`, which the pinned DOSBox-X SDL2 CD-ROM API requires. A31 restores the **exact pinned upstream LGPL 2.1+ declarations** and verifies its Git blob ID. This header alone does not implement CD-ROM runtime functions: the SDL2 CD-ROM backend and real ISO/media functionality still require a tested host implementation or stubs with explicit feature limitations. No guest data or ROMs are imported.

## Full upstream CPU archive gate

Following the object matrix, `scripts/A31_CPU_ARCHIVE_PROBE.sh` invokes the generated upstream Autotools `src/cpu/Makefile` to build `libcpu.a` using the Android cross-compiler. The gate verifies that the archive has at least ten object members and that an extracted member is AArch64 ELF. Logs list all archive members and failures. It is an architecture/build acceptance gate only; it neither links an Android executable nor runs a DOS guest.

## Boot-critical subsystem archive targets

The next A31 validation now invokes upstream `make` targets for `ints/libints.a`, `dos/libdos.a`, `fpu/libfpu.a` and `hardware/libhardware.a` via `scripts/A31_SUBSYSTEM_ARCHIVES.sh`. These archives are *individual source compilation tests*: even all passing does not produce a usable emulator until the linker resolves their dependencies and Android SDL activity, audio and filesystem lifecycles work. Each target writes an independent log to identify missing source, dependencies, and Android-specific portability errors.

## A31 native archive dependency recovery

First non-CPU archive run: `libints.a` (29 members) and `libfpu.a` (3 members) compile successfully for Android ARM64. DOS compilation encountered a missing `vs/sdl/src/cdrom/SDL_cdrom.c`; hardware compilation encountered `snd_pc98/sound/getsnd/getsndmn.mcr`. Restored both from exact pinned upstream along with required `SDL_syscdrom.h`, with Git blob verification. The CD-ROM source may still rely on unimplemented platform callbacks at **link/runtime** time. A successful archive compilation does not certify CD/DVD support. No vendor binary media or guest OS images are included.

## Android CD-ROM backend selection

The upstream SDL2 CD-ROM integration selects `linux/SDL_syscdrom.c` when `LINUX` is defined, but Android uses Bionic and should not perform Linux host physical-drive ioctl operations. A31 makes a **temporary Android-only** compile-copy adjustment so Android chooses the upstream dummy physical-CD backend (exact pinned source, GPL/LGPL notices preserved). Guest ISO/CHD image support remains distinct from host optical-drive access and still needs runtime verification. PC-98 audio compilation also now includes the upstream `getsndst.mcr` companion macro. The original vendored `src/dos/cdrom.cpp` is unmodified.

## A31 further source completion and first full link probe

The A31 completion matrix confirmed 17/18 extra archives; `src/misc/savestates.cpp` referenced missing in-tree upstream MiniZip source. Seven exact text-source blobs from pinned DOSBox-X commit `029bd65c5dcc7cdc7f2e14e35278620d9707d969` were restored under `vendor/dosbox-x/vs/zlib/contrib/minizip/`. CI verifies original Git blob identities. These are original upstream sources, not arbitrary new MiniZip versions.

`scripts/A31_FULL_LINK_PROBE.sh` now attempts `make -C src dosbox-x` with Android cross-compiled Makefiles *after* archive checks. It records native compiler/linker failures or checks ELF architecture when successful. A linked Android AArch64 executable still does **not** establish an APK, SDL Activity integration, keyboard lifecycle, sound, synthetic DOS boot, or WFW boot.

## Full native link probe on Android

The verified A31 archive matrix has now compiled 19 of 19 additional upstream static archives, including xBRZ. The first executable link then identified only an absent `main`: Android's SDL2 headers name the native entry `SDL_main` for an SDLActivity host. The diagnostic link script now compiles a **temporary** two-line native forwarding adapter (`main` → `SDL_main`) and links it with upstream's generated Android linker command. This does **not** implement an Android SDL Activity, package an APK or prove boot. Later runtime integration must use the proper Android SDL lifecycle rather than relying on this shell executable adapter.

## A31 isolated SDL2 Activity packaging probe

`android/a31-runtime` is an independent Android Gradle 8.9 application with package ID `org.winkit.android.a31smoke`; it cannot replace the existing WinKit Android package. The Android app has no Internet permission and contains no guest operating-system media. In CI, the pinned upstream SDL2 Android Java source is copied from commit `5d249570393f7a37e037abf22cd6012a4cc56a71` at build time. The Java runtime is matched to the exactly pinned native libSDL2.so built earlier.

For shared-library packaging, the DOSBox-X archives are cross-built with `-fPIC`, then linked as `libmain.so` using an undefined-root request for `SDL_main` (rather than using the diagnostic executable's `main` forwarding adapter). The pipeline verifies AArch64 ELF, exported SDL_main, and a needed libSDL2.so dependency. Gradle packages both libraries into a distinct ARM64 debug smoke APK and checks the APK's ZIP members and absence of guest disk files.

Passing this build proves only package assembly and native symbol availability. It does **not** prove that an Android activity can initialize and sustain DOSBox-X, that keyboard/audio/video work, that guest disks are imported, or that DOS 6.22 and WFW 3.11 boot. A debug smoke APK must not be installed as an upgrade to an existing A26/A29 working package. Physical device tests and native crash capture remain required.

## A31 packaged C++ runtime dependency closure

The initial APK build from Actions run 39974565265 omitted `libc++_shared.so`, despite `libmain.so` declaring it in `DT_NEEDED`. Android does not provide NDK's shared C++ runtime as a system library.

The A31 CI now copies `libc++_shared.so` for ARM64 from the **same pinned NDK revision** as the emulator build. `scripts/A31_VERIFY_APK_RUNTIME.py` inspects all packaged AArch64 native ELF libraries and their `DT_NEEDED` dependencies. Every dependency must be either packaged in the APK or provided by Android's API-28 system NDK stubs. The checker explicitly forbids treating `libc++_shared.so` as an Android system library, checks for other ABIs and guest-disk media, and verifies the independent smoke application ID.

Success only verifies packaging, ELF dependencies and the Android APK build. It does **not** establish device startup, SDL initialization, keyboard operation, sound, DOS or Windows for Workgroups boot.

## A31 black-screen physical-device outcome and fast Java-first diagnostics

A user installed the first properly packaged A31 Android smoke APK and reported a persistent **black screen**. This is evidence that Android opens the activity, **not** proof that SDL, DOSBox-X rendering, guest DOS, or WFW starts. A31 has not configured a guest disk.

To avoid rebuilding the full 19-archive native core for Java startup diagnostics, `.github/workflows/a31-android-runtime-fast.yml` pins the previously successful run 39980004155 and reuses the exact verified native ELF libraries (`libSDL2.so`, `libmain.so`, `libc++_shared.so`) from its APK artifact. The fast workflow fetches nine matching SDL2 Android Java files at pinned revision and builds only the diagnostic APK. The full native workflow no longer triggers for Java app-only edits or manifest updates.

A31 now opens a **Java-first launcher** with a native library load check, explicit launch button, and persisted lifecycle checkpoints around SDLActivity.onCreate/onResume/onPause/onDestroy/focus. A small noninteractive overlay distinguishes an active Android Java surface from a completely blank startup. Copy and Share report require no network permission or privileged logcat access. The report contains only local runtime metadata and recent lifecycle checkpoints, not guest images. Native SIGSEGV, crashes before Java callbacks, or SDL main-thread hangs may still require device ADB logcat.

No guest image imported; black-screen root cause NOT YET ESTABLISHED. The diagnostic APK uses the same A31 smoke package ID, but CI debug signing can differ across runs; uninstall ONLY the A31 smoke package if Android rejects an update.

## Device test: 2026-10-23 Samsung SM-A366U Android API 36

The user confirmed A31 `libc++_shared.so`, `libSDL2.so` and `libmain.so` all pass Java `System.loadLibrary`. SDL Activity `onCreate` and `onResume` completed, and the diagnostic Java overlay was visible above a black SDL rendering area. Focus/pause/destroy transitions were observed on exit. No guest disk was mounted. The pre-existing copy of `/storage/emulated/0/YARDOMOB.raw` was not accessed. These observations **do not** prove native `SDL_main` entry or a rendered frame.

A31 versionCode 33 samples the pinned SDL2 `mSDLThread` (protected) at 1, 3.5, 8.5 seconds and during pause/destroy, recording thread existence, state, liveness, SDL lifecycle flags and up to five Java stack frames. The Java overlay displays the status below the Android notification icons. This lets a new device report distinguish “SDLThread never created,” “thread alive in nativeRunMain,” and “thread stopped.” If SDLThread is alive in nativeRunMain with no video, further **native** DOSBox-X startup/frame checkpoints are required; this Java probe cannot pinpoint a C++ initialization hang. Full DOSBox-X rebuild remains unnecessary for these Java-only checks.

The prior black-screen test **was not a crash report**. No assertion about DOSBox-X engine rendering is warranted yet.
