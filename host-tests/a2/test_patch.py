import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
loc=ROOT/'scripts/apply_a2_overlay.py'
spec=importlib.util.spec_from_file_location('patch',loc)
p=importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)

FRONT='''/*
 * retro-dosbox frontend — SDL3 + Dear ImGui.
 */
#include "retrodos_host.h"
void foo() {
    retrodos_host_set_window(nullptr);

    char a0[] = "dosbox-x";
    auto refresh = [&]() {
        if (games.empty() && retrodos::demo_prepare(demo_dir)) {
        }
    };
    refresh();

    enum class View { Emulator };
        if (!g.conf_override.empty()) {
            /* A machine's conf is its own, but the floppy drives on the
        }
}
'''
MANIFEST='''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <application android:label="Retro-DOS" android:allowBackup="true">
        <activity
            android:name=".MainActivity"
            android:label="Retro-DOS"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>'''
class TestPatch(unittest.TestCase):
    def test_native_insert_once(self):
        s=p.patch_frontend(FRONT)
        self.assertIn('#include "winkit_offline_gate.h"',s)
        self.assertIn('winkit_lease::ImageLease lease',s)
        self.assertNotIn('\\x00',s)
        self.assertIn('WinKit 3.11 - YARDOMOB (OFFLINE)',s)
        self.assertIn('SDL_GetAndroidInternalStoragePath()',s)
        self.assertEqual(s.count('games.push_back(std::move(w))'),1)
        with self.assertRaises(ValueError):p.patch_frontend(s)
    def test_manifest_security(self):
        s=p.patch_manifest(MANIFEST)
        self.assertIn('WinKitSetupActivity',s)
        self.assertIn('allowBackup="false"',s)
        self.assertNotIn('android.permission.INTERNET',s)
        self.assertEqual(s.count('android.intent.category.LAUNCHER'),1)
        with self.assertRaises(ValueError):p.patch_manifest(s)
    def test_gradle_unique(self):
        self.assertIn('org.winkit.android.a2',p.patch_gradle('applicationId = "com.dosboxx.app"'))
        with self.assertRaises(ValueError):p.patch_gradle('applicationId = "something.else"')
    def test_different_source_fails(self):
        with self.assertRaises(ValueError):p.patch_frontend('unexpected source')
        with self.assertRaises(ValueError):p.patch_manifest('<manifest/>')
    def test_host_tag(self):
        fixture = ('TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"\n'
                   '-DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF\n'
                   '    cmake --build "$OUT/sdl3-build" -j"$JOBS"\n'
                   'APP_LIBS="$HERE/app/libs"\n')
        s=p.patch_build(fixture)
        self.assertIn('NDK_HOST_TAG',s)
        self.assertIn('SDL3-jar',s)
        with self.assertRaises(ValueError):p.patch_build(s)

if __name__=='__main__':unittest.main(verbosity=2)
