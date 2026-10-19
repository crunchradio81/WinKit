#!/usr/bin/env python3
"""Offline fake DOSBox-X endpoint. Not an actual emulator/network acceptance test."""
import socket
import struct
import subprocess
import threading
import unittest
from pathlib import Path

JAR = Path(__file__).resolve().parents[1] / "transport/ethnet-poc-jvm.jar"
SERVER_ID = 0x55667788
MAC = b"\x02\x57\x4b\x31\x10\x01"

def avp(kind, payload, vendor=0, mandatory=False):
    return struct.pack('!HHH', len(payload)+6 | (0x8000 if mandatory else 0), vendor, kind)+payload

def ctl(dst, ns, nr, typ, *extra):
    body = avp(0, struct.pack('!H', typ), mandatory=True)+b''.join(extra)
    return struct.pack('!BBHIHH', 0xc8, 3, 12+len(body), dst, ns, nr)+body

def parse(buf):
    if len(buf) < 20 or buf[:2] != b'\xc8\x03':
        raise ValueError('invalid control')
    magic, ver, length, dst, ns, nr = struct.unpack_from('!BBHIHH',buf)
    if len(buf) != length:
        raise ValueError('bad length')
    a = {}
    off = 12
    while off < len(buf):
        flags, vendor, kind = struct.unpack_from('!HHH', buf, off)
        size = flags & 0x3ff
        if size < 6 or off+size > len(buf):
            raise ValueError('bad AVP')
        a[(vendor,kind)] = buf[off+6:off+size]
        off += size
    return dst, ns, nr, int.from_bytes(a[0,0],'big'), a

class DummyServer(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.error = None
        self.seen = []
        self.sock = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
        self.sock.bind(('127.0.0.1',1234))
        self.sock.settimeout(8)
    def run(self):
        try:
            d,peer = self.sock.recvfrom(4096)
            dest, ns, nr, typ, av = parse(d)
            assert typ == 1 and dest == 0 and av[0,62] == b'\x00\x05'
            assert av[0xD05B,0x4000] == MAC
            cli_id = int.from_bytes(av[0,61], 'big')
            self.seen.append('SCCRQ')
            self.sock.sendto(ctl(cli_id,0,1,2, avp(61,struct.pack('!I',SERVER_ID)),avp(3,struct.pack('!I',3)),avp(62,b'\x00\x05')),peer)
            d,peer = self.sock.recvfrom(4096)
            dest,ns,nr,typ,av=parse(d)
            assert typ == 3 and dest == SERVER_ID
            self.seen.append('SCCCN')
            d,peer = self.sock.recvfrom(4096)
            dest,ns,nr,typ,av=parse(d)
            assert typ == 10 and dest == SERVER_ID and av[0,62] == b'\x00\x05'
            cli_session = int.from_bytes(av[0,63],'big')
            srv_session = int.from_bytes(av[0,64],'big')
            self.seen.append('ICRQ')
            self.sock.sendto(ctl(cli_id,1,3,11, avp(63,struct.pack('!I',srv_session)),avp(64,struct.pack('!I',cli_session)),avp(62,b'\x00\x05')),peer)
            d,peer = self.sock.recvfrom(4096)
            dest,ns,nr,typ,av = parse(d)
            assert typ == 12 and dest == SERVER_ID
            self.seen.append('ICCN')
            frame=b'\xff'*6+MAC+b'\x00\x0e'
            self.sock.sendto(b'\x00\x03\x00\x00'+struct.pack('!I',cli_session)+frame,peer)
            self.seen.append('sent frame')
            seen_hello = False
            seen_frame = False
            seen_ack = False
            while True:
                d,peer = self.sock.recvfrom(4096)
                if d[:2] == b'\x00\x03':
                    assert int.from_bytes(d[4:8],'big') == srv_session and d[8:] == frame
                    seen_frame=True
                    self.seen.append('outbound frame')
                    continue
                dest,ns,nr,typ,av=parse(d)
                assert dest == SERVER_ID
                if typ == 6:
                    seen_hello=True
                    self.seen.append('HELLO')
                    self.sock.sendto(ctl(cli_id,0,0,6),peer)
                if typ == 20:
                    seen_ack=True
                    self.seen.append('ACK')
                if typ == 4:
                    self.seen.append('STOP')
                    break
            assert seen_hello and seen_frame and seen_ack
        except Exception as e:
            self.error=e
        finally:
            self.sock.close()

class ProtocolTests(unittest.TestCase):
    def test_kotlin_codec(self):
        p=subprocess.run(['java','-jar',str(JAR),'--unit-checks'],capture_output=True,text=True,timeout=15)
        self.assertEqual(p.returncode,0,p.stderr)
        self.assertIn('13 ETHNET wire checks passed',p.stdout)

    def test_session_with_fake_host(self):
        server=DummyServer();server.start()
        try:
            p=subprocess.run(['java','-jar',str(JAR),'127.0.0.1','4','--loopback-test-frame'],capture_output=True,text=True,timeout=12)
            server.join(timeout=3)
            self.assertEqual(p.returncode,0,p.stderr)
            self.assertIsNone(server.error)
            self.assertFalse(server.is_alive(),'server still waiting')
            self.assertIn('ETHNET_TRANSPORT_HANDSHAKE_OK',p.stdout)
            self.assertIn('RECEIVED_ETHERNET_FRAME bytes=14',p.stdout)
            self.assertEqual(server.seen,['SCCRQ','SCCCN','ICRQ','ICCN','sent frame','outbound frame','HELLO','ACK','STOP'])
        finally:
            server.sock.close()

if __name__ == '__main__': unittest.main(verbosity=2)
