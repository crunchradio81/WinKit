# A2 Android build / integration guide (no Mac runtime testing required)

## 1. Linux host dependencies

Target: Android ARM64 (`arm64-v8a`), Android API >=28. Recommended host for the pinned
third-party shell script: Linux x86_64. You need Git, Python 3, Java 17, Gradle 8.9,
Ninja, autoconf, automake, libtool, GNU make, pkg-config, CMake, Android SDK
(platform API 36 / build-tools), NDK **28.2.13676358**, and Internet to clone the
pinned upstream source & SDL 3.2.20. Kotlin plugin is 2.0.21, Android Gradle plugin
8.7.3 in the pinned upstream Android build.

No Android SDK/NDK existed in the execution container that produced this ZIP.

## 2. Source-only checks

Extract ZIP then `cd WinKit311-Android-A2`.

```
bash scripts/RUN_A2_OFFLINE_TESTS.sh
```

The `android/` folder at package root is **A1 historical standalone source**. Do NOT
run Gradle there for the A2 integrated application. `scripts/BUILD_A2_ANDROID.sh`
builds the pinned upstream `Retro-Dosbox/android` after installing the overlay.

## 3. Build command (Linux x86_64)

```
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export ANDROID_NDK="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
bash scripts/BUILD_A2_ANDROID.sh
```

The script performs these stages, each of which must succeed:

1. Checkout parent git commit `65e8c67ed4d8e5aacabc4b96337e8d47ac92d720` and
   core submodule commit `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11`.
2. Apply patch only when upstream checkout is pristine and exact revision matches.
3. Copy four Kotlin sources into upstream `com.crownparkcomputing.retrodos.winkit`.
4. Add strict offline machine discovery to upstream SDL3 frontend `refresh()`.
5. Turn off Internet/Network State permissions, disable Android auto-backup, give app
   dedicated `org.winkit.android.a2` ID; make importer the launcher.
6. Run upstream `android/build-core.sh` with Android NDK to build `libretrodos.so`.
7. Check library is actually AArch64 ELF, then run Android Gradle debug build.
8. Confirm `app-debug.apk` exists and copy to `_build_outputs/` as **unverified**.

No Gradle wrapper is bundled by upstream. Use local Gradle 8.9. If any source SHA,
Android tools, source patch anchor, shared library or APK check fails, do not assume
anything was built. Build products are **excluded** from this source ZIP.

The upstream build script uses GNU shell tools and a Linux x86_64 toolchain by default;
A2 source patch includes an NDK host-tag switch for Darwin but a complete native
macOS host build is **not validated** (do not test Mac installation for this milestone).

## 4. On-device future acceptance — not yet performed

1. Confirm debug APK installs without colliding with an existing Retro-DOS app (A2
   has a distinct application ID). Android runtime must load SDL3 and libretrodos.
2. Install independent stopped-clone `YARDOMOB.raw` through Storage Access Framework
   picker with its **own export manifest's SHA-256**.
3. Select actual working Mac DOSBox-X config text via picker and enter precise original
   source image pathname referenced by its HDD IMGMOUNT line. Config conversion
   rejects ambiguous or unsupported boot sequences. If it rejects: inspect real config
   and improve converter, rather than guessing CHS or enabling network.
4. Inspect converted offline config in app, acknowledge boot commands, open local
   emulation library, choose `WinKit 3.11 - YARDOMOB (OFFLINE)` entry.
5. Confirm local DOS 6.22 boot and then WFW 3.11 Program Manager; capture screenshots,
   frame input, sound and device logs for follow-on fixes. Guest boot NOT yet proven.
6. Guest Windows must be renamed to `YARDOMOB` **in the guest**, and a future real NIC
   MAC must be bound and confirmed. A2 never enables an Ethernet device.
7. Do not attempt a live Mac/NetBEUI/Tailscale test until all identity and packet
   acceptance gates are met.

## 5. Why the first Android run may fail

- Converted config may contain Windows/macOS-specific DOSBox-X options unsupported
  by Android SDL3 port (a source check cannot verify driver compatibility).
- Emulated IDE/CHS may differ, even though the actual IMGMOUNT line is preserved.
- SDL3 video/audio integration and lifecycle may not support the copied WFW mode yet.
- The selected upstream Android port has disabled SDL_net and related networking.
- The overlay has **not** been compiled against the entire upstream source tree yet.

Record actual compiler diagnostics/device output before adjusting any of these.
