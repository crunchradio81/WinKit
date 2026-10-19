# A2 protection and future LAN requirements

The Mac WinKit 3.11 RC24.3 app, YARDO/YARDO-B guests and backups MUST be left untouched.
A2 sources do not read any Mac files. The historical RD01 export tool only copies from
stopped known-working clones, verifies SHA and refuses originals. The user controls
when or whether to run it separately on the Mac.

A2 is offline by construction:

- New Android application ID `org.winkit.android.a2` (no collision with Retro-DOS).
- User-picked raw HDD is SHA-verified, staged in app-private storage, never distributed.
- Converted config names `YARDOMOB.raw`, preserves actual IMGMOUNT geometry and BOOT,
  explicitly has `ne2000=false` and `ipx=false`.
- Unknown boot commands, CD mounts, extra disks, absent source path and duplicate
  config sections cause an import rejection rather than a guessed replacement.
- Native machine tile is absent unless a committed config and private disk are present.
- Android INTERNET permission is removed, no network code is activated.
- Android app backup is disabled to avoid copying licensed installed guest disks.

**Not yet solved:** The native engine has no A2-exclusive disk lease held from before
`retrodos_host_run` through clean shutdown. The A1 POSIX DiskImage is only a probe;
never run multiple writers on an imported image. Future work must integrate an actual
lifetime lease with the engine, including crash/termination semantics.

**Before any LAN:** Change copied WFW computer name to `YARDOMOB`, attach proposed
locally-administered unicast MAC to emulated NE2000, verify it differs from both Mac
NICs, confirm correct NDIS settings, and test frame transport against DOSBox-X ETHNET
protocol end-to-end. Kotlin PoC1 is mock-only. NetBEUI should traverse actual Ethernet
frames over Tailscale transport, not VNC, SMB exposed to Android host, or RDP.

Never include a raw guest disk or original Microsoft files in public GitHub releases.
