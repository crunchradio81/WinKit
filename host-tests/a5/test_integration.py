"""Offline cross-build/source contract: no Android runtime or emulation claimed."""
import re, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

class Integration(unittest.TestCase):
    def test_native_precore_lease_and_raw_guard(self):
        s=(ROOT/'scripts/apply_a2_overlay.py').read_text()
        lease=s.index('winkit_lease::ImageLease lease(')
        guard=s.index('winkit_launch::ValidatedLaunch run(')
        run=s.index('retrodos_host_run(5, argv)',guard)
        self.assertLess(lease, guard)
        self.assertLess(guard, run)
        self.assertIn('g_engine_done.store(true);',s[guard:run])
        self.assertIn('run.config_path().begin()', s[guard:run])
        self.assertIn('machineRoot + "/YARDOMOB.offline.conf"',s[guard:run])
    def test_java_import_blocks_geometry(self):
        s=(ROOT/'overlay/kotlin/WinKitSetupActivity.kt').read_text()
        self.assertIn('OfflineDiskGeometry.matches(converted, disk)',s)
        self.assertIn('OfflineDiskGeometry.matches(offlineConf.readText(), store.installedImage())',s)
        self.assertIn('OfflineSmokeFixture.install(filesDir)',s)
    def test_app_private_and_no_overwrite(self):
        s=(ROOT/'overlay/kotlin/OfflineSmokeFixture.kt').read_text()
        self.assertIn('"machines"',s)
        self.assertIn('none { it.exists()',s)
        self.assertIn('Files.createLink(image.toPath(), stage.toPath())',s)
        self.assertIn('Files.createLink(config.toPath(), stagedConf.toPath())',s)
        self.assertIn('Files.createLink(marker.toPath(), stagedMarker.toPath())',s)
        self.assertNotIn('.renameTo(',s)
        self.assertLess(s.index('Files.createLink(image.toPath(), stage.toPath())'),
                        s.index('Files.createLink(config.toPath(), stagedConf.toPath())'))
        self.assertLess(s.index('Files.createLink(config.toPath(), stagedConf.toPath())'),
                        s.index('Files.createLink(marker.toPath(), stagedMarker.toPath())'))
    def test_artifact_media_absent(self):
        banned={'.img','.raw','.iso','.vhd','.vhdx','.vfd','.ima','.qcow2','.vdi','.vmdk'}
        actual=[str(p.relative_to(ROOT)) for p in ROOT.rglob('*') if p.is_file() and p.suffix.lower() in banned]
        self.assertEqual(actual,[])
    def test_patch_copies_geometry(self):
        s=(ROOT/'scripts/apply_a2_overlay.py').read_text()
        self.assertIn("shutil.copy2(ROOT / 'overlay/native/winkit_raw_geometry.h', dest_raw)",s)
    def test_fails_closed_on_wrong_upstream(self):
        s=(ROOT/'scripts/apply_a2_overlay.py').read_text()
        self.assertIn('git_head(repo) != APP_SHA or git_head(repo / \'core\') != CORE_SHA',s)

if __name__=='__main__':unittest.main(verbosity=2)
