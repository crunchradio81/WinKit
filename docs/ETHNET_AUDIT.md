# A1 ETHNET wire audit — real source vs old Kotlin mock

**Reference inspected:** `src/misc/ethernet_ethnet.cpp` of upstream DOSBox-X and pinned SDL3 DOSBox-X core (`4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11`). Upstream raw source:
https://raw.githubusercontent.com/joncampbell123/dosbox-x/master/src/misc/ethernet_ethnet.cpp

**Missing reference:** actual Mac executable `DOSBox-X 2026.10.01`, its exact source commit/configuration, and real UDP capture. Thus we have *source correspondence*, not Mac interoperability proof. The Mac's local two-guest WinPopup success remains MAC VERIFIED per handoff.

## Source-derived facts

- Transport is SDL_net **UDP**. Server and client use `SDLNet_UDP_Open(...)` and `SDLNet_UDP_Send/Recv`; default demonstration port 1234 comes from Mac tests, not wire standard. No TCP control/data channel.
- Custom, NON-STANDARD L2TPv3-like encapsulation, not ordinary authenticated/encrypted L2TP. Tailnet must protect host sockets; no Internet port forwarding.
- Control header: `C8 03` + 16-bit BE datagram length + 32-bit BE *destination control connection ID* + 16-bit Ns + 16-bit Nr (total 12 bytes).
- AVPs: BE 16-bit M/H/length (low 10 bits length including six-byte AVP header), BE vendor, BE attribute, payload. Control messages are AVP 0 (mandatory): SCCRQ(1), SCCRP(2), SCCCN(3), StopCCN(4), HELLO(6), ICRQ(10), ICRP(11), ICCN(12), ACK(20).
- Attr framing capabilities=3 (BE uint32 `00000003`) attr 3, router ID 60, assigned control ID 61, Ethernet pseudowire capabilities attr 62 (`0005`, BE uint16), local/remote session IDs attrs 63/64, DOSBox MAC vendor `D05B` attr `4000` payload 6 bytes. Server requires framing caps with bit 0/1, nonzero client connection ID and PSW 5.
- **Control handshake**: client SCCRQ (conn=0; declares client control ID and NIC MAC). Server SCCRP addressed to client ID (gives server control ID). Client SCCCN addressed to server ID. Client ICRQ (local=client-session, remote=server-session). Server ICRP (local=server-session, remote=client-session). Client ICCN. Subsequent control HELLO/ACK and STOP.
- **Data header**: `00 03 00 00` plus BE 32-bit *destination session ID*, followed by unmodified Ethernet frame. Client-to-server uses the server-session ID; server-to-client uses the client-session ID. No extra Ethernet framing or FCS is added by this implementation.
- **Broadcast/multicast**: server forwards frames where `(destination_mac[0] & 1) != 0` to active clients except the client whose source MAC matches. Unicast looks for exact destination MAC. Full MAC separation is essential for `YARDOMOB`.
- **Connection bookkeeping:** `MAX_CLIENTS=64` in source, 15-second idle timeout and HELLO every ~2–3 seconds; actual multi-guest+Tailscale capacity remains untested. UDP peer identity includes host IP and source port.
- **Payload limits:** source socket buffers are 4096 bytes and incoming frames require at least 14 bytes. Original Kotlin restricted frames to 1518 bytes; this may reject oversized frames the upstream code accepts. The A1 experimental copy now permits up to `4096-8=4088` bytes per data packet. This is compatibility of *bounds*, not permission to send jumbo Ethernet on an old NIC.

## Original PoC1: what matched and what was changed

`reference/PoC1` is copied byte-for-byte from the received handoff. It has already passed its own mock tests; no Mac server was present. Structure is broadly aligned: message and AVP numbers, 12-byte control/8-byte data header, UDP, bidirectional session ID mapping, HELLO/ACK and STOP.

Separate `network/experimental` includes source-level improvements without changing that original:
1. Allow the same 4096-byte UDP receive bound for complete data packets (previous `MAX_FRAME=1518`).
2. Require locally administered unicast MAC (first octet bits 1,0 = binary `10`), not merely unicast. This is a policy gate; actual uniqueness against Mac guests must still be checked.
3. Added independent byte-for-byte SCCRQ golden fixture with known header, framing, pseudowire and vendor AVP, plus malformed control and max-buffer cases. Run via `RUN_OFFLINE_TESTS.sh`.

## Remaining unverified / integration-critical

- Exact 2026.10.01 Mac executable source parity; packet capture of client/server control and framing as actually shipped.
- Does pinned Android NDK build retain ETHNET? Build script disables SDL_net and fork ETHNET is compile-guarded; do not infer networking support from the existence of its `.cpp` file.
- NE2000 TX/RX bridge into emulator execution thread, queue capacity, session reset and device suspend behavior; no callback API is currently exposed through `retrodos_host.h`.
- Kotlin `DatagramSocket` on Tailscale Android VPN, UDP 1234 Mac firewall and limited-tailnet access policy.
- Actual NetBEUI raw 802.3 LLC frames end-to-end; two-way WinPopup and shared-file read/write across Mac and Android.
- Error handling: authentication is underlay-only, no retransmission of frames (UDP); packet loss, out-of-order data, reconnection and duplicate sessions need explicit device tests.

**Acceptance order:** compiled local core → local private HDD DOS/WFW boot with ETHNET disabled → unique name/MAC → real Mac ETHNET handshake and full duplex frames → real guest WinPopup → actual file sharing, then robustness.
