"""No Android device, emulator, network access, or licensed images required."""
import hashlib
import json
import pathlib
import socket
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts' / 'common'))
from export_guest import valid_working_source, export_guest
from verify_export import verify


class TestDiskExport(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='winkit-offline-')
        self.root = pathlib.Path(self.tmp.name)
        self.srcdir = self.root / 'WorkingGuests-Synthetic'
        self.srcdir.mkdir()
        self.src = self.srcdir / 'YARDO-B-WORKING.raw'
        self.src.write_bytes(b'\x00' * 512 * 2 + b'GUEST-DEMO-BYTES' + b'\x00' * (512 - len(b'GUEST-DEMO-BYTES')))
        self.output = self.root / 'AndroidExports'

    def tearDown(self):
        self.tmp.cleanup()

    def test_accepts_a_synthetic_working_clone(self):
        self.assertEqual(valid_working_source(self.src), self.src)

    def test_rejects_original_disk_name(self):
        bad = self.srcdir / 'DOS622-PeerB-NetBEUI-v10.raw'
        bad.write_bytes(self.src.read_bytes())
        with self.assertRaises(ValueError):
            valid_working_source(bad)

    def test_rejects_symlink(self):
        link = self.root / 'WorkingGuests-Synthetic2'
        link.mkdir()
        (link / self.src.name).symlink_to(self.src)
        with self.assertRaises(ValueError):
            valid_working_source(link / self.src.name)

    def test_rejects_non_working_folder(self):
        bad = self.root / self.src.name
        bad.write_bytes(self.src.read_bytes())
        with self.assertRaises(ValueError):
            valid_working_source(bad)

    def test_rejects_non_sector_aligned_image(self):
        self.src.write_bytes(b'garbage')
        with self.assertRaises(ValueError):
            valid_working_source(self.src)

    def test_copy_integrity_and_source_unchanged(self):
        original = hashlib.sha256(self.src.read_bytes()).hexdigest()
        destdir = export_guest(self.src, self.output)
        manifest = json.loads((destdir / 'manifest.json').read_text())
        self.assertEqual(manifest['sha256'], original)
        self.assertFalse(manifest['network_safe_to_connect'])
        self.assertTrue(manifest['requires_offline_first_boot'])
        self.assertTrue(verify(destdir))
        self.assertEqual(hashlib.sha256(self.src.read_bytes()).hexdigest(), original)

    def test_copy_detects_tampering_on_verification(self):
        destdir = export_guest(self.src, self.output)
        with (destdir / 'YARDOMOB.raw').open('ab') as f:
            f.write(b'x')
        with self.assertRaises(AssertionError):
            verify(destdir)

    def test_no_private_image_in_pack(self):
        self.assertFalse(any(p.name == 'YARDOMOB.raw' for p in ROOT.rglob('*')))


class TestNetwork(unittest.TestCase):
    def test_localhost_udp_probe(self):
        probe = ROOT / 'scripts' / 'common' / 'net_probe.py'
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.bind(('127.0.0.1', 0))
            port = s.getsockname()[1]
        server = subprocess.Popen([sys.executable, str(probe), 'server', '--port', str(port), '--timeout', '4'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            # Poll client until listener is up; only localhost and test payloads.
            out = None
            for _ in range(12):
                out = subprocess.run([sys.executable, str(probe), 'client', '--host', '127.0.0.1', '--port', str(port), '--timeout', '0.2'], capture_output=True, text=True)
                if out.returncode == 0:
                    break
                time.sleep(0.05)
            self.assertEqual(out.returncode, 0, out.stderr)
            sout, serr = server.communicate(timeout=5)
            self.assertEqual(server.returncode, 0, serr)
            self.assertIn('PASS: UDP', sout)
        finally:
            if server.poll() is None:
                server.kill()
                server.communicate()

    def test_rejects_bind_all_interfaces(self):
        probe = ROOT / 'scripts' / 'common' / 'net_probe.py'
        out = subprocess.run([sys.executable, str(probe), 'server', '--bind', '0.0.0.0'], capture_output=True, text=True)
        self.assertNotEqual(out.returncode, 0)
        self.assertIn('Refusing', out.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
