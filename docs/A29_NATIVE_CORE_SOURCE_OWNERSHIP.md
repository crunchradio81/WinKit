# A29 native emulator source ownership — recovery and build contract

## Actual diagnostic status (October 22, 2026)

- GitHub Actions build #42 **did compile** DOSBox-X SDL3 and package `libretrodos.so`, `libSDL3.so` and `libc++_shared.so` into a signed debug APK.
- It **did not prove successful execution** of the DOSBox-X core or a guest boot. The user's A26 hardware test still returned from the emulator to the launcher when **Kbd** was pressed, and DOS 6.22 / WFW 3.11 from the Mac had not booted.
- Successful build #42 fetched the exact customized core revision `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11` at the time, as shown by the submodule checkout log. GitHub's temporary runner was then destroyed, and the published APK artifact includes binaries, **not the Git source history**.
- `CrownParkComputing/dosbox-x-sdl3` presently returns GitHub 404. The parent `CrownParkComputing/Retro-Dosbox` is public but its `core` entry is only a Git submodule pointer, not an embedded source tree.

**Therefore, a build that reuses the old APK would freeze the native Kbd/lifecycle failure. The goal is a source-owned, fully rebuildable native emulator.**

## Import full editable source into the WinKit repo

WinKit now has a source-import script:

```bash
# Execute from a full, clean local clone of the A29 branch
git checkout android-a29-mac-transfer-isolated
bash scripts/VENDOR_A29_PINNED_CORE.sh /path/to/authorized/dosbox-x-sdl3-git-clone
# Or, only if restored to public accessibility:
bash scripts/VENDOR_A29_PINNED_CORE.sh https://github.com/CrownParkComputing/dosbox-x-sdl3.git
```

The script requires the **exact source Git commit** above, its Retro-DOS host integration headers, and an unmodified clean destination branch. It imports via **non-squashed Git subtree** at `vendor/dosbox-x-sdl3/`; the original commit remains in WinKit's Git ancestry. It refuses overwrite, unexpected symlinks/nested submodules, and GitHub-prohibited oversized objects; it regenerates `MANIFEST.sha256` including the imported sources. Users should review the imported Git diff, license notices, size and origin, and then deliberately push the A29 branch to GitHub.

**This script is prepared but has NOT yet imported any actual SDL3 sources.** An accessible authorized source clone is still required. A compiled APK cannot recover editable source or original history.

## Full source compilation architecture

Once the exact source has been imported, the A29 GitHub workflow checks out **complete WinKit Git history**. Its SDL3 source preflight validates that:

1. `HEAD:vendor/dosbox-x-sdl3` resolves to precisely the Git tree of `4167c3a...`.
2. The normal `MANIFEST.sha256` verifier covers every imported file.
3. `scripts/bootstrap_core.sh` checks out the original pinned core Git commit from the WinKit checkout itself (`WINKIT_CORE_HISTORY_GIT`), not from the defunct Crown Park Computing submodule URL.
4. Existing exact-source patching, A18/A26 keyboard-lifecycle instrumentation, native C++ compilation, packaging and APK native-library checks still apply.

To edit native runtime behavior, revise the tracked overlay transforms in `scripts/apply_a2_overlay.py`, or make a reviewed, re-pinned change to the vendored core and its source-integrity/gate contract. Never patch an untracked runner source tree as the only source of truth.

## What this does not change

No Mac guest disk, Windows media, network credentials, or installed Android A26 data is imported to GitHub. Separate `org.winkit.android.a29transfer` private storage remains required for the new APK. Independent first-boot identity setup and networking disablement remain enforced. No successful real-device guest boot has been claimed.

If no authorized clone can be obtained, the proper longer-term option is to port a documented, legally usable upstream DOSBox-X revision with a new source baseline and native regression tests. **That is a new porting task**, not pretending the old customized fork is present.
