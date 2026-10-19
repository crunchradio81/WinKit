# Mac as a Windows for Workgroups guest-network host

Existing RC23 baseline: `bridge_ctl.py` knows about YARDO and `ETHNET STARTSERVER 1234` through its `--mode host`. This is the existing **guest Ethernet** server, not macOS file sharing and not VNC.

1. Make an offline backup and shut down any already-running DOSBox-X instance that has the YARDO writable `.raw` disk mounted. **Do not start a second process on the same disk.**
2. Install and sign in to Tailscale on the Mac and Android. Confirm each device is a member of the intended tailnet and record the Mac's numeric `100.64.0.0/10` IPv4 address. The Android app will use the Android Tailscale VPN and an ordinary UDP socket to that IP.
3. Ensure the Tailscale grants restrict Android devices to the Mac's **UDP 1234** only as needed. Ensure the Mac's network/firewall policy blocks unsolicited traffic to UDP 1234 outside the intended tailnet. Existing ETHNET binds to all interfaces; Tailscale login alone is **not** a host-socket access restriction.
4. From the installed RC23 host scripts, run read-only preflight (no guest boot):

   ```bash
   python3 tailscale_probe.py --role host
   python3 bridge_ctl.py plan --mode host --transport tailscale
   ```

5. When the macOS host/guest is ready, launch exactly once (changes live host process state):

   ```bash
   python3 bridge_ctl.py start --mode host --transport tailscale
   ```

6. Inside the Mac guest verify WfW3.11 is running and the network services are initialized. The old working NetBEUI path uses compatible NE2000 driver setup and appropriate `NET START` initialization as documented in the main WinKit project. Do not claim readiness merely because UDP 1234 is listening.
7. From a *separate* Android test device/guest, attempt connection to the Mac's Tailscale IPv4. On success, verify bidirectional `\\YARDO\SHARETES` access and WinPopup; never infer full guest-network functionality from a handshake alone.

**No routing of native NetBEUI by Tailscale:** the phone and Mac exchange IP/UDP, but the emulator tunnels original Ethernet frames inside it. The existing `ETHNET` server also attaches its own Mac guest to the segment via an internal loopback client; the Android client will be an additional participant.

**Do not**: enable internet-facing port forwarding, put a live `.raw` guest image in a shared cloud folder for simultaneous access, assume `backend=slirp` routes NetBEUI frames, or enable outdated SMB1 services directly on macOS for this purpose.
