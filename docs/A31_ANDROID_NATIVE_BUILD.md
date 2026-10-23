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

## Physical-device render and escape milestone (A31 v34)

Samsung SM-A366U Android API 36 displays a Java overlay and reports SDLThread ALIVE/RUNNABLE, RESUMED/RESUMED, but the native DOSBox-X view stays black; the system Back navigation is consumed. This confirms Android/SDL thread existence, not a DOSBox-X video frame.

A31 v34 introduces an on-screen **RETURN TO DIAGNOSTICS** Java Button (with Android 13+ back callback and legacy Back override), deliberately bringing the diagnostic launcher to the foreground without blocking on SDLActivity.onDestroy(), which synchronously joins the native SDLThread. The paused SDL Activity may remain alive; this is a diagnostic escape, not a complete guest shutdown feature.

The launcher has a separate **TEST NATIVE SDL2 COLORS** action. This uses `A31RenderProbeActivity` and its own native `libprobe.so`, compiled from `scripts/A31_SDL_RENDER_PROBE.c` with the exact pinned upstream SDL2 headers and Android NDK. Its SDL_main calls SDL_Init, SDL_CreateWindow, SDL_CreateRenderer, SDL_RenderPresent, drawing a purple background and cyan/yellow bars. JNI reports startup stage and frame count to the Java overlay and app-private diagnostic log. A visible colored surface would establish SDL2 rendering independently of DOSBox-X; an increasing frame count with a black display indicates SDL window/surface presentation needs work. This is **not** a DOS boot test.

Keep `/storage/emulated/0/YARDOMOB.raw` untouched and unmounted until native video startup is established. No Windows or DOS media is included. Only the fast CI job builds the new tiny probe; no DOSBox-X core rebuild required.

## A31 v35 native DOSBox-X entry tracer after confirmed SDL2 rendering

The physical Samsung Android API 36 test of v34 showed **real native SDL2 purple/cyan/yellow rendering**, stage=5, frames increasing to 318, and the Java RETURN control working. SDL2 video/presentation is verified. The persistent DOSBox-X black screen is therefore a DOSBox-X-specific startup/render problem, not a generic SDL2 video failure.

Without recompiling DOSBox-X, A31 v35 leaves the originally validated `libmain.so` **unchanged** inside the APK and installs a separate `libbridge.so`. Only the DOSBox-X Activity selects `libbridge.so` as SDL2's entry library. Its `SDL_main` exports checkpoints 1 (bridge called), 2 (original `libmain.so` opened), 3 (entered genuine DOSBox-X SDL_main), 4 (DOSBox-X SDL_main returned) and negative values for loader failures. A small JNI API surfaces these stages in the overlay and report. The bridge also redirects its own stdout/stderr and the subsequent DOSBox-X output to `a31-dosbox-stdout.txt` under Android's app-private files directory, and the diagnostic report includes only its last 9KB.

The independent native SDL2 color probe is unchanged. No guest image is mounted. The report shows native-entry checkpoints; stage 3 does **not** prove DOSBox-X initialized video. Any further source-level instrumentation should target the specific DOSBox-X initialization block indicated by this output. For test: tap '3 — Launch DOSBox-X SDL2', leave black screen ~10 seconds, tap Return, Copy Startup Report. A31 v35 remains a diagnostic package, not a boot-verified release.

## v36: confirmed Android black-screen blocker and noninteractive working-directory fix

The user's v35 report from Samsung SM-A366U Android API 36 confirmed `DOSBox entry stage=3`: the real upstream DOSBox-X `SDL_main` was executing. Its captured standard error repeatedly showed **"You have not selected a valid path. Do you want to run DOSBox-X with the current path as the DOSBox-X working directory? ... y/n:"**. This is an interactive desktop working-directory selection loop with no Android stdin. It explains the persistent black screen without requiring a renderer rewrite; no boot is yet verified.

The pinned upstream `vendor/dosbox-x/src/gui/sdlmain.cpp` supports an explicit `-nopromptfolder` CLI switch (`optname == "nopromptfolder"` sets `opt_promptfolder=0`). The separate `scripts/A31_DOSBOX_ENTRY_BRIDGE.c` now sets CWD to SDL2's existing app-private internal storage path with `chdir()`, validates it, and appends `-nopromptfolder` to the forwarded argv before calling the **unchanged** original DOSBox-X `SDL_main`. The bridge emits explicit errors for inaccessible working directories and prints the chosen directory and switch to its private diagnostic log. The fast workflow checks that this switch remains supported by the pinned source before compiling only the bridge and Java packaging; no full emulator core rebuild is needed.

**v36 acceptance:** install the new standalone diagnostic APK; tap "3 — Launch DOSBox-X SDL2", observe whether the DOSBox-X screen appears, then Return and Copy Startup Report. The previous repeated y/n folder prompt should disappear. Further failures must be diagnosed from the new log, not assumed fixed. `/storage/emulated/0/YARDOMOB.raw` is intentionally unmounted and unchanged. DOS 6.22 and WFW 3.11 have not yet booted.

## v37 — Android SDL2 video backend instead of desktop X11

The v36 physical-device report confirms `libmain.so` enters actual DOSBox-X `SDL_main`, successfully selects `/data/data/org.winkit.android.a31smoke/files` as its CWD, and suppresses the folder prompt. Startup reaches SDL initialization, then terminates with `E_Exit: Can't init SDL x11 not available`. After failure, Android's Bionic reports `FORTIFY: pthread_mutex_lock called on a destroyed mutex`; the latter is not proved to be an independent root cause.

The pinned `sdlmain.cpp` uses `putenv("SDL_VIDEODRIVER=x11")` whenever the build has LINUX defined and the environment variable is absent. Our cross-target configuration defines LINUX even on Android. Its `[sdl] videodriver` configuration default is empty. In v37 the small native Android entry bridge sets `SDL_VIDEODRIVER=android` using `setenv(...,1)` **before** calling the unchanged DOSBox-X `SDL_main`. No full emulator recompile is required. CI verifies these source assumptions against the pinned original source.

On-device acceptance: launch Option 3 and check for DOSBox-X built-in graphical startup. If it closes or remains black, return to the Java diagnostics (reopen the app if necessary) and copy the output. The expected v37 log must show `[A31 ENTRY] SDL_VIDEODRIVER=android`; any next DOSBox-X error should be diagnosed from that output. SDL2 color rendering was already physically verified on this device. The existing `/storage/emulated/0/YARDOMOB.raw` remains untouched, unmounted, and is not shipped in any build. DOS 6.22 / WFW 3.11 boot is not yet verified.

## A31 v38 — Choose and inspect existing YARDOMOB.raw without changes

Samsung SM-A366U Android API 36 screenshots conclusively show the real DOSBox-X 2026.10.01 built-in Z: shell rendered on-device (v37). No guest DOS 6.22 or WFW 3.11 boot has been attempted or verified. The existing user-owned image resides at `/storage/emulated/0/YARDOMOB.raw`.

A31 v38 adds an offline Android Storage Access Framework `ACTION_OPEN_DOCUMENT` picker for selecting the existing image. No whole-device storage permission is requested. Its inspector opens only with mode `"r"`, checks the provider supplies a seekable file, checks size is a multiple of 512 bytes, reads the first sector and optionally the first partition's boot sector, and reports MBR signature/partition metadata. Only `FLAG_GRANT_READ_URI_PERMISSION` is persisted; no write grant, guest mount, file copy, repair, or conversion is attempted. The selected document's URI and inspection result remain app-private. A Forget button releases persisted read access and keeps the original file intact.

Android DocumentsUI content URIs are **not** DOSBox-X filesystem paths. Random-access guest mounting/writes are a separate implementation milestone and must not be implied by successful inspection. This stage explicitly avoids copying the user's potentially multi-gigabyte RAW image into the APK or app data.

Device test: choose the existing YARDOMOB.raw in Android's Files picker; then Copy Startup Report and check image length and MBR/partition metadata before implementing actual guest boot. Avoid uninstalling the A31 smoke app after selection because doing so revokes app-owned document grants.

## A31 v39 — native read-only Android file-descriptor interoperability

The user confirmed `YARDOMOB.raw` is 2,147,475,456 bytes / 4,194,288 sectors, with MBR signature 55 AA, an in-range type 0x06 partition at LBA 63 and a signed partition boot sector. These facts establish structure, **not guest boot**.

A31 v39 adds a separate **TEST NATIVE DISK ACCESS (READ ONLY)** launcher action. It reopens only the previously selected Storage Access Framework URI with mode `"r"`, borrows the live descriptor into `libbridge.so` and calls `pread` against the first two relevant sectors. It then tests `fopen("/proc/self/fd/N", "rb")` against the same descriptor, while still keeping the Java-granted descriptor open. This determines whether normal DOSBox-X stdio image handling can access this SAF file directly. Results and errors are persisted in the Copy Startup Report.

The native code never opens the image for writing, never issues `pwrite`, never mounts a DOSBox-X drive or boots guest code and never copies the two-gigabyte image. Only sector signatures and partition offsets are inspected. If `/proc/self/fd` reopening fails on the device, the subsequent implementation must integrate a descriptor-backed sector transport; it must not silently copy or corrupt the image. The built-in DOSBox-X shell is already physically verified on the Samsung SM-A366U running Android API 36.

The v39 acceptance sequence is: install APK; existing disk-selection grant may survive an in-place update; tap **5 — TEST NATIVE DISK ACCESS (READ ONLY)**; copy startup report. Device success of the stdio path is not assumed from CI.
