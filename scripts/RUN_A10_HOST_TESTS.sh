#!/usr/bin/env bash
# A10: focused JVM publication races + offline A9 parity + A8 APK contracts
# and A6 native launch regression. Optional full inherited suite may take time.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP="$(mktemp -d)"; trap 'rm -rf "$TEMP"' EXIT
for cmd in kotlinc java c++ python3 as ld; do command -v "$cmd" >/dev/null || { echo "Missing host prerequisite: $cmd" >&2; exit 2; }; done
kotlinc "$ROOT/overlay/kotlin/GuestStore.kt" \
        "$ROOT/overlay/kotlin/OfflineBootPolicy.kt" \
        "$ROOT/overlay/kotlin/OfflineDiskGeometry.kt" \
        "$ROOT/overlay/kotlin/OfflineSmokeFixture.kt" \
        "$ROOT/host-tests/a10/NoReplaceChecks.kt" \
        -include-runtime -d "$TEMP/no-replace.jar"
java -jar "$TEMP/no-replace.jar"
c++ -std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native" \
   "$ROOT/host-tests/a14/test_android_disk_alias.cpp" -o "$TEMP/a14-alias"
"$TEMP/a14-alias"
python3 "$ROOT/host-tests/a10/test_no_replace_contract.py"
python3 "$ROOT/host-tests/a15/test_native_crash_export_contract.py"
python3 "$ROOT/host-tests/a16/test_native_startup_log_contract.py"
python3 "$ROOT/host-tests/a18/test_core_probe_contract.py"
if [[ "${A10_SKIP_INHERITED:-0}" != 1 ]]; then
    A9_SKIP_INHERITED=1 bash "$ROOT/scripts/RUN_A9_HOST_TESTS.sh"
    python3 "$ROOT/host-tests/a8/test_a8_build.py"
    bash "$ROOT/scripts/BUILD_A4_BOOT_FIXTURE.sh" --out "$TEMP/fixture" > "$TEMP/fixture-log.txt"
    c++ -std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native" \
       "$ROOT/host-tests/a6/test_launch_snapshot.cpp" -o "$TEMP/a6-native"
    "$TEMP/a6-native" "$TEMP/fixture/machines/YARDOMOB.raw" "$TEMP/fixture/machines/YARDOMOB.offline.conf"
fi
if [[ "${A10_FULL_INHERITED:-0}" = 1 ]]; then
    bash "$ROOT/scripts/RUN_A8_HOST_TESTS.sh"
    bash "$ROOT/scripts/RUN_A5_TESTS.sh"
fi
python3 "$ROOT/VERIFY_A10.py"
echo 'A10 HOST TESTS PASS. Android ABI, APK, actual boot and LAN remain UNVERIFIED.'
