import importlib.util
from pathlib import Path
import unittest

p = Path(__file__).resolve().parents[1] / 'scripts' / 'prepare_offline_conf.py'
spec = importlib.util.spec_from_file_location('prepare_offline_conf', p)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

SOURCE = '[dosbox]\nworking directory option=default\n[ne2000]\nne2000=true\nbackend=ethnet\n[autoexec]\nIMGMOUNT 2 "/mac/YARDO-WORKING.raw" -t hdd -fs none\nETHNET CONNECT 127.0.0.1 1234\nBOOT C:\n'
class TestPrepare(unittest.TestCase):
    def test_substitutes_only_private_image(self):
        result = m.prepare(SOURCE, '/mac/YARDO-WORKING.raw', '/data/user/0/id/files/machines/YARDOMOB.raw')
        self.assertIn('IMGMOUNT 2 "/data/user/0/id/files/machines/YARDOMOB.raw" -t hdd -fs none', result)
        self.assertIn('BOOT C:', result)
        self.assertIn('ne2000=false',result)
        self.assertNotIn('ETHNET CONNECT',result)
        self.assertIn('working directory option=noprompt',result)
    def test_must_match_once(self):
        with self.assertRaises(ValueError): m.prepare(SOURCE, '/mac/missing.raw', '/data/raw')
    def test_empty_config_rejected(self):
        with self.assertRaises(ValueError): m.prepare('IMGMOUNT 2 /mac/a.raw\n', '/mac/a.raw', '/data/a.raw')
    def test_wrong_path_rejected(self):
        with self.assertRaises(ValueError): m.prepare(SOURCE,'/mac/YARDO-WORKING.raw', 'relative.raw')

if __name__ == '__main__': unittest.main(verbosity=2)
