# Android guest acceptance gates

| Gate | Expected evidence | Status |
|---|---|---|
| Kotlin wire framing | Valid/malformed control and data tests | PASS offline mock |
| Kotlin ETHNET handshake | Fake DOSBox-X UDP peer + bidirectional sample Ethernet frame | PASS offline mock |
| Real Mac ETHNET handshake | Join actual DOSBox-X UDP/1234 over tailnet | PENDING |
| Android emulator | Actual ARM64 Android boot to DOS prompt from private guest disk | NOT IMPLEMENTED |
| Windows for Workgroups 3.11 | Program Manager and keyboard/mouse/audio from Android | NOT TESTED |
| NE2000 integration | NIC frames injected and delivered by emulator | NOT IMPLEMENTED |
| Guest-to-guest NetBEUI | WinPopup in both directions Android vs Mac | NOT TESTED |
| Workgroup file sharing | Read/write between unique independent guest images | NOT TESTED |
| Reconnect / suspend-resume | Android sleep, network migration, no guest disk corruption | NOT TESTED |
| Graphics | 640x480 and advanced S3 modes on Android | NOT TESTED |
| Printing | Android guest to Mac document-output workflow | NOT TESTED |
| Public Android package | Reproducible signed APK, dependency notices and licensing | NOT BUILT |

**Important distinction:** a successful ETHNET handshake only verifies the UDP virtual Ethernet session setup. It does not mean the guest boots, the NIC adapter works, or a WfW LAN share is reachable. The Mac release roadmap and final DMG certification remain separate.
