"""Synthetic full anchor test plus build/source safety assertions, no SDK needed."""
import sys, unittest, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'scripts'))
import apply_a2_overlay as patch

class A6Patch(unittest.TestCase):
    def test_actual_patch_blocks_floppy_and_reads_config(self):
        data = '''/*
 * retro-dosbox frontend — SDL3 + Dear ImGui.
 */
#include "retrodos_host.h"
void scan() {
        if (games.empty() && retrodos::demo_prepare(demo_dir)) {
        }
    };
    refresh();

    enum class View { Shell };
}
void engine() {
    retrodos_host_set_window(nullptr);

    char a0[] = "dosbox-x";
        if (!g.conf_override.empty()) {
            /* A machine's conf is its own, but the floppy drives on the
        }
}
'''
        result=patch.patch_frontend(data)
        self.assertIn('winkit_launch::ValidatedLaunch run(',result)
        self.assertIn('run.config_path().begin()',result)
        self.assertIn('winkit_launch_snapshot.h',result)
        self.assertIn('g.name != "WinKit 3.11 - YARDOMOB (OFFLINE)"',result)
        self.assertNotIn('image_matches_profile(conf,',result)
        self.assertEqual(result.count('retrodos_host_run(5, argv)'),1)
        with self.assertRaises(ValueError): patch.patch_frontend(result)
    def test_overlay_contains_native_snapshot(self):
        src=(ROOT/'scripts/apply_a2_overlay.py').read_text()
        self.assertIn("shutil.copy2(ROOT / 'overlay/native/winkit_launch_snapshot.h', dest_snapshot)",src)
        self.assertIn("or dest_snapshot.exists()",src)
    def test_status_without_fake_elf(self):
        import json
        status=json.loads((ROOT/'STATUS.json').read_text())
        self.assertFalse(status['android_arm64_native_compiled'])
        self.assertFalse(status['android_apk_built'])
    def test_no_windows_disk_images(self):
        ext={'.raw','.img','.iso','.vhd','.vhdx','.vmdk','.qcow2','.exe','.dll','.vdi'}
        source_files=[row.split('  ',1)[1] for row in (ROOT/'MANIFEST.sha256').read_text().splitlines()]
        self.assertEqual([name for name in source_files if Path(name).suffix.lower() in ext],[])

if __name__ == '__main__':unittest.main(verbosity=2)
