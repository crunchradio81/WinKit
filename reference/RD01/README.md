# WinKit 3.11 Android — Research & Development Pack RD01

**Prepared:** 2026-10-19  
**Scope:** independent Android Windows for Workgroups 3.11 guest, local rendering, Mac-to-Android virtual NetBEUI LAN over Tailscale.  
**Status:** architecture + tested host-side preparation tools; **NOT** an Android emulator APK and **NOT** an Android runtime pass.

## Decision

Develop a native Android WinKit shell around a DOSBox-X-derived Android core. Reuse design ideas/build lessons from the open-source Retro-DOS Android port, but keep a purpose-built Windows 3.11 UI. Align its DOSBox-X NE2000 networking and experimental `ETHNET` protocol with the verified Mac DOSBox-X build (2026.10.01). The Android guest **owns an independent virtual HDD**. It is *not* VNC/RDP, a live Mac-mounted C:, or an arbitrary host SMB redirector.

**Crucial unresolved gate:** Retro-DOS's current DOSBox-X fork may not contain the 2026.08+ upstream `ETHNET` implementation, and feature parity/actual mobile networking **has not been verified**. Build and compare the forks before claiming compatibility. No off-the-shelf APK is represented as a finished WinKit Android release here.

## What's actually in this ZIP

- `docs/01_ARCHITECTURE.md`: proposed architecture and candidate analysis.
- `docs/02_NETWORK.md`: layer-2-over-layer-3 explanation, ETHNET/Tailscale design, protocol/version gates.
- `docs/03_DISK_AND_IDENTITY.md`: how to clone the guest without altering the source or duplicating a live workgroup identity.
- `docs/04_ANDROID_UI_AND_RUNTIME.md`: requirements for touch, input, sound, media, file storage and lifecycle.
- `docs/05_ENGINE_BUILD_PLAN.md`: project structure, fork strategy, staged Android build steps.
- `docs/06_ACCEPTANCE_MATRIX.md`: precise Android and Mac test gates, logging and acceptance criteria.
- `docs/07_SECURITY_AND_LICENSES.md`: local ownership, no shipped Microsoft media, encryption boundary.
- `docs/08_RESEARCH_SOURCES.md`: upstream references and dated evidence.
- `scripts/mac/INSPECT_WORKING_GUESTS.command`: read-only Mac guest/config inventory.
- `scripts/mac/EXPORT_ANDROID_GUEST.command` + `scripts/common/export_guest.py`: guarded export of **one stopped `*-WORKING.raw`** to a private Android transfer folder with SHA-256 manifest; **does not modify guest identity internally**.
- `scripts/android/DEVICE_PRECHECK.sh`: read-only Android/Termux device inventory for a future test.
- `scripts/common/net_probe.py`: generic UDP echo over Tailscale to check IP transport only (NOT ETHNET and NOT NetBEUI).
- `scripts/common/verify_export.py`: verify exported private guest image against checksum.
- `templates/guest-config-not-runnable.ini`: proposed DOSBox-X properties; needs real guest geometry and selected Android core.
- `tests/`: self-tests of host utilities on synthetic test fixtures.

## Use right now, even if Android testing is unavailable

1. Read `docs/01_ARCHITECTURE.md`, `docs/02_NETWORK.md`, and `docs/05_ENGINE_BUILD_PLAN.md`.
2. Keep the Mac RC work untouched. The export helper is *optional* and does not require an Android device.
3. When the two DOSBox-X guests are completely shut down, in the ZIP's root run:

   ```bash
   bash scripts/mac/INSPECT_WORKING_GUESTS.command
   bash scripts/mac/EXPORT_ANDROID_GUEST.command
   ```

   The exporter defaults to a verified **working clone** folder from October 18. It refuses to run while `dosbox-x` is running, and it refuses original images. It writes a **new** private image under `~/Documents/WinKit/AndroidExports/` (outside this ZIP/repository). The exported image is intentionally not distributed.
4. After export, **do not boot it on the same virtual LAN until Windows computer name and emulated MAC are made unique**. See disk/identity docs.
5. For IP-only Tailscale connectivity testing later, see `docs/02_NETWORK.md` and the `net_probe.py --help` output.

## Non-goals in RD01

- No driver changes to Mac's RC working images.
- No running/packaging the Windows OS inside this ZIP.
- No claim that a public Android DOSBox app already supports the experimental ETHNET protocol.
- No claimed Windows-on-Android benchmark, touch pass, or WinPopup cross-device pass.
- No raw Ethernet/NetBEUI over plain Tailscale by itself; the virtual ethernet encapsulation must work end-to-end.

## Baselines to preserve

Mac WinKit: two independent guest machines (YARDO / YARDO-B) have working sound, S3 graphics, WinPopup and network files on verified working copies. Mac release remains separate and should remain on its established guest-acceptance path. Android machine target identity: **YARDOMOB** (tentative; must be set inside cloned Windows before network join). The unique NIC MAC must be configured on Android and must not duplicate either Mac peer.
