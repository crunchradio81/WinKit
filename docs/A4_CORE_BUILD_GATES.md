# A4 build gates: exact and separated

The host-only BIOS fixture tests do not demonstrate ARM64 core compatibility.

- Source pin: Retro-DOS `65e8c67ed4d8e5aacabc4b96337e8d47ac92d720`.
- DOSBox-X SDL3 fork `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11`.
- SDL3 release `3.2.20`; Android min API 28, target ABI arm64-v8a.
- Upstream Android Gradle uses AGP 8.7.3, Kotlin plugin 2.0.21, Android SDK
  platform 36 and NDK 28.2.13676358. Android studio/SDK/Gradle not included.
- Upstream Android script selects `--disable-sdlnet`; ETHNET cannot be presumed
  active. This is intentionally an **offline-only** milestone.
- Building requires internet for public git/SDL dependencies, Java 17,
  autoconf/automake, Ninja/CMake, Gradle and installed SDK/NDK.

Future run on a dedicated Linux x86_64 Android build host:

```
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export ANDROID_NDK="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
bash scripts/BUILD_A3_ANDROID.sh
```

**Gate B1**: pinned upstream clone/submodule SHA verified.

**Gate B2**: Android SDL3, DOSBox-X core and patched frontend compiled with
NDK AArch64 toolchain; ELF architecture checked; no stale `.so` accepted.

**Gate B3**: APK contains arm64-v8a SDL3 and libretrodos `.so` artifacts;
manifest proves separate application ID and no network permissions.

**Gate B4**: actual Android device launches native SDL3 frontend.

**Gate B5**: independent synthetic generated BIOS HDD fixture displays banner.

**Gate B6**: independent stopped/exported Microsoft guest disk boots DOS 6.22
then WFW 3.11, with stable video, input and sound.

**Gate B7**: guest identity changes & unique Ethernet MAC are configured and
verified before network activity. NetBEUI over ETHNET/Tailscale is a distinct
future integration gate.

No gates B1–B7 are claimed completed by the A4 tests.
