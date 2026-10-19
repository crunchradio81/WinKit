# A10 engineering / GitHub repository handoff

## Status and claims

Only host JVM/source test work is completed. The connected public repository
`crunchradio81/WinKit` was discovered and its default `main` branch initialized
with a clear status/safety README. Source publication is **not** complete until
`scripts/PUBLISH_A10_GITHUB.sh` successfully pushes the separate
`android-a10-source` branch. No actions workflow, Android binary, APK, BIOS boot,
DOS/Windows 3.11 boot, or ETHNET/Tailscale transport has been verified.

## Critical fix: no-overwrite publication

The A9 staging code used `File.renameTo()` for the disk and boot profile.
On Linux-like filesystems this can replace an existing destination, even after
an earlier `!exists()` check succeeds. That check can race a second writer.

A10 replaces these publication points with `Files.createLink(destination, staged)`
for the same-directory, app-private staging files, after writing and fsyncing
the staged file. `createLink` **fails if the destination is already present**;
linking the complete staged inode and then unlinking the staging name leaves
an intact destination. This is a fail-closed choice on an unsupported storage
filesystem. Android API >=28 supports java.nio.file linking, but behavior on a
real device is unverified. Private disk files must be on internal app storage
supporting hardlinks; external SAF files must be copied into private storage.

`WinKitSetupActivity` now stages/publishes the readiness marker exclusively as
well, rather than using `writeText` that can truncate an existing marker.

Caveat: hardlinks are not a substitute for the native exclusive disk lease,
crash recovery, permission protection or authenticated disk-export workflow.

## Offline tests

`bash scripts/RUN_A10_HOST_TESTS.sh` compiles the new Kotlin tests, exercises
races against the destination, runs targeted A9 policy parity, A8 package contracts, A6 native snapshot checks,
and verifies
the SHA256 manifest. Set `A10_FULL_INHERITED=1` for an additional full legacy regression sweep,
which can be slow. A successful host test is **not** emulator execution.

## Android ARM64 CI

The workflow `.github/workflows/a10-android-arm64.yml` requires all A10 source
files uploaded to GitHub before it can run. It installs SDK/NDK/Java/Kotlin,
compiles the pinned upstream DOSBox-X SDL3 port using the A9 build chain,
checks packages, and only uploads an APK if compilation succeeded. This CI
workflow is **not yet executed** and does not prove local guest boot.

## GitHub publication without Mac testing

The source tree includes `scripts/PUBLISH_A10_GITHUB.sh`. Use a Git-capable
Linux or Android Termux shell with GitHub credentials already configured.
It clones the GitHub repository, copies approved source files, scans the staged
index for guest disks/binaries, and pushes to a new `android-a10-source` branch.
It never modifies the `main` ref or force pushes; it refuses an existing remote
branch. The script supports a private local mock remote for offline testing.

Mac RC24.3, YARDO and YARDO-B and all associated active images and backups
remain outside this source tree. No licensed DOS/Windows media are included.
