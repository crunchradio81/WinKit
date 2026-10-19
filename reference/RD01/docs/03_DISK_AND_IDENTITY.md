# 03 — Guest disk export, machine identity and integrity

## Clone source of truth

Use `~/Documents/WinKit/TestSessions/WorkingGuests-20261018-174922-30f2e1c8/YARDO-B-WORKING.raw` or `YARDO-WORKING.raw`, **not** the original virtual disk under `~/Library/Application Support/WinKit311/LegacyLANLab/` and **not** any cold backup. Source working image must be cleanly shut down. Never copy while DOSBox-X is running or while HDD image is mounted elsewhere. The included exporter has guards for these conditions, checks hashes before/after copy, and reports mismatch as failure.

Private target: `~/Documents/WinKit/AndroidExports/YARDOMOB-.../YARDOMOB.raw`. No guest bytes exist in this ZIP. Existing output names are never overwritten. Transfer to **Android app-private files** or an explicitly SAF-selected folder with durable access, then verify SHA-256 in manifest on Android before first boot. A filesystem that cannot support large files or reliable random writes is not acceptable. Avoid SD card/FAT32 file limit if guest image >4 GiB. The file must be local writable random-access; cloud-backed SAF handles may need copy-to-internal-storage.

## Identity must change BEFORE joining Mac's Ethernet LAN

Copied WFW guest retains prior computer name, workgroup, network configuration, NetBIOS names, credentials and possibly stateful application settings. Choose new computer name `YARDOMOB` (<=15 characters; check name rules); retain the intended workgroup if needed. Configure Android emulated NE2000 MAC with a new *locally administered unicast* MAC, e.g. `02:57:4B:33:11:03` (subject to collision check). Match NE2000 base/IRQ to installed Windows 3.11 driver, but never duplicate emulated MAC address across simultaneously active guests.

**Safe first boot order:** Android guest boot with ETHNET disconnected; Windows Control Panel / Network Setup -> identify/rename computer -> reboot Windows to apply; confirm new name and workgroup; verify independent disk snapshot; only then connect ETHNET/Tailscale and test Mac communication. Alternative for final automated provisioner: first-run guest provisioning recipe stages the identity update in the exported clone, then enables LAN. Do not patch binary registry/INI offsets blindly without proven WFW procedure. Guest updates should remain setup-only, not in-guest background macros.

## Boot/geometry portability

DOSBox-X image management supports bootable raw HDD mounting. Preserve **exact boot `IMGMOUNT` line**, CHS parameters (if used), machine type, memory, video, NIC IRQ/base, audio and CD settings from *the validated working guest config*. Do NOT infer CHS solely from file size or use Windows 95 instructions on WFW 3.11. The Android host path and native input/rendering options may differ. Android core must implement compatible disk controller, BIOS services, images, NE2000 and timing.

When Mac starts with a DOS boot disk, there may also be floppy or CD media critical to booting; inventory all active mount lines rather than exporting only `C:` with assumptions. `INSPECT_WORKING_GUESTS.command` helps capture boot chains, but logs may contain paths; keep local.

## Backups and synchronization

Separate guest disks are not shared live. Avoid block-level sync while Windows is running. If file transfer is needed, use WFW share/NetBEUI or a separately user-approved explicit file import/export. Offline backup copy after a clean shutdown with SHA-256 and dated filename. Guests cannot safely point at the same writable image via USB/cloud storage.
