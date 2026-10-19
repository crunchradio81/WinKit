# 06 — Acceptance gates and evidence

| ID | Gate | Pass criterion | Status 2026-10-19 |
|---|---|---|---|
| D01 | Safe Mac export | stopped working clone only, SHA match before/after; source unchanged | Helper implemented/offline tests; live Mac pending |
| D02 | Guest identity | Android name YARDOMOB, unique NIC MAC, no original peer name collision | Pending Android |
| A01 | Native Android boot | local image boots MS-DOS / Windows 3.11 to Program Manager without Mac | Pending Android |
| A02 | Input | click/double-click/drag/right-click, keyboard and Bluetooth peripherals | Pending Android |
| A03 | Video | 800×600 + 1024×768 plus fit screen and pointer correctness | Pending Android |
| A04 | Audio | WAV and MIDI each audible; volume and focus stable | Pending Android |
| A05 | Floppy | read, file write when writable, deny write when protected, safely swap | Pending Android |
| A06 | CD | read ISO; validate CUE/BIN where supported | Pending Android |
| N01 | Tailscale IP | Android app socket reaches Mac tailnet IP on dedicated test port | Generic probe available, device pending |
| N02 | ETHNET | raw virtual Ethernet link is established, unicast+broadcast frames observed | Pending Android core parity |
| N03 | WinPopup | YARDOMOB -> YARDO and YARDO -> YARDOMOB, text matches | Pending Android |
| N04 | NetBEUI SMB | files opened/copied to/from Windows shares in both directions | Pending Android |
| N05 | Link recovery | suspend/resume or VPN reconnect without silent file corruption | Pending Android |
| S01 | Lifecycle | clean Windows shutdown, no image hash mismatch; forced stop handled safely | Pending Android |
| P01 | Android packaging | signed personal debug APK installed; 64-bit ABI, no proprietary artifacts | Pending build |
| M01 | Mac regression | preexisting YARDO/YARDO-B messaging/shares still pass | Must rerun after Android join |

## Logging contract

Per test capture `timestamp`, `android_abi`, `android_sdk`, `emulator_revision`, `mac_dosbox_version`, `guest_image_sha256`, `guest_computer_name`, `guest_mac_address`, `ethernet_link`, `tailnet_ip_reachable`, `test_case`, `result`, `error_detail` (scrub credentials). Avoid logging Windows passwords, file contents, tailnet auth tokens, or full personal directory listings.

Stop on **first failed safety gate**. No repeated guest disk writes after unclear emulator crash. Hardware tests only occur on explicit test windows, not background automation.
