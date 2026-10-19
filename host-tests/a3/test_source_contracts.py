#!/usr/bin/env python3
"""Static source contract checks; not a Gradle, JNI or emulator compilation."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class Contracts(unittest.TestCase):
    def test_importer_still_private(self):
        store = (ROOT/'overlay/kotlin/GuestStore.kt').read_text()
        self.assertIn('importVerified', store)
        self.assertIn('Checksum mismatch', store)
        self.assertIn('YARDOMOB.raw', store)
        self.assertIn('refusing overwrite', store)
    def test_renderer_lease(self):
        patch = (ROOT/'scripts/apply_a2_overlay.py').read_text()
        self.assertIn('winkit_lease::ImageLease lease(', patch)
        self.assertIn('retrodos_host_run(5, argv)', patch)
        self.assertIn('return; // lease scope ends here', patch)
        self.assertIn('winkit_offline_gate.h', patch)
    def test_core_stays_network_disabled(self):
        script = (ROOT/'scripts/BUILD_A3_ANDROID.sh').read_text()
        self.assertIn('! grep -Fq', script)
        self.assertIn('android.permission.INTERNET', script)
        self.assertIn('libSDL3.so', script)
    def test_no_microsoft_payload(self):
        suspicious = {'.vhd', '.vhdx', '.iso', '.ima', '.vfd', '.raw', '.img', '.vdi', '.qcow2'}
        source_files = [row.split('  ', 1)[1] for row in (ROOT/'MANIFEST.sha256').read_text().splitlines()]
        items = [name for name in source_files if Path(name).suffix.lower() in suspicious]
        self.assertEqual(items, [], f'OS/disk media in source package: {items}')
    def test_a3_policy_used_at_boot_gate(self):
        activity = (ROOT/'overlay/kotlin/WinKitSetupActivity.kt').read_text()
        converter = (ROOT/'overlay/kotlin/OfflineBootConfig.kt').read_text()
        self.assertIn('OfflineDiskGeometry.matches', activity)
        geometry = (ROOT/'overlay/kotlin/OfflineDiskGeometry.kt').read_text()
        self.assertIn('OfflineBootPolicy.allows', geometry)
        self.assertIn('OfflineBootPolicy.allows', converter)
if __name__ == '__main__': unittest.main(verbosity=2)
