# A3 NetBEUI / ETHNET research plan — network deliberately disabled

## Layer distinction

WinPopup / WFW 3.11 file sharing using **native NetBEUI (NetBIOS Frames)** is
an Ethernet-layer protocol, NOT TCP/IP and NOT the same as NetBIOS over IPX.
Tailscale is an authenticated IP transport and does not itself forward guest
Ethernet broadcasts. Therefore the future Android integration must carry
complete Ethernet frames as payloads of a verified host-to-host IP transport.
The desired peer is another fully local guest on the Mac, not a VNC or screen
stream. DOSBox-X documents that SLIRP is not suitable for non-IP native NetBEUI
in this configuration; it documents raw Ethernet/pcap for such traffic.

Public documentation: https://github.com/joncampbell123/dosbox-x/wiki/Guide%3ASetting-up-networking-in-DOSBox%E2%80%90X

## Current code and blockers

- A2 preserved `network/experimental/` Kotlin ETHNET handshake/wire framing.
  Its tests are mock/fixture-only and do NOT demonstrate a DOSBox-X link.
- Pinned Retro-DOS Android `android/build-core.sh` passes `--disable-sdlnet`.
  DOSBox-X ETHNET support in the selected source is compiled conditionally
  behind SDL_net, so the interface is not active in the Android build.
- Android's packaged app intentionally lacks INTERNET permission; no Tailscale
  socket can run in A3.
- Proposed Android MAC is not assigned to the emulator's NE2000 MAC register;
  local guest Windows hostname is not proved to be YARDOMOB.
- Neither Ethernet broadcast forwarding nor MAC learning nor duplicate-address
  handling has been tested against the real Mac DOSBox-X server 2026.10.01.

## Future stages: AFTER actual independent guest boot

1. Verify exact native ETHNET handshake constants and session semantics
   against Mac's pinned 2026.10.01 `ethernet_ethnet.cpp`; do not assume the
   fork used for Android is wire-compatible without tests.
2. Make a deterministic native frame boundary in the **emulator's NE2000**
   device; compare against source-driven Kotlin frame-byte fixtures.
3. Select network backend: cross-compile SDL_net/ETHNET natively OR implement
   a controlled native UDP host bridge; do not conflate a Kotlin UDP socket
   prototype with a verified NE2000 implementation.
4. Build local loopback tests using two independently named machines, unique
   MACs, Ethernet unicast/broadcast/multicast, session recovery and disconnect.
5. Integrate Android/Tailscale opt-in permission and routing only after
   validation, and test against Mac peer without mounting Mac guest disks.
6. Demonstrate native NetBEUI WinPopup + SMB file sharing at the **guest** layer,
   with logs from each peer before declaring success.

No networking in A3, no VNC in any phase, and no claims of WinPopup interop.
