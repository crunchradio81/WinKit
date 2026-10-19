#!/usr/bin/env python3
from pathlib import Path
import re, unittest
ROOT=Path(__file__).resolve().parents[2]
class A7BuildContract(unittest.TestCase):
 def test_workflow_prerequisites(self):
  s=(ROOT/'.github/workflows/a7-android-arm64.yml').read_text()
  for term in ('workflow_dispatch:', 'kotlin-compiler-2.0.21.zip', 'GITHUB_PATH', 'gradle-version:', 'sdkmanager --install', 'if: always()', 'if: success()', 'BUILD_A7_ANDROID.sh'):
   self.assertIn(term,s)
 def test_builder_pipeline(self):
  s=(ROOT/'scripts/BUILD_A7_ANDROID.sh').read_text()
  for term in ('RUN_A7_HOST_TESTS.sh','BUILD_A3_ANDROID.sh','WinKit311-Android-A7-debug-DEVICE-UNVERIFIED.apk','A7-BUILD-SHA256SUMS.txt','libretrodos.so','libSDL3.so'):
   self.assertIn(term,s)
  self.assertIn('WinKit A6: launch profile mismatch',s)
 def test_no_apk_claim(self):
  self.assertFalse(any(ROOT.rglob('*.apk')))
  self.assertFalse(any(ROOT.rglob('*.raw')))
 def test_source_identity(self):
  s=(ROOT/'scripts/bootstrap_core.sh').read_text()
  self.assertIn('65e8c67ed4d8e5aacabc4b96337e8d47ac92d720',s)
  self.assertIn('4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11',s)
 def test_workflow_does_not_auto_publish(self):
  s=(ROOT/'.github/workflows/a7-android-arm64.yml').read_text()
  self.assertNotIn('push:',s)
  self.assertNotIn('release:',s)
if __name__=='__main__':unittest.main()
