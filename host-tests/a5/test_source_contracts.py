import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class BuildContracts(unittest.TestCase):
    def test_actual_build_contains_a5_gates(self):
        s=(ROOT/'scripts/BUILD_A5_ANDROID.sh').read_text()
        for term in ('BUILD_A3_ANDROID.sh','WinKit A5: geometry/raw-container gate failed',
                     'OfflineSmokeFixture','lib/arm64-v8a/libretrodos.so','classes.dex',
                     'org.winkit.android.a2'):
            self.assertIn(term,s)
    def test_host_not_claimed_device(self):
        s=(ROOT/'STATUS.json').read_text()
        self.assertIn('"android_apk_built": false',s)
        self.assertIn('"fixture_booted_in_emulator": false',s)
    def test_build_uses_exact_baseline_pins(self):
        s=(ROOT/'scripts/bootstrap_core.sh').read_text()
        self.assertIn('65e8c67ed4d8e5aacabc4b96337e8d47ac92d720',s)
        self.assertIn('4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11',s)
if __name__=='__main__':unittest.main(verbosity=2)
