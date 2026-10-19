# WinKit 3.11

WinKit 3.11 is a personal-use project for running an independent DOS 6.22 / Windows for Workgroups 3.11 guest on macOS and, eventually, Android ARM64.

## Android status

**Engineering source and offline tests only.** As of the A10 milestone, Android ARM64 DOSBox-X has not been successfully compiled or booted on a device. No working APK, Windows 3.11 boot, or live NetBEUI-over-Tailscale connection is claimed.

The Android guest is intended to use a **separate, app-private virtual disk**, computer name `YARDOMOB`, and a distinct emulated Ethernet MAC. It is a local x86 virtual computer—not VNC, screen sharing, or a remote session of the Mac guest.

### Source upload

This repository has been initialized. The cumulative A10 Android source package should be committed to this repository before starting any GitHub Actions build. **Do not trigger an Android build until the complete source tree, including the `scripts/`, `overlay/`, `android/`, `host-tests/`, and `.github/workflows/` directories, has been uploaded and host tests pass.**

Current Mac release candidate and working guests are independent; do not upload or modify Mac RC24.3, `YARDO`, `YARDO-B`, their hard disks, or their backups as part of Android development.

## Distribution rules

No Microsoft DOS/Windows installation images, licensed guest-disk exports, private backup images, secrets, signed Android keystores, or third-party binaries of uncertain redistribution status should be committed. `*.raw`, `*.img`, `*.iso`, `*.vhd`, `*.apk`, and signing files must remain outside the repository.

A Linux CI build is a compiler/packaging gate only. It does not demonstrate working Android emulation or LAN interoperability.
