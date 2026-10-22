#!/usr/bin/env bash
# Compile all actual A29 Android launcher, SAF/JSON transfer, and offline-gate
# Kotlin sources against the REAL Android platform SDK (API 36). No DOSBox-X.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to a valid Android SDK path}"
JAR="$ANDROID_SDK_ROOT/platforms/android-36/android.jar"
[[ -s "$JAR" ]] || { echo "Missing Android API 36 platform android.jar: $JAR" >&2; exit 2; }
command -v kotlinc >/dev/null || { echo "Kotlin compiler not found" >&2; exit 2; }
command -v jar >/dev/null || { echo "Java jar tool not found" >&2; exit 2; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
# This stub exists only to type-check references to the original native app's
# MainActivity. Never package the stub into an APK.
kotlinc \
  -jvm-target 17 \
  -classpath "$JAR" \
  "$ROOT"/overlay/kotlin/*.kt \
  "$ROOT/host-tests/a29/AndroidMainActivityStub.kt" \
  -d "$tmp/winkit-a29-android-classes.jar"
[[ -s "$tmp/winkit-a29-android-classes.jar" ]] || {
  echo "Android SDK source compilation produced no class archive" >&2; exit 2;
}
for c in \
  com/crownparkcomputing/retrodos/winkit/WinKitSetupActivity.class \
  com/crownparkcomputing/retrodos/winkit/MacTransferImporter.class \
  com/crownparkcomputing/retrodos/winkit/MacTransferDiskInstaller.class \
  com/crownparkcomputing/retrodos/winkit/MacTransferArchiveVerifier.class \
  com/crownparkcomputing/retrodos/winkit/OfflineBootConfig.class; do
    jar tf "$tmp/winkit-a29-android-classes.jar" | grep -Fxq "$c" || {
      echo "Missing compiled A29 class: $c" >&2; exit 2;
    }
done
echo "PASS A29 Android API 36 source compilation: all real overlay Kotlin files (SDK API signatures checked)"
echo "No Gradle APK, DOSBox-X core, MS-DOS/Windows guest, or device boot used."
