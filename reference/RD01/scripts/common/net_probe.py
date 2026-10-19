#!/usr/bin/env python3
"""Single-request UDP echo for Tailnet IP connectivity ONLY. Not DOSBox-X ETHNET.

Use only via loopback or an existing encrypted tailnet, not over the Internet.
"""
import argparse
import secrets
import socket
import sys

PREFIX = b'WINKIT-UDP-PROBE-V1:'
ACK = b'WINKIT-UDP-ACK-V1:'


def server(bind, port, timeout):
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.bind((bind, port))
        sock.settimeout(timeout)
        print(f'Listening for ONE WinKit IP-only UDP probe on {bind}:{port}', flush=True)
        data, addr = sock.recvfrom(256)
        if not data.startswith(PREFIX) or len(data) != len(PREFIX) + 32:
            print('FAIL: unexpected probe payload', file=sys.stderr)
            return 1
        sock.sendto(ACK + data[len(PREFIX):], addr)
        print('PASS: UDP request received and acknowledgement sent (not Ethernet/NetBEUI)')
        return 0


def client(host, port, timeout):
    token = secrets.token_hex(16).encode('ascii')
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.settimeout(timeout)
        sock.sendto(PREFIX + token, (host, port))
        data, addr = sock.recvfrom(256)
        if data != ACK + token:
            print('FAIL: mismatched UDP response', file=sys.stderr)
            return 1
        print(f'PASS: app-level UDP request/response via {addr[0]}:{addr[1]}')
        print('NOT TESTED: ETHNET protocol, virtual Ethernet, NetBEUI, WinPopup')
        return 0


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='command', required=True)
    ps = sub.add_parser('server', help='Run one bounded UDP probe response')
    ps.add_argument('--bind', default='127.0.0.1', help='IP to bind; use Mac Tailscale IPv4 for device tests')
    ps.add_argument('--port', type=int, default=15431)
    ps.add_argument('--timeout', type=float, default=30)
    pc = sub.add_parser('client', help='Send one UDP probe and wait for acknowledgement')
    pc.add_argument('--host', required=True, help='Mac Tailscale IPv4')
    pc.add_argument('--port', type=int, default=15431)
    pc.add_argument('--timeout', type=float, default=5)
    args = p.parse_args()
    if not 1024 <= args.port <= 65535 or args.timeout <= 0 or args.timeout > 120:
        p.error('Non-privileged port 1024-65535 and timeout 0-120 required')
    if args.command == 'server' and args.bind == '0.0.0.0':
        p.error('Refusing all-interface bind; use a trusted loopback or Tailscale interface IP')
    try:
        return server(args.bind, args.port, args.timeout) if args.command == 'server' else client(args.host, args.port, args.timeout)
    except (OSError, socket.timeout) as e:
        print('FAIL:', e, file=sys.stderr)
        return 2

if __name__ == '__main__':
    sys.exit(main())
