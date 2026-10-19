# A8 Engineering Results — no Mac tests, no Android APK

## Scope

A8 continues the cumulative WinKit 3.11 Android branch using the unchanged pinned
Retro-DOS parent `65e8c67ed4d8e5aacabc4b96337e8d47ac92d720` and its DOSBox-X
SDL3 submodule `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11`. A8 makes no
Mac RC24.3 or Mac disk changes, and includes no Microsoft OS content.

## Actual source changes

1. `scripts/apply_a2_overlay.py` patches upstream Android `build-core.sh` to
   request `SDL_ANDROID_JAR=ON`, build its SDL3 Java target, fail immediately
   if SDL3.jar is missing, and check for the expected
   `org/libsdl/app/SDLActivity.class` before Gradle. The pinned SDL3 v3.2.20
   CMakeLists.txt declares `SDL_ANDROID_JAR` and `SDL3-jar`.
2. `scripts/verify_a8_upstream.py` requires exact source transformed from the
   pinned git revisions; even if an old checkout has the right *marker*, any
   missing/mismatched source changes or stale Kotlin/native headers stop the
   build before compilation.
3. `scripts/verify_a8_apk.py` checks ELF64 AArch64 ET_DYN for all required
   native libraries, byte-for-byte library matches between JNI build output
   and the packaged APK, SDL3 Java activity class, APK DEX/resources/manifest,
   WinKit native signatures, and absence of other ABIs and virtual HDDs.
4. `scripts/BUILD_A8_ANDROID.sh` integrates these gates around the inherited
   actual NDK/Gradle build, copying an APK only after all gates pass. Output
   filenames are unique; old files are not overwritten.
5. `.github/workflows/a8-android-arm64.yml` is manually dispatched on Linux,
   preserving failure logs; it has NOT been executed in this session.
6. `host-tests/a8/test_a8_build.py` tests success/error branches with
   STRUCTURAL FAKE ELF/APK assets; none of these fixtures can boot Android.

## Acceptance boundaries

- Host Python source/validator tests: executable offline, see RUN_A8_HOST_TESTS.sh.
- Inherited A6 native C++ launch/lease test: executable offline.
- Genuine Android NDK/Gradle ARM64 compile and APK: **NOT executed** (Android
  SDK, NDK and Gradle absent from the working container).
- Android install, SDL3 lifecycle, synthetic BIOS screen, DOS 6.22 boot,
  WFW 3.11 boot: **NOT verified**.
- NetBEUI/ETHNET/Tailscale: **NOT enabled, not verified**. SDL_net stays off.
- Upstream Android source must be fetched on the configured CI/Linux build
  host; network retrieval from this container is unavailable.
- The A8 structural APK fixture is a *negative/positive validator test*,
  not a real ELF executable, a real APK, or proof of machine code correctness.

## How to run

Offline, no Android dependencies:

```sh
bash scripts/RUN_A8_HOST_TESTS.sh
python3 VERIFY_A6.py
```

On Linux x86_64 with Gradle 8.9, Java 17, Kotlin CLI, Android SDK API 36,
NDK 28.2.13676358, and Autotools/CMake/Ninja:

```sh
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"    # use your actual SDK path
bash scripts/BUILD_A8_ANDROID.sh
```

Or, after committing the A8 package to a GitHub repo, manually dispatch
`.github/workflows/a8-android-arm64.yml` under Actions. Do not claim a good
build until the CI run really succeeds, and do not claim boot until observed
on an Android device.

## Known risks

- The upstream fork is a candidate emulator, not yet demonstrated running
  this particular DOS/WFW guest on Android ARM64.
- The Kotlin compiler download in CI records its SHA256 on execution, but
  the package does not yet pin a trusted vendor digest. Pin/check the release
  asset checksum for release-grade supply-chain integrity.
- Extra `aapt2` manifest permissions/application-ID tests remain in the
  inherited A3 build recipe and only run when the SDK exists.
- Exact-source verification is narrow by design: a deliberate code change
  requires updating the controlled overlay, not bypassing the check.
