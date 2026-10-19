#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
printf '[A2 1/4] Native offline boot gate (host C++)\n'
c++ -std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native" "$ROOT/host-tests/a2/test_offline_gate.cpp" -o "$T/native_test"
"$T/native_test"
printf '[A2 2/4] Portable Kotlin offline converter tests\n'
kotlinc "$ROOT/overlay/kotlin/OfflineBootPolicy.kt" "$ROOT/overlay/kotlin/OfflineBootConfig.kt" "$ROOT/host-tests/a2/OfflineBootChecks.kt" -include-runtime -d "$T/checks.jar"
java -jar "$T/checks.jar"
printf '[A2 3/4] Upstream overlay rewrite fixture and fail-closed checks\n'
python3 "$ROOT/host-tests/a2/test_patch.py"
printf '[A2 4/4] Previous A1 checks\n'
bash "$ROOT/scripts/RUN_OFFLINE_TESTS.sh"
echo 'A2 OFFLINE PASS; no Android JNI, emulator shared library, APK or real guest boot was built or tested.'
