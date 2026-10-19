# A1 emulator decision, source pin and genuine build boundary

Checked 2026-10-19 against public GitHub source (not against Android binary):

- Preferred: https://github.com/CrownParkComputing/Retro-Dosbox
- Parent pinned Git SHA: `65e8c67ed4d8e5aacabc4b96337e8d47ac92d720`
- Exact `core` git submodule at that commit: `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11`
- Core git repo: https://github.com/CrownParkComputing/dosbox-x-sdl3 (`sdl3` branch)
- Core base: DOSBox-X (https://github.com/joncampbell123/dosbox-x), GPL-2.0-or-later, with SDL3 and ImGui frontend (check bundled upstream notices before distribution).
- `android/build-core.sh`: builds SDL3 release-3.2.20 and DOSBox-X Android arm64-v8a (`ANDROID_API=28`), links `libretrodos.so`, packages SDL3 dependencies. Host API from `core/include/retrodos_host.h` supports `retrodos_host_run(argc,argv)` and `-conf` launch, pause/reset/quit, keyboard/mouse, framebuffer, CD/floppy hooks and status.
- Pinned third-party build script explicitly uses `--disable-sdlnet`, `--disable-libslirp`, `--disable-printer` and other feature flags; `src/misc/ethernet_ethnet.cpp` lives under `#if defined(C_SDL_NET) || defined(C_SDL2_NET)`. **Do not claim ETHNET present in the produced Android .so without symbol/config inspection.** Future network work will need a socket-enabled core or NE2000 frame hook.
- Android build script's NDK toolchain directory is hardcoded to `linux-x86_64`. This means on user's Apple Silicon Mac it must be adapted for a Darwin toolchain or run on a supported Linux x86_64 builder. The A1 standalone JNI scaffold is separately built with AGP CMake and doesn't depend on that script.
- Full 64-character SHAs, not branch HEADs, are enforced by `scripts/bootstrap_core.sh`. Git sources are **not embedded** in this archive, so that script requires internet in a build environment.

## Alternative evaluated

https://github.com/schellingb/dosbox-pure is a functioning libretro DOS core and has NE2000 through Libretro netplay, plus booted OS support. It is not a native DOSBox-X ETHNET peer and would require modifying both its NE2000 frame hooks and front-end networking. Less fidelity with already-verified Mac DOSBox-X reference; use only if selected SDL3 port fails the real ARM64 boot acceptance gate.

## A1 app vs selected emulator

A1 `android/` is a **separate source-level import / JNI disk-test application**. It does **not** call or bundle `retrodos_host_run` and deliberately will not start a fake emulator. The chosen third-party Android host frontend already owns SDL3 and SDLActivity and must later be extended or replaced within the same process. Linking WinKit's import/store UI to it is an A2 integration task, not just copying `.so` files into this scaffold.

The third-party core's host API explicitly says `run` occupies one thread and other host requests are marshaled into its mainloop. The session must keep the private disk file open exclusively throughout running guest activity. Before that bridge exists and succeeds on Android ARM64, status stays ANDROID UNVERIFIED.

## Build commands (NOT executed here)

```bash
bash scripts/bootstrap_core.sh                 # network required, exact revisions validated
# On a Linux x86_64 builder with Android SDK installed and SDK root exported:
ANDROID_SDK_ROOT="$HOME/Android/Sdk" bash scripts/build_retro_core_linux.sh
# Separate minimal Android import/JNI A1 launcher:
cd android
gradle assembleDebug
```

Dependencies include Java 17, Android SDK platform 35, NDK 28.2.13676358, CMake 3.22.1, ninja, git, GNU autoconf/automake/libtool, SDL3 source via internet, Gradle 8.9. **These toolchains were not available for an Android build in the execution environment.** No APK nor `.so` from Android exists in this package.

Primary files reviewed:
- https://raw.githubusercontent.com/CrownParkComputing/Retro-Dosbox/65e8c67ed4d8e5aacabc4b96337e8d47ac92d720/android/build-core.sh
- https://raw.githubusercontent.com/CrownParkComputing/dosbox-x-sdl3/4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11/include/retrodos_host.h
- https://raw.githubusercontent.com/CrownParkComputing/dosbox-x-sdl3/4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11/src/misc/ethernet_ethnet.cpp
