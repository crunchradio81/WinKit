#!/usr/bin/env bash
# Real host compiles and executes: not Android cross compilation or guest boot.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for x in as ld c++ python3 kotlinc java sha256sum; do
    command -v "$x" >/dev/null || { echo "MISSING host dependency: $x" >&2; exit 2; }
done
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
bash "$ROOT/scripts/BUILD_A4_BOOT_FIXTURE.sh" --out "$TMP/fixture" > "$TMP/fixture.txt"
DISK="$TMP/fixture/machines/YARDOMOB.raw"
CONF="$TMP/fixture/machines/YARDOMOB.offline.conf"
c++ -std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native" \
    "$ROOT/host-tests/a5/test_raw_geometry.cpp" -o "$TMP/native-geometry"
"$TMP/native-geometry" "$DISK"
kotlinc "$ROOT/overlay/kotlin/OfflineBootPolicy.kt" \
        "$ROOT/overlay/kotlin/OfflineDiskGeometry.kt" \
        "$ROOT/host-tests/a5/OfflineDiskGeometryChecks.kt" \
        -include-runtime -d "$TMP/kotlin-geometry.jar"
java -jar "$TMP/kotlin-geometry.jar" "$DISK"
kotlinc "$ROOT/overlay/kotlin/OfflineBootPolicy.kt" \
        "$ROOT/overlay/kotlin/OfflineDiskGeometry.kt" \
        "$ROOT/overlay/kotlin/OfflineSmokeFixture.kt" \
        "$ROOT/host-tests/a5/OfflineSmokeFixtureChecks.kt" \
        -include-runtime -d "$TMP/kotlin-smoke.jar"
java -jar "$TMP/kotlin-smoke.jar" "$(sha256sum "$DISK" | cut -d' ' -f1)"
python3 "$ROOT/host-tests/a5/test_integration.py"
python3 "$ROOT/host-tests/a5/test_source_contracts.py"
python3 "$ROOT/VERIFY_A5.py"
if [[ "${A5_RUN_A4:-0}" = "1" ]]; then
  bash "$ROOT/scripts/RUN_A4_TESTS.sh"
fi
echo 'A5 SOURCE/OFFLINE SMOKE TESTS PASS; ANDROID ELF/APK/BOOT NOT VERIFIED'
