import os
import sys
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from inspect_a4_disk import inspect_image

class FixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory()
        cls.out=Path(cls.temp.name)/'separate-fixture-folder'
        subprocess.run(['bash',str(ROOT/'scripts/BUILD_A4_BOOT_FIXTURE.sh'), '--out',str(cls.out)],
                       check=True, stdout=subprocess.DEVNULL)
        cls.out=cls.out/'machines'
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def test_exact_geometry(self):
        i=inspect_image(self.out/'YARDOMOB.raw',(512,63,16,8))
        self.assertEqual(i['image_bytes'], 512*63*16*8)
        self.assertTrue(i['geometry_exactly_matches_file'])
        self.assertFalse(i['boot_verified'])
        self.assertFalse(i['network_safe_to_enable'])
    def test_mbr_signature_and_marker(self):
        i=inspect_image(self.out/'YARDOMOB.raw')
        self.assertEqual(i['classification'],'A4_BIOS_ONLY_FIXTURE')
        self.assertEqual(i['partitions'],[])
        self.assertEqual(i['partition_errors'],[])
    def test_info_and_config(self):
        info=json.loads((self.out/'A4-FIXTURE-INFO.json').read_text())
        self.assertFalse(info['boot_verified'])
        self.assertFalse(info['guest_os_installed'])
        self.assertIn('BOOT C:', (self.out/'YARDOMOB.offline.conf').read_text())
        self.assertIn('ne2000=false',(self.out/'YARDOMOB.offline.conf').read_text())
        self.assertNotIn('NET START',(self.out/'YARDOMOB.offline.conf').read_text())
        self.assertEqual((self.out/'OFFLINE_READY').read_bytes(),b'a2-offline\n')
    def test_refuse_overwrite(self):
        before=(self.out/'YARDOMOB.raw').stat().st_size
        r=subprocess.run(['bash',str(ROOT/'scripts/BUILD_A4_BOOT_FIXTURE.sh'), '--out',str(self.out.parent)],
                         stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        self.assertNotEqual(r.returncode,0)
        self.assertEqual((self.out/'YARDOMOB.raw').stat().st_size,before)
    def test_read_only_inspector_negative(self):
        with tempfile.TemporaryDirectory() as tmp:
            bad=Path(tmp)/'disk.raw'
            bad.write_bytes(b'X'*513)
            with self.assertRaises(ValueError): inspect_image(bad)
            bad.write_bytes(b'X'*512)
            self.assertFalse(inspect_image(bad)['first_sector_signature_55aa'])
            sym=Path(tmp)/'sym.raw';sym.symlink_to(bad)
            with self.assertRaises(ValueError): inspect_image(sym)
    def test_overlap_and_invalid_partition_bounds(self):
        with tempfile.TemporaryDirectory() as tmp:
            img=Path(tmp)/'disk.raw'
            raw=bytearray(512)
            raw[510:]=b'\x55\xaa'
            def part(i,start,length):
                off=446+i*16
                raw[off]=0x80 if i==0 else 0
                raw[off+4]=6
                raw[off+8:off+12]=start.to_bytes(4,'little')
                raw[off+12:off+16]=length.to_bytes(4,'little')
            part(0,1,50);part(1,30,40)
            img.write_bytes(raw+bytearray(99*512))
            self.assertTrue(any('overlaps' in x for x in inspect_image(img)['partition_errors']))
            part(1,90,40)
            img.write_bytes(raw+bytearray(99*512))
            self.assertTrue(any('LBA' in x for x in inspect_image(img)['partition_errors']))

if __name__=='__main__':unittest.main(verbosity=2)
