# A3 Android ARM64 build and eventual acceptance — NOT executed here

## Source and tool pin

- Parent application: `CrownParkComputing/Retro-Dosbox`
  `65e8c67ed4d8e5aacabc4b96337e8d47ac92d720`
- Core submodule: `CrownParkComputing/dosbox-x-sdl3`
  `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11`
- SDL3 source tag referenced by pinned builder: `release-3.2.20`.
- Target: Android `arm64-v8a`, minimum API 28. Upstream Gradle build:
  AGP 8.7.3, Kotlin plugin 2.0.21, Java 17, Gradle 8.9, SDK 36,
  NDK `28.2.13676358`.
- Build host: Linux x86_64. These are source/build plan dependencies;
  compilation and device compatibility are not proven by this handoff.

## Offline source checks

Run from extracted `WinKit311-Android-A3/`:

```bash
bash scripts/RUN_A3_TARGETED_TESTS.sh
```

These compile/test only portable Kotlin/JVM and C++, exercise patch fixture
rewriting, and check source contracts. No guest image, macOS system or network
interface is needed.

## Native Android build on a Linux x86_64 machine

1. Install Android SDK platform 36/build tools and NDK r28.2, Java 17, Gradle
   8.9, CMake, Ninja, autotools, GNU make, pkg-config, Git and Python 3.
2. Export `ANDROID_SDK_ROOT` and `ANDROID_NDK` (see root README).
3. Execute `bash scripts/BUILD_A3_ANDROID.sh`.
4. The script checks source pin, applies the A3 overlay to an isolated checkout,
   compiles upstream Android SDL3/core, checks AArch64 ELF and the injected
   machine string, builds a debug APK, checks packaged native SDL3/core
   libraries, package name and absence of Android network permissions.
5. Only on successful completion does it copy
   `_build_outputs/WinKit311-Android-A3-debug-DEVICE-UNVERIFIED.apk`.

This path has NOT BEEN RUN in the producing environment; no APK is included.
A failed native/Gradle build requires actual compiler diagnostics before claims
of compatibility. The CLI script is a recipe, not evidence of binary success.

## Android-only acceptance after an APK really exists

1. Install distinct `org.winkit.android.a2` package without uninstalling
   third-party Retro-DOS or affecting any Mac files.
2. Check SDL3 activity loads, setup screen opens, imported stopped/independent
   `YARDOMOB.raw` SHA-256 verifies and exists in app-private `files/machines/`.
3. Convert the exact stopped guest DOSBox-X config; review known geometry.
   A3 rejects any extra mount, unrecognized boot chain or unsupported flag.
4. Check WinKit offline entry appears once; press Start; inspect logs if the
   image lease cannot be acquired. Confirm no network permission/NIC.
5. Prove DOS 6.22 then WFW 3.11 on the **Android device itself**, plus clean
   stop/restart and keyboard/mouse/SVGA/audio behavior.
6. Inspect Windows network control panel and rename its identity to `YARDOMOB`
   if needed; verify identity and *later* MAC assignment before enabling NIC.
7. Capture emulator/Android logs and device specifics. A3 does not offer a
   script to alter Mac system disks or to enable live networking.

## Explicit caveats

- Source overlay is revision-locked and designed for the pinned public source,
  but the patched C++ + Android Kotlin have not been compiled as one Android APK.
- Advisory POSIX `flock` protects only cooperating clients. It does not make
  imports copy-on-write or detect malicious concurrent filesystem edits.
- Android offline mode intentionally lacks `INTERNET` permission and NE2000;
  A4/A5 networking requires a new opt-in design and separate safety validation.
