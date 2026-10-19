# Third-party code and constraints

A1 code is released under GPL-2.0-or-later; the full GPL version 2 text accompanies it in `LICENSE.GPL-2.txt`.

The original user-provided handoff `reference/RD01` and `reference/PoC1` retain their own notices. The Kotlin PoC source files explicitly declare GPL-2.0-or-later. The JVM-only development JAR is **not distributed**; the PoC1 offline test script regenerates it locally from source.

Pinned external DOSBox-X SDL3 core and Retro-DOS frontend are not included in this ZIP: clone them separately with `scripts/bootstrap_core.sh`. On any subsequent distribution of a derived emulator/APK, supply corresponding GPL source and third-party notices for DOSBox-X, DOSBox, SDL3, Dear ImGui, and other integrated components as required by their respective licenses.

Microsoft DOS 6.22, Windows for Workgroups 3.11, drivers, installer disks, and proprietary third-party archives are **not included**, and cannot be put in the public APK or source ZIP. User's authorized private export is not a distribution asset.

## A2 source-overlay integration

A2 does not vendor the upstream Retro-DOS frontend or DOSBox-X core; its build script
requires exact publicly recorded commits and generates source patches in a separate
checkout. Any distributed binary must provide source/attribution under GPL and the
other applicable bundled upstream licences. Refer to pinned Retro-DOS and DOSBox-X
LICENSE/COPYING and SDL3 licensing. No prebuilt Android library or APK is in A2.
