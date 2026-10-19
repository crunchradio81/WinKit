# WinKit 3.11 Android — A10 cumulative source

**Current status: host-compiled/offline engineering tests only.** No Android ARM64
emulator library, APK, BIOS boot, DOS 6.22, Windows for Workgroups 3.11 boot,
or guest NetBEUI-over-Tailscale LAN has been verified.

This is a **local, independent** Android ARM64 virtual PC, **not** VNC, remote
desktop, or a remote launch of YARDO/YARDO-B. `YARDOMOB` needs its own private
writable disk, Windows computer name and NIC MAC before Ethernet is enabled.

## A10 advancement

A10 replaces image/config/ready-marker `File.renameTo` publication with
same-directory `Files.createLink` no-replace publication. Host tests exercise a
second writer and a destination appearing mid-import. This is a guardrail for
cooperating instances, not a substitute for native disk leases or Android
hardware validation. Full engineering details: `docs/A10_ENGINEERING_RESULTS.md`.

### Host tests (no Mac)

```bash
bash scripts/RUN_A10_HOST_TESTS.sh
```

### GitHub publishing (one-shot, Android Termux or Linux; no Mac test)

The **public** repository is https://github.com/crunchradio81/WinKit. It was
initialized with a status README, but the A10 source has **not yet been pushed**
by this package. From the extracted package, with GitHub credentials already
configured for git operations, run:

```bash
bash scripts/PUBLISH_A10_GITHUB.sh
```

This will create and push a `android-a10-source` branch **without merging it**.
Inspect and review the proposed PR before deciding whether to merge. The
uploader rejects guest disk images and binary media from the staged Git index.

### Linux x86_64 Android toolchain attempt

Only *after* the entire source tree has been pushed, the manual workflow
`.github/workflows/a10-android-arm64.yml` can attempt compilation and packaging
using Android SDK/NDK, pinned DOSBox-X/Retro-DOS, and SDL3. Alternatively:

```bash
bash scripts/BUILD_A10_ANDROID.sh
```

This environment cannot run the Android toolchain. Even a successfully built
APK is **not** evidence of an Android device boot.

## Safety and distribution

No Microsoft DOS/Windows media, installed virtual HDD, Mac guest disk, Mac
backup, private network credentials, or signing keys are included. The PoC1
Kotlin test JAR is no longer bundled: it is recreated locally during the tests.
Mac RC24.3, YARDO and YARDO-B remain untouched.
