"""Fail-closed Android evidence collector contract tests, no real device required."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
CAPTURE=ROOT/'scripts/CAPTURE_A9_ANDROID_BOOT.sh'

class CaptureTests(unittest.TestCase):
    def fake_adb(self, out, scenario):
        path=out/'adb'
        path.write_text('''#!/usr/bin/env bash
set -eu
if [[ "${1:-}" == devices ]]; then
  printf 'List of devices attached\\nunit-test-device\\tdevice\\n'; exit 0
fi
if [[ "${1:-}" == -s ]]; then shift 2; fi
case "${1:-}" in
  get-state) echo device ;;
  shell)
    shift
    case "$*" in
      'pm path org.winkit.android.a2')
        if [[ "${ADBFIXTURE:-}" == missing ]]; then echo ''; else echo package:/data/app/test/base.apk; fi ;;
      'getprop ro.product.cpu.abi') echo arm64-v8a ;;
      'getprop ro.build.version.sdk') echo 35 ;;
      'screencap -p') printf '\\211PNG\\r\\n\\032\\nFAKE-TEST-ONLY' ;;
      *) echo 'test diagnostic' ;;
    esac ;;
  logcat) echo 'test log output' ;;
  *) exit 4 ;;
esac
''')
        path.chmod(0o755)
        return path

    def run_capture(self, scenario, dest):
        env=os.environ.copy();env['PATH']=str(dest.parent/'bin')+os.pathsep+env['PATH']
        env['ADBFIXTURE']=scenario;env.pop('ANDROID_SERIAL',None)
        return subprocess.run(['bash',str(CAPTURE),'--out',str(dest)],env=env,capture_output=True,text=True)

    def test_valid_mock_generates_local_evidence_not_success_claim(self):
        with tempfile.TemporaryDirectory() as folder:
            tmp=Path(folder);(tmp/'bin').mkdir();self.fake_adb(tmp/'bin','installed')
            dest=tmp/'evidence'
            proc=self.run_capture('installed',dest)
            self.assertEqual(proc.returncode,0,proc.stderr)
            self.assertIn('NOT a boot PASS',proc.stdout)
            self.assertEqual((dest/'device-abi.txt').read_text().strip(),'arm64-v8a')
            self.assertIn('WINKIT A4 BIOS', (dest/'MANUAL_ACCEPTANCE.txt').read_text())
            self.assertTrue((dest/'current-screen.png').read_bytes().startswith(b'\x89PNG'))

    def test_missing_app_refuses_to_create_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            tmp=Path(folder);(tmp/'bin').mkdir();self.fake_adb(tmp/'bin','missing')
            dest=tmp/'evidence'
            proc=self.run_capture('missing',dest)
            self.assertNotEqual(proc.returncode,0)
            self.assertFalse(dest.exists())

    def test_existing_evidence_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            tmp=Path(folder);(tmp/'bin').mkdir();self.fake_adb(tmp/'bin','installed')
            dest=tmp/'evidence';dest.mkdir(); (dest/'sentinel').write_text('preserve')
            proc=self.run_capture('installed',dest)
            self.assertNotEqual(proc.returncode,0)
            self.assertEqual((dest/'sentinel').read_text(),'preserve')

if __name__=='__main__': unittest.main(verbosity=2)
