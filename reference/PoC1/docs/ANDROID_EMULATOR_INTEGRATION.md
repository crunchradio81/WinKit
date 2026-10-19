# Android local-guest implementation checklist

## Required on-device guest behavior

- Boot the phone/tablet's own x86 virtual PC: DOS 6.22 then Windows for Workgroups 3.11.
- Store a **separate writable copy** of a user-supplied, legally obtained guest image in Android app-private storage. Do not stream or mount the Mac's live guest disk.
- Match the imported guest's historical emulated hardware; for networking that means NE2000 I/O base and IRQ identical to the configured guest drivers, not just NIC presence. In the WinKit RC23 Mac config those settings should be discovered from the actual `PeerA-Print.conf` before porting rather than assumed.
- Use a unique locally administered **unicast** MAC per Android installation (e.g. first octet `02`) and a distinct WfW computer name (e.g. `YARDO-A`). The fixed test MAC used by the CLI is a regression fixture, **not** a production constant.
- Port touchscreen mouse/pointer mapping, on-screen keyboard including Ctrl/Alt/Win key combinations, audio, MIDI, file picker and safe guest shutdown. High-resolution S3 graphics remains a separate Windows driver test, not the same as scaling the Android display.

## Emulator choice — assessment, not an installed component

Option A: an Android build/fork of DOSBox-X with SDL2 + JNI/NDK. Best match for existing Mac guest, ETHNET NE2000 and S3/printing configuration, but upstream Android release/build support is unverified and a full UI port is a substantial effort. Port one known-good DOSBox-X commit and inspect GPL/licensed dependencies.

Option B: RetroArch Android + DOSBox Pure core. Has a documented OS boot path, emulated NE2000 and touch input options. Its multiplayer NIC tunnel uses RetroArch's own netplay transport, so it cannot connect directly to Mac DOSBox-X ETHNET. A DOSBox Pure native fork must expose NE2000 send/receive frame hooks or a custom frontend networking extension; unchanged off-the-shelf apps are **not** sufficient for NetBEUI peer networking with RC23.

## Exact transport integration seam

When the emulator emulates the guest writing an Ethernet packet through its NE2000 hardware:

```kotlin
// Pseudocode: callback names MUST be adapted to the selected emulator.
fun onGuestNe2000Tx(fullEthernetFrame: ByteArray) {
    session.sendEthernet(fullEthernetFrame)
}

// Dedicated I/O thread or event pump, not the Android UI thread:
while (guestIsRunning) {
    session.pump { fullEthernetFrame ->
        emulator.enqueueNe2000Rx(fullEthernetFrame)
    }
}
```

`EthnetSession.connect(macTailnetIpv4, 1234, guestMac)` does the DOSBox-X-compatible L2TP-like ETHNET handshake in Kotlin. Its `pump()` performs UDP reads, keepalives, and emits received Ethernet frames (including 802.3 LLC / NetBEUI frames) unmodified. It **must** run on a dedicated thread and guest RX must be safely marshalled to the emulator's execution thread. The app must handle reconnect after sleep/Tailscale network changes and must suspend/write-protect storage during any snapshot or guest-export operations.

On Android, use `android.permission.INTERNET`. There is no need for root or a virtual TAP device if the emulator exposes frame callbacks; ordinary app sockets go through Tailscale's Android VPN when its routes are active. For background runs, Android foreground-service restrictions and battery optimization need explicit handling; the PoC does not implement that.

## Boot path

1. First **offline** milestone: Android boots its independent Windows 3.11 guest and shows Program Manager; keyboard/mouse work.
2. Confirm NE2000 adapter and Windows Network Setup bind correctly, with unique name and MAC.
3. Turn on Tailscale and join the Mac UDP 1234 endpoint using the Kotlin transport.
4. Test WinPopup and Windows for Workgroups Network Neighborhood/UNC shares. Because NetBEUI uses broadcast Ethernet, the tunnel must relay L2 broadcasts, not merely TCP/IP unicast.
5. Test two-way file transfer, conflict-safe writes, printer routing and disconnect/reconnect behavior.

## Known unverified points

Actual DOSBox-X 2026.08.x Android compilation, performance on ARM64, graphics driver compatibility, live ETHNET handshake, independent guest NIC RX/TX timing, WfW NetBEUI over mobile networks, and two-device print/sharing behavior all remain **UNTESTED**.
