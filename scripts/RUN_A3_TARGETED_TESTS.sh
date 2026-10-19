#!/usr/bin/env bash
# Portable C++/JVM and revision-patch tests (no proprietary media, Android device, Mac).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP="$(mktemp -d)"; trap 'rm -rf "$TEMP"' EXIT
CFLAGS=(-std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native")
for test in "$ROOT/host-tests/a3/test_lease.cpp" "$ROOT/host-tests/a3/test_offline_policy.cpp" "$ROOT/host-tests/a2/test_offline_gate.cpp"; do
  c++ "${CFLAGS[@]}" "$test" -o "$TEMP/$(basename "${test%.cpp}")"
  "$TEMP/$(basename "${test%.cpp}")"
done
kotlinc "$ROOT/overlay/kotlin/OfflineBootPolicy.kt" \
  "$ROOT/overlay/kotlin/OfflineBootConfig.kt" \
  "$ROOT/host-tests/a3/OfflinePolicyChecks.kt" -include-runtime -d "$TEMP/a3.jar"
java -jar "$TEMP/a3.jar"
kotlinc "$ROOT/overlay/kotlin/OfflineBootPolicy.kt" \
  "$ROOT/overlay/kotlin/OfflineBootConfig.kt" \
  "$ROOT/host-tests/a2/OfflineBootChecks.kt" -include-runtime -d "$TEMP/a2.jar"
java -jar "$TEMP/a2.jar"
python3 "$ROOT/host-tests/a2/test_patch.py"
python3 "$ROOT/host-tests/a3/test_source_contracts.py"
python3 "$ROOT/host-tests/test_boot_conf.py"
echo 'A3 TARGETED OFFLINE TESTS PASSED (HOST ONLY; NO APK / BOOT CLAIMED)'
