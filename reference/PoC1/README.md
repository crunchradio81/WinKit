# WinKit 3.11 Android — Local Guest + Mac Legacy LAN (PoC 1)

**Status:** transport prototype only. **NOT** an Android APK, Android emulator, Windows guest, or verified Tailscale connection.

## The product we are building

On Android, boot a **local, independently writable** DOS 6.22 / Windows for Workgroups 3.11 guest in an on-device x86 emulator. That Android guest joins the **Mac's separate, already-running Windows for Workgroups guest**, using legacy workgroup networking across a private Tailscale connection. Android display, CPU emulation, storage, input and audio remain entirely local. This is **not VNC, screen mirroring, cloud emulation, SMB1 forwarding or a remote desktop**.

```text
Android phone/tablet                           Mac
--------------------                           ------------------------
WinKit Android app                              WinKit macOS Control Center
  Local DOS/WfW 3.11 image                        Separate YARDO guest image
  x86 emulator + NE2000 NIC                      DOSBox-X + NE2000 NIC
           |                                               |
  NE2000 TX/RX frame callback                    DOSBox-X ETHNET STARTSERVER
           |                                               |
  EthnetSession.kt -- UDP over Tailscale ------> Tailscale IPv4:UDP/1234
                [MAC's local guest joins this virtual L2 segment]
```

The Mac's existing experimental DOSBox-X `ETHNET` protocol wraps entire emulated Ethernet frames in UDP. It includes a **nonstandard L2TPv3-like handshake**, not regular L2TP and not something a generic VPN or RetroArch core can speak automatically.

## What PoC 1 contains and demonstrates

- A Kotlin/JVM, Android-reusable codec for the DOSBox-X ETHNET control, session and data envelopes.
- A UDP transport that exchanges SCCRQ/SCCRP/SCCCN and ICRQ/ICRP/ICCN, sends HELLO/ACK keepalives, transports complete Ethernet frames and disconnects cleanly.
- A numeric Tailscale IPv4 allowlist for production use. Tests only may use localhost.
- 13 codec checks plus a fake-Mac UDP peer regression test that exercises framing, handshake, both frame directions, keepalive and shutdown.
- Mac host setup instructions and an actionable Android NE2000/emulator integration checklist.

This does **not** verify compatibility with a real DOSBox-X server: the fake peer implements the protocol behavior observed in the DOSBox-X source. It also does not implement, compile or launch an Android emulator. No WinKit guest images, Windows media or DOSBox-X binaries are included.

## Files

- `transport/src/main/kotlin/org/winkit/transport/EthnetWire.kt` — wire format and parser
- `transport/src/main/kotlin/org/winkit/transport/EthnetSession.kt` — Android-compatible UDP client, intended for a native emulator's NE2000 frame hooks
- `transport/src/main/kotlin/org/winkit/transport/EthnetCli.kt` — JVM-only transport smoke CLI (not a guest boot)
- `transport/ethnet-poc-jvm.jar` — **generated locally by `RUN_OFFLINE_TESTS.sh`**; not included in public source, not an Android APK
- `tests/test_mock_ethnet.py` — mock UDP peer regression
- `RUN_OFFLINE_TESTS.sh` — build and regression runner (requires JDK, Kotlin compiler, Python 3)
- `docs/ANDROID_EMULATOR_INTEGRATION.md` — engineering steps to obtain a local Windows boot
- `docs/MAC_HOST_SETUP.md` — existing RC23 Mac path, independent disk and Tailscale precautions
- `docs/ACCEPTANCE_MATRIX.md` — what must be proved on real devices
- `docs/SECURITY_AND_LICENSES.md` — important warnings and provenance

## Running the offline transport checks

```bash
bash RUN_OFFLINE_TESTS.sh
```

When the existing Mac's **actual** DOSBox-X ETHNET UDP server is running, a **development machine** with this JAR, Java and Tailscale can attempt the protocol handshake (does not boot Windows):

```bash
bash RUN_OFFLINE_TESTS.sh   # builds the test JAR first
java -jar transport/ethnet-poc-jvm.jar 100.x.y.z 8
```

Replace with the Mac's real Tailscale IPv4. First configure the tailnet to permit only the intended Android/dev peer to reach Mac UDP 1234, and restrict the service at the host firewall. **Do not forward this port from the public internet.** The current DOSBox-X server may listen on all IPv4 interfaces.

## Compatibility decision

**Preferred integration**: attempt a native Android SDL-based DOSBox-X build/fork, so that its existing NE2000 interface and the same guest emulated hardware configuration can be reused. Android support, performance and the desktop-oriented DOSBox-X UI are significant porting risks.

**Alternative**: boot the guest with DOSBox Pure via a compatible Android libretro frontend, then add a *new* Ethernet frame bridge at the NE2000 adapter boundary. DOSBox Pure's published NE2000 multiplayer uses RetroArch netplay and does **not** speak DOSBox-X's custom ETHNET UDP handshake out of the box.

**Next real product milestone**: demonstrate a DOS 6.22 / WfW 3.11 guest actually booting in the Android emulator offline; connect `sendEthernet()` to NE2000 TX and `pump()` callbacks to NE2000 RX. Only then run a live end-to-end NetBEUI file/WinPopup test on separate devices.

## Upstream technical sources

- [DOSBox-X ETHNET source](https://github.com/joncampbell123/dosbox-x/blob/master/src/misc/ethernet_ethnet.cpp) (experimental, nonstandard packet protocol)
- [DOSBox-X 2026.08.02 release](https://github.com/joncampbell123/dosbox-x/releases) (ETHNET feature description)
- [DOSBox Pure README](https://github.com/schellingb/dosbox-pure) (NE2000 / RetroArch multiplayer)
- [DOSBox-X WfW3.11 guide](https://dosbox-x.com/wiki/Guide%3AInstalling-Windows-3.1x)

PoC 1 is a separate Android branch artifact. It **does not change RC23** or the established Mac guest disks, and does not advance the public DMG release gate.
