# 08 — Research evidence and unresolved interpretation

Research checked 2026-10-19. These are source pointers, not claims of Android device success.

1. DOSBox-X 2026.08.02 release notes — experimental ETHNET client/server for NE2000 over localhost/LAN, intended to bypass pcap/Wi-Fi problem: https://dosbox-x.com/release-2026.08.02.html
2. DOSBox-X full configuration reference (Debian 2026.08.31 mirror) — `[ne2000] backend=ethnet` accepted among possible backends: https://sources.debian.org/src/dosbox-x/2026.08.31%2Bdfsg-1/dosbox-x.reference.full.conf
3. DOSBox-X Windows 3.1x networking guide — legacy NetBEUI support with pcap vs TCP/IP-only slirp; driver and NDIS details: https://dosbox-x.com/wiki/Guide%3AInstalling-Windows-3.1x
4. DOSBox-X imaging documentation — raw HDD IMGMOUNT boot and guest image storage: https://dosbox-x.com/wiki/Guide%3AManaging-image-files-in-DOSBox%E2%80%90X
5. DOSBox-X upstream supported build platform document — no official Android binary build listed: https://github.com/joncampbell123/dosbox-x/blob/master/BUILD.md
6. DOSBox-X Android build target issue (still open at time of search): https://github.com/joncampbell123/dosbox-x/issues/959
7. Retro-DOS Android/iOS/Linux native SDL3-based DOSBox-X port, build documentation and storage SAF caveats: https://github.com/CrownParkComputing/Retro-Dosbox
8. Retro-DOS public Android store listing (last app update September 2026, claims DOSBox-X basis): https://play.google.com/store/apps/details?id=com.dosboxx.app
9. DOSBox Pure source — NE2000 and multiplayer supported, **not demonstrated ETHNET wire compatibility**: https://git.libretro.com/libretro/dosbox-pure
10. Tailscale assigned node IPs: https://tailscale.com/kb/1033/ip-and-dns-addresses/
11. Tailscale Android lacks a CLI: https://tailscale.com/kb/1080/cli
12. Tailscale local server access through tailnet: https://tailscale.com/kb/1034/local-team-server

## Interpretation constraints

- Upstream DOSBox-X having ETHNET does not establish that Retro-DOS port contains it.
- Mac-to-Mac WinPopup working does not establish Android-to-Mac compatibility.
- Public Android APK documentation does not certify Win3.11+NetBEUI or user-owned disk migration.
- Source mentions `ETHNET` but exact network transport (TCP/UDP), handshake and wire-version compatibility should be read/verified from the pinned implementation; no substitute guessed Ethernet packets.
- User's Mac device guest names, paths and prior acceptance outcomes reflect conversation history; device hardware properties remain unknown.
