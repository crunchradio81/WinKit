import pathlib
import sys
import unittest

ROOT = pathlib.Path(sys.argv.pop(1))

class Safety(unittest.TestCase):
    def test_no_guest_disk_or_credential_in_package(self):
        suspect = []
        for path in ROOT.rglob('*'):
            if not path.is_file(): continue
            if any(p.startswith('.') for p in path.relative_to(ROOT).parts): continue
            if path.suffix.lower() in {'.raw', '.img', '.vhd', '.vdi', '.iso', '.dsk', '.apk', '.jks', '.keystore'}:
                suspect.append(str(path.relative_to(ROOT)))
        self.assertEqual(suspect, [])
    def test_android_is_offline_only(self):
        manifest = (ROOT / 'android/app/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('android.permission.INTERNET',manifest)
        self.assertNotIn('android.permission.MANAGE_EXTERNAL_STORAGE',manifest)
    def test_emulator_not_fake(self):
        main = (ROOT / 'android/app/src/main/java/org/winkit/android/MainActivity.kt').read_text()
        self.assertNotIn('ETHNET CONNECT', main)
        self.assertNotIn('startEmulator(', main)
    def test_no_sdk_apk(self):
        self.assertFalse(any(ROOT.rglob('*.apk')))

if __name__ == '__main__': unittest.main(verbosity=2)
