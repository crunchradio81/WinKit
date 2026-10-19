# 01 — Target architecture & engine decision

## Product behavior

Android runs the *real* guest operating system, locally on ARM64 Android. Rendering, CPU emulation, mouse, keyboard, WAV/MIDI, HDD, floppy and CD images are managed on-device. Remote Mac is **only a peer on the legacy LAN** and can be offline when Android boots. The local disk is private to Android and can be backed up independently. Android-to-Mac networking carries legacy guest Ethernet frames across an encrypted Tailscale IP path, rather than drawing the Mac desktop remotely.

```text
Android tablet/phone                                      Mac mini
+------------------------------------+                    +-------------------------------+
| Android WinKit launcher             |                    | WinKit 3.11 Control Center     |
| touch / keyboard / storage          |                    | Mac host's local guest HDDs   |
|   +-----------------------------+  |                    |   +------------------------+  |
|   | Android DOSBox-X-derived    |  |                    |   | DOSBox-X 2026.10.01     |  |
|   | x86 CPU + NE2000 + S3/SB16  |  |                    |   | NE2000 / ETHNET backend  |  |
|   | YARDOMOB.raw -> DOS / WFW   |  |                    |   | YARDO guest (NetBEUI)    |  |
|   +-----------------------------+  |                    |   +------------------------+  |
|           NE2000 raw frames          |                    |           NE2000 frames       |
|               |                     |                    |                 |             |
|     ETHNET client [UNVERIFIED]      |                    |      ETHNET server (working   |
|               |                     |                    |         on local Mac peers)   |
+---------------+---------------------+                    +-----------------+-------------+
                |                    Tailscale IP VPN                      |
                +------------ app socket transport over tailnet ----------+
                  (port/protocol of ETHNET to be verified; no L2 Tailscale)
```

## Engine short list (not equivalently capable)

**A. DOSBox-X-derived native Android port — preferred.** Already matches Mac CPU/hardware model, WFW guest disk format and, on recent upstream DOSBox-X, `backend=ethnet`. Native Android hosting/ABI/input/permissions must be solved. `Retro-DOS` is a tangible SDL3/NDK/Gradle porting reference, not necessarily an Android WinKit-compatible binary. Inspect its `core` submodule revision against upstream 2026.08+ ETHNET before using it.

**B. DOSBox Pure / RetroArch — fallback for a boot-and-input spike only.** Has a NE2000 emulated device and multiplayer mechanism; however, its multiplayer may be core/netplay specific and should NOT be assumed wire-compatible with DOSBox-X ETHNET or able to carry arbitrary NetBEUI between Android and Mac. An apparently working Windows GUI is not LAN acceptance. If option A is blocked, assess whether a shared Ethernet adapter/transport bridge can be implemented without compromising emulator correctness.

**C. QEMU system-i386 on Android — contingency.** Full-system x86 emulation is conceptually possible and emulates NICs but may be slower, require a different guest hardware configuration and a custom layer-2 bridge. User-mode NAT/TCP/IP alone doesn't satisfy NetBEUI. Do not silently swap this in as final product.

**D. VNC/RDP / cloud Windows — rejected for target**: not an independently booted Windows 3.11 instance.

## Component boundaries

- `AndroidShell`: Kotlin activity + native SDL surface or equivalent, lifecycle; Android Storage Access Framework (SAF) file import; physical keyboard/mouse and touch controls.
- `EmulatorCore`: version-pinned DOSBox-X-derived ARM64 library with dynamic-core/JIT correctness explicitly validated (interpreter fallback), NE2000, Sound Blaster, video and CD/floppy mounting.
- `MachineProfile`: local writable copy of Mac-exported disk with independent machine ID and new NIC MAC; configuration, state and last graceful stop.
- `NetworkService`: launcher-specified Mac tailnet peer, ETHNET client command, connected indicator, failures and retries (exact transport uncertain until source check).
- `MediaService`: controlled removable-media insertion/ejection; no unsafe writes to currently mounted HDD or mounting one guest disk simultaneously in two emulators.
- `Diagnostics`: structured gates and logs; explicit 'transport healthy' vs 'virtual ethernet established' vs 'WinPopup bidirectional' statuses.

## Hard boundary

Mac release candidate remains independent of Android; no Android work may alter Mac original HDDs, cold backups, existing accepted UI, or change established Mac two-guest protocol without separate Mac regression pass.
