# 07 — Security, ownership, licensing

- Project ZIP and GitHub contain *only* original scripts, research/docs and test fixtures. No Microsoft Windows, DOS, drivers, installed C: image, application binaries with unclear licensing or user-specific Tailscale credentials. User-provided licensed disk stays in private local export location.
- DOSBox-X is GPL-family licensed; a derivative Android runtime requires matching source availability and notices for any distributed build. Retro-DOS's repository states GPL-2.0 and includes third-party notices; audit exact submodule code/licenses before redistribution.
- Do not connect Windows 3.11 NetBEUI/SMB directly to the open Internet. Transport should run only over an authorized tailnet, with explicit peer allowlist where possible. Old WFW file sharing is not a modern secure file-sharing service.
- No attempt to expose raw Ethernet/L2 broadcasts directly through Android OS networking; keep them encapsulated within private emulator transport.
- Copy private HDD only while Windows is shut down; original Mac virtual disks and cold backup never touched.
- Keep Android machine distinct: computer name and NIC MAC collision can break NetBIOS resolution or cause wrong peer attribution.
- Tailnet access policy may need a narrowly allowed service port after ETHNET actual protocol is established. Do not open unverified port/protocol to Internet/public network.
- `net_probe.py` is intentionally a generic UDP echo demo. It has no encryption on its own; use only local loopback or on an already encrypted/authorized tailnet, never over the public Internet.
