# 02 — NetBEUI virtual LAN over Tailscale

## Requirements (and what is NOT equivalent)

WinPopup messages and WFW SMB/file sharing over NetBEUI/NetBIOS Frames require access to **virtual Ethernet frames**, including broadcast/multicast and non-IP EtherTypes. A Tailscale tailnet delivers routed IP packets and application sockets, **not** a shared Ethernet broadcast segment. Simply enabling Tailscale on both hosts, enabling `backend=slirp` (TCP/IP NAT), or exposing an SMB-over-TCP port won't carry NetBEUI as-is.

DOSBox-X **2026.08.02** introduced experimental `ETHNET` client/server for NE2000 virtual networking across DOSBox-X instances, intended to work over host IP/Wi-Fi without pcap promiscuous mode. In current config reference, `[ne2000] backend=ethnet` is an explicit choice. The Mac machines have already used ETHNET on the same Mac. The key unverified work is **Android engine parity** and its ETHNET client connecting over Android's Tailscale route.

## Proposed direct path (preferred)

1. On Mac, keep its verified ETHNET server machine as a relay. Prefer the existing YARDO configuration/port rather than setting up a separate untested Mac bridge. Mac Tailscale app should run when off-LAN access is wanted.
2. On Android, install/enable Tailscale VPN, signed into the same authorized tailnet, with Mac visible and reachable at a tailnet 100.x.y.z IPv4 address. Android Tailscale has no CLI; use its app to inspect connectivity; Mac CLI (if installed) can run `tailscale ip -4`.
3. Android emulator needs **matching ETHNET wire protocol** and `[ne2000] backend=ethnet`. Inside emulator (before guest OS boot) client concept is `ETHNET CONNECT <MAC_TAILSCALE_IP> <SERVER_PORT>`. The exact command parameters and transport must be checked against the Android-pinned core; never assume a generic UDP echo implements ETHNET.
4. Android guest NE2000 hardware settings must match the *installed Windows NE2000 NDIS driver* (base I/O, IRQ) from the Mac clone; emulated MAC must be unique. Use a different Windows Computer Name; same workgroup allowed.
5. WFW starts networking with the existing DOS/Windows network stack. Then test NetBEUI frame receipt, Workgroup browsing, WinPopup AND actual read/write using a small test share. Both directions, not just one.

## Protocol/version compatibility gates

G1 — Mac: capture actual DOSBox-X version and `ETHNET` host commands/port, NIC settings and binary support; compare expected transport (TCP vs UDP) using upstream source or packet trace. Verified same-machine working does not establish cross-version interop.

G2 — Android core: inspect source under `Retro-Dosbox/core` and parent upstream DOSBox-X for *exact* ETHNET feature and version. If missing, port/fork upstream ETHNET code including its socket library dependencies, or build matching DOSBox-X on Android. No speculative protocol shim.

G3 — IP tunnel: run the bundled `net_probe.py` over Tailscale, **on port 15431, separate from ETHNET**. This proves only application UDP connectivity. A passed UDP probe is not proof that ETHNET uses UDP or that Windows networking works.

G4 — ETHNET: launch Android emulated NE2000, connect to Mac ETHNET server via Mac's tailnet IP. Record negotiated status, raw frame counters and logs. Test both unicast and broadcast frames; preserve MAC separation.

G5 — Guest networking: WinPopup Android -> Mac, Mac -> Android; workgroup browsing; file open/read/write both directions; survive suspend/resume and VPN reconnection (avoid false positive from cached browsing).

## IP transport probe (optional future device test)

On the Mac in Terminal, first find Mac's tailnet IPv4 using Tailscale GUI or `tailscale ip -4`, then (replace illustrative IP):

```bash
python3 scripts/common/net_probe.py server --bind 100.101.102.103 --port 15431 --timeout 30
```

On Android Termux *while Android's Tailscale VPN is running*:

```bash
python scripts/common/net_probe.py client --host 100.101.102.103 --port 15431 --timeout 5
```

This requires Python in Termux and access permission from tailnet ACL/grants and macOS firewall. Don't publicly expose the test port; don't use `0.0.0.0` unless deliberately confined to a trusted local test. It sends only its own prefixed test messages, never guest frames.

## Unresolved questions deliberately not glossed over

- What wire protocol and port behavior does `ETHNET` use in exact Mac 2026.10.01 build? Source-code confirmation before firewall recommendations.
- Does the Android port's native networking support ETHNET, including broadcasts? The publicly available Android port does not automatically prove it.
- Will Android's VPN routing permit the app-specific socket path on this device? Device test required.
- If macOS Tailscale runs with local client firewalls, will server bind to tailnet IP / all interfaces? Need actual log and safe firewall rules.
- Do the Mac's two local guests run one common ETHNET server; how does adding a third peer affect sessions? Regression required.
- How are reconnects handled without reboot/guest deadlock? Test after stable first connection.

## Why not libslirp or Tailscale alone?

DOSBox-X documents SLIRP as TCP/IP NAT, not a general-purpose arbitrary legacy Ethernet LAN. Tailscale offers routed IP host connectivity, not Windows 3.11 NBF/NetBEUI forwarding. The ETHNET protocol is the relevant link between those layers.
