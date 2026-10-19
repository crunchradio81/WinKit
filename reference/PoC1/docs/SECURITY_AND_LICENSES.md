# Security and licensing

- Tailscale encrypts IP transport between peers; DOSBox-X's `ETHNET` application protocol itself has **no separate cryptographic authentication or encryption**. Limit the Mac's UDP port 1234 to intended tailnet devices using grants/firewall restrictions; do not expose it on the public Internet. The existing DOSBox-X server listens on all IPv4 interfaces by default.
- Neither the raw Ethernet tunnel nor the Windows 3.11 NetBEUI stack should be exposed to arbitrary/untrusted participants. Old Windows guest services are not hardened for modern hostile networks.
- Android private storage contains writable guest disks; prevent simultaneous mounts and back up only after orderly guest shutdown. Mac and Android guests need independent disks and names, even if the Android copy was made from a powered-off Mac image.
- No Microsoft DOS or Windows binaries, installation media, commercial fonts or proprietary device drivers are in this PoC. Users supply properly licensed media as appropriate.
- DOSBox-X uses GPLv2-family licensing; any fork, derivative distribution, included emulator binaries and libraries require careful notice/source-compliance review. DOSBox Pure/libretro/RetroArch license terms must also be reviewed before packaging.
- The new Kotlin prototype source in this folder is offered under GPL-2.0-or-later. It is an interoperability implementation inspired by public DOSBox-X ETHNET source; it does not include upstream DOSBox-X code or binary.
- The included fat Kotlin JVM JAR is exclusively a host testing utility; it is **not** an Android application and is not a substitute for signed production APK packaging.

Source reference: https://github.com/joncampbell123/dosbox-x/blob/master/src/misc/ethernet_ethnet.cpp
