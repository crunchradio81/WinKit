#!/usr/bin/env bash
# A4 portable tests: assemble x86 BIOS smoke fixture, inspect, validate C++/Kotlin config.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for cmd in python3 as ld c++ kotlinc java; do command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 2; }; done
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
bash "$ROOT/scripts/BUILD_A4_BOOT_FIXTURE.sh" --out "$TMP/fixture" > "$TMP/create.log"
python3 "$ROOT/host-tests/a4/test_fixture.py"
DISK="$TMP/fixture/machines/YARDOMOB.raw"
CONF="$TMP/fixture/machines/YARDOMOB.offline.conf"
c++ -std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native" \
   "$ROOT/host-tests/a4/boot_policy_cli.cpp" -o "$TMP/checkconf"
"$TMP/checkconf" "$CONF" "$DISK"
# A4 generated profile must independently pass the Kotlin policy and native policy.
kotlinc "$ROOT/overlay/kotlin/OfflineBootPolicy.kt" \
  "$ROOT/host-tests/a4/BootPolicyFileChecks.kt" -include-runtime -d "$TMP/filepolicy.jar"
java -jar "$TMP/filepolicy.jar" "$CONF" "$DISK"
kotlinc "$ROOT/overlay/kotlin/BootSectorEvidence.kt" \
  "$ROOT/host-tests/a4/BootSectorEvidenceChecks.kt" -include-runtime -d "$TMP/sectors.jar"
java -jar "$TMP/sectors.jar" "$DISK"
# Fixture negative tests (policy must not permit arbitrary commands or switched HDD path).
sed '/BOOT C:/i ETHNET CONNECT 100.64.0.1' "$CONF" > "$TMP/unsafe.conf"
if "$TMP/checkconf" "$TMP/unsafe.conf" "$DISK" >/dev/null 2>&1; then
  echo 'FAIL: native validator accepted injected ETHNET' >&2; exit 1
fi
if "$TMP/checkconf" "$CONF" "$TMP/wrong/machines/YARDOMOB.raw" >/dev/null 2>&1; then
  echo 'FAIL: native validator accepted wrong destination' >&2; exit 1
fi
# Minimal inherited C++ policy and patch tests. A complete inherited A3 suite
# can be requested explicitly, but is deliberately not repeated in every A4 run
# (four standalone kotlinc invocations can exceed limited CI time windows).
for legacy in "$ROOT/host-tests/a3/test_offline_policy.cpp" \
              "$ROOT/host-tests/a2/test_offline_gate.cpp" \
              "$ROOT/host-tests/a3/test_lease.cpp"; do
  c++ -std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native" \
    "$legacy" -o "$TMP/$(basename "${legacy%.cpp}")"
  "$TMP/$(basename "${legacy%.cpp}")"
done
python3 "$ROOT/host-tests/a2/test_patch.py"
python3 "$ROOT/host-tests/a3/test_source_contracts.py"
python3 "$ROOT/host-tests/test_boot_conf.py"
if [[ "${A4_FULL_REGRESSION:-0}" = 1 ]]; then
  bash "$ROOT/scripts/RUN_A3_TARGETED_TESTS.sh"
fi
python3 "$ROOT/VERIFY_A4.py"
echo 'A4 OFFLINE SOURCE TESTS PASS (NOT AN ARM64 APK OR AN EMULATOR BOOT)'
