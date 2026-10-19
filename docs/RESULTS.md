# A1 acceptance report — 2026-10-19

## Exact status by deliverable

| Item | Result | Evidence |
|---|---|---|
| User handoff integrity | SOURCE AUDITED, verified | 41 files matched received `MANIFEST.sha256` via `VERIFY_HANDOFF.py` |
| RD01 export/probe/verify utilities | OFFLINE VERIFIED | 10/10 Python tests against synthetic, ephemeral working-clone fixtures |
| Original PoC1 Kotlin transport | JVM MOCK VERIFIED | 2/2 Python wrapper tests running bundled JVM JAR and local synthetic UDP peer; **not actual DOSBox-X** |
| A1 native `DiskImage` | COMPILES ON HOST, OFFLINE VERIFIED | Linux x86_64 C++17 build and 1 ctest with disk bounds, write/read, lock, symlink, alignment, read-only tests |
| A1 `GuestStore`, `GuestIdentity`, `BootProfile` | COMPILES ON JVM, OFFLINE VERIFIED | 116 Kotlin assertions on SHA/bytes, no overwrite, symlink, 100 generated MACs, network disabled |
| A1 experimental ETHNET wire codec/session | COMPILES ON JVM, SOURCE-BASED FIXTURES | 10 Kotlin assertions matching control header/AVPs/overrun bounds from read upstream source. **Interop not tested** |
| A1 offline Mac-config rewriting script | OFFLINE VERIFIED | 4 Python tests with synthetic config only (geometry preserved; no real Mac config supplied) |
| A1 package safety/static checks | OFFLINE VERIFIED | 4 Python tests excluding raw images/APKs; checking offline Android manifest |
| Android Gradle Kotlin Activity | SOURCE CREATED, ANDROID BUILD UNVERIFIED | Android SDK, NDK, Gradle unavailable here; NO APK created |
| Android JNI `libwinkit_disk.so` | SOURCE CREATED, ANDROID BUILD UNVERIFIED | Host C++ test verified same disk code; JNI compilation not verified |
| Pinned SDL3 DOSBox-X ARM64 core | UPSTREAM BUILD PATH INSPECTED, NOT BUILT HERE | Exact parent/submodule Git commits pinned; no network access in build container; no SDK/NDK |
| Program Manager / DOS 6.22 / WFW 3.11 | ANDROID DEVICE BOOT UNVERIFIED | No guest OS media copied into build/test; no emulator core linked |
| Network to Mac, Tailscale / WinPopup / SMB | INTEROP UNVERIFIED | Mac LAN YARDO↔YARDO-B verified in handoff; cross-device not tested |

## Host test command

```bash
bash scripts/RUN_OFFLINE_TESTS.sh
```

This builds the native test in a temporary directory, compiles Kotlin classes to ephemeral JVM test jars, reruns both original source-package tests, runs Python config tests, then validates source-package safety. No user HDD or Mac path is read or touched by this command. It does not download SDK, emulator source or commercial guest media.

## Correct next acceptance gate

1. On a Linux x86_64 environment with Android SDK/NDK, run `scripts/bootstrap_core.sh`, `scripts/build_retro_core_linux.sh` and capture compiler/link logs; prove ELF `Machine: AArch64` and exported `retrodos_host_run` symbols (`llvm-readelf` / `llvm-nm`). The published engine script builds `libretrodos.so` for the **third-party** app; verify that it is not a stale `.so` and has all shared dependencies.
2. On same build host build `android/` A1 Gradle launcher (source-level import/probe) and test startup on Android when available. Never claim the scaffold boots DOS.
3. Integrate host API with private HDD and generated **offline** `.conf`, run first with free DOS content and then with private exported licensed DOS 6.22 + WFW 3.11 guest, verify input/render/clean shutdown and untouched Mac data.
4. Only after local boot, enable native ETHNET in a compatible Android core with intentional MAC/name commissioning. Compare Mac 2026.10.01 exact source or wire trace, then test real NetBEUI across authorized Tailscale.

## Not claimed

No APK, ARM64 native emulator, Windows boot, true Android audio/graphics performance, MIDI, NetBEUI, Android VPN traffic, Mac live UDP handshake, functional Android file share, or release candidate. The source ZIP is a development milestone only.
