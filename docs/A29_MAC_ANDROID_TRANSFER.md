# WinKit A29: Mac RC24.6 → Android transfer integration

Status: **source integrated; Android A29 APK not yet built or device-verified.**

## Separate installation and guest safety

- Legacy Android diagnostic app applicationId: `org.winkit.android.a2`.
- This A29 Mac transfer app applicationId: `org.winkit.android.a29transfer`.
- Android assigns different app-private storage to these IDs. A29 must be **installed alongside** A26, never used as an update to it. No guest disk or app data is to be removed.
- A29 debug artifacts are NOT stable-update-signed; future A29 updates need a consistent signing key.
- A29's destination remains app-private `files/machines/YARDOMOB.raw` and fails closed if an existing machine is present.

## RC24.6 Mac export ZIP (v1)

The existing Mac exporter is the baseline; do not re-export merely to change app IDs. The ZIP receiver (`overlay/kotlin/MacTransferImporter.kt`) expects exactly these entries, in this order:

1. `manifest.json` (up to 64 KiB)
2. `README.txt` (up to 64 KiB)
3. `disk/YARDOMOB.raw` (raw, 512-byte sectors)

The manifest uses `schema: "winkit-android-disk-transfer-v1"`.
`target` must include `guest_name: "YARDOMOB"`, `private_disk_required: true`, `ethernet_enabled: false`, `requires_first_boot_identity_setup: true`.
`disk` includes `entry: "disk/YARDOMOB.raw"`, `format: "raw-sector-image"`, `sector_bytes: 512`, `bytes`, and lowercase hex `sha256`.

The Android importer stages the entire disk, checks the uncompressed byte count, ZIP CRC, SHA-256 and absence of extra entries before publishing it through the exclusive `GuestStore`. It reserves a network-deny marker with `identity_verified: false` before disk publication. Interrupted imports may leave an unready reserved destination. No automatic overwrite, erase, or cleanup of that reserved guest is permitted.

## Boot configuration remains a separate prerequisite

**RC24.6 v1 disk ZIP alone is not bootable through WinKit A29.** The existing Android setup still requires the original, known-working Mac DOSBox-X guest config imported separately, plus the original absolute Mac disk path from the `[autoexec]` `IMGMOUNT` line.

`OfflineBootConfig.convert` changes only the single matching original image path to the app-private `YARDOMOB.raw`, disables virtual networking, and rejects unsupported commands or ambiguous boot chains. `OfflineDiskGeometry.matches` demands the disk file size agree with the explicit `IMGMOUNT -size` CHS values. Do not invent disk geometry from a ZIP filesize.

The Android user must review the generated offline config and acknowledge its local-only mode before boot. The `OFFLINE_READY` gate is created only after successful config checks; the ZIP importer alone never authorizes boot.

A later Mac exporter version can include an authenticated, reviewed boot-config sidecar as a backward-compatible **new schema**, rather than silently changing v1 or auto-executing unknown guest commands.

## First boot and acceptance

- Boot DOS 6.22 first, then Windows for Workgroups 3.11; do not claim either until observed on an actual device.
- Preserve the original Mac YARDO/YARDO-B image; Android uses a separate physical copy.
- Before enabling any WFW LAN capability, change the cloned guest's computer identity to YARDOMOB, independently verify a unique network adapter MAC, and complete explicit network acceptance. First boot stays offline.
- Host-side synthetic Mac export verification is not the same as a confirmed Android boot.

## Core source dependency / build workflows

The pinned parent Retro-DOS commit is `65e8c67ed4d8e5aacabc4b96337e8d47ac92d720`; it points to DOSBox-X SDL3 submodule `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11`.
`CrownParkComputing/dosbox-x-sdl3` returned GitHub 404 at this integration pass. **Do not replace with unverified stock DOSBox-X.** Restore authorized access to the exact source, or prepare and independently validate an explicit source-porting plan.

Original diagnostics workflow: `.github/workflows/a10-android-arm64.yml`.
Independent transfer build workflow: `.github/workflows/a29-mac-transfer-android-arm64.yml` on `main`, which checks out branch `android-a29-mac-transfer-isolated`, verifies the A29 package identity and offline transfer source before trying the emulator build, and marks compiled debug APKs device-unverified.

This document is an implementation contract and handoff; it is **not** a claim that Android A29 compiled or booted.
