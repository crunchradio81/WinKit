# A5 engineering results — independent Android diagnostic / raw-disk safety

Date: 2026-10-19. Scope: **off-Mac source engineering only**, with generated
GPL test media. No active YARDO/YARDO-B/RC24.3 disk, backup or emulator modified.
No executable Android ARM64 core, APK or in-emulator BIOS boot was produced.

## New deliverables

- `overlay/native/winkit_raw_geometry.h`: strict CHS parse of the accepted
  `[autoexec]` mount command, 512-byte raw sectors, bounds for sectors/heads/
  cylinders, overflow-safe byte length, rejection of known non-raw image
  signatures and fixed VHD footer. Exact size match before DOSBox-X runs.
- `overlay/native/winkit_image_lease.h`: now exposes the already-locked
  descriptor and fstat-derived byte count; worker validates disk *under the
  exclusive lease* before entering the emulator.
- `scripts/apply_a2_overlay.py`: pinned-upstream patch installs the native
  guard in the actual frontend worker, not merely in a standalone test.
- `overlay/kotlin/OfflineDiskGeometry.kt`: independent JVM raw-container and
  exact-geometry verifier called when importing boot configuration and when
  enabling the launcher button.
- `overlay/kotlin/OfflineSmokeFixture.kt`: freely redistributable 512-byte
  x86 test boot sector, compiled from `fixtures/winkit_bios_boot.S` and encoded
  in Kotlin source; creates a *private, sparse 4,128,768-byte* synthetic
  diagnostic HDD and its offline config/readiness marker on Android. Does not
  require Mac export, DOS, Windows or Tailscale.
- `WinKitSetupActivity.kt`: explicit **Install GPL BIOS boot smoke test** action
  for a pristine app-private machine folder. Existing guest media or markers
  cause fail-closed refusal; test disk is not silently installed.
- `scripts/BUILD_A5_ANDROID.sh`: Linux Android ARM64 build and post-build
  checks using the existing pinned A3 pipeline, plus exact A5 native guard
  string and DEX class presence checks. Source/build gate only; **not run**.

## Measured host evidence

`bash scripts/RUN_A5_TESTS.sh` compiles real C++ and Kotlin/JVM code and uses
`BUILD_A4_BOOT_FIXTURE.sh` to generate a temporary diagnostic disk. The
on-device Kotlin generator produces a byte-for-byte matching raw image digest
compared with the GNU assembler-generated host fixture. Native file-descriptor
reads and Kotlin RandomAccessFile checks reject known non-raw magic strings.
The A5 default host suite completed with 48 native assertions, 24 Kotlin
image/geometry assertions, 10 Kotlin smoke-installer assertions, 6 integration
checks and 3 build-contract checks. The A4 inherited runner (6 Python fixture
tests, native and Kotlin boot-policy checks, 18 Kotlin boot-sector checks,
native lock test and patch/source tests) was repeated after updating a test to
follow the new indirect Kotlin boot-policy call. All retained checks passed.
The host-only tests do **not** execute BIOS instructions or DOSBox-X.

Expected eventual banner *only if a real Android emulator boots correctly*:
`WINKIT A4 BIOS HDD BOOT OK - NO MICROSOFT FILES`.

## Locked-down phases

1. A5 host tests — completed if reported by test log.
2. Linux x86_64 Gradle 8.9 / Android SDK 36 / NDK 28.2.13676358 /
   AGP 8.7.3 / Kotlin 2.0.21, actual native SDL3 + DOSBox-X compile — **not done**.
3. Verify `libretrodos.so` AArch64 ELF; build APK and verify DEX and native libs
   — **not done**.
4. Android ARM64 installation and visible BIOS fixture banner — **not done**.
5. Clean power-off and independent stopped/exported DOS 6.22 + WFW 3.11 disk
   import; verify Windows computer-name change to `YARDOMOB` — **not done**.
6. NIC MAC provisioning and full-frame native ETHNET/L2 connection across
   Tailscale, NetBEUI/WinPopup and file sharing — **separate future phase**.

Network permissions remain absent, NE2000 and IPX disabled, Kotlin ETHNET
transport mock-only. The Android machine always uses a separate private HDD,
never direct access to a Mac image. Shipping an APK without a successful core
build and packaging proof is explicitly prohibited by the build script.

## Toolchain status

The A5 source-development container includes JDK/Kotlin, Python, GNU binutils,
C++ and CMake. It does **not** have Android SDK 36, Android NDK, or Gradle. It
also cannot reach GitHub directly from the container, so full upstream source
and missing build tools were not fetched. No substituted build evidence.

Run on a dedicated **Linux x86_64** builder when available:

```sh
bash scripts/CHECK_A5_TOOLCHAIN.sh
bash scripts/RUN_A5_TESTS.sh
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
bash scripts/BUILD_A5_ANDROID.sh
```

`BUILD_A5_ANDROID.sh` may take a while and depends on public upstream source
availability. Its output name includes `DEVICE-UNVERIFIED`; a built APK alone
would still not prove BIOS, DOS, Windows or Ethernet runtime operation.
