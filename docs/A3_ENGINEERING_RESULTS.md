# A3 engineering acceptance ledger — 2026-10-19

**Scope: offline research and source engineering. No macOS test required or performed.**

## Newly developed

| Component | Code or artifact | Evidence from this pass | State |
|---|---|---|---|
| Independent JVM boot policy | `overlay/kotlin/OfflineBootPolicy.kt` | 21 adversarial assertions | Host-tested |
| Native launch-time boot parser | `overlay/native/winkit_offline_gate.h` | 13 adversarial + 6 inherited native assertions | Host-compiled/tested |
| Converter output preflight | `OfflineBootConfig.kt` | 24 inherited converter assertions + new 21 JVM assertions | Host-compiled/tested |
| Image lease | `overlay/native/winkit_image_lease.h` | Lock acquisition, second-writer refusal, release and symlink refusal | Host-compiled/tested |
| Emulator worker integration | `scripts/apply_a2_overlay.py` | 5 Python source-transform fixtures + source contract checks | Patch-string tested; full upstream compile **NOT done** |
| Android setup Activity gate | `overlay/kotlin/WinKitSetupActivity.kt` | Static source-contract test | Source implemented; Android compile **NOT done** |
| Android ARM64 build + APK preflight | `scripts/BUILD_A3_ANDROID.sh` | Shell parser/source test | Build recipe only; **NOT run** |
| Package excludes guest disk and Microsoft media | source-contract test | File-type source tree audit | Passed |

## A3 targeted offline test command

`bash scripts/RUN_A3_TARGETED_TESTS.sh`

The host-test command checked these in this session:

- Native A3 image lease acquired/released and refused concurrent second lock,
  wrong machine filename and symlink.
- Native A3 offline boot profile: 13 assertions passed.
- Prior native A2 gate, compiled against stricter A3 header: 6 assertions passed.
- Kotlin A3 offline boot policy: 21 assertions passed.
- Kotlin A2 offline config converter against A3 policy: 24 assertions passed.
- Revision-locked overlay fixture tests: 5 passed.
- Source contract tests: 5 passed.
- Existing Python boot-config host tests: 4 passed.

The full inherited A1 + RD01/PoC1 combined suite was **not completed in this
A3 session** (an earlier combined invocation timed out while running inherited
checks). Earlier A2 documentation reports those prior offline passes; no
new A3 regression result is claimed for the full inherited suite.

## Why the gate needed revision

The A2 converter was comparatively strict, but the A2 native boot gate merely
looked for required strings and a disk path substring. A syntactically different
config with extra commands could pass its check. A3 explicitly constrains the
[autoexec] chain to one `IMGMOUNT` of the exact app-private disk (required
`-t hdd -fs none -size` flags and numeric geometry), followed by one boot of
C:, with only narrowly permitted presentation commands beforehand.

A3 intentionally refuses unsupported or unusual IMGMOUNT flags even if a full
DOSBox-X build could support them. When an actual known-good profile is
available, refine this parser using real evidence instead of relaxing it
blindly. This still cannot prove that DOS 6.22 or WFW 3.11 will boot.

## Hardware and network gates still false

- Integrated Android native DOSBox-X + SDL3 compilation **NOT RUN**.
- Android Gradle packaging / `libSDL3.so` / `libretrodos.so` integration **NOT VERIFIED**.
- APK **NOT BUILT**, installed or run.
- DOS 6.22 boot, WFW 3.11 boot, keyboard, mouse, video, WAV, MIDI **NOT VERIFIED**.
- Private imported image's actual guest identity `YARDOMOB` **NOT VERIFIED**.
- Proposed local MAC **NOT yet wired to virtual NE2000**.
- SDL_net-dependent ETHNET backend **disabled in pinned Android build**.
- Mac peers **not contacted**; no real WinPopup, NetBEUI, Tailscale test.

Mac RC24.3, YARDO, YARDO-B and backups were not modified. No Microsoft binaries
or private virtual HDD contents are included in the ZIP.
