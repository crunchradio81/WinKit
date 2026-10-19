# Developer handoff / next implementation batch

The complete Android emulator binary/APK is **not** in this package. Prioritize engine feasibility and the exact legacy Ethernet transport, not UI decoration.

## Highest-priority source inspection

- [ ] Check out public `CrownParkComputing/Retro-Dosbox` and its `dosbox-x-sdl3` submodule; compare its exact DOSBox-X ancestry against 2026.08 ETHNET changes.
- [ ] Determine what native Android core API exists for loading a *bootable raw hard drive image*, keyboard, mouse, audio and NE2000 network.
- [ ] Verify/fill ETHNET packet protocol wire compatibility with Mac `2026.10.01`. If fork feature absent, forward-port from original upstream with audit, not ad-hoc guessed UDP encap.
- [ ] Build `arm64-v8a` debug library with reproducible SDK/NDK pins. Check interpreter fallback and whether JIT is available/safe.
- [ ] Boot a free synthetic DOS test (no Microsoft media) locally in Android. Only after that transfer user's private exported `.raw`.
- [ ] Establish socket/Tailscale route at the application layer; then prove raw virtual NE2000 frames; only then attach the WFW guest to Mac's NetBEUI network.
- [ ] Rename cloned WFW computer `YARDOMOB` while offline and assign unique locally administered NIC MAC; never bring two devices online with same Windows computer name.
- [ ] Deliver signed debug APK and one-shot device acceptance report; promote no feature without testing.

## Acceptance classification

- `SOURCE_RESEARCH` means confirmed in public upstream or repo documentation.
- `OFFLINE_TESTED` means test scripts run here with synthetic image or localhost loopback.
- `MAC_VERIFIED` means real DOSBox-X on user's Mac verified in prior acceptance cycle.
- `ANDROID_UNTESTED` means not run on actual Android hardware.

The first Android deliverable should be a bootable local guest, **without** NetBEUI connected. The second should be a bidirectional LAN pass. The last is refined touch UI, media operations, and packaging. Mac two-peer regression is a required final gate.
