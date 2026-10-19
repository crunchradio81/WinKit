#!/usr/bin/env bash
# Linux x86_64 dedicated builder. Builds A5's pinned DOSBox-X Android ARM64
# package IF (and only if) all toolchains and network dependencies are present.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(uname -s)" = Linux && "$(uname -m)" = x86_64 ]] || {
    echo 'Requires Linux x86_64 build host, Android device NOT required' >&2; exit 2;
}
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to a locally installed Android SDK}"
[[ -d "$ANDROID_SDK_ROOT/ndk/28.2.13676358" ]] || {
    echo 'Missing NDK 28.2.13676358' >&2; exit 2;
}
for exe in gradle java kotlinc c++ as ld git cmake ninja autoconf automake libtoolize make python3; do
    command -v "$exe" >/dev/null || { echo "Missing: $exe" >&2; exit 2; }
done
# Fail if an old overlay checkout is present; A3's build script skips patching
# when it recognizes the old lease header, which could otherwise ship stale code.
UP="$ROOT/_external/Retro-Dosbox"
if [[ -f "$UP/frontend/retrodos_frontend.cpp" ]] &&
   grep -Fq 'winkit_image_lease.h' "$UP/frontend/retrodos_frontend.cpp" &&
   ! grep -Fq 'winkit_raw_geometry.h' "$UP/frontend/retrodos_frontend.cpp"; then
    echo 'Old A2/A3 overlay checkout detected. Preserve it; use a fresh unmodified pinned checkout for A5.' >&2
    exit 2
fi
bash "$ROOT/scripts/RUN_A5_TESTS.sh"
bash "$ROOT/scripts/BUILD_A3_ANDROID.sh"
# Confirm the dedicated application identity before post-build acceptance.
grep -Fq 'applicationId = "org.winkit.android.a2"' "$UP/android/app/build.gradle.kts" || {
    echo 'Dedicated WinKit Android application ID missing' >&2; exit 2;
}
SO="$UP/android/app/src/main/jniLibs/arm64-v8a/libretrodos.so"
APK="$ROOT/_build_outputs/WinKit311-Android-A3-debug-DEVICE-UNVERIFIED.apk"
[[ -s "$SO" && -s "$APK" ]] || { echo 'Missing verified predecessor outputs' >&2; exit 2; }
strings "$SO" > "$ROOT/_build_outputs/A5-native-strings.txt"
grep -Fq 'WinKit A5: geometry/raw-container gate failed' "$ROOT/_build_outputs/A5-native-strings.txt" || {
   echo 'A5 geometry validation code not present in native emulator' >&2; exit 2;
}
python3 - "$APK" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    names=set(z.namelist())
    assert 'lib/arm64-v8a/libretrodos.so' in names, 'ARM64 DOSBox-X missing'
    assert 'lib/arm64-v8a/libSDL3.so' in names, 'Android SDL3 missing'
    import re
    dex=[z.read(n) for n in sorted(names) if re.fullmatch(r'classes[0-9]*\.dex',n)]
    assert dex, 'No Android classes.dex'
    assert any(b'OfflineSmokeFixture' in d for d in dex), 'Android smoke fixture source not compiled into APK'
    assert any(b'OfflineDiskGeometry' in d for d in dex), 'Android disk geometry gate not compiled into APK'
print('A5 APK classes/native integration checks passed; installation NOT verified')
PY
OUT="$ROOT/_build_outputs/WinKit311-Android-A5-debug-DEVICE-UNVERIFIED.apk"
[[ ! -e "$OUT" ]] || { echo 'Will not overwrite previous A5 build' >&2; exit 2; }
cp "$APK" "$OUT"
sha256sum "$SO" "$OUT" > "$ROOT/_build_outputs/A5-BUILD-SHA256SUMS.txt"
echo "A5 ANDROID ARM64 CORE/APK BUILT: $OUT (if previous checks passed)"
echo 'No Android boot, guest OS or network operation is claimed.'
