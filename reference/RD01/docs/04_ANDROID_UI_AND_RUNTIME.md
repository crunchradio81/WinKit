# 04 — Android presentation & platform integration

## Main experience

WinKit Android is a classic WFW companion PC, not a catalog or DOS game launcher. Single machine dashboard: Windows 3.11 thumbnail, Boot, Shutdown, Networking (Disconnected / Tunnel Connected / Legacy LAN Verified), A: floppy, D: CD, Local Files, Settings, Diagnostics. Keep metallic silver/blue period-inspired styling aligned with approved Mac Control Center; Android controls must be comfortable on touch.

## Controls

- Default landscape orientation for 800x600 or 1024x768 guest; fit to physical screen with preserved aspect ratio; user-selectable integer scale when possible, viewport/zoom, and optional portrait virtual trackpad area.
- Touchpad mode (relative pointer, tap-to-click, two-finger right-click, drag lock, two-finger wheel) and direct touch mode (tap translated to guest coordinates) independently switchable.
- On-screen Windows keys: Ctrl, Alt, Shift, Esc, Tab, Enter, F1-F12, Print Screen, arrow cluster, and three-button mouse; software keyboard and hardware Bluetooth keyboard/mouse support.
- Safe keyboard capture with a visible method of leaving the emulator; no gesture conflicts with Android navigation bars.
- Mouse-cursor scaling must track resolution changes (800x600, 1024x768, 1280x1024, 1600x1200) without coordinate drift; preserve guest vs host scaling as separate settings.

## Guest runtime

- CPU emulation performance and wake/sleep/idle stability; interpreter fallback when ARM64 dynamic recompilation is unavailable or unstable. Do not claim a JIT port until verified.
- NE2000 NDIS settings identical to guest's installed adapter; ETHNET backend selectable, not assumed.
- Sound Blaster digital WAV; MIDI requires an actual Android synthesizer/soundfont route if DOSBox-X core does not provide one. Mac pass is not automatic Android pass.
- Removable floppy images: copy-on-write by default for read/write tests; explicit write protection; safe insertion/removal only when guest I/O is idle; support single/multiple disks and automatic prompt.
- CD-ROM ISO/BIN/CUE path selection; `CUE` should remain associated with relative BIN tracks; access via staged local files when SAF provider can't give ordinary paths.
- Guest printing: **not** automatically solved by Mac host Ghostscript. Design future Android-local PostScript file capture + PDF export via Android APIs/Ghostscript-compatible converter; avoid premature promises about DOCX/RTF conversion on mobile.
- No opaque disk mutation or automatic repair when a guest unexpectedly closes; prefer per-session output logs and snapshot restore.

## Android storage and app lifecycle

Use Android Storage Access Framework with explicit permission to import images, then stage writable HDDs into app-specific persistent storage, with SHA verification and available-free-space check. Don't assume `content://` URIs are ordinary paths or that external storage permits random write. Handle configuration changes, app pause/resume, audio focus, system low-memory stop, device rotation, lost VPN connection. Foreground playback/long-running emulation may need current Android platform policies. Graceful Windows shutdown should precede emulator stop, with recovery marker if interrupted.

## Android minimum device requirements: to be measured

Target 64-bit ARM (`arm64-v8a`), Android 11+ as initial engineering baseline rather than final product promise. Physical memory/storage needs depend on actual disk and selected emulator core; benchmark on the user's hardware before choosing final minimum. No Google Play store packaging commitment until native port, licensing, crash/lifecycle and legacy-LAN acceptance pass.
