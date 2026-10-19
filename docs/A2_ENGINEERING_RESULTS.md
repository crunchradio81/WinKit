# A2 engineering results / acceptance ledger

Date: 2026-10-19. This is **host-tested integration SOURCE**, not an APK or emulator claim.

## Completed this pass

| Feature | Engineering state | Evidence |
|---|---|---|
| Pinned upstream Android DOSBox-X candidate selected | Source pinned | Parent `65e8c67`, core submodule `4167c3ac` |
| Same-process Android UI-to-SDLActivity integration source | Implemented, uncompiled | `overlay/kotlin/WinKitSetupActivity.kt`, A2 manifest transform |
| Independent SHA256-verified app-private HDD import | Source implemented; JVM logic already host-tested A1 | `overlay/kotlin/GuestStore.kt`, A1 116 assertions |
| Private system identity source | Implemented; NIC binding NOT implemented | `GuestIdentity.kt`, Android prefs |
| Offline boot-config conversion | Compiled JVM tests PASS | `OfflineBootConfig.kt`, 24 A2 assertions |
| Native machine library discovery and offline check | C++ source insertion scaffold; helper compiled/tested native host | `winkit_offline_gate.h`, 6 assertions |
| Revision-locked Android upstream patch transform | Source rewrite fixture tests PASS | `scripts/apply_a2_overlay.py`, 5 tests |
| Existing A1 offline/native suites | PASS in later run; combined runner completed to PASS log | `scripts/RUN_A2_OFFLINE_TESTS.sh` |
| Retro-DOS SDL3 native Android ARM64 library | NOT BUILT | no SDK/NDK or upstream Git checkout in execution environment |
| Retro-DOS Android APK | NOT BUILT | no Android Gradle build |
| Actual DOS 6.22 boot | UNVERIFIED | no guest image distributed or device execution |
| Actual WFW 3.11 boot, SVGA/audio/input | UNVERIFIED | hardware validation pending |
| Android ETHNET / NE2000 integration | NOT IMPLEMENTED | third-party build disables SDL_net |
| WinPopup / NetBEUI over Tailscale to Mac | NOT IMPLEMENTED / UNVERIFIED | no live peer test |

## Offline tests run

- Native C++ offline-gate 6 assertions: passed.
- Kotlin standalone offline converter 24 assertions: passed.
- Python fail-closed overlay rewriting 5 tests: passed.
- A1 JNI/POSIX disk locking host test 1 passed; JVM GuestStore/MAC 116 assertions passed.
- A1 upstream ETHNET Kotlin 10 codec checks passed, not network interoperability.
- A1 boot config 4 tests passed.
- RD01 10 tests passed (separate run too); PoC1 2 mock tests passed (separate run too).
- A1 4 package safety checks passed.

One combined runner invocation was interrupted by the execution environment's timeout,
**but its captured final log states** `ALL OFFLINE TESTS PASSED` and `A2 OFFLINE PASS`.
Tests were also individually run where useful to confirm deterministic behavior.

## Implementation notes

The upstream frontend has an existing `Game.conf_override` launch path which invokes
`retrodos_host_run(5, argv)` with `-conf` from a separate engine thread. A2 inserts a
single offline `Game` into `refresh()` only when app-private `YARDOMOB.raw`, the
converted config, and `OFFLINE_READY` marker exist and the native gate approves it.
This is an actual source integration route, but it is not verified by upstream Android
compilation or device launch yet.

The A2 Android UI uses the Android SAF picker, but imports to its OWN `filesDir`; the
native frontend uses `SDL_GetAndroidInternalStoragePath()` for the same location.
The setup Activity does not mount the Mac source image; it accepts only a private copy.

### Explicitly unfinished

- Build and link actual Android 64-bit SDL3 and DOSBox-X engine; test overlay compilation.
- Improve cross-process image lease and clean-shutdown invariants with engine ownership.
- Establish actual BIOS/IDE and DOS/WFW boot, video/audio/keyboard and pause/resume.
- Verify copied WFW computer name inside offline guest and persist proof of renaming.
- Confirm WFW/NE2000 card address matches a persistent unique MAC before LAN phase.
- Enable SDL_net ETHNET or implement a native NE2000 Ethernet frame adapter with actual
  deterministic interoperability tests (current Kotlin mock doesn't satisfy this).
- Only then enable deliberately opt-in Tailscale transport; no remote display ever.
