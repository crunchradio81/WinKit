# Android private disk transfer (Mac RC24.6 compatible)

**Source branch:** `feature/android-disk-transfer-v1` from `android-a26-keyboard-diagnostics`.

## User flow
In WinKit Mac's silver-blue Control Center, go to **Disks & Media → Export hard drive to WinKit Android**. Quit both DOSBox-X guests. Select Peer A/B and a live or frozen-snapshot disk. Export one private transfer ZIP. Check `manifest.json`, `README.txt`, and `disk/YARDOMOB.raw` using the included verifier.

In Android, choose **Import from WinKit Mac ZIP** in the launcher and select the transfer file using Android's system picker. The importer checks the exact three-entry format `winkit-android-disk-transfer-v1`, declared byte count, sector alignment and SHA-256, then stages a copy in the existing `filesDir/machines` directory. It uses the preexisting `NoReplacePublisher` to publish `YARDOMOB.raw` without replacing an installed disk.

Before publishing the disk it creates a persistent `YARDOMOB.network-gate.json` with `ethernet_enabled=false` and `identity_verified=false`. No guest-boot/online-ready marker is created. Existing offline network policy remains restrictive.

## Known limitations
- Copying does **not** rename the Windows for Workgroups computer from YARDO/YARDO-B to YARDOMOB; it must be renamed after **offline** boot before any LAN use.
- A unique emulated NIC MAC, matching disk geometry and actual Android boot still need independent verification.
- The GitHub branch named `android-a26-keyboard-diagnostics` currently has an A1-style `MainActivity.kt` and A10 `STATUS.json`. This PR integrates the transfer into that checked-in launcher, **not** a verified production A26 Android build.
- No new Android APK or device test has been run by this change.
- The Mac transfer ZIP contains copyrighted guest material. Keep it local/private; NEVER commit it to the public GitHub repo. No Windows binaries are in this source change.
