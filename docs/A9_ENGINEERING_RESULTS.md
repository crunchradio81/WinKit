# A9: native/Kotlin first-boot transport lockdown and device-evidence protocol

Date: 2026-10-19

## Scope and source status

**Not an APK, Android emulator run, DOS boot, WFW 3.11 boot, or live ETHNET link.**

A8's first-boot profiles required `ne2000=false` and `ipx=false`, but the
native and Kotlin validators did not restrict the `[serial]`, `[parallel]` or
other transport-related sections. This was a source policy gap, not proof of a
working external transport. A9 enforces the same rules in each checker:

* Reject `[ethernet]`, `[ethnet]`, `[network]`, `[ipxnet]`, `[modem]`, `[slirp]`
  sections during *offline* boot; full Ethernet bridging will require a
  separate, explicitly authorized future networking mode.
* Permit `[serial]` and `[parallel]` only when each option is `disabled`,
  `dummy` or `none` (these are a conservative source policy, not validation
  of all possible DOSBox-X per-port settings).
* Reject settings named `configfile`, `config file`, `autoexec`, `sdl_net`,
  `ethernet`, `tunnel`, `hostip`, `host port` across sections.
* Preserve default graphics, sound, RAM, MIDI and CPU profile sections.
  Legacy Mac configurations with live serial/parallel ports are deliberately
  rejected pending review; do NOT enable them by weakening the gate.

A deterministic differential test compiles the independent native C++ gate
and JVM Kotlin gate, then checks 74 negative and positive profile cases.
That test is a source-policy assertion, not a network penetration test.

## Genuine first-device acceptance (future)

`bash scripts/CAPTURE_A9_ANDROID_BOOT.sh` captures a screenshot, package
presence, Android ABI/API and relevant emulator diagnostics from **exactly one**
selected device. It does not read/export guest disks, join Tailscale, or
modify the Mac. It **never** certifies a passing boot: a person must inspect
`MANUAL_ACCEPTANCE.txt` and the screenshot showing the synthetic A4 BIOS banner.
Any captured log/screenshot is private, can reveal user data, and must be
reviewed before sharing. Evidence outputs are not included in the source ZIP.

## Host tests

```
bash scripts/RUN_A9_HOST_TESTS.sh
```

`A9_SKIP_INHERITED=1` runs only new source tests and the archive verifier.
Default also executes A8/A7/A6 regressions.

## Build attempt (requires separate Linux Android toolchain)

```
bash scripts/BUILD_A9_ANDROID.sh
```

The workflow `.github/workflows/a9-android-arm64.yml` runs these checks in
an independent Linux GitHub Actions runner when uploaded to a WinKit repository.
No connected WinKit GitHub repository was available during this pass; the
workflow was not triggered. No ARM64 ELF or APK was built in this environment.

## Preserved boundaries

The A9 sources contain no Microsoft installation media, guest hard disks,
Mac backup images, live tokens, or Tailscale credentials. Nothing touches
Mac RC24.3, YARDO, YARDO-B, guest disks, or backups. Full ETHNET frame
transport remains experimental and disabled.
