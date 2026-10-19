# 05 — Engine strategy / Android construction plan

## Recommended implementation sequence

**A0: Freeze the Mac ABI/protocol reference.** Record DOSBox-X `2026.10.01` executable checksum, active working guest `.conf` (mount, NIC and ETHNET settings only), guest disk SHA, RAM/video and sound. Keep baseline unchanged while developing Android.

**A1: Investigate a working Android core.** Clone `CrownParkComputing/Retro-Dosbox` with submodules as *research*, inspect `core` submodule commit and `dosbox-x-sdl3` fork; check whether it includes `[ne2000] backend=ethnet` and `ETHNET STARTSERVER/CONNECT`, wire protocol and packet size, and whether Android NDK build includes its socket/SDL_net dependencies. Compile a stripped ARM64 engine. Compile/run a basic DOS sample before transferring the proprietary guest.

**A2: Android guest boot.** Use a separately exported, checksum-verified `.raw`. Implement boot from image via native core equivalent of `IMGMOUNT 2 <image> -t hdd -fs none` + `BOOT C:` or the exact validated Mac boot chain. Do not assume the sample is a complete autoexec; read Mac boot config. Prove Program Manager and keyboard/mouse work with ETHNET disabled.

**A3: Android hardware parity.** NE2000 NIC IRQ/base and MAC distinct; display, WAV and MIDI, guest resolution changes, app suspend/resume, floppy read and write protection, ISO. Record failures distinctly.

**A4: ETHNET/Tailscale.** Match upstream wire protocol exactly (merge/port feature into Android core if absent); test generic IP connectivity, establish ETHNET, then verify NetBEUI. This is a crucial functionality gate; no release claim without bidirectional WinPopup and actual file-sharing evidence.

**A5: WinKit launcher.** Native Android Activity/SDL rendering, image picker/store, guest profiles, status, import wizard, input mapper, clean exit and logs. Keep this thin and period-authentic. Preserve source/config/docs separate from test artifacts and guest media.

**A6: Packaging.** One private debug APK for device QA after source build passes, then release APK with source and GPL notices. No licensed guest image, Microsoft drivers, proprietary disk images, or unclear binaries in public GitHub artifacts. In a local personal installer, source media is user-provided and processed on their machine.

## Candidate codebase

Open source Android port reference: https://github.com/CrownParkComputing/Retro-Dosbox
Upstream DOSBox-X: https://github.com/joncampbell123/dosbox-x

In a working developer environment (network + Android SDK/NDK/Gradle):

```bash
# Investigative only. No cloning is performed by this ZIP.
git clone --recurse-submodules https://github.com/CrownParkComputing/Retro-Dosbox.git
cd Retro-Dosbox
# Review licensing and networking support before compiling.
git submodule status
git -C core rev-parse HEAD
rg -n 'ethnet|EthNet|backend=ethnet|Ethernet' core android frontend || true
# Repository documents a build using android/build-core.sh and Gradle;
# consult the checked-out version before invoking scripts.
```

Do not claim that the upstream Android port builds cleanly on this Mac without the Android SDK/NDK available; this is a reference plan, not a completed APK.

## Proposed standalone WinKit Android repository

```text
WinKit311Android/
  README.md
  app/                  # Android launcher & native controls
  core/                 # pinned DOSBox-X derived core, submodule/patches
  network/              # ETHNET adapter integration, no proprietary media
  media/                # SAF staging, imports, integrity
  docs/                 # architecture, UX, diagnostics, tests
  tests/                # fake disks, synthetic frame tests
  scripts/              # host export/build/test utilities
  licenses/             # GPL and dependencies
```

## Decisions to freeze before Android core integration

- Guest disk source choice (which **stopped working clone**), size and boot chain.
- Android ABI and min Android target validated on hardware.
- DOSBox-X wire protocol version / ETHNET framing and whether the selected fork implements it.
- Storage layout and private transfer method.
- How guest Windows name is changed before LAN attachment.
- Whether port 1234 is TCP or UDP according to source/trace, not assumption.
- Mac Tailscale endpoint and access policy limited to authorized tailnet devices.
